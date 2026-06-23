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
import java.util.function.BiConsumer;
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

	/** Public so overlays / row panels in other packages can read auth state via panel.isPremium(). */
	public O7FlipPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> refreshTask;
	private ScheduledFuture<?> authRefreshTask;

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

	// Buy-side counterpart: last item id we've armed the implicit-BUY auto-fill
	// for on a manually-opened buy setup (one not initiated by a panel
	// right-click). Same reset/re-arm semantics. Game-thread only.
	private int buySetupArmedItemId = -1;

	// Last item id the auto-open-Item-tab detector has already opened the
	// Insights view for. Latched per setup instance so the user can switch
	// tabs afterwards without the detector fighting them every tick; reset
	// when the setup screen closes. Game-thread only.
	private int autoOpenInsightsItemId = -1;

	// True once the GE auto-open has switched the panel to the Item tab this
	// GE session, so we know to restore the user's previous tab when the GE
	// offer screen closes. Game-thread only.
	private boolean geAutoOpenedTab = false;

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

	/**
	 * Offer epochs with a POST /tracker currently in flight. Guards against
	 * firing a second POST for the same row while the first one's response
	 * is still outstanding (terminal GE events can repeat within seconds —
	 * login replays, world hops). Entries are removed in the response
	 * callback; on success the row's {@code serverSynced} flag takes over as
	 * the permanent re-POST suppressor. Game-thread only (events record on
	 * the game thread; callbacks re-enter via {@code clientThread.invoke}).
	 */
	private final Set<Long> tradePostsInFlight = new HashSet<>();

	/** Completed trade history (oldest first). Volatile reference swap. */
	public volatile List<TradeRecord> tradeHistory = Collections.emptyList();

	/**
	 * Lifetime bond ledger backing the "Membership cost" stat. Independent
	 * of {@link #tradeHistory} so a year of heavy flipping doesn't recycle
	 * the user's bond history out from under the panel. See
	 * {@link com.o7flip.util.BondLedger} for semantics.
	 */
	public volatile com.o7flip.util.BondLedger bondLedger = com.o7flip.util.BondLedger.EMPTY;

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
	 *
	 * Each entry carries the price AND the timestamp the freeze was placed
	 * so that stale freezes (item sitting unsold past
	 * {@link O7FlipConfig#frozenSellStaleAfterHours()}) can be refreshed
	 * with the current live recommendation on read — keeps the suggested
	 * target tracking the market when the original projection becomes
	 * unattainable.
	 */
	private final java.util.concurrent.ConcurrentHashMap<Integer, FrozenSell> frozenSellByItemId
		= new java.util.concurrent.ConcurrentHashMap<>();

	/** Frozen rec_buy per item — the locked entry price, paired with {@link #frozenSellByItemId}. */
	private final java.util.concurrent.ConcurrentHashMap<Integer, Long> frozenBuyByItemId
		= new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Small immutable holder for a frozen sell-price + the moment it was
	 * stamped. Stamps live in millis to match every other timestamp in this
	 * plugin (System.currentTimeMillis).
	 */
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

	// -------------------------------------------------------------------------
	// Buy-limit cooldown tracking (local). When the user buys an item's full
	// 4-hour GE buy limit, we track when the rolling window started so the
	// Favourites tab can show a "rebuy in HH:MM" countdown and notify on reset.
	// -------------------------------------------------------------------------

	/** 4-hour GE buy-limit window. */
	private static final long BUY_LIMIT_WINDOW_MS = 4L * 60L * 60L * 1000L;
	private static final String BUY_LIMIT_WINDOWS_KEY = "buyLimitWindows";
	private static final String BUY_LIMITS_KEY = "buyLimitsByItem";

	/** Per-item rolling buy window: when it started + how much was bought. */
	private static final class BuyLimitWindow
	{
		long windowStartMs;
		int  boughtQty;
		boolean notified;   // "available again" toast already fired for this window
	}

	/** itemId → current window. Persisted so a restart mid-window keeps the timer. */
	private final java.util.concurrent.ConcurrentHashMap<Integer, BuyLimitWindow> buyLimitWindows
		= new java.util.concurrent.ConcurrentHashMap<>();

	/** itemId → GE buy limit, learned opportunistically from any FlipItem/insights we see. */
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
	/**
	 * Version stamp on the scrub pass — bumped when the dedup rules grow to
	 * cover a new failure mode (e.g. v2 added the stuck-partial-duplicate
	 * pass for offers re-observed across sessions). Users who migrated on
	 * an older version run the new scrub once when they upgrade.
	 */
	private static final String SCRUB_VERSION_KEY = "tradeHistoryScrubVersion";
	// v3: 2-row stuck-observation pairs at ≥ 48 h span also collapse —
	// covers the case where the first pass already removed older
	// duplicates and only a couple remain.
	private static final String SCRUB_VERSION_CURRENT = "3";

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

		// The panel row's price can be minutes stale: the flips list refreshes
		// every ~90s and pauses entirely while the panel is hidden, so a market
		// move between fetch and right-click fills a visibly wrong price (seen
		// live: row said 10,025,488 while the wiki low had moved to 10,350,000).
		// Re-resolve from /v2/item at click time and swap the queued price when
		// the response lands — the gap between right-clicking the panel and
		// clicking a GE slot is ample. Tier rule as everywhere: premium → rec
		// buy, free → live buy. Guards keep a late response from clobbering a
		// different item the user queued afterwards.
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
	 * Posts a price freeze for the given item using the /v2/item rec_buy /
	 * rec_sell pair — the SAME source the Item panel and auto-fill use, so the
	 * frozen margin matches everything else. Uses the cached insights when
	 * available, else fetches /v2/item and freezes when it lands. Best-effort;
	 * no-op when the user has no API key (freeze is account-scoped).
	 */
	private void freezeFromTrackedOrFetch(int itemId)
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		// Synchronous cache reads first (open item, then overlay cache).
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
		// Cold cache — fetch /v2/item and freeze when it lands.
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
		// Cache immediately so the GE overlay can use the frozen sell without
		// waiting for the server round trip. If the POST eventually fails the
		// local cache stays — the next sell still gets the right number, and
		// the server miss matters only across plugin restarts.
		frozenSellByItemId.put(itemId, new FrozenSell(recSell, System.currentTimeMillis()));
		frozenBuyByItemId.put(itemId, recBuy);
		apiClient.postFreeze(itemId, recBuy, recSell, null);
	}

	/** Locally-cached frozen rec_buy (the entry the margin was locked against), or null. */
	public Long getFrozenBuy(int itemId)
	{
		return frozenBuyByItemId.get(itemId);
	}

	// -------------------------------------------------------------------------
	// Manual price lock — Item-tab action button. Lets the user pin the current
	// 07Flip recommended prices for an item (normally frozen automatically on
	// buy) so the GE overlay keeps suggesting that target through market drift.
	// -------------------------------------------------------------------------

	/** True when this item currently has a locked (frozen) sell price. */
	public boolean isPriceLocked(int itemId)
	{
		return frozenSellByItemId.get(itemId) != null;
	}

	/**
	 * Manually lock (freeze) the recommended prices for an item. When the
	 * caller has the rec pair on hand (premium Item view) it's cached
	 * immediately so the UI reflects the lock at once; otherwise we fall back
	 * to the shared fetch-then-freeze path.
	 */
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

	/** Manually unlock (unfreeze) a previously-locked price. */
	public void unlockPrice(int itemId)
	{
		frozenSellByItemId.remove(itemId);
		frozenBuyByItemId.remove(itemId);
		apiClient.postUnfreeze(itemId, null);
	}

	// ── Buy-limit cooldown API ───────────────────────────────────────────────

	/** Accumulates a buy fill into the item's rolling 4h window. Game-thread. */
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

	/** Learns an item's buy limit from any data that carries it. */
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

	/**
	 * Remaining cooldown (ms) before the user can rebuy this item — non-zero
	 * only when the full buy limit has been hit and the 4h window is still
	 * open. 0 means buyable now (or limit/window unknown).
	 */
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

	/** Fraction [0..1] of the cooldown elapsed, for a progress bar. 1.0 = ready. */
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

	/**
	 * Game-tick check: when a hit-limit window passes 4h, fire the "available
	 * again" notification once and prune the spent window.
	 */
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
			// Only announce items the user actually maxed out this window.
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

	/**
	 * Returns the locally-tracked frozen sell price for an item, or null if
	 * none. If the freeze is older than
	 * {@link O7FlipConfig#frozenSellStaleAfterHours()} and a current live
	 * rec_sell is available, the stored value is REFRESHED to the live
	 * price (with a fresh timestamp) before being returned — so an item
	 * that's been sitting unsold for hours stops suggesting an
	 * unattainable target. If no live value is available we keep the
	 * stale frozen as a fallback rather than blank out the overlay.
	 */
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
		// Replace the stored freeze with the current live so subsequent
		// reads see a fresh target and a fresh timestamp. The server-side
		// freeze isn't re-posted here — it gets re-stamped on the next
		// actual buy of this item, which is the only moment when a
		// genuine new projected margin is being committed to.
		FrozenSell refreshed = new FrozenSell(live, System.currentTimeMillis());
		frozenSellByItemId.put(itemId, refreshed);
		return live;
	}

	/**
	 * Picks the best sell price the plugin knows of for an item, used by the
	 * implicit-sell auto-fill when the user clicks an inventory item directly
	 * (no panel right-click).
	 *
	 * Returns {@code max(frozen-or-refreshed, live)} — {@link #getFrozenSell}
	 * has already refreshed a stale freeze with the current live value, so
	 * a market drop after the buy now resolves to the live (achievable)
	 * price once the staleness threshold passes. Market rises during the
	 * wait still surface as live > frozen and win the max.
	 */
	long computeAutoSellPrice(int itemId)
	{
		// Premium → 07Flip recommended sell (plus the frozen-at-buy floor).
		// Free  → live market sell price only (rec + freeze are premium features).
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
		// Break-even floor — the critical guard. Never auto-fill a sell that
		// would lose money against the user's actual average buy cost (after the
		// 2% GE tax). This protects ALL tiers regardless of whether a freeze
		// exists, against rec/market drift, and against thin-margin items whose
		// recommended sell can itself sit below buy + tax. We'd rather the offer
		// sit unfilled than instantly sell at a loss.
		long breakEven = breakEvenSellPrice(itemId);
		if (candidate > 0 && breakEven > 0)
		{
			candidate = Math.max(candidate, breakEven);
		}
		return candidate;
	}

	/**
	 * Lowest sell price that breaks even against the user's open-position
	 * average buy cost after the 2% GE tax (capped at 5M/item). Returns -1 when
	 * there's no tracked open position for the item.
	 */
	private long breakEvenSellPrice(int itemId)
	{
		com.o7flip.util.ProfitCalculator.Result r = com.o7flip.util.ProfitCalculator.compute(tradeHistory);
		com.o7flip.util.ProfitCalculator.OpenPosition pos = r.openPositions.get(itemId);
		if (pos == null || pos.remainingQty <= 0 || pos.remainingCostBasis <= 0)
		{
			return -1L;
		}
		double avgCost = pos.remainingCostBasis / (double) pos.remainingQty;
		// Sell S nets S − min(floor(S·0.02), 5M); break-even when net ≥ avgCost.
		long uncapped = (long) Math.ceil(avgCost / 0.98);
		if (uncapped <= 250_000_000L)
		{
			return uncapped;
		}
		// Above ~250M the 2% tax is capped at 5M, so the break-even is lower.
		return (long) Math.ceil(avgCost) + 5_000_000L;
	}

	/**
	 * Live market sell price for the free-user sell auto-fill. Pulled from the
	 * loaded flips list, whose {@code sellPrice} is the live market sell on the
	 * v2 feed. Returns null when the item isn't listed — free users then type
	 * the sell price manually (we don't fetch premium rec prices for them).
	 */
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

	/**
	 * Single source of truth for an item's 07Flip rec prices: the /v2/item
	 * insights — the SAME object the Item panel renders — so the auto-fill,
	 * overlay and panel can never show different "recommended" numbers. Uses
	 * the open item's {@code currentInsights} when it matches, else the
	 * {@code getOverlayInsights} cache (which async-fetches /v2/item on a miss
	 * and re-arms the auto-fill when it lands). Returns null when not yet
	 * cached — the caller leaves the field for manual entry until it arrives.
	 */
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

	/**
	 * Best buy price for the implicit-buy auto-fill. Premium → 07Flip
	 * recommended buy; free → live market buy (wiki low). Returns -1 when
	 * nothing is known so the caller leaves the field for manual entry.
	 * Buy-side analogue of {@link #computeAutoSellPrice} (no frozen concept —
	 * freezes apply only to the sell target).
	 */
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

	/** Live market buy price (wiki low) from the loaded flips list, or null. */
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
		getOverlayInsights(itemId);
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
	 * Buy counterpart to {@link #armSellPriceIfStillRelevant}: re-arms the buy
	 * auto-fill once a rec-prices fetch lands, if the user is still on a buy
	 * setup for the same item. No-op on a sell screen, a different item, or
	 * while an explicit panel-queued buy owns the price.
	 */
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

	/**
	 * Fires when a sell either fully consumes the open position for an item
	 * (good — the flip cycle is done) or is recorded without one ever having
	 * existed (no-op). Posts the server-side unfreeze and clears the local cache.
	 * Best-effort — server failures are logged, not propagated.
	 */
	private void unfreezeIfPositionClosed(int itemId)
	{
		FrozenSell cached = frozenSellByItemId.get(itemId);
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
		frozenBuyByItemId.remove(itemId);
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
		loadBondLedger();
		loadBlocklist();
		loadSlotRecordedFills();

		// Surface 401s from /favourites to the user via a one-shot notifier
		// pointing at the key-setup step. Same channel used by other auth-
		// gated flows — keeps the failure mode visible instead of silent.
		apiClient.setOnFavouritesUnauthorized(() -> SwingUtilities.invokeLater(() ->
			notifier.notify("Your 07Flip API key was rejected. Open the plugin config and paste it again.")));

		// Replay cached data from the last session so the panel doesn't render
		// empty during the ~0.5–2s gap before the first fetchAll lands. Cheap
		// and purely client-side — no server impact.
		hydrateCachedTabs();

		executor = Executors.newSingleThreadScheduledExecutor();
		fetchAuthStatus();
		// Re-check auth periodically so subscription upgrades take effect without a
		// client restart, and so transient 5xx at startup self-heal within minutes
		// rather than blocking premium tabs for the whole session.
		authRefreshTask = executor.scheduleAtFixedRate(
			this::fetchAuthStatus, 15, 15, TimeUnit.MINUTES);
		executor.execute(() -> fetchAll(true)); // forced — panel not yet visible at startup
		executor.execute(this::doSyncTrackerHistory);
		// Push-direction sync: recovers locally-recorded trades that never
		// reached the server during the May 14 → zero-delta-fix release
		// window. Idempotent on the server side, so safe to run every
		// startup; the cost is one bulk HTTP request per session.
		executor.execute(this::doBulkSyncToServer);
		executor.execute(this::doFetchTrackerStats);
		// Cross-surface optimiser session — pull whatever was last saved on
		// the website (or from a previous plugin session) so the user sees
		// their plan immediately, without having to click Build again.
		executor.execute(this::doHydrateOptimizerSession);
		// The background session poll is gated on sidebar visibility — started in
		// onPanelShown() (the Activatable hook fired when our panel is shown) and
		// cancelled in onPanelHidden(). Kick it now only if the sidebar is already
		// open at startup; otherwise it starts the first time the user opens it.
		if (panel.isShowing())
		{
			startSessionBackgroundPoll();
		}
		// Pull the shared completed-positions history (web ↔ plugin synced store).
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

	// -------------------------------------------------------------------------
	// GE auto-fill — fires when the Grand Exchange interface opens
	// -------------------------------------------------------------------------

	// onWidgetLoaded is intentionally NOT used for GE pre-fill because clicking a buy slot
	// only toggles widget visibility within the already-loaded GRAND_EXCHANGE interface —
	// it does NOT re-fire WidgetLoaded. onGameTick polls instead.

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Arm the offline-completion reconcile on login (SYNC_CONTRACT §7). The
		// actual scan runs on the next game tick, where GE offers are reliably
		// readable; doing it here can race the client populating the offer array.
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			offlineReconcileArmed = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Keep activeOffers in sync with the client's view of the GE.
		// GrandExchangeOfferChanged only fires on state *transitions*, so any
		// offer placed before the plugin loaded (e.g. user logged in with
		// offers already running) is invisible to the event-driven path.
		// Game-tick polling closes that gap — cheap snapshot, hash-compared.
		syncActiveOffersFromClient();

		// Announce any buy-limit cooldown that just reset (cheap — only iterates
		// items currently on cooldown).
		checkBuyLimitResets();

		// Once per login, after GE offers are readable, reconcile any optimiser
		// leg whose live sell offer vanished while offline (§7 offline-sell half).
		if (offlineReconcileArmed && activeSession != null)
		{
			offlineReconcileArmed = false;
			reconcileOfflineCompletions();
		}

		// Mirror the buy flow's deterministic queue, but for sells the trigger
		// is "sell setup screen is visible" — there's no panel right-click to
		// arm an explicit queue. Idempotent per-setup-instance.
		detectAndArmSellSetup();

		// Same again for buys the user opens manually (searching the item in the
		// GE itself rather than right-clicking a panel row) — auto-fill the
		// 07Flip recommended buy so the price field isn't left on the GE default.
		detectAndArmBuySetup();

		// Auto-open the Item tab for whatever item is on the setup screen —
		// buy and sell flows alike — so the panel shows the full detail view
		// of the item the user is about to trade.
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
		}
		if (panel != null && panel.isPremium())
		{
			if (auto <= 0)
			{
				// Premium: no cached rec yet — the shared rec-prices fetcher's
				// completion callback re-arms via armSellPriceIfStillRelevant.
				log.debug("[07Flip] sell-setup detector: no cached rec for itemId {}, kicking off fetch", itemId);
				getOverlayInsights(itemId);
			}
		}
		else
		{
			// Free: the flips-list live sell (when present) can be minutes
			// stale, and unlisted items have nothing at all — refresh the live
			// sell from /v2/item and re-arm when the response lands.
			refreshFreeLiveSell(itemId);
		}
	}

	/**
	 * Click-time freshness for the FREE-tier sell auto-fill: fetches the item's
	 * current live sell price from /v2/item and re-arms {@code pendingGeInputPrice}
	 * if the sell setup is still showing the same item when the response lands.
	 * Mirrors {@link #armSellPriceIfStillRelevant}'s guards so a slow response
	 * can never overwrite a different item's (or a buy screen's) price.
	 */
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

	/**
	 * Buy-side counterpart to {@link #detectAndArmSellSetup}. Fires when the
	 * user opens a BUY setup they did <em>not</em> initiate from a panel
	 * right-click — e.g. they searched the item directly in the GE. Without
	 * this, manual buys got no auto-fill at all (only sells had a poller and
	 * only panel-queued buys armed a price), so the price field was left on
	 * Jagex's own guide price instead of the 07Flip recommended buy.
	 *
	 * Arms {@link #pendingGeInputPrice} with the recommended buy (premium) or
	 * the live market buy (free). Deliberately yields to an in-flight
	 * panel-queued buy ({@code pendingGeSetPrice}/{@code pendingGeBuyItemId}
	 * set) so the two never race on the same item — the explicit queue wins.
	 *
	 * Idempotency via {@link #buySetupArmedItemId}, mirroring the sell latch.
	 * Must run on the game thread.
	 */
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
		// An explicit panel-queued buy owns the price for its item — don't fight it.
		if (pendingGeSetPrice != -1 || pendingGeBuyItemId != -1)
		{
			return;
		}
		// Still on "Choose an item..." — placeholder icons resolve to junk ids.
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
				// No cached rec yet — the /v2/item fetch's completion callback
				// re-arms via armBuyPriceIfStillRelevant.
				getOverlayInsights(itemId);
			}
		}
		else
		{
			refreshFreeLiveBuy(itemId);
		}
	}

	/**
	 * Click-time freshness for the FREE-tier buy auto-fill: fetches the live
	 * market buy from /v2/item and re-arms {@code pendingGeInputPrice} if the
	 * buy setup still shows the same item. Mirrors {@link #refreshFreeLiveSell}.
	 */
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
				// Don't stomp a panel-queued buy that arrived meanwhile.
				if (pendingGeSetPrice != -1 || pendingGeBuyItemId != -1)
				{
					return;
				}
				pendingGeInputPrice = freshBuy;
			});
		});
	}

	/**
	 * Game-tick detector that auto-opens the Item tab for whatever item the
	 * user has on a GE offer screen. Covers three entry points:
	 * <ul>
	 *   <li>the new-offer setup screen, buy flow (item picked from search),</li>
	 *   <li>the new-offer setup screen, sell flow (item clicked from inventory),</li>
	 *   <li>the existing-offer status screen ({@code GeOffers.DETAILS}) shown
	 *       when the user clicks an already-placed buy or sell to review it.</li>
	 * </ul>
	 * Polled on the game tick rather than reacting to
	 * {@code GE_OFFERS_SETUP_BUILD} for the same reliability reasons as
	 * {@link #detectAndArmSellSetup}: the script fires multiple times for
	 * buys and sometimes not at all for inventory-click sells, while the
	 * widget state is the ground truth. The detail screen has no build script
	 * at all, so polling is the only option there.
	 *
	 * Only fires while the plugin sidebar is actually visible — the point is
	 * to enrich a panel the user is already looking at, not to yank the
	 * sidebar open mid-trade. If the user opens the panel while a GE screen is
	 * still up, the next tick picks it up.
	 *
	 * Idempotency: {@link #autoOpenInsightsItemId} latches the current item
	 * so the user can navigate to another tab afterwards without the
	 * detector dragging them back every tick. The latch clears once both GE
	 * offer screens are closed, letting the next offer re-trigger.
	 *
	 * Must run on the game thread.
	 */
	private void detectAutoOpenItemInsights()
	{
		Widget setup   = client.getWidget(InterfaceID.GeOffers.SETUP);
		Widget details = client.getWidget(InterfaceID.GeOffers.DETAILS);
		boolean setupOpen   = setup   != null && !setup.isHidden();
		boolean detailsOpen = details != null && !details.isHidden();

		if (!setupOpen && !detailsOpen)
		{
			// Both GE offer screens closed — reset latch so the next one re-fires.
			autoOpenInsightsItemId = -1;
			// Return the user to whatever tab they were on before the GE screen
			// auto-switched them to the Item tab.
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
			// Still picking an item — the setup screen renders "Choose an item..."
			// until a search result (buy) or inventory item (sell) is actually
			// selected. Firing before that resolves placeholder widget icons to
			// bogus item ids and opens the tab mid-search. The rendered text is
			// the same ground-truth approach as isSellSetupVisible.
			if (widgetTreeHasText(setup, "choose an item"))
			{
				return;
			}
			itemId = resolveItemIdFromSetupWidget();
		}
		else
		{
			// Existing offer being reviewed — read the viewed slot's item from
			// game state (the selected-slot varbit indexing the GE offer array)
			// rather than scraping the detail widget tree.
			itemId = resolveOfferStatusItemId();
		}

		if (itemId <= 0 || itemId == autoOpenInsightsItemId)
		{
			return;
		}
		// Unobtainable/placeholder ids have an item definition literally named
		// "null" — never a real selection. Don't latch, so the genuine pick a
		// tick later still fires; this also keeps junk ids from burning
		// rate-limit budget on doomed /v2/item fetches.
		String name = client.getItemDefinition(itemId).getName();
		if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
		{
			return;
		}
		autoOpenInsightsItemId = itemId;
		// The latch resets whenever both GE screens close — and a screen can
		// momentarily hide mid-interaction (e.g. the custom-price chatbox
		// opening) — so a still-open offer for the same item would otherwise
		// re-resolve and re-open here every few ticks. Each re-open runs
		// openInsights → a loading-state rebuild → fetch → render, which throws
		// the user's scroll position back to the top mid-read. If the Item tab
		// is already showing this exact item, the re-open is pure churn: skip it
		// (latch is already set above so we won't reconsider until a different
		// item appears).
		com.o7flip.model.ItemInsights shown = currentInsights;
		if (shown != null && shown.itemId == itemId)
		{
			return;
		}
		log.debug("[07Flip] GE offer screen detected — auto-opening Item tab for {} ({})", name, itemId);
		// Remember the user's current tab the first time we hijack to Item this
		// GE session, so we can restore it when the offer screen closes. Posted
		// before openInsights so the capture reads the pre-switch tab.
		if (!geAutoOpenedTab)
		{
			geAutoOpenedTab = true;
			SwingUtilities.invokeLater(() -> panel.markGeAutoOpen());
		}
		openInsights(itemId, name);
	}

	/**
	 * Item id of the existing GE offer the user is reviewing on the
	 * {@code GeOffers.DETAILS} status screen.
	 *
	 * Cross-validates two sources to avoid opening the WRONG item: the item
	 * icon actually rendered on the DETAILS screen (ground truth of what's on
	 * screen) and the {@code GE_SELECTEDSLOT} varbit indexed into the live GE
	 * offer array (authoritative game state). The varbit can lag or linger on a
	 * previously-viewed offer while a different one is on screen — which opened
	 * an unrelated active offer's item. So: if both resolve and DISAGREE, bail
	 * (-1) rather than guess; otherwise prefer the on-screen icon, falling back
	 * to the varbit slot only when no icon is found (e.g. a just-collected,
	 * now-empty slot still showing its detail panel).
	 */
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

	/**
	 * Ground-truth check for "this setup screen is a buy offer, not a sell".
	 * Inverse of {@link #isSellSetupVisible} — Jagex renders "Buy offer" at the
	 * top-left of the buy setup screen where the sell screen shows "Sell offer".
	 */
	private static boolean isBuySetupVisible(Widget setup)
	{
		if (setup == null || setup.isHidden())
		{
			return false;
		}
		return widgetTreeHasText(setup, "buy offer");
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
		return firstItemIdInWidget(client.getWidget(InterfaceID.GeOffers.SETUP));
	}

	/** First item-model id rendered in a widget's dynamic/static/grandchild tree, or -1. */
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
				//
				// SETUP_BUILD fires several times per buy, and on the first fire(s)
				// the searched-item varp and the setup widget are often not
				// populated yet, so currentItemId resolves to <= 0. We must NOT
				// consume the queued price until we have a real id to compare:
				// the old code cleared it on every fire regardless, so whenever the
				// id wasn't ready on the first fire the price was discarded before
				// the fire that could have matched it — the auto-fill then silently
				// did nothing ("sometimes works, sometimes doesn't"). Leave the
				// queue armed until an id resolves; the sell branch below already
				// works this way, which is why sells fill more reliably.
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
					getOverlayInsights(sellItemId);
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
		// User opt-out: when auto-fill is disabled the plugin never writes to the
		// GE custom price input — neither the queued buy/sell auto-fill nor the
		// overlay "Set price" menu. The overlay still shows recommended prices for
		// reference; the user types them in themselves.
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
		// One-shot trigger for the tab reorder dialog.
		if ("openTabReorderDialog".equals(key) && Boolean.parseBoolean(event.getNewValue()))
		{
			configManager.setConfiguration("o7flip", "openTabReorderDialog", false);
			SwingUtilities.invokeLater(this::openTabReorderDialog);
			return;
		}
		// Item-tab section visibility toggles ("itemTab*") — re-render the
		// loaded item in place so the change applies without re-clicking it.
		if (key.startsWith("itemTab"))
		{
			SwingUtilities.invokeLater(() -> panel.refreshInsightsSections());
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
			case "showDips":
			case "showFavourites":
			case "showHighAlch":
			case "showMyFlips":
			case "tabOrder":
			case "topRowTabs":
				return true;
			default:
				return false;
		}
	}

	/**
	 * Opens the "Customise top row tabs" picker. The bottom row (Flips,
	 * My Trades, Item, Other) is fixed; this dialog only manages the four
	 * customisable Row-1 slots — anything the user leaves out shows up
	 * inside the Other tab on Row 2.
	 */
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
			status -> SwingUtilities.invokeLater(() ->
			{
				panel.updateAuthStatus(status.authenticated, status.premium);
				// The startup session hydrate races this auth check: it bails on
				// isPremium() before the tier is known and never retried, leaving
				// activeSession null — so fills landing before the Plan tab was
				// first opened attributed to nothing. Re-pull once premium is
				// actually confirmed (no-op when a session is already loaded).
				if (status.premium && activeSession == null
					&& executor != null && !executor.isShutdown())
				{
					executor.execute(this::doHydrateOptimizerSession);
				}
			}),
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
			// Dumps no longer piggyback on the bundle — tier_totals (v5)
			// only ship from the dedicated /dumps endpoint, so a fetchDumps
			// call below replaces the bundle's dumps section.
			null,
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

		// Dips lives on its own /dips endpoint outside the bundle.
		if (config.showDips())
		{
			fetchDipsAtPage(panel.getDipsSortKey(), panel.getDipsPage());
		}

		// Dumps fires through the dedicated endpoint so tier_totals come
		// back in the response. The bot-dumps branch below still applies
		// when the user has switched the source toggle in the panel.
		if (config.showDumps() && !panel.dumpsUsesBotEndpoint())
		{
			fetchDumpsAtPage(panel.getDumpsSortKey(), panel.getDumpsPage());
		}

		// High Alch, Favourites — all on their own /runelite
		// endpoints, polled at the same 60s cadence.
		if (config.showHighAlch())
		{
			fetchHighAlchAtPage(panel.getHighAlchSortKey(), panel.getHighAlchPage());
		}
		if (config.showFavourites() && hasApiKey())
		{
			apiClient.fetchFavourites(items ->
			{
				if (items != null && !items.isEmpty()) saveCache("favourites", items);
				pushFavouritesToPanel(items);
			});
		}
		// Bot-dumps lives on a dedicated endpoint outside the bundle. When
		// the Dumps tab is in bot mode, fire the additional fetch in parallel.
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

	// -------------------------------------------------------------------------
	// Tab data cache — persists each tab's last fetched payload to RuneLite's
	// config store so the next plugin launch shows cached data immediately
	// instead of empty states. Net server-load impact is zero (writes are
	// purely client-side) and reads on startup avoid the visual gap between
	// "panel opens" and "first periodic poll lands".
	// -------------------------------------------------------------------------

	/** Per-cache-key cap. Skip writes that would exceed this so a runaway
	 *  payload can't bloat settings.properties indefinitely. */
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

	/**
	 * Replays the last-known data from each tab's cache into the panel so the
	 * UI doesn't render empty before the periodic poll lands. Called once
	 * from startUp after the panel has been constructed.
	 *
	 * Each branch is best-effort: malformed JSON, missing fields, or a class
	 * shape that's evolved since the cache was written all result in a null
	 * load and the tab simply waits for fresh data.
	 */
	private void hydrateCachedTabs()
	{
		if (panel == null) return;

		// Dumps — full Response wrapper (tier totals + items)
		com.o7flip.model.DumpItem.Response cd = loadCache("dumps", com.o7flip.model.DumpItem.Response.class);
		if (cd != null && cd.items != null && !cd.items.isEmpty())
		{
			lastDumps = cd.items;
			final com.o7flip.model.DumpItem.Response snap = cd;
			SwingUtilities.invokeLater(() -> panel.updateDumps(snap, 0));
		}

		// Dips
		List<com.o7flip.model.DipItem> cdips = loadListCache("dips", com.o7flip.model.DipItem.class);
		if (cdips != null && !cdips.isEmpty())
		{
			final List<com.o7flip.model.DipItem> snap = cdips;
			SwingUtilities.invokeLater(() -> panel.updateDips(snap, snap.size(), 0));
		}

		// High Alch
		com.o7flip.model.HighAlchItem.Response ca = loadCache("highAlch", com.o7flip.model.HighAlchItem.Response.class);
		if (ca != null && ca.items != null && !ca.items.isEmpty())
		{
			final com.o7flip.model.HighAlchItem.Response snap = ca;
			SwingUtilities.invokeLater(() -> panel.updateHighAlch(snap, 0));
		}

		// Buy-limit cooldown state (windows still within their 4h life + the
		// learned item→limit map).
		loadBuyLimitState();

		// Favourites — requires the IDs set to be rebuilt for the star icon
		List<FlipItem> cf = loadListCache("favourites", FlipItem.class);
		if (cf != null && !cf.isEmpty())
		{
			pushFavouritesToPanel(cf);
		}

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
		long previousCoins = inventoryCoins;
		inventoryCoins     = coins;

		// Keep the panel's Capital readout in sync when Auto mode is on. We
		// don't refetch here — Auto-derived capital changes naturally as the
		// 60-second poll lifts the new value — this just refreshes the
		// displayed number so the user sees their inventory update live.
		if (panel != null && previousCoins != coins)
		{
			SwingUtilities.invokeLater(panel::onInventoryCoinsChanged);
		}
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

		// Active-offer state moved — deployed capital probably changed, so
		// refresh the capital readout and re-apply the affordability filter.
		// Cheap: no refetch, just re-render rows from in-memory data.
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

		// SYNC_CONTRACT §10 — sell-listing signal for the active plan. A SELLING
		// offer means the user has LISTED the leg's item; flag it immediately so
		// the card (and the website, via the session POST) flips from "Filled" to
		// "Selling" without waiting for the first sell fill. CANCELLED_SELL
		// un-flags, but only when no other GE slot still has a live sell for the
		// item (a 1400-unit position can be split across slots).
		if (state == GrandExchangeOfferState.SELLING)
		{
			markPlanSellListed(offer.getItemId());
		}
		else if (state == GrandExchangeOfferState.CANCELLED_SELL)
		{
			clearPlanSellListedIfNoLiveSell(offer.getItemId());
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

		// When the offer terminates (BOUGHT/SOLD) with no extra fill delta
		// since the last record, recordIfNewFills bails early and the local
		// merged row keeps {@code partial=true}. That stale flag is what the
		// dedup uses to tell a fresh terminal observation from a stale one;
		// leaving it stuck on every-already-fully-recorded offer breaks
		// duplicate detection downstream. Clear it here so the row reflects
		// the offer's actual terminal state.
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

		// Detect a reused GE slot and discard the stale per-slot baseline so
		// the new offer is recorded fresh rather than diffed against the
		// previous offer's cumulative counters. Two independent signals:
		//   1. Cumulative qty dropped below what we recorded — the obvious
		//      case, hit when we DO observe the re-list while the slot is
		//      empty or only lightly filled.
		//   2. The baseline points at a tradeHistory row for a different item
		//      or order size — catches a re-list whose intervening EMPTY
		//      transition we never saw (offer collected + re-listed across a
		//      logout, or while the plugin was unloaded). Without this, a slot
		//      re-listed to an offer that has already filled to >= the old
		//      count has its fills mis-merged into the previous offer's row,
		//      or dropped entirely when the counts coincide — making a
		//      completed trade vanish from the log, never POSTed upstream, and
		//      leaving the later sell to FIFO-match a stale lot.
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
			// No new fills since the last observation — but if the offer
			// has transitioned to a terminal state, the existing local row
			// needs (a) its partial flag flipped for BOUGHT/SOLD, and (b) a
			// server POST so the trade syncs upstream. recordTrade's tail
			// handles both for positive-delta observations, but a fast-
			// filling offer often maxes quantitySold in its last BUYING /
			// SELLING tick before the BOUGHT/SOLD event arrives — that
			// terminal event then carries delta=0 and was silently dropping
			// the server submission, leaving locally-recorded trades that
			// never made it to /api/runelite/tracker.
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

		// On first observation, check if a partial row in tradeHistory already
		// represents this offer. Two flavours:
		//   1. Legacy partial row (no offerInstanceId) — pre-upgrade data.
		//   2. Stuck partial row (has offerInstanceId) — same active offer
		//      observed in a previous session, where slotRecordedFills was
		//      lost so we'd otherwise mint a new oId and double-record.
		// In both cases we adopt the existing row by stamping it with our
		// fresh offerInstanceId and shrink the delta to just the fills not
		// yet captured. Without this, a stuck SELL 1/2 primordial offer
		// gets re-recorded as a fresh row every time the plugin restarts —
		// the on-disk symptom: 14 identical SELL 1 rows for the same offer.
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
			// SYNC_CONTRACT §5 — re-observing a TERMINAL offer (BOUGHT offer
			// sitting uncollected across a relog where the slot baseline was
			// lost) must reclaim the already-recorded row, not mint a fresh
			// one. The reclaim above only matches partial rows; without this,
			// every such re-observation appended a duplicate row with a new
			// timestamp + epoch and re-POSTed it — the production "two buy
			// records, same qty/price, fresh tradedAt" duplication.
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
					// Adopt the row's own offer epoch rather than re-stamping
					// our freshly-minted one — keeps offerInstanceId (the §2
					// leg key) and the epoch-derived tradedAt stable across
					// restarts, which is what the server's dedup keys on.
					offerInstanceId = existing.offerInstanceId;
				}
				else
				{
					// Legacy row (pre-epoch data): stamp it with our fresh
					// offerInstanceId so recordTrade merges subsequent fills
					// into it via the exact-match path.
					stampLegacyWithOfferInstanceId(existingIdx, offerInstanceId);
				}

				// Account for the qty/gp the existing row already captured.
				// If it holds >= the current cumulative, there's nothing new
				// to record — just align slotRecordedFills with what's
				// already in tradeHistory and return.
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

		// Observation time for the fill — used for slot attribution and the
		// GP-drop overlay. NOTE: this is NOT what stamps the trade row's
		// timestamp. SYNC_CONTRACT §5 requires a stable, offer-bound
		// tradedAt on every /tracker POST, so recordTrade derives the row
		// timestamp from the offer epoch (offerInstanceId / 10) — identical
		// no matter when or how often the same offer is re-observed.
		// (Earlier versions back-dated first observations to pair phantom
		// sells; that reordered FIFO and was removed — see git history.)
		long timestamp = System.currentTimeMillis();

		recordTrade(offer, isBuy, partial, deltaQty, deltaGp, timestamp, offerInstanceId);

		slotRecordedFills.put(slot, new long[]{currentQty, currentGp, offerInstanceId});
		saveSlotRecordedFills();
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
			// SYNC_CONTRACT §5 — the row timestamp (and therefore the POSTed
			// tradedAt and the dedup fingerprint) is bound to the offer epoch,
			// not the wall clock at observation. Re-observing the same offer
			// can never produce a different timestamp.
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

		// Update the lifetime bond ledger by the FILL DELTA. Using the
		// delta (not the merged row's cumulative qty/gp) keeps the count
		// correct when a single bond offer fills in multiple chunks — every
		// invocation of recordTrade represents one delta, so we apply once
		// per delta. No-op for non-bond trades.
		if (posted.itemId == com.o7flip.util.BondLedger.BOND_ITEM_ID && deltaQty > 0)
		{
			TradeRecord delta = new TradeRecord();
			delta.itemId   = posted.itemId;
			delta.isBuy    = posted.isBuy;
			delta.quantity = deltaQty;
			delta.totalGp  = deltaGp;
			updateBondLedgerFor(delta);
		}

		// Warm the rec-prices cache for this item the moment a buy fill
		// lands. The user is overwhelmingly likely to sell this item next,
		// and computeAutoSellPrice on the sell-setup screen otherwise has
		// to wait on a fresh server round trip when the item isn't in
		// lastFlips (anything outside the top-10 cheap flips list — e.g.
		// Primordial boots — falls into this gap). Async fetch; the
		// in-flight guard inside getRecommendedPrices deduplicates back-
		// to-back calls for multi-fill offers.
		if (isBuy)
		{
			getRecommendedPrices(offer.getItemId());
			recordBuyForLimit(offer.getItemId(), deltaQty, timestamp);
			// Freeze the 07Flip rec margin on the FIRST fill of a new position so
			// the sell target is locked in for ANY buy — manually-placed offers
			// included, not just panel-queued ones. Skip when already frozen so
			// later fills of the same position don't drift the frozen value.
			// (Premium-only in effect: no rec prices to freeze for free users,
			// who are still protected by the break-even floor on the sell side.)
			if (!frozenSellByItemId.containsKey(offer.getItemId()))
			{
				freezeFromTrackedOrFetch(offer.getItemId());
			}
		}

		final List<TradeRecord> snapshot = tradeHistory;
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(snapshot));

		// Adjust the user's typed Capital bankroll by this fill's cash flow.
		// Manual-mode users keep their typed total in sync with actual trades
		// (no-op for Auto / Off modes — see adjustCapitalForTrade).
		adjustCapitalForTrade(offer.getItemId(), isBuy, deltaQty, deltaGp);

		// Cross-surface optimiser session — when the fill is for an item in
		// the active plan, push a SlotFill so the website's polling can
		// surface the same fills on its end (and vice versa via mergeRemoteFills).
		long pricePer = deltaQty > 0 ? deltaGp / deltaQty : fallbackPriceEach;
		attributeTradeToActiveSlot(offer.getItemId(), deltaQty, pricePer, isBuy, timestamp, offerInstanceId);

		if (!isBuy && config.showGpDropOverlay())
		{
			// GP-drop uses the just-completed fill's profit, not the merged
			// row's lifetime profit — show the delta to keep the animation
			// honest about what changed right now.
			long profit = computeProfitForFill(deltaQty, deltaGp, offer.getItemId(), timestamp);
			gpDropOverlay.queue(profit);
		}

		// Only post to the server when this fill brings the offer to a
		// terminal state — i.e. BOUGHT/SOLD (partial=false) or one of the
		// CANCELLED states (no more fills will ever land here). Earlier
		// versions posted every partial delta, which the server stored as
		// separate rows; the next sync echoed them back and the local merged
		// row + each server-fill row both ended up in tradeHistory, doubling
		// the FIFO input and inflating Today/Worst figures. One terminal
		// post per offer is enough — fingerprint dedup on the next sync now
		// matches it against the local merged row instead of duplicating.
		net.runelite.api.GrandExchangeOfferState st = offer.getState();
		boolean terminal = !partial
			|| st == net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY
			|| st == net.runelite.api.GrandExchangeOfferState.CANCELLED_SELL;
		// SYNC_CONTRACT §5b — a fill the server has already accepted (2xx) is
		// never re-POSTed: serverSynced persists delivery, and the in-flight
		// set suppresses a second POST racing the first one's response.
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
	 * True when the per-slot fill baseline points at a DIFFERENT offer than
	 * the one currently occupying the slot — i.e. the slot was collected and
	 * re-listed without us observing the intervening EMPTY transition (a
	 * logout/relog, or the plugin being unloaded across the swap). Used by
	 * {@link #recordIfNewFills} to discard the stale baseline so the freshly
	 * re-listed offer is treated as a first observation, instead of having its
	 * cumulative fills diffed against the previous offer's counters (which
	 * under-records the new offer, or drops it entirely when the two fill
	 * counts coincide).
	 *
	 * Compares the offer's STABLE identity — item id and order size, both
	 * constant for an offer's lifetime — against the {@code tradeHistory} row
	 * the baseline's offerInstanceId points at:
	 * <ul>
	 *   <li>A {@code null} baseline row (the referenced row has already rolled
	 *       out of the 200-row window) is NOT treated as stale — we can't
	 *       prove reuse, so we preserve existing behaviour rather than risk
	 *       re-recording an in-flight offer.</li>
	 *   <li>{@code totalQuantity} is only compared when the baseline row
	 *       carries it; legacy rows predate the field and fall back to the
	 *       item-id check alone.</li>
	 * </ul>
	 * During a single offer's lifetime the baseline row always shares the
	 * offer's item id and order size, so this never fires a false reset
	 * mid-fill.
	 */
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

	/**
	 * Clears {@code partial=true} on the row for the given
	 * {@code offerInstanceId} when the offer has reached BOUGHT/SOLD. The
	 * normal merge path in {@link #recordTrade} updates the partial flag
	 * automatically, but only when a state transition arrives WITH a
	 * non-zero fill delta. Many offers — a single bond redemption is the
	 * classic case — fill entirely during the BUYING state and then go to
	 * BOUGHT with no extra delta; the merge never fires and the row stays
	 * stuck at partial=true. The dedup uses the partial flag to tell stale
	 * vs terminal observations apart, so the stuck flag has to be cleaned
	 * up here.
	 */
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

	/**
	 * Marks the local TradeRecord identified by {@code offerInstanceId} as
	 * delivered to the server ({@code serverSynced=true} — SYNC_CONTRACT
	 * §5b's permanent re-POST suppressor) and stamps the server-issued
	 * {@code trade_id} when the response carried one ({@code tradeId} is
	 * nullable: duplicates may come back without an id). Called from the
	 * POST /tracker and /tracker/bulk response handlers.
	 *
	 * No-op if the row has fallen out of the rolling window, the
	 * offerInstanceId never matched (a later merge built a new row before
	 * the POST returned), or nothing would change. Must be invoked on the
	 * client thread because tradeHistory is mutated by reassignment from a
	 * single thread.
	 */
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

	/**
	 * Companion to {@link #clearPartialFlag} for the zero-delta terminal-
	 * observation case: flips the local row's partial flag for BOUGHT/SOLD
	 * (CANCELLED rows stay partial=true to mark them as partially filled),
	 * then POSTs the finalised row to the server so the trade actually
	 * syncs upstream. Gated by {@code shareTradeData} + a non-empty API key
	 * exactly like {@link #recordTrade}'s server-submit path.
	 *
	 * Terminal GE events repeat — every login replays the slot states, so
	 * this runs many times for an offer that sits uncollected. SYNC_CONTRACT
	 * §5b: once the server has accepted the row ({@code serverSynced}), or a
	 * POST is already in flight for it, this is a local-only no-op.
	 */
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
		stamped.serverSynced    = legacy.serverSynced;
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
	 * Companion to {@link #findClaimableLegacyOfferRow} that hunts for a
	 * STILL-ACTIVE local row representing the same offer we're now
	 * observing fresh. Triggered when {@code slotRecordedFills} has no
	 * entry for the slot — typically after a plugin restart that lost the
	 * per-slot ledger but the GE offer is still partial-filled in-game.
	 *
	 * Without this, every restart re-records the same offer as a brand-new
	 * row with a fresh offerInstanceId, and tradeHistory accumulates one
	 * duplicate per session for as long as the offer sits stuck.
	 *
	 * Matches on the offer's SHAPE plus OFFER-EPOCH CONTINUITY — same item,
	 * side, total quantity, and the same GE slot the stuck row was minted in —
	 * rather than on per-item price. priceEach is the integer-divided running
	 * average ({@code totalGp/quantity}); it DRIFTS when fills land while the
	 * plugin is offline, so an exact-price match silently failed and a duplicate
	 * row was appended on restart (P7). The fill count is allowed to differ (the
	 * row's quantity may be less than the current observation if more items have
	 * filled since the row was last touched).
	 *
	 * Slot continuity is read from the offerInstanceId itself: it is minted as
	 * {@code currentTimeMillis()*10 + slot}, so {@code offerInstanceId % 10} is
	 * the slot. A still-partial GE offer never changes slot across a restart, so
	 * this is a precise "same physical offer" signal without depending on price.
	 *
	 * Guarded by the fills-accumulate invariant ({@code recordedQty <= currentQty}
	 * and {@code recordedGp <= currentGp}): the same still-active offer can only
	 * have recorded at-or-behind its live cumulative. A candidate row that sits
	 * AHEAD of the live observation is a different, more-progressed offer — most
	 * importantly a terminal CANCELLED partial row from a PRIOR flip in this slot
	 * (cancellation keeps {@code partial == true}, see
	 * {@link #finaliseAndPostExistingRow}). Reclaiming such a row would rewrite
	 * that trade's identity and, via the {@code existingQty >= currentQty}
	 * early-return in {@link #recordIfNewFills}, swallow the new offer's fills.
	 */
	// Package-private (not private) so SlotReuseDetectionTest-style unit tests can
	// exercise the reclaim matcher directly.
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
			// Offer-epoch (slot) continuity instead of exact price — see javadoc.
			if (t.offerInstanceId % 10 != slot) continue;
			// Fills-accumulate invariant — never reclaim a row that is ahead of the
			// live offer (e.g. a cancelled partial from a prior flip in this slot).
			if (t.quantity > currentQty) continue;
			if (t.totalGp  > currentGp)  continue;
			return i;
		}
		return -1;
	}

	/**
	 * Terminal counterpart to {@link #findReObservableActiveOfferRow}: hunts
	 * for a FULLY-RECORDED row representing the same TERMINAL offer we are
	 * re-observing fresh (slot baseline lost across a relog while a BOUGHT/
	 * SOLD offer sat uncollected in the slot). Only called when the observed
	 * offer is itself in a terminal state.
	 *
	 * Deliberately stricter than the active-offer matcher: the fill ledger
	 * must match the live observation EXACTLY (quantity AND gp), on top of
	 * item, side, order size, and slot continuity. A terminal offer can never
	 * gain fills, so anything less than exact equality is a different offer —
	 * the exactness is what makes it safe to match non-partial rows here
	 * (a completed prior flip re-listed identically would still have to land
	 * the same slot, order size, fill count AND gp total to collide).
	 */
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
				// Scrub: removes server-fill duplicates, stale-partial twins,
				// and stuck-partial re-observations of the same offer (each
				// case described in TradeHistoryDedup). Gated by a scrub
				// version so users who migrated on an older build pick up
				// new dedup rules when they upgrade.
				int before = list.size();
				String prevScrub = configManager.getConfiguration("o7flip", SCRUB_VERSION_KEY);
				boolean needsScrub = !SCRUB_VERSION_CURRENT.equals(prevScrub);
				if (needsScrub)
				{
					list = com.o7flip.util.TradeHistoryDedup.scrub(list);
					configManager.setConfiguration("o7flip", SCRUB_VERSION_KEY, SCRUB_VERSION_CURRENT);
				}
				int removed = before - list.size();

				// One-time heal: an earlier plugin version back-dated freshly
				// observed buy timestamps to 1 second before the earliest
				// existing trade of the same item, in an attempt to pair them
				// with phantom sells. That mis-ordered FIFO so a NEW buy
				// could sort before an OLDER buy of the same item, and a
				// later sell would attribute cost basis to the wrong lot.
				// The heal recovers the real observation time from
				// offerInstanceId (which is millis*10 + slot) and rewrites
				// the row's timestamp. Gated on a config flag so it doesn't
				// re-run unnecessarily.
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
					// Persist the cleaned/healed list so we don't redo the same
					// work on every startup.
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
		// Also reset the per-slot fill ledger — without this, the next time
		// the user places a fresh offer in a slot we'd compute a delta
		// against stale state from before the history wipe.
		slotRecordedFills.clear();
		configManager.unsetConfiguration("o7flip", SLOT_FILLS_KEY);
		// The bond ledger represents a lifetime stat (account-wide
		// membership spend) and is preserved across history clears — it
		// isn't backed by the rows we're wiping. A user clearing their
		// recent trade list shouldn't lose their year-of-bonds tally.
		SwingUtilities.invokeLater(() -> panel.updateMyFlips(Collections.emptyList()));
	}

	/**
	 * Loads the persistent bond ledger from config. On first run after the
	 * ledger was introduced, seeds it from any bond rows still sitting in
	 * {@code tradeHistory} (a one-shot migration so existing installs don't
	 * see their "Membership cost" stat drop to zero on upgrade), then marks
	 * itself migrated.
	 *
	 * Called once on plugin start, AFTER {@link #loadTradeHistory()} so the
	 * seed has the freshly-scrubbed list to work with.
	 */
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

	/**
	 * Replaces the lifetime bond ledger with explicit values, used by the
	 * "Adjust lifetime…" panel action so the user can recover the historic
	 * bond ledger that the migration couldn't find (rows that had already
	 * been evicted from the 200-row tradeHistory window before the ledger
	 * existed). Marks the ledger as migrated so the auto-seed won't
	 * overwrite the value on next load. Triggers a panel refresh.
	 */
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

	/**
	 * Apply a freshly-recorded trade to the bond ledger if it's a bond.
	 * No-op for non-bond trades. Game thread is fine; persists immediately
	 * so a crash mid-session doesn't lose the bond update.
	 */
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
			executor.execute(() -> apiClient.fetchRecommendedPrices(itemId,
				rp ->
				{
					try
					{
						if (rp != null)
						{
							recPriceCache.put(itemId, rp);
							// Any fresh rec-price arrival is a chance to arm the
							// implicit auto-fill if the user is still on a matching
							// setup. Each arm checks its own buy/sell screen type,
							// so only the relevant one fires; both no-op otherwise.
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
					// /recommended-prices 503 (server deploy warmup): keep the
					// last-known cached price on screen and retry once after the
					// server's Retry-After. Release the in-flight guard first so the
					// retry — and any user-triggered refresh meanwhile — can proceed.
					// The 2-arg overload on the retry means a second 503 just falls
					// through; the next on-demand call will try again.
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
						// Fresh /v2/item rec prices landed — re-arm the GE auto-fill
						// if a buy/sell setup for this item is still open. Each arm
						// checks its own screen type, so only the relevant one fires.
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
			if (insights != null && insights.buyLimit > 0)
			{
				rememberBuyLimit(insights.itemId, insights.buyLimit);
			}
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

	/**
	 * Push-direction counterpart to {@link #doSyncTrackerHistory}: walks the
	 * locally-recorded {@link #tradeHistory} and bulk-submits it to
	 * {@code /api/runelite/tracker/bulk}. The server dedups via
	 * {@code unique_trade(userId, itemId, tradedAt, isBuy)} so already-known
	 * trades come back in the duplicate counter, not as inserts.
	 *
	 * Run unconditionally at startup (subject to {@code shareTradeData} +
	 * apiKey gating) to recover any trades the May 14 → zero-delta-fix-
	 * release plugin builds recorded locally but failed to POST upstream.
	 * The trade window is bounded at {@link #MAX_TRADE_HISTORY}=200, so the
	 * payload is comfortably under the server's 500-row request cap and the
	 * full backlog ships in a single request.
	 */
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
		// SYNC_CONTRACT §5b — only offer rows the server hasn't confirmed yet.
		// Rows with serverSynced or a stamped tradeId are already delivered;
		// re-sending them is exactly the duplicate pressure the contract bans.
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
				// A 2xx batch with no rejects means every offered row is now
				// on the server — inserted or deduped both count as delivered.
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
		// Catch any server fills the loop above appended that overlap with a
		// pre-existing local merged row. Without this scrub a newly-arrived
		// per-fill payload from the server would re-introduce the duplicates
		// loadTradeHistory cleaned out.
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
				rememberBuyLimits(items);
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
	 *
	 * Delegates to {@link #capitalFilterCeiling()} so the Flips feed and the
	 * client-side affordable tabs filter against the same ceiling — the user's
	 * total capital, not the momentary free balance.
	 */
	private long cashStackBucketGp()
	{
		return capitalFilterCeiling();
	}

	/**
	 * Single source of truth for the affordability filter — how much GP the
	 * user has free to deploy on a NEW flip right now. Returns 0 when capital
	 * tracking is off (callers treat 0 as "no filter").
	 *
	 * Equals {@link #freeCapital()} bucketed to the nearest 100K — the bucket
	 * preserves the original {@code cashStack} privacy semantic (we never
	 * expose exact wealth to the server).
	 */
	public long effectiveCapital()
	{
		long free = freeCapital();
		if (free <= 0)
		{
			return 0L;
		}
		return (free / CASH_BUCKET) * CASH_BUCKET;
	}

	/**
	 * The ceiling used to filter item lists (Flips + the client-side affordable
	 * tabs) to what the user's capital can buy. This is the user's <em>total</em>
	 * capital — the same figure shown in the Capital input — bucketed to 100K
	 * for privacy, NOT {@link #freeCapital()}.
	 *
	 * Using total (rather than free) is deliberate: placing a buy offer ties up
	 * GP but should not shrink the list of flip ideas the user is browsing. The
	 * user wants "show me everything priced at or below my capital", and the
	 * capital figure they see is {@link #totalCapital()}. Returns 0 when capital
	 * tracking is OFF (callers treat 0 as "no filter").
	 */
	public long capitalFilterCeiling()
	{
		long total = totalCapital();
		if (total <= 0)
		{
			return 0L;
		}
		return (total / CASH_BUCKET) * CASH_BUCKET;
	}

	/**
	 * The user's free (unlocked) capital. In Auto mode this is just inventory
	 * coins — the GE has already deducted gp from inventory when offers were
	 * placed, so what's left in your pouch IS your free capital. In Manual
	 * mode it's the user's typed total minus what's locked in active buy
	 * offers, so the figure tracks live as offers fill / cancel.
	 */
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

	/** Total bankroll figure shown in the UI readout — free + deployed. */
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

	/**
	 * GP currently locked in unfilled portions of active GE buy offers.
	 * Filled portions are excluded (the gp already became items). Sell offers
	 * don't count — they tie up items, not GP.
	 */
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
		// Legacy: usePersonalisedFlips=true with mode=OFF means the user
		// migrated from the old toggle but hasn't touched the new control —
		// treat it as AUTO so their experience doesn't silently change.
		if (mode == O7FlipConfig.CapitalMode.OFF && config.usePersonalisedFlips())
		{
			return O7FlipConfig.CapitalMode.AUTO;
		}
		return mode;
	}

	/** Called by the panel when the user types or toggles the Capital input. */
	public void onCapitalChanged()
	{
		executor.execute(() ->
		{
			if (config.showFlips())
			{
				fetchFlipsAtPage(panel.getFlipsPage());
			}
		});
		// Client-side-filtered tabs (Dips / Alch / Favourites / Spikes
		// / Dumps) don't need a refetch — they already have the rows, the filter
		// just changed. Trigger a re-render on the EDT so the new ceiling
		// applies immediately.
		SwingUtilities.invokeLater(() -> panel.rerenderCapitalAffectedTabs());
	}

	/** Panel helper for persisting the Capital mode through ConfigManager. */
	public void persistCapitalMode(O7FlipConfig.CapitalMode mode)
	{
		configManager.setConfiguration("o7flip", "capitalMode", mode);
	}

	/**
	 * Explicit, persisted on/off switch for the capital filter, driven by the
	 * toggle in the panel's Capital section. ON resolves to MANUAL (use the
	 * typed value); OFF is a true off — it also clears the legacy
	 * {@code usePersonalisedFlips} flag so {@link #resolveCapitalMode()} can't
	 * silently re-enable AUTO behind the user's back. Refreshes every
	 * capital-gated tab so the change applies immediately.
	 */
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

	/** Panel helper for persisting the manual capital value. */
	public void persistCapitalManual(long gp)
	{
		configManager.setConfiguration("o7flip", "capitalManual", gp);
	}

	/** Panel helper for persisting the capital-locked flag. */
	public void persistCapitalLocked(boolean locked)
	{
		configManager.setConfiguration("o7flip", "capitalLocked", locked);
	}

	/**
	 * Auto-adjusts the manual capital figure when a GE offer fills. In
	 * Manual mode the user's typed value represents their flipping bankroll,
	 * so a fresh fill should move it: a buy converts liquid GP into items
	 * (subtract cost), a sell converts items back into GP (add after-tax
	 * proceeds). Auto mode is a no-op — it derives from inventory coins
	 * which already update naturally.
	 *
	 * Called from {@link #recordTrade} on every fill delta.
	 */
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
			// deltaGp is the per-fill change in offer.getSpent(), which for
			// sells is already NET of GE tax in current RuneLite — adding it
			// straight to capital is correct. Subtracting tax here was the
			// double-deduction bug that produced phantom -2.5M-ish losses on
			// high-value sells.
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

	// -------------------------------------------------------------------------
	// High Alch / Favourites — "Other" tab feeds
	// -------------------------------------------------------------------------

	void onHighAlchPageChanged(int page)
	{
		executor.execute(() -> fetchHighAlchAtPage(panel.getHighAlchSortKey(), page));
	}

	void onHighAlchSortChanged(String sort)
	{
		executor.execute(() -> fetchHighAlchAtPage(sort, 0));
	}

	void onHighAlchModifierChanged()
	{
		executor.execute(() -> fetchHighAlchAtPage(panel.getHighAlchSortKey(), 0));
	}

	private void fetchHighAlchAtPage(String sort, int page)
	{
		apiClient.fetchHighAlch(sort, page,
			panel.getHighAlchFireStaff(), panel.getHighAlchBryophyta(),
			resp ->
			{
				if (resp != null && resp.items != null && !resp.items.isEmpty())
				{
					saveCache("highAlch", resp);
				}
				SwingUtilities.invokeLater(() -> panel.updateHighAlch(resp, page));
			});
	}

	/**
	 * Tab-select fetch throttle. Holds the last-fetch wall-clock per sub-tab
	 * name; a select event only fires a fresh request if its entry is older
	 * than {@link #TAB_SELECT_FRESHNESS_MS}. Caps "user-clicks-tab" load on
	 * the server at one request per sub-tab per 30 seconds, regardless of
	 * how rapidly the user toggles.
	 */
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

	/**
	 * Called when the user navigates to one of the Other tab's sub-tabs.
	 * Fires a fresh fetch for that sub-tab if its data is stale per
	 * {@link #TAB_SELECT_FRESHNESS_MS}. The 30-second floor keeps "user
	 * rapidly clicking sub-tabs" from amplifying load on the server.
	 */
	void onOtherSubTabSelected(String name)
	{
		if (name == null || executor == null || executor.isShutdown()) return;
		if (!tabSelectFresh(name)) return;
		switch (name)
		{
			case "Dips":
				executor.execute(() -> fetchDipsAtPage(panel.getDipsSortKey(), panel.getDipsPage()));
				break;
			case "Alch":
				executor.execute(() -> fetchHighAlchAtPage(panel.getHighAlchSortKey(), panel.getHighAlchPage()));
				break;
			// Favs has its own dedicated handler below — keep it out of this
			// switch so the throttle doesn't double-count.
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

	// -------------------------------------------------------------------------
	// Favourites — server is the source of truth, plugin holds an in-memory
	// mirror of the user's favourite item IDs so star-toggle UIs can render
	// the correct filled/hollow state without a refetch.
	// -------------------------------------------------------------------------

	/** Item IDs the server says the user has favourited. Refreshed on every
	 *  GET /favourites; optimistically updated on every star toggle. */
	private volatile java.util.Set<Integer> favouriteItemIds = java.util.Collections.emptySet();

	/** Items the user has just-added but the server's GET hasn't reflected
	 *  yet. Lives for {@link #FAV_BUFFER_TTL_MS} so a slow read-after-write
	 *  doesn't wipe an optimistic toggle. Keyed by itemId → wall-clock ms. */
	private final java.util.Map<Integer, Long> recentlyAddedFavs = new java.util.concurrent.ConcurrentHashMap<>();

	/** Mirror of {@link #recentlyAddedFavs} for the remove direction. */
	private final java.util.Map<Integer, Long> recentlyRemovedFavs = new java.util.concurrent.ConcurrentHashMap<>();

	/** FlipItem rows for {@link #recentlyAddedFavs} entries, so an optimistic
	 *  add can be re-inserted into the Favs list if a stale GET response
	 *  doesn't yet include it. Kept in sync with recentlyAddedFavs. */
	private final java.util.Map<Integer, FlipItem> recentlyAddedFavItems = new java.util.concurrent.ConcurrentHashMap<>();

	/** How long a recent local toggle survives a stale server response.
	 *  5 min is plenty for any reasonable read-after-write delay; longer
	 *  starts to mask real "user cleared all favs on the website" syncs. */
	private static final long FAV_BUFFER_TTL_MS = 5 * 60 * 1000L;

	/** True when the user has favourited this item. Cheap lookup for star
	 *  icons rendered on item rows. */
	public boolean isFavourite(int itemId)
	{
		return favouriteItemIds.contains(itemId);
	}

	private static final String FAVOURITES_ORDER_KEY = "favouritesOrder";

	/** The user's saved manual favourites order (item ids), or empty if none. */
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

	/** Persists the manual favourites order set in the reorder popup. */
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

	/**
	 * Server-side unfavourite used by the reorder popup when an item is dropped.
	 * Updates the id set + recent-toggle buffers + refreshes the star, but does
	 * NOT touch the panel's Favs list (the panel rebuilds it itself after a
	 * reorder/remove batch).
	 */
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

	/**
	 * Toggles favourite state for an item — optimistically flips the local
	 * cache and fires POST or DELETE to the server. On non-2xx the local
	 * state is reverted and {@code onError} is invoked so the caller can
	 * toast / revert UI. {@code onSuccess} runs on the EDT after the
	 * server confirms.
	 */
	public void toggleFavourite(int itemId, boolean currentlyFav, Runnable onSuccess, Runnable onError)
	{
		if (!hasApiKey() || itemId <= 0)
		{
			if (onError != null) SwingUtilities.invokeLater(onError);
			return;
		}
		// Optimistic: flip immediately so the UI feels instant. We snapshot
		// the previous set so a revert can be exact (no race with a
		// concurrent /favourites GET overwriting it).
		java.util.Set<Integer> snapshot = favouriteItemIds;
		java.util.Set<Integer> next = new java.util.HashSet<>(snapshot);
		if (currentlyFav) next.remove(itemId); else next.add(itemId);
		favouriteItemIds = java.util.Collections.unmodifiableSet(next);
		// Build the optimistic Favs-list row up front (for an add) so the panel
		// shows it instantly rather than waiting for the next /favourites GET.
		final FlipItem optimisticFav = currentlyFav ? null : buildFavouriteFlipItem(itemId);
		// Refresh any panel that displays favourite state — star + the Favs list.
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
				// Stamp the toggle into the recent-toggle buffer so the next
				// periodic poll's GET response can't wipe it via a stale
				// read-after-write. The opposite buffer is cleared because
				// the user's intent is now unambiguous.
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
				// No immediate refetch — the optimistic update is already
				// what the user expects to see, and an immediate GET often
				// races a slow read-after-write on the server side. The
				// 60-second periodic poll is the reconciliation path.
				if (onSuccess != null) onSuccess.run();
			}
			else
			{
				// Server rejected — revert the optimistic id-set + list and notify.
				favouriteItemIds = snapshot;
				recentlyAddedFavItems.remove(itemId);
				panel.onFavouriteToggled(itemId);
				// Restore the list to server truth (rare path).
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

	/**
	 * Reconciles the favourite-id set with a fresh server response, then
	 * overlays the recently-toggled buffers so we don't lose a confirmed
	 * local toggle that the server's GET hasn't yet caught up to.
	 *
	 * Buffer entries are dropped when (a) the server's response already
	 * reflects them — no longer needed — or (b) they've aged past
	 * {@link #FAV_BUFFER_TTL_MS} — eventual consistency wins.
	 */
	/**
	 * Builds the row data for an optimistic favourite add. Prefers the
	 * currently-open Insights item (the user favourites what they're viewing),
	 * falls back to the loaded Flips list, then to a name-only stub the next
	 * GET fills in.
	 */
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

	/**
	 * Merges a fresh /favourites GET with the recently-toggled buffers so an
	 * optimistic add the server hasn't caught up to stays visible, and an
	 * optimistic remove stays hidden, until the TTL window passes (mirrors
	 * {@link #rebuildFavouriteIds} but for the row list, not the id set).
	 */
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

	/** Centralised favourites push: reconcile ids + rows, then render. */
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

		// Expire stale buffer entries — server is source of truth once the
		// TTL window has passed.
		recentlyAddedFavs.entrySet().removeIf(e -> now - e.getValue() > FAV_BUFFER_TTL_MS);
		recentlyRemovedFavs.entrySet().removeIf(e -> now - e.getValue() > FAV_BUFFER_TTL_MS);

		// If the server has caught up to a buffered toggle, clear the buffer
		// entry — we no longer need to "protect" it from being wiped.
		recentlyAddedFavs.keySet().removeIf(serverSet::contains);
		recentlyRemovedFavs.keySet().removeIf(id -> !serverSet.contains(id));
		// Keep the optimistic-row map in lockstep with the add buffer.
		recentlyAddedFavItems.keySet().retainAll(recentlyAddedFavs.keySet());

		// Merged set = (server ∪ recentlyAdded) − recentlyRemoved.
		java.util.Set<Integer> merged = new java.util.HashSet<>(serverSet);
		merged.addAll(recentlyAddedFavs.keySet());
		merged.removeAll(recentlyRemovedFavs.keySet());
		favouriteItemIds = java.util.Collections.unmodifiableSet(merged);

		// Diagnostic logging — surfaces server-side sync issues without
		// spamming. INFO when there's a divergence; DEBUG otherwise.
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

	/**
	 * Kicks off an optimizer request and routes the three outcomes to the
	 * panel. Called from the Plan sub-tab's Build button. Always non-blocking
	 * — runs on the executor so the EDT stays free for animations.
	 *
	 * On success, also seeds the cross-surface session (sets activeSession +
	 * schedules a debounced POST so the website sees the same plan).
	 */
	public void runOptimizer(long capital, int slots, String risk,
	                         int maxFillHours, Boolean members, Double minProfitPct)
	{
		if (executor == null || executor.isShutdown()) return;
		executor.execute(() -> apiClient.fetchOptimize(
			capital, slots, risk, maxFillHours, members,
			null, minProfitPct,
			result -> clientThread.invoke(() ->
			{
				// Seed BEFORE rendering, and on the game thread (P3): the seed
				// carries in-flight fill ledgers from the old plan onto the new
				// allocations, so the cards render with progress intact — and the
				// fold path can never race an EDT-side session swap.
				seedActiveSessionFrom(result, capital, slots, risk, maxFillHours, members, minProfitPct);
				scheduleSessionPost();
				SwingUtilities.invokeLater(() -> panel.onOptimizeResult(result));
			}),
			upgradeUrl -> SwingUtilities.invokeLater(() -> panel.onOptimizePremiumRequired(upgradeUrl)),
			reason -> SwingUtilities.invokeLater(() -> panel.onOptimizeError(reason))));
	}

	/**
	 * Re-runs the optimiser with the same inputs as the active session but a
	 * new slot count — backs the "Using N slots would deploy ~X more" button
	 * (Task A3). No-op without an active session to read inputs from.
	 */
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

	/**
	 * Swaps a single allocation in the user's current plan. Re-uses the main
	 * /optimize endpoint with slots=1, capital = the original slot's gp, and
	 * exclude_item_ids = all currently-allocated items (so the same item
	 * never comes back).
	 *
	 * The panel's onOptimizeSlotSwapped callback receives the swap index +
	 * the new allocation, and edits its local result in place.
	 */
	public void swapPlanSlot(int swapIndex, com.o7flip.model.OptimizeResult current)
	{
		if (executor == null || executor.isShutdown() || current == null
			|| current.allocations == null || swapIndex < 0 || swapIndex >= current.allocations.size())
		{
			return;
		}
		com.o7flip.model.OptimizeResult.Allocation old = current.allocations.get(swapIndex);
		long slotCapital = old.gpAllocated;
		// Exclude every currently-allocated item id so the server can't return
		// any of them — including the slot being swapped.
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
				// Advance generated_at to the swap's server timestamp so the POST is
				// structurally newer and the site adopts the new item (plugin→site).
				replaceSlotInActiveSession(swapIndex, result.allocations.get(0), result.updatedAt);
				scheduleSessionPost();
			}),
			upgradeUrl -> SwingUtilities.invokeLater(() -> panel.onOptimizePremiumRequired(upgradeUrl)),
			reason -> SwingUtilities.invokeLater(() -> panel.onOptimizeError(reason))));
	}

	// -------------------------------------------------------------------------
	// Cross-surface optimiser session (/optimize/active)
	// -------------------------------------------------------------------------
	//
	// Shared row between the website and the plugin. Last-write-wins. The
	// plugin POSTs blindly on local changes (1s debounce) and polls GET at
	// 15s while the Plan tab is open OR the session has in-flight legs,
	// falling back to 60s when idle (server ask: fast sync while anything
	// is live; the rate cap is 60/min/IP so 4/min is comfortable).
	//
	// activeSession is the single source of truth on the plugin side; both
	// the panel UI and the wire-format converter (sessionToJson) read from it.

	private volatile com.o7flip.model.OptimizerSession activeSession;
	/** Armed on login (GameState.LOGGED_IN); the next game tick runs the
	 *  offline-completion reconcile once GE offers are reliably readable. */
	private volatile boolean offlineReconcileArmed = false;
	private ScheduledFuture<?> pendingSessionPost;
	private ScheduledFuture<?> sessionPollTask;
	/** Always-on, low-frequency GET of the synced session, independent of which
	 *  tab is open. The operator's primary flow is building the plan on the
	 *  WEBSITE; without this, activeSession stays null until the Plan tab is
	 *  first opened, so in-client fills are never attributed or POSTed back —
	 *  the website then shows 0/N forever. The merge it runs is local-
	 *  authoritative ({@link #mergeRemoteFills}), so it never clobbers in-flight
	 *  local fills. Lifecycle-bound: started in startUp, cancelled in shutDown. */
	private ScheduledFuture<?> sessionBackgroundPollTask;
	/** In-memory cache of the shared completed-positions history (Task D),
	 *  hydrated from GET /optimize/completed and kept authoritative by POST
	 *  responses. Newest-first. Guard all access on the list's monitor. */
	private final java.util.List<com.o7flip.model.CompletedPosition> completedPositions = new java.util.ArrayList<>();
	private static final long SESSION_POST_DEBOUNCE_MS = 1000L;
	/** Poll cadence WHILE the Plan tab is open. Server cap is 60/min/IP so a
	 *  15s loop is comfortably under it (4 GET/min) while still feeling live. */
	private static final long SESSION_POLL_INTERVAL_S  = 15L;
	/** Background poll BASE cadence regardless of tab. Every tick polls while
	 *  the session has in-flight legs (live fills need fast cross-surface
	 *  sync); when idle, only every {@link #SESSION_BACKGROUND_IDLE_DIVISOR}th
	 *  tick fires, for an effective 60s cadence — just enough to discover a
	 *  web-built plan. */
	private static final long SESSION_BACKGROUND_POLL_INTERVAL_S = 15L;
	/** Idle slowdown: with a 15s base tick, 4 → one GET per 60s. */
	private static final int SESSION_BACKGROUND_IDLE_DIVISOR = 4;
	/** Counts background poll ticks for the idle slowdown. Executor thread only. */
	private int sessionBackgroundPollTick;

	/** Startup GET — pulls whatever was last saved on either surface. */
	private void doHydrateOptimizerSession()
	{
		// Premium-only feature — don't touch the optimiser session endpoints
		// for non-premium users.
		if (panel == null || !panel.isPremium()) return;
		apiClient.fetchActiveSession(session ->
		{
			if (session == null || session.slots == null || session.slots.isEmpty())
			{
				return;
			}
			// Assign on the game thread (P3 consistency) so the startup hydrate
			// can't race the fold path's reads/writes of activeSession — the
			// fetchActiveSession callback runs on an OkHttp dispatcher thread.
			clientThread.invoke(() ->
			{
				activeSession = session;
				// §5b — fold any trades that landed before this hydrate (the fill
				// path no-ops while activeSession is null) back onto empty legs.
				boolean changed = retroAttributeFills(session);
				// §10 — pick up sell listings that predate this hydrate (login
				// replay fires before the session GET lands).
				changed |= sweepSellListedFromOffers(session);
				if (changed)
				{
					scheduleSessionPost();
				}
				// Arm the offline-completion scan too — covers enabling the plugin
				// while already logged in (no LOGGED_IN transition fires then).
				offlineReconcileArmed = true;
				SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(session));
			});
		});
	}

	/**
	 * Called by the panel when the Plan sub-tab is selected. Triggers an
	 * immediate GET (fresh snapshot the moment the user looks) and starts a
	 * recurring poll so live edits made on the website appear in the panel
	 * within the next interval.
	 *
	 * The poll is bounded by tab visibility — when the user navigates away,
	 * {@link #onPlanTabDeselected} cancels it. Net result: live updates
	 * while the tab is active, zero idle traffic otherwise.
	 */
	public void onPlanTabSelected()
	{
		// Optimiser is premium-only — never start a session poll/POST for a
		// non-premium user (the Plan tab shows the upsell card for them).
		if (panel == null || !panel.isPremium()) return;
		if (executor == null || executor.isShutdown()) return;
		executor.execute(this::doPollActiveSession);
		// Also pull the shared history so web-side closes show on tab open.
		refreshCompletedPositions();
		if (sessionPollTask == null || sessionPollTask.isCancelled() || sessionPollTask.isDone())
		{
			sessionPollTask = executor.scheduleAtFixedRate(
				this::doPollActiveSession,
				SESSION_POLL_INTERVAL_S, SESSION_POLL_INTERVAL_S, TimeUnit.SECONDS);
		}
	}

	/** Called when the Plan tab loses focus — stops the live-update poll. */
	public void onPlanTabDeselected()
	{
		if (sessionPollTask != null) sessionPollTask.cancel(false);
	}

	/**
	 * Starts the always-on (45s) background session poll if it isn't already
	 * running. Premium- and executor-gated. Called when the sidebar panel
	 * becomes visible ({@link #onPanelShown}), and at startup if it's already open.
	 */
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

	/**
	 * The 07Flip sidebar panel became visible (RuneLite {@code Activatable} hook,
	 * via {@link O7FlipPanel#onActivate()}). Resume the optimiser polling we pause
	 * while hidden so we don't hit the server when the user can't see the panel.
	 */
	public void onPanelShown()
	{
		startSessionBackgroundPoll();
		// If the panel re-opened directly onto the Plan tab, the tab-change
		// listener won't fire (the selection didn't change), so resume the fast
		// poll explicitly here.
		if (panel != null && panel.isPremium() && panel.isPlanTabActive())
		{
			onPlanTabSelected();
		}
	}

	/**
	 * The 07Flip sidebar panel was hidden/collapsed ({@link O7FlipPanel#onDeactivate()}).
	 * Stop BOTH session polls so no GET /optimize/active fires while the panel
	 * isn't visible — including the 15s fast poll, which the Plan-tab listener
	 * cannot stop on a collapse (the tab selection doesn't change).
	 */
	public void onPanelHidden()
	{
		if (sessionBackgroundPollTask != null) sessionBackgroundPollTask.cancel(false);
		if (sessionPollTask != null) sessionPollTask.cancel(false);
		if (eagerDiscoveryTask != null) eagerDiscoveryTask.cancel(false);
	}

	/**
	 * Background tick — defers to the Plan-tab fast poll when it's running (that
	 * already covers updates at 15s while the tab is open) and otherwise runs
	 * the same merge-safe GET so a web-built plan is discovered and in-client
	 * fills keep attributing even with the tab closed.
	 */
	/** Eager-discovery burst task — see {@link #startEagerSessionDiscovery}. */
	private volatile ScheduledFuture<?> eagerDiscoveryTask;

	/**
	 * Burst-polls the session endpoint (5s cadence, 2 minute cap) after the
	 * user heads to the website optimiser, so a freshly built/reconfigured
	 * plan lands in the panel within seconds instead of on the next 15s/45s
	 * cycle. Bounded well under the 60/min/IP server cap; re-clicking just
	 * restarts the window.
	 */
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

	/**
	 * True when the synced session has at least one leg with live GE activity
	 * still pending — BUYING (fills incoming), FILLED (sell to place), or
	 * SELLING (sell fills incoming). PENDING legs aren't trading yet and
	 * CLOSED legs are done; neither needs the fast poll. Reads the volatile
	 * session reference off-thread; a rare concurrent structure swap during
	 * iteration is treated as "live" so we never under-poll an active flip.
	 */
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
		// Premium-only — skip the always-on session discovery for non-premium.
		if (panel == null || !panel.isPremium()) return;
		// Visibility gate — don't poll while the sidebar is hidden/collapsed.
		if (!panel.isShowing()) return;
		ScheduledFuture<?> fast = sessionPollTask;
		if (fast != null && !fast.isCancelled() && !fast.isDone())
		{
			return;
		}
		// Idle slowdown — the 15s base tick only fires every cycle while the
		// session has in-flight legs; otherwise once per IDLE_DIVISOR ticks
		// (effective 60s) to keep discovering web-built plans cheaply.
		int tick = sessionBackgroundPollTick++;
		if (!sessionHasInFlightLegs() && tick % SESSION_BACKGROUND_IDLE_DIVISOR != 0)
		{
			return;
		}
		doPollActiveSession();
	}

	private void doPollActiveSession()
	{
		// Visibility gate — never poll the session endpoint while the sidebar is
		// hidden/collapsed (covers the fast poll lingering on the Plan tab and the
		// immediate poll fired on tab-select).
		if (panel == null || !panel.isShowing()) return;
		apiClient.fetchActiveSession(remote ->
		{
			if (remote == null) return;
			// P3 — serialise all activeSession/leg mutations onto the game thread.
			// The fold path (attributeTradeToActiveSlot → foldFill) already runs on
			// the game thread; routing this poll merge there too means the two never
			// race on the non-thread-safe buys/sells ArrayLists, and the
			// isEmpty()-check-then-adopt in mergeRemoteFills becomes atomic w.r.t. a
			// concurrent fold. Fill semantics are unchanged — only the thread.
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
				// Merge any new buys/sells the website's tracker poll discovered,
				// so the plugin's next POST doesn't clobber them. Item identity
				// is by item_id; per-slot fill arrays are unioned by traded_at
				// (de-duped against what we already have locally).
				boolean changed = mergeRemoteFills(local, remote);
				// §5b — heal legs that are STILL empty on both surfaces from the
				// local GE history (covers fills lost before this poll cycle).
				// Idempotent: a healed leg is non-empty and skipped next poll.
				boolean healed = retroAttributeFills(local);
				// §10 — reconcile sell-listed flags against the live GE offers.
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

	/**
	 * Force-GET the server session and adopt its plan structure (Phase 4 Resync
	 * button), re-attaching the plugin's local fills. Unlike a normal poll this
	 * ignores the generated_at gate and always adopts the server's allocation set,
	 * but it keeps local fills via {@link #adoptServerStructure} so it is
	 * non-destructive to in-flight progress. Deliberate user action, so it is NOT
	 * gated on panel visibility (only premium + a live executor).
	 */
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
				// §5b — Resync is the user's "make it right" button, so also heal
				// any legs that are empty on both surfaces from local GE history.
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

	// Package-private static (no instance state) so the §7 override carve-out is
	// unit-testable; see OverrideAdoptionTest.
	static boolean mergeRemoteFills(com.o7flip.model.OptimizerSession local,
	                                 com.o7flip.model.OptimizerSession remote)
	{
		if (local.slots == null || remote.slots == null) return false;
		// Phase 4 — plan STRUCTURE is site-authoritative by generated_at. When the
		// server's version is strictly newer (a website re-optimise), adopt its
		// allocation SET wholesale — add new items, drop removed ones — and re-attach
		// the plugin-owned fills to surviving legs. Same-version polls fall through to
		// the local-authoritative fill merge below (today's behaviour, unchanged).
		if (isRemoteStructurallyNewer(local, remote))
		{
			adoptServerStructure(local, remote);
			return true;
		}
		boolean anyChange = false;
		// Build remote lookup by item_id — last-write-wins on item identity
		// rather than slot position so a swap on one side reflects on the other.
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
			// §7 manual-override carve-out — when the server's overrideRev for this
			// leg is AHEAD of what we've applied, adopt the server's bought/sold/state
			// AUTHORITATIVELY, bypassing the local-authoritative isEmpty() rule for
			// THIS leg only (e.g. a site-entered offline-sell correction). Record the
			// applied rev so we don't re-adopt every poll, and clear any pending
			// offline-reconcile prompt the correction resolves. All other legs below
			// stay local-authoritative.
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
			// The plugin owns the fill ledger for slots it is trading (§5/§5a):
			// buys/sells are single consolidated entries it overwrites in place.
			// Only adopt remote fills when WE have none yet — e.g. a plan built
			// on the website that the plugin is hydrating for the first time.
			// Never union remote into a leg we already track, or a stale poll
			// snapshot of our own consolidated entry (different qty/traded_at as
			// it fills) would be re-added as a duplicate and double-count.
			if (l.buys.isEmpty()  && mergeFillList(l.buys,  r.buys))  anyChange = true;
			if (l.sells.isEmpty() && mergeFillList(l.sells, r.sells)) anyChange = true;
			// Hydrate leg-identity (NOT the §7 override path — that returned above):
			// when the plugin has no offerInstanceId for this leg yet (a freshly
			// Built plan, or a post-restart rebuild) but the server-discovered leg
			// carries one, adopt it so the NEXT in-client fill merges into the SAME
			// leg via attributeTradeToActiveSlot's first-fill stamp, instead of
			// minting a fresh epoch and forking the leg server-side. The server
			// already holds this id, so no POST is needed (no anyChange).
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

	/**
	 * Plan STRUCTURE version check (Phase 4). True when the server's
	 * {@code generated_at} is present AND strictly newer than the local session's
	 * — the website re-optimised and the plugin should adopt the new allocation
	 * set. Empty/absent server version ⇒ false (nothing to adopt); empty local
	 * version ⇒ true (adopt the server's). Package-private static for unit tests.
	 */
	static boolean isRemoteStructurallyNewer(com.o7flip.model.OptimizerSession local,
		com.o7flip.model.OptimizerSession remote)
	{
		String rg = remote == null ? null : remote.generatedAt;
		if (rg == null || rg.isEmpty()) return false;
		String lg = local == null ? null : local.generatedAt;
		if (lg == null || lg.isEmpty()) return true;
		return isIsoAfter(rg, lg);
	}

	/** True when ISO-8601 timestamp {@code a} is strictly after {@code b}. Parses
	 *  to {@link java.time.Instant} so differing precision can't misorder; falls
	 *  back to a lexical compare if either isn't a parseable instant. */
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

	/**
	 * Next {@code generated_at} for a plugin-side STRUCTURE change — the symmetric
	 * counterpart to the server's {@code nextGeneratedAt}. Returns
	 * {@code max(serverUpdatedAt, prev + 1s)} as an ISO-8601 UTC string, floored
	 * strictly-monotonic so the plugin's version can never go backwards (clock skew
	 * or a non-monotonic {@code /optimize updated_at}). {@code serverUpdatedAt} is
	 * the swap/build's {@code /optimize} {@code updated_at} when there was a
	 * round-trip; pass null for a local-only change (e.g. "Stop buying"), where the
	 * base falls back to now and is then floored to {@code prev + 1s}.
	 */
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

	/**
	 * Adopts the server's allocation SET as the new plan structure
	 * (site-authoritative by generated_at) while keeping the plugin's local FILLS
	 * (plugin-authoritative). Each server leg is matched to a local leg by
	 * offerInstanceId then item_id; when matched AND the plugin has fills, the
	 * plugin-owned fields (buys/sells/offerInstanceId/appliedOverrideRev/
	 * pendingOfflineReconcile) are carried onto the server's plan leg and state is
	 * re-derived — UNLESS the server's overrideRev is ahead (§7), where the server's
	 * authoritative fills/state win. New server items keep their parsed fills; local
	 * legs absent from the new plan are dropped. Adopts generated_at / updated_at /
	 * summary / last_poll_at. Runs on the game thread (poll merge / Resync).
	 */
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
						// §7 override newer than the plugin applied — server's
						// authoritative bought/sold/state (already on r) win.
						r.appliedOverrideRev      = r.overrideRev;
						r.pendingOfflineReconcile = false;
					}
					else
					{
						// Re-attach plugin fills onto the new plan leg, but only when
						// the plugin actually has fills — otherwise keep the server's
						// (first-hydration) fills so none are lost.
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

	/** Union remote fills into local on (qty, price_each, traded_at) identity. */
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

	/**
	 * Login reconcile (SYNC_CONTRACT §7, offline-sell half). Scans the GE slots
	 * and, for any optimiser leg the plugin still thinks is {@code SELLING}
	 * (bought, partially sold) whose sell offer has VANISHED — no live
	 * BUYING/SELLING offer for the item in any slot — flags it and surfaces a
	 * NON-DESTRUCTIVE "looks complete — confirm?" prompt.
	 *
	 * <p>It never mutates counts, state, or guesses the sold quantity. The
	 * authoritative correction is entered site-side (§8.3) and flows back via the
	 * §7 override carve-out in {@link #mergeRemoteFills}, which clears the flag.
	 * Runs on the game thread (reads {@code client.getGrandExchangeOffers()}).
	 */
	private void reconcileOfflineCompletions()
	{
		com.o7flip.model.OptimizerSession s = activeSession;
		if (s == null || s.slots == null) return;

		// §10 — post-login sweep: the GE offer replay at login fires before the
		// session hydrate, so reconcile the sell-listed flags here too (this
		// runs once per arm, on the game thread, with the offers loaded).
		if (sweepSellListedFromOffers(s))
		{
			scheduleSessionPost();
			final com.o7flip.model.OptimizerSession listedSnap = s;
			SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(listedSnap));
		}

		// Items with a live (in-progress) GE offer right now — a sell still sitting
		// in the GE means the position is NOT complete.
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

		// Non-destructive prompt only — direct the user to confirm/adjust on the
		// site (site-first per §8.3); the override then flows back and clears it.
		String msg = flagged.size() == 1
			? "07Flip: your sell of " + flagged.get(0) + " looks complete after being offline. "
				+ "Confirm or adjust it on 07flip.com and the plan will update."
			: "07Flip: " + flagged.size() + " optimiser sells look complete after being offline. "
				+ "Confirm or adjust them on 07flip.com and the plan will update.";
		notifier.notify(msg);

		final com.o7flip.model.OptimizerSession snap = s;
		SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
	}

	/**
	 * The offline-SELL completion signature for one leg: the plugin still records
	 * it as {@code SELLING} (a sell was in progress) but there is no live GE offer
	 * for the item, so the sale resolved while the plugin was offline. Pure +
	 * package-private for unit testing (see OfflineReconcileTest).
	 */
	static boolean isOfflineSellCompletion(com.o7flip.model.OptimizeResult.Allocation a,
		java.util.Set<Integer> liveOfferItems)
	{
		return a != null
			&& a.state == com.o7flip.model.SlotState.SELLING
			&& !liveOfferItems.contains(a.itemId);
	}

	/**
	 * Clears the offline-reconcile flag for a leg WITHOUT touching its counts —
	 * the user dismissed the "looks complete" prompt (it wasn't actually done).
	 * Public so a panel control can call it; routed onto the game thread to stay
	 * consistent with the rest of the leg-mutation serialisation (P3). No-op if
	 * the leg isn't flagged.
	 */
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

	/**
	 * Builds an OptimizerSession from a fresh /optimize response. Runs on the
	 * game thread (P3 — all activeSession mutations are game-thread-serialised).
	 *
	 * In-flight fill ledgers from the previous plan are CARRIED onto surviving
	 * items (matched by item_id), mirroring {@link #adoptServerStructure}.
	 * Without this, a Build/Reconfigure wiped the buys/sells of positions the
	 * user was mid-flip on, and the debounced POST (newer generated_at, legs
	 * without offer_instance_id) propagated the wipe to the server — the
	 * "website says Filled, plugin says 0/N bought" divergence.
	 */
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
		// Floor against the prior plan's stamp so a re-Build can't regress the version.
		String prevGen = prev != null ? prev.generatedAt : null;
		s.generatedAt         = nextGeneratedAt(prevGen, result.updatedAt);
		activeSession         = s;
	}

	/** Drop-in replacement when a single slot was swapped via /optimize (slots=1).
	 *  Callers fire from the EDT after a /optimize swap, so the structural
	 *  {@code slots.set(...)} is routed onto the game thread to avoid racing a
	 *  concurrent game-thread iteration of {@code activeSession.slots}.
	 *
	 *  A swap is a plugin-side STRUCTURE change, so it must advance {@code generated_at}
	 *  ({@code newGeneratedAt} = the swap's {@code /optimize} {@code updated_at}, a fresh
	 *  server timestamp). Without this the POST ships an equal/stale version and the
	 *  server keeps the old leg under the Phase 4 merge-by-leg-key write model — i.e. the
	 *  swap never reaches the site. Stamped on the game thread so it's visible in the
	 *  snapshot the debounced POST copies. */
	private void replaceSlotInActiveSession(int idx, com.o7flip.model.OptimizeResult.Allocation next,
		String newGeneratedAt)
	{
		clientThread.invoke(() ->
		{
			com.o7flip.model.OptimizerSession s = activeSession;
			if (s == null || s.slots == null || idx < 0 || idx >= s.slots.size()) return;
			s.slots.set(idx, next);
			// Floored-monotonic from the current stamp (mirrors the server helper) so
			// a stale/non-monotonic /optimize updated_at can't regress the version.
			s.generatedAt = nextGeneratedAt(s.generatedAt, newGeneratedAt);
		});
	}

	/** Debounce window — collapse a flurry of local edits into one POST. */
	private void scheduleSessionPost()
	{
		if (executor == null || executor.isShutdown()) return;
		if (pendingSessionPost != null) pendingSessionPost.cancel(false);
		pendingSessionPost = executor.schedule(this::doPostActiveSession,
			SESSION_POST_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	private void doPostActiveSession()
	{
		// Snapshot the live session on the GAME THREAD before it is serialised — the
		// scheduler thread must never iterate buys/sells (or the slots list) while
		// the game thread mutates them (foldFill / mergeRemoteFills / §7 override
		// adoption). The deep copy is unshared, so the OkHttp/serialisation path can
		// read it freely. Building the JSON here (≤8 slots) is trivial work.
		clientThread.invoke(() ->
		{
			com.o7flip.model.OptimizerSession live = activeSession;
			if (live == null) return;
			com.o7flip.model.OptimizerSession snapshot = live.copy();
			apiClient.postActiveSession(snapshot, ok -> { /* fire-and-forget */ });
		});
	}

	/** Called from the panel's ✕ Clear button — wipe both local and remote. */
	public void clearActivePlan()
	{
		activeSession = null;
		if (executor == null || executor.isShutdown()) return;
		executor.execute(() -> apiClient.deleteActiveSession(ok ->
		{
			SwingUtilities.invokeLater(() -> panel.onActivePlanCleared());
		}));
	}

	/**
	 * Hook called from {@code recordTrade} when a trade matches an item in
	 * the active allocation. Pushes a SlotFill onto the relevant slot, re-
	 * derives state, and runs two transition side-effects:
	 *
	 * <ul>
	 *   <li><b>PENDING/BUYING → FILLED</b>: silently arm the GE sell-side
	 *       queue at the slot's recommended {@code sell_price} so the next
	 *       time the user lists this item the ask auto-fills.</li>
	 *   <li><b>SELLING → CLOSED</b>: realised profit is added to the slot's
	 *       allocation and a fresh single-slot {@code /optimize} call rolls
	 *       a new recommendation into the same position, excluding all other
	 *       items currently in the plan. The new allocation is POSTed to
	 *       {@code /optimize/active} so the website sees the rolled plan.</li>
	 * </ul>
	 */
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
		// SYNC_CONTRACT §2 — stamp the leg with the offer epoch on the first fill
		// the plugin attributes here, and hold it stable for the flip so the server
		// keys legs by (item_id, offerInstanceId) and never collapses repeat flips
		// of the same item. Recycling the slot installs a fresh allocation
		// (offerInstanceId == null), so the next flip gets a new epoch.
		if (slot.offerInstanceId == null && offerInstanceId > 0)
		{
			slot.offerInstanceId = offerInstanceId;
		}
		com.o7flip.model.SlotState prevState = slot.state;

		// §5a — sync LIVE (in-progress) fill progress, not just on completion.
		// Fold each fill delta into ONE consolidated synthetic entry per leg
		// (running qty + weighted-average price) instead of appending an entry
		// per tick. The website renders bought/qty straight from sum(buys.qty),
		// so a partially-filled offer (e.g. 906/3760) shows live within one poll
		// — and because the terminal BOUGHT/SOLD delta just folds into the same
		// entry, the count never double-jumps on completion. Consolidating also
		// bounds the payload: a large multi-tranche fill stays a single entry,
		// not dozens, well under the ~32KB synced-row cap. The buy entry keeps
		// the earliest traded_at (flip start) while the sell entry advances to
		// the latest (honest closed_at / fill_hours).
		// Black-and-white cap (product decision): the optimiser leg counts only up
		// to the PLAN target — if the plan says buy 1000 and the user buys 1100, the
		// card and the POSTed leg count 1000. The extra units (and their profit) are
		// still recorded in My Trades + the GE log (tradeHistory), tracked
		// independently of this leg. Capping here also makes the leg immune to any
		// re-fold inflation from a lost cross-session baseline.
		int countedQty = cappedFillQty(isBuy, slot, qty);
		if (countedQty <= 0) return;   // leg already at its cap — counted in My Trades only

		String tradedAt = java.time.Instant.ofEpochMilli(timestampMs).toString();
		foldFill(isBuy ? slot.buys : slot.sells, countedQty, pricePer, tradedAt, !isBuy);
		slot.state = com.o7flip.model.SlotState.derive(slot.qty, slot.buys, slot.sells);
		scheduleSessionPost();

		// FILLED transition — arm sell-side auto-fill so the user's GE sell
		// listing reads the recommended ask without further action. Silent
		// (no notifier ping) to avoid double-notifying — the regular flow
		// already notified them on the initial buy queue.
		if (prevState != com.o7flip.model.SlotState.FILLED
			&& slot.state == com.o7flip.model.SlotState.FILLED
			&& slot.sellPrice > 0)
		{
			armSellAutoFill(slot.itemId, slot.sellPrice, slot.name);
		}

		// CLOSED transition — record the finished position in local history. The
		// slot is then left CLOSED (its capital is realised); the plugin does NOT
		// pick a replacement. Plan STRUCTURE is site-authoritative (Phase 4), so the
		// site re-optimises to redeploy the freed capital and the plugin adopts the
		// new structure on the next poll. The closed state itself reaches the server
		// via the fill-path scheduleSessionPost already queued above.
		if (prevState != com.o7flip.model.SlotState.CLOSED
			&& slot.state == com.o7flip.model.SlotState.CLOSED)
		{
			slot.sellListed = false;   // §10 — the listing resolved; flag is spent
			appendCompletedPosition(slot);
		}

		final com.o7flip.model.OptimizerSession snap = s;
		SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
	}

	/**
	 * Quietly arms the GE sell-side overlay at the supplied price. Unlike
	 * the user-initiated {@link #queueGeSell} this does NOT call the
	 * notifier — the action is automatic, the user didn't ask for a ping.
	 */
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

	/**
	 * SYNC_CONTRACT §10 sweep — reconciles every plan leg's {@code sellListed}
	 * flag against the CURRENT GE offers. The offer-changed handler catches live
	 * transitions, but misses listings that predate session hydration: at login
	 * the GE events replay within the first ticks, BEFORE the startup session
	 * GET has landed, so {@link #markPlanSellListed} no-ops on a null session —
	 * and a listed offer that just sits there never fires another event. The
	 * sweep also self-heals missed cancellations. Game thread only; no-op
	 * unless logged in (the offer array is only trustworthy in game).
	 *
	 * @return true when any leg changed (caller should POST + re-render).
	 */
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

	/**
	 * SYNC_CONTRACT §10 — flags the plan leg for {@code itemId} as having a live
	 * GE sell offer. Game thread only (fires from the offer-changed handler).
	 * No-op when there is no plan, the item isn't in it, the leg is already
	 * flagged, or the leg has fully closed.
	 */
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

	/**
	 * SYNC_CONTRACT §10 — clears the sell-listed flag for {@code itemId} after a
	 * sell cancellation, unless another GE slot still holds a live sell offer
	 * for the item. Game thread only (reads {@code getGrandExchangeOffers()}).
	 */
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

	/**
	 * Retro-attribution (§5b) — heals legs whose live fold was missed. A fill
	 * that lands while {@code activeSession} is null (the startup-hydrate race,
	 * trading before the Plan tab first opens) was previously lost forever:
	 * nothing ever reconciled the GE trade history against the plan. This scans
	 * {@code tradeHistory} for rows on an EMPTY leg's item recorded at/after the
	 * plan's {@code generated_at} and folds them in exactly like live fills
	 * (plan-capped, consolidated entry, leg-identity stamp).
	 *
	 * <p>Empty-leg only + generated_at anchor keeps it conservative: a leg the
	 * plugin (or server) already tracks is never touched, and flips of the same
	 * item that predate the plan can't double-count. Idempotent — after the
	 * first heal the leg is non-empty and skipped. Game thread only (P3).
	 *
	 * @return true when any leg changed (caller should POST + re-render).
	 */
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

	/**
	 * Quantity to count on the OPTIMISER leg, capped to the plan (black-and-white):
	 * a buy fills only up to {@code slot.qty} (the plan target); a sell only up to
	 * what the leg counts as bought. Actual fills beyond the cap are still recorded
	 * in My Trades + the GE log (tradeHistory) — they're just not counted here.
	 */
	static int cappedFillQty(boolean isBuy, com.o7flip.model.OptimizeResult.Allocation slot, int qty)
	{
		if (slot == null) return 0;
		int capacity = isBuy
			? slot.qty - sumQty(slot.buys)              // headroom to the plan target
			: sumQty(slot.buys) - sumQty(slot.sells);   // can't sell beyond counted-bought
		return Math.min(qty, Math.max(0, capacity));
	}

	/**
	 * Folds one fill delta into a leg's single consolidated {@link com.o7flip.model.SlotFill}
	 * (§5a). The leg holds at most one entry whose {@code qty} is the running
	 * total transacted on this slot and whose {@code priceEach} is the
	 * quantity-weighted average. Any extra entries already present (e.g. adopted
	 * from a remote poll before the plugin started trading the slot) are
	 * collapsed into the first so the invariant "one entry per leg" holds.
	 *
	 * <p>Fills arrive in chronological order, so {@code preferLatestTime} simply
	 * overwrites {@code traded_at} with the incoming time (used for the sell leg
	 * → honest last-sell / closed_at) while {@code false} keeps the earliest
	 * (used for the buy leg → flip start, so fill_hours spans the whole flip).
	 */
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

	/**
	 * "Stop buying" — caps a still-buying slot to whatever has been bought so
	 * far, freeing the rest of its budget. Mirrors the website's
	 * {@code markPartial}: the slot advances to FILLED/SELLING and sits there
	 * until its bought units sell and it CLOSES. The plugin no longer redeploys the
	 * freed/realised capital itself (plan structure is site-authoritative, Phase 4);
	 * {@code reservedGp} is round-tripped so the SITE can redeploy on its next
	 * re-optimise. Backs the Stop-buying button on BUYING cards.
	 */
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

		// Capping usually pushes the slot to FILLED — arm sell-side auto-fill so
		// listing the bought units reads the recommended ask, same as a natural fill.
		if (slot.state == com.o7flip.model.SlotState.FILLED && slot.sellPrice > 0)
		{
			armSellAutoFill(slot.itemId, slot.sellPrice, slot.name);
		}

		// "Stop buying" shrinks the buy target — a STRUCTURE change, so advance
		// generated_at (floored-monotonic; no /optimize round-trip here) or the
		// website (strictly-newer adoption) never picks up the cap. Mirrors the
		// server's nextGeneratedAt on its own markPartialFill.
		s.generatedAt = nextGeneratedAt(s.generatedAt, null);

		scheduleSessionPost();
		final com.o7flip.model.OptimizerSession snap = s;
		SwingUtilities.invokeLater(() -> panel.hydrateOptimizerSession(snap));
	}

	// (Per-position resetSlotFills/removeSlot removed — the ⟳ Resync button
	// re-pulls the site's plan wholesale, which covers clearing/dropping legs.)

	// -------------------------------------------------------------------------
	// Completed-positions history (Task D) — shared web ↔ plugin store
	// -------------------------------------------------------------------------

	/**
	 * Records a just-closed slot as a {@link com.o7flip.model.CompletedPosition}.
	 * Profit is realised + after-tax ({@code sellGp − GE tax − buyGp}).
	 * De-duped on item + last-sell time so a replay (e.g. a session merge that
	 * re-derives CLOSED) doesn't double-count. The shared server store is
	 * authoritative: we add optimistically for instant UI, then POST and adopt
	 * the server's returned list (idempotent on item_id + closed_at).
	 */
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

		// Optimistic local insert (deduped) so the close shows immediately,
		// then push to the shared store and reconcile with the authoritative
		// list it returns.
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

	/** Wall-clock span first-buy → last-sell in hours; null if &lt; 2 timestamped trades. */
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

	/** Snapshot of the completed-positions history for the panel (newest-first). */
	public java.util.List<com.o7flip.model.CompletedPosition> getCompletedPositions()
	{
		synchronized (completedPositions)
		{
			return new java.util.ArrayList<>(completedPositions);
		}
	}

	/** Total realised after-tax profit across all recorded positions. */
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

	/** Adopt an authoritative list from the shared store and notify the panel. */
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

	/**
	 * Pulls the shared completed-positions list (web ↔ plugin synced store).
	 * Called on startup, when the Plan tab opens, and when the history view is
	 * shown — so closes made on the website surface in the plugin. Keeps the
	 * current cache on any failure (the callback returns null then).
	 */
	public void refreshCompletedPositions()
	{
		if (executor == null || executor.isShutdown()) return;
		executor.execute(() -> apiClient.fetchCompletedPositions(list ->
		{
			if (list != null) setCompletedPositions(list);
		}));
	}

	/**
	 * Persists a High Alch staff-modifier toggle through RuneLite's config
	 * system. Called from the panel so the UI doesn't need to inject
	 * {@link ConfigManager} directly.
	 */
	public void persistHighAlchModifier(String keyName, boolean value)
	{
		configManager.setConfiguration("o7flip", keyName, value);
	}

	/** Public mirror of {@link #hasApiKey} for UI consumers in {@code com.o7flip.ui}. */
	public boolean hasApiKeyPublic()
	{
		return hasApiKey();
	}

	private boolean hasApiKey()
	{
		String k = config.apiKey();
		return k != null && !k.trim().isEmpty();
	}

	/**
	 * Single source-of-truth for fetching the Dumps tab. Routes to either
	 * {@code /dumps} or {@code /bot-dumps} depending on the panel's source
	 * toggle. Both endpoints return the same DumpItem shape so the panel
	 * doesn't care which one served the data.
	 */
	private void fetchDumpsAtPage(String sort, int page)
	{
		java.util.function.Consumer<DumpItem.Response> cb = resp ->
		{
			// Persist only when we actually got rows back — empty responses
			// shouldn't overwrite a previously-good cache (server hiccup
			// shouldn't wipe yesterday's data from disk).
			if (resp != null && resp.items != null && !resp.items.isEmpty())
			{
				saveCache("dumps", resp);
			}
			lastDumps = resp.items;
			rebuildTrackedItems();
			SwingUtilities.invokeLater(() -> panel.updateDumps(resp, page));
		};
		// Filters plumbed from the panel. confirmedOnly removed — server
		// enforces it as a base filter now. v5 adds a tier filter
		// (all/confirmed/likely) so users can narrow to just the
		// structural-evidence rows.
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
