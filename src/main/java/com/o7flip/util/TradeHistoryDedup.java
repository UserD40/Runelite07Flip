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

/**
 * Cleans duplicate rows out of the on-disk trade history.
 *
 * The plugin records each GE offer locally as ONE merged row (qty/totalGp
 * accumulated across fills, tagged with {@code offerInstanceId}), but earlier
 * versions of the plugin also POSTed each fill to 07flip.com as a separate
 * delta. On the next sync, the server returned those fills as their own
 * {@link TradeRecord} rows — different qty/totalGp than the local merged row,
 * so {@code fingerprint()}-based dedup missed them, and they got appended.
 *
 * Result: tradeHistory carried both the local merged row AND each server-fill
 * row for the same offer. FIFO double-counted, "Today" and "Worst" stats were
 * wildly wrong. This class detects those overlaps and drops the redundant
 * rows; the local merged row stays (it carries the offer's authoritative cost
 * basis and the user-facing presentation expects one row per offer).
 *
 * Two kinds of duplicates are removed:
 * <ol>
 *   <li><b>Server-fill subsumption:</b> N server-fill rows ({@code tradeId} set,
 *       {@code offerInstanceId} null) whose quantities and totals sum exactly
 *       to a local merged row ({@code offerInstanceId} set) for the same
 *       {@code (itemId, isBuy)} within a 24-hour window.</li>
 *   <li><b>Stale partial twins:</b> two local merged rows for the same
 *       {@code (itemId, isBuy, quantity, totalGp)} within 24 hours where one
 *       is {@code partial=true} (recorded mid-flight) and the other
 *       {@code partial=false} (the final terminal observation). The
 *       partial twin is dropped.</li>
 * </ol>
 *
 * Stateless and pure. Safe to call from any thread.
 */
public final class TradeHistoryDedup
{
	/**
	 * Outer window for treating two rows as "the same offer" by timestamp.
	 * Generous because a slow GE offer can fill over many hours, and the
	 * server-fill timestamps reflect when each delta arrived, not when the
	 * offer started.
	 */
	private static final long PROXIMITY_MS = 24L * 60L * 60L * 1000L;

	private TradeHistoryDedup()
	{
	}

