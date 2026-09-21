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
import com.o7flip.model.Models.DipItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;

public class DipItemPanel extends JPanel
{
	public DipItemPanel(DipItem item, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = FlipItemPanel.frame(this, odd);
		JLabel iconLabel = FlipItemPanel.buildIcon(item.itemId, itemManager);

		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		JLabel badge = FlipItemPanel.badge("↓ 24h", "#7DA8FF");
		badge.setToolTipText("Buy price is at least 5% below its 24-hour average.");

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setOpaque(false);
		nameRow.add(nameLabel, BorderLayout.CENTER);
		nameRow.add(badge, BorderLayout.EAST);

		String ref = item.avg24hBuy != null
			? "  <font color='#888888'>· 24h avg: </font><font color='#FFE07A'>"
				+ FlipItemPanel.formatGpCompact(item.avg24hBuy) + "</font>"
			: "";
		JLabel priceRow = new JLabel("<html><font color='#FF7070'><b>Buy:</b> "
			+ FlipItemPanel.formatGpCompact(item.buyPrice) + "</font>" + ref + "</html>");
		priceRow.setFont(Fonts.SM);
		priceRow.setToolTipText("<html><b>" + FlipItemPanel.escapeHtml(item.name) + "</b><br>"
			+ "Current buy: <font color='#FF7070'>" + FlipItemPanel.formatGp(item.buyPrice) + "</font>"
			+ (item.avg24hBuy != null ? "<br>24-hour average: " + FlipItemPanel.formatGp(item.avg24hBuy) + " gp" : "")
			+ "<br><font color='#666666'>Right-click anywhere to queue a Buy on the GE · Click for insights</font></html>");

		String pct = item.dipPct != null ? String.format("%.1f", Math.abs(item.dipPct)) : null;
		JLabel signalLabel = new JLabel("<html><font color='#00C27A'>"
			+ (pct != null ? "<b>↓ " + pct + "%</b> in 24h" : "Recently dropped") + "</font></html>");
		signalLabel.setFont(Fonts.SM);
		if (pct != null)
		{
			signalLabel.setToolTipText("Buy is " + pct + "% below the 24h average.");
		}

		JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameRow);
		textPanel.add(priceRow);
		textPanel.add(signalLabel);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		ClickRouter.attach(this, plugin, item.itemId, item.name);
		FlipItemPanel.routeLabels(plugin, item.itemId, item.buyPrice, item.name, nameLabel, badge, priceRow, signalLabel);
		FlipItemPanel.hoverAndQueueBuy(this, textPanel, bg, plugin, item.itemId, item.buyPrice, item.name);
	}
}
