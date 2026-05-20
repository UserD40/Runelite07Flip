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

import com.o7flip.model.ScreenerPreset;
import com.o7flip.util.Fonts;
import net.runelite.client.ui.ColorScheme;
import javax.swing.BorderFactory;
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
import java.util.function.Consumer;

/**
 * Clickable card representing a single screener preset on the list view.
 * Click → drill into the detail view that shows the preset's matching
 * items. Premium-gated rows are rendered with a muted "Unlock" CTA instead
 * of the count chip.
 */
public class ScreenerPresetCard extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color HOVER_BG  = new Color(0x3A3A3A);
	private static final Color ORANGE    = new Color(0xFF981F);

	public ScreenerPresetCard(ScreenerPreset preset, boolean odd, Consumer<ScreenerPreset> onClick)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(10, 12, 10, 12));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── LEFT: name + description ─────────────────────────────────────────
		JLabel title = new JLabel(preset.name);
		title.setFont(Fonts.BOLD);
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		String descText = preset.description != null && !preset.description.isEmpty()
			? preset.description : "—";
		JLabel desc = new JLabel("<html><font color='#888888'>" + escapeHtml(descText) + "</font></html>");
		desc.setFont(Fonts.SM);
		desc.setBorder(new EmptyBorder(2, 0, 0, 0));
		desc.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel leftCol = new JPanel();
		leftCol.setLayout(new javax.swing.BoxLayout(leftCol, javax.swing.BoxLayout.Y_AXIS));
		leftCol.setBackground(bg);
		leftCol.add(title);
		leftCol.add(desc);

		// ── RIGHT: count chip OR unlock CTA ──────────────────────────────────
		JLabel rightChip;
		if (preset.premiumRequired)
		{
			rightChip = new JLabel("<html><font color='#FF981F'>🔒 Unlock</font></html>");
			rightChip.setToolTipText("Matches are premium-only. Click to open the upgrade page.");
		}
		else
		{
			rightChip = new JLabel("<html><font color='#FFFFFF'><b>"
				+ preset.count + "</b></font>  <font color='#888888'>›</font></html>");
			rightChip.setToolTipText(preset.count + " item" + (preset.count == 1 ? "" : "s") + " match this preset right now.");
		}
		rightChip.setFont(Fonts.BOLD);

		add(leftCol,   BorderLayout.CENTER);
		add(rightChip, BorderLayout.EAST);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				leftCol.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				leftCol.setBackground(bg);
			}
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (onClick != null && !javax.swing.SwingUtilities.isRightMouseButton(e))
				{
					onClick.accept(preset);
				}
			}
		});

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	/**
	 * Empty-state placeholder shown in the "Custom screeners" section when
	 * the user has none. Clicking opens the website's screener editor.
	 */
	public static JPanel buildCreateOwnCard(Runnable onClick)
	{
		JPanel card = new JPanel(new BorderLayout(8, 0));
		card.setBackground(ColorScheme.DARK_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			new EmptyBorder(2, 8, 2, 8),
			BorderFactory.createDashedBorder(new Color(0x4A4A4A), 1f, 4f, 4f, true)));
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel("+ Create your own screener");
		title.setFont(Fonts.BOLD);
		title.setForeground(ORANGE);

		JLabel hint = new JLabel("<html><font color='#888888'>Build a custom indicator preset on 07flip.com</font></html>");
		hint.setFont(Fonts.SM);
		hint.setBorder(new EmptyBorder(2, 0, 0, 0));

		JPanel col = new JPanel();
		col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));
		col.setBackground(ColorScheme.DARK_GRAY_COLOR);
		col.setBorder(new EmptyBorder(8, 4, 8, 4));
		col.add(title);
		col.add(hint);
		card.add(col, BorderLayout.CENTER);

		JLabel arrow = new JLabel("↗");
		arrow.setFont(Fonts.BOLD);
		arrow.setForeground(ORANGE);
		arrow.setBorder(new EmptyBorder(0, 0, 0, 6));
		card.add(arrow, BorderLayout.EAST);

		card.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e) { card.setBackground(new Color(0x3A3A3A)); col.setBackground(new Color(0x3A3A3A)); }
			@Override
			public void mouseExited(MouseEvent e)  { card.setBackground(ColorScheme.DARK_GRAY_COLOR); col.setBackground(ColorScheme.DARK_GRAY_COLOR); }
			@Override
			public void mouseClicked(MouseEvent e) { if (onClick != null) onClick.run(); }
		});

		return card;
	}

	private static String escapeHtml(String s)
	{
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