	/**
	 * Returns a new list with duplicates removed. Input list is not modified.
	 * Order is preserved — for any pair of kept rows the relative order in the
	 * input survives in the output.
	 */
	public static List<TradeRecord> scrub(List<TradeRecord> trades)
	{
		if (trades == null || trades.size() < 2)
		{
			return trades == null ? new ArrayList<>() : new ArrayList<>(trades);
		}

		BitSet drop = new BitSet(trades.size());

		// Pass 1 — drop stale-partial local twins. Two local merged rows with
		// identical (itemId, isBuy, qty, totalGp) within the proximity window
		// represent the same offer being re-observed (e.g., across a plugin
		// restart that lost slotRecordedFills, or a slot that recycled without
		// the plugin seeing the EMPTY transition).
		//
		// Critical constraint: we ONLY collapse when one row is "stale" and
		// the other "fresh". A stale row has partial=true OR no tradeId yet;
		// a fresh row has partial=false AND/OR a tradeId. Two terminal,
		// server-reconciled rows of identical shape are LEGITIMATE separate
		// offers (e.g. someone buying 21 OSRS bonds for membership over
		// time — each bond is its own offer of qty=1 at the same price).
		// Without this constraint the bond ledger gets wiped on first scrub.
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
					break; // A is gone, move to next a
				}
			}
		}

		// Pass 2 — drop server-fill rows subsumed by a surviving local merged
		// row. A "subsumed" set is a subset of server fills (tradeId set,
		// offerInstanceId null) for the same (itemId, isBuy) whose quantities
		// and totals add up to exactly the local row's qty/totalGp inside the
		// proximity window. Exact-sum matching keeps us conservative: we
		// won't accidentally collapse a legitimately repeated offer.
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
				if (S.tradeId == null) continue;            // need a server-side id
				if (S.offerInstanceId != null) continue;    // not a per-fill server row
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
			// Only collapse when the running totals match the local row exactly.
			// Partial matches are left alone — could be a legitimate mix of
			// website-only trades and locally-tracked ones.
			if (candQty == L.quantity && candGp == L.totalGp && !candIdxs.isEmpty())
			{
				for (int j : candIdxs)
				{
					drop.set(j);
				}
			}
		}

		// Pass 3 — collapse "stuck-partial duplicates". When the plugin loses
		// track of an active GE offer across sessions (slotRecordedFills wasn't
		// persisted, or the slot was re-observed before the EMPTY transition),
		// it re-records the SAME unchanged offer state as a brand-new row with
		// a fresh offerInstanceId. The on-disk symptom is a chain of rows
		// with identical (itemId, isBuy, quantity, totalGp, totalQuantity)
		// spaced hours-to-days apart — each one a re-observation of the same
		// fill, all partial=true. FIFO would consume each as a separate sell,
		// producing phantom flips and inflated "Today" counts.
		//
		// Conservative: rows must agree on quantity, gp, AND totalQuantity AND
		// all be partial=true within a 7-day window. A user genuinely placing
		// the same offer twice in a week would also be collapsed here — that's
		// a rare false positive we trade for the much more common stuck-offer
		// case the user actually hits.
		// Grouping key intentionally omits totalQuantity — a sync round-trip
		// can drop it to null on an otherwise-identical row, and that
		// shouldn't make the row escape collapse. (itemId, isBuy, quantity,
		// totalGp) is unique enough in practice: a second LEGITIMATE offer
		// of the same item with the same fill count and gross gp inside a
		// 7-day window is vanishingly rare.
		Map<String, List<Integer>> stuckGroups = new HashMap<>();
		for (int i = 0; i < trades.size(); i++)
		{
			if (drop.get(i)) continue;
			TradeRecord t = trades.get(i);
			if (t == null) continue;
			if (t.offerInstanceId == null) continue;     // only local-merged rows
			if (!t.partial) continue;                    // terminal rows aren't stuck
			if (t.quantity <= 0) continue;
			String key = t.itemId + "|" + t.isBuy + "|" + t.quantity + "|" + t.totalGp;
			stuckGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
		}
		final long STUCK_WINDOW_MS    = 7L * 24L * 60L * 60L * 1000L;
		// Two thresholds for when a group counts as "stuck":
		//  - 3+ rows spanning ≥ 1 hour  → the classic many-observation
		//    pattern after a long-stuck offer is re-observed across
		//    many sessions.
		//  - 2 rows spanning ≥ 48 hours → the rarer case where only a
		//    couple of sessions have run since the offer got stuck. A
		//    user genuinely placing the same offer twice 2+ days apart
		//    is unusual enough that we accept the small false-positive
		//    risk; legitimate "two identical offers" within 48h still
		//    stay separated (see localTwins_farApart_inTime_areKept).
		final long STUCK_MIN_SPAN_MS  = 60L * 60L * 1000L;
		final long STUCK_LONG_SPAN_MS = 48L * 60L * 60L * 1000L;
		for (List<Integer> group : stuckGroups.values())
		{
			if (group.size() < 2) continue;
			// Sort by timestamp ascending — keep the EARLIEST observation
			// (closest to when the fill actually happened) and drop the
			// re-observations that came later.
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
				// totalQuantity is "compatible" when both rows agree on
				// the offer size OR one row's totalQuantity is null. Null
				// can sneak in via server reconciliation paths that
				// preserve fingerprint but drop the totalQuantity field —
				// the row is still the same stuck offer.
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

	/**
	 * Picks which of two local-merged twins to drop. Prefers to KEEP the row
	 * that has a tradeId (server-reconciled = more authoritative), failing that
	 * the row with {@code partial=false} (terminal = more authoritative),
	 * failing that the earlier-timestamped row (older observation, generally
	 * more stable).
	 */
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

	/**
	 * Returns true when {@code A} and {@code B} are likely the SAME real GE
	 * offer observed twice (e.g., across a plugin restart that lost the
	 * per-slot fill ledger). A pair is considered a duplicate observation
	 * only when exactly one of them carries the "authoritative" signal — a
	 * server-issued tradeId OR a terminal state ({@code partial=false}) —
	 * while the other is stale (no tradeId, still partial=true).
	 *
	 * Two terminal rows of identical shape are treated as legitimate
	 * separate offers. That preserves bond ledgers (21 separate bond buys
	 * of qty=1 at the same gp), commodity bulk flips (e.g., five separate
	 * runite ore offers), and any case where a user genuinely places the
	 * same offer twice in a 24-hour window.
	 */
	private static boolean looksLikeDuplicateObservation(TradeRecord A, TradeRecord B)
	{
		boolean aAuth = A.tradeId != null || !A.partial;
		boolean bAuth = B.tradeId != null || !B.partial;
		return aAuth != bAuth;
	}

	/**
	 * Convenience for callers that want a sorted, scrubbed view. Sorts by
	 * timestamp ascending — same order the FIFO matcher expects.
	 */
	public static List<TradeRecord> scrubAndSort(List<TradeRecord> trades)
	{
		List<TradeRecord> out = scrub(trades);
		out.sort(Comparator.comparingLong(t -> t.timestamp));
		return out;
	}

	/**
	 * Anything earlier than 5 minutes before the offer was first observed
	 * is treated as a back-dated relic; smaller gaps could just be event
	 * propagation jitter and aren't worth rewriting.
	 */
	private static final long BACKDATE_TOLERANCE_MS = 5L * 60L * 1000L;

	/**
	 * Repairs timestamps on locally-merged rows where an earlier plugin
	 * version pushed a freshly-recorded buy to "1 second before the
	 * earliest existing trade of the same item", in an attempt to pair it
	 * with phantom sells. That heuristic silently re-ordered FIFO so a
	 * brand-new buy could sort BEFORE an older buy of the same item,
	 * which mis-attributed cost basis on subsequent sells (e.g., the
	 * rancour SELL 3 today was being matched against 2 cheaper items from
	 * an older BUY 2 lot instead of the 3-of-3 lot the user actually
	 * sold). Even with the back-dating removed for new trades, the bad
	 * timestamps still sit in tradeHistory and keep distorting FIFO.
	 *
	 * The fix uses {@code offerInstanceId}, which the plugin minted as
	 * {@code System.currentTimeMillis() * 10 + slot} when the offer was
	 * first observed. Dividing by 10 recovers the real-time ms at first
	 * observation; if the stored {@code timestamp} is more than
	 * {@link #BACKDATE_TOLERANCE_MS} earlier, we replace it with that
	 * recovered real time. Rows without {@code offerInstanceId}
	 * (server-fetched fills) aren't touched — their timestamps come from
	 * the server and are trusted.
	 *
	 * Pure. Returns a new list. Run as a one-shot migration alongside
	 * {@link #scrub(List)}; callers gate with a "healed" flag in config
	 * so it doesn't repeat on every load.
	 */
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
			// Recreate the row with the corrected timestamp. Copy every
			// other field unchanged — FIFO accuracy is the goal, not data
			// loss elsewhere.
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
