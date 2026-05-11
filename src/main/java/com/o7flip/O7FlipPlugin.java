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
import java.util.function.BiConsumer;
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
	private GePriceOverlay priceOverlay;

	@Inject
	private GpDropOverlay gpDropOverlay;

	@Inject
	private InventoryTooltipOverlay inventoryTooltipOverlay;

	@Inject
	private Gson gson;

	@Inject
	private net.runelite.client.config.ConfigManager configManager;

	/** Public so overlays / row panels in other packages can read auth state via panel.isPremium(). */
	public O7FlipPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> refreshTask;
	private ScheduledFuture<?> authRefreshTask;

	// Barrows/Moon/Decanting change with GE prices (hourly), not every minute.
	// Only refresh them every SLOW_EVERY cycles to reduce server load.
	// Initial value SLOW_EVERY (not 0) so the first refresh after startup
	// already pulls Moons / Barrows / Decant — otherwise the user opens the
	// panel and stares at empty premium tabs for ~6 minutes (5 × 90s) until
	// the slow tick rolls over for the first time.
	private static final int SLOW_EVERY = 5;
	private int slowTick = SLOW_EVERY;

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

	// Phase 4: after auto-fill, highlight the Confirm button for ~3s so the
	// user knows the offer is queued and ready. Cleared once the deadline
	// passes or the GE setup screen closes.
	public volatile long confirmHighlightUntilMs = 0L;

	// Last item id we've already armed the implicit-sell auto-fill for. Reset
	// when the setup screen closes so a fresh sell of the same item still
	// re-arms. Game-thread only.
	private int sellSetupArmedItemId = -1;

	// -------------------------------------------------------------------------
	// Long-lived right-click queue used by the movable GE price overlay.
	// Outlives the per-phase pendingGe* fields above so the overlay can show the
	// queued price across the full GE flow (search → setup → price input).
	// Cleared on offer placement, manual cancel, or TTL expiry.
	// -------------------------------------------------------------------------
	private static final long OVERLAY_QUEUE_TTL_MS = 10L * 60L * 1000L;

	private volatile int    overlayQueueItemId   = -1;
	private volatile long   overlayQueuePrice    = -1;
	private volatile boolean overlayQueueIsBuy   = false;
	private volatile long   overlayQueueExpiresAt = 0L;

	// -------------------------------------------------------------------------
	// GE integration — shared volatile state
	// -------------------------------------------------------------------------

	/** Per-tab last-fetched lists. Written on executor thread only. */
	/** Most recent /flips response. Package-private so GePriceOverlay can read flip07Score from it. */
	List<FlipItem>  lastFlips  = Collections.emptyList();
	private List<AlertItem> lastAlerts = Collections.emptyList();
	private List<DumpItem>  lastDumps  = Collections.emptyList();
	private List<SpikeItem> lastSpikes = Collections.emptyList();

	/** Aggregated lookup map by item ID. Volatile reference swap on each rebuild. */
	public volatile Map<Integer, TrackedItemData> trackedItems = Collections.emptyMap();

	/** Item IDs currently in the player's inventory. Volatile reference swap. */
	public volatile Set<Integer> inventoryItemIds = Collections.emptySet();

	/** Coin count in the player's inventory. Used by the cash-stack-aware
	 *  Flips request when {@link O7FlipConfig#usePersonalisedFlips()} is on. */
	public volatile long inventoryCoins = 0L;
	private static final int COINS_ITEM_ID = 995;
	private static final long CASH_BUCKET = 100_000L;

	/**
	 * Cache of {@code /recommended-prices} responses keyed by item ID.
	 * Server caches 60s, so we cache locally for 60s too. Used by
	 * GePriceOverlay to fall back when an item isn't in the current Flips
	 * list (the bundled response only carries ~40 items).
	 */
	private final java.util.concurrent.ConcurrentHashMap<Integer, com.o7flip.model.RecommendedPrices> recPriceCache
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> recPriceFetchedAt
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Integer> recPriceInFlight
		= java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static final long REC_PRICE_TTL_MS = 60_000L;

	/**
	 * Per-item insights cache used exclusively by the GE setup-screen
	 * overlay. Separate from {@link #currentInsights} (which the Item tab
	 * owns) so an open GE setup can render its own chart / score / volume
	 * without overwriting the panel's loaded item. Same TTL as
	 * {@link #recPriceCache} since the server caches alongside it.
	 */
	private final java.util.concurrent.ConcurrentHashMap<Integer, com.o7flip.model.ItemInsights> overlayInsightsCache
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> overlayInsightsFetchedAt
		= new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Integer> overlayInsightsInFlight
		= java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Live GE offers keyed by slot index — snapshots, NOT raw
	 * {@link GrandExchangeOffer} references. Snapshots are captured on the
	 * game thread (where item-name resolution is legal) so the panel can
	 * render rows from any thread without triggering the client-thread
	 * assertion in {@code Client.getItemDefinition}.
	 */
	public volatile Map<Integer, com.o7flip.model.ActiveOfferSnapshot> activeOffers = Collections.emptyMap();

	/** Previous offer state per slot — used to detect buy/sell completions. Game-thread only. */
	private final Map<Integer, GrandExchangeOfferState> prevSlotStates = new HashMap<>();

	/**
	 * Cumulative fills already written to {@link #tradeHistory} per GE slot.
	 * Each entry is {@code {quantitySold, totalGp, offerInstanceId}} where
	 * {@code offerInstanceId} is a unique per-offer key generated on first
	 * observation. Compared against the live offer state every tick (and on
	 * state-change events) to detect incremental fills so partial buys land
	 * in tradeHistory while the offer is still BUYING — otherwise a sell of
	 * a partially-filled buy is a phantom flip (no matching buy in history).
	 *
	 * The {@code offerInstanceId} is what lets {@link #recordTrade} merge
	 * subsequent fills of the same offer into the same TradeRecord row
	 * instead of appending one row per fill — keeping the UI clean while
	 * preserving the per-fill timing needed for accurate cost-basis tracking.
	 *
	 * Cleared when the slot transitions to EMPTY (offer collected) so a new
	 * offer in the same slot starts from zero with a fresh offerInstanceId.
	 * A defensive check inside {@link #recordIfNewFills} also resets the
	 * slot when {@code quantitySold} drops below what we recorded, which
	 * catches the rare case where the slot changes offers between
	 * observations.
	 *
	 * Game-thread only.
	 */
	private final Map<Integer, long[]> slotRecordedFills = new HashMap<>();

	/** Completed trade history (oldest first). Volatile reference swap. */
	public volatile List<TradeRecord> tradeHistory = Collections.emptyList();

	/**
	 * Latest server-authoritative My Trades stats. Null when no API key is
	 * set, sharing is off, or the endpoint hasn't responded yet — in which
	 * case the panel falls back to a local FIFO ProfitCalculator result.
	 */
	public volatile com.o7flip.model.TrackerStats trackerStats = null;

	/** Currently selected item for the Insights tab; null when nothing is selected. */
	public volatile com.o7flip.model.ItemInsights currentInsights = null;

	/**
	 * Local cache of frozen 07Flip sell prices keyed by item id. Populated when
	 * the plugin calls {@code /v2/item/{id}/freeze} after a Buy on GE action,
	 * cleared when the matching sell closes out the open position. The GE
	 * setup overlay reads this map to override live rec_sell — projected
	 * margin survives market drift between buy placement and sell setup.
	 */
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> frozenSellByItemId
		= new java.util.concurrent.ConcurrentHashMap<>();

	private static final int MAX_TRADE_HISTORY = 200;
	private static final String TRADE_HISTORY_KEY = "tradeHistory";
	private static final String LAST_TRACKER_SYNC_KEY = "lastTrackerSync";
	private static final String BLOCKLIST_KEY = "blocklistItemIds";
	private static final String SLOT_FILLS_KEY = "slotRecordedFills";

	/** Item IDs the user has hidden from Flips/Dumps/Spikes/Dips/Alerts panels. */
	public volatile Set<Integer> blocklist = Collections.emptySet();

	/** Called by item panels on right-click to queue a GE buy pre-fill. */
	public void queueGeBuy(int itemId, long price, String name)
	{
		log.debug("[07Flip] GE buy queued: {} ({}) @ {}", name, itemId, price);
		setOverlayQueue(itemId, price, true);

		// Auto-freeze the current 07Flip rec_buy / rec_sell pair the moment
		// the user intends to buy. The sell-side overlay later reads the
		// frozen sell price via /v2/item/{id} so projected margin survives
		// any market drift between buy placement and sell setup. We try the
		// Flips list first (cheapest source of the rec pair); if the item
		// isn't in any tracked list, fall back to /recommended-prices.
		freezeFromTrackedOrFetch(itemId);
		clientThread.invokeLater(() ->
		{
			// Always arm the queue first. The search chatbox only appears AFTER
			// the user clicks an empty buy slot — on the slots view the widget
			// doesn't exist yet, so the immediate-fire path below would silently
			// no-op. onGameTick polls every tick and fires once the chatbox
			// becomes available, clearing the queue on success.
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

			// Edge case: search chatbox is already open (e.g. user clicked a
			// slot before right-clicking the panel). Fire immediately; clear
			// the queue on success so onGameTick doesn't double-fire.
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

	/** Called by item panels on right-click to queue a GE sell price pre-fill. */
	public void queueGeSell(int itemId, long price, String name)
	{
		log.debug("[07Flip] GE sell queued: {} ({}) @ {}", name, itemId, price);
		setOverlayQueue(itemId, price, false);
		pendingGeSellItemId = itemId;
		pendingGeSellPrice  = price;
		pendingGeSellName   = name;
		notifier.notify("Open GE \u2192 click a sell slot \u2192 select " + name + " from inventory \u2014 use the 07Flip overlay to set the price");
	}

	// -------------------------------------------------------------------------
	// Overlay queue helpers \u2014 read by GePriceOverlay and O7FlipOverlay
	// -------------------------------------------------------------------------

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

	/**
	 * Posts a price freeze for the given item using the best rec_buy / rec_sell
	 * pair we have at hand. Tries the loaded Flips list first; if the item
	 * isn't there, falls back to {@code /recommended-prices}. Best-effort —
	 * any failure is logged at warn level by the API client. No-op when the
	 * user has no API key (freeze is account-scoped).
	 */
	private void freezeFromTrackedOrFetch(int itemId)
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(() ->
		{
			for (FlipItem f : lastFlips)
			{
				if (f.itemId == itemId && f.recBuyPrice != null && f.recSellPrice != null)
				{
					freezeAndCache(itemId, f.recBuyPrice, f.recSellPrice);
					return;
				}
			}
			// Not in Flips list — try the cached /recommended-prices, which the
			// GE overlay already populates on hover for arbitrary items. Triggers
			// an async fetch if cold; we'll just miss the freeze for this attempt.
			com.o7flip.model.RecommendedPrices rp = getRecommendedPrices(itemId);
			if (rp != null && rp.recBuyPrice != null && rp.recSellPrice != null)
			{
				freezeAndCache(itemId, rp.recBuyPrice, rp.recSellPrice);
			}
			else
			{
				log.debug("[07Flip] Skipping freeze for item {} — no rec prices available", itemId);
			}
		});
	}

	private void freezeAndCache(int itemId, long recBuy, long recSell)
	{
		// Cache immediately so the GE overlay can use the frozen sell without
		// waiting for the server round trip. If the POST eventually fails the
		// local cache stays — the next sell still gets the right number, and
		// the server miss matters only across plugin restarts.
		frozenSellByItemId.put(itemId, recSell);
		apiClient.postFreeze(itemId, recBuy, recSell, null);
	}

	/** Returns the locally-tracked frozen sell price for an item, or null if none. */
	public Long getFrozenSell(int itemId)
	{
		return frozenSellByItemId.get(itemId);
	}

	/**
	 * Picks the best sell price the plugin knows of for an item, used by the
	 * implicit-sell auto-fill when the user clicks an inventory item directly
	 * (no panel right-click). Order of precedence:
	 * <ol>
	 *   <li>{@code max(frozen, liveRec)} — frozen preserves the projected
	 *       margin from when the buy was placed; live rec captures any upward
	 *       drift so the user takes the better number.</li>
	 *   <li>Live rec alone, if no freeze exists.</li>
	 *   <li>Frozen alone, if live rec is missing.</li>
	 *   <li>-1 — nothing recommended; the user types their own price.</li>
	 * </ol>
	 */
	long computeAutoSellPrice(int itemId)
	{
		Long frozen = frozenSellByItemId.get(itemId);
		Long live   = lookupLiveRecSell(itemId);
		long best   = -1L;
		if (frozen != null && frozen > 0)        best = frozen;
		if (live   != null && live   > 0 && live > best) best = live;
		return best;
	}

	private Long lookupLiveRecSell(int itemId)
	{
		// First check the Flips list — cheapest source, already on-hand.
		for (FlipItem f : lastFlips)
		{
			if (f.itemId == itemId && f.recSellPrice != null && f.recSellPrice > 0)
			{
				return f.recSellPrice;
			}
		}
		// Fall back to the /recommended-prices cache populated by the GE overlay.
		com.o7flip.model.RecommendedPrices rp = recPriceCache.get(itemId);
		if (rp != null && rp.recSellPrice != null && rp.recSellPrice > 0)
		{
			return rp.recSellPrice;
		}
		return null;
	}

	/**
	 * On-demand fetch for the implicit-sell flow. Delegates to the shared
	 * rec-prices fetcher whose completion callback now also drives the
	 * sell-arming check, so this single call kicks off (or piggybacks on)
	 * the fetch and ensures the highlight + auto-fill arm once the response
	 * lands — including when {@link GePriceOverlay} already triggered the
	 * same fetch via its per-frame cache check.
	 */
	private void kickoffSellAutoFillFetch(int itemId)
	{
		getRecommendedPrices(itemId);
	}

	/**
	 * Called on the client thread when a rec-prices fetch returns. Re-validates
	 * that the user is still on a sell setup for the same item before arming
	 * {@link #pendingGeInputPrice} — guards against the user backing out,
	 * switching items, or starting a buy in the interim.
	 *
	 * Uses the same ground-truth checks as {@link #detectAndArmSellSetup}:
	 * setup widget visible + offer-type varbit == sell + item-id match.
	 */
	private void armSellPriceIfStillRelevant(int itemId)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (!isSellSetupVisible(setup))
		{
			// Setup closed, hidden, or showing a buy screen — never overwrite a
			// buy price.
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

	/**
	 * Fires when a sell either fully consumes the open position for an item
	 * (good — the flip cycle is done) or is recorded without one ever having
	 * existed (no-op). Posts the server-side unfreeze and clears the local cache.
	 * Best-effort — server failures are logged, not propagated.
	 */
	private void unfreezeIfPositionClosed(int itemId)
	{
		Long cached = frozenSellByItemId.get(itemId);
		if (cached == null)
		{
			return;
		}
		// Recompute FIFO state — if the open position for this item is gone
		// (qty = 0 means every buy lot matched into a sell), the flip cycle
		// has completed and the freeze can be cleared.
		com.o7flip.util.ProfitCalculator.Result r = com.o7flip.util.ProfitCalculator.compute(tradeHistory);
		com.o7flip.util.ProfitCalculator.OpenPosition pos = r.openPositions.get(itemId);
		if (pos != null && pos.remainingQty > 0)
		{
			// Partial sell — leave the freeze in place; remaining qty still
			// flips with the same target.
			return;
		}
		frozenSellByItemId.remove(itemId);
		apiClient.postUnfreeze(itemId, null);
	}

	/** True when a panel right-click is still awaiting the user picking a slot. */
	public boolean hasOverlayQueue()
	{
		return overlayQueueItemId != -1
			&& System.currentTimeMillis() < overlayQueueExpiresAt;
	}

	/** Direction of the currently-queued panel action. Only meaningful when {@link #hasOverlayQueue()}. */
	public boolean overlayQueueIsBuy()
	{
		return overlayQueueIsBuy;
	}

	/** Returns the queued price for the given (itemId, direction) pair, or -1 if none. */
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

		// Loaded as a relative path (not "/icon.png") so the resource is resolved
		// from this class's own package — src/main/resources/com/o7flip/icon.png —
		// not from the classpath root. Avoids collisions with other plugins that
		// also ship an icon.png at the root of their jar.
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
		loadBlocklist();
		loadSlotRecordedFills();

		executor = Executors.newSingleThreadScheduledExecutor();
		fetchAuthStatus();
		// Re-check auth periodically so subscription upgrades take effect without a
		// client restart, and so transient 5xx at startup self-heal within minutes
		// rather than blocking premium tabs for the whole session.
		authRefreshTask = executor.scheduleAtFixedRate(
			this::fetchAuthStatus, 15, 15, TimeUnit.MINUTES);
		executor.execute(() -> fetchAll(true)); // forced — panel not yet visible at startup
		executor.execute(this::doSyncTrackerHistory);
		executor.execute(this::doFetchTrackerStats);
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
		if (authRefreshTask != null)
		{
			authRefreshTask.cancel(true);
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
		// Keep activeOffers in sync with the client's view of the GE.
		// GrandExchangeOfferChanged only fires on state *transitions*, so any
		// offer placed before the plugin loaded (e.g. user logged in with
		// offers already running) is invisible to the event-driven path.
		// Game-tick polling closes that gap — cheap snapshot, hash-compared.
		syncActiveOffersFromClient();

		// Mirror the buy flow's deterministic queue, but for sells the trigger
		// is "sell setup screen is visible" — there's no panel right-click to
		// arm an explicit queue. Idempotent per-setup-instance.
		detectAndArmSellSetup();

		if (pendingGeBuyItemId == -1)
		{
			return;
		}
		Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
		if (offerContainer == null || offerContainer.isHidden())
		{
			return;
		}
		// Only reset the pending queue when fillGeBuyOffer actually types.
		// The search-input widget only appears AFTER the user clicks an empty
		// buy slot — until then fillGeBuyOffer returns false and we leave the
		// queue armed for the next tick. (Previously we also bailed when the
		// Set-up-offer screen was visible, but that screen IS where the search
		// chatbox lives, so the bail prevented every successful fire.)
		if (fillGeBuyOffer(pendingGeBuyItemId, pendingGeBuyPrice, pendingGeBuyName))
		{
			pendingGeBuyItemId = -1;
			pendingGeBuyPrice  = -1;
			pendingGeBuyName   = null;
		}
	}

	// GE search mode integer used by MESLAYERMODE to indicate an active GE item search.
	private static final int GE_SEARCH_MODE = 14;

	/**
	 * @return true when the search was actually fired into the chatbox.
	 *         False means the widget wasn't ready yet — callers should
	 *         leave any pending-queue state in place so the next opportunity
	 *         (game tick after the user clicks an empty buy slot) can retry.
	 */
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
		// Pre-fill the search text, then trigger the search by running the chatbox
		// input widget's own key-listener script — the same mechanism GE Filters uses.
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, name);
		client.setVarcIntValue(VarClientID.MESLAYERMODE, GE_SEARCH_MODE);
		// Store the price + itemId so onScriptPostFired(GE_OFFERS_SETUP_BUILD) can arm the
		// highlight only if the user ends up selecting the item we searched for. Without
		// the itemId guard, any item chosen from search would trigger the highlight.
		pendingGeSetPrice  = price;
		pendingGeSetItemId = itemId;
		client.runScript(scriptArgs);
		return true;
	}

	// Script ID 108 fires when the GE price chatbox input opens (after clicking "Enter price").
	private static final int SCRIPT_CHATBOX_INPUT_OPEN = 108;

	/**
	 * Game-tick poller for the implicit-sell auto-fill. Mirrors the buy flow's
	 * deterministic queue: "if a sell setup screen is visible for an item we
	 * haven't already armed, arm it."
	 *
	 * Why this is more reliable than reacting to {@code GE_OFFERS_SETUP_BUILD}:
	 * <ul>
	 *   <li>SETUP_BUILD fires multiple times during a single offer screen and
	 *       sometimes not at all on inventory-click sells.</li>
	 *   <li>{@code TRADINGPOST_SEARCH} can hold a stale value from an earlier
	 *       GE search, so it's not a trustworthy buy/sell discriminator.</li>
	 *   <li>The setup widget itself is the ground truth — if it's visible and
	 *       the offer-type varbit says sell, this is a sell setup, full stop.</li>
	 * </ul>
	 *
	 * Idempotency: {@link #sellSetupArmedItemId} latches the current item so we
	 * only arm once per setup instance. The latch clears when the setup widget
	 * closes, letting the next sell re-arm.
	 *
	 * Must run on the game thread.
	 */
	private void detectAndArmSellSetup()
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			// Setup closed — reset latch so the next sell setup arms fresh.
			sellSetupArmedItemId = -1;
			return;
		}
		// The setup widget has a static child whose text is either "Sell offer"
		// or "Buy offer" — this is the ground truth for which side the screen
		// represents. We can't trust GE_NEWOFFER_TYPE varbit (semantics aren't
		// what existing code assumed) or TRADINGPOST_SEARCH (stale values).
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
		// Latch immediately so we don't re-fetch every tick while the response
		// is in flight. If the fetch fails we accept the user typing manually.
		sellSetupArmedItemId = itemId;

		long auto = computeAutoSellPrice(itemId);
		if (auto > 0)
		{
			pendingGeInputPrice = auto;
			log.debug("[07Flip] sell-setup detector: armed sell price {} for itemId {}", auto, itemId);
			return;
		}
		// No cached recommendation yet — trigger the shared rec-prices fetcher.
		// Its completion callback runs {@code armSellPriceIfStillRelevant}
		// which arms pendingGeInputPrice once the response lands.
		log.debug("[07Flip] sell-setup detector: no cached rec for itemId {}, kicking off fetch", itemId);
		getRecommendedPrices(itemId);
	}

	/**
	 * Ground-truth check for "this setup screen is a sell offer, not a buy".
	 * Scans the setup widget's child text labels for the string "sell offer"
	 * (case-insensitive) which Jagex renders at the top-left of the sell setup
	 * screen. Buy setups render "Buy offer" in the same spot.
	 *
	 * Trusted because:
	 * <ul>
	 *   <li>The text comes from the actual rendered screen, not a varbit/varp
	 *       whose semantics we can't probe without breakage.</li>
	 *   <li>Both buy and sell go through the same setup interface, so widget
	 *       text is the only thing that actually changes between them.</li>
	 * </ul>
	 */
	private static boolean isSellSetupVisible(Widget setup)
	{
		if (setup == null || setup.isHidden())
		{
			return false;
		}
		return widgetTreeHasText(setup, "sell offer");
	}

	/** Case-insensitive recursive text search over a widget's child tree. */
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

	/**
	 * Scans the GE setup widget tree for the item icon and returns its id.
	 * Used when the user reaches the sell setup screen via the inventory-click
	 * path, where {@code TRADINGPOST_SEARCH} is never set. Mirrors the lookup
	 * that {@link GePriceOverlay#resolveCurrentItemId} runs for the same
	 * reason — keeping them in sync prevents one surface from working while
	 * the other silently no-ops.
	 */
	private int resolveItemIdFromSetupWidget()
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			return -1;
		}
		Widget[] dyn = setup.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget w : dyn)
			{
				if (w == null) continue;
				int id = w.getItemId();
				if (id > 0) return id;
			}
		}
		Widget[] stat = setup.getStaticChildren();
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
		// Phase 2: item was selected in GE search — highlight the "Enter price" button.
		if (event.getScriptId() == ScriptID.GE_OFFERS_SETUP_BUILD)
		{
			// Once the user has reached any setup screen, the empty-slot guidance hints
			// have served their purpose. Clear the queue so the cyan boxes don't linger
			// after the user backs out without placing the offer.
			clearOverlayQueue();

			// TRADINGPOST_SEARCH is only set when the item was picked from the
			// GE search interface (the buy flow). For inventory-click sells the
			// user never touches search, so the varp stays 0. We keep the
			// original varp value as a discriminator between flows further
			// down, and fall back to scanning the setup widget for the actual
			// item id when search wasn't used.
			int searchedItemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
			int currentItemId = searchedItemId > 0 ? searchedItemId : resolveItemIdFromSetupWidget();
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

			// The implicit sell-setup flow no longer runs here — SETUP_BUILD
			// turned out to be too unreliable as a sell trigger (fires multiple
			// times for buys, sometimes not at all for inventory-click sells).
			// {@link #detectAndArmSellSetup} polls the setup widget on every
			// game tick instead, which is the ground truth for "sell screen
			// open right now" regardless of script-event quirks.
			//
			// BUT: we still use SETUP_BUILD as an EARLY signal to kick off the
			// rec-prices fetch ~600ms ahead of the game-tick detector. The
			// fetch path is idempotent (in-flight tracking) so triggering it
			// twice is harmless. This makes the "..." auto-fill feel reliable
			// instead of inconsistent — the fetch is more likely to have
			// landed by the time the user clicks "...".
			Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
			if (setup != null && !setup.isHidden() && isSellSetupVisible(setup))
			{
				int sellItemId = resolveItemIdFromSetupWidget();
				if (sellItemId > 0 && computeAutoSellPrice(sellItemId) <= 0)
				{
					getRecommendedPrices(sellItemId);
				}
			}

			if (price != -1)
			{
				pendingGeInputPrice = price;
			}

			return;
		}

		// Phase 3: chatbox input opened. Auto-fill the custom price input if one
		// has been armed by the right-click queue or the GePriceOverlay menu.
		if (event.getScriptId() == SCRIPT_CHATBOX_INPUT_OPEN)
		{
			Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
			if (setup == null || setup.isHidden())
			{
				pendingGeInputPrice = -1;
				return;
			}

			// Last-ditch arm: if nothing has been queued for input (e.g. the
			// user clicked "..." faster than the game-tick detector could fire,
			// or the rec-prices fetch landed but hit the latch and didn't make
			// it through), try to compute a sell price right now from the cache.
			// This is what makes the "..." auto-fill feel reliable instead of
			// hit-or-miss.
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

	// -------------------------------------------------------------------------
	// Custom price input — sets the chatbox text + Jagex's MESLAYERINPUT var
	// so the value is committed when the user presses Enter or clicks Confirm.
	// -------------------------------------------------------------------------

	private void autoFillPriceInput(long price)
	{
		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (input == null)
		{
			return;
		}
		input.setText(price + "*");
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(price));
		// Arm the Confirm-button highlight for the next 3 seconds so the user
		// can see exactly which button completes the auto-filled offer.
		confirmHighlightUntilMs = System.currentTimeMillis() + 3000L;
	}

	// -------------------------------------------------------------------------
	// GePriceOverlay action — right-click "Set <label> (<price> gp)"
	// -------------------------------------------------------------------------

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

	/**
	 * Sets the GE custom price input to {@code price}. If the chatbox is already
	 * open, fills directly. Otherwise arms pendingGeInputPrice so the chatbox-open
	 * handler will fill it once the user clicks "Enter price" themselves
	 * (O7FlipOverlay highlights the button in yellow as a hint).
	 */
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
		String key = event.getKey();

		// Re-check auth only when the API key itself changes.
		if ("apiKey".equals(key))
		{
			executor.execute(this::fetchAuthStatus);
			return;
		}
		// Re-fetch repair costs when smithing level changes.
		if ("smithingLevel".equals(key))
		{
			executor.execute(this::fetchSlow);
			return;
		}
		// One-shot trigger for the tab reorder dialog.
		if ("openTabReorderDialog".equals(key) && Boolean.parseBoolean(event.getNewValue()))
		{
			configManager.setConfiguration("o7flip", "openTabReorderDialog", false);
			SwingUtilities.invokeLater(this::openTabReorderDialog);
			return;
		}
		// Only rebuild the entire tab structure when a key that actually
		// affects which tabs are shown (or in what order) changes. The
		// previous catchall rebuilt on every o7flip config write — including
		// the very chatty tradeHistory, lastTrackerSync, and blocklistItemIds
		// keys — which clobbered any in-progress UI state (filters, scroll
		// position, dialog popups) every time a trade synced or got recorded.
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
			case "showAlerts":
			case "showMoon":
			case "showBarrows":
			case "showDecant":
			case "showMyFlips":
			case "tabOrder":
				return true;
			default:
				return false;
		}
	}

	/** Opens the reorder dialog. Called by the panel header's reorder button. */
	public void openTabReorderDialog()
	{
		java.util.List<String> current = panel.resolveTabOrder();
		com.o7flip.ui.TabOrderDialog.show(panel, current, O7FlipPanel.DEFAULT_TAB_ORDER, ordered ->
		{
			String csv = String.join(",", ordered);
			configManager.setConfiguration("o7flip", "tabOrder", csv);
			panel.rebuildTabs();
		});
	}

	// -------------------------------------------------------------------------
	// Auth
	// -------------------------------------------------------------------------

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
			status -> SwingUtilities.invokeLater(() -> panel.updateAuthStatus(status.authenticated, status.premium)),
			() ->
			{
				// 503 / network failure — server is likely warming up after a deploy
				// (server returns 503 with a ~60s startup guard). Schedule one quick
				// retry so a user who happens to launch during a deploy doesn't have
				// to wait for the next 15-min periodic poll. Don't recurse beyond a
				// single retry — the periodic poll handles longer outages.
				if (!isRetry && executor != null && !executor.isShutdown())
				{
					executor.schedule(() -> fetchAuthStatusInternal(true), 60, TimeUnit.SECONDS);
				}
			}
		);
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

		// Flips intentionally excluded from the v2/bundle request: the bundle
		// is not honouring the sort=flip07Score parameter (verified via curl —
		// returns null-score items first instead of sorted desc), while the
		// standalone /flips endpoint sorts correctly. Going through the bundle
		// would land an unsorted list on every periodic refresh and overwrite
		// the correctly-sorted result of any user-driven filter change.
		// fetchFlipsAtPage is invoked below alongside the bundle so the user
		// still gets a periodic refresh.

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

		// Dumps: when the source toggle is on the bot-dumps feed we fetch
		// it via a separate /bot-dumps call below the bundle, so skip the
		// "dumps" bundle section entirely in that mode.
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

		if (config.showAlerts())
		{
			JsonObject p = new JsonObject();
			// Pagination dropped per the redesign — fetch up to 200 in one shot,
			// filter pending vs successful client-side. Free users still only
			// receive successful alerts (server-enforced).
			p.addProperty("limit", 200);
			p.addProperty("status", "all");
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
		final int dumpsPage   = panel.getDumpsPage();

		apiClient.fetchBundle(
			sections,
			// Flips intentionally null — fetched separately below to use the
			// /flips endpoint, which honours sort=flip07Score correctly.
			null,
			config.showSpikes() ? (items, total) ->
			{
				lastSpikes = items;
				rebuildTrackedItems();
				SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, spikesPage));
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
				SwingUtilities.invokeLater(() -> panel.updateAlerts(items));
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

		// Flips: standalone /flips while the v2/bundle ignores sort.
		if (config.showFlips())
		{
			fetchFlipsAtPage(flipsPage);
		}

		// Bot-dumps lives on a dedicated endpoint outside the bundle. When
		// the Dumps tab is in bot mode, fire the additional fetch in parallel.
		if (config.showDumps() && panel.dumpsUsesBotEndpoint())
		{
			final int botDumpsPage = panel.getDumpsPage();
			apiClient.fetchBotDumps(
				panel.getDumpsSortKey(),
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				botDumpsPage,
				(items, total) ->
				{
					lastDumps = items;
					rebuildTrackedItems();
					SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, botDumpsPage));
				});
		}
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
			null, null, null, null,
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
		inventoryCoins   = coins;
	}

	// -------------------------------------------------------------------------
	// GE offer tracking — keeps activeOffers in sync and records completions
	// -------------------------------------------------------------------------

	/** Hash of the last polled offer state — used to skip UI updates when nothing changed. */
	private long lastActiveOffersHash = 0L;

	/**
	 * Polls the client's authoritative GE state and updates {@link #activeOffers}
	 * if anything has changed. Necessary because {@link GrandExchangeOfferChanged}
	 * only fires on transitions — offers placed before the plugin loaded (or
	 * before login) don't get events. Cheap: 8-slot array read + 8 ints hashed.
	 *
	 * Game-thread only — must run during {@code onGameTick} or via
	 * {@code clientThread.invoke}. Reading {@code client.getGrandExchangeOffers()}
	 * from any other thread is a data race.
	 */
	private void syncActiveOffersFromClient()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}

		// Build a fresh map of non-empty slots, plus a hash that captures
		// every field the UI cares about (slot, itemId, qtySold, total, state).
		// If the hash matches what we cached last tick, nothing rendered
		// would change — skip the EDT trip entirely.
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
			// Catch partial fills that happen between GrandExchangeOfferChanged
			// events. Without this, a buy that fills 2 of 6 and then gets sold
			// before completing would show as a phantom flip in My Trades —
			// the buy isn't in tradeHistory because BOUGHT hasn't fired yet.
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

		final List<TradeRecord> snap = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));
	}

	/**
	 * Resolves an item name via {@code Client.getItemDefinition} (legal only
	 * on the game thread) and packages the offer into an EDT-safe snapshot.
	 * Always called from the game thread — either {@link #onGameTick} polling
	 * or the {@code GrandExchangeOfferChanged} event handler.
	 */
	private com.o7flip.model.ActiveOfferSnapshot snapshot(int slot, GrandExchangeOffer offer)
	{
		String name = "Item " + offer.getItemId();
		try
		{
			name = client.getItemDefinition(offer.getItemId()).getName();
		}
		catch (Exception ignored)
		{
			// Cache miss / unknown id — keep the placeholder.
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

		// Keep activeOffers map in sync — snapshot the offer on the game
		// thread (this @Subscribe handler runs there) so the panel can
		// render it from the EDT without crossing thread boundaries.
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

		// Refresh My Trades panel when the user is on the Active sort —
		// progress bars and qty-filled numbers update live as offers move.
		// Cheap because renderMyFlips() is a no-op for tabs other than Active.
		final List<TradeRecord> snap = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));

		// Clear the overlay queue once the queued offer is actually placed —
		// stops the empty-slot hints from continuing to cyan-flash.
		if ((state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING)
			&& offer.getItemId() == overlayQueueItemId)
		{
			clearOverlayQueue();
		}

		// Record any newly-filled qty since we last looked at this slot. This
		// catches BOUGHT / SOLD transitions, partial cancellations, AND any
		// in-flight fills that happened between events — the latter is what
		// makes a sell of a partially-filled buy match against an in-progress
		// buy (instead of producing a phantom flip with no buyTotal).
		if (state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.SELLING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_BUY
			|| state == GrandExchangeOfferState.CANCELLED_SELL)
		{
			recordIfNewFills(offer, slot);
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

	/**
	 * Compares the offer's cumulative {@code quantitySold} / {@code spent}
	 * against what we've previously recorded for this slot and, if there's a
	 * positive delta, appends a {@link TradeRecord} for that delta.
	 *
	 * Treats the trade as {@code partial=true} while the offer is still
	 * BUYING/SELLING or was cancelled mid-flight, and {@code partial=false}
	 * once it transitions to BOUGHT/SOLD. Either way the cost basis ends up
	 * in {@link #tradeHistory} promptly so the FIFO matcher can pair it with
	 * sells the moment they complete.
	 *
	 * Idempotent — if no new fills happened since last call, this is a no-op.
	 * Game-thread only.
	 */
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

		// Slot reused for a new offer — cumulative dropped below recorded
		// state. Reset to zero so the new offer starts fresh, with a fresh
		// offerInstanceId so the new offer's fills don't merge into the
		// previous offer's TradeRecord row.
		if (currentQty < prevQty)
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

		// On first observation, check if a legacy partial row in tradeHistory
		// already represents some or all of these fills (written by an older
		// version of the plugin without offerInstanceId). Adopt the legacy
		// row by stamping it with our id, and reduce the delta to only the
		// NEW fills not yet captured. Without this, the legacy row + the new
		// recordTrade call would double-count the same fills.
		if (firstObservation)
		{
			long fallbackPriceEach = deltaQty > 0 ? deltaGp / deltaQty : offer.getPrice();
			int legacyIdx = findClaimableLegacyOfferRow(tradeHistory, offer.getItemId(), isBuy, fallbackPriceEach);
			if (legacyIdx >= 0)
			{
				TradeRecord legacy = tradeHistory.get(legacyIdx);
				// Stamp the legacy with our offerInstanceId so recordTrade
				// merges subsequent fills into it via the exact-match path.
				stampLegacyWithOfferInstanceId(legacyIdx, offerInstanceId);

				// Account for the qty/gp the legacy already captured. If the
				// legacy holds >= the current cumulative, there's nothing new
				// to record — just align slotRecordedFills with what's
				// already in tradeHistory and return.
				int  legacyQty = legacy.quantity;
				long legacyGp  = legacy.totalGp;
				if (legacyQty >= currentQty)
				{
					slotRecordedFills.put(slot, new long[]{legacyQty, legacyGp, offerInstanceId});
					saveSlotRecordedFills();
					return;
				}
				deltaQty = currentQty - legacyQty;
				deltaGp  = currentGp  - legacyGp;
				// Initial-observation back-dating already happened against
				// existing trade history; the legacy row is now stamped, so
				// further fills use current-time and merge by id.
				firstObservation = false;
			}
		}

		// The very first time we observe a slot, the fills we're recording
		// happened BEFORE the plugin started tracking this offer — which
		// might be before existing trades of the same item are sitting in
		// tradeHistory. Back-date the timestamp so the FIFO matcher sees
		// this buy first and can pair earlier sells with it. Without this,
		// a sell of items from an already-running buy offer becomes a
		// phantom flip (sell sorted before buy, queue empty when consumed).
		long timestamp = firstObservation && isBuy
			? backdatedTimestampBefore(offer.getItemId())
			: System.currentTimeMillis();

		recordTrade(offer, isBuy, partial, deltaQty, deltaGp, timestamp, offerInstanceId);

		slotRecordedFills.put(slot, new long[]{currentQty, currentGp, offerInstanceId});
		saveSlotRecordedFills();
	}

	/**
	 * Returns a timestamp strictly older than every existing trade of {@code itemId}
	 * in {@link #tradeHistory}, so a freshly-recorded initial-observation partial
	 * buy sorts to the front of the FIFO queue. Falls back to {@code now} when
	 * there are no prior trades of the item.
	 */
	private long backdatedTimestampBefore(int itemId)
	{
		long earliest = Long.MAX_VALUE;
		for (TradeRecord t : tradeHistory)
		{
			if (t.itemId == itemId && t.timestamp < earliest)
			{
				earliest = t.timestamp;
			}
		}
		if (earliest == Long.MAX_VALUE)
		{
			return System.currentTimeMillis();
		}
		return earliest - 1000L;
	}

	/**
	 * Records an incremental fill on a GE offer. {@code deltaQty} /
	 * {@code deltaGp} represent the quantity and gp filled SINCE the last
	 * recording for this offer — not the offer's cumulative state.
	 *
	 * If a TradeRecord with the same {@code offerInstanceId} already exists
	 * in {@link #tradeHistory}, the new fill is MERGED into it (qty/gp added,
	 * partial flag updated to the latest state) rather than appended as a
	 * separate row. That keeps the user-facing trade list at one row per
	 * logical offer — the partial-fill recording is for FIFO accuracy, not
	 * something the user wants to scroll through fill-by-fill.
	 *
	 * The timestamp on a merged record is preserved at the earliest fill so
	 * the back-dated initial observation continues to sort before any sells
	 * placed after the offer started.
	 *
	 * Use {@link #recordIfNewFills} as the entry point; this method is the
	 * inner writer.
	 */
	private void recordTrade(GrandExchangeOffer offer, boolean isBuy, boolean partial,
		int deltaQty, long deltaGp, long timestamp, long offerInstanceId)
	{
		String itemName = client.getItemDefinition(offer.getItemId()).getName();
		long fallbackPriceEach = deltaQty > 0 ? deltaGp / deltaQty : offer.getPrice();
		int  totalQty = offer.getTotalQuantity();

		List<TradeRecord> updated = new ArrayList<>(tradeHistory);
		int existingIdx = findMatchingOfferRow(updated, offerInstanceId);
		// The legacy-row claim used to live here too, but it double-counted
		// fills the legacy row already captured. It's now done in
		// recordIfNewFills BEFORE recordTrade is called — by the time we
		// reach this method the delta reflects only NEW fills not yet
		// represented in tradeHistory.
		TradeRecord posted;
		if (existingIdx >= 0)
		{
			// Merge: bump qty / gp on the existing row, recompute average
			// priceEach, update the partial flag (a final fill clears it),
			// keep the earliest timestamp (preserves back-dating for FIFO).
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
			trade.timestamp       = timestamp;
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

		final List<TradeRecord> snapshot = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snapshot));

		if (!isBuy && config.showGpDropOverlay())
		{
			// GP-drop uses the just-completed fill's profit, not the merged
			// row's lifetime profit — show the delta to keep the animation
			// honest about what changed right now.
			long profit = computeProfitForFill(deltaQty, deltaGp, offer.getItemId(), timestamp);
			gpDropOverlay.queue(profit);
		}

		if (config.shareTradeData() && config.apiKey() != null && !config.apiKey().trim().isEmpty())
		{
			// Post the per-fill delta to the server, not the merged row —
			// the server tracks individual fills and dedups on its own key.
			TradeRecord fillForServer = new TradeRecord();
			fillForServer.itemId    = offer.getItemId();
			fillForServer.name      = itemName;
			fillForServer.isBuy     = isBuy;
			fillForServer.quantity  = deltaQty;
			fillForServer.totalGp   = deltaGp;
			fillForServer.priceEach = fallbackPriceEach;
			fillForServer.timestamp = timestamp;
			fillForServer.partial   = partial;
			apiClient.postTradeRecord(fillForServer, null);
		}

		// Close out the freeze when a sell fully consumes the buys for this
		// item — flip cycle done. Partial sells leave the freeze in place.
		if (!isBuy)
		{
			unfreezeIfPositionClosed(posted.itemId);
		}
	}

	/**
	 * Scans tradeHistory from the back for a record sharing this
	 * {@code offerInstanceId}. Returns -1 when no row matches (first fill of
	 * a new offer). Walks backward because the most recent record is usually
	 * the one we want to merge into, so we exit early in the common case.
	 */
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

	/**
	 * Mutates the {@link TradeRecord} at {@code idx} in {@link #tradeHistory}
	 * to carry the given {@code offerInstanceId}, so subsequent fills of the
	 * same offer merge into this row via the exact-id match path in
	 * {@link #recordTrade}. Replaces the row immutably to keep the list's
	 * unmodifiable wrapper semantics intact.
	 */
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
		stamped.offerInstanceId = offerInstanceId;
		stamped.totalQuantity   = legacy.totalQuantity;
		updated.set(idx, stamped);
		tradeHistory = Collections.unmodifiableList(updated);
		saveTradeHistory();
	}

	/**
	 * Upgrade self-heal: scans tradeHistory back-to-front for the latest
	 * partial-fill row that LOOKS like the in-progress offer we just
	 * observed — same item, same direction, same per-item price, no
	 * {@code offerInstanceId} (i.e. pre-upgrade legacy row), and still
	 * partial. Returns its index so {@link #recordIfNewFills} can stamp
	 * it with an offerInstanceId and reduce the new fill's delta to only
	 * the qty not yet captured.
	 *
	 * Conservative on purpose: walks only the tail of the list (last 16
	 * records) and stops at the first match, so we don't reach back far
	 * enough to accidentally claim genuinely separate older offers.
	 */
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

	/**
	 * Computes the FIFO-matched profit produced by a single incoming sell
	 * fill, without polluting {@link #computeProfitForSell} (which scans by
	 * timestamp and would now mis-count merged rows). Runs a one-shot FIFO
	 * over the current history with the new fill appended virtually.
	 */
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
		// Replace any merged row representing this very fill so the FIFO
		// doesn't double-count: strip out the row matching our offer.
		// Not strictly needed for the drop number but keeps the probe honest.
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

	/**
	 * Sums the FIFO-matched profit of every completed flip whose sell happened
	 * at the given timestamp. A single sell can consume multiple buy lots,
	 * producing several CompletedFlip records — they all share the same
	 * sellTimestamp and we want to add them up for the drop animation.
	 */
	private long computeProfitForSell(long sellTimestamp)
	{
		com.o7flip.util.ProfitCalculator.Result r = com.o7flip.util.ProfitCalculator.compute(tradeHistory);
		long total = 0L;
		for (com.o7flip.util.ProfitCalculator.CompletedFlip f : r.completedFlips)
		{
			if (f.sellTimestamp == sellTimestamp)
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
		configManager.unsetConfiguration("o7flip", LAST_TRACKER_SYNC_KEY);
		// Also reset the per-slot fill ledger — without this, the next time
		// the user places a fresh offer in a slot we'd compute a delta
		// against stale state from before the history wipe.
		slotRecordedFills.clear();
		configManager.unsetConfiguration("o7flip", SLOT_FILLS_KEY);
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(Collections.emptyList()));
	}

	/**
	 * Persists {@link #slotRecordedFills} as a compact CSV ({@code slot:qty:gp,...})
	 * so it survives plugin restarts. Without persistence, every plugin reload
	 * sees active offers as "first observations" and re-records their
	 * cumulative fills as new partial trades, duplicating the entry in
	 * tradeHistory.
	 */
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
			// Accept both 3-part (legacy, no offerInstanceId) and 4-part formats
			// so existing installs upgrade cleanly. Legacy entries get a fresh
			// offerInstanceId synthesised on load — won't merge with prior
			// records in tradeHistory (those are also legacy with null id),
			// but new fills of the same offer will merge correctly.
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
				// Skip malformed entries; resume with the rest.
			}
		}
	}

	// -------------------------------------------------------------------------
	// Item blocklist — IDs hidden from Flips/Dumps/Spikes/Dips/Alerts panels
	// -------------------------------------------------------------------------

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

	// -------------------------------------------------------------------------
	// Cross-device tracker sync — pulls server history and merges with local
	// -------------------------------------------------------------------------


	/**
	 * Returns the cached recommended prices for an item, or null if we
	 * haven't fetched them recently. If the cache is stale (or empty), an
	 * async fetch is fired and {@code null} is returned this call —
	 * subsequent calls will get the populated value once the network round
	 * trip completes. Safe to call from the EDT (overlay render) and from
	 * the executor thread.
	 */
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
			executor.execute(() -> apiClient.fetchRecommendedPrices(itemId, rp ->
			{
				try
				{
					if (rp != null)
					{
						recPriceCache.put(itemId, rp);
						// Any fresh rec-price arrival is a chance to arm the
						// implicit-sell auto-fill if the user is still on a
						// matching sell setup. No-op otherwise.
						clientThread.invokeLater(() -> armSellPriceIfStillRelevant(itemId));
					}
					recPriceFetchedAt.put(itemId, System.currentTimeMillis());
				}
				finally
				{
					recPriceInFlight.remove(itemId);
				}
			}));
		}
		return recPriceCache.get(itemId);
	}

	/**
	 * Returns cached item insights for the GE overlay, or null if not yet
	 * loaded. On a cache miss / stale entry, fires an async fetch — the next
	 * render frame after the response lands will get the data. Safe to call
	 * from the EDT (overlay render).
	 */
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

	/** Public entry point used by the My Trades "Sync from server" button. */
	public void syncTrackerHistory()
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(this::doSyncTrackerHistory);
		executor.execute(this::doFetchTrackerStats);
	}

	/**
	 * One-shot fetch of server-authoritative My Trades stats. Updates
	 * {@link #trackerStats} on success (or sets it null on any failure)
	 * and refreshes the panel. Safe to call from any thread; HTTP runs
	 * on OkHttp's pool, panel update is marshalled to the EDT.
	 */
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

	/**
	 * Click target for every item row across the plugin. Switches the panel to
	 * the Insights tab synchronously (so the user sees an immediate response)
	 * and kicks off the fetch on the executor. The Insights panel paints a
	 * loading state for the requested item id until the response arrives.
	 *
	 * Safe to call from the EDT — the synchronous part touches only Swing,
	 * the HTTP call is dispatched to the executor.
	 */
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
			// Late callbacks from earlier clicks shouldn't overwrite the user's
			// current selection — only apply if this is still the item the
			// panel is showing (or no selection yet).
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
				// Already known by ID — skip
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

		log.info("[07Flip] Tracker sync: +{} new, {} reconciled, total {}", added, reconciled, snapshot.size());

		final List<TradeRecord> snap = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snap));
	}

	// -------------------------------------------------------------------------
	// Page navigation (each call re-fetches that page from the server)
	// -------------------------------------------------------------------------

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
				SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, page));
			},
			upgradeUrl -> SwingUtilities.invokeLater(() -> panel.showPremiumRequiredToast(upgradeUrl))
		);
	}

	/**
	 * Returns the player's cash floored to the nearest 100,000 gp when the
	 * personalised-flips toggle is on, or 0 when the feature is disabled or
	 * the player has no coins. The bucketed value (never the exact wealth)
	 * is what we send to the server as ?cashStack=…
	 */
	private long cashStackBucketGp()
	{
		if (!config.usePersonalisedFlips() || inventoryCoins <= 0)
		{
			return 0L;
		}
		return (inventoryCoins / CASH_BUCKET) * CASH_BUCKET;
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

	/**
	 * Single source-of-truth for fetching the Dumps tab. Routes to either
	 * {@code /dumps} or {@code /bot-dumps} depending on the panel's source
	 * toggle. Both endpoints return the same DumpItem shape so the panel
	 * doesn't care which one served the data.
	 */
	private void fetchDumpsAtPage(String sort, int page)
	{
		BiConsumer<List<DumpItem>, Integer> cb = (items, total) ->
		{
			lastDumps = items;
			rebuildTrackedItems();
			SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, page));
		};
		if (panel.dumpsUsesBotEndpoint())
		{
			apiClient.fetchBotDumps(sort,
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				page, cb);
		}
		else
		{
			apiClient.fetchDumps(sort,
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				page, cb);
		}
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
