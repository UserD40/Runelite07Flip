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

/**
 * Response from {@code POST /api/runelite/optimize}. Carries the per-slot
 * allocation plan plus a summary block with portfolio-level stats.
 *
 * Premium-gated server-side — free callers get a 403 with
 * {@code premium_required} which the API client surfaces separately, so
 * this DTO never has to encode the upgrade path.
 */
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
		/** Sum of {@code expected_profit} across all allocations. Single
		 *  integer gp, after tax. Cycle total, NOT per-hour. */
		public long    expectedProfitTotal;
		public Double  avgFillConfidence;
		public Double  minFillConfidence;
		public int     recommendedCount;
		public int     rawCount;
		public Integer maxFillHours;
		public Double  avgEstimatedFillHours;
		public Double  maxEstimatedFillHours;
		/** Server-supplied free-text explainers — surface in tooltips, not on cards. */
		public String  fillConfidenceFormula;
		public String  pricingNote;
		public String  compositionNote;
		public String  realismNote;
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
		/** Total profit if this slot completes one buy→sell cycle, after GE
		 *  tax. NOT a per-hour rate — server changed semantics. */
		public long   expectedProfit;
		public Double fillConfidence;
		public int    buyLimit;
		public Integer hourlyVolume;
		/** "recommended" = p25/p75 of hourly fills; "raw" = instant prices. */
		public String  priceSource;
		public Long    rawBuyPrice;
		public Long    rawSellPrice;
		public Long    rawProfitPerUnit;
		public Double  estimatedFillHours;
		public Integer realisticQtyCap;
		/** Last-24h avg_low buy prices for sparkline. Null if the DB query
		 *  failed server-side. Length matches the server's bucket count. */
		public int[]   hourlyTrend;

		// ── Live-tracking fields ─────────────────────────────────────────────
		// Empty / null on /optimize responses; populated on /optimize/active
		// responses where the slot has been tracking real GE activity.
		public java.util.List<SlotFill> buys  = new java.util.ArrayList<>();
		public java.util.List<SlotFill> sells = new java.util.ArrayList<>();
		public SlotState state                = SlotState.PENDING;
	}
}
