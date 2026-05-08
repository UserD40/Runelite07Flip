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
		TradeRecord sell = trade(1, "Cannonball", false, 100, 100_000L, 1000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Collections.singletonList(sell));
		assertEquals(1, r.completedFlips.size());
		ProfitCalculator.CompletedFlip flip = r.completedFlips.get(0);
		assertEquals(0L, flip.buyTotal);
		assertEquals(100_000L, flip.sellTotal);
		assertEquals(100_000L, flip.profit);
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
		assertEquals(120_000L, flip.sellTotal);
		assertEquals(20_000L, flip.profit);
		assertEquals(20.0, flip.roiPct, 0.01);
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
		assertEquals(48_000L, flip.sellTotal);
		assertEquals(8_000L, flip.profit);

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

		// First flip: 50 from buy1
		ProfitCalculator.CompletedFlip f1 = r.completedFlips.get(0);
		assertEquals(50, f1.quantity);
		assertEquals(50_000L, f1.buyTotal);
		// sellGp portion: 120_000 * 50 / 80 = 75_000
		assertEquals(75_000L, f1.sellTotal);
		assertEquals(25_000L, f1.profit);
		assertEquals(1000L, f1.firstBuyTimestamp);

		// Second flip: 30 from buy2 (remainder of sell)
		ProfitCalculator.CompletedFlip f2 = r.completedFlips.get(1);
		assertEquals(30, f2.quantity);
		assertEquals(36_000L, f2.buyTotal); // 60_000 * 30 / 50
		// sellGp remainder: 120_000 - 75_000 = 45_000
		assertEquals(45_000L, f2.sellTotal);
		assertEquals(9_000L, f2.profit);
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
		assertEquals(20_000L, r.completedFlips.get(0).profit);
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
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(
			trade(1, "A", true,  10, 10_000L, 1000L),
			trade(1, "A", false, 10, 12_000L, 1100L), // +2000 win
			trade(2, "B", true,  10, 10_000L, 2000L),
			trade(2, "B", false, 10,  8_000L, 2100L), // -2000 loss
			trade(3, "C", true,  10, 10_000L, 3000L),
			trade(3, "C", false, 10, 10_000L, 3100L)  // 0 breakeven
		));
		assertEquals(3, r.stats.completedFlipCount);
		assertEquals(1, r.stats.winCount);
		assertEquals(1, r.stats.lossCount);
		assertEquals(1, r.stats.breakEvenCount);
		assertEquals(33.33, r.stats.winRatePct, 0.1);
		// total profit: +2000 - 2000 + 0 = 0
		assertEquals(0L, r.stats.totalProfit);
		// total gp sold: 12000 + 8000 + 10000 = 30000
		assertEquals(30_000L, r.stats.totalGpSold);
	}

	@Test
	public void stats_bestAndWorstFlip_identified()
	{
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(
			trade(1, "A", true,  10, 10_000L, 1000L),
			trade(1, "A", false, 10, 15_000L, 1100L), // +5000
			trade(2, "B", true,  10, 10_000L, 2000L),
			trade(2, "B", false, 10,  7_000L, 2100L)  // -3000
		));
		assertNotNull(r.stats.bestFlip);
		assertEquals(1, r.stats.bestFlip.itemId);
		assertEquals(5_000L, r.stats.bestFlip.profit);

		assertNotNull(r.stats.worstFlip);
		assertEquals(2, r.stats.worstFlip.itemId);
		assertEquals(-3_000L, r.stats.worstFlip.profit);
	}

	@Test
	public void avgRoi_excludesPhantomFlips()
	{
		// One real flip @ +20% ROI, one phantom (no buy basis) — avgRoi should be 20% not 10%.
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(
			trade(1, "A", true,  10, 10_000L, 1000L),
			trade(1, "A", false, 10, 12_000L, 1100L), // +2000, +20% ROI
			trade(2, "B", false, 10, 50_000L, 2000L)  // phantom, buyTotal=0, ROI undefined → excluded
		));
		assertEquals(2, r.stats.completedFlipCount);
		assertEquals(20.0, r.stats.avgRoiPct, 0.01);
	}

	@Test
	public void zeroQuantityTrade_skipped()
	{
		TradeRecord weird = trade(1, "X", true, 0, 0L, 1000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Collections.singletonList(weird));
		assertTrue(r.completedFlips.isEmpty());
		assertTrue(r.openPositions.isEmpty());
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
		assertEquals(900L, flip.sellTotal);

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
