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
import com.o7flip.model.Models.DumpItem;
import com.o7flip.model.Models.FlipItem;
import com.o7flip.model.Models.TrackedItemData;
import com.o7flip.model.Models.TradeRecord;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
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
	name = "07flip",
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

	public long flipsAsOfMs()
	{
		return apiClient != null ? apiClient.flipsAsOfMs : 0L;
	}

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
	private GeQuickLookOverlay geQuickLookOverlay;

	@Inject
	private GeChatPriceOverlay geChatPriceOverlay;

	@Inject
	private net.runelite.client.input.MouseManager mouseManager;

	@Inject
	private Gson gson;

	@Inject
	private net.runelite.client.config.ConfigManager configManager;

	public O7FlipPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> refreshTask;
	private ScheduledFuture<?> authRefreshTask;
	private ScheduledFuture<?> partialFlushTask;
	private ScheduledFuture<?> tradeRetryTask;

	volatile int    pendingGeBuyItemId = -1;
	volatile long   pendingGeBuyPrice  = -1;
	volatile String pendingGeBuyName   = null;

	volatile int    pendingGeSellItemId = -1;
	volatile long   pendingGeSellPrice  = -1;
	volatile String pendingGeSellName   = null;

	volatile long   pendingGeSetPrice  = -1;
	volatile int    pendingGeSetItemId = -1;
	volatile long   pendingGeInputPrice = -1;
	private boolean gePriceInputFilled  = false;

	public volatile long confirmHighlightUntilMs = 0L;
	private long confirmCheckKey = Long.MIN_VALUE;
	private long confirmCheckAtMs = 0L;
	private Boolean confirmCheckResult;

	private int sellSetupArmedItemId = -1;

	private int buySetupArmedItemId = -1;

	private int autoOpenInsightsItemId = -1;

	private boolean geAutoOpenedTab = false;

	private volatile int insightsRequestSeq = 0;

	private static final long OVERLAY_QUEUE_TTL_MS = 10L * 60L * 1000L;

	private volatile int    overlayQueueItemId   = -1;
	private volatile long   overlayQueuePrice    = -1;
	private volatile boolean overlayQueueIsBuy   = false;
	private volatile long   overlayQueueExpiresAt = 0L;

	List<FlipItem>  lastFlips  = Collections.emptyList();
	private List<DumpItem>  lastDumps  = Collections.emptyList();

	public volatile Map<Integer, TrackedItemData> trackedItems = Collections.emptyMap();

	private static final long CASH_BUCKET = 100_000L;

	private final java.util.concurrent.ConcurrentHashMap<Integer, com.o7flip.model.Models.RecommendedPrices> recPriceCache
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> recPriceFetchedAt
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Integer> recPriceInFlight
		= java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static final long REC_PRICE_TTL_MS = 60_000L;

	private static final long FLIP_AGE_TTL_MS = 300_000L;

	private final java.util.concurrent.ConcurrentHashMap<Integer, long[]> flipAges
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Integer> flipAgesInFlight
		= java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final long FETCH_FAIL_RETRY_MS = 8_000L;

	private final java.util.concurrent.ConcurrentHashMap<Integer, com.o7flip.model.Models.ItemInsights> overlayInsightsCache
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> overlayInsightsFetchedAt
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Integer> overlayInsightsInFlight
		= java.util.concurrent.ConcurrentHashMap.newKeySet();

	private final java.util.concurrent.ConcurrentHashMap<Integer, com.o7flip.model.Models.RepriceResult> repriceCache
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> repriceFetchedAt
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Integer> repriceInFlight
		= java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final long REPRICE_TTL_MS = 30_000L;

	private static final long FREEZE_TTL_MS = 12L * 60L * 60L * 1000L;

	public volatile Map<Integer, com.o7flip.model.Models.ActiveOfferSnapshot> activeOffers = Collections.emptyMap();

	private final Map<Integer, GrandExchangeOfferState> prevSlotStates = new HashMap<>();

	private final Map<Integer, long[]> slotRecordedFills = new java.util.concurrent.ConcurrentHashMap<>();

	private final Map<Integer, long[]> slotListedAt = new java.util.concurrent.ConcurrentHashMap<>();

	private final Map<Integer, long[]> slotFillClock = new java.util.concurrent.ConcurrentHashMap<>();

	private final Set<Long> tradePostsInFlight = new HashSet<>();

	private final Map<Integer, Long> slotPartialPostedAt = new java.util.concurrent.ConcurrentHashMap<>();

	private final Set<Long> deferredTerminalPosts = new HashSet<>();

	private static final long PARTIAL_POST_INTERVAL_MS = 60_000L;
	private static final long TRADE_RETRY_INTERVAL_MIN = 5L;
	private static final long UNAUTHORIZED_NOTICE_INTERVAL_MS = 15L * 60_000L;
	private volatile long lastUnauthorizedNoticeAt = 0L;

	public volatile List<TradeRecord> tradeHistory = Collections.emptyList();

	public volatile com.o7flip.util.BondLedger bondLedger = com.o7flip.util.BondLedger.EMPTY;

	public volatile com.o7flip.model.Models.TrackerStats trackerStats = null;

	public volatile com.o7flip.model.Models.ItemInsights currentInsights = null;

	public volatile String selectedChartPeriod = null;

	private final java.util.concurrent.ConcurrentHashMap<Integer, FrozenSell> frozenSellByItemId
		= new java.util.concurrent.ConcurrentHashMap<>();

	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> frozenBuyByItemId
		= new java.util.concurrent.ConcurrentHashMap<>();

	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> frozenProfitByItemId
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
	private static final String SLOT_LISTED_KEY = "slotListedAt";
	private static final String SLOT_FILL_CLOCK_KEY = "slotFillClock";
	private static final String AUTH_CACHE_KEY = "authStatusCache";
	private static final String BOND_LEDGER_SPEND_KEY = "bondLedgerSpend";
	private static final String BOND_LEDGER_COUNT_KEY = "bondLedgerCount";
	private static final String BOND_LEDGER_MIGRATED_KEY = "bondLedgerMigrated";
	private static final String MEMBERSHIP_HIDDEN_KEY = "membershipCostHidden";
	private static final String SHARE_TRADE_DATA_KEY = "shareTradeData";
	private static final String SYNC_PROMPT_DISMISSED_KEY = "syncPromptDismissed";
	private static final String TRADE_HISTORY_HEALED_KEY = "tradeHistoryHealed";
	private static final String SCRUB_VERSION_KEY = "tradeHistoryScrubVersion";
	private static final String SCRUB_VERSION_CURRENT = "3";

	public volatile Set<Integer> blocklist = Collections.emptySet();

	public void queueGeBuy(int itemId, long price, String name)
	{
		log.debug("[07Flip] GE buy queued: {} ({}) @ {}", name, itemId, price);
		setOverlayQueue(itemId, price, true);

		final boolean premiumAtQueue = panel != null && panel.isPremium();
		if (panel == null || !panel.isKnownFree())
		{
			freezeAtPlacement(itemId, price);
		}

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
		com.o7flip.model.Models.ItemInsights cached = currentInsights;
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

	private static boolean hasFreezableRec(com.o7flip.model.Models.ItemInsights ins, int itemId)
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

	private void freezeAtPlacement(int itemId, long paidEach)
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		if (paidEach > 0)
		{
			frozenBuyByItemId.put(itemId, paidEach);
		}
		com.o7flip.model.Models.ItemInsights cached = recInsightsFor(itemId);
		if (cached != null && cached.itemId == itemId && cached.current != null
			&& cached.current.recSell != null && cached.current.recSell > 0)
		{
			commitPlacementFreeze(itemId, paidEach, cached.current.recSell);
			return;
		}
		executor.execute(() -> apiClient.fetchItemInsights(itemId, ins ->
		{
			if (ins != null && ins.itemId == itemId && ins.current != null
				&& ins.current.recSell != null && ins.current.recSell > 0)
			{
				commitPlacementFreeze(itemId, paidEach, ins.current.recSell);
			}
		}));
	}

	private void commitPlacementFreeze(int itemId, long paidEach, long recSell)
	{
		frozenSellByItemId.put(itemId, new FrozenSell(recSell, System.currentTimeMillis()));
		Long buyForPost = paidEach > 0 ? paidEach : openPositionAvgCost(itemId);
		if (buyForPost == null || buyForPost <= 0)
		{
			return;
		}
		frozenBuyByItemId.put(itemId, buyForPost);
		apiClient.postFreeze(itemId, buyForPost, recSell, null);
	}

	private void restoreFreezeFromServer(int itemId, com.o7flip.model.Models.ItemInsights ins)
	{
		com.o7flip.model.Models.ItemInsights.Frozen f = ins != null ? ins.frozen : null;
		if (f == null)
		{
			return;
		}
		if (f.expired)
		{
			dropFreeze(itemId);
			return;
		}
		if (f.sell <= 0 || frozenSellByItemId.containsKey(itemId))
		{
			return;
		}
		frozenSellByItemId.put(itemId, new FrozenSell(f.sell, parseIsoMillis(f.frozenAt)));
		if (f.buy > 0)
		{
			frozenBuyByItemId.put(itemId, f.buy);
		}
		if (f.profit != 0)
		{
			frozenProfitByItemId.put(itemId, f.profit);
		}
	}

	private void restoreFreezesFromServer()
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(() -> apiClient.fetchFreezes(rows ->
		{
			for (com.o7flip.model.Models.FreezeRow r : rows)
			{
				if (r == null || r.itemId <= 0 || r.frozenSell <= 0 || frozenSellByItemId.containsKey(r.itemId))
				{
					continue;
				}
				frozenSellByItemId.put(r.itemId, new FrozenSell(r.frozenSell, parseIsoMillis(r.frozenAt)));
				if (r.frozenBuy > 0)
				{
					frozenBuyByItemId.put(r.itemId, r.frozenBuy);
				}
				if (r.frozenProfit != 0)
				{
					frozenProfitByItemId.put(r.itemId, r.frozenProfit);
				}
			}
		}));
	}

	public Long getFrozenProfit(int itemId)
	{
		return frozenProfitByItemId.get(itemId);
	}

	private static long parseIsoMillis(String iso)
	{
		if (iso != null)
		{
			try
			{
				return java.time.Instant.parse(iso).toEpochMilli();
			}
			catch (Exception ignored)
			{
			}
		}
		return System.currentTimeMillis();
	}

	public Long getFrozenBuy(int itemId)
	{
		Long paid = openPositionAvgCost(itemId);
		if (paid != null)
		{
			return paid;
		}
		return frozenBuyByItemId.get(itemId);
	}

	private Long openPositionAvgCost(int itemId)
	{
		com.o7flip.util.ProfitCalculator.Result r = com.o7flip.util.ProfitCalculator.compute(tradeHistory);
		com.o7flip.util.ProfitCalculator.OpenPosition pos = r.openPositions.get(itemId);
		if (pos == null || pos.remainingQty <= 0 || pos.remainingCostBasis <= 0)
		{
			return null;
		}
		return Math.round(pos.remainingCostBasis / (double) pos.remainingQty);
	}

	public boolean isPriceLocked(int itemId)
	{
		return panel != null && panel.isPremium() && getFrozenSell(itemId) != null;
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
		frozenProfitByItemId.remove(itemId);
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
			it.remove();
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
		if (System.currentTimeMillis() - f.frozenAtMillis > FREEZE_TTL_MS)
		{
			dropFreeze(itemId);
			return null;
		}
		return f.price;
	}

	private void dropFreeze(int itemId)
	{
		frozenSellByItemId.remove(itemId);
		frozenBuyByItemId.remove(itemId);
		frozenProfitByItemId.remove(itemId);
	}

	public long ladderPrice(int itemId, boolean sell, O7FlipConfig.GePriceDefault which)
	{
		com.o7flip.model.Models.ItemInsights ins = getOverlayInsights(itemId);
		if (ins == null || ins.current == null)
		{
			return -1L;
		}
		boolean premium = panel == null || !panel.isKnownFree();
		long live = sell ? ins.current.sellPrice : ins.current.buyPrice;
		Long rec = premium ? (sell ? ins.current.recSell : ins.current.recBuy) : null;
		long base = (rec != null && rec > 0) ? rec : live;
		if (base <= 0)
		{
			return -1L;
		}
		long step = Math.max(1, Math.round(base * 0.005));
		switch (which)
		{
			case QUICK:   return sell ? base - step : base + step;
			case PATIENT: return sell ? base + step : base - step;
			case MARKET:  return live > 0 ? live : base;
			default:      return base;
		}
	}

	long computeAutoSellPrice(int itemId)
	{
		long candidate = ladderPrice(itemId, true, config.geDefaultPrice());
		if (candidate <= 0)
		{
			return -1L;
		}
		if ((panel == null || !panel.isKnownFree()) && config.geDefaultPrice() != O7FlipConfig.GePriceDefault.MARKET)
		{
			Long frozen = getFrozenSell(itemId);
			if (frozen != null && frozen > candidate)
			{
				candidate = frozen;
			}
		}
		long breakEven = breakEvenSellPrice(itemId);
		if (breakEven > 0)
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

	private com.o7flip.model.Models.ItemInsights recInsightsFor(int itemId)
	{
		com.o7flip.model.Models.ItemInsights cur = currentInsights;
		if (cur != null && cur.itemId == itemId && cur.current != null)
		{
			return cur;
		}
		return getOverlayInsights(itemId);
	}

	long computeAutoBuyPrice(int itemId)
	{
		return ladderPrice(itemId, false, config.geDefaultPrice());
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
		frozenProfitByItemId.remove(itemId);
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

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(O7FlipPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("07flip")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(geOverlay);
		overlayManager.add(priceOverlay);
		overlayManager.add(gpDropOverlay);
		overlayManager.add(inventoryTooltipOverlay);
		overlayManager.add(geQuickLookOverlay);
		overlayManager.add(geChatPriceOverlay);
		mouseManager.registerMouseListener(geChatPriceOverlay);

		loadTradeHistory();
		loadBondLedger();
		loadBlocklist();
		loadSlotRecordedFills();
		loadSlotListedAt();
		loadSlotFillClock();
		restoreFreezesFromServer();
		applyCachedAuthStatus();

		apiClient.setOnFavouritesUnauthorized(this::notifyApiKeyRejected);

		hydrateCachedTabs();

		executor = Executors.newSingleThreadScheduledExecutor();
		fetchAuthStatus();
		authRefreshTask = executor.scheduleAtFixedRate(
			this::fetchAuthStatus, 15, 15, TimeUnit.MINUTES);
		partialFlushTask = executor.scheduleAtFixedRate(
			() -> clientThread.invoke(this::flushPendingPartialFills), 30, 30, TimeUnit.SECONDS);
		tradeRetryTask = executor.scheduleAtFixedRate(
			this::doBulkSyncToServer, TRADE_RETRY_INTERVAL_MIN, TRADE_RETRY_INTERVAL_MIN, TimeUnit.MINUTES);
		executor.execute(() -> fetchAll(true));
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
		if (partialFlushTask != null)
		{
			partialFlushTask.cancel(true);
		}
		if (tradeRetryTask != null)
		{
			tradeRetryTask.cancel(true);
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
		overlayManager.remove(geQuickLookOverlay);
		overlayManager.remove(geChatPriceOverlay);
		mouseManager.unregisterMouseListener(geChatPriceOverlay);
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

		if (config.showGeSlotTimer())
		{
			leftAlignSlotLabels();
		}

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

		int itemId = -1;
		if (setupOpen)
		{
			if (!isSellSetupVisible(setup) && widgetTreeHasText(setup, "choose an item"))
			{
				return;
			}
			itemId = resolveItemIdFromSetupWidget();
		}
		if (itemId <= 0 && detailsOpen)
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
		if (w == null || w.isHidden())
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
		if (event.getScriptId() != ScriptID.GE_OFFERS_SETUP_BUILD)
		{
			return;
		}
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
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden() || !isGePriceEntryOpen())
		{
			gePriceInputFilled = false;
			return;
		}
		if (gePriceInputFilled)
		{
			return;
		}
		long price = pendingGeInputPrice;
		if (price == -1)
		{
			int itemId = resolveItemIdFromSetupWidget();
			if (itemId <= 0)
			{
				return;
			}
			price = isGeSellSetup() ? computeAutoSellPrice(itemId) : computeAutoBuyPrice(itemId);
		}
		if (price <= 0)
		{
			return;
		}
		pendingGeInputPrice = -1;
		autoFillPriceInput(price);
	}

	private void autoFillPriceInput(long price)
	{
		if (isGeQuantityEntryOpen())
		{
			pendingGeInputPrice = price;
			return;
		}
		gePriceInputFilled = true;
		if (!config.autoFillGePrice())
		{
			return;
		}
		setPriceInput(price);
	}

	private void setPriceInput(long price)
	{
		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (input == null)
		{
			return;
		}
		input.setText(price + "*");
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(price));
		confirmHighlightUntilMs = System.currentTimeMillis() + 3000L;
	}

	public void fillPriceInputForced(long price)
	{
		if (price <= 0)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			gePriceInputFilled = true;
			setPriceInput(price);
		});
	}

	public void clearPriceInput()
	{
		clientThread.invokeLater(() ->
		{
			Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
			if (input == null || input.isHidden())
			{
				return;
			}
			gePriceInputFilled = true;
			input.setText("*");
			client.setVarcStrValue(VarClientID.MESLAYERINPUT, "");
		});
	}

	public int currentGeSetupItemId()
	{
		int searched = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (searched > 0)
		{
			return searched;
		}
		return resolveItemIdFromSetupWidget();
	}

	public boolean isGeSellSetup()
	{
		return isSellSetupVisible(client.getWidget(InterfaceID.GeOffers.SETUP));
	}

	public Boolean geOfferPriceMatchesPlan()
	{
		int itemId = currentGeSetupItemId();
		if (itemId <= 0)
		{
			return null;
		}
		boolean sell = isGeSellSetup();
		long entered = client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE);
		long key = ((long) itemId << 33) | (sell ? 1L << 32 : 0L) | entered;
		long now = System.currentTimeMillis();
		if (key != confirmCheckKey || now - confirmCheckAtMs > 1000L)
		{
			confirmCheckKey = key;
			confirmCheckAtMs = now;
			confirmCheckResult = priceMatchesPlan(itemId, sell, entered);
		}
		return confirmCheckResult;
	}

	private Boolean priceMatchesPlan(int itemId, boolean sell, long entered)
	{
		if (ladderPrice(itemId, sell, O7FlipConfig.GePriceDefault.SEVEN_FLIP) <= 0)
		{
			return null;
		}
		if (entered <= 0)
		{
			return false;
		}
		for (O7FlipConfig.GePriceDefault which : O7FlipConfig.GePriceDefault.values())
		{
			if (entered == ladderPrice(itemId, sell, which))
			{
				return true;
			}
		}
		return entered == (sell ? computeAutoSellPrice(itemId) : computeAutoBuyPrice(itemId));
	}

	public boolean isGePriceEntryOpen()
	{
		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (input == null || input.isHidden())
		{
			return false;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			return false;
		}
		return chatboxPromptHas(input, "price");
	}

	public boolean isGeQuantityEntryOpen()
	{
		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (input == null || input.isHidden())
		{
			return false;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			return false;
		}
		return chatboxPromptHas(input, "how many");
	}

	private static boolean chatboxPromptHas(Widget input, String needle)
	{
		Widget p = input.getParent();
		for (int i = 0; i < 2 && p != null; i++)
		{
			if (widgetTreeHasText(p, needle))
			{
				return true;
			}
			p = p.getParent();
		}
		return false;
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
			lastUnauthorizedNoticeAt = 0L;
			executor.execute(this::fetchAuthStatus);
			syncMissedTrades();
			SwingUtilities.invokeLater(panel::refreshSyncNotice);
			return;
		}
		if (SHARE_TRADE_DATA_KEY.equals(key))
		{
			if (config.shareTradeData())
			{
				syncMissedTrades();
			}
			else
			{
				trackerStats = null;
			}
			final List<TradeRecord> snap = tradeHistory;
			SwingUtilities.invokeLater(() ->
			{
				panel.updateMyFlips(snap);
				panel.refreshSyncNotice();
			});
			return;
		}
		if (key.startsWith("itemTab"))
		{
			SwingUtilities.invokeLater(() -> panel.refreshInsightsSections());
			return;
		}
		if ("narrowByPendingOffers".equals(key) || "capitalFilterDisabled".equals(key))
		{
			lastPendingFilterCeiling = Long.MIN_VALUE;
			onCapitalChanged();
			SwingUtilities.invokeLater(panel::onCapitalAutoAdjusted);
			return;
		}
		if ("activeFillCounter".equals(key) || "activeLastFillAge".equals(key))
		{
			final List<TradeRecord> snap = tradeHistory;
			SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));
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
			case "showItem":
			case "showDips":
			case "showDecant":
			case "showFavourites":
			case "showMyFlips":
				return true;
			default:
				return false;
		}
	}

	void fetchAuthStatus()
	{
		fetchAuthStatusInternal(0);
	}

	private void fetchAuthStatusInternal(int attempt)
	{
		String key = config.apiKey();
		if (key == null || key.trim().isEmpty())
		{
			configManager.unsetConfiguration("o7flip", AUTH_CACHE_KEY);
			SwingUtilities.invokeLater(() -> panel.updateAuthStatus(false, false));
			return;
		}
		apiClient.fetchAuthStatus(
			status -> SwingUtilities.invokeLater(() ->
			{
				configManager.setConfiguration("o7flip", AUTH_CACHE_KEY,
					(status.authenticated ? "1" : "0") + (status.premium ? "1" : "0"));
				panel.updateAuthStatus(status.authenticated, status.premium);
				if (status.premium && activeSession == null
					&& executor != null && !executor.isShutdown())
				{
					executor.execute(this::doHydrateOptimizerSession);
				}
			}),
			() ->
			{
				if (attempt < 5 && executor != null && !executor.isShutdown())
				{
					long delay = 30L << attempt;
					executor.schedule(() -> fetchAuthStatusInternal(attempt + 1), delay, TimeUnit.SECONDS);
				}
			}
		);
	}

	private void applyCachedAuthStatus()
	{
		String key = config.apiKey();
		String cached = configManager.getConfiguration("o7flip", AUTH_CACHE_KEY);
		if (key == null || key.trim().isEmpty() || cached == null || cached.length() < 2)
		{
			return;
		}
		boolean signedIn = cached.charAt(0) == '1';
		boolean premium  = cached.charAt(1) == '1';
		SwingUtilities.invokeLater(() -> panel.updateAuthStatus(signedIn, premium));
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
		final int dumpsPage   = panel.getDumpsPage();

		apiClient.fetchBundle(
			sections,
			null,
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

		if (config.showDecant())
		{
			fetchDecantingNow();
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

		com.o7flip.model.Models.DumpItem.Response cd = loadCache("dumps", com.o7flip.model.Models.DumpItem.Response.class);
		if (cd != null && cd.items != null && !cd.items.isEmpty())
		{
			lastDumps = cd.items;
			final com.o7flip.model.Models.DumpItem.Response snap = cd;
			SwingUtilities.invokeLater(() -> panel.updateDumps(snap, 0));
		}

		List<com.o7flip.model.Models.DipItem> cdips = loadListCache("dips", com.o7flip.model.Models.DipItem.class);
		if (cdips != null && !cdips.isEmpty())
		{
			final List<com.o7flip.model.Models.DipItem> snap = cdips;
			SwingUtilities.invokeLater(() -> panel.updateDips(snap, snap.size(), 0));
		}

		List<com.o7flip.model.Models.DecantItem> cdec = loadListCache("decant", com.o7flip.model.Models.DecantItem.class);
		if (cdec != null && !cdec.isEmpty())
		{
			final List<com.o7flip.model.Models.DecantItem> snap = cdec;
			SwingUtilities.invokeLater(() -> panel.updateDecanting(snap));
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

		trackedItems = Collections.unmodifiableMap(map);
	}

	private long lastActiveOffersHash = 0L;

	private void syncActiveOffersFromClient()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}

		Map<Integer, com.o7flip.model.Models.ActiveOfferSnapshot> next = new HashMap<>();
		long hash = 0L;
		for (int slot = 0; slot < offers.length; slot++)
		{
			GrandExchangeOffer o = offers[slot];
			if (o == null || o.getState() == GrandExchangeOfferState.EMPTY)
			{
				continue;
			}
			next.put(slot, snapshot(slot, o));
			updateSlotListedAt(slot, o);
			trackFillClock(slot, o);
			if (o.getState() == GrandExchangeOfferState.BUYING
				|| o.getState() == GrandExchangeOfferState.SELLING)
			{
				recordIfNewFills(o, slot);
				getReprice(o.getItemId(), o.getState() == GrandExchangeOfferState.BUYING,
					Math.max(1, o.getTotalQuantity() - o.getQuantitySold()), o.getPrice(), offerHeldMinutes(slot));
			}
			hash = hash * 31 + slot;
			hash = hash * 31 + o.getItemId();
			hash = hash * 31 + o.getQuantitySold();
			hash = hash * 31 + o.getTotalQuantity();
			hash = hash * 31 + o.getState().ordinal();
		}

		if (slotListedAt.keySet().retainAll(next.keySet()))
		{
			saveSlotListedAt();
		}
		if (slotFillClock.keySet().retainAll(next.keySet()))
		{
			saveSlotFillClock();
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

	private void leftAlignSlotLabels()
	{
		Widget first = client.getWidget(InterfaceID.GeOffers.INDEX_0);
		if (first == null || first.isHidden())
		{
			return;
		}
		for (int i = 0; i < 8; i++)
		{
			Widget slot = client.getWidget(InterfaceID.GeOffers.INDEX_0 + i);
			if (slot == null || slot.isHidden())
			{
				continue;
			}
			Widget label = findSlotStatusLabel(slot);
			if (label == null)
			{
				continue;
			}
			if (label.getXTextAlignment() != net.runelite.api.widgets.WidgetTextAlignment.LEFT
				|| label.getXPositionMode() != net.runelite.api.widgets.WidgetPositionMode.ABSOLUTE_LEFT
				|| label.getOriginalX() != 3)
			{
				label.setXTextAlignment(net.runelite.api.widgets.WidgetTextAlignment.LEFT);
				label.setXPositionMode(net.runelite.api.widgets.WidgetPositionMode.ABSOLUTE_LEFT);
				label.setOriginalX(3);
				label.revalidate();
			}
		}
	}

	private static Widget findSlotStatusLabel(Widget w)
	{
		if (w == null)
		{
			return null;
		}
		String t = w.getText();
		if (t != null)
		{
			String lt = t.trim().toLowerCase();
			if (lt.startsWith("sell") || lt.startsWith("buy") || lt.startsWith("empty"))
			{
				return w;
			}
		}
		Widget[] dyn = w.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn)
			{
				Widget f = findSlotStatusLabel(c);
				if (f != null) return f;
			}
		}
		Widget[] stat = w.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat)
			{
				Widget f = findSlotStatusLabel(c);
				if (f != null) return f;
			}
		}
		return null;
	}

	private void updateSlotListedAt(int slot, GrandExchangeOffer o)
	{
		long[] prev = slotListedAt.get(slot);
		if (prev == null || prev[1] != o.getItemId() || prev[2] != o.getTotalQuantity())
		{
			slotListedAt.put(slot, new long[]{System.currentTimeMillis(), o.getItemId(), o.getTotalQuantity()});
			saveSlotListedAt();
		}
	}

	public long offerListedAtMs(int slot)
	{
		long[] v = slotListedAt.get(slot);
		return (v != null && v.length >= 1) ? v[0] : -1L;
	}

	private com.o7flip.model.Models.ActiveOfferSnapshot snapshot(int slot, GrandExchangeOffer offer)
	{
		String name = "Item " + offer.getItemId();
		try
		{
			name = client.getItemDefinition(offer.getItemId()).getName();
		}
		catch (Exception ignored)
		{
		}
		return new com.o7flip.model.Models.ActiveOfferSnapshot(
			slot, offer.getItemId(), name, offer.getPrice(),
			offer.getQuantitySold(), offer.getTotalQuantity(), offer.getState());
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		int slot = event.getSlot();
		GrandExchangeOfferState state = offer.getState();

		Map<Integer, com.o7flip.model.Models.ActiveOfferSnapshot> next = new HashMap<>(activeOffers);
		if (state == GrandExchangeOfferState.EMPTY)
		{
			next.remove(slot);
			if (slotFillClock.remove(slot) != null)
			{
				saveSlotFillClock();
			}
		}
		else
		{
			next.put(slot, snapshot(slot, offer));
			trackFillClock(slot, offer);
		}
		activeOffers = Collections.unmodifiableMap(next);

		refreshForPendingCapitalChange();

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
			&& prevSlotStates.get(slot) != GrandExchangeOfferState.BUYING
			&& (panel == null || !panel.isKnownFree())
			&& !frozenSellByItemId.containsKey(offer.getItemId()))
		{
			freezeAtPlacement(offer.getItemId(), offer.getPrice());
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

		if (state == GrandExchangeOfferState.BUYING)
		{
			maybePostPartialFill(slot);
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
			slotPartialPostedAt.remove(slot);
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
			slotPartialPostedAt.remove(slot);
		}

		int  deltaQty = currentQty - (int) prevQty;
		long deltaGp  = currentGp  - prevGp;
		if (deltaQty <= 0)
		{
			if (isTerminalOfferState(offer.getState()))
			{
				finaliseAndPostExistingRow(offerInstanceId);
			}
			return;
		}

		GrandExchangeOfferState state = offer.getState();
		boolean isBuy = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;
		boolean partial = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.SELLING;

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
			if (existingIdx < 0 && isTerminalOfferState(state))
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
					slotRecordedFills.put(slot,
						new long[]{existingQty, existingGp, offerInstanceId, lastPostedFor(prev, offerInstanceId)});
					saveSlotRecordedFills();
					if (isTerminalOfferState(state))
					{
						finaliseAndPostExistingRow(offerInstanceId);
					}
					return;
				}
				deltaQty = currentQty - existingQty;
				deltaGp  = currentGp  - existingGp;
				firstObservation = false;
			}
		}

		long timestamp = System.currentTimeMillis();

		recordTrade(offer, isBuy, partial, deltaQty, deltaGp, timestamp, offerInstanceId);

		slotRecordedFills.put(slot,
			new long[]{currentQty, currentGp, offerInstanceId, lastPostedFor(prev, offerInstanceId)});
		saveSlotRecordedFills();
	}

	private static long lastPostedFor(long[] prev, long offerInstanceId)
	{
		return prev != null && prev.length >= 4 && prev[2] == offerInstanceId ? prev[3] : 0L;
	}

	private void flushPendingPartialFills()
	{
		for (Integer slot : new ArrayList<>(slotRecordedFills.keySet()))
		{
			maybePostPartialFill(slot);
		}
	}

	private void maybePostPartialFill(int slot)
	{
		long[] v = slotRecordedFills.get(slot);
		if (v == null || v.length < 4 || v[0] <= v[3])
		{
			return;
		}
		long offerInstanceId = v[2];
		if (tradePostsInFlight.contains(offerInstanceId))
		{
			return;
		}
		Long lastAt = slotPartialPostedAt.get(slot);
		long now = System.currentTimeMillis();
		if (lastAt != null && now - lastAt < PARTIAL_POST_INTERVAL_MS)
		{
			return;
		}
		int idx = findMatchingOfferRow(tradeHistory, offerInstanceId);
		if (idx < 0)
		{
			return;
		}
		TradeRecord row = tradeHistory.get(idx);
		if (row.quantity <= 0 || row.serverSynced || !row.isBuy)
		{
			return;
		}
		slotPartialPostedAt.put(slot, now);
		postTradeRow(row, false, slot);
	}

	private void notePartialPosted(Integer slot, long offerInstanceId, int postedQty)
	{
		if (slot == null)
		{
			return;
		}
		long[] v = slotRecordedFills.get(slot);
		if (v != null && v.length >= 4 && v[2] == offerInstanceId && v[3] < postedQty)
		{
			v[3] = postedQty;
			saveSlotRecordedFills();
		}
	}

	private void drainDeferredTerminal(long offerInstanceId)
	{
		if (!deferredTerminalPosts.remove(offerInstanceId))
		{
			return;
		}
		int idx = findMatchingOfferRow(tradeHistory, offerInstanceId);
		if (idx < 0)
		{
			return;
		}
		TradeRecord row = tradeHistory.get(idx);
		if (!row.serverSynced && row.quantity > 0)
		{
			postTradeRow(row, true, null);
		}
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
			TradeRecord merged = existing.copy();
			merged.quantity        = existing.quantity + deltaQty;
			merged.totalGp         = existing.totalGp  + deltaGp;
			merged.priceEach       = merged.quantity > 0 ? merged.totalGp / merged.quantity : existing.priceEach;
			merged.partial         = partial;
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
			if ((panel == null || !panel.isKnownFree()) && !frozenSellByItemId.containsKey(offer.getItemId()))
			{
				long paidEach = deltaQty > 0 ? deltaGp / deltaQty : offer.getPrice();
				freezeAtPlacement(offer.getItemId(), paidEach);
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

		if (!partial && posted.quantity > 0 && !posted.serverSynced)
		{
			postOrDeferTerminal(posted);
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
		TradeRecord cleared = existing.copy();
		cleared.partial = false;
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
		TradeRecord stamped = existing.copy();
		stamped.tradeId      = stampId ? tradeId : existing.tradeId;
		stamped.serverSynced = true;
		List<TradeRecord> updated = new ArrayList<>(tradeHistory);
		updated.set(idx, stamped);
		tradeHistory = Collections.unmodifiableList(updated);
		saveTradeHistory();
	}

	private static boolean isTerminalOfferState(GrandExchangeOfferState state)
	{
		return state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_BUY
			|| state == GrandExchangeOfferState.CANCELLED_SELL;
	}

	private void finaliseAndPostExistingRow(long offerInstanceId)
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
		if (existing.partial)
		{
			TradeRecord cleared = existing.copy();
			cleared.partial = false;
			List<TradeRecord> updated = new ArrayList<>(tradeHistory);
			updated.set(idx, cleared);
			tradeHistory = Collections.unmodifiableList(updated);
			saveTradeHistory();
			toPost = cleared;
		}

		if (toPost.serverSynced)
		{
			return;
		}

		postOrDeferTerminal(toPost);
	}

	private synchronized void notifyApiKeyRejected()
	{
		long now = System.currentTimeMillis();
		if (now - lastUnauthorizedNoticeAt < UNAUTHORIZED_NOTICE_INTERVAL_MS)
		{
			return;
		}
		lastUnauthorizedNoticeAt = now;
		SwingUtilities.invokeLater(() ->
			notifier.notify("Your 07Flip API key was rejected. Your trades are still recorded locally — "
				+ "open the plugin config, paste a fresh key from 07flip.com/account, and they will sync."));
	}

	public void syncMissedTrades()
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(this::doBulkSyncToServer);
		executor.execute(this::doSyncTrackerHistory);
		executor.execute(this::doFetchTrackerStats);
	}

	private void postOrDeferTerminal(TradeRecord row)
	{
		Long oid = row.offerInstanceId;
		if (oid != null && tradePostsInFlight.contains(oid))
		{
			deferredTerminalPosts.add(oid);
			return;
		}
		postTradeRow(row, true, null);
	}

	private void postTradeRow(TradeRecord source, boolean terminal, Integer partialSlot)
	{
		if (!config.shareTradeData()
			|| config.apiKey() == null
			|| config.apiKey().trim().isEmpty())
		{
			return;
		}
		TradeRecord rowForServer = source.copy();
		rowForServer.priceEach = rowForServer.quantity > 0
			? Math.max(1L, rowForServer.totalGp / rowForServer.quantity)
			: Math.max(1L, rowForServer.priceEach);
		final Long postedOid = rowForServer.offerInstanceId;
		final int postedQty = rowForServer.quantity;
		if (postedOid != null)
		{
			tradePostsInFlight.add(postedOid);
		}
		apiClient.postTradeRecord(rowForServer, (delivered, tradeId) ->
			clientThread.invoke(() ->
			{
				if (postedOid == null)
				{
					return;
				}
				tradePostsInFlight.remove(postedOid);
				if (delivered)
				{
					if (terminal)
					{
						markRowSynced(postedOid, tradeId);
					}
					else
					{
						notePartialPosted(partialSlot, postedOid, postedQty);
					}
				}
				drainDeferredTerminal(postedOid);
			}));
	}

	private void stampLegacyWithOfferInstanceId(int idx, long offerInstanceId)
	{
		List<TradeRecord> updated = new ArrayList<>(tradeHistory);
		TradeRecord legacy = updated.get(idx);
		TradeRecord stamped = legacy.copy();
		stamped.offerInstanceId = offerInstanceId;
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
			if (t.offerInstanceId != null) continue;
			if (!t.partial)                continue;
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
			if (t.offerInstanceId == null) continue;
			if (!t.partial)                continue;
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
			if (t.offerInstanceId == null) continue;
			if (t.itemId != itemId)        continue;
			if (t.isBuy != isBuy)          continue;
			if (t.totalQuantity == null || t.totalQuantity != totalQuantity) continue;
			if (t.offerInstanceId % 10 != slot) continue;
			if (t.quantity != currentQty) continue;
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
		virtualSell.timestamp = fillTimestamp + 1L;
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
		slotPartialPostedAt.clear();
		deferredTerminalPosts.clear();
		configManager.unsetConfiguration("o7flip", SLOT_FILLS_KEY);
		slotListedAt.clear();
		configManager.unsetConfiguration("o7flip", SLOT_LISTED_KEY);
		slotFillClock.clear();
		configManager.unsetConfiguration("o7flip", SLOT_FILL_CLOCK_KEY);
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

	public boolean isSyncPromptDismissed()
	{
		return "true".equals(configManager.getConfiguration("o7flip", SYNC_PROMPT_DISMISSED_KEY));
	}

	public void dismissSyncPrompt()
	{
		configManager.setConfiguration("o7flip", SYNC_PROMPT_DISMISSED_KEY, "true");
		SwingUtilities.invokeLater(panel::refreshSyncNotice);
	}

	public void enableTradeSync()
	{
		configManager.setConfiguration("o7flip", SHARE_TRADE_DATA_KEY, "true");
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
			long lastPosted = v.length >= 4 ? v[3] : 0L;
			sb.append(entry.getKey()).append(':').append(v[0]).append(':').append(v[1])
				.append(':').append(offerId).append(':').append(lastPosted);
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
				long lastPosted = parts.length >= 5 ? Long.parseLong(parts[4]) : 0L;
				slotRecordedFills.put(slot, new long[]{qty, gp, offerId, lastPosted});
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	private void saveSlotListedAt()
	{
		if (slotListedAt.isEmpty())
		{
			configManager.unsetConfiguration("o7flip", SLOT_LISTED_KEY);
			return;
		}
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<Integer, long[]> entry : slotListedAt.entrySet())
		{
			if (!first)
			{
				sb.append(',');
			}
			long[] v = entry.getValue();
			sb.append(entry.getKey()).append(':').append(v[0]).append(':').append(v[1]).append(':').append(v[2]);
			first = false;
		}
		configManager.setConfiguration("o7flip", SLOT_LISTED_KEY, sb.toString());
	}

	private void loadSlotListedAt()
	{
		String csv = configManager.getConfiguration("o7flip", SLOT_LISTED_KEY);
		if (csv == null || csv.trim().isEmpty())
		{
			return;
		}
		for (String tok : csv.split(","))
		{
			String[] parts = tok.split(":");
			if (parts.length < 4) continue;
			try
			{
				slotListedAt.put(Integer.parseInt(parts[0]), new long[]{
					Long.parseLong(parts[1]), Long.parseLong(parts[2]), Long.parseLong(parts[3])});
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

	private static long fetchStamp(boolean ok, long ttlMs)
	{
		long now = System.currentTimeMillis();
		return ok ? now : now - Math.max(0, ttlMs - FETCH_FAIL_RETRY_MS);
	}

	public com.o7flip.model.Models.RecommendedPrices getRecommendedPrices(int itemId)
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
						recPriceFetchedAt.put(itemId, fetchStamp(rp != null, REC_PRICE_TTL_MS));
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

	public com.o7flip.model.Models.ItemInsights getOverlayInsights(int itemId)
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
						restoreFreezeFromServer(itemId, ins);
						clientThread.invokeLater(() ->
						{
							armSellPriceIfStillRelevant(itemId);
							armBuyPriceIfStillRelevant(itemId);
						});
					}
					overlayInsightsFetchedAt.put(itemId, fetchStamp(ins != null, REC_PRICE_TTL_MS));
				}
				finally
				{
					overlayInsightsInFlight.remove(itemId);
				}
			}));
		}
		return overlayInsightsCache.get(itemId);
	}

	private Integer flipAge(int itemId, boolean sell)
	{
		long[] v = flipAges.get(itemId);
		if (v == null)
		{
			return null;
		}
		long stored = sell ? v[1] : v[0];
		if (stored < 0)
		{
			return null;
		}
		long elapsedMin = (System.currentTimeMillis() - v[2]) / 60_000L;
		return (int) (stored + elapsedMin);
	}

	public Integer flipBuyAge(int itemId)
	{
		return flipAge(itemId, false);
	}

	public Integer flipSellAge(int itemId)
	{
		return flipAge(itemId, true);
	}

	private Integer flipEta(int itemId, boolean sell)
	{
		long[] v = flipAges.get(itemId);
		if (v == null || v.length < 5)
		{
			return null;
		}
		long stored = sell ? v[4] : v[3];
		return stored < 0 ? null : (int) stored;
	}

	public Integer flipEtaBuy(int itemId)
	{
		return flipEta(itemId, false);
	}

	public Integer flipEtaSell(int itemId)
	{
		return flipEta(itemId, true);
	}

	private static final int  PRIME_BATCH_MAX  = 8;
	private static final long PRIME_SPACING_MS = 400L;

	public void primeFlipAges(java.util.List<com.o7flip.model.Models.FlipItem> items)
	{
		if (items == null || items.isEmpty() || executor == null || executor.isShutdown()
			|| apiClient.isRateLimited())
		{
			return;
		}
		long now = System.currentTimeMillis();
		java.util.List<Integer> wanted = new ArrayList<>();
		for (com.o7flip.model.Models.FlipItem f : items)
		{
			if (wanted.size() >= PRIME_BATCH_MAX)
			{
				break;
			}
			if (f == null || f.itemId <= 0 || f.buyAgeMinutes != null)
			{
				continue;
			}
			long[] v = flipAges.get(f.itemId);
			if (v != null && now - v[2] <= FLIP_AGE_TTL_MS)
			{
				continue;
			}
			if (flipAgesInFlight.add(f.itemId))
			{
				wanted.add(f.itemId);
			}
		}
		if (wanted.isEmpty())
		{
			return;
		}
		final java.util.concurrent.atomic.AtomicInteger remaining
			= new java.util.concurrent.atomic.AtomicInteger(wanted.size());
		for (int i = 0; i < wanted.size(); i++)
		{
			final int itemId = wanted.get(i);
			executor.schedule(() -> apiClient.fetchItemInsights(itemId, ins ->
			{
				try
				{
					if (ins != null && ins.current != null)
					{
						com.o7flip.model.Models.ItemInsights.Liquidity liq = ins.liquidity;
						flipAges.put(itemId, new long[]{
							ins.current.buyAgeMinutes  != null ? ins.current.buyAgeMinutes  : -1L,
							ins.current.sellAgeMinutes != null ? ins.current.sellAgeMinutes : -1L,
							System.currentTimeMillis(),
							liq != null && liq.etaBuyMinutes  != null ? liq.etaBuyMinutes  : -1L,
							liq != null && liq.etaSellMinutes != null ? liq.etaSellMinutes : -1L});
					}
					else
					{
						flipAges.putIfAbsent(itemId, new long[]{-1L, -1L,
							System.currentTimeMillis() - FLIP_AGE_TTL_MS + FETCH_FAIL_RETRY_MS, -1L, -1L});
					}
				}
				finally
				{
					flipAgesInFlight.remove(itemId);
					if (remaining.decrementAndGet() == 0)
					{
						SwingUtilities.invokeLater(() -> panel.refreshAgeRows());
					}
				}
			}), (long) i * PRIME_SPACING_MS, TimeUnit.MILLISECONDS);
		}
	}

	private static final double OFFER_GREEN_TOL = 0.015;
	private static final double OFFER_MID_TOL   = 0.05;

	public static int competitiveTier(double wrongness)
	{
		return wrongness <= OFFER_GREEN_TOL ? 0 : (wrongness <= OFFER_MID_TOL ? 1 : 2);
	}

	public int offerCompetitiveTier(int itemId, boolean isBuy, long yourPrice)
	{
		if (itemId <= 0 || yourPrice <= 0)
		{
			return -1;
		}
		com.o7flip.model.Models.ItemInsights ins = getOverlayInsights(itemId);
		if (ins == null || ins.current == null)
		{
			return -1;
		}
		com.o7flip.model.Models.ItemInsights.Current c = ins.current;
		Long rec = isBuy ? c.recBuy : c.recSell;
		long live = isBuy ? c.buyPrice : c.sellPrice;
		long benchmark = (rec != null && rec > 0) ? rec : live;
		if (benchmark <= 0)
		{
			return -1;
		}
		double wrongness = isBuy
			? (benchmark - yourPrice) / (double) benchmark
			: (yourPrice - benchmark) / (double) benchmark;
		return competitiveTier(wrongness);
	}

	public java.awt.Color offerTierColor(int tier)
	{
		if (tier < 0)
		{
			return null;
		}
		return tier == 0 ? config.geBorderGood() : (tier == 1 ? config.geBorderMid() : config.geBorderBad());
	}

	public com.o7flip.model.Models.RepriceResult getReprice(int itemId, boolean isBuy, int qty, long currentPrice, int holdMinutes)
	{
		if (itemId <= 0 || panel == null || !panel.isPremium() || !config.shareTradeData())
		{
			return null;
		}
		Long fetched = repriceFetchedAt.get(itemId);
		boolean stale = fetched == null || (System.currentTimeMillis() - fetched) > REPRICE_TTL_MS;
		if (stale && executor != null && !executor.isShutdown() && repriceInFlight.add(itemId))
		{
			final Long buyPrice = getFrozenBuy(itemId);
			executor.execute(() -> apiClient.fetchReprice(itemId, isBuy, buyPrice, qty, currentPrice, holdMinutes, res ->
			{
				try
				{
					if (res != null)
					{
						repriceCache.put(itemId, res);
					}
					repriceFetchedAt.put(itemId, fetchStamp(res != null, REPRICE_TTL_MS));
				}
				finally
				{
					repriceInFlight.remove(itemId);
				}
			}));
		}
		return repriceCache.get(itemId);
	}

	private long offerLastFillAtMs(int slot)
	{
		long[] v = slotFillClock.get(slot);
		return (v != null && v[3] > 0L) ? v[3] : -1L;
	}

	public String offerLastFillText(int slot)
	{
		return ageText(offerLastFillAtMs(slot));
	}

	private static String ageText(long sinceMs)
	{
		if (sinceMs <= 0)
		{
			return null;
		}
		return ageFromMinutes(Math.max(0L, (System.currentTimeMillis() - sinceMs) / 60_000L));
	}

	public static String ageFromMinutes(long minutes)
	{
		if (minutes < 1)
		{
			return ">1m";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}
		long hours = minutes / 60;
		return hours < 24 ? hours + "h" : (hours / 24) + "d";
	}

	private void trackFillClock(int slot, GrandExchangeOffer o)
	{
		long[] prev = slotFillClock.get(slot);
		boolean sameOffer = prev != null
			&& prev[0] == o.getItemId()
			&& prev[1] == o.getTotalQuantity()
			&& o.getQuantitySold() >= prev[2];
		long lastAt = sameOffer ? prev[3] : 0L;
		if (sameOffer && o.getQuantitySold() > prev[2])
		{
			lastAt = System.currentTimeMillis();
		}
		if (prev != null
			&& prev[0] == o.getItemId()
			&& prev[1] == o.getTotalQuantity()
			&& prev[2] == o.getQuantitySold()
			&& prev[3] == lastAt)
		{
			return;
		}
		slotFillClock.put(slot, new long[]{o.getItemId(), o.getTotalQuantity(), o.getQuantitySold(), lastAt});
		saveSlotFillClock();
	}

	private void saveSlotFillClock()
	{
		if (slotFillClock.isEmpty())
		{
			configManager.unsetConfiguration("o7flip", SLOT_FILL_CLOCK_KEY);
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, long[]> entry : slotFillClock.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			long[] v = entry.getValue();
			sb.append(entry.getKey()).append(':').append(v[0]).append(':').append(v[1])
				.append(':').append(v[2]).append(':').append(v[3]);
		}
		configManager.setConfiguration("o7flip", SLOT_FILL_CLOCK_KEY, sb.toString());
	}

	private void loadSlotFillClock()
	{
		String csv = configManager.getConfiguration("o7flip", SLOT_FILL_CLOCK_KEY);
		if (csv == null || csv.trim().isEmpty())
		{
			return;
		}
		for (String tok : csv.split(","))
		{
			String[] parts = tok.split(":");
			if (parts.length < 5) continue;
			try
			{
				slotFillClock.put(Integer.parseInt(parts[0]), new long[]{
					Long.parseLong(parts[1]), Long.parseLong(parts[2]),
					Long.parseLong(parts[3]), Long.parseLong(parts[4])});
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	public int offerHeldMinutes(int slot)
	{
		long listedMs = offerListedAtMs(slot);
		if (listedMs <= 0)
		{
			return 0;
		}
		long ageMs = System.currentTimeMillis() - listedMs;
		return ageMs <= 0 ? 0 : (int) (ageMs / 60_000L);
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
		final int seq = ++insightsRequestSeq;
		SwingUtilities.invokeLater(() -> panel.showInsightsLoading(itemId, fallbackName));
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(() -> doFetchItemInsights(itemId, seq));
	}

	private void doFetchItemInsights(int itemId, int seq)
	{
		apiClient.fetchItemInsights(itemId, insights ->
		{
			if (seq != insightsRequestSeq)
			{
				return;
			}
			if (insights != null && insights.buyLimit > 0)
			{
				rememberBuyLimit(insights.itemId, insights.buyLimit);
			}
			if (insights != null)
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
		Set<Long> liveOfferIds = new HashSet<>();
		for (long[] v : slotRecordedFills.values())
		{
			if (v != null && v.length >= 3)
			{
				liveOfferIds.add(v[2]);
			}
		}
		List<TradeRecord> snapshot = new ArrayList<>();
		for (TradeRecord t : all)
		{
			if (t == null || t.serverSynced || t.tradeId != null)
			{
				continue;
			}
			if (t.partial && t.offerInstanceId != null && liveOfferIds.contains(t.offerInstanceId))
			{
				continue;
			}
			snapshot.add(t);
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

	private long lastPendingFilterCeiling = Long.MIN_VALUE;

	public long capitalFilterCeiling()
	{
		if (totalCapital() <= 0)
		{
			return 0L;
		}
		boolean useFree = config.narrowByPendingOffers();
		long basis = useFree ? freeCapital() : totalCapital();
		long bucketed = (basis / CASH_BUCKET) * CASH_BUCKET;
		if (bucketed <= 0)
		{
			return useFree ? 1L : 0L;
		}
		return bucketed;
	}

	private O7FlipConfig.CapitalMode activeCapitalMode()
	{
		return config.capitalFilterDisabled() ? O7FlipConfig.CapitalMode.OFF : config.capitalMode();
	}

	public long freeCapital()
	{
		switch (activeCapitalMode())
		{
			case MANUAL:
				return Math.max(0L, config.capitalManual() - deployedCapital());
			case OFF:
			default:
				return 0L;
		}
	}

	public long totalCapital()
	{
		switch (activeCapitalMode())
		{
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
		for (com.o7flip.model.Models.ActiveOfferSnapshot s : activeOffers.values())
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

	private void refreshForPendingCapitalChange()
	{
		if (!config.narrowByPendingOffers() || activeCapitalMode() != O7FlipConfig.CapitalMode.MANUAL)
		{
			return;
		}
		long ceiling = capitalFilterCeiling();
		if (ceiling == lastPendingFilterCeiling)
		{
			return;
		}
		lastPendingFilterCeiling = ceiling;
		onCapitalChanged();
		if (panel != null)
		{
			SwingUtilities.invokeLater(panel::onCapitalAutoAdjusted);
		}
	}

	public void setCapitalFilterEnabled(boolean enabled)
	{
		if (enabled)
		{
			if (config.capitalFilterDisabled())
			{
				return;
			}
			configManager.setConfiguration("o7flip", "capitalMode", O7FlipConfig.CapitalMode.MANUAL);
		}
		else
		{
			configManager.setConfiguration("o7flip", "capitalMode", O7FlipConfig.CapitalMode.OFF);
		}
		onCapitalChanged();
	}

	public void persistCapitalManual(long gp)
	{
		configManager.setConfiguration("o7flip", "capitalManual", gp);
	}

	public void persistNarrowByPendingOffers(boolean enabled)
	{
		configManager.setConfiguration("o7flip", "narrowByPendingOffers", enabled);
	}

	private void adjustCapitalForTrade(int itemId, boolean isBuy, int deltaQty, long deltaGp)
	{
		if (activeCapitalMode() != O7FlipConfig.CapitalMode.MANUAL || deltaQty <= 0)
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

	void onDecantPageChanged(int page)
	{
		SwingUtilities.invokeLater(() -> panel.rerenderDecants());
	}

	void onDecantSortChanged(int sortIdx)
	{
		SwingUtilities.invokeLater(() -> panel.rerenderDecants());
	}

	private void fetchDecantingNow()
	{
		apiClient.fetchDecanting(items ->
		{
			if (items != null && !items.isEmpty()) saveCache("decant", items);
			SwingUtilities.invokeLater(() -> panel.updateDecanting(items));
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
			case "Decant":
				executor.execute(this::fetchDecantingNow);
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
		com.o7flip.model.Models.ItemInsights ins = currentInsights;
		if (ins != null && ins.itemId == itemId)
		{
			FlipItem f = new FlipItem();
			f.members = false;
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
		stub.members = false;
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
		com.o7flip.model.Models.OptimizerSession s = activeSession;
		long capital = s != null ? s.inputs.capital : effectiveCapital();
		if (capital <= 0 || slots < 1) return;
		String risk        = s != null && s.inputs.risk != null ? s.inputs.risk : "medium";
		int maxFillHours   = s != null && s.inputs.maxFillHours != null ? s.inputs.maxFillHours : 4;
		Boolean members    = s != null ? s.inputs.members : null;
		Double minProfit   = s != null ? s.inputs.minProfitPct : null;
		runOptimizer(capital, Math.min(8, slots), risk, maxFillHours, members, minProfit);
	}

	public void swapPlanSlot(int swapIndex, com.o7flip.model.Models.OptimizeResult current)
	{
		if (executor == null || executor.isShutdown() || current == null
			|| current.allocations == null || swapIndex < 0 || swapIndex >= current.allocations.size())
		{
			return;
		}
		com.o7flip.model.Models.OptimizeResult.Allocation old = current.allocations.get(swapIndex);
		long slotCapital = old.gpAllocated;
		java.util.List<Integer> excludes = new java.util.ArrayList<>();
		for (com.o7flip.model.Models.OptimizeResult.Allocation a : current.allocations)
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

	private volatile com.o7flip.model.Models.OptimizerSession activeSession;

	public int planRemainingBuyQty(int itemId)
	{
		com.o7flip.model.Models.OptimizeResult.Allocation a = planAllocationFor(itemId);
		if (a == null || a.qty <= 0)
		{
			return -1;
		}
		int bought = 0;
		if (a.buys != null)
		{
			for (com.o7flip.model.Models.SlotFill f : a.buys)
			{
				if (f != null) bought += f.qty;
			}
		}
		int remaining = a.qty - bought;
		return remaining > 0 ? remaining : -1;
	}

	public com.o7flip.model.Models.OptimizeResult.Allocation planAllocationFor(int itemId)
	{
		com.o7flip.model.Models.OptimizerSession s = activeSession;
		if (s == null || s.slots == null)
		{
			return null;
		}
		for (com.o7flip.model.Models.OptimizeResult.Allocation a : s.slots)
		{
			if (a != null && a.itemId == itemId)
			{
				return a;
			}
		}
		return null;
	}
	private volatile boolean offlineReconcileArmed = false;
	private ScheduledFuture<?> pendingSessionPost;
	private ScheduledFuture<?> sessionPollTask;
	private ScheduledFuture<?> sessionBackgroundPollTask;
	private final java.util.List<com.o7flip.model.Models.CompletedPosition> completedPositions = new java.util.ArrayList<>();
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
				boolean changed = dedupeSlotsByLeg(session);
				if (changed)
				{
					session.generatedAt = nextGeneratedAt(session.generatedAt, session.updatedAt);
				}
				changed |= retroAttributeFills(session);
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
		com.o7flip.model.Models.OptimizerSession s = activeSession;
		if (s == null || s.slots == null)
		{
			return false;
		}
		try
		{
			for (com.o7flip.model.Models.OptimizeResult.Allocation a : s.slots)
			{
				if (a == null) continue;
				if (a.state == com.o7flip.model.Models.SlotState.BUYING
					|| a.state == com.o7flip.model.Models.SlotState.FILLED
					|| a.state == com.o7flip.model.Models.SlotState.SELLING)
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
				com.o7flip.model.Models.OptimizerSession local = activeSession;
				if (local == null)
				{
					activeSession = remote;
					boolean seeded = dedupeSlotsByLeg(remote);
					if (seeded)
					{
						remote.generatedAt = nextGeneratedAt(remote.generatedAt, remote.updatedAt);
					}
					seeded |= retroAttributeFills(remote);
					seeded |= sweepSellListedFromOffers(remote);
					if (seeded)
					{
						scheduleSessionPost();
					}
					SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(remote));
					return;
				}
				boolean changed = mergeRemoteFills(local, remote);
				if (dedupeSlotsByLeg(local))
				{
					local.generatedAt = nextGeneratedAt(local.generatedAt, local.updatedAt);
					changed = true;
					scheduleSessionPost();
				}
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

	static boolean mergeRemoteFills(com.o7flip.model.Models.OptimizerSession local,
	                                 com.o7flip.model.Models.OptimizerSession remote)
	{
		if (local.slots == null || remote.slots == null) return false;
		if (isRemoteStructurallyNewer(local, remote))
		{
			adoptServerStructure(local, remote);
			return true;
		}
		boolean anyChange = false;
		java.util.Map<Integer, com.o7flip.model.Models.OptimizeResult.Allocation> byId = new java.util.HashMap<>();
		for (com.o7flip.model.Models.OptimizeResult.Allocation r : remote.slots)
		{
			if (r != null && r.itemId > 0) byId.put(r.itemId, r);
		}
		for (com.o7flip.model.Models.OptimizeResult.Allocation l : local.slots)
		{
			if (l == null) continue;
			com.o7flip.model.Models.OptimizeResult.Allocation r = byId.get(l.itemId);
			if (r == null) continue;
			if (r.overrideRev > l.appliedOverrideRev)
			{
				l.buys                    = new java.util.ArrayList<>(r.buys);
				l.sells                   = new java.util.ArrayList<>(r.sells);
				l.state                   = r.state;
				l.sellListed              = r.sellListed;
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
			com.o7flip.model.Models.SlotState derived =
				com.o7flip.model.Models.SlotState.derive(l.qty, l.buys, l.sells);
			if (l.state != derived) { l.state = derived; anyChange = true; }
		}
		local.lastPollAt = remote.lastPollAt;
		return anyChange;
	}

	static boolean isRemoteStructurallyNewer(com.o7flip.model.Models.OptimizerSession local,
		com.o7flip.model.Models.OptimizerSession remote)
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

	static void adoptServerStructure(com.o7flip.model.Models.OptimizerSession local,
		com.o7flip.model.Models.OptimizerSession remote)
	{
		java.util.Map<Long, com.o7flip.model.Models.OptimizeResult.Allocation> byOid = new java.util.HashMap<>();
		java.util.Map<Integer, com.o7flip.model.Models.OptimizeResult.Allocation> byItem = new java.util.HashMap<>();
		if (local.slots != null)
		{
			for (com.o7flip.model.Models.OptimizeResult.Allocation l : local.slots)
			{
				if (l == null) continue;
				if (l.offerInstanceId != null) byOid.put(l.offerInstanceId, l);
				if (l.itemId > 0) byItem.putIfAbsent(l.itemId, l);
			}
		}
		java.util.List<com.o7flip.model.Models.OptimizeResult.Allocation> rebuilt = new java.util.ArrayList<>();
		if (remote.slots != null)
		{
			for (com.o7flip.model.Models.OptimizeResult.Allocation r : remote.slots)
			{
				if (r == null) continue;
				com.o7flip.model.Models.OptimizeResult.Allocation l =
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
						boolean localClosed = localHasFills
							&& com.o7flip.model.Models.SlotState.derive(l.qty, l.buys, l.sells) == com.o7flip.model.Models.SlotState.CLOSED;
						if (localHasFills && !localClosed)
						{
							r.buys  = l.buys;
							r.sells = l.sells;
							if (l.offerInstanceId != null) r.offerInstanceId = l.offerInstanceId;
							r.sellListed              = l.sellListed || r.sellListed;
							r.pendingOfflineReconcile = l.pendingOfflineReconcile;
						}
						r.appliedOverrideRev      = Math.max(l.appliedOverrideRev, r.overrideRev);
						r.state = com.o7flip.model.Models.SlotState.derive(r.qty, r.buys, r.sells);
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

	static boolean dedupeSlotsByLeg(com.o7flip.model.Models.OptimizerSession s)
	{
		if (s == null || s.slots == null || s.slots.size() < 2) return false;
		java.util.List<com.o7flip.model.Models.OptimizeResult.Allocation> out = new java.util.ArrayList<>();
		java.util.Map<String, Integer> posByLeg = new java.util.HashMap<>();
		for (com.o7flip.model.Models.OptimizeResult.Allocation a : s.slots)
		{
			if (a == null) continue;
			if (a.itemId <= 0) { out.add(a); continue; }
			String key = slotLegKey(a);
			Integer pos = posByLeg.get(key);
			if (pos == null)
			{
				posByLeg.put(key, out.size());
				out.add(a);
			}
			else if (slotFillProgress(a) > slotFillProgress(out.get(pos)))
			{
				out.set(pos, a);
			}
		}
		if (out.size() == s.slots.size()) return false;
		s.slots = out;
		return true;
	}

	private static String slotLegKey(com.o7flip.model.Models.OptimizeResult.Allocation a)
	{
		return a.offerInstanceId != null
			? a.itemId + ":" + a.offerInstanceId
			: Integer.toString(a.itemId);
	}

	private static int slotFillProgress(com.o7flip.model.Models.OptimizeResult.Allocation a)
	{
		return a == null ? -1 : sumQty(a.buys) + sumQty(a.sells);
	}

	private static boolean mergeFillList(java.util.List<com.o7flip.model.Models.SlotFill> local,
	                              java.util.List<com.o7flip.model.Models.SlotFill> remote)
	{
		if (remote == null || remote.isEmpty()) return false;
		boolean any = false;
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (com.o7flip.model.Models.SlotFill f : local) if (f != null) seen.add(fillKey(f));
		for (com.o7flip.model.Models.SlotFill rf : remote)
		{
			if (rf == null) continue;
			if (seen.add(fillKey(rf))) { local.add(rf); any = true; }
		}
		return any;
	}

	private static String fillKey(com.o7flip.model.Models.SlotFill f)
	{
		return f.qty + "@" + f.priceEach + "@" + (f.tradedAt == null ? "" : f.tradedAt);
	}

	private void reconcileOfflineCompletions()
	{
		com.o7flip.model.Models.OptimizerSession s = activeSession;
		if (s == null || s.slots == null) return;

		if (sweepSellListedFromOffers(s))
		{
			scheduleSessionPost();
			final com.o7flip.model.Models.OptimizerSession listedSnap = s;
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
		for (com.o7flip.model.Models.OptimizeResult.Allocation a : s.slots)
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

		final com.o7flip.model.Models.OptimizerSession snap = s;
		SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
	}

	static boolean isOfflineSellCompletion(com.o7flip.model.Models.OptimizeResult.Allocation a,
		java.util.Set<Integer> liveOfferItems)
	{
		return a != null
			&& a.state == com.o7flip.model.Models.SlotState.SELLING
			&& !liveOfferItems.contains(a.itemId);
	}

	public void dismissOfflineReconcile(int itemId)
	{
		clientThread.invoke(() ->
		{
			com.o7flip.model.Models.OptimizerSession s = activeSession;
			if (s == null || s.slots == null) return;
			boolean changed = false;
			for (com.o7flip.model.Models.OptimizeResult.Allocation a : s.slots)
			{
				if (a != null && a.itemId == itemId && a.pendingOfflineReconcile)
				{
					a.pendingOfflineReconcile = false;
					changed = true;
				}
			}
			if (changed)
			{
				final com.o7flip.model.Models.OptimizerSession snap = s;
				SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
			}
		});
	}

	private void seedActiveSessionFrom(com.o7flip.model.Models.OptimizeResult result, long capital, int slots,
	                                   String risk, int maxFillHours, Boolean members, Double minProfitPct)
	{
		if (result == null || result.allocations == null) return;
		com.o7flip.model.Models.OptimizerSession prev = activeSession;
		if (prev != null && prev.slots != null)
		{
			java.util.Map<Integer, com.o7flip.model.Models.OptimizeResult.Allocation> prevByItem = new java.util.HashMap<>();
			for (com.o7flip.model.Models.OptimizeResult.Allocation p : prev.slots)
			{
				if (p != null && p.itemId > 0) prevByItem.putIfAbsent(p.itemId, p);
			}
			for (com.o7flip.model.Models.OptimizeResult.Allocation next : result.allocations)
			{
				if (next == null) continue;
				com.o7flip.model.Models.OptimizeResult.Allocation old = prevByItem.get(next.itemId);
				if (old == null || (old.buys.isEmpty() && old.sells.isEmpty())) continue;
				if (com.o7flip.model.Models.SlotState.derive(old.qty, old.buys, old.sells) == com.o7flip.model.Models.SlotState.CLOSED) continue;
				next.buys  = old.buys;
				next.sells = old.sells;
				if (old.offerInstanceId != null) next.offerInstanceId = old.offerInstanceId;
				next.partial                 = old.partial;
				next.sellListed              = old.sellListed;
				next.appliedOverrideRev      = Math.max(old.appliedOverrideRev, next.overrideRev);
				next.pendingOfflineReconcile = old.pendingOfflineReconcile;
				next.state = com.o7flip.model.Models.SlotState.derive(next.qty, next.buys, next.sells);
			}
		}
		com.o7flip.model.Models.OptimizerSession s = new com.o7flip.model.Models.OptimizerSession();
		s.inputs.capital      = capital;
		s.inputs.slots        = slots;
		s.inputs.risk         = risk;
		s.inputs.maxFillHours = maxFillHours;
		s.inputs.members      = members;
		s.inputs.minProfitPct = minProfitPct;
		s.slots               = new java.util.ArrayList<>(result.allocations);
		String prevGen = prev != null ? prev.generatedAt : null;
		s.generatedAt         = nextGeneratedAt(prevGen, result.updatedAt);
		dedupeSlotsByLeg(s);
		activeSession         = s;
	}

	private void replaceSlotInActiveSession(int idx, com.o7flip.model.Models.OptimizeResult.Allocation next,
		String newGeneratedAt)
	{
		clientThread.invoke(() ->
		{
			com.o7flip.model.Models.OptimizerSession s = activeSession;
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
			com.o7flip.model.Models.OptimizerSession live = activeSession;
			if (live == null) return;
			com.o7flip.model.Models.OptimizerSession snapshot = live.copy();
			apiClient.postActiveSession(snapshot, ok -> { });
		});
	}

	private void attributeTradeToActiveSlot(int itemId, int qty, long pricePer, boolean isBuy,
		long timestampMs, long offerInstanceId)
	{
		com.o7flip.model.Models.OptimizerSession s = activeSession;
		if (s == null || s.slots == null || qty <= 0 || itemId <= 0) return;
		int slotIdx = -1;
		com.o7flip.model.Models.OptimizeResult.Allocation slot = null;
		for (int i = 0; i < s.slots.size(); i++)
		{
			com.o7flip.model.Models.OptimizeResult.Allocation a = s.slots.get(i);
			if (a != null && a.itemId == itemId) { slot = a; slotIdx = i; break; }
		}
		if (slot == null) return;
		if (slot.offerInstanceId == null && offerInstanceId > 0)
		{
			slot.offerInstanceId = offerInstanceId;
		}
		com.o7flip.model.Models.SlotState prevState = slot.state;

		int countedQty = cappedFillQty(isBuy, slot, qty);
		if (countedQty <= 0) return;

		String tradedAt = java.time.Instant.ofEpochMilli(timestampMs).toString();
		foldFill(isBuy ? slot.buys : slot.sells, countedQty, pricePer, tradedAt, !isBuy);
		slot.state = com.o7flip.model.Models.SlotState.derive(slot.qty, slot.buys, slot.sells);
		scheduleSessionPost();

		if (prevState != com.o7flip.model.Models.SlotState.FILLED
			&& slot.state == com.o7flip.model.Models.SlotState.FILLED
			&& slot.sellPrice > 0)
		{
			armSellAutoFill(slot.itemId, slot.sellPrice, slot.name);
		}

		if (prevState != com.o7flip.model.Models.SlotState.CLOSED
			&& slot.state == com.o7flip.model.Models.SlotState.CLOSED)
		{
			slot.sellListed = false;
			appendCompletedPosition(slot);
		}

		final com.o7flip.model.Models.OptimizerSession snap = s;
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

	private boolean sweepSellListedFromOffers(com.o7flip.model.Models.OptimizerSession s)
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
		for (com.o7flip.model.Models.OptimizeResult.Allocation a : s.slots)
		{
			if (a == null || a.itemId <= 0) continue;
			boolean live = liveSells.contains(a.itemId);
			if (live && !a.sellListed && a.state != com.o7flip.model.Models.SlotState.CLOSED)
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
		com.o7flip.model.Models.OptimizerSession s = activeSession;
		if (s == null || s.slots == null || itemId <= 0) return;
		boolean changed = false;
		for (com.o7flip.model.Models.OptimizeResult.Allocation a : s.slots)
		{
			if (a == null || a.itemId != itemId) continue;
			if (a.sellListed || a.state == com.o7flip.model.Models.SlotState.CLOSED) continue;
			a.sellListed = true;
			changed = true;
		}
		if (changed)
		{
			scheduleSessionPost();
			final com.o7flip.model.Models.OptimizerSession snap = s;
			SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
		}
	}

	private void clearPlanSellListedIfNoLiveSell(int itemId)
	{
		com.o7flip.model.Models.OptimizerSession s = activeSession;
		if (s == null || s.slots == null || itemId <= 0) return;
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers != null)
		{
			for (GrandExchangeOffer o : offers)
			{
				if (o != null && o.getItemId() == itemId
					&& o.getState() == GrandExchangeOfferState.SELLING)
				{
					return;
				}
			}
		}
		boolean changed = false;
		for (com.o7flip.model.Models.OptimizeResult.Allocation a : s.slots)
		{
			if (a == null || a.itemId != itemId || !a.sellListed) continue;
			a.sellListed = false;
			changed = true;
		}
		if (changed)
		{
			scheduleSessionPost();
			final com.o7flip.model.Models.OptimizerSession snap = s;
			SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
		}
	}

	private boolean retroAttributeFills(com.o7flip.model.Models.OptimizerSession s)
	{
		if (s == null || s.slots == null) return false;
		java.time.Instant gen = parseInstantOrNull(s.generatedAt);
		if (gen == null) return false;
		long genMs = gen.toEpochMilli();
		java.util.List<TradeRecord> history = tradeHistory;
		if (history == null || history.isEmpty()) return false;

		boolean anyChange = false;
		for (com.o7flip.model.Models.OptimizeResult.Allocation slot : s.slots)
		{
			if (slot == null || slot.itemId <= 0 || slot.qty <= 0) continue;
			if (!slot.buys.isEmpty() || !slot.sells.isEmpty()) continue;
			boolean slotChanged = false;
			for (TradeRecord t : history)
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
				slot.state = com.o7flip.model.Models.SlotState.derive(slot.qty, slot.buys, slot.sells);
				anyChange = true;
				log.debug("[07Flip] Retro-attributed trade history to plan slot {} ({})",
					slot.name, slot.itemId);
			}
		}
		return anyChange;
	}

	static int cappedFillQty(boolean isBuy, com.o7flip.model.Models.OptimizeResult.Allocation slot, int qty)
	{
		if (slot == null) return 0;
		int capacity = isBuy
			? slot.qty - sumQty(slot.buys)
			: sumQty(slot.buys) - sumQty(slot.sells);
		return Math.min(qty, Math.max(0, capacity));
	}

	private static void foldFill(java.util.List<com.o7flip.model.Models.SlotFill> leg,
		int qty, long pricePer, String tradedAt, boolean preferLatestTime)
	{
		if (leg == null || qty <= 0) return;
		com.o7flip.model.Models.SlotFill entry;
		if (leg.isEmpty())
		{
			entry = new com.o7flip.model.Models.SlotFill();
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
				com.o7flip.model.Models.SlotFill extra = leg.remove(1);
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

	private static long sumGp(java.util.List<com.o7flip.model.Models.SlotFill> fills)
	{
		if (fills == null) return 0L;
		long total = 0L;
		for (com.o7flip.model.Models.SlotFill f : fills)
		{
			if (f != null) total += (long) f.qty * f.priceEach;
		}
		return total;
	}

	private static int sumQty(java.util.List<com.o7flip.model.Models.SlotFill> fills)
	{
		if (fills == null) return 0;
		int total = 0;
		for (com.o7flip.model.Models.SlotFill f : fills)
		{
			if (f != null) total += f.qty;
		}
		return total;
	}

	public void markPartial(int slotIdx)
	{
		com.o7flip.model.Models.OptimizerSession s = activeSession;
		if (s == null || s.slots == null || slotIdx < 0 || slotIdx >= s.slots.size()) return;
		com.o7flip.model.Models.OptimizeResult.Allocation slot = s.slots.get(slotIdx);
		if (slot == null) return;
		int bought = sumQty(slot.buys);
		if (bought <= 0 || slot.partial) return;

		slot.reservedGp     = slot.gpAllocated;
		slot.qty            = bought;
		slot.gpAllocated    = sumGp(slot.buys);
		slot.expectedProfit = (long) bought * slot.profitPerUnit;
		slot.partial        = true;
		slot.state          = com.o7flip.model.Models.SlotState.derive(slot.qty, slot.buys, slot.sells);

		if (slot.state == com.o7flip.model.Models.SlotState.FILLED && slot.sellPrice > 0)
		{
			armSellAutoFill(slot.itemId, slot.sellPrice, slot.name);
		}

		s.generatedAt = nextGeneratedAt(s.generatedAt, null);

		scheduleSessionPost();
		final com.o7flip.model.Models.OptimizerSession snap = s;
		SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
	}

	private void appendCompletedPosition(com.o7flip.model.Models.OptimizeResult.Allocation closed)
	{
		if (closed == null) return;
		int soldQty = sumQty(closed.sells);
		if (soldQty <= 0) return;

		com.o7flip.model.Models.CompletedPosition cp = new com.o7flip.model.Models.CompletedPosition();
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
			for (com.o7flip.model.Models.CompletedPosition existing : completedPositions)
			{
				if (existing != null && existing.dedupeKey().equals(key)) { added = false; break; }
			}
			if (added) completedPositions.add(0, cp);
		}
		if (added && panel != null) SwingUtilities.invokeLater(panel::onCompletedPositionsChanged);

		if (executor == null || executor.isShutdown()) return;
		executor.execute(() -> apiClient.postCompletedPosition(cp, list ->
		{
			if (list != null) setCompletedPositions(list);
		}));
	}

	private static Double computeFillHours(com.o7flip.model.Models.OptimizeResult.Allocation a)
	{
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		int seen = 0;
		for (java.util.List<com.o7flip.model.Models.SlotFill> list :
			java.util.Arrays.asList(a.buys, a.sells))
		{
			if (list == null) continue;
			for (com.o7flip.model.Models.SlotFill f : list)
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

	private static String lastTradeIso(java.util.List<com.o7flip.model.Models.SlotFill> sells)
	{
		String last = null;
		long lastMs = Long.MIN_VALUE;
		if (sells != null)
		{
			for (com.o7flip.model.Models.SlotFill f : sells)
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

	public java.util.List<com.o7flip.model.Models.CompletedPosition> getCompletedPositions()
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
			for (com.o7flip.model.Models.CompletedPosition cp : completedPositions)
			{
				if (cp != null) total += cp.profit;
			}
		}
		return total;
	}

	private void setCompletedPositions(java.util.List<com.o7flip.model.Models.CompletedPosition> list)
	{
		if (list == null) return;
		synchronized (completedPositions)
		{
			completedPositions.clear();
			for (com.o7flip.model.Models.CompletedPosition cp : list)
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
