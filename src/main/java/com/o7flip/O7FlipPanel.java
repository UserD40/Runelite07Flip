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

import com.o7flip.model.AlertItem;
import com.o7flip.model.BarrowsSet;
import com.o7flip.model.DecantItem;
import com.o7flip.model.DipItem;
import com.o7flip.model.DumpItem;
import com.o7flip.model.FlipItem;
import com.o7flip.model.MoonSet;
import com.o7flip.model.SearchResultItem;
import com.o7flip.model.SpikeItem;
import com.o7flip.model.TradeRecord;
import com.o7flip.ui.AlertItemPanel;
import com.o7flip.ui.BarrowsItemPanel;
import com.o7flip.ui.BarrowsSetPanel;
import com.o7flip.ui.DecantItemPanel;
import com.o7flip.ui.DipItemPanel;
import com.o7flip.ui.DumpItemPanel;
import com.o7flip.ui.FlipItemPanel;
import com.o7flip.ui.MoonSetPanel;
import com.o7flip.ui.SearchResultPanel;
import com.o7flip.ui.SpikeItemPanel;
import com.o7flip.ui.TradeRecordPanel;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.Box;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class O7FlipPanel extends PluginPanel
{
	// -------------------------------------------------------------------------
	// Colours / sizing
	// -------------------------------------------------------------------------
	private static final String WEBSITE_URL  = "https://07flip.com";
	private static final String DISCORD_URL  = "https://discord.gg/xQaYM9TaMr";
	private static final String RUNELITE_URL  = "https://07flip.com/runelite";
	private static final String SUBSCRIBE_URL = "https://07flip.com/subscribe";
	private static final Color  ORANGE       = new Color(0xFF981F);
	private static final Color  GREEN        = new Color(0x00C27A);
	private static final int    PAGE_SIZE    = 10;
	private static final int    FREE_ROWS    = 5;

	// -------------------------------------------------------------------------
	// Preset definitions — true = premium required
	// -------------------------------------------------------------------------
	private static final String[][] PRESETS = {
		{"",            "All Flips"},
		{"highVolume",  "High Volume"},
		{"highMargin",  "High Margin"},
		{"priceDip",    "Price Dip"},
		{"stableFlips", "Stable"},
		{"f2p",         "F2P Only"},
	};
	private static final boolean[] PREMIUM_PRESET = {false, true, false, true, true, false};

	// -------------------------------------------------------------------------
	// Client-side Flips filters
	// -------------------------------------------------------------------------
	private static final long[]   MIN_PROFITS       = {0, 100_000, 500_000, 1_000_000, 5_000_000};
	private static final String[] MIN_PROFIT_LABELS = {"Any Profit", "100K+", "500K+", "1M+", "5M+"};

	// Dumps profit thresholds — flip margin per item, much smaller than flip potential profit
	private static final long[]   DUMP_MIN_PROFITS       = {0, 1_000, 5_000, 25_000, 100_000};
	private static final String[] DUMP_MIN_PROFIT_LABELS = {"Any Profit", "1K+", "5K+", "25K+", "100K+"};

	// Price range: each entry is {lowerInclusive, upperExclusive}
	private static final long[][] PRICE_RANGES = {
		{0,               Long.MAX_VALUE},  // Any Price
		{0,               10_000},          // 0 – 10K
		{10_000,          50_000},          // 10K – 50K
		{50_000,          100_000},         // 50K – 100K
		{100_000,         500_000},         // 100K – 500K
		{500_000,         1_000_000},       // 500K – 1M
		{1_000_000,       5_000_000},       // 1M – 5M
		{5_000_000,       10_000_000},      // 5M – 10M
		{10_000_000,      25_000_000},      // 10M – 25M
		{25_000_000,      50_000_000},      // 25M – 50M
		{50_000_000,      100_000_000},     // 50M – 100M
		{100_000_000,     Long.MAX_VALUE},  // 100M+
	};
	private static final String[] PRICE_RANGE_LABELS = {
		"Any Price",
		"0 \u2013 10K",
		"10K \u2013 50K",
		"50K \u2013 100K",
		"100K \u2013 500K",
		"500K \u2013 1M",
		"1M \u2013 5M",
		"5M \u2013 10M",
		"10M \u2013 25M",
		"25M \u2013 50M",
		"50M \u2013 100M",
		"100M+",
	};

	private int flipsMinProfitIdx  = 0;
	private int flipsPriceRangeIdx = 0;
	private int dumpsMinProfitIdx  = 0;
	private int dumpsPriceRangeIdx = 0;

	// -------------------------------------------------------------------------
	// Server-side sort keys for spikes / dumps
	// -------------------------------------------------------------------------
	private String spikesSortKey = "recent";
	private String dipsSortKey   = "recent";
	private String dumpsSortKey  = "recent";

	// -------------------------------------------------------------------------
	// Auth state
	// -------------------------------------------------------------------------
	private boolean isSignedIn = false;
	private boolean isPremium  = false;
	private boolean noKeyBannerExpanded = true;

	// -------------------------------------------------------------------------
	// Stored data
	// -------------------------------------------------------------------------
	private List<FlipItem>    allFlips   = new ArrayList<>();
	private List<SpikeItem>   allSpikes  = new ArrayList<>();
	private List<DipItem>     allDips    = new ArrayList<>();
	private List<DumpItem>    allDumps   = new ArrayList<>();
	private List<BarrowsSet>  allBarrows = new ArrayList<>();
	private List<MoonSet>     allMoon    = new ArrayList<>();
	private List<DecantItem>  allDecants = new ArrayList<>();
	private List<AlertItem>   allAlerts  = new ArrayList<>();
	private List<TradeRecord> allMyFlips = new ArrayList<>();

	// -------------------------------------------------------------------------
	// Sort state
	// -------------------------------------------------------------------------
	private int flipsSortIdx   = 0;
	private int spikesSortIdx  = 0;
	private int dipsSortIdx    = 0;
	private int dumpsSortIdx   = 0;
	private int barrowsSortIdx = 0;
	private int moonFilterIdx  = 0;  // 0=Blood 1=Blue 2=Eclipse
	private int decantSortIdx  = 0;
	private int alertsSortIdx  = 0;

	// -------------------------------------------------------------------------
	// Page state (server-paginated tabs track total from server)
	// -------------------------------------------------------------------------
	private int flipsPage   = 0;  private int flipsTotal  = 0;
	private int spikesPage  = 0;  private int spikesTotal = 0;
	private int dipsPage    = 0;  private int dipsTotal   = 0;
	private int dumpsPage   = 0;  private int dumpsTotal  = 0;
	private int barrowsPage = 0;
	private int moonPage    = 0;
	private int decantPage  = 0;
	private int alertsPage  = 0;  private int alertsTotal = 0;

	// -------------------------------------------------------------------------
	// List panels
	// -------------------------------------------------------------------------
	private JPanel flipsListPanel;
	private JPanel spikesListPanel;
	private JPanel dipsListPanel;
	private JPanel dumpsListPanel;
	private JPanel barrowsListPanel;
	private JPanel barrowsDetailPanel;
	private JPanel barrowsTabCard;
	private JLabel barrowsDetailTitle;
	private JPanel moonListPanel;
	private JPanel decantListPanel;
	private JPanel alertsListPanel;
	private JPanel myFlipsListPanel;
	private JPanel searchResultsPanel;
	private JScrollPane searchScrollPane;

	// -------------------------------------------------------------------------
	// Sort buttons
	// -------------------------------------------------------------------------
	private JButton[] flipsSortBtns;
	private JButton[] spikesSortBtns;
	private JButton[] dipsSortBtns;
	private JButton[] dumpsSortBtns;
	private JButton[] barrowsSortBtns;
	private JButton[] moonFilterBtns;
	private JButton[] decantSortBtns;
	private JButton[] alertsSortBtns;

	// -------------------------------------------------------------------------
	// Page controls
	// -------------------------------------------------------------------------
	private JLabel  flipsPageLabel;   private JButton flipsPrev,    flipsNext;
	private JLabel  spikesPageLabel;  private JButton spikesPrev,   spikesNext;
	private JLabel  dipsPageLabel;    private JButton dipsPrev,     dipsNext;
	private JLabel  dumpsPageLabel;   private JButton dumpsPrev,    dumpsNext;
	private JLabel  barrowsPageLabel; private JButton barrowsPrev,  barrowsNext;
	private JLabel  moonPageLabel;    private JButton moonPrev,     moonNext;
	private JLabel  decantPageLabel;  private JButton decantPrev,   decantNext;
	private JLabel  alertsPageLabel;  private JButton alertsPrev,   alertsNext;

	// -------------------------------------------------------------------------
	// Other UI
	// -------------------------------------------------------------------------
	private final JComboBox<String> presetSelector;
	private JTextField searchField;
	private JLabel statusLabel;
	private JLabel lastUpdatedLabel;
	private JPanel mainArea;
	private Timer  searchDebounce;

	// -------------------------------------------------------------------------
	// Injected
	// -------------------------------------------------------------------------
	@Inject
	private O7FlipPlugin plugin;
	@Inject
	private ItemManager itemManager;
	@Inject
	private O7FlipConfig config;

	// -------------------------------------------------------------------------
	// Tabs wrapper (allows rebuilding without losing the CardLayout slot)
	// -------------------------------------------------------------------------
	private JPanel tabsWrapper;

	// -------------------------------------------------------------------------
	// Auth banner (shown below search, hidden when premium)
	// -------------------------------------------------------------------------
	private JPanel authBanner;

	// -------------------------------------------------------------------------
	// Invalid key warning (shown when API key is set but server says not connected)
	// -------------------------------------------------------------------------
	private JPanel invalidKeyBar;

	// -------------------------------------------------------------------------
	// North area (holds top panel, invalid-key bar, auth banner)
	// -------------------------------------------------------------------------
	private JPanel northArea;

	// =========================================================================
	// Constructor
	// =========================================================================
	public O7FlipPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		String[] labels = new String[PRESETS.length];
		for (int i = 0; i < PRESETS.length; i++)
		{
			labels[i] = PRESETS[i][1];
		}
		presetSelector = styledCombo(labels);
		presetSelector.setRenderer(buildPresetRenderer());
		presetSelector.addActionListener(e ->
		{
			int idx = presetSelector.getSelectedIndex();
			if (idx >= 0 && idx < PREMIUM_PRESET.length && PREMIUM_PRESET[idx] && !isPremium)
			{
				presetSelector.setSelectedIndex(0);
				return;
			}
			if (plugin != null)
			{
				plugin.onPresetChanged();
			}
		});

		tabsWrapper = new JPanel(new BorderLayout());
		tabsWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabsWrapper.add(buildTabs(), BorderLayout.CENTER);

		mainArea = new JPanel(new CardLayout());
		mainArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		mainArea.add(tabsWrapper,       "tabs");
		mainArea.add(buildSearchView(), "search");

		authBanner = new JPanel(new BorderLayout());
		authBanner.setVisible(false);

		invalidKeyBar = new JPanel(new BorderLayout());
		invalidKeyBar.setVisible(false);

		northArea = new JPanel(new BorderLayout());
		northArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		northArea.add(buildTopPanel(), BorderLayout.NORTH);
		northArea.add(invalidKeyBar,   BorderLayout.CENTER);
		northArea.add(authBanner,      BorderLayout.SOUTH);

		add(northArea,     BorderLayout.NORTH);
		add(mainArea,      BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);
	}

	// =========================================================================
	// Auth update
	// =========================================================================

	public void updateAuthStatus(boolean signedIn, boolean premium)
	{
		this.isSignedIn = signedIn;
		this.isPremium  = premium;
		presetSelector.repaint();
		updateAuthBanner();
		rebuildTabs();
	}

	private void updateAuthBanner()
	{
		authBanner.removeAll();

		if (isPremium)
		{
			authBanner.setVisible(false);
			authBanner.revalidate();
			authBanner.repaint();
			northArea.revalidate();
			northArea.repaint();
			return;
		}

		if (isSignedIn)
		{
			// ── Free account — upgrade prompt ─────────────────────────────────
			JPanel inner = new JPanel();
			inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
			inner.setOpaque(false);

			authBanner.setBackground(new Color(0x0D1A0D));
			authBanner.setBorder(BorderFactory.createCompoundBorder(
				new MatteBorder(1, 0, 1, 0, new Color(0x1E4A1E)),
				new EmptyBorder(9, 10, 9, 10)));

			bannerRow(inner, "\u2713 Free Account Connected",                        Fonts.BOLD, GREEN,               0);
			bannerRow(inner, "Unlock with Premium:",                                 Fonts.SM,   new Color(0x777777), 5);
			bannerRow(inner, "\u2022  Merch Alerts & live prices",                   Fonts.SM,   new Color(0xAAAAAA), 2);
			bannerRow(inner, "\u2022  High Volume, Price Dip & Stable flip presets", Fonts.SM,   new Color(0xAAAAAA), 1);
			bannerRow(inner, "\u2022  Full pagination & Moon / Barrows calculators", Fonts.SM,   new Color(0xAAAAAA), 1);

			inner.add(Box.createRigidArea(new Dimension(0, 9)));

			JButton upgradeBtn = pillButton("Upgrade to Premium");
			upgradeBtn.setBackground(ORANGE);
			upgradeBtn.setForeground(Color.BLACK);
			upgradeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
			upgradeBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
			upgradeBtn.addActionListener(e -> openUrl(SUBSCRIBE_URL));
			inner.add(upgradeBtn);

			authBanner.add(inner, BorderLayout.CENTER);
			authBanner.setVisible(true);
			authBanner.revalidate();
			authBanner.repaint();
			northArea.revalidate();
			northArea.repaint();
			return;
		}

		// ── Not signed in: separate State A (no key) from State D (has key) ──
		String key = config.apiKey();
		boolean noKey = key == null || key.trim().isEmpty();

		if (!noKey)
		{
			// State D — key is set but auth hasn't completed or failed.
			// The invalidKeyBar handles reconnection if the key is invalid.
			authBanner.setVisible(false);
			authBanner.revalidate();
			authBanner.repaint();
			northArea.revalidate();
			northArea.repaint();
			return;
		}

		// State A — no API key configured
		authBanner.setBackground(new Color(0x0D0D1E));
		authBanner.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(1, 0, 1, 0, new Color(0x2A2A55)),
			new EmptyBorder(0, 0, 0, 0)));

		if (!noKeyBannerExpanded)
		{
			// ── Collapsed pill ────────────────────────────────────────────────
			JPanel pill = new JPanel(new BorderLayout());
			pill.setOpaque(false);
			pill.setBorder(new EmptyBorder(4, 10, 4, 10));
			pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			JLabel pillLabel = new JLabel(
				"\uD83D\uDD11  Connect account to unlock features  \u25BC");
			pillLabel.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			pillLabel.setForeground(new Color(0xBBBBBB));
			pill.add(pillLabel, BorderLayout.CENTER);

			pill.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					noKeyBannerExpanded = true;
					updateAuthBanner();
				}
			});

			authBanner.add(pill, BorderLayout.CENTER);
			authBanner.setVisible(true);
			authBanner.revalidate();
			authBanner.repaint();
			northArea.revalidate();
			northArea.repaint();
			return;
		}

		// ── Expanded guide ────────────────────────────────────────────────────
		authBanner.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(1, 0, 1, 0, new Color(0x2A2A55)),
			new EmptyBorder(9, 10, 9, 10)));

		JPanel inner = new JPanel();
		inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
		inner.setOpaque(false);

		// Header row: title left, collapse chevron right
		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setOpaque(false);
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel titleLbl = new JLabel("\uD83D\uDD11 Connect Your Account");
		titleLbl.setFont(Fonts.BOLD);
		titleLbl.setForeground(ORANGE);
		headerRow.add(titleLbl, BorderLayout.CENTER);

		JButton chevron = new JButton("\u25B2");
		chevron.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		chevron.setForeground(new Color(0x777777));
		chevron.setBackground(null);
		chevron.setBorderPainted(false);
		chevron.setContentAreaFilled(false);
		chevron.setFocusPainted(false);
		chevron.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		chevron.setMargin(new Insets(0, 4, 0, 0));
		chevron.addActionListener(e ->
		{
			noKeyBannerExpanded = false;
			updateAuthBanner();
		});
		headerRow.add(chevron, BorderLayout.EAST);

		inner.add(headerRow);

		bannerRow(inner, "An API key unlocks more flip presets.", Fonts.SM, new Color(0x777777), 4);
		bannerRow(inner, "Premium subscription required for full access.", Fonts.SM, new Color(0x777777), 0);

		inner.add(Box.createRigidArea(new Dimension(0, 10)));

		bannerRow(inner, "GET YOUR KEY:", Fonts.SM_BOLD, new Color(0x999999), 0);
		bannerRow(inner, "1.  Visit 07flip.com/runelite and sign up",         Fonts.SM, new Color(0xDDDDDD), 3);
		bannerRow(inner, "2.  Log in with Discord",                           Fonts.SM, new Color(0xDDDDDD), 1);
		bannerRow(inner, "3.  Click your Discord user icon (top-right)",      Fonts.SM, new Color(0xDDDDDD), 1);
		bannerRow(inner, "     \u2192  Select \u201CView API Key\u201D",      Fonts.SM, new Color(0xFF981F), 0);
		bannerRow(inner, "4.  Copy the key shown on screen",                  Fonts.SM, new Color(0xDDDDDD), 1);

		inner.add(Box.createRigidArea(new Dimension(0, 9)));

		bannerRow(inner, "ADD KEY IN RUNELITE:", Fonts.SM_BOLD, new Color(0x999999), 0);
		bannerRow(inner, "1.  Open RuneLite plugin settings",                 Fonts.SM, new Color(0xDDDDDD), 3);
		bannerRow(inner, "2.  Find 07Flip and click the spanner icon",        Fonts.SM, new Color(0xDDDDDD), 1);
		bannerRow(inner, "3.  Paste key into the API Key field",              Fonts.SM, new Color(0xDDDDDD), 1);
		bannerRow(inner, "4.  Press Enter \u2014 done!",                      Fonts.SM, new Color(0xDDDDDD), 1);

		inner.add(Box.createRigidArea(new Dimension(0, 10)));

		JButton visitBtn = pillButton("Visit 07flip.com/runelite");
		visitBtn.setBackground(ORANGE);
		visitBtn.setForeground(Color.BLACK);
		visitBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		visitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		visitBtn.addActionListener(e -> openUrl(RUNELITE_URL));
		inner.add(visitBtn);

		authBanner.add(inner, BorderLayout.CENTER);
		authBanner.setVisible(true);
		authBanner.revalidate();
		authBanner.repaint();
		northArea.revalidate();
		northArea.repaint();
	}

	public void updateInvalidKeyWarning(String connectUrl)
	{
		invalidKeyBar.removeAll();
		if (connectUrl == null || connectUrl.isEmpty())
		{
			invalidKeyBar.setVisible(false);
			invalidKeyBar.revalidate();
			invalidKeyBar.repaint();
			return;
		}
		JLabel lbl = new JLabel("\u26A0 API key invalid \u2014 click to reconnect");
		lbl.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		lbl.setForeground(new Color(0xFF6B6B));
		lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		lbl.setBorder(new EmptyBorder(5, 10, 5, 10));
		lbl.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(connectUrl);
			}
		});
		invalidKeyBar.setBackground(new Color(0x2A0000));
		invalidKeyBar.setBorder(new MatteBorder(1, 0, 1, 0, new Color(0x660000)));
		invalidKeyBar.add(lbl, BorderLayout.CENTER);
		invalidKeyBar.setVisible(true);
		invalidKeyBar.revalidate();
		invalidKeyBar.repaint();
	}

	private static void bannerRow(JPanel panel, String text, java.awt.Font font, Color color, int topPad)
	{
		JLabel lbl = new JLabel(text);
		lbl.setFont(font);
		lbl.setForeground(color);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (topPad > 0)
		{
			lbl.setBorder(new EmptyBorder(topPad, 0, 0, 0));
		}
		panel.add(lbl);
	}

	// =========================================================================
	// Public update methods
	// =========================================================================

	public String getSelectedPreset()
	{
		int i = presetSelector.getSelectedIndex();
		return i >= 0 ? PRESETS[i][0] : "";
	}

	public String getSpikesSortKey()
	{
		return spikesSortKey;
	}

	public String getDipsSortKey()
	{
		return dipsSortKey;
	}

	public String getDumpsSortKey()
	{
		return dumpsSortKey;
	}

	public long getFlipsMinProfit()
	{
		return flipsMinProfitIdx > 0 ? MIN_PROFITS[flipsMinProfitIdx] : 0;
	}

	public long getFlipsPriceMin()
	{
		return flipsPriceRangeIdx > 0 ? PRICE_RANGES[flipsPriceRangeIdx][0] : 0;
	}

	public long getFlipsPriceMax()
	{
		return flipsPriceRangeIdx > 0 ? PRICE_RANGES[flipsPriceRangeIdx][1] : Long.MAX_VALUE;
	}

	public long getDumpsMinProfit()
	{
		return dumpsMinProfitIdx > 0 ? DUMP_MIN_PROFITS[dumpsMinProfitIdx] : 0;
	}

	public long getDumpsPriceMin()
	{
		return dumpsPriceRangeIdx > 0 ? PRICE_RANGES[dumpsPriceRangeIdx][0] : 0;
	}

	public long getDumpsPriceMax()
	{
		return dumpsPriceRangeIdx > 0 ? PRICE_RANGES[dumpsPriceRangeIdx][1] : Long.MAX_VALUE;
	}

	public int getFlipsPage()
	{
		return flipsPage;
	}

	public int getSpikesPage()
	{
		return spikesPage;
	}

	public int getDipsPage()
	{
		return dipsPage;
	}

	public int getDumpsPage()
	{
		return dumpsPage;
	}

	public int getAlertsPage()
	{
		return alertsPage;
	}

	public void setLoading(boolean loading)
	{
		statusLabel.setText(loading ? "\u25CF Fetching..." : "\u25CF Live");
		statusLabel.setForeground(loading ? new Color(0xFFAA00) : GREEN);
	}

	public void updateFlips(List<FlipItem> items, int total, int page)
	{
		allFlips = items;
		flipsTotal = total;
		flipsPage = page;
		renderFlips(filtered());
		updateTimestamp();
		setLoading(false);
	}

	public void updateSpikes(List<SpikeItem> items, int total, int page)
	{
		allSpikes = items;
		spikesTotal = total;
		spikesPage = page;
		renderSpikes(filtered());
	}

	public void updateDips(List<DipItem> items, int total, int page)
	{
		allDips = items;
		dipsTotal = total;
		dipsPage = page;
		renderDips(filtered());
	}

	public void updateDumps(List<DumpItem> items, int total, int page)
	{
		allDumps = items;
		dumpsTotal = total;
		dumpsPage = page;
		renderDumps(filtered());
	}

	public void updateAlerts(List<AlertItem> items, int total, int page)
	{
		allAlerts = items;
		alertsTotal = total;
		alertsPage = page;
		renderAlerts(filtered());
		setLoading(false);
	}

	public void updateMyFlips(List<TradeRecord> records)
	{
		allMyFlips = new ArrayList<>(records);
		renderMyFlips();
	}

	public void updateBarrows(List<BarrowsSet> i)
	{
		allBarrows = i;
		barrowsPage = 0;
		renderBarrows(filtered());
	}

	public void updateMoon(List<MoonSet> i)
	{
		allMoon = i;
		moonPage = 0;
		renderMoon(filtered());
	}

	public void updateDecanting(List<DecantItem> i)
	{
		allDecants = i;
		decantPage = 0;
		renderDecants(filtered());
	}

	// =========================================================================
	// Search
	// =========================================================================

	private String filtered()
	{
		return searchField == null ? "" : searchField.getText().trim();
	}

	private void onSearchChanged()
	{
		String q = filtered();
		CardLayout cl = (CardLayout) mainArea.getLayout();

		if (searchDebounce != null)
		{
			searchDebounce.stop();
		}

		if (q.isEmpty())
		{
			cl.show(mainArea, "tabs");
			renderFlips("");
			renderSpikes("");
			renderDips("");
			renderDumps("");
			renderBarrows("");
			renderMoon("");
			renderDecants("");
			renderAlerts("");
			return;
		}

		cl.show(mainArea, "search");

		if (q.length() < 2)
		{
			renderSearchMessage("Type at least 2 characters\u2026");
			return;
		}

		renderSearchMessage("Searching\u2026");
		searchDebounce = new Timer(300, e ->
		{
			if (plugin != null)
			{
				plugin.searchItems(q);
			}
		});
		searchDebounce.setRepeats(false);
		searchDebounce.start();
	}

	/**
	 * Multi-word, order-independent match.
	 * Every space-separated token must appear somewhere in the text.
	 * e.g. "hat party" matches "Blue party hat".
	 */
	private static boolean matches(String text, String query)
	{
		if (text == null || text.isEmpty())
		{
			return false;
		}
		String lower = text.toLowerCase();
		for (String token : query.toLowerCase().split("\\s+"))
		{
			if (!token.isEmpty() && !lower.contains(token))
			{
				return false;
			}
		}
		return true;
	}

	// Per-tab filter helpers
	private List<FlipItem>   fFlips(String q)
	{
		return q.isEmpty() ? allFlips : allFlips.stream().filter(i -> matches(i.name, q)).collect(Collectors.toList());
	}

	private List<SpikeItem>  fSpikes(String q)
	{
		return q.isEmpty() ? allSpikes : allSpikes.stream().filter(i -> matches(i.name, q)).collect(Collectors.toList());
	}

	private List<DipItem>    fDips(String q)
	{
		return q.isEmpty() ? allDips : allDips.stream().filter(i -> matches(i.name, q)).collect(Collectors.toList());
	}

	private List<DumpItem>   fDumps(String q)
	{
		return q.isEmpty() ? allDumps : allDumps.stream().filter(i -> matches(i.name, q)).collect(Collectors.toList());
	}

	private List<BarrowsSet> fBarrows(String q)
	{
		return q.isEmpty() ? allBarrows : allBarrows.stream().filter(i -> matches(i.setName + " " + i.shortName, q)).collect(Collectors.toList());
	}

	private List<MoonSet> fMoon(String q)
	{
		List<MoonSet> base = allMoon;
		if (moonFilterIdx == 0)
		{
			base = base.stream().filter(s -> s.setName.contains("Blood")).collect(Collectors.toList());
		}
		else if (moonFilterIdx == 1)
		{
			base = base.stream().filter(s -> s.setName.contains("Blue")).collect(Collectors.toList());
		}
		else if (moonFilterIdx == 2)
		{
			base = base.stream().filter(s -> s.setName.contains("Eclipse")).collect(Collectors.toList());
		}
		if (!q.isEmpty())
		{
			base = base.stream().filter(s -> matches(s.setName, q)).collect(Collectors.toList());
		}
		return base;
	}

	private List<DecantItem> fDecants(String q)
	{
		return q.isEmpty() ? allDecants : allDecants.stream().filter(i -> matches(i.potionName, q)).collect(Collectors.toList());
	}

	private List<AlertItem>  fAlerts(String q)
	{
		return q.isEmpty() ? allAlerts : allAlerts.stream().filter(i -> matches(i.name, q)).collect(Collectors.toList());
	}

	// =========================================================================
	// Sort helpers
	// =========================================================================

	private List<FlipItem> sortFlips(List<FlipItem> items)
	{
		Comparator<FlipItem> c = flipsSortIdx == 1
			? Comparator.comparingDouble((FlipItem x) -> x.roiPct)
			: Comparator.comparingLong((FlipItem x) -> x.profit);
		return items.stream().sorted(c.reversed()).collect(Collectors.toList());
	}

	private List<DumpItem> sortDumps(List<DumpItem> items)
	{
		if (dumpsSortIdx == 1)  // Score
		{
			return items.stream().sorted(Comparator.comparingInt((DumpItem x) -> x.dumpScore).reversed())
				.collect(Collectors.toList());
		}
		// Default idx == 0: Recent — smallest lastDumpHoursAgo first, nulls last
		return items.stream().sorted((a, b) ->
		{
			if (a.lastDumpHoursAgo == null && b.lastDumpHoursAgo == null)
			{
				return 0;
			}
			if (a.lastDumpHoursAgo == null)
			{
				return 1;
			}
			if (b.lastDumpHoursAgo == null)
			{
				return -1;
			}
			return Double.compare(a.lastDumpHoursAgo, b.lastDumpHoursAgo);
		}).collect(Collectors.toList());
	}

	private List<BarrowsSet> sortBarrows(List<BarrowsSet> items)
	{
		Comparator<BarrowsSet> c = barrowsSortIdx == 1 ? Comparator.comparingLong((BarrowsSet x) -> x.totalBrokenCost)
			: Comparator.comparingLong((BarrowsSet x) -> x.bestProfit);
		return barrowsSortIdx == 1 ? items.stream().sorted(c).collect(Collectors.toList())
			: items.stream().sorted(c.reversed()).collect(Collectors.toList());
	}

	private List<DecantItem> sortDecants(List<DecantItem> items)
	{
		Comparator<DecantItem> c = decantSortIdx == 1 ? Comparator.comparingDouble((DecantItem x) -> x.roiPct)
			: decantSortIdx == 2 ? Comparator.comparingInt((DecantItem x) -> x.dailyVolume)
			: Comparator.comparingLong((DecantItem x) -> x.profitPer4dose);
		return items.stream().sorted(c.reversed()).collect(Collectors.toList());
	}

	private List<AlertItem> sortAlerts(List<AlertItem> items)
	{
		Comparator<AlertItem> c;
		if (alertsSortIdx == 1)
		{
			c = Comparator.comparingDouble((AlertItem x) -> x.upsidePct);
		}
		else if (alertsSortIdx == 2)
		{
			c = Comparator.comparingDouble((AlertItem x) -> x.drawdownPct);
		}
		else
		{
			c = Comparator.comparing((AlertItem x) -> x.detectedAt);
		}
		return items.stream().sorted(c.reversed()).collect(Collectors.toList());
	}

	// =========================================================================
	// Tab renderers
	// =========================================================================

	private void renderFlips(String q)
	{
		List<FlipItem> list = sortFlips(fFlips(q));
		fillListPaged(flipsListPanel, list, flipsPage, flipsTotal,
			flipsPageLabel, flipsPrev, flipsNext,
			(item, odd) -> new FlipItemPanel(item, itemManager, odd, plugin),
			"No flips found", "Try a different preset or filter");
		hilite(flipsSortBtns, flipsSortIdx);
	}

	private void renderSpikes(String q)
	{
		fillListPaged(spikesListPanel, fSpikes(q), spikesPage, spikesTotal,
			spikesPageLabel, spikesPrev, spikesNext,
			(item, odd) -> new SpikeItemPanel(item, itemManager, odd, plugin),
			"No spike signals", "Check back soon");
		hilite(spikesSortBtns, spikesSortIdx);
	}

	private void renderDips(String q)
	{
		fillListPaged(dipsListPanel, fDips(q), dipsPage, dipsTotal,
			dipsPageLabel, dipsPrev, dipsNext,
			(item, odd) -> new DipItemPanel(item, itemManager, odd, plugin),
			"No dip signals", "Check back soon");
		hilite(dipsSortBtns, dipsSortIdx);
	}

	private void renderDumps(String q)
	{
		fillListPaged(dumpsListPanel, sortDumps(fDumps(q)), dumpsPage, dumpsTotal,
			dumpsPageLabel, dumpsPrev, dumpsNext,
			(item, odd) -> new DumpItemPanel(item, itemManager, odd, plugin),
			"No dump signals", "Check back soon");
		hiliteFilter(dumpsSortBtns, dumpsSortIdx);
	}

	private void renderBarrows(String q)
	{
		fillList(barrowsListPanel, sortBarrows(fBarrows(q)), barrowsPage, barrowsPageLabel, barrowsPrev, barrowsNext,
			(item, odd) -> new BarrowsSetPanel(item, itemManager, odd, plugin,
				() ->
				{
					if (plugin != null)
					{
						plugin.onBarrowsSetClicked(item);
					}
				}),
			"No Barrows data", "");
		hilite(barrowsSortBtns, barrowsSortIdx);
	}

	/** Switches the Barrows tab to the detail view and renders per-item rows. */
	public void showBarrowsDetail(BarrowsSet set)
	{
		barrowsDetailTitle.setText(set.setName != null && !set.setName.isEmpty() ? set.setName : set.shortName);
		barrowsDetailPanel.removeAll();

		// Set summary strip
		barrowsDetailPanel.add(buildBarrowsDetailSummary(set));
		barrowsDetailPanel.add(sep());

		// Per-item rows
		for (int i = 0; i < set.items.size(); i++)
		{
			barrowsDetailPanel.add(new BarrowsItemPanel(set.items.get(i), itemManager, i % 2 != 0, plugin));
			barrowsDetailPanel.add(sep());
		}

		barrowsDetailPanel.revalidate();
		barrowsDetailPanel.repaint();

		((CardLayout) barrowsTabCard.getLayout()).show(barrowsTabCard, "detail");
	}

	/** Switches the Barrows tab back to the list view. */
	private void showBarrowsList()
	{
		((CardLayout) barrowsTabCard.getLayout()).show(barrowsTabCard, "list");
	}

	/** Small summary panel shown at the top of the drill-down detail view. */
	private JPanel buildBarrowsDetailSummary(BarrowsSet set)
	{
		JPanel p = new JPanel(new GridLayout(2, 2, 8, 2));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.setBorder(new EmptyBorder(8, 10, 8, 10));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean profitable = set.bestProfit > 0;
		String profitColor = profitable ? "#00C27A" : "#FF5555";
		String profitSign  = profitable ? "+" : "";
		String strat = "sell_set".equals(set.bestStrategy) ? "sell as set" : "sell individual";

		p.add(detailCell("<html><font color='#888888'>Buy all: </font>"
			+ "<font color='#FF7070'>" + FlipItemPanel.formatGp(set.totalBrokenCost) + "</font></html>"));
		p.add(detailCell("<html><font color='#888888'>NPC rpr: </font>"
			+ FlipItemPanel.formatGp(set.totalNpcRepairCost) + "</html>"));
		p.add(detailCell("<html><font color='#888888'>Best: </font>"
			+ "<font color='" + profitColor + "'><b>" + profitSign + FlipItemPanel.formatGp(set.bestProfit) + "</b></font></html>"));
		p.add(detailCell("<html><font color='#888888'>POH rpr: </font>"
			+ FlipItemPanel.formatGp(set.totalPohRepairCost) + "</html>"));

		return p;
	}

	private static JLabel detailCell(String html)
	{
		JLabel l = new JLabel(html);
		l.setFont(Fonts.SM);
		return l;
	}

	private void renderMoon(String q)
	{
		fillList(moonListPanel, fMoon(q), moonPage, moonPageLabel, moonPrev, moonNext,
			(item, odd) -> new MoonSetPanel(item, itemManager, odd, plugin),
			"No Moon armour data", "");
		hiliteFilter(moonFilterBtns, moonFilterIdx);
	}

	private void renderDecants(String q)
	{
		fillList(decantListPanel, sortDecants(fDecants(q)), decantPage, decantPageLabel, decantPrev, decantNext,
			(item, odd) -> new DecantItemPanel(item, itemManager, odd),
			"No decanting opportunities", "");
		hilite(decantSortBtns, decantSortIdx);
	}

	private void renderAlerts(String q)
	{
		fillListPaged(alertsListPanel, sortAlerts(fAlerts(q)), alertsPage, alertsTotal,
			alertsPageLabel, alertsPrev, alertsNext,
			(item, odd) -> new AlertItemPanel(item, itemManager, odd, plugin),
			"No active price alerts", "Alerts posted twice daily");
		hilite(alertsSortBtns, alertsSortIdx);
	}

	private void renderMyFlips()
	{
		if (myFlipsListPanel == null)
		{
			return;
		}
		myFlipsListPanel.removeAll();
		if (allMyFlips.isEmpty())
		{
			myFlipsListPanel.add(emptyLabel("No trades recorded yet", "Completed GE buys and sells appear here"));
		}
		else
		{
			List<TradeRecord> reversed = new ArrayList<>(allMyFlips);
			Collections.reverse(reversed);
			for (int i = 0; i < reversed.size(); i++)
			{
				myFlipsListPanel.add(new TradeRecordPanel(reversed.get(i), itemManager, i % 2 != 0));
				myFlipsListPanel.add(sep());
			}
		}
		myFlipsListPanel.revalidate();
		myFlipsListPanel.repaint();
	}

	/** Shows a plain status/placeholder message in the search panel. */
	private void renderSearchMessage(String message)
	{
		searchResultsPanel.removeAll();
		JLabel lbl = new JLabel(message);
		lbl.setFont(Fonts.SM);
		lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lbl.setBorder(new EmptyBorder(16, 12, 16, 12));
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchResultsPanel.add(lbl);
		searchResultsPanel.revalidate();
		searchResultsPanel.repaint();
	}

	/** Called by the plugin when the API search returns results. */
	public void showSearchResults(List<SearchResultItem> items, String query)
	{
		// Ignore stale callbacks if the user has already changed the query
		if (!query.equals(filtered()))
		{
			return;
		}

		searchResultsPanel.removeAll();

		if (items.isEmpty())
		{
			JLabel none = new JLabel("No results for \u201C" + query + "\u201D");
			none.setFont(Fonts.SM);
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			none.setBorder(new EmptyBorder(16, 12, 16, 12));
			none.setAlignmentX(Component.LEFT_ALIGNMENT);
			searchResultsPanel.add(none);
		}
		else
		{
			for (int i = 0; i < items.size(); i++)
			{
				searchResultsPanel.add(new SearchResultPanel(items.get(i), itemManager, i % 2 != 0));
				searchResultsPanel.add(sep());
			}
		}

		searchResultsPanel.revalidate();
		searchResultsPanel.repaint();
		searchScrollPane.revalidate();
		searchScrollPane.repaint();
	}

	// =========================================================================
	// Locked panel (premium feature)
	// =========================================================================

	private void renderLocked(JPanel panel, String title, String sub)
	{
		panel.removeAll();

		JPanel inner = new JPanel();
		inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
		inner.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inner.setBorder(new EmptyBorder(20, 14, 20, 14));
		inner.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = new JLabel("\uD83D\uDD12 " + title);
		icon.setFont(Fonts.BOLD);
		icon.setForeground(ORANGE);
		icon.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel desc = new JLabel("<html>" + sub + "<br><br>Get premium at <b>07flip.com/subscribe</b>.</html>");
		desc.setFont(Fonts.SM);
		desc.setForeground(new Color(0x888888));
		desc.setBorder(new EmptyBorder(6, 0, 14, 0));
		desc.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton btn = pillButton("Get Premium");
		btn.setBackground(ORANGE);
		btn.setForeground(Color.BLACK);
		btn.addActionListener(e -> openUrl(SUBSCRIBE_URL));

		inner.add(icon);
		inner.add(desc);
		inner.add(btn);

		panel.add(inner);
		panel.revalidate();
		panel.repaint();
	}

	// =========================================================================
	// Generic list filler with pagination and auth-gating
	// =========================================================================

	@FunctionalInterface
	private interface RowFactory<T>
	{
		JComponent build(T item, boolean odd);
	}

	@FunctionalInterface
	interface IntSupplier
	{
		int get();
	}

	@FunctionalInterface
	interface IntConsumer
	{
		void accept(int v);
	}

	/**
	 * Server-paginated variant: items is exactly one page from the server.
	 * serverTotal is the total item count across all pages.
	 * page/prev/next are for display and navigation only.
	 */
	private <T> void fillListPaged(JPanel panel, List<T> items, int page, int serverTotal,
		JLabel pageLabel, JButton prev, JButton next,
		RowFactory<T> factory, String emptyTitle, String emptySub)
	{
		panel.removeAll();
		int ps    = isSignedIn ? PAGE_SIZE : FREE_ROWS;
		int total = Math.max(serverTotal, items.size());
		int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
		int end   = Math.min(items.size(), ps);

		if (total == 0 || items.isEmpty())
		{
			panel.add(emptyLabel(emptyTitle, emptySub));
		}
		else
		{
			for (int i = 0; i < end; i++)
			{
				panel.add(factory.build(items.get(i), i % 2 != 0));
				panel.add(sep());
			}
			if (!isSignedIn && total > FREE_ROWS)
			{
				panel.add(signInPrompt(total - FREE_ROWS));
			}
		}

		pageLabel.setText(isSignedIn && total > 0 ? (page + 1) + " / " + pages : "");
		prev.setEnabled(isSignedIn && page > 0);
		next.setEnabled(isSignedIn && page < pages - 1);
		panel.revalidate();
		panel.repaint();
	}

	private <T> void fillList(JPanel panel, List<T> items, int page,
		JLabel pageLabel, JButton prev, JButton next,
		RowFactory<T> factory, String emptyTitle, String emptySub)
	{
		panel.removeAll();
		int ps    = isSignedIn ? PAGE_SIZE : FREE_ROWS;
		int total = items.size();
		int pages = Math.max(1, (int) Math.ceil(total / (double) ps));
		int safe  = isSignedIn ? Math.min(page, pages - 1) : 0;
		int start = safe * ps;
		int end   = Math.min(start + ps, total);

		if (total == 0)
		{
			panel.add(emptyLabel(emptyTitle, emptySub));
		}
		else
		{
			for (int i = start; i < end; i++)
			{
				panel.add(factory.build(items.get(i), i % 2 != 0));
				panel.add(sep());
			}
			if (!isSignedIn && total > FREE_ROWS)
			{
				panel.add(signInPrompt(total - FREE_ROWS));
			}
		}

		pageLabel.setText(isSignedIn && total > 0 ? (safe + 1) + " / " + pages : "");
		prev.setEnabled(isSignedIn && safe > 0);
		next.setEnabled(isSignedIn && safe < pages - 1);
		panel.revalidate();
		panel.repaint();
	}

	// =========================================================================
	// Build top panel
	// =========================================================================

	private JPanel buildTopPanel()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(10, 12, 6, 12));

		JLabel title = new JLabel("07Flip");
		title.setFont(Fonts.TITLE);
		title.setForeground(ORANGE);

		statusLabel = new JLabel("\u25CF Loading");
		statusLabel.setFont(Fonts.SM);
		statusLabel.setForeground(new Color(0xFFAA00));

		header.add(title,       BorderLayout.WEST);
		header.add(statusLabel, BorderLayout.EAST);

		// Search field with placeholder
		searchField = new JTextField()
		{
			private static final String HINT = "Search all items\u2026";

			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				if (getText().isEmpty() && !isFocusOwner())
				{
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setColor(new Color(0x555555));
					g2.setFont(getFont());
					FontMetrics fm = g2.getFontMetrics();
					Insets ins = getInsets();
					g2.drawString(HINT, ins.left, ins.top + fm.getAscent() + (getHeight() - ins.top - ins.bottom - fm.getHeight()) / 2);
					g2.dispose();
				}
			}
		};
		searchField.setBackground(new Color(0x1E1E1E));
		searchField.setForeground(Color.WHITE);
		searchField.setCaretColor(Color.WHITE);
		searchField.setFont(Fonts.SM);
		searchField.setBorder(new EmptyBorder(5, 8, 5, 4));

		// ── Clear (×) button — shown only when the field has text ─────────────
		JLabel clearBtn = new JLabel("\u00D7", SwingConstants.CENTER);
		clearBtn.setFont(Fonts.BOLD);
		clearBtn.setForeground(new Color(0x666666));
		clearBtn.setBorder(new EmptyBorder(0, 6, 0, 8));
		clearBtn.setPreferredSize(new Dimension(28, 28));
		clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		clearBtn.setVisible(false);
		clearBtn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				searchField.setText("");
				searchField.requestFocus();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				clearBtn.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				clearBtn.setForeground(new Color(0x666666));
			}
		});

		// ── Wrapper panel provides the outer border around field + button ──────
		JPanel searchBox = new JPanel(new BorderLayout());
		searchBox.setBackground(new Color(0x1E1E1E));
		searchBox.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(1, 1, 1, 1, new Color(0x4A4A4A)),
			BorderFactory.createEmptyBorder()));
		searchBox.add(searchField, BorderLayout.CENTER);
		searchBox.add(clearBtn,    BorderLayout.EAST);

		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				clearBtn.setVisible(!searchField.getText().isEmpty());
				onSearchChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				clearBtn.setVisible(!searchField.getText().isEmpty());
				onSearchChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				clearBtn.setVisible(!searchField.getText().isEmpty());
				onSearchChanged();
			}
		});

		JPanel searchRow = new JPanel(new BorderLayout());
		searchRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchRow.setBorder(new EmptyBorder(0, 12, 8, 12));
		searchRow.add(searchBox, BorderLayout.CENTER);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		top.add(header,    BorderLayout.NORTH);
		top.add(searchRow, BorderLayout.SOUTH);
		return top;
	}

	// =========================================================================
	// Build tabs
	// =========================================================================

	private JTabbedPane buildTabs()
	{
		JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.setForeground(Color.WHITE);
		tabs.setFont(Fonts.SM);

		// Always build all tab content to initialise list-panel fields,
		// then conditionally add each tab based on config + auth state.
		JPanel flipsContent    = buildFlipsTab();
		JPanel dumpsContent    = buildDumpsTab();
		JPanel spikesContent   = buildSpikesTab();
		JPanel dipsContent     = buildDipsTab();
		JPanel alertsContent   = buildGenericTab("Merch");
		JPanel moonContent     = buildMoonTab();
		JPanel barrowsContent  = buildGenericTab("Barrows");
		JPanel decantContent   = buildGenericTab("Decant");
		JPanel myFlipsContent  = buildMyFlipsTab();

		if (config == null || config.showFlips())                          tabs.addTab("Flips",     flipsContent);
		if (config == null || config.showDumps())                          tabs.addTab("Dumps",     dumpsContent);
		if (config == null || config.showSpikes())                         tabs.addTab("Spikes",    spikesContent);
		if (config == null || config.showDips())                           tabs.addTab("Dips",      dipsContent);
		if ((config == null || config.showAlerts()) && isPremium)          tabs.addTab("Alerts",    alertsContent);
		if ((config == null || config.showMoon()) && isSignedIn)           tabs.addTab("Moon",      moonContent);
		if ((config == null || config.showBarrows()) && isSignedIn)        tabs.addTab("Barrows",   barrowsContent);
		if (config == null || config.showDecant())                         tabs.addTab("Decant",    decantContent);
		if (config == null || config.showMyFlips())                        tabs.addTab("My Trades", myFlipsContent);

		return tabs;
	}

	private JPanel buildMyFlipsTab()
	{
		myFlipsListPanel = listPanel();

		JButton clearBtn = pillButton("Clear History");
		clearBtn.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.clearTradeHistory();
			}
		});

		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.setBorder(new EmptyBorder(6, 10, 6, 10));
		topBar.add(clearBtn, BorderLayout.EAST);

		renderMyFlips();

		JPanel footer = new JPanel();
		footer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		return assembleTab(topBar, myFlipsListPanel, footer);
	}

	/**
	 * Switches to the named tab if it is currently visible.
	 * Returns true if the tab was found and selected, false otherwise.
	 * Must be called on the EDT.
	 */
	public boolean selectTab(String tabName)
	{
		if (tabsWrapper.getComponentCount() == 0)
		{
			return false;
		}
		java.awt.Component c = tabsWrapper.getComponent(0);
		if (!(c instanceof JTabbedPane))
		{
			return false;
		}
		JTabbedPane pane = (JTabbedPane) c;
		for (int i = 0; i < pane.getTabCount(); i++)
		{
			if (tabName.equals(pane.getTitleAt(i)))
			{
				pane.setSelectedIndex(i);
				return true;
			}
		}
		return false;
	}

	public void rebuildTabs()
	{
		tabsWrapper.removeAll();
		tabsWrapper.add(buildTabs(), BorderLayout.CENTER);
		tabsWrapper.revalidate();
		tabsWrapper.repaint();
		String q = filtered();
		renderFlips(q);
		renderSpikes(q);
		renderDips(q);
		renderDumps(q);
		renderBarrows(q);
		renderMoon(q);
		renderDecants(q);
		renderAlerts(q);
	}

	private JPanel buildFlipsTab()
	{
		JPanel presetRow = new JPanel(new BorderLayout(4, 0));
		presetRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		presetRow.setBorder(new EmptyBorder(4, 8, 0, 8));
		presetRow.add(presetSelector, BorderLayout.CENTER);

		JComboBox<String> minProfitCb = styledCombo(MIN_PROFIT_LABELS);
		minProfitCb.addActionListener(e ->
		{
			flipsMinProfitIdx = minProfitCb.getSelectedIndex();
			flipsPage = 0;
			renderFlips(filtered());
			if (plugin != null)
			{
				plugin.onFlipsFilterChanged();
			}
		});

		JComboBox<String> priceRangeCb = styledCombo(PRICE_RANGE_LABELS);
		priceRangeCb.addActionListener(e ->
		{
			flipsPriceRangeIdx = priceRangeCb.getSelectedIndex();
			flipsPage = 0;
			renderFlips(filtered());
			if (plugin != null)
			{
				plugin.onFlipsFilterChanged();
			}
		});

		JPanel filterRow = new JPanel(new GridLayout(1, 2, 4, 0));
		filterRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		filterRow.setBorder(new EmptyBorder(4, 8, 0, 8));
		filterRow.add(minProfitCb);
		filterRow.add(priceRangeCb);

		flipsSortBtns = new JButton[2];
		JPanel sortRow = buildSortBar(flipsSortBtns, new String[]{"Profit", "ROI %"},
			() -> flipsSortIdx, i ->
			{
				flipsSortIdx = i;
				flipsPage = 0;
				renderFlips(filtered());
			});

		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(presetRow,  BorderLayout.NORTH);
		topBar.add(filterRow,  BorderLayout.CENTER);
		topBar.add(sortRow,    BorderLayout.SOUTH);

		flipsListPanel = listPanel();
		flipsPageLabel = pageLabel();
		flipsPrev      = pageBtn("\u2039");
		flipsNext      = pageBtn("\u203A");
		flipsPrev.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.onFlipsPageChanged(--flipsPage);
			}
		});
		flipsNext.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.onFlipsPageChanged(++flipsPage);
			}
		});

		return assembleTab(topBar, flipsListPanel, buildPageBar(flipsPageLabel, flipsPrev, flipsNext));
	}

	private JPanel buildMoonTab()
	{
		// Filter bar: Blood Moon | Blue Moon | Eclipse Moon
		moonFilterBtns = new JButton[3];
		String[] labels = {"Blood", "Blue", "Eclipse"};
		JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		filterBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		filterBar.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		for (int i = 0; i < labels.length; i++)
		{
			final int idx = i;
			JButton btn = pillButton(labels[i]);
			applySortStyle(btn, idx == moonFilterIdx);
			btn.addActionListener(e ->
			{
				moonFilterIdx = idx;
				hiliteFilter(moonFilterBtns, moonFilterIdx);
				moonPage = 0;
				renderMoon(filtered());
			});
			moonFilterBtns[i] = btn;
			filterBar.add(btn);
		}

		moonListPanel = listPanel();
		moonPageLabel = pageLabel();
		moonPrev      = pageBtn("\u2039");
		moonNext      = pageBtn("\u203A");
		moonPrev.addActionListener(e ->
		{
			moonPage--;
			renderMoon(filtered());
		});
		moonNext.addActionListener(e ->
		{
			moonPage++;
			renderMoon(filtered());
		});

		return assembleTab(filterBar, moonListPanel, buildPageBar(moonPageLabel, moonPrev, moonNext));
	}

	private JPanel buildSpikesTab()
	{
		spikesSortBtns = new JButton[2];
		JPanel sortRow = buildSortBar(spikesSortBtns, new String[]{"Recent", "Spike %"},
			() -> spikesSortIdx, i ->
			{
				spikesSortIdx = i;
				spikesSortKey = i == 0 ? "recent" : "spike_pct";
				spikesPage    = 0;
				if (plugin != null)
				{
					plugin.onSpikesSortChanged(spikesSortKey);
				}
			});

		spikesListPanel = listPanel();
		spikesPageLabel = pageLabel();
		spikesPrev      = pageBtn("\u2039");
		spikesNext      = pageBtn("\u203A");
		spikesPrev.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.onSpikesPageChanged(--spikesPage);
			}
		});
		spikesNext.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.onSpikesPageChanged(++spikesPage);
			}
		});

		return assembleTab(sortRow, spikesListPanel, buildPageBar(spikesPageLabel, spikesPrev, spikesNext));
	}

	private JPanel buildDipsTab()
	{
		dipsSortBtns = new JButton[2];
		JPanel sortRow = buildSortBar(dipsSortBtns, new String[]{"Recent", "Biggest Dip"},
			() -> dipsSortIdx, i ->
			{
				dipsSortIdx = i;
				dipsSortKey = i == 0 ? "recent" : "dip_pct";
				dipsPage    = 0;
				if (plugin != null)
				{
					plugin.onDipsSortChanged(dipsSortKey);
				}
			});

		dipsListPanel = listPanel();
		dipsPageLabel = pageLabel();
		dipsPrev      = pageBtn("\u2039");
		dipsNext      = pageBtn("\u203A");
		dipsPrev.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.onDipsPageChanged(--dipsPage);
			}
		});
		dipsNext.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.onDipsPageChanged(++dipsPage);
			}
		});

		return assembleTab(sortRow, dipsListPanel, buildPageBar(dipsPageLabel, dipsPrev, dipsNext));
	}

	private JPanel buildDumpsTab()
	{
		// Sort bar — no sign-in gate (server-side sort, useful to all users)
		dumpsSortBtns = new JButton[2];
		String[] sortLabels = {"Recent", "Score"};
		JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		sortRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortRow.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		for (int i = 0; i < sortLabels.length; i++)
		{
			final int idx = i;
			JButton btn = pillButton(sortLabels[i]);
			applySortStyle(btn, idx == dumpsSortIdx);
			btn.addActionListener(e ->
			{
				dumpsSortIdx = idx;
				dumpsSortKey = idx == 0 ? "recent" : "dump_pct";
				dumpsPage    = 0;
				hiliteFilter(dumpsSortBtns, dumpsSortIdx);
				if (plugin != null)
				{
					plugin.onDumpsSortChanged(dumpsSortKey);
				}
			});
			dumpsSortBtns[i] = btn;
			sortRow.add(btn);
		}

		// Client-side filters
		JComboBox<String> minProfitCb = styledCombo(DUMP_MIN_PROFIT_LABELS);
		minProfitCb.addActionListener(e ->
		{
			dumpsMinProfitIdx = minProfitCb.getSelectedIndex();
			dumpsPage = 0;
			renderDumps(filtered());
			if (plugin != null)
			{
				plugin.onDumpsFilterChanged();
			}
		});

		JComboBox<String> priceRangeCb = styledCombo(PRICE_RANGE_LABELS);
		priceRangeCb.addActionListener(e ->
		{
			dumpsPriceRangeIdx = priceRangeCb.getSelectedIndex();
			dumpsPage = 0;
			renderDumps(filtered());
			if (plugin != null)
			{
				plugin.onDumpsFilterChanged();
			}
		});

		JPanel filterRow = new JPanel(new GridLayout(1, 2, 4, 0));
		filterRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		filterRow.setBorder(new EmptyBorder(4, 8, 4, 8));
		filterRow.add(minProfitCb);
		filterRow.add(priceRangeCb);

		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(filterRow, BorderLayout.NORTH);
		topBar.add(sortRow,   BorderLayout.SOUTH);

		dumpsListPanel = listPanel();
		dumpsPageLabel = pageLabel();
		dumpsPrev      = pageBtn("\u2039");
		dumpsNext      = pageBtn("\u203A");
		dumpsPrev.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.onDumpsPageChanged(--dumpsPage);
			}
		});
		dumpsNext.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.onDumpsPageChanged(++dumpsPage);
			}
		});

		return assembleTab(topBar, dumpsListPanel, buildPageBar(dumpsPageLabel, dumpsPrev, dumpsNext));
	}

	private JPanel buildGenericTab(String name)
	{
		switch (name)
		{
			case "Barrows":
			{
				// ── List view ─────────────────────────────────────────────────
				barrowsSortBtns  = new JButton[2];
				barrowsListPanel = listPanel();
				barrowsPageLabel = pageLabel();
				barrowsPrev      = pageBtn("\u2039");
				barrowsNext      = pageBtn("\u203A");
				barrowsPrev.addActionListener(e ->
				{
					barrowsPage--;
					renderBarrows(filtered());
				});
				barrowsNext.addActionListener(e ->
				{
					barrowsPage++;
					renderBarrows(filtered());
				});
				JPanel barrowsListView = assembleTab(
					buildSortBar(barrowsSortBtns, new String[]{"Best Profit", "Cost"},
						() -> barrowsSortIdx, i ->
						{
							barrowsSortIdx = i;
							barrowsPage = 0;
							renderBarrows(filtered());
						}),
					barrowsListPanel,
					buildPageBar(barrowsPageLabel, barrowsPrev, barrowsNext));

				// ── Detail view ───────────────────────────────────────────────
				barrowsDetailPanel = listPanel();
				barrowsDetailTitle = new JLabel("");
				barrowsDetailTitle.setFont(Fonts.BOLD);
				barrowsDetailTitle.setForeground(Color.WHITE);

				JButton backBtn = pillButton("\u2190 Back");
				backBtn.setBackground(new Color(0x3E3E3E));
				backBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				backBtn.addActionListener(e -> showBarrowsList());

				JPanel detailHeader = new JPanel(new BorderLayout(8, 0));
				detailHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				detailHeader.setBorder(new EmptyBorder(6, 8, 6, 8));
				detailHeader.add(backBtn,            BorderLayout.WEST);
				detailHeader.add(barrowsDetailTitle, BorderLayout.CENTER);

				ListWrapperPanel detailWrapper = new ListWrapperPanel(barrowsDetailPanel);
				detailWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
				JScrollPane detailSp = new JScrollPane(detailWrapper);
				detailSp.setBorder(BorderFactory.createEmptyBorder());
				detailSp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
				detailSp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
				detailSp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));

				JPanel barrowsDetailView = new JPanel(new BorderLayout());
				barrowsDetailView.setBackground(ColorScheme.DARK_GRAY_COLOR);
				barrowsDetailView.add(detailHeader, BorderLayout.NORTH);
				barrowsDetailView.add(detailSp,     BorderLayout.CENTER);

				// ── Card panel ────────────────────────────────────────────────
				barrowsTabCard = new JPanel(new CardLayout());
				barrowsTabCard.add(barrowsListView,   "list");
				barrowsTabCard.add(barrowsDetailView, "detail");
				return barrowsTabCard;
			}

			case "Decant":
				decantSortBtns  = new JButton[3];
				decantListPanel = listPanel();
				decantPageLabel = pageLabel();
				decantPrev      = pageBtn("\u2039");
				decantNext      = pageBtn("\u203A");
				decantPrev.addActionListener(e ->
				{
					decantPage--;
					renderDecants(filtered());
				});
				decantNext.addActionListener(e ->
				{
					decantPage++;
					renderDecants(filtered());
				});
				return assembleTab(buildSortBar(decantSortBtns, new String[]{"Profit", "ROI %", "Volume"},
					() -> decantSortIdx, i ->
					{
						decantSortIdx = i;
						decantPage = 0;
						renderDecants(filtered());
					}),
					decantListPanel, buildPageBar(decantPageLabel, decantPrev, decantNext));

			default: // Merch / Price Alerts
				alertsSortBtns  = new JButton[3];
				alertsListPanel = listPanel();
				alertsPageLabel = pageLabel();
				alertsPrev      = pageBtn("\u2039");
				alertsNext      = pageBtn("\u203A");
				alertsPrev.addActionListener(e ->
				{
					if (plugin != null)
					{
						plugin.onAlertsPageChanged(--alertsPage);
					}
				});
				alertsNext.addActionListener(e ->
				{
					if (plugin != null)
					{
						plugin.onAlertsPageChanged(++alertsPage);
					}
				});
				return assembleTab(buildSortBar(alertsSortBtns, new String[]{"Recent", "Upside %", "Drawdown"},
					() -> alertsSortIdx, i ->
					{
						alertsSortIdx = i;
						alertsPage = 0;
						renderAlerts(filtered());
					}),
					alertsListPanel, buildPageBar(alertsPageLabel, alertsPrev, alertsNext));
		}
	}

	// =========================================================================
	// Search view
	// =========================================================================

	private JScrollPane buildSearchView()
	{
		searchResultsPanel = new JPanel();
		searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
		searchResultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		ListWrapperPanel searchWrapper = new ListWrapperPanel(searchResultsPanel);
		searchWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchScrollPane = new JScrollPane(searchWrapper);
		searchScrollPane.setBorder(BorderFactory.createEmptyBorder());
		searchScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		searchScrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchScrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
		return searchScrollPane;
	}

	// =========================================================================
	// Preset dropdown renderer (premium items greyed + [P] tag)
	// =========================================================================

	private ListCellRenderer<Object> buildPresetRenderer()
	{
		return new BasicComboBoxRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
			{
				JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				lbl.setBackground(isSelected ? new Color(0x4A4A4A) : ColorScheme.DARKER_GRAY_COLOR);
				int i = index >= 0 ? index : presetSelector.getSelectedIndex();
				if (i >= 0 && i < PREMIUM_PRESET.length && PREMIUM_PRESET[i] && !isPremium)
				{
					lbl.setForeground(new Color(0x777777));
					lbl.setText("<html>" + value + " <font color='#FF981F'><b>[P]</b></font></html>");
				}
				else
				{
					lbl.setForeground(Color.WHITE);
				}
				return lbl;
			}
		};
	}

	// =========================================================================
	// Sort bar — pill buttons
	// =========================================================================

	private JPanel buildSortBar(JButton[] store, String[] labels, IntSupplier get, IntConsumer set)
	{
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		for (int i = 0; i < labels.length; i++)
		{
			final int idx = i;
			JButton btn = pillButton(labels[i]);
			applySortStyle(btn, i == get.get());
			btn.addActionListener(e ->
			{
				if (!isSignedIn)
				{
					return;
				}
				set.accept(idx);
				hilite(store, idx);
			});
			store[i] = btn;
			bar.add(btn);
		}
		return bar;
	}

	private static JButton pillButton(String label)
	{
		JButton btn = new JButton(label)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color bg = isEnabled() ? getBackground() : new Color(0x2A2A2A);
				Color fg = isEnabled() ? getForeground() : new Color(0x555555);
				int arc = getHeight();
				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
				g2.setColor(fg);
				g2.setFont(getFont());
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
					(getHeight() + fm.getAscent() - fm.getDescent()) / 2);
				g2.dispose();
			}

			@Override
			protected void paintBorder(Graphics g)
			{
			}

			@Override
			public boolean isOpaque()
			{
				return false;
			}
		};
		btn.setFont(Fonts.SM);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(new EmptyBorder(3, 10, 3, 10));
		return btn;
	}

	private void applySortStyle(JButton btn, boolean active)
	{
		btn.setBackground(active ? ORANGE : new Color(0x3E3E3E));
		btn.setForeground(active ? Color.BLACK : ColorScheme.LIGHT_GRAY_COLOR);
	}

	private void hilite(JButton[] btns, int active)
	{
		if (btns == null)
		{
			return;
		}
		for (int i = 0; i < btns.length; i++)
		{
			btns[i].setEnabled(isSignedIn);
			applySortStyle(btns[i], isSignedIn && i == active);
		}
	}

	/** Filter buttons — always enabled, no sign-in gate. */
	private void hiliteFilter(JButton[] btns, int active)
	{
		if (btns == null)
		{
			return;
		}
		for (int i = 0; i < btns.length; i++)
		{
			applySortStyle(btns[i], i == active);
		}
	}

	// =========================================================================
	// Page bar
	// =========================================================================

	private JPanel buildPageBar(JLabel label, JButton prev, JButton next)
	{
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setBorder(new MatteBorder(1, 0, 0, 0, new Color(0x3A3A3A)));
		bar.add(prev);
		bar.add(label);
		bar.add(next);
		return bar;
	}

	private JLabel pageLabel()
	{
		JLabel l = new JLabel("");
		l.setFont(Fonts.SM);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setPreferredSize(new Dimension(40, 18));
		l.setHorizontalAlignment(SwingConstants.CENTER);
		return l;
	}

	private JButton pageBtn(String sym)
	{
		JButton b = pillButton(sym);
		b.setBackground(new Color(0x3E3E3E));
		b.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		b.setEnabled(false);
		b.setPreferredSize(new Dimension(26, 22));
		return b;
	}

	// =========================================================================
	// Tab assembly
	// =========================================================================

	private JPanel assembleTab(JPanel topBar, JPanel list, JPanel pageBar)
	{
		ListWrapperPanel wrapper = new ListWrapperPanel(list);
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane sp = new JScrollPane(wrapper);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		sp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));

		JPanel tab = new JPanel(new BorderLayout());
		tab.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tab.add(topBar,  BorderLayout.NORTH);
		tab.add(sp,      BorderLayout.CENTER);
		tab.add(pageBar, BorderLayout.SOUTH);
		return tab;
	}

	// =========================================================================
	// Footer
	// =========================================================================

	private JPanel buildFooter()
	{
		lastUpdatedLabel = new JLabel(" ");
		lastUpdatedLabel.setFont(Fonts.SM);
		lastUpdatedLabel.setForeground(new Color(0x555555));
		lastUpdatedLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lastUpdatedLabel.setBorder(new EmptyBorder(2, 0, 4, 0));

		JButton wb = pillButton("07flip.com");
		JButton db = pillButton("Discord");
		wb.setBackground(ORANGE);
		wb.setForeground(Color.BLACK);
		db.setBackground(new Color(0x5865F2));
		db.setForeground(Color.WHITE);
		wb.addActionListener(e -> openUrl(WEBSITE_URL));
		db.addActionListener(e -> openUrl(DISCORD_URL));

		JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
		btns.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		btns.setBorder(new EmptyBorder(6, 10, 4, 10));
		btns.add(wb);
		btns.add(db);

		JPanel footer = new JPanel(new BorderLayout());
		footer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		footer.setBorder(new MatteBorder(1, 0, 0, 0, new Color(0x3A3A3A)));
		footer.add(btns,             BorderLayout.CENTER);
		footer.add(lastUpdatedLabel, BorderLayout.SOUTH);
		return footer;
	}

	// =========================================================================
	// Micro helpers
	// =========================================================================

	private JPanel listPanel()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return p;
	}

	private Component sep()
	{
		JPanel s = new JPanel();
		s.setBackground(new Color(0x333333));
		s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		s.setPreferredSize(new Dimension(0, 1));
		s.setAlignmentX(Component.LEFT_ALIGNMENT);
		return s;
	}

	private JPanel signInPrompt(int hiddenCount)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(new Color(0x1E1E1E));
		p.setBorder(new EmptyBorder(10, 12, 10, 12));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

		JLabel t = new JLabel("+" + hiddenCount + " results hidden");
		t.setFont(Fonts.SM_BOLD);
		t.setForeground(ORANGE);
		t.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel sub = new JLabel("Sign up at 07flip.com \u2192 Profile \u2192 View API Key");
		sub.setFont(Fonts.SM);
		sub.setForeground(new Color(0x666666));
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);

		p.add(t);
		p.add(sub);
		return p;
	}

	private JPanel emptyLabel(String title, String sub)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.setBorder(new EmptyBorder(20, 14, 20, 14));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel t = new JLabel(title);
		t.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		t.setFont(Fonts.SM);
		p.add(t);
		if (!sub.isEmpty())
		{
			JLabel s = new JLabel(sub);
			s.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			s.setFont(Fonts.SM);
			s.setBorder(new EmptyBorder(4, 0, 0, 0));
			p.add(s);
		}
		return p;
	}

	private static JComboBox<String> styledCombo(String[] items)
	{
		JComboBox<String> cb = new JComboBox<>(items);
		cb.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cb.setForeground(Color.WHITE);
		cb.setFont(Fonts.SM);
		return cb;
	}

	private void updateTimestamp()
	{
		java.time.LocalTime now = java.time.LocalTime.now();
		lastUpdatedLabel.setText(String.format("Updated %02d:%02d", now.getHour(), now.getMinute()));
	}

	private void openUrl(String url)
	{
		LinkBrowser.browse(url);
	}

	// =========================================================================
	// Scrollable list wrapper — anchors content to top, tracks viewport width
	// =========================================================================

	private static class ListWrapperPanel extends JPanel implements Scrollable
	{
		ListWrapperPanel(JPanel list)
		{
			super(new BorderLayout());
			add(list, BorderLayout.NORTH);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle r, int o, int d)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle r, int o, int d)
		{
			return 100;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
