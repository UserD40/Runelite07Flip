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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.o7flip.model.AlertItem;
import com.o7flip.model.BarrowsSet;
import com.o7flip.model.DipItem;
import com.o7flip.model.DumpItem;
import com.o7flip.model.FlipItem;
import com.o7flip.model.SpikeItem;
import com.o7flip.model.TrackedItemData;
import com.o7flip.model.TradeRecord;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.Notifier;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
	name = "07Flip - GE Flip Finder",
	description = "Live GE flips, price dump signals, Barrows/Moon repair profits, decanting, and price alerts from 07flip.com",
	tags = {"flipping", "grand exchange", "ge", "money making", "merching", "barrows", "decanting", "07flip"}
)
public class O7FlipPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(O7FlipPlugin.class);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Notifier notifier;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private O7FlipConfig config;

	@Inject
	private O7FlipApiClient apiClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private O7FlipOverlay geOverlay;

	@Inject
	private Gson gson;

	@Inject
	private net.runelite.client.config.ConfigManager configManager;

	private O7FlipPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> refreshTask;

	// Barrows/Moon/Decanting change with GE prices (hourly), not every minute.
	// Only refresh them every SLOW_EVERY cycles to reduce server load.
	private static final int SLOW_EVERY = 5;
	private int slowTick = 0;

	// -------------------------------------------------------------------------
	// Pending GE pre-fill state (set by panel right-click, cleared after use)
	// -------------------------------------------------------------------------

	// Buy flow: set on right-click, consumed when offer container becomes visible
	volatile int    pendingGeBuyItemId = -1;
	volatile long   pendingGeBuyPrice  = -1;
	volatile String pendingGeBuyName   = null;

	// Sell flow: set on right-click, consumed when GE_OFFERS_SETUP_BUILD matches item + sell type
	volatile int    pendingGeSellItemId = -1;
	volatile long   pendingGeSellPrice  = -1;
	volatile String pendingGeSellName   = null;

	// Phase 2: price to highlight once GE_OFFERS_SETUP_BUILD fires (buy or sell).
	// pendingGeSetItemId guards against the user selecting a different item from search —
	// we only arm the highlight if GE_OFFERS_SETUP_BUILD reports the item we queued.
	volatile long   pendingGeSetPrice  = -1;
	volatile int    pendingGeSetItemId = -1;
	// Phase 3: price to input once the chatbox opens (script 108)
	volatile long   pendingGeInputPrice = -1;

	// -------------------------------------------------------------------------
	// GE integration — shared volatile state
	// -------------------------------------------------------------------------

	/** Per-tab last-fetched lists. Written on executor thread only. */
	private List<FlipItem>  lastFlips  = Collections.emptyList();
	private List<AlertItem> lastAlerts = Collections.emptyList();
	private List<DipItem>   lastDips   = Collections.emptyList();
	private List<DumpItem>  lastDumps  = Collections.emptyList();
	private List<SpikeItem> lastSpikes = Collections.emptyList();

	/** Aggregated lookup map by item ID. Volatile reference swap on each rebuild. */
	public volatile Map<Integer, TrackedItemData> trackedItems = Collections.emptyMap();

	/** Item IDs currently in the player's inventory. Volatile reference swap. */
	public volatile Set<Integer> inventoryItemIds = Collections.emptySet();

	/** Active GE offers keyed by slot index. Volatile reference swap. */
	public volatile Map<Integer, GrandExchangeOffer> activeOffers = Collections.emptyMap();

	/** Previous offer state per slot — used to detect buy/sell completions. Game-thread only. */
	private final Map<Integer, GrandExchangeOfferState> prevSlotStates = new HashMap<>();

	/** Completed trade history (oldest first). Volatile reference swap. */
	public volatile List<TradeRecord> tradeHistory = Collections.emptyList();

	private static final int MAX_TRADE_HISTORY = 200;
	private static final String TRADE_HISTORY_KEY = "tradeHistory";

	/** Called by item panels on right-click to queue a GE buy pre-fill. */
	public void queueGeBuy(int itemId, long price, String name)
	{
		log.debug("[07Flip] GE buy queued: {} ({}) @ {}", name, itemId, price);
		clientThread.invokeLater(() ->
		{
			// GE_ITEM_SEARCH only works when a buy slot is active (offer container visible).
			Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
			if (offerContainer != null && !offerContainer.isHidden())
			{
				fillGeBuyOffer(itemId, price, name);
			}
			else
			{
				pendingGeBuyItemId = itemId;
				pendingGeBuyPrice  = price;
				pendingGeBuyName   = name;
				notifier.notify("Open the Grand Exchange, click an empty buy slot, then your offer will pre-fill for " + name);
			}
		});
	}

	/** Called by item panels on right-click to queue a GE sell price pre-fill. */
	public void queueGeSell(int itemId, long price, String name)
	{
		log.debug("[07Flip] GE sell queued: {} ({}) @ {}", name, itemId, price);
		pendingGeSellItemId = itemId;
		pendingGeSellPrice  = price;
		pendingGeSellName   = name;
		notifier.notify("Open GE \u2192 click a sell slot \u2192 select " + name + " from inventory \u2014 click the highlighted price button");
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(O7FlipPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("07Flip - GE Flip Finder")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(geOverlay);

		loadTradeHistory();

		executor = Executors.newSingleThreadScheduledExecutor();
		fetchAuthStatus();
		executor.execute(() -> fetchAll(true)); // forced — panel not yet visible at startup
		refreshTask = executor.scheduleAtFixedRate(
			() -> fetchAll(false),
			config.refreshIntervalSeconds(),
			config.refreshIntervalSeconds(),
			TimeUnit.SECONDS
		);
		log.info("[07Flip] Started, refreshing every {}s", config.refreshIntervalSeconds());
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(true);
		}
		if (executor != null)
		{
			executor.shutdown();
		}
		overlayManager.remove(geOverlay);
		clientToolbar.removeNavigation(navButton);
		log.info("[07Flip] Stopped");
	}

	// -------------------------------------------------------------------------
	// GE auto-fill — fires when the Grand Exchange interface opens
	// -------------------------------------------------------------------------

	// onWidgetLoaded is intentionally NOT used for GE pre-fill because clicking a buy slot
	// only toggles widget visibility within the already-loaded GRAND_EXCHANGE interface —
	// it does NOT re-fire WidgetLoaded. onGameTick polls instead.

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (pendingGeBuyItemId == -1)
		{
			return;
		}
		Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
		if (offerContainer == null || offerContainer.isHidden())
		{
			return;
		}
		final int    itemId = pendingGeBuyItemId;
		final long   price  = pendingGeBuyPrice;
		final String name   = pendingGeBuyName;
		pendingGeBuyItemId = -1;
		pendingGeBuyPrice  = -1;
		pendingGeBuyName   = null;
		fillGeBuyOffer(itemId, price, name);
	}

	// GE search mode integer used by MESLAYERMODE to indicate an active GE item search.
	private static final int GE_SEARCH_MODE = 14;

	private void fillGeBuyOffer(int itemId, long price, String name)
	{
		// Pre-fill the search text, then trigger the search by running the chatbox
		// input widget's own key-listener script — the same mechanism GE Filters uses.
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, name);
		client.setVarcIntValue(VarClientID.MESLAYERMODE, GE_SEARCH_MODE);
		Widget searchBox = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (searchBox == null)
		{
			log.debug("[07Flip] GE search box widget not found");
			return;
		}
		Object[] scriptArgs = searchBox.getOnKeyListener();
		if (scriptArgs == null)
		{
			log.debug("[07Flip] GE search box has no key listener");
			return;
		}
		// Store the price + itemId so onScriptPostFired(GE_OFFERS_SETUP_BUILD) can arm the
		// highlight only if the user ends up selecting the item we searched for. Without
		// the itemId guard, any item chosen from search would trigger the highlight.
		pendingGeSetPrice  = price;
		pendingGeSetItemId = itemId;
		client.runScript(scriptArgs);
	}

	// Script ID 108 fires when the GE price chatbox input opens (after clicking "Enter price").
	private static final int SCRIPT_CHATBOX_INPUT_OPEN = 108;

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// Phase 2: item was selected in GE search — highlight the "Enter price" button.
		if (event.getScriptId() == ScriptID.GE_OFFERS_SETUP_BUILD)
		{
			int currentItemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
			long price = -1;

			if (pendingGeSetPrice != -1)
			{
				// Buy flow: only arm the highlight if the user actually selected the
				// item we queued — otherwise the yellow highlight would show on any
				// item they pick from search.
				if (pendingGeSetItemId == -1 || currentItemId == pendingGeSetItemId)
				{
					price = pendingGeSetPrice;
				}
				pendingGeSetPrice  = -1;
				pendingGeSetItemId = -1;
			}
			else if (pendingGeSellItemId != -1)
			{
				// Sell flow: check that the item and offer type match.
				int offerType = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE);
				if (offerType == 0 && currentItemId == pendingGeSellItemId)
				{
					price = pendingGeSellPrice;
					pendingGeSellItemId = -1;
					pendingGeSellPrice  = -1;
					pendingGeSellName   = null;
				}
			}

			if (price != -1)
			{
				pendingGeInputPrice = price;
			}

			return;
		}

		// Phase 3: chatbox input opened for a GE price entry — render a clickable
		// 07Flip price card inside the chatbox for the tracked item. The user
		// clicks any price row to fill the input field.
		if (event.getScriptId() == SCRIPT_CHATBOX_INPUT_OPEN)
		{
			Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
			if (setup == null || setup.isHidden())
			{
				pendingGeInputPrice = -1;
				return;
			}

			final int currentItemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
			final TrackedItemData data = trackedItems.get(currentItemId);
			final long queuedPrice = pendingGeInputPrice;
			pendingGeInputPrice = -1;

			// Offer type governs which prices are relevant: buyers want the lowest
			// buy price, sellers want the highest sell price. Sell == 0, buy == 1.
			final boolean isBuy = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) != 0;

			// Skip if the user has disabled the card AND there's no queued price to honour.
			if (!config.showGeOfferOverlay() && queuedPrice == -1)
			{
				return;
			}
			// Nothing to show if the item isn't tracked and nothing was queued.
			if (data == null && queuedPrice == -1)
			{
				return;
			}

			clientThread.invokeLater(() -> renderChatboxPriceCard(data, queuedPrice, isBuy));
		}
	}

	// -------------------------------------------------------------------------
	// Chatbox price card — rendered inside the GE "Enter price" chatbox input
	// -------------------------------------------------------------------------

	private static final int CHAT_ROW_H       = 14;
	private static final int COLOR_HEADER     = 0xffd700;
	private static final int COLOR_BUY        = 0xff7070;
	private static final int COLOR_SELL       = 0x00c27a;
	private static final int COLOR_INFO       = 0xc0c0c0;
	private static final int COLOR_QUEUED     = 0xffd700;

	private void renderChatboxPriceCard(TrackedItemData data, long queuedPrice, boolean isBuy)
	{
		Widget chatbox = client.getWidget(ComponentID.CHATBOX_CONTAINER);
		if (chatbox == null)
		{
			return;
		}

		List<String[]> rows = buildChatboxRows(data, queuedPrice, isBuy);
		if (rows.isEmpty())
		{
			return;
		}

		// Bottom-left corner of the chatbox — empty space in the "Enter price"
		// prompt that doesn't collide with GE Tracker (which anchors top-left).
		int cardWidth  = 180;
		int cardX      = 6;
		int totalH     = rows.size() * CHAT_ROW_H;
		int y          = chatbox.getHeight() - totalH - 6;
		for (String[] row : rows)
		{
			// row[0]=text, row[1]=color hex, row[2]=click price ("" = not clickable)
			Widget w = chatbox.createChild(-1, WidgetType.TEXT);
			w.setOriginalX(cardX);
			w.setOriginalY(y);
			w.setOriginalWidth(cardWidth);
			w.setOriginalHeight(CHAT_ROW_H);
			w.setText(row[0]);
			w.setTextColor(Integer.parseInt(row[1], 16));
			w.setXTextAlignment(0); // left-aligned
			if (row[2] != null && !row[2].isEmpty())
			{
				final long clickPrice = Long.parseLong(row[2]);
				w.setAction(0, "Set price");
				w.setHasListener(true);
				w.setOnOpListener((JavaScriptCallback) e ->
				{
					Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
					if (input != null)
					{
						input.setText(clickPrice + "*");
						client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(clickPrice));
					}
				});
			}
			w.revalidate();
			y += CHAT_ROW_H;
		}
	}

	private List<String[]> buildChatboxRows(TrackedItemData data, long queuedPrice, boolean isBuy)
	{
		List<String[]> rows = new ArrayList<>();

		String header = "07Flip" + (data != null ? " — " + data.name : "")
			+ (isBuy ? " (buy)" : " (sell)");
		rows.add(new String[]{header, hex(COLOR_HEADER), ""});

		// Queued price from the right-click flow gets its own emphasised row.
		if (queuedPrice != -1)
		{
			rows.add(new String[]{
				"Click to set " + String.format("%,d", queuedPrice) + " gp",
				hex(COLOR_QUEUED),
				String.valueOf(queuedPrice)
			});
		}

		if (data != null)
		{
			if (isBuy)
			{
				// Only buy-relevant prices: the user is paying, so they want the lowest.
				if (data.flipBuyPrice != null)
				{
					rows.add(new String[]{
						"Buy: " + String.format("%,d", data.flipBuyPrice) + " gp",
						hex(COLOR_BUY),
						String.valueOf(data.flipBuyPrice)
					});
				}
				if (data.dipBuyPrice != null)
				{
					rows.add(new String[]{
						"Dip: " + String.format("%,d", data.dipBuyPrice) + " gp",
						hex(COLOR_BUY),
						String.valueOf(data.dipBuyPrice)
					});
				}
				if (data.spikeBuyPrice != null)
				{
					rows.add(new String[]{
						"Spike: " + String.format("%,d", data.spikeBuyPrice) + " gp",
						hex(COLOR_BUY),
						String.valueOf(data.spikeBuyPrice)
					});
				}
				if (data.flipRoiPct != null)
				{
					rows.add(new String[]{
						String.format("ROI: %.2f%%", data.flipRoiPct),
						hex(COLOR_INFO),
						""
					});
				}
			}
			else
			{
				// Only sell-relevant prices: the user wants the highest.
				if (data.flipSellPrice != null)
				{
					rows.add(new String[]{
						"Sell: " + String.format("%,d", data.flipSellPrice) + " gp",
						hex(COLOR_SELL),
						String.valueOf(data.flipSellPrice)
					});
				}
				if (data.alertSellTarget != null)
				{
					rows.add(new String[]{
						"Alert target: " + String.format("%,d", data.alertSellTarget) + " gp",
						hex(COLOR_SELL),
						String.valueOf(data.alertSellTarget)
					});
				}
				if (data.dumpSellPrice != null)
				{
					rows.add(new String[]{
						"Dump sell: " + String.format("%,d", data.dumpSellPrice) + " gp",
						hex(COLOR_SELL),
						String.valueOf(data.dumpSellPrice)
					});
				}
			}
		}

		return rows;
	}

	private static String hex(int color)
	{
		return Integer.toHexString(color);
	}

	// -------------------------------------------------------------------------
	// Config changes — rebuild tabs when visibility toggles change
	// -------------------------------------------------------------------------

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"o7flip".equals(event.getGroup()))
		{
			return;
		}
		// Re-check auth only when the API key itself changes.
		if ("apiKey".equals(event.getKey()))
		{
			executor.execute(this::fetchAuthStatus);
		}
		// Re-fetch repair costs immediately when smithing level changes —
		// but no need to rebuild tabs for that setting.
		if ("smithingLevel".equals(event.getKey()))
		{
			executor.execute(this::fetchSlow);
			return;
		}
		SwingUtilities.invokeLater(() -> panel.rebuildTabs());
	}

	// -------------------------------------------------------------------------
	// Auth
	// -------------------------------------------------------------------------

	void fetchAuthStatus()
	{
		String key = config.apiKey();
		if (key == null || key.trim().isEmpty())
		{
			SwingUtilities.invokeLater(() -> panel.updateAuthStatus(false, false));
			return;
		}
		apiClient.fetchAuthStatus(status ->
			SwingUtilities.invokeLater(() -> panel.updateAuthStatus(status.authenticated, status.premium)));
	}

	// -------------------------------------------------------------------------
	// Full refresh (scheduled + on startup)
	// -------------------------------------------------------------------------

	void fetchAll(boolean forced)
	{
		// Skip entirely if the panel is not visible — no point fetching data nobody is looking at.
		// The forced flag bypasses this on startup when the panel isn't yet in the component tree.
		if (!forced && !panel.isShowing())
		{
			return;
		}

		// Back off if the server returned 429 recently — let the window expire before retrying.
		if (apiClient.isRateLimited())
		{
			return;
		}

		SwingUtilities.invokeLater(() -> panel.setLoading(true));

		// Build the bundle sections object — only include tabs the user has enabled.
		JsonObject sections = new JsonObject();

		if (config.showFlips())
		{
			JsonObject p = new JsonObject();
			String preset = panel.getSelectedPreset();
			if (preset != null && !preset.isEmpty())
			{
				p.addProperty("preset", preset);
			}
			long minProfit = panel.getFlipsMinProfit();
			if (minProfit > 0)
			{
				p.addProperty("minProfit", minProfit);
			}
			long priceMin = panel.getFlipsPriceMin();
			if (priceMin > 0)
			{
				p.addProperty("priceMin", priceMin);
			}
			long priceMax = panel.getFlipsPriceMax();
			if (priceMax < Long.MAX_VALUE)
			{
				p.addProperty("priceMax", priceMax);
			}
			p.addProperty("page", panel.getFlipsPage());
			sections.add("flips", p);
		}

		if (config.showSpikes())
		{
			JsonObject p = new JsonObject();
			String sort = panel.getSpikesSortKey();
			if (sort != null && !sort.isEmpty())
			{
				p.addProperty("sort", sort);
			}
			p.addProperty("page", panel.getSpikesPage());
			sections.add("spikes", p);
		}

		if (config.showDips())
		{
			JsonObject p = new JsonObject();
			String sort = panel.getDipsSortKey();
			if (sort != null && !sort.isEmpty())
			{
				p.addProperty("sort", sort);
			}
			p.addProperty("page", panel.getDipsPage());
			sections.add("dips", p);
		}

		if (config.showDumps())
		{
			JsonObject p = new JsonObject();
			String sort = panel.getDumpsSortKey();
			if (sort != null && !sort.isEmpty())
			{
				p.addProperty("sort", sort);
			}
			long minProfit = panel.getDumpsMinProfit();
			if (minProfit > 0)
			{
				p.addProperty("minProfit", minProfit);
			}
			long priceMin = panel.getDumpsPriceMin();
			if (priceMin > 0)
			{
				p.addProperty("priceMin", priceMin);
			}
			long priceMax = panel.getDumpsPriceMax();
			if (priceMax < Long.MAX_VALUE)
			{
				p.addProperty("priceMax", priceMax);
			}
			p.addProperty("page", panel.getDumpsPage());
			sections.add("dumps", p);
		}

		if (config.showAlerts())
		{
			JsonObject p = new JsonObject();
			p.addProperty("page", panel.getAlertsPage());
			sections.add("alerts", p);
		}

		// Slow sections (Barrows, Moon, Decanting) update hourly — include only every SLOW_EVERY cycles.
		slowTick++;
		boolean includeSlow = slowTick >= SLOW_EVERY;
		if (includeSlow)
		{
			slowTick = 0;
			if (config.showBarrows())
			{
				JsonObject p = new JsonObject();
				p.addProperty("smithingLevel", config.smithingLevel());
				p.addProperty("set", "all");
				sections.add("barrows", p);
			}
			if (config.showMoon())
			{
				JsonObject p = new JsonObject();
				p.addProperty("smithingLevel", config.smithingLevel());
				sections.add("moon", p);
			}
			if (config.showDecant())
			{
				sections.add("decanting", new JsonObject());
			}
		}

		final int flipsPage   = panel.getFlipsPage();
		final int spikesPage  = panel.getSpikesPage();
		final int dipsPage    = panel.getDipsPage();
		final int dumpsPage   = panel.getDumpsPage();
		final int alertsPage  = panel.getAlertsPage();

		apiClient.fetchBundle(
			sections,
			config.showFlips() ? (items, total) ->
			{
				lastFlips = items;
				rebuildTrackedItems();
				SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, flipsPage));
			} : null,
			config.showSpikes() ? (items, total) ->
			{
				lastSpikes = items;
				rebuildTrackedItems();
				SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, spikesPage));
			} : null,
			config.showDips() ? (items, total) ->
			{
				lastDips = items;
				rebuildTrackedItems();
				SwingUtilities.invokeLater(() -> panel.updateDips(items, total, dipsPage));
			} : null,
			config.showDumps() ? (items, total) ->
			{
				lastDumps = items;
				rebuildTrackedItems();
				SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, dumpsPage));
			} : null,
			config.showAlerts() ? (items, total) ->
			{
				lastAlerts = items;
				rebuildTrackedItems();
				SwingUtilities.invokeLater(() -> panel.updateAlerts(items, total, alertsPage));
			} : null,
			(config.showBarrows() && includeSlow) ? sets    -> SwingUtilities.invokeLater(() -> panel.updateBarrows(sets))    : null,
			(config.showMoon()    && includeSlow) ? sets    -> SwingUtilities.invokeLater(() -> panel.updateMoon(sets))       : null,
			(config.showDecant()  && includeSlow) ? decants -> SwingUtilities.invokeLater(() -> panel.updateDecanting(decants)) : null,
			connectUrl ->
			{
				String key = config.apiKey();
				boolean hasKey = key != null && !key.trim().isEmpty();
				SwingUtilities.invokeLater(() -> panel.updateInvalidKeyWarning(hasKey ? connectUrl : null));
			}
		);
	}

	// Called when smithingLevel config changes — fires a bundle with just the slow sections.
	void fetchSlow()
	{
		JsonObject sections = new JsonObject();
		if (config.showBarrows())
		{
			JsonObject p = new JsonObject();
			p.addProperty("smithingLevel", config.smithingLevel());
			p.addProperty("set", "all");
			sections.add("barrows", p);
		}
		if (config.showMoon())
		{
			JsonObject p = new JsonObject();
			p.addProperty("smithingLevel", config.smithingLevel());
			sections.add("moon", p);
		}
		if (config.showDecant())
		{
			sections.add("decanting", new JsonObject());
		}
		apiClient.fetchBundle(
			sections,
			null, null, null, null, null,
			config.showBarrows() ? sets    -> SwingUtilities.invokeLater(() -> panel.updateBarrows(sets))      : null,
			config.showMoon()    ? sets    -> SwingUtilities.invokeLater(() -> panel.updateMoon(sets))         : null,
			config.showDecant()  ? decants -> SwingUtilities.invokeLater(() -> panel.updateDecanting(decants)) : null,
			null
		);
	}

	// -------------------------------------------------------------------------
	// TrackedItems rebuild — called on executor thread after every API fetch
	// -------------------------------------------------------------------------

	private void rebuildTrackedItems()
	{
		Map<Integer, TrackedItemData> map = new HashMap<>();

		for (FlipItem f : lastFlips)
		{
			TrackedItemData d = map.computeIfAbsent(f.itemId, id ->
			{
				TrackedItemData t = new TrackedItemData();
				t.itemId = id;
				t.name = f.name;
				return t;
			});
			d.flipBuyPrice  = f.buyPrice;
			d.flipSellPrice = f.sellPrice;
			d.flipProfit    = f.profit;
			d.flipRoiPct    = f.roiPct;
			d.presentIn.add("Flips");
		}

		for (AlertItem a : lastAlerts)
		{
			TrackedItemData d = map.computeIfAbsent(a.itemId, id ->
			{
				TrackedItemData t = new TrackedItemData();
				t.itemId = id;
				t.name = a.name;
				return t;
			});
			d.alertCurrentPrice = a.currentPrice;
			d.alertSellTarget   = a.sellTarget;
			d.alertUpsidePct    = a.upsidePct;
			d.presentIn.add("Alerts");
		}

		for (DipItem di : lastDips)
		{
			TrackedItemData d = map.computeIfAbsent(di.itemId, id ->
			{
				TrackedItemData t = new TrackedItemData();
				t.itemId = id;
				t.name = di.name;
				return t;
			});
			d.dipBuyPrice = di.buyPrice;
			d.dipPct      = di.dipPct;
			d.presentIn.add("Dips");
		}

		for (DumpItem du : lastDumps)
		{
			TrackedItemData d = map.computeIfAbsent(du.itemId, id ->
			{
				TrackedItemData t = new TrackedItemData();
				t.itemId = id;
				t.name = du.name;
				return t;
			});
			d.dumpBuyPrice  = du.buyPrice;
			d.dumpSellPrice = du.sellPrice;
			d.dumpPct       = du.dumpPct;
			d.presentIn.add("Dumps");
		}

		for (SpikeItem s : lastSpikes)
		{
			TrackedItemData d = map.computeIfAbsent(s.itemId, id ->
			{
				TrackedItemData t = new TrackedItemData();
				t.itemId = id;
				t.name = s.name;
				return t;
			});
			d.spikeBuyPrice = s.buyPrice;
			d.presentIn.add("Spikes");
		}

		trackedItems = Collections.unmodifiableMap(map);
	}

	// -------------------------------------------------------------------------
	// Inventory tracking — keeps inventoryItemIds in sync
	// -------------------------------------------------------------------------

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		Set<Integer> next = new HashSet<>();
		for (Item item : event.getItemContainer().getItems())
		{
			if (item.getId() >= 0)
			{
				next.add(item.getId());
			}
		}
		inventoryItemIds = Collections.unmodifiableSet(next);
	}

	// -------------------------------------------------------------------------
	// GE offer tracking — keeps activeOffers in sync and records completions
	// -------------------------------------------------------------------------

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		int slot = event.getSlot();
		GrandExchangeOfferState state = offer.getState();

		// Keep activeOffers map in sync.
		Map<Integer, GrandExchangeOffer> next = new HashMap<>(activeOffers);
		if (state == GrandExchangeOfferState.EMPTY)
		{
			next.remove(slot);
		}
		else
		{
			next.put(slot, offer);
		}
		activeOffers = Collections.unmodifiableMap(next);

		// Detect completed or partially-filled transactions by comparing to the previous state.
		GrandExchangeOfferState prev = prevSlotStates.get(slot);
		if (prev == GrandExchangeOfferState.BUYING && state == GrandExchangeOfferState.BOUGHT)
		{
			recordTrade(offer, true, false);
		}
		else if (prev == GrandExchangeOfferState.SELLING && state == GrandExchangeOfferState.SOLD)
		{
			recordTrade(offer, false, false);
		}
		else if (prev == GrandExchangeOfferState.BUYING && state == GrandExchangeOfferState.CANCELLED_BUY
			&& offer.getQuantitySold() > 0)
		{
			recordTrade(offer, true, true);
		}
		else if (prev == GrandExchangeOfferState.SELLING && state == GrandExchangeOfferState.CANCELLED_SELL
			&& offer.getQuantitySold() > 0)
		{
			recordTrade(offer, false, true);
		}

		if (state == GrandExchangeOfferState.EMPTY)
		{
			prevSlotStates.remove(slot);
		}
		else
		{
			prevSlotStates.put(slot, state);
		}
	}

	private void recordTrade(GrandExchangeOffer offer, boolean isBuy, boolean partial)
	{
		TradeRecord trade = new TradeRecord();
		trade.itemId    = offer.getItemId();
		trade.name      = client.getItemDefinition(offer.getItemId()).getName();
		trade.isBuy     = isBuy;
		trade.quantity  = offer.getQuantitySold();
		trade.totalGp   = offer.getSpent();
		trade.priceEach = trade.quantity > 0 ? trade.totalGp / trade.quantity : offer.getPrice();
		trade.timestamp = System.currentTimeMillis();
		trade.partial   = partial;

		List<TradeRecord> updated = new ArrayList<>(tradeHistory);
		updated.add(trade);
		if (updated.size() > MAX_TRADE_HISTORY)
		{
			updated = updated.subList(updated.size() - MAX_TRADE_HISTORY, updated.size());
		}
		tradeHistory = Collections.unmodifiableList(updated);

		saveTradeHistory();

		final List<TradeRecord> snapshot = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snapshot));

		if (config.shareTradeData() && config.apiKey() != null && !config.apiKey().trim().isEmpty())
		{
			apiClient.postTradeRecord(trade, null);
		}
	}

	private void loadTradeHistory()
	{
		String json = configManager.getConfiguration("o7flip", TRADE_HISTORY_KEY);
		if (json == null || json.trim().isEmpty())
		{
			tradeHistory = Collections.emptyList();
		}
		else
		{
			try
			{
				TradeRecord[] records = gson.fromJson(json, TradeRecord[].class);
				List<TradeRecord> list = new ArrayList<>();
				if (records != null)
				{
					for (TradeRecord r : records)
					{
						list.add(r);
					}
				}
				tradeHistory = Collections.unmodifiableList(list);
			}
			catch (Exception e)
			{
				log.warn("[07Flip] Failed to load trade history: {}", e.getMessage());
				tradeHistory = Collections.emptyList();
			}
		}
		final List<TradeRecord> snapshot = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snapshot));
	}

	private void saveTradeHistory()
	{
		try
		{
			String json = gson.toJson(tradeHistory);
			configManager.setConfiguration("o7flip", TRADE_HISTORY_KEY, json);
		}
		catch (Exception e)
		{
			log.warn("[07Flip] Failed to save trade history: {}", e.getMessage());
		}
	}

	public void clearTradeHistory()
	{
		tradeHistory = Collections.emptyList();
		configManager.unsetConfiguration("o7flip", TRADE_HISTORY_KEY);
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(Collections.emptyList()));
	}

	// -------------------------------------------------------------------------
	// Page navigation (each call re-fetches that page from the server)
	// -------------------------------------------------------------------------

	void onFlipsPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchFlips(panel.getSelectedPreset(),
				panel.getFlipsMinProfit(), panel.getFlipsPriceMin(), panel.getFlipsPriceMax(),
				page,
				(items, total) ->
				{
					lastFlips = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, page));
				}));
	}

	void onSpikesPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchSpikes(panel.getSpikesSortKey(), page,
				(items, total) ->
				{
					lastSpikes = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, page));
				}));
	}

	void onDipsPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchDips(panel.getDipsSortKey(), page,
				(items, total) ->
				{
					lastDips = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateDips(items, total, page));
				}));
	}

	void onDipsSortChanged(String sort)
	{
		executor.execute(() ->
			apiClient.fetchDips(sort, 0,
				(items, total) ->
				{
					lastDips = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateDips(items, total, 0));
				}));
	}

	void onDumpsPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchDumps(panel.getDumpsSortKey(),
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				page,
				(items, total) ->
				{
					lastDumps = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, page));
				}));
	}

	void onAlertsPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchAlerts(page,
				(items, total) ->
				{
					lastAlerts = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateAlerts(items, total, page));
				}));
	}

	// -------------------------------------------------------------------------
	// Sort / filter / preset changes (always reset to page 0)
	// -------------------------------------------------------------------------

	void onSpikesSortChanged(String sort)
	{
		executor.execute(() ->
			apiClient.fetchSpikes(sort, 0,
				(items, total) ->
				{
					lastSpikes = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, 0));
				}));
	}

	void onDumpsSortChanged(String sort)
	{
		executor.execute(() ->
			apiClient.fetchDumps(sort,
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				0,
				(items, total) ->
				{
					lastDumps = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, 0));
				}));
	}

	void onFlipsFilterChanged()
	{
		executor.execute(() ->
			apiClient.fetchFlips(panel.getSelectedPreset(),
				panel.getFlipsMinProfit(), panel.getFlipsPriceMin(), panel.getFlipsPriceMax(),
				0,
				(items, total) ->
				{
					lastFlips = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, 0));
				}));
	}

	void onDumpsFilterChanged()
	{
		executor.execute(() ->
			apiClient.fetchDumps(panel.getDumpsSortKey(),
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				0,
				(items, total) ->
				{
					lastDumps = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, 0));
				}));
	}

	void onPresetChanged()
	{
		executor.execute(() ->
			apiClient.fetchFlips(panel.getSelectedPreset(),
				panel.getFlipsMinProfit(), panel.getFlipsPriceMin(), panel.getFlipsPriceMax(),
				0,
				(items, total) ->
				{
					lastFlips = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, 0));
				}));
	}

	void onBarrowsSetClicked(BarrowsSet set)
	{
		executor.execute(() ->
			apiClient.fetchBarrowsDetail(set.setParam, config.smithingLevel(),
				fullSet -> SwingUtilities.invokeLater(() ->
				{
					if (fullSet != null)
					{
						panel.showBarrowsDetail(fullSet);
					}
				})));
	}

	void searchItems(String query)
	{
		apiClient.fetchSearch(query,
			items -> SwingUtilities.invokeLater(() -> panel.showSearchResults(items, query)));
	}

	public O7FlipConfig getConfig()
	{
		return config;
	}

	@Provides
	O7FlipConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(O7FlipConfig.class);
	}
}
