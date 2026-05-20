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

public class DumpItem
{
	/**
	 * v5 tier classification — "confirmed" for rows with structural automation
	 * evidence (10k daily-volume floor), "likely" for strong-pattern rows that
	 * couldn't be proved bot-driven (PvM drops with off-clock peaks, etc.).
	 * Null on legacy responses; the UI treats null as "likely" with a fallback
	 * badge — but server v5 always populates it.
	 */
	public String  tier;

	public int     itemId;
	public String  name;
	public long    buyPrice;
	public long    sellPrice;
	public long    profit;
	public int     dumpScore;
	public double  dumpPct;
	public String  dumpStatus;
	public Double  lastDumpHoursAgo;
	public Double  nextDumpHours;
	public Integer burstCount;
	public int     hourlyVolume;
	public int     buyLimit;
	public boolean members;

	// ── v3 dump-engine fields ───────────────────────────────────────────────
	// Added with cache_version=3. All nullable so older responses still parse.
	public Double   roiPct;
	public Long     maxProfitAtLimit;
	/** True when the pattern hasn't fired in 7+ days. Server still returns
	 *  the row for historical context — render it muted / at the bottom. */
	public Boolean  patternStale;
	public Integer  dailyVolume;
	public Integer  periodHours;
	public Integer  dumpPeakHourUtc;
	public Boolean  isClockAligned;
	public Boolean  confirmedBot;
	public Double   sellRatio;
	public Double   avgBurstIntervalMin;
	/** 24-element hourly sell-volume history (oldest → newest). Null when
	 *  this isn't a bot-dump candidate. Driven into an inline sparkline. */
	public int[]    hourlyVolumes;

	// ── Recovery metrics (historical median) — gated on sample size ─────────
	public Double   recoveryPct;
	public Double   recoveryHours;
	public Integer  recoverySamples;

	/**
	 * Response wrapper — pairs the paginated dump rows with v5's per-tier
	 * counts so the panel's segmented [All N] [Confirmed N] [Likely N]
	 * control can show accurate totals without a second call.
	 */
	public static class Response
	{
		public java.util.List<DumpItem> items;
		public int total;
		public int confirmedCount;
		public int likelyCount;
	}
}
