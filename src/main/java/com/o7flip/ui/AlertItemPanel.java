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
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class AlertItemPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color ORANGE   = new Color(0xFF981F);
	private static final Color GRAY_LBL = new Color(0x888888);
	private static final Color SEP_COL  = new Color(0x3A3A3A);

	public AlertItemPanel(AlertItem alert, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(bg);
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── Row 1: Icon + Name + link arrow ───────────────────────────────────
		JPanel headerRow = new JPanel(new java.awt.BorderLayout(8, 0));
		headerRow.setOpaque(false);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel iconLabel = FlipItemPanel.buildIcon(alert.itemId, itemManager);

		JLabel nameLabel = new JLabel(alert.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		JLabel linkLabel = new JLabel("\u2197");
		linkLabel.setFont(Fonts.SM_BOLD);
		linkLabel.setForeground(ORANGE);

		headerRow.add(iconLabel, java.awt.BorderLayout.WEST);
		headerRow.add(nameLabel, java.awt.BorderLayout.CENTER);
		headerRow.add(linkLabel, java.awt.BorderLayout.EAST);

		// ── Tier badge ─────────────────────────────────────────────────────────
		JLabel tierBadge = buildTierBadge(alert.tier);
		tierBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
		tierBadge.setBorder(new EmptyBorder(5, 0, 2, 0));

		// ── Price section: Current Price | Est. Sell Target ───────────────────
		JPanel priceLabels = row2();
		priceLabels.setBorder(new EmptyBorder(4, 0, 1, 0));
		priceLabels.add(lbl("Current Price",    Fonts.SM, GRAY_LBL));
		priceLabels.add(lbl("Est. Sell Target", Fonts.SM, GRAY_LBL));

		JPanel priceValues = row2();
		priceValues.setBorder(new EmptyBorder(0, 0, 4, 0));
		priceValues.add(lbl(formatGpFull(alert.currentPrice), Fonts.SM, Color.WHITE));
		JLabel targetLbl = new JLabel("<html><font color='#00C27A'><b>"
			+ formatGpFull(alert.sellTarget) + "</b>"
			+ " (+" + String.format("%.1f", alert.upsidePct) + "%)</font></html>");
		targetLbl.setFont(Fonts.SM);
		priceValues.add(targetLbl);

		// ── 90d stats section: High | Low ─────────────────────────────────────
		JPanel statsLabels = row2();
		statsLabels.setBorder(new EmptyBorder(4, 0, 1, 0));
		statsLabels.add(lbl("90d High", Fonts.SM, GRAY_LBL));
		statsLabels.add(lbl("90d Low",  Fonts.SM, GRAY_LBL));

		JPanel statsValues = row2();
		statsValues.setBorder(new EmptyBorder(0, 0, 2, 0));
		statsValues.add(lbl(formatGpFull(alert.high90d), Fonts.SM, Color.WHITE));
		statsValues.add(lbl(formatGpFull(alert.low90d),  Fonts.SM, Color.WHITE));

		// ── Hold time + drawdown ───────────────────────────────────────────────
		JLabel holdRow = new JLabel("<html><b>" + alert.holdTime + "</b>"
			+ "&nbsp;&nbsp;<font color='#888888'>\u00B7</font>&nbsp;&nbsp;"
			+ "<font color='#FF981F'>Drawdown " + String.format("%.1f", alert.drawdownPct) + "%</font></html>");
		holdRow.setFont(Fonts.SM);
		holdRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		holdRow.setBorder(new EmptyBorder(0, 0, 4, 0));

		// ── Timestamp ──────────────────────────────────────────────────────────
		JLabel timeLabel = lbl("Detected " + formatTimestamp(alert.detectedAt), Fonts.SM, new Color(0x555555));
		timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		timeLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

		// ── Assemble ───────────────────────────────────────────────────────────
		add(headerRow);
		add(cardSep());
		add(tierBadge);
		add(priceLabels);
		add(priceValues);
		add(cardSep());
		add(statsLabels);
		add(statsValues);
		add(holdRow);
		add(cardSep());
		add(timeLabel);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				repaint();
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				repaint();
			}
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					FlipItemPanel.openUrl("https://07flip.com/item/" + alert.itemId);
				}
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && plugin != null)
				{
					JPopupMenu menu = new JPopupMenu();
					JMenuItem buyItem = new JMenuItem("Buy on GE \u2014 " + formatGpFull(alert.currentPrice));
					buyItem.addActionListener(ae -> plugin.queueGeBuy(alert.itemId, alert.currentPrice, alert.name));
					menu.add(buyItem);
					JMenuItem sellItem = new JMenuItem("Sell on GE \u2014 " + formatGpFull(alert.sellTarget));
					sellItem.addActionListener(ae -> plugin.queueGeSell(alert.itemId, alert.sellTarget, alert.name));
					menu.add(sellItem);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	// =========================================================================
	// Layout helpers
	// =========================================================================

	private static JLabel buildTierBadge(String tier)
	{
		String text;
		Color  color;
		if ("conviction".equalsIgnoreCase(tier))
		{
			text  = "CONVICTION";
			color = new Color(0xFF981F);
		}
		else if ("high_probability".equalsIgnoreCase(tier))
		{
			text  = "HIGH PROB";
			color = new Color(0xFFD700);
		}
		else
		{
			text  = tier != null ? tier.toUpperCase() : "";
			color = new Color(0x888888);
		}
		JLabel lbl = new JLabel(text);
		lbl.setFont(Fonts.SM_BOLD);
		lbl.setForeground(color);
		return lbl;
	}

	private static JPanel row2()
	{
		JPanel p = new JPanel(new GridLayout(1, 2, 6, 0));
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		return p;
	}

	private static JLabel lbl(String text, Font font, Color fg)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(fg);
		return l;
	}

	private static JPanel cardSep()
	{
		JPanel sep = new JPanel();
		sep.setBackground(SEP_COL);
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		sep.setPreferredSize(new Dimension(0, 1));
		sep.setAlignmentX(Component.LEFT_ALIGNMENT);
		return sep;
	}

	// =========================================================================
	// Formatting
	// =========================================================================

	private static String formatGpFull(long amount)
	{
		return String.format("%,d gp", amount);
	}

	/** "2026-02-14T20:30:00.000Z" → "Feb 14 · 20:30" */
	private static String formatTimestamp(String iso)
	{
		try
		{
			String[] parts  = iso.split("T");
			String[] date   = parts[0].split("-");
			String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
			int month = Integer.parseInt(date[1]);
			int day   = Integer.parseInt(date[2]);
			String time = parts[1].substring(0, 5);
			return months[month - 1] + " " + day + " \u00B7 " + time;
		}
		catch (Exception e)
		{
			return iso;
		}
	}
}
