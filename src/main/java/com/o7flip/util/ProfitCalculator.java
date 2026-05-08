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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure FIFO cost-basis calculator over a list of {@link TradeRecord}.
 * Returns completed flips (buy/sell pairs), remaining open positions,
 * and aggregate stats — used by the My Trades stats panel, the GP drop
 * overlay, and any future profit charting.
 *
 * Stateless and side-effect-free. Safe to call from any thread.
 */
public final class ProfitCalculator
{
	private ProfitCalculator()
	{
	}

	/**
	 * Compute completed flips, open positions, and lifetime stats.
	 *
	 * Trades are sorted by timestamp ascending before processing — the input
	 * list is not modified. FIFO matching: each sell consumes the oldest open
	 * buy(s) of the same item.
	 *
	 * Defensive: a sell with no prior buy emits a "phantom" completed flip
	 * with {@code buyTotal=0}. This shouldn't happen in normal OSRS GE use
	 * but keeps the math monotonic if the trade history is incomplete.
	 *
	 * Precision: gp values are long. Partial consumption of a buy lot uses
	 * half-up rounding on the consumed portion; the remainder is tracked
	 * exactly so accumulated drift stays under ~1 gp per partial fill.
	 */
	public static Result compute(List<TradeRecord> trades)
	{
		if (trades == null || trades.isEmpty())
		{
			return Result.empty();
		}

		List<TradeRecord> sorted = new ArrayList<>(trades);
		sorted.sort(Comparator.comparingLong(t -> t.timestamp));

		Map<Integer, Deque<OpenLot>> openLotsByItem = new HashMap<>();
		List<CompletedFlip> completedFlips = new ArrayList<>();

		for (TradeRecord trade : sorted)
		{
			if (trade.quantity <= 0 || trade.totalGp < 0)
			{
				continue;
			}
			if (trade.isBuy)
			{
				openLotsByItem
					.computeIfAbsent(trade.itemId, k -> new ArrayDeque<>())
					.addLast(new OpenLot(trade.quantity, trade.totalGp, trade.timestamp, trade.name));
			}
			else
			{
				consumeSell(trade, openLotsByItem.get(trade.itemId), completedFlips);
			}
		}

		Map<Integer, OpenPosition> openPositions = new HashMap<>();
		for (Map.Entry<Integer, Deque<OpenLot>> entry : openLotsByItem.entrySet())
		{
			OpenPosition pos = OpenPosition.from(entry.getKey(), entry.getValue());
			if (pos != null)
			{
				openPositions.put(entry.getKey(), pos);
			}
		}

		return new Result(
			Collections.unmodifiableList(completedFlips),
			Collections.unmodifiableMap(openPositions),
			Stats.from(completedFlips)
		);
	}

	private static void consumeSell(TradeRecord sell, Deque<OpenLot> queue, List<CompletedFlip> out)
	{
		int sellRemainingQty = sell.quantity;
		long sellRemainingGp = sell.totalGp;

		while (sellRemainingQty > 0 && queue != null && !queue.isEmpty())
		{
			OpenLot head = queue.peekFirst();
			int consumed = Math.min(sellRemainingQty, head.qty);

			long consumedBuyGp;
			if (consumed == head.qty)
			{
				consumedBuyGp = head.gp;
				queue.pollFirst();
			}
			else
			{
				consumedBuyGp = Math.round((double) head.gp * consumed / head.qty);
				head.qty -= consumed;
				head.gp -= consumedBuyGp;
			}

			long consumedSellGp;
			if (consumed == sellRemainingQty)
			{
				consumedSellGp = sellRemainingGp;
			}
			else
			{
				consumedSellGp = Math.round((double) sell.totalGp * consumed / sell.quantity);
			}

			out.add(new CompletedFlip(
				sell.itemId,
				sell.name,
				consumed,
				consumedBuyGp,
				consumedSellGp,
				head.firstTimestamp,
				sell.timestamp
			));

			sellRemainingQty -= consumed;
			sellRemainingGp -= consumedSellGp;
		}

		if (sellRemainingQty > 0)
		{
			out.add(new CompletedFlip(
				sell.itemId,
				sell.name,
				sellRemainingQty,
				0L,
				sellRemainingGp,
				sell.timestamp,
				sell.timestamp
			));
		}
	}

	// ── Data classes ────────────────────────────────────────────────────────

	public static final class Result
	{
		public final List<CompletedFlip> completedFlips;
		public final Map<Integer, OpenPosition> openPositions;
		public final Stats stats;

		Result(List<CompletedFlip> completedFlips, Map<Integer, OpenPosition> openPositions, Stats stats)
		{
			this.completedFlips = completedFlips;
			this.openPositions = openPositions;
			this.stats = stats;
		}

		static Result empty()
		{
			return new Result(
				Collections.emptyList(),
				Collections.emptyMap(),
				Stats.EMPTY
			);
		}
	}

