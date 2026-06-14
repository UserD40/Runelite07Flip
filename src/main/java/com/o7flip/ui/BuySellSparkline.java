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

import com.o7flip.util.Fonts;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Two-line price sparkline shared by the Insights tab and the Alerts feed.
 * Buy line is orange-red, sell line is green. Both lines render on shared
 * min/max so the vertical gap between them visually represents the live
 * margin. Now also draws a basic Y axis (3 gp ticks: low / mid / high)
 * and X axis (start / now) so users can read the price range without
 * hovering.
 *
 * Either array may be null or under-populated — the component is forgiving:
 * <ul>
 *   <li>null on either side → that line is simply not drawn</li>
 *   <li>{@code null} entries inside an array → polyline breaks cleanly,
 *       not interpolated through</li>
 *   <li>fewer than 2 valid points across both series → centred placeholder
 *       "Collecting price history…" with a dashed track. Reserves the same
 *       vertical space as a populated chart so neighbour cards don't
 *       jitter when scrolled past.</li>
 * </ul>
 */
public class BuySellSparkline extends JPanel
{
	public static final Color BUY_COL  = new Color(0xFF7070);
	public static final Color SELL_COL = new Color(0x00C27A);

	private static final Color GRAY_LBL    = new Color(0x888888);
	private static final Color AXIS_LINE   = new Color(0x404040);
	private static final Color PLACE_TRACK = new Color(0x3A3A3A);

	/** Pixel width reserved on the left for Y-axis gp labels (label text + gap to chart). */
	private static final int Y_LABEL_WIDTH = 56;
	/** Pixel height reserved at the bottom for X-axis labels (label + gap to chart). */
	private static final int X_LABEL_HEIGHT = 16;
	/** Horizontal gap between the right edge of a Y label and the left edge of the chart. */
	private static final int Y_LABEL_GAP = 6;
	/**
	 * Right-side gutter. Without it, the "now" X-axis label's last character
	 * (typically a tall ascender or descender like 'w') and the chart's
	 * rightmost vertex would render at the very right edge of the component
	 * and get clipped by Swing's redraw bounds — a few pixels of breathing
	 * room here keeps everything visible without changing the panel size.
	 */
	private static final int RIGHT_PAD = 4;
	/** Pixel padding inside the plot area so the line doesn't graze the top/bottom edges. */
	private static final int PLOT_PAD = 4;

	/** Crosshair colour for the hover readout. */
	private static final Color HOVER_LINE = new Color(0xB0B0B0);
	/** Background of the hover price tooltip box. */
	private static final Color HOVER_BOX  = new Color(0x10, 0x10, 0x10, 0xE0);
	private static final Color HOVER_BORD = new Color(0x505050);

	private final Long[] buy;
	private final Long[] sell;
	private final int    height;
	private final String xStartLabel;
	private final String xEndLabel;

	/** Current mouse x within the component, or -1 when not hovering. */
	private int hoverX = -1;

	public BuySellSparkline(Long[] buy, Long[] sell)
	{
		this(buy, sell, 80, "start", "now");
	}

	public BuySellSparkline(Long[] buy, Long[] sell, int height)
	{
		this(buy, sell, height, "start", "now");
	}

	public BuySellSparkline(Long[] buy, Long[] sell, int height, String xStartLabel, String xEndLabel)
	{
		this.buy         = buy  != null ? buy  : new Long[0];
		this.sell        = sell != null ? sell : new Long[0];
		this.height      = height;
		this.xStartLabel = xStartLabel != null ? xStartLabel : "";
		this.xEndLabel   = xEndLabel   != null ? xEndLabel   : "";
		setOpaque(false);
		setPreferredSize(new Dimension(0, height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		setMinimumSize(new Dimension(50, height));

		// Hover readout: track the cursor x and repaint so the crosshair +
		// price box follow it. Clearing on exit removes the overlay. Motion
		// listeners don't interfere with the click routing attached elsewhere.
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				hoverX = e.getX();
				repaint();
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverX = -1;
				repaint();
			}
		});
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
			if (w <= 0 || h <= 0)
			{
				return;
			}

