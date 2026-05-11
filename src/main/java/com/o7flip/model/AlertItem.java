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

/**
 * One merch alert. The new redesign collapses tier/drawdown out of the visual
 * card but keeps them on the model in case other surfaces reuse them.
 *
 * Three price points are now tracked separately:
 * <ul>
 *   <li>{@code startingPrice} — snapshot at {@code detectedAt} (the price
 *       the merch detector originally flagged)</li>
 *   <li>{@code livePrice} — fresh Wiki market price right now</li>
 *   <li>{@code achievedPrice} — the {@code sellTarget} value, set when
 *       the alert went {@code "successful"}; null while pending</li>
 * </ul>
 *
 * Realised profit is server-computed post-tax against {@code startingPrice}
 * so the plugin renders identical numbers to the website.
 */
public class AlertItem
{
	public int    itemId;
	public String name;
	public String tier;

	/** @deprecated kept for parsing back-compat — server now sends starting_price. */
	@Deprecated
	public long currentPrice;

	public long   startingPrice;
	public Long   livePrice;          // null when server has no Wiki sample yet
	public long   sellTarget;
	public double upsidePct;
	public String holdTime;
	public long   high90d;
	public long   low90d;
	public double drawdownPct;
	public String detectedAt;

	public String status;             // "pending" | "successful"
	public Long   achievedPrice;      // null when pending
	public String achievedAt;         // ISO string, null when pending
	public Long   realisedProfit;     // null when pending — post-tax gp
	public Double realisedRoiPct;     // null when pending

	/**
	 * Buy / sell price buckets for the chart. Timeframe is server-decided:
	 * older alerts get daily buckets from {@code detectedAt} to now (the
	 * since-detection journey), brand-new alerts (≤ 1 daily bucket) get the
	 * last 24h hourly as a fallback so the card always has a chart. The
	 * plugin doesn't distinguish between the two cases — both render
	 * identically through {@link com.o7flip.ui.BuySellSparkline}.
	 *
	 * Either array may contain null entries for gaps; the renderer breaks
	 * the polyline cleanly across them.
	 */
	public Long[] sparklineBuy;
	public Long[] sparklineSell;
	public String sparklineStart;
}
