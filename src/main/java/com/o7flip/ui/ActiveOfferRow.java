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
import com.o7flip.model.ActiveOfferSnapshot;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * One row for a live GE offer in the Active sort. Shows item icon, name,
 * direction (Buy/Sell), price, a coloured progress bar based on quantity
 * filled, and the qty completed / qty total.
 *
 * Consumes a pre-resolved {@link ActiveOfferSnapshot} so we never touch the
 * client thread from inside the EDT — name resolution happens upstream in
 * the plugin's GE-state sync code.
 */
public class ActiveOfferRow extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color BUY_COL   = new Color(0x5B9BD5);
	private static final Color SELL_COL  = new Color(0x00C27A);
	private static final Color BAR_TRACK = new Color(0x3A3A3A);

	public ActiveOfferRow(ActiveOfferSnapshot offer, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean isBuy = offer.isBuy();
		Color   sideCol = isBuy ? BUY_COL : SELL_COL;

		JLabel iconLabel = FlipItemPanel.buildIcon(offer.itemId, itemManager);

		JLabel nameLabel = new JLabel(offer.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		String typeWord = isBuy ? "Buy" : "Sell";
		JLabel typeLabel = new JLabel(typeWord + " · " + FlipItemPanel.formatGpCompact(offer.price) + " gp ea");
		typeLabel.setFont(Fonts.SM);
		typeLabel.setForeground(sideCol);
		typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		ProgressBar bar = new ProgressBar(offer.quantitySold, offer.totalQuantity, sideCol);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel qtyLabel = new JLabel(offer.quantitySold + " / " + offer.totalQuantity, SwingConstants.RIGHT);
		qtyLabel.setFont(Fonts.SM_BOLD);
		qtyLabel.setForeground(offer.quantitySold >= offer.totalQuantity ? SELL_COL : Color.LIGHT_GRAY);

		JPanel textCol = new JPanel();
		textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
		textCol.setBackground(bg);
		textCol.setAlignmentX(Component.LEFT_ALIGNMENT);
		textCol.add(nameLabel);
		textCol.add(javax.swing.Box.createVerticalStrut(2));
		textCol.add(typeLabel);
		textCol.add(javax.swing.Box.createVerticalStrut(4));
		textCol.add(bar);

		add(iconLabel, BorderLayout.WEST);
		add(textCol,   BorderLayout.CENTER);
		add(qtyLabel,  BorderLayout.EAST);

		ClickRouter.attachInsightsOnly(this, plugin, offer.itemId, offer.name);

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	/**
	 * Tiny progress bar — coloured fill on a grey track, no label inside
	 * (the qty / qty count sits to the right of the row instead). Fully-
	 * filled offers get a brighter green tint so a complete order is
	 * obvious without reading the numbers.
	 */
	private static class ProgressBar extends JPanel
	{
		private static final int HEIGHT = 6;

		private final int filled;
		private final int total;
		private final Color colour;

		ProgressBar(int filled, int total, Color colour)
		{
			this.filled = filled;
			this.total  = total;
			this.colour = colour;
			setOpaque(false);
			setPreferredSize(new Dimension(0, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
			setMinimumSize(new Dimension(50, HEIGHT));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int w = getWidth();
				int h = getHeight();
				int radius = h;

				g2.setColor(BAR_TRACK);
				g2.fillRoundRect(0, 0, w, h, radius, radius);

				if (total > 0 && filled > 0)
				{
					double frac = Math.min(1.0, filled / (double) total);
					int fillW = (int) Math.round(frac * w);
					if (fillW > 0)
					{
						g2.setColor(colour);
						g2.fillRoundRect(0, 0, fillW, h, radius, radius);
					}
				}

				g2.setStroke(new BasicStroke(1f));
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
