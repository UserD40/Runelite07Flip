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

import com.o7flip.model.DecantItem;
import com.o7flip.util.Fonts;
import com.o7flip.util.ItemIds;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DecantItemPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color ORANGE   = new Color(0xFF981F);

	public DecantItemPanel(DecantItem item, ItemManager itemManager, boolean odd)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		int dose   = item.buyDose > 0 ? item.buyDose : 4;
		int iconId = ItemIds.forPotion(item.potionName, dose);
		if (iconId == 0 && item.itemId > 0)
		{
			iconId = item.itemId;
		}
		JLabel iconLabel = FlipItemPanel.buildIcon(iconId, itemManager);

		JLabel nameLabel = new JLabel(item.potionName);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		String buyText = item.buyDose > 0 ? "Buy " + item.buyDose + "-dose" : item.strategy;
		JLabel buyLabel = new JLabel(buyText);
		buyLabel.setFont(Fonts.SM);
		buyLabel.setForeground(new Color(0xFF7070));
		buyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		String sellText = item.sellDose > 0 ? "Sell " + item.sellDose + "-dose" : "";
		JLabel sellLabel = new JLabel(sellText);
		sellLabel.setFont(Fonts.SM);
		sellLabel.setForeground(new Color(0x00C27A));
		sellLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(bg);
		textPanel.add(nameLabel);
		textPanel.add(Box.createVerticalStrut(3));
		textPanel.add(buyLabel);
		textPanel.add(Box.createVerticalStrut(3));
		textPanel.add(sellLabel);

		JLabel roiLabel = new JLabel(
			"<html><center><b>" + String.format("%.1f", item.roiPct) + "%</b>"
			+ "<br><font color='#888888'>ROI</font></center></html>");
		roiLabel.setFont(Fonts.SM);
		roiLabel.setForeground(ORANGE);
		roiLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		roiLabel.setPreferredSize(new Dimension(48, 0));

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);
		add(roiLabel,  BorderLayout.EAST);

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
				if (SwingUtilities.isLeftMouseButton(e))
				{
					FlipItemPanel.openUrl("https://07flip.com/item/" + item.itemId);
				}
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}
}
