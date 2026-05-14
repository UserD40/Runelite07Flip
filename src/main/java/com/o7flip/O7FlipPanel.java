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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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
import java.awt.Font;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	// Preset definitions — true = premium required (must match PRESETS order)
	// Free presets first, then premium, mirroring the website's "All Flips" UI.
	// -------------------------------------------------------------------------
	private static final String[][] PRESETS = {
		// Free
		{"",                 "All Flips"},
		{"starterFlips",     "Starter"},
		{"highMargin",       "High Margin"},
		{"f2p",              "F2P Only"},
		// Premium
		{"priceDip",         "Price Dip"},
		{"stableFlips",      "Stable"},
		{"highVolume",       "High Volume"},
		{"volumeSpike",      "Volume Spike"},
		{"oversoldDip",      "Oversold"},
		{"momentumRecovery", "Momentum"},
		{"lowVolatility",    "Low Volatility"},
	};
	private static final boolean[] PREMIUM_PRESET = {
		false, false, false, false,                // free
		true,  true,  true,  true,  true,  true,  true,  // premium
	};

	// -------------------------------------------------------------------------
	// Sort options for the Flips tab — server-side sort via ?sort= param.
	// -------------------------------------------------------------------------
	private static final String[][] FLIPS_SORTS = {
		{"flip07Score",     "07Flip Score"},
		{"potentialProfit", "gp / hour"},
		{"profit",          "Profit"},
		{"roi",             "ROI %"},
		{"recProfit",       "Recommended profit"},
	};

	// -------------------------------------------------------------------------
	// Client-side Flips filters (Flips tab uses Capital + Min profit + F2P toggle)
	// -------------------------------------------------------------------------
	private static final long[]   MIN_PROFITS       = {0, 100_000, 500_000, 1_000_000};
	private static final String[] MIN_PROFIT_LABELS = {"Any profit", "100K+", "500K+", "1M+"};

	// "Capital" replaces the old 12-bucket Price filter — frames the choice
	// as "what can I afford" rather than "what tier is this item". Each
	// entry is {lowerInclusive, upperExclusive} on the buy-side price.
	private static final long[][] CAPITAL_RANGES = {
		{0,            Long.MAX_VALUE}, // Any
		{0,            100_000},        // Under 100K
		{100_000,      1_000_000},      // 100K – 1M
		{1_000_000,    10_000_000},     // 1M – 10M
		{10_000_000,   Long.MAX_VALUE}, // 10M+
	};
	private static final String[] CAPITAL_LABELS = {
		"Any capital",
		"Under 100K",
		"100K – 1M",
		"1M – 10M",
		"10M+",
	};

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
	private int flipsCapitalIdx    = 0;
	private boolean flipsF2pOnly   = false;
	private boolean flipsFilterPanelOpen = false;
	private int dumpsMinProfitIdx  = 0;
	private int dumpsPriceRangeIdx = 0;

	// -------------------------------------------------------------------------
	// Server-side sort keys for spikes / dumps
	// -------------------------------------------------------------------------
	private String spikesSortKey = "recent";
	private String dumpsSortKey  = "recent";
	private boolean dumpsUseBotEndpoint = false;

	// -------------------------------------------------------------------------
	// Auth state
	// -------------------------------------------------------------------------
	private boolean isSignedIn = false;
	private boolean isPremium  = false;

	/** Public accessors so the GE overlay and row panels can gate premium features. */
	public boolean isSignedIn() { return isSignedIn; }
	public boolean isPremium()  { return isPremium;  }
	private boolean authChecked = false;
	private boolean noKeyBannerExpanded = true;

	// -------------------------------------------------------------------------
	// Stored data
	// -------------------------------------------------------------------------
	private List<FlipItem>    allFlips   = new ArrayList<>();
	private List<SpikeItem>   allSpikes  = new ArrayList<>();
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
	private int dumpsSortIdx   = 0;
	private int barrowsSortIdx = 0;
	private int moonFilterIdx  = 0;  // 0=Blood 1=Blue 2=Eclipse
	private int decantSortIdx  = 0;
	private int alertsSortIdx  = 0;
	private int myFlipsSortIdx = 0;  // 0=Active 1=Recent 2=Margin
	private int myFlipsPage    = 0;
	private static final int MY_FLIPS_PAGE_SIZE = 5;
	/** Secondary sort applied inside the Margin view: 0=Profit (desc), 1=Recent, 2=ROI% (desc). */
	private int myFlipsMarginSortIdx = 0;
	/** Secondary sort applied inside the Recent view: 0=Profit (desc), 1=ROI (desc), 2=Quantity (desc). */
	private int myFlipsRecentSortIdx = 0;
	/** Currently selected period for the stats panel — Daily by default. */
	private com.o7flip.ui.MyTradesStatsPanel.Period myFlipsPeriod =
		com.o7flip.ui.MyTradesStatsPanel.Period.DAILY;

	// -------------------------------------------------------------------------
	// Page state (server-paginated tabs track total from server)
	// -------------------------------------------------------------------------
	private int flipsPage   = 0;  private int flipsTotal  = 0;
	private int spikesPage  = 0;  private int spikesTotal = 0;
	private int dumpsPage   = 0;  private int dumpsTotal  = 0;
	private int barrowsPage = 0;
	private int moonPage    = 0;
	private int decantPage  = 0;

	// -------------------------------------------------------------------------
	// List panels
	// -------------------------------------------------------------------------
	private JPanel flipsListPanel;
	private JPanel spikesListPanel;
	private JPanel dumpsListPanel;
	private JPanel barrowsListPanel;
	private JPanel barrowsDetailPanel;
	private JPanel barrowsTabCard;
	private JLabel barrowsDetailTitle;
	private JPanel moonListPanel;
	private JPanel decantListPanel;
	private JPanel alertsListPanel;
	private JPanel myFlipsListPanel;
	private com.o7flip.ui.MyTradesStatsPanel myFlipsStatsPanel;
	private com.o7flip.ui.InsightsPanel insightsPanel;
	private JPanel searchResultsPanel;
	private JScrollPane searchScrollPane;

	// -------------------------------------------------------------------------
	// Sort buttons
	// -------------------------------------------------------------------------
	// flipsSortBtns removed — replaced by flipsSortSelector dropdown for the
	// expanded 5-option sort (07Flip Score / gp-hour / profit / roi / recProfit).
	private JButton[] spikesSortBtns;
	private JButton[] dumpsSortBtns;
	private JButton[] barrowsSortBtns;
	private JButton[] myFlipsSortBtns;
	private JButton[] myFlipsMarginSortBtns;
	private JButton[] myFlipsRecentSortBtns;
	private JPanel    myFlipsMarginSortBar;
	private JPanel    myFlipsRecentSortBar;
	private JButton   myFlipsPeriodButton;
	private JButton[] moonFilterBtns;
	private JButton[] decantSortBtns;
	private JButton[] alertsSortBtns;

	// -------------------------------------------------------------------------
	// Page controls
	// -------------------------------------------------------------------------
	private JLabel  flipsPageLabel;   private JButton flipsPrev,    flipsNext;
	private JLabel  spikesPageLabel;  private JButton spikesPrev,   spikesNext;
	private JLabel  dumpsPageLabel;   private JButton dumpsPrev,    dumpsNext;
	private JLabel  barrowsPageLabel; private JButton barrowsPrev,  barrowsNext;
	private JLabel  moonPageLabel;    private JButton moonPrev,     moonNext;
	private JLabel  decantPageLabel;  private JButton decantPrev,   decantNext;

	// -------------------------------------------------------------------------
	// Other UI
	// -------------------------------------------------------------------------
	// Filter panel widgets — created lazily inside buildFlipsTab and stashed
	// here so chip-removals can keep them in sync with the panel state.
	private JComboBox<String> flipsCapitalCombo;
	private JComboBox<String> flipsMinProfitCombo;
	private JButton           flipsMembersBtn;
	private JButton           flipsF2pBtn;
	private JButton           flipsFilterButton;
	private JPanel            flipsFilterPanel;
	private JPanel            flipsChipBar;
	private JTextField searchField;
	private JLabel statusLabel;
	private JLabel hiddenCountLabel;
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

		// Flips tab no longer uses preset/sort dropdowns — sort is hardcoded
		// to flip07Score, presets are gone. The filter panel widgets are
		// created lazily inside buildFlipsTab(); we just need tabsWrapper.

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
		this.isSignedIn  = signedIn;
		this.isPremium   = premium;
		this.authChecked = true;
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
			if (!authChecked)
			{
				// Auth call in flight — keep banner hidden until we know the outcome.
				authBanner.setVisible(false);
				authBanner.revalidate();
				authBanner.repaint();
				northArea.revalidate();
				northArea.repaint();
				return;
			}

			// Server returned authenticated:false for the configured key.
			// Most common cause: stray characters pasted with the key.
			authBanner.setBackground(new Color(0x2A0D0D));
			authBanner.setBorder(BorderFactory.createCompoundBorder(
				new MatteBorder(1, 0, 1, 0, new Color(0x663333)),
				new EmptyBorder(9, 10, 9, 10)));

			JPanel notConnectedInner = new JPanel();
			notConnectedInner.setLayout(new BoxLayout(notConnectedInner, BoxLayout.Y_AXIS));
			notConnectedInner.setOpaque(false);

			bannerRow(notConnectedInner, "⚠ Not Connected",                          Fonts.BOLD, new Color(0xFF6B6B), 0);
			bannerRow(notConnectedInner, "Your API key wasn't recognised.",               Fonts.SM,   new Color(0xBBBBBB), 4);
			bannerRow(notConnectedInner, "Check for stray spaces or quotes when pasting.", Fonts.SM,   new Color(0xBBBBBB), 1);

			notConnectedInner.add(Box.createRigidArea(new Dimension(0, 9)));

			JButton fixBtn = pillButton("Get a new key at 07flip.com/runelite");
			fixBtn.setBackground(ORANGE);
			fixBtn.setForeground(Color.BLACK);
			fixBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
			fixBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
			fixBtn.addActionListener(e -> openUrl(RUNELITE_URL));
			notConnectedInner.add(fixBtn);

			authBanner.add(notConnectedInner, BorderLayout.CENTER);
			authBanner.setVisible(true);
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
		// The Flips tab now exposes only an "F2P / All" toggle, no preset
		// dropdown. F2P uses the server's free 'f2p' preset; otherwise no
		// preset (defaults to 'all' on the server).
		return flipsF2pOnly ? "f2p" : "";
	}

	public String getFlipsSortKey()
	{
		// Sort is hardcoded to flip07Score now — the panel surfaces the
		// "Top flips by 07Flip Score" header without a sort dropdown,
		// so anything else would be a lie. If the server adds a better
		// score later, just change this constant.
		return "flip07Score";
	}

	public String getSpikesSortKey()
	{
		return spikesSortKey;
	}

	/**
	 * True when the Dumps tab is showing the bot-driven dump feed (from
	 * {@code /api/runelite/bot-dumps}) instead of the general dump feed
	 * (from {@code /api/runelite/dumps}). Driven by the source toggle in
	 * the Dumps tab header.
	 */
	public boolean dumpsUsesBotEndpoint()
	{
		return dumpsUseBotEndpoint;
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
		return flipsCapitalIdx > 0 ? CAPITAL_RANGES[flipsCapitalIdx][0] : 0;
	}

	public long getFlipsPriceMax()
	{
		return flipsCapitalIdx > 0 ? CAPITAL_RANGES[flipsCapitalIdx][1] : Long.MAX_VALUE;
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

	public int getDumpsPage()
	{
		return dumpsPage;
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
		pushInsightsRecommendations();   // refresh the Item-tab empty state if it's showing
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

	public void updateDumps(List<DumpItem> items, int total, int page)
	{
		allDumps = items;
		dumpsTotal = total;
		dumpsPage = page;
		renderDumps(filtered());
	}

	public void updateAlerts(List<AlertItem> items)
	{
		allAlerts = items;
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
	private boolean notBlocked(int itemId)
	{
		return plugin == null || !plugin.isBlocked(itemId);
	}

	private List<FlipItem>   fFlips(String q)
	{
		return allFlips.stream()
			.filter(i -> notBlocked(i.itemId))
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
	}

	private List<SpikeItem>  fSpikes(String q)
	{
		return allSpikes.stream()
			.filter(i -> notBlocked(i.itemId))
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
	}

	private List<DumpItem>   fDumps(String q)
	{
		return allDumps.stream()
			.filter(i -> notBlocked(i.itemId))
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
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
		return allAlerts.stream()
			.filter(i -> notBlocked(i.itemId))
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
	}

	// =========================================================================
	// Sort helpers
	// =========================================================================

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
		// Tabs are filters now — within each filtered set we always sort
		// reverse-chronologically. Successful alerts use achievedAt (when the
		// target was hit), everything else uses detectedAt (when it was first
		// flagged). Falls back gracefully if either timestamp is missing.
		boolean successfulView = (!isPremium) || alertsSortIdx == 2;
		Comparator<AlertItem> c = successfulView
			? Comparator.comparing((AlertItem x) ->
				x.achievedAt != null ? x.achievedAt : (x.detectedAt != null ? x.detectedAt : ""))
			: Comparator.comparing((AlertItem x) -> x.detectedAt != null ? x.detectedAt : "");
		return items.stream().sorted(c.reversed()).collect(Collectors.toList());
	}

	// =========================================================================
	// Tab renderers
	// =========================================================================

	private void renderFlips(String q)
	{
		// Sort is applied server-side via ?sort=flip07Score|profit|roi|...
		// so we just render the rows in the order the server returned them.
		fillListPaged(flipsListPanel, fFlips(q), flipsPage, flipsTotal,
			flipsPageLabel, flipsPrev, flipsNext,
			(item, odd) -> new FlipItemPanel(item, itemManager, odd, plugin),
			"No flips found", "Try a different preset or filter");
	}

	private void renderSpikes(String q)
	{
		fillListPaged(spikesListPanel, fSpikes(q), spikesPage, spikesTotal,
			spikesPageLabel, spikesPrev, spikesNext,
			(item, odd) -> new SpikeItemPanel(item, itemManager, odd, plugin),
			"No spike signals", "Check back soon");
		hilite(spikesSortBtns, spikesSortIdx);
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
			(item, odd) -> new DecantItemPanel(item, itemManager, odd, plugin),
			"No decanting opportunities", "");
		hilite(decantSortBtns, decantSortIdx);
	}

	private void renderAlerts(String q)
	{
		alertsListPanel.removeAll();

		List<AlertItem> filtered = sortAlerts(filterAlertsByStatus(fAlerts(q)));
		if (filtered.isEmpty())
		{
			alertsListPanel.add(emptyLabel(emptyAlertsHeadline(), emptyAlertsSub()));
		}
		else
		{
			for (int i = 0; i < filtered.size(); i++)
			{
				alertsListPanel.add(new AlertItemPanel(filtered.get(i), itemManager, i % 2 != 0, plugin));
			}
		}

		alertsListPanel.revalidate();
		alertsListPanel.repaint();
		hilite(alertsSortBtns, alertsSortIdx);
	}

	/**
	 * Filters the alert list by the current sort tab. Free users only see the
	 * "Successful" tab — alertsSortIdx is forced to 0 in {@link #buildAlertsSortBar}
	 * for them. Premium index map: 0=Most Recent, 1=Pending, 2=Successful.
	 */
	private List<AlertItem> filterAlertsByStatus(List<AlertItem> input)
	{
		String wantStatus;
		if (!isPremium)
		{
			wantStatus = "successful";
		}
		else if (alertsSortIdx == 1)
		{
			wantStatus = "pending";
		}
		else if (alertsSortIdx == 2)
		{
			wantStatus = "successful";
		}
		else
		{
			wantStatus = null;   // Most Recent — keep all
		}
		if (wantStatus == null)
		{
			return input;
		}
		List<AlertItem> out = new ArrayList<>();
		for (AlertItem a : input)
		{
			if (wantStatus.equalsIgnoreCase(a.status))
			{
				out.add(a);
			}
		}
		return out;
	}

	private String emptyAlertsHeadline()
	{
		if (!isPremium)                 return "No successful alerts yet";
		if (alertsSortIdx == 1)         return "No pending alerts";
		if (alertsSortIdx == 2)         return "No successful alerts yet";
		return "No active price alerts";
	}

	private String emptyAlertsSub()
	{
		if (!isPremium)                 return "Premium unlocks pending + most-recent alerts";
		if (alertsSortIdx == 1)         return "No alerts currently waiting to hit target";
		if (alertsSortIdx == 2)         return "Successful alerts will appear here once targets hit";
		return "Alerts posted twice daily";
	}

	/**
	 * Small CTA card shown above the trade list when the user has no API key
	 * set. Explains that local recording works as-is and points them to the
	 * website sign-up to unlock cross-device tracker sync. Hidden as soon as
	 * an API key lands in config — we don't keep nagging people once they're
	 * signed up.
	 */
	private JPanel buildTrackerCta()
	{
		JPanel card = new JPanel(new BorderLayout(0, 4));
		card.setBackground(new Color(0x1F1F1F));
		card.setBorder(new javax.swing.border.CompoundBorder(
			javax.swing.BorderFactory.createLineBorder(new Color(0x3A3A3A), 1),
			new EmptyBorder(8, 10, 8, 10)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));

		JLabel title = new JLabel("Sync your trades across devices");
		title.setFont(com.o7flip.util.Fonts.SM_BOLD);
		title.setForeground(new Color(0xFFAA00));

		JLabel body = new JLabel("<html><font color='#A0A0A0'>"
			+ "Local recording works as-is. Sign up at <b>07flip.com</b> with Discord, "
			+ "paste your API key into config, and your completed flips sync to your "
			+ "Tracker page so you can browse them from any device."
			+ "</font></html>");
		body.setFont(com.o7flip.util.Fonts.SM);

		JButton signUp = new JButton("Open 07flip.com");
		signUp.setFont(com.o7flip.util.Fonts.SM_BOLD);
		signUp.setBackground(new Color(0xFFAA00));
		signUp.setForeground(Color.BLACK);
		signUp.setFocusPainted(false);
		signUp.setOpaque(true);
		signUp.setBorderPainted(false);
		signUp.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse("https://07flip.com"));

		JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		buttonRow.setBackground(card.getBackground());
		buttonRow.add(signUp);

		card.add(title,     BorderLayout.NORTH);
		card.add(body,      BorderLayout.CENTER);
		card.add(buttonRow, BorderLayout.SOUTH);
		return card;
	}

	private void renderMyFlips()
	{
		if (myFlipsListPanel == null)
		{
			return;
		}
		myFlipsListPanel.removeAll();

		com.o7flip.util.ProfitCalculator.Result result = com.o7flip.util.ProfitCalculator.compute(allMyFlips);

		if (myFlipsStatsPanel != null)
		{
			myFlipsStatsPanel.update(
				result,
				plugin != null ? plugin.trackerStats : null,
				plugin != null ? plugin.bondLedger  : com.o7flip.util.BondLedger.EMPTY,
				myFlipsPeriod,
				plugin != null && plugin.isMembershipCostHidden());
			if (myFlipsStatsPanel.isVisible())
			{
				myFlipsListPanel.add(myFlipsStatsPanel);
				myFlipsListPanel.add(sep());
			}
		}

		// CTA for users without an API key: surface the website sign-up so they
		// know how to unlock cross-device tracker sync. Local recording always
		// works without a key; this card just explains how to take their
		// history further.
		String apiKey = plugin != null && plugin.getConfig() != null ? plugin.getConfig().apiKey() : null;
		if (apiKey == null || apiKey.trim().isEmpty())
		{
			myFlipsListPanel.add(buildTrackerCta());
			myFlipsListPanel.add(sep());
		}

		switch (myFlipsSortIdx)
		{
			case 1:
				renderMyFlipsByRecent(result);
				break;
			case 2:
				renderMyFlipsByMargin(result);
				break;
			default:
				renderMyFlipsActive();
				break;
		}

		myFlipsListPanel.revalidate();
		myFlipsListPanel.repaint();
	}

	/**
	 * Recent: per-leg trade list, filtered by the selected period and sorted
	 * by the inline Profit / ROI / Quantity selector that sits above the
	 * items. Bonds are excluded — they're tracked separately by
	 * {@link com.o7flip.util.BondLedger}.
	 */
	private void renderMyFlipsByRecent(com.o7flip.util.ProfitCalculator.Result result)
	{
		// Per-sell-timestamp profit, summed over every CompletedFlip whose
		// sell matches that timestamp. Phantom flips (buyTotal == 0) are
		// excluded so unmatched sells stay number-less rather than showing
		// an inflated gross. Also needed for the Profit/ROI sort comparators.
		Map<Long, Long> profitBySellTimestamp = new HashMap<>();
		for (com.o7flip.util.ProfitCalculator.CompletedFlip f : result.completedFlips)
		{
			if (f.buyTotal <= 0) continue;
			profitBySellTimestamp.merge(f.sellTimestamp, f.profit, Long::sum);
		}
		// ROI uses the matched buy cost for the sell timestamp. Multiple
		// CompletedFlips can share a sell timestamp (FIFO split across
		// buy lots), so sum buyTotal across them too.
		Map<Long, Long> buyTotalBySellTimestamp = new HashMap<>();
		for (com.o7flip.util.ProfitCalculator.CompletedFlip f : result.completedFlips)
		{
			if (f.buyTotal <= 0) continue;
			buyTotalBySellTimestamp.merge(f.sellTimestamp, f.buyTotal, Long::sum);
		}

		long fromMs = periodStartMillis();
		List<TradeRecord> filtered = new ArrayList<>();
		for (TradeRecord t : allMyFlips)
		{
			if (t.itemId == com.o7flip.util.ProfitCalculator.BOND_ITEM_ID) continue;
			if (t.timestamp < fromMs) continue;
			filtered.add(t);
		}

		// Sort row is added BEFORE the empty-state check so the row stays
		// visible (the user can re-sort even when zero rows match the
		// current period, e.g., they widen the period and rows reappear).
		myFlipsListPanel.add(myFlipsRecentSortBar);
		myFlipsListPanel.add(sep());

		if (filtered.isEmpty())
		{
			myFlipsListPanel.add(emptyLabel(
				"No trades " + myFlipsPeriod.phrase(),
				"Widen the time filter or place an offer to see rows here"));
			return;
		}

		switch (myFlipsRecentSortIdx)
		{
			case 1: // ROI desc — sells with matched buys first, then everything else by qty desc
				filtered.sort((a, b) ->
				{
					double ra = roiFor(a, profitBySellTimestamp, buyTotalBySellTimestamp);
					double rb = roiFor(b, profitBySellTimestamp, buyTotalBySellTimestamp);
					int cmp = Double.compare(rb, ra);
					if (cmp != 0) return cmp;
					return Long.compare(b.timestamp, a.timestamp);
				});
				break;
			case 2: // Quantity desc
				filtered.sort((a, b) ->
				{
					int cmp = Integer.compare(b.quantity, a.quantity);
					if (cmp != 0) return cmp;
					return Long.compare(b.timestamp, a.timestamp);
				});
				break;
			default: // Profit desc — sells with matched profit first; buys (no profit) by timestamp
				filtered.sort((a, b) ->
				{
					long pa = profitFor(a, profitBySellTimestamp);
					long pb = profitFor(b, profitBySellTimestamp);
					int cmp = Long.compare(pb, pa);
					if (cmp != 0) return cmp;
					return Long.compare(b.timestamp, a.timestamp);
				});
				break;
		}

		int total      = filtered.size();
		int totalPages = pageCount(total);
		myFlipsPage    = clampPage(myFlipsPage, totalPages);
		int from       = myFlipsPage * MY_FLIPS_PAGE_SIZE;
		int to         = Math.min(total, from + MY_FLIPS_PAGE_SIZE);

		for (int i = from; i < to; i++)
		{
			TradeRecord t = filtered.get(i);
			Long rowProfit = !t.isBuy ? profitBySellTimestamp.get(t.timestamp) : null;
			myFlipsListPanel.add(new TradeRecordPanel(t, itemManager, i % 2 != 0, rowProfit, plugin));
			myFlipsListPanel.add(sep());
		}

		appendPageBar(total, totalPages);
	}

	/** Period-start millis for the currently selected My Trades window. */
	private long periodStartMillis()
	{
		switch (myFlipsPeriod)
		{
			case DAILY:
				return java.time.LocalDate.now()
					.atStartOfDay(java.time.ZoneId.systemDefault())
					.toInstant().toEpochMilli();
			case WEEKLY:
				return java.time.LocalDate.now().minusDays(7)
					.atStartOfDay(java.time.ZoneId.systemDefault())
					.toInstant().toEpochMilli();
			case MONTHLY:
				return java.time.LocalDate.now().minusDays(30)
					.atStartOfDay(java.time.ZoneId.systemDefault())
					.toInstant().toEpochMilli();
			default: // ALL_TIME
				return Long.MIN_VALUE;
		}
	}

	private static long profitFor(TradeRecord t, Map<Long, Long> profitBySellTimestamp)
	{
		if (t.isBuy) return Long.MIN_VALUE; // buys sort below sells when sorting by profit desc
		Long p = profitBySellTimestamp.get(t.timestamp);
		return p != null ? p : Long.MIN_VALUE;
	}

	private static double roiFor(TradeRecord t, Map<Long, Long> profitByTs, Map<Long, Long> buyTotalByTs)
	{
		if (t.isBuy) return -Double.MAX_VALUE;
		Long buy    = buyTotalByTs.get(t.timestamp);
		Long profit = profitByTs.get(t.timestamp);
		if (buy == null || buy <= 0 || profit == null) return -Double.MAX_VALUE;
		return 100.0 * profit / buy;
	}

	/**
	 * Margin: closed flips only, filtered by the active period. The inline
	 * sub-sort selector above the items picks the ordering:
	 * <ul>
	 *   <li>0 — Profit desc (default; surfaces the biggest wins)</li>
	 *   <li>1 — Recent first (most recently closed sell at the top)</li>
	 *   <li>2 — ROI% desc (efficiency view — small flips with high margin)</li>
	 * </ul>
	 * Phantom flips (sells with no matching buy in tracked history) are
	 * excluded regardless of sort.
	 */
	private void renderMyFlipsByMargin(com.o7flip.util.ProfitCalculator.Result result)
	{
		long fromMs = periodStartMillis();
		List<com.o7flip.util.ProfitCalculator.CompletedFlip> matched = new ArrayList<>();
		for (com.o7flip.util.ProfitCalculator.CompletedFlip f : result.completedFlips)
		{
			if (f.buyTotal <= 0) continue;
			if (f.sellTimestamp < fromMs) continue;
			matched.add(f);
		}

		// Sort row is added BEFORE the empty-state check so the user can
		// still re-sort when the current period has no matching flips.
		myFlipsListPanel.add(myFlipsMarginSortBar);
		myFlipsListPanel.add(sep());

		if (matched.isEmpty())
		{
			myFlipsListPanel.add(emptyLabel(
				"No completed flips " + myFlipsPeriod.phrase(),
				"Widen the time filter or close more buy/sell pairs to populate this list"));
			return;
		}
		switch (myFlipsMarginSortIdx)
		{
			case 1:
				matched.sort((a, b) -> Long.compare(b.sellTimestamp, a.sellTimestamp));
				break;
			case 2:
				matched.sort((a, b) -> Double.compare(b.roiPct, a.roiPct));
				break;
			default:
				matched.sort((a, b) -> Long.compare(b.profit, a.profit));
				break;
		}

		int total      = matched.size();
		int totalPages = pageCount(total);
		myFlipsPage    = clampPage(myFlipsPage, totalPages);
		int from       = myFlipsPage * MY_FLIPS_PAGE_SIZE;
		int to         = Math.min(total, from + MY_FLIPS_PAGE_SIZE);

		for (int i = from; i < to; i++)
		{
			myFlipsListPanel.add(new com.o7flip.ui.CompletedFlipRow(matched.get(i), itemManager, i % 2 != 0, plugin));
			myFlipsListPanel.add(sep());
		}

		appendPageBar(total, totalPages);
	}

	/**
	 * Active: live GE state — one row per occupied slot, with progress bars
	 * for partially-filled offers. Sources from {@code plugin.activeOffers},
	 * which the plugin keeps in sync via the GrandExchangeOfferChanged event
	 * handler. The plugin calls {@code panel.updateMyFlips(...)} from that
	 * handler so this renders update live as offers fill.
	 */
	private void renderMyFlipsActive()
	{
		java.util.Map<Integer, com.o7flip.model.ActiveOfferSnapshot> offers =
			plugin != null ? plugin.activeOffers : java.util.Collections.emptyMap();

		if (offers == null || offers.isEmpty())
		{
			myFlipsListPanel.add(emptyLabel("No active GE orders",
				"Buys and sells will show here while they're on the exchange"));
			return;
		}

		// Sort by slot index so rows stay in a consistent order across rerenders
		// — otherwise progress bars would visually jump as offers fill out of order.
		List<Integer> slots = new ArrayList<>(offers.keySet());
		Collections.sort(slots);

		int rendered = 0;
		for (int slot : slots)
		{
			com.o7flip.model.ActiveOfferSnapshot offer = offers.get(slot);
			if (offer == null || offer.itemId <= 0)
			{
				continue;
			}
			myFlipsListPanel.add(new com.o7flip.ui.ActiveOfferRow(offer, itemManager, rendered % 2 != 0, plugin));
			myFlipsListPanel.add(sep());
			rendered++;
		}

		if (rendered == 0)
		{
			myFlipsListPanel.add(emptyLabel("No active GE orders",
				"Buys and sells will show here while they're on the exchange"));
		}
		// No pagination on Active — max 8 rows ever (one per GE slot).
	}

	private static int pageCount(int total)
	{
		return Math.max(1, (total + MY_FLIPS_PAGE_SIZE - 1) / MY_FLIPS_PAGE_SIZE);
	}

	private static int clampPage(int page, int totalPages)
	{
		if (page < 0)            return 0;
		if (page >= totalPages)  return totalPages - 1;
		return page;
	}

	/**
	 * Footer-style row showing "‹ Page 2 / 12 ›" when the underlying list
	 * has more rows than fit on a single page. Hidden when total ≤ page size
	 * since pagination would just be visual noise. Buttons disable at
	 * boundaries so the user can't navigate off the ends.
	 */
	private void appendPageBar(int total, int totalPages)
	{
		if (total <= MY_FLIPS_PAGE_SIZE)
		{
			return;
		}
		JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 8, 4));
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		bar.setBorder(new EmptyBorder(6, 0, 6, 0));

		JButton prev = pageBtn("‹");
		prev.setEnabled(myFlipsPage > 0);
		prev.addActionListener(e ->
		{
			if (myFlipsPage > 0)
			{
				myFlipsPage--;
				renderMyFlips();
			}
		});

		JLabel label = new JLabel("Page " + (myFlipsPage + 1) + " / " + totalPages);
		label.setFont(Fonts.SM);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JButton next = pageBtn("›");
		next.setEnabled(myFlipsPage < totalPages - 1);
		next.addActionListener(e ->
		{
			if (myFlipsPage < totalPages - 1)
			{
				myFlipsPage++;
				renderMyFlips();
			}
		});

		bar.add(prev);
		bar.add(label);
		bar.add(next);
		myFlipsListPanel.add(bar);
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
				searchResultsPanel.add(new SearchResultPanel(items.get(i), itemManager, i % 2 != 0, plugin));
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

		JPanel rightCluster = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
		rightCluster.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rightCluster.add(statusLabel);

		header.add(title,        BorderLayout.WEST);
		header.add(rightCluster, BorderLayout.EAST);

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

	private boolean shouldShowTab(String name)
	{
		if (config == null)
		{
			return true;
		}
		switch (name)
		{
			case "Flips":     return config.showFlips();
			case "Dumps":     return config.showDumps();
			case "Item":      return config.showInsights();
			case "Alerts":    return config.showAlerts();
			case "Moons":     return config.showMoon()    && isSignedIn;
			case "Barrows":   return config.showBarrows() && isSignedIn;
			case "Decant":    return config.showDecant();
			case "My Trades": return config.showMyFlips();
			default:          return false;
		}
	}

	/** Default left-to-right order of the panel tabs. Mirrors the order
	 *  this plugin shipped with before the reorder feature was added.
	 *  Used by {@link com.o7flip.ui.TabOrderDialog} as the "reset" value. */
	public static final List<String> DEFAULT_TAB_ORDER = java.util.Arrays.asList(
		// Row 1 (4 tabs)
		"Flips", "My Trades", "Alerts", "Dumps",
		// Row 2 (4 tabs)
		"Moons", "Barrows", "Decant", "Item"
	);

	/**
	 * Resolves the user's preferred tab order from config, falling back to
	 * the default order. Any name in config that doesn't match a known tab
	 * is dropped; any default tab name not listed in config is appended at
	 * the end so newly-added tabs naturally show without requiring config
	 * migration.
	 */
	public List<String> resolveTabOrder()
	{
		String raw = config != null ? config.tabOrder() : "";
		if (raw == null || raw.trim().isEmpty())
		{
			return new ArrayList<>(DEFAULT_TAB_ORDER);
		}
		List<String> result = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (String token : raw.split(","))
		{
			String name = token.trim();
			if (DEFAULT_TAB_ORDER.contains(name) && !seen.contains(name))
			{
				result.add(name);
				seen.add(name);
			}
		}
		for (String name : DEFAULT_TAB_ORDER)
		{
			if (!seen.contains(name))
			{
				result.add(name);
			}
		}
		return result;
	}

	private JTabbedPane buildTabs()
	{
		// WRAP_TAB_LAYOUT spreads all tabs across multiple rows so every tab
		// is visible at once — avoids the awkward "> chevron + dropdown"
		// overflow that SCROLL_TAB_LAYOUT shows in narrow side panels.
		// 9 tabs at SM font fit comfortably across 2 rows of the side panel.
		JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.setForeground(Color.WHITE);
		tabs.setFont(Fonts.SM);

		// Always build all tab content to initialise list-panel fields,
		// then conditionally add each tab based on config + auth state.
		JPanel flipsContent    = buildFlipsTab();
		JPanel dumpsContent    = buildDumpsTab();
		JPanel spikesContent   = buildSpikesTab();
		JPanel insightsContent = buildInsightsTab();
		JPanel alertsContent   = buildGenericTab("Merch");
		JPanel moonContent     = buildMoonTab();
		JPanel barrowsContent  = buildGenericTab("Barrows");
		JPanel decantContent   = buildGenericTab("Decant");
		JPanel myFlipsContent  = buildMyFlipsTab();

		// Map each tab name to its (content, visibility predicate) — the
		// dialog reorder list and config tab-toggles drive what actually
		// gets added below.
		java.util.Map<String, JPanel> contentByName = new java.util.HashMap<>();
		contentByName.put("Flips",     flipsContent);
		contentByName.put("Dumps",     dumpsContent);
		contentByName.put("Item",      insightsContent);
		contentByName.put("Alerts",    alertsContent);
		contentByName.put("Moons",     moonContent);
		contentByName.put("Barrows",   barrowsContent);
		contentByName.put("Decant",    decantContent);
		contentByName.put("My Trades", myFlipsContent);
		// Spikes intentionally not registered: feature retired from the panel
		// per design feedback. The buildSpikesTab() call above still runs to
		// initialise listPanel state used by sortFlips/filtered fallbacks.

		for (String name : resolveTabOrder())
		{
			if (!shouldShowTab(name))
			{
				continue;
			}
			JPanel content = contentByName.get(name);
			if (content == null)
			{
				continue;
			}
			tabs.addTab(name, content);
		}

		// Lazy-create the Insights panel the first time the user navigates to
		// the Item tab. We can't construct it in buildInsightsTab() because
		// itemManager is @Inject'd by Guice *after* the constructor returns,
		// so capturing it at construction time would leave the panel with a
		// permanently-null reference. By the time the user can click a tab,
		// injection has completed and itemManager is populated.
		tabs.addChangeListener(e ->
		{
			int idx = tabs.getSelectedIndex();
			if (idx >= 0 && "Item".equals(tabs.getTitleAt(idx)))
			{
				ensureInsightsPanel();
			}
		});

		// Cover the edge case where Item is the user's first tab (custom
		// reorder) — Swing won't fire the listener for the initial selection
		// since it happens before the listener is attached. Defer to EDT to
		// give Guice a moment to finish field injection before we capture
		// itemManager into the InsightsPanel constructor.
		SwingUtilities.invokeLater(() ->
		{
			int idx = tabs.getSelectedIndex();
			if (idx >= 0 && "Item".equals(tabs.getTitleAt(idx)))
			{
				ensureInsightsPanel();
			}
		});

		return tabs;
	}

	private JPanel insightsHost;

	private JPanel buildInsightsTab()
	{
		// itemManager is null at panel-construction time (Guice field-injects
		// AFTER the constructor returns). Defer InsightsPanel creation to the
		// first show — same lazy pattern other tabs use via lambdas.
		insightsHost = listPanel();
		return assembleTab(null, insightsHost, null);
	}

	/**
	 * Context-aware target for the footer "07flip.com" button. When the user
	 * is on the Item tab and an item is currently loaded, link directly to
	 * that item's page on the website. Anywhere else, fall back to the
	 * homepage.
	 */
	private String resolveWebsiteUrl()
	{
		String selectedTab = currentTabName();
		if ("Item".equals(selectedTab) && plugin != null && plugin.currentInsights != null
			&& plugin.currentInsights.itemId > 0)
		{
			return "https://07flip.com/item/" + plugin.currentInsights.itemId;
		}
		return WEBSITE_URL;
	}

	private String currentTabName()
	{
		if (tabsWrapper == null || tabsWrapper.getComponentCount() == 0)
		{
			return null;
		}
		java.awt.Component c = tabsWrapper.getComponent(0);
		if (!(c instanceof JTabbedPane))
		{
			return null;
		}
		JTabbedPane pane = (JTabbedPane) c;
		int idx = pane.getSelectedIndex();
		return idx >= 0 ? pane.getTitleAt(idx) : null;
	}

	private com.o7flip.ui.InsightsPanel ensureInsightsPanel()
	{
		if (insightsPanel == null && insightsHost != null)
		{
			insightsPanel = new com.o7flip.ui.InsightsPanel(itemManager, plugin);
			insightsHost.add(insightsPanel);
			// If the user had an item loaded before this panel was torn down
			// (e.g. by an auth-refresh rebuilding the tabs), restore it so
			// data refreshes don't blank the Item tab. Otherwise seed the
			// empty-state recommendations from the Flips list.
			com.o7flip.model.ItemInsights loaded = plugin != null ? plugin.currentInsights : null;
			if (loaded != null)
			{
				insightsPanel.show(loaded);
			}
			else
			{
				pushInsightsRecommendations();
			}
		}
		return insightsPanel;
	}

	private void pushInsightsRecommendations()
	{
		if (insightsPanel == null || allFlips == null || allFlips.isEmpty())
		{
			return;
		}
		// Top 3 by score, falling back to the order the server returned (which
		// is already 07Flip-score-sorted on the Flips fetch). Filtering out
		// score==null items keeps "Recommended" focused on items the engine
		// has high-confidence numbers for.
		List<com.o7flip.model.FlipItem> top = new ArrayList<>();
		for (com.o7flip.model.FlipItem f : allFlips)
		{
			if (f.flip07Score == null) continue;
			top.add(f);
			if (top.size() >= 3) break;
		}
		if (top.isEmpty() && !allFlips.isEmpty())
		{
			top = allFlips.subList(0, Math.min(3, allFlips.size()));
		}
		insightsPanel.setRecommended(top, plugin);
	}

	/** Switch to the Insights tab and show a loading state for the requested item. */
	public void showInsightsLoading(int itemId, String fallbackName)
	{
		com.o7flip.ui.InsightsPanel p = ensureInsightsPanel();
		if (p != null)
		{
			p.showLoading(itemId, fallbackName);
		}
		// If the search overlay is currently showing (user clicked a search
		// result), swap the CardLayout back to the tabs view so the Item tab's
		// content is actually visible — otherwise selectTab() picks the right
		// tab underneath but the search card stays on top.
		CardLayout cl = (CardLayout) mainArea.getLayout();
		cl.show(mainArea, "tabs");
		// Clear the search field so the next character the user types starts
		// a fresh search instead of re-triggering on the lingering query.
		if (searchField != null)
		{
			searchField.setText("");
		}
		selectTab("Item");
	}

	/** Render the fetched Insights data, ignoring late callbacks for items the user moved off. */
	public void showInsights(int itemId, com.o7flip.model.ItemInsights insights)
	{
		com.o7flip.ui.InsightsPanel p = ensureInsightsPanel();
		if (p != null)
		{
			p.show(insights);
		}
	}

	private JPanel buildMyFlipsTab()
	{
		myFlipsListPanel = listPanel();
		myFlipsStatsPanel = new com.o7flip.ui.MyTradesStatsPanel();
		// Wire the membership cost row callbacks. The stats panel deliberately
		// stays decoupled from plugin config and dialog UI; this glue lives
		// here in the parent panel where dialogs and config are already in scope.
		myFlipsStatsPanel.setOnMembershipToggle(() ->
		{
			if (plugin != null)
			{
				plugin.setMembershipCostHidden(!plugin.isMembershipCostHidden());
			}
		});
		myFlipsStatsPanel.setOnMembershipAdjust(this::openMembershipAdjustDialog);

		// Header is two stacked rows: the existing Active/Recent/Margin
		// switcher with a trailing Filter pill (period selector), and a
		// second row that only appears when Margin is the active view to
		// expose the Profit/Recent/ROI sub-sort. Keeping the sub-sort out
		// of the main row stops it cluttering Active and Recent where it
		// has no meaning.
		myFlipsSortBtns = new JButton[3];
		// requiresSignIn=false — all three views (Active / Recent / Margin) read
		// from local state (tradeHistory + activeOffers). Anonymous/free users
		// must be able to switch between them without an auth gate eating the click.
		JPanel sortBar = buildSortBar(myFlipsSortBtns,
			new String[]{"Active", "Recent", "Margin"},
			() -> myFlipsSortIdx,
			i ->
			{
				myFlipsSortIdx = i;
				myFlipsPage    = 0;   // reset to first page when switching sort
				renderMyFlips();
			},
			false);

		// Period filter pill sits at the right edge of the sort bar — opens
		// a small popup with Daily / Weekly / Monthly / All time so the
		// user can re-target every stat in MyTradesStatsPanel at once.
		// Daily is the default since that's the most-checked window.
		myFlipsPeriodButton = pillButton(periodPillLabel());
		myFlipsPeriodButton.setToolTipText("Click to choose the stats time window: Today / This week / This month / All time");
		myFlipsPeriodButton.addActionListener(e -> showMyFlipsPeriodMenu());
		JPanel sortBarTrail = new JPanel(new java.awt.BorderLayout());
		sortBarTrail.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortBarTrail.setBorder(new javax.swing.border.MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		sortBarTrail.add(sortBar, java.awt.BorderLayout.CENTER);
		JPanel filterWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
		filterWrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		filterWrap.add(myFlipsPeriodButton);
		sortBarTrail.add(filterWrap, java.awt.BorderLayout.EAST);

		// Sub-sort rows for Margin and Recent. Built once, kept as fields,
		// and the renderers add the matching one to the list panel right
		// after the stats card. Placing the sub-sort INSIDE the scrolling
		// list keeps it logically grouped with the items it sorts and
		// stops it cluttering Active where it has no meaning.
		myFlipsMarginSortBtns = new JButton[3];
		myFlipsMarginSortBar = buildSortBar(myFlipsMarginSortBtns,
			new String[]{"Profit", "Recent", "ROI%"},
			() -> myFlipsMarginSortIdx,
			i ->
			{
				myFlipsMarginSortIdx = i;
				myFlipsPage = 0;
				renderMyFlips();
			},
			false);
		// LEFT_ALIGNMENT is mandatory before adding to the listPanel — its
		// BoxLayout otherwise treats this bar's default CENTER_ALIGNMENT as
		// the layout anchor for any narrower neighbour, shifting the stats
		// card to the right (the "ghost row" the user spotted).
		myFlipsMarginSortBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		myFlipsMarginSortBar.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, myFlipsMarginSortBar.getPreferredSize().height));

		myFlipsRecentSortBtns = new JButton[3];
		myFlipsRecentSortBar = buildSortBar(myFlipsRecentSortBtns,
			new String[]{"Profit", "ROI", "Quantity"},
			() -> myFlipsRecentSortIdx,
			i ->
			{
				myFlipsRecentSortIdx = i;
				myFlipsPage = 0;
				renderMyFlips();
			},
			false);
		myFlipsRecentSortBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		myFlipsRecentSortBar.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, myFlipsRecentSortBar.getPreferredSize().height));

		attachClearPopup(myFlipsListPanel);

		renderMyFlips();

		JPanel footer = new JPanel();
		footer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		return assembleTab(sortBarTrail, myFlipsListPanel, footer);
	}

	private String periodPillLabel()
	{
		// Just the period name — the "Filter:" prefix wasted enough horizontal
		// space on the My Trades header to push the Margin button out of view.
		return myFlipsPeriod.label;
	}

	private void showMyFlipsPeriodMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		for (com.o7flip.ui.MyTradesStatsPanel.Period p : com.o7flip.ui.MyTradesStatsPanel.Period.values())
		{
			JMenuItem item = new JMenuItem(p.label);
			final com.o7flip.ui.MyTradesStatsPanel.Period selected = p;
			item.addActionListener(ae ->
			{
				if (myFlipsPeriod != selected)
				{
					myFlipsPeriod = selected;
					myFlipsPeriodButton.setText(periodPillLabel());
					renderMyFlips();
				}
			});
			menu.add(item);
		}
		menu.show(myFlipsPeriodButton, 0, myFlipsPeriodButton.getHeight());
	}

	/**
	 * Opens a two-field dialog letting the user manually seed the lifetime
	 * bond ledger. Useful when the migration couldn't recover historical
	 * bonds because their TradeRecord rows had been evicted from the
	 * 200-row tradeHistory window before the ledger existed (the exact
	 * symptom: "I bought 21 bonds for membership but they don't show
	 * because the recent flip-style 3+3 wiped them out of history").
	 */
	private void openMembershipAdjustDialog()
	{
		if (plugin == null)
		{
			return;
		}
		com.o7flip.util.BondLedger current = plugin.bondLedger != null
			? plugin.bondLedger
			: com.o7flip.util.BondLedger.EMPTY;

		javax.swing.JTextField countField = new javax.swing.JTextField(String.valueOf(current.count), 8);
		javax.swing.JTextField spendField = new javax.swing.JTextField(String.valueOf(current.spend), 14);

		JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
		form.add(new javax.swing.JLabel("Lifetime bonds bought:"));
		form.add(countField);
		form.add(new javax.swing.JLabel("Lifetime gp spent:"));
		form.add(spendField);
		form.add(new javax.swing.JLabel("<html><font color='#888888'>Negative or non-numeric inputs are rejected.</font></html>"));
		form.add(new javax.swing.JLabel(""));

		int choice = javax.swing.JOptionPane.showConfirmDialog(
			this,
			form,
			"Adjust lifetime bond ledger",
			javax.swing.JOptionPane.OK_CANCEL_OPTION,
			javax.swing.JOptionPane.PLAIN_MESSAGE);

		if (choice != javax.swing.JOptionPane.OK_OPTION)
		{
			return;
		}

		long spend;
		int  count;
		try
		{
			count = Integer.parseInt(countField.getText().trim());
			spend = Long.parseLong(spendField.getText().trim());
		}
		catch (NumberFormatException ex)
		{
			javax.swing.JOptionPane.showMessageDialog(this,
				"Both fields must be whole numbers (no commas or 'gp').",
				"Adjust lifetime bond ledger",
				javax.swing.JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (count < 0 || spend < 0)
		{
			javax.swing.JOptionPane.showMessageDialog(this,
				"Counts and gp must be zero or positive.",
				"Adjust lifetime bond ledger",
				javax.swing.JOptionPane.WARNING_MESSAGE);
			return;
		}
		plugin.setBondLedger(spend, count);
	}

	/**
	 * Right-click popup on the trade list — replaces the dropped "Clear" pill
	 * button. Attached to the list panel itself rather than individual rows so
	 * row-level right-click handlers (if added later) can coexist.
	 */
	private void attachClearPopup(JPanel listPanel)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem clearItem = new JMenuItem("Clear local trade history…");
		clearItem.setToolTipText("Erase the local trade history stored on this machine. Server data is not affected.");
		clearItem.addActionListener(e ->
		{
			int choice = javax.swing.JOptionPane.showConfirmDialog(
				listPanel,
				"Erase the local trade history stored on this machine?\n"
					+ "Trades synced from 07flip.com will repopulate on next sync.",
				"Clear local trade history",
				javax.swing.JOptionPane.OK_CANCEL_OPTION,
				javax.swing.JOptionPane.WARNING_MESSAGE);
			if (choice == javax.swing.JOptionPane.OK_OPTION && plugin != null)
			{
				plugin.clearTradeHistory();
			}
		});
		menu.add(clearItem);
		listPanel.setComponentPopupMenu(menu);
	}

	/**
	 * Surfaces the server's "premium required" rejection. Resets the preset
	 * dropdown back to the free default ("All Flips") so the user isn't
	 * stuck on an empty list, then offers to open the upgrade URL.
	 */
	public void showPremiumRequiredToast(String upgradeUrl)
	{
		String url = (upgradeUrl == null || upgradeUrl.isEmpty()) ? "https://07flip.com/premium" : upgradeUrl;
		// No preset dropdown to reset anymore — the Flips tab no longer
		// exposes premium presets. Just inform the user and offer to open
		// the upgrade URL.
		int choice = javax.swing.JOptionPane.showConfirmDialog(this,
			"That preset requires a 07Flip premium subscription. Open the upgrade page?",
			"Premium required",
			javax.swing.JOptionPane.OK_CANCEL_OPTION,
			javax.swing.JOptionPane.INFORMATION_MESSAGE);
		if (choice == javax.swing.JOptionPane.OK_OPTION)
		{
			net.runelite.client.util.LinkBrowser.browse(url);
		}
	}


	/**
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
		// Capture the currently-selected tab name BEFORE tearing down so we
		// can restore it after the rebuild. Without this, every rebuild snaps
		// the user back to the first tab — including the rebuilds triggered
		// by background auth-status refreshes, which yanked users off
		// My Trades / Item every refresh cycle.
		String previouslySelected = currentSelectedTabName();

		// The Item tab's lazy-init pattern caches the InsightsPanel in a
		// field but its host container is recreated by buildInsightsTab()
		// on every rebuild. Without clearing the field reference, the old
		// InsightsPanel stays bound to a detached host and the new host
		// renders blank. Null it out so ensureInsightsPanel rebuilds it
		// against the fresh host.
		insightsPanel = null;

		tabsWrapper.removeAll();
		tabsWrapper.add(buildTabs(), BorderLayout.CENTER);
		tabsWrapper.revalidate();
		tabsWrapper.repaint();
		String q = filtered();
		renderFlips(q);
		renderSpikes(q);
		renderDumps(q);
		renderBarrows(q);
		renderMoon(q);
		renderDecants(q);
		renderAlerts(q);
		refreshBlocklistFooter();

		if (previouslySelected != null)
		{
			selectTab(previouslySelected);
		}
	}

	/** Returns the title of the currently selected tab, or null if none. */
	private String currentSelectedTabName()
	{
		if (tabsWrapper.getComponentCount() == 0)
		{
			return null;
		}
		java.awt.Component c = tabsWrapper.getComponent(0);
		if (!(c instanceof JTabbedPane))
		{
			return null;
		}
		JTabbedPane pane = (JTabbedPane) c;
		int idx = pane.getSelectedIndex();
		if (idx < 0 || idx >= pane.getTabCount())
		{
			return null;
		}
		return pane.getTitleAt(idx);
	}

	public void refreshBlocklistFooter()
	{
		if (hiddenCountLabel == null)
		{
			return;
		}
		int n = plugin != null ? plugin.blocklist.size() : 0;
		if (n == 0)
		{
			hiddenCountLabel.setText(" ");
			hiddenCountLabel.setVisible(false);
		}
		else
		{
			hiddenCountLabel.setText(n + " item" + (n == 1 ? "" : "s") + " hidden — manage");
			hiddenCountLabel.setVisible(true);
		}
	}

	private void showBlocklistDialog()
	{
		if (plugin == null || plugin.blocklist.isEmpty())
		{
			return;
		}
		java.util.List<Integer> ids = new java.util.ArrayList<>(plugin.blocklist);
		ids.sort(Integer::compareTo);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(8, 8, 8, 8));

		for (Integer id : ids)
		{
			String name;
			try
			{
				name = itemManager != null ? itemManager.getItemComposition(id).getName() : ("Item " + id);
			}
			catch (Exception ex)
			{
				name = "Item " + id;
			}

			JLabel nameLbl = new JLabel(name);
			nameLbl.setFont(Fonts.SM);
			nameLbl.setForeground(Color.WHITE);

			JButton remove = pillButton("Unhide");
			remove.addActionListener(ev ->
			{
				plugin.removeFromBlocklist(id);
				java.awt.Window w = SwingUtilities.getWindowAncestor(remove);
				if (w != null)
				{
					w.dispose();
				}
				if (!plugin.blocklist.isEmpty())
				{
					showBlocklistDialog();
				}
			});

			JPanel row = new JPanel(new BorderLayout(8, 0));
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(new EmptyBorder(4, 6, 4, 6));
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
			row.add(FlipItemPanel.buildIcon(id, itemManager), BorderLayout.WEST);
			row.add(nameLbl, BorderLayout.CENTER);
			row.add(remove,  BorderLayout.EAST);
			content.add(row);
			content.add(Box.createVerticalStrut(4));
		}

		JButton clearAll = pillButton("Unhide all");
		clearAll.setBackground(ORANGE);
		clearAll.setForeground(Color.BLACK);
		clearAll.addActionListener(ev ->
		{
			plugin.clearBlocklist();
			java.awt.Window w = SwingUtilities.getWindowAncestor(clearAll);
			if (w != null)
			{
				w.dispose();
			}
		});
		content.add(Box.createVerticalStrut(8));
		content.add(clearAll);

		JScrollPane sp = new JScrollPane(content);
		sp.setPreferredSize(new Dimension(280, Math.min(360, 60 + ids.size() * 36)));
		sp.setBorder(BorderFactory.createEmptyBorder());

		javax.swing.JDialog dialog = new javax.swing.JDialog(SwingUtilities.getWindowAncestor(this), "Hidden items");
		dialog.setContentPane(sp);
		dialog.pack();
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private JPanel buildFlipsTab()
	{
		// Filter button only \u2014 header text removed; the score-sorted list
		// speaks for itself.
		flipsFilterButton = pillButton("Filter");
		flipsFilterButton.addActionListener(e -> toggleFlipsFilterPanel());

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerRow.setBorder(new EmptyBorder(6, 10, 4, 8));
		headerRow.add(flipsFilterButton, BorderLayout.EAST);

		// \u2500\u2500 Active filter chips (visible only when any filter is set) \u2500\u2500\u2500\u2500
		flipsChipBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		flipsChipBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		flipsChipBar.setBorder(new EmptyBorder(0, 8, 4, 8));
		flipsChipBar.setVisible(false);

		// \u2500\u2500 Collapsible filter panel \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
		flipsFilterPanel = buildFlipsFilterPanel();
		flipsFilterPanel.setVisible(false);

		JPanel topBar = new JPanel();
		topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(headerRow);
		topBar.add(flipsChipBar);
		topBar.add(flipsFilterPanel);

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

		// Refresh chips and combo selections to match current state.
		rebuildFlipsChipBar();

		return assembleTab(topBar, flipsListPanel, buildPageBar(flipsPageLabel, flipsPrev, flipsNext));
	}

	/** Builds the collapsible "Capital / Account / Min profit" filter panel. */
	private JPanel buildFlipsFilterPanel()
	{
		flipsCapitalCombo = styledCombo(CAPITAL_LABELS);
		flipsCapitalCombo.setSelectedIndex(flipsCapitalIdx);
		flipsCapitalCombo.addActionListener(e ->
		{
			flipsCapitalIdx = flipsCapitalCombo.getSelectedIndex();
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null)
			{
				plugin.onFlipsFilterChanged();
			}
		});

		flipsMinProfitCombo = styledCombo(MIN_PROFIT_LABELS);
		flipsMinProfitCombo.setSelectedIndex(flipsMinProfitIdx);
		flipsMinProfitCombo.addActionListener(e ->
		{
			flipsMinProfitIdx = flipsMinProfitCombo.getSelectedIndex();
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null)
			{
				plugin.onFlipsFilterChanged();
			}
		});

		flipsMembersBtn = pillButton("Members");
		flipsF2pBtn     = pillButton("F2P");
		applyAccountStyle();

		flipsMembersBtn.addActionListener(e ->
		{
			if (!flipsF2pOnly)
			{
				return;
			}
			flipsF2pOnly = false;
			applyAccountStyle();
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null)
			{
				plugin.onFlipsFilterChanged();
			}
		});
		flipsF2pBtn.addActionListener(e ->
		{
			if (flipsF2pOnly)
			{
				return;
			}
			flipsF2pOnly = true;
			applyAccountStyle();
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null)
			{
				plugin.onFlipsFilterChanged();
			}
		});

		JPanel accountRow = new JPanel(new GridLayout(1, 2, 4, 0));
		accountRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		accountRow.add(flipsMembersBtn);
		accountRow.add(flipsF2pBtn);

		JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(0, 8, 8, 8));
		panel.add(filterRowLabel("Capital"));
		panel.add(flipsCapitalCombo);
		panel.add(filterRowLabel("Min profit"));
		panel.add(flipsMinProfitCombo);
		panel.add(filterRowLabel("Account"));
		panel.add(accountRow);
		return panel;
	}

	/** Highlights the active Members/F2P button using the existing sort-bar style. */
	private void applyAccountStyle()
	{
		if (flipsMembersBtn == null || flipsF2pBtn == null)
		{
			return;
		}
		applySortStyle(flipsMembersBtn, !flipsF2pOnly);
		applySortStyle(flipsF2pBtn,      flipsF2pOnly);
	}

	private static JLabel filterRowLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(Fonts.SM);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private void toggleFlipsFilterPanel()
	{
		flipsFilterPanelOpen = !flipsFilterPanelOpen;
		flipsFilterPanel.setVisible(flipsFilterPanelOpen);
		flipsFilterButton.setText(flipsFilterPanelOpen ? "Filter \u25B4" : "Filter");
		flipsFilterPanel.revalidate();
		flipsFilterPanel.repaint();
	}

	/** Renders one chip per active filter; click the \u00D7 to clear that filter. */
	private void rebuildFlipsChipBar()
	{
		if (flipsChipBar == null)
		{
			return;
		}
		flipsChipBar.removeAll();
		if (flipsCapitalIdx > 0)
		{
			flipsChipBar.add(buildFilterChip("Capital: " + CAPITAL_LABELS[flipsCapitalIdx], () ->
			{
				flipsCapitalIdx = 0;
				if (flipsCapitalCombo != null)
				{
					flipsCapitalCombo.setSelectedIndex(0);
				}
			}));
		}
		if (flipsMinProfitIdx > 0)
		{
			flipsChipBar.add(buildFilterChip("Min: " + MIN_PROFIT_LABELS[flipsMinProfitIdx], () ->
			{
				flipsMinProfitIdx = 0;
				if (flipsMinProfitCombo != null)
				{
					flipsMinProfitCombo.setSelectedIndex(0);
				}
			}));
		}
		if (flipsF2pOnly)
		{
			flipsChipBar.add(buildFilterChip("F2P only", () ->
			{
				flipsF2pOnly = false;
				applyAccountStyle();
			}));
		}
		flipsChipBar.setVisible(flipsChipBar.getComponentCount() > 0);
		flipsChipBar.revalidate();
		flipsChipBar.repaint();
	}

	/** Pill-shaped chip with a small \u00D7 that resets the filter when clicked. */
	private JButton buildFilterChip(String text, Runnable onClear)
	{
		JButton chip = new JButton(text + "  \u00D7");
		chip.setFont(Fonts.SM);
		chip.setBackground(new Color(0x2F2F2F));
		chip.setForeground(Color.WHITE);
		chip.setBorder(new EmptyBorder(2, 8, 2, 8));
		chip.setFocusable(false);
		chip.setToolTipText("Click to clear this filter");
		chip.addActionListener(ev ->
		{
			onClear.run();
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null)
			{
				plugin.onFlipsFilterChanged();
			}
		});
		return chip;
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

		// Source toggle: switch between /dumps (all dumps) and /bot-dumps
		// (machine-detected bot-driven dumps). Same response shape on the
		// server side so both feeds render through the existing DumpItemPanel.
		JComboBox<String> sourceCb = styledCombo(new String[]{"All Dumps", "Bot Dumps"});
		sourceCb.setSelectedIndex(dumpsUseBotEndpoint ? 1 : 0);
		sourceCb.addActionListener(e ->
		{
			boolean wantBot = sourceCb.getSelectedIndex() == 1;
			if (wantBot == dumpsUseBotEndpoint)
			{
				return;
			}
			dumpsUseBotEndpoint = wantBot;
			dumpsPage = 0;
			if (plugin != null)
			{
				plugin.onDumpsFilterChanged();
			}
		});
		JPanel sourceRow = new JPanel(new BorderLayout());
		sourceRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sourceRow.setBorder(new EmptyBorder(4, 8, 0, 8));
		sourceRow.add(sourceCb, BorderLayout.CENTER);

		JPanel topBar = new JPanel();
		topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(sourceRow);
		topBar.add(filterRow);
		topBar.add(sortRow);

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
				alertsListPanel = listPanel();
				return assembleTab(buildAlertsSortBar(), alertsListPanel, null);
		}
	}

	/**
	 * Three-tab sort bar for the Alerts panel: Most Recent / Pending / Successful.
	 * Free users only ever see the Successful button \u2014 Most Recent and Pending
	 * are hidden, and the index is forced to "Successful" on render. Premium
	 * users see all three with Most Recent as the default.
	 */
	private JPanel buildAlertsSortBar()
	{
		String[] labels = isPremium
			? new String[]{"Most Recent", "Pending", "Successful"}
			: new String[]{"Successful"};
		alertsSortBtns = new JButton[labels.length];
		// Free user only has the Successful button \u2014 index 0 maps to "successful".
		// Premium users get the full ordering (0=recent, 1=pending, 2=successful).
		if (!isPremium)
		{
			alertsSortIdx = 0;
		}
		return buildSortBar(alertsSortBtns, labels,
			() -> alertsSortIdx,
			i ->
			{
				alertsSortIdx = i;
				renderAlerts(filtered());
			});
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

	// buildPresetRenderer removed — preset dropdown no longer exists.

	// =========================================================================
	// Sort bar — pill buttons
	// =========================================================================

	private JPanel buildSortBar(JButton[] store, String[] labels, IntSupplier get, IntConsumer set)
	{
		return buildSortBar(store, labels, get, set, true);
	}

	/**
	 * Sort-bar builder with an explicit "requires sign-in" flag. The default
	 * (true) is for tabs whose sort triggers a server request — if the user
	 * isn't signed in we silently swallow the click since the request would
	 * fail anyway. Pass {@code false} for tabs that operate on local data
	 * (My Trades, Active GE state) so anonymous / free users can still
	 * switch between local views.
	 */
	private JPanel buildSortBar(JButton[] store, String[] labels, IntSupplier get, IntConsumer set, boolean requiresSignIn)
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
				if (requiresSignIn && !isSignedIn)
				{
					return;
				}
				set.accept(idx);
				if (requiresSignIn)
				{
					hilite(store, idx);
				}
				else
				{
					hiliteLocal(store, idx);
				}
			});
			store[i] = btn;
			bar.add(btn);
		}
		return bar;
	}

	/**
	 * Like {@link #hilite}, but doesn't gate enabled state on {@code isSignedIn}.
	 * Used for sort bars over local-only data, where the auth gate would
	 * incorrectly disable working buttons for anonymous users.
	 */
	private void hiliteLocal(JButton[] btns, int active)
	{
		if (btns == null)
		{
			return;
		}
		for (int i = 0; i < btns.length; i++)
		{
			btns[i].setEnabled(true);
			applySortStyle(btns[i], i == active);
		}
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
		if (topBar != null)
		{
			tab.add(topBar, BorderLayout.NORTH);
		}
		tab.add(sp, BorderLayout.CENTER);
		if (pageBar != null)
		{
			tab.add(pageBar, BorderLayout.SOUTH);
		}
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
		wb.addActionListener(e -> openUrl(resolveWebsiteUrl()));
		db.addActionListener(e -> openUrl(DISCORD_URL));

		JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
		btns.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		btns.setBorder(new EmptyBorder(6, 10, 4, 10));
		btns.add(wb);
		btns.add(db);

		hiddenCountLabel = new JLabel(" ");
		hiddenCountLabel.setFont(Fonts.SM);
		hiddenCountLabel.setForeground(new Color(0x888888));
		hiddenCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
		hiddenCountLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hiddenCountLabel.setBorder(new EmptyBorder(0, 0, 2, 0));
		hiddenCountLabel.setVisible(false);
		hiddenCountLabel.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				showBlocklistDialog();
			}
		});

		JPanel footer = new JPanel(new BorderLayout());
		footer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		footer.setBorder(new MatteBorder(1, 0, 0, 0, new Color(0x3A3A3A)));
		footer.add(hiddenCountLabel, BorderLayout.NORTH);
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
