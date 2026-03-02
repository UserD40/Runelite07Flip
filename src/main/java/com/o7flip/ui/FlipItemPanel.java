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
import com.o7flip.model.FlipItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class FlipItemPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color GREEN    = new Color(0x00C27A);

	public FlipItemPanel(FlipItem flip, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── ICON ──────────────────────────────────────────────────────────────
		JLabel iconLabel = buildIcon(flip.itemId, itemManager);

		// ── NAME ──────────────────────────────────────────────────────────────
		JLabel nameLabel = new JLabel(flip.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		// ── BUY (red) ─────────────────────────────────────────────────────────
		JLabel buyLabel = new JLabel("Buy:  " + formatGp(flip.buyPrice));
		buyLabel.setFont(Fonts.SM);
		buyLabel.setForeground(new Color(0xFF7070));

		// ── SELL (green) ──────────────────────────────────────────────────────
		JLabel sellLabel = new JLabel("Sell:  " + formatGp(flip.sellPrice));
		sellLabel.setFont(Fonts.SM);
		sellLabel.setForeground(GREEN);

		// ── PROFIT + ROI ───────────────────────────────────────────────────────
		String limitText = flip.buyLimit > 0 ? "  \u00B7  Limit " + flip.buyLimit : "";
		JLabel profitLabel = new JLabel(
			"+" + formatGp(flip.profit) + "  (" + String.format("%.1f", flip.roiPct) + "% ROI)" + limitText);
		profitLabel.setFont(Fonts.SM);
		profitLabel.setForeground(GREEN);

		JPanel textPanel = new JPanel(new GridLayout(4, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameLabel);
		textPanel.add(buyLabel);
		textPanel.add(sellLabel);
		textPanel.add(profitLabel);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

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
					openUrl("https://07flip.com/item/" + flip.itemId);
				}
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && plugin != null)
				{
					JPopupMenu menu = new JPopupMenu();
					JMenuItem buyItem = new JMenuItem("Buy on GE \u2014 " + formatGp(flip.sellPrice));
					buyItem.addActionListener(ae -> plugin.queueGeBuy(flip.itemId, flip.sellPrice, flip.name));
					menu.add(buyItem);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	// =========================================================================
	// Static helpers shared by all item panels
	// =========================================================================

	static JLabel buildIcon(int itemId, ItemManager itemManager)
	{
		JLabel lbl = new JLabel();
		lbl.setPreferredSize(new Dimension(32, 32));
		lbl.setHorizontalAlignment(SwingConstants.CENTER);
		if (itemManager != null && itemId > 0)
		{
			AsyncBufferedImage img = itemManager.getImage(itemId);
			img.addTo(lbl);
		}
		return lbl;
	}

	public static String formatGp(long amount)
	{
		return String.format("%,d", amount);
	}

	static void openUrl(String url)
	{
		LinkBrowser.browse(url);
	}
}
