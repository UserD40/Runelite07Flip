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
import java.util.concurrent.TimeUnit;

/**
 * One row for the My Trades "Pending" sort — an open position
 * (bought, not yet sold). Right-side label is "Xd held" rather
 * than profit, since profit doesn't realise until sell time.
 */
public class OpenPositionRow extends JPanel
{
	private static final Color ODD_BG  = new Color(0x272727);
	private static final Color BUY_COL = new Color(0x5B9BD5);

	public OpenPositionRow(ProfitCalculator.OpenPosition pos, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(7, 10, 7, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(pos.itemId, itemManager);

		JLabel nameLabel = new JLabel(pos.name != null ? pos.name : "Item " + pos.itemId);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		long avgBuy = pos.remainingQty > 0 ? pos.remainingCostBasis / pos.remainingQty : 0L;
		JLabel costLabel = new JLabel(
			"Held: " + pos.remainingQty + " × " + FlipItemPanel.formatGpCompact(avgBuy) + " gp");
		costLabel.setFont(Fonts.SM);
		costLabel.setForeground(BUY_COL);

		long daysHeld = Math.max(0,
			TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - pos.earliestBuyTimestamp));
		JLabel ageLabel = new JLabel(daysHeld == 0 ? "today" : daysHeld + "d ago");
		ageLabel.setFont(Fonts.SM);
		ageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameLabel);
		textPanel.add(costLabel);
		textPanel.add(ageLabel);

		JLabel investedLabel = new JLabel(
			FlipItemPanel.formatGpCompact(pos.remainingCostBasis) + " gp", SwingConstants.RIGHT);
		investedLabel.setFont(Fonts.SM_BOLD);
		investedLabel.setForeground(Color.LIGHT_GRAY);
		investedLabel.setToolTipText(String.format(
			"<html><b>%s gp invested</b><br>%d × %s avg<br>Oldest buy %d day%s ago</html>",
			FlipItemPanel.formatGp(pos.remainingCostBasis),
			pos.remainingQty,
			FlipItemPanel.formatGp(avgBuy),
			daysHeld,
			daysHeld == 1 ? "" : "s"));

		add(iconLabel,     BorderLayout.WEST);
		add(textPanel,     BorderLayout.CENTER);
		add(investedLabel, BorderLayout.EAST);

		ClickRouter.attachInsightsOnly(this, plugin, pos.itemId, pos.name);

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}
}
