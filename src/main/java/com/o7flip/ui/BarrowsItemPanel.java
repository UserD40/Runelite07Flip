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

import com.o7flip.model.BarrowsItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class BarrowsItemPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);

	public BarrowsItemPanel(BarrowsItem item, ItemManager itemManager, boolean odd)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(6, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		int iconId = item.itemIdBroken > 0 ? item.itemIdBroken : item.itemIdRepaired;
		JLabel iconLabel = FlipItemPanel.buildIcon(iconId, itemManager);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(bg);

		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(nameLabel);
		textPanel.add(Box.createVerticalStrut(4));

		JLabel buyLbl  = new JLabel("Buy: " + FlipItemPanel.formatGp(item.brokenBuyPrice));
		buyLbl.setFont(Fonts.SM);
		buyLbl.setForeground(new Color(0xFF7070));

		JLabel sellLbl = new JLabel("  \u2192  Sell: " + FlipItemPanel.formatGp(item.repairedAfterTax));
		sellLbl.setFont(Fonts.SM);
		sellLbl.setForeground(new Color(0x00C27A));

		JPanel buySellRow = new JPanel();
		buySellRow.setLayout(new BoxLayout(buySellRow, BoxLayout.X_AXIS));
		buySellRow.setBackground(bg);
		buySellRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		buySellRow.add(buyLbl);
		buySellRow.add(sellLbl);
		buySellRow.add(Box.createHorizontalGlue());
		textPanel.add(buySellRow);
		textPanel.add(Box.createVerticalStrut(3));

		JLabel npcRprLbl = new JLabel("NPC: " + FlipItemPanel.formatGp(item.npcRepairCost));
		npcRprLbl.setFont(Fonts.SM);
		npcRprLbl.setForeground(new Color(0x888888));

		JLabel pohRprLbl = new JLabel("  \u00B7  POH: " + FlipItemPanel.formatGp(item.pohRepairCost));
		pohRprLbl.setFont(Fonts.SM);
		pohRprLbl.setForeground(new Color(0x888888));

		JPanel repairRow = new JPanel();
		repairRow.setLayout(new BoxLayout(repairRow, BoxLayout.X_AXIS));
		repairRow.setBackground(bg);
		repairRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		repairRow.add(npcRprLbl);
		repairRow.add(pohRprLbl);
		repairRow.add(Box.createHorizontalGlue());
		textPanel.add(repairRow);
		textPanel.add(Box.createVerticalStrut(3));

		String npcSign = item.npcProfit >= 0 ? "+" : "";
		String pohSign = item.pohProfit >= 0 ? "+" : "";
		Color  npcCol  = item.npcProfit >= 0 ? new Color(0x00C27A) : new Color(0xFF5555);
		Color  pohCol  = item.pohProfit >= 0 ? new Color(0x00C27A) : new Color(0xFF5555);

		JLabel profHdrLbl = new JLabel("Profit: ");
		profHdrLbl.setFont(Fonts.SM);
		profHdrLbl.setForeground(new Color(0x888888));

		JLabel npcProfLbl = new JLabel("NPC " + npcSign + FlipItemPanel.formatGp(item.npcProfit));
		npcProfLbl.setFont(Fonts.SM);
		npcProfLbl.setForeground(npcCol);

		JLabel pohProfLbl = new JLabel("  \u00B7  POH " + pohSign + FlipItemPanel.formatGp(item.pohProfit));
		pohProfLbl.setFont(Fonts.SM);
		pohProfLbl.setForeground(pohCol);

		JPanel profitRow = new JPanel();
		profitRow.setLayout(new BoxLayout(profitRow, BoxLayout.X_AXIS));
		profitRow.setBackground(bg);
		profitRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		profitRow.add(profHdrLbl);
		profitRow.add(npcProfLbl);
		profitRow.add(pohProfLbl);
		profitRow.add(Box.createHorizontalGlue());
		textPanel.add(profitRow);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
				buySellRow.setBackground(HOVER_BG);
				repairRow.setBackground(HOVER_BG);
				profitRow.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				textPanel.setBackground(bg);
				buySellRow.setBackground(bg);
				repairRow.setBackground(bg);
				profitRow.setBackground(bg);
			}
			@Override
			public void mouseClicked(MouseEvent e)
			{
				FlipItemPanel.openUrl("https://07flip.com/item/" + item.itemIdRepaired);
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}
}
