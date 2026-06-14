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

public class FlipItem
{
	public int itemId;
	public String name;
	public long buyPrice;
	public long sellPrice;
	public long profit;
	public double roiPct;
	public long potentialProfit;
	public int buyLimit;
	public boolean members;

	// Optional cash-stack annotation (only present when the request used
	// ?cashStack=…&annotate=affordableQty). Null otherwise.
	public Integer affordableQty;

	// Composite "07Flip Score" 0–100. Null when the item had < 20 trades
	// in the last hour (illiquid or fresh items).
	public Integer flip07Score;

	// Recommended buy / sell prices from the server's last-hour p10/p90 of
	// fills. Use for the GE auto-fill overlay and the item-detail drawer.
	// Null together when the item had < 10 snapshots in the last hour.
	public Long recBuyPrice;
	public Long recSellPrice;
	public Long recProfit;

	// Hourly / daily volume. Server doesn't always echo these on /flips rows
	// — when null, the volume filter is a pass-through. Server agent offered
	// to add them to /flips response; once that ships these light up.
	public Integer hourlyVolume;
	public Integer dailyVolume;

	// ── Band ("Bulk Margin") fields ────────────────────────────────────────
	// Present ONLY on rows returned by ?preset=bandFlip. For these patient
	// range flips the LIVE buy/sell/profit above are ~0 (near-zero instant
	// spread) and must NOT be shown — render from these instead:
	//   Buy  = bandFloor    (14-day p10 dependable buy)
	//   Sell = bandCeiling  (14-day p90 dependable sell)
	//   Margin = bandMargin (after-tax gp/item)  ·  bandMarginPct (% of floor)
	//   Profit / rank = bandProfit (after-tax over one 4h buy-limit cycle)
	// bandFloor doubles as the presence flag — null on every other preset.
	public Long bandProfit;
	public Long bandMargin;
	public Long bandFloor;
	public Long bandCeiling;
	public double bandMarginPct;
	public Integer bandVolumeCoverage;

	/** True when this row carries band ("Bulk Margin") data. */
	public boolean isBand()
	{
		return bandFloor != null;
	}
}
