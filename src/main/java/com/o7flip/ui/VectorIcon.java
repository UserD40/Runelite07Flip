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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import javax.swing.JLabel;

/**
 * Icons painted in code, used as the macOS path where Java/AWT cannot render the
 * emoji/symbol glyphs used on Windows. Vector shapes render identically on every OS.
 */
public final class VectorIcon implements Icon
{
	public enum Kind { AUTOFILL, LOCK, UNLOCK, GLOBE, STAR, STAR_OUTLINE, PENCIL }

	private final Kind kind;
	private final int size;
	private Color color;

	public VectorIcon(Kind kind, int size, Color color)
	{
		this.kind = kind;
		this.size = size;
		this.color = color;
	}

	public void setColor(Color color)
	{
		this.color = color;
	}

	/** Windows keeps the original glyph; elsewhere the label shows a drawn icon. */
	public static void apply(JLabel label, String windowsGlyph, Kind kind, int size, Color color)
	{
		if (Fonts.WINDOWS)
		{
			label.setIcon(null);
			label.setText(windowsGlyph);
		}
		else
		{
			label.setText("");
			label.setIcon(new VectorIcon(kind, size, color));
		}
	}

	@Override
	public int getIconWidth()
	{
		return size;
	}

	@Override
	public int getIconHeight()
	{
		return size;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.translate(x, y);
		g2.setColor(color);
		final float s = size;
		g2.setStroke(new BasicStroke(Math.max(1.3f, s / 11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		switch (kind)
		{
			case AUTOFILL:
				float cp = s * 0.13f;
				g2.draw(new Ellipse2D.Float(cp, cp, s - 2 * cp, s - 2 * cp));
				g2.draw(new Line2D.Float(s * 0.5f, s * 0.27f, s * 0.5f, s * 0.73f));
				Path2D dollar = new Path2D.Float();
				dollar.moveTo(s * 0.60f, s * 0.39f);
				dollar.curveTo(s * 0.56f, s * 0.31f, s * 0.42f, s * 0.32f, s * 0.41f, s * 0.42f);
				dollar.curveTo(s * 0.40f, s * 0.50f, s * 0.60f, s * 0.50f, s * 0.59f, s * 0.58f);
				dollar.curveTo(s * 0.58f, s * 0.68f, s * 0.44f, s * 0.69f, s * 0.40f, s * 0.61f);
				g2.draw(dollar);
				break;
			case LOCK:
			case UNLOCK:
				g2.fill(new RoundRectangle2D.Float(s * 0.24f, s * 0.46f, s * 0.52f, s * 0.40f, s * 0.14f, s * 0.14f));
				g2.draw(new Arc2D.Float(s * 0.34f, s * 0.24f, s * 0.32f, s * 0.34f, 0, 180, Arc2D.OPEN));
				g2.draw(new Line2D.Float(s * 0.34f, s * 0.41f, s * 0.34f, s * 0.47f));
				boolean open = kind == Kind.UNLOCK;
				g2.draw(new Line2D.Float(s * 0.66f, open ? s * 0.30f : s * 0.41f, s * 0.66f, open ? s * 0.37f : s * 0.47f));
				break;
			case GLOBE:
				float p = s * 0.17f;
				float d = s - 2 * p;
				g2.draw(new Ellipse2D.Float(p, p, d, d));
				g2.draw(new Ellipse2D.Float(p + d * 0.3f, p, d * 0.4f, d));
				g2.draw(new Line2D.Float(p, s * 0.5f, p + d, s * 0.5f));
				break;
			case STAR:
			case STAR_OUTLINE:
				Path2D star = new Path2D.Float();
				double cx = s / 2.0, cy = s * 0.54, rOut = s * 0.42, rIn = s * 0.17;
				for (int i = 0; i < 10; i++)
				{
					double r = (i % 2 == 0) ? rOut : rIn;
					double a = -Math.PI / 2 + i * Math.PI / 5;
					double px = cx + r * Math.cos(a), py = cy + r * Math.sin(a);
					if (i == 0)
					{
						star.moveTo(px, py);
					}
					else
					{
						star.lineTo(px, py);
					}
				}
				star.closePath();
				if (kind == Kind.STAR)
				{
					g2.fill(star);
				}
				else
				{
					g2.draw(star);
				}
				break;
			case PENCIL:
				g2.draw(new Line2D.Float(s * 0.30f, s * 0.78f, s * 0.74f, s * 0.34f));
				g2.draw(new Line2D.Float(s * 0.22f, s * 0.70f, s * 0.66f, s * 0.26f));
				g2.draw(new Line2D.Float(s * 0.66f, s * 0.26f, s * 0.74f, s * 0.34f));
				Path2D tip = new Path2D.Float();
				tip.moveTo(s * 0.22f, s * 0.70f);
				tip.lineTo(s * 0.18f, s * 0.84f);
				tip.lineTo(s * 0.32f, s * 0.78f);
				tip.closePath();
				g2.fill(tip);
				break;
			default:
				break;
		}
		g2.dispose();
	}
}
