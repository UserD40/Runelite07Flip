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
import net.runelite.client.config.Range;

@ConfigGroup("o7flip")
public interface O7FlipConfig extends Config
{
	@ConfigItem(
		keyName = "apiKey",
		name = "API Key",
		description = "Your 07flip.com API key. To get it: sign up at 07flip.com, log in with Discord, then click your Discord user icon (top-right) and select 'View API Key'. No player data is sent to external servers.",
		secret = true,
		position = 0
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "refreshInterval",
		name = "Refresh Interval (seconds)",
		description = "How often to fetch data from 07flip.com. Minimum 60 seconds.",
		position = 1
	)
	@Range(min = 60, max = 600)
	default int refreshIntervalSeconds()
	{
		return 90;
	}

	@ConfigItem(
		keyName = "smithingLevel",
		name = "Smithing Level",
		description = "Your Smithing level, used to calculate PoH repair costs for Barrows and Moon.",
		position = 2
	)
	@Range(min = 1, max = 99)
	default int smithingLevel()
	{
		return 99;
	}

	// ── Tab visibility ─────────────────────────────────────────────────────

	@ConfigItem(
		keyName = "showFlips",
		name = "Show Flips tab",
		description = "Show the Flips tab in the panel.",
		position = 3
	)
	default boolean showFlips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDumps",
		name = "Show Dumps tab",
		description = "Show the Dumps tab in the panel.",
		position = 4
	)
	default boolean showDumps()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSpikes",
		name = "Show Spikes tab",
		description = "Show the Spikes tab in the panel.",
		position = 5
	)
	default boolean showSpikes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDips",
		name = "Show Dips tab",
		description = "Show the Dips tab in the panel.",
		position = 6
	)
	default boolean showDips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAlerts",
		name = "Show Alerts tab",
		description = "Show the Alerts tab in the panel. Requires a premium 07flip.com subscription.",
		position = 7
	)
	default boolean showAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMoon",
		name = "Show Moon tab",
		description = "Show the Moon armour tab in the panel. Requires an 07flip.com API key.",
		position = 8
	)
	default boolean showMoon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBarrows",
		name = "Show Barrows tab",
		description = "Show the Barrows tab in the panel. Requires an 07flip.com API key.",
		position = 9
	)
	default boolean showBarrows()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDecant",
		name = "Show Decant tab",
		description = "Show the Decanting tab in the panel.",
		position = 10
	)
	default boolean showDecant()
	{
		return true;
	}

	// ── GE integration features ────────────────────────────────────────────

	@ConfigItem(
		keyName = "showGePriceColouring",
		name = "GE Slot Price Colouring",
		description = "Colour GE slot prices green/red based on 07Flip recommended buy/sell prices.",
		position = 11
	)
	default boolean showGePriceColouring()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOfferOverlay",
		name = "GE Offer Data Overlay",
		description = "Show a data card with 07Flip info when you open a GE slot for a tracked item.",
		position = 12
	)
	default boolean showGeOfferOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inventoryCheckOnSell",
		name = "Inventory Check on Sell",
		description = "Hide the Sell on GE right-click option if the item is not in your inventory.",
		position = 13
	)
	default boolean inventoryCheckOnSell()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoSwitchTabOnGe",
		name = "Auto-Switch Tab on GE Open",
		description = "Automatically switch the 07Flip panel to the relevant tab when you open a GE slot for a tracked item.",
		position = 14
	)
	default boolean autoSwitchTabOnGe()
	{
		return true;
	}
}
