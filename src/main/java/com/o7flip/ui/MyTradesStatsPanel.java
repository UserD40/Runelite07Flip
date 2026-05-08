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

	private final JPanel bestRow;
	private final JPanel worstRow;

	public MyTradesStatsPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(SECTION_BG);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(sectionHeader("Profit"));
		add(row("Total",      totalProfitValue));
		add(row("Today",      todayProfitValue));

		add(Box.createVerticalStrut(8));
		add(sectionHeader("Performance"));
		add(row("Trades",     tradesValue));
		add(row("Win rate",   winRateValue));
		add(row("Avg ROI",    avgRoiValue));
		add(row("GE tax paid (est.)", taxValue));

		add(Box.createVerticalStrut(8));
		add(sectionHeader("Highlights"));
		bestRow  = row("Best",  bestNameValue,  bestProfitValue);
		worstRow = row("Worst", worstNameValue, worstProfitValue);
		add(bestRow);
		add(worstRow);
	}

	/**
	 * Refresh all labels from a freshly-computed result. Hides the whole
	 * panel when there are zero completed flips, since every value would
	 * be meaningless.
	 */
	public void update(ProfitCalculator.Result result)
	{
		ProfitCalculator.Stats stats = result.stats;

		if (stats.completedFlipCount == 0)
		{
			setVisible(false);
			return;
		}
		setVisible(true);

		long todayProfit = sumProfitSinceTodayStart(result);

		setProfit(totalProfitValue, stats.totalProfit);
		setProfit(todayProfitValue, todayProfit);

		tradesValue.setText(String.valueOf(stats.completedFlipCount));
		tradesValue.setForeground(Color.WHITE);

		winRateValue.setText(String.format("%.0f%% (%dW / %dL / %dE)",
			stats.winRatePct, stats.winCount, stats.lossCount, stats.breakEvenCount));
		winRateValue.setForeground(Color.WHITE);

		avgRoiValue.setText(String.format("%+.1f%%", stats.avgRoiPct));
		avgRoiValue.setForeground(stats.avgRoiPct >= 0 ? PROFIT_COL : LOSS_COL);

		// Approx GE tax: server records totalGpSold post-tax, so gross = post / 0.98
		// and tax = gross × 0.02 = post × 2/98. Slightly overstates for items <100gp
		// (no tax) and understates for >250M (5M cap), both rare in practice.
		long estimatedTax = Math.round(stats.totalGpSold * (2.0 / 98.0));
		taxValue.setText(FlipItemPanel.formatGp(estimatedTax) + " gp");
		taxValue.setForeground(Color.LIGHT_GRAY);

		if (stats.bestFlip != null && stats.bestFlip.profit > 0)
		{
			bestNameValue.setText(truncate(stats.bestFlip.name, 16));
			bestNameValue.setForeground(Color.WHITE);
			setProfit(bestProfitValue, stats.bestFlip.profit);
			bestRow.setVisible(true);
		}
		else
		{
			bestRow.setVisible(false);
		}

		if (stats.worstFlip != null && stats.worstFlip.profit < 0)
		{
			worstNameValue.setText(truncate(stats.worstFlip.name, 16));
			worstNameValue.setForeground(Color.WHITE);
			setProfit(worstProfitValue, stats.worstFlip.profit);
			worstRow.setVisible(true);
		}
		else
		{
			worstRow.setVisible(false);
		}

		revalidate();
		repaint();
	}

	// ── helpers ─────────────────────────────────────────────────────────────

	private static long sumProfitSinceTodayStart(ProfitCalculator.Result result)
	{
		long todayStart = LocalDate.now()
			.atStartOfDay(ZoneId.systemDefault())
			.toInstant()
			.toEpochMilli();
		long sum = 0L;
		for (ProfitCalculator.CompletedFlip f : result.completedFlips)
		{
			if (f.sellTimestamp >= todayStart)
			{
				sum += f.profit;
			}
		}
		return sum;
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