	public static final class CompletedFlip
	{
		public final int itemId;
		public final String name;
		public final int quantity;
		public final long buyTotal;
		public final long sellTotal;
		public final long profit;
		public final double roiPct;
		public final long firstBuyTimestamp;
		public final long sellTimestamp;

		CompletedFlip(int itemId, String name, int quantity, long buyTotal, long sellTotal,
			long firstBuyTimestamp, long sellTimestamp)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.buyTotal = buyTotal;
			this.sellTotal = sellTotal;
			this.profit = sellTotal - buyTotal;
			this.roiPct = buyTotal > 0 ? (100.0 * (sellTotal - buyTotal) / buyTotal) : 0.0;
			this.firstBuyTimestamp = firstBuyTimestamp;
			this.sellTimestamp = sellTimestamp;
		}
	}

	public static final class OpenPosition
	{
		public final int itemId;
		public final String name;
		public final int remainingQty;
		public final long remainingCostBasis;
		public final long earliestBuyTimestamp;

		OpenPosition(int itemId, String name, int remainingQty, long remainingCostBasis, long earliestBuyTimestamp)
		{
			this.itemId = itemId;
			this.name = name;
			this.remainingQty = remainingQty;
			this.remainingCostBasis = remainingCostBasis;
			this.earliestBuyTimestamp = earliestBuyTimestamp;
		}

		static OpenPosition from(int itemId, Deque<OpenLot> lots)
		{
			if (lots == null || lots.isEmpty())
			{
				return null;
			}
			int totalQty = 0;
			long totalGp = 0;
			String name = null;
			long firstTs = Long.MAX_VALUE;
			for (OpenLot lot : lots)
			{
				totalQty += lot.qty;
				totalGp += lot.gp;
				if (name == null)
				{
					name = lot.name;
				}
				if (lot.firstTimestamp < firstTs)
				{
					firstTs = lot.firstTimestamp;
				}
			}
			if (totalQty <= 0)
			{
				return null;
			}
			return new OpenPosition(itemId, name, totalQty, totalGp, firstTs);
		}
	}

	public static final class Stats
	{
		static final Stats EMPTY = new Stats(0L, 0L, 0, 0, 0, 0, 0.0, 0.0, null, null);

		public final long totalProfit;
		public final long totalGpSold;
		public final int completedFlipCount;
		public final int winCount;
		public final int lossCount;
		public final int breakEvenCount;
		public final double winRatePct;
		public final double avgRoiPct;
		public final CompletedFlip bestFlip;
		public final CompletedFlip worstFlip;

		Stats(long totalProfit, long totalGpSold, int completedFlipCount,
			int winCount, int lossCount, int breakEvenCount,
			double winRatePct, double avgRoiPct,
			CompletedFlip bestFlip, CompletedFlip worstFlip)
		{
			this.totalProfit = totalProfit;
			this.totalGpSold = totalGpSold;
			this.completedFlipCount = completedFlipCount;
			this.winCount = winCount;
			this.lossCount = lossCount;
			this.breakEvenCount = breakEvenCount;
			this.winRatePct = winRatePct;
			this.avgRoiPct = avgRoiPct;
			this.bestFlip = bestFlip;
			this.worstFlip = worstFlip;
		}

		static Stats from(List<CompletedFlip> flips)
		{
			if (flips.isEmpty())
			{
				return EMPTY;
			}
			long totalProfit = 0L;
			long totalGpSold = 0L;
			int wins = 0, losses = 0, evens = 0;
			double roiSum = 0.0;
			int roiCount = 0;
			CompletedFlip best = null;
			CompletedFlip worst = null;
			for (CompletedFlip f : flips)
			{
				totalProfit += f.profit;
				totalGpSold += f.sellTotal;
				if (f.profit > 0)
				{
					wins++;
				}
				else if (f.profit < 0)
				{
					losses++;
				}
				else
				{
					evens++;
				}
				if (f.buyTotal > 0)
				{
					roiSum += f.roiPct;
					roiCount++;
				}
				if (best == null || f.profit > best.profit)
				{
					best = f;
				}
				if (worst == null || f.profit < worst.profit)
				{
					worst = f;
				}
			}
			int n = flips.size();
			double winRate = 100.0 * wins / n;
			double avgRoi = roiCount > 0 ? roiSum / roiCount : 0.0;
			return new Stats(totalProfit, totalGpSold, n, wins, losses, evens, winRate, avgRoi, best, worst);
		}
	}

	// ── Internal mutable lot holder ─────────────────────────────────────────

	private static final class OpenLot
	{
		int qty;
		long gp;
		final long firstTimestamp;
		final String name;

		OpenLot(int qty, long gp, long firstTimestamp, String name)
		{
			this.qty = qty;
			this.gp = gp;
			this.firstTimestamp = firstTimestamp;
			this.name = name;
		}
	}
}