			long min = Long.MAX_VALUE;
			long max = Long.MIN_VALUE;
			min = updateMin(min, buy);   max = updateMax(max, buy);
			min = updateMin(min, sell);  max = updateMax(max, sell);

			boolean enoughData = min != Long.MAX_VALUE && max > min;
			if (!enoughData)
			{
				paintPlaceholder(g2, w, h);
				return;
			}

			int plotX = Y_LABEL_WIDTH;
			int plotY = PLOT_PAD;
			int plotW = w - Y_LABEL_WIDTH - RIGHT_PAD;
			int plotH = h - PLOT_PAD - X_LABEL_HEIGHT;
			if (plotW < 30 || plotH < 16)
			{
				// Too small to be useful with axes — fall back to line-only.
				drawSeries(g2, buy,  Math.max(buy.length, sell.length), 0, w, PLOT_PAD, h - 2 * PLOT_PAD, min, max, BUY_COL);
				drawSeries(g2, sell, Math.max(buy.length, sell.length), 0, w, PLOT_PAD, h - 2 * PLOT_PAD, min, max, SELL_COL);
				return;
			}

			paintAxes(g2, plotX, plotY, plotW, plotH, min, max);

			int n = Math.max(buy.length, sell.length);
			Stroke prev = g2.getStroke();
			g2.setStroke(new BasicStroke(1.4f));
			drawSeries(g2, buy,  n, plotX, plotW, plotY, plotH, min, max, BUY_COL);
			drawSeries(g2, sell, n, plotX, plotW, plotY, plotH, min, max, SELL_COL);
			g2.setStroke(prev);

