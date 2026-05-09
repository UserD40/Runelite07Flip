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

	@ConfigSection(
		name = "Tab order",
		description = "Customise the left-to-right order of the panel tabs.",
		position = 4
	)
	String tabOrderSection = "tabOrder";

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

	// ── Panel tabs ──────────────────────────────────────────────────────────

	@ConfigItem(
		keyName = "showFlips",
		name = "Show Flips tab",
		description = "Show the Flips tab in the panel.",
		section = tabsSection,
		position = 0
	)
	default boolean showFlips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDumps",
		name = "Show Dumps tab",
		description = "Show the Dumps tab in the panel.",
		section = tabsSection,
		position = 1
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
		keyName = "showDips",
		name = "Show Dips tab",
		description = "Show the Dips tab in the panel.",
		section = tabsSection,
		position = 3
	)
	default boolean showDips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAlerts",
		name = "Show Alerts tab",
		description = "Show the Alerts tab in the panel. Requires a premium 07flip.com subscription.",
		section = tabsSection,
		position = 4
	)
	default boolean showAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMoon",
		name = "Show Moons tab",
		description = "Show the Moons armour tab in the panel. Requires an 07flip.com API key.",
		section = tabsSection,
		position = 5
	)
	default boolean showMoon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBarrows",
		name = "Show Barrows tab",
		description = "Show the Barrows tab in the panel. Requires an 07flip.com API key.",
		section = tabsSection,
		position = 6
	)
	default boolean showBarrows()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDecant",
		name = "Show Decant tab",
		description = "Show the Decanting tab in the panel.",
		section = tabsSection,
		position = 7
	)
	default boolean showDecant()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMyFlips",
		name = "Show My Trades tab",
		description = "Show the My Trades tab in the panel. Records completed GE buys and sells locally.",
		section = tabsSection,
		position = 8
	)
	default boolean showMyFlips()
	{
		return true;
	}

	// ── Tab order ──────────────────────────────────────────────────────────

	@ConfigItem(
		keyName = "openTabReorderDialog",
		name = "Open reorder dialog",
		description = "<html>Tick this box to open the tab reorder dialog. Pick a tab in the popup,<br>"
			+ "use the ▲ / ▼ buttons to move it, then Save. The tick auto-clears once<br>"
			+ "the dialog is open, so you can re-tick it any time to re-open.</html>",
		section = tabOrderSection,
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
		section = tabOrderSection,
		position = 1,
		hidden = true
	)
	default String tabOrder()
	{
		return "";
	}

	// ── Grand Exchange integration ─────────────────────────────────────────

	@ConfigItem(
		keyName = "showGePriceColouring",
		name = "Slot price colouring",
		description = "Colour GE slot prices green/red based on 07Flip recommended buy/sell prices.",
		section = geSection,
		position = 0
	)
	default boolean showGePriceColouring()
	{
		return true;
	}

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
