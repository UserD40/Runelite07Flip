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
import com.o7flip.util.Fonts;
import com.o7flip.util.ProfitCalculator;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CompletedFlipRow extends JPanel
{
	private static final Color ODD_BG     = new Color(0x272727);
	private static final Color PROFIT_COL = new Color(0x00C27A);
	private static final Color LOSS_COL   = new Color(0xE85050);

	private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("d MMM");

	public CompletedFlipRow(ProfitCalculator.CompletedFlip flip, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(7, 10, 7, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(flip.itemId, itemManager);

		JLabel nameLabel = new JLabel(flip.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		long avgBuy  = flip.quantity > 0 ? flip.buyTotal  / flip.quantity : 0L;
		long avgSell = flip.quantity > 0 ? flip.sellTotal / flip.quantity : 0L;
		JLabel detailLabel = new JLabel(
			flip.quantity + " × " + FlipItemPanel.formatGpCompact(avgBuy)
				+ " → " + FlipItemPanel.formatGpCompact(avgSell));
		detailLabel.setFont(Fonts.SM);
		detailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel dateLabel = new JLabel(DATE_FMT.format(new Date(flip.sellTimestamp)));
		dateLabel.setFont(Fonts.SM);
		dateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameLabel);
		textPanel.add(detailLabel);
		textPanel.add(dateLabel);

		String prefix = flip.profit > 0 ? "+" : "";
		JLabel profitLabel = new JLabel(
			prefix + FlipItemPanel.formatGpCompact(flip.profit) + " gp", SwingConstants.RIGHT);
		profitLabel.setFont(Fonts.SM_BOLD);
		profitLabel.setForeground(profitColor(flip.profit));
		profitLabel.setToolTipText(String.format(
			"<html><b>%s%s gp</b> net  <font color='#888888'>(ROI %+.1f%%)</font><br>"
				+ "Bought: %s gp<br>"
				+ "Sold (gross): %s gp<br>"
				+ "<font color='#E85050'>GE tax: −%s gp</font><br>"
				+ "Received (net): %s gp</html>",
			prefix,
			FlipItemPanel.formatGp(flip.profit),
			flip.roiPct,
			FlipItemPanel.formatGp(flip.buyTotal),
			FlipItemPanel.formatGp(flip.sellTotal + flip.tax),
			FlipItemPanel.formatGp(flip.tax),
			FlipItemPanel.formatGp(flip.sellTotal)));

		add(iconLabel,   BorderLayout.WEST);
		add(textPanel,   BorderLayout.CENTER);
		add(profitLabel, BorderLayout.EAST);

		ClickRouter.attachInsightsOnly(this, plugin, flip.itemId, flip.name);

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static Color profitColor(long profit)
	{
		if (profit > 0)
		{
			return PROFIT_COL;
		}
		if (profit < 0)
		{
			return LOSS_COL;
		}
		return Color.LIGHT_GRAY;
	}
}
