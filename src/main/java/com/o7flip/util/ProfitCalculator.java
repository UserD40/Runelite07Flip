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

import com.o7flip.model.Models.TradeRecord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProfitCalculator
{
	public static final int BOND_ITEM_ID = 13190;

	private static final double GE_TAX_RATE = 0.02;
	private static final long   GE_TAX_MIN_PRICE_PER_ITEM = 100L;
	private static final long   GE_TAX_CAP_PER_ITEM = 5_000_000L;

	private ProfitCalculator()
	{
	}

	public static long geTaxFor(int itemId, long grossSellTotal, int quantity)
	{
		if (itemId == BOND_ITEM_ID || quantity <= 0 || grossSellTotal <= 0)
		{
			return 0L;
		}
		long pricePerItem = grossSellTotal / quantity;
		if (pricePerItem < GE_TAX_MIN_PRICE_PER_ITEM)
		{
			return 0L;
		}
		long taxPerItem = (long) Math.floor(pricePerItem * GE_TAX_RATE);
		if (taxPerItem > GE_TAX_CAP_PER_ITEM)
		{
			taxPerItem = GE_TAX_CAP_PER_ITEM;
		}
		return taxPerItem * quantity;
	}

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
			if (trade.itemId == BOND_ITEM_ID)
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
		long sellRemainingGross = sell.totalGp;

		while (sellRemainingQty > 0 && queue != null && !queue.isEmpty())
		{
			OpenLot lot = queue.peekLast();
			int consumed = Math.min(sellRemainingQty, lot.qty);

			long consumedBuyGp;
			if (consumed == lot.qty)
			{
				consumedBuyGp = lot.gp;
				queue.pollLast();
			}
			else
			{
				consumedBuyGp = Math.round((double) lot.gp * consumed / lot.qty);
				lot.qty -= consumed;
				lot.gp -= consumedBuyGp;
			}

			long consumedSellGross;
			if (consumed == sellRemainingQty)
			{
				consumedSellGross = sellRemainingGross;
			}
			else
			{
				consumedSellGross = Math.round((double) sell.totalGp * consumed / sell.quantity);
			}

			out.add(new CompletedFlip(
				sell.itemId,
				sell.name,
				consumed,
				consumedBuyGp,
				consumedSellGross,
				lot.firstTimestamp,
				sell.timestamp
			));

			sellRemainingQty -= consumed;
			sellRemainingGross -= consumedSellGross;
		}

		if (sellRemainingQty > 0)
		{
			out.add(new CompletedFlip(
				sell.itemId,
				sell.name,
				sellRemainingQty,
				0L,
				sellRemainingGross,
				sell.timestamp,
				sell.timestamp
			));
		}
	}

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
		public final long tax;
		public final long profit;
		public final double roiPct;
		public final long firstBuyTimestamp;
		public final long sellTimestamp;

		CompletedFlip(int itemId, String name, int quantity, long buyTotal, long sellGross,
			long firstBuyTimestamp, long sellTimestamp)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.buyTotal = buyTotal;
			this.tax = geTaxFor(itemId, sellGross, quantity);
			this.sellTotal = sellGross - this.tax;
			this.profit = this.sellTotal - buyTotal;
			this.roiPct = buyTotal > 0 ? (100.0 * this.profit / buyTotal) : 0.0;
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
		static final Stats EMPTY = new Stats(0L, 0L, 0L, 0, 0, 0, 0, 0.0, 0.0, null, null);

		public final long totalProfit;
		public final long totalGpSold;
		public final long totalTaxPaid;
		public final int completedFlipCount;
		public final int winCount;
		public final int lossCount;
		public final int breakEvenCount;
		public final double winRatePct;
		public final double avgRoiPct;
		public final CompletedFlip bestFlip;
		public final CompletedFlip worstFlip;

		Stats(long totalProfit, long totalGpSold, long totalTaxPaid, int completedFlipCount,
			int winCount, int lossCount, int breakEvenCount,
			double winRatePct, double avgRoiPct,
			CompletedFlip bestFlip, CompletedFlip worstFlip)
		{
			this.totalProfit = totalProfit;
			this.totalGpSold = totalGpSold;
			this.totalTaxPaid = totalTaxPaid;
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
			long totalTaxPaid = 0L;
			int wins = 0, losses = 0, evens = 0;
			double roiSum = 0.0;
			int matchedCount = 0;
			CompletedFlip best = null;
			CompletedFlip worst = null;
			for (CompletedFlip f : flips)
			{
				if (f.buyTotal <= 0)
				{
					continue;
				}
				matchedCount++;
				totalProfit += f.profit;
				totalGpSold += f.sellTotal;
				totalTaxPaid += f.tax;
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
				roiSum += f.roiPct;
				if (best == null || f.profit > best.profit)
				{
					best = f;
				}
				if (worst == null || f.profit < worst.profit)
				{
					worst = f;
				}
			}
			if (matchedCount == 0)
			{
				return EMPTY;
			}
			double winRate = 100.0 * wins / matchedCount;
			double avgRoi  = roiSum / matchedCount;
			return new Stats(totalProfit, totalGpSold, totalTaxPaid, matchedCount, wins, losses, evens,
				winRate, avgRoi, best, worst);
		}
	}

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
