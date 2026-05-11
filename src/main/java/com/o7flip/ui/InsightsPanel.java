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
package com.o7flip.ui;

import com.o7flip.O7FlipPlugin;
import com.o7flip.model.FlipItem;
import com.o7flip.model.ItemInsights;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

/**
 * Per-item Insights view, populated by {@link com.o7flip.O7FlipPlugin#openInsights}.
 * Renders the response from {@code GET /api/runelite/v2/item/{itemId}}, gated on
 * the {@code premium_locked} flag — premium-only fields render as dashes, with
 * an upsell card appended at the bottom directing users to {@code upgrade_url}.
 *
 * Pure presentation. The plugin owns the fetch lifecycle and the panel is
 * fully rebuilt on each {@link #show} call to keep state minimal.
 */
public class InsightsPanel extends JPanel
{
	private static final Color SECTION_BG    = new Color(0x1F1F1F);
	private static final Color HEADER_COL    = new Color(0xC4A052);
	private static final Color PROFIT_COL    = new Color(0x00C27A);
	private static final Color LOSS_COL      = new Color(0xE85050);
	private static final Color LOCKED_COL    = new Color(0x808080);
	private static final Color UPSELL_BG     = new Color(0x2A2418);
	private static final Color UPSELL_BORD   = new Color(0xC4A052);

	private final ItemManager itemManager;
	private O7FlipPlugin plugin;
	private List<FlipItem> recommended = Collections.emptyList();
	private boolean inEmptyState = true;

	public InsightsPanel(ItemManager itemManager)
	{
		this(itemManager, null);
	}

