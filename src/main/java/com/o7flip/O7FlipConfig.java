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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("o7flip")
public interface O7FlipConfig extends Config
{
	// ── Sections ────────────────────────────────────────────────────────────

	@ConfigSection(
		name = "General",
		description = "API key, refresh interval and skill settings.",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Panel tabs",
		description = "Show or hide tabs in the 07Flip sidebar panel.",
		position = 1
	)
	String tabsSection = "tabs";

	@ConfigSection(
		name = "Grand Exchange integration",
		description = "Overlays and helpers shown inside the Grand Exchange interface.",
		position = 2
	)
	String geSection = "ge";

	@ConfigSection(
		name = "Trade tracker",
		description = "Options for the local trade history and sharing it with 07flip.com.",
		position = 3
	)
	String trackerSection = "tracker";

	// (The legacy Tab order section was removed — tabOrder + topRowTabs are
	//  hidden persistence items that now live under the Panel tabs section
	//  to avoid an empty header in the config UI. The Customise top row
	//  tabs button is the user-facing entry point.)

	// ── General ─────────────────────────────────────────────────────────────

	@ConfigItem(
		keyName = "apiKey",
		name = "API key",
		description = "Your 07flip.com API key. To get it: sign up at 07flip.com, log in with Discord, then click your Discord user icon (top-right) and select 'View API Key'. No player data is sent to external servers.",
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
		name = "Refresh interval (seconds)",
		description = "How often to fetch data from 07flip.com. Minimum 60 seconds.",
		section = generalSection,
		position = 1
	)
	@Range(min = 60, max = 600)
	default int refreshIntervalSeconds()
	{
		return 90;
	}

	@ConfigItem(
		keyName = "smithingLevel",
		name = "Smithing level",
		description = "Your Smithing level, used to calculate PoH repair costs for Barrows and Moon.",
		section = generalSection,
		position = 2
	)
	@Range(min = 1, max = 99)
	default int smithingLevel()
	{
		return 99;
	}

	@ConfigItem(
		keyName = "usePersonalisedFlips",
		name = "Personalised flips by cash stack",
		description = "<html>Filter the Flips tab to items you can afford right now based on the coins<br>"
			+ "in your inventory. The cash value is rounded down to the nearest 100,000 gp before<br>"
			+ "it leaves your machine — exact wealth is never sent to 07flip.com.</html>",
		section = generalSection,
		position = 3
	)
	default boolean usePersonalisedFlips()
	{
		return false;
	}

	/**
	 * Capital-input mode for the cross-tab affordability filter. Surfaced
	 * directly on the panel as a toggle; the config entries below are hidden
	 * persistence only.
	 *
	 * <ul>
	 *   <li>{@code OFF} — no capital filter (default; preserves the legacy
	 *       behaviour where every tab shows the full list).</li>
	 *   <li>{@code AUTO} — derive from inventory coins, rounded to 100K.</li>
	 *   <li>{@code MANUAL} — use {@link #capitalManual()}, the typed value.</li>
	 * </ul>
	 */
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

	/** True when the manual capital field is locked against accidental edits.
	 *  Defaults to true — a value the user typed once survives misclicks
	 *  until they explicitly unlock. */
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

