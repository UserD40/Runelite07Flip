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
package com.o7flip;

import com.o7flip.model.Models.ItemInsights;
import com.o7flip.util.ProfitCalculator;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferTierRiskTest
{
	private static final int BLOWPIPE = 12926;
	private static final long PAID = 9_670_428L;

	private static ItemInsights insights(long buyPrice, long sellPrice, Long recSell)
	{
		ItemInsights ins = new ItemInsights();
		ItemInsights.Current c = new ItemInsights.Current();
		c.buyPrice = buyPrice;
		c.sellPrice = sellPrice;
		c.recSell = recSell;
		ins.current = c;
		return ins;
	}

	private static boolean underwater(long price)
	{
		return price - ProfitCalculator.geTaxFor(BLOWPIPE, price, 1) < PAID;
	}

	@Test
	public void priceDistanceAloneCallsTheLosingOfferCompetitive()
	{
		long benchmark = O7FlipPlugin.offerBenchmark(insights(9_480_000L, 9_500_000L, 9_694_052L), false);
		assertEquals(9_694_052L, benchmark);
		assertEquals(0, O7FlipPlugin.competitiveTier((9_699_999L - benchmark) / (double) benchmark));
	}

	@Test
	public void offerThatFillsButLosesGpCountsAsUnderwater()
	{
		assertTrue(underwater(9_699_999L));
	}

	@Test
	public void breakEvenSellIsNotUnderwater()
	{
		assertFalse(underwater(9_867_783L));
		assertTrue(underwater(9_867_782L));
	}

	@Test
	public void benchmarkFallsBackToLiveWhenNoRecommendation()
	{
		assertEquals(9_500_000L, O7FlipPlugin.offerBenchmark(insights(9_480_000L, 9_500_000L, null), false));
		assertEquals(-1L, O7FlipPlugin.offerBenchmark(null, false));
	}
}
