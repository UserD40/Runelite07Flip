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
import net.runelite.api.gameval.VarbitID;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Movable overlay that surfaces 07Flip's recommended buy/sell prices for the
 * item currently open in the GE setup screen. Right-click → menu of "Set X gp"
 * options; clicking one fills the in-game custom price input (opening the
 * chatbox first if it isn't already).
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

		// Visible only while the GE qty/price setup screen is open with an item selected.
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			return null;
		}

		int currentItemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (currentItemId <= 0)
		{
			return null;
		}

		TrackedItemData data = plugin.trackedItems.get(currentItemId);
		boolean isBuy = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) != 0;

		// Carry over any right-click-queued price for this item + direction.
		long queuedPrice = plugin.queuedPriceFor(currentItemId, isBuy);

		List<PriceOption> options = collectOptions(data, queuedPrice, isBuy);
		if (options.isEmpty())
		{
			return null;
		}

		// Refresh menu entries — one "Set <label> (<price> gp)" per available option.
		menuPrices.clear();
		getMenuEntries().clear();
		for (PriceOption opt : options)
		{
			String optionText = "Set " + opt.label + " (" + formatGp(opt.price) + " gp)";
			getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, optionText, TARGET));
			menuPrices.put(optionText, opt.price);
		}

		// Draw the visible card.
		panel.getChildren().clear();
		panel.setPreferredSize(new Dimension(180, 0));

		String title = data != null ? truncate(data.name, 24) : "07Flip";
		panel.getChildren().add(TitleComponent.builder()
			.text(title)
			.color(HEADER)
			.build());

		for (PriceOption opt : options)
		{
			panel.getChildren().add(LineComponent.builder()
				.left(opt.label)
				.leftColor(INFO_GRAY)
				.right(formatGp(opt.price))
				.rightColor(opt.color)
				.build());
		}

		panel.getChildren().add(LineComponent.builder()
			.left(isBuy ? "BUY" : "SELL")
			.leftColor(isBuy ? BUY_RED : SELL_GREEN)
			.right("right-click")
			.rightColor(INFO_GRAY)
			.build());

		return panel.render(graphics);
	}

	private static List<PriceOption> collectOptions(TrackedItemData data, long queuedPrice, boolean isBuy)
	{
		List<PriceOption> result = new ArrayList<>();

		// Queued price (from panel right-click) shown first when present and not duplicated below.
		if (queuedPrice > 0)
		{
			result.add(new PriceOption("Queued", queuedPrice, HEADER));
		}

		if (data == null)
		{
			return result;
		}

		if (isBuy)
		{
			if (data.flipBuyPrice  != null) addUnique(result, "Flip",  data.flipBuyPrice,  BUY_RED);
			if (data.dipBuyPrice   != null) addUnique(result, "Dip",   data.dipBuyPrice,   BUY_RED);
			if (data.spikeBuyPrice != null) addUnique(result, "Spike", data.spikeBuyPrice, BUY_RED);
		}
		else
		{
			if (data.flipSellPrice   != null) addUnique(result, "Flip",  data.flipSellPrice,   SELL_GREEN);
			if (data.alertSellTarget != null) addUnique(result, "Alert", data.alertSellTarget, SELL_GREEN);
			if (data.dumpSellPrice   != null) addUnique(result, "Dump",  data.dumpSellPrice,   SELL_GREEN);
		}

		return result;
	}

	private static void addUnique(List<PriceOption> list, String label, long price, Color color)
	{
		for (PriceOption existing : list)
		{
			if (existing.price == price)
			{
				return;
			}
		}
		list.add(new PriceOption(label, price, color));
	}

	private static String formatGp(long amount)
	{
		return String.format("%,d", amount);
	}

	private static String truncate(String s, int max)
	{
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}

	private static final class PriceOption
	{
		final String label;
		final long   price;
		final Color  color;

		PriceOption(String label, long price, Color color)
		{
			this.label = label;
			this.price = price;
			this.color = color;
		}
	}
}
