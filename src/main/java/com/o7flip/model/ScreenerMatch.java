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

/** One technical-indicator match inside a {@link ScreenerPreset}. */
public class ScreenerMatch
{
	public int    itemId;
	public String name;

	// Technical indicators — any of these may be null when not relevant
	// to the preset that produced the row.
	public Double macdHist;
	public String macdCross;        // "bullish" | "bearish" | null
	public Double volSurge;          // multiple-of-mean volume
	public Double bbPosition;        // Bollinger band position [0..1]
	public Double pricePos30d;       // 30-day price position [0..1]
	public Double pricePos90d;       // 90-day price position [0..1]
	public Double pct1d;
	public Double pct7d;
	public Double pct30d;

	// Optional market data — populated either by the server (future) or by
	// the plugin enriching from cached FlipItem rows. Null when neither
	// source has data for this item, in which case the UI shows "—".
	public Long    buyPrice;
	public Long    sellPrice;
	public Long    profit;
	public Double  roiPct;
	public Integer flip07Score;
}
