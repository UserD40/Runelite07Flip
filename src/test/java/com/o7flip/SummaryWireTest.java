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

import com.o7flip.model.OptimizeResult;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SummaryWireTest
{
	private final O7FlipApiClient client = new O7FlipApiClient();

	@Test
	public void summaryRoundTrip_preservesFields()
	{
		OptimizeResult.Summary s = new OptimizeResult.Summary();
		s.capitalInput          = 60_000_000L;
		s.capitalDeployed       = 54_700_000L;
		s.capitalUnused         = 5_300_000L;
		s.slotsUsed             = 4;
		s.slotsRequested        = 4;
		s.risk                  = "high";
		s.members               = Boolean.TRUE;
		s.expectedProfitTotal   = 1_440_000L;
		s.avgFillConfidence     = 0.8;
		s.minFillConfidence     = 0.5;
		s.recommendedCount      = 3;
		s.rawCount              = 1;
		s.maxFillHours          = 4;
		s.avgEstimatedFillHours = 2.0;
		s.maxEstimatedFillHours = 3.0;
		s.fillConfidenceFormula = "f";
		s.pricingNote           = "p";
		s.compositionNote       = "c";
		s.realismNote           = "r";
		s.emptyReason           = "er";
		s.degradedTrendData     = true;
		s.minProfitPctApplied   = 0.49;
		OptimizeResult.SlotSuggestion sug = new OptimizeResult.SlotSuggestion();
		sug.suggestedSlots            = 6;
		sug.additionalCapitalDeployed = 1_000_000L;
		sug.additionalExpectedProfit  = 50_000L;
		s.slotSuggestion = sug;
		s.eligibilityRejections.put("low_volume", 3);

		OptimizeResult.Summary r = client.parseSummary(client.summaryToJson(s));

		assertEquals(60_000_000L, r.capitalInput);
		assertEquals(54_700_000L, r.capitalDeployed);
		assertEquals(5_300_000L, r.capitalUnused);
		assertEquals(4, r.slotsUsed);
		assertEquals(4, r.slotsRequested);
		assertEquals("high", r.risk);
		assertEquals(Boolean.TRUE, r.members);
		assertEquals(1_440_000L, r.expectedProfitTotal);
		assertEquals(Double.valueOf(0.8), r.avgFillConfidence);
		assertEquals(Double.valueOf(0.5), r.minFillConfidence);
		assertEquals(3, r.recommendedCount);
		assertEquals(1, r.rawCount);
		assertEquals(Integer.valueOf(4), r.maxFillHours);
		assertEquals(Double.valueOf(2.0), r.avgEstimatedFillHours);
		assertEquals(Double.valueOf(3.0), r.maxEstimatedFillHours);
		assertEquals("f", r.fillConfidenceFormula);
		assertEquals("p", r.pricingNote);
		assertEquals("c", r.compositionNote);
		assertEquals("r", r.realismNote);
		assertEquals("er", r.emptyReason);
		assertTrue(r.degradedTrendData);
		assertEquals(Double.valueOf(0.49), r.minProfitPctApplied);
		assertNotNull(r.slotSuggestion);
		assertEquals(6, r.slotSuggestion.suggestedSlots);
		assertEquals(1_000_000L, r.slotSuggestion.additionalCapitalDeployed);
		assertEquals(50_000L, r.slotSuggestion.additionalExpectedProfit);
		assertEquals(Integer.valueOf(3), r.eligibilityRejections.get("low_volume"));
	}

	@Test
	public void summaryCopy_isDeepAndIndependent()
	{
		OptimizeResult.Summary s = new OptimizeResult.Summary();
		OptimizeResult.SlotSuggestion sug = new OptimizeResult.SlotSuggestion();
		sug.suggestedSlots = 6;
		s.slotSuggestion = sug;
		s.eligibilityRejections.put("x", 1);

		OptimizeResult.Summary c = s.copy();
		assertNotSame(s.slotSuggestion, c.slotSuggestion);
		assertNotSame(s.eligibilityRejections, c.eligibilityRejections);

		s.slotSuggestion.suggestedSlots = 99;
		s.eligibilityRejections.put("y", 2);
		assertEquals(6, c.slotSuggestion.suggestedSlots);
		assertEquals(1, c.eligibilityRejections.size());
	}
}
