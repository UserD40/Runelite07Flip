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
import com.o7flip.model.TradeRecord;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * One row in the Recent My Trades view. Mirrors the Active view's layout
 * (icon + name + "Buy/Sell · X gp ea" + progress bar) and adds the realised
 * profit on the right alongside the filled/total qty counter. Visually
 * unifies "live" and "completed" trade rows so the user reads one consistent
 * row format regardless of which sort tab they're on.
 *
 * Falls back gracefully for legacy {@link TradeRecord} entries that don't
 * carry {@code totalQuantity} (records written before that field existed):
 * such rows render as a fully-filled bar with the historical qty.
 */
public class TradeRecordPanel extends JPanel
{
	private static final Color ODD_BG     = new Color(0x272727);
	private static final Color BUY_COL    = new Color(0x5B9BD5);
	private static final Color SELL_COL   = new Color(0x00C27A);
	private static final Color PART_COL   = new Color(0xE8A838);
	private static final Color PROFIT_COL = new Color(0x00C27A);
	private static final Color LOSS_COL   = new Color(0xE85050);
	private static final Color BAR_TRACK  = new Color(0x3A3A3A);
	private static final Color MUTED      = new Color(0x888888);

	// Fixed right-column width, kept identical to ActiveOfferRow.RIGHT_COL_W so
	// the progress bar doesn't shift/resize when a row moves from in-flight
	// (Active, qty only) to completed (Recent, qty + profit label).
	private static final int RIGHT_COL_W = ActiveOfferRow.RIGHT_COL_W;

	private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("d MMM");

	public TradeRecordPanel(TradeRecord trade, ItemManager itemManager, boolean odd)
	{
		this(trade, itemManager, odd, null, null);
	}

	public TradeRecordPanel(TradeRecord trade, ItemManager itemManager, boolean odd, Long matchedProfit)
	{
		this(trade, itemManager, odd, matchedProfit, null);
	}

