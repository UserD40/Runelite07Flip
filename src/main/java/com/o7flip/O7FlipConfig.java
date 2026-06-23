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
package com.o7flip;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("o7flip")
public interface O7FlipConfig extends Config
{

	@ConfigSection(
		name = "General",
		description = "Your 07flip.com API key and how often data is refreshed.",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Sidebar panel",
		description = "Tabs and behaviour of the 07Flip sidebar panel.",
		position = 1
	)
	String tabsSection = "tabs";

	@ConfigSection(
		name = "Item tab",
		description = "Show or hide individual sections of the Item insights tab.",
		position = 2
	)
	String itemTabSection = "itemtab";

	@ConfigSection(
		name = "GE offers",
		description = "Helpers for setting up buy and sell offers at the Grand Exchange.",
		position = 3
	)
	String geSection = "ge";

	@ConfigSection(
		name = "GE price overlay",
		description = "The movable 07Flip overlay shown on the GE offer setup screen.",
		position = 4
	)
	String geOverlaySection = "geoverlay";

	@ConfigSection(
		name = "Inventory",
		description = "Helpers shown on items in your inventory.",
		position = 5
	)
	String inventorySection = "inventory";

	@ConfigSection(
		name = "GP drop animation",
		description = "The fading +X gp / -X gp drop shown when a flip completes — position, font and colours.",
		position = 6
	)
	String gpDropSection = "gpdrop";

	@ConfigSection(
		name = "Trade tracker",
		description = "Options for the local trade history and sharing it with 07flip.com.",
		position = 7
	)
	String trackerSection = "tracker";



	@ConfigItem(
		keyName = "apiKey",
		name = "API key",
		description = "Your 07flip.com API key. To get it: sign up at 07flip.com, then visit 07flip.com/account and copy your API key. Paste it here to connect your account. No player data is sent to external servers.",
		secret = true,
		section = generalSection,
		position = 0
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "refreshInterval",
		name = "",
		description = "",
		hidden = true,
		section = generalSection,
		position = 1
	)
	@Range(min = 60, max = 600)
	default int refreshIntervalSeconds()
	{
		return 90;
	}

	@ConfigItem(
		keyName = "usePersonalisedFlips",
		name = "Personalised flips by cash stack",
		description = "<html>Filter the Flips tab to items you can afford right now based on the coins<br>"
			+ "in your inventory. The cash value is rounded down to the nearest 100,000 gp before<br>"
			+ "it leaves your machine — exact wealth is never sent to 07flip.com.</html>",
		section = tabsSection,
		position = 1
	)
	default boolean usePersonalisedFlips()
	{
		return false;
	}

	enum CapitalMode
	{
		OFF, AUTO, MANUAL
	}

	@ConfigItem(
		keyName = "capitalMode",
		name = "",
		description = "",
		hidden = true,
		section = generalSection,
		position = 4
	)
	default CapitalMode capitalMode()
	{
		return CapitalMode.OFF;
	}

	@ConfigItem(
		keyName = "capitalManual",
		name = "",
		description = "",
		hidden = true,
		section = generalSection,
		position = 5
	)
	default long capitalManual()
	{
		return 0L;
	}

	@ConfigItem(
		keyName = "capitalLocked",
		name = "",
		description = "",
		hidden = true,
		section = generalSection,
		position = 6
	)
	default boolean capitalLocked()
	{
		return true;
	}


