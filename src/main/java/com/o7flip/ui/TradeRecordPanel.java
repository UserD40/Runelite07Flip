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

import com.o7flip.model.TradeRecord;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TradeRecordPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color BUY_COL  = new Color(0x5B9BD5);
	private static final Color SELL_COL = new Color(0x00C27A);
	private static final Color PART_COL = new Color(0xE8A838);

	private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("d MMM");

	public TradeRecordPanel(TradeRecord trade, ItemManager itemManager, boolean odd)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(7, 10, 7, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(trade.itemId, itemManager);

		JLabel nameLabel = new JLabel(trade.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		String typeWord = trade.isBuy ? "Bought" : "Sold";
		Color  typeCol  = trade.partial ? PART_COL : (trade.isBuy ? BUY_COL : SELL_COL);
		String partSuffix = trade.partial ? " (partial)" : "";
		JLabel typeLabel = new JLabel(
			typeWord + partSuffix + ": " + trade.quantity + " \u00D7 " + FlipItemPanel.formatGp(trade.priceEach) + " gp");
		typeLabel.setFont(Fonts.SM);
		typeLabel.setForeground(typeCol);

		JLabel dateLabel = new JLabel(DATE_FMT.format(new Date(trade.timestamp)));
		dateLabel.setFont(Fonts.SM);
		dateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameLabel);
		textPanel.add(typeLabel);
		textPanel.add(dateLabel);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}
}
