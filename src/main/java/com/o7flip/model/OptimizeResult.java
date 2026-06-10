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

		// ── Redesigned-engine additions (additive; absent on older responses) ──
		/** Set only when {@link #slotsUsed} == 0. Server's reason for an empty
		 *  plan — drives the empty-state copy instead of a generic message. */
		public String  emptyReason;
		/** True when the server's trend DB query was degraded/partial, so the
		 *  ranking leaned on fallbacks. Surface as a soft caveat. */
		public boolean degradedTrendData;
		/** The per-slot wealth floor the server actually applied (the request's
		 *  {@code min_profit_pct}, or the server's auto value). Null = none. */
		public Double  minProfitPctApplied;
		/** Present when deploying more slots would help AND the user asked for
		 *  fewer than 8. Null otherwise. */
		public SlotSuggestion slotSuggestion;
		/** Per-reason counts of why candidate items were rejected. Optional —
		 *  surfaced in the empty-state / hint tooltips. Keys are the server's
		 *  reason codes (low_confidence, low_volume, low_roi, …). */
		public java.util.Map<String, Integer> eligibilityRejections = new java.util.LinkedHashMap<>();

		/** Deep copy — fresh {@link #slotSuggestion} and {@code eligibilityRejections}
		 *  map so a game-thread POST snapshot can't tear the nested mutables. */
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

	/**
	 * Server hint that the user left slots on the table. Only present when the
	 * request used fewer than 8 slots and the engine could profitably deploy
	 * more. The UI offers a one-click re-run at {@link #suggestedSlots}.
	 */
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
		/** This slot's expected cycle profit as a percent of total bank, e.g.
		 *  0.49 → "+0.49% of bank". Null on older responses. */
		public Double  profitPctOfBank;
		/** True when the slot sits below the applied per-slot wealth floor —
		 *  the server kept it but flags it as a thin contributor. */
		public Boolean belowWealthThreshold;

		// ── Live-tracking fields ─────────────────────────────────────────────
		// Empty / null on /optimize responses; populated on /optimize/active
		// responses where the slot has been tracking real GE activity.
		public java.util.List<SlotFill> buys  = new java.util.ArrayList<>();
		public java.util.List<SlotFill> sells = new java.util.ArrayList<>();
		public SlotState state                = SlotState.PENDING;
		/** True once the user hit "Stop buying" — the slot's target was capped
		 *  to whatever had already been bought. Round-trips through the shared
		 *  session ({@code partial}); the website tolerates + renders it. */
		public boolean partial;
		/** When {@link #partial}, the slot's ORIGINAL gp budget before the cap
		 *  (so freed capital can be redeployed after the partial sells). 0 when
		 *  not partial. Wire key {@code reserved_gp}. */
		public long reservedGp;
		/** The GE offer epoch this leg's fills belong to
		 *  ({@code currentTimeMillis()*10 + slot}, minted per offer by the plugin).
		 *  Set on the first fill the plugin attributes to this slot and held stable
		 *  for the flip, so the server can key legs by {@code (item_id,
		 *  offerInstanceId)} (SYNC_CONTRACT §2) instead of collapsing repeat flips
		 *  of the same item. Null until the plugin trades the slot. Wire key
		 *  {@code offer_instance_id}. */
		public Long offerInstanceId;
		/** Manual-override revision for this leg (SYNC_CONTRACT §7). Server-owned,
		 *  monotonic, default 0; round-tripped on the wire ({@code override_rev}). A
		 *  site/plugin correction bumps it and sets authoritative {@code bought}/
		 *  {@code sold}. */
		public int overrideRev;
		/** Who issued the last override — {@code "site"} or {@code "plugin"}, null
		 *  when none. Round-tripped ({@code override_source}). */
		public String overrideSource;
		/** Plugin-local high-water mark: the highest {@link #overrideRev} this client
		 *  has already adopted for the leg. Drives the §7 merge carve-out. NOT sent to
		 *  the server (re-adopting an unchanged override is idempotent); {@code transient}
		 *  so a stray Gson pass never serialises it. */
		public transient int appliedOverrideRev;
		/** Plugin-local: set when the login reconcile sees this in-progress leg's GE
		 *  offer has vanished (offline-completion signature). A non-destructive
		 *  "looks complete — confirm?" flag — it NEVER mutates counts. Cleared on
		 *  dismiss or when a server override is adopted. NOT sent to the server. */
		public transient boolean pendingOfflineReconcile;
		/** SYNC_CONTRACT §10 — true while a GE SELL offer is LISTED for this leg's
		 *  item (set the moment the offer is placed, before any sell fill lands).
		 *  Bridges the gap where a fully-bought leg sat at "Filled" until the
		 *  first sell fill: both surfaces can render "Selling — awaiting buyer".
		 *  Plugin-authoritative; cleared when the sell is cancelled with no other
		 *  live sell offer, or when the leg closes. Wire key {@code sell_listed}. */
		public boolean sellListed;

		/**
		 * Deep copy of this allocation, including fresh {@link #buys}/{@link #sells}
		 * lists of cloned {@link SlotFill}s and a cloned {@link #hourlyTrend}. Used
		 * to snapshot the live session on the game thread before an off-thread POST
		 * serialises it, so the writer never iterates a list the game thread is
		 * mutating. Copies every field (incl. the plugin-local transient ones).
		 */
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
