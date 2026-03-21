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
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.o7flip.model;

import java.util.LinkedHashSet;

/**
 * Aggregated data for a single item ID across all 07Flip API tabs.
 * Fields are boxed (Long/Double) — null means the item is not in that tab.
 * presentIn is an insertion-ordered set (Flips → Alerts → Dips → Dumps → Spikes).
 */
public class TrackedItemData
{
	public int    itemId;
	public String name;

	// Flips
	public Long   flipBuyPrice;
	public Long   flipSellPrice;
	public Long   flipProfit;
	public Double flipRoiPct;

	// Alerts
	public Long   alertCurrentPrice;
	public Long   alertSellTarget;
	public Double alertUpsidePct;

	// Dips
	public Long   dipBuyPrice;
	public Double dipPct;

	// Dumps
	public Long   dumpBuyPrice;
	public Long   dumpSellPrice;
	public Double dumpPct;

	// Spikes
	public Long   spikeBuyPrice;

	/** Ordered set of tab names that have data for this item. */
	public LinkedHashSet<String> presentIn = new LinkedHashSet<>();
}
