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

import com.o7flip.model.SpikeItem;
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
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SpikeItemPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color GREEN    = new Color(0x00C27A);

	public SpikeItemPanel(SpikeItem item, ItemManager itemManager, boolean odd)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(6, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(13, 10, 13, 8));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── Icon ──────────────────────────────────────────────────────────────
		JLabel iconLabel = FlipItemPanel.buildIcon(item.itemId, itemManager);

		// ── Name ──────────────────────────────────────────────────────────────
		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		// ── Prices ────────────────────────────────────────────────────────────
		JLabel buyLbl = new JLabel("Buy: " + FlipItemPanel.formatGp(item.buyPrice));
		buyLbl.setFont(Fonts.SM);
		buyLbl.setForeground(new Color(0xFF7070));

		JLabel avgLbl = new JLabel("  24h avg: " + FlipItemPanel.formatGp(item.avg24hBuy));
		avgLbl.setFont(Fonts.SM);
		avgLbl.setForeground(new Color(0x666666));

		JPanel priceRow = new JPanel();
		priceRow.setLayout(new BoxLayout(priceRow, BoxLayout.X_AXIS));
		priceRow.setBackground(bg);
		priceRow.add(buyLbl);
		priceRow.add(avgLbl);
		priceRow.add(Box.createHorizontalGlue());

		// ── Volume ────────────────────────────────────────────────────────────
		String volStr   = item.hourlyVolume > 0 ? "Vol: " + FlipItemPanel.formatGp(item.hourlyVolume) + "/hr" : "";
		String limitStr = item.buyLimit > 0 ? (volStr.isEmpty() ? "" : "  ") + "Limit: " + FlipItemPanel.formatGp(item.buyLimit) : "";
		JLabel volLabel = new JLabel(volStr + limitStr);
		volLabel.setFont(Fonts.SM);
		volLabel.setForeground(new Color(0x888888));

		JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 6));
		textPanel.setBackground(bg);
		textPanel.add(nameLabel);
		textPanel.add(priceRow);
		textPanel.add(volLabel);

		// ── Spike badge ───────────────────────────────────────────────────────
		JLabel spikeBadge = new JLabel(
			"<html><center><b><font color='#00C27A'>+"
			+ String.format("%.1f%%", item.spikePct)
			+ "</font></b><br><font color='#888888'>spike</font></center></html>");
		spikeBadge.setFont(Fonts.SM);
		spikeBadge.setForeground(GREEN);
		spikeBadge.setHorizontalAlignment(SwingConstants.RIGHT);
		spikeBadge.setPreferredSize(new Dimension(52, 0));

		add(iconLabel,  BorderLayout.WEST);
		add(textPanel,  BorderLayout.CENTER);
		add(spikeBadge, BorderLayout.EAST);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
				priceRow.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				textPanel.setBackground(bg);
				priceRow.setBackground(bg);
			}
			@Override
			public void mouseClicked(MouseEvent e)
			{
				FlipItemPanel.openUrl("https://07flip.com/item/" + item.itemId);
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}
}
