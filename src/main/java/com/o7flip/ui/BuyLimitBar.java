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
import net.runelite.client.ui.ColorScheme;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A slim buy-limit cooldown bar shown under a Favourites row (Available sort
 * only). Live-queries the plugin each repaint for the item's remaining 4-hour
 * cooldown and progress, drawing a fill bar + a "Rebuy in HH:MM" / "Available
 * now" caption. A repaint timer in the panel drives the live countdown.
 */
public class BuyLimitBar extends JPanel
{
	private static final Color TRACK = new Color(0x202020);
	private static final Color RED    = new Color(0xE85050);
	private static final Color ORANGE = new Color(0xE8A838);
	private static final Color GREEN  = new Color(0x00C27A);
	private static final int   PAD_X = 10;

	private final O7FlipPlugin plugin;
	private final int itemId;

	public BuyLimitBar(O7FlipPlugin plugin, int itemId)
	{
		this.plugin = plugin;
		this.itemId = itemId;
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBorder(new EmptyBorder(2, PAD_X, 4, PAD_X));
		setPreferredSize(new Dimension(0, 22));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (plugin == null)
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			long ms = plugin.buyLimitCooldownMs(itemId);
			double progress = plugin.buyLimitCooldownProgress(itemId);
			boolean ready = ms <= 0;

			int x = PAD_X;
			int w = getWidth() - PAD_X * 2;
			int barH = getHeight() - 4;
			int barY = 2;
			int radius = Math.min(barH, 8);
			if (w <= 0)
			{
				return;
			}

			// Track.
			g2.setColor(TRACK);
			g2.fillRoundRect(x, barY, w, barH, radius, radius);
			// Fill = elapsed fraction, coloured red → orange → green as it nears ready.
			int fillW = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * w);
			if (fillW > 0)
			{
				g2.setColor(ready ? GREEN : progressColour(progress));
				g2.fillRoundRect(x, barY, fillW, barH, radius, radius);
			}

			// Countdown text centred inside the bar.
			String text = ready ? "Buy limit available now" : "Rebuy in " + formatRemaining(ms);
			g2.setFont(Fonts.SM);
			g2.setColor(Color.WHITE);
			FontMetrics fm = g2.getFontMetrics();
			int tx = x + (w - fm.stringWidth(text)) / 2;
			int ty = barY + (barH + fm.getAscent() - fm.getDescent()) / 2;
			g2.drawString(text, tx, ty);
		}
		finally
		{
			g2.dispose();
		}
	}

	/** Red (far) → orange (halfway) → green (almost ready). */
	private static Color progressColour(double p)
	{
		p = Math.max(0.0, Math.min(1.0, p));
		if (p < 0.5)
		{
			return lerp(RED, ORANGE, p / 0.5);
		}
		return lerp(ORANGE, GREEN, (p - 0.5) / 0.5);
	}

	private static Color lerp(Color a, Color b, double t)
	{
		t = Math.max(0.0, Math.min(1.0, t));
		int r = (int) Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t);
		int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
		int bl = (int) Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t);
		return new Color(r, g, bl);
	}

	/** "1h 23m" when ≥ 1h, else "12m 04s". */
	private static String formatRemaining(long ms)
	{
		long totalSec = ms / 1000L;
		long h = totalSec / 3600L;
		long m = (totalSec % 3600L) / 60L;
		long s = totalSec % 60L;
		if (h > 0)
		{
			return h + "h " + m + "m";
		}
		return String.format("%dm %02ds", m, s);
	}
}
