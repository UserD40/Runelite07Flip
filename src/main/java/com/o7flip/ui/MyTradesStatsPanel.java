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

import com.o7flip.model.TrackerStats;
import com.o7flip.util.Fonts;
import com.o7flip.util.ProfitCalculator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDate;
import java.time.ZoneId;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * Compact stats summary that sits above the trade list on the My Trades tab.
 * Reads from {@link ProfitCalculator.Result} — pure presentation, no logic.
 *
 * Renders only when at least one completed flip exists. Caller decides
 * whether to add the panel to the tab; an empty state hides itself by
 * setting visibility to false.
 */
public class MyTradesStatsPanel extends JPanel
{
	private static final Color PROFIT_COL  = new Color(0x00C27A);
	private static final Color LOSS_COL    = new Color(0xE85050);
	private static final Color SECTION_BG  = new Color(0x1F1F1F);
	private static final Color HEADER_COL  = new Color(0xC4A052);

	private final JLabel totalProfitValue = valueLabel();
	private final JLabel todayProfitValue = valueLabel();
	private final JLabel tradesValue      = valueLabel();
	private final JLabel winRateValue     = valueLabel();
	private final JLabel avgRoiValue      = valueLabel();
	private final JLabel taxValue         = valueLabel();
	private final JLabel bestNameValue    = valueLabel();
	private final JLabel bestProfitValue  = valueLabel();
	private final JLabel worstNameValue   = valueLabel();
	private final JLabel worstProfitValue = valueLabel();
	private final JLabel weekValue        = valueLabel();
	private final JLabel monthValue       = valueLabel();
	private final JLabel bondsValue       = valueLabel();

	private final JPanel bestRow;
	private final JPanel worstRow;
	private final JPanel bondsRow;

	public MyTradesStatsPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(SECTION_BG);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(sectionHeader("Profit"));
		add(row("Total",      totalProfitValue));
		add(row("Today",      todayProfitValue));
		bestRow  = row("Best",  bestNameValue,  bestProfitValue);
		worstRow = row("Worst", worstNameValue, worstProfitValue);
		add(bestRow);
		add(worstRow);

		add(Box.createVerticalStrut(8));
		add(sectionHeader("Performance"));
		add(row("Trades",     tradesValue));
		add(row("Win rate",   winRateValue));
		add(row("Avg ROI",    avgRoiValue));
		add(row("GE tax paid (est.)", taxValue));
		// Membership cost value can be very long (e.g. "289,128,766 gp · 21 bonds")
		// so it wouldn't fit on the same line as the label without collision.
		// Stack the label above the value instead — label left, value right
		// on its own line below.
		bondsRow = stackedRow("Membership cost", bondsValue);
		add(bondsRow);