	@ConfigItem(
		keyName = "showFlips",
		name = "",
		description = "",
		section = tabsSection,
		position = 0,
		hidden = true
	)
	default boolean showFlips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDumps",
		name = "",
		description = "",
		section = tabsSection,
		position = 1,
		hidden = true
	)
	default boolean showDumps()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showItem",
		name = "",
		description = "",
		section = tabsSection,
		position = 3,
		hidden = true
	)
	default boolean showInsights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDecant",
		name = "",
		description = "",
		section = tabsSection,
		position = 7,
		hidden = true
	)
	default boolean showDecant()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDips",
		name = "",
		description = "",
		section = tabsSection,
		position = 8,
		hidden = true
	)
	default boolean showDips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFavourites",
		name = "",
		description = "",
		section = tabsSection,
		position = 9,
		hidden = true
	)
	default boolean showFavourites()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMyFlips",
		name = "",
		description = "",
		hidden = true,
		section = tabsSection,
		position = 15
	)
	default boolean showMyFlips()
	{
		return true;
	}


	@ConfigItem(
		keyName = "autoFillGePrice",
		name = "Auto-fill GE price",
		description = "<html>Automatically type the recommended price into the GE custom price input "
			+ "when you set up a buy or sell, and when you pick a price from the overlay.<br>"
			+ "Turn this off to enter every price yourself — the plugin will not write to the GE "
			+ "price field. The overlay still shows the recommended buy/sell prices for reference.</html>",
		section = geSection,
		position = 0
	)
	default boolean autoFillGePrice()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOfferOverlay",
		name = "Show price overlay",
		description = "Show a movable 07Flip overlay on the GE setup screen with recommended buy/sell prices for the current item. Right-click the overlay and pick a price to auto-fill the custom price input.",
		section = geOverlaySection,
		position = 0
	)
	default boolean showGeOfferOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGePriceHint",
		name = "Highlight 'Enter price' button",
		description = "Show the yellow highlight around the Enter price button after you right-click a flip in the panel.",
		section = geSection,
		position = 1
	)
	default boolean showGePriceHint()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInventoryTooltip",
		name = "Show cost basis tooltip",
		description = "Hover an item in inventory to see your cost basis and 07Flip's recommended sell price.",
		section = inventorySection,
		position = 0
	)
	default boolean showInventoryTooltip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOverlayScore",
		name = "Show Score row",
		description = "Show the 07Flip merchant score row inside the GE setup overlay. Turn off for a smaller overlay.",
		section = geOverlaySection,
		position = 1
	)
	default boolean showGeOverlayScore()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOverlayBuyLimit",
		name = "Show Buy limit row",
		description = "Show the 4-hour GE buy limit inside the GE setup overlay.",
		section = geOverlaySection,
		position = 2
	)
	default boolean showGeOverlayBuyLimit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOverlayVolume",
		name = "Show Volume row",
		description = "Show hourly GE volume inside the GE setup overlay. Turn off for a smaller overlay.",
		section = geOverlaySection,
		position = 3
	)
	default boolean showGeOverlayVolume()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOverlayChart",
		name = "Show 24h chart",
		description = "Show the compact 24h buy/sell sparkline inside the GE setup overlay. Turn off for a much smaller overlay.",
		section = geOverlaySection,
		position = 4
	)
	default boolean showGeOverlayChart()
	{
		return true;
	}

	@ConfigItem(
		keyName = "frozenSellStaleAfterHours",
		name = "Frozen sell expiry",
		description = "<html>When you buy an item, the recommended sell price at that moment is "
			+ "<b>frozen</b> so the GE setup overlay keeps suggesting that target even if the market dips. "
			+ "If the item stays unsold for longer than this many hours, the frozen price is replaced "
			+ "with the live 07flip recommended price so you're not chasing a stale target.<br>"
			+ "Set higher to hold targets longer, lower to follow the market more aggressively.</html>",
		section = geSection,
		position = 2
	)
	@Range(min = 1, max = 48)
	default int frozenSellStaleAfterHours()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "autoOpenItemTab",
		name = "Open Item tab on offer setup",
		description = "<html>When you pick an item on the GE offer setup screen — buying after a search, or "
			+ "selling from your inventory — automatically open the plugin's Item tab showing that item's "
			+ "full detail view.<br>Only happens while the 07Flip sidebar is open.</html>",
		section = geSection,
		position = 3
	)
	default boolean autoOpenItemTab()
	{
		return true;
	}


	enum GpDropFontType
	{
		DEFAULT_BOLD("Default (bold)"),
		RUNESCAPE("RuneScape"),
		RUNESCAPE_BOLD("RuneScape bold"),
		SANS("Sans"),
		SANS_BOLD("Sans bold");

		private final String label;

		GpDropFontType(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "showGpDropOverlay",
		name = "Show GP drop animation",
		description = "Show a fading +X gp / -X gp drop near the GE interface each time a flip completes.",
		section = gpDropSection,
		position = 0
	)
	default boolean showGpDropOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gpDropOffsetX",
		name = "Offset X",
		description = "<html>Horizontal position adjustment for the GP drop animation, in pixels.<br>"
			+ "0 = default (centred on the GE window). Negative moves left, positive moves right.</html>",
		section = gpDropSection,
		position = 1
	)
	@Range(min = -2000, max = 2000)
	default int gpDropOffsetX()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "gpDropOffsetY",
		name = "Offset Y",
		description = "<html>Vertical position adjustment for the GP drop animation, in pixels.<br>"
			+ "0 = default (top of the GE window). Negative moves up, positive moves down.</html>",
		section = gpDropSection,
		position = 2
	)
	@Range(min = -2000, max = 2000)
	default int gpDropOffsetY()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "gpDropPreview",
		name = "Position preview",
		description = "<html>Continuously show a sample GP drop so you can see exactly how and where it will<br>"
			+ "appear while adjusting the settings in this section. <b>Turn this off when you're done.</b></html>",
		section = gpDropSection,
		position = 3
	)
	default boolean gpDropPreview()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gpDropDurationMs",
		name = "Animation time (ms)",
		description = "<html>How long each GP drop lasts, in milliseconds, before it fully fades out.<br>"
			+ "Default 1500 (1.5 seconds). The rise distance stays the same, so longer = slower drift.</html>",
		section = gpDropSection,
		position = 4
	)
	@Range(min = 500, max = 10000)
	default int gpDropDurationMs()
	{
		return 1500;
	}

	@ConfigItem(
		keyName = "gpDropFontType",
		name = "Font",
		description = "Typeface used for the GP drop text.",
		section = gpDropSection,
		position = 5
	)
	default GpDropFontType gpDropFontType()
	{
		return GpDropFontType.DEFAULT_BOLD;
	}

	@ConfigItem(
		keyName = "gpDropFontSize",
		name = "Font size",
		description = "Size of the GP drop text, in points.",
		section = gpDropSection,
		position = 6
	)
	@Range(min = 8, max = 48)
	default int gpDropFontSize()
	{
		return 14;
	}

	@ConfigItem(
		keyName = "gpDropProfitColor",
		name = "Profit colour",
		description = "Colour of the GP drop text when the completed flip made a profit.",
		section = gpDropSection,
		position = 7
	)
	default Color gpDropProfitColor()
	{
		return new Color(0x00C27A);
	}

	@ConfigItem(
		keyName = "gpDropLossColor",
		name = "Loss colour",
		description = "Colour of the GP drop text when the completed flip made a loss.",
		section = gpDropSection,
		position = 8
	)
	default Color gpDropLossColor()
	{
		return new Color(0xE85050);
	}


	@ConfigItem(
		keyName = "shareTradeData",
		name = "Share trade data with 07flip.com",
		description = "<html>Send your completed GE trades to 07flip.com so you can view your history<br>"
			+ "on the website under the Tracker feature. Requires an API key.<br>"
			+ "<b>Only item ID, quantity, and price are sent — your account name is never included.</b></html>",
		section = trackerSection,
		position = 0
	)
	default boolean shareTradeData()
	{
		return false;
	}


	@ConfigItem(
		keyName = "itemTabLivePrices",
		name = "Live prices",
		description = "Show the live buy/sell/margin/tax/profit/ROI section on the Item tab.",
		section = itemTabSection,
		position = 0
	)
	default boolean itemTabLivePrices()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemTabChart",
		name = "Price chart",
		description = "Show the buy/sell price chart (with the 24h / 7d / 30d toggle) on the Item tab.",
		section = itemTabSection,
		position = 1
	)
	default boolean itemTabChart()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemTabPriceRange",
		name = "Price range",
		description = "Show the 24h / 7d / 90d price range section on the Item tab.",
		section = itemTabSection,
		position = 2
	)
	default boolean itemTabPriceRange()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemTabRecommended",
		name = "07Flip recommended",
		description = "Show the 07Flip recommended buy/sell prices section on the Item tab (premium).",
		section = itemTabSection,
		position = 3
	)
	default boolean itemTabRecommended()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemTabScore",
		name = "07Flip score",
		description = "Show the 07Flip confidence / tier / signal section on the Item tab.",
		section = itemTabSection,
		position = 4
	)
	default boolean itemTabScore()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemTabIndicators",
		name = "Technical indicators",
		description = "Show the RSI / MACD / moving averages / % change section on the Item tab (premium).",
		section = itemTabSection,
		position = 5
	)
	default boolean itemTabIndicators()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemTabLiquidity",
		name = "Liquidity",
		description = "Show the buy/sell volume split, imbalance, and fill-time section on the Item tab (premium).",
		section = itemTabSection,
		position = 7
	)
	default boolean itemTabLiquidity()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemTabProjection",
		name = "Projection",
		description = "Show the 30-day / 3-month projection bands on the Item tab (premium).",
		section = itemTabSection,
		position = 9
	)
	default boolean itemTabProjection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemTabVolume",
		name = "Volume",
		description = "Show the hourly / daily trade volume section on the Item tab.",
		section = itemTabSection,
		position = 10
	)
	default boolean itemTabVolume()
	{
		return true;
	}

	enum DefaultChartPeriod
	{
		TWO_HOUR("2 hours", "2h"),
		FOUR_HOUR("4 hours", "4h"),
		DAY("24 hours", "24h"),
		WEEK("7 days", "7d"),
		MONTH("30 days", "30d");

		private final String label;
		private final String chartLabel;

		DefaultChartPeriod(String label, String chartLabel)
		{
			this.label      = label;
			this.chartLabel = chartLabel;
		}

		public String chartLabel()
		{
			return chartLabel;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "defaultChartPeriod",
		name = "Default chart timeframe",
		description = "Which Buy/Sell chart period loads by default on the Item tab. "
			+ "Falls back to 24h when the chosen period has no data for an item.",
		section = itemTabSection,
		position = 12
	)
	default DefaultChartPeriod defaultChartPeriod()
	{
		return DefaultChartPeriod.DAY;
	}
}
