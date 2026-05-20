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
import com.o7flip.model.DipItem;
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
 * Row component for /api/runelite/dips entries. The endpoint returns two
 * row shapes distinguished by {@code type}:
 *
 * <ul>
 *   <li>{@code "24h_dip"} — buy price ≥5% below the 24h average. Shows the
 *       24h average and dip %.</li>
 *   <li>{@code "atl"} — buy price within 0.3% of the 1-year floor. Shows
 *       the ATL floor and current price vs. ATL %.</li>
 * </ul>
 *
 * Right-click anywhere on the row queues a Buy on the GE at the row's
 * {@code buy_price}, mirroring the Flips tab UX.
 */
public class DipItemPanel extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color HOVER_BG  = new Color(0x3A3A3A);
	private static final Color GREEN     = new Color(0x00C27A);
	private static final Color RED       = new Color(0xFF7070);
	private static final Color ORANGE    = new Color(0xFF981F);
	private static final Color GRAY      = new Color(0xAAAAAA);

	/** Convenience overload — defaults to the 1d window when caller doesn't
	 *  care which dip-window to label (e.g. cached restore). */
	public DipItemPanel(DipItem item, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		this(item, itemManager, odd, plugin, "1d");
	}

	public DipItemPanel(DipItem item, ItemManager itemManager, boolean odd, O7FlipPlugin plugin, String window)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean isAtl = "atl".equalsIgnoreCase(item.type);

		// ── ICON ──────────────────────────────────────────────────────────────
		JLabel iconLabel = FlipItemPanel.buildIcon(item.itemId, itemManager);

		// ── NAME + type badge ─────────────────────────────────────────────────
		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		String badgeColor = isAtl ? "#FF981F" : "#7DA8FF";
		String badgeText  = isAtl ? "ATL"     : "↓ 24h";
		JLabel badge = new JLabel("<html><font color='" + badgeColor + "'>" + badgeText + "</font></html>");
		badge.setFont(Fonts.BOLD);
		badge.setToolTipText(isAtl
			? "Buy price is within 0.3% of the item's 1-year price floor."
			: "Buy price is at least 5% below its 24-hour average.");

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(bg);
		nameRow.add(nameLabel, BorderLayout.CENTER);
		nameRow.add(badge,     BorderLayout.EAST);

		// ── Row 2 (combined): Buy · reference price ──────────────────────────
		// Was two rows (Buy / ATL or Buy / 24h avg). Now one line so the
		// card stays compact and the user sees the two prices side-by-side
		// for easy comparison.
		String refLabelText = "";
		String refTip = "";
		if (isAtl && item.atlFloor != null)
		{
			refLabelText = "<font color='#888888'>· ATL: </font><font color='#FFE07A'>"
				+ FlipItemPanel.formatGpCompact(item.atlFloor) + "</font>";
			refTip = "<br>1-year floor: " + FlipItemPanel.formatGp(item.atlFloor) + " gp";
		}
		else if (item.avg24hBuy != null)
		{
			refLabelText = "<font color='#888888'>· 24h avg: </font><font color='#FFE07A'>"
				+ FlipItemPanel.formatGpCompact(item.avg24hBuy) + "</font>";
			refTip = "<br>24-hour average: " + FlipItemPanel.formatGp(item.avg24hBuy) + " gp";
		}
		JLabel priceRow = new JLabel("<html>"
			+ "<font color='#FF7070'><b>Buy:</b> " + FlipItemPanel.formatGpCompact(item.buyPrice) + "</font>"
			+ "  " + refLabelText
			+ "</html>");
		priceRow.setFont(Fonts.SM);
		priceRow.setToolTipText("<html><b>" + escapeHtml(item.name) + "</b><br>"
			+ "Current buy: <font color='#FF7070'>" + FlipItemPanel.formatGp(item.buyPrice) + "</font>"
			+ refTip
			+ "<br><font color='#666666'>Right-click anywhere to queue a Buy on the GE · Click for insights</font></html>");
		priceRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// ── Row 3: prominent drop signal ─────────────────────────────────────
		// Window-aware: picks dipPct1d / dipPct7d / dipPct30d to match the
		// user's selected window in the panel pills. Server now ships all
		// three on every dip row (plus the legacy dipPct, which mirrors 1d).
		// ATL: "At ATL" when ≤0.5% above the floor (5-year), otherwise the
		// signed buy_vs_atl_pct. Suppresses the "0.0% vs ATL" redundancy.
		Double dipForWindow = item.dipPct;  // fallback
		String windowLabel = "24h";
		if ("7d".equalsIgnoreCase(window))
		{
			dipForWindow = item.dipPct7d != null ? item.dipPct7d : item.dipPct;
			windowLabel = "7d";
		}
		else if ("30d".equalsIgnoreCase(window))
		{
			dipForWindow = item.dipPct30d != null ? item.dipPct30d : item.dipPct;
			windowLabel = "30d";
		}
		else if ("1d".equalsIgnoreCase(window))
		{
			dipForWindow = item.dipPct1d != null ? item.dipPct1d : item.dipPct;
			windowLabel = "24h";
		}

		Double pct = isAtl ? item.buyVsAtlPct : dipForWindow;
		String signalText;
		String signalColor;
		String signalTooltip;
		if (isAtl)
		{
			if (pct == null || Math.abs(pct) < 0.5)
			{
				signalText = "At all-time low";
				signalColor = "#FF981F";
				signalTooltip = "Buy price is at or within 0.5% of the 5-year floor.";
			}
			else
			{
				String sign = pct > 0 ? "+" : "";
				signalText = "<b>" + sign + String.format("%.1f", pct) + "%</b> above ATL";
				signalColor = pct < 0 ? "#00C27A" : "#FFE07A";
				signalTooltip = "Buy is " + String.format("%.1f", pct) + "% above the 5-year floor.";
			}
		}
		else
		{
			if (pct != null)
			{
				signalText = "<b>↓ " + String.format("%.1f", Math.abs(pct)) + "%</b> in " + windowLabel;
				signalColor = "#00C27A";
				signalTooltip = "Buy is " + String.format("%.1f", Math.abs(pct))
					+ "% below the " + windowLabel + " average.";
			}
			else
			{
				signalText = "Recently dropped";
				signalColor = "#00C27A";
				signalTooltip = "";
			}
		}
		JLabel signalLabel = new JLabel("<html><font color='" + signalColor + "'>"
			+ signalText + "</font></html>");
		signalLabel.setFont(Fonts.SM);
		if (!signalTooltip.isEmpty()) signalLabel.setToolTipText(signalTooltip);

		// 3-row text column — name, prices, drop signal.
		JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameRow);
		textPanel.add(priceRow);
		textPanel.add(signalLabel);

		// Re-aliased so the click + right-click hooks below match the new names.
		JLabel buyLabel = priceRow;
		JLabel refLabel = signalLabel;
		JLabel pctLabel = signalLabel;

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		// Click → Item insights, shift/double-click → website, right-click → buy.
		ClickRouter.attach(this, plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(nameLabel, plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(buyLabel,  plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(refLabel,  plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(pctLabel,  plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(badge,     plugin, item.itemId, item.name);

		MouseAdapter rightClickQueueBuy = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null && item.buyPrice > 0)
				{
					plugin.queueGeBuy(item.itemId, item.buyPrice, item.name);
					e.consume();
				}
			}
		};
		buyLabel.addMouseListener(rightClickQueueBuy);
		refLabel.addMouseListener(rightClickQueueBuy);
		pctLabel.addMouseListener(rightClickQueueBuy);
		nameLabel.addMouseListener(rightClickQueueBuy);
		badge.addMouseListener(rightClickQueueBuy);

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
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null && item.buyPrice > 0)
				{
					plugin.queueGeBuy(item.itemId, item.buyPrice, item.name);
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