		// Tight 4px gap before Activity instead of 8px — the stacked Membership
		// cost row adds vertical height on its own, so the extra strut pushes
		// the bottom of the panel past the visible area without it.
		add(Box.createVerticalStrut(4));
		add(sectionHeader("Activity"));
		add(row("This week",  weekValue));
		add(row("This month", monthValue));
	}

	/**
	 * Backwards-compatible local-only update — still used by tests and any
	 * caller that doesn't have server stats handy.
	 */
	public void update(ProfitCalculator.Result result)
	{
		update(result, null);
	}

	/**
	 * Refresh all labels. Server stats (when present and non-empty) are
	 * authoritative for headline numbers — Total, Trades, Win rate, Best —
	 * because they merge plugin-recorded GE trades with website-logged
	 * tracker entries that the plugin has no visibility into. Today, Avg
	 * ROI, GE tax, and Worst still come from the local FIFO result since
	 * the server stats endpoint doesn't expose per-flip detail.
	 *
	 * Hides the panel only when both sources are empty.
	 */
	public void update(ProfitCalculator.Result result, TrackerStats server)
	{
		ProfitCalculator.Stats local = result.stats;
		boolean hasServer = server != null && server.closedCount > 0;
		boolean hasLocal  = local.completedFlipCount > 0;
		boolean hasBonds  = local.bondCount > 0;

		// Keep the panel visible if there's any signal to show — flips, bonds,
		// or server-side stats. A user who's only redeemed bonds (no flips
		// captured yet) still sees the Membership cost line.
		if (!hasServer && !hasLocal && !hasBonds)
		{
			setVisible(false);
			return;
		}
		setVisible(true);

		long totalProfit = hasServer ? server.totalRealisedProfit : local.totalProfit;
		setProfit(totalProfitValue, totalProfit);
		if (hasServer && server.declaredProfit != 0L)
		{
			totalProfitValue.setToolTipText(String.format(
				"<html>%s gp confirmed by your GE trades<br>%s gp self-reported (manually-closed flips)</html>",
				FlipItemPanel.formatGp(server.verifiedProfit),
				FlipItemPanel.formatGp(server.declaredProfit)));
		}
		else
		{
			totalProfitValue.setToolTipText(null);
		}

		// Today profit comes from local FIFO only — server stats don't expose
		// per-flip timestamps. For users who only flip on the website, this
		// stays at 0 gp, which is correct ("plugin saw zero today").
		long todayProfit = hasLocal ? sumProfitSinceTodayStart(result) : 0L;
		setProfit(todayProfitValue, todayProfit);
		int phantomsToday = hasLocal ? countPhantomsSinceTodayStart(result) : 0;
		if (phantomsToday > 0)
		{
			todayProfitValue.setToolTipText(String.format(
				"<html>+%s gp from matched flips today.<br>"
				+ "<font color='#888888'>%d sell%s today had no matching buy in tracked history<br>"
				+ "and %s excluded from this total — hover that row for details.</font></html>",
				FlipItemPanel.formatGp(todayProfit),
				phantomsToday,
				phantomsToday == 1 ? "" : "s",
				phantomsToday == 1 ? "is" : "are"));
		}
		else
		{
			todayProfitValue.setToolTipText(null);
		}

		int tradesCount = hasServer ? server.closedCount : local.completedFlipCount;
		tradesValue.setText(String.valueOf(tradesCount));
		tradesValue.setForeground(Color.WHITE);

		if (hasServer)
		{
			// Server returns just the rate; W/L/E breakdown isn't in /tracker/stats.
			winRateValue.setText(String.format("%.0f%%", server.winRate * 100.0));
		}
		else
		{
			winRateValue.setText(String.format("%.0f%% (%dW / %dL / %dE)",
				local.winRatePct, local.winCount, local.lossCount, local.breakEvenCount));
		}
		winRateValue.setForeground(Color.WHITE);

		if (hasLocal)
		{
			avgRoiValue.setText(String.format("%+.1f%%", local.avgRoiPct));
			avgRoiValue.setForeground(local.avgRoiPct >= 0 ? PROFIT_COL : LOSS_COL);
		}
		else
		{
			avgRoiValue.setText("—");
			avgRoiValue.setForeground(Color.LIGHT_GRAY);
		}

		// GE tax: now an exact sum across matched flips (ProfitCalculator
		// applies the OSRS rules per-flip — 2% with a 5M/item cap, exempt
		// below 100 gp/item, exempt for bonds). Profit shown elsewhere in
		// the panel is already net of this tax, so the two figures add up.
		if (hasLocal)
		{
			taxValue.setText(FlipItemPanel.formatGp(local.totalTaxPaid) + " gp");
		}
		else
		{
			taxValue.setText("—");
		}
		taxValue.setForeground(Color.LIGHT_GRAY);

		applyBestRow(local, server, hasServer);

		if (local.worstFlip != null && local.worstFlip.profit < 0)
		{
			worstNameValue.setText(truncate(local.worstFlip.name, 16));
			worstNameValue.setForeground(Color.WHITE);
			setProfit(worstProfitValue, local.worstFlip.profit);
			worstRow.setVisible(true);
		}
		else
		{
			worstRow.setVisible(false);
		}

		// Membership cost — only shown when the user has consumed bonds.
		// The number is the gp value of bond buys with no matching sell, so
		// it grows with every redemption and shrinks (rare) if a held bond
		// gets flipped back. Hidden entirely when zero so it's not noise.
		if (local.bondCount > 0)
		{
			String unit = local.bondCount == 1 ? " bond" : " bonds";
			bondsValue.setText(FlipItemPanel.formatGp(local.bondSpend) + " gp · " + local.bondCount + unit);
			bondsValue.setForeground(LOSS_COL);
			bondsRow.setVisible(true);
		}
		else
		{
			bondsRow.setVisible(false);
		}

		// Activity windows — local-only since server stats don't carry per-flip
		// timestamps. A user who only logs flips on the website will see
		// "0 gp · 0 flips" here, which is honest: the plugin saw nothing.
		applyPeriod(weekValue,  result, periodStart(7));
		applyPeriod(monthValue, result, periodStart(30));

		revalidate();
		repaint();
	}

	private void applyBestRow(ProfitCalculator.Stats local, TrackerStats server, boolean hasServer)
	{
		if (hasServer && server.bestFlip != null && server.bestFlip.profit > 0)
		{
			TrackerStats.BestFlip b = server.bestFlip;
			bestNameValue.setText(truncate(b.name, 16));
			bestNameValue.setForeground(bestNameColor(b.source));
			setProfit(bestProfitValue, b.profit);

			String tip = sourceTooltip(b.source, b.profit);
			bestNameValue.setToolTipText(tip);
			bestProfitValue.setToolTipText(tip);
			bestRow.setVisible(true);
			return;
		}
		if (local.bestFlip != null && local.bestFlip.profit > 0)
		{
			bestNameValue.setText(truncate(local.bestFlip.name, 16));
			bestNameValue.setForeground(Color.WHITE);
			bestNameValue.setToolTipText(null);
			setProfit(bestProfitValue, local.bestFlip.profit);
			bestProfitValue.setToolTipText(null);
			bestRow.setVisible(true);
			return;
		}
		bestRow.setVisible(false);
	}

	private static Color bestNameColor(String source)
	{
		if ("declared".equals(source))
		{
			return new Color(0xE8A838);   // amber — projection, not real fill
		}
		if ("mixed".equals(source))
		{
			return new Color(0xC4A052);   // dimmer gold — partial fill backing
		}
		return Color.WHITE;
	}

	private static String sourceTooltip(String source, long profit)
	{
		String amount = FlipItemPanel.formatGp(profit) + " gp";
		if ("declared".equals(source))
		{
			return "<html><b>" + amount + " — projected</b><br>"
				+ "Manually closed on the website at a target price.<br>"
				+ "No real GE fills are linked to this flip.</html>";
		}
		if ("mixed".equals(source))
		{
			return "<html><b>" + amount + " — partly verified</b><br>"
				+ "Some quantity closed manually, some by real GE fills.</html>";
		}
		return "<html><b>" + amount + " — verified</b><br>"
			+ "Backed by real GE trades captured by the plugin.</html>";
	}

	// ── helpers ─────────────────────────────────────────────────────────────

	private static long sumProfitSinceTodayStart(ProfitCalculator.Result result)
	{
		return sumProfitSince(result, periodStart(0));
	}

	/**
	 * Counts sells today that produced a phantom CompletedFlip — i.e., the
	 * FIFO matcher couldn't pair them with a prior buy in tradeHistory. Used
	 * by the Today tooltip so users understand why a fresh sell didn't bump
	 * the Today number.
	 */
	private static int countPhantomsSinceTodayStart(ProfitCalculator.Result result)
	{
		long fromMillis = periodStart(0);
		int phantoms = 0;
		for (ProfitCalculator.CompletedFlip f : result.completedFlips)
		{
			if (f.buyTotal <= 0 && f.sellTimestamp >= fromMillis)
			{
				phantoms++;
			}
		}
		return phantoms;
	}

	private static long periodStart(int daysAgo)
	{
		return LocalDate.now()
			.minusDays(daysAgo)
			.atStartOfDay(ZoneId.systemDefault())
			.toInstant()
			.toEpochMilli();
	}

	private static long sumProfitSince(ProfitCalculator.Result result, long fromMillis)
	{
		long sum = 0L;
		for (ProfitCalculator.CompletedFlip f : result.completedFlips)
		{
			if (f.buyTotal > 0 && f.sellTimestamp >= fromMillis)
			{
				sum += f.profit;
			}
		}
		return sum;
	}

	private static int countFlipsSince(ProfitCalculator.Result result, long fromMillis)
	{
		int n = 0;
		for (ProfitCalculator.CompletedFlip f : result.completedFlips)
		{
			if (f.buyTotal > 0 && f.sellTimestamp >= fromMillis)
			{
				n++;
			}
		}
		return n;
	}

	private static void applyPeriod(JLabel label, ProfitCalculator.Result result, long fromMillis)
	{
		long profit = sumProfitSince(result, fromMillis);
		int  count  = countFlipsSince(result, fromMillis);
		String prefix = profit > 0 ? "+" : "";
		label.setText(prefix + FlipItemPanel.formatGp(profit) + " gp · " + count + (count == 1 ? " flip" : " flips"));
		if (profit > 0)
		{
			label.setForeground(PROFIT_COL);
		}
		else if (profit < 0)
		{
			label.setForeground(LOSS_COL);
		}
		else
		{
			label.setForeground(Color.LIGHT_GRAY);
		}
	}

	private static void setProfit(JLabel label, long profit)
	{
		String prefix = profit > 0 ? "+" : "";
		label.setText(prefix + FlipItemPanel.formatGp(profit) + " gp");
		if (profit > 0)
		{
			label.setForeground(PROFIT_COL);
		}
		else if (profit < 0)
		{
			label.setForeground(LOSS_COL);
		}
		else
		{
			label.setForeground(Color.LIGHT_GRAY);
		}
	}

	private static JLabel valueLabel()
	{
		JLabel l = new JLabel(" ");
		l.setFont(Fonts.SM_BOLD);
		l.setForeground(Color.WHITE);
		l.setHorizontalAlignment(SwingConstants.RIGHT);
		return l;
	}

	private static JLabel sectionHeader(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(Fonts.SM_BOLD);
		l.setForeground(HEADER_COL);
		l.setBorder(new EmptyBorder(0, 0, 4, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JPanel row(String labelText, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JLabel l = new JLabel(labelText);
		l.setFont(Fonts.SM);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(l,     BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		return row;
	}

	/**
	 * Two-line row variant for values that won't fit alongside their label
	 * (currently just "Membership cost" with its full gp + count text).
	 * Label sits on top in grey, value sits below right-aligned in its
	 * own colour — same look as the single-line row, just split.
	 */
	private static JPanel stackedRow(String labelText, JLabel value)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel l = new JLabel(labelText);
		l.setFont(Fonts.SM);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		// value already has horizontalAlignment=RIGHT from valueLabel(); just
		// stretch it to full row width so the right-align fires.
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		value.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		row.add(l);
		row.add(value);
		return row;
	}

	private static JPanel row(String labelText, JLabel name, JLabel profit)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JLabel l = new JLabel(labelText);
		l.setFont(Fonts.SM);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel rightSide = new JPanel(new BorderLayout(8, 0));
		rightSide.setBackground(SECTION_BG);
		name.setHorizontalAlignment(SwingConstants.RIGHT);
		profit.setHorizontalAlignment(SwingConstants.RIGHT);
		rightSide.add(name,   BorderLayout.CENTER);
		rightSide.add(profit, BorderLayout.EAST);

		row.add(l,         BorderLayout.WEST);
		row.add(rightSide, BorderLayout.CENTER);
		return row;
	}

	private static String truncate(String s, int max)
	{
		if (s == null)
		{
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}
}
