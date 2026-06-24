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
import com.o7flip.util.BondLedger;
import com.o7flip.util.Fonts;
import com.o7flip.util.ProfitCalculator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

public class MyTradesStatsPanel extends JPanel
{
	private static final Color PROFIT_COL  = new Color(0x00C27A);
	private static final Color LOSS_COL    = new Color(0xE85050);
	private static final Color SECTION_BG  = new Color(0x1F1F1F);
	private static final Color HEADER_COL  = new Color(0xC4A052);

	public enum Period
	{
		DAILY  ("Today", 0),
		WEEKLY ("Week",  7),
		MONTHLY("Month", 30),
		ALL_TIME("All", -1);

		public final String label;
		private final int daysAgo;

		Period(String label, int daysAgo)
		{
			this.label = label;
			this.daysAgo = daysAgo;
		}

		long startMillis()
		{
			if (daysAgo < 0)
			{
				return Long.MIN_VALUE;
			}
			return LocalDate.now()
				.minusDays(daysAgo)
				.atStartOfDay(ZoneId.systemDefault())
				.toInstant()
				.toEpochMilli();
		}

		public String phrase()
		{
			switch (this)
			{
				case DAILY:    return "today";
				case WEEKLY:   return "this week";
				case MONTHLY:  return "this month";
				case ALL_TIME: return "all time";
				default:       return label.toLowerCase();
			}
		}
	}

	private final JLabel profitHeader     = sectionHeaderLabel("Profit");
	private final JLabel totalProfitLabel = rowLabel("Total");
	private final JLabel totalProfitValue = valueLabel();
	private final JLabel tradesValue      = valueLabel();
	private final JLabel winRateValue     = valueLabel();
	private final JLabel avgRoiValue      = valueLabel();
	private final JLabel taxValue         = valueLabel();
	private final JLabel investedValue    = valueLabel();
	private final JLabel bestNameValue    = valueLabel();
	private final JLabel bestProfitValue  = valueLabel();
	private final JLabel worstNameValue   = valueLabel();
	private final JLabel worstProfitValue = valueLabel();
	private final JLabel bondsValue       = valueLabel();
	private final JLabel bondsToggle      = toggleLabel("−");

	private final JPanel bestRow;
	private final JPanel worstRow;
	private final JPanel bondsRow;

	private final JPanel body = new JPanel();
	private final JLabel collapseToggle = toggleLabel("▾");
	private boolean collapsed = false;

	private Runnable onMembershipToggle = () -> {};

	private Runnable onMembershipAdjust = () -> {};

