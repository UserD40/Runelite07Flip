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
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfitCalculatorTest
{
	@Test
	public void emptyList_returnsEmptyResult()
	{
		ProfitCalculator.Result r = ProfitCalculator.compute(Collections.emptyList());
		assertTrue(r.completedFlips.isEmpty());
		assertTrue(r.openPositions.isEmpty());
		assertEquals(0L, r.stats.totalProfit);
		assertEquals(0, r.stats.completedFlipCount);
		assertNull(r.stats.bestFlip);
		assertNull(r.stats.worstFlip);
	}

	@Test
	public void nullList_returnsEmptyResult()
	{
		ProfitCalculator.Result r = ProfitCalculator.compute(null);
		assertTrue(r.completedFlips.isEmpty());
		assertTrue(r.openPositions.isEmpty());
	}

	@Test
	public void buyWithoutSell_producesOpenPosition()
	{
		TradeRecord buy = trade(1, "Cannonball", true, 100, 100_000L, 1000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Collections.singletonList(buy));
		assertTrue(r.completedFlips.isEmpty());
		assertEquals(1, r.openPositions.size());
		ProfitCalculator.OpenPosition pos = r.openPositions.get(1);
		assertNotNull(pos);
		assertEquals(100, pos.remainingQty);
		assertEquals(100_000L, pos.remainingCostBasis);
		assertEquals(1000L, pos.earliestBuyTimestamp);
	}

	@Test
	public void sellWithoutBuy_producesPhantomFlip()
	{
		// Sell 100 @ 1000 gp/item → 2% tax = 20 gp/item × 100 = 2000.
		// Phantom profit still nets the tax (consistent with matched flips).
		TradeRecord sell = trade(1, "Cannonball", false, 100, 100_000L, 1000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Collections.singletonList(sell));
		assertEquals(1, r.completedFlips.size());
		ProfitCalculator.CompletedFlip flip = r.completedFlips.get(0);
		assertEquals(0L, flip.buyTotal);
		// sellTotal is net of GE tax: 100_000 gross − 2_000 tax = 98_000.
		assertEquals(98_000L, flip.sellTotal);
		assertEquals(2_000L, flip.tax);
		assertEquals(98_000L, flip.profit);
		assertEquals(0.0, flip.roiPct, 0.001);
	}

	@Test
	public void exactMatch_buyThenSell_oneFlip()
	{
		TradeRecord buy  = trade(1, "Cannonball", true,  100, 100_000L, 1000L);
		TradeRecord sell = trade(1, "Cannonball", false, 100, 120_000L, 2000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buy, sell));
		assertEquals(1, r.completedFlips.size());
		ProfitCalculator.CompletedFlip flip = r.completedFlips.get(0);
		assertEquals(1, flip.itemId);
		assertEquals(100, flip.quantity);
		assertEquals(100_000L, flip.buyTotal);
		// sellTotal is net of GE tax: 120_000 gross − 2_400 tax = 117_600.
		assertEquals(117_600L, flip.sellTotal);
		// 2% GE tax on 1200 gp/item = 24 gp/item × 100 = 2400 gp.
		// Profit is reported NET of tax: 120_000 - 2400 - 100_000 = 17_600.
		assertEquals(2_400L,  flip.tax);
		assertEquals(17_600L, flip.profit);
		assertEquals(17.6, flip.roiPct, 0.01);
		assertEquals(1000L, flip.firstBuyTimestamp);
		assertEquals(2000L, flip.sellTimestamp);
		assertTrue(r.openPositions.isEmpty());
	}

	@Test
	public void partialSell_leavesOpenPosition()
	{
		TradeRecord buy  = trade(1, "Cannonball", true,  100, 100_000L, 1000L);
		TradeRecord sell = trade(1, "Cannonball", false,  40,  48_000L, 2000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buy, sell));
		assertEquals(1, r.completedFlips.size());
		ProfitCalculator.CompletedFlip flip = r.completedFlips.get(0);
		assertEquals(40, flip.quantity);
		assertEquals(40_000L, flip.buyTotal);  // 100_000 * 40 / 100
		// sellTotal is net of GE tax: 48_000 gross − 960 tax = 47_040.
		assertEquals(47_040L, flip.sellTotal);
		// 2% tax on 1200 gp/item = 24 gp/item × 40 = 960 gp.
		// Net profit: 48_000 - 960 - 40_000 = 7_040.
		assertEquals(960L,   flip.tax);
		assertEquals(7_040L, flip.profit);

		ProfitCalculator.OpenPosition pos = r.openPositions.get(1);
		assertNotNull(pos);
		assertEquals(60, pos.remainingQty);
		assertEquals(60_000L, pos.remainingCostBasis);
	}

	@Test
	public void sellExceedsFirstBuy_consumesMultipleBuysFifo()
	{
		// Buy 50 @ 1000gp each = 50,000
		TradeRecord buy1 = trade(1, "Cannonball", true,  50,  50_000L, 1000L);
		// Buy 50 @ 1200gp each = 60,000
		TradeRecord buy2 = trade(1, "Cannonball", true,  50,  60_000L, 2000L);
		// Sell 80 @ 1500gp each = 120,000
		TradeRecord sell = trade(1, "Cannonball", false, 80, 120_000L, 3000L);

		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buy1, buy2, sell));
		assertEquals(2, r.completedFlips.size());

		// First flip: 50 from buy1. Sell price 1500 gp/item → tax 30 gp × 50 = 1500.
		// Net profit: 75_000 - 1500 - 50_000 = 23_500.
		ProfitCalculator.CompletedFlip f1 = r.completedFlips.get(0);
		assertEquals(50, f1.quantity);
		assertEquals(50_000L, f1.buyTotal);
		// sellTotal is net of GE tax: 75_000 gross − 1_500 tax = 73_500.
		assertEquals(73_500L, f1.sellTotal);
		assertEquals(1_500L,  f1.tax);
		assertEquals(23_500L, f1.profit);
		assertEquals(1000L, f1.firstBuyTimestamp);

		// Second flip: 30 from buy2 (remainder of sell). Tax 30 gp × 30 = 900.
		// Net profit: 45_000 - 900 - 36_000 = 8_100.
		ProfitCalculator.CompletedFlip f2 = r.completedFlips.get(1);
		assertEquals(30, f2.quantity);
		assertEquals(36_000L, f2.buyTotal);
		// sellTotal is net of GE tax: 45_000 gross − 900 tax = 44_100.
		assertEquals(44_100L, f2.sellTotal);
		assertEquals(900L,    f2.tax);
		assertEquals(8_100L,  f2.profit);
		assertEquals(2000L, f2.firstBuyTimestamp);

		// Open position: 20 from buy2 remaining
		ProfitCalculator.OpenPosition pos = r.openPositions.get(1);
		assertNotNull(pos);
		assertEquals(20, pos.remainingQty);
		assertEquals(24_000L, pos.remainingCostBasis); // 60_000 - 36_000
	}

	@Test
	public void unsortedInput_isSortedByTimestamp()
	{
		// Pass sell first; calculator should sort by timestamp before processing.
		TradeRecord sell = trade(1, "Cannonball", false, 100, 120_000L, 2000L);
		TradeRecord buy  = trade(1, "Cannonball", true,  100, 100_000L, 1000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(sell, buy));
		assertEquals(1, r.completedFlips.size());
		// Same scenario as exactMatch_buyThenSell_oneFlip — net of 2% tax.
		assertEquals(17_600L, r.completedFlips.get(0).profit);
		assertTrue(r.openPositions.isEmpty());
	}

	@Test
	public void differentItems_doNotMatch()
	{
		TradeRecord buyA  = trade(1, "A", true,  10, 10_000L, 1000L);
		TradeRecord sellB = trade(2, "B", false, 10, 12_000L, 2000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buyA, sellB));
		// Sell of B has no buy → phantom; A buy is still open.
		assertEquals(1, r.completedFlips.size());
		assertEquals(0L, r.completedFlips.get(0).buyTotal);
		assertEquals(2, r.completedFlips.get(0).itemId);
		assertNotNull(r.openPositions.get(1));
	}

	@Test
	public void stats_winLossBreakeven_classifiedCorrectly()
	{
		// Sell prices below 100 gp/item are tax-exempt — picking 1000+ gp/item
		// keeps the tax math interesting and exercises the post-tax counters.
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(
			trade(1, "A", true,  10, 10_000L, 1000L),
			trade(1, "A", false, 10, 12_000L, 1100L), // sell 1200/item → tax 240, profit +1760 (win)
			trade(2, "B", true,  10, 10_000L, 2000L),
			trade(2, "B", false, 10,  8_000L, 2100L), // sell 800/item → tax 160, profit -2160 (loss)
			trade(3, "C", true,  10, 10_000L, 3000L),
			trade(3, "C", false, 10, 10_000L, 3100L)  // sell 1000/item → tax 200, profit -200 (still a loss after tax)
		));
		assertEquals(3, r.stats.completedFlipCount);
		assertEquals(1, r.stats.winCount);
		assertEquals(2, r.stats.lossCount);
		assertEquals(0, r.stats.breakEvenCount);
		assertEquals(33.33, r.stats.winRatePct, 0.1);
		// 1760 - 2160 - 200 = -600 (net of GE tax)
		assertEquals(-600L, r.stats.totalProfit);
		// net gp received (post-tax): (12_000−240)+(8_000−160)+(10_000−200)
		// = 11_760 + 7_840 + 9_800 = 29_400.
		assertEquals(29_400L, r.stats.totalGpSold);
		// total tax = 240 + 160 + 200 = 600
		assertEquals(600L, r.stats.totalTaxPaid);
	}

	@Test
	public void stats_bestAndWorstFlip_identified()
	{
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(
			trade(1, "A", true,  10, 10_000L, 1000L),
			trade(1, "A", false, 10, 15_000L, 1100L), // sell 1500/item, tax 300, profit +4700
			trade(2, "B", true,  10, 10_000L, 2000L),
			trade(2, "B", false, 10,  7_000L, 2100L)  // sell 700/item, tax 140, profit -3140
		));
		assertNotNull(r.stats.bestFlip);
		assertEquals(1, r.stats.bestFlip.itemId);
		assertEquals(4_700L, r.stats.bestFlip.profit);

		assertNotNull(r.stats.worstFlip);
		assertEquals(2, r.stats.worstFlip.itemId);
		assertEquals(-3_140L, r.stats.worstFlip.profit);
	}

	@Test
	public void stats_excludePhantomFlips()
	{
		// One real flip plus one phantom (sell without matched buy in
		// tracked history). Phantoms must be excluded from every stat —
		// counting them would make totalProfit, win-rate, best/worst, and
		// the flip count all misleading whenever the user has trades older
		// than the plugin's history (e.g. items bought before installing).
		// Buy 10 @ 1000 / sell 10 @ 1200; tax = 24/item × 10 = 240 → profit 1760.
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(
			trade(1, "A", true,  10, 10_000L, 1000L),
			trade(1, "A", false, 10, 12_000L, 1100L),
			trade(2, "B", false, 10, 50_000L, 2000L)  // phantom, buyTotal=0
		));
		assertEquals(2, r.completedFlips.size());
		assertEquals(1, r.stats.completedFlipCount);
		assertEquals(1_760L, r.stats.totalProfit);
		assertEquals(1, r.stats.winCount);
		assertEquals(17.6, r.stats.avgRoiPct, 0.01);
		assertEquals(1, r.stats.bestFlip.itemId);
	}

	@Test
	public void zeroQuantityTrade_skipped()
	{
		TradeRecord weird = trade(1, "X", true, 0, 0L, 1000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Collections.singletonList(weird));
		assertTrue(r.completedFlips.isEmpty());
		assertTrue(r.openPositions.isEmpty());
	}

	/**
	 * Combined-stock flip with two cost bases. User had 1 leftover from a
	 * 5-11 buy (cost basis 21.6M ea) plus a freshly-bought 8 at 21.38M ea,
	 * then sold all 9 together at 22.14M ea. FIFO must split into TWO
	 * matched flips — the old leftover priced against its 21.6M basis and
	 * the new 8 priced against their 21.38M basis — instead of a blended
	 * average. Each slice reports its own profit and contributes
	 * separately to win/loss counts.
	 */
	@Test
	public void mixedCostBasis_sellAcrossTwoBuyLots_splitsIntoTwoFlips()
	{
		TradeRecord oldBuy = trade(13239, "Primordial boots", true, 1, 21_602_010L, 1000L);
		TradeRecord newBuy = trade(13239, "Primordial boots", true, 8, 171_006_976L, 2000L);
		TradeRecord sell   = trade(13239, "Primordial boots", false, 9, 199_285_929L, 3000L);

		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(oldBuy, newBuy, sell));

		// Two matched flips — one per buy lot.
		assertEquals(2, r.completedFlips.size());

		// First slice: 1 item from the old lot.
		ProfitCalculator.CompletedFlip f1 = r.completedFlips.get(0);
		assertEquals(1, f1.quantity);
		assertEquals(21_602_010L, f1.buyTotal);
		// sellTotal slice net of GE tax: round(199_285_929 * 1/9) = 22_142_881
		// gross, − 442_857 tax = 21_700_024.
		assertEquals(21_700_024L, f1.sellTotal);
		// 2% per-item tax on 22_142_881 → 442_857
		assertEquals(442_857L, f1.tax);
		assertEquals(22_142_881L - 442_857L - 21_602_010L, f1.profit);

		// Second slice: 8 items from the new lot, taking the remainder.
		ProfitCalculator.CompletedFlip f2 = r.completedFlips.get(1);
		assertEquals(8, f2.quantity);
		assertEquals(171_006_976L, f2.buyTotal);
		// sellTotal net of GE tax: (199_285_929 − 22_142_881) gross 177_143_048
		// − (442_857 × 8) tax 3_542_856 = 173_600_192.
		assertEquals(199_285_929L - 22_142_881L - 442_857L * 8, f2.sellTotal); // 173_600_192
		assertEquals(442_857L * 8, f2.tax);
		// profit = net sellTotal − buyTotal (tax already removed from sellTotal).
		assertEquals(f2.sellTotal - f2.buyTotal, f2.profit);

		// Combined profit + matched count exposed via stats.
		assertEquals(2, r.stats.completedFlipCount);
		assertEquals(f1.profit + f2.profit, r.stats.totalProfit);
	}

	@Test
	public void partialBuyConsumption_preservesExactRemainder()
	{
		// Buy 7 items for 1000gp total (142.857... per unit, doesn't divide cleanly).
		// Sell 3 of them — round-trip the remaining 4 lot must preserve buyTotal=1000.
		TradeRecord buy  = trade(1, "X", true,  7, 1000L, 1000L);
		TradeRecord sell = trade(1, "X", false, 3,  900L, 2000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buy, sell));

		assertEquals(1, r.completedFlips.size());
		ProfitCalculator.CompletedFlip flip = r.completedFlips.get(0);
		// Math.round(1000 * 3 / 7.0) = Math.round(428.571) = 429
		assertEquals(429L, flip.buyTotal);
		// sellTotal net of GE tax: sell 3 @ 300/item = 900 gross; per-item tax
		// floor(300 * 0.02) = 6 → 18 total; net = 882.
		assertEquals(882L, flip.sellTotal);

		// Remaining lot must hold exactly 1000 - 429 = 571 gp for 4 items.
		ProfitCalculator.OpenPosition pos = r.openPositions.get(1);
		assertEquals(4, pos.remainingQty);
		assertEquals(571L, pos.remainingCostBasis);
	}

	private static TradeRecord trade(int itemId, String name, boolean isBuy, int qty, long totalGp, long ts)
	{
		TradeRecord t = new TradeRecord();
		t.itemId = itemId;
		t.name = name;
		t.isBuy = isBuy;
		t.quantity = qty;
		t.totalGp = totalGp;
		t.priceEach = qty > 0 ? totalGp / qty : 0;
		t.timestamp = ts;
		t.partial = false;
		return t;
	}
}
