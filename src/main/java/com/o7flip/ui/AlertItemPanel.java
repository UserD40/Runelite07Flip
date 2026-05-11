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
import com.o7flip.model.AlertItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Merch alert card. Two visual variants:
 *
 * <ul>
 *   <li><b>Pending</b> — neutral dark background. Header (icon + name), then
 *       Target / Current / Starting price lines, then a sparkline of the
 *       price journey since detection, then footer "Holds for X · Y ago".</li>
 *   <li><b>Successful</b> — soft green-tinted background with extra rows
 *       for achieved price, realised ROI %, and per-item profit. Same
 *       overall structure but the achievement is the headline.</li>
 * </ul>
 *
 * Tier rendering, drawdown, 90d range bar — all dropped per the redesign.
 * The card is for "what's happening with this alert", not stat trivia.
 */
public class AlertItemPanel extends JPanel
{
	private static final Color CARD_BG          = new Color(0x1F1F1F);
	private static final Color CARD_BG_ALT      = new Color(0x252525);
	private static final Color SUCCESS_BG       = new Color(0x12321F);   // green-tinted card
	private static final Color SUCCESS_BG_ALT   = new Color(0x153925);
	private static final Color HOVER_TINT       = new Color(0xFFFFFF, true);
	private static final Color GREEN            = new Color(0x00C27A);
	private static final Color RED              = new Color(0xE85050);
	private static final Color ORANGE           = new Color(0xFF981F);
	private static final Color GRAY_LBL         = new Color(0x888888);
	private static final Color WHITE_DIM        = new Color(0xCCCCCC);

