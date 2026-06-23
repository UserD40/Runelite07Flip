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
import com.o7flip.model.HighAlchItem;
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

public class HighAlchPanel extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color HOVER_BG  = new Color(0x3A3A3A);
	private static final Color GREEN     = new Color(0x00C27A);
	private static final Color RED       = new Color(0xFF7070);
	private static final Color GRAY      = new Color(0xAAAAAA);

	public HighAlchPanel(HighAlchItem item, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(item.itemId, itemManager);

		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(bg);
		nameRow.add(nameLabel, BorderLayout.CENTER);

		JLabel pricesLabel = new JLabel("<html>"
			+ "<font color='#FF7070'><b>Buy:</b> " + FlipItemPanel.formatGp(item.buyPrice) + "</font>"
			+ "  <font color='#888888'>·</font>  "
			+ "<font color='#FFE07A'><b>Alch:</b> " + FlipItemPanel.formatGp(item.highAlchValue) + "</font>"
			+ "</html>");
		pricesLabel.setFont(Fonts.SM);
		pricesLabel.setToolTipText("<html><b>" + escapeHtml(item.name) + "</b><br>"
			+ "GE buy: <font color='#FF7070'>" + FlipItemPanel.formatGp(item.buyPrice) + "</font> gp<br>"
			+ "Alch value: <font color='#FFE07A'>" + FlipItemPanel.formatGp(item.highAlchValue) + "</font> gp<br>"
			+ "Rune cost: <font color='#888888'>" + FlipItemPanel.formatGp(item.runeCost) + "</font> gp (your staff toggle applied)<br>"
			+ "Daily volume: " + FlipItemPanel.formatGp(item.dailyVolume) + "<br>"
			+ "<font color='#666666'>Right-click anywhere to queue a Buy on the GE · Click for insights</font></html>");
		pricesLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		String sign = item.profit >= 0 ? "+" : "";
		String colHex = item.profit >= 0 ? "#00C27A" : "#E85050";
		Color profitFg = item.profit >= 0 ? GREEN : new Color(0xE85050);
		long totalProfit = (long) item.profit * (long) Math.max(item.buyLimit, 0);
		String totalSegment = item.buyLimit > 0
			? "  <font color='#888888'>·</font>  <font color='#888888'>×" + item.buyLimit + " = </font>"
				+ "<font color='" + colHex + "'><b>" + sign + FlipItemPanel.formatGpCompact(totalProfit) + "</b></font>"
			: "";
		JLabel profitLabel = new JLabel("<html>"
			+ "<font color='" + colHex + "'>" + sign + FlipItemPanel.formatGp(item.profit) + "</font>"
			+ "<font color='#888888'>/cast</font>"
			+ totalSegment
			+ "</html>");
		profitLabel.setFont(Fonts.SM);
		profitLabel.setForeground(profitFg);
		profitLabel.setToolTipText("<html>Profit per cast: <font color='" + colHex + "'>" + sign
			+ FlipItemPanel.formatGp(item.profit) + "</font> gp<br>"
			+ "Buy limit (per 4h): " + item.buyLimit + "<br>"
			+ "Total profit at limit: <font color='" + colHex + "'>" + sign
			+ FlipItemPanel.formatGp(totalProfit) + "</font> gp</html>");

		JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameRow);
		textPanel.add(pricesLabel);
		textPanel.add(profitLabel);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		ClickRouter.attach(this, plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(nameLabel,    plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(pricesLabel,  plugin, item.itemId, item.name);
		ClickRouter.attachClickOnly(profitLabel,  plugin, item.itemId, item.name);

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
		pricesLabel.addMouseListener(rightClickQueueBuy);
		profitLabel.addMouseListener(rightClickQueueBuy);
		nameLabel.addMouseListener(rightClickQueueBuy);

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
