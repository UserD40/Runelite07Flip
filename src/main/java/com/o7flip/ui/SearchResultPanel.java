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

import com.o7flip.model.SearchResultItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.Box;
import javax.swing.BoxLayout;
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

public class SearchResultPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color GREEN    = new Color(0x00C27A);
	private static final Color RED      = new Color(0xFF5555);
	private static final Color ORANGE   = new Color(0xFF981F);
	private static final int   STALE    = 30;   // minutes

	public SearchResultPanel(SearchResultItem item, ItemManager itemManager, boolean odd)
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
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean stale    = item.dataAgeMinutes != null && item.dataAgeMinutes > STALE;
		boolean hasPrice = item.buyPrice != null && item.sellPrice != null;

		JLabel priceLabel;
		if (!hasPrice)
		{
			priceLabel = new JLabel("No recent price data");
			priceLabel.setFont(Fonts.SM);
			priceLabel.setForeground(new Color(0x555555));
		}
		else
		{
			String buyColor  = stale ? "#666666" : "#FF7070";
			String sellColor = stale ? "#666666" : "#00C27A";
			priceLabel = new JLabel(
				"<html><font color='" + buyColor  + "'>" + FlipItemPanel.formatGp(item.buyPrice)  + "</font>"
				+ "<font color='#555555'> \u2192 </font>"
				+ "<font color='" + sellColor + "'>" + FlipItemPanel.formatGp(item.sellPrice) + "</font></html>");
			priceLabel.setFont(Fonts.SM);
		}
		priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel profitLabel = new JLabel("");
		if (item.profit != null && hasPrice)
		{
			String sign   = item.profit >= 0 ? "+" : "";
			Color  fg     = item.profit >= 0 ? GREEN : RED;
			String roiStr = item.roi != null ? "  (" + String.format("%.1f", item.roi) + "% ROI)" : "";
			profitLabel = new JLabel(sign + FlipItemPanel.formatGp(item.profit) + roiStr);
			profitLabel.setFont(Fonts.SM);
			profitLabel.setForeground(fg);
		}
		profitLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel volLabel = new JLabel("");
		if (item.hourlyVolume != null && item.hourlyVolume > 0)
		{
			volLabel = new JLabel(FlipItemPanel.formatGp(item.hourlyVolume) + "/hr");
			volLabel.setFont(Fonts.SM);
			volLabel.setForeground(new Color(0x666666));
		}
		volLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(bg);
		textPanel.add(nameLabel);
		textPanel.add(Box.createVerticalStrut(2));
		textPanel.add(priceLabel);
		textPanel.add(Box.createVerticalStrut(2));
		textPanel.add(profitLabel);
		textPanel.add(Box.createVerticalStrut(2));
		textPanel.add(volLabel);

		String ageText  = formatAge(item.dataAgeMinutes);
		Color  ageColor = item.dataAgeMinutes == null ? new Color(0x666666)
			: item.dataAgeMinutes > STALE ? ORANGE : GREEN;
		JLabel ageBadge = new JLabel(
			"<html><center><b>" + ageText + "</b><br><font color='#888888'>ago</font></center></html>");
		ageBadge.setFont(Fonts.SM);
		ageBadge.setForeground(ageColor);
		ageBadge.setHorizontalAlignment(SwingConstants.RIGHT);
		ageBadge.setPreferredSize(new Dimension(40, 0));

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);
		add(ageBadge,  BorderLayout.EAST);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				textPanel.setBackground(bg);
			}
			@Override
			public void mouseClicked(MouseEvent e)
			{
				FlipItemPanel.openUrl("https://07flip.com/item/" + item.itemId);
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static String formatAge(Integer minutes)
	{
		if (minutes == null)
		{
			return "?";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return (minutes / 60) + "h";
	}
}
