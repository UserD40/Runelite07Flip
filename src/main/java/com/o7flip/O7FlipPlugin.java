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
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.o7flip.model.DumpItem;
import com.o7flip.model.FlipItem;
import com.o7flip.model.SpikeItem;
import com.o7flip.model.TrackedItemData;
import com.o7flip.model.TradeRecord;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.events.GameStateChanged;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.Notifier;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
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
	description = "Live GE flips, price dump signals, and price alerts from 07flip.com",
	tags = {"flipping", "grand exchange", "ge", "money making", "merching", "07flip"}
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
	private GePriceOverlay priceOverlay;

	@Inject
	private GpDropOverlay gpDropOverlay;

	@Inject
	private InventoryTooltipOverlay inventoryTooltipOverlay;

	@Inject
	private Gson gson;

	@Inject
	private net.runelite.client.config.ConfigManager configManager;

	public O7FlipPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> refreshTask;
	private ScheduledFuture<?> authRefreshTask;


	volatile int    pendingGeBuyItemId = -1;
	volatile long   pendingGeBuyPrice  = -1;
	volatile String pendingGeBuyName   = null;

	volatile int    pendingGeSellItemId = -1;
	volatile long   pendingGeSellPrice  = -1;
	volatile String pendingGeSellName   = null;

	volatile long   pendingGeSetPrice  = -1;
	volatile int    pendingGeSetItemId = -1;
	volatile long   pendingGeInputPrice = -1;

	public volatile long confirmHighlightUntilMs = 0L;

	private int sellSetupArmedItemId = -1;

	private int buySetupArmedItemId = -1;

	private int autoOpenInsightsItemId = -1;

	private boolean geAutoOpenedTab = false;

	private static final long OVERLAY_QUEUE_TTL_MS = 10L * 60L * 1000L;

	private volatile int    overlayQueueItemId   = -1;
	private volatile long   overlayQueuePrice    = -1;
	private volatile boolean overlayQueueIsBuy   = false;
	private volatile long   overlayQueueExpiresAt = 0L;


	List<FlipItem>  lastFlips  = Collections.emptyList();
	private List<DumpItem>  lastDumps  = Collections.emptyList();
	private List<SpikeItem> lastSpikes = Collections.emptyList();

	public volatile Map<Integer, TrackedItemData> trackedItems = Collections.emptyMap();

	public volatile Set<Integer> inventoryItemIds = Collections.emptySet();

	public volatile long inventoryCoins = 0L;
	private static final int COINS_ITEM_ID = 995;
	private static final long CASH_BUCKET = 100_000L;

	private final java.util.concurrent.ConcurrentHashMap<Integer, com.o7flip.model.RecommendedPrices> recPriceCache
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> recPriceFetchedAt
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Integer> recPriceInFlight
		= java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static final long REC_PRICE_TTL_MS = 60_000L;

	private final java.util.concurrent.ConcurrentHashMap<Integer, com.o7flip.model.ItemInsights> overlayInsightsCache
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> overlayInsightsFetchedAt
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Integer> overlayInsightsInFlight
		= java.util.concurrent.ConcurrentHashMap.newKeySet();

	public volatile Map<Integer, com.o7flip.model.ActiveOfferSnapshot> activeOffers = Collections.emptyMap();

	private final Map<Integer, GrandExchangeOfferState> prevSlotStates = new HashMap<>();

	private final Map<Integer, long[]> slotRecordedFills = new HashMap<>();

	private final Set<Long> tradePostsInFlight = new HashSet<>();

	public volatile List<TradeRecord> tradeHistory = Collections.emptyList();

	public volatile com.o7flip.util.BondLedger bondLedger = com.o7flip.util.BondLedger.EMPTY;

	public volatile com.o7flip.model.TrackerStats trackerStats = null;

	public volatile com.o7flip.model.ItemInsights currentInsights = null;

	private final java.util.concurrent.ConcurrentHashMap<Integer, FrozenSell> frozenSellByItemId
		= new java.util.concurrent.ConcurrentHashMap<>();

	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> frozenBuyByItemId
		= new java.util.concurrent.ConcurrentHashMap<>();

	private static final class FrozenSell
	{
		final long price;
		final long frozenAtMillis;

		FrozenSell(long price, long frozenAtMillis)
		{
			this.price = price;
			this.frozenAtMillis = frozenAtMillis;
		}
	}


	private static final long BUY_LIMIT_WINDOW_MS = 4L * 60L * 60L * 1000L;
	private static final String BUY_LIMIT_WINDOWS_KEY = "buyLimitWindows";
	private static final String BUY_LIMITS_KEY = "buyLimitsByItem";

	private static final class BuyLimitWindow
	{
		long windowStartMs;
		int  boughtQty;
		boolean notified;   // "available again" toast already fired for this window
	}

	private final java.util.concurrent.ConcurrentHashMap<Integer, BuyLimitWindow> buyLimitWindows
		= new java.util.concurrent.ConcurrentHashMap<>();

	private final java.util.concurrent.ConcurrentHashMap<Integer, Integer> buyLimitByItem
		= new java.util.concurrent.ConcurrentHashMap<>();

	private static final int MAX_TRADE_HISTORY = 200;
	private static final String TRADE_HISTORY_KEY = "tradeHistory";
	private static final String LAST_TRACKER_SYNC_KEY = "lastTrackerSync";
	private static final String BLOCKLIST_KEY = "blocklistItemIds";
	private static final String SLOT_FILLS_KEY = "slotRecordedFills";
	private static final String BOND_LEDGER_SPEND_KEY = "bondLedgerSpend";
	private static final String BOND_LEDGER_COUNT_KEY = "bondLedgerCount";
	private static final String BOND_LEDGER_MIGRATED_KEY = "bondLedgerMigrated";
	private static final String MEMBERSHIP_HIDDEN_KEY = "membershipCostHidden";
	private static final String TRADE_HISTORY_HEALED_KEY = "tradeHistoryHealed";
	private static final String SCRUB_VERSION_KEY = "tradeHistoryScrubVersion";
	private static final String SCRUB_VERSION_CURRENT = "3";

	public volatile Set<Integer> blocklist = Collections.emptySet();

	public void queueGeBuy(int itemId, long price, String name)
	{
		log.debug("[07Flip] GE buy queued: {} ({}) @ {}", name, itemId, price);
		setOverlayQueue(itemId, price, true);

		freezeFromTrackedOrFetch(itemId);

		final boolean premiumAtQueue = panel != null && panel.isPremium();
		apiClient.fetchItemInsights(itemId, ins ->
		{
			if (ins == null || ins.current == null)
			{
				return;
			}
			long fresh = premiumAtQueue && ins.current.recBuy != null && ins.current.recBuy > 0
				? ins.current.recBuy : ins.current.buyPrice;
			if (fresh <= 0)
			{
				return;
			}
			final long freshPrice = fresh;
			clientThread.invokeLater(() ->
			{
				if (pendingGeBuyItemId == itemId)
				{
					pendingGeBuyPrice = freshPrice;
				}
				if (overlayQueueItemId == itemId && overlayQueueIsBuy)
				{
					overlayQueuePrice = freshPrice;
				}
				if (pendingGeSetItemId == itemId)
				{
					pendingGeSetPrice = freshPrice;
				}
			});
		});

		clientThread.invokeLater(() ->
		{
			pendingGeBuyItemId = itemId;
			pendingGeBuyPrice  = price;
			pendingGeBuyName   = name;

			Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
			boolean setupOpen = setup != null && !setup.isHidden();
			if (setupOpen)
			{
				notifier.notify("Close the current offer first — your buy for " + name + " is queued");
				return;
			}

			if (fillGeBuyOffer(itemId, price, name))
			{
				pendingGeBuyItemId = -1;
				pendingGeBuyPrice  = -1;
				pendingGeBuyName   = null;
				return;
			}

			Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
			boolean geOpen = offerContainer != null && !offerContainer.isHidden();
			notifier.notify(geOpen
				? "Click an empty buy slot — your offer will pre-fill for " + name
				: "Open the Grand Exchange, click an empty buy slot, then your offer will pre-fill for " + name);
		});
	}

	public void queueGeSell(int itemId, long price, String name)
	{
		log.debug("[07Flip] GE sell queued: {} ({}) @ {}", name, itemId, price);
		setOverlayQueue(itemId, price, false);
		pendingGeSellItemId = itemId;
		pendingGeSellPrice  = price;
		pendingGeSellName   = name;
		notifier.notify("Open GE \u2192 click a sell slot \u2192 select " + name + " from inventory \u2014 use the 07Flip overlay to set the price");
	}


	private void setOverlayQueue(int itemId, long price, boolean isBuy)
	{
		overlayQueueItemId = itemId;
		overlayQueuePrice = price;
		overlayQueueIsBuy = isBuy;
		overlayQueueExpiresAt = System.currentTimeMillis() + OVERLAY_QUEUE_TTL_MS;
	}

	private void clearOverlayQueue()
	{
		overlayQueueItemId = -1;
		overlayQueuePrice = -1;
		overlayQueueExpiresAt = 0L;
	}

	private void freezeFromTrackedOrFetch(int itemId)
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		com.o7flip.model.ItemInsights cached = currentInsights;
		if (!hasFreezableRec(cached, itemId))
		{
			cached = overlayInsightsCache.get(itemId);
		}
		if (hasFreezableRec(cached, itemId))
		{
			freezeAndCache(itemId, cached.current.recBuy, cached.current.recSell);
			return;
		}
		executor.execute(() -> apiClient.fetchItemInsights(itemId, ins ->
		{
			if (hasFreezableRec(ins, itemId))
			{
				freezeAndCache(itemId, ins.current.recBuy, ins.current.recSell);
			}
			else
			{
				log.debug("[07Flip] Skipping freeze for item {} — no /v2/item rec prices", itemId);
			}
		}));
	}

	private static boolean hasFreezableRec(com.o7flip.model.ItemInsights ins, int itemId)
	{
		return ins != null && ins.itemId == itemId && ins.current != null
			&& ins.current.recBuy != null && ins.current.recBuy > 0
			&& ins.current.recSell != null && ins.current.recSell > 0;
	}

	private void freezeAndCache(int itemId, long recBuy, long recSell)
	{
		frozenSellByItemId.put(itemId, new FrozenSell(recSell, System.currentTimeMillis()));
		frozenBuyByItemId.put(itemId, recBuy);
		apiClient.postFreeze(itemId, recBuy, recSell, null);
	}

	public Long getFrozenBuy(int itemId)
	{
		return frozenBuyByItemId.get(itemId);
	}


	public boolean isPriceLocked(int itemId)
	{
		return frozenSellByItemId.get(itemId) != null;
	}

	public void lockPrice(int itemId, Long recBuy, Long recSell)
	{
		if (recBuy != null && recSell != null && recBuy > 0 && recSell > 0)
		{
			freezeAndCache(itemId, recBuy, recSell);
		}
		else
		{
			freezeFromTrackedOrFetch(itemId);
		}
	}

	public void unlockPrice(int itemId)
	{
		frozenSellByItemId.remove(itemId);
		frozenBuyByItemId.remove(itemId);
		apiClient.postUnfreeze(itemId, null);
	}


	private void recordBuyForLimit(int itemId, int qty, long timestamp)
	{
		if (itemId <= 0 || qty <= 0)
		{
			return;
		}
		BuyLimitWindow w = buyLimitWindows.get(itemId);
		if (w == null || timestamp - w.windowStartMs >= BUY_LIMIT_WINDOW_MS)
		{
			w = new BuyLimitWindow();
			w.windowStartMs = timestamp;
			w.boughtQty = qty;
		}
		else
		{
			w.boughtQty += qty;
		}
		buyLimitWindows.put(itemId, w);
		saveBuyLimitWindows();
	}

	void rememberBuyLimit(int itemId, int buyLimit)
	{
		if (itemId > 0 && buyLimit > 0)
		{
			Integer prev = buyLimitByItem.put(itemId, buyLimit);
			if (prev == null || prev != buyLimit)
			{
				saveCache(BUY_LIMITS_KEY, buyLimitByItem);
			}
		}
	}

	void rememberBuyLimits(java.util.List<FlipItem> items)
	{
		if (items == null)
		{
			return;
		}
		boolean changed = false;
		for (FlipItem f : items)
		{
			if (f != null && f.itemId > 0 && f.buyLimit > 0
				&& !Integer.valueOf(f.buyLimit).equals(buyLimitByItem.get(f.itemId)))
			{
				buyLimitByItem.put(f.itemId, f.buyLimit);
				changed = true;
			}
		}
		if (changed)
		{
			saveCache(BUY_LIMITS_KEY, buyLimitByItem);
		}
	}

	public long buyLimitCooldownMs(int itemId)
	{
		BuyLimitWindow w = buyLimitWindows.get(itemId);
		Integer limit = buyLimitByItem.get(itemId);
		if (w == null || limit == null || limit <= 0 || w.boughtQty < limit)
		{
			return 0L;
		}
		long remaining = (w.windowStartMs + BUY_LIMIT_WINDOW_MS) - System.currentTimeMillis();
		return remaining > 0 ? remaining : 0L;
	}

	public double buyLimitCooldownProgress(int itemId)
	{
		BuyLimitWindow w = buyLimitWindows.get(itemId);
		Integer limit = buyLimitByItem.get(itemId);
		if (w == null || limit == null || limit <= 0 || w.boughtQty < limit)
		{
			return 1.0;
		}
		double p = (System.currentTimeMillis() - w.windowStartMs) / (double) BUY_LIMIT_WINDOW_MS;
		return Math.max(0.0, Math.min(1.0, p));
	}

	private void checkBuyLimitResets()
	{
		if (buyLimitWindows.isEmpty())
		{
			return;
		}
		long now = System.currentTimeMillis();
		java.util.Iterator<java.util.Map.Entry<Integer, BuyLimitWindow>> it = buyLimitWindows.entrySet().iterator();
		boolean changed = false;
		while (it.hasNext())
		{
			java.util.Map.Entry<Integer, BuyLimitWindow> e = it.next();
			BuyLimitWindow w = e.getValue();
			if (now - w.windowStartMs < BUY_LIMIT_WINDOW_MS)
			{
				continue;
			}
			int itemId = e.getKey();
			Integer limit = buyLimitByItem.get(itemId);
			if (!w.notified && limit != null && limit > 0 && w.boughtQty >= limit)
			{
				notifier.notify("07Flip: buy limit available again for " + itemNameFor(itemId));
			}
			it.remove();   // window expired — drop it
			changed = true;
		}
		if (changed)
		{
			saveBuyLimitWindows();
		}
	}

	private String itemNameFor(int itemId)
	{
		try
		{
			String n = client.getItemDefinition(itemId).getName();
			if (n != null && !n.isEmpty() && !"null".equalsIgnoreCase(n))
			{
				return n;
			}
		}
		catch (Exception ignored)
		{
		}
		return "an item";
	}

	private void saveBuyLimitWindows()
	{
		saveCache(BUY_LIMIT_WINDOWS_KEY, buyLimitWindows);
	}

	@SuppressWarnings("unchecked")
	private void loadBuyLimitState()
	{
		try
		{
			java.util.Map<String, Double> limits = loadCache(BUY_LIMITS_KEY, java.util.HashMap.class);
			if (limits != null)
			{
				for (java.util.Map.Entry<String, Double> e : limits.entrySet())
				{
					buyLimitByItem.put(Integer.parseInt(e.getKey()), (int) (double) e.getValue());
				}
			}
		}
		catch (Exception ignored)
		{
		}
		try
		{
			java.lang.reflect.Type t = com.google.gson.reflect.TypeToken
				.getParameterized(java.util.HashMap.class, Integer.class, BuyLimitWindow.class).getType();
			String json = configManager.getConfiguration("o7flip", "cache_" + BUY_LIMIT_WINDOWS_KEY);
			if (json != null && !json.isEmpty())
			{
				java.util.Map<Integer, BuyLimitWindow> loaded = gson.fromJson(json, t);
				long now = System.currentTimeMillis();
				if (loaded != null)
				{
					for (java.util.Map.Entry<Integer, BuyLimitWindow> e : loaded.entrySet())
					{
						BuyLimitWindow w = e.getValue();
						if (w != null && now - w.windowStartMs < BUY_LIMIT_WINDOW_MS)
						{
							buyLimitWindows.put(e.getKey(), w);
						}
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}
	}

	public Long getFrozenSell(int itemId)
	{
		FrozenSell f = frozenSellByItemId.get(itemId);
		if (f == null)
		{
			return null;
		}
		long staleAfterMs = (long) config.frozenSellStaleAfterHours() * 60L * 60L * 1000L;
		if (System.currentTimeMillis() - f.frozenAtMillis <= staleAfterMs)
		{
			return f.price;
		}
		Long live = lookupLiveRecSell(itemId);
		if (live == null || live <= 0)
		{
			return f.price;
		}
		FrozenSell refreshed = new FrozenSell(live, System.currentTimeMillis());
		frozenSellByItemId.put(itemId, refreshed);
		return live;
	}

	long computeAutoSellPrice(int itemId)
	{
		boolean premium = panel != null && panel.isPremium();
		long candidate;
		if (!premium)
		{
			Long liveSell = lookupLiveSell(itemId);
			candidate = (liveSell != null && liveSell > 0) ? liveSell : -1L;
		}
		else
		{
			Long frozen  = getFrozenSell(itemId);
			Long recSell = lookupLiveRecSell(itemId);
			long best    = -1L;
			if (frozen  != null && frozen  > 0)                   best = frozen;
			if (recSell != null && recSell > 0 && recSell > best) best = recSell;
			candidate = best;
		}
		long breakEven = breakEvenSellPrice(itemId);
		if (candidate > 0 && breakEven > 0)
		{
			candidate = Math.max(candidate, breakEven);
		}
		return candidate;
	}

	private long breakEvenSellPrice(int itemId)
	{
		com.o7flip.util.ProfitCalculator.Result r = com.o7flip.util.ProfitCalculator.compute(tradeHistory);
		com.o7flip.util.ProfitCalculator.OpenPosition pos = r.openPositions.get(itemId);
		if (pos == null || pos.remainingQty <= 0 || pos.remainingCostBasis <= 0)
		{
			return -1L;
		}
		double avgCost = pos.remainingCostBasis / (double) pos.remainingQty;
		long uncapped = (long) Math.ceil(avgCost / 0.98);
		if (uncapped <= 250_000_000L)
		{
			return uncapped;
		}
		return (long) Math.ceil(avgCost) + 5_000_000L;
	}

	private Long lookupLiveSell(int itemId)
	{
		for (FlipItem f : lastFlips)
		{
			if (f.itemId == itemId && f.sellPrice > 0)
			{
				return f.sellPrice;
			}
		}
		return null;
	}

	private com.o7flip.model.ItemInsights recInsightsFor(int itemId)
	{
		com.o7flip.model.ItemInsights cur = currentInsights;
		if (cur != null && cur.itemId == itemId && cur.current != null)
		{
			return cur;
		}
		return getOverlayInsights(itemId);
	}

	private Long lookupLiveRecSell(int itemId)
	{
		com.o7flip.model.ItemInsights ins = recInsightsFor(itemId);
		if (ins != null && ins.current != null && ins.current.recSell != null && ins.current.recSell > 0)
		{
			return ins.current.recSell;
		}
		return null;
	}

	long computeAutoBuyPrice(int itemId)
	{
		boolean premium = panel != null && panel.isPremium();
		if (!premium)
		{
			Long liveBuy = lookupLiveBuy(itemId);
			return (liveBuy != null && liveBuy > 0) ? liveBuy : -1L;
		}
		Long recBuy = lookupLiveRecBuy(itemId);
		return (recBuy != null && recBuy > 0) ? recBuy : -1L;
	}

	private Long lookupLiveBuy(int itemId)
	{
		for (FlipItem f : lastFlips)
		{
			if (f.itemId == itemId && f.buyPrice > 0)
			{
				return f.buyPrice;
			}
		}
		return null;
	}

	private Long lookupLiveRecBuy(int itemId)
	{
		com.o7flip.model.ItemInsights ins = recInsightsFor(itemId);
		if (ins != null && ins.current != null && ins.current.recBuy != null && ins.current.recBuy > 0)
		{
			return ins.current.recBuy;
		}
		return null;
	}

	private void armSellPriceIfStillRelevant(int itemId)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (!isSellSetupVisible(setup))
		{
			return;
		}
		int current = resolveItemIdFromSetupWidget();
		if (current != itemId)
		{
			return;
		}
		long auto = computeAutoSellPrice(itemId);
		if (auto > 0)
		{
			pendingGeInputPrice = auto;
		}
	}

	private void armBuyPriceIfStillRelevant(int itemId)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (!isBuySetupVisible(setup))
		{
			return;
		}
		if (resolveItemIdFromSetupWidget() != itemId)
		{
			return;
		}
		if (pendingGeSetPrice != -1 || pendingGeBuyItemId != -1)
		{
			return;
		}
		long auto = computeAutoBuyPrice(itemId);
		if (auto > 0)
		{
			pendingGeInputPrice = auto;
		}
	}

	private void unfreezeIfPositionClosed(int itemId)
	{
		FrozenSell cached = frozenSellByItemId.get(itemId);
		if (cached == null)
		{
			return;
		}
		com.o7flip.util.ProfitCalculator.Result r = com.o7flip.util.ProfitCalculator.compute(tradeHistory);
		com.o7flip.util.ProfitCalculator.OpenPosition pos = r.openPositions.get(itemId);
		if (pos != null && pos.remainingQty > 0)
		{
			return;
		}
		frozenSellByItemId.remove(itemId);
		frozenBuyByItemId.remove(itemId);
		apiClient.postUnfreeze(itemId, null);
	}

	public boolean hasOverlayQueue()
	{
		return overlayQueueItemId != -1
			&& System.currentTimeMillis() < overlayQueueExpiresAt;
	}

	public boolean overlayQueueIsBuy()
	{
		return overlayQueueIsBuy;
	}

	public long queuedPriceFor(int itemId, boolean isBuy)
	{
		if (!hasOverlayQueue()) return -1L;
		if (overlayQueueItemId == itemId && overlayQueueIsBuy == isBuy)
		{
			return overlayQueuePrice;
		}
		return -1L;
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(O7FlipPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("07Flip - GE Flip Finder")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(geOverlay);
		overlayManager.add(priceOverlay);
		overlayManager.add(gpDropOverlay);
		overlayManager.add(inventoryTooltipOverlay);

		loadTradeHistory();
		loadBondLedger();
		loadBlocklist();
		loadSlotRecordedFills();

		apiClient.setOnFavouritesUnauthorized(() -> SwingUtilities.invokeLater(() ->
			notifier.notify("Your 07Flip API key was rejected. Open the plugin config and paste it again.")));

		hydrateCachedTabs();

		executor = Executors.newSingleThreadScheduledExecutor();
		fetchAuthStatus();
		authRefreshTask = executor.scheduleAtFixedRate(
			this::fetchAuthStatus, 15, 15, TimeUnit.MINUTES);
		executor.execute(() -> fetchAll(true)); // forced — panel not yet visible at startup
		executor.execute(this::doSyncTrackerHistory);
		executor.execute(this::doBulkSyncToServer);
		executor.execute(this::doFetchTrackerStats);
		executor.execute(this::doHydrateOptimizerSession);
		if (panel.isShowing())
		{
			startSessionBackgroundPoll();
		}
		executor.execute(this::refreshCompletedPositions);
		refreshTask = executor.scheduleAtFixedRate(
			() -> fetchAll(false),
			config.refreshIntervalSeconds(),
			config.refreshIntervalSeconds(),
			TimeUnit.SECONDS
		);
		log.debug("[07Flip] Started, refreshing every {}s", config.refreshIntervalSeconds());
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(true);
		}
		if (authRefreshTask != null)
		{
			authRefreshTask.cancel(true);
		}
		if (sessionBackgroundPollTask != null)
		{
			sessionBackgroundPollTask.cancel(true);
		}
		if (executor != null)
		{
			executor.shutdown();
		}
		overlayManager.remove(geOverlay);
		overlayManager.remove(priceOverlay);
		overlayManager.remove(gpDropOverlay);
		overlayManager.remove(inventoryTooltipOverlay);
		clientToolbar.removeNavigation(navButton);
		log.debug("[07Flip] Stopped");
	}



	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			offlineReconcileArmed = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		syncActiveOffersFromClient();

		checkBuyLimitResets();

		if (offlineReconcileArmed && activeSession != null)
		{
			offlineReconcileArmed = false;
			reconcileOfflineCompletions();
		}

		detectAndArmSellSetup();

		detectAndArmBuySetup();

		detectAutoOpenItemInsights();

		if (pendingGeBuyItemId == -1)
		{
			return;
		}
		Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
		if (offerContainer == null || offerContainer.isHidden())
		{
			return;
		}
		if (fillGeBuyOffer(pendingGeBuyItemId, pendingGeBuyPrice, pendingGeBuyName))
		{
			pendingGeBuyItemId = -1;
			pendingGeBuyPrice  = -1;
			pendingGeBuyName   = null;
		}
	}

	private static final int GE_SEARCH_MODE = 14;

	private boolean fillGeBuyOffer(int itemId, long price, String name)
	{
		Widget searchBox = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (searchBox == null || searchBox.isHidden())
		{
			log.debug("[07Flip] GE search box widget not ready (user hasn't clicked an empty buy slot yet)");
			return false;
		}
		Object[] scriptArgs = searchBox.getOnKeyListener();
		if (scriptArgs == null)
		{
			log.debug("[07Flip] GE search box has no key listener");
			return false;
		}
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, name);
		client.setVarcIntValue(VarClientID.MESLAYERMODE, GE_SEARCH_MODE);
		pendingGeSetPrice  = price;
		pendingGeSetItemId = itemId;
		client.runScript(scriptArgs);
		return true;
	}

	private static final int SCRIPT_CHATBOX_INPUT_OPEN = 108;

	private void detectAndArmSellSetup()
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			sellSetupArmedItemId = -1;
			return;
		}
		if (!isSellSetupVisible(setup))
		{
			return;
		}
		int itemId = resolveItemIdFromSetupWidget();
		if (itemId <= 0)
		{
			log.debug("[07Flip] sell-setup detector: setup visible but itemId resolved to {} — no arm", itemId);
			return;
		}
		if (itemId == sellSetupArmedItemId)
		{
			return;
		}
		sellSetupArmedItemId = itemId;

		long auto = computeAutoSellPrice(itemId);
		if (auto > 0)
		{
			pendingGeInputPrice = auto;
			log.debug("[07Flip] sell-setup detector: armed sell price {} for itemId {}", auto, itemId);
		}
		if (panel != null && panel.isPremium())
		{
			if (auto <= 0)
			{
				log.debug("[07Flip] sell-setup detector: no cached rec for itemId {}, kicking off fetch", itemId);
				getOverlayInsights(itemId);
			}
		}
		else
		{
			refreshFreeLiveSell(itemId);
		}
	}

	private void refreshFreeLiveSell(int itemId)
	{
		apiClient.fetchItemInsights(itemId, ins ->
		{
			if (ins == null || ins.current == null || ins.current.sellPrice <= 0)
			{
				return;
			}
			final long freshSell = ins.current.sellPrice;
			clientThread.invokeLater(() ->
			{
				Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
				if (!isSellSetupVisible(setup))
				{
					return;
				}
				if (resolveItemIdFromSetupWidget() != itemId)
				{
					return;
				}
				pendingGeInputPrice = freshSell;
			});
		});
	}

	private void detectAndArmBuySetup()
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			buySetupArmedItemId = -1;
			return;
		}
		if (!isBuySetupVisible(setup))
		{
			return;
		}
		if (pendingGeSetPrice != -1 || pendingGeBuyItemId != -1)
		{
			return;
		}
		if (widgetTreeHasText(setup, "choose an item"))
		{
			return;
		}
		int itemId = resolveItemIdFromSetupWidget();
		if (itemId <= 0 || itemId == buySetupArmedItemId)
		{
			return;
		}
		buySetupArmedItemId = itemId;

		long auto = computeAutoBuyPrice(itemId);
		if (auto > 0)
		{
			pendingGeInputPrice = auto;
			log.debug("[07Flip] buy-setup detector: armed buy price {} for itemId {}", auto, itemId);
		}
		if (panel != null && panel.isPremium())
		{
			if (auto <= 0)
			{
				getOverlayInsights(itemId);
			}
		}
		else
		{
			refreshFreeLiveBuy(itemId);
		}
	}

	private void refreshFreeLiveBuy(int itemId)
	{
		apiClient.fetchItemInsights(itemId, ins ->
		{
			if (ins == null || ins.current == null || ins.current.buyPrice <= 0)
			{
				return;
			}
			final long freshBuy = ins.current.buyPrice;
			clientThread.invokeLater(() ->
			{
				Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
				if (!isBuySetupVisible(setup))
				{
					return;
				}
				if (resolveItemIdFromSetupWidget() != itemId)
				{
					return;
				}
				if (pendingGeSetPrice != -1 || pendingGeBuyItemId != -1)
				{
					return;
				}
				pendingGeInputPrice = freshBuy;
			});
		});
	}

	private void detectAutoOpenItemInsights()
	{
		Widget setup   = client.getWidget(InterfaceID.GeOffers.SETUP);
		Widget details = client.getWidget(InterfaceID.GeOffers.DETAILS);
		boolean setupOpen   = setup   != null && !setup.isHidden();
		boolean detailsOpen = details != null && !details.isHidden();

		if (!setupOpen && !detailsOpen)
		{
			autoOpenInsightsItemId = -1;
			if (geAutoOpenedTab)
			{
				geAutoOpenedTab = false;
				SwingUtilities.invokeLater(() -> panel.restoreTabAfterGeAutoOpen());
			}
			return;
		}
		if (!config.autoOpenItemTab() || !config.showInsights() || panel == null || !panel.isShowing())
		{
			return;
		}

		int itemId;
		if (setupOpen)
		{
			if (widgetTreeHasText(setup, "choose an item"))
			{
				return;
			}
			itemId = resolveItemIdFromSetupWidget();
		}
		else
		{
			itemId = resolveOfferStatusItemId();
		}

		if (itemId <= 0 || itemId == autoOpenInsightsItemId)
		{
			return;
		}
		String name = client.getItemDefinition(itemId).getName();
		if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
		{
			return;
		}
		autoOpenInsightsItemId = itemId;
		com.o7flip.model.ItemInsights shown = currentInsights;
		if (shown != null && shown.itemId == itemId)
		{
			return;
		}
		log.debug("[07Flip] GE offer screen detected — auto-opening Item tab for {} ({})", name, itemId);
		if (!geAutoOpenedTab)
		{
			geAutoOpenedTab = true;
			SwingUtilities.invokeLater(() -> panel.markGeAutoOpen());
		}
		openInsights(itemId, name);
	}

	private int resolveOfferStatusItemId()
	{
		int shown = firstItemIdInWidget(client.getWidget(InterfaceID.GeOffers.DETAILS));

		int slotItem = -1;
		int slot = client.getVarbitValue(VarbitID.GE_SELECTEDSLOT);
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (slot >= 0 && offers != null && slot < offers.length)
		{
			GrandExchangeOffer offer = offers[slot];
			if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY)
			{
				slotItem = offer.getItemId();
			}
		}

		if (shown > 0 && slotItem > 0 && shown != slotItem)
		{
			return -1;   // ambiguous (stale/lingering varbit) — don't open the wrong item
		}
		if (shown > 0)
		{
			return shown;
		}
		return slotItem;
	}

	private static boolean isSellSetupVisible(Widget setup)
	{
		if (setup == null || setup.isHidden())
		{
			return false;
		}
		return widgetTreeHasText(setup, "sell offer");
	}

	private static boolean isBuySetupVisible(Widget setup)
	{
		if (setup == null || setup.isHidden())
		{
			return false;
		}
		return widgetTreeHasText(setup, "buy offer");
	}

	private static boolean widgetTreeHasText(Widget w, String needle)
	{
		if (w == null)
		{
			return false;
		}
		String text = w.getText();
		if (text != null && text.toLowerCase().contains(needle))
		{
			return true;
		}
		Widget[] dyn = w.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn)
			{
				if (widgetTreeHasText(c, needle)) return true;
			}
		}
		Widget[] stat = w.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat)
			{
				if (widgetTreeHasText(c, needle)) return true;
			}
		}
		Widget[] nest = w.getNestedChildren();
		if (nest != null)
		{
			for (Widget c : nest)
			{
				if (widgetTreeHasText(c, needle)) return true;
			}
		}
		return false;
	}

	private int resolveItemIdFromSetupWidget()
	{
		return firstItemIdInWidget(client.getWidget(InterfaceID.GeOffers.SETUP));
	}

	private static int firstItemIdInWidget(Widget root)
	{
		if (root == null || root.isHidden())
		{
			return -1;
		}
		Widget[] dyn = root.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget w : dyn)
			{
				if (w == null) continue;
				int id = w.getItemId();
				if (id > 0) return id;
			}
		}
		Widget[] stat = root.getStaticChildren();
		if (stat != null)
		{
			for (Widget w : stat)
			{
				if (w == null) continue;
				int id = w.getItemId();
				if (id > 0) return id;
				Widget[] grand = w.getDynamicChildren();
				if (grand == null) continue;
				for (Widget g : grand)
				{
					if (g == null) continue;
					int gid = g.getItemId();
					if (gid > 0) return gid;
				}
			}
		}
		return -1;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.GE_OFFERS_SETUP_BUILD)
		{
			clearOverlayQueue();

			int searchedItemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
			int currentItemId = searchedItemId > 0 ? searchedItemId : resolveItemIdFromSetupWidget();
			long price = -1;

			if (pendingGeSetPrice != -1)
			{
				if (currentItemId > 0)
				{
					if (pendingGeSetItemId == -1 || currentItemId == pendingGeSetItemId)
					{
						price = pendingGeSetPrice;
					}
					pendingGeSetPrice  = -1;
					pendingGeSetItemId = -1;
				}
			}
			else if (pendingGeSellItemId != -1)
			{
				int offerType = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE);
				if (offerType == 0 && currentItemId == pendingGeSellItemId)
				{
					price = pendingGeSellPrice;
					pendingGeSellItemId = -1;
					pendingGeSellPrice  = -1;
					pendingGeSellName   = null;
				}
			}

			Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
			if (setup != null && !setup.isHidden() && isSellSetupVisible(setup))
			{
				int sellItemId = resolveItemIdFromSetupWidget();
				if (sellItemId > 0 && computeAutoSellPrice(sellItemId) <= 0)
				{
					getOverlayInsights(sellItemId);
				}
			}

			if (price != -1)
			{
				pendingGeInputPrice = price;
			}

			return;
		}

		if (event.getScriptId() == SCRIPT_CHATBOX_INPUT_OPEN)
		{
			Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
			if (setup == null || setup.isHidden())
			{
				pendingGeInputPrice = -1;
				return;
			}

			if (pendingGeInputPrice == -1 && isSellSetupVisible(setup))
			{
				int currentItemId = resolveItemIdFromSetupWidget();
				if (currentItemId > 0)
				{
					long auto = computeAutoSellPrice(currentItemId);
					if (auto > 0)
					{
						pendingGeInputPrice = auto;
						log.debug("[07Flip] chatbox-open last-ditch armed sell price {} for itemId {}", auto, currentItemId);
					}
				}
			}

			if (pendingGeInputPrice == -1)
			{
				return;
			}
			final long price = pendingGeInputPrice;
			pendingGeInputPrice = -1;
			clientThread.invokeLater(() -> autoFillPriceInput(price));
		}
	}


	private void autoFillPriceInput(long price)
	{
		if (!config.autoFillGePrice())
		{
			return;
		}
		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (input == null)
		{
			return;
		}
		input.setText(price + "*");
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(price));
		confirmHighlightUntilMs = System.currentTimeMillis() + 3000L;
	}


	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() != priceOverlay)
		{
			return;
		}
		OverlayMenuEntry entry = event.getEntry();
		if (entry == null || !GePriceOverlay.TARGET.equals(entry.getTarget()))
		{
			return;
		}
		long price = priceOverlay.priceForMenuOption(entry.getOption());
		if (price <= 0)
		{
			return;
		}
		invokePriceFill(price);
	}

	public void invokePriceFill(long price)
	{
		if (price <= 0)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
			if (input != null && !input.isHidden())
			{
				autoFillPriceInput(price);
				return;
			}
			Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
			if (setup == null || setup.isHidden())
			{
				return;
			}
			pendingGeInputPrice = price;
		});
	}


	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"o7flip".equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();

		if ("apiKey".equals(key))
		{
			executor.execute(this::fetchAuthStatus);
			return;
		}
		if ("openTabReorderDialog".equals(key) && Boolean.parseBoolean(event.getNewValue()))
		{
			configManager.setConfiguration("o7flip", "openTabReorderDialog", false);
			SwingUtilities.invokeLater(this::openTabReorderDialog);
			return;
		}
		if (key.startsWith("itemTab"))
		{
			SwingUtilities.invokeLater(() -> panel.refreshInsightsSections());
			return;
		}
		if (isTabStructureKey(key))
		{
			SwingUtilities.invokeLater(() -> panel.rebuildTabs());
		}
	}

	private static boolean isTabStructureKey(String key)
	{
		switch (key)
		{
			case "showFlips":
			case "showDumps":
			case "showSpikes":
			case "showItem":
			case "showDips":
			case "showFavourites":
			case "showMyFlips":
			case "tabOrder":
			case "topRowTabs":
				return true;
			default:
				return false;
		}
	}

	public void openTabReorderDialog()
	{
		java.util.List<String> topRow = panel.resolveTopRow();
		com.o7flip.ui.TabOrderDialog.show(panel, topRow,
			O7FlipPanel.MOVABLE_POOL, O7FlipPanel.DEFAULT_TOP_ROW,
			selected ->
			{
				String csv = String.join(",", selected);
				configManager.setConfiguration("o7flip", "topRowTabs", csv);
				panel.rebuildTabs();
			});
	}


	void fetchAuthStatus()
	{
		fetchAuthStatusInternal(false);
	}

	private void fetchAuthStatusInternal(boolean isRetry)
	{
		String key = config.apiKey();
		if (key == null || key.trim().isEmpty())
		{
			SwingUtilities.invokeLater(() -> panel.updateAuthStatus(false, false));
			return;
		}
		apiClient.fetchAuthStatus(
			status -> SwingUtilities.invokeLater(() ->
			{
				panel.updateAuthStatus(status.authenticated, status.premium);
				if (status.premium && activeSession == null
					&& executor != null && !executor.isShutdown())
				{
					executor.execute(this::doHydrateOptimizerSession);
				}
			}),
			() ->
			{
				if (!isRetry && executor != null && !executor.isShutdown())
				{
					executor.schedule(() -> fetchAuthStatusInternal(true), 60, TimeUnit.SECONDS);
				}
			}
		);
	}


	void fetchAll(boolean forced)
	{
		if (!forced && !panel.isShowing())
		{
			return;
		}

		if (apiClient.isRateLimited())
		{
			return;
		}

		SwingUtilities.invokeLater(() -> panel.setLoading(true));

		JsonObject sections = new JsonObject();


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

		if (config.showDumps() && !panel.dumpsUsesBotEndpoint())
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

		final int flipsPage   = panel.getFlipsPage();
		final int spikesPage  = panel.getSpikesPage();
		final int dumpsPage   = panel.getDumpsPage();

		apiClient.fetchBundle(
			sections,
			null,
			config.showSpikes() ? (items, total) ->
			{
				lastSpikes = items;
				rebuildTrackedItems();
				SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, spikesPage));
			} : null,
			null,
			connectUrl ->
			{
				String key = config.apiKey();
				boolean hasKey = key != null && !key.trim().isEmpty();
				SwingUtilities.invokeLater(() -> panel.updateInvalidKeyWarning(hasKey ? connectUrl : null));
			}
		);

		if (config.showFlips())
		{
			fetchFlipsAtPage(flipsPage);
		}

		if (config.showDips())
		{
			fetchDipsAtPage(panel.getDipsSortKey(), panel.getDipsPage());
		}

		if (config.showDumps() && !panel.dumpsUsesBotEndpoint())
		{
			fetchDumpsAtPage(panel.getDumpsSortKey(), panel.getDumpsPage());
		}

		if (config.showFavourites() && hasApiKey())
		{
			apiClient.fetchFavourites(items ->
			{
				if (items != null && !items.isEmpty()) saveCache("favourites", items);
				pushFavouritesToPanel(items);
			});
		}
		if (config.showDumps() && panel.dumpsUsesBotEndpoint())
		{
			final int botDumpsPage = panel.getDumpsPage();
			apiClient.fetchBotDumps(
				panel.getDumpsSortKey(),
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				panel.getDumpsMinScore(), panel.getDumpsActiveOnly(), panel.getDumpsTier(),
				botDumpsPage,
				resp ->
				{
					lastDumps = resp.items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateDumps(resp, botDumpsPage));
				});
		}
	}


	private static final int CACHE_MAX_BYTES = 200_000;

	private void saveCache(String key, Object data)
	{
		if (data == null) return;
		try
		{
			String json = gson.toJson(data);
			if (json.length() > CACHE_MAX_BYTES)
			{
				log.warn("[07Flip] Cache for '{}' too large ({} bytes) — skipping", key, json.length());
				return;
			}
			configManager.setConfiguration("o7flip", "cache_" + key, json);
		}
		catch (Exception e)
		{
			log.warn("[07Flip] Failed to save cache '{}': {}", key, e.getMessage());
		}
	}

	private <T> T loadCache(String key, Class<T> type)
	{
		try
		{
			String json = configManager.getConfiguration("o7flip", "cache_" + key);
			if (json == null || json.isEmpty()) return null;
			return gson.fromJson(json, type);
		}
		catch (Exception e)
		{
			log.warn("[07Flip] Failed to load cache '{}': {}", key, e.getMessage());
			return null;
		}
	}

	private <T> List<T> loadListCache(String key, Class<T> elementType)
	{
		try
		{
			String json = configManager.getConfiguration("o7flip", "cache_" + key);
			if (json == null || json.isEmpty()) return null;
			java.lang.reflect.Type listType =
				com.google.gson.reflect.TypeToken.getParameterized(List.class, elementType).getType();
			return gson.fromJson(json, listType);
		}
		catch (Exception e)
		{
			log.warn("[07Flip] Failed to load list cache '{}': {}", key, e.getMessage());
			return null;
		}
	}

	private void hydrateCachedTabs()
	{
		if (panel == null) return;

		com.o7flip.model.DumpItem.Response cd = loadCache("dumps", com.o7flip.model.DumpItem.Response.class);
		if (cd != null && cd.items != null && !cd.items.isEmpty())
		{
			lastDumps = cd.items;
			final com.o7flip.model.DumpItem.Response snap = cd;
			SwingUtilities.invokeLater(() -> panel.updateDumps(snap, 0));
		}

		List<com.o7flip.model.DipItem> cdips = loadListCache("dips", com.o7flip.model.DipItem.class);
		if (cdips != null && !cdips.isEmpty())
		{
			final List<com.o7flip.model.DipItem> snap = cdips;
			SwingUtilities.invokeLater(() -> panel.updateDips(snap, snap.size(), 0));
		}

		loadBuyLimitState();

		List<FlipItem> cf = loadListCache("favourites", FlipItem.class);
		if (cf != null && !cf.isEmpty())
		{
			pushFavouritesToPanel(cf);
		}

	}


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
		}

		trackedItems = Collections.unmodifiableMap(map);
	}


	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		Set<Integer> next = new HashSet<>();
		long coins = 0L;
		for (Item item : event.getItemContainer().getItems())
		{
			if (item.getId() < 0)
			{
				continue;
			}
			next.add(item.getId());
			if (item.getId() == COINS_ITEM_ID)
			{
				coins = item.getQuantity();
			}
		}
		inventoryItemIds = Collections.unmodifiableSet(next);
		long previousCoins = inventoryCoins;
		inventoryCoins     = coins;

		if (panel != null && previousCoins != coins)
		{
			SwingUtilities.invokeLater(panel::onInventoryCoinsChanged);
		}
	}


	private long lastActiveOffersHash = 0L;

	private void syncActiveOffersFromClient()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}

		Map<Integer, com.o7flip.model.ActiveOfferSnapshot> next = new HashMap<>();
		long hash = 0L;
		for (int slot = 0; slot < offers.length; slot++)
		{
			GrandExchangeOffer o = offers[slot];
			if (o == null || o.getState() == GrandExchangeOfferState.EMPTY)
			{
				continue;
			}
			next.put(slot, snapshot(slot, o));
			if (o.getState() == GrandExchangeOfferState.BUYING
				|| o.getState() == GrandExchangeOfferState.SELLING)
			{
				recordIfNewFills(o, slot);
			}
			hash = hash * 31 + slot;
			hash = hash * 31 + o.getItemId();
			hash = hash * 31 + o.getQuantitySold();
			hash = hash * 31 + o.getTotalQuantity();
			hash = hash * 31 + o.getState().ordinal();
		}

		if (hash == lastActiveOffersHash)
		{
			return;
		}
		lastActiveOffersHash = hash;
		activeOffers = Collections.unmodifiableMap(next);

		if (panel != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				panel.onCapitalAutoAdjusted();
				panel.rerenderCapitalAffectedTabs();
			});
		}

		final List<TradeRecord> snap = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));
	}

	private com.o7flip.model.ActiveOfferSnapshot snapshot(int slot, GrandExchangeOffer offer)
	{
		String name = "Item " + offer.getItemId();
		try
		{
			name = client.getItemDefinition(offer.getItemId()).getName();
		}
		catch (Exception ignored)
		{
		}
		return new com.o7flip.model.ActiveOfferSnapshot(
			slot, offer.getItemId(), name, offer.getPrice(),
			offer.getQuantitySold(), offer.getTotalQuantity(), offer.getState());
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		int slot = event.getSlot();
		GrandExchangeOfferState state = offer.getState();

		Map<Integer, com.o7flip.model.ActiveOfferSnapshot> next = new HashMap<>(activeOffers);
		if (state == GrandExchangeOfferState.EMPTY)
		{
			next.remove(slot);
		}
		else
		{
			next.put(slot, snapshot(slot, offer));
		}
		activeOffers = Collections.unmodifiableMap(next);

		final List<TradeRecord> snap = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));

		if ((state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING)
			&& offer.getItemId() == overlayQueueItemId)
		{
			clearOverlayQueue();
		}

		if (state == GrandExchangeOfferState.SELLING)
		{
			markPlanSellListed(offer.getItemId());
		}
		else if (state == GrandExchangeOfferState.CANCELLED_SELL)
		{
			clearPlanSellListedIfNoLiveSell(offer.getItemId());
		}

		if (state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.SELLING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_BUY
			|| state == GrandExchangeOfferState.CANCELLED_SELL)
		{
			recordIfNewFills(offer, slot);
		}

		if (state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD)
		{
			long[] slotState = slotRecordedFills.get(slot);
			if (slotState != null && slotState.length >= 3)
			{
				clearPartialFlag(slotState[2]);
			}
		}

		if (state == GrandExchangeOfferState.EMPTY)
		{
			prevSlotStates.remove(slot);
			slotRecordedFills.remove(slot);
			saveSlotRecordedFills();
		}
		else
		{
			prevSlotStates.put(slot, state);
		}
	}

	private void recordIfNewFills(GrandExchangeOffer offer, int slot)
	{
		int currentQty = offer.getQuantitySold();
		long currentGp = offer.getSpent();
		long[] prev = slotRecordedFills.get(slot);
		boolean firstObservation = prev == null;
		long prevQty = prev != null ? prev[0] : 0L;
		long prevGp  = prev != null ? prev[1] : 0L;
		long offerInstanceId = prev != null && prev.length >= 3
			? prev[2]
			: System.currentTimeMillis() * 10 + slot;

		boolean cumulativeDropped = currentQty < prevQty;
		boolean identityChanged = false;
		if (!firstObservation && !cumulativeDropped)
		{
			int baselineIdx = findMatchingOfferRow(tradeHistory, offerInstanceId);
			TradeRecord baselineRow = baselineIdx >= 0 ? tradeHistory.get(baselineIdx) : null;
			identityChanged = slotBaselineIsStale(baselineRow, offer.getItemId(), offer.getTotalQuantity());
		}
		if (cumulativeDropped || identityChanged)
		{
			prevQty = 0L;
			prevGp  = 0L;
			firstObservation = true;
			offerInstanceId = System.currentTimeMillis() * 10 + slot;
		}

		int  deltaQty = currentQty - (int) prevQty;
		long deltaGp  = currentGp  - prevGp;
		if (deltaQty <= 0)
		{
			GrandExchangeOfferState terminalState = offer.getState();
			if (terminalState == GrandExchangeOfferState.BOUGHT
				|| terminalState == GrandExchangeOfferState.SOLD
				|| terminalState == GrandExchangeOfferState.CANCELLED_BUY
				|| terminalState == GrandExchangeOfferState.CANCELLED_SELL)
			{
				finaliseAndPostExistingRow(offerInstanceId, terminalState);
			}
			return;
		}

		GrandExchangeOfferState state = offer.getState();
		boolean isBuy = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;
		boolean partial = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.SELLING
			|| state == GrandExchangeOfferState.CANCELLED_BUY
			|| state == GrandExchangeOfferState.CANCELLED_SELL;

		if (firstObservation)
		{
			long fallbackPriceEach = deltaQty > 0 ? deltaGp / deltaQty : offer.getPrice();
			int  totalQtyForLookup = offer.getTotalQuantity();
			int existingIdx = findClaimableLegacyOfferRow(tradeHistory, offer.getItemId(), isBuy, fallbackPriceEach);
			if (existingIdx < 0)
			{
				existingIdx = findReObservableActiveOfferRow(tradeHistory,
					offer.getItemId(), isBuy, totalQtyForLookup, slot, currentQty, currentGp);
			}
			if (existingIdx < 0
				&& (state == GrandExchangeOfferState.BOUGHT
					|| state == GrandExchangeOfferState.SOLD
					|| state == GrandExchangeOfferState.CANCELLED_BUY
					|| state == GrandExchangeOfferState.CANCELLED_SELL))
			{
				existingIdx = findRecordedTerminalOfferRow(tradeHistory,
					offer.getItemId(), isBuy, totalQtyForLookup, slot, currentQty, currentGp);
			}
			if (existingIdx >= 0)
			{
				TradeRecord existing = tradeHistory.get(existingIdx);
				if (existing.offerInstanceId != null)
				{
					offerInstanceId = existing.offerInstanceId;
				}
				else
				{
					stampLegacyWithOfferInstanceId(existingIdx, offerInstanceId);
				}

				int  existingQty = existing.quantity;
				long existingGp  = existing.totalGp;
				if (existingQty >= currentQty)
				{
					slotRecordedFills.put(slot, new long[]{existingQty, existingGp, offerInstanceId});
					saveSlotRecordedFills();
					return;
				}
				deltaQty = currentQty - existingQty;
				deltaGp  = currentGp  - existingGp;
				firstObservation = false;
			}
		}

		long timestamp = System.currentTimeMillis();

		recordTrade(offer, isBuy, partial, deltaQty, deltaGp, timestamp, offerInstanceId);

		slotRecordedFills.put(slot, new long[]{currentQty, currentGp, offerInstanceId});
		saveSlotRecordedFills();
	}

	private void recordTrade(GrandExchangeOffer offer, boolean isBuy, boolean partial,
		int deltaQty, long deltaGp, long timestamp, long offerInstanceId)
	{
		String itemName = client.getItemDefinition(offer.getItemId()).getName();
		long fallbackPriceEach = deltaQty > 0 ? deltaGp / deltaQty : offer.getPrice();
		int  totalQty = offer.getTotalQuantity();

		List<TradeRecord> updated = new ArrayList<>(tradeHistory);
		int existingIdx = findMatchingOfferRow(updated, offerInstanceId);
		TradeRecord posted;
		if (existingIdx >= 0)
		{
			TradeRecord existing = updated.get(existingIdx);
			TradeRecord merged = new TradeRecord();
			merged.itemId          = existing.itemId;
			merged.name            = existing.name;
			merged.isBuy           = existing.isBuy;
			merged.quantity        = existing.quantity + deltaQty;
			merged.totalGp         = existing.totalGp  + deltaGp;
			merged.priceEach       = merged.quantity > 0 ? merged.totalGp / merged.quantity : existing.priceEach;
			merged.timestamp       = existing.timestamp;
			merged.partial         = partial;
			merged.tradeId         = existing.tradeId;
			merged.serverSynced    = existing.serverSynced;
			merged.offerInstanceId = offerInstanceId;
			merged.totalQuantity   = totalQty > 0 ? totalQty : existing.totalQuantity;
			updated.set(existingIdx, merged);
			posted = merged;
		}
		else
		{
			TradeRecord trade = new TradeRecord();
			trade.itemId          = offer.getItemId();
			trade.name            = itemName;
			trade.isBuy           = isBuy;
			trade.quantity        = deltaQty;
			trade.totalGp         = deltaGp;
			trade.priceEach       = fallbackPriceEach;
			trade.timestamp       = offerInstanceId / 10L;
			trade.partial         = partial;
			trade.offerInstanceId = offerInstanceId;
			trade.totalQuantity   = totalQty > 0 ? totalQty : null;
			updated.add(trade);
			posted = trade;
		}

		if (updated.size() > MAX_TRADE_HISTORY)
		{
			updated = updated.subList(updated.size() - MAX_TRADE_HISTORY, updated.size());
		}
		tradeHistory = Collections.unmodifiableList(updated);

		saveTradeHistory();

		if (posted.itemId == com.o7flip.util.BondLedger.BOND_ITEM_ID && deltaQty > 0)
		{
			TradeRecord delta = new TradeRecord();
			delta.itemId   = posted.itemId;
			delta.isBuy    = posted.isBuy;
			delta.quantity = deltaQty;
			delta.totalGp  = deltaGp;
			updateBondLedgerFor(delta);
		}

		if (isBuy)
		{
			getRecommendedPrices(offer.getItemId());
			recordBuyForLimit(offer.getItemId(), deltaQty, timestamp);
			if (!frozenSellByItemId.containsKey(offer.getItemId()))
			{
				freezeFromTrackedOrFetch(offer.getItemId());
			}
		}

		final List<TradeRecord> snapshot = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snapshot));

		adjustCapitalForTrade(offer.getItemId(), isBuy, deltaQty, deltaGp);

		long pricePer = deltaQty > 0 ? deltaGp / deltaQty : fallbackPriceEach;
		attributeTradeToActiveSlot(offer.getItemId(), deltaQty, pricePer, isBuy, timestamp, offerInstanceId);

		if (!isBuy && config.showGpDropOverlay())
		{
			long profit = computeProfitForFill(deltaQty, deltaGp, offer.getItemId(), timestamp);
			gpDropOverlay.queue(profit);
		}

		net.runelite.api.GrandExchangeOfferState st = offer.getState();
		boolean terminal = !partial
			|| st == net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY
			|| st == net.runelite.api.GrandExchangeOfferState.CANCELLED_SELL;
		if (terminal
			&& posted.quantity > 0
			&& !posted.serverSynced
			&& (posted.offerInstanceId == null || !tradePostsInFlight.contains(posted.offerInstanceId))
			&& config.shareTradeData()
			&& config.apiKey() != null
			&& !config.apiKey().trim().isEmpty())
		{
			TradeRecord rowForServer = new TradeRecord();
			rowForServer.itemId          = posted.itemId;
			rowForServer.name            = posted.name;
			rowForServer.isBuy           = posted.isBuy;
			rowForServer.quantity        = posted.quantity;
			rowForServer.totalGp         = posted.totalGp;
			rowForServer.priceEach       = posted.quantity > 0 ? posted.totalGp / posted.quantity : posted.priceEach;
			rowForServer.timestamp       = posted.timestamp;
			rowForServer.partial         = posted.partial;
			rowForServer.totalQuantity   = posted.totalQuantity;
			rowForServer.offerInstanceId = posted.offerInstanceId;
			final Long postedOid = posted.offerInstanceId;
			if (postedOid != null)
			{
				tradePostsInFlight.add(postedOid);
			}
			apiClient.postTradeRecord(rowForServer, (delivered, tradeId) ->
				clientThread.invoke(() ->
				{
					if (postedOid != null)
					{
						tradePostsInFlight.remove(postedOid);
						if (delivered)
						{
							markRowSynced(postedOid, tradeId);
						}
					}
				}));
		}

		if (!isBuy)
		{
			unfreezeIfPositionClosed(posted.itemId);
		}
	}

	private static int findMatchingOfferRow(List<TradeRecord> list, long offerInstanceId)
	{
		for (int i = list.size() - 1; i >= 0; i--)
		{
			Long id = list.get(i).offerInstanceId;
			if (id != null && id == offerInstanceId)
			{
				return i;
			}
		}
		return -1;
	}

	static boolean slotBaselineIsStale(TradeRecord baselineRow, int currentItemId, int currentTotalQuantity)
	{
		if (baselineRow == null)
		{
			return false;
		}
		if (baselineRow.itemId != currentItemId)
		{
			return true;
		}
		return baselineRow.totalQuantity != null
			&& currentTotalQuantity > 0
			&& baselineRow.totalQuantity != currentTotalQuantity;
	}

	private void clearPartialFlag(long offerInstanceId)
	{
		int idx = findMatchingOfferRow(tradeHistory, offerInstanceId);
		if (idx < 0)
		{
			return;
		}
		TradeRecord existing = tradeHistory.get(idx);
		if (!existing.partial)
		{
			return;
		}
		List<TradeRecord> updated = new ArrayList<>(tradeHistory);
		TradeRecord cleared = new TradeRecord();
		cleared.itemId          = existing.itemId;
		cleared.name            = existing.name;
		cleared.isBuy           = existing.isBuy;
		cleared.quantity        = existing.quantity;
		cleared.totalGp         = existing.totalGp;
		cleared.priceEach       = existing.priceEach;
		cleared.timestamp       = existing.timestamp;
		cleared.partial         = false;
		cleared.tradeId         = existing.tradeId;
		cleared.serverSynced    = existing.serverSynced;
		cleared.offerInstanceId = existing.offerInstanceId;
		cleared.totalQuantity   = existing.totalQuantity;
		updated.set(idx, cleared);
		tradeHistory = Collections.unmodifiableList(updated);
		saveTradeHistory();
	}

	private void markRowSynced(long offerInstanceId, Long tradeId)
	{
		int idx = findMatchingOfferRow(tradeHistory, offerInstanceId);
		if (idx < 0)
		{
			return;
		}
		TradeRecord existing = tradeHistory.get(idx);
		boolean stampId = tradeId != null && existing.tradeId == null;
		if (existing.serverSynced && !stampId)
		{
			return;
		}
		TradeRecord stamped = new TradeRecord();
		stamped.itemId          = existing.itemId;
		stamped.name            = existing.name;
		stamped.isBuy           = existing.isBuy;
		stamped.quantity        = existing.quantity;
		stamped.totalGp         = existing.totalGp;
		stamped.priceEach       = existing.priceEach;
		stamped.timestamp       = existing.timestamp;
		stamped.partial         = existing.partial;
		stamped.tradeId         = stampId ? tradeId : existing.tradeId;
		stamped.serverSynced    = true;
		stamped.offerInstanceId = existing.offerInstanceId;
		stamped.totalQuantity   = existing.totalQuantity;
		List<TradeRecord> updated = new ArrayList<>(tradeHistory);
		updated.set(idx, stamped);
		tradeHistory = Collections.unmodifiableList(updated);
		saveTradeHistory();
	}

	private void finaliseAndPostExistingRow(long offerInstanceId, GrandExchangeOfferState terminalState)
	{
		int idx = findMatchingOfferRow(tradeHistory, offerInstanceId);
		if (idx < 0)
		{
			return;
		}
		TradeRecord existing = tradeHistory.get(idx);
		if (existing.quantity <= 0)
		{
			return;
		}

		TradeRecord toPost = existing;
		boolean shouldClearPartial = (terminalState == GrandExchangeOfferState.BOUGHT
			|| terminalState == GrandExchangeOfferState.SOLD) && existing.partial;
		if (shouldClearPartial)
		{
			TradeRecord cleared = new TradeRecord();
			cleared.itemId          = existing.itemId;
			cleared.name            = existing.name;
			cleared.isBuy           = existing.isBuy;
			cleared.quantity        = existing.quantity;
			cleared.totalGp         = existing.totalGp;
			cleared.priceEach       = existing.priceEach;
			cleared.timestamp       = existing.timestamp;
			cleared.partial         = false;
			cleared.tradeId         = existing.tradeId;
			cleared.serverSynced    = existing.serverSynced;
			cleared.offerInstanceId = existing.offerInstanceId;
			cleared.totalQuantity   = existing.totalQuantity;
			List<TradeRecord> updated = new ArrayList<>(tradeHistory);
			updated.set(idx, cleared);
			tradeHistory = Collections.unmodifiableList(updated);
			saveTradeHistory();
			toPost = cleared;
		}

		if (toPost.serverSynced
			|| (toPost.offerInstanceId != null && tradePostsInFlight.contains(toPost.offerInstanceId)))
		{
			return;
		}

		if (config.shareTradeData()
			&& config.apiKey() != null
			&& !config.apiKey().trim().isEmpty())
		{
			TradeRecord rowForServer = new TradeRecord();
			rowForServer.itemId          = toPost.itemId;
			rowForServer.name            = toPost.name;
			rowForServer.isBuy           = toPost.isBuy;
			rowForServer.quantity        = toPost.quantity;
			rowForServer.totalGp         = toPost.totalGp;
			rowForServer.priceEach       = toPost.priceEach;
			rowForServer.timestamp       = toPost.timestamp;
			rowForServer.partial         = toPost.partial;
			rowForServer.totalQuantity   = toPost.totalQuantity;
			rowForServer.offerInstanceId = toPost.offerInstanceId;
			final Long postedOid = toPost.offerInstanceId;
			if (postedOid != null)
			{
				tradePostsInFlight.add(postedOid);
			}
			apiClient.postTradeRecord(rowForServer, (delivered, tradeId) ->
				clientThread.invoke(() ->
				{
					if (postedOid != null)
					{
						tradePostsInFlight.remove(postedOid);
						if (delivered)
						{
							markRowSynced(postedOid, tradeId);
						}
					}
				}));
		}
	}

	private void stampLegacyWithOfferInstanceId(int idx, long offerInstanceId)
	{
		List<TradeRecord> updated = new ArrayList<>(tradeHistory);
		TradeRecord legacy = updated.get(idx);
		TradeRecord stamped = new TradeRecord();
		stamped.itemId          = legacy.itemId;
		stamped.name            = legacy.name;
		stamped.isBuy           = legacy.isBuy;
		stamped.quantity        = legacy.quantity;
		stamped.totalGp         = legacy.totalGp;
		stamped.priceEach       = legacy.priceEach;
		stamped.timestamp       = legacy.timestamp;
		stamped.partial         = legacy.partial;
		stamped.tradeId         = legacy.tradeId;
		stamped.serverSynced    = legacy.serverSynced;
		stamped.offerInstanceId = offerInstanceId;
		stamped.totalQuantity   = legacy.totalQuantity;
		updated.set(idx, stamped);
		tradeHistory = Collections.unmodifiableList(updated);
		saveTradeHistory();
	}

	private static int findClaimableLegacyOfferRow(List<TradeRecord> list, int itemId, boolean isBuy, long priceEach)
	{
		int searchDepth = Math.min(16, list.size());
		for (int i = list.size() - 1, scanned = 0; i >= 0 && scanned < searchDepth; i--, scanned++)
		{
			TradeRecord t = list.get(i);
			if (t.offerInstanceId != null) continue;        // already-owned row
			if (!t.partial)                continue;        // legacy completed — leave alone
			if (t.itemId != itemId)        continue;
			if (t.isBuy != isBuy)          continue;
			if (t.priceEach != priceEach)  continue;
			return i;
		}
		return -1;
	}

	static int findReObservableActiveOfferRow(
		List<TradeRecord> list, int itemId, boolean isBuy, int totalQuantity, int slot,
		int currentQty, long currentGp)
	{
		if (totalQuantity <= 0)
		{
			return -1;
		}
		int searchDepth = Math.min(32, list.size());
		for (int i = list.size() - 1, scanned = 0; i >= 0 && scanned < searchDepth; i--, scanned++)
		{
			TradeRecord t = list.get(i);
			if (t.offerInstanceId == null) continue;          // need a locally-merged row
			if (!t.partial)                continue;          // terminal row isn't this active offer
			if (t.itemId != itemId)        continue;
			if (t.isBuy != isBuy)          continue;
			if (t.totalQuantity == null || t.totalQuantity != totalQuantity) continue;
			if (t.offerInstanceId % 10 != slot) continue;
			if (t.quantity > currentQty) continue;
			if (t.totalGp  > currentGp)  continue;
			return i;
		}
		return -1;
	}

	static int findRecordedTerminalOfferRow(
		List<TradeRecord> list, int itemId, boolean isBuy, int totalQuantity, int slot,
		int currentQty, long currentGp)
	{
		if (totalQuantity <= 0)
		{
			return -1;
		}
		int searchDepth = Math.min(32, list.size());
		for (int i = list.size() - 1, scanned = 0; i >= 0 && scanned < searchDepth; i--, scanned++)
		{
			TradeRecord t = list.get(i);
			if (t.offerInstanceId == null) continue;          // need a locally-merged row
			if (t.itemId != itemId)        continue;
			if (t.isBuy != isBuy)          continue;
			if (t.totalQuantity == null || t.totalQuantity != totalQuantity) continue;
			if (t.offerInstanceId % 10 != slot) continue;     // offer-epoch slot continuity
			if (t.quantity != currentQty) continue;           // exact-match only — see javadoc
			if (t.totalGp  != currentGp)  continue;
			return i;
		}
		return -1;
	}

	private long computeProfitForFill(int deltaQty, long deltaGp, int itemId, long fillTimestamp)
	{
		List<TradeRecord> probe = new ArrayList<>(tradeHistory);
		TradeRecord virtualSell = new TradeRecord();
		virtualSell.itemId    = itemId;
		virtualSell.isBuy     = false;
		virtualSell.quantity  = deltaQty;
		virtualSell.totalGp   = deltaGp;
		virtualSell.priceEach = deltaQty > 0 ? deltaGp / deltaQty : 0L;
		virtualSell.timestamp = fillTimestamp + 1L;  // sort after history
		probe.add(virtualSell);
		com.o7flip.util.ProfitCalculator.Result r = com.o7flip.util.ProfitCalculator.compute(probe);
		long total = 0L;
		for (com.o7flip.util.ProfitCalculator.CompletedFlip f : r.completedFlips)
		{
			if (f.sellTimestamp == virtualSell.timestamp && f.buyTotal > 0)
			{
				total += f.profit;
			}
		}
		return total;
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
				int before = list.size();
				String prevScrub = configManager.getConfiguration("o7flip", SCRUB_VERSION_KEY);
				boolean needsScrub = !SCRUB_VERSION_CURRENT.equals(prevScrub);
				if (needsScrub)
				{
					list = com.o7flip.util.TradeHistoryDedup.scrub(list);
					configManager.setConfiguration("o7flip", SCRUB_VERSION_KEY, SCRUB_VERSION_CURRENT);
				}
				int removed = before - list.size();

				int healed = 0;
				if (!"true".equals(configManager.getConfiguration("o7flip", TRADE_HISTORY_HEALED_KEY)))
				{
					List<TradeRecord> healedList = com.o7flip.util.TradeHistoryDedup.healBackdatedTimestamps(list);
					for (int i = 0; i < list.size(); i++)
					{
						if (list.get(i) != healedList.get(i))
						{
							healed++;
						}
					}
					list = healedList;
					if (healed > 0)
					{
						list.sort(java.util.Comparator.comparingLong(t -> t.timestamp));
					}
					configManager.setConfiguration("o7flip", TRADE_HISTORY_HEALED_KEY, "true");
				}

				if (removed > 0 || healed > 0)
				{
					log.debug("[07Flip] Trade history load: scrubbed={}, healed back-dated timestamps={}", removed, healed);
					tradeHistory = Collections.unmodifiableList(list);
					saveTradeHistory();
				}
				else
				{
					tradeHistory = Collections.unmodifiableList(list);
				}
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
		configManager.unsetConfiguration("o7flip", LAST_TRACKER_SYNC_KEY);
		slotRecordedFills.clear();
		configManager.unsetConfiguration("o7flip", SLOT_FILLS_KEY);
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(Collections.emptyList()));
	}

	private void loadBondLedger()
	{
		String migrated = configManager.getConfiguration("o7flip", BOND_LEDGER_MIGRATED_KEY);
		String spendStr = configManager.getConfiguration("o7flip", BOND_LEDGER_SPEND_KEY);
		String countStr = configManager.getConfiguration("o7flip", BOND_LEDGER_COUNT_KEY);

		long spend = 0L;
		int  count = 0;
		try
		{
			if (spendStr != null && !spendStr.trim().isEmpty()) spend = Long.parseLong(spendStr.trim());
			if (countStr != null && !countStr.trim().isEmpty()) count = Integer.parseInt(countStr.trim());
		}
		catch (NumberFormatException e)
		{
			log.warn("[07Flip] Bond ledger config malformed, resetting: {}", e.getMessage());
			spend = 0L;
			count = 0;
		}
		bondLedger = new com.o7flip.util.BondLedger(spend, count);

		if (!"true".equals(migrated))
		{
			com.o7flip.util.BondLedger seeded = com.o7flip.util.BondLedger.seedFromHistory(tradeHistory);
			if (seeded.spend > 0L || seeded.count > 0)
			{
				bondLedger = seeded;
				log.debug("[07Flip] Bond ledger migrated from tradeHistory: {} gp · {} bonds",
					seeded.spend, seeded.count);
			}
			configManager.setConfiguration("o7flip", BOND_LEDGER_MIGRATED_KEY, "true");
			saveBondLedger();
		}
	}

	private void saveBondLedger()
	{
		configManager.setConfiguration("o7flip", BOND_LEDGER_SPEND_KEY, String.valueOf(bondLedger.spend));
		configManager.setConfiguration("o7flip", BOND_LEDGER_COUNT_KEY, String.valueOf(bondLedger.count));
	}

	public void setBondLedger(long spend, int count)
	{
		bondLedger = new com.o7flip.util.BondLedger(spend, count);
		saveBondLedger();
		configManager.setConfiguration("o7flip", BOND_LEDGER_MIGRATED_KEY, "true");
		final List<TradeRecord> snapshot = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snapshot));
	}

	public boolean isMembershipCostHidden()
	{
		return "true".equals(configManager.getConfiguration("o7flip", MEMBERSHIP_HIDDEN_KEY));
	}

	public void setMembershipCostHidden(boolean hidden)
	{
		configManager.setConfiguration("o7flip", MEMBERSHIP_HIDDEN_KEY, hidden ? "true" : "false");
		final List<TradeRecord> snapshot = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snapshot));
	}

	private void updateBondLedgerFor(TradeRecord trade)
	{
		if (trade == null || trade.itemId != com.o7flip.util.BondLedger.BOND_ITEM_ID)
		{
			return;
		}
		com.o7flip.util.BondLedger next = bondLedger.apply(trade);
		if (next.spend == bondLedger.spend && next.count == bondLedger.count)
		{
			return;
		}
		bondLedger = next;
		saveBondLedger();
	}

	private void saveSlotRecordedFills()
	{
		if (slotRecordedFills.isEmpty())
		{
			configManager.unsetConfiguration("o7flip", SLOT_FILLS_KEY);
			return;
		}
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<Integer, long[]> entry : slotRecordedFills.entrySet())
		{
			if (!first)
			{
				sb.append(',');
			}
			long[] v = entry.getValue();
			long offerId = v.length >= 3 ? v[2] : System.currentTimeMillis();
			sb.append(entry.getKey()).append(':').append(v[0]).append(':').append(v[1]).append(':').append(offerId);
			first = false;
		}
		configManager.setConfiguration("o7flip", SLOT_FILLS_KEY, sb.toString());
	}

	private void loadSlotRecordedFills()
	{
		String csv = configManager.getConfiguration("o7flip", SLOT_FILLS_KEY);
		if (csv == null || csv.trim().isEmpty())
		{
			return;
		}
		for (String tok : csv.split(","))
		{
			String[] parts = tok.split(":");
			if (parts.length < 3) continue;
			try
			{
				int slot = Integer.parseInt(parts[0]);
				long qty = Long.parseLong(parts[1]);
				long gp  = Long.parseLong(parts[2]);
				long offerId = parts.length >= 4
					? Long.parseLong(parts[3])
					: System.currentTimeMillis() * 10 + slot;
				slotRecordedFills.put(slot, new long[]{qty, gp, offerId});
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}


	public boolean isBlocked(int itemId)
	{
		return blocklist.contains(itemId);
	}

	public void addToBlocklist(int itemId)
	{
		Set<Integer> next = new HashSet<>(blocklist);
		if (next.add(itemId))
		{
			blocklist = Collections.unmodifiableSet(next);
			saveBlocklist();
			SwingUtilities.invokeLater(() -> panel.rebuildTabs());
		}
	}

	public void removeFromBlocklist(int itemId)
	{
		Set<Integer> next = new HashSet<>(blocklist);
		if (next.remove(itemId))
		{
			blocklist = Collections.unmodifiableSet(next);
			saveBlocklist();
			SwingUtilities.invokeLater(() -> panel.rebuildTabs());
		}
	}

	public void clearBlocklist()
	{
		blocklist = Collections.emptySet();
		configManager.unsetConfiguration("o7flip", BLOCKLIST_KEY);
		SwingUtilities.invokeLater(() -> panel.rebuildTabs());
	}

	private void loadBlocklist()
	{
		String csv = configManager.getConfiguration("o7flip", BLOCKLIST_KEY);
		if (csv == null || csv.trim().isEmpty())
		{
			blocklist = Collections.emptySet();
			return;
		}
		Set<Integer> ids = new HashSet<>();
		for (String token : csv.split(","))
		{
			try
			{
				ids.add(Integer.parseInt(token.trim()));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		blocklist = Collections.unmodifiableSet(ids);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.refreshBlocklistFooter();
			}
		});
	}

	private void saveBlocklist()
	{
		if (blocklist.isEmpty())
		{
			configManager.unsetConfiguration("o7flip", BLOCKLIST_KEY);
			return;
		}
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Integer id : blocklist)
		{
			if (!first)
			{
				sb.append(',');
			}
			sb.append(id);
			first = false;
		}
		configManager.setConfiguration("o7flip", BLOCKLIST_KEY, sb.toString());
	}



	public com.o7flip.model.RecommendedPrices getRecommendedPrices(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}
		Long fetched = recPriceFetchedAt.get(itemId);
		boolean stale = fetched == null || (System.currentTimeMillis() - fetched) > REC_PRICE_TTL_MS;
		if (stale && executor != null && !executor.isShutdown() && recPriceInFlight.add(itemId))
		{
			executor.execute(() -> apiClient.fetchRecommendedPrices(itemId,
				rp ->
				{
					try
					{
						if (rp != null)
						{
							recPriceCache.put(itemId, rp);
							clientThread.invokeLater(() ->
							{
								armSellPriceIfStillRelevant(itemId);
								armBuyPriceIfStillRelevant(itemId);
							});
						}
						recPriceFetchedAt.put(itemId, System.currentTimeMillis());
					}
					finally
					{
						recPriceInFlight.remove(itemId);
					}
				},
				retryMs ->
				{
					recPriceInFlight.remove(itemId);
					if (executor != null && !executor.isShutdown())
					{
						executor.schedule(() -> apiClient.fetchRecommendedPrices(itemId, rp ->
						{
							if (rp != null)
							{
								recPriceCache.put(itemId, rp);
								recPriceFetchedAt.put(itemId, System.currentTimeMillis());
								clientThread.invokeLater(() ->
								{
									armSellPriceIfStillRelevant(itemId);
									armBuyPriceIfStillRelevant(itemId);
								});
							}
						}), retryMs, TimeUnit.MILLISECONDS);
					}
				}));
		}
		return recPriceCache.get(itemId);
	}

	public com.o7flip.model.ItemInsights getOverlayInsights(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}
		Long fetched = overlayInsightsFetchedAt.get(itemId);
		boolean stale = fetched == null || (System.currentTimeMillis() - fetched) > REC_PRICE_TTL_MS;
		if (stale && executor != null && !executor.isShutdown() && overlayInsightsInFlight.add(itemId))
		{
			executor.execute(() -> apiClient.fetchItemInsights(itemId, ins ->
			{
				try
				{
					if (ins != null)
					{
						overlayInsightsCache.put(itemId, ins);
						clientThread.invokeLater(() ->
						{
							armSellPriceIfStillRelevant(itemId);
							armBuyPriceIfStillRelevant(itemId);
						});
					}
					overlayInsightsFetchedAt.put(itemId, System.currentTimeMillis());
				}
				finally
				{
					overlayInsightsInFlight.remove(itemId);
				}
			}));
		}
		return overlayInsightsCache.get(itemId);
	}

	public void syncTrackerHistory()
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(this::doSyncTrackerHistory);
		executor.execute(this::doFetchTrackerStats);
	}

	public void fetchTrackerStats()
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(this::doFetchTrackerStats);
	}

	private void doFetchTrackerStats()
	{
		if (!config.shareTradeData())
		{
			trackerStats = null;
			final List<TradeRecord> snap = tradeHistory;
			SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));
			return;
		}
		apiClient.fetchTrackerStats(stats ->
		{
			trackerStats = stats;
			final List<TradeRecord> snap = tradeHistory;
			SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));
		});
	}

	public void openInsights(int itemId, String fallbackName)
	{
		if (itemId <= 0)
		{
			return;
		}
		SwingUtilities.invokeLater(() -> panel.showInsightsLoading(itemId, fallbackName));
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(() -> doFetchItemInsights(itemId));
	}

	private void doFetchItemInsights(int itemId)
	{
		apiClient.fetchItemInsights(itemId, insights ->
		{
			if (insights != null && insights.buyLimit > 0)
			{
				rememberBuyLimit(insights.itemId, insights.buyLimit);
			}
			com.o7flip.model.ItemInsights existing = currentInsights;
			if (existing != null && insights != null && existing.itemId == insights.itemId)
			{
				currentInsights = insights;
			}
			else if (existing == null || (insights != null && existing.itemId == insights.itemId))
			{
				currentInsights = insights;
			}
			SwingUtilities.invokeLater(() -> panel.showInsights(itemId, insights));
		});
	}

	private void doBulkSyncToServer()
	{
		String key = config.apiKey();
		if (key == null || key.trim().isEmpty())
		{
			return;
		}
		if (!config.shareTradeData())
		{
			return;
		}
		List<TradeRecord> all = tradeHistory;
		if (all == null || all.isEmpty())
		{
			return;
		}
		List<TradeRecord> snapshot = new ArrayList<>();
		for (TradeRecord t : all)
		{
			if (t != null && !t.serverSynced && t.tradeId == null)
			{
				snapshot.add(t);
			}
		}
		if (snapshot.isEmpty())
		{
			log.debug("[07Flip] Bulk sync to server: all {} local rows already delivered", all.size());
			return;
		}
		apiClient.postTradeRecordsBulk(snapshot, res ->
		{
			if (res == null || !res.ok) return;
			if (res.accepted > 0)
			{
				log.info("[07Flip] Bulk sync to server: +{} new, {} duplicates, {} rejected (of {} offered)",
					res.accepted, res.duplicates, res.rejected, snapshot.size());
			}
			else
			{
				log.debug("[07Flip] Bulk sync to server: server already has all {} offered rows",
					snapshot.size());
			}
			clientThread.invoke(() ->
			{
				for (Map.Entry<Long, Long> e : res.tradeIdsByOfferInstanceId.entrySet())
				{
					markRowSynced(e.getKey(), e.getValue());
				}
				if (res.rejected == 0)
				{
					for (TradeRecord t : snapshot)
					{
						if (t.offerInstanceId != null)
						{
							markRowSynced(t.offerInstanceId, null);
						}
					}
				}
			});
		});
	}

	private void doSyncTrackerHistory()
	{
		String key = config.apiKey();
		if (key == null || key.trim().isEmpty())
		{
			return;
		}
		if (!config.shareTradeData())
		{
			return;
		}
		Long since = readLastSyncTimestamp();
		apiClient.fetchTrackerHistory(since, MAX_TRADE_HISTORY, (serverTrades, hasMore) ->
		{
			if (serverTrades == null || serverTrades.isEmpty())
			{
				return;
			}
			mergeServerTrades(serverTrades);
		});
	}

	private Long readLastSyncTimestamp()
	{
		String s = configManager.getConfiguration("o7flip", LAST_TRACKER_SYNC_KEY);
		if (s == null || s.trim().isEmpty())
		{
			return null;
		}
		try
		{
			return Long.parseLong(s.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private void writeLastSyncTimestamp(long ts)
	{
		configManager.setConfiguration("o7flip", LAST_TRACKER_SYNC_KEY, String.valueOf(ts));
	}

	private void mergeServerTrades(List<TradeRecord> serverTrades)
	{
		List<TradeRecord> snapshot = new ArrayList<>(tradeHistory);
		Map<Long, TradeRecord> byTradeId = new HashMap<>();
		Map<String, TradeRecord> byFingerprint = new HashMap<>();
		for (TradeRecord r : snapshot)
		{
			if (r.tradeId != null)
			{
				byTradeId.put(r.tradeId, r);
			}
			byFingerprint.put(r.fingerprint(), r);
		}

		Long lastSync = readLastSyncTimestamp();
		long maxTs = lastSync != null ? lastSync : 0L;
		int added = 0;
		int reconciled = 0;
		for (TradeRecord srv : serverTrades)
		{
			if (srv.tradeId != null && byTradeId.containsKey(srv.tradeId))
			{
			}
			else
			{
				TradeRecord local = byFingerprint.get(srv.fingerprint());
				if (local != null)
				{
					if (local.tradeId == null && srv.tradeId != null)
					{
						local.tradeId = srv.tradeId;
						reconciled++;
					}
				}
				else
				{
					snapshot.add(srv);
					added++;
				}
			}
			if (srv.timestamp > maxTs)
			{
				maxTs = srv.timestamp;
			}
		}

		snapshot.sort(java.util.Comparator.comparingLong(t -> t.timestamp));
		int beforeScrub = snapshot.size();
		snapshot = new ArrayList<>(com.o7flip.util.TradeHistoryDedup.scrub(snapshot));
		int scrubbed = beforeScrub - snapshot.size();
		if (snapshot.size() > MAX_TRADE_HISTORY)
		{
			snapshot = new ArrayList<>(snapshot.subList(snapshot.size() - MAX_TRADE_HISTORY, snapshot.size()));
		}

		tradeHistory = Collections.unmodifiableList(snapshot);
		saveTradeHistory();
		if (maxTs > 0L)
		{
			writeLastSyncTimestamp(maxTs);
		}

		log.debug("[07Flip] Tracker sync: +{} new, {} reconciled, {} scrubbed, total {}",
			added, reconciled, scrubbed, snapshot.size());

		final List<TradeRecord> snap = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));
	}


	void onFlipsPageChanged(int page)
	{
		executor.execute(() -> fetchFlipsAtPage(page));
	}

	private void fetchFlipsAtPage(int page)
	{
		apiClient.fetchFlips(
			panel.getSelectedPreset(),
			panel.getFlipsSortKey(),
			panel.getFlipsMinProfit(), panel.getFlipsPriceMin(), panel.getFlipsPriceMax(),
			cashStackBucketGp(),
			page,
			(items, total) ->
			{
				lastFlips = items;
				rebuildTrackedItems();
				rememberBuyLimits(items);
				SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, page));
			},
			upgradeUrl -> SwingUtilities.invokeLater(() -> panel.showPremiumRequiredToast(upgradeUrl))
		);
	}

	private long cashStackBucketGp()
	{
		return capitalFilterCeiling();
	}

	public long effectiveCapital()
	{
		long free = freeCapital();
		if (free <= 0)
		{
			return 0L;
		}
		return (free / CASH_BUCKET) * CASH_BUCKET;
	}

	public long capitalFilterCeiling()
	{
		long total = totalCapital();
		if (total <= 0)
		{
			return 0L;
		}
		return (total / CASH_BUCKET) * CASH_BUCKET;
	}

	public long freeCapital()
	{
		O7FlipConfig.CapitalMode mode = resolveCapitalMode();
		switch (mode)
		{
			case AUTO:
				return Math.max(0L, inventoryCoins);
			case MANUAL:
				return Math.max(0L, config.capitalManual() - deployedCapital());
			case OFF:
			default:
				return 0L;
		}
	}

	public long totalCapital()
	{
		O7FlipConfig.CapitalMode mode = resolveCapitalMode();
		switch (mode)
		{
			case AUTO:
				return Math.max(0L, inventoryCoins) + deployedCapital();
			case MANUAL:
				return Math.max(0L, config.capitalManual());
			case OFF:
			default:
				return 0L;
		}
	}

	public long deployedCapital()
	{
		long sum = 0L;
		for (com.o7flip.model.ActiveOfferSnapshot s : activeOffers.values())
		{
			if (s == null || s.state != GrandExchangeOfferState.BUYING)
			{
				continue;
			}
			int remaining = s.totalQuantity - s.quantitySold;
			if (remaining > 0 && s.price > 0)
			{
				sum += s.price * (long) remaining;
			}
		}
		return sum;
	}

	private O7FlipConfig.CapitalMode resolveCapitalMode()
	{
		O7FlipConfig.CapitalMode mode = config.capitalMode();
		if (mode == O7FlipConfig.CapitalMode.OFF && config.usePersonalisedFlips())
		{
			return O7FlipConfig.CapitalMode.AUTO;
		}
		return mode;
	}

	public void onCapitalChanged()
	{
		executor.execute(() ->
		{
			if (config.showFlips())
			{
				fetchFlipsAtPage(panel.getFlipsPage());
			}
		});
		SwingUtilities.invokeLater(() -> panel.rerenderCapitalAffectedTabs());
	}

	public void persistCapitalMode(O7FlipConfig.CapitalMode mode)
	{
		configManager.setConfiguration("o7flip", "capitalMode", mode);
	}

	public void setCapitalFilterEnabled(boolean enabled)
	{
		if (enabled)
		{
			configManager.setConfiguration("o7flip", "capitalMode", O7FlipConfig.CapitalMode.MANUAL);
		}
		else
		{
			configManager.setConfiguration("o7flip", "capitalMode", O7FlipConfig.CapitalMode.OFF);
			if (config.usePersonalisedFlips())
			{
				configManager.setConfiguration("o7flip", "usePersonalisedFlips", false);
			}
		}
		onCapitalChanged();
	}

	public void persistCapitalManual(long gp)
	{
		configManager.setConfiguration("o7flip", "capitalManual", gp);
	}

	public void persistCapitalLocked(boolean locked)
	{
		configManager.setConfiguration("o7flip", "capitalLocked", locked);
	}

	private void adjustCapitalForTrade(int itemId, boolean isBuy, int deltaQty, long deltaGp)
	{
		if (config.capitalMode() != O7FlipConfig.CapitalMode.MANUAL || deltaQty <= 0)
		{
			return;
		}
		long current = config.capitalManual();
		long adjusted;
		if (isBuy)
		{
			adjusted = current - deltaGp;
		}
		else
		{
			adjusted = current + deltaGp;
		}
		if (adjusted < 0)
		{
			adjusted = 0;
		}
		if (adjusted == current)
		{
			return;
		}
		persistCapitalManual(adjusted);
		if (panel != null)
		{
			SwingUtilities.invokeLater(panel::onCapitalAutoAdjusted);
		}
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

	void onDumpsPageChanged(int page)
	{
		executor.execute(() -> fetchDumpsAtPage(panel.getDumpsSortKey(), page));
	}

	void onDipsPageChanged(int page)
	{
		executor.execute(() -> fetchDipsAtPage(panel.getDipsSortKey(), page));
	}

	void onDipsSortChanged(String sort)
	{
		executor.execute(() -> fetchDipsAtPage(sort, 0));
	}

	private void fetchDipsAtPage(String sort, int page)
	{
		apiClient.fetchDips(sort, panel.getDipsActivityWindow(), page, (items, total) ->
		{
			if (items != null && !items.isEmpty()) saveCache("dips", items);
			SwingUtilities.invokeLater(() -> panel.updateDips(items, total, page));
		});
	}


	private final java.util.Map<String, Long> lastTabSelectFetchMs = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long TAB_SELECT_FRESHNESS_MS = 30_000L;

	private boolean tabSelectFresh(String name)
	{
		long now = System.currentTimeMillis();
		long last = lastTabSelectFetchMs.getOrDefault(name, 0L);
		if (now - last < TAB_SELECT_FRESHNESS_MS) return false;
		lastTabSelectFetchMs.put(name, now);
		return true;
	}

	void onOtherSubTabSelected(String name)
	{
		if (name == null || executor == null || executor.isShutdown()) return;
		if (!tabSelectFresh(name)) return;
		switch (name)
		{
			case "Dips":
				executor.execute(() -> fetchDipsAtPage(panel.getDipsSortKey(), panel.getDipsPage()));
				break;
			default:
				break;
		}
	}

	void onFavouritesTabSelected()
	{
		if (!hasApiKey())
		{
			return;
		}
		executor.execute(() -> apiClient.fetchFavourites(items ->
		{
			if (items != null && !items.isEmpty()) saveCache("favourites", items);
			pushFavouritesToPanel(items);
		}));
	}


	private volatile java.util.Set<Integer> favouriteItemIds = java.util.Collections.emptySet();

	private final java.util.Map<Integer, Long> recentlyAddedFavs = new java.util.concurrent.ConcurrentHashMap<>();

	private final java.util.Map<Integer, Long> recentlyRemovedFavs = new java.util.concurrent.ConcurrentHashMap<>();

	private final java.util.Map<Integer, FlipItem> recentlyAddedFavItems = new java.util.concurrent.ConcurrentHashMap<>();

	private static final long FAV_BUFFER_TTL_MS = 5 * 60 * 1000L;

	public boolean isFavourite(int itemId)
	{
		return favouriteItemIds.contains(itemId);
	}

	private static final String FAVOURITES_ORDER_KEY = "favouritesOrder";

	public java.util.List<Integer> getFavouritesOrder()
	{
		java.util.List<Integer> out = new java.util.ArrayList<>();
		String csv = configManager.getConfiguration("o7flip", FAVOURITES_ORDER_KEY);
		if (csv != null && !csv.isEmpty())
		{
			for (String s : csv.split(","))
			{
				try
				{
					out.add(Integer.parseInt(s.trim()));
				}
				catch (NumberFormatException ignored)
				{
				}
			}
		}
		return out;
	}

	public void setFavouritesOrder(java.util.List<Integer> ids)
	{
		if (ids == null || ids.isEmpty())
		{
			configManager.unsetConfiguration("o7flip", FAVOURITES_ORDER_KEY);
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ids.size(); i++)
		{
			if (i > 0) sb.append(',');
			sb.append(ids.get(i));
		}
		configManager.setConfiguration("o7flip", FAVOURITES_ORDER_KEY, sb.toString());
	}

	public void unfavouriteForReorder(int itemId)
	{
		if (!hasApiKey() || itemId <= 0)
		{
			return;
		}
		java.util.Set<Integer> next = new java.util.HashSet<>(favouriteItemIds);
		next.remove(itemId);
		favouriteItemIds = java.util.Collections.unmodifiableSet(next);
		long now = System.currentTimeMillis();
		recentlyRemovedFavs.put(itemId, now);
		recentlyAddedFavs.remove(itemId);
		recentlyAddedFavItems.remove(itemId);
		apiClient.removeFavourite(itemId, ok ->
		{
			if (!Boolean.TRUE.equals(ok))
			{
				log.warn("[07Flip] reorder unfavourite failed for itemId {}", itemId);
			}
		});
		SwingUtilities.invokeLater(() -> panel.onFavouriteToggled(itemId));
	}

	public void toggleFavourite(int itemId, boolean currentlyFav, Runnable onSuccess, Runnable onError)
	{
		if (!hasApiKey() || itemId <= 0)
		{
			if (onError != null) SwingUtilities.invokeLater(onError);
			return;
		}
		java.util.Set<Integer> snapshot = favouriteItemIds;
		java.util.Set<Integer> next = new java.util.HashSet<>(snapshot);
		if (currentlyFav) next.remove(itemId); else next.add(itemId);
		favouriteItemIds = java.util.Collections.unmodifiableSet(next);
		final FlipItem optimisticFav = currentlyFav ? null : buildFavouriteFlipItem(itemId);
		SwingUtilities.invokeLater(() ->
		{
			panel.onFavouriteToggled(itemId);
			if (currentlyFav)
			{
				panel.removeFavouriteRow(itemId);
			}
			else if (optimisticFav != null)
			{
				panel.addFavouriteRow(optimisticFav);
			}
		});

		java.util.function.Consumer<Boolean> done = ok -> SwingUtilities.invokeLater(() ->
		{
			if (Boolean.TRUE.equals(ok))
			{
				long now = System.currentTimeMillis();
				if (currentlyFav)
				{
					recentlyRemovedFavs.put(itemId, now);
					recentlyAddedFavs.remove(itemId);
					recentlyAddedFavItems.remove(itemId);
				}
				else
				{
					recentlyAddedFavs.put(itemId, now);
					recentlyRemovedFavs.remove(itemId);
					if (optimisticFav != null) recentlyAddedFavItems.put(itemId, optimisticFav);
				}
				if (onSuccess != null) onSuccess.run();
			}
			else
			{
				favouriteItemIds = snapshot;
				recentlyAddedFavItems.remove(itemId);
				panel.onFavouriteToggled(itemId);
				onFavouritesTabSelected();
				if (onError != null) onError.run();
			}
		});

		if (currentlyFav)
		{
			apiClient.removeFavourite(itemId, done);
		}
		else
		{
			apiClient.addFavourite(itemId, done);
		}
	}

	private FlipItem buildFavouriteFlipItem(int itemId)
	{
		com.o7flip.model.ItemInsights ins = currentInsights;
		if (ins != null && ins.itemId == itemId)
		{
			FlipItem f = new FlipItem();
			f.itemId = itemId;
			f.name   = ins.name;
			if (ins.current != null)
			{
				f.buyPrice     = ins.current.buyPrice;
				f.sellPrice    = ins.current.sellPrice;
				f.profit       = ins.current.profit;
				f.roiPct       = ins.current.roiPct;
				f.recBuyPrice  = ins.current.recBuy;
				f.recSellPrice = ins.current.recSell;
				f.recProfit    = ins.current.recProfit;
			}
			f.buyLimit = ins.buyLimit;
			if (ins.score != null) f.flip07Score = ins.score.confidence;
			if (ins.volume != null)
			{
				f.hourlyVolume = ins.volume.hourly;
				f.dailyVolume  = ins.volume.daily;
			}
			return f;
		}
		for (FlipItem f : lastFlips)
		{
			if (f.itemId == itemId) return f;
		}
		FlipItem stub = new FlipItem();
		stub.itemId = itemId;
		stub.name   = "Item " + itemId;
		return stub;
	}

	private java.util.List<FlipItem> reconcileFavouritesList(java.util.List<FlipItem> serverItems)
	{
		java.util.List<FlipItem> merged = new java.util.ArrayList<>();
		java.util.Set<Integer> present = new java.util.HashSet<>();
		if (serverItems != null)
		{
			for (FlipItem f : serverItems)
			{
				if (f == null || recentlyRemovedFavs.containsKey(f.itemId)) continue;
				merged.add(f);
				present.add(f.itemId);
			}
		}
		for (java.util.Map.Entry<Integer, FlipItem> e : recentlyAddedFavItems.entrySet())
		{
			if (!present.contains(e.getKey()))
			{
				merged.add(0, e.getValue());
			}
		}
		return merged;
	}

	private void pushFavouritesToPanel(java.util.List<FlipItem> serverItems)
	{
		rebuildFavouriteIds(serverItems);
		rememberBuyLimits(serverItems);
		final java.util.List<FlipItem> reconciled = reconcileFavouritesList(serverItems);
		SwingUtilities.invokeLater(() -> panel.updateFavourites(reconciled));
	}

	private void rebuildFavouriteIds(java.util.List<FlipItem> items)
	{
		long now = System.currentTimeMillis();
		java.util.Set<Integer> serverSet = new java.util.HashSet<>(items.size());
		for (FlipItem f : items)
		{
			if (f.itemId > 0) serverSet.add(f.itemId);
		}

		recentlyAddedFavs.entrySet().removeIf(e -> now - e.getValue() > FAV_BUFFER_TTL_MS);
		recentlyRemovedFavs.entrySet().removeIf(e -> now - e.getValue() > FAV_BUFFER_TTL_MS);

		recentlyAddedFavs.keySet().removeIf(serverSet::contains);
		recentlyRemovedFavs.keySet().removeIf(id -> !serverSet.contains(id));
		recentlyAddedFavItems.keySet().retainAll(recentlyAddedFavs.keySet());

		java.util.Set<Integer> merged = new java.util.HashSet<>(serverSet);
		merged.addAll(recentlyAddedFavs.keySet());
		merged.removeAll(recentlyRemovedFavs.keySet());
		favouriteItemIds = java.util.Collections.unmodifiableSet(merged);

		if (!recentlyAddedFavs.isEmpty() || !recentlyRemovedFavs.isEmpty())
		{
			log.info("[07Flip] /favourites GET = {} items; local toggle buffer: +{} / -{} (merged: {})",
				serverSet.size(),
				recentlyAddedFavs.size(),
				recentlyRemovedFavs.size(),
				merged.size());
		}
		else
		{
			log.debug("[07Flip] /favourites GET = {} items", serverSet.size());
		}
	}

	public void runOptimizer(long capital, int slots, String risk,
	                         int maxFillHours, Boolean members, Double minProfitPct)
	{
		if (executor == null || executor.isShutdown()) return;
		executor.execute(() -> apiClient.fetchOptimize(
			capital, slots, risk, maxFillHours, members,
			null, minProfitPct,
			result -> clientThread.invoke(() ->
			{
				seedActiveSessionFrom(result, capital, slots, risk, maxFillHours, members, minProfitPct);
				scheduleSessionPost();
				SwingUtilities.invokeLater(() -> panel.onOptimizeResult(result));
			}),
			upgradeUrl -> SwingUtilities.invokeLater(() -> panel.onOptimizePremiumRequired(upgradeUrl)),
			reason -> SwingUtilities.invokeLater(() -> panel.onOptimizeError(reason))));
	}

	public void rerunWithSlots(int slots)
	{
		com.o7flip.model.OptimizerSession s = activeSession;
		long capital = s != null ? s.inputs.capital : effectiveCapital();
		if (capital <= 0 || slots < 1) return;
		String risk        = s != null && s.inputs.risk != null ? s.inputs.risk : "medium";
		int maxFillHours   = s != null && s.inputs.maxFillHours != null ? s.inputs.maxFillHours : 4;
		Boolean members    = s != null ? s.inputs.members : null;
		Double minProfit   = s != null ? s.inputs.minProfitPct : null;
		runOptimizer(capital, Math.min(8, slots), risk, maxFillHours, members, minProfit);
	}

	public void swapPlanSlot(int swapIndex, com.o7flip.model.OptimizeResult current)
	{
		if (executor == null || executor.isShutdown() || current == null
			|| current.allocations == null || swapIndex < 0 || swapIndex >= current.allocations.size())
		{
			return;
		}
		com.o7flip.model.OptimizeResult.Allocation old = current.allocations.get(swapIndex);
		long slotCapital = old.gpAllocated;
		java.util.List<Integer> excludes = new java.util.ArrayList<>();
		for (com.o7flip.model.OptimizeResult.Allocation a : current.allocations)
		{
			if (a != null && a.itemId > 0) excludes.add(a.itemId);
		}
		String risk = current.summary != null && current.summary.risk != null
			? current.summary.risk : "medium";
		int maxFillHours = current.summary != null && current.summary.maxFillHours != null
			? current.summary.maxFillHours : 4;
		Boolean members = current.summary != null ? current.summary.members : null;
		Double minProfit = activeSession != null ? activeSession.inputs.minProfitPct : null;

		executor.execute(() -> apiClient.fetchOptimize(
			slotCapital, 1, risk, maxFillHours, members, excludes, minProfit,
			result -> SwingUtilities.invokeLater(() ->
			{
				if (result == null || result.allocations == null || result.allocations.isEmpty())
				{
					return;
				}
				panel.onOptimizeSlotSwapped(swapIndex, result.allocations.get(0));
				replaceSlotInActiveSession(swapIndex, result.allocations.get(0), result.updatedAt);
				scheduleSessionPost();
			}),
			upgradeUrl -> SwingUtilities.invokeLater(() -> panel.onOptimizePremiumRequired(upgradeUrl)),
			reason -> SwingUtilities.invokeLater(() -> panel.onOptimizeError(reason))));
	}


	private volatile com.o7flip.model.OptimizerSession activeSession;
	private volatile boolean offlineReconcileArmed = false;
	private ScheduledFuture<?> pendingSessionPost;
	private ScheduledFuture<?> sessionPollTask;
	private ScheduledFuture<?> sessionBackgroundPollTask;
	private final java.util.List<com.o7flip.model.CompletedPosition> completedPositions = new java.util.ArrayList<>();
	private static final long SESSION_POST_DEBOUNCE_MS = 1000L;
	private static final long SESSION_POLL_INTERVAL_S  = 15L;
	private static final long SESSION_BACKGROUND_POLL_INTERVAL_S = 15L;
	private static final int SESSION_BACKGROUND_IDLE_DIVISOR = 4;
	private int sessionBackgroundPollTick;

	private void doHydrateOptimizerSession()
	{
		if (panel == null || !panel.isPremium()) return;
		apiClient.fetchActiveSession(session ->
		{
			if (session == null || session.slots == null || session.slots.isEmpty())
			{
				return;
			}
			clientThread.invoke(() ->
			{
				activeSession = session;
				boolean changed = retroAttributeFills(session);
				changed |= sweepSellListedFromOffers(session);
				if (changed)
				{
					scheduleSessionPost();
				}
				offlineReconcileArmed = true;
				SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(session));
			});
		});
	}

	public void onPlanTabSelected()
	{
		if (panel == null || !panel.isPremium()) return;
		if (executor == null || executor.isShutdown()) return;
		executor.execute(this::doPollActiveSession);
		refreshCompletedPositions();
		if (sessionPollTask == null || sessionPollTask.isCancelled() || sessionPollTask.isDone())
		{
			sessionPollTask = executor.scheduleAtFixedRate(
				this::doPollActiveSession,
				SESSION_POLL_INTERVAL_S, SESSION_POLL_INTERVAL_S, TimeUnit.SECONDS);
		}
	}

	public void onPlanTabDeselected()
	{
		if (sessionPollTask != null) sessionPollTask.cancel(false);
	}

	private void startSessionBackgroundPoll()
	{
		if (executor == null || executor.isShutdown()) return;
		if (panel == null || !panel.isPremium()) return;
		if (sessionBackgroundPollTask == null
			|| sessionBackgroundPollTask.isCancelled()
			|| sessionBackgroundPollTask.isDone())
		{
			sessionBackgroundPollTask = executor.scheduleAtFixedRate(
				this::doBackgroundPollActiveSession,
				SESSION_BACKGROUND_POLL_INTERVAL_S, SESSION_BACKGROUND_POLL_INTERVAL_S, TimeUnit.SECONDS);
		}
	}

	public void onPanelShown()
	{
		startSessionBackgroundPoll();
		if (panel != null && panel.isPremium() && panel.isPlanTabActive())
		{
			onPlanTabSelected();
		}
	}

	public void onPanelHidden()
	{
		if (sessionBackgroundPollTask != null) sessionBackgroundPollTask.cancel(false);
		if (sessionPollTask != null) sessionPollTask.cancel(false);
		if (eagerDiscoveryTask != null) eagerDiscoveryTask.cancel(false);
	}

	private volatile ScheduledFuture<?> eagerDiscoveryTask;

	public void startEagerSessionDiscovery()
	{
		if (executor == null || executor.isShutdown()) return;
		if (eagerDiscoveryTask != null) eagerDiscoveryTask.cancel(false);
		final long deadline = System.currentTimeMillis() + 120_000L;
		eagerDiscoveryTask = executor.scheduleAtFixedRate(() ->
		{
			if (System.currentTimeMillis() > deadline)
			{
				ScheduledFuture<?> self = eagerDiscoveryTask;
				if (self != null) self.cancel(false);
				return;
			}
			doPollActiveSession();
		}, 5, 5, TimeUnit.SECONDS);
	}

	private boolean sessionHasInFlightLegs()
	{
		com.o7flip.model.OptimizerSession s = activeSession;
		if (s == null || s.slots == null)
		{
			return false;
		}
		try
		{
			for (com.o7flip.model.OptimizeResult.Allocation a : s.slots)
			{
				if (a == null) continue;
				if (a.state == com.o7flip.model.SlotState.BUYING
					|| a.state == com.o7flip.model.SlotState.FILLED
					|| a.state == com.o7flip.model.SlotState.SELLING)
				{
					return true;
				}
			}
		}
		catch (Exception e)
		{
			return true;
		}
		return false;
	}

	private void doBackgroundPollActiveSession()
	{
		if (panel == null || !panel.isPremium()) return;
		if (!panel.isShowing()) return;
		ScheduledFuture<?> fast = sessionPollTask;
		if (fast != null && !fast.isCancelled() && !fast.isDone())
		{
			return;
		}
		int tick = sessionBackgroundPollTick++;
		if (!sessionHasInFlightLegs() && tick % SESSION_BACKGROUND_IDLE_DIVISOR != 0)
		{
			return;
		}
		doPollActiveSession();
	}

	private void doPollActiveSession()
	{
		if (panel == null || !panel.isShowing()) return;
		apiClient.fetchActiveSession(remote ->
		{
			if (remote == null) return;
			clientThread.invoke(() ->
			{
				com.o7flip.model.OptimizerSession local = activeSession;
				if (local == null)
				{
					activeSession = remote;
					boolean seeded = retroAttributeFills(remote);
					seeded |= sweepSellListedFromOffers(remote);
					if (seeded)
					{
						scheduleSessionPost();
					}
					SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(remote));
					return;
				}
				boolean changed = mergeRemoteFills(local, remote);
				boolean healed = retroAttributeFills(local);
				healed |= sweepSellListedFromOffers(local);
				if (healed)
				{
					changed = true;
					scheduleSessionPost();
				}
				if (changed)
				{
					SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(local));
				}
			});
		});
	}

	public void resyncActivePlan()
	{
		if (panel == null || !panel.isPremium()) return;
		if (executor == null || executor.isShutdown()) return;
		apiClient.fetchActiveSession(remote ->
		{
			if (remote == null) return;
			clientThread.invoke(() ->
			{
				if (activeSession == null)
				{
					activeSession = remote;
				}
				else
				{
					adoptServerStructure(activeSession, remote);
				}
				boolean healed = retroAttributeFills(activeSession);
				healed |= sweepSellListedFromOffers(activeSession);
				if (healed)
				{
					scheduleSessionPost();
				}
				final com.o7flip.model.OptimizerSession snap = activeSession;
				SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
			});
		});
	}

	static boolean mergeRemoteFills(com.o7flip.model.OptimizerSession local,
	                                 com.o7flip.model.OptimizerSession remote)
	{
		if (local.slots == null || remote.slots == null) return false;
		if (isRemoteStructurallyNewer(local, remote))
		{
			adoptServerStructure(local, remote);
			return true;
		}
		boolean anyChange = false;
		java.util.Map<Integer, com.o7flip.model.OptimizeResult.Allocation> byId = new java.util.HashMap<>();
		for (com.o7flip.model.OptimizeResult.Allocation r : remote.slots)
		{
			if (r != null && r.itemId > 0) byId.put(r.itemId, r);
		}
		for (com.o7flip.model.OptimizeResult.Allocation l : local.slots)
		{
			if (l == null) continue;
			com.o7flip.model.OptimizeResult.Allocation r = byId.get(l.itemId);
			if (r == null) continue;
			if (r.overrideRev > l.appliedOverrideRev)
			{
				l.buys                    = new java.util.ArrayList<>(r.buys);
				l.sells                   = new java.util.ArrayList<>(r.sells);
				l.state                   = r.state;
				l.sellListed              = r.sellListed;   // §7 correction wins outright
				l.overrideRev             = r.overrideRev;
				l.overrideSource          = r.overrideSource;
				l.appliedOverrideRev      = r.overrideRev;
				l.pendingOfflineReconcile = false;
				anyChange = true;
				continue;
			}
			if (l.buys.isEmpty()  && mergeFillList(l.buys,  r.buys))  anyChange = true;
			if (l.sells.isEmpty() && mergeFillList(l.sells, r.sells)) anyChange = true;
			if (l.offerInstanceId == null && r.offerInstanceId != null)
			{
				l.offerInstanceId = r.offerInstanceId;
			}
			com.o7flip.model.SlotState derived =
				com.o7flip.model.SlotState.derive(l.qty, l.buys, l.sells);
			if (l.state != derived) { l.state = derived; anyChange = true; }
		}
		local.lastPollAt = remote.lastPollAt;
		return anyChange;
	}

	static boolean isRemoteStructurallyNewer(com.o7flip.model.OptimizerSession local,
		com.o7flip.model.OptimizerSession remote)
	{
		String rg = remote == null ? null : remote.generatedAt;
		if (rg == null || rg.isEmpty()) return false;
		String lg = local == null ? null : local.generatedAt;
		if (lg == null || lg.isEmpty()) return true;
		return isIsoAfter(rg, lg);
	}

	static boolean isIsoAfter(String a, String b)
	{
		try
		{
			return java.time.Instant.parse(a).isAfter(java.time.Instant.parse(b));
		}
		catch (Exception notInstants)
		{
			return a.compareTo(b) > 0;
		}
	}

	static String nextGeneratedAt(String prevIso, String serverUpdatedAt)
	{
		java.time.Instant base = parseInstantOrNull(serverUpdatedAt);
		if (base == null) base = java.time.Instant.now();
		java.time.Instant prev = parseInstantOrNull(prevIso);
		if (prev != null)
		{
			java.time.Instant floor = prev.plusSeconds(1);
			if (floor.isAfter(base)) base = floor;
		}
		return base.toString();
	}

	private static java.time.Instant parseInstantOrNull(String iso)
	{
		if (iso == null || iso.isEmpty()) return null;
		try { return java.time.Instant.parse(iso); }
		catch (Exception notAnInstant) { return null; }
	}

	static void adoptServerStructure(com.o7flip.model.OptimizerSession local,
		com.o7flip.model.OptimizerSession remote)
	{
		java.util.Map<Long, com.o7flip.model.OptimizeResult.Allocation> byOid = new java.util.HashMap<>();
		java.util.Map<Integer, com.o7flip.model.OptimizeResult.Allocation> byItem = new java.util.HashMap<>();
		if (local.slots != null)
		{
			for (com.o7flip.model.OptimizeResult.Allocation l : local.slots)
			{
				if (l == null) continue;
				if (l.offerInstanceId != null) byOid.put(l.offerInstanceId, l);
				if (l.itemId > 0) byItem.putIfAbsent(l.itemId, l);
			}
		}
		java.util.List<com.o7flip.model.OptimizeResult.Allocation> rebuilt = new java.util.ArrayList<>();
		if (remote.slots != null)
		{
			for (com.o7flip.model.OptimizeResult.Allocation r : remote.slots)
			{
				if (r == null) continue;
				com.o7flip.model.OptimizeResult.Allocation l =
					r.offerInstanceId != null ? byOid.get(r.offerInstanceId) : null;
				if (l == null && r.itemId > 0) l = byItem.get(r.itemId);
				if (l != null)
				{
					if (r.overrideRev > l.appliedOverrideRev)
					{
						r.appliedOverrideRev      = r.overrideRev;
						r.pendingOfflineReconcile = false;
					}
					else
					{
						boolean localHasFills = !l.buys.isEmpty() || !l.sells.isEmpty();
						if (localHasFills)
						{
							r.buys  = l.buys;
							r.sells = l.sells;
						}
						if (l.offerInstanceId != null) r.offerInstanceId = l.offerInstanceId;
						r.sellListed              = l.sellListed || r.sellListed;
						r.appliedOverrideRev      = Math.max(l.appliedOverrideRev, r.overrideRev);
						r.pendingOfflineReconcile = l.pendingOfflineReconcile;
						r.state = com.o7flip.model.SlotState.derive(r.qty, r.buys, r.sells);
					}
				}
				rebuilt.add(r);
			}
		}
		local.slots       = rebuilt;
		local.generatedAt = remote.generatedAt;
		local.updatedAt   = remote.updatedAt;
		local.summary     = remote.summary;
		local.lastPollAt  = remote.lastPollAt;
	}

	private static boolean mergeFillList(java.util.List<com.o7flip.model.SlotFill> local,
	                              java.util.List<com.o7flip.model.SlotFill> remote)
	{
		if (remote == null || remote.isEmpty()) return false;
		boolean any = false;
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (com.o7flip.model.SlotFill f : local) if (f != null) seen.add(fillKey(f));
		for (com.o7flip.model.SlotFill rf : remote)
		{
			if (rf == null) continue;
			if (seen.add(fillKey(rf))) { local.add(rf); any = true; }
		}
		return any;
	}

	private static String fillKey(com.o7flip.model.SlotFill f)
	{
		return f.qty + "@" + f.priceEach + "@" + (f.tradedAt == null ? "" : f.tradedAt);
	}

	private void reconcileOfflineCompletions()
	{
		com.o7flip.model.OptimizerSession s = activeSession;
		if (s == null || s.slots == null) return;

		if (sweepSellListedFromOffers(s))
		{
			scheduleSessionPost();
			final com.o7flip.model.OptimizerSession listedSnap = s;
			SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(listedSnap));
		}

		java.util.Set<Integer> liveOfferItems = new java.util.HashSet<>();
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers != null)
		{
			for (GrandExchangeOffer o : offers)
			{
				if (o == null) continue;
				GrandExchangeOfferState st = o.getState();
				if (st == GrandExchangeOfferState.BUYING || st == GrandExchangeOfferState.SELLING)
				{
					liveOfferItems.add(o.getItemId());
				}
			}
		}

		java.util.List<String> flagged = new java.util.ArrayList<>();
		for (com.o7flip.model.OptimizeResult.Allocation a : s.slots)
		{
			if (a == null || a.pendingOfflineReconcile) continue;
			if (!isOfflineSellCompletion(a, liveOfferItems)) continue;
			a.pendingOfflineReconcile = true;
			flagged.add(a.name != null ? a.name : ("item " + a.itemId));
		}

		if (flagged.isEmpty()) return;

		String msg = flagged.size() == 1
			? "07Flip: your sell of " + flagged.get(0) + " looks complete after being offline. "
				+ "Confirm or adjust it on 07flip.com and the plan will update."
			: "07Flip: " + flagged.size() + " optimiser sells look complete after being offline. "
				+ "Confirm or adjust them on 07flip.com and the plan will update.";
		notifier.notify(msg);

		final com.o7flip.model.OptimizerSession snap = s;
		SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
	}

	static boolean isOfflineSellCompletion(com.o7flip.model.OptimizeResult.Allocation a,
		java.util.Set<Integer> liveOfferItems)
	{
		return a != null
			&& a.state == com.o7flip.model.SlotState.SELLING
			&& !liveOfferItems.contains(a.itemId);
	}

	public void dismissOfflineReconcile(int itemId)
	{
		clientThread.invoke(() ->
		{
			com.o7flip.model.OptimizerSession s = activeSession;
			if (s == null || s.slots == null) return;
			boolean changed = false;
			for (com.o7flip.model.OptimizeResult.Allocation a : s.slots)
			{
				if (a != null && a.itemId == itemId && a.pendingOfflineReconcile)
				{
					a.pendingOfflineReconcile = false;
					changed = true;
				}
			}
			if (changed)
			{
				final com.o7flip.model.OptimizerSession snap = s;
				SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
			}
		});
	}

	private void seedActiveSessionFrom(com.o7flip.model.OptimizeResult result, long capital, int slots,
	                                   String risk, int maxFillHours, Boolean members, Double minProfitPct)
	{
		if (result == null || result.allocations == null) return;
		com.o7flip.model.OptimizerSession prev = activeSession;
		if (prev != null && prev.slots != null)
		{
			java.util.Map<Integer, com.o7flip.model.OptimizeResult.Allocation> prevByItem = new java.util.HashMap<>();
			for (com.o7flip.model.OptimizeResult.Allocation p : prev.slots)
			{
				if (p != null && p.itemId > 0) prevByItem.putIfAbsent(p.itemId, p);
			}
			for (com.o7flip.model.OptimizeResult.Allocation next : result.allocations)
			{
				if (next == null) continue;
				com.o7flip.model.OptimizeResult.Allocation old = prevByItem.get(next.itemId);
				if (old == null || (old.buys.isEmpty() && old.sells.isEmpty())) continue;
				next.buys  = old.buys;
				next.sells = old.sells;
				if (old.offerInstanceId != null) next.offerInstanceId = old.offerInstanceId;
				next.partial                 = old.partial;
				next.sellListed              = old.sellListed;
				next.appliedOverrideRev      = Math.max(old.appliedOverrideRev, next.overrideRev);
				next.pendingOfflineReconcile = old.pendingOfflineReconcile;
				next.state = com.o7flip.model.SlotState.derive(next.qty, next.buys, next.sells);
			}
		}
		com.o7flip.model.OptimizerSession s = new com.o7flip.model.OptimizerSession();
		s.inputs.capital      = capital;
		s.inputs.slots        = slots;
		s.inputs.risk         = risk;
		s.inputs.maxFillHours = maxFillHours;
		s.inputs.members      = members;
		s.inputs.minProfitPct = minProfitPct;
		s.slots               = new java.util.ArrayList<>(result.allocations);
		String prevGen = prev != null ? prev.generatedAt : null;
		s.generatedAt         = nextGeneratedAt(prevGen, result.updatedAt);
		activeSession         = s;
	}

	private void replaceSlotInActiveSession(int idx, com.o7flip.model.OptimizeResult.Allocation next,
		String newGeneratedAt)
	{
		clientThread.invoke(() ->
		{
			com.o7flip.model.OptimizerSession s = activeSession;
			if (s == null || s.slots == null || idx < 0 || idx >= s.slots.size()) return;
			s.slots.set(idx, next);
			s.generatedAt = nextGeneratedAt(s.generatedAt, newGeneratedAt);
		});
	}

	private void scheduleSessionPost()
	{
		if (executor == null || executor.isShutdown()) return;
		if (pendingSessionPost != null) pendingSessionPost.cancel(false);
		pendingSessionPost = executor.schedule(this::doPostActiveSession,
			SESSION_POST_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	private void doPostActiveSession()
	{
		clientThread.invoke(() ->
		{
			com.o7flip.model.OptimizerSession live = activeSession;
			if (live == null) return;
			com.o7flip.model.OptimizerSession snapshot = live.copy();
			apiClient.postActiveSession(snapshot, ok -> { /* fire-and-forget */ });
		});
	}

	public void clearActivePlan()
	{
		activeSession = null;
		if (executor == null || executor.isShutdown()) return;
		executor.execute(() -> apiClient.deleteActiveSession(ok ->
		{
			SwingUtilities.invokeLater(() -> panel.onActivePlanCleared());
		}));
	}

	private void attributeTradeToActiveSlot(int itemId, int qty, long pricePer, boolean isBuy,
		long timestampMs, long offerInstanceId)
	{
		com.o7flip.model.OptimizerSession s = activeSession;
		if (s == null || s.slots == null || qty <= 0 || itemId <= 0) return;
		int slotIdx = -1;
		com.o7flip.model.OptimizeResult.Allocation slot = null;
		for (int i = 0; i < s.slots.size(); i++)
		{
			com.o7flip.model.OptimizeResult.Allocation a = s.slots.get(i);
			if (a != null && a.itemId == itemId) { slot = a; slotIdx = i; break; }
		}
		if (slot == null) return;
		if (slot.offerInstanceId == null && offerInstanceId > 0)
		{
			slot.offerInstanceId = offerInstanceId;
		}
		com.o7flip.model.SlotState prevState = slot.state;

		int countedQty = cappedFillQty(isBuy, slot, qty);
		if (countedQty <= 0) return;   // leg already at its cap — counted in My Trades only

		String tradedAt = java.time.Instant.ofEpochMilli(timestampMs).toString();
		foldFill(isBuy ? slot.buys : slot.sells, countedQty, pricePer, tradedAt, !isBuy);
		slot.state = com.o7flip.model.SlotState.derive(slot.qty, slot.buys, slot.sells);
		scheduleSessionPost();

		if (prevState != com.o7flip.model.SlotState.FILLED
			&& slot.state == com.o7flip.model.SlotState.FILLED
			&& slot.sellPrice > 0)
		{
			armSellAutoFill(slot.itemId, slot.sellPrice, slot.name);
		}

		if (prevState != com.o7flip.model.SlotState.CLOSED
			&& slot.state == com.o7flip.model.SlotState.CLOSED)
		{
			slot.sellListed = false;   // §10 — the listing resolved; flag is spent
			appendCompletedPosition(slot);
		}

		final com.o7flip.model.OptimizerSession snap = s;
		SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
	}

	private void armSellAutoFill(int itemId, long price, String name)
	{
		log.debug("[07Flip] Plan slot armed for sell auto-fill: {} ({}) at {}", name, itemId, price);
		overlayQueueItemId    = itemId;
		overlayQueuePrice     = price;
		overlayQueueIsBuy     = false;
		overlayQueueExpiresAt = System.currentTimeMillis() + OVERLAY_QUEUE_TTL_MS;
		pendingGeSellItemId   = itemId;
		pendingGeSellPrice    = price;
		pendingGeSellName     = name;
	}

	private boolean sweepSellListedFromOffers(com.o7flip.model.OptimizerSession s)
	{
		if (s == null || s.slots == null) return false;
		if (client.getGameState() != GameState.LOGGED_IN) return false;
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null) return false;
		java.util.Set<Integer> liveSells = new java.util.HashSet<>();
		for (GrandExchangeOffer o : offers)
		{
			if (o != null && o.getState() == GrandExchangeOfferState.SELLING)
			{
				liveSells.add(o.getItemId());
			}
		}
		boolean changed = false;
		for (com.o7flip.model.OptimizeResult.Allocation a : s.slots)
		{
			if (a == null || a.itemId <= 0) continue;
			boolean live = liveSells.contains(a.itemId);
			if (live && !a.sellListed && a.state != com.o7flip.model.SlotState.CLOSED)
			{
				a.sellListed = true;
				changed = true;
			}
			else if (!live && a.sellListed)
			{
				a.sellListed = false;
				changed = true;
			}
		}
		return changed;
	}

	private void markPlanSellListed(int itemId)
	{
		com.o7flip.model.OptimizerSession s = activeSession;
		if (s == null || s.slots == null || itemId <= 0) return;
		boolean changed = false;
		for (com.o7flip.model.OptimizeResult.Allocation a : s.slots)
		{
			if (a == null || a.itemId != itemId) continue;
			if (a.sellListed || a.state == com.o7flip.model.SlotState.CLOSED) continue;
			a.sellListed = true;
			changed = true;
		}
		if (changed)
		{
			scheduleSessionPost();
			final com.o7flip.model.OptimizerSession snap = s;
			SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
		}
	}

	private void clearPlanSellListedIfNoLiveSell(int itemId)
	{
		com.o7flip.model.OptimizerSession s = activeSession;
		if (s == null || s.slots == null || itemId <= 0) return;
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers != null)
		{
			for (GrandExchangeOffer o : offers)
			{
				if (o != null && o.getItemId() == itemId
					&& o.getState() == GrandExchangeOfferState.SELLING)
				{
					return;   // still listed elsewhere
				}
			}
		}
		boolean changed = false;
		for (com.o7flip.model.OptimizeResult.Allocation a : s.slots)
		{
			if (a == null || a.itemId != itemId || !a.sellListed) continue;
			a.sellListed = false;
			changed = true;
		}
		if (changed)
		{
			scheduleSessionPost();
			final com.o7flip.model.OptimizerSession snap = s;
			SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
		}
	}

	private boolean retroAttributeFills(com.o7flip.model.OptimizerSession s)
	{
		if (s == null || s.slots == null) return false;
		java.time.Instant gen = parseInstantOrNull(s.generatedAt);
		if (gen == null) return false;   // no anchor — can't safely scope history
		long genMs = gen.toEpochMilli();
		java.util.List<TradeRecord> history = tradeHistory;
		if (history == null || history.isEmpty()) return false;

		boolean anyChange = false;
		for (com.o7flip.model.OptimizeResult.Allocation slot : s.slots)
		{
			if (slot == null || slot.itemId <= 0 || slot.qty <= 0) continue;
			if (!slot.buys.isEmpty() || !slot.sells.isEmpty()) continue;
			boolean slotChanged = false;
			for (TradeRecord t : history)   // chronological append order
			{
				if (t == null || t.itemId != slot.itemId || t.quantity <= 0) continue;
				if (t.timestamp < genMs) continue;
				int counted = cappedFillQty(t.isBuy, slot, t.quantity);
				if (counted <= 0) continue;
				String tradedAt = java.time.Instant.ofEpochMilli(t.timestamp).toString();
				foldFill(t.isBuy ? slot.buys : slot.sells, counted, t.priceEach, tradedAt, !t.isBuy);
				if (slot.offerInstanceId == null && t.offerInstanceId != null && t.offerInstanceId > 0)
				{
					slot.offerInstanceId = t.offerInstanceId;
				}
				slotChanged = true;
			}
			if (slotChanged)
			{
				slot.state = com.o7flip.model.SlotState.derive(slot.qty, slot.buys, slot.sells);
				anyChange = true;
				log.debug("[07Flip] Retro-attributed trade history to plan slot {} ({})",
					slot.name, slot.itemId);
			}
		}
		return anyChange;
	}

	static int cappedFillQty(boolean isBuy, com.o7flip.model.OptimizeResult.Allocation slot, int qty)
	{
		if (slot == null) return 0;
		int capacity = isBuy
			? slot.qty - sumQty(slot.buys)              // headroom to the plan target
			: sumQty(slot.buys) - sumQty(slot.sells);   // can't sell beyond counted-bought
		return Math.min(qty, Math.max(0, capacity));
	}

	private static void foldFill(java.util.List<com.o7flip.model.SlotFill> leg,
		int qty, long pricePer, String tradedAt, boolean preferLatestTime)
	{
		if (leg == null || qty <= 0) return;
		com.o7flip.model.SlotFill entry;
		if (leg.isEmpty())
		{
			entry = new com.o7flip.model.SlotFill();
			entry.qty       = 0;
			entry.priceEach = pricePer;
			entry.tradedAt  = tradedAt;
			leg.add(entry);
		}
		else
		{
			entry = leg.get(0);
			while (leg.size() > 1)
			{
				com.o7flip.model.SlotFill extra = leg.remove(1);
				if (extra == null) continue;
				long gp = (long) entry.qty * entry.priceEach + (long) extra.qty * extra.priceEach;
				entry.qty += extra.qty;
				if (entry.qty > 0) entry.priceEach = gp / entry.qty;
			}
		}
		long newGp = (long) entry.qty * entry.priceEach + (long) qty * pricePer;
		entry.qty += qty;
		if (entry.qty > 0) entry.priceEach = newGp / entry.qty;
		if (entry.tradedAt == null || preferLatestTime) entry.tradedAt = tradedAt;
	}

	private static long sumGp(java.util.List<com.o7flip.model.SlotFill> fills)
	{
		if (fills == null) return 0L;
		long total = 0L;
		for (com.o7flip.model.SlotFill f : fills)
		{
			if (f != null) total += (long) f.qty * f.priceEach;
		}
		return total;
	}

	private static int sumQty(java.util.List<com.o7flip.model.SlotFill> fills)
	{
		if (fills == null) return 0;
		int total = 0;
		for (com.o7flip.model.SlotFill f : fills)
		{
			if (f != null) total += f.qty;
		}
		return total;
	}

	public void markPartial(int slotIdx)
	{
		com.o7flip.model.OptimizerSession s = activeSession;
		if (s == null || s.slots == null || slotIdx < 0 || slotIdx >= s.slots.size()) return;
		com.o7flip.model.OptimizeResult.Allocation slot = s.slots.get(slotIdx);
		if (slot == null) return;
		int bought = sumQty(slot.buys);
		if (bought <= 0 || slot.partial) return;   // nothing bought yet, or already partial

		slot.reservedGp     = slot.gpAllocated;                 // remember original budget
		slot.qty            = bought;                           // cap target → advances state
		slot.gpAllocated    = sumGp(slot.buys);                 // actual spend
		slot.expectedProfit = (long) bought * slot.profitPerUnit;
		slot.partial        = true;
		slot.state          = com.o7flip.model.SlotState.derive(slot.qty, slot.buys, slot.sells);

		if (slot.state == com.o7flip.model.SlotState.FILLED && slot.sellPrice > 0)
		{
			armSellAutoFill(slot.itemId, slot.sellPrice, slot.name);
		}

		s.generatedAt = nextGeneratedAt(s.generatedAt, null);

		scheduleSessionPost();
		final com.o7flip.model.OptimizerSession snap = s;
		SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
	}



	private void appendCompletedPosition(com.o7flip.model.OptimizeResult.Allocation closed)
	{
		if (closed == null) return;
		int soldQty = sumQty(closed.sells);
		if (soldQty <= 0) return;

		com.o7flip.model.CompletedPosition cp = new com.o7flip.model.CompletedPosition();
		cp.itemId   = closed.itemId;
		cp.name     = closed.name;
		cp.qty      = soldQty;
		cp.buyGp    = sumGp(closed.buys);
		cp.sellGp   = sumGp(closed.sells);
		long tax    = com.o7flip.util.ProfitCalculator.geTaxFor(closed.itemId, cp.sellGp, soldQty);
		cp.profit   = cp.sellGp - tax - cp.buyGp;
		cp.partial  = closed.partial;
		cp.closedAt = lastTradeIso(closed.sells);
		cp.fillHours = computeFillHours(closed);

		boolean added;
		synchronized (completedPositions)
		{
			String key = cp.dedupeKey();
			added = true;
			for (com.o7flip.model.CompletedPosition existing : completedPositions)
			{
				if (existing != null && existing.dedupeKey().equals(key)) { added = false; break; }
			}
			if (added) completedPositions.add(0, cp);   // newest-first
		}
		if (added && panel != null) SwingUtilities.invokeLater(panel::onCompletedPositionsChanged);

		if (executor == null || executor.isShutdown()) return;
		executor.execute(() -> apiClient.postCompletedPosition(cp, list ->
		{
			if (list != null) setCompletedPositions(list);
		}));
	}

	private static Double computeFillHours(com.o7flip.model.OptimizeResult.Allocation a)
	{
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		int seen = 0;
		for (java.util.List<com.o7flip.model.SlotFill> list :
			java.util.Arrays.asList(a.buys, a.sells))
		{
			if (list == null) continue;
			for (com.o7flip.model.SlotFill f : list)
			{
				if (f == null || f.tradedAt == null) continue;
				try
				{
					long t = java.time.Instant.parse(f.tradedAt).toEpochMilli();
					min = Math.min(min, t);
					max = Math.max(max, t);
					seen++;
				}
				catch (Exception ignored) {}
			}
		}
		if (seen < 2 || max <= min) return null;
		return (max - min) / 3_600_000.0;
	}

	private static String lastTradeIso(java.util.List<com.o7flip.model.SlotFill> sells)
	{
		String last = null;
		long lastMs = Long.MIN_VALUE;
		if (sells != null)
		{
			for (com.o7flip.model.SlotFill f : sells)
			{
				if (f == null || f.tradedAt == null) continue;
				try
				{
					long t = java.time.Instant.parse(f.tradedAt).toEpochMilli();
					if (t > lastMs) { lastMs = t; last = f.tradedAt; }
				}
				catch (Exception ignored) {}
			}
		}
		return last != null ? last : java.time.Instant.now().toString();
	}

	public java.util.List<com.o7flip.model.CompletedPosition> getCompletedPositions()
	{
		synchronized (completedPositions)
		{
			return new java.util.ArrayList<>(completedPositions);
		}
	}

	public long getCompletedProfitTotal()
	{
		long total = 0L;
		synchronized (completedPositions)
		{
			for (com.o7flip.model.CompletedPosition cp : completedPositions)
			{
				if (cp != null) total += cp.profit;
			}
		}
		return total;
	}

	private void setCompletedPositions(java.util.List<com.o7flip.model.CompletedPosition> list)
	{
		if (list == null) return;
		synchronized (completedPositions)
		{
			completedPositions.clear();
			for (com.o7flip.model.CompletedPosition cp : list)
			{
				if (cp != null) completedPositions.add(cp);
			}
		}
		if (panel != null) SwingUtilities.invokeLater(panel::onCompletedPositionsChanged);
	}

	public void refreshCompletedPositions()
	{
		if (executor == null || executor.isShutdown()) return;
		executor.execute(() -> apiClient.fetchCompletedPositions(list ->
		{
			if (list != null) setCompletedPositions(list);
		}));
	}

	public boolean hasApiKeyPublic()
	{
		return hasApiKey();
	}

	private boolean hasApiKey()
	{
		String k = config.apiKey();
		return k != null && !k.trim().isEmpty();
	}

	private void fetchDumpsAtPage(String sort, int page)
	{
		java.util.function.Consumer<DumpItem.Response> cb = resp ->
		{
			if (resp != null && resp.items != null && !resp.items.isEmpty())
			{
				saveCache("dumps", resp);
			}
			lastDumps = resp.items;
			rebuildTrackedItems();
			SwingUtilities.invokeLater(() -> panel.updateDumps(resp, page));
		};
		int     minScore   = panel.getDumpsMinScore();
		boolean activeOnly = panel.getDumpsActiveOnly();
		String  tier       = panel.getDumpsTier();
		if (panel.dumpsUsesBotEndpoint())
		{
			apiClient.fetchBotDumps(sort,
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				minScore, activeOnly, tier,
				page, cb);
		}
		else
		{
			apiClient.fetchDumps(sort,
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				minScore, activeOnly, tier,
				page, cb);
		}
	}



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
		executor.execute(() -> fetchDumpsAtPage(sort, 0));
	}

	void onFlipsFilterChanged()
	{
		executor.execute(() -> fetchFlipsAtPage(0));
	}

	void onDumpsFilterChanged()
	{
		executor.execute(() -> fetchDumpsAtPage(panel.getDumpsSortKey(), 0));
	}

	void onPresetChanged()
	{
		executor.execute(() -> fetchFlipsAtPage(0));
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