	public InsightsPanel(ItemManager itemManager, O7FlipPlugin plugin)
	{
		this.itemManager = itemManager;
		this.plugin      = plugin;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 0, 8, 0));
		// Stretch fully within the parent listPanel — otherwise the InsightsPanel
		// takes its preferred width (which is the widest child's preferred width)
		// and gets left-aligned inside the host, leaving a gap on the right.
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		showEmpty();
	}

	/**
	 * Updates the recommended-items list shown in the empty state. Re-renders
	 * the empty state in place if it's currently showing — does nothing when
	 * an item is already loaded so the user's selection isn't blown away by a
	 * background flips refresh.
	 */
	public void setRecommended(List<FlipItem> items, O7FlipPlugin p)
	{
		this.recommended = items != null ? items : Collections.emptyList();
		this.plugin = p;
		if (inEmptyState)
		{
			showEmpty();
		}
	}

	/**
	 * No item picked yet. Renders a friendly placeholder plus a "Recommended"
	 * section with up to three top-scoring flips so the user can jump straight
	 * into one without leaving the tab. Recommendations come from whatever
	 * {@link #setRecommended} most recently passed in — typically the top of
	 * the Flips list, sorted by 07Flip score.
	 */
	public void showEmpty()
	{
		removeAll();
		inEmptyState = true;

		add(Box.createVerticalStrut(20));
		add(stretchedCenterLabel("Item Insights", HEADER_COL, Fonts.TITLE));
		add(Box.createVerticalStrut(8));
		add(stretchedWrappedCenter(
			"Left-click any item across the plugin (Flips, Alerts, My Trades, anywhere) "
				+ "to see live prices, charts, and 07Flip recommendations here.",
			ColorScheme.LIGHT_GRAY_COLOR, Fonts.SM));
		add(Box.createVerticalStrut(4));
		add(stretchedCenterLabel("Double-click to open the item page on 07flip.com",
			new Color(0x666666), Fonts.SM));
		add(Box.createVerticalStrut(20));

		if (!recommended.isEmpty() && plugin != null)
		{
			add(buildRecommendedSection());
		}

		revalidate();
		repaint();
	}

	/**
	 * Full-width JLabel with text centred via the label's own horizontal
	 * alignment, not via BoxLayout's alignmentX. Mixing CENTER/LEFT
	 * alignmentX in a Y_AXIS BoxLayout makes children visually drift
	 * around a virtual centre axis, so we keep every child LEFT_ALIGNED
	 * and stretchable instead.
	 */
	private static JLabel stretchedCenterLabel(String text, Color colour, java.awt.Font font)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(colour);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(4, 12, 4, 12));
		int h = l.getPreferredSize().height;
		l.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		return l;
	}

	/**
	 * Word-wrapping centred paragraph for the empty-state explanation.
	 * Uses HTML body with no fixed width — JLabel adapts to whatever
	 * width the BoxLayout column gives us. Height is unconstrained so
	 * tall wraps don't get clipped.
	 */
	private static JLabel stretchedWrappedCenter(String text, Color colour, java.awt.Font font)
	{
		String html = "<html><div style='text-align:center'>"
			+ text.replace("<", "&lt;").replace(">", "&gt;")
			+ "</div></html>";
		JLabel l = new JLabel(html);
		l.setFont(font);
		l.setForeground(colour);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(0, 16, 0, 16));
		l.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
		return l;
	}

	private JPanel buildRecommendedSection()
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARK_GRAY_COLOR);
		section.setBorder(new EmptyBorder(0, 10, 0, 10));
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		// Stretch to full panel width — without this BoxLayout caps at the
		// preferred width and adjacent rows visually drift inwards.
		section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

		JLabel header = new JLabel("Recommended");
		header.setFont(Fonts.SM_BOLD);
		header.setForeground(HEADER_COL);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(new EmptyBorder(0, 0, 6, 0));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
		section.add(header);

		JLabel sub = new JLabel("Top-scoring flips right now");
		sub.setFont(Fonts.SM);
		sub.setForeground(new Color(0x888888));
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);
		sub.setBorder(new EmptyBorder(0, 0, 8, 0));
		sub.setMaximumSize(new Dimension(Integer.MAX_VALUE, sub.getPreferredSize().height));
		section.add(sub);

		int rendered = 0;
		for (FlipItem item : recommended)
		{
			if (item == null || item.itemId <= 0) continue;
			section.add(new RecommendedCard(item, itemManager, plugin));
			section.add(Box.createVerticalStrut(6));
			if (++rendered >= 3) break;
		}
		return section;
	}

	/**
	 * Compact clickable card for the empty state. Click → openInsights(itemId)
	 * which switches the tab content to that item's loaded view in place.
	 */
	private static class RecommendedCard extends JPanel
	{
		private static final Color CARD_BG    = new Color(0x1F1F1F);
		private static final Color CARD_HOVER = new Color(0x2A2A2A);

		RecommendedCard(FlipItem item, ItemManager itemManager, O7FlipPlugin plugin)
		{
			setLayout(new BorderLayout(8, 0));
			setBackground(CARD_BG);
			setBorder(new EmptyBorder(8, 10, 8, 10));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			JLabel icon = FlipItemPanel.buildIcon(item.itemId, itemManager);

			JLabel name = new JLabel(item.name);
			name.setFont(Fonts.BOLD);
			name.setForeground(Color.WHITE);

			JLabel sub;
			if (item.flip07Score != null)
			{
				sub = new JLabel("Score " + item.flip07Score + " · " + FlipItemPanel.formatGpCompact(item.profit) + " gp profit");
			}
			else
			{
				sub = new JLabel(FlipItemPanel.formatGpCompact(item.profit) + " gp profit");
			}
			sub.setFont(Fonts.SM);
			sub.setForeground(new Color(0xC4A052));

			JPanel text = new JPanel();
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
			text.setBackground(CARD_BG);
			text.add(name);
			text.add(Box.createVerticalStrut(2));
			text.add(sub);

			add(icon, BorderLayout.WEST);
			add(text, BorderLayout.CENTER);

			ClickRouter.attachInsightsOnly(this, plugin, item.itemId, item.name);

			final Color base = CARD_BG;
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					setBackground(CARD_HOVER);
					text.setBackground(CARD_HOVER);
				}
				@Override
				public void mouseExited(MouseEvent e)
				{
					setBackground(base);
					text.setBackground(base);
				}
			});
		}
	}

	/** Loading state: shown immediately on click while the fetch is in flight. */
	public void showLoading(int itemId, String fallbackName)
	{
		removeAll();
		inEmptyState = false;
		add(buildHeader(itemId, fallbackName != null ? fallbackName : "Item " + itemId, false, 0, null, null));
		add(Box.createVerticalStrut(8));
		add(centeredLabel("Loading…", ColorScheme.LIGHT_GRAY_COLOR, Fonts.SM));
		revalidate();
		repaint();
	}

	/** Successful response — render the full view. */
	public void show(ItemInsights ins)
	{
		removeAll();
		inEmptyState = false;
		if (ins == null)
		{
			add(centeredLabel("Failed to load insights", LOSS_COL, Fonts.BOLD));
			add(Box.createVerticalStrut(4));
			add(centeredLabel("Click another item to retry", ColorScheme.LIGHT_GRAY_COLOR, Fonts.SM));
			revalidate();
			repaint();
			return;
		}

		// Always-visible sections (header → live prices → chart → volume → alerts).
		// Free users see only these plus a single upsell card; premium users see
		// the locked sections inline before Volume.
		add(buildHeader(ins.itemId, ins.name, ins.members, ins.buyLimit, ins.highAlch, ins.lowAlch));
		add(Box.createVerticalStrut(8));

		add(buildLivePrices(ins));
		add(Box.createVerticalStrut(8));

		add(buildBuySellSparklineSection("Buy / Sell · last 24h", ins.sparkline24hBuy, ins.sparkline24hSell));
		add(Box.createVerticalStrut(8));

		// Premium-only sections sit between the chart and Volume so the
		// open data flows naturally and the locked block is one cohesive
		// region, not interleaved with open rows.
		if (!ins.premiumLocked)
		{
			add(build07FlipPrices(ins));
			add(Box.createVerticalStrut(8));

			add(buildScore(ins));
			add(Box.createVerticalStrut(8));

			add(buildRanges(ins));
			add(Box.createVerticalStrut(8));

			if (ins.projection != null)
			{
				add(buildProjection(ins.projection));
				add(Box.createVerticalStrut(8));
			}
		}

		add(buildVolume(ins));
		add(Box.createVerticalStrut(8));

		add(buildAlerts(ins));
		add(Box.createVerticalStrut(8));

		// Single consolidated upsell card for free users — replaces the four
		// per-section "—" placeholders the previous design rendered. One CTA,
		// not spammy.
		if (ins.premiumLocked)
		{
			add(buildUpsell(ins.upgradeUrl != null && !ins.upgradeUrl.isEmpty() ? ins.upgradeUrl : "https://07flip.com/premium"));
		}

		// Make every region of the panel respond to shift+click / double-click
		// → open the item page on 07flip.com. Each section captures its own
		// mouse events otherwise so a single panel-level listener wouldn't
		// catch clicks landed on labels deep in the tree. Plugin reference
		// comes from setRecommended (set on first construction by O7FlipPanel)
		// — if it's missing, we silently skip the wire-up.
		if (plugin != null)
		{
			ClickRouter.attachToTree(this, plugin, ins.itemId, ins.name);
		}

		revalidate();
		repaint();
	}

	// ── Sections ────────────────────────────────────────────────────────────

	private JPanel buildHeader(int itemId, String name, boolean members, int buyLimit, Integer highAlch, Integer lowAlch)
	{
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setBackground(SECTION_BG);
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

		JLabel icon = FlipItemPanel.buildIcon(itemId, itemManager);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		StringBuilder sub = new StringBuilder();
		sub.append(members ? "Members" : "F2P");
		if (buyLimit > 0)
		{
			sub.append(" · Buy limit ").append(buyLimit);
		}
		if (highAlch != null && highAlch > 0)
		{
			sub.append(" · Alch ").append(FlipItemPanel.formatGpCompact(highAlch));
		}
		JLabel subLabel = new JLabel(sub.toString());
		subLabel.setFont(Fonts.SM);
		subLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(SECTION_BG);
		text.add(nameLabel);
		text.add(Box.createVerticalStrut(2));
		text.add(subLabel);

		panel.add(icon, BorderLayout.WEST);
		panel.add(text, BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildLivePrices(ItemInsights ins)
	{
		JPanel panel = sectionPanel("Live prices");
		ItemInsights.Current c = ins.current;
		if (c == null)
		{
			panel.add(row("No price data", "—"));
			return panel;
		}
		panel.add(rowGp("Buy",    c.buyPrice,  ageSuffix(c.buyAgeMinutes),  Color.WHITE));
		panel.add(rowGp("Sell",   c.sellPrice, ageSuffix(c.sellAgeMinutes), Color.WHITE));
		panel.add(rowGp("Margin", c.margin,    null,                         margingColor(c.margin)));
		panel.add(rowGp("Tax",    c.tax,       null,                         LOCKED_COL));
		panel.add(rowGp("Profit", c.profit,    null,                         margingColor(c.profit)));
		panel.add(rowText("ROI",  String.format("%+.2f%%", c.roiPct),       margingColor((long) c.roiPct)));
		return panel;
	}

	private JPanel build07FlipPrices(ItemInsights ins)
	{
		// Premium-only — show called only when ins.premiumLocked is false.
		JPanel panel = sectionPanel("07Flip recommended");
		ItemInsights.Current c = ins.current;
		if (c == null || c.recBuy == null)
		{
			panel.add(row("No recommended prices", "—"));
			return panel;
		}
		panel.add(rowGp("Buy",    c.recBuy,    null, Color.WHITE));
		panel.add(rowGp("Sell",   c.recSell,   null, Color.WHITE));
		panel.add(rowGp("Profit", c.recProfit, null, margingColor(c.recProfit)));
		return panel;
	}

	private JPanel buildRanges(ItemInsights ins)
	{
		// Slated for replacement by a "Technical indicators" section once the
		// server exposes RSI / MACD / moving averages / etc. For now, show a
		// minimal price-range block — only callable in the premium-only branch
		// of show(), so we don't gate per-row.
		JPanel panel = sectionPanel("Price range");
		ItemInsights.Ranges r = ins.ranges;
		if (r == null)
		{
			panel.add(row("No range data", "—"));
			return panel;
		}
		panel.add(rowText("24h", FlipItemPanel.formatGpCompact(r.low24h) + " — " + FlipItemPanel.formatGpCompact(r.high24h), Color.WHITE));
		panel.add(rowText("90d", FlipItemPanel.formatGpCompact(r.low90d) + " — " + FlipItemPanel.formatGpCompact(r.high90d), Color.WHITE));
		if (r.drawdownPctFrom90d != null)
		{
			panel.add(rowText("Drawdown", String.format("%.1f%% below 90d high", r.drawdownPctFrom90d), ColorScheme.LIGHT_GRAY_COLOR));
		}
		return panel;
	}

	private JPanel buildScore(ItemInsights ins)
	{
		// Premium-only — caller gates on ins.premiumLocked.
		JPanel panel = sectionPanel("07Flip score");
		ItemInsights.Score s = ins.score;
		if (s == null)
		{
			panel.add(row("No score data", "—"));
			return panel;
		}
		panel.add(rowText("Confidence", s.confidence + " / 100", confidenceColor(s.confidence)));
		panel.add(rowText("Tier",       s.tier != null ? capitalise(s.tier) : "—", tierColor(s.tier)));
		if (s.signal != null && !s.signal.isEmpty())
		{
			panel.add(rowText("Signal", s.signal, signalColor(s.signal)));
		}
		return panel;
	}

	private JPanel buildVolume(ItemInsights ins)
	{
		JPanel panel = sectionPanel("Volume");
		ItemInsights.Volume v = ins.volume;
		if (v == null)
		{
			panel.add(row("No volume data", "—"));
			return panel;
		}
		panel.add(rowText("Hourly", String.format("%,d / hr", v.hourly), Color.WHITE));
		panel.add(rowText("Daily",  String.format("%,d / day", v.daily), Color.WHITE));
		return panel;
	}

	private JPanel buildBuySellSparklineSection(String title, Long[] buy, Long[] sell)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(SECTION_BG);
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel(title);
		header.setFont(Fonts.SM_BOLD);
		header.setForeground(HEADER_COL);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(new EmptyBorder(0, 0, 4, 0));
		panel.add(header);

		// Default height (80 px) leaves room for the X/Y axis labels.
		// Item insights chart is always last-24h hourly, so the X-axis labels
		// reflect that explicitly.
		BuySellSparkline spark = new BuySellSparkline(buy, sell, 80, "−24h", "now");
		spark.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(spark);

		// Tiny inline legend so users know which colour is which without a tooltip.
		JPanel legend = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
		legend.setBackground(SECTION_BG);
		legend.setBorder(new EmptyBorder(4, 0, 0, 0));
		legend.setAlignmentX(Component.LEFT_ALIGNMENT);
		legend.add(legendChip("Buy",  BuySellSparkline.BUY_COL));
		legend.add(legendChip("Sell", BuySellSparkline.SELL_COL));
		panel.add(legend);

		return panel;
	}

	private static JLabel legendChip(String label, Color colour)
	{
		// "■ Buy" — a coloured square glyph, then the label, in one JLabel.
		JLabel l = new JLabel("■ " + label);
		l.setFont(Fonts.SM);
		l.setForeground(colour);
		return l;
	}

	private JPanel buildAlerts(ItemInsights ins)
	{
		JPanel panel = sectionPanel("Alerts");
		ItemInsights.Alerts a = ins.alerts;
		if (a == null)
		{
			panel.add(row("No alert data", "—"));
			return panel;
		}
		if (a.activeMerch && a.merchTarget != null)
		{
			String tier = a.merchTier != null ? capitalise(a.merchTier) : "Active";
			panel.add(rowText("Merch alert",
				tier + " · target " + FlipItemPanel.formatGpCompact(a.merchTarget) + " gp",
				PROFIT_COL));
		}
		else
		{
			panel.add(rowText("Merch alert", "None", LOCKED_COL));
		}
		if (a.spikePct24h != null)
		{
			panel.add(rowText("Spike 24h", String.format("%+.1f%%", a.spikePct24h), margingColor((long) (double) a.spikePct24h)));
		}
		if (a.dipPct24h != null)
		{
			panel.add(rowText("Dip 24h", String.format("%+.1f%%", a.dipPct24h), margingColor((long) (double) a.dipPct24h)));
		}
		return panel;
	}

	private JPanel buildProjection(ItemInsights.Projection p)
	{
		JPanel panel = sectionPanel("Projection");
		if (p.band30d != null)
		{
			panel.add(rowText("30 days",
				FlipItemPanel.formatGpCompact(p.band30d.low) + " — " + FlipItemPanel.formatGpCompact(p.band30d.high)
					+ " (" + Math.round(p.band30d.hitRate * 100) + "%)",
				Color.WHITE));
		}
		if (p.band3m != null)
		{
			panel.add(rowText("3 months",
				FlipItemPanel.formatGpCompact(p.band3m.low) + " — " + FlipItemPanel.formatGpCompact(p.band3m.high)
					+ " (" + Math.round(p.band3m.hitRate * 100) + "%)",
				Color.WHITE));
		}
		return panel;
	}

	private JPanel buildUpsell(String upgradeUrl)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(UPSELL_BG);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(UPSELL_BORD, 1),
			new EmptyBorder(10, 12, 10, 12)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel headline = new JLabel("Unlock the full picture");
		headline.setFont(Fonts.BOLD);
		headline.setForeground(HEADER_COL);
		headline.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(headline);
		panel.add(Box.createVerticalStrut(4));

		JLabel pitch = new JLabel(
			"<html><div style='width:230px'>See 07Flip recommended buy/sell prices, the live Flip / Wait signal, "
				+ "and 30d / 3-month projection bands.</div></html>");
		pitch.setFont(Fonts.SM);
		pitch.setForeground(Color.WHITE);
		pitch.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(pitch);
		panel.add(Box.createVerticalStrut(8));

		JButton button = new JButton("Get Premium");
		button.setFont(Fonts.SM_BOLD);
		button.setForeground(Color.BLACK);
		button.setBackground(HEADER_COL);
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setBorder(new EmptyBorder(6, 14, 6, 14));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.addActionListener(e -> LinkBrowser.browse(upgradeUrl));
		panel.add(button);

		return panel;
	}

	// ── Row helpers ─────────────────────────────────────────────────────────

	private JPanel sectionPanel(String header)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(SECTION_BG);
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel h = new JLabel(header);
		h.setFont(Fonts.SM_BOLD);
		h.setForeground(HEADER_COL);
		h.setAlignmentX(Component.LEFT_ALIGNMENT);
		h.setBorder(new EmptyBorder(0, 0, 4, 0));
		panel.add(h);
		return panel;
	}

	private static JPanel row(String label, String value)
	{
		return rowText(label, value, Color.WHITE);
	}

	private static JPanel rowText(String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JLabel l = new JLabel(label);
		l.setFont(Fonts.SM);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel v = new JLabel(value, SwingConstants.RIGHT);
		v.setFont(Fonts.SM_BOLD);
		v.setForeground(valueColor);

		row.add(l, BorderLayout.WEST);
		row.add(v, BorderLayout.EAST);
		return row;
	}

	private static JPanel rowGp(String label, long gp, String suffix, Color valueColor)
	{
		String text = FlipItemPanel.formatGp(gp) + " gp";
		if (suffix != null && !suffix.isEmpty())
		{
			text = text + "  " + suffix;
		}
		return rowText(label, text, valueColor);
	}

	private static String ageSuffix(Integer ageMinutes)
	{
		if (ageMinutes == null)
		{
			return null;
		}
		if (ageMinutes < 1)
		{
			return "now";
		}
		if (ageMinutes < 60)
		{
			return ageMinutes + "m ago";
		}
		long h = ageMinutes / 60;
		return h + "h ago";
	}

	private static JLabel centeredLabel(String text, Color colour, java.awt.Font font)
	{
		JLabel l = new JLabel(text, SwingConstants.CENTER);
		l.setFont(font);
		l.setForeground(colour);
		l.setAlignmentX(Component.CENTER_ALIGNMENT);
		l.setBorder(new EmptyBorder(8, 12, 8, 12));
		return l;
	}

	/**
	 * Centred multi-line label using HTML-wrapped text — JLabel handles word
	 * wrap natively when the body is HTML with a fixed-width div. Keeps the
	 * empty-state explanation readable in the ~280px sidebar without a
	 * separate JTextArea / styled component.
	 */
	private static JLabel wrappedCenter(String text, Color colour, java.awt.Font font)
	{
		String html = "<html><div style='text-align:center;width:240px'>"
			+ text.replace("<", "&lt;").replace(">", "&gt;")
			+ "</div></html>";
		JLabel l = new JLabel(html, SwingConstants.CENTER);
		l.setFont(font);
		l.setForeground(colour);
		l.setAlignmentX(Component.CENTER_ALIGNMENT);
		l.setBorder(new EmptyBorder(0, 16, 0, 16));
		return l;
	}

	private static Color margingColor(long amount)
	{
		if (amount > 0)
		{
			return PROFIT_COL;
		}
		if (amount < 0)
		{
			return LOSS_COL;
		}
		return Color.LIGHT_GRAY;
	}

	private static Color confidenceColor(int score)
	{
		if (score >= 75)
		{
			return PROFIT_COL;
		}
		if (score >= 50)
		{
			return new Color(0xC4A052);
		}
		return LOCKED_COL;
	}

	private static Color tierColor(String tier)
	{
		if (tier == null)
		{
			return Color.LIGHT_GRAY;
		}
		switch (tier)
		{
			case "great": return new Color(0x00C27A);
			case "good":  return new Color(0xC4A052);
			case "fair":  return Color.LIGHT_GRAY;
			default:      return LOCKED_COL;
		}
	}

	private static Color signalColor(String signal)
	{
		if (signal == null)
		{
			return Color.LIGHT_GRAY;
		}
		if (signal.equalsIgnoreCase("flip"))
		{
			return PROFIT_COL;
		}
		if (signal.equalsIgnoreCase("wait") || signal.equalsIgnoreCase("hold"))
		{
			return new Color(0xE8A838);
		}
		return Color.WHITE;
	}

	private static String capitalise(String s)
	{
		if (s == null || s.isEmpty())
		{
			return "";
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

}
