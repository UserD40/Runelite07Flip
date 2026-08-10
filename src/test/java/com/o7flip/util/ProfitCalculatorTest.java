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
		assertEquals(117_600L, flip.sellTotal);
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
		assertEquals(40_000L, flip.buyTotal);
		assertEquals(47_040L, flip.sellTotal);
		assertEquals(960L,   flip.tax);
		assertEquals(7_040L, flip.profit);

		ProfitCalculator.OpenPosition pos = r.openPositions.get(1);
		assertNotNull(pos);
		assertEquals(60, pos.remainingQty);
		assertEquals(60_000L, pos.remainingCostBasis);
	}

	@Test
	public void sellExceedsNewestBuy_consumesMultipleBuysLifo()
	{
		TradeRecord buy1 = trade(1, "Cannonball", true,  50,  50_000L, 1000L);
		TradeRecord buy2 = trade(1, "Cannonball", true,  50,  60_000L, 2000L);
		TradeRecord sell = trade(1, "Cannonball", false, 80, 120_000L, 3000L);

		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buy1, buy2, sell));
		assertEquals(2, r.completedFlips.size());

		ProfitCalculator.CompletedFlip f1 = r.completedFlips.get(0);
		assertEquals(50, f1.quantity);
		assertEquals(60_000L, f1.buyTotal);
		assertEquals(73_500L, f1.sellTotal);
		assertEquals(1_500L,  f1.tax);
		assertEquals(13_500L, f1.profit);
		assertEquals(2000L, f1.firstBuyTimestamp);

		ProfitCalculator.CompletedFlip f2 = r.completedFlips.get(1);
		assertEquals(30, f2.quantity);
		assertEquals(30_000L, f2.buyTotal);
		assertEquals(44_100L, f2.sellTotal);
		assertEquals(900L,    f2.tax);
		assertEquals(14_100L, f2.profit);
		assertEquals(1000L, f2.firstBuyTimestamp);

		ProfitCalculator.OpenPosition pos = r.openPositions.get(1);
		assertNotNull(pos);
		assertEquals(20, pos.remainingQty);
		assertEquals(20_000L, pos.remainingCostBasis);
	}

	@Test
	public void unsortedInput_isSortedByTimestamp()
	{
		TradeRecord sell = trade(1, "Cannonball", false, 100, 120_000L, 2000L);
		TradeRecord buy  = trade(1, "Cannonball", true,  100, 100_000L, 1000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(sell, buy));
		assertEquals(1, r.completedFlips.size());
		assertEquals(17_600L, r.completedFlips.get(0).profit);
		assertTrue(r.openPositions.isEmpty());
	}

	@Test
	public void differentItems_doNotMatch()
	{
		TradeRecord buyA  = trade(1, "A", true,  10, 10_000L, 1000L);
		TradeRecord sellB = trade(2, "B", false, 10, 12_000L, 2000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buyA, sellB));
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
		assertEquals(-600L, r.stats.totalProfit);
		assertEquals(29_400L, r.stats.totalGpSold);
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

	@Test
	public void mixedCostBasis_sellAcrossTwoBuyLots_splitsNewestFirstLifo()
	{
		TradeRecord oldBuy = trade(13239, "Primordial boots", true, 1, 21_602_010L, 1000L);
		TradeRecord newBuy = trade(13239, "Primordial boots", true, 8, 171_006_976L, 2000L);
		TradeRecord sell   = trade(13239, "Primordial boots", false, 9, 199_285_929L, 3000L);

		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(oldBuy, newBuy, sell));

		assertEquals(2, r.completedFlips.size());

		ProfitCalculator.CompletedFlip f1 = r.completedFlips.get(0);
		assertEquals(8, f1.quantity);
		assertEquals(171_006_976L, f1.buyTotal);
		assertEquals(173_600_192L, f1.sellTotal);
		assertEquals(442_857L * 8, f1.tax);
		assertEquals(173_600_192L - 171_006_976L, f1.profit);
		assertEquals(2000L, f1.firstBuyTimestamp);

		ProfitCalculator.CompletedFlip f2 = r.completedFlips.get(1);
		assertEquals(1, f2.quantity);
		assertEquals(21_602_010L, f2.buyTotal);
		assertEquals(21_700_024L, f2.sellTotal);
		assertEquals(442_857L, f2.tax);
		assertEquals(21_700_024L - 21_602_010L, f2.profit);
		assertEquals(1000L, f2.firstBuyTimestamp);

		assertEquals(2, r.stats.completedFlipCount);
		assertEquals(f1.profit + f2.profit, r.stats.totalProfit);
	}

	@Test
	public void equalTimestamp_consumesLaterListEntryFirst_lifo()
	{
		TradeRecord buyA = trade(1, "X", true, 10, 10_000L, 1000L); // 1000/item, earlier in list
		TradeRecord buyB = trade(1, "X", true, 10, 20_000L, 1000L); // 2000/item, same ts, later in list
		TradeRecord sell = trade(1, "X", false, 10, 30_000L, 2000L);

		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buyA, buyB, sell));
		assertEquals(1, r.completedFlips.size());
		ProfitCalculator.CompletedFlip f = r.completedFlips.get(0);
		assertEquals(10, f.quantity);
		assertEquals(20_000L, f.buyTotal);

		ProfitCalculator.OpenPosition pos = r.openPositions.get(1);
		assertNotNull(pos);
		assertEquals(10, pos.remainingQty);
		assertEquals(10_000L, pos.remainingCostBasis);
	}

	@Test
	public void partialBuyConsumption_preservesExactRemainder()
	{
		TradeRecord buy  = trade(1, "X", true,  7, 1000L, 1000L);
		TradeRecord sell = trade(1, "X", false, 3,  900L, 2000L);
		ProfitCalculator.Result r = ProfitCalculator.compute(Arrays.asList(buy, sell));

		assertEquals(1, r.completedFlips.size());
		ProfitCalculator.CompletedFlip flip = r.completedFlips.get(0);
		assertEquals(429L, flip.buyTotal);
		assertEquals(882L, flip.sellTotal);

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
