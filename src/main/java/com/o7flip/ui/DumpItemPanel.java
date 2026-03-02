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
import com.o7flip.model.DumpItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DumpItemPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color HIGH     = new Color(0xFF4444);  // score >= 70
	private static final Color MID      = new Color(0xFFAA00);  // score >= 30
	private static final Color LOW      = new Color(0x888888);  // score < 30

	public DumpItemPanel(DumpItem item, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(6, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(10, 10, 10, 8));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── Icon ──────────────────────────────────────────────────────────────
		JLabel iconLabel = FlipItemPanel.buildIcon(item.itemId, itemManager);

		// ── Text panel (Y_AXIS) ───────────────────────────────────────────────
		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(bg);

		// Row 1: Name
		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(nameLabel);
		textPanel.add(Box.createVerticalStrut(3));

		// Row 2: Buy price (red) · Profit (green)
		JLabel buyLbl = new JLabel("Buy: " + FlipItemPanel.formatGp(item.buyPrice));
		buyLbl.setFont(Fonts.SM);
		buyLbl.setForeground(new Color(0xFF7070));

		JLabel profitLbl = new JLabel("  \u00B7  +" + FlipItemPanel.formatGp(item.profit) + " profit");
		profitLbl.setFont(Fonts.SM);
		profitLbl.setForeground(new Color(0x00C27A));

		JPanel buyRow = new JPanel();
		buyRow.setLayout(new BoxLayout(buyRow, BoxLayout.X_AXIS));
		buyRow.setBackground(bg);
		buyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		buyRow.add(buyLbl);
		buyRow.add(profitLbl);
		buyRow.add(Box.createHorizontalGlue());
		textPanel.add(buyRow);
		textPanel.add(Box.createVerticalStrut(3));

		// Row 3: Score · Next dump · Volume
		Color scoreColor = item.dumpScore >= 70 ? HIGH : item.dumpScore >= 30 ? MID : LOW;

		JLabel scoreHdrLbl = new JLabel("Score: ");
		scoreHdrLbl.setFont(Fonts.SM);
		scoreHdrLbl.setForeground(new Color(0x666666));

		JLabel scoreValLbl = new JLabel(String.valueOf(item.dumpScore));
		scoreValLbl.setFont(Fonts.SM);
		scoreValLbl.setForeground(scoreColor);

		String nextStr = item.nextDumpHours != null ? "  \u00B7  Next: " + formatNext(item.nextDumpHours) : "";
		JLabel nextLbl = new JLabel(nextStr);
		nextLbl.setFont(Fonts.SM);
		nextLbl.setForeground(new Color(0x666666));

		String volStr = item.hourlyVolume > 0 ? "  \u00B7  " + FlipItemPanel.formatGp(item.hourlyVolume) + "/hr" : "";
		JLabel volLbl = new JLabel(volStr);
		volLbl.setFont(Fonts.SM);
		volLbl.setForeground(new Color(0x666666));

		JPanel scoreRow = new JPanel();
		scoreRow.setLayout(new BoxLayout(scoreRow, BoxLayout.X_AXIS));
		scoreRow.setBackground(bg);
		scoreRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		scoreRow.add(scoreHdrLbl);
		scoreRow.add(scoreValLbl);
		scoreRow.add(nextLbl);
		scoreRow.add(volLbl);
		scoreRow.add(Box.createHorizontalGlue());
		textPanel.add(scoreRow);
		textPanel.add(Box.createVerticalStrut(3));

		// Row 4: Status · Last dump time
		String lastStr = item.lastDumpHoursAgo != null
			? formatAgo(item.lastDumpHoursAgo) + " ago"
			: "unknown";

		JLabel statusLbl = new JLabel(formatStatus(item.dumpStatus));
		statusLbl.setFont(Fonts.SM);
		statusLbl.setForeground(statusColor(item.dumpStatus));

		JLabel lastLbl = new JLabel("  \u00B7  " + lastStr);
		lastLbl.setFont(Fonts.SM);
		lastLbl.setForeground(new Color(0x666666));

		JPanel statusRow = new JPanel();
		statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
		statusRow.setBackground(bg);
		statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		statusRow.add(statusLbl);
		statusRow.add(lastLbl);
		statusRow.add(Box.createHorizontalGlue());
		textPanel.add(statusRow);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
				buyRow.setBackground(HOVER_BG);
				scoreRow.setBackground(HOVER_BG);
				statusRow.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				textPanel.setBackground(bg);
				buyRow.setBackground(bg);
				scoreRow.setBackground(bg);
				statusRow.setBackground(bg);
			}
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					FlipItemPanel.openUrl("https://07flip.com/item/" + item.itemId);
				}
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && plugin != null)
				{
					JPopupMenu menu = new JPopupMenu();
					JMenuItem buyItem = new JMenuItem("Buy on GE \u2014 " + FlipItemPanel.formatGp(item.sellPrice));
					buyItem.addActionListener(ae -> plugin.queueGeBuy(item.itemId, item.sellPrice, item.name));
					menu.add(buyItem);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static String formatStatus(String status)
	{
		if (status == null)
		{
			return "Pattern";
		}
		switch (status)
		{
			case "dumping":  return "Dumping now";
			case "due_soon": return "Due soon";
			case "pattern":  return "Pattern";
			default:         return "Watching";
		}
	}

	private static Color statusColor(String status)
	{
		if (status == null)
		{
			return new Color(0x888888);
		}
		switch (status)
		{
			case "dumping":  return new Color(0xFF5555);
			case "due_soon": return new Color(0xFF981F);
			case "pattern":  return new Color(0x888888);
			default:         return new Color(0x666666);
		}
	}

	private static String formatAgo(double hours)
	{
		if (hours < 2.0)
		{
			return (int) Math.round(hours * 60) + " min";
		}
		if (hours < 24.0)
		{
			return String.format("%.1fh", hours);
		}
		return String.format("%.1fd", hours / 24.0);
	}

	private static String formatNext(double hours)
	{
		if (hours < 2.0)
		{
			return (int) Math.round(hours * 60) + "m";
		}
		if (hours < 24.0)
		{
			return String.format("%.1fh", hours);
		}
		return String.format("%.1fd", hours / 24.0);
	}
}
