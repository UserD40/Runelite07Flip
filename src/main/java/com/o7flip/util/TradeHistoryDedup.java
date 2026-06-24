/*
 * Copyright (c) 2026, 07Flip
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.o7flip.util;

import com.o7flip.model.TradeRecord;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TradeHistoryDedup
{
	private static final long PROXIMITY_MS = 24L * 60L * 60L * 1000L;

	private TradeHistoryDedup()
	{
	}

	public static List<TradeRecord> scrub(List<TradeRecord> trades)
	{
		if (trades == null || trades.size() < 2)
		{
			return trades == null ? new ArrayList<>() : new ArrayList<>(trades);
		}

		BitSet drop = new BitSet(trades.size());

		List<Integer> localMerged = new ArrayList<>();
		for (int i = 0; i < trades.size(); i++)
		{
			TradeRecord t = trades.get(i);
			if (t != null && t.offerInstanceId != null && t.quantity > 0 && t.totalGp >= 0)
			{
				localMerged.add(i);
			}
		}
		for (int a = 0; a < localMerged.size(); a++)
		{
			int ia = localMerged.get(a);
			if (drop.get(ia))
			{
				continue;
			}
			TradeRecord A = trades.get(ia);
			for (int b = a + 1; b < localMerged.size(); b++)
			{
				int ib = localMerged.get(b);
				if (drop.get(ib))
				{
					continue;
				}
				TradeRecord B = trades.get(ib);
				if (A.itemId != B.itemId) continue;
				if (A.isBuy != B.isBuy) continue;
				if (A.quantity != B.quantity) continue;
				if (A.totalGp != B.totalGp) continue;
				if (Math.abs(A.timestamp - B.timestamp) > PROXIMITY_MS) continue;
				if (!looksLikeDuplicateObservation(A, B)) continue;

				int loser = pickStaleTwin(ia, A, ib, B);
				drop.set(loser);
				if (loser == ia)
				{
					break;
				}
			}
		}

		List<Integer> survivingLocal = new ArrayList<>();
		for (int i : localMerged)
		{
			if (!drop.get(i)) survivingLocal.add(i);
		}

		for (int li : survivingLocal)
		{
			TradeRecord L = trades.get(li);
			List<Integer> candIdxs = new ArrayList<>();
			long candQty = 0;
			long candGp = 0;
			for (int j = 0; j < trades.size(); j++)
			{
				if (j == li) continue;
				if (drop.get(j)) continue;
				TradeRecord S = trades.get(j);
				if (S == null) continue;
				if (S.tradeId == null) continue;
				if (S.offerInstanceId != null) continue;
				if (S.itemId != L.itemId) continue;
				if (S.isBuy != L.isBuy) continue;
				if (S.quantity <= 0) continue;
				if (Math.abs(S.timestamp - L.timestamp) > PROXIMITY_MS) continue;
				if (S.quantity > L.quantity) continue;
				if (S.totalGp > L.totalGp) continue;
				if (candQty + S.quantity > L.quantity) continue;
				if (candGp + S.totalGp  > L.totalGp)  continue;
				candIdxs.add(j);
				candQty += S.quantity;
				candGp  += S.totalGp;
			}
			if (candQty == L.quantity && candGp == L.totalGp && !candIdxs.isEmpty())
			{
				for (int j : candIdxs)
				{
					drop.set(j);
				}
			}
		}

		Map<String, List<Integer>> stuckGroups = new HashMap<>();
		for (int i = 0; i < trades.size(); i++)
		{
			if (drop.get(i)) continue;
			TradeRecord t = trades.get(i);
			if (t == null) continue;
			if (t.offerInstanceId == null) continue;
			if (!t.partial) continue;
			if (t.quantity <= 0) continue;
			String key = t.itemId + "|" + t.isBuy + "|" + t.quantity + "|" + t.totalGp;
			stuckGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
		}
		final long STUCK_WINDOW_MS    = 7L * 24L * 60L * 60L * 1000L;
		final long STUCK_MIN_SPAN_MS  = 60L * 60L * 1000L;
		final long STUCK_LONG_SPAN_MS = 48L * 60L * 60L * 1000L;
		for (List<Integer> group : stuckGroups.values())
		{
			if (group.size() < 2) continue;
			group.sort((a, b) -> Long.compare(trades.get(a).timestamp, trades.get(b).timestamp));
			TradeRecord keeper = trades.get(group.get(0));
			TradeRecord last = trades.get(group.get(group.size() - 1));
			long span = last.timestamp - keeper.timestamp;
			boolean stuck =
				(group.size() >= 3 && span >= STUCK_MIN_SPAN_MS)
					|| (group.size() >= 2 && span >= STUCK_LONG_SPAN_MS);
			if (!stuck)
			{
				continue;
			}
			for (int k = 1; k < group.size(); k++)
			{
				int idx = group.get(k);
				TradeRecord cand = trades.get(idx);
				if (cand.timestamp - keeper.timestamp > STUCK_WINDOW_MS) continue;
				if (keeper.totalQuantity != null && cand.totalQuantity != null
					&& !keeper.totalQuantity.equals(cand.totalQuantity))
				{
					continue;
				}
				drop.set(idx);
			}
		}

		List<TradeRecord> out = new ArrayList<>(trades.size() - drop.cardinality());
		for (int i = 0; i < trades.size(); i++)
		{
			if (!drop.get(i)) out.add(trades.get(i));
		}
		return out;
	}

	private static int pickStaleTwin(int ia, TradeRecord A, int ib, TradeRecord B)
	{
		boolean aHasId = A.tradeId != null;
		boolean bHasId = B.tradeId != null;
		if (aHasId != bHasId)
		{
			return aHasId ? ib : ia;
		}
		if (A.partial != B.partial)
		{
			return A.partial ? ia : ib;
		}
		return A.timestamp <= B.timestamp ? ib : ia;
	}

	private static boolean looksLikeDuplicateObservation(TradeRecord A, TradeRecord B)
	{
		boolean aAuth = A.tradeId != null || !A.partial;
		boolean bAuth = B.tradeId != null || !B.partial;
		return aAuth != bAuth;
	}

	public static List<TradeRecord> scrubAndSort(List<TradeRecord> trades)
	{
		List<TradeRecord> out = scrub(trades);
		out.sort(Comparator.comparingLong(t -> t.timestamp));
		return out;
	}

	private static final long BACKDATE_TOLERANCE_MS = 5L * 60L * 1000L;

	public static List<TradeRecord> healBackdatedTimestamps(List<TradeRecord> trades)
	{
		if (trades == null || trades.isEmpty())
		{
			return trades == null ? new ArrayList<>() : new ArrayList<>(trades);
		}
		List<TradeRecord> out = new ArrayList<>(trades.size());
		for (TradeRecord t : trades)
		{
			if (t == null || t.offerInstanceId == null)
			{
				out.add(t);
				continue;
			}
			long observedRealMs = t.offerInstanceId / 10L;
			if (observedRealMs <= 0L)
			{
				out.add(t);
				continue;
			}
			if (t.timestamp >= observedRealMs - BACKDATE_TOLERANCE_MS)
			{
				out.add(t);
				continue;
			}
			TradeRecord healed = new TradeRecord();
			healed.itemId          = t.itemId;
			healed.name            = t.name;
			healed.isBuy           = t.isBuy;
			healed.quantity        = t.quantity;
			healed.totalGp         = t.totalGp;
			healed.priceEach       = t.priceEach;
			healed.timestamp       = observedRealMs;
			healed.partial         = t.partial;
			healed.tradeId         = t.tradeId;
			healed.offerInstanceId = t.offerInstanceId;
			healed.totalQuantity   = t.totalQuantity;
			out.add(healed);
		}
		return out;
	}
}
