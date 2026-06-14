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
import com.o7flip.ui.DipItemPanel;
import com.o7flip.ui.DumpItemPanel;
import com.o7flip.ui.HighAlchPanel;
import com.o7flip.ui.ScreenerMatchPanel;
import com.o7flip.ui.ScreenerPresetCard;
import com.o7flip.ui.TeleTabletPanel;
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
	/** Account page that shows the user's API key — the link the no-key banner pushes. */
	private static final String ACCOUNT_URL   = "https://07flip.com/account";

	// ── Upsell-card palette (mirrors InsightsPanel's "Unlock the full picture") ──
	private static final Color CARD_GOLD    = new Color(0xC4A052);
	private static final Color CARD_GOLD_BG = new Color(0x2A2418);
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
		{"dailyVolume",     "Daily volume"},
		{"hourlyVolume",    "Hourly volume"},
		{"buyPrice",        "Buy price (low → high)"},
		{"buyPriceDesc",    "Buy price (high → low)"},
		{"sellPrice",       "Sell price (low → high)"},
		{"sellPriceDesc",   "Sell price (high → low)"},
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
	/** Flips category — index into {@link #PRESETS}. 0 = "All Flips" (default). */
	private int flipsCategoryIdx   = 0;
	/** Min hourly volume floor — index into {@link #FLIPS_MIN_VOL_LABELS}. Default
	 *  index 0 = "Any volume" so the headline 07Flip Score view shows high-value
	 *  low-volume items (Amulet of rancour, Tumeken's shadow, etc.) out of the
	 *  box. Users can opt in to 1K+/hr when ROI sorting brings penny-flips to
	 *  the top (Bird snare, Bronze med helm, Fire rune). */
	private int flipsMinHourlyVolIdx = 0;
	/** Min buy-price floor — index into {@link #FLIPS_MIN_BUY_PRICE_LABELS}. Default
	 *  index 2 = 100gp, same anti-trash reasoning. */
	private int flipsMinBuyPriceIdx  = 2;
	/** Tax-free toggle — shows ONLY items priced under 50gp (no GE tax). */
	private boolean flipsTaxFreeOnly = false;

	private static final int[]    FLIPS_MIN_HOURLY_VOL    = {0, 100, 500, 1_000, 5_000, 10_000};
	private static final String[] FLIPS_MIN_VOL_LABELS    = {"Any volume", "100+/hr", "500+/hr", "1K+/hr", "5K+/hr", "10K+/hr"};
	private static final long[]   FLIPS_MIN_BUY_PRICE     = {0, 10, 100, 1_000, 10_000, 100_000};
	private static final String[] FLIPS_MIN_BUY_PRICE_LABELS = {"Any price", "10+", "100+", "1K+", "10K+", "100K+"};
	private int dumpsMinProfitIdx  = 0;
	private int dumpsPriceRangeIdx = 0;

	// -------------------------------------------------------------------------
	// Server-side sort keys for spikes / dumps / dips
	// -------------------------------------------------------------------------
	private String spikesSortKey = "recent";
	// v3 default: show "what should I act on right now" — the website ships
	// max_profit + confirmed_bot=true + active dumps as its primary view.
	private String dumpsSortKey  = "max_profit";
	private String dipsSortKey   = "recent";
	/** Active dip window — "1d" | "7d" | "30d". Drives the server's
	 *  activity_window param and the per-row "↓ X% in Yd" label. */
	private String dipsActivityWindow = "1d";
	private boolean dumpsUseBotEndpoint = false;

	// ── v3 dumps filters (server-side gates beyond minProfit / price range) ─
	/** Floor on dump_score (0–100). 0 = no gate. */
	private int     dumpsMinScore   = 0;
	/** When true, only dump_status in {"dumping","due_soon"} is shown. */
	private boolean dumpsActiveOnly = true;
	/** v5 tier filter: "all" | "confirmed" | "likely". "all" is the default. */
	private String  dumpsTier       = "all";
	/** Per-tier totals from the most recent /dumps response. Drives the
	 *  count chips on the segmented [All N] [Confirmed N] [Likely N] control. */
	private int     dumpsConfirmedTotal = 0;
	private int     dumpsLikelyTotal    = 0;

	// -------------------------------------------------------------------------
	// Auth state
	// -------------------------------------------------------------------------
	private boolean isSignedIn = false;
	private boolean isPremium  = false;

	/** Public accessors so the GE overlay and row panels can gate premium features. */
	public boolean isSignedIn() { return isSignedIn; }
	public boolean isPremium()  { return isPremium;  }
	private boolean authChecked = false;

	// -------------------------------------------------------------------------
	// Panel visibility (RuneLite Activatable) — gate optimiser polling on show/hide.
	// NavigationButton has no onSelect/onDeselect; these Activatable hooks are the
	// canonical signal the client fires when this panel is shown/hidden.
	// -------------------------------------------------------------------------

	@Override
	public void onActivate()
	{
		// Our sidebar panel became the visible one — resume the optimiser session
		// polling we pause while hidden.
		if (plugin != null)
		{
			plugin.onPanelShown();
		}
	}

	@Override
	public void onDeactivate()
	{
		// Panel hidden/collapsed — stop optimiser polling so we don't keep hitting
		// the server when the user can't see the panel.
		if (plugin != null)
		{
			plugin.onPanelHidden();
		}
	}

	/**
	 * True when the Plan view is the one currently on screen — either as the
	 * top-row Plan tab, or as the Plan sub-tab inside the Other pane. Lets the
	 * plugin resume the fast session poll when the panel re-opens directly onto
	 * Plan (the tab-change listener won't fire in that case).
	 */
	public boolean isPlanTabActive()
	{
		String top = currentTabName();
		if ("Plan".equals(top)) return true;
		return "Other".equals(top) && "Plan".equals(lastOtherSubTab);
	}

	// -------------------------------------------------------------------------
	// Stored data
	// -------------------------------------------------------------------------
	private List<FlipItem>    allFlips   = new ArrayList<>();
	private List<SpikeItem>   allSpikes  = new ArrayList<>();
	private List<DumpItem>    allDumps   = new ArrayList<>();
	private List<BarrowsSet>  allBarrows = new ArrayList<>();
	private List<MoonSet>     allMoon    = new ArrayList<>();
	private List<DecantItem>  allDecants = new ArrayList<>();
	private List<com.o7flip.model.DipItem> allDips = new ArrayList<>();
	private List<com.o7flip.model.HighAlchItem> allHighAlch = new ArrayList<>();
	private List<com.o7flip.model.TeleTablet>   allTablets  = new ArrayList<>();
	private List<FlipItem>    allFavourites = new ArrayList<>();
	private com.o7flip.model.ScreenerPreset.Bundle screenersBundle = new com.o7flip.model.ScreenerPreset.Bundle();
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
	private int dipsPage    = 0;  private int dipsTotal   = 0;
	private int barrowsPage = 0;
	private int moonPage    = 0;
	private int decantPage  = 0;

	// -------------------------------------------------------------------------
	// List panels
	// -------------------------------------------------------------------------
	private JPanel flipsListPanel;
	private JPanel spikesListPanel;
	private JPanel dumpsListPanel;
	private JPanel dipsListPanel;
	private JPanel highAlchListPanel;
	private JPanel tabletsListPanel;
	private JPanel favouritesListPanel;
	private JPanel screenersListPanel;
	private JPanel newsListPanel;
	private JPanel newsTabCard;        // CardLayout: "list" / "detail"
	private JPanel newsDetailPanel;
	private com.o7flip.model.NewsItem activeNewsItem;
	private java.util.List<com.o7flip.model.NewsItem> newsItems = new ArrayList<>();
	private JPanel watchListPanel;
	private java.util.List<com.o7flip.model.PriceAlert> priceAlerts = new ArrayList<>();
	// Alerts tab sub-nav: card switcher + the two toggle pills (Alerts / Watch).
	private JPanel alertsTabCard;
	private JButton[] alertsSubNavBtns;
	private JLabel highAlchCostLabel;
	private JPanel barrowsListPanel;
	private JPanel barrowsDetailPanel;
	private JPanel barrowsTabCard;
	private JLabel barrowsDetailTitle;
	// Screener master-detail state. screenersTabCard is the CardLayout shell;
	// screenersListPanel is the preset cards, screenersDetailPanel hosts one
	// preset's matches. activeScreenerPreset == null means we're on the list.
	private JPanel screenersTabCard;
	private JPanel screenersDetailPanel;
	private JLabel screenersDetailTitle;
	// Optimizer ("Plan") sub-tab state — ephemeral, lives only while the
	// panel is alive. Inputs are kept in fields so the user's settings
	// survive sub-tab switches within the session.
	private JPanel optimizerListPanel;       // results column (cards + summary)
	private com.o7flip.model.OptimizeResult lastOptimize;
	private int     optSlots         = 8;
	private String  optRisk          = "medium";
	private int     optMaxFillHours  = 4;
	private Boolean optMembers       = null;  // null = both, true = members, false = F2P
	/** Optional per-slot wealth floor as a whole-percent 0..10. 0 = auto/omit
	 *  (server default). Threaded into the optimize request as min_profit_pct. */
	private int     optMinProfitPct  = 0;
	/** True while the completed-positions history view is showing instead of
	 *  the live allocation list (Task D). */
	private boolean optShowingHistory;
	private boolean optInFlight;
	/** True after a successful build — collapses the input form to a compact
	 *  summary pill so the allocations get the panel's full vertical space.
	 *  Reset by the Reconfigure button. */
	private boolean optFormCollapsed;
	private JPanel  optCollapsedPanel;    // the "Active plan · Reconfigure" header strip
	private JPanel  optInputsHost;        // hosts the strip while a plan is live, empty otherwise
	private com.o7flip.model.ScreenerPreset activeScreenerPreset;
	// Inner Other-tab selection — preserved across rebuildTabs so the user
	// doesn't get snapped back to the first sub-tab whenever data refreshes
	// or an auth-status poll triggers a rebuild. Null until the user
	// actually clicks something inside Other.
	private String lastOtherSubTab;
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
	private JButton[] dumpsTierBtns;       // [All, Confirmed, Likely]
	private JPanel    dumpsTierBar;        // container for the segmented control
	private JButton[] dipsSortBtns;
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
	private JLabel  dipsPageLabel;    private JButton dipsPrev,     dipsNext;
	private JLabel  highAlchPageLabel; private JButton highAlchPrev, highAlchNext;
	private int     dipsSortIdx = 0;
	private int     highAlchSortIdx = 0;
	private int     highAlchPage    = 0;
	private int     highAlchTotal   = 0;
	private String  highAlchSortKey = "profit";
	private int     tabletsSortIdx = 0;
	private int     tabletsSpellbookIdx = 0;        // 0=All, 1=Standard, 2=Lunar, 3=Ancient, 4=Arceuus
	private boolean tabletsProfitableOnly = true;
	private JButton[] tabletsSortBtns;
	private JButton[] highAlchSortBtns;
	private JButton   highAlchFireStaffBtn;
	private JButton   highAlchBryophytaBtn;
	private boolean highAlchFireStaff;
	private boolean highAlchBryophyta;
	private JLabel  barrowsPageLabel; private JButton barrowsPrev,  barrowsNext;
	private JLabel  moonPageLabel;    private JButton moonPrev,     moonNext;
	private JLabel  decantPageLabel;  private JButton decantPrev,   decantNext;

	// -------------------------------------------------------------------------
	// Other UI
	// -------------------------------------------------------------------------
	// Filter panel widgets — created lazily inside buildFlipsTab and stashed
	// here so chip-removals can keep them in sync with the panel state.
	private JComboBox<String> flipsCapitalCombo;
	private JComboBox<String> flipsSortCombo;
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
		// Only rebuild the tab structure when the auth result actually
		// changed. Without this guard, every 15-minute auth re-check yanks
		// the user out of whichever tab they're sitting on — including the
		// Plan tab mid-poll, which is the reported regression.
		boolean wasChecked = this.authChecked;
		boolean changed = (signedIn != this.isSignedIn) || (premium != this.isPremium);
		this.isSignedIn  = signedIn;
		this.isPremium   = premium;
		this.authChecked = true;
		updateAuthBanner();
		// First-time check (wasChecked false) ALWAYS rebuilds because the
		// initial panel was built before auth status was known — its tab
		// visibility may be wrong until we hear back. Subsequent checks only
		// rebuild on actual state change.
		if (changed || !wasChecked)
		{
			rebuildTabs();
		}
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
			// Same visual language as the Insights tab's "Unlock the full
			// picture" box: gold-bordered card, one short wrapped pitch, one
			// CTA. Replaces the old bullet list, which truncated at panel
			// width and read as spam.
			authBanner.setBackground(ColorScheme.DARK_GRAY_COLOR);
			authBanner.setBorder(new EmptyBorder(6, 8, 6, 8));
			authBanner.add(buildGoldCard(
				"Unlock everything with Premium",
				"Your free account is connected. Upgrade to get 07Flip's "
					+ "<b>recommended buy/sell prices</b> and access to "
					+ "<b>every section</b> of the panel.",
				"Get Premium", SUBSCRIBE_URL), BorderLayout.CENTER);
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

		// State A — no API key configured. Make the missing-key step impossible
		// to miss: the same gold card language as the premium upsell, pointing
		// straight at the account page where the key lives. (Replaces the old
		// collapsible 8-step guide, which hid the single action that matters.)
		authBanner.setBackground(ColorScheme.DARK_GRAY_COLOR);
		authBanner.setBorder(new EmptyBorder(6, 8, 6, 8));
		authBanner.add(buildGoldCard(
			"Link your API key",
			"Get your key at <b>07flip.com/account</b>, then paste it into the "
				+ "07Flip plugin settings (wrench icon) to connect your account.",
			"Get my API key", ACCOUNT_URL), BorderLayout.CENTER);
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

	/**
	 * Compact gold upsell card — the same visual language as the Insights
	 * tab's "Unlock the full picture" box: gold-bordered dark card, bold gold
	 * headline, one short html-wrapped pitch (wraps instead of truncating at
	 * panel width) and a single gold CTA button.
	 */
	private JPanel buildGoldCard(String headline, String pitchHtml, String buttonText, String url)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD_GOLD_BG);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(CARD_GOLD, 1),
			new EmptyBorder(10, 12, 10, 12)));

		JLabel head = new JLabel(headline);
		head.setFont(Fonts.BOLD);
		head.setForeground(CARD_GOLD);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(head);
		card.add(Box.createVerticalStrut(4));

		JLabel pitch = new JLabel("<html><div style='width:210px'>" + pitchHtml + "</div></html>");
		pitch.setFont(Fonts.SM);
		pitch.setForeground(Color.WHITE);
		pitch.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(pitch);
		card.add(Box.createVerticalStrut(8));

		JButton btn = new JButton(buttonText);
		btn.setFont(Fonts.SM_BOLD);
		btn.setForeground(Color.BLACK);
		btn.setBackground(CARD_GOLD);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(new EmptyBorder(6, 14, 6, 14));
		btn.setAlignmentX(Component.LEFT_ALIGNMENT);
		btn.addActionListener(e -> openUrl(url));
		card.add(btn);
		return card;
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
		// Category dropdown in the Filter panel drives the preset. F2P toggle
		// overrides to the server's "f2p" preset since that's stricter than
		// any other category that might also include F2P items.
		if (flipsF2pOnly) return "f2p";
		int idx = Math.max(0, Math.min(flipsCategoryIdx, PRESETS.length - 1));
		return PRESETS[idx][0];
	}

	public String getFlipsSortKey()
	{
		// Selected from the Sort dropdown in the Flips filter panel. Default
		// (index 0) is flip07Score so the headline ranking matches the
		// server's primary signal. Pseudo-keys like "buyPriceDesc" are
		// expanded into sort + order by the API client.
		int idx = Math.max(0, Math.min(flipsSortIdx, FLIPS_SORTS.length - 1));
		return FLIPS_SORTS[idx][0];
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
		// Tax-free overrides any other floor — those items are <50gp.
		if (flipsTaxFreeOnly) return 0;
		long capitalFloor = flipsCapitalIdx > 0 ? CAPITAL_RANGES[flipsCapitalIdx][0] : 0;
		long qualityFloor = FLIPS_MIN_BUY_PRICE[Math.max(0, Math.min(flipsMinBuyPriceIdx, FLIPS_MIN_BUY_PRICE.length - 1))];
		return Math.max(capitalFloor, qualityFloor);
	}

	public long getFlipsPriceMax()
	{
		// Tax-free flips are items priced ≤ 49gp (GE tax kicks in at 50gp).
		if (flipsTaxFreeOnly) return 49L;
		return flipsCapitalIdx > 0 ? CAPITAL_RANGES[flipsCapitalIdx][1] : Long.MAX_VALUE;
	}

	/** Volume floor read by {@link #fFlips} for client-side filtering until
	 *  the server gains a minHourlyVolume query param. */
	private int getFlipsMinHourlyVolume()
	{
		return FLIPS_MIN_HOURLY_VOL[Math.max(0, Math.min(flipsMinHourlyVolIdx, FLIPS_MIN_HOURLY_VOL.length - 1))];
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

	public int     getDumpsMinScore()   { return dumpsMinScore; }
	public boolean getDumpsActiveOnly() { return dumpsActiveOnly; }
	public String  getDumpsTier()       { return dumpsTier; }

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

	public void updateDumps(com.o7flip.model.DumpItem.Response resp, int page)
	{
		allDumps = resp.items;
		dumpsTotal = resp.total;
		dumpsPage = page;
		dumpsConfirmedTotal = resp.confirmedCount;
		dumpsLikelyTotal    = resp.likelyCount;
		repaintDumpsTierBar();
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

	public void updateDips(List<com.o7flip.model.DipItem> items, int total, int page)
	{
		allDips = items;
		dipsTotal = total;
		dipsPage = page;
		renderDips(filtered());
	}

	public String getDipsSortKey()
	{
		return dipsSortKey;
	}

	public String getDipsActivityWindow()
	{
		return dipsActivityWindow == null ? "1d" : dipsActivityWindow;
	}

	public int getDipsPage()
	{
		return dipsPage;
	}

	public void updateHighAlch(com.o7flip.model.HighAlchItem.Response resp, int page)
	{
		allHighAlch    = resp.items;
		highAlchTotal  = resp.total;
		highAlchPage   = page;
		if (highAlchCostLabel != null)
		{
			highAlchCostLabel.setText("Cost / cast: " + FlipItemPanel.formatGp(resp.alchCost) + " gp"
				+ (resp.fireStaff ? "  · Fire staff" : "")
				+ (resp.bryophytaStaff ? "  · Bryophyta" : ""));
		}
		renderHighAlch(filtered());
	}

	public String getHighAlchSortKey()
	{
		return highAlchSortKey;
	}

	public int getHighAlchPage()
	{
		return highAlchPage;
	}

	public boolean getHighAlchFireStaff()
	{
		return highAlchFireStaff;
	}

	public boolean getHighAlchBryophyta()
	{
		return highAlchBryophyta;
	}

	public void updateTeleTablets(List<com.o7flip.model.TeleTablet> items)
	{
		allTablets = items;
		renderTeleTablets(filtered());
	}

	public String getTabletsSortKey()
	{
		// Two-option sort: Volume · Profit. Volume is the default (index 0) —
		// high-volume tablets sell fastest, so it's what most flippers want
		// to see first. Server may not yet understand "dailyVolume"; we
		// sort client-side too so the UI is always deterministic.
		final String[] keys = {"dailyVolume", "profit"};
		return keys[Math.max(0, Math.min(tabletsSortIdx, keys.length - 1))];
	}

	public String getTabletsSpellbook()
	{
		final String[] books = {"", "Standard", "Lunar", "Ancient", "Arceuus"};
		return books[Math.max(0, Math.min(tabletsSpellbookIdx, books.length - 1))];
	}

	public boolean getTabletsProfitableOnly()
	{
		return tabletsProfitableOnly;
	}

	public void updateFavourites(List<FlipItem> items)
	{
		allFavourites = items;
		renderFavourites(filtered());
		// Insights might be showing one of these items — refresh its star.
		if (insightsPanel != null)
		{
			insightsPanel.refreshFavouriteState();
		}
	}

	/**
	 * Called by the plugin after an optimistic favourite toggle. Repaints
	 * any visible star icon so the user sees the new state immediately.
	 */
	public void onFavouriteToggled(int itemId)
	{
		if (insightsPanel != null)
		{
			insightsPanel.refreshFavouriteState();
		}
	}

	public void updateScreeners(com.o7flip.model.ScreenerPreset.Bundle bundle)
	{
		screenersBundle = bundle == null ? new com.o7flip.model.ScreenerPreset.Bundle() : bundle;
		renderScreeners();
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
			renderDips("");
			renderHighAlch("");
			renderTeleTablets("");
			renderFavourites("");
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

	/**
	 * Returns the active capital ceiling in gp, or 0 when the user has the
	 * Capital input set to "Off". Callers use this to filter their row list
	 * to items the user can actually afford. Zero is "no filter".
	 */
	private long capitalCeiling()
	{
		return plugin != null ? plugin.capitalFilterCeiling() : 0L;
	}

	/** Convenience: true when {@code price} fits within the active capital. */
	private boolean affordable(long price)
	{
		long ceiling = capitalCeiling();
		return ceiling <= 0 || price <= ceiling;
	}

	private List<FlipItem>   fFlips(String q)
	{
		final int minVol = getFlipsMinHourlyVolume();
		return allFlips.stream()
			.filter(i -> notBlocked(i.itemId))
			// Client-side volume floor — passes through any row that doesn't
			// carry hourlyVolume yet (server still adding to /flips). Once
			// the field lands, the filter activates without further changes.
			.filter(i -> minVol <= 0 || i.hourlyVolume == null || i.hourlyVolume >= minVol)
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
	}

	private List<SpikeItem>  fSpikes(String q)
	{
		return allSpikes.stream()
			.filter(i -> notBlocked(i.itemId))
			.filter(i -> affordable(i.buyPrice))
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
	}

	private List<DumpItem>   fDumps(String q)
	{
		return allDumps.stream()
			.filter(i -> notBlocked(i.itemId))
			.filter(i -> affordable(i.buyPrice))
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
	}

	private List<com.o7flip.model.DipItem> fDips(String q)
	{
		return allDips.stream()
			.filter(i -> notBlocked(i.itemId))
			.filter(i -> affordable(i.buyPrice))
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
	}

	private List<com.o7flip.model.HighAlchItem> fHighAlch(String q)
	{
		return allHighAlch.stream()
			.filter(i -> notBlocked(i.itemId))
			.filter(i -> affordable(i.buyPrice))
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
	}

	private List<com.o7flip.model.TeleTablet> fTablets(String q)
	{
		return allTablets.stream()
			// Tablets gate on material cost, not buy price — the user pays for
			// the runes + soft clay upfront and the GE proceeds come later.
			.filter(t -> affordable(t.materialCost))
			.filter(t -> q.isEmpty() || matches(t.name, q))
			.collect(Collectors.toList());
	}

	private List<FlipItem> fFavourites(String q)
	{
		// Deliberately NOT subject to the capital filter: favourites are a
		// watchlist the user starred on purpose, and hiding starred items
		// because they sit above the current cash stack reads as data loss
		// (the panel showed "No favourites yet" to an account with favourites
		// and got diagnosed as a server bug). Search still applies.
		return allFavourites.stream()
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

	/**
	 * Identity wrapper — kept so callers don't break, but client-side sorting
	 * was retired when the server gained max_profit / recovery_pct /
	 * volume_consistency / dump_pct sorts. The server already applies the
	 * user's selected sort before responding; re-sorting client-side would
	 * just override it with stale logic (the legacy indices don't match the
	 * new sort options anyway).
	 */
	private List<DumpItem> sortDumps(List<DumpItem> items)
	{
		return items;
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

	/**
	 * Client-side sort for the Tablets list. The server may or may not
	 * understand the {@code dailyVolume} sort key (it's new), so we sort
	 * locally too — the dataset is small enough that this is essentially
	 * free, and the UI stays deterministic regardless of server support.
	 *
	 * Tablets without a known daily volume sort to the bottom of the
	 * Volume order.
	 */
	private List<com.o7flip.model.TeleTablet> sortTablets(List<com.o7flip.model.TeleTablet> items)
	{
		if (tabletsSortIdx == 1) // Profit
		{
			return items.stream()
				.sorted(Comparator.comparingLong((com.o7flip.model.TeleTablet x) -> x.profit).reversed())
				.collect(Collectors.toList());
		}
		// Volume (default, index 0) — desc by daily GE volume. Tablets
		// without a known daily volume sort to the bottom.
		return items.stream()
			.sorted((a, b) ->
			{
				int va = a.dailyVolume != null ? a.dailyVolume : -1;
				int vb = b.dailyVolume != null ? b.dailyVolume : -1;
				return Integer.compare(vb, va);
			})
			.collect(Collectors.toList());
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
		if (dumpsListPanel == null)
		{
			return;
		}
		hiliteFilter(dumpsSortBtns, dumpsSortIdx);

		// Bypass fillListPaged — we want to group rows by dump_status with
		// section headers, then apply the server's sort within each bucket.
		// Pagination still drives page label + prev/next via the server's
		// total count, so the user can paginate through the full list.
		List<DumpItem> items = sortDumps(fDumps(q));
		dumpsListPanel.removeAll();

		int total = Math.max(dumpsTotal, items.size());
		int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
		dumpsPageLabel.setText(isSignedIn && total > 0 ? (dumpsPage + 1) + " / " + pages : "");
		dumpsPrev.setEnabled(isSignedIn && dumpsPage > 0);
		dumpsNext.setEnabled(isSignedIn && dumpsPage < pages - 1);

		if (items.isEmpty())
		{
			dumpsListPanel.add(emptyLabel("No confirmed dumps right now",
				"Bot activity is bursty — check back in an hour."));
			dumpsListPanel.revalidate();
			dumpsListPanel.repaint();
			return;
		}

		// Split into four buckets — three live + stale at the bottom. Stale
		// items don't get a status-coloured header; they go under a separate
		// "Stale patterns" section so users learn to trust the live groups.
		List<DumpItem> dumping  = new ArrayList<>();
		List<DumpItem> dueSoon  = new ArrayList<>();
		List<DumpItem> pattern  = new ArrayList<>();
		List<DumpItem> stale    = new ArrayList<>();
		for (DumpItem it : items)
		{
			if (Boolean.TRUE.equals(it.patternStale))
			{
				stale.add(it);
				continue;
			}
			String s = it.dumpStatus == null ? "" : it.dumpStatus;
			switch (s)
			{
				case "dumping":  dumping.add(it);  break;
				case "due_soon": dueSoon.add(it);  break;
				default:         pattern.add(it);  break;
			}
		}

		int idx = 0;
		idx = appendGroup("Dumping now",    new Color(0xFF5555), dumping, idx);
		idx = appendGroup("Due soon",       new Color(0xFF981F), dueSoon, idx);
		idx = appendGroup("Pattern",        new Color(0xAAAAAA), pattern, idx);
		idx = appendGroup("Stale patterns", new Color(0x666666), stale,   idx);

		if (!isSignedIn && total > FREE_ROWS)
		{
			dumpsListPanel.add(signInPrompt(total - FREE_ROWS));
		}

		dumpsListPanel.revalidate();
		dumpsListPanel.repaint();
	}

	/**
	 * Renders one urgency bucket: a coloured header showing "{label} (N)"
	 * followed by the bucket's rows. Returns the running row-index so the
	 * caller can keep the odd/even striping consistent across buckets.
	 */
	private int appendGroup(String label, Color headerFg, List<DumpItem> rows, int startIdx)
	{
		if (rows.isEmpty())
		{
			return startIdx;
		}
		JLabel header = new JLabel(label + "  (" + rows.size() + ")");
		header.setFont(Fonts.BOLD);
		header.setForeground(headerFg);
		header.setBorder(new EmptyBorder(10, 12, 4, 12));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		dumpsListPanel.add(header);

		int idx = startIdx;
		// Cap visible rows on free tier (matches fillListPaged's PAGE_SIZE).
		int ps = isSignedIn ? PAGE_SIZE : FREE_ROWS;
		for (DumpItem it : rows)
		{
			if (idx >= ps) break;
			dumpsListPanel.add(new DumpItemPanel(it, itemManager, idx % 2 != 0, plugin));
			dumpsListPanel.add(sep());
			idx++;
		}
		return idx;
	}

	private void renderDips(String q)
	{
		if (dipsListPanel == null)
		{
			return;
		}
		final String window = getDipsActivityWindow();
		fillListPaged(dipsListPanel, fDips(q), dipsPage, dipsTotal,
			dipsPageLabel, dipsPrev, dipsNext,
			(item, odd) -> new DipItemPanel(item, itemManager, odd, plugin, window),
			"No dip signals", "Items below the " + window + " average or near ATL will appear here");
		hiliteFilter(dipsSortBtns, dipsSortIdx);
	}

	private void renderHighAlch(String q)
	{
		if (highAlchListPanel == null)
		{
			return;
		}
		fillListPaged(highAlchListPanel, fHighAlch(q), highAlchPage, highAlchTotal,
			highAlchPageLabel, highAlchPrev, highAlchNext,
			(item, odd) -> new HighAlchPanel(item, itemManager, odd, plugin),
			"No alch-profitable items", "Try toggling Fire staff or Bryophyta");
		hiliteFilter(highAlchSortBtns, highAlchSortIdx);
	}

	private void renderTeleTablets(String q)
	{
		if (tabletsListPanel == null)
		{
			return;
		}
		// Tablets endpoint isn't paginated and the dataset is tiny — render in
		// a single client-side pass. Sort is applied here (not server-side)
		// so "Volume" works even before the server supports sort=dailyVolume.
		tabletsListPanel.removeAll();
		List<com.o7flip.model.TeleTablet> shown = sortTablets(fTablets(q));
		if (shown.isEmpty())
		{
			tabletsListPanel.add(emptyLabel("No tablets to craft", "Try a different spellbook or turn off 'Profitable only'"));
		}
		else
		{
			for (int i = 0; i < shown.size(); i++)
			{
				tabletsListPanel.add(new TeleTabletPanel(shown.get(i), itemManager, i % 2 != 0, plugin));
				tabletsListPanel.add(sep());
			}
		}
		tabletsListPanel.revalidate();
		tabletsListPanel.repaint();
		hiliteFilter(tabletsSortBtns, tabletsSortIdx);
	}

	private void renderFavourites(String q)
	{
		if (favouritesListPanel == null)
		{
			return;
		}
		favouritesListPanel.removeAll();
		// Favourites only need an API key — premium isn't required. Earlier
		// the gate used isSignedIn which conflates apiKey-present with
		// premium-status, so non-premium users with a valid key were getting
		// a "Sign in required" placeholder even though the server would have
		// happily returned their favourites.
		boolean hasKey = config != null && config.apiKey() != null && !config.apiKey().trim().isEmpty();
		if (!hasKey)
		{
			favouritesListPanel.add(emptyLabel("API key required",
				"Paste your 07flip.com API key into the plugin config to use favourites."));
		}
		else
		{
			List<FlipItem> shown = fFavourites(q);
			if (shown.isEmpty() && !allFavourites.isEmpty())
			{
				// Favourites DID load but the search box filtered them all out
				// (favourites are exempt from the capital filter by design). A
				// blanket "No favourites yet" here once misdiagnosed a real
				// incident as a server bug — be precise about why it's empty.
				favouritesListPanel.add(emptyLabel(allFavourites.size() + " favourites hidden by search",
					"Clear the search box to see your full favourites list."));
			}
			else if (shown.isEmpty())
			{
				favouritesListPanel.add(emptyLabel("No favourites yet",
					"Tap the ★ on any item's Insights tab to add it here."));
			}
			else
			{
				for (int i = 0; i < shown.size(); i++)
				{
					favouritesListPanel.add(new FlipItemPanel(shown.get(i), itemManager, i % 2 != 0, plugin));
					favouritesListPanel.add(sep());
				}
			}
		}
		favouritesListPanel.revalidate();
		favouritesListPanel.repaint();
	}

	private void renderScreeners()
	{
		// Master-detail dispatcher: list of preset cards by default, drill-down
		// for one preset's matches when {@link #activeScreenerPreset} is set.
		if (screenersTabCard == null)
		{
			return;
		}
		if (activeScreenerPreset != null)
		{
			renderScreenersDetail(activeScreenerPreset);
		}
		else
		{
			renderScreenersList();
		}
	}

	/** Renders the list view — custom presets on top, system below. */
	private void renderScreenersList()
	{
		if (screenersListPanel == null)
		{
			return;
		}
		screenersListPanel.removeAll();

		List<com.o7flip.model.ScreenerPreset> system = screenersBundle.systemPresets;
		List<com.o7flip.model.ScreenerPreset> user   = screenersBundle.userPresets;

		// ── Custom screeners — shown FIRST per UX ask ────────────────────────
		screenersListPanel.add(buildScreenerSectionHeader("My screeners"));
		if (!screenersBundle.authenticated)
		{
			screenersListPanel.add(emptyLabel("Sign in required",
				"Add an API key in plugin config to see your custom screeners."));
		}
		else if (user == null || user.isEmpty())
		{
			// Empty-state CTA — links to the website's screener editor.
			screenersListPanel.add(ScreenerPresetCard.buildCreateOwnCard(
				() -> openUrl("https://07flip.com/alerts?tab=screener")));
		}
		else
		{
			for (int i = 0; i < user.size(); i++)
			{
				final com.o7flip.model.ScreenerPreset p = user.get(i);
				screenersListPanel.add(new ScreenerPresetCard(p, i % 2 != 0, this::onScreenerPresetClicked));
				screenersListPanel.add(sep());
			}
		}

		// ── System screeners — the 5 defaults ────────────────────────────────
		screenersListPanel.add(buildScreenerSectionHeader("System screeners"));
		if (system == null || system.isEmpty())
		{
			screenersListPanel.add(emptyLabel("Screeners loading…",
				"Indicators update hourly. Pull-to-refresh isn't needed."));
		}
		else
		{
			for (int i = 0; i < system.size(); i++)
			{
				final com.o7flip.model.ScreenerPreset p = system.get(i);
				screenersListPanel.add(new ScreenerPresetCard(p, i % 2 != 0, this::onScreenerPresetClicked));
				screenersListPanel.add(sep());
			}
		}

		screenersListPanel.revalidate();
		screenersListPanel.repaint();
		((CardLayout) screenersTabCard.getLayout()).show(screenersTabCard, "list");
	}

	/** Renders the detail view for a single preset's matches. */
	private void renderScreenersDetail(com.o7flip.model.ScreenerPreset preset)
	{
		if (screenersDetailPanel == null || screenersDetailTitle == null)
		{
			return;
		}
		screenersDetailTitle.setText(preset.name);
		screenersDetailPanel.removeAll();

		// Preset description strip at the top of the detail view.
		if (preset.description != null && !preset.description.isEmpty())
		{
			JLabel desc = new JLabel("<html><font color='#888888'>" + escapeHtml(preset.description) + "</font></html>");
			desc.setFont(Fonts.SM);
			desc.setBorder(new EmptyBorder(8, 12, 8, 12));
			desc.setAlignmentX(Component.LEFT_ALIGNMENT);
			screenersDetailPanel.add(desc);
			screenersDetailPanel.add(sep());
		}

		if (preset.premiumRequired)
		{
			JLabel locked = new JLabel("<html><b>Matches are premium-only.</b><br>"
				+ "<font color='#888888'>Upgrade on 07flip.com to see which items hit this preset.</font></html>");
			locked.setFont(Fonts.SM);
			locked.setBorder(new EmptyBorder(12, 12, 8, 12));
			locked.setAlignmentX(Component.LEFT_ALIGNMENT);
			screenersDetailPanel.add(locked);

			JButton upgrade = pillButton("Unlock with Premium");
			upgrade.setBackground(ORANGE);
			upgrade.setForeground(Color.BLACK);
			upgrade.setAlignmentX(Component.LEFT_ALIGNMENT);
			upgrade.addActionListener(e -> openUrl(preset.upgradeUrl != null ? preset.upgradeUrl : "https://07flip.com/premium"));
			JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
			wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrap.setBorder(new EmptyBorder(0, 12, 12, 12));
			wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
			wrap.add(upgrade);
			screenersDetailPanel.add(wrap);
		}
		else if (preset.matches == null || preset.matches.isEmpty())
		{
			screenersDetailPanel.add(emptyLabel("No items match",
				"This preset has no current hits. Check back later."));
		}
		else
		{
			// Enrich matches with market data from cached Flips before render
			// so the rows show price + score + margin when available.
			enrichScreenerMatches(preset.matches);
			for (int i = 0; i < preset.matches.size(); i++)
			{
				screenersDetailPanel.add(new ScreenerMatchPanel(
					preset.matches.get(i), itemManager, i % 2 != 0, plugin));
				screenersDetailPanel.add(sep());
			}
		}

		screenersDetailPanel.revalidate();
		screenersDetailPanel.repaint();
		((CardLayout) screenersTabCard.getLayout()).show(screenersTabCard, "detail");
	}

	/**
	 * Fills in {@link com.o7flip.model.ScreenerMatch#buyPrice}, sellPrice,
	 * profit, roiPct and flip07Score from the plugin's cached Flips list
	 * when the server didn't ship them. Best-effort — items the user
	 * doesn't have cached data for stay at null and render as "—".
	 */
	private void enrichScreenerMatches(List<com.o7flip.model.ScreenerMatch> matches)
	{
		if (plugin == null || matches == null || matches.isEmpty()) return;
		java.util.Map<Integer, FlipItem> byId = new java.util.HashMap<>();
		for (FlipItem f : plugin.lastFlips) byId.put(f.itemId, f);
		// Also fall back to our locally-rendered Flips list (in-memory in panel)
		// which may have items the plugin's lastFlips no longer caches.
		for (FlipItem f : allFlips) byId.putIfAbsent(f.itemId, f);
		for (FlipItem f : allFavourites) byId.putIfAbsent(f.itemId, f);
		for (com.o7flip.model.ScreenerMatch m : matches)
		{
			FlipItem f = byId.get(m.itemId);
			if (f == null) continue;
			if (m.buyPrice    == null) m.buyPrice    = f.buyPrice;
			if (m.sellPrice   == null) m.sellPrice   = f.sellPrice;
			if (m.profit      == null) m.profit      = f.profit;
			if (m.roiPct      == null) m.roiPct      = f.roiPct;
			if (m.flip07Score == null) m.flip07Score = f.flip07Score;
		}
	}

	private void onScreenerPresetClicked(com.o7flip.model.ScreenerPreset preset)
	{
		// Premium-locked rows route to the upgrade URL instead of detail.
		if (preset.premiumRequired)
		{
			openUrl(preset.upgradeUrl != null ? preset.upgradeUrl : "https://07flip.com/premium");
			return;
		}
		activeScreenerPreset = preset;
		renderScreenersDetail(preset);
	}

	private JComponent buildScreenerSectionHeader(String title)
	{
		JLabel lbl = new JLabel(title);
		lbl.setFont(Fonts.BOLD);
		lbl.setForeground(ORANGE);
		lbl.setBorder(new EmptyBorder(10, 10, 4, 10));
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		return lbl;
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
			// "Invested" components — both derived from the live offer
			// snapshot. Active BUY gp = remaining qty × listing price across
			// every BUYING-state offer. Held cost basis = cost basis of items
			// currently sitting in SELLING-state offers, proportional to qty
			// still waiting to fill. SOLD/CANCELLED states are excluded:
			// SOLD items are already gone, CANCELLED returns them to bank.
			long activeBuyGp = 0L;
			java.util.Map<Integer, Integer> qtyInActiveSells = new java.util.HashMap<>();
			if (plugin != null && plugin.activeOffers != null)
			{
				for (com.o7flip.model.ActiveOfferSnapshot s : plugin.activeOffers.values())
				{
					if (s == null) continue;
					int remaining = Math.max(0, s.totalQuantity - s.quantitySold);
					if (remaining <= 0) continue;
					if (s.state == net.runelite.api.GrandExchangeOfferState.BUYING)
					{
						activeBuyGp += (long) remaining * s.price;
					}
					else if (s.state == net.runelite.api.GrandExchangeOfferState.SELLING)
					{
						qtyInActiveSells.merge(s.itemId, remaining, Integer::sum);
					}
				}
			}
			long heldCostBasisInActiveSells = 0L;
			if (result != null && result.openPositions != null)
			{
				for (com.o7flip.util.ProfitCalculator.OpenPosition op : result.openPositions.values())
				{
					if (op == null || op.remainingQty <= 0) continue;
					Integer qtyInSell = qtyInActiveSells.get(op.itemId);
					if (qtyInSell == null || qtyInSell <= 0) continue;
					int coveredQty = Math.min(qtyInSell, op.remainingQty);
					long perUnitCost = op.remainingCostBasis / op.remainingQty;
					heldCostBasisInActiveSells += perUnitCost * coveredQty;
				}
			}
			myFlipsStatsPanel.update(
				result,
				plugin != null ? plugin.trackerStats : null,
				plugin != null ? plugin.bondLedger  : com.o7flip.util.BondLedger.EMPTY,
				myFlipsPeriod,
				plugin != null && plugin.isMembershipCostHidden(),
				activeBuyGp,
				heldCostBasisInActiveSells);
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

	/**
	 * Builds a standalone tab whose entire content is the premium-gate card.
	 * Used by {@link #buildTabs()} to replace premium-only features (Screener,
	 * Plan, Dumps, Dips, Moons, Barrows, Tablets, Decant) for non-premium users
	 * so no data is shown — only the upsell card. {@code rebuildTabs()} runs
	 * whenever premium status changes, so the real content returns on upgrade.
	 */
	private JPanel buildPremiumGateTab(String title, String sub)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		renderLocked(p, title, sub);
		return p;
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

		// Capital indicator chip — lives in the header's right cluster
		// alongside ● Live. Compact pen icon: orange when capital is set,
		// muted grey when not. Click toggles the slim inline editor below
		// (which only renders when toggled on, so the header doesn't burn a
		// whole row when capital isn't being edited).
		capitalHeaderChip = new JLabel("✎");
		capitalHeaderChip.setFont(capitalHeaderChip.getFont().deriveFont(15f));
		capitalHeaderChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		capitalHeaderChip.setBorder(new EmptyBorder(0, 4, 0, 4));
		updateCapitalHeaderChip();
		capitalHeaderChip.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				capitalExpanded = !capitalExpanded;
				// Expanding only opens the editor — it no longer force-enables
				// the filter. The explicit On/Off toggle inside the section is
				// the single source of truth for whether capital filtering runs.
				renderCapitalRow();
				if (capitalExpanded) focusCapitalFieldIfEditable();
			}
			@Override public void mouseEntered(MouseEvent e) { repaintCapitalChipHover(true); }
			@Override public void mouseExited(MouseEvent e)  { repaintCapitalChipHover(false); }
		});

		JPanel rightCluster = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
		rightCluster.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rightCluster.add(capitalHeaderChip);
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

		JPanel capitalRow = buildCapitalRow();

		JPanel topInner = new JPanel(new BorderLayout());
		topInner.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topInner.add(capitalRow, BorderLayout.NORTH);
		topInner.add(searchRow,  BorderLayout.SOUTH);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		top.add(header,    BorderLayout.NORTH);
		top.add(topInner,  BorderLayout.SOUTH);
		return top;
	}

	// =========================================================================
	// Capital input — drives the cross-tab affordability filter (cashStack /
	// affordable_qty params on /flips, client-side filter on other feeds).
	//
	// The row collapses to a compact chip by default so the input stays out
	// of the way; clicking the chip expands a full editor. A lock toggle
	// keeps a saved value safe from accidental edits, and completed GE
	// trades adjust the manual value automatically (see plugin's
	// adjustCapitalForTrade).
	// =========================================================================

	private JPanel     capitalContainer;   // the swappable shell — collapsed or expanded
	private JTextField capitalField;
	private JLabel     capitalReadout;
	private JLabel     capitalHeaderChip;  // small pen icon in the title bar
	private boolean    capitalExpanded;    // ephemeral — UI state, not persisted

	private JPanel buildCapitalRow()
	{
		capitalContainer = new JPanel(new BorderLayout());
		capitalContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		capitalContainer.setBorder(new EmptyBorder(0, 12, 6, 12));
		renderCapitalRow();
		return capitalContainer;
	}

	/**
	 * Renders either the collapsed chip or the expanded editor into
	 * {@link #capitalContainer}, depending on {@link #capitalExpanded}.
	 * Called whenever the user toggles, types, or the mode changes.
	 */
	private void renderCapitalRow()
	{
		if (capitalContainer == null) return;
		capitalContainer.removeAll();
		// Editor only renders when the header chip has been toggled on. When
		// collapsed, the container is invisible AND has zero preferred height,
		// so no empty row appears at the top of the plugin.
		if (capitalExpanded)
		{
			capitalContainer.add(buildCapitalExpanded(), BorderLayout.CENTER);
			capitalContainer.setVisible(true);
		}
		else
		{
			capitalContainer.setVisible(false);
		}
		capitalContainer.revalidate();
		capitalContainer.repaint();
		updateCapitalHeaderChip();
	}

	/**
	 * Reflects the current capital state into the small header chip. Orange
	 * pen when a value is set; muted grey when not. The number itself stays
	 * off the always-visible surface — the user opens the editor to see it.
	 */
	private void updateCapitalHeaderChip()
	{
		if (capitalHeaderChip == null) return;
		long value = displayedCapital();
		boolean active = value > 0;
		capitalHeaderChip.setForeground(active ? ORANGE : new Color(0x666666));
		capitalHeaderChip.setToolTipText(active
			? "Capital: " + formatCapital(value) + " · click to edit"
			: "Set capital — items filter to what's affordable");
	}

	/** Subtle hover styling on the header chip — slightly brighter on hover. */
	private void repaintCapitalChipHover(boolean hovering)
	{
		if (capitalHeaderChip == null) return;
		long value = displayedCapital();
		boolean active = value > 0;
		if (hovering)
		{
			capitalHeaderChip.setForeground(active ? Color.WHITE : new Color(0xAAAAAA));
		}
		else
		{
			capitalHeaderChip.setForeground(active ? ORANGE : new Color(0x666666));
		}
	}

	private JComponent buildCapitalExpanded()
	{
		JPanel outer = new JPanel();
		outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
		outer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel label = new JLabel("Capital");
		label.setFont(Fonts.SM);
		label.setForeground(new Color(0xAAAAAA));

		// Persistent On/Off switch for the capital filter. This is the single
		// source of truth for whether item lists are gated by capital — the
		// preference survives restarts (stored as capitalMode). When OFF the
		// filter is truly off and never silently re-enables.
		final boolean filterOn = currentCapitalMode() != O7FlipConfig.CapitalMode.OFF;
		JButton capitalToggle = pillButton(filterOn ? "On" : "Off");
		capitalToggle.setBackground(filterOn ? new Color(0x00C27A) : new Color(0x3A3A3A));
		capitalToggle.setForeground(filterOn ? Color.BLACK : new Color(0xCCCCCC));
		capitalToggle.setToolTipText(filterOn
			? "Capital filter ON — flips show items priced at or below your capital. Click to turn off."
			: "Capital filter OFF — every flip is shown. Click to filter by your capital.");
		capitalToggle.addActionListener(e ->
		{
			if (plugin != null)
			{
				// Commit any pending edit first so enabling the filter uses the
				// value currently in the field, not the last saved one.
				if (!filterOn)
				{
					commitManualCapital();
				}
				plugin.setCapitalFilterEnabled(!filterOn);
			}
			renderCapitalRow();
		});

		// Editable field — always typeable in this state. Pressing Enter or
		// blurring saves; the save button does the same, just as an explicit
		// affordance.
		capitalField = new JTextField(formatCapital(displayedCapital()));
		capitalField.setBackground(new Color(0x1E1E1E));
		capitalField.setForeground(Color.WHITE);
		capitalField.setCaretColor(Color.WHITE);
		capitalField.setFont(Fonts.SM);
		capitalField.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(1, 1, 1, 1, new Color(0x4A4A4A)),
			new EmptyBorder(4, 8, 4, 8)));
		capitalField.setEditable(true);
		capitalField.setEnabled(true);
		capitalField.addActionListener(e -> saveCapitalAndCollapse());
		capitalField.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				// Focus leaving the field commits the current text but keeps
				// the editor open. Explicit save (or Enter) collapses.
				commitManualCapital();
			}
		});

		// Save button — floppy disk. Click commits + collapses.
		JButton saveBtn = pillButton("💾");
		saveBtn.setFont(saveBtn.getFont().deriveFont(15f));
		saveBtn.setBackground(ORANGE);
		saveBtn.setForeground(Color.BLACK);
		saveBtn.setToolTipText("Save capital");
		saveBtn.addActionListener(e -> saveCapitalAndCollapse());

		capitalReadout = new JLabel("");
		capitalReadout.setFont(Fonts.SM);
		capitalReadout.setForeground(new Color(0x888888));
		capitalReadout.setBorder(new EmptyBorder(3, 2, 0, 0));
		capitalReadout.setAlignmentX(Component.LEFT_ALIGNMENT);
		updateCapitalReadout();

		// Top row: the "Capital" label (left) and the On/Off filter toggle
		// (right). The field + save sit on row 2 to give the input the full
		// width it deserves at panel sizes.
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.add(label, BorderLayout.WEST);
		top.add(capitalToggle, BorderLayout.EAST);
		top.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.setMaximumSize(new Dimension(Integer.MAX_VALUE, capitalToggle.getPreferredSize().height + 4));

		JPanel fieldRow = new JPanel(new BorderLayout(6, 0));
		fieldRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		fieldRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		fieldRow.add(capitalField, BorderLayout.CENTER);
		fieldRow.add(saveBtn,      BorderLayout.EAST);
		fieldRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		outer.add(top);
		outer.add(fieldRow);
		outer.add(capitalReadout);
		return outer;
	}

	/**
	 * Commits the field text + collapses the editor. Called by both the
	 * floppy-disk save button and the field's Enter key — the single
	 * "explicit save" path. Bad input silently reverts to the last saved
	 * value (matches the field's focus-lost behaviour).
	 */
	private void saveCapitalAndCollapse()
	{
		commitManualCapital();
		capitalExpanded = false;
		renderCapitalRow();
	}

	private O7FlipConfig.CapitalMode currentCapitalMode()
	{
		O7FlipConfig.CapitalMode mode = config != null ? config.capitalMode() : O7FlipConfig.CapitalMode.OFF;
		// Mirror the plugin's legacy migration so the UI label reflects what's
		// actually being applied (Auto when usePersonalisedFlips is on but
		// the new mode hasn't been touched).
		if (mode == O7FlipConfig.CapitalMode.OFF && config != null && config.usePersonalisedFlips())
		{
			return O7FlipConfig.CapitalMode.AUTO;
		}
		return mode;
	}

	/**
	 * Defers a focus + select-all to {@link SwingUtilities#invokeLater} so the
	 * field has time to be laid out by the EDT before we try to grab focus.
	 * No-op when the field isn't currently editable.
	 */
	private void focusCapitalFieldIfEditable()
	{
		if (capitalField == null || !capitalField.isEditable())
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			capitalField.requestFocusInWindow();
			capitalField.selectAll();
		});
	}

	private void commitManualCapital()
	{
		if (capitalField == null)
		{
			return;
		}
		long parsed = parseCapital(capitalField.getText());
		if (parsed < 0)
		{
			// Bad input — revert to last saved value rather than nag the user.
			capitalField.setText(formatCapital(displayedCapital()));
			return;
		}
		if (plugin != null)
		{
			// Saving any value implicitly puts us in MANUAL mode (the only
			// surfaced mode now). Legacy users on AUTO get migrated the
			// moment they save their first value.
			if (currentCapitalMode() != O7FlipConfig.CapitalMode.MANUAL)
			{
				plugin.persistCapitalMode(O7FlipConfig.CapitalMode.MANUAL);
			}
			plugin.persistCapitalManual(parsed);
		}
		capitalField.setText(formatCapital(parsed));
		updateCapitalReadout();
		if (plugin != null)
		{
			plugin.onCapitalChanged();
		}
	}

	/** Called by the plugin after a GE fill auto-adjusts the manual capital,
	 *  or when active-offer state changes (which shifts deployed/free). */
	public void onCapitalAutoAdjusted()
	{
		if (capitalField != null && currentCapitalMode() != O7FlipConfig.CapitalMode.OFF)
		{
			capitalField.setText(formatCapital(displayedCapital()));
		}
		updateCapitalReadout();
		// Also refresh collapsed view if that's what's showing.
		if (!capitalExpanded)
		{
			renderCapitalRow();
		}
		// A trade / offer change also moves what the user can afford — re-
		// apply the filter across every tab that's gated on capital.
		rerenderCapitalAffectedTabs();
	}

	/**
	 * Re-renders every tab whose row list is gated by capital. Cheap —
	 * uses the data already in memory, no refetch. Called when the user
	 * changes the Capital input or when a trade auto-adjusts it.
	 */
	public void rerenderCapitalAffectedTabs()
	{
		String q = filtered();
		renderDumps(q);
		renderSpikes(q);
		renderDips(q);
		renderHighAlch(q);
		renderTeleTablets(q);
		renderFavourites(q);
	}

	/** Pushes a fresh inventory-coin reading into the Auto-mode display. */
	public void onInventoryCoinsChanged()
	{
		if (currentCapitalMode() == O7FlipConfig.CapitalMode.AUTO && capitalField != null)
		{
			capitalField.setText(formatCapital(displayedCapital()));
			updateCapitalReadout();
		}
	}

	/**
	 * The "headline" capital figure displayed in the field — the user's total
	 * bankroll (free + deployed). The affordability filter uses {@code
	 * effectiveCapital()} which is the bucketed FREE figure.
	 */
	private long displayedCapital()
	{
		if (plugin != null && currentCapitalMode() != O7FlipConfig.CapitalMode.OFF)
		{
			return plugin.totalCapital();
		}
		return 0L;
	}

	private void updateCapitalReadout()
	{
		if (capitalReadout == null) return;
		O7FlipConfig.CapitalMode mode = currentCapitalMode();
		if (mode == O7FlipConfig.CapitalMode.OFF)
		{
			capitalReadout.setText("");
			return;
		}
		long deployed = plugin != null ? plugin.deployedCapital() : 0L;
		long total    = plugin != null ? plugin.totalCapital()    : 0L;

		// Always-on diagnostic: shows the exact GP value the abbreviated
		// input parsed to (e.g. "100M" → "100,000,000 gp"). Removes the
		// ambiguity the user reported around suffix parsing.
		StringBuilder html = new StringBuilder("<html>");
		if (total > 0)
		{
			html.append("<font color='#888888'>= ")
				.append(String.format("%,d", total)).append(" gp</font>");
		}
		if (deployed > 0)
		{
			long free = plugin != null ? plugin.freeCapital() : 0L;
			if (total > 0) html.append("  ");
			html.append("<font color='#00C27A'>Free ").append(formatCapital(free)).append("</font>")
				.append(" <font color='#888888'>· GE ").append(formatCapital(deployed)).append("</font>");
		}
		else if (mode == O7FlipConfig.CapitalMode.AUTO && total > 0)
		{
			html.append("  <font color='#888888'>(inventory)</font>");
		}
		html.append("</html>");
		capitalReadout.setText(html.toString());
	}

	/**
	 * Parses an input like {@code "50m"}, {@code "1.5B"} or {@code "250000"}
	 * into a gp amount. Returns -1 on unparseable input so callers can
	 * silently revert without a popup. Empty input is treated as 0.
	 */
	static long parseCapital(String raw)
	{
		if (raw == null) return -1;
		String s = raw.trim().toLowerCase().replace(",", "").replace(" ", "").replace("gp", "");
		if (s.isEmpty()) return 0L;
		long mult = 1L;
		char last = s.charAt(s.length() - 1);
		if (last == 'k')      { mult = 1_000L;             s = s.substring(0, s.length() - 1); }
		else if (last == 'm') { mult = 1_000_000L;         s = s.substring(0, s.length() - 1); }
		else if (last == 'b') { mult = 1_000_000_000L;     s = s.substring(0, s.length() - 1); }
		try
		{
			double n = Double.parseDouble(s);
			if (n < 0) return -1;
			return (long) (n * mult);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	private static String formatCapital(long gp)
	{
		if (gp <= 0) return "0";
		if (gp >= 1_000_000_000L) return trimZeros(String.format("%.2f", gp / 1_000_000_000.0)) + "B";
		if (gp >= 1_000_000L)     return trimZeros(String.format("%.2f", gp / 1_000_000.0))     + "M";
		if (gp >= 1_000L)         return trimZeros(String.format("%.1f", gp / 1_000.0))         + "K";
		return String.valueOf(gp);
	}

	private static String trimZeros(String s)
	{
		int dot = s.indexOf('.');
		if (dot < 0) return s;
		int end = s.length();
		while (end > dot && s.charAt(end - 1) == '0') end--;
		if (end > dot && s.charAt(end - 1) == '.') end--;
		return s.substring(0, end);
	}

	private static String escapeHtml(String s)
	{
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
			case "Item":      return config.showInsights();
			case "Trades":    return config.showMyFlips();
			// "Other" hosts anything from the customisable pool that the user
			// didn't promote to Row 1. Shown whenever at least one sub-feature
			// is both enabled AND not currently in the top row.
			case "Other":     return otherTabHasContent();
			// Movable pool — same visibility predicates whether they're sitting
			// on Row 1 (top-level) or inside Other (sub-tab).
			case "Dumps":
			case "Alerts":
			case "Moons":
			case "Barrows":
			case "Dips":
			case "Favs":
			case "Alch":
			case "Tablets":
			case "Screener":
			case "Decant":
			case "Plan":
			case "News":
				return subFeatureEnabled(name);
			default:          return false;
		}
	}

	/** Returns true when the Other tab has at least one sub-tab to host. */
	private boolean otherTabHasContent()
	{
		if (config == null) return false;
		for (String name : MOVABLE_POOL)
		{
			if (resolveTopRow().contains(name)) continue;
			if (subFeatureEnabled(name)) return true;
		}
		return false;
	}

	/**
	 * Pool of "movable" tabs that can sit either on Row 1 (top, customisable)
	 * or inside the Other tab on Row 2. Bottom-row tabs (Flips / My Trades /
	 * Item / Other) are NOT in this pool — they're fixed.
	 *
	 * Order here only matters as the default ordering inside the Customise
	 * dialog; the user's saved {@code topRowTabs} drives the actual layout.
	 */
	public static final List<String> MOVABLE_POOL = java.util.Arrays.asList(
		"Moons", "Barrows", "Dumps", "Alerts",
		"Dips", "Favs", "Alch", "Tablets", "Screener", "Decant", "Plan", "News"
	);

	/** Default top-row pick when {@code topRowTabs} is empty. */
	public static final List<String> DEFAULT_TOP_ROW = java.util.Arrays.asList(
		"Moons", "Barrows", "Dumps", "Alerts"
	);

	/** Fixed Row 2 — non-customisable, non-reorderable. */
	public static final List<String> FIXED_BOTTOM_ROW = java.util.Arrays.asList(
		"Flips", "Trades", "Item", "Other"
	);

	/** Default order = Row 1 + Row 2. Kept for the legacy {@code tabOrder}
	 *  config and the TabOrderDialog reset target. */
	public static final List<String> DEFAULT_TAB_ORDER;
	static
	{
		List<String> combined = new ArrayList<>(8);
		combined.addAll(DEFAULT_TOP_ROW);
		combined.addAll(FIXED_BOTTOM_ROW);
		DEFAULT_TAB_ORDER = java.util.Collections.unmodifiableList(combined);
	}

	/**
	 * Parses {@code topRowTabs} into the user's chosen Row 1 selection.
	 * Returns at most 4 names, all of which are members of {@link #MOVABLE_POOL}.
	 * Falls back to {@link #DEFAULT_TOP_ROW} when the config is empty or
	 * malformed.
	 */
	public List<String> resolveTopRow()
	{
		String raw = config != null ? config.topRowTabs() : "";
		if (raw == null || raw.trim().isEmpty())
		{
			return new ArrayList<>(DEFAULT_TOP_ROW);
		}
		List<String> out = new ArrayList<>(4);
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (String token : raw.split(","))
		{
			String name = token.trim();
			// Migration: the old "Screen" short label is now "Screener" — map
			// any saved config entry transparently so users don't lose a slot.
			if ("Screen".equals(name)) name = "Screener";
			if (MOVABLE_POOL.contains(name) && seen.add(name))
			{
				out.add(name);
				if (out.size() >= 4) break;
			}
		}
		return out.isEmpty() ? new ArrayList<>(DEFAULT_TOP_ROW) : out;
	}

	/** True when the user has pasted any non-empty API key into config. */
	private boolean hasApiKey()
	{
		return config != null && config.apiKey() != null && !config.apiKey().trim().isEmpty();
	}

	/** Returns true when the user's per-feature visibility toggle is on. */
	private boolean subFeatureEnabled(String name)
	{
		if (config == null) return true;
		switch (name)
		{
			case "Moons":   return config.showMoon()      && isSignedIn;
			case "Barrows": return config.showBarrows()   && isSignedIn;
			case "Dumps":   return config.showDumps();
			case "Alerts":  return config.showAlerts();
			case "Dips":    return config.showDips();
			case "Favs":    return config.showFavourites() && hasApiKey();
			case "Alch":    return config.showHighAlch();
			case "Tablets": return config.showTeleTablets();
			case "Screener":return config.showScreeners();
			case "Decant":  return config.showDecant();
			case "Plan":    return hasApiKey();   // optimizer is premium-only; signing in is the gate
			case "News":    return config.showNews();   // free, no auth required
			default:        return false;
		}
	}

	/**
	 * Resolves the panel's tab order. JTabbedPane with WRAP_TAB_LAYOUT and
	 * shouldRotateTabRuns=false places first-inserted tabs on the run
	 * adjacent to content (i.e. the BOTTOM of the strip for TOP placement).
	 * So we insert the fixed Row 2 (Flips/My Trades/Item/Other) FIRST so it
	 * lands on the bottom row, then the customisable Row 1 so it stacks on
	 * top — matching the design contract "customisable row sits up high".
	 */
	public List<String> resolveTabOrder()
	{
		List<String> out = new ArrayList<>(8);
		out.addAll(FIXED_BOTTOM_ROW);
		out.addAll(resolveTopRow());
		return out;
	}

	private JTabbedPane buildTabs()
	{
		// WRAP_TAB_LAYOUT spreads all tabs across multiple rows so every tab
		// is visible at once — avoids the awkward "> chevron + dropdown"
		// overflow that SCROLL_TAB_LAYOUT shows in narrow side panels.
		// 8 tabs at SM font fit comfortably across 2 rows of the side panel.
		JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.setForeground(Color.WHITE);
		tabs.setFont(Fonts.SM);
		// Pin the row order: Swing's default behaviour rotates runs so the
		// SELECTED tab's row sits adjacent to the content area (i.e. at the
		// bottom of the tab strip). That breaks the design contract of
		// "customisable row on top, fixed row below" — selecting Moons would
		// shove its row below Flips. The override below is a no-op
		// rotateTabRuns so the runs stay in insertion order.
		applyStaticOrderUI(tabs);

		// Always build all tab content to initialise list-panel fields,
		// then conditionally add each tab based on config + auth state.
		JPanel flipsContent    = buildFlipsTab();
		JPanel dumpsContent    = buildDumpsTab();
		JPanel spikesContent   = buildSpikesTab();
		JPanel insightsContent = buildInsightsTab();
		JPanel alertsContent   = buildGenericTab("Merch");
		JPanel moonContent     = buildMoonTab();
		JPanel barrowsContent  = buildGenericTab("Barrows");
		JPanel decantContent     = buildGenericTab("Decant");
		JPanel dipsContent       = buildDipsTab();
		JPanel highAlchContent   = buildHighAlchTab();
		JPanel tabletsContent    = buildTeleTabletsTab();
		JPanel favouritesContent = buildFavouritesTab();
		JPanel screenersContent  = buildScreenersTab();
		JPanel planContent       = buildPlanTab();
		JPanel newsContent       = buildNewsTab();

		// Premium gate: the recommended-price feeds and the optimiser are
		// premium features, so for non-premium users these tabs show an upsell
		// card instead of any data. Reassign the content vars BEFORE they're
		// slotted into Other (below) and the top-row map, so the gate shows
		// wherever the feature lives (top row OR inside Other). The real list
		// panels were still built above (initialising their fields); we simply
		// never display them. Flips, Trades, Favourites, Item, Alerts, Spikes
		// and High Alch stay accessible.
		if (!isPremium)
		{
			final String gate = "To view this feature you need to be a member and link your API key.";
			dumpsContent     = buildPremiumGateTab("Dumps",     gate);
			moonContent      = buildPremiumGateTab("Moons",     gate);
			barrowsContent   = buildPremiumGateTab("Barrows",   gate);
			decantContent    = buildPremiumGateTab("Decant",    gate);
			dipsContent      = buildPremiumGateTab("Dips",      gate);
			tabletsContent   = buildPremiumGateTab("Tablets",   gate);
			planContent      = buildPremiumGateTab("Plan",      gate);
			screenersContent = buildPremiumGateTab("Screeners",
				"To use screeners, sign up and link your API key.");
		}

		JPanel otherContent      = buildOtherTab(decantContent, dipsContent,
			highAlchContent, tabletsContent, favouritesContent, screenersContent,
			planContent, newsContent,
			moonContent, barrowsContent, dumpsContent, alertsContent);
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
		contentByName.put("Other",     otherContent);
		contentByName.put("Trades",    myFlipsContent);
		// New movable-pool entries: can appear EITHER in Row 1 (top-level tab)
		// OR inside Other (sub-tab). Building each panel once and slotting it
		// into the right pane keeps state coherent across the swap.
		contentByName.put("Decant",  decantContent);
		contentByName.put("Dips",    dipsContent);
		contentByName.put("Alch",    highAlchContent);
		contentByName.put("Tablets", tabletsContent);
		contentByName.put("Favs",    favouritesContent);
		contentByName.put("Screener",screenersContent);
		contentByName.put("Plan",    planContent);
		contentByName.put("News",    newsContent);
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
			if (idx < 0) return;
			String title = tabs.getTitleAt(idx);
			// Track the user's currently-active movable-pool tab regardless
			// of whether they reached it via top-row promotion OR via the
			// Other inner pane. This way rebuilds can ALWAYS restore the
			// right sub-tab even when the pool flickers (auth check, etc.).
			if (MOVABLE_POOL.contains(title))
			{
				lastOtherSubTab = title;
			}
			if ("Item".equals(title))
			{
				ensureInsightsPanel();
			}
			else if ("Plan".equals(title) && plugin != null)
			{
				// Plan-as-top-row: kick the session poll on tab selection so
				// the sync starts even when Plan isn't inside Other.
				plugin.onPlanTabSelected();
			}
			else if (plugin != null && !"Other".equals(title))
			{
				// Stop the Plan poll when the user navigates away from Plan to
				// any other top-row tab (Other has its own inner listener that
				// handles the Plan-inside-Other case).
				plugin.onPlanTabDeselected();
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

	/**
	 * Installs a {@link javax.swing.plaf.metal.MetalTabbedPaneUI} subclass on
	 * the given pane that (a) pins run order via {@code shouldRotateTabRuns}
	 * returning false, and (b) overrides the paint pipeline so the tabs
	 * match the dark theme — flat dark background, no focus ring, no 3D
	 * content border, with a 2px orange underline on the selected tab.
	 *
	 * Falls back to a logged warning + leaving the default UI alone if the
	 * platform LAF refuses Metal's UI (very rare on Swing).
	 */
	private static void applyStaticOrderUI(JTabbedPane pane)
	{
		try
		{
			pane.setUI(new javax.swing.plaf.metal.MetalTabbedPaneUI()
			{
				private final Color TAB_BG    = ColorScheme.DARK_GRAY_COLOR;
				private final Color TAB_HOVER = new Color(0x3A3A3A);
				private final Color UNDERLINE = new Color(0xFF981F);

				@Override
				protected boolean shouldRotateTabRuns(int tabPlacement)
				{
					// Pin run order — the selected tab's row doesn't get
					// moved adjacent to content.
					return false;
				}

				@Override
				protected void paintTabBackground(java.awt.Graphics g, int tabPlacement,
					int tabIndex, int x, int y, int w, int h, boolean isSelected)
				{
					// Flat dark fill. The unselected/selected distinction is
					// carried by the orange underline below, not by background.
					g.setColor(isSelected ? TAB_HOVER : TAB_BG);
					g.fillRect(x, y, w, h);
					if (isSelected)
					{
						g.setColor(UNDERLINE);
						g.fillRect(x, y + h - 2, w, 2);
					}
				}

				@Override
				protected void paintTabBorder(java.awt.Graphics g, int tabPlacement,
					int tabIndex, int x, int y, int w, int h, boolean isSelected)
				{
					// No tab border — Metal's default is a 3D bevel that
					// clashes with the flat dark look.
				}

				@Override
				protected void paintContentBorder(java.awt.Graphics g, int tabPlacement,
					int selectedIndex)
				{
					// No content border. Metal draws a chunky frame around
					// the active tab's content; we want the rows below to
					// flow directly off the tab strip.
				}

				@Override
				protected void paintFocusIndicator(java.awt.Graphics g, int tabPlacement,
					java.awt.Rectangle[] rects, int tabIndex,
					java.awt.Rectangle iconRect, java.awt.Rectangle textRect, boolean isSelected)
				{
					// No focus ring — the dotted rectangle that Metal paints
					// around the focused tab reads as a "white border" bug.
				}

				@Override
				protected java.awt.Insets getContentBorderInsets(int tabPlacement)
				{
					// Zero out the content-area gap so rows abut the tabs
					// cleanly instead of leaving a thick frame.
					return new java.awt.Insets(0, 0, 0, 0);
				}

				@Override
				protected java.awt.Insets getTabInsets(int tabPlacement, int tabIndex)
				{
					// Vertical 7/7 gives a chunky-but-not-tall bar. Horizontal
					// 4/4 lets labels sit centred with visible breathing room
					// inside the forced 4-per-row grid at side-panel widths.
					return new java.awt.Insets(7, 4, 7, 4);
				}

				@Override
				protected int calculateTabWidth(int tabPlacement, int tabIndex,
					java.awt.FontMetrics metrics)
				{
					// Force a deterministic grid. WRAP_TAB_LAYOUT's default
					// uses each tab's natural width, so longer labels would
					// otherwise spill the layout into uneven rows. Pinning
					// every tab to (available/perRow) guarantees an even
					// spread and a fixed number of rows.
					javax.swing.JTabbedPane pane = this.tabPane;
					int paneWidth = pane == null ? 0 : pane.getWidth();
					if (paneWidth <= 0)
					{
						return super.calculateTabWidth(tabPlacement, tabIndex, metrics);
					}
					// IMPORTANT: the JTabbedPane layout subtracts BOTH the
					// outer pane insets AND the tab-area insets when computing
					// `returnAt`. Subtracting only one (the previous bug)
					// returned over-wide tabs and pushed the layout to extra
					// rows (e.g. 7 sub-tabs landed as 3+3+1 instead of 4+3).
					java.awt.Insets paneInsets = pane.getInsets();
					java.awt.Insets tabAreaInsets = getTabAreaInsets(tabPlacement);
					int available = paneWidth
						- paneInsets.left - paneInsets.right
						- tabAreaInsets.left - tabAreaInsets.right;
					int n = pane.getTabCount();
					int perRow = perRowFor(n);
					// -4 safety buffer absorbs run-overlay + per-tab border
					// rounding so an off-by-one can't bump the last tab to a
					// new row.
					return Math.max(40, (available - 4) / perRow);
				}

				@Override
				protected int calculateMaxTabWidth(int tabPlacement)
				{
					// Some Metal layout paths consult the max width separately
					// — keep it in sync with the per-tab width so the layout
					// doesn't over-size based on the longest label.
					return calculateTabWidth(tabPlacement, 0,
						tabPane.getFontMetrics(tabPane.getFont()));
				}

				/** Tab-count → tabs-per-row. Caps to ≤ 2 rows and ensures
				 *  each row is fully spread. 7 sub-tabs → 4+3, not 3+3+1. */
				private int perRowFor(int n)
				{
					if (n <= 4) return Math.max(1, n);
					if (n <= 6) return 3;   // 5 → 3+2, 6 → 3+3
					if (n <= 8) return 4;   // 7 → 4+3, 8 → 4+4
					return (int) Math.ceil(n / 2.0); // 9+ → ceil(n/2) per row, 2 rows
				}
			});
			// Foreground colour reads from setForeground on the pane; selected
			// tab keeps the same colour since we don't repaint text — this is
			// already handled by Metal's default text-rendering path.
			pane.setOpaque(true);
		}
		catch (Exception e)
		{
			// If the LAF rejects MetalTabbedPaneUI we'd rather keep working
			// (just with Swing's default run rotation) than crash the panel.
			org.slf4j.LoggerFactory.getLogger(O7FlipPanel.class)
				.warn("[07Flip] Could not pin tab-run order: {}", e.getMessage());
		}
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
			insightsPanel = new com.o7flip.ui.InsightsPanel(itemManager, plugin, config);
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

	/**
	 * Re-renders the Item tab's loaded item with the current section-visibility
	 * config. Called by the plugin when an "Item tab" toggle changes so the
	 * panel updates live, without the user re-clicking the item.
	 */
	public void refreshInsightsSections()
	{
		if (insightsPanel != null)
		{
			insightsPanel.refreshSectionVisibility();
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

	// Tab the user was on before the GE offer screen auto-switched them to the
	// Item tab. Restored when the GE screen closes. EDT-only.
	private String tabBeforeGeAutoOpen = null;

	/**
	 * Called by the GE auto-open detector just before it switches to the Item
	 * tab. Remembers the current tab so we can return to it when the GE offer
	 * screen closes. Records once per GE session, and never records "Item"
	 * itself (so re-entering insights mid-session doesn't lose the origin).
	 */
	public void markGeAutoOpen()
	{
		if (tabBeforeGeAutoOpen != null)
		{
			return;
		}
		String cur = currentSelectedTabName();
		if (cur != null && !"Item".equals(cur))
		{
			tabBeforeGeAutoOpen = cur;
		}
	}

	/**
	 * Called when both GE offer screens have closed. Returns to the tab the
	 * user was on before the auto-open — but only if the Item tab is still
	 * selected, so a deliberate navigation away while the offer was open is
	 * never overridden.
	 */
	public void restoreTabAfterGeAutoOpen()
	{
		if (tabBeforeGeAutoOpen == null)
		{
			return;
		}
		String target = tabBeforeGeAutoOpen;
		tabBeforeGeAutoOpen = null;
		if ("Item".equals(currentSelectedTabName()))
		{
			selectTab(target);
		}
	}

	public void rebuildTabs()
	{
		// Capture the user's CURRENT selection — both the outer tab and, if
		// they're inside Other, the inner sub-tab. Without preserving the
		// inner explicitly the rebuild relies on lastOtherSubTab tracking,
		// which is racy and breaks when the previously-active sub-tab
		// momentarily disappears from the pool (e.g. Plan during an auth
		// re-check). Capturing here and forcing the restore below makes
		// the rebuild idempotent regardless of pool fluctuation.
		String previouslySelected = currentSelectedTabName();
		if ("Other".equals(previouslySelected))
		{
			String innerNow = currentInnerOtherSubTab();
			if (innerNow != null) lastOtherSubTab = innerNow;
		}

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
		renderDips(q);
		renderHighAlch(q);
		renderTeleTablets(q);
		renderFavourites(q);
		renderScreeners();
		renderAlerts(q);
		refreshBlocklistFooter();

		if (previouslySelected != null)
		{
			boolean restored = selectTab(previouslySelected);
			if (!restored && MOVABLE_POOL.contains(previouslySelected))
			{
				// The tab the user was on (e.g. Plan promoted to top row)
				// no longer exists at the outer level — likely demoted into
				// Other. Pivot to Other so the inner pane's restoration
				// (which reads lastOtherSubTab — already set to this name
				// by the outer change listener) lands on the right sub-tab.
				lastOtherSubTab = previouslySelected;
				selectTab("Other");
			}
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

	/**
	 * Walks into the currently-selected outer tab and returns the inner
	 * sub-tab title if and only if the outer tab is "Other". Used by
	 * {@link #rebuildTabs} to lock in the inner selection before the tear-
	 * down begins. Null if the outer isn't Other, or the inner pane can't
	 * be reached.
	 */
	private String currentInnerOtherSubTab()
	{
		if (tabsWrapper.getComponentCount() == 0) return null;
		java.awt.Component c = tabsWrapper.getComponent(0);
		if (!(c instanceof JTabbedPane)) return null;
		JTabbedPane pane = (JTabbedPane) c;
		int idx = pane.getSelectedIndex();
		if (idx < 0 || !"Other".equals(pane.getTitleAt(idx))) return null;
		java.awt.Component outerContent = pane.getComponentAt(idx);
		// buildOtherTab wraps the inner pane in a JPanel(BorderLayout) — the
		// inner pane is the CENTER component. Walk one level down to reach it.
		if (outerContent instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) outerContent).getComponents())
			{
				if (child instanceof JTabbedPane)
				{
					JTabbedPane inner = (JTabbedPane) child;
					int innerIdx = inner.getSelectedIndex();
					if (innerIdx >= 0) return inner.getTitleAt(innerIdx);
					return null;
				}
			}
		}
		return null;
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

		// Sort selector — server-side sort key + asc/desc handling. Labels
		// taken from FLIPS_SORTS so adding entries there auto-updates the UI.
		String[] sortLabels = new String[FLIPS_SORTS.length];
		for (int i = 0; i < FLIPS_SORTS.length; i++) sortLabels[i] = FLIPS_SORTS[i][1];
		flipsSortCombo = styledCombo(sortLabels);
		flipsSortCombo.setSelectedIndex(Math.max(0, Math.min(flipsSortIdx, FLIPS_SORTS.length - 1)));
		flipsSortCombo.addActionListener(e ->
		{
			flipsSortIdx = flipsSortCombo.getSelectedIndex();
			flipsPage = 0;
			if (plugin != null)
			{
				plugin.onFlipsFilterChanged();
			}
		});

		// Category selector — base preset. F2P toggle still overrides for
		// stricter F2P-only behaviour; otherwise the chosen preset drives
		// the server's ?preset= param.
		String[] categoryLabels = new String[PRESETS.length];
		for (int i = 0; i < PRESETS.length; i++) categoryLabels[i] = PRESETS[i][1];
		JComboBox<String> categoryCombo = styledCombo(categoryLabels);
		categoryCombo.setSelectedIndex(Math.max(0, Math.min(flipsCategoryIdx, PRESETS.length - 1)));
		categoryCombo.addActionListener(e ->
		{
			flipsCategoryIdx = categoryCombo.getSelectedIndex();
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null) plugin.onFlipsFilterChanged();
		});

		// Min hourly volume — kills the "Bird snare / Fire rune" penny-flips
		// that bubble to the top of ROI sort. Default index = 1000/hr.
		JComboBox<String> minVolCombo = styledCombo(FLIPS_MIN_VOL_LABELS);
		minVolCombo.setSelectedIndex(Math.max(0, Math.min(flipsMinHourlyVolIdx, FLIPS_MIN_VOL_LABELS.length - 1)));
		minVolCombo.addActionListener(e ->
		{
			flipsMinHourlyVolIdx = minVolCombo.getSelectedIndex();
			flipsPage = 0;
			rebuildFlipsChipBar();
			renderFlips(filtered());
		});

		// Min buy price — same anti-trash purpose as Min volume, but uses the
		// server's existing priceMin param so it works without server changes.
		JComboBox<String> minPriceCombo = styledCombo(FLIPS_MIN_BUY_PRICE_LABELS);
		minPriceCombo.setSelectedIndex(Math.max(0, Math.min(flipsMinBuyPriceIdx, FLIPS_MIN_BUY_PRICE_LABELS.length - 1)));
		minPriceCombo.addActionListener(e ->
		{
			flipsMinBuyPriceIdx = minPriceCombo.getSelectedIndex();
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null) plugin.onFlipsFilterChanged();
		});

		// Tax-free toggle — items under 50gp pay no GE tax. Useful for
		// high-volume / low-margin flipping where the 2% bite would dominate.
		JButton taxFreeBtn = pillButton("Tax-free only");
		taxFreeBtn.setToolTipText("<html>Show only items priced below 50gp.<br>"
			+ "These pay no GE tax so the listed margin is the real margin.</html>");
		applyToggleStyle(taxFreeBtn, flipsTaxFreeOnly);
		taxFreeBtn.addActionListener(e ->
		{
			flipsTaxFreeOnly = !flipsTaxFreeOnly;
			applyToggleStyle(taxFreeBtn, flipsTaxFreeOnly);
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null) plugin.onFlipsFilterChanged();
		});

		JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(0, 8, 8, 8));
		panel.add(filterRowLabel("Category"));
		panel.add(categoryCombo);
		panel.add(filterRowLabel("Sort by"));
		panel.add(flipsSortCombo);
		panel.add(filterRowLabel("Capital"));
		panel.add(flipsCapitalCombo);
		panel.add(filterRowLabel("Min volume"));
		panel.add(minVolCombo);
		panel.add(filterRowLabel("Min price"));
		panel.add(minPriceCombo);
		panel.add(filterRowLabel("Min profit"));
		panel.add(flipsMinProfitCombo);
		panel.add(filterRowLabel("Account"));
		panel.add(accountRow);
		panel.add(filterRowLabel(""));
		panel.add(taxFreeBtn);
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

	/**
	 * Builds the three-pill segmented control for the dumps tier filter.
	 * Buttons are persisted in {@link #dumpsTierBtns} so
	 * {@link #repaintDumpsTierBar()} can refresh the counts whenever a fresh
	 * response lands without rebuilding the row.
	 */
	private JPanel buildDumpsTierBar()
	{
		dumpsTierBtns = new JButton[3];
		final String[] keys   = {"all", "confirmed", "likely"};
		final String[] labels = {"All", "Confirmed", "Likely"};
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		for (int i = 0; i < keys.length; i++)
		{
			final int idx = i;
			JButton btn = pillButton(labels[i]);
			btn.addActionListener(e ->
			{
				if (dumpsTier.equals(keys[idx])) return;
				dumpsTier = keys[idx];
				dumpsPage = 0;
				repaintDumpsTierBar();
				if (plugin != null) plugin.onDumpsFilterChanged();
			});
			dumpsTierBtns[i] = btn;
			bar.add(btn);
		}
		repaintDumpsTierBar();
		return bar;
	}

	/** Refreshes the pill labels + selected state on the tier bar. */
	private void repaintDumpsTierBar()
	{
		if (dumpsTierBtns == null) return;
		final String[] keys = {"all", "confirmed", "likely"};
		int totalAll = dumpsConfirmedTotal + dumpsLikelyTotal;
		// Show counts when we have them; bare label on first paint.
		String[] withCounts = {
			totalAll > 0 ? "All " + totalAll : "All",
			dumpsConfirmedTotal > 0 ? "Confirmed " + dumpsConfirmedTotal : "Confirmed",
			dumpsLikelyTotal > 0 ? "Likely " + dumpsLikelyTotal : "Likely",
		};
		for (int i = 0; i < dumpsTierBtns.length; i++)
		{
			dumpsTierBtns[i].setText(withCounts[i]);
			applySortStyle(dumpsTierBtns[i], keys[i].equals(dumpsTier));
		}
	}

	private JPanel buildDumpsTab()
	{
		// ── v5 tier segmented control ────────────────────────────────────────
		// Three pill buttons: [All N] [Confirmed N] [Likely N]. Default to
		// "all" — the server includes the strong-pattern PvM-drop bots in
		// "likely" tier and the user wants them visible by default.
		dumpsTierBar = buildDumpsTierBar();

		// ── v3 sort bar ──────────────────────────────────────────────────────
		// Five server-side sorts. The default (Max Profit) matches the
		// website's "what should I act on right now" view.
		final String[] sortLabels = {"Max Profit", "Recovery", "Vol×Cons", "Score", "Recent"};
		final String[] sortKeys   = {"max_profit", "recovery_pct", "volume_consistency", "dump_pct", "recent"};
		// Seed the index from the persisted key so the highlighted button
		// matches state on rebuild.
		dumpsSortIdx = 0;
		for (int i = 0; i < sortKeys.length; i++) { if (sortKeys[i].equals(dumpsSortKey)) { dumpsSortIdx = i; break; } }
		dumpsSortBtns = new JButton[sortLabels.length];
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
				dumpsSortKey = sortKeys[idx];
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

		// ── Secondary filters (Active · Min Score · Min Profit · Price) ─────
		// All hidden behind a "More" expander to keep the default surface to
		// just tier + sort. The website mostly uses Tier + Max Profit and
		// rarely the other knobs, so we follow that hierarchy.
		JButton activeBtn = pillButton("Active");
		activeBtn.setToolTipText("<html>When on, only items currently dumping or due soon<br>"
			+ "(dump_status ∈ {dumping, due_soon}) are shown.</html>");
		applyToggleStyle(activeBtn, dumpsActiveOnly);
		activeBtn.addActionListener(e ->
		{
			dumpsActiveOnly = !dumpsActiveOnly;
			applyToggleStyle(activeBtn, dumpsActiveOnly);
			dumpsPage = 0;
			if (plugin != null) plugin.onDumpsFilterChanged();
		});

		final int[]    scoreThresholds = {0, 30, 60, 80};
		final String[] scoreLabels     = {"Any score", "30+", "60+", "80+"};
		JComboBox<String> minScoreCb = styledCombo(scoreLabels);
		int initialScoreIdx = 0;
		for (int i = 0; i < scoreThresholds.length; i++) { if (dumpsMinScore >= scoreThresholds[i]) initialScoreIdx = i; }
		minScoreCb.setSelectedIndex(initialScoreIdx);
		minScoreCb.addActionListener(e ->
		{
			dumpsMinScore = scoreThresholds[minScoreCb.getSelectedIndex()];
			dumpsPage = 0;
			if (plugin != null) plugin.onDumpsFilterChanged();
		});

		JComboBox<String> minProfitCb = styledCombo(DUMP_MIN_PROFIT_LABELS);
		minProfitCb.addActionListener(e ->
		{
			dumpsMinProfitIdx = minProfitCb.getSelectedIndex();
			dumpsPage = 0;
			renderDumps(filtered());
			if (plugin != null) plugin.onDumpsFilterChanged();
		});

		JComboBox<String> priceRangeCb = styledCombo(PRICE_RANGE_LABELS);
		priceRangeCb.addActionListener(e ->
		{
			dumpsPriceRangeIdx = priceRangeCb.getSelectedIndex();
			dumpsPage = 0;
			renderDumps(filtered());
			if (plugin != null) plugin.onDumpsFilterChanged();
		});

		// Collapsible "More" expander hosting the four secondary controls.
		// First row: Active + Min Score. Second row: Min Profit + Max Price.
		JPanel moreInner = new JPanel();
		moreInner.setLayout(new BoxLayout(moreInner, BoxLayout.Y_AXIS));
		moreInner.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		moreInner.setBorder(new EmptyBorder(2, 8, 4, 8));
		moreInner.setVisible(false);

		JPanel moreRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		moreRow1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		moreRow1.add(activeBtn);
		moreRow1.add(minScoreCb);

		JPanel moreRow2 = new JPanel(new GridLayout(1, 2, 4, 0));
		moreRow2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		moreRow2.setBorder(new EmptyBorder(4, 0, 0, 0));
		moreRow2.add(minProfitCb);
		moreRow2.add(priceRangeCb);

		moreInner.add(moreRow1);
		moreInner.add(moreRow2);

		JButton moreToggle = pillButton("More filters ▾");
		moreToggle.setBackground(new Color(0x3E3E3E));
		moreToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		moreToggle.addActionListener(e ->
		{
			boolean show = !moreInner.isVisible();
			moreInner.setVisible(show);
			moreToggle.setText(show ? "More filters ▴" : "More filters ▾");
		});

		JPanel moreToggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		moreToggleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		moreToggleRow.setBorder(new EmptyBorder(2, 8, 0, 8));
		moreToggleRow.add(moreToggle);

		JPanel topBar = new JPanel();
		topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(dumpsTierBar);
		topBar.add(sortRow);
		topBar.add(moreToggleRow);
		topBar.add(moreInner);

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

			default: // Alerts tab: "Alerts" (merch feed) + "Watch" (user price alerts) sub-views
			{
				// Merch-feed view (the original Alerts content, with its sort bar).
				alertsListPanel = listPanel();
				JPanel merchView = assembleTab(buildAlertsSortBar(), alertsListPanel, null);

				// Watch view — the user's own price alerts.
				watchListPanel = listPanel();
				JPanel watchView = assembleTab(null, watchListPanel, null);
				renderWatch();

				alertsTabCard = new JPanel(new CardLayout());
				alertsTabCard.add(merchView, "alerts");
				alertsTabCard.add(watchView, "watch");

				// Sub-nav toggle pinned above the card.
				JButton alertsBtn = pillButton("Alerts");
				JButton watchBtn  = pillButton("Watch");
				alertsSubNavBtns = new JButton[]{alertsBtn, watchBtn};
				applySortStyle(alertsBtn, true);
				applySortStyle(watchBtn, false);
				alertsBtn.addActionListener(e -> selectAlertsSubView("alerts"));
				watchBtn.addActionListener(e -> selectAlertsSubView("watch"));

				JPanel subNav = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
				subNav.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				subNav.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
				subNav.add(alertsBtn);
				subNav.add(watchBtn);

				JPanel wrap = new JPanel(new BorderLayout());
				wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
				wrap.add(subNav, BorderLayout.NORTH);
				wrap.add(alertsTabCard, BorderLayout.CENTER);
				return wrap;
			}
		}
	}

	/** Switches the Alerts tab between the merch feed and the user's Watch list. */
	private void selectAlertsSubView(String which)
	{
		if (alertsTabCard == null)
		{
			return;
		}
		((CardLayout) alertsTabCard.getLayout()).show(alertsTabCard, which);
		if (alertsSubNavBtns != null && alertsSubNavBtns.length == 2)
		{
			applySortStyle(alertsSubNavBtns[0], "alerts".equals(which));
			applySortStyle(alertsSubNavBtns[1], "watch".equals(which));
		}
		// Lazily refresh the user's price alerts when they open the Watch view.
		if ("watch".equals(which) && plugin != null)
		{
			plugin.onWatchTabSelected();
		}
	}

	/**
	 * The Dips sub-tab — paginated list against /api/runelite/dips, sortable
	 * by recency, dip %, or distance from ATL. The endpoint mixes 24h-dip
	 * and ATL rows; {@link DipItemPanel} handles the per-row badge + metric.
	 */
	private JPanel buildDipsTab()
	{
		dipsSortBtns  = new JButton[3];
		dipsListPanel = listPanel();
		dipsPageLabel = pageLabel();
		dipsPrev      = pageBtn("‹");
		dipsNext      = pageBtn("›");
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

		// Activity-window selector — drives the server's activity_window
		// param + the per-row "in 1d/7d/30d" label. Default 1d.
		String[] windowLabels = {"1d", "7d", "30d"};
		JButton[] dipsWindowBtns = new JButton[windowLabels.length];
		JPanel windowRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		windowRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		windowRow.setBorder(new EmptyBorder(2, 0, 0, 0));
		for (int i = 0; i < windowLabels.length; i++)
		{
			final String w = windowLabels[i];
			JButton btn = pillButton(w);
			applySortStyle(btn, w.equals(dipsActivityWindow));
			btn.addActionListener(e ->
			{
				if (w.equals(dipsActivityWindow)) return;
				dipsActivityWindow = w;
				dipsPage = 0;
				for (int j = 0; j < dipsWindowBtns.length; j++)
				{
					applySortStyle(dipsWindowBtns[j], windowLabels[j].equals(dipsActivityWindow));
				}
				if (plugin != null) plugin.onDipsSortChanged(dipsSortKey);
			});
			dipsWindowBtns[i] = btn;
			windowRow.add(btn);
		}

		String[] labels = {"Recent", "Dip %", "ATL %"};
		String[] keys   = {"recent", "dip_pct", "atl_pct"};
		JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		sortRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortRow.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		for (int i = 0; i < labels.length; i++)
		{
			final int idx = i;
			JButton btn = pillButton(labels[i]);
			applySortStyle(btn, idx == dipsSortIdx);
			btn.addActionListener(e ->
			{
				dipsSortIdx = idx;
				dipsSortKey = keys[idx];
				dipsPage    = 0;
				hiliteFilter(dipsSortBtns, dipsSortIdx);
				if (plugin != null)
				{
					plugin.onDipsSortChanged(dipsSortKey);
				}
			});
			dipsSortBtns[i] = btn;
			sortRow.add(btn);
		}

		JPanel topBar = new JPanel();
		topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(windowRow);
		topBar.add(sortRow);

		return assembleTab(topBar, dipsListPanel, buildPageBar(dipsPageLabel, dipsPrev, dipsNext));
	}

	/**
	 * High Alch sub-tab — server-paginated against /api/runelite/high-alch.
	 * Staff toggles (Fire / Bryophyta) re-fire the request with the matching
	 * query params; the server applies the modifier and recomputes profit so
	 * the panel never needs to know the rune-cost math.
	 */
	private JPanel buildHighAlchTab()
	{
		// Seed toggle state from the persisted config so the buttons render
		// in the right state on first build.
		highAlchFireStaff = config != null && config.highAlchFireStaff();
		highAlchBryophyta = config != null && config.highAlchBryophyta();

		highAlchSortBtns  = new JButton[4];
		highAlchListPanel = listPanel();
		highAlchPageLabel = pageLabel();
		highAlchPrev      = pageBtn("‹");
		highAlchNext      = pageBtn("›");
		highAlchPrev.addActionListener(e ->
		{
			if (plugin != null) plugin.onHighAlchPageChanged(--highAlchPage);
		});
		highAlchNext.addActionListener(e ->
		{
			if (plugin != null) plugin.onHighAlchPageChanged(++highAlchPage);
		});

		String[] labels = {"Profit", "ROI", "Buy", "Volume"};
		String[] keys   = {"profit", "roi", "buyPrice", "dailyVolume"};
		JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		sortRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortRow.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		for (int i = 0; i < labels.length; i++)
		{
			final int idx = i;
			JButton btn = pillButton(labels[i]);
			applySortStyle(btn, idx == highAlchSortIdx);
			btn.addActionListener(e ->
			{
				highAlchSortIdx = idx;
				highAlchSortKey = keys[idx];
				highAlchPage    = 0;
				hiliteFilter(highAlchSortBtns, highAlchSortIdx);
				if (plugin != null) plugin.onHighAlchSortChanged(highAlchSortKey);
			});
			highAlchSortBtns[i] = btn;
			sortRow.add(btn);
		}

		// Staff toggles row — sits underneath the sort bar and shows current
		// alch cost so the user sees the modifier's effect immediately.
		highAlchFireStaffBtn = pillButton("Fire staff");
		highAlchBryophytaBtn = pillButton("Bryophyta");
		applyToggleStyle(highAlchFireStaffBtn, highAlchFireStaff);
		applyToggleStyle(highAlchBryophytaBtn, highAlchBryophyta);
		// Fire staff + Bryophyta are mutually exclusive — a player carries
		// one staff at a time, so turning one on automatically turns the
		// other off. Both off is fine (= no staff modifier).
		highAlchFireStaffBtn.addActionListener(e ->
		{
			highAlchFireStaff = !highAlchFireStaff;
			if (highAlchFireStaff) highAlchBryophyta = false;
			applyToggleStyle(highAlchFireStaffBtn, highAlchFireStaff);
			applyToggleStyle(highAlchBryophytaBtn, highAlchBryophyta);
			if (plugin != null)
			{
				plugin.persistHighAlchModifier("highAlchFireStaff", highAlchFireStaff);
				plugin.persistHighAlchModifier("highAlchBryophyta", highAlchBryophyta);
				highAlchPage = 0;
				plugin.onHighAlchModifierChanged();
			}
		});
		highAlchBryophytaBtn.addActionListener(e ->
		{
			highAlchBryophyta = !highAlchBryophyta;
			if (highAlchBryophyta) highAlchFireStaff = false;
			applyToggleStyle(highAlchBryophytaBtn, highAlchBryophyta);
			applyToggleStyle(highAlchFireStaffBtn, highAlchFireStaff);
			if (plugin != null)
			{
				plugin.persistHighAlchModifier("highAlchBryophyta", highAlchBryophyta);
				plugin.persistHighAlchModifier("highAlchFireStaff", highAlchFireStaff);
				highAlchPage = 0;
				plugin.onHighAlchModifierChanged();
			}
		});

		highAlchCostLabel = new JLabel(" ");
		highAlchCostLabel.setFont(Fonts.SM);
		highAlchCostLabel.setForeground(new Color(0x888888));

		JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		toggleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		toggleRow.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		toggleRow.add(highAlchFireStaffBtn);
		toggleRow.add(highAlchBryophytaBtn);
		toggleRow.add(highAlchCostLabel);

		JPanel topBar = new JPanel();
		topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(sortRow);
		topBar.add(toggleRow);

		return assembleTab(topBar, highAlchListPanel, buildPageBar(highAlchPageLabel, highAlchPrev, highAlchNext));
	}

	/** Compact green/grey "on" styling for boolean toggle pills. */
	private void applyToggleStyle(JButton btn, boolean on)
	{
		btn.setBackground(on ? new Color(0x00C27A) : new Color(0x3E3E3E));
		btn.setForeground(on ? Color.BLACK : ColorScheme.LIGHT_GRAY_COLOR);
	}

	/**
	 * Tablets sub-tab — non-paginated (dataset is small). Sort + spellbook
	 * filter + "profitable only" toggle drive the request.
	 */
	private JPanel buildTeleTabletsTab()
	{
		// Two sort options: Volume (daily GE volume) and Profit (margin
		// between cost and post-tax sell). Volume leads — high-volume
		// tablets are the actionable ones; profit-per-tablet matters less
		// when a niche tablet sells once a day.
		tabletsSortBtns  = new JButton[2];
		tabletsListPanel = listPanel();

		String[] labels = {"Volume", "Profit"};
		JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		sortRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortRow.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		for (int i = 0; i < labels.length; i++)
		{
			final int idx = i;
			JButton btn = pillButton(labels[i]);
			applySortStyle(btn, idx == tabletsSortIdx);
			btn.addActionListener(e ->
			{
				tabletsSortIdx = idx;
				hiliteFilter(tabletsSortBtns, tabletsSortIdx);
				if (plugin != null) plugin.onTeleTabletsFilterChanged();
			});
			tabletsSortBtns[i] = btn;
			sortRow.add(btn);
		}

		// Spellbook filter dropdown + Profitable-only pill.
		String[] books = {"All books", "Standard", "Lunar", "Ancient", "Arceuus"};
		JComboBox<String> bookCombo = styledCombo(books);
		bookCombo.setSelectedIndex(tabletsSpellbookIdx);
		bookCombo.addActionListener(e ->
		{
			tabletsSpellbookIdx = bookCombo.getSelectedIndex();
			if (plugin != null) plugin.onTeleTabletsFilterChanged();
		});

		JButton profitableBtn = pillButton("Profitable only");
		applyToggleStyle(profitableBtn, tabletsProfitableOnly);
		profitableBtn.addActionListener(e ->
		{
			tabletsProfitableOnly = !tabletsProfitableOnly;
			applyToggleStyle(profitableBtn, tabletsProfitableOnly);
			if (plugin != null) plugin.onTeleTabletsFilterChanged();
		});

		JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		filterRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		filterRow.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x3A3A3A)));
		filterRow.add(bookCombo);
		filterRow.add(profitableBtn);

		JPanel topBar = new JPanel();
		topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(sortRow);
		topBar.add(filterRow);

		return assembleTab(topBar, tabletsListPanel, null);
	}

	/** Favourites sub-tab — the user's saved item list, rendered as flip rows. */
	private JPanel buildFavouritesTab()
	{
		favouritesListPanel = listPanel();
		return assembleTab(null, favouritesListPanel, null);
	}

	/**
	 * News sub-tab — a master/detail pair behind a {@link CardLayout}: a list
	 * of article cards, and a per-article detail view showing the relevant
	 * items plus a link to the full post on 07flip.com.
	 */
	private JPanel buildNewsTab()
	{
		newsListPanel = listPanel();
		JPanel listView = assembleTab(null, newsListPanel, null);

		newsDetailPanel = listPanel();
		JButton back = pillButton("← Back to news");
		back.setBackground(new Color(0x2A2A2A));
		back.setForeground(new Color(0xAAAAAA));
		back.addActionListener(e -> showNewsList());

		JPanel detailHeader = new JPanel(new BorderLayout());
		detailHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailHeader.setBorder(new EmptyBorder(6, 8, 6, 8));
		detailHeader.add(back, BorderLayout.WEST);

		ListWrapperPanel detailWrapper = new ListWrapperPanel(newsDetailPanel);
		detailWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JScrollPane detailSp = new JScrollPane(detailWrapper);
		detailSp.setBorder(BorderFactory.createEmptyBorder());
		detailSp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		detailSp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		detailSp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));

		JPanel detailView = new JPanel(new BorderLayout());
		detailView.setBackground(ColorScheme.DARK_GRAY_COLOR);
		detailView.add(detailHeader, BorderLayout.NORTH);
		detailView.add(detailSp,     BorderLayout.CENTER);

		newsTabCard = new JPanel(new CardLayout());
		newsTabCard.add(listView,   "list");
		newsTabCard.add(detailView, "detail");

		renderNews();   // restore cached list across a tab rebuild
		// Restore the open article if the tab was rebuilt mid-read.
		if (activeNewsItem != null)
		{
			renderNewsDetail(activeNewsItem);
			((CardLayout) newsTabCard.getLayout()).show(newsTabCard, "detail");
		}
		return newsTabCard;
	}

	/** Pushes a fresh news feed into the News tab. Called from the plugin (EDT). */
	public void updateNews(java.util.List<com.o7flip.model.NewsItem> items)
	{
		newsItems = items != null ? items : new ArrayList<>();
		renderNews();
	}

	private void showNewsList()
	{
		activeNewsItem = null;
		if (newsTabCard != null)
		{
			((CardLayout) newsTabCard.getLayout()).show(newsTabCard, "list");
		}
	}

	private void showNewsDetail(com.o7flip.model.NewsItem n)
	{
		activeNewsItem = n;
		renderNewsDetail(n);
		if (newsTabCard != null)
		{
			((CardLayout) newsTabCard.getLayout()).show(newsTabCard, "detail");
		}
	}

	private void renderNews()
	{
		if (newsListPanel == null)
		{
			return;
		}
		newsListPanel.removeAll();
		if (newsItems.isEmpty())
		{
			newsListPanel.add(emptyLabel("No news yet",
				"Game-update posts and market-moving news will appear here."));
		}
		else
		{
			newsListPanel.add(Box.createVerticalStrut(4));
			int rendered = 0;
			for (int i = 0; i < newsItems.size(); i++)
			{
				final com.o7flip.model.NewsItem n = newsItems.get(i);
				if (n == null)
				{
					continue;
				}
				newsListPanel.add(new com.o7flip.ui.NewsItemPanel(n, rendered % 2 != 0, () -> showNewsDetail(n)));
				newsListPanel.add(Box.createVerticalStrut(6));
				rendered++;
			}
		}
		newsListPanel.revalidate();
		newsListPanel.repaint();
	}

	/** Detail view for one article: title, date, full summary, relevant items, and a link to the post. */
	private void renderNewsDetail(com.o7flip.model.NewsItem n)
	{
		if (newsDetailPanel == null || n == null)
		{
			return;
		}
		newsDetailPanel.removeAll();

		JLabel title = new JLabel("<html><div style='width:250px'>"
			+ com.o7flip.ui.NewsItemPanel.escapeHtml(n.title) + "</div></html>");
		title.setFont(Fonts.BOLD);
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setBorder(new EmptyBorder(10, 10, 0, 10));
		title.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
		newsDetailPanel.add(title);

		String meta = com.o7flip.ui.NewsItemPanel.relativeTime(n.publishedAt);
		if (n.category != null && !n.category.isEmpty())
		{
			meta = com.o7flip.ui.NewsItemPanel.capitalise(n.category) + (meta.isEmpty() ? "" : "  ·  " + meta);
		}
		if (!meta.isEmpty())
		{
			JLabel metaLabel = new JLabel(meta);
			metaLabel.setFont(Fonts.SM);
			metaLabel.setForeground(new Color(0x888888));
			metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			metaLabel.setBorder(new EmptyBorder(2, 10, 0, 10));
			newsDetailPanel.add(metaLabel);
		}

		if (n.summary != null && !n.summary.isEmpty())
		{
			JLabel summary = new JLabel("<html><div style='width:250px'>"
				+ com.o7flip.ui.NewsItemPanel.escapeHtml(n.summary) + "</div></html>");
			summary.setFont(Fonts.SM);
			summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			summary.setAlignmentX(Component.LEFT_ALIGNMENT);
			summary.setBorder(new EmptyBorder(8, 10, 0, 10));
			summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
			newsDetailPanel.add(summary);
		}

		// Relevant items — clickable rows into each item's insights.
		if (n.items != null && !n.items.isEmpty())
		{
			JLabel itemsHeader = new JLabel("Relevant items");
			itemsHeader.setFont(Fonts.SM_BOLD);
			itemsHeader.setForeground(new Color(0xC4A052));
			itemsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
			itemsHeader.setBorder(new EmptyBorder(12, 10, 4, 10));
			itemsHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemsHeader.getPreferredSize().height));
			newsDetailPanel.add(itemsHeader);

			for (com.o7flip.model.NewsItem.Related r : n.items)
			{
				if (r == null || r.itemId <= 0)
				{
					continue;
				}
				newsDetailPanel.add(buildNewsItemRow(r));
				newsDetailPanel.add(Box.createVerticalStrut(4));
			}
		}

		// Link to the full post.
		if (n.url != null && !n.url.isEmpty())
		{
			JButton openPost = pillButton("See the whole news post →");
			openPost.setBackground(ORANGE);
			openPost.setForeground(Color.BLACK);
			openPost.setAlignmentX(Component.LEFT_ALIGNMENT);
			openPost.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(n.url));
			JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
			wrap.setBorder(new EmptyBorder(14, 10, 10, 10));
			wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, openPost.getPreferredSize().height + 24));
			wrap.add(openPost);
			newsDetailPanel.add(wrap);
		}

		newsDetailPanel.revalidate();
		newsDetailPanel.repaint();
	}

	/** A single clickable item row in the news detail view (icon + name → insights). */
	private JPanel buildNewsItemRow(com.o7flip.model.NewsItem.Related r)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(new Color(0x1F1F1F));
		row.setBorder(new EmptyBorder(6, 10, 6, 10));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel icon = com.o7flip.ui.FlipItemPanel.buildIcon(r.itemId, itemManager);
		JLabel name = new JLabel(r.name != null && !r.name.isEmpty() ? r.name : "Item " + r.itemId);
		name.setFont(Fonts.BOLD);
		name.setForeground(Color.WHITE);

		row.add(icon, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);

		com.o7flip.ui.ClickRouter.attachInsightsOnly(row,  plugin, r.itemId, r.name);
		com.o7flip.ui.ClickRouter.attachInsightsOnly(name, plugin, r.itemId, r.name);

		final Color base = new Color(0x1F1F1F);
		final Color hover = new Color(0x2A2A2A);
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				row.setBackground(hover);
			}
			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				row.setBackground(base);
			}
		});
		return row;
	}

	/** Pushes the user's price alerts into the Watch sub-view. Called from the plugin (EDT). */
	public void updatePriceAlerts(java.util.List<com.o7flip.model.PriceAlert> alerts)
	{
		priceAlerts = alerts != null ? alerts : new ArrayList<>();
		renderWatch();
	}

	private void renderWatch()
	{
		if (watchListPanel == null)
		{
			return;
		}
		watchListPanel.removeAll();
		if (!hasApiKey())
		{
			watchListPanel.add(emptyLabel("API key required",
				"Paste your 07flip.com API key in the plugin config to use price alerts."));
		}
		else if (priceAlerts.isEmpty())
		{
			watchListPanel.add(emptyLabel("No price alerts",
				"Open an item, then tap 'Set price alert' to watch a target price."));
		}
		else
		{
			for (int i = 0; i < priceAlerts.size(); i++)
			{
				com.o7flip.model.PriceAlert a = priceAlerts.get(i);
				if (a == null)
				{
					continue;
				}
				watchListPanel.add(new com.o7flip.ui.WatchAlertPanel(a, itemManager, i % 2 != 0, plugin));
				watchListPanel.add(sep());
			}
		}
		watchListPanel.revalidate();
		watchListPanel.repaint();
	}

	/**
	 * "Create price alert" dialog, opened from the Item panel's button. Lets
	 * the user pick a side (buy/sell), a direction (above/below) and a target
	 * price (seeded with the current sell), then POSTs via the plugin.
	 */
	public void openPriceAlertDialog(int itemId, String name, long currentBuy, long currentSell)
	{
		if (plugin == null || itemId <= 0)
		{
			return;
		}

		String[] sides = {"Sell", "Buy"};
		String[] directions = {"above", "below"};
		javax.swing.JComboBox<String> sideBox = new javax.swing.JComboBox<>(sides);
		javax.swing.JComboBox<String> dirBox  = new javax.swing.JComboBox<>(directions);
		long seed = currentSell > 0 ? currentSell : currentBuy;
		javax.swing.JTextField targetField = new javax.swing.JTextField(seed > 0 ? String.valueOf(seed) : "", 12);

		JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
		form.add(new JLabel("Item:"));
		form.add(new JLabel("<html><b>" + (name != null ? name : "Item " + itemId) + "</b></html>"));
		form.add(new JLabel("Alert when the"));
		form.add(sideBox);
		form.add(new JLabel("price goes"));
		form.add(dirBox);
		form.add(new JLabel("target price (gp):"));
		form.add(targetField);
		form.add(new JLabel("<html><font color='#888888'>Whole numbers only — no commas or 'gp'.</font></html>"));
		form.add(new JLabel(""));
		if (!isPremium)
		{
			form.add(new JLabel("<html><font color='#888888'>Free accounts can keep up to 5 active alerts.</font></html>"));
			form.add(new JLabel(""));
		}

		int choice = javax.swing.JOptionPane.showConfirmDialog(this, form,
			"Set price alert", javax.swing.JOptionPane.OK_CANCEL_OPTION,
			javax.swing.JOptionPane.PLAIN_MESSAGE);
		if (choice != javax.swing.JOptionPane.OK_OPTION)
		{
			return;
		}

		long target;
		try
		{
			target = Long.parseLong(targetField.getText().trim().replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			javax.swing.JOptionPane.showMessageDialog(this,
				"The target price must be a whole number.", "Set price alert",
				javax.swing.JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (target <= 0)
		{
			javax.swing.JOptionPane.showMessageDialog(this,
				"The target price must be greater than zero.", "Set price alert",
				javax.swing.JOptionPane.WARNING_MESSAGE);
			return;
		}

		String side = ((String) sideBox.getSelectedItem()).toLowerCase();
		String direction = (String) dirBox.getSelectedItem();
		plugin.createPriceAlert(itemId, side, direction, target,
			() -> javax.swing.JOptionPane.showMessageDialog(this,
				"Alert created — manage it under Alerts → Watch.", "Set price alert",
				javax.swing.JOptionPane.INFORMATION_MESSAGE),
			() -> javax.swing.JOptionPane.showMessageDialog(this,
				"Couldn't create the alert — try again in a moment.", "Set price alert",
				javax.swing.JOptionPane.WARNING_MESSAGE));
	}

	/**
	 * Screeners sub-tab — two card layouts behind a {@link CardLayout}:
	 *
	 * <ul>
	 *   <li>{@code "list"} — preset cards (Custom on top, System below).</li>
	 *   <li>{@code "detail"} — drill-down for one preset's matches, with a
	 *       Back button that flips the card layout back to the list.</li>
	 * </ul>
	 *
	 * Same pattern as the Barrows tab so the navigation feels consistent.
	 */
	/**
	 * Builds the Optimizer "Plan" sub-tab. Plans are BUILT on 07flip.com
	 * (the in-panel slots/risk/window form is gone — the website is the
	 * configuration surface and syncs here automatically). The header strip
	 * shows a Reconfigure link while a plan is live; the list below renders
	 * the allocation cards, or a setup CTA when there is no plan yet.
	 */
	private JPanel buildPlanTab()
	{
		optimizerListPanel = listPanel();
		// Initial empty state — points the user at the website optimiser.
		renderOptimizerEmpty();

		optCollapsedPanel = buildOptimizerCollapsedPill();

		// Host shows the header strip only while a plan is live; empty
		// otherwise (the list's empty state carries the setup CTA).
		optInputsHost = new JPanel(new BorderLayout());
		optInputsHost.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		applyOptimizerFormVisibility();

		return assembleTab(optInputsHost, optimizerListPanel, null);
	}

	/**
	 * Header strip shown while a plan is live. The ⟳ Resync button is gone —
	 * the 15s poll now adopts site-side structure changes, merges fills and
	 * retro-attributes automatically, so a manual resync had nothing left to
	 * do. One button, one sentence saying what this view is.
	 */
	private JPanel buildOptimizerCollapsedPill()
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(8, 10, 8, 10));

		JLabel title = new JLabel("Active plan");
		title.setFont(Fonts.BOLD);
		title.setForeground(Color.WHITE);
		JLabel caption = new JLabel("Syncs with 07flip.com");
		caption.setFont(Fonts.SM);
		caption.setForeground(new Color(0x888888));

		JPanel text = new JPanel(new GridLayout(2, 1, 0, 1));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.add(title);
		text.add(caption);

		JButton reconfigure = pillButton("Reconfigure");
		reconfigure.setBackground(new Color(0x3E3E3E));
		reconfigure.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		reconfigure.setToolTipText("<html>Adjust capital, slots, risk or fill window on 07flip.com.<br>"
			+ "<font color='#888888'>Changes appear here automatically within a few seconds.</font></html>");
		reconfigure.addActionListener(e ->
		{
			LinkBrowser.browse("https://07flip.com/optimiser");
			if (plugin != null) plugin.startEagerSessionDiscovery();
		});

		JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
		east.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		east.add(reconfigure);

		row.add(text, BorderLayout.CENTER);
		row.add(east, BorderLayout.EAST);
		return row;
	}

	private void applyOptimizerFormVisibility()
	{
		if (optInputsHost == null) return;
		optInputsHost.removeAll();
		if (optFormCollapsed && optCollapsedPanel != null)
		{
			optInputsHost.add(optCollapsedPanel, BorderLayout.CENTER);
		}
		// No plan → empty header; the list's empty state carries the setup CTA.
		optInputsHost.revalidate();
		optInputsHost.repaint();
	}

	private static boolean memKeyMatches(String key, Boolean state)
	{
		switch (key)
		{
			case "members": return Boolean.TRUE.equals(state);
			case "f2p":     return Boolean.FALSE.equals(state);
			case "any":
			default:        return state == null;
		}
	}

	private String buildOptimizerCapitalLabel()
	{
		long cap = plugin != null ? plugin.effectiveCapital() : 0L;
		if (cap <= 0)
		{
			return "<html><font color='#E85050'>Capital not set</font></html>";
		}
		return "<html><font color='#888888'>Capital: </font><font color='#FFFFFF'>"
			+ formatCapital(cap) + "</font></html>";
	}

	/**
	 * Helper for the {Slots, Composition} stepper row. Both fields use ◀ / ▶
	 * buttons + a centered numeric label, kept side-by-side at narrow panel
	 * widths.
	 */
	private JPanel buildLabeledStepperRow(String labelA, java.util.function.IntSupplier getA,
		java.util.function.IntConsumer setA, int minA, int maxA,
		String labelB, java.util.function.IntSupplier getB,
		java.util.function.IntConsumer setB, int minB, int maxB)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(buildStepper(labelA, getA, setA, minA, maxA));
		row.add(buildStepper(labelB, getB, setB, minB, maxB));
		return row;
	}

	private JPanel buildStepper(String label, java.util.function.IntSupplier getter,
		java.util.function.IntConsumer setter, int min, int max)
	{
		JPanel s = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		s.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel l = new JLabel(label);
		l.setFont(Fonts.SM);
		l.setForeground(new Color(0xAAAAAA));
		JButton dec = pillButton("◀");
		JButton inc = pillButton("▶");
		dec.setBackground(new Color(0x3E3E3E));
		dec.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		inc.setBackground(new Color(0x3E3E3E));
		inc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JLabel value = new JLabel(String.valueOf(getter.getAsInt()));
		value.setFont(Fonts.BOLD);
		value.setForeground(Color.WHITE);
		value.setBorder(new EmptyBorder(0, 6, 0, 6));
		dec.addActionListener(e ->
		{
			int v = Math.max(min, getter.getAsInt() - 1);
			setter.accept(v);
			value.setText(String.valueOf(getter.getAsInt()));
		});
		inc.addActionListener(e ->
		{
			int v = Math.min(max, getter.getAsInt() + 1);
			setter.accept(v);
			value.setText(String.valueOf(getter.getAsInt()));
		});
		s.add(l);
		s.add(dec);
		s.add(value);
		s.add(inc);
		return s;
	}

	/** Called by the plugin when an optimizer response lands. */
	public void onOptimizeResult(com.o7flip.model.OptimizeResult result)
	{
		optInFlight = false;
		lastOptimize = result;
		// Collapse the form so allocations get the panel's vertical space.
		// Reconfigure button expands again.
		optFormCollapsed = true;
		applyOptimizerFormVisibility();
		renderOptimizerResult(result);
	}

	public void onOptimizePremiumRequired(String upgradeUrl)
	{
		optInFlight = false;
		renderOptimizerPremium(upgradeUrl);
	}

	/**
	 * Called by the plugin when a per-card swap request returns. Replaces
	 * the allocation at {@code index} in {@link #lastOptimize} and re-renders.
	 * Summary stays untouched — the total expected profit shifts slightly,
	 * but recomputing client-side would drift from the server's reality, so
	 * we just refresh the rows.
	 */
	public void onOptimizeSlotSwapped(int index, com.o7flip.model.OptimizeResult.Allocation next)
	{
		if (lastOptimize == null || lastOptimize.allocations == null
			|| index < 0 || index >= lastOptimize.allocations.size() || next == null)
		{
			return;
		}
		lastOptimize.allocations.set(index, next);
		renderOptimizerResult(lastOptimize);
	}

	public void onOptimizeError(String reason)
	{
		optInFlight = false;
		renderOptimizerEmptyMessage("Couldn't build plan",
			"Server returned: " + reason + ". Try again, or adjust your inputs.");
	}

	/**
	 * Hydrates the Plan tab from a server-saved {@link OptimizerSession}.
	 * Called on startup (GET /optimize/active) and on the 30s poll cycle so
	 * the panel always reflects whichever surface last wrote.
	 *
	 * Re-uses the existing optimize-result render path by stuffing the live
	 * slots into a synthetic {@link OptimizeResult} — the card visuals don't
	 * care which endpoint the data came from.
	 */
	public void hydrateOptimizerSession(com.o7flip.model.OptimizerSession session)
	{
		if (session == null || session.slots == null || session.slots.isEmpty()) return;
		// Restore the input controls so the user can see what generated this plan.
		optSlots        = session.inputs.slots > 0 ? Math.min(8, session.inputs.slots) : optSlots;
		optRisk         = session.inputs.risk != null && !session.inputs.risk.isEmpty() ? session.inputs.risk : optRisk;
		optMaxFillHours = session.inputs.maxFillHours != null ? session.inputs.maxFillHours : optMaxFillHours;
		optMembers      = session.inputs.members;
		optMinProfitPct = session.inputs.minProfitPct != null
			? (int) Math.round(Math.max(0, Math.min(10, session.inputs.minProfitPct))) : optMinProfitPct;

		com.o7flip.model.OptimizeResult result = new com.o7flip.model.OptimizeResult();
		result.updatedAt   = session.generatedAt;
		result.allocations = new java.util.ArrayList<>(session.slots);
		// Phase 4 — carry the server's plan summary verbatim so a poll renders the
		// real figures (slotSuggestion, emptyReason, degradedTrendData, fill
		// confidence, …) instead of a lossy re-sum. Fall back to a best-effort
		// local re-sum only when the server omitted it (older sessions), so a poll
		// never renders an all-zero summary.
		if (session.summary != null)
		{
			result.summary = session.summary;
		}
		else
		{
			long deployed = 0, cycleProfit = 0;
			for (com.o7flip.model.OptimizeResult.Allocation a : session.slots)
			{
				if (a == null) continue;
				deployed    += a.gpAllocated;
				cycleProfit += a.expectedProfit;
			}
			result.summary.capitalInput        = session.inputs.capital;
			result.summary.capitalDeployed     = deployed;
			result.summary.capitalUnused       = Math.max(0L, session.inputs.capital - deployed);
			result.summary.slotsRequested      = session.inputs.slots;
			result.summary.slotsUsed           = session.slots.size();
			result.summary.risk                = optRisk;
			result.summary.maxFillHours        = optMaxFillHours;
			result.summary.members             = optMembers;
			result.summary.expectedProfitTotal = cycleProfit;
		}

		lastOptimize = result;
		optFormCollapsed = true;
		applyOptimizerFormVisibility();
		// Don't yank the user out of the history view on a background poll —
		// just keep lastOptimize fresh so "Back to plan" shows current data.
		if (!optShowingHistory)
		{
			renderOptimizerResult(result);
		}
	}

	/** Called by the plugin after a successful DELETE /optimize/active. */
	public void onActivePlanCleared()
	{
		lastOptimize = null;
		optFormCollapsed = false;
		optShowingHistory = false;
		applyOptimizerFormVisibility();
		renderOptimizerEmpty();
	}

	private void renderOptimizerEmpty()
	{
		if (optimizerListPanel == null) return;
		optimizerListPanel.removeAll();
		optimizerListPanel.add(emptyLabel("No active plan",
			"Build your flip plan on 07flip.com — it appears here automatically and tracks your GE fills."));

		JButton open = pillButton("Open optimiser");
		open.setBackground(ORANGE);
		open.setForeground(Color.BLACK);
		open.setToolTipText("Opens 07flip.com/optimiser in your browser");
		open.addActionListener(e ->
		{
			LinkBrowser.browse("https://07flip.com/optimiser");
			// Burst-poll while they build so the plan lands here within ~5s of
			// saving, instead of waiting on the next regular poll cycle.
			if (plugin != null) plugin.startEagerSessionDiscovery();
		});
		JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.setBorder(new EmptyBorder(0, 14, 16, 14));
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(open);
		optimizerListPanel.add(wrap);

		optimizerListPanel.revalidate();
		optimizerListPanel.repaint();
	}

	private void renderOptimizerEmptyMessage(String title, String sub)
	{
		if (optimizerListPanel == null) return;
		optimizerListPanel.removeAll();
		optimizerListPanel.add(emptyLabel(title, sub));
		optimizerListPanel.revalidate();
		optimizerListPanel.repaint();
	}

	private void renderOptimizerLoading()
	{
		renderOptimizerEmptyMessage("Building plan…", "Asking the server to allocate your capital.");
	}

	private void renderOptimizerPremium(String upgradeUrl)
	{
		if (optimizerListPanel == null) return;
		optimizerListPanel.removeAll();

		JLabel title = new JLabel("Premium required");
		title.setFont(Fonts.BOLD);
		title.setForeground(ORANGE);
		title.setBorder(new EmptyBorder(20, 14, 4, 14));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel sub = new JLabel("<html><font color='#888888'>"
			+ "The 8-slot Optimizer is a premium feature. Upgrade to unlock<br>"
			+ "automatic capital allocation across the best risk-adjusted flips."
			+ "</font></html>");
		sub.setFont(Fonts.SM);
		sub.setBorder(new EmptyBorder(0, 14, 12, 14));
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton upgrade = pillButton("Unlock with Premium");
		upgrade.setBackground(ORANGE);
		upgrade.setForeground(Color.BLACK);
		upgrade.setAlignmentX(Component.LEFT_ALIGNMENT);
		final String href = upgradeUrl != null && !upgradeUrl.isEmpty() ? upgradeUrl : "https://07flip.com/premium";
		upgrade.addActionListener(e -> openUrl(href));

		JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.setBorder(new EmptyBorder(0, 14, 16, 14));
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(upgrade);

		optimizerListPanel.add(title);
		optimizerListPanel.add(sub);
		optimizerListPanel.add(wrap);
		optimizerListPanel.revalidate();
		optimizerListPanel.repaint();
	}

	private void renderOptimizerResult(com.o7flip.model.OptimizeResult r)
	{
		if (optimizerListPanel == null) return;
		optimizerListPanel.removeAll();

		if (r.allocations == null || r.allocations.isEmpty())
		{
			renderOptimizerEmptyFromSummary(r.summary);
			return;
		}

		// Phase 4 — offline-completion notice (the Phase 3 reconcile flag) at the
		// very top so the user sees it before the cards.
		JComponent reconcileBanner = buildOfflineReconcileBanner(r);
		if (reconcileBanner != null)
		{
			optimizerListPanel.add(reconcileBanner);
			optimizerListPanel.add(sep());
		}

		// A3 — "you left slots on the table" suggestion banner. (The Plan summary
		// block is no longer shown.)
		JComponent slotSuggestion = buildSlotSuggestionBanner(r.summary);
		if (slotSuggestion != null)
		{
			optimizerListPanel.add(slotSuggestion);
			optimizerListPanel.add(sep());
		}

		for (int i = 0; i < r.allocations.size(); i++)
		{
			final int idx = i;
			Runnable swap = () ->
			{
				if (plugin != null && lastOptimize != null)
				{
					plugin.swapPlanSlot(idx, lastOptimize);
				}
			};
			optimizerListPanel.add(new com.o7flip.ui.OptimizerAllocationCard(
				r.allocations.get(i), itemManager, i % 2 != 0, plugin, swap, i));
			optimizerListPanel.add(sep());
		}

		optimizerListPanel.revalidate();
		optimizerListPanel.repaint();
	}

	/**
	 * Empty-plan state driven by the server's {@code empty_reason} /
	 * {@code degraded_trend_data} (Task A5) rather than a generic message.
	 */
	private void renderOptimizerEmptyFromSummary(com.o7flip.model.OptimizeResult.Summary s)
	{
		String title = "No allocations possible";
		String sub;
		if (s != null && s.emptyReason != null && !s.emptyReason.isEmpty())
		{
			sub = s.emptyReason;
		}
		else
		{
			sub = "Try widening the risk to 'High', or lengthening the fill window.";
		}
		if (s != null && s.degradedTrendData)
		{
			sub += " (Trend data was limited this run — results may be conservative.)";
		}
		optimizerListPanel.add(emptyLabel(title, sub));
		optimizerListPanel.revalidate();
		optimizerListPanel.repaint();
	}

	/**
	 * A3 — banner shown when the server says deploying more slots would help
	 * and the user requested fewer than 8. One click re-runs the optimiser at
	 * the suggested slot count. Returns null when there's nothing to suggest.
	 */
	private JComponent buildSlotSuggestionBanner(com.o7flip.model.OptimizeResult.Summary s)
	{
		if (s == null || s.slotSuggestion == null) return null;
		final int suggested = s.slotSuggestion.suggestedSlots;
		// Never suggest "more slots" at the 8-slot ceiling, and only when the
		// suggestion is actually larger than what was requested.
		if (suggested <= s.slotsRequested || s.slotsRequested >= 8 || suggested > 8) return null;

		JPanel p = new JPanel(new BorderLayout(8, 0));
		p.setBackground(new Color(0x1E2E22));
		p.setBorder(new EmptyBorder(8, 12, 8, 12));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);

		String body = "<html><font color='#00C27A'>● </font>"
			+ "<font color='#FFFFFF'>Using " + suggested + " slots</font>"
			+ "<font color='#888888'> would deploy ~</font>"
			+ "<font color='#FFE07A'>" + FlipItemPanel.formatGpCompact(s.slotSuggestion.additionalCapitalDeployed) + "</font>"
			+ "<font color='#888888'> more</font>"
			+ (s.slotSuggestion.additionalExpectedProfit > 0
				? " <font color='#888888'>(≈</font><font color='#00C27A'>+"
					+ FlipItemPanel.formatGpCompact(s.slotSuggestion.additionalExpectedProfit)
					+ "</font><font color='#888888'> profit)</font>"
				: "")
			+ "</html>";
		JLabel label = new JLabel(body);
		label.setFont(Fonts.SM);

		JButton apply = pillButton("Use " + suggested);
		apply.setBackground(ORANGE);
		apply.setForeground(Color.BLACK);
		apply.setToolTipText("Re-run the optimiser with " + suggested + " slots");
		apply.addActionListener(e ->
		{
			if (plugin == null || optInFlight) return;
			optSlots = suggested;
			optInFlight = true;
			optShowingHistory = false;
			renderOptimizerLoading();
			plugin.rerunWithSlots(suggested);
		});

		p.add(label, BorderLayout.CENTER);
		p.add(apply, BorderLayout.EAST);
		return p;
	}

	/**
	 * Phase 4 — surfaces the Phase 3 offline-completion flag
	 * ({@code Allocation.pendingOfflineReconcile}) as a non-destructive banner with
	 * a Dismiss. Returns null when no leg is flagged. Mirrors
	 * {@link #buildSlotSuggestionBanner}.
	 */
	private JComponent buildOfflineReconcileBanner(com.o7flip.model.OptimizeResult r)
	{
		if (r == null || r.allocations == null) return null;
		java.util.List<com.o7flip.model.OptimizeResult.Allocation> flagged = new java.util.ArrayList<>();
		for (com.o7flip.model.OptimizeResult.Allocation a : r.allocations)
		{
			if (a != null && a.pendingOfflineReconcile) flagged.add(a);
		}
		if (flagged.isEmpty()) return null;

		JPanel p = new JPanel(new BorderLayout(8, 0));
		p.setBackground(new Color(0x2E2A1E));
		p.setBorder(new EmptyBorder(8, 12, 8, 12));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);

		String body = flagged.size() == 1
			? "<html><font color='#FF981F'>⚠ </font><font color='#FFFFFF'>"
				+ (flagged.get(0).name != null ? flagged.get(0).name : "A sell")
				+ "</font><font color='#888888'> looks complete after being offline — confirm on 07flip.com.</font></html>"
			: "<html><font color='#FF981F'>⚠ </font><font color='#FFFFFF'>" + flagged.size()
				+ " sells</font><font color='#888888'> look complete after being offline — confirm on 07flip.com.</font></html>";
		JLabel label = new JLabel(body);
		label.setFont(Fonts.SM);

		JButton dismiss = pillButton("Dismiss");
		dismiss.setBackground(new Color(0x2A2A2A));
		dismiss.setForeground(new Color(0xAAAAAA));
		dismiss.setToolTipText("Hide this notice — your recorded fills are unchanged");
		dismiss.addActionListener(e ->
		{
			if (plugin != null)
			{
				for (com.o7flip.model.OptimizeResult.Allocation a : flagged)
				{
					plugin.dismissOfflineReconcile(a.itemId);
				}
			}
		});

		p.add(label, BorderLayout.CENTER);
		p.add(dismiss, BorderLayout.EAST);
		return p;
	}

	// -------------------------------------------------------------------------
	// Completed-positions history (Task D)
	// -------------------------------------------------------------------------

	/** History sort modes for the completed-positions view. */
	private enum HistorySort { RECENT, QUICKEST, MOST_PROFIT }
	private HistorySort optHistorySort = HistorySort.RECENT;

	/** Re-render the active view if the history changed underneath us. */
	public void onCompletedPositionsChanged()
	{
		if (optShowingHistory)
		{
			renderCompletedPositionsHistory();
		}
		else if (lastOptimize != null)
		{
			// Refresh so the footer count stays accurate.
			renderOptimizerResult(lastOptimize);
		}
	}

	private void renderCompletedPositionsHistory()
	{
		if (optimizerListPanel == null) return;
		optimizerListPanel.removeAll();

		java.util.List<com.o7flip.model.CompletedPosition> positions =
			plugin != null ? plugin.getCompletedPositions() : new java.util.ArrayList<>();
		sortHistory(positions);

		// Header: back link + total realised profit.
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBackground(new Color(0x1F1F1F));
		header.setBorder(new EmptyBorder(10, 12, 10, 12));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton back = pillButton("← Back to plan");
		back.setBackground(new Color(0x2A2A2A));
		back.setForeground(new Color(0xAAAAAA));
		back.addActionListener(e ->
		{
			optShowingHistory = false;
			if (lastOptimize != null) renderOptimizerResult(lastOptimize);
			else renderOptimizerEmpty();
		});
		header.add(back, BorderLayout.WEST);

		long total = plugin != null ? plugin.getCompletedProfitTotal() : 0L;
		String totColor = total >= 0 ? "#00C27A" : "#E85050";
		JLabel totLbl = new JLabel("<html><font color='#888888'>Realised: </font>"
			+ "<font color='" + totColor + "'><b>" + (total >= 0 ? "+" : "")
			+ FlipItemPanel.formatGpCompact(total) + "</b></font></html>");
		totLbl.setFont(Fonts.BOLD);
		header.add(totLbl, BorderLayout.EAST);
		optimizerListPanel.add(header);

		// Sort pills.
		JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		sortRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		sortRow.add(buildHistorySortPill("Recent",  HistorySort.RECENT));
		sortRow.add(buildHistorySortPill("Quickest", HistorySort.QUICKEST));
		sortRow.add(buildHistorySortPill("Profit",   HistorySort.MOST_PROFIT));
		optimizerListPanel.add(sortRow);
		optimizerListPanel.add(sep());

		if (positions.isEmpty())
		{
			optimizerListPanel.add(emptyLabel("No completed positions yet",
				"When an Optimiser slot fully buys and sells, it lands here with its realised profit."));
		}
		else
		{
			boolean odd = false;
			for (com.o7flip.model.CompletedPosition cp : positions)
			{
				optimizerListPanel.add(buildCompletedPositionRow(cp, odd));
				optimizerListPanel.add(sep());
				odd = !odd;
			}
		}

		optimizerListPanel.revalidate();
		optimizerListPanel.repaint();
	}

	private JButton buildHistorySortPill(String label, HistorySort mode)
	{
		JButton b = pillButton(label);
		applySortStyle(b, optHistorySort == mode);
		b.addActionListener(e ->
		{
			optHistorySort = mode;
			renderCompletedPositionsHistory();
		});
		return b;
	}

	private void sortHistory(java.util.List<com.o7flip.model.CompletedPosition> list)
	{
		switch (optHistorySort)
		{
			case QUICKEST:
				// Fastest fills first; unknown (null) fill times sink to the bottom.
				list.sort((a, b) ->
				{
					double da = a.fillHours != null ? a.fillHours : Double.MAX_VALUE;
					double db = b.fillHours != null ? b.fillHours : Double.MAX_VALUE;
					return Double.compare(da, db);
				});
				break;
			case MOST_PROFIT:
				list.sort((a, b) -> Long.compare(b.profit, a.profit));
				break;
			case RECENT:
			default:
				// Already newest-first from the store; keep it stable.
				break;
		}
	}

	private JComponent buildCompletedPositionRow(com.o7flip.model.CompletedPosition cp, boolean odd)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(odd ? new Color(0x272727) : ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(8, 10, 8, 10));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = FlipItemPanel.buildIcon(cp.itemId, itemManager);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		StringBuilder name = new StringBuilder("<html><font color='#FFFFFF'><b>")
			.append(escapeHtml(cp.name)).append("</b></font>");
		if (cp.partial)
		{
			name.append(" <font color='#FFC077'>· partial</font>");
		}
		name.append("</html>");
		JLabel nameLbl = new JLabel(name.toString());
		nameLbl.setFont(Fonts.SM);
		nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

		String profitColor = cp.profit >= 0 ? "#00C27A" : "#E85050";
		StringBuilder sub = new StringBuilder("<html>")
			.append("<font color='").append(profitColor).append("'><b>")
			.append(cp.profit >= 0 ? "+" : "").append(FlipItemPanel.formatGpCompact(cp.profit))
			.append("</b></font><font color='#888888'> · ")
			.append(formatNumberLocal(cp.qty)).append(" @ ")
			.append(FlipItemPanel.formatGpCompact(cp.qty > 0 ? cp.buyGp / cp.qty : 0))
			.append("</font>");
		if (cp.fillHours != null)
		{
			sub.append("<font color='#888888'> · ").append(formatHoursLocal(cp.fillHours)).append("</font>");
		}
		sub.append("</html>");
		JLabel subLbl = new JLabel(sub.toString());
		subLbl.setFont(Fonts.SM);
		subLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

		text.add(nameLbl);
		text.add(Box.createVerticalStrut(2));
		text.add(subLbl);

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static String formatNumberLocal(long n)
	{
		return String.format("%,d", n);
	}

	private static String formatHoursLocal(double h)
	{
		if (h < 1.0)
		{
			long mins = Math.round(h * 60);
			return Math.max(1, mins) + "m fill";
		}
		String s = String.format("%.1f", h);
		if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
		return s + "h fill";
	}

	private JPanel buildScreenersTab()
	{
		// ── List view ────────────────────────────────────────────────────────
		screenersListPanel = listPanel();
		JPanel listView = assembleTab(null, screenersListPanel, null);

		// ── Detail view ──────────────────────────────────────────────────────
		screenersDetailPanel = listPanel();
		screenersDetailTitle = new JLabel("");
		screenersDetailTitle.setFont(Fonts.BOLD);
		screenersDetailTitle.setForeground(Color.WHITE);

		JButton backBtn = pillButton("← Back");
		backBtn.setBackground(new Color(0x3E3E3E));
		backBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		backBtn.addActionListener(e ->
		{
			activeScreenerPreset = null;
			renderScreenersList();
		});

		JPanel detailHeader = new JPanel(new BorderLayout(8, 0));
		detailHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailHeader.setBorder(new EmptyBorder(6, 8, 6, 8));
		detailHeader.add(backBtn,              BorderLayout.WEST);
		detailHeader.add(screenersDetailTitle, BorderLayout.CENTER);

		ListWrapperPanel detailWrapper = new ListWrapperPanel(screenersDetailPanel);
		detailWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JScrollPane detailSp = new JScrollPane(detailWrapper);
		detailSp.setBorder(BorderFactory.createEmptyBorder());
		detailSp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		detailSp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		detailSp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));

		JPanel detailView = new JPanel(new BorderLayout());
		detailView.setBackground(ColorScheme.DARK_GRAY_COLOR);
		detailView.add(detailHeader, BorderLayout.NORTH);
		detailView.add(detailSp,     BorderLayout.CENTER);

		// ── CardLayout shell ─────────────────────────────────────────────────
		screenersTabCard = new JPanel(new CardLayout());
		screenersTabCard.add(listView,   "list");
		screenersTabCard.add(detailView, "detail");
		return screenersTabCard;
	}

	/**
	 * Returns the tab index that sits at the TOP-LEFT of the rendered tab
	 * strip — i.e. the visually-first sub-tab the user sees. With our
	 * {@code shouldRotateTabRuns=false} pinning, first-inserted tabs land
	 * on the BOTTOM run (closest to content) and later tabs on rows above.
	 * The top-left position is therefore the first tab of the LAST run.
	 *
	 * For n ≤ perRow → all on one row → top-left = index 0.
	 * Otherwise → the partial-bottom-row holds the first (n mod perRow)
	 * tabs (or all perRow if it divides evenly), so the top row's first
	 * tab is at index (bottomRowSize).
	 *
	 * Examples (with perRow = 3): n=6 → 3+3 → top-left index 3.
	 * (with perRow = 4): n=8 → 4+4 → top-left index 4.
	 */
	private int topLeftSubTabIndex(int n)
	{
		if (n <= 0) return 0;
		int perRow;
		if (n <= 4)      perRow = n;
		else if (n <= 6) perRow = 3;
		else if (n <= 8) perRow = 4;
		else             perRow = (int) Math.ceil(n / 2.0);
		if (n <= perRow) return 0;
		int bottomRowSize = n % perRow;
		if (bottomRowSize == 0) bottomRowSize = perRow;
		return bottomRowSize;
	}

	/**
	 * The Other tab — a nested {@link JTabbedPane} hosting any feature from
	 * {@link #MOVABLE_POOL} that the user didn't promote to Row 1. Auto-
	 * populates based on the user's top-row selection: swap Dumps in to
	 * Row 1 and it disappears from Other; swap it out and it reappears.
	 *
	 * Six per-feature content panels are passed in (we can't conditionally
	 * skip building them because list-panel fields like {@code dipsListPanel}
	 * are read by render methods even when the tab isn't visible).
	 */
	private JPanel buildOtherTab(JPanel decantContent, JPanel dipsContent,
	                             JPanel highAlchContent, JPanel tabletsContent,
	                             JPanel favouritesContent, JPanel screenersContent,
	                             JPanel planContent, JPanel newsContent,
	                             JPanel moonContent, JPanel barrowsContent,
	                             JPanel dumpsContent, JPanel alertsContent)
	{
		JTabbedPane inner = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
		inner.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inner.setForeground(Color.WHITE);
		inner.setFont(Fonts.SM);
		// Other's inner pane has up to 6 sub-tabs — same row-rotation concern
		// applies. Pin the order so the user sees consistent layout regardless
		// of which sub-tab is selected.
		applyStaticOrderUI(inner);

		// Map every movable name → its content panel. We add a tab to Other
		// only when (a) the feature isn't in Row 1, and (b) the user's
		// visibility toggle for it is on.
		java.util.Map<String, JPanel> byName = new java.util.HashMap<>();
		byName.put("Decant",  decantContent);
		byName.put("Dips",    dipsContent);
		byName.put("Alch",    highAlchContent);
		byName.put("Tablets", tabletsContent);
		byName.put("Favs",    favouritesContent);
		byName.put("Screener",screenersContent);
		byName.put("Plan",    planContent);
		byName.put("News",    newsContent);
		byName.put("Moons",   moonContent);
		byName.put("Barrows", barrowsContent);
		byName.put("Dumps",   dumpsContent);
		byName.put("Alerts",  alertsContent);

		java.util.Set<String> topRow = new java.util.HashSet<>(resolveTopRow());
		for (String name : MOVABLE_POOL)
		{
			if (topRow.contains(name)) continue;
			if (!subFeatureEnabled(name)) continue;
			JPanel content = byName.get(name);
			if (content == null) continue;
			inner.addTab(name, content);
		}

		// Default selection: if the user has a saved sub-tab choice, restore
		// it; otherwise pick the visually-first tab (top-left of the strip)
		// rather than index 0 (which is whichever sub-tab landed in
		// MOVABLE_POOL first and ends up on the bottom run with our static
		// run-order pinning). Done BEFORE attaching the change listener so
		// the restore doesn't burn a fetch slot via onOtherSubTabSelected.
		if (inner.getTabCount() > 0)
		{
			int targetIdx = -1;
			if (lastOtherSubTab != null)
			{
				for (int i = 0; i < inner.getTabCount(); i++)
				{
					if (lastOtherSubTab.equals(inner.getTitleAt(i)))
					{
						targetIdx = i;
						break;
					}
				}
			}
			if (targetIdx < 0)
			{
				targetIdx = topLeftSubTabIndex(inner.getTabCount());
			}
			inner.setSelectedIndex(targetIdx);
		}

		// Tab-select hook — fetches fresh data for the sub-tab the user just
		// opened, but only when the data is older than the throttle window
		// (handled plugin-side). Screeners and Favs keep their dedicated
		// handlers (Screeners has its own 2-min poll floor; Favs requires
		// the auth check). Everyone else routes through the generic helper
		// which enforces a 30-second floor per sub-tab.
		inner.addChangeListener(e ->
		{
			int idx = inner.getSelectedIndex();
			if (idx < 0 || plugin == null) return;
			String title = inner.getTitleAt(idx);
			// If we're leaving the Plan tab (the previous sub-tab was Plan
			// and the new one isn't), stop the 30s session-poll loop.
			if ("Plan".equals(lastOtherSubTab) && !"Plan".equals(title))
			{
				plugin.onPlanTabDeselected();
			}
			lastOtherSubTab = title;
			switch (title)
			{
				case "Screener":
					plugin.onScreenersTabSelected();
					return;
				case "Favs":
					plugin.onFavouritesTabSelected();
					return;
				case "Plan":
					plugin.onPlanTabSelected();
					return;
				default:
					plugin.onOtherSubTabSelected(title);
			}
		});

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.add(inner, BorderLayout.CENTER);
		return wrap;
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
