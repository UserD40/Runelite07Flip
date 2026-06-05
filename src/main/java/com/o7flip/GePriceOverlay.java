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

	/**
	 * Most recently rendered mini-chart, keyed by the data it was built from
	 * so render() can reuse the BufferedImage across frames instead of
	 * re-rasterising. Overlay render() runs every frame — without this the
	 * chart would burn ~1ms each tick painting the same pixels.
	 */
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

		// 07Flip recommended prices are a premium feature, so the whole price
		// overlay is hidden for non-premium users. The panel right-click still
		// auto-fills the live buy price; this overlay (rec prices, score, chart)
		// is premium-only.
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

		// Buy = lower side (price to place a buy offer at).
		// Sell = higher side (price to place a sell offer at).
		Long buyPrice  = data != null ? firstNonNull(data.flipBuyPrice,  data.spikeBuyPrice, data.dumpBuyPrice) : null;
		Long sellPrice = data != null ? firstNonNull(data.flipSellPrice, data.dumpSellPrice) : null;
		String displayName = data != null ? data.name : null;

		// Fall back to the dedicated /recommended-prices endpoint for items
		// that aren't in the current Flips/Spikes/Dumps lists. Async — first
		// hover triggers a fetch and the next render gets cached values.
		if (buyPrice == null || sellPrice == null)
		{
			com.o7flip.model.RecommendedPrices rp = plugin.getRecommendedPrices(currentItemId);
			if (rp != null && rp.hasPrices())
			{
				if (buyPrice == null)
				{
					buyPrice = rp.recBuyPrice;
				}
				if (sellPrice == null)
				{
					sellPrice = rp.recSellPrice;
				}
			}
		}

		// Frozen sell pins 07Flip's rec_sell at buy time so the projected margin
		// survives a market DROP — but it acts only as a FLOOR. If the live
		// market has risen above the lock, show the higher live price instead so
		// the user doesn't leave gp on the table (matches the sell-box auto-fill,
		// which uses max(frozen, live)). Premium-only: the freeze is a paid
		// feature, so free users always see the live market sell price.
		boolean isPremium = plugin.panel != null && plugin.panel.isPremium();
		Long frozenSell = isPremium ? plugin.getFrozenSell(currentItemId) : null;
		Long liveSell   = sellPrice;
		boolean sellIsFrozen = false;
		if (frozenSell != null)
		{
			if (liveSell != null && liveSell > frozenSell)
			{
				// Market rose above the lock — take the higher live price and
				// drop the "locked" framing (we're intentionally above the lock).
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

		// Refresh menu entries — at most one Buy and one Sell entry. When auto-
		// fill is disabled the overlay stays purely informational: we still show
		// the Buy/Sell price rows but add no "Set price" menu options, since the
		// fill they'd trigger is a no-op (autoFillPriceInput early-returns).
		boolean allowFill = config.autoFillGePrice();
		menuPrices.clear();
		getMenuEntries().clear();

		panel.getChildren().clear();
		panel.setPreferredSize(new Dimension(180, 0));

		// Only show the title when we have a resolved item name. Skipping the
		// "07Flip" fallback keeps the overlay one row shorter when the item
		// isn't in any tracked list and the user hasn't loaded its insights
		// yet — the GE setup screen below already labels the item, so the
		// title is redundant in that state.
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
				String optionText = "Set buy price (" + formatGp(buyPrice) + " gp)";
				getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, optionText, TARGET));
				menuPrices.put(optionText, buyPrice);
			}

			panel.getChildren().add(LineComponent.builder()
				.left("Buy")
				.leftColor(BUY_RED)
				.right(formatGp(buyPrice))
				.rightColor(INFO_GRAY)
				.build());
		}

		if (sellPrice != null)
		{
			String label = sellIsFrozen ? "Sell (locked)" : "Sell";
			if (allowFill)
			{
				String optionText = (sellIsFrozen ? "Set locked sell price (" : "Set sell price (")
					+ formatGp(sellPrice) + " gp)";
				getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, optionText, TARGET));
				menuPrices.put(optionText, sellPrice);
			}

			panel.getChildren().add(LineComponent.builder()
				.left(label)
				.leftColor(sellIsFrozen ? HEADER : SELL_GREEN)
				.right(formatGp(sellPrice))
				.rightColor(sellIsFrozen ? HEADER : INFO_GRAY)
				.build());

			// When the sell price is the locked-from-buy price, surface the
			// reasoning + the per-item margin. The margin reconciles with the
			// two rows shown above it: (locked sell) − (recommended buy) − GE
			// tax, so the number is self-consistent with what's on screen rather
			// than diverging against a blended open-position cost basis.
			if (sellIsFrozen)
			{
				panel.getChildren().add(LineComponent.builder()
					.left("Locked at your buy")
					.leftColor(INFO_GRAY)
					.right("")
					.build());

				if (buyPrice != null && buyPrice > 0)
				{
					// After-tax margin: subtract the GE sell tax (2%, capped at
					// 5M/item, exempt under 100 gp) so the figure reflects what
					// actually lands in the coffer, not the gross spread. Without
					// this the overlay overstated the per-item profit by the tax.
					long sellTax = com.o7flip.util.ProfitCalculator.geTaxFor(currentItemId, sellPrice, 1);
					long marginPerItem = sellPrice - buyPrice - sellTax;
					String sign = marginPerItem >= 0 ? "+" : "";
					panel.getChildren().add(LineComponent.builder()
						.left("Margin / item")
						.leftColor(INFO_GRAY)
						.right(sign + formatGp(marginPerItem))
						.rightColor(marginPerItem >= 0 ? SELL_GREEN : BUY_RED)
						.build());
				}
			}
		}

		// Below the prices: pull in cached item insights — score, hourly
		// volume, and a compact 24h chart. Fetched lazily by the plugin and
		// served from cache on subsequent frames; missing data simply omits
		// the row(s) so the overlay degrades gracefully.
		com.o7flip.model.ItemInsights insights = plugin.getOverlayInsights(currentItemId);
		appendInsightsRows(insights, currentItemId);
		appendInsightsChart(insights, currentItemId);

		return panel.render(graphics);
	}

	/**
	 * Adds score + hourly volume rows when the insights data carries them.
	 * Skipped entirely when both fields are null so the overlay doesn't grow
	 * a "Score —" placeholder for items the server has no signal on.
	 */
	private void appendInsightsRows(com.o7flip.model.ItemInsights insights, int itemId)
	{
		// Score: prefer the same flip07Score the Flips panel displays — that's
		// the merchant score (0-100). ItemInsights.score.confidence is a
		// different metric from /v2/item/{id} so using it here would create
		// a visible inconsistency between the panel and the overlay.
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
				.right(formatGp(buyLimit))
				.rightColor(INFO_GRAY)
				.build());
		}
		if (wantVolume)
		{
			panel.getChildren().add(LineComponent.builder()
				.left("Volume / h")
				.leftColor(INFO_GRAY)
				.right(formatGp(hourlyVol))
				.rightColor(INFO_GRAY)
				.build());
		}
	}

	/**
	 * Looks up the 07Flip merchant score for an item from the currently-loaded
	 * Flips list. Returns null when the item isn't a tracked top flip — the
	 * overlay then omits the Score row entirely rather than fall back to a
	 * different metric that would disagree with what the Flips panel shows.
	 */
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

	/**
	 * Renders the 24h buy/sell sparkline as an {@link ImageComponent} below
	 * the data rows. Image is cached between frames keyed by item + a hash
	 * of the data arrays so we only re-rasterise when the cached series
	 * actually changes (the chart paints sub-millisecond, but every saved
	 * paint multiplied by 60fps adds up while the overlay is on screen).
	 */
	private void appendInsightsChart(com.o7flip.model.ItemInsights insights, int itemId)
	{
		if (insights == null || !config.showGeOverlayChart())
		{
			return;
		}
		Long[] buy  = insights.sparkline24hBuy;
		Long[] sell = insights.sparkline24hSell;
		if ((buy == null || buy.length == 0) && (sell == null || sell.length == 0))
		{
			return;
		}
		int dataHash = java.util.Arrays.deepHashCode(new Object[]{buy, sell});
		if (cachedChartImage == null || cachedChartItemId != itemId || cachedChartDataHash != dataHash)
		{
			cachedChartImage   = com.o7flip.ui.MiniChart.render(CHART_W, CHART_H, buy, sell);
			cachedChartItemId  = itemId;
			cachedChartDataHash = dataHash;
		}
		panel.getChildren().add(new ImageComponent(cachedChartImage));
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
