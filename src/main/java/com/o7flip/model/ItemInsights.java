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

public class ItemInsights
{
	public int     itemId;
	public String  name;
	public boolean members;
	public int     buyLimit;
	public Integer highAlch;        // null if item has no alch value
	public Integer lowAlch;

	public Current current;
	public Volume  volume;
	public Ranges  ranges;
	public Score   score;
	public Projection projection;   // null for free users or ineligible items
	public Frozen  frozen;          // null when the requesting user has no freeze for this item
	public Indicators indicators;   // premium-only, null when free
	public Liquidity  liquidity;    // premium-only, null when free

	public Long[]  sparkline24hBuy;
	public Long[]  sparkline24hSell;
	public String  sparkline24hStart;

	public Long[]  sparkline2hBuy;
	public Long[]  sparkline2hSell;
	public String  sparkline2hStart;
	public Long[]  sparkline4hBuy;
	public Long[]  sparkline4hSell;
	public String  sparkline4hStart;

	public Long[]  sparkline7dBuy;
	public Long[]  sparkline7dSell;
	public String  sparkline7dStart;
	public Long[]  sparkline30dBuy;
	public Long[]  sparkline30dSell;
	public String  sparkline30dStart;

	public boolean premiumLocked;
	public String  upgradeUrl;
	public String  updatedAt;

	public static class Current
	{
		public long   buyPrice;
		public long   sellPrice;
		public long   margin;
		public long   tax;
		public long   profit;
		public double roiPct;
		public Long   recBuy;        // premium-only, null when locked
		public Long   recSell;
		public Long   recProfit;
		public Integer buyAgeMinutes;
		public Integer sellAgeMinutes;
	}

	public static class Volume
	{
		public int hourly;
		public int daily;
	}

	public static class Ranges
	{
		public long   high24h;
		public long   low24h;
		public long   high7d;
		public long   low7d;
		public long   high90d;
		public long   low90d;
		public Double position90dPct;
		public Double drawdownPctFrom90d;
		public Integer daysSince90dLow;
		public Integer daysSince90dHigh;
	}

	public static class Score
	{
		public int    confidence;
		public String tier;          // "poor" | "fair" | "good" | "great"
		public String signal;        // premium-only, null when locked
	}

	public static class Projection
	{
		public Band band30d;
		public Band band3m;
	}

	public static class Band
	{
		public long   low;
		public long   high;
		public double hitRate;
	}

	public static class Frozen
	{
		public long   buy;
		public long   sell;
	}

	public static class Indicators
	{
		public Double  rsi14;
		public Double  macdHist;
		public String  macdCross;      // "bullish" | "bearish" | null
		public Double  bbPositionPct;  // 0–100 position within Bollinger bands
		public Double  volSurge;       // current vs baseline volume ratio
		public Long    ma7d;
		public Long    ma30d;
		public String  maCross;        // "bullish" | "bearish" | null — 7d vs 30d
		public Double  pct1h;
		public Double  pct24h;
		public Double  pct7d;
		public Double  pct30d;
	}

	public static class Liquidity
	{
		public Integer hourlyBuyVolume;       // instant buys (wiki lowPriceVolume)
		public Integer hourlySellVolume;      // instant sells (wiki highPriceVolume)
		public Double  volumeImbalancePct;    // + = buy pressure, − = sell pressure
		public Integer crossedHours24h;       // hours of last 24 with inverted margin
		public Double  estHoursToFillLimit;   // volume-throughput proxy, not a queue model
	}

}
