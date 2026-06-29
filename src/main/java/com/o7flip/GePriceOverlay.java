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
import com.o7flip.ui.FlipItemPanel;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

public class GePriceOverlay extends Overlay
{
	private static final Color HEADER     = new Color(0xFFD700);
	private static final Color BUY_RED    = new Color(0xFF7070);
	private static final Color SELL_GREEN = new Color(0x00C27A);
	private static final Color INFO_GRAY  = new Color(0xC0C0C0);

	static final String TARGET = "07Flip price";

	private final Client client;
	private final O7FlipPlugin plugin;
	private final O7FlipConfig config;
	private final PanelComponent panel = new PanelComponent();

	private final Map<String, Long> menuPrices = new LinkedHashMap<>();

	private int cachedChartItemId = -1;
	private int cachedChartDataHash = 0;
	private BufferedImage cachedChartImage = null;

	private static final int CHART_W = 180;
	private static final int CHART_H = 38;

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

		if (plugin.panel == null || !plugin.panel.isPremium())
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
		String displayName = data != null ? data.name : null;
		boolean isPremium = plugin.panel != null && plugin.panel.isPremium();

		Long buyPrice  = null;
		Long sellPrice = null;
		com.o7flip.model.ItemInsights ins = plugin.getOverlayInsights(currentItemId);
		if (ins != null && ins.current != null)
		{
			if (displayName == null)
			{
				displayName = ins.name;
			}
			if (isPremium)
			{
				if (ins.current.recBuy != null && ins.current.recBuy > 0)   buyPrice  = ins.current.recBuy;
				if (ins.current.recSell != null && ins.current.recSell > 0) sellPrice = ins.current.recSell;
			}
			else
			{
				if (ins.current.buyPrice > 0)  buyPrice  = ins.current.buyPrice;
				if (ins.current.sellPrice > 0) sellPrice = ins.current.sellPrice;
			}
		}
		if (buyPrice == null && data != null)
		{
			buyPrice = firstNonNull(data.flipBuyPrice, data.dumpBuyPrice);
		}
		if (sellPrice == null && data != null)
		{
			sellPrice = firstNonNull(data.flipSellPrice, data.dumpSellPrice);
		}

		Long frozenSell = isPremium ? plugin.getFrozenSell(currentItemId) : null;
		Long liveSell   = sellPrice;
		boolean sellIsFrozen = false;
		if (frozenSell != null)
		{
			if (liveSell != null && liveSell > frozenSell)
			{
				sellPrice = liveSell;
			}
			else
			{
				sellPrice = frozenSell;
				sellIsFrozen = true;
			}
		}

		if (buyPrice == null && sellPrice == null)
		{
			return null;
		}

		boolean allowFill = config.autoFillGePrice();
		menuPrices.clear();
		getMenuEntries().clear();

		panel.getChildren().clear();
		panel.setPreferredSize(new Dimension(180, 0));

		if (displayName != null)
		{
			panel.getChildren().add(TitleComponent.builder()
				.text(truncate(displayName, 24))
				.color(HEADER)
				.build());
		}