	// ── Panel tabs ──────────────────────────────────────────────────────────
	//
	// All per-tab show flags below are HIDDEN in the config UI now — the
	// "Customise top row tabs" dialog is the primary visibility control.
	// Each flag still resolves to its default, so power users editing
	// settings.properties directly can hard-disable a fetch (useful for the
	// premium-gated features like Screeners / Alerts when free).

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
		keyName = "showSpikes",
		name = "",
		description = "",
		section = tabsSection,
		position = 2,
		hidden = true
	)
	default boolean showSpikes()
	{
		return false;
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
		keyName = "showAlerts",
		name = "",
		description = "",
		section = tabsSection,
		position = 4,
		hidden = true
	)
	default boolean showAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMoon",
		name = "",
		description = "",
		section = tabsSection,
		position = 5,
		hidden = true
	)
	default boolean showMoon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBarrows",
		name = "",
		description = "",
		section = tabsSection,
		position = 6,
		hidden = true
	)
	default boolean showBarrows()
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
		keyName = "showHighAlch",
		name = "",
		description = "",
		section = tabsSection,
		position = 10,
		hidden = true
	)
	default boolean showHighAlch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTeleTablets",
		name = "",
		description = "",
		section = tabsSection,
		position = 11,
		hidden = true
	)
	default boolean showTeleTablets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showScreeners",
		name = "",
		description = "",
		section = tabsSection,
		position = 12,
		hidden = true
	)
	default boolean showScreeners()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highAlchFireStaff",
		name = "",
		description = "",
		hidden = true,
		section = tabsSection,
		position = 13
	)
	default boolean highAlchFireStaff()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highAlchBryophyta",
		name = "",
		description = "",
		hidden = true,
		section = tabsSection,
		position = 14
	)
	default boolean highAlchBryophyta()
	{
		return false;
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

	// ── Tab order ──────────────────────────────────────────────────────────

	@ConfigItem(
		keyName = "openTabReorderDialog",
		name = "Customise top row tabs",
		description = "<html>Tick this box to open the top-row picker. Pick which 4 tabs<br>"
			+ "live on the top row of the panel; anything you leave out shows<br>"
			+ "inside the <b>Other</b> tab on the bottom row. The bottom row<br>"
			+ "(Flips · Trades · Item · Other) is always fixed.</html>",
		section = tabsSection,
		position = 0
	)
	default boolean openTabReorderDialog()
	{
		return false;
	}

	@ConfigItem(
		keyName = "tabOrder",
		name = "",
		description = "",
		section = tabsSection,
		position = 50,
		hidden = true
	)
	default String tabOrder()
	{
		return "";
	}

	/**
	 * CSV of up to 4 tab names that occupy the customisable top row of the
	 * panel. Defaults to {@code "Moons,Barrows,Dumps,Alerts"}. Anything from
	 * the candidate pool that isn't listed here shows up inside the Other
	 * tab on the bottom row instead.
	 */
	@ConfigItem(
		keyName = "topRowTabs",
		name = "",
		description = "",
		section = tabsSection,
		position = 51,
		hidden = true
	)
	default String topRowTabs()
	{
		return "Moons,Barrows,Dumps,Alerts";
	}

	// ── Grand Exchange integration ─────────────────────────────────────────

	@ConfigItem(
		keyName = "showGeOfferOverlay",
		name = "Show price overlay on GE setup",
		description = "Show a movable 07Flip overlay on the GE setup screen with recommended buy/sell prices for the current item. Right-click the overlay and pick a price to auto-fill the custom price input.",
		section = geSection,
		position = 1
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
		position = 2
	)
	default boolean showGePriceHint()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inventoryCheckOnSell",
		name = "Hide 'Sell on GE' when not carrying item",
		description = "Hide the Sell on GE right-click option if the item is not in your inventory.",
		section = geSection,
		position = 3
	)
	default boolean inventoryCheckOnSell()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGpDropOverlay",
		name = "Show GP drop animation on completed sells",
		description = "Show a fading +X gp / -X gp drop near the GE interface each time a flip completes.",
		section = geSection,
		position = 4
	)
	default boolean showGpDropOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInventoryTooltip",
		name = "Show inventory tooltip with cost basis",
		description = "Hover an item in inventory to see your cost basis and 07Flip's recommended sell price.",
		section = geSection,
		position = 5
	)
	default boolean showInventoryTooltip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOverlayScore",
		name = "Overlay: show Score row",
		description = "Show the 07Flip merchant score row inside the GE setup overlay. Turn off for a smaller overlay.",
		section = geSection,
		position = 6
	)
	default boolean showGeOverlayScore()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOverlayBuyLimit",
		name = "Overlay: show Buy limit row",
		description = "Show the 4-hour GE buy limit inside the GE setup overlay.",
		section = geSection,
		position = 7
	)
	default boolean showGeOverlayBuyLimit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOverlayVolume",
		name = "Overlay: show Volume row",
		description = "Show hourly GE volume inside the GE setup overlay. Turn off for a smaller overlay.",
		section = geSection,
		position = 8
	)
	default boolean showGeOverlayVolume()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOverlayChart",
		name = "Overlay: show 24h chart",
		description = "Show the compact 24h buy/sell sparkline inside the GE setup overlay. Turn off for a much smaller overlay.",
		section = geSection,
		position = 9
	)
	default boolean showGeOverlayChart()
	{
		return true;
	}

	@ConfigItem(
		keyName = "frozenSellStaleAfterHours",
		name = "Refresh frozen sell after (hours)",
		description = "<html>When you buy an item, the recommended sell price at that moment is "
			+ "<b>frozen</b> so the GE setup overlay keeps suggesting that target even if the market dips. "
			+ "If the item stays unsold for longer than this many hours, the frozen price is replaced "
			+ "with the live 07flip recommended price so you're not chasing a stale target.<br>"
			+ "Set higher to hold targets longer, lower to follow the market more aggressively.</html>",
		section = geSection,
		position = 10
	)
	@Range(min = 1, max = 48)
	default int frozenSellStaleAfterHours()
	{
		return 3;
	}

	// ── Trade tracker ───────────────────────────────────────────────────────

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
}
