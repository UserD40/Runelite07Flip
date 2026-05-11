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
 * Server-authoritative My Trades headline numbers, returned by
 * {@code GET /api/runelite/tracker/stats}. Merges plugin-recorded
 * {@code trade_records} with website-logged {@code tracker_entries},
 * de-duped via {@code flip_trade_links} on the server side.
 *
 * Falls back to {@code null} when the user has no API key, hasn't
 * enabled trade sharing, or the endpoint is unreachable — callers
 * should then compute a local-only result via {@link com.o7flip.util.ProfitCalculator}.
 *
 * Trust split: {@code totalRealisedProfit = verifiedProfit + declaredProfit}.
 * "Verified" means at least one real GE trade is linked to the entry;
 * "declared" means the user closed the entry manually with a target price
 * before any fill landed (Pegasian-boots-style projections).
 */
public class TrackerStats
{
	public long totalRealisedProfit;
	public long verifiedProfit;
	public long declaredProfit;
	public long totalInvestedOpen;
	public int closedCount;
	public int openCount;
	public double winRate;
	public double hitRate;
	public BestFlip bestFlip;   // null if user has no closed flips
	public String updatedAt;

	public static class BestFlip
	{
		public String entryId;
		public int    itemId;
		public String name;
		public long   profit;
		public String source;          // "verified" | "mixed" | "declared"
		public String fullyClosedAt;
	}
}
