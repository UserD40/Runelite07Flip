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

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.GrandExchangeOfferState;

public final class Models
{
	private Models()
	{
	}

	public static class ActiveOfferSnapshot
	{
		public final int slot;
		public final int itemId;
		public final String name;
		public final long price;
		public final int quantitySold;
		public final int totalQuantity;
		public final GrandExchangeOfferState state;

		public ActiveOfferSnapshot(int slot, int itemId, String name, long price,
			int quantitySold, int totalQuantity, GrandExchangeOfferState state)
		{
			this.slot          = slot;
			this.itemId        = itemId;
			this.name          = name != null ? name : "Item " + itemId;
			this.price         = price;
			this.quantitySold  = quantitySold;
			this.totalQuantity = totalQuantity;
			this.state         = state;
		}

		public boolean isBuy()
		{
			return state == GrandExchangeOfferState.BUYING
				|| state == GrandExchangeOfferState.BOUGHT
				|| state == GrandExchangeOfferState.CANCELLED_BUY;
		}
	}
	public static class AuthStatus
	{
		public boolean authenticated;
		public boolean premium;
	}
	public static class CompletedPosition
	{
		public int     itemId;
		public String  name;
		public int     qty;
		public long    buyGp;
		public long    sellGp;
		public long    profit;
		public boolean partial;
		public Double  fillHours;
		public String  closedAt;

		public String dedupeKey()
		{
			return itemId + "@" + (closedAt == null ? "" : closedAt);
		}
	}
	public static class DecantItem
	{
		public int    itemId;
		public String potionName = "Unknown";
		public String strategy = "";
		@SerializedName("profit_per_4dose")
		public long   profitPer4dose;
		public long   profitPerDose;
		public double roiPct;
		public int    minHourlyVolume;
		public int    dailyVolume;
		public int    buyDose;
		public int    sellDose;
	}
	public static class DipItem
	{
		public int     itemId;
		public String  name = "Unknown";
		public long    buyPrice;
		public int     hourlyVolume;
		public int     dailyVolume;
		public int     buyLimit;
		public boolean members = true;
		public String  lastUpdated = "";

		public String  type = "24h_dip";

		@SerializedName("avg_24h_buy")
		public Long    avg24hBuy;
		public Double  dipPct;

		@SerializedName("dip_pct_1d")
		public Double  dipPct1d;
		@SerializedName("dip_pct_7d")
		public Double  dipPct7d;
		@SerializedName("dip_pct_30d")
		public Double  dipPct30d;

		public Long    atlFloor;
		public Double  buyVsAtlPct;
	}
	public static class DumpItem
	{
		public String  tier;

		public int     itemId;
		public String  name = "Unknown";
		public long    buyPrice;
		public long    sellPrice;
		public long    profit;
		public int     dumpScore;
		public double  dumpPct;
		public String  dumpStatus = "none";
		public Double  lastDumpHoursAgo;
		public int     hourlyVolume;
		public int     buyLimit;
		public boolean members = true;

		public Double   roiPct;
		public Boolean  patternStale;
		public Integer  dailyVolume;
		public Integer  periodHours;
		public Integer  dumpPeakHourUtc;
		public Boolean  isClockAligned;

		public static class Response
		{
			public java.util.List<DumpItem> items;
			public int total;
			public int confirmedCount;
			public int likelyCount;
		}
	}
	public static class FlipItem
	{
		public int itemId;
		public String name = "Unknown";
		public long buyPrice;
		public long sellPrice;
		public long profit;
		public double roiPct;
		public long potentialProfit;
		public int buyLimit;
		public boolean members = true;

		public Integer affordableQty;

		public Integer flip07Score;

		public Long recBuyPrice;
		public Long recSellPrice;
		public Long recProfit;

		public Integer hourlyVolume;
		public Integer dailyVolume;

		public Integer buyAgeMinutes;
		public Integer sellAgeMinutes;

		public Long bandProfit;
		public Long bandMargin;
		public Long bandFloor;
		public Long bandCeiling;
		public double bandMarginPct;
		public Integer bandVolumeCoverage;

		public boolean isBand()
		{
			return bandFloor != null;
		}
	}
	public static class ItemInsights
	{
		public int     itemId;
		public String  name;
		public boolean members;
		public int     buyLimit;
		public Integer highAlch;
		public Integer lowAlch;

