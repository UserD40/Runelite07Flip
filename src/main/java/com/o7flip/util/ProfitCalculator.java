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
	/**
	 * Old school bond. Skipped entirely by the FIFO loop — bonds aren't
	 * flips, they're a one-way membership purchase. The lifetime
	 * gp/count tally backing the panel's "Membership cost" row lives in
	 * {@link com.o7flip.util.BondLedger}, persisted by O7FlipPlugin
	 * outside the 200-row trade-history sliding window so it survives a
	 * year of heavy flipping. ProfitCalculator just leaves bond rows alone:
	 * no completed flips, no open positions, no contribution to totalProfit
	 * or win-rate. A bond that does get sold back to GE is also skipped
	 * here; the ledger handles the decrement.
	 */
	public static final int BOND_ITEM_ID = 13190;

	// ── GE tax constants ────────────────────────────────────────────────────
	/** OSRS GE sells 2% sales tax. Buyers pay no tax. */
	private static final double GE_TAX_RATE = 0.02;
	/** Items selling for less than 100 gp/item are exempt from the GE tax. */
	private static final long   GE_TAX_MIN_PRICE_PER_ITEM = 100L;
	/** Tax is capped at 5,000,000 gp per individual item sold. */
	private static final long   GE_TAX_CAP_PER_ITEM = 5_000_000L;

	private ProfitCalculator()
	{
	}

	/**
	 * Computes the OSRS GE sales tax that would have been deducted from a sell
	 * offer. Mirrors the in-game rules:
	 * <ul>
	 *   <li>Sells only — buys pay no tax.</li>
	 *   <li>2% of the per-item sale price, rounded down per item.</li>
	 *   <li>No tax on items selling for less than 100 gp.</li>
	 *   <li>Capped at 5,000,000 gp per individual item.</li>
	 *   <li>Bonds ({@link #BOND_ITEM_ID}) are tax-exempt.</li>
	 * </ul>
	 *
	 * @param itemId    item id of the sell
	 * @param sellTotal gross sale value (price × qty, before tax) — this is
	 *                  what {@code GrandExchangeOffer.getSpent()} reports for
	 *                  a completed sell offer
	 * @param quantity  number of items sold
	 * @return total gp deducted as tax across the whole offer
	 */
	public static long geTaxFor(int itemId, long sellTotal, int quantity)
	{
		if (itemId == BOND_ITEM_ID || quantity <= 0 || sellTotal <= 0)
		{
			return 0L;
		}
		long pricePerItem = sellTotal / quantity;
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
			// Bonds are tracked by BondLedger, not as flips. Skip them
			// entirely — no completed flips, no open positions, no
			// pollution of totalProfit/winRate by membership purchases.
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
		/** Total gp paid for the buy leg(s), FIFO-matched. */
		public final long buyTotal;
		/** Gross sale value — what the buyer paid, BEFORE the GE took its 2% tax. */
		public final long sellTotal;
		/** GE sales tax deducted from this sell (always {@code >= 0}). */
		public final long tax;
		/** Net realised profit — {@code sellTotal - tax - buyTotal}. */
		public final long profit;
		/** ROI computed on net profit over buy cost. */
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
			this.tax = geTaxFor(itemId, sellTotal, quantity);
			long netSell = sellTotal - this.tax;
			this.profit = netSell - buyTotal;
			this.roiPct = buyTotal > 0 ? (100.0 * (netSell - buyTotal) / buyTotal) : 0.0;
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

		/** Net realised profit summed across matched flips (sellTotal − tax − buyTotal each). */
		public final long totalProfit;
		/** Gross gp sold (pre-tax) summed across matched flips. */
		public final long totalGpSold;
		/** Total GE tax paid across matched flips — the authoritative tax figure shown in the stats panel. */
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
				// Phantom flips (sells with no matching buy in tracked history)
				// are excluded from every aggregate stat. Including their gross
				// proceeds inflates totalProfit dramatically when the underlying
				// buy was made before the plugin started recording, which is
				// exactly when phantom flips occur. The flip itself still
				// appears in the completedFlips list so it shows in trade
				// history; it just doesn't pollute the summary numbers.
				if (f.buyTotal <= 0)
				{
					continue;
				}
				matchedCount++;
				totalProfit += f.profit;     // f.profit is NET (post-tax)
				totalGpSold += f.sellTotal;  // gross sale value
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