	public AlertItemPanel(AlertItem alert, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		boolean successful = "successful".equalsIgnoreCase(alert.status);
		Color bg = successful
			? (odd ? SUCCESS_BG_ALT : SUCCESS_BG)
			: (odd ? CARD_BG_ALT    : CARD_BG);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(bg);
		setBorder(new EmptyBorder(10, 12, 10, 12));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(buildHeader(alert, itemManager));
		add(verticalStrut(8));
		add(buildPriceLines(alert, successful));
		add(verticalStrut(8));

		// Same chart logic as the Insights tab: dual buy/sell line, shared
		// min/max, breaks polylines on null. The server picks the timeframe
		// (since-detection daily for older alerts, last-24h hourly for brand-
		// new ones) so the chart always has data to draw.
		// 78 px gives the chart room for the X / Y axis labels added in the
		// shared BuySellSparkline. "start" / "now" label the visible window
		// regardless of whether the server gave us daily-since-detection or
		// last-24h hourly buckets — the timeframe is server-decided.
		BuySellSparkline chart = new BuySellSparkline(alert.sparklineBuy, alert.sparklineSell, 78, "start", "now");
		chart.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(chart);
		add(verticalStrut(8));

		if (successful)
		{
			add(buildSuccessBlock(alert));
			add(verticalStrut(8));
		}

		add(buildFooter(alert, successful));

		ClickRouter.attach(this, plugin, alert.itemId, alert.name);
		final Color baseBg = bg;
		final Color hoverBg = successful
			? new Color(Math.min(255, bg.getRed() + 12),
				Math.min(255, bg.getGreen() + 12),
				Math.min(255, bg.getBlue() + 12))
			: new Color(0x2A2A2A);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(hoverBg);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(baseBg);
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null)
				{
					showContextMenu(e, alert, plugin);
				}
			}
		});

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	// ── Sections ─────────────────────────────────────────────────────────────

	private static JPanel buildHeader(AlertItem alert, ItemManager itemManager)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

		JLabel iconLabel = FlipItemPanel.buildIcon(alert.itemId, itemManager);

		JLabel nameLabel = new JLabel(alert.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		row.add(iconLabel, BorderLayout.WEST);
		row.add(nameLabel, BorderLayout.CENTER);
		return row;
	}

	private static JPanel buildPriceLines(AlertItem alert, boolean successful)
	{
		// Three rows: Target, Current, Starting. Target is always green-bold
		// regardless of variant. Current price comes from live_price (server
		// Wiki cache); falls back to startingPrice if the server hasn't filled
		// in a live sample. Starting price is the detection-time snapshot.
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setOpaque(false);
		block.setAlignmentX(Component.LEFT_ALIGNMENT);

		long current = alert.livePrice != null ? alert.livePrice : alert.startingPrice;

		block.add(priceRow("Target",         formatGp(alert.sellTarget),    GREEN));
		block.add(verticalStrut(2));
		block.add(priceRow("Current price",  formatGp(current),             Color.WHITE));
		block.add(verticalStrut(2));
		block.add(priceRow("Starting price", formatGp(alert.startingPrice), WHITE_DIM));
		return block;
	}

	private static JPanel priceRow(String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JLabel l = new JLabel(label);
		l.setFont(Fonts.SM);
		l.setForeground(GRAY_LBL);

		JLabel v = new JLabel(value);
		v.setFont(Fonts.SM_BOLD);
		v.setForeground(valueColor);
		v.setHorizontalAlignment(JLabel.RIGHT);

		row.add(l, BorderLayout.WEST);
		row.add(v, BorderLayout.EAST);
		return row;
	}

	private static JPanel buildSuccessBlock(AlertItem alert)
	{
		// Achievement detail rows — only shown for successful alerts. Three
		// numbers stacked: achieved price (the sell_target threshold-cross
		// price the alert hit), ROI %, and per-item profit.
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setOpaque(false);
		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.setBorder(new EmptyBorder(4, 0, 0, 0));

		long achieved = alert.achievedPrice != null ? alert.achievedPrice : alert.sellTarget;
		block.add(priceRow("Achieved price",
			formatGp(achieved),
			GREEN));
		block.add(verticalStrut(2));

		String roiText = alert.realisedRoiPct != null
			? String.format("%+.2f%%", alert.realisedRoiPct)
			: "—";
		block.add(priceRow("Realised ROI", roiText, GREEN));
		block.add(verticalStrut(2));

		String profitText = alert.realisedProfit != null
			? "+" + formatGp(alert.realisedProfit)
			: "—";
		block.add(priceRow("Profit / item", profitText, GREEN));

		return block;
	}

	private static JPanel buildFooter(AlertItem alert, boolean successful)
	{
		// "Holds for 2-4 weeks" left, "28d ago" right. For successful alerts
		// the right side shows time since achievement instead of detection,
		// since "achieved 5d ago" is the more relevant fact.
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		String holdText = (alert.holdTime != null && !alert.holdTime.isEmpty())
			? "Holds for " + alert.holdTime
			: "";
		JLabel left = new JLabel(holdText);
		left.setFont(Fonts.SM);
		left.setForeground(GRAY_LBL);

		String rightText;
		if (successful && alert.achievedAt != null && !alert.achievedAt.isEmpty())
		{
			rightText = "Hit " + relativeAge(alert.achievedAt);
		}
		else
		{
			rightText = relativeAge(alert.detectedAt);
		}
		JLabel right = new JLabel(rightText);
		right.setFont(Fonts.SM);
		right.setForeground(GRAY_LBL);
		right.setHorizontalAlignment(JLabel.RIGHT);

		row.add(left,  BorderLayout.WEST);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private static Component verticalStrut(int height)
	{
		return javax.swing.Box.createRigidArea(new Dimension(0, height));
	}

	private static String formatGp(long amount)
	{
		return String.format("%,d gp", amount);
	}

	/** "2026-02-14T20:30:00.000Z" → "5d ago" / "3h ago" / "just now" */
	private static String relativeAge(String iso)
	{
		if (iso == null || iso.isEmpty())
		{
			return "";
		}
		try
		{
			java.time.Instant t = java.time.Instant.parse(iso);
			long secs = java.time.Duration.between(t, java.time.Instant.now()).getSeconds();
			if (secs < 60)            return "just now";
			if (secs < 3600)          return (secs / 60) + "m ago";
			if (secs < 86400)         return (secs / 3600) + "h ago";
			if (secs < 86400L * 30)   return (secs / 86400) + "d ago";
			return (secs / (86400L * 30)) + "mo ago";
		}
		catch (Exception e)
		{
			return iso;
		}
	}

	private static void showContextMenu(MouseEvent e, AlertItem alert, O7FlipPlugin plugin)
	{
		JPopupMenu menu = new JPopupMenu();
		long buyPrice = alert.livePrice != null ? alert.livePrice : alert.startingPrice;
		JMenuItem buyItem = new JMenuItem("Buy on GE — " + formatGp(buyPrice));
		buyItem.addActionListener(ae -> plugin.queueGeBuy(alert.itemId, buyPrice, alert.name));
		menu.add(buyItem);
		boolean inInventory = plugin.inventoryItemIds.contains(alert.itemId);
		if (!plugin.getConfig().inventoryCheckOnSell() || inInventory)
		{
			JMenuItem sellItem = new JMenuItem("Sell on GE — " + formatGp(alert.sellTarget));
			sellItem.addActionListener(ae -> plugin.queueGeSell(alert.itemId, alert.sellTarget, alert.name));
			menu.add(sellItem);
		}
		menu.addSeparator();
		FlipItemPanel.addHideMenuItem(menu, plugin, alert.itemId, alert.name);
		menu.show(e.getComponent(), e.getX(), e.getY());
	}
}