	public MyTradesStatsPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(SECTION_BG);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(buildHeaderBar());

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(SECTION_BG);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);

		body.add(row(totalProfitLabel, totalProfitValue));
		bestRow  = row("Best",  bestNameValue,  bestProfitValue);
		worstRow = row("Worst", worstNameValue, worstProfitValue);
		body.add(bestRow);
		body.add(worstRow);

		body.add(Box.createVerticalStrut(8));
		body.add(sectionHeaderLabel("Performance"));
		body.add(row("Trades",     tradesValue));
		body.add(row("Win rate",   winRateValue));
		body.add(row("Avg ROI",    avgRoiValue));
		body.add(row("GE tax paid", taxValue));
		body.add(row("Invested",   investedValue));
		bondsRow = stackedBondsRow();
		body.add(bondsRow);

		add(body);
	}

	private JPanel buildHeaderBar()
	{
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(SECTION_BG);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

		collapseToggle.setToolTipText("Hide stats");
		collapseToggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setCollapsed(!collapsed);
			}
		});

		bar.add(profitHeader,   BorderLayout.WEST);
		bar.add(collapseToggle, BorderLayout.EAST);
		return bar;
	}

	private void setCollapsed(boolean c)
	{
		collapsed = c;
		body.setVisible(!c);
		collapseToggle.setText(c ? "▴" : "▾");
		collapseToggle.setToolTipText(c ? "Show stats" : "Hide stats");
		revalidate();
		repaint();
	}

	public void setOnMembershipToggle(Runnable r)
	{
		this.onMembershipToggle = r != null ? r : () -> {};
	}

	public void setOnMembershipAdjust(Runnable r)
	{
		this.onMembershipAdjust = r != null ? r : () -> {};
	}

	public void update(ProfitCalculator.Result result, TrackerStats server, BondLedger bonds,
		Period period, boolean membershipHidden, long activeBuyGp, long heldCostBasisInActiveSells)
	{
		if (period == null) period = Period.ALL_TIME;
		if (bonds  == null) bonds  = BondLedger.EMPTY;

		PeriodStats stats = PeriodStats.from(result, period);
		boolean preferServer = period == Period.ALL_TIME && server != null && server.closedCount > 0;
		boolean hasLocal     = stats.matchedCount > 0;
		boolean showBondsRow = true;

		if (!preferServer && !hasLocal && !showBondsRow)
		{
			setVisible(false);
			return;
		}
		setVisible(true);

		profitHeader.setText("Profit · " + period.label);

		long totalProfit = preferServer ? server.totalRealisedProfit : stats.totalProfit;
		setProfit(totalProfitValue, totalProfit);
		if (preferServer && server.declaredProfit != 0L)
		{
			totalProfitValue.setToolTipText(String.format(
				"<html>%s gp confirmed by your GE trades<br>%s gp self-reported (manually-closed flips)</html>",
				FlipItemPanel.formatGp(server.verifiedProfit),
				FlipItemPanel.formatGp(server.declaredProfit)));
		}
		else if (stats.phantomCount > 0)
		{
			totalProfitValue.setToolTipText(String.format(
				"<html>%s gp from matched flips in %s.<br>"
				+ "<font color='#888888'>%d sell%s in this window had no matching buy in tracked history<br>"
				+ "and %s excluded from the total.</font></html>",
				FlipItemPanel.formatGp(totalProfit),
				period.phrase(),
				stats.phantomCount,
				stats.phantomCount == 1 ? "" : "s",
				stats.phantomCount == 1 ? "is" : "are"));
		}
		else
		{
			totalProfitValue.setToolTipText(null);
		}

		int tradesCount = preferServer ? server.closedCount : stats.matchedCount;
		tradesValue.setText(String.valueOf(tradesCount));
		tradesValue.setForeground(Color.WHITE);

		if (preferServer)
		{
			winRateValue.setText(String.format("%.0f%%", server.winRate * 100.0));
		}
		else if (hasLocal)
		{
			winRateValue.setText(String.format("%.0f%% (%dW / %dL)",
				stats.winRatePct, stats.winCount, stats.lossCount));
		}
		else
		{
			winRateValue.setText("—");
		}
		winRateValue.setForeground(Color.WHITE);

		if (hasLocal)
		{
			avgRoiValue.setText(String.format("%+.1f%%", stats.avgRoiPct));
			avgRoiValue.setForeground(stats.avgRoiPct >= 0 ? PROFIT_COL : LOSS_COL);
		}
		else
		{
			avgRoiValue.setText("—");
			avgRoiValue.setForeground(Color.LIGHT_GRAY);
		}

		if (hasLocal)
		{
			taxValue.setText(FlipItemPanel.formatGp(stats.totalTaxPaid) + " gp");
		}
		else
		{
			taxValue.setText("—");
		}
		taxValue.setForeground(Color.LIGHT_GRAY);

		long heldGross = Math.max(0L, heldCostBasisInActiveSells);
		long heldNet = Math.round(heldGross * 0.98);
		long invested = Math.max(0L, activeBuyGp) + heldNet;
		investedValue.setText(FlipItemPanel.formatGp(invested) + " gp");
		investedValue.setForeground(invested > 0 ? Color.WHITE : Color.LIGHT_GRAY);
		investedValue.setToolTipText(String.format(
			"<html>%s gp in active buy offers<br>%s gp cost basis of items in active sell offers (−2%% GE tax = %s gp)</html>",
			FlipItemPanel.formatGp(Math.max(0L, activeBuyGp)),
			FlipItemPanel.formatGp(heldGross),
			FlipItemPanel.formatGp(heldNet)));

		applyBestRow(stats, server, preferServer);
		applyWorstRow(stats);

		if (showBondsRow)
		{
			if (membershipHidden)
			{
				bondsValue.setText("•••");
				bondsValue.setForeground(Color.LIGHT_GRAY);
				bondsToggle.setText("+");
				bondsToggle.setToolTipText("Click to reveal the lifetime bond total");
			}
			else
			{
				if (bonds.count > 0)
				{
					String unit = bonds.count == 1 ? " bond" : " bonds";
					bondsValue.setText(FlipItemPanel.formatGp(bonds.spend) + " gp · " + bonds.count + unit);
					bondsValue.setForeground(LOSS_COL);
				}
				else
				{
					bondsValue.setText("0 gp · 0 bonds");
					bondsValue.setForeground(Color.LIGHT_GRAY);
				}
				bondsToggle.setText("−");
				bondsToggle.setToolTipText("Click to hide the lifetime bond total. Right-click the row to adjust it.");
			}
			bondsRow.setVisible(true);
		}
		else
		{
			bondsRow.setVisible(false);
		}

		revalidate();
		repaint();
	}

	private void applyBestRow(PeriodStats stats, TrackerStats server, boolean preferServer)
	{
		if (preferServer && server.bestFlip != null && server.bestFlip.profit > 0)
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
		if (stats.bestFlip != null && stats.bestFlip.profit > 0)
		{
			bestNameValue.setText(truncate(stats.bestFlip.name, 16));
			bestNameValue.setForeground(Color.WHITE);
			bestNameValue.setToolTipText(null);
			setProfit(bestProfitValue, stats.bestFlip.profit);
			bestProfitValue.setToolTipText(null);
			bestRow.setVisible(true);
			return;
		}
		bestRow.setVisible(false);
	}

	private void applyWorstRow(PeriodStats stats)
	{
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
	}

	private static Color bestNameColor(String source)
	{
		if ("declared".equals(source))
		{
			return new Color(0xE8A838);
		}
		if ("mixed".equals(source))
		{
			return new Color(0xC4A052);
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

	private static final class PeriodStats
	{
		final long totalProfit;
		final long totalTaxPaid;
		final int  matchedCount;
		final int  winCount;
		final int  lossCount;
		final int  breakEvenCount;
		final double winRatePct;
		final double avgRoiPct;
		final ProfitCalculator.CompletedFlip bestFlip;
		final ProfitCalculator.CompletedFlip worstFlip;
		final int phantomCount;

		PeriodStats(long totalProfit, long totalTaxPaid, int matchedCount,
			int winCount, int lossCount, int breakEvenCount,
			double winRatePct, double avgRoiPct,
			ProfitCalculator.CompletedFlip best, ProfitCalculator.CompletedFlip worst,
			int phantomCount)
		{
			this.totalProfit = totalProfit;
			this.totalTaxPaid = totalTaxPaid;
			this.matchedCount = matchedCount;
			this.winCount = winCount;
			this.lossCount = lossCount;
			this.breakEvenCount = breakEvenCount;
			this.winRatePct = winRatePct;
			this.avgRoiPct = avgRoiPct;
			this.bestFlip = best;
			this.worstFlip = worst;
			this.phantomCount = phantomCount;
		}

		static PeriodStats from(ProfitCalculator.Result result, Period period)
		{
			long from = period.startMillis();
			long totalProfit = 0L;
			long totalTaxPaid = 0L;
			int matched = 0, wins = 0, losses = 0, evens = 0, phantoms = 0;
			double roiSum = 0.0;
			ProfitCalculator.CompletedFlip best = null, worst = null;
			for (ProfitCalculator.CompletedFlip f : result.completedFlips)
			{
				if (f.sellTimestamp < from)
				{
					continue;
				}
				if (f.buyTotal <= 0)
				{
					phantoms++;
					continue;
				}
				matched++;
				totalProfit += f.profit;
				totalTaxPaid += f.tax;
				if (f.profit > 0) wins++;
				else if (f.profit < 0) losses++;
				else evens++;
				roiSum += f.roiPct;
				if (best  == null || f.profit > best.profit)  best  = f;
				if (worst == null || f.profit < worst.profit) worst = f;
			}
			double winRate = matched > 0 ? 100.0 * wins / matched : 0.0;
			double avgRoi  = matched > 0 ? roiSum / matched : 0.0;
			return new PeriodStats(totalProfit, totalTaxPaid, matched, wins, losses, evens,
				winRate, avgRoi, best, worst, phantoms);
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

	private static JLabel sectionHeaderLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(Fonts.SM_BOLD);
		l.setForeground(HEADER_COL);
		l.setBorder(new EmptyBorder(0, 0, 4, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel rowLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(Fonts.SM);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private static JPanel row(String labelText, JLabel value)
	{
		return row(rowLabel(labelText), value);
	}

	private static JPanel row(JLabel label, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		row.add(label, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		return row;
	}

	private JPanel stackedBondsRow()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JPanel leftCluster = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		leftCluster.setBackground(SECTION_BG);
		leftCluster.add(rowLabel("Bonds bought"));

		bondsToggle.setHorizontalAlignment(SwingConstants.CENTER);
		bondsToggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					onMembershipToggle.run();
				}
			}
		});
		leftCluster.add(bondsToggle);

		bondsValue.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(leftCluster, BorderLayout.WEST);
		row.add(bondsValue,  BorderLayout.EAST);

		JPopupMenu menu = new JPopupMenu();
		javax.swing.JMenuItem adjust = new javax.swing.JMenuItem("Adjust lifetime…");
		adjust.setToolTipText("Manually set the lifetime gp + bond count, e.g. to recover history from before the ledger existed");
		adjust.addActionListener(e -> onMembershipAdjust.run());
		menu.add(adjust);
		row.setComponentPopupMenu(menu);
		leftCluster.setComponentPopupMenu(menu);
		bondsValue.setComponentPopupMenu(menu);

		return row;
	}

	private static JLabel toggleLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(Fonts.SM);
		l.setForeground(new Color(0x8AB6FF));
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return l;
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
