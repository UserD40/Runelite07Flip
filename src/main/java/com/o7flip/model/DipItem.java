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
 * One row from /api/runelite/dips. The endpoint mixes two row shapes
 * (distinguished by the {@code type} field): {@code "24h_dip"} rows
 * carry {@code avg24hBuy} + {@code dipPct}, {@code "atl"} rows carry
 * {@code atlFloor} + {@code buyVsAtlPct}. The other type's fields are
 * null.
 */
public class DipItem
{
	public int     itemId;
	public String  name;
	public long    buyPrice;
	public int     hourlyVolume;
	public int     dailyVolume;
	public int     buyLimit;
	public boolean members;
	public String  lastUpdated;

	/** "24h_dip" or "atl". */
	public String  type;

	// 24h_dip fields (null when type == "atl")
	public Long    avg24hBuy;
	public Double  dipPct;

	// Window-specific dip percentages — server now ships all three on every
	// dip row, so the UI can show "↓ X% in 7d" without re-fetching when the
	// user switches windows. Nullable when the data window doesn't have
	// enough history (server returns null below the minimum sample size).
	public Double  dipPct1d;
	public Double  dipPct7d;
	public Double  dipPct30d;

	// atl fields (null when type == "24h_dip"). The atl_floor now uses a
	// 5-year lookback (was 1-year) — purely additive, semantics unchanged.
	public Long    atlFloor;
	public Double  buyVsAtlPct;
}
