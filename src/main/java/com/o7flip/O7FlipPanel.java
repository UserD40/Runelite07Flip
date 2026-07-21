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

import com.o7flip.model.DecantItem;
import com.o7flip.model.DumpItem;
import com.o7flip.model.FlipItem;
import com.o7flip.model.SearchResultItem;
import com.o7flip.model.TradeRecord;
import com.o7flip.ui.DecantItemPanel;
import com.o7flip.ui.DipItemPanel;
import com.o7flip.ui.DumpItemPanel;
import com.o7flip.ui.FlipItemPanel;
import com.o7flip.ui.SearchResultPanel;
import com.o7flip.ui.TradeRecordPanel;
import com.o7flip.ui.VectorIcon;
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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.Box;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class O7FlipPanel extends PluginPanel
{
	private static final String WEBSITE_URL  = "https://07flip.com";
	private static final String DISCORD_URL  = "https://discord.gg/xQaYM9TaMr";
	private static final String RUNELITE_URL  = "https://07flip.com/runelite";
	private static final String SUBSCRIBE_URL = "https://07flip.com/subscribe";
	private static final String ACCOUNT_URL   = "https://07flip.com/account";

	private static final Color CARD_GOLD    = new Color(0xC4A052);
	private static final Color CARD_GOLD_BG = new Color(0x2A2418);
	private static final Color  ORANGE       = new Color(0xFF981F);
	private static final Color  GREEN        = new Color(0x00C27A);
	private static final int    PAGE_SIZE    = 10;
	private static final int    FREE_ROWS    = 5;

	private static final String[][] PRESETS = {
		{"",                 "All Flips"},
		{"starterFlips",     "Starter"},
		{"highMargin",       "High Margin"},
		{"f2p",              "F2P Only"},
		{"priceDip",         "Price Dip"},
		{"stableFlips",      "Stable"},
		{"highVolume",       "High Volume"},
		{"volumeSpike",      "Volume Spike"},
		{"oversoldDip",      "Oversold"},
		{"momentumRecovery", "Momentum"},
		{"lowVolatility",    "Low Volatility"},
		{"bandFlip",         "Bulk Margin"},
	};
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

	private static final long[]   MIN_PROFITS       = {0, 100_000, 500_000, 1_000_000};
	private static final String[] MIN_PROFIT_LABELS = {"Any profit", "100K+", "500K+", "1M+"};

	private static final long[][] CAPITAL_RANGES = {
		{0,            Long.MAX_VALUE},
		{0,            100_000},
		{100_000,      1_000_000},
		{1_000_000,    10_000_000},
		{10_000_000,   Long.MAX_VALUE},
	};
	private static final String[] CAPITAL_LABELS = {
		"Any capital",
		"Under 100K",
		"100K – 1M",
		"1M – 10M",
		"10M+",
	};

	private static final long[]   DUMP_MIN_PROFITS       = {0, 1_000, 5_000, 25_000, 100_000};
	private static final String[] DUMP_MIN_PROFIT_LABELS = {"Any Profit", "1K+", "5K+", "25K+", "100K+"};

	private static final long[][] PRICE_RANGES = {
		{0,               Long.MAX_VALUE},
		{0,               10_000},
		{10_000,          50_000},
		{50_000,          100_000},
		{100_000,         500_000},
		{500_000,         1_000_000},
		{1_000_000,       5_000_000},
		{5_000_000,       10_000_000},
		{10_000_000,      25_000_000},
		{25_000_000,      50_000_000},
		{50_000_000,      100_000_000},
		{100_000_000,     Long.MAX_VALUE},
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
	private int flipsCategoryIdx   = 0;
	private int flipsMinHourlyVolIdx = 0;
	private int flipsMinBuyPriceIdx  = 2;
	private boolean flipsTaxFreeOnly = false;

	private static final int[]    FLIPS_MIN_HOURLY_VOL    = {0, 100, 500, 1_000, 5_000, 10_000};
	private static final String[] FLIPS_MIN_VOL_LABELS    = {"Any volume", "100+/hr", "500+/hr", "1K+/hr", "5K+/hr", "10K+/hr"};
	private static final long[]   FLIPS_MIN_BUY_PRICE     = {0, 10, 100, 1_000, 10_000, 100_000};
	private static final String[] FLIPS_MIN_BUY_PRICE_LABELS = {"Any price", "10+", "100+", "1K+", "10K+", "100K+"};
	private int dumpsMinProfitIdx  = 0;
	private int dumpsPriceRangeIdx = 0;

	private String dumpsSortKey  = "max_profit";
	private String dipsSortKey   = "recent";
	private String dipsActivityWindow = "1d";
	private boolean dumpsUseBotEndpoint = false;

	private int     dumpsMinScore   = 0;
	private boolean dumpsActiveOnly = true;
	private String  dumpsTier       = "all";
	private int     dumpsConfirmedTotal = 0;
	private int     dumpsLikelyTotal    = 0;

	private boolean isSignedIn = false;
	private boolean isPremium  = false;

	public boolean isSignedIn() { return isSignedIn; }
	public boolean isPremium()  { return isPremium;  }
	private boolean authChecked = false;

	@Override
	public void onActivate()
	{
		if (plugin != null)
		{
			plugin.onPanelShown();
		}
	}

	@Override
	public void onDeactivate()
	{
		if (plugin != null)
		{
			plugin.onPanelHidden();
		}
	}

	public boolean isPlanTabActive()
	{
		return "Plan".equals(currentTabName());
	}

	private List<FlipItem>    allFlips   = new ArrayList<>();
	private List<DumpItem>    allDumps   = new ArrayList<>();
	private List<com.o7flip.model.DipItem> allDips = new ArrayList<>();
	private List<DecantItem>  allDecants = new ArrayList<>();
	private List<FlipItem>    allFavourites = new ArrayList<>();
	private JButton[] favouritesSortBtns;
	private int favouritesSortIdx = 1;
	private List<TradeRecord> allMyFlips = new ArrayList<>();

	private int flipsSortIdx   = 0;
	private int dumpsSortIdx   = 0;
	private int myFlipsSortIdx = 0;
	private int myFlipsPage    = 0;
	private static final int MY_FLIPS_PAGE_SIZE = 5;
	private int myFlipsMarginSortIdx = 0;
	private int myFlipsRecentSortIdx = 0;
	private com.o7flip.ui.MyTradesStatsPanel.Period myFlipsPeriod =
		com.o7flip.ui.MyTradesStatsPanel.Period.DAILY;

	private int flipsPage   = 0;  private int flipsTotal  = 0;
	private int dumpsPage   = 0;  private int dumpsTotal  = 0;
	private int dipsPage    = 0;  private int dipsTotal   = 0;
	private int decantPage  = 0;

	private JPanel flipsListPanel;
	private JPanel dumpsListPanel;
	private JPanel dipsListPanel;
	private JPanel decantListPanel;
	private JPanel favouritesListPanel;
	private JPanel optimizerListPanel;
	private com.o7flip.model.OptimizeResult lastOptimize;
	private int     optSlots         = 8;
	private String  optRisk          = "medium";
	private int     optMaxFillHours  = 4;
	private Boolean optMembers       = null;
	private int     optMinProfitPct  = 0;
	private boolean optShowingHistory;
	private boolean optInFlight;
	private boolean optFormCollapsed;
	private JPanel  optCollapsedPanel;
	private JPanel  optInputsHost;
	private String lastOtherSubTab;
	private JPanel myFlipsListPanel;
	private javax.swing.Timer activeColorTimer;
	private com.o7flip.ui.MyTradesStatsPanel myFlipsStatsPanel;
	private com.o7flip.ui.InsightsPanel insightsPanel;
	private JPanel searchResultsPanel;
	private JScrollPane searchScrollPane;

	private JButton[] dumpsSortBtns;
	private JButton[] dumpsTierBtns;
	private JPanel    dumpsTierBar;
	private JButton[] dipsSortBtns;
	private JButton[] decantSortBtns;
	private JButton[] myFlipsSortBtns;
	private JButton[] myFlipsMarginSortBtns;
	private JButton[] myFlipsRecentSortBtns;
	private JPanel    myFlipsMarginSortBar;
	private JPanel    myFlipsRecentSortBar;
	private JButton   myFlipsPeriodButton;

	private JLabel  flipsPageLabel;   private JButton flipsPrev,    flipsNext;
	private JLabel  dumpsPageLabel;   private JButton dumpsPrev,    dumpsNext;
	private JLabel  dipsPageLabel;    private JButton dipsPrev,     dipsNext;
	private int     dipsSortIdx = 0;
	private JLabel  decantPageLabel;  private JButton decantPrev,   decantNext;
	private int     decantSortIdx = 0;

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

	@Inject
	private O7FlipPlugin plugin;
	@Inject
	private ItemManager itemManager;
	@Inject
	private O7FlipConfig config;

	private JPanel tabsWrapper;

	private JPanel authBanner;

	private JPanel invalidKeyBar;

	private JPanel northArea;

	public O7FlipPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

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

	public void updateAuthStatus(boolean signedIn, boolean premium)
	{
		boolean wasChecked = this.authChecked;
		boolean changed = (signedIn != this.isSignedIn) || (premium != this.isPremium);
		this.isSignedIn  = signedIn;
		this.isPremium   = premium;
		this.authChecked = true;
		updateAuthBanner();
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

		String key = config.apiKey();
		boolean noKey = key == null || key.trim().isEmpty();

		if (!noKey)
		{
			if (!authChecked)
			{
				authBanner.setVisible(false);
				authBanner.revalidate();
				authBanner.repaint();
				northArea.revalidate();
				northArea.repaint();
				return;
			}

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

	public String getSelectedPreset()
	{
		if (flipsF2pOnly) return "f2p";
		int idx = Math.max(0, Math.min(flipsCategoryIdx, PRESETS.length - 1));
		return PRESETS[idx][0];
	}

	public String getFlipsSortKey()
	{
		if ("bandFlip".equals(getSelectedPreset())) return "bandProfit";
		int idx = Math.max(0, Math.min(flipsSortIdx, FLIPS_SORTS.length - 1));
		return FLIPS_SORTS[idx][0];
	}

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
		if (flipsTaxFreeOnly) return 0;
		long capitalFloor = flipsCapitalIdx > 0 ? CAPITAL_RANGES[flipsCapitalIdx][0] : 0;
		long qualityFloor = FLIPS_MIN_BUY_PRICE[Math.max(0, Math.min(flipsMinBuyPriceIdx, FLIPS_MIN_BUY_PRICE.length - 1))];
		return Math.max(capitalFloor, qualityFloor);
	}

	public long getFlipsPriceMax()
	{
		if (flipsTaxFreeOnly) return 49L;
		return flipsCapitalIdx > 0 ? CAPITAL_RANGES[flipsCapitalIdx][1] : Long.MAX_VALUE;
	}

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
		pushInsightsRecommendations();
		updateTimestamp();
		setLoading(false);
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

	public void updateMyFlips(List<TradeRecord> records)
	{
		allMyFlips = new ArrayList<>(records);
		renderMyFlips();
	}

	public void updateDips(List<com.o7flip.model.DipItem> items, int total, int page)
	{
		allDips = items;
		dipsTotal = total;
		dipsPage = page;
		renderDips(filtered());
	}

	public void updateDecanting(List<DecantItem> items)
	{
		allDecants = items != null ? items : new ArrayList<>();
		decantPage = 0;
		renderDecants(filtered());
	}

	public void rerenderDecants()
	{
		renderDecants(filtered());
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

	public void updateFavourites(List<FlipItem> items)
	{
		allFavourites = items;
		renderFavourites(filtered());
		if (insightsPanel != null)
		{
			insightsPanel.refreshFavouriteState();
		}
	}

	public void addFavouriteRow(FlipItem item)
	{
		if (item == null || item.itemId <= 0)
		{
			return;
		}
		for (FlipItem f : allFavourites)
		{
			if (f.itemId == item.itemId)
			{
				return;
			}
		}
		List<FlipItem> updated = new ArrayList<>();
		updated.add(item);
		updated.addAll(allFavourites);
		allFavourites = updated;
		renderFavourites(filtered());
	}

	public void removeFavouriteRow(int itemId)
	{
		List<FlipItem> updated = new ArrayList<>();
		boolean found = false;
		for (FlipItem f : allFavourites)
		{
			if (f.itemId == itemId)
			{
				found = true;
				continue;
			}
			updated.add(f);
		}
		if (!found)
		{
			return;
		}
		allFavourites = updated;
		renderFavourites(filtered());
	}

	public void onFavouriteToggled(int itemId)
	{
		if (insightsPanel != null)
		{
			insightsPanel.refreshFavouriteState();
		}
	}

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
			renderDumps("");
			renderDips("");
			renderDecants("");
			renderFavourites("");
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

	private boolean notBlocked(int itemId)
	{
		return plugin == null || !plugin.isBlocked(itemId);
	}

	private long capitalCeiling()
	{
		return plugin != null ? plugin.capitalFilterCeiling() : 0L;
	}

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
			.filter(i -> minVol <= 0 || i.hourlyVolume == null || i.hourlyVolume >= minVol)
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

	private List<DecantItem> fDecants(String q)
	{
		return q.isEmpty() ? allDecants : allDecants.stream().filter(i -> matches(i.potionName, q)).collect(Collectors.toList());
	}

	private List<DecantItem> sortDecants(List<DecantItem> items)
	{
		java.util.Comparator<DecantItem> c = decantSortIdx == 1 ? java.util.Comparator.comparingDouble((DecantItem x) -> x.roiPct)
			: decantSortIdx == 2 ? java.util.Comparator.comparingInt((DecantItem x) -> x.dailyVolume)
			: java.util.Comparator.comparingLong((DecantItem x) -> x.profitPer4dose);
		return items.stream().sorted(c.reversed()).collect(Collectors.toList());
	}

	private List<FlipItem> fFavourites(String q)
	{
		List<FlipItem> list = allFavourites.stream()
			.filter(i -> q.isEmpty() || matches(i.name, q))
			.collect(Collectors.toList());
		sortFavourites(list);
		return list;
	}

	private void sortFavourites(List<FlipItem> list)
	{
		switch (favouritesSortIdx)
		{
			case 1:
				list.sort((a, b) -> Long.compare(b.profit, a.profit));
				break;
			default:
				applyManualOrder(list);
				break;
		}
	}

	private void applyManualOrder(List<FlipItem> list)
	{
		if (plugin == null)
		{
			return;
		}
		List<Integer> order = plugin.getFavouritesOrder();
		if (order.isEmpty())
		{
			return;
		}
		java.util.Map<Integer, Integer> rank = new java.util.HashMap<>();
		for (int i = 0; i < order.size(); i++)
		{
			rank.put(order.get(i), i);
		}
		list.sort((a, b) -> Integer.compare(
			rank.getOrDefault(a.itemId, Integer.MAX_VALUE),
			rank.getOrDefault(b.itemId, Integer.MAX_VALUE)));
	}

	private List<FlipItem> favouritesForReorder()
	{
		List<FlipItem> list = new ArrayList<>(allFavourites);
		applyManualOrder(list);
		return list;
	}

	private void applyFavouritesOrder(List<Integer> keptIds)
	{
		if (keptIds == null)
		{
			return;
		}
		java.util.Set<Integer> keptSet = new java.util.HashSet<>(keptIds);
		if (plugin != null)
		{
			for (FlipItem f : new ArrayList<>(allFavourites))
			{
				if (!keptSet.contains(f.itemId))
				{
					plugin.unfavouriteForReorder(f.itemId);
				}
			}
			plugin.setFavouritesOrder(keptIds);
		}
		List<FlipItem> kept = new ArrayList<>();
		for (FlipItem f : allFavourites)
		{
			if (keptSet.contains(f.itemId))
			{
				kept.add(f);
			}
		}
		allFavourites = kept;
		favouritesSortIdx = 0;
		if (favouritesSortBtns != null)
		{
			for (int i = 0; i < favouritesSortBtns.length; i++)
			{
				applySortStyle(favouritesSortBtns[i], i == 0);
			}
		}
		renderFavourites(filtered());
		if (insightsPanel != null)
		{
			insightsPanel.refreshFavouriteState();
		}
	}

	private List<DumpItem> sortDumps(List<DumpItem> items)
	{
		return items;
	}

	private void renderFlips(String q)
	{
		fillListPaged(flipsListPanel, fFlips(q), flipsPage, flipsTotal,
			flipsPageLabel, flipsPrev, flipsNext,
			(item, odd) -> new FlipItemPanel(item, itemManager, odd, plugin),
			"No flips found", "Try a different preset or filter");
	}

	private void renderDumps(String q)
	{
		if (dumpsListPanel == null)
		{
			return;
		}
		hiliteFilter(dumpsSortBtns, dumpsSortIdx);

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

	private void renderDecants(String q)
	{
		if (decantListPanel == null)
		{
			return;
		}
		List<DecantItem> all = sortDecants(fDecants(q));
		int total = all.size();
		int ps    = isSignedIn ? PAGE_SIZE : FREE_ROWS;
		int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
		int safe  = isSignedIn ? Math.max(0, Math.min(decantPage, pages - 1)) : 0;
		decantPage = safe;
		int start = safe * PAGE_SIZE;
		int end   = Math.min(start + ps, total);
		List<DecantItem> window = start < end ? all.subList(start, end) : new ArrayList<>();
		fillListPaged(decantListPanel, window, decantPage, total,
			decantPageLabel, decantPrev, decantNext,
			(item, odd) -> new DecantItemPanel(item, itemManager, odd, plugin),
			"No decanting opportunities", "");
		hiliteFilter(decantSortBtns, decantSortIdx);
	}

	private void renderFavourites(String q)
	{
		if (favouritesListPanel == null)
		{
			return;
		}
		favouritesListPanel.removeAll();
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
				favouritesListPanel.add(emptyLabel(allFavourites.size() + " favourites hidden by search",
					"Clear the search box to see your full favourites list."));
			}
			else if (shown.isEmpty())
			{
				favouritesListPanel.add(emptyLabel("No favourites yet",
					"Tap the star on any item's Insights tab to add it here."));
			}
			else
			{
				for (int i = 0; i < shown.size(); i++)
				{
					FlipItem item = shown.get(i);
					favouritesListPanel.add(new FlipItemPanel(item, itemManager, i % 2 != 0, plugin, true));
					favouritesListPanel.add(sep());
				}
			}
		}
		favouritesListPanel.revalidate();
		favouritesListPanel.repaint();
	}

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
		updateActiveColorTimer();
	}

	private void updateActiveColorTimer()
	{
		boolean want = myFlipsSortIdx == 0 && myFlipsListPanel != null;
		if (want)
		{
			if (activeColorTimer == null)
			{
				activeColorTimer = new javax.swing.Timer(5000, e ->
				{
					if (myFlipsSortIdx == 0 && myFlipsListPanel != null && myFlipsListPanel.isShowing())
					{
						myFlipsListPanel.repaint();
					}
					else if (activeColorTimer != null)
					{
						activeColorTimer.stop();
					}
				});
				activeColorTimer.setRepeats(true);
			}
			if (!activeColorTimer.isRunning())
			{
				activeColorTimer.start();
			}
		}
		else if (activeColorTimer != null && activeColorTimer.isRunning())
		{
			activeColorTimer.stop();
		}
	}

	private void renderMyFlipsByRecent(com.o7flip.util.ProfitCalculator.Result result)
	{
		Map<Long, Long> profitBySellTimestamp = new HashMap<>();
		for (com.o7flip.util.ProfitCalculator.CompletedFlip f : result.completedFlips)
		{
			if (f.buyTotal <= 0) continue;
			profitBySellTimestamp.merge(f.sellTimestamp, f.profit, Long::sum);
		}
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
			case 1:
				filtered.sort((a, b) ->
				{
					double ra = roiFor(a, profitBySellTimestamp, buyTotalBySellTimestamp);
					double rb = roiFor(b, profitBySellTimestamp, buyTotalBySellTimestamp);
					int cmp = Double.compare(rb, ra);
					if (cmp != 0) return cmp;
					return Long.compare(b.timestamp, a.timestamp);
				});
				break;
			case 2:
				filtered.sort((a, b) ->
				{
					int cmp = Integer.compare(b.quantity, a.quantity);
					if (cmp != 0) return cmp;
					return Long.compare(b.timestamp, a.timestamp);
				});
				break;
			default:
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
			default:
				return Long.MIN_VALUE;
		}
	}

	private static long profitFor(TradeRecord t, Map<Long, Long> profitBySellTimestamp)
	{
		if (t.isBuy) return Long.MIN_VALUE;
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

	public void showSearchResults(List<SearchResultItem> items, String query)
	{
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

	private JPanel buildPremiumGateTab(String title, String sub)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		renderLocked(p, title, sub);
		return p;
	}

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

		capitalHeaderChip = new JLabel();
		VectorIcon.apply(capitalHeaderChip, "✎", VectorIcon.Kind.PENCIL, 13, new Color(0x666666));
		capitalHeaderChip.setFont(Fonts.SM);
		capitalHeaderChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		capitalHeaderChip.setBorder(new EmptyBorder(0, 4, 0, 4));
		updateCapitalHeaderChip();
		capitalHeaderChip.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				capitalExpanded = !capitalExpanded;
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

	private JPanel     capitalContainer;
	private JTextField capitalField;
	private JLabel     capitalReadout;
	private JLabel     capitalHeaderChip;
	private boolean    capitalExpanded;

	private JPanel buildCapitalRow()
	{
		capitalContainer = new JPanel(new BorderLayout());
		capitalContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		capitalContainer.setBorder(new EmptyBorder(0, 12, 6, 12));
		renderCapitalRow();
		return capitalContainer;
	}

	private void renderCapitalRow()
	{
		if (capitalContainer == null) return;
		capitalContainer.removeAll();
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

	private void updateCapitalHeaderChip()
	{
		if (capitalHeaderChip == null) return;
		long value = displayedCapital();
		boolean active = value > 0;
		Color cc = active ? ORANGE : new Color(0x666666);
		capitalHeaderChip.setForeground(cc);
		javax.swing.Icon ic = capitalHeaderChip.getIcon();
		if (ic instanceof VectorIcon)
		{
			((VectorIcon) ic).setColor(cc);
			capitalHeaderChip.repaint();
		}
		capitalHeaderChip.setToolTipText(active
			? "Capital: " + formatCapital(value) + " · click to edit"
			: "Set capital — items filter to what's affordable");
	}

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
				if (!filterOn)
				{
					commitManualCapital();
				}
				plugin.setCapitalFilterEnabled(!filterOn);
			}
			renderCapitalRow();
		});

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
				commitManualCapital();
			}
		});

		JButton saveBtn = pillButton(Fonts.iconOr("💾", "Save"));
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

		JCheckBox pendingToggle = new JCheckBox("Subtract pending buys");
		pendingToggle.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		pendingToggle.setForeground(new Color(0xAAAAAA));
		pendingToggle.setFont(Fonts.SM);
		pendingToggle.setFocusable(false);
		pendingToggle.setEnabled(filterOn);
		pendingToggle.setSelected(config != null && config.narrowByPendingOffers());
		pendingToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		pendingToggle.setBorder(new EmptyBorder(4, 0, 0, 0));
		pendingToggle.setToolTipText("<html>Filter the list by your <b>free</b> capital (total minus gp in "
			+ "pending buy offers) instead of your full total.<br>Place a buy and the list narrows to what's left.</html>");
		pendingToggle.addActionListener(e ->
		{
			if (plugin != null)
			{
				plugin.persistNarrowByPendingOffers(pendingToggle.isSelected());
			}
		});

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
		outer.add(pendingToggle);
		return outer;
	}

	private void saveCapitalAndCollapse()
	{
		commitManualCapital();
		capitalExpanded = false;
		renderCapitalRow();
	}

	private O7FlipConfig.CapitalMode currentCapitalMode()
	{
		return config != null ? config.capitalMode() : O7FlipConfig.CapitalMode.OFF;
	}

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
			capitalField.setText(formatCapital(displayedCapital()));
			return;
		}
		if (plugin != null)
		{
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

	public void onCapitalAutoAdjusted()
	{
		if (capitalField != null && currentCapitalMode() != O7FlipConfig.CapitalMode.OFF)
		{
			capitalField.setText(formatCapital(displayedCapital()));
		}
		updateCapitalReadout();
		if (!capitalExpanded)
		{
			renderCapitalRow();
		}
		rerenderCapitalAffectedTabs();
	}

	public void rerenderCapitalAffectedTabs()
	{
		String q = filtered();
		renderDumps(q);
		renderDips(q);
		renderFavourites(q);
	}

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
		html.append("</html>");
		capitalReadout.setText(html.toString());
	}

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
			case "Other":     return otherTabHasContent();
			case "Favs":      return config.showFavourites() && hasApiKey();
			case "Plan":      return hasApiKey();   // optimizer is premium-only; signing in is the gate
			default:          return false;
		}
	}

	private boolean otherTabHasContent()
	{
		if (config == null) return false;
		for (String name : OTHER_SUB_TABS)
		{
			if (subFeatureEnabled(name)) return true;
		}
		return false;
	}

	public static final List<String> OTHER_SUB_TABS = java.util.Arrays.asList(
		"Dips", "Dumps", "Decant"
	);

	public static final List<String> MAIN_TAB_ORDER = java.util.Arrays.asList(
		"Flips", "Trades", "Other", "Plan", "Item", "Favs"
	);

	private boolean hasApiKey()
	{
		return config != null && config.apiKey() != null && !config.apiKey().trim().isEmpty();
	}

	private boolean subFeatureEnabled(String name)
	{
		if (config == null) return true;
		switch (name)
		{
			case "Dumps":   return config.showDumps();
			case "Dips":    return config.showDips();
			case "Decant":  return config.showDecant();
			default:        return false;
		}
	}

	public List<String> resolveTabOrder()
	{
		return new ArrayList<>(MAIN_TAB_ORDER);
	}

	private JTabbedPane buildTabs()
	{
		JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.setForeground(Color.WHITE);
		tabs.setFont(Fonts.SM);
		applyStaticOrderUI(tabs);

		JPanel flipsContent    = buildFlipsTab();
		JPanel dumpsContent    = buildDumpsTab();
		JPanel insightsContent = buildInsightsTab();
		JPanel dipsContent       = buildDipsTab();
		JPanel decantContent     = buildDecantTab();
		JPanel favouritesContent = buildFavouritesTab();
		JPanel planContent       = buildPlanTab();

		if (!isPremium)
		{
			final String gate = "To view this feature you need to be a member and link your API key.";
			dumpsContent     = buildPremiumGateTab("Dumps",     gate);
			dipsContent      = buildPremiumGateTab("Dips",      gate);
			decantContent    = buildPremiumGateTab("Decant",    gate);
			planContent      = buildPremiumGateTab("Plan",      gate);
		}

		JPanel otherContent      = buildOtherTab(dipsContent,
			decantContent,
			dumpsContent);
		JPanel myFlipsContent  = buildMyFlipsTab();

		java.util.Map<String, JPanel> contentByName = new java.util.HashMap<>();
		contentByName.put("Flips",     flipsContent);
		contentByName.put("Dumps",     dumpsContent);
		contentByName.put("Item",      insightsContent);
		contentByName.put("Other",     otherContent);
		contentByName.put("Trades",    myFlipsContent);
		contentByName.put("Dips",    dipsContent);
		contentByName.put("Decant",  decantContent);
		contentByName.put("Favs",    favouritesContent);
		contentByName.put("Plan",    planContent);

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

		tabs.addChangeListener(e ->
		{
			int idx = tabs.getSelectedIndex();
			if (idx < 0) return;
			String title = tabs.getTitleAt(idx);
			if (OTHER_SUB_TABS.contains(title))
			{
				lastOtherSubTab = title;
			}
			if ("Item".equals(title))
			{
				ensureInsightsPanel();
			}
			else if ("Plan".equals(title) && plugin != null)
			{
				plugin.onPlanTabSelected();
			}
			else if ("Favs".equals(title) && plugin != null)
			{
				plugin.onPlanTabDeselected();
				plugin.onFavouritesTabSelected();
			}
			else if (plugin != null && !"Other".equals(title))
			{
				plugin.onPlanTabDeselected();
			}
		});

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
					return false;
				}

				@Override
				protected void paintTabBackground(java.awt.Graphics g, int tabPlacement,
					int tabIndex, int x, int y, int w, int h, boolean isSelected)
				{
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
				}

				@Override
				protected void paintContentBorder(java.awt.Graphics g, int tabPlacement,
					int selectedIndex)
				{
				}

				@Override
				protected void paintFocusIndicator(java.awt.Graphics g, int tabPlacement,
					java.awt.Rectangle[] rects, int tabIndex,
					java.awt.Rectangle iconRect, java.awt.Rectangle textRect, boolean isSelected)
				{
				}

				@Override
				protected java.awt.Insets getContentBorderInsets(int tabPlacement)
				{
					return new java.awt.Insets(0, 0, 0, 0);
				}

				@Override
				protected java.awt.Insets getTabInsets(int tabPlacement, int tabIndex)
				{
					return new java.awt.Insets(7, 4, 7, 4);
				}

				@Override
				protected int calculateTabWidth(int tabPlacement, int tabIndex,
					java.awt.FontMetrics metrics)
				{
					javax.swing.JTabbedPane pane = this.tabPane;
					int paneWidth = pane == null ? 0 : pane.getWidth();
					if (paneWidth <= 0)
					{
						return super.calculateTabWidth(tabPlacement, tabIndex, metrics);
					}
					java.awt.Insets paneInsets = pane.getInsets();
					java.awt.Insets tabAreaInsets = getTabAreaInsets(tabPlacement);
					int available = paneWidth
						- paneInsets.left - paneInsets.right
						- tabAreaInsets.left - tabAreaInsets.right;
					int n = pane.getTabCount();
					int perRow = perRowFor(n);
					return Math.max(40, (available - 4) / perRow);
				}

				@Override
				protected int calculateMaxTabWidth(int tabPlacement)
				{
					return calculateTabWidth(tabPlacement, 0,
						tabPane.getFontMetrics(tabPane.getFont()));
				}

				private int perRowFor(int n)
				{
					if (n <= 4) return Math.max(1, n);
					if (n <= 6) return 3;
					if (n <= 8) return 4;
					return (int) Math.ceil(n / 2.0);
				}
			});
			pane.setOpaque(true);
		}
		catch (Exception e)
		{
			org.slf4j.LoggerFactory.getLogger(O7FlipPanel.class)
				.warn("[07Flip] Could not pin tab-run order: {}", e.getMessage());
		}
	}

	private JPanel insightsHost;

	private JPanel buildInsightsTab()
	{
		insightsHost = listPanel();
		return assembleTab(null, insightsHost, null);
	}

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

	public void showInsightsLoading(int itemId, String fallbackName)
	{
		com.o7flip.ui.InsightsPanel p = ensureInsightsPanel();
		if (p != null)
		{
			p.showLoading(itemId, fallbackName);
		}
		CardLayout cl = (CardLayout) mainArea.getLayout();
		cl.show(mainArea, "tabs");
		if (searchField != null)
		{
			searchField.setText("");
		}
		selectTab("Item");
	}

	public void showInsights(int itemId, com.o7flip.model.ItemInsights insights)
	{
		com.o7flip.ui.InsightsPanel p = ensureInsightsPanel();
		if (p != null)
		{
			p.show(insights);
		}
	}

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
		myFlipsStatsPanel.setOnMembershipToggle(() ->
		{
			if (plugin != null)
			{
				plugin.setMembershipCostHidden(!plugin.isMembershipCostHidden());
			}
		});
		myFlipsStatsPanel.setOnMembershipAdjust(this::openMembershipAdjustDialog);

		myFlipsSortBtns = new JButton[3];
		JPanel sortBar = buildSortBar(myFlipsSortBtns,
			new String[]{"Active", "Recent", "Margin"},
			() -> myFlipsSortIdx,
			i ->
			{
				myFlipsSortIdx = i;
				myFlipsPage    = 0;
				renderMyFlips();
			},
			false);

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

	public void showPremiumRequiredToast(String upgradeUrl)
	{
		String url = (upgradeUrl == null || upgradeUrl.isEmpty()) ? "https://07flip.com/premium" : upgradeUrl;
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

	private String tabBeforeGeAutoOpen = null;

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
		String previouslySelected = currentSelectedTabName();
		if ("Other".equals(previouslySelected))
		{
			String innerNow = currentInnerOtherSubTab();
			if (innerNow != null) lastOtherSubTab = innerNow;
		}

		insightsPanel = null;

		tabsWrapper.removeAll();
		tabsWrapper.add(buildTabs(), BorderLayout.CENTER);
		tabsWrapper.revalidate();
		tabsWrapper.repaint();
		String q = filtered();
		renderFlips(q);
		renderDumps(q);
		renderDips(q);
		renderDecants(q);
		renderFavourites(q);
		refreshBlocklistFooter();

		if (previouslySelected != null)
		{
			boolean restored = selectTab(previouslySelected);
			if (!restored && OTHER_SUB_TABS.contains(previouslySelected))
			{
				lastOtherSubTab = previouslySelected;
				selectTab("Other");
			}
		}
	}

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

	private String currentInnerOtherSubTab()
	{
		if (tabsWrapper.getComponentCount() == 0) return null;
		java.awt.Component c = tabsWrapper.getComponent(0);
		if (!(c instanceof JTabbedPane)) return null;
		JTabbedPane pane = (JTabbedPane) c;
		int idx = pane.getSelectedIndex();
		if (idx < 0 || !"Other".equals(pane.getTitleAt(idx))) return null;
		java.awt.Component outerContent = pane.getComponentAt(idx);
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
		flipsFilterButton = pillButton("Filter");
		flipsFilterButton.addActionListener(e -> toggleFlipsFilterPanel());

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerRow.setBorder(new EmptyBorder(6, 10, 4, 8));
		headerRow.add(flipsFilterButton, BorderLayout.EAST);

		flipsChipBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		flipsChipBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		flipsChipBar.setBorder(new EmptyBorder(0, 8, 4, 8));
		flipsChipBar.setVisible(false);

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

		rebuildFlipsChipBar();

		return assembleTab(topBar, flipsListPanel, buildPageBar(flipsPageLabel, flipsPrev, flipsNext));
	}

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

		JComboBox<String> minVolCombo = styledCombo(FLIPS_MIN_VOL_LABELS);
		minVolCombo.setSelectedIndex(Math.max(0, Math.min(flipsMinHourlyVolIdx, FLIPS_MIN_VOL_LABELS.length - 1)));
		minVolCombo.addActionListener(e ->
		{
			flipsMinHourlyVolIdx = minVolCombo.getSelectedIndex();
			flipsPage = 0;
			rebuildFlipsChipBar();
			renderFlips(filtered());
		});

		JComboBox<String> minPriceCombo = styledCombo(FLIPS_MIN_BUY_PRICE_LABELS);
		minPriceCombo.setSelectedIndex(Math.max(0, Math.min(flipsMinBuyPriceIdx, FLIPS_MIN_BUY_PRICE_LABELS.length - 1)));
		minPriceCombo.addActionListener(e ->
		{
			flipsMinBuyPriceIdx = minPriceCombo.getSelectedIndex();
			flipsPage = 0;
			rebuildFlipsChipBar();
			if (plugin != null) plugin.onFlipsFilterChanged();
		});

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

	private void repaintDumpsTierBar()
	{
		if (dumpsTierBtns == null) return;
		final String[] keys = {"all", "confirmed", "likely"};
		int totalAll = dumpsConfirmedTotal + dumpsLikelyTotal;
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
		dumpsTierBar = buildDumpsTierBar();

		final String[] sortLabels = {"Max Profit", "Recovery", "Vol×Cons", "Score", "Recent"};
		final String[] sortKeys   = {"max_profit", "recovery_pct", "volume_consistency", "dump_pct", "recent"};
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

		JButton activeBtn = pillButton("Active");
		activeBtn.setToolTipText("<html>When on, only items currently dumping or due soon<br>"
			+ "(dump_status is dumping or due_soon) are shown.</html>");
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

	private JPanel buildDecantTab()
	{
		decantSortBtns  = new JButton[3];
		decantListPanel = listPanel();
		decantPageLabel = pageLabel();
		decantPrev      = pageBtn("‹");
		decantNext      = pageBtn("›");
		decantPrev.addActionListener(e ->
		{
			decantPage--;
			if (plugin != null)
			{
				plugin.onDecantPageChanged(decantPage);
			}
		});
		decantNext.addActionListener(e ->
		{
			decantPage++;
			if (plugin != null)
			{
				plugin.onDecantPageChanged(decantPage);
			}
		});
		return assembleTab(buildSortBar(decantSortBtns, new String[]{"Profit", "ROI %", "Volume"},
			() -> decantSortIdx, i ->
			{
				decantSortIdx = i;
				decantPage = 0;
				if (plugin != null)
				{
					plugin.onDecantSortChanged(decantSortIdx);
				}
			}),
			decantListPanel, buildPageBar(decantPageLabel, decantPrev, decantNext));
	}

	private void applyToggleStyle(JButton btn, boolean on)
	{
		btn.setBackground(on ? new Color(0x00C27A) : new Color(0x3E3E3E));
		btn.setForeground(on ? Color.BLACK : ColorScheme.LIGHT_GRAY_COLOR);
	}

	private JPanel buildFavouritesTab()
	{
		favouritesListPanel = listPanel();
		favouritesSortBtns = new JButton[2];
		JPanel sortBar = buildSortBar(favouritesSortBtns,
			new String[]{"Default", "Margin"},
			() -> favouritesSortIdx,
			i ->
			{
				favouritesSortIdx = i;
				renderFavourites(filtered());
			},
			false);

		JButton reorderBtn = pillButton("⇅");
		reorderBtn.setToolTipText("Reorder or remove favourites");
		reorderBtn.addActionListener(e ->
			com.o7flip.ui.FavouritesReorderDialog.show(this, favouritesForReorder(), this::applyFavouritesOrder));

		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.add(sortBar, BorderLayout.CENTER);
		JPanel reorderWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
		reorderWrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		reorderWrap.add(reorderBtn);
		topBar.add(reorderWrap, BorderLayout.EAST);

		return assembleTab(topBar, favouritesListPanel, null);
	}

	private JPanel buildPlanTab()
	{
		optimizerListPanel = listPanel();
		renderOptimizerEmpty();

		optCollapsedPanel = buildOptimizerCollapsedPill();

		optInputsHost = new JPanel(new BorderLayout());
		optInputsHost.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		applyOptimizerFormVisibility();

		return assembleTab(optInputsHost, optimizerListPanel, null);
	}

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
		optInputsHost.revalidate();
		optInputsHost.repaint();
	}

	public void onOptimizeResult(com.o7flip.model.OptimizeResult result)
	{
		optInFlight = false;
		lastOptimize = result;
		optFormCollapsed = true;
		applyOptimizerFormVisibility();
		renderOptimizerResult(result);
	}

	public void onOptimizePremiumRequired(String upgradeUrl)
	{
		optInFlight = false;
		renderOptimizerPremium(upgradeUrl);
	}

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

	public void hydrateOptimizerSession(com.o7flip.model.OptimizerSession session)
	{
		if (session == null || session.slots == null || session.slots.isEmpty()) return;
		optSlots        = session.inputs.slots > 0 ? Math.min(8, session.inputs.slots) : optSlots;
		optRisk         = session.inputs.risk != null && !session.inputs.risk.isEmpty() ? session.inputs.risk : optRisk;
		optMaxFillHours = session.inputs.maxFillHours != null ? session.inputs.maxFillHours : optMaxFillHours;
		optMembers      = session.inputs.members;
		optMinProfitPct = session.inputs.minProfitPct != null
			? (int) Math.round(Math.max(0, Math.min(10, session.inputs.minProfitPct))) : optMinProfitPct;

		com.o7flip.model.OptimizeResult result = new com.o7flip.model.OptimizeResult();
		result.updatedAt   = session.generatedAt;
		result.allocations = new java.util.ArrayList<>(session.slots);
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
		if (!optShowingHistory)
		{
			renderOptimizerResult(result);
		}
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

		JComponent reconcileBanner = buildOfflineReconcileBanner(r);
		if (reconcileBanner != null)
		{
			optimizerListPanel.add(reconcileBanner);
			optimizerListPanel.add(sep());
		}

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

	private JComponent buildSlotSuggestionBanner(com.o7flip.model.OptimizeResult.Summary s)
	{
		if (s == null || s.slotSuggestion == null) return null;
		final int suggested = s.slotSuggestion.suggestedSlots;
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

	private enum HistorySort { RECENT, QUICKEST, MOST_PROFIT }
	private HistorySort optHistorySort = HistorySort.RECENT;

	public void onCompletedPositionsChanged()
	{
		if (optShowingHistory)
		{
			renderCompletedPositionsHistory();
		}
		else if (lastOptimize != null)
		{
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
			.append(FlipItemPanel.escapeHtml(cp.name)).append("</b></font>");
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
			.append(FlipItemPanel.formatGp(cp.qty)).append(" @ ")
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

	private JPanel buildOtherTab(JPanel dipsContent,
	                             JPanel decantContent,
	                             JPanel dumpsContent)
	{
		JTabbedPane inner = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
		inner.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inner.setForeground(Color.WHITE);
		inner.setFont(Fonts.SM);
		applyStaticOrderUI(inner);

		java.util.Map<String, JPanel> byName = new java.util.HashMap<>();
		byName.put("Dips",    dipsContent);
		byName.put("Decant",  decantContent);
		byName.put("Dumps",   dumpsContent);

		for (String name : OTHER_SUB_TABS)
		{
			if (!subFeatureEnabled(name)) continue;
			JPanel content = byName.get(name);
			if (content == null) continue;
			inner.addTab(name, content);
		}

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

		inner.addChangeListener(e ->
		{
			int idx = inner.getSelectedIndex();
			if (idx < 0 || plugin == null) return;
			String title = inner.getTitleAt(idx);
			lastOtherSubTab = title;
			plugin.onOtherSubTabSelected(title);
		});

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.add(inner, BorderLayout.CENTER);
		return wrap;
	}

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

	private JPanel buildSortBar(JButton[] store, String[] labels, IntSupplier get, IntConsumer set)
	{
		return buildSortBar(store, labels, get, set, true);
	}

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
