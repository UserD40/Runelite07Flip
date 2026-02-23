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

import com.o7flip.model.BarrowsSet;
import com.o7flip.util.Fonts;
import com.o7flip.util.ItemIds;
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

public class BarrowsSetPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color GREEN    = new Color(0x00C27A);
	private static final Color RED      = new Color(0xFF5555);

	public BarrowsSetPanel(BarrowsSet set, ItemManager itemManager, boolean odd, Runnable onDrillDown)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		int iconId = set.iconItemId > 0 ? set.iconItemId : ItemIds.forBarrows(set.shortName);
		JLabel iconLabel = FlipItemPanel.buildIcon(iconId, itemManager);

		// ── Text panel (Y_AXIS) ───────────────────────────────────────────────
		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(bg);

		JLabel nameLabel = new JLabel(set.shortName);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(nameLabel);
		textPanel.add(Box.createVerticalStrut(3));

		JLabel buyLbl = new JLabel("Buy: " + FlipItemPanel.formatGp(set.totalBrokenCost));
		buyLbl.setFont(Fonts.SM);
		buyLbl.setForeground(new Color(0xFF7070));
		buyLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(buyLbl);
		textPanel.add(Box.createVerticalStrut(2));

		JLabel repairLbl = new JLabel("Repair: " + FlipItemPanel.formatGp(set.totalPohRepairCost));
		repairLbl.setFont(Fonts.SM);
		repairLbl.setForeground(new Color(0x888888));
		repairLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(repairLbl);
		textPanel.add(Box.createVerticalStrut(3));

		String stratText = "sell_set".equals(set.bestStrategy) ? "Sell as set" : "Sell individual";
		String volText   = set.dailyVolume > 0 ? "  \u00B7  " + set.dailyVolume + "/day" : "";
		JLabel stratLabel = new JLabel(stratText + volText + "  \u203A");
		stratLabel.setFont(Fonts.SM);
		stratLabel.setForeground(new Color(0xAAAAAA));
		stratLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(stratLabel);

		// ── Right badge: best profit ──────────────────────────────────────────
		boolean profitable = set.bestProfit > 0;
		JLabel profitLabel = new JLabel(
			"<html><center><b>" + (profitable ? "+" : "") + FlipItemPanel.formatGp(set.bestProfit)
			+ "</b><br><font color='#888888'>profit</font></center></html>");
		profitLabel.setFont(Fonts.SM);
		profitLabel.setForeground(profitable ? GREEN : RED);
		profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		profitLabel.setPreferredSize(new Dimension(56, 0));

		add(iconLabel,   BorderLayout.WEST);
		add(textPanel,   BorderLayout.CENTER);
		add(profitLabel, BorderLayout.EAST);

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
				if (onDrillDown != null)
				{
					onDrillDown.run();
				}
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}
}