	/**
	 * @param matchedProfit FIFO-matched profit for this sell row, summed across
	 *                      every CompletedFlip whose {@code sellTimestamp}
	 *                      equals {@code trade.timestamp}. Null for buys, for
	 *                      sells with no matching buy (phantom flips), and any
	 *                      time the caller doesn't have a result handy.
	 * @param plugin       used to route left-click → Insights for this item.
	 *                     Null disables click routing (e.g. unit tests).
	 */
	public TradeRecordPanel(TradeRecord trade, ItemManager itemManager, boolean odd, Long matchedProfit, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── ICON ──────────────────────────────────────────────────────────────
		JLabel iconLabel = FlipItemPanel.buildIcon(trade.itemId, itemManager);

		// ── NAME ──────────────────────────────────────────────────────────────
		JLabel nameLabel = new JLabel(trade.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── FILL STATE ────────────────────────────────────────────────────────
		// Legacy records (no totalQuantity) treat quantity as both filled and
		// total, so they render as a full bar — accurate to the data we have.
		// Colour scheme: amber while in-flight (or cancelled mid-flight),
		// green once fully filled. We deliberately don't colour completed
		// buys differently from completed sells — the "Buy" / "Sell" word in
		// the text already carries that distinction, and using a single green
		// "done" state reads more cleanly across mixed rows.
		int filled = trade.quantity;
		int total  = trade.totalQuantity != null && trade.totalQuantity > 0
			? trade.totalQuantity
			: Math.max(trade.quantity, 1);
		boolean fullyFilled = filled >= total;
		Color sideCol = fullyFilled ? SELL_COL : PART_COL;

		// ── TYPE + PRICE ─────────────────────────────────────────────────────
		String typeWord = trade.isBuy ? "Buy" : "Sell";
		JLabel typeLabel = new JLabel(typeWord + " · " + FlipItemPanel.formatGpCompact(trade.priceEach) + " gp ea");
		typeLabel.setFont(Fonts.SM);
		typeLabel.setForeground(sideCol);
		typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── PROGRESS BAR ──────────────────────────────────────────────────────
		ProgressBar bar = new ProgressBar(filled, total, sideCol);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── DATE (small caption under the progress bar) ──────────────────────
		JLabel dateLabel = new JLabel(DATE_FMT.format(new Date(trade.timestamp)));
		dateLabel.setFont(Fonts.SM);
		dateLabel.setForeground(MUTED);
		dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── TEXT COLUMN ──────────────────────────────────────────────────────
		JPanel textCol = new JPanel();
		textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
		textCol.setBackground(bg);
		textCol.setAlignmentX(Component.LEFT_ALIGNMENT);
		textCol.add(nameLabel);
		textCol.add(Box.createVerticalStrut(2));
		textCol.add(typeLabel);
		textCol.add(Box.createVerticalStrut(4));
		textCol.add(bar);
		textCol.add(Box.createVerticalStrut(2));
		textCol.add(dateLabel);

		// ── RIGHT-SIDE COLUMN: qty counter on top, profit (or "—" phantom) below ──
		JPanel rightCol = new JPanel();
		rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
		rightCol.setBackground(bg);

		JLabel qtyLabel = new JLabel(filled + " / " + total);
		qtyLabel.setFont(Fonts.SM_BOLD);
		qtyLabel.setForeground(filled >= total ? Color.WHITE : MUTED);
		qtyLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		qtyLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		rightCol.add(qtyLabel);

		if (matchedProfit != null)
		{
			JLabel profitLabel = new JLabel(formatProfit(matchedProfit));
			profitLabel.setFont(Fonts.SM_BOLD);
			profitLabel.setForeground(profitColor(matchedProfit));
			profitLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
			profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			profitLabel.setToolTipText(
				"Realised profit (" + FlipItemPanel.formatGp(matchedProfit) + " gp) — FIFO-matched against earlier buys");
			rightCol.add(Box.createVerticalStrut(2));
			rightCol.add(profitLabel);
		}
		else if (!trade.isBuy)
		{
			JLabel phantomLabel = new JLabel("—");
			phantomLabel.setFont(Fonts.SM_BOLD);
			phantomLabel.setForeground(Color.LIGHT_GRAY);
			phantomLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
			phantomLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			phantomLabel.setToolTipText(
				"<html><b>No matching buy tracked</b><br>"
				+ "The buy for this item happened before the plugin started recording,<br>"
				+ "from a different installation, or hasn't filled yet. Realised profit<br>"
				+ "can't be computed without a cost basis, so this sell is excluded<br>"
				+ "from the Total / Today / Best / Worst aggregates.</html>");
			rightCol.add(Box.createVerticalStrut(2));
			rightCol.add(phantomLabel);
		}

		// Reserve the shared fixed width so the progress bar above lines up with
		// the Active row's bar (which has no profit label and would otherwise be
		// wider). Done after children are added so the height is correct.
		rightCol.setPreferredSize(new Dimension(RIGHT_COL_W, rightCol.getPreferredSize().height));

		add(iconLabel, BorderLayout.WEST);
		add(textCol,   BorderLayout.CENTER);
		add(rightCol,  BorderLayout.EAST);

		ClickRouter.attachInsightsOnly(this, plugin, trade.itemId, trade.name);

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static String formatProfit(long profit)
	{
		String prefix = profit > 0 ? "+" : "";
		return prefix + FlipItemPanel.formatGpCompact(profit) + " gp";
	}

	private static Color profitColor(long profit)
	{
		if (profit > 0) return PROFIT_COL;
		if (profit < 0) return LOSS_COL;
		return Color.LIGHT_GRAY;
	}

	/**
	 * Coloured fill on a grey track, no label inside (the qty counter sits to
	 * the right of the row instead). Same visual as
	 * {@link ActiveOfferRow.ProgressBar} so Recent and Active rows align.
	 */
	private static class ProgressBar extends JPanel
	{
		private static final int HEIGHT = 6;

		private final int filled;
		private final int total;
		private final Color colour;

		ProgressBar(int filled, int total, Color colour)
		{
			this.filled = filled;
			this.total  = total;
			this.colour = colour;
			setOpaque(false);
			setPreferredSize(new Dimension(0, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
			setMinimumSize(new Dimension(50, HEIGHT));
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
				int radius = h;

				g2.setColor(BAR_TRACK);
				g2.fillRoundRect(0, 0, w, h, radius, radius);

				if (total > 0 && filled > 0)
				{
					double frac = Math.min(1.0, filled / (double) total);
					int fillW = (int) Math.round(frac * w);
					if (fillW > 0)
					{
						g2.setColor(colour);
						g2.fillRoundRect(0, 0, fillW, h, radius, radius);
					}
				}

				g2.setStroke(new BasicStroke(1f));
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
