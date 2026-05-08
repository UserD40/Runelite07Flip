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
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Renders animated "+X gp" / "-X gp" drops near the Grand Exchange interface
 * each time a flip completes. Pure visual feedback — no game-state effect,
 * no state owned by other components.
 */
@Singleton
public class GpDropOverlay extends Overlay
{
	private static final long DROP_LIFE_MS = 1500L;
	private static final int  RISE_PIXELS  = 40;
	private static final int  MAX_DROPS    = 6;
	private static final Font DROP_FONT    = new Font("Dialog", Font.BOLD, 14);

	private static final Color PROFIT_COLOR = new Color(0x00C27A);
	private static final Color LOSS_COLOR   = new Color(0xE85050);
	private static final Color SHADOW_COLOR = new Color(0, 0, 0, 180);

	private final Client client;
	private final Deque<Drop> drops = new ArrayDeque<>();

	@Inject
	public GpDropOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** Queue a drop. Positive amounts render green, negative red. Safe to call from any thread. */
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
		if (drops.isEmpty())
		{
			return null;
		}

		// Anchor: top of the GE interface widget if visible, otherwise top-left of canvas
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

		long now = System.currentTimeMillis();
		graphics.setFont(DROP_FONT);
		FontMetrics fm = graphics.getFontMetrics();

		synchronized (this)
		{
			Iterator<Drop> it = drops.iterator();
			int idx = 0;
			while (it.hasNext())
			{
				Drop d = it.next();
				long age = now - d.startMs;
				if (age >= DROP_LIFE_MS)
				{
					it.remove();
					continue;
				}

				float t = age / (float) DROP_LIFE_MS;
				int alpha = Math.max(0, Math.min(255, (int) (255 * (1 - t))));
				int yOffset = (int) (RISE_PIXELS * t);

				String text = (d.amount >= 0 ? "+" : "") + formatGp(d.amount) + " gp";
				int textWidth = fm.stringWidth(text);

				int x = anchorX - textWidth / 2;
				int y = anchorY - yOffset - idx * 18;

				graphics.setColor(new Color(0, 0, 0, Math.min(alpha, SHADOW_COLOR.getAlpha())));
				graphics.drawString(text, x + 1, y + 1);

				Color base = d.amount >= 0 ? PROFIT_COLOR : LOSS_COLOR;
				graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
				graphics.drawString(text, x, y);

				idx++;
			}
		}

		return null;
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