			paintHover(g2, plotX, plotY, plotW, plotH, min, max, n);
		}
		finally
		{
			g2.dispose();
		}
	}

	/**
	 * Draws the hover crosshair and a small price box for the data point under
	 * the cursor. Snaps to the nearest bucket so the readout matches a real
	 * sample rather than an interpolated pixel. No-op when not hovering, or when
	 * the cursor is outside the plot area (e.g. over the Y-axis labels).
	 */
	private void paintHover(Graphics2D g2, int plotX, int plotY, int plotW, int plotH, long min, long max, int n)
	{
		if (hoverX < 0 || n < 2 || plotW < 2)
		{
			return;
		}
		// Ignore hovering left of the plot (Y labels) or past the right edge.
		if (hoverX < plotX - 2 || hoverX > plotX + plotW + 2)
		{
			return;
		}

		double rel = (hoverX - plotX) / (double) (plotW - 1);
		rel = Math.max(0.0, Math.min(1.0, rel));
		int idx = (int) Math.round(rel * (n - 1));
		int snappedX = plotX + (int) Math.round((double) idx / (n - 1) * (plotW - 1));

		Long buyAt  = valueAt(buy, idx);
		Long sellAt = valueAt(sell, idx);
		if (buyAt == null && sellAt == null)
		{
			return;   // gap on both series here — nothing meaningful to show
		}

		// Vertical crosshair.
		g2.setColor(HOVER_LINE);
		g2.setStroke(new BasicStroke(1f));
		g2.drawLine(snappedX, plotY, snappedX, plotY + plotH);

		// Marker dots at the actual sample heights.
		if (buyAt != null)
		{
			int y = plotY + (int) Math.round((1.0 - (buyAt - min) / (double) (max - min)) * plotH);
			g2.setColor(BUY_COL);
			g2.fillOval(snappedX - 3, y - 3, 6, 6);
		}
		if (sellAt != null)
		{
			int y = plotY + (int) Math.round((1.0 - (sellAt - min) / (double) (max - min)) * plotH);
			g2.setColor(SELL_COL);
			g2.fillOval(snappedX - 3, y - 3, 6, 6);
		}

		// Price box. Two short lines, colour-coded, in a dark rounded panel that
		// flips to the left of the crosshair when it would overflow the right.
		g2.setFont(Fonts.SM);
		FontMetrics fm = g2.getFontMetrics();
		String buyText  = buyAt  != null ? "Buy "  + String.format("%,d", buyAt)  : null;
		String sellText = sellAt != null ? "Sell " + String.format("%,d", sellAt) : null;
		int textW = 0;
		if (buyText  != null) textW = Math.max(textW, fm.stringWidth(buyText));
		if (sellText != null) textW = Math.max(textW, fm.stringWidth(sellText));
		int lines = (buyText != null ? 1 : 0) + (sellText != null ? 1 : 0);
		int lineH = fm.getHeight();
		int padX = 6, padY = 4;
		int boxW = textW + padX * 2;
		int boxH = lines * lineH + padY * 2;

		int boxX = snappedX + 8;
		if (boxX + boxW > plotX + plotW)
		{
			boxX = snappedX - 8 - boxW;   // flip to the left
		}
		boxX = Math.max(plotX, Math.min(boxX, plotX + plotW - boxW));
		int boxY = plotY + 2;

		g2.setColor(HOVER_BOX);
		g2.fillRoundRect(boxX, boxY, boxW, boxH, 6, 6);
		g2.setColor(HOVER_BORD);
		g2.drawRoundRect(boxX, boxY, boxW, boxH, 6, 6);

		int textY = boxY + padY + fm.getAscent();
		if (buyText != null)
		{
			g2.setColor(BUY_COL);
			g2.drawString(buyText, boxX + padX, textY);
			textY += lineH;
		}
		if (sellText != null)
		{
			g2.setColor(SELL_COL);
			g2.drawString(sellText, boxX + padX, textY);
		}
	}

	/** Value at index if it's a real (non-null, positive) sample, else null. */
	private static Long valueAt(Long[] series, int idx)
	{
		if (series == null || idx < 0 || idx >= series.length)
		{
			return null;
		}
		Long v = series[idx];
		return (v == null || v <= 0) ? null : v;
	}

	/**
	 * Renders the Y axis (3 gp ticks — low / mid / high) and the X axis
	 * (start / now labels). Gridlines are drawn lightly so they don't
	 * compete with the price lines for attention.
	 */
	private void paintAxes(Graphics2D g2, int plotX, int plotY, int plotW, int plotH, long min, long max)
	{
		g2.setFont(Fonts.SM);
		FontMetrics fm = g2.getFontMetrics();
		int ascent = fm.getAscent();
		long mid = min + (max - min) / 2;

		// Light horizontal gridlines at min / mid / max
		g2.setColor(AXIS_LINE);
		g2.setStroke(new BasicStroke(1f));
		int gridLeft = plotX;
		int gridRight = plotX + plotW;
		g2.drawLine(gridLeft, plotY,                gridRight, plotY);
		g2.drawLine(gridLeft, plotY + plotH / 2,    gridRight, plotY + plotH / 2);
		g2.drawLine(gridLeft, plotY + plotH,        gridRight, plotY + plotH);

		// Y-axis labels: right edge sits {@code Y_LABEL_GAP} px clear of the
		// plot left edge. Baselines are positioned so the max label sits just
		// above its gridline, the mid label centres on its gridline, and the
		// min label sits just below — keeps all three visually inside the
		// chart's vertical band without overlapping the price lines.
		int rightX = plotX - Y_LABEL_GAP;
		g2.setColor(GRAY_LBL);
		drawRightAligned(g2, fm, formatGpCompact(max), rightX, plotY + ascent / 2);
		drawRightAligned(g2, fm, formatGpCompact(mid), rightX, plotY + plotH / 2 + ascent / 2);
		drawRightAligned(g2, fm, formatGpCompact(min), rightX, plotY + plotH + ascent / 2);

		// X-axis labels sit in their own band below the plot — leave a small
		// gap so the lowest Y label's descenders don't kiss the start/end
		// labels. {@code X_LABEL_HEIGHT} reserves that band; place the label
		// baselines near its bottom so the text sits clear of the chart.
		int xLabelY = plotY + plotH + X_LABEL_HEIGHT - 2;
		g2.drawString(xStartLabel, plotX, xLabelY);
		int endWidth = fm.stringWidth(xEndLabel);
		g2.drawString(xEndLabel, plotX + plotW - endWidth, xLabelY);
	}

	private static void drawRightAligned(Graphics2D g2, FontMetrics fm, String text, int rightX, int baselineY)
	{
		int textW = fm.stringWidth(text);
		g2.drawString(text, rightX - textW, baselineY);
	}

	/**
	 * Compact gp formatter inlined here so the chart doesn't depend on
	 * FlipItemPanel for a tiny utility. Same B/M/K convention used elsewhere.
	 */
	private static String formatGpCompact(long amount)
	{
		long abs = Math.abs(amount);
		if (abs >= 1_000_000_000L)
		{
			return trim(String.format("%.2f", amount / 1_000_000_000.0)) + "B";
		}
		if (abs >= 1_000_000L)
		{
			return trim(String.format("%.2f", amount / 1_000_000.0)) + "M";
		}
		if (abs >= 1_000L)
		{
			return trim(String.format("%.1f", amount / 1_000.0)) + "K";
		}
		return String.valueOf(amount);
	}

	private static String trim(String s)
	{
		// "22.50" → "22.5", "5.00" → "5"
		if (s.contains("."))
		{
			s = s.replaceAll("0+$", "");
			if (s.endsWith("."))
			{
				s = s.substring(0, s.length() - 1);
			}
		}
		return s;
	}

	/**
	 * Draws one polyline, breaking on null/zero entries. Contiguous runs of
	 * non-null values are emitted as their own polylines so a gap appears
	 * as a visible break rather than a straight bridge across the hole.
	 */
	private static void drawSeries(Graphics2D g2, Long[] series, int n, int plotX, int plotW, int plotY, int plotH, long min, long max, Color colour)
	{
		if (series.length < 2)
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
			xs[runLen] = plotX + (int) Math.round((double) i / (n - 1) * (plotW - 1));
			double norm = (v - min) / (double) (max - min);
			ys[runLen] = plotY + (int) Math.round((1.0 - norm) * plotH);
			runLen++;
		}
		if (runLen >= 2)
		{
			g2.drawPolyline(xs, ys, runLen);
		}
	}

	private void paintPlaceholder(Graphics2D g2, int w, int h)
	{
		int midY = h / 2;
		g2.setColor(PLACE_TRACK);
		g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
			1f, new float[]{4f, 4f}, 0f));
		g2.drawLine(0, midY, w, midY);

		g2.setStroke(new BasicStroke(1f));
		g2.setColor(GRAY_LBL);
		g2.setFont(Fonts.SM);
		String msg = "Collecting price history…";
		int tw = g2.getFontMetrics().stringWidth(msg);
		int th = g2.getFontMetrics().getAscent();
		int tx = (w - tw) / 2;
		int ty = midY - 6;
		// Mask the dashed line where the text sits so it doesn't slash through.
		Color parentBg = getParent() != null ? getParent().getBackground() : Color.BLACK;
		g2.setColor(parentBg);
		g2.fillRect(tx - 4, ty - th, tw + 8, th + 4);
		g2.setColor(GRAY_LBL);
		g2.drawString(msg, tx, ty);
	}

	private static long updateMin(long current, Long[] series)
	{
		for (Long v : series)
		{
			if (v == null || v <= 0) continue;
			if (v < current) current = v;
		}
		return current;
	}

	private static long updateMax(long current, Long[] series)
	{
		for (Long v : series)
		{
			if (v == null || v <= 0) continue;
			if (v > current) current = v;
		}
		return current;
	}
}
