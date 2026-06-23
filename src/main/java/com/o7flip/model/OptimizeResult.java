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
package com.o7flip.model;

import java.util.ArrayList;
import java.util.List;

public class OptimizeResult
{
	public Summary summary = new Summary();
	public List<Allocation> allocations = new ArrayList<>();
	public String updatedAt;

	public static class Summary
	{
		public long    capitalInput;
		public long    capitalDeployed;
		public long    capitalUnused;
		public int     slotsUsed;
		public int     slotsRequested;
		public String  risk;            // "low" | "medium" | "high"
		public Boolean members;         // null = both, true = members, false = F2P
		public long    expectedProfitTotal;
		public Double  avgFillConfidence;
		public Double  minFillConfidence;
		public int     recommendedCount;
		public int     rawCount;
		public Integer maxFillHours;
		public Double  avgEstimatedFillHours;
		public Double  maxEstimatedFillHours;
		public String  fillConfidenceFormula;
		public String  pricingNote;
		public String  compositionNote;
		public String  realismNote;

		public String  emptyReason;
		public boolean degradedTrendData;
		public Double  minProfitPctApplied;
		public SlotSuggestion slotSuggestion;
		public java.util.Map<String, Integer> eligibilityRejections = new java.util.LinkedHashMap<>();

		public Summary copy()
		{
			Summary c = new Summary();
			c.capitalInput          = capitalInput;
			c.capitalDeployed       = capitalDeployed;
			c.capitalUnused         = capitalUnused;
			c.slotsUsed             = slotsUsed;
			c.slotsRequested        = slotsRequested;
			c.risk                  = risk;
			c.members               = members;
			c.expectedProfitTotal   = expectedProfitTotal;
			c.avgFillConfidence     = avgFillConfidence;
			c.minFillConfidence     = minFillConfidence;
			c.recommendedCount      = recommendedCount;
			c.rawCount              = rawCount;
			c.maxFillHours          = maxFillHours;
			c.avgEstimatedFillHours = avgEstimatedFillHours;
			c.maxEstimatedFillHours = maxEstimatedFillHours;
			c.fillConfidenceFormula = fillConfidenceFormula;
			c.pricingNote           = pricingNote;
			c.compositionNote       = compositionNote;
			c.realismNote           = realismNote;
			c.emptyReason           = emptyReason;
			c.degradedTrendData     = degradedTrendData;
			c.minProfitPctApplied   = minProfitPctApplied;
			c.slotSuggestion        = slotSuggestion != null ? slotSuggestion.copy() : null;
			c.eligibilityRejections = eligibilityRejections != null
				? new java.util.LinkedHashMap<>(eligibilityRejections)
				: new java.util.LinkedHashMap<>();
			return c;
		}
	}

	public static class SlotSuggestion
	{
		public int  suggestedSlots;
		public long additionalCapitalDeployed;
		public long additionalExpectedProfit;

		public SlotSuggestion copy()
		{
			SlotSuggestion c = new SlotSuggestion();
			c.suggestedSlots            = suggestedSlots;
			c.additionalCapitalDeployed = additionalCapitalDeployed;
			c.additionalExpectedProfit  = additionalExpectedProfit;
			return c;
		}
	}

	public static class Allocation
	{
		public int    itemId;
		public String name;
		public int    qty;
		public long   gpAllocated;
		public long   buyPrice;
		public long   sellPrice;
		public long   profitPerUnit;
		public long   expectedProfit;
		public Double fillConfidence;
		public int    buyLimit;
		public Integer hourlyVolume;
		public String  priceSource;
		public Long    rawBuyPrice;
		public Long    rawSellPrice;
		public Long    rawProfitPerUnit;
		public Double  estimatedFillHours;
		public Integer realisticQtyCap;
		public int[]   hourlyTrend;
		public Double  profitPctOfBank;
		public Boolean belowWealthThreshold;

		public java.util.List<SlotFill> buys  = new java.util.ArrayList<>();
		public java.util.List<SlotFill> sells = new java.util.ArrayList<>();
		public SlotState state                = SlotState.PENDING;
		public boolean partial;
		public long reservedGp;
		public Long offerInstanceId;
		public int overrideRev;
		public String overrideSource;
		public transient int appliedOverrideRev;
		public transient boolean pendingOfflineReconcile;
		public boolean sellListed;

		public Allocation copy()
		{
			Allocation c = new Allocation();
			c.itemId               = itemId;
			c.name                 = name;
			c.qty                  = qty;
			c.gpAllocated          = gpAllocated;
			c.buyPrice             = buyPrice;
			c.sellPrice            = sellPrice;
			c.profitPerUnit        = profitPerUnit;
			c.expectedProfit       = expectedProfit;
			c.fillConfidence       = fillConfidence;
			c.buyLimit             = buyLimit;
			c.hourlyVolume         = hourlyVolume;
			c.priceSource          = priceSource;
			c.rawBuyPrice          = rawBuyPrice;
			c.rawSellPrice         = rawSellPrice;
			c.rawProfitPerUnit     = rawProfitPerUnit;
			c.estimatedFillHours   = estimatedFillHours;
			c.realisticQtyCap      = realisticQtyCap;
			c.hourlyTrend          = hourlyTrend != null ? hourlyTrend.clone() : null;
			c.profitPctOfBank      = profitPctOfBank;
			c.belowWealthThreshold = belowWealthThreshold;
			c.state                = state;
			c.partial              = partial;
			c.reservedGp           = reservedGp;
			c.offerInstanceId      = offerInstanceId;
			c.overrideRev          = overrideRev;
			c.overrideSource       = overrideSource;
			c.appliedOverrideRev   = appliedOverrideRev;
			c.pendingOfflineReconcile = pendingOfflineReconcile;
			c.sellListed           = sellListed;
			c.buys  = new ArrayList<>();
			for (SlotFill f : buys)  if (f != null) c.buys.add(f.copy());
			c.sells = new ArrayList<>();
			for (SlotFill f : sells) if (f != null) c.sells.add(f.copy());
			return c;
		}
	}
}
