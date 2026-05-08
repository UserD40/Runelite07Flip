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

import com.o7flip.model.TrackedItemData;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Movable overlay that surfaces 07Flip's recommended buy and sell prices for
 * the item currently open in the GE setup screen. Always shows two rows when
 * data is available: Buy (the lower price you place a buy offer at) and Sell
 * (the higher price you place a sell offer at). Right-click → menu entry
 * fills the in-game custom price input if open, or arms it for the next time
 * "Enter price" is clicked.
 */
public class GePriceOverlay extends Overlay
{
	private static final Color HEADER     = new Color(0xFFD700);
	private static final Color BUY_RED    = new Color(0xFF7070);
	private static final Color SELL_GREEN = new Color(0x00C27A);
	private static final Color INFO_GRAY  = new Color(0xC0C0C0);

	/** Used to identify which overlay raised an OverlayMenuClicked event. */
	static final String TARGET = "07Flip price";

	private final Client client;
	private final O7FlipPlugin plugin;
	private final O7FlipConfig config;
	private final PanelComponent panel = new PanelComponent();

	/** Maps menu entry option text to the price it represents. Rebuilt every render. */
	private final Map<String, Long> menuPrices = new LinkedHashMap<>();

	@Inject
	public GePriceOverlay(Client client, O7FlipPlugin plugin, O7FlipConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setMovable(true);
		setSnappable(true);
		setResettable(true);
	}

	/** Returns the price associated with a clicked overlay menu option, or -1 if unknown. */
	long priceForMenuOption(String option)
	{
		Long p = menuPrices.get(option);
		return p == null ? -1L : p;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showGeOfferOverlay())
		{
			return null;
		}

		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			return null;
		}

		int currentItemId = resolveCurrentItemId(setup);
		if (currentItemId <= 0)
		{
			return null;
		}

		TrackedItemData data = plugin.trackedItems.get(currentItemId);
		if (data == null)
		{
			return null;
		}

		// Buy = lower side (price to place a buy offer at).
		// Sell = higher side (price to place a sell offer at).
		Long buyPrice  = firstNonNull(data.flipBuyPrice,  data.dipBuyPrice,  data.spikeBuyPrice, data.dumpBuyPrice);
		Long sellPrice = firstNonNull(data.flipSellPrice, data.dumpSellPrice);

		if (buyPrice == null && sellPrice == null)
		{
			return null;
		}

		// Refresh menu entries — at most one Buy and one Sell entry.
		menuPrices.clear();
		getMenuEntries().clear();

		panel.getChildren().clear();
		panel.setPreferredSize(new Dimension(180, 0));

		String title = data.name != null ? truncate(data.name, 24) : "07Flip";
		panel.getChildren().add(TitleComponent.builder()
			.text(title)
			.color(HEADER)
			.build());

		if (buyPrice != null)
		{
			String optionText = "Set buy price (" + formatGp(buyPrice) + " gp)";
			getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, optionText, TARGET));
			menuPrices.put(optionText, buyPrice);

			panel.getChildren().add(LineComponent.builder()
				.left("Buy")
				.leftColor(BUY_RED)
				.right(formatGp(buyPrice))
				.rightColor(INFO_GRAY)
				.build());
		}

		if (sellPrice != null)
		{
			String optionText = "Set sell price (" + formatGp(sellPrice) + " gp)";
			getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, optionText, TARGET));
			menuPrices.put(optionText, sellPrice);

			panel.getChildren().add(LineComponent.builder()
				.left("Sell")
				.leftColor(SELL_GREEN)
				.right(formatGp(sellPrice))
				.rightColor(INFO_GRAY)
				.build());
		}

		return panel.render(graphics);
	}

	/**
	 * Resolves the item ID currently shown on the GE setup screen.
	 * Tries TRADINGPOST_SEARCH first (set when the user picks an item from search),
	 * then falls back to scanning the setup widget's item icon child (covers the
	 * drag-from-inventory-to-sell-slot path where the search varplayer is not set).
	 */
	private int resolveCurrentItemId(Widget setup)
	{
		int searchItemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (searchItemId > 0)
		{
			return searchItemId;
		}
		Widget[] children = setup.getDynamicChildren();
		if (children != null)
		{
			for (Widget w : children)
			{
				int id = w.getItemId();
				if (id > 0)
				{
					return id;
				}
			}
		}
		Widget[] staticChildren = setup.getStaticChildren();
		if (staticChildren != null)
		{
			for (Widget w : staticChildren)
			{
				int id = w.getItemId();
				if (id > 0)
				{
					return id;
				}
				Widget[] grand = w.getDynamicChildren();
				if (grand == null) continue;
				for (Widget g : grand)
				{
					int gid = g.getItemId();
					if (gid > 0)
					{
						return gid;
					}
				}
			}
		}
		return -1;
	}

	@SafeVarargs
	private static <T> T firstNonNull(T... values)
	{
		for (T v : values)
		{
			if (v != null) return v;
		}
		return null;
	}

	private static String formatGp(long amount)
	{
		return String.format("%,d", amount);
	}

	private static String truncate(String s, int max)
	{
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}
}
