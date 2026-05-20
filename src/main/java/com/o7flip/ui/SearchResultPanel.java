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
import com.o7flip.model.SearchResultItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Search-result card. Visually identical to {@link FlipItemPanel} — same
 * icon, name+chip row, Buy/Sell/Profit rows, colours, paddings and fonts —
 * so toggling between the Flips tab and a search filter feels seamless.
 *
 * Search results don't carry a 07Flip score, so the score-chip slot renders
 * as a muted dash with an explanatory tooltip rather than a number. The
 * tooltip prevents the column from looking broken.
 *
 * Right-click anywhere on the row queues a Buy on the GE — same single-
 * click action as FlipItemPanel, no popup menu. The queued price matches
 * FlipItemPanel's free-user behaviour (the instant-fill sell-side price).
 */
public class SearchResultPanel extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color HOVER_BG  = new Color(0x3A3A3A);
	private static final Color GREEN     = new Color(0x00C27A);

	public SearchResultPanel(SearchResultItem item, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean hasPrice = item.buyPrice != null && item.sellPrice != null;

		// ── ICON ──────────────────────────────────────────────────────────────
		JLabel iconLabel = FlipItemPanel.buildIcon(item.itemId, itemManager);

		// ── NAME + SCORE chip (mirrors FlipItemPanel's right-edge slot) ──────
		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		// Search payload doesn't include flip07_score, but we still render a
		// dash so the row's right edge aligns with FlipItemPanel rows above
		// and below it. Tooltip explains the gap.
		JLabel scoreLabel = new JLabel("—");
		scoreLabel.setFont(Fonts.BOLD);
		scoreLabel.setForeground(new Color(0xAAAAAA));
		scoreLabel.setToolTipText("07Flip merch algorithm score — only shown on items that surface in the Flips/Spikes/Alerts feeds.");

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(bg);
		nameRow.add(nameLabel,  BorderLayout.CENTER);
		nameRow.add(scoreLabel, BorderLayout.EAST);

		// ── BUY (red) ─────────────────────────────────────────────────────────
		JLabel buyLabel;
		if (hasPrice)
		{
			buyLabel = new JLabel("<html><b>Buy:</b>  " + FlipItemPanel.formatGpCompact(item.buyPrice) + "</html>");
			buyLabel.setFont(Fonts.SM);
			buyLabel.setForeground(new Color(0xFF7070));

			StringBuilder buyTip = new StringBuilder("<html><b>").append(escapeHtml(item.name)).append("</b><br>");
			buyTip.append("Market buy: <font color='#FF7070'>").append(FlipItemPanel.formatGp(item.buyPrice)).append("</font><br>");
			buyTip.append("<font color='#666666'>Right-click anywhere to queue a Buy on the GE · Click for insights · Shift+click or double-click to open on 07flip.com</font></html>");
			buyLabel.setToolTipText(buyTip.toString());
			buyLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}
		else
		{
			buyLabel = new JLabel("No recent price data");
			buyLabel.setFont(Fonts.SM);
			buyLabel.setForeground(new Color(0x555555));
		}

		// ── SELL (green) ──────────────────────────────────────────────────────
		JLabel sellLabel = new JLabel("");
		if (hasPrice)
		{
			sellLabel = new JLabel("<html><b>Sell:</b>  " + FlipItemPanel.formatGpCompact(item.sellPrice) + "</html>");
			sellLabel.setFont(Fonts.SM);
			sellLabel.setForeground(GREEN);

			StringBuilder sellTip = new StringBuilder("<html><b>").append(escapeHtml(item.name)).append("</b><br>");
			sellTip.append("Market sell: <font color='#00C27A'>").append(FlipItemPanel.formatGp(item.sellPrice)).append("</font><br>");
			sellTip.append("<font color='#666666'>Right-click anywhere to queue a Buy on the GE · Click for insights · Shift+click or double-click to open on 07flip.com</font></html>");
			sellLabel.setToolTipText(sellTip.toString());
			sellLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		// Match FlipItemPanel's free-user buy target: queue at the sell-side
		// (instant-fill ask). For premium users with a rec_buy_price we'd use
		// that instead, but the search payload doesn't carry rec prices —
		// search is the "look anything up" surface, not the curated feed.
		final long buyTarget = hasPrice ? item.sellPrice : 0L;

		// ── PROFIT + ROI ──────────────────────────────────────────────────────
		JLabel profitLabel = new JLabel("");
		if (item.profit != null && hasPrice)
		{
			String sign = item.profit >= 0 ? "+" : "";
			Color  fg   = item.profit >= 0 ? GREEN : new Color(0xE85050);
			profitLabel = new JLabel("<html><font color='" + (item.profit >= 0 ? "#00C27A" : "#E85050") + "'>"
				+ sign + FlipItemPanel.formatGpCompact(item.profit) + "</font></html>");
			profitLabel.setFont(Fonts.SM);
			profitLabel.setForeground(fg);

			StringBuilder tip = new StringBuilder("<html><b>").append(escapeHtml(item.name)).append("</b><br>");
			tip.append("Market margin: <font color='").append(item.profit >= 0 ? "#00C27A" : "#E85050").append("'>")
				.append(sign).append(FlipItemPanel.formatGp(item.profit)).append("</font>");
			if (item.roi != null)
			{
				tip.append("  (").append(String.format("%.2f", item.roi)).append("% ROI)");
			}
			tip.append("<br>");
			if (item.buyLimit > 0)
			{
				tip.append("GE buy limit: ").append(item.buyLimit)
					.append(" <font color='#888888'>(resets every 4 hours)</font><br>");
			}
			if (item.hourlyVolume != null && item.hourlyVolume > 0)
			{
				tip.append("Hourly volume: ").append(FlipItemPanel.formatGp(item.hourlyVolume)).append("<br>");
			}
			tip.append("</html>");
			profitLabel.setToolTipText(tip.toString());
		}

		// ── TEXT COLUMN (4 rows — matches FlipItemPanel) ──────────────────────
		JPanel textPanel = new JPanel(new GridLayout(4, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameRow);
		textPanel.add(buyLabel);
		textPanel.add(sellLabel);
		textPanel.add(profitLabel);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		// Row-level click → Item insights; shift/double click → website.
		ClickRouter.attach(this, plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(nameLabel,   plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(buyLabel,    plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(sellLabel,   plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(profitLabel, plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(scoreLabel,  plugin, item.itemId, item.name);

		// Right-click on any inner label forwards to the same queueGeBuy call
		// as the row-level handler — Swing doesn't bubble mouse events, so
		// without these the inner labels would swallow the right-click and
		// the row's MouseListener would never fire.
		final JLabel sellLabelF   = sellLabel;
		final JLabel profitLabelF = profitLabel;
		MouseAdapter rightClickQueueBuy = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null && hasPrice)
				{
					plugin.queueGeBuy(item.itemId, buyTarget, item.name);
					e.consume();
				}
			}
		};
		buyLabel.addMouseListener(rightClickQueueBuy);
		sellLabelF.addMouseListener(rightClickQueueBuy);
		profitLabelF.addMouseListener(rightClickQueueBuy);
		nameLabel.addMouseListener(rightClickQueueBuy);
		scoreLabel.addMouseListener(rightClickQueueBuy);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
				nameRow.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				textPanel.setBackground(bg);
				nameRow.setBackground(bg);
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null && hasPrice)
				{
					plugin.queueGeBuy(item.itemId, buyTarget, item.name);
				}
			}
		});

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static String escapeHtml(String s)
	{
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
