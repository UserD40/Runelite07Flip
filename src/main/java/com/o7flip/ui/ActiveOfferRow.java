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

import com.o7flip.O7FlipConfig;
import com.o7flip.O7FlipPlugin;
import com.o7flip.model.Models.ActiveOfferSnapshot;
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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class ActiveOfferRow extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color BUY_COL   = new Color(0x5B9BD5);
	private static final Color SELL_COL  = new Color(0x00C27A);
	private static final Color BAR_TRACK  = new Color(0x1F1F1F);

	private final ActiveOfferSnapshot offer;
	private final O7FlipPlugin plugin;
	private final Color baseBg;
	private final ProgressBar bar;
	private int lastTier = -1;

	public ActiveOfferRow(ActiveOfferSnapshot offer, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		this.offer  = offer;
		this.plugin = plugin;
		this.baseBg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setOpaque(false);
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

		boolean fullyFilled = offer.totalQuantity > 0 && offer.quantitySold >= offer.totalQuantity;
		Color barCol = fullyFilled ? SELL_COL : sideCol;

		O7FlipConfig cfg = plugin != null ? plugin.getConfig() : null;
		bar = new ProgressBar(offer.quantitySold, offer.totalQuantity, barCol,
			cfg != null ? cfg.lastFillColour() : Color.LIGHT_GRAY);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel textCol = new JPanel();
		textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
		textCol.setOpaque(false);
		textCol.setAlignmentX(Component.LEFT_ALIGNMENT);
		textCol.add(nameLabel);
		textCol.add(javax.swing.Box.createVerticalStrut(2));
		textCol.add(typeLabel);
		textCol.add(javax.swing.Box.createVerticalStrut(4));
		textCol.add(bar);

		add(iconLabel, BorderLayout.WEST);
		add(textCol,   BorderLayout.CENTER);

		ClickRouter.attachInsightsOnly(this, plugin, offer.itemId, offer.name);

		refreshCaption();

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	public void refreshCaption()
	{
		O7FlipConfig cfg = plugin != null ? plugin.getConfig() : null;
		String counter = (cfg == null || cfg.activeFillCounter())
			? offer.quantitySold + "/" + offer.totalQuantity
			: "";
		String age = (cfg == null || cfg.activeLastFillAge()) ? lastFillText() : "";
		bar.setLabels(counter, age);
	}

	private String lastFillText()
	{
		if (plugin == null || offer.quantitySold <= 0)
		{
			return "";
		}
		String age = plugin.offerLastFillText(offer.slot);
		return age == null ? "" : (offer.isBuy() ? "Last buy: " : "Last sale: ") + age;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		int tier = plugin != null ? plugin.offerCompetitiveTier(offer.itemId, offer.isBuy(), offer.price) : -1;
		lastTier = tier;
		Color fill = baseBg;
		Color tc = plugin != null ? plugin.offerTierColor(tier) : null;
		if (tc != null)
		{
			fill = tintBg(baseBg, tc);
		}
		g.setColor(fill);
		g.fillRect(0, 0, getWidth(), getHeight());
		bar.setTrackBase(fill);
	}

	@Override
	public String getToolTipText(java.awt.event.MouseEvent event)
	{
		if (lastTier < 0 || plugin == null)
		{
			return ClickRouter.CLICK_HINT;
		}
		boolean isBuy = offer.isBuy();
		StringBuilder sb = new StringBuilder("<html><b>");
		sb.append(verdictText(lastTier, isBuy)).append("</b><br>");
		sb.append("Your ").append(isBuy ? "buy" : "sell").append(": ")
			.append(FlipItemPanel.formatGp(offer.price)).append(" gp");
		com.o7flip.model.Models.ItemInsights ins = plugin.getOverlayInsights(offer.itemId);
		if (ins != null && ins.current != null)
		{
			com.o7flip.model.Models.ItemInsights.Current c = ins.current;
			if (c.buyPrice > 0)  sb.append("<br>Live buy: ").append(FlipItemPanel.formatGp(c.buyPrice)).append(" gp");
			if (c.sellPrice > 0) sb.append("<br>Live sell: ").append(FlipItemPanel.formatGp(c.sellPrice)).append(" gp");
		}
		sb.append("<br><br>Colour matches the Grand Exchange border:<br>")
			.append("<font color='#3FC77F'>Green</font> competitive &nbsp;")
			.append("<font color='#E8C34A'>Amber</font> borderline &nbsp;")
			.append("<font color='#E05B5B'>Red</font> won't fill<br>")
			.append(ClickRouter.CLICK_HINT).append("</html>");
		return sb.toString();
	}

	private static Color tintBg(Color base, Color accent)
	{
		double w = 0.25;
		int r = (int) Math.round(base.getRed()   * (1 - w) + accent.getRed()   * w);
		int g = (int) Math.round(base.getGreen() * (1 - w) + accent.getGreen() * w);
		int b = (int) Math.round(base.getBlue()  * (1 - w) + accent.getBlue()  * w);
		return new Color(clampByte(r), clampByte(g), clampByte(b));
	}

	private static int clampByte(int v)
	{
		return v < 0 ? 0 : Math.min(v, 255);
	}

	private static String verdictText(int tier, boolean isBuy)
	{
		if (tier == 0)
		{
			return "Competitive - should fill at this price";
		}
		if (tier == 1)
		{
			return isBuy ? "A bit low - may fill slowly" : "A bit high - may fill slowly";
		}
		return isBuy ? "Underpriced - raise your buy to fill" : "Overpriced - lower your sell to fill";
	}

	private static class ProgressBar extends JPanel
	{
		private static final int HEIGHT = 15;
		private static final int PAD = 5;
		private static final java.awt.Font BAR_FONT = Fonts.SM_BOLD.deriveFont(10f);

		private final int filled;
		private final int total;
		private final Color colour;
		private final Color textColour;
		private Color trackBase = BAR_TRACK;
		private String left = "";
		private String right = "";

		ProgressBar(int filled, int total, Color colour, Color textColour)
		{
			this.filled     = filled;
			this.total      = total;
			this.colour     = colour;
			this.textColour = textColour;
			setOpaque(false);
			setPreferredSize(new Dimension(0, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
			setMinimumSize(new Dimension(50, HEIGHT));
		}

		void setTrackBase(Color c)
		{
			this.trackBase = c;
		}

		private static Color scale(Color c, double f)
		{
			return new Color(
				clampByte((int) Math.round(c.getRed()   * f)),
				clampByte((int) Math.round(c.getGreen() * f)),
				clampByte((int) Math.round(c.getBlue()  * f)));
		}

		void setLabels(String left, String right)
		{
			this.left  = left  != null ? left  : "";
			this.right = right != null ? right : "";
			repaint();
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

				g2.setColor(scale(trackBase, 0.62));
				g2.fillRect(0, 0, w, h);

				if (total > 0 && filled > 0)
				{
					double frac = Math.min(1.0, filled / (double) total);
					int fillW = (int) Math.round(frac * w);
					if (fillW > 0)
					{
						g2.setColor(scale(colour, 0.62));
						g2.fillRect(0, 0, fillW, h);
					}
				}


				g2.setFont(BAR_FONT);
				java.awt.FontMetrics fm = g2.getFontMetrics();
				String tail = right;
				if (!left.isEmpty() && !tail.isEmpty()
					&& fm.stringWidth(left) + fm.stringWidth(tail) + PAD * 3 > w)
				{
					int colon = tail.indexOf(": ");
					tail = colon >= 0 ? tail.substring(colon + 2) : tail;
				}
				int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;
				g2.setColor(textColour);
				if (!left.isEmpty())
				{
					g2.drawString(left, PAD, baseline);
				}
				if (!tail.isEmpty() && fm.stringWidth(left) + fm.stringWidth(tail) + PAD * 3 <= w)
				{
					g2.drawString(tail, w - PAD - fm.stringWidth(tail), baseline);
				}
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
