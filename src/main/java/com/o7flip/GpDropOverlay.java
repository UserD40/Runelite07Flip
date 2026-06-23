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
package com.o7flip;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class GpDropOverlay extends Overlay
{
	private static final int  RISE_PIXELS  = 40;
	private static final int  MAX_DROPS    = 6;

	private static final Color SHADOW_COLOR = new Color(0, 0, 0, 180);

	private final Client client;
	private final O7FlipConfig config;
	private final Deque<Drop> drops = new ArrayDeque<>();

	private Font cachedFont;
	private O7FlipConfig.GpDropFontType cachedFontType;
	private int cachedFontSize;

	@Inject
	public GpDropOverlay(Client client, O7FlipConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public synchronized void queue(long amount)
	{
		if (drops.size() >= MAX_DROPS)
		{
			drops.pollFirst();
		}
		drops.addLast(new Drop(amount, System.currentTimeMillis()));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean preview = config.gpDropPreview();
		if (drops.isEmpty() && !preview)
		{
			return null;
		}

		int anchorX, anchorY;
		Widget geRoot = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		if (geRoot != null && !geRoot.isHidden())
		{
			anchorX = geRoot.getCanvasLocation().getX() + geRoot.getWidth() / 2;
			anchorY = geRoot.getCanvasLocation().getY() + 20;
		}
		else
		{
			anchorX = 200;
			anchorY = 80;
		}

		anchorX += config.gpDropOffsetX();
		anchorY += config.gpDropOffsetY();

		long now = System.currentTimeMillis();
		long life = dropLifeMs();
		graphics.setFont(dropFont());
		FontMetrics fm = graphics.getFontMetrics();

		if (preview)
		{
			drawDrop(graphics, fm, 1_250_000L, now % life, anchorX, anchorY, 0);
		}

		synchronized (this)
		{
			Iterator<Drop> it = drops.iterator();
			int idx = preview ? 1 : 0;
			while (it.hasNext())
			{
				Drop d = it.next();
				long age = now - d.startMs;
				if (age >= life)
				{
					it.remove();
					continue;
				}

				drawDrop(graphics, fm, d.amount, age, anchorX, anchorY, idx);
				idx++;
			}
		}

		return null;
	}

	private long dropLifeMs()
	{
		return Math.max(100, config.gpDropDurationMs());
	}

	private void drawDrop(Graphics2D graphics, FontMetrics fm, long amount, long age,
		int anchorX, int anchorY, int idx)
	{
		float t = age / (float) dropLifeMs();
		int alpha = Math.max(0, Math.min(255, (int) (255 * (1 - t))));
		int yOffset = (int) (RISE_PIXELS * t);

		String text = (amount >= 0 ? "+" : "") + formatGp(amount) + " gp";
		int textWidth = fm.stringWidth(text);

		int x = anchorX - textWidth / 2;
		int y = anchorY - yOffset - idx * (fm.getHeight() + 4);

		graphics.setColor(new Color(0, 0, 0, Math.min(alpha, SHADOW_COLOR.getAlpha())));
		graphics.drawString(text, x + 1, y + 1);

		Color base = amount >= 0 ? config.gpDropProfitColor() : config.gpDropLossColor();
		graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
		graphics.drawString(text, x, y);
	}

	private Font dropFont()
	{
		O7FlipConfig.GpDropFontType type = config.gpDropFontType();
		int size = config.gpDropFontSize();
		if (cachedFont == null || type != cachedFontType || size != cachedFontSize)
		{
			cachedFont = buildFont(type, size);
			cachedFontType = type;
			cachedFontSize = size;
		}
		return cachedFont;
	}

	private static Font buildFont(O7FlipConfig.GpDropFontType type, int size)
	{
		switch (type)
		{
			case RUNESCAPE:
				return FontManager.getRunescapeFont().deriveFont((float) size);
			case RUNESCAPE_BOLD:
				return FontManager.getRunescapeBoldFont().deriveFont((float) size);
			case SANS:
				return new Font(Font.SANS_SERIF, Font.PLAIN, size);
			case SANS_BOLD:
				return new Font(Font.SANS_SERIF, Font.BOLD, size);
			case DEFAULT_BOLD:
			default:
				return new Font("Dialog", Font.BOLD, size);
		}
	}

	private static String formatGp(long amount)
	{
		long abs = Math.abs(amount);
		if (abs >= 1_000_000L)
		{
			return String.format("%.2fM", amount / 1_000_000.0);
		}
		if (abs >= 1_000L)
		{
			return String.format("%.1fK", amount / 1_000.0);
		}
		return String.valueOf(amount);
	}

	private static final class Drop
	{
		final long amount;
		final long startMs;

		Drop(long amount, long startMs)
		{
			this.amount = amount;
			this.startMs = startMs;
		}
	}
}