		if (buyPrice != null)
		{
			if (allowFill)
			{
				String optionText = "Set buy price (" + FlipItemPanel.formatGp(buyPrice) + " gp)";
				getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, optionText, TARGET));
				menuPrices.put(optionText, buyPrice);
			}

			panel.getChildren().add(LineComponent.builder()
				.left("Buy")
				.leftColor(BUY_RED)
				.right(FlipItemPanel.formatGp(buyPrice))
				.rightColor(INFO_GRAY)
				.build());
		}

		if (sellPrice != null)
		{
			String label = sellIsFrozen ? "Sell (locked)" : "Sell";
			if (allowFill)
			{
				String optionText = (sellIsFrozen ? "Set locked sell price (" : "Set sell price (")
					+ FlipItemPanel.formatGp(sellPrice) + " gp)";
				getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, optionText, TARGET));
				menuPrices.put(optionText, sellPrice);
			}

			panel.getChildren().add(LineComponent.builder()
				.left(label)
				.leftColor(sellIsFrozen ? HEADER : SELL_GREEN)
				.right(FlipItemPanel.formatGp(sellPrice))
				.rightColor(sellIsFrozen ? HEADER : INFO_GRAY)
				.build());

			if (sellIsFrozen)
			{
				panel.getChildren().add(LineComponent.builder()
					.left("Locked at your buy")
					.leftColor(INFO_GRAY)
					.right("")
					.build());

				if (buyPrice != null && buyPrice > 0)
				{
					long sellTax = com.o7flip.util.ProfitCalculator.geTaxFor(currentItemId, sellPrice, 1);
					long marginPerItem = sellPrice - buyPrice - sellTax;
					String sign = marginPerItem >= 0 ? "+" : "";
					panel.getChildren().add(LineComponent.builder()
						.left("Margin / item")
						.leftColor(INFO_GRAY)
						.right(sign + FlipItemPanel.formatGp(marginPerItem))
						.rightColor(marginPerItem >= 0 ? SELL_GREEN : BUY_RED)
						.build());
				}
			}
		}

		com.o7flip.model.ItemInsights insights = plugin.getOverlayInsights(currentItemId);
		appendInsightsRows(insights, currentItemId);
		appendInsightsChart(insights, currentItemId);

		return panel.render(graphics);
	}

	private void appendInsightsRows(com.o7flip.model.ItemInsights insights, int itemId)
	{
		Integer score = lookupFlip07Score(itemId);

		int hourlyVol = insights != null && insights.volume != null ? insights.volume.hourly : 0;
		int buyLimit  = insights != null ? insights.buyLimit : 0;

		boolean wantScore    = config.showGeOverlayScore()  && score != null;
		boolean wantVolume   = config.showGeOverlayVolume() && hourlyVol > 0;
		boolean wantBuyLimit = config.showGeOverlayBuyLimit() && buyLimit > 0;

		if (!wantScore && !wantVolume && !wantBuyLimit)
		{
			return;
		}
		if (wantScore)
		{
			Color scoreColor = score >= 70 ? SELL_GREEN
				: score >= 40 ? new Color(0xE8A838) : BUY_RED;
			panel.getChildren().add(LineComponent.builder()
				.left("Score")
				.leftColor(INFO_GRAY)
				.right(String.valueOf(score))
				.rightColor(scoreColor)
				.build());
		}
		if (wantBuyLimit)
		{
			panel.getChildren().add(LineComponent.builder()
				.left("Buy limit")
				.leftColor(INFO_GRAY)
				.right(FlipItemPanel.formatGp(buyLimit))
				.rightColor(INFO_GRAY)
				.build());
		}
		if (wantVolume)
		{
			panel.getChildren().add(LineComponent.builder()
				.left("Volume / h")
				.leftColor(INFO_GRAY)
				.right(FlipItemPanel.formatGp(hourlyVol))
				.rightColor(INFO_GRAY)
				.build());
		}
	}

	private Integer lookupFlip07Score(int itemId)
	{
		for (com.o7flip.model.FlipItem f : plugin.lastFlips)
		{
			if (f.itemId == itemId)
			{
				return f.flip07Score;
			}
		}
		return null;
	}

	private void appendInsightsChart(com.o7flip.model.ItemInsights insights, int itemId)
	{
		if (insights == null || !config.showGeOverlayChart())
		{
			return;
		}
		String period = plugin.selectedChartPeriod;
		if (period == null)
		{
			period = config.defaultChartPeriod().chartLabel();
		}
		Long[] buy  = sparklineBuyFor(insights, period);
		Long[] sell = sparklineSellFor(insights, period);
		if ((buy == null || buy.length == 0) && (sell == null || sell.length == 0))
		{
			period = "24h";
			buy  = insights.sparkline24hBuy;
			sell = insights.sparkline24hSell;
		}
		if ((buy == null || buy.length == 0) && (sell == null || sell.length == 0))
		{
			return;
		}
		int dataHash = java.util.Arrays.deepHashCode(new Object[]{buy, sell, period});
		if (cachedChartImage == null || cachedChartItemId != itemId || cachedChartDataHash != dataHash)
		{
			cachedChartImage   = com.o7flip.ui.MiniChart.render(CHART_W, CHART_H, buy, sell);
			cachedChartItemId  = itemId;
			cachedChartDataHash = dataHash;
		}
		panel.getChildren().add(new ImageComponent(cachedChartImage));
	}

	private static Long[] sparklineBuyFor(com.o7flip.model.ItemInsights ins, String period)
	{
		switch (period)
		{
			case "2h":  return ins.sparkline2hBuy;
			case "4h":  return ins.sparkline4hBuy;
			case "7d":  return ins.sparkline7dBuy;
			case "30d": return ins.sparkline30dBuy;
			default:    return ins.sparkline24hBuy;
		}
	}

	private static Long[] sparklineSellFor(com.o7flip.model.ItemInsights ins, String period)
	{
		switch (period)
		{
			case "2h":  return ins.sparkline2hSell;
			case "4h":  return ins.sparkline4hSell;
			case "7d":  return ins.sparkline7dSell;
			case "30d": return ins.sparkline30dSell;
			default:    return ins.sparkline24hSell;
		}
	}

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

	private static String truncate(String s, int max)
	{
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}
}
