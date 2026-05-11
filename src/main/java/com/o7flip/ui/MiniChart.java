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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Standalone (Graphics2D-only) version of the buy/sell sparkline used by
 * {@link BuySellSparkline}, rendered into a {@link BufferedImage} so it can
 * be embedded directly in a RuneLite overlay panel via
 * {@code ImageComponent}. No JPanel / Swing layout involved — overlays paint
 * with raw Graphics2D, which is incompatible with the JPanel-based sparkline.
 *
 * Sized for compact overlay use (typically ~180 × 50 px). Skips axis labels
 * entirely — they don't fit at this size and the user has the price values
 * right above in the overlay's text rows.
 */
public final class MiniChart
{
	private static final Color BUY_COL  = new Color(0xFF7070);
	private static final Color SELL_COL = new Color(0x00C27A);
	private static final Color TRACK    = new Color(0x3A3A3A);
	private static final Color MID_LINE = new Color(0x303030);

	private MiniChart() {}

	/**
	 * Renders the buy/sell sparkline into a freshly allocated BufferedImage.
	 * Either array may be null or under-populated:
	 * <ul>
	 *   <li>null on either side → that line is simply not drawn</li>
	 *   <li>{@code null} / zero entries inside an array → polyline breaks
	 *       cleanly instead of bridging the gap</li>
	 *   <li>fewer than 2 valid points across both series → a dashed placeholder
	 *       track is rendered so the overlay layout stays stable</li>
	 * </ul>
	 *
	 * @param width   image width in pixels
	 * @param height  image height in pixels
	 * @param buy     buy-side history (oldest → newest), or null
	 * @param sell    sell-side history (oldest → newest), or null
	 * @return a new image — callers own it and may cache between frames
	 */
	public static BufferedImage render(int width, int height, Long[] buy, Long[] sell)
	{
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			paint(g2, width, height, buy, sell);
		}
		finally
		{
			g2.dispose();
		}
		return img;
	}

	private static void paint(Graphics2D g2, int w, int h, Long[] buy, Long[] sell)
	{
		Long[] b = buy  != null ? buy  : new Long[0];
		Long[] s = sell != null ? sell : new Long[0];

		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (Long v : b) { if (v != null && v > 0) { if (v < min) min = v; if (v > max) max = v; } }
		for (Long v : s) { if (v != null && v > 0) { if (v < min) min = v; if (v > max) max = v; } }

		boolean enoughData = min != Long.MAX_VALUE && max > min;
		if (!enoughData)
		{
			// Dashed placeholder so the overlay reserves the height even with
			// no data — keeps the layout from popping when data lands.
			g2.setColor(TRACK);
			g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
				1f, new float[]{4f, 4f}, 0f));
			g2.drawLine(0, h / 2, w, h / 2);
			return;
		}

		// Subtle midline so the eye anchors the chart vertically.
		g2.setColor(MID_LINE);
		g2.setStroke(new BasicStroke(1f));
		g2.drawLine(0, h / 2, w, h / 2);

		int n = Math.max(b.length, s.length);
		g2.setStroke(new BasicStroke(1.3f));
		drawSeries(g2, b, n, w, h, min, max, BUY_COL);
		drawSeries(g2, s, n, w, h, min, max, SELL_COL);
	}

	private static void drawSeries(Graphics2D g2, Long[] series, int n, int w, int h, long min, long max, Color colour)
	{
		if (series == null || series.length < 2)
		{
			return;
		}
		g2.setColor(colour);

		int[] xs = new int[n];
		int[] ys = new int[n];
		int runLen = 0;
		for (int i = 0; i < n; i++)
		{
			Long v = i < series.length ? series[i] : null;
			if (v == null || v <= 0)
			{
				if (runLen >= 2)
				{
					g2.drawPolyline(xs, ys, runLen);
				}
				runLen = 0;
				continue;
			}
			xs[runLen] = (int) Math.round((double) i / (n - 1) * (w - 1));
			double norm = (v - min) / (double) (max - min);
			ys[runLen] = (int) Math.round((1.0 - norm) * (h - 1));
			runLen++;
		}
		if (runLen >= 2)
		{
			g2.drawPolyline(xs, ys, runLen);
		}
	}
}
