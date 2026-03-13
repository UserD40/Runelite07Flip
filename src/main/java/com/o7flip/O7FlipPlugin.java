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

import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.o7flip.model.BarrowsSet;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.Notifier;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
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

	private O7FlipPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> refreshTask;

	// Barrows/Moon/Decanting change with GE prices (hourly), not every minute.
	// Only refresh them every SLOW_EVERY cycles to reduce server load.
	private static final int SLOW_EVERY = 5;
	private int slowTick = 0;

	// -------------------------------------------------------------------------
	// Pending GE buy intent (set by panel right-click, cleared after use)
	// -------------------------------------------------------------------------

	volatile int    pendingGeBuyItemId = -1;
	volatile long   pendingGeBuyPrice  = -1;
	volatile String pendingGeBuyName   = null;

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

	private void fillGeBuyOffer(int itemId, long price, String name)
	{
		client.runScript(ScriptID.GE_ITEM_SEARCH, name);
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
			config.showFlips()   ? (items, total) -> SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, flipsPage))   : null,
			config.showSpikes()  ? (items, total) -> SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, spikesPage)) : null,
			config.showDips()    ? (items, total) -> SwingUtilities.invokeLater(() -> panel.updateDips(items, total, dipsPage))     : null,
			config.showDumps()   ? (items, total) -> SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, dumpsPage))   : null,
			config.showAlerts()  ? (items, total) -> SwingUtilities.invokeLater(() -> panel.updateAlerts(items, total, alertsPage)) : null,
			(config.showBarrows() && includeSlow) ? sets    -> SwingUtilities.invokeLater(() -> panel.updateBarrows(sets))    : null,
			(config.showMoon()    && includeSlow) ? sets    -> SwingUtilities.invokeLater(() -> panel.updateMoon(sets))       : null,
			(config.showDecant()  && includeSlow) ? decants -> SwingUtilities.invokeLater(() -> panel.updateDecanting(decants)) : null
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
			config.showDecant()  ? decants -> SwingUtilities.invokeLater(() -> panel.updateDecanting(decants)) : null
		);
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
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, page))));
	}

	void onSpikesPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchSpikes(panel.getSpikesSortKey(), page,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, page))));
	}

	void onDipsPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchDips(panel.getDipsSortKey(), page,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateDips(items, total, page))));
	}

	void onDipsSortChanged(String sort)
	{
		executor.execute(() ->
			apiClient.fetchDips(sort, 0,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateDips(items, total, 0))));
	}

	void onDumpsPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchDumps(panel.getDumpsSortKey(),
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				page,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, page))));
	}

	void onAlertsPageChanged(int page)
	{
		executor.execute(() ->
			apiClient.fetchAlerts(page,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateAlerts(items, total, page))));
	}

	// -------------------------------------------------------------------------
	// Sort / filter / preset changes (always reset to page 0)
	// -------------------------------------------------------------------------

	void onSpikesSortChanged(String sort)
	{
		executor.execute(() ->
			apiClient.fetchSpikes(sort, 0,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, 0))));
	}

	void onDumpsSortChanged(String sort)
	{
		executor.execute(() ->
			apiClient.fetchDumps(sort,
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				0,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, 0))));
	}

	void onFlipsFilterChanged()
	{
		executor.execute(() ->
			apiClient.fetchFlips(panel.getSelectedPreset(),
				panel.getFlipsMinProfit(), panel.getFlipsPriceMin(), panel.getFlipsPriceMax(),
				0,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, 0))));
	}

	void onDumpsFilterChanged()
	{
		executor.execute(() ->
			apiClient.fetchDumps(panel.getDumpsSortKey(),
				panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
				0,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, 0))));
	}

	void onPresetChanged()
	{
		executor.execute(() ->
			apiClient.fetchFlips(panel.getSelectedPreset(),
				panel.getFlipsMinProfit(), panel.getFlipsPriceMin(), panel.getFlipsPriceMax(),
				0,
				(items, total) -> SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, 0))));
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

	@Provides
	O7FlipConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(O7FlipConfig.class);
	}
}
