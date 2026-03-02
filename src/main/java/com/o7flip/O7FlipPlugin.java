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

import com.google.inject.Provides;
import com.o7flip.model.BarrowsSet;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
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

	// -------------------------------------------------------------------------
	// Pending GE buy intent (set by panel right-click, cleared after use)
	// -------------------------------------------------------------------------

	volatile int    pendingGeBuyItemId = -1;
	volatile long   pendingGeBuyPrice  = -1;
	volatile String pendingGeBuyName   = null;

	/** Called by item panels on right-click to queue a GE buy pre-fill. */
	public void queueGeBuy(int itemId, long price, String name)
	{
		pendingGeBuyItemId = itemId;
		pendingGeBuyPrice  = price;
		pendingGeBuyName   = name;
		notifier.notify("Open the Grand Exchange to pre-fill your offer for " + name);
		log.debug("[07Flip] GE buy queued: {} ({}) @ {}", name, itemId, price);
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
		fetchAll();
		refreshTask = executor.scheduleAtFixedRate(
			this::fetchAll,
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

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.GRAND_EXCHANGE)
		{
			return;
		}
		if (pendingGeBuyItemId == -1)
		{
			return;
		}
		final int    itemId = pendingGeBuyItemId;
		final long   price  = pendingGeBuyPrice;
		final String name   = pendingGeBuyName;
		pendingGeBuyItemId = -1;
		pendingGeBuyPrice  = -1;
		pendingGeBuyName   = null;
		clientThread.invokeLater(() -> fillGeBuyOffer(itemId, price, name));
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

	void fetchAll()
	{
		SwingUtilities.invokeLater(() -> panel.setLoading(true));
		fetchAuthStatus();

		apiClient.fetchFlips(panel.getSelectedPreset(),
			panel.getFlipsMinProfit(), panel.getFlipsPriceMin(), panel.getFlipsPriceMax(),
			panel.getFlipsPage(),
			(items, total) -> SwingUtilities.invokeLater(() -> panel.updateFlips(items, total, panel.getFlipsPage())));

		apiClient.fetchSpikes(panel.getSpikesSortKey(), panel.getSpikesPage(),
			(items, total) -> SwingUtilities.invokeLater(() -> panel.updateSpikes(items, total, panel.getSpikesPage())));

		apiClient.fetchDips(panel.getDipsSortKey(), panel.getDipsPage(),
			(items, total) -> SwingUtilities.invokeLater(() -> panel.updateDips(items, total, panel.getDipsPage())));

		apiClient.fetchDumps(panel.getDumpsSortKey(),
			panel.getDumpsMinProfit(), panel.getDumpsPriceMin(), panel.getDumpsPriceMax(),
			panel.getDumpsPage(),
			(items, total) -> SwingUtilities.invokeLater(() -> panel.updateDumps(items, total, panel.getDumpsPage())));

		apiClient.fetchAlerts(panel.getAlertsPage(),
			(items, total) -> SwingUtilities.invokeLater(() -> panel.updateAlerts(items, total, panel.getAlertsPage())));

		apiClient.fetchBarrows(config.smithingLevel(),
			sets -> SwingUtilities.invokeLater(() -> panel.updateBarrows(sets)));

		apiClient.fetchMoon(config.smithingLevel(),
			sets -> SwingUtilities.invokeLater(() -> panel.updateMoon(sets)));

		apiClient.fetchDecanting(
			decants -> SwingUtilities.invokeLater(() -> panel.updateDecanting(decants)));
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