		public Current current;
		public Volume  volume;
		public Ranges  ranges;
		public Score   score;
		public Projection projection;
		public Frozen  frozen;
		public Indicators indicators;
		public Liquidity  liquidity;

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
			public Long   recBuy;
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
			public String signal;
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
			public long    buy;
			public long    sell;
			public long    profit;
			public String  frozenAt;
			public boolean expired;
		}

		public static class Indicators
		{
			public Double  rsi14;
			public Double  macdHist;
			public String  macdCross;      // "bullish" | "bearish" | null
			public Double  bbPositionPct;
			public Double  volSurge;
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
			public Integer hourlyBuyVolume;
			public Integer hourlySellVolume;
			public Double  volumeImbalancePct;
			public Integer crossedHours24h;
			public Double  estHoursToFillLimit;
		}

	}
	public static class OptimizeResult
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
			public Boolean members;
			public long    expectedProfitTotal;
			public Double  avgFillConfidence;
			public Double  minFillConfidence;
			public int     recommendedCount;
			public int     rawCount;
			public Integer maxFillHours;
			public Double  avgEstimatedFillHours;
			public Double  maxEstimatedFillHours;
			public String  fillConfidenceFormula;
			public String  pricingNote;
			public String  compositionNote;
			public String  realismNote;

			public String  emptyReason;
			public boolean degradedTrendData;
			public Double  minProfitPctApplied;
			public SlotSuggestion slotSuggestion;
			public java.util.Map<String, Integer> eligibilityRejections = new java.util.LinkedHashMap<>();

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
			public long   expectedProfit;
			public Double fillConfidence;
			public int    buyLimit;
			public Integer hourlyVolume;
			public String  priceSource;
			public Long    rawBuyPrice;
			public Long    rawSellPrice;
			public Long    rawProfitPerUnit;
			public Double  estimatedFillHours;
			public Integer realisticQtyCap;
			public int[]   hourlyTrend;
			public Double  profitPctOfBank;
			public Boolean belowWealthThreshold;

			public java.util.List<SlotFill> buys  = new java.util.ArrayList<>();
			public java.util.List<SlotFill> sells = new java.util.ArrayList<>();
			public SlotState state                = SlotState.PENDING;
			public boolean partial;
			public long reservedGp;
			public Long offerInstanceId;
			public int overrideRev;
			public String overrideSource;
			public transient int appliedOverrideRev;
			public transient boolean pendingOfflineReconcile;
			public boolean sellListed;

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
	public static class OptimizerSession
	{
		public Inputs inputs = new Inputs();
		public List<OptimizeResult.Allocation> slots = new ArrayList<>();
		public String generatedAt;
		public String lastPollAt;
		public OptimizeResult.Summary summary;

		public OptimizerSession copy()
		{
			OptimizerSession c = new OptimizerSession();
			c.inputs      = inputs != null ? inputs.copy() : new Inputs();
			c.generatedAt = generatedAt;
			c.lastPollAt  = lastPollAt;
			c.updatedAt   = updatedAt;
			c.summary     = summary != null ? summary.copy() : null;
			c.slots       = new ArrayList<>();
			if (slots != null)
			{
				for (OptimizeResult.Allocation a : slots)
				{
					if (a != null) c.slots.add(a.copy());
				}
			}
			return c;
		}
		public String updatedAt;

		public static class Inputs
		{
			public long          capital;
			public int           slots;
			public Integer       maxFillHours;
			public String        risk;
			public List<Integer> excludeItemIds = new ArrayList<>();
			public Boolean       members;
			public Double        minProfitPct;

			public Inputs copy()
			{
				Inputs c = new Inputs();
				c.capital        = capital;
				c.slots          = slots;
				c.maxFillHours   = maxFillHours;
				c.risk           = risk;
				c.excludeItemIds = new ArrayList<>(excludeItemIds);
				c.members        = members;
				c.minProfitPct   = minProfitPct;
				return c;
			}
		}
	}
	public static class FreezeRow
	{
		public int    itemId;
		public long   frozenBuy;
		public long   frozenSell;
		public long   frozenProfit;
		public String frozenAt;
	}

	public static class RecommendedPrices
	{
		public int itemId;
		public Long recBuyPrice;
		public Long recSellPrice;
		public Long geTax;
		public Long recProfit;
		public Integer sampleSize;
	}
	public static class RepriceResult
	{
		public long   suggestedPrice;
		public long   breakEvenPrice;
		public long   clearingPrice;
		public long   netMarginEach;
		public double netMarginPct;
		public long   cutLossMarginEach;
		public int    etaMinutes;
		public String status;
		public Long   costBasis;
		public String costBasisSource;
	}
	public static class SearchResultItem
	{
		public int     itemId;
		public String  name = "Unknown";
		public Long    buyPrice;
		public Long    sellPrice;
		public Long    margin;
		public Long    profit;
		public Double  roi;
		public Long    recBuyPrice;
		public Long    recSellPrice;
		public Long    recProfit;
		public Integer hourlyVolume;
		public Integer dailyVolume;
		public int     buyLimit;
		public boolean members;
		public Integer highAlch;
		public String  lastUpdated = "";
	}
	public static class SlotFill
	{
		public int    qty;
		public long   priceEach;
		public String tradedAt;

		public SlotFill copy()
		{
			SlotFill c = new SlotFill();
			c.qty       = qty;
			c.priceEach = priceEach;
			c.tradedAt  = tradedAt;
			return c;
		}
	}
	public enum SlotState
	{
		PENDING("pending"),
		BUYING("buying"),
		FILLED("filled"),
		SELLING("selling"),
		CLOSED("closed");

		private final String wire;

		SlotState(String wire) { this.wire = wire; }

		public String wire() { return wire; }

		public static SlotState fromWire(String s)
		{
			if (s == null) return PENDING;
			switch (s)
			{
				case "buying":  return BUYING;
				case "filled":  return FILLED;
				case "selling": return SELLING;
				case "closed":  return CLOSED;
				case "pending":
				default:        return PENDING;
			}
		}

		public static SlotState derive(int targetQty, List<SlotFill> buys, List<SlotFill> sells)
		{
			int bought = sum(buys);
			int sold   = sum(sells);
			if (sold >= bought && bought >= targetQty) return CLOSED;
			if (sold > 0)                              return SELLING;
			if (bought >= targetQty)                   return FILLED;
			if (bought > 0)                            return BUYING;
			return PENDING;
		}

		private static int sum(List<SlotFill> fills)
		{
			if (fills == null) return 0;
			int total = 0;
			for (SlotFill f : fills)
			{
				if (f != null) total += f.qty;
			}
			return total;
		}
	}
	public static class TrackedItemData
	{
		public int    itemId;
		public String name;

		public Long   flipBuyPrice;
		public Long   flipSellPrice;

		public Long   alertSellTarget;

		public Long   dumpBuyPrice;
		public Long   dumpSellPrice;
	}
	public static class TrackerStats
	{
		public long totalRealisedProfit;
		public long verifiedProfit;
		public long declaredProfit;
		public int closedCount;
		public double winRate;
		public double hitRate;
		public BestFlip bestFlip;
		public String updatedAt;

		public static class BestFlip
		{
			public int    itemId;
			public String name;
			public long   profit;
			public String source;          // "verified" | "mixed" | "declared"
		}
	}
	public static class TradeRecord
	{
		public int    itemId;
		public String name;
		public boolean isBuy;
		public int    quantity;
		public long   priceEach;
		public long   totalGp;
		public long   timestamp;
		public boolean partial;

		public Long tradeId;

		public Long offerInstanceId;

		public boolean serverSynced;

		public Integer totalQuantity;

		public TradeRecord copy()
		{
			TradeRecord c = new TradeRecord();
			c.itemId          = itemId;
			c.name            = name;
			c.isBuy           = isBuy;
			c.quantity        = quantity;
			c.priceEach       = priceEach;
			c.totalGp         = totalGp;
			c.timestamp       = timestamp;
			c.partial         = partial;
			c.tradeId         = tradeId;
			c.offerInstanceId = offerInstanceId;
			c.serverSynced    = serverSynced;
			c.totalQuantity   = totalQuantity;
			return c;
		}

		public String fingerprint()
		{
			return itemId + "|" + (isBuy ? "B" : "S") + "|" + quantity + "|" + totalGp + "|" + timestamp;
		}
	}
}
