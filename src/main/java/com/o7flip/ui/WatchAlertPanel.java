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
import com.o7flip.model.PriceAlert;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
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

/**
 * Row component for one user-created {@link PriceAlert} in the Watch tab.
 * Shows the item, the alert condition (e.g. "Sell ≥ 1.25M"), the latest
 * market price for that side, and a delete control. Clicking the row opens
 * the item's insights; the ✕ deletes the alert via the plugin.
 */
public class WatchAlertPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color GREEN    = new Color(0x00C27A);
	private static final Color GRAY     = new Color(0xAAAAAA);
	private static final Color DELETE   = new Color(0xE85050);

	public WatchAlertPanel(PriceAlert alert, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel icon = FlipItemPanel.buildIcon(alert.itemId, itemManager);

		JLabel nameLabel = new JLabel(alert.name != null && !alert.name.isEmpty() ? alert.name : "Item " + alert.itemId);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(alert.triggered ? GREEN : Color.WHITE);

		String cmp = "above".equalsIgnoreCase(alert.direction) ? "≥" : "≤";
		String sideText = capitalise(alert.side);
		JLabel condLabel = new JLabel(sideText + " " + cmp + " " + FlipItemPanel.formatGpCompact(alert.targetPrice));
		condLabel.setFont(Fonts.SM);
		condLabel.setForeground(new Color(0xC4A052));

		String sub = alert.currentPrice != null && alert.currentPrice > 0
			? "now " + FlipItemPanel.formatGpCompact(alert.currentPrice)
			: "";
		if (alert.triggered)
		{
			sub = sub.isEmpty() ? "triggered" : sub + " · triggered";
		}
		JLabel subLabel = new JLabel(sub);
		subLabel.setFont(Fonts.SM);
		subLabel.setForeground(alert.triggered ? GREEN : GRAY);

		JPanel condRow = new JPanel(new BorderLayout(6, 0));
		condRow.setBackground(bg);
		condRow.add(condLabel, BorderLayout.WEST);
		if (!sub.isEmpty())
		{
			condRow.add(subLabel, BorderLayout.EAST);
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(bg);
		text.add(nameLabel);
		text.add(javax.swing.Box.createVerticalStrut(2));
		text.add(condRow);

		// Delete control on the right edge.
		JLabel del = new JLabel("✕");
		del.setFont(Fonts.BOLD);
		del.setForeground(DELETE);
		del.setBorder(new EmptyBorder(0, 8, 0, 2));
		del.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		del.setToolTipText("Delete this alert");
		del.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (plugin != null && alert.id != null)
				{
					del.setForeground(GRAY);   // optimistic: dim while the delete is in flight
					plugin.deletePriceAlert(alert.id, () -> del.setForeground(DELETE));
				}
				e.consume();
			}
		});

		add(icon, BorderLayout.WEST);
		add(text, BorderLayout.CENTER);
		add(del,  BorderLayout.EAST);

		// Row click → insights (delete keeps its own consumed handler).
		ClickRouter.attachInsightsOnly(this, plugin, alert.itemId, alert.name);
		ClickRouter.attachInsightsOnly(nameLabel, plugin, alert.itemId, alert.name);
		ClickRouter.attachInsightsOnly(condLabel, plugin, alert.itemId, alert.name);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				text.setBackground(HOVER_BG);
				condRow.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				text.setBackground(bg);
				condRow.setBackground(bg);
			}
		});

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static String capitalise(String s)
	{
		if (s == null || s.isEmpty())
		{
			return "";
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
