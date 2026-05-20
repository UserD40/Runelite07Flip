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

/**
 * Compact stats summary that sits above the trade list on the My Trades tab.
 *
 * Every figure on the panel is scoped to a {@link Period} chosen via the
 * Filter pill in the sort bar — Daily, Weekly, Monthly, or All time. Older
 * versions of this panel stacked all four windows on screen at once (Today,
 * This week, This month plus a separate Total); the filter replaces that
 * with a single set of rows the user re-targets at will. Cleaner read,
 * more screen budget for the trade list below.
 *
 * Server-side {@link TrackerStats} (lifetime, not period-filterable) is only
 * preferred when the panel is showing All time — for shorter windows we
 * fall back to filtering the local FIFO result by {@code sellTimestamp}.
 * The bond ledger is always lifetime, since "Membership cost" is a
 * cumulative account stat.
 */
public class MyTradesStatsPanel extends JPanel
{
	private static final Color PROFIT_COL  = new Color(0x00C27A);
	private static final Color LOSS_COL    = new Color(0xE85050);
	private static final Color SECTION_BG  = new Color(0x1F1F1F);
	private static final Color HEADER_COL  = new Color(0xC4A052);

	/**
	 * Stats window the panel renders. {@code daysAgo == -1} means "no
	 * filter" — every matched flip in tradeHistory contributes. The
	 * runtime label ({@link #label}) is the one the user sees in the
	 * section header and the filter dropdown.
	 */
	public enum Period
	{
		// Labels are intentionally terse — the period name renders inside
		// the Filter pill on the sort bar next to Active/Recent/Margin,
		// and "This week"/"This month"/"All time" pushed the Margin
		// button off-screen. "Today / Week / Month / All" keeps every
		// sub-tab visible without sacrificing clarity.
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

		/**
		 * Longer-form phrase used in body text — empty-state copy, tooltips —
		 * where the terse {@link #label} ("Week", "All") would read
		 * awkwardly inside a sentence. Keeps the pill compact while still
		 * sounding natural in prose.
		 */
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

	/**
	 * Callback fired when the user clicks the hide/show toggle on the
	 * Membership cost row. The panel itself doesn't persist the state —
	 * O7FlipPanel owns the config-backed flag and feeds it back through
	 * {@link #update(ProfitCalculator.Result, TrackerStats, BondLedger, Period, boolean)}.
	 */
	private Runnable onMembershipToggle = () -> {};

	/**
	 * Callback fired when the user picks "Adjust lifetime…" from the
	 * right-click on the Membership cost row. O7FlipPanel pops the
	 * dialog so this class stays free of frame/dialog plumbing.
	 */
	private Runnable onMembershipAdjust = () -> {};

	public MyTradesStatsPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(SECTION_BG);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(profitHeader);
		add(row(totalProfitLabel, totalProfitValue));
		bestRow  = row("Best",  bestNameValue,  bestProfitValue);
		worstRow = row("Worst", worstNameValue, worstProfitValue);
		add(bestRow);
		add(worstRow);

		add(Box.createVerticalStrut(8));
		add(sectionHeaderLabel("Performance"));
		add(row("Trades",     tradesValue));
		add(row("Win rate",   winRateValue));
		add(row("Avg ROI",    avgRoiValue));
		add(row("GE tax paid", taxValue));
		// Current capital tied up in trading: gp held by the GE for pending
		// buy offers + cost basis of items bought but not yet sold, with a 2%
		// haircut on the held items as a conservative estimate of the GE tax
		// that'll be deducted when they eventually sell. Snapshot — does not
		// honour the period filter.
		add(row("Invested",   investedValue));
		// Membership cost can be very long ("289,128,766 gp · 21 bonds")
		// so it gets a stacked row — label + toggle on top, value on its
		// own line below right-aligned.
		bondsRow = stackedBondsRow();
		add(bondsRow);
	}

	/** Setter used by O7FlipPanel to wire the hide/show config flip. */
	public void setOnMembershipToggle(Runnable r)
	{
		this.onMembershipToggle = r != null ? r : () -> {};
	}

	/** Setter used by O7FlipPanel to open the lifetime-adjust dialog. */
	public void setOnMembershipAdjust(Runnable r)
	{
		this.onMembershipAdjust = r != null ? r : () -> {};
	}

	/**
	 * Backwards-compatible local-only update — still used by tests and any
	 * caller that doesn't have server stats handy. Renders all-time.
	 */
	public void update(ProfitCalculator.Result result)
	{
		update(result, null, BondLedger.EMPTY, Period.ALL_TIME, false);
	}

	/**
	 * Compatibility shim for callers that haven't been migrated to pass a
	 * {@link Period}. Defaults to {@link Period#ALL_TIME}.
	 */
	public void update(ProfitCalculator.Result result, TrackerStats server, BondLedger bonds)
	{
		update(result, server, bonds, Period.ALL_TIME, false);
	}

	/**
	 * Compatibility shim for callers that don't pass the hide flag yet.
	 */
	public void update(ProfitCalculator.Result result, TrackerStats server, BondLedger bonds, Period period)
	{
		update(result, server, bonds, period, false, 0L);
	}

	/**
	 * Compatibility shim for callers that pass everything except the gp tied
	 * up in active buy offers.
	 */
	public void update(ProfitCalculator.Result result, TrackerStats server, BondLedger bonds,
		Period period, boolean membershipHidden)
	{
		update(result, server, bonds, period, membershipHidden, 0L, 0L);
	}

	/**
	 * Compatibility shim for callers that pass activeBuyGp but not the
	 * pre-filtered held cost basis.
	 */
	public void update(ProfitCalculator.Result result, TrackerStats server, BondLedger bonds,
		Period period, boolean membershipHidden, long activeBuyGp)
	{
		update(result, server, bonds, period, membershipHidden, activeBuyGp, 0L);
	}

	/**
	 * Refresh every label for the chosen period.
	 *
	 * Server stats ({@link TrackerStats}) are only consulted when the user
	 * is viewing All time — they cover lifetime and have no per-flip
	 * timestamps, so they can't answer "last 7 days". Bond ledger is
	 * always lifetime (membership cost is cumulative by definition).
	 *
	 * "Invested" snapshot components, both passed pre-computed by the
	 * caller because filtering open positions against active sell offers
	 * requires the live offer snapshot which lives on the plugin:
	 * <ul>
	 *   <li>{@code activeBuyGp} — gp the GE is currently holding for
	 *       pending buy offers.</li>
	 *   <li>{@code heldCostBasisInActiveSells} — gross cost basis of items
	 *       currently sitting in active SELLING offers, proportional to
	 *       qty remaining to fill. Excludes inventory hoarding and old
	 *       open positions whose corresponding sell history fell off the
	 *       rolling window.</li>
	 * </ul>
	 */
	public void update(ProfitCalculator.Result result, TrackerStats server, BondLedger bonds,
		Period period, boolean membershipHidden, long activeBuyGp, long heldCostBasisInActiveSells)
	{
		if (period == null) period = Period.ALL_TIME;
		if (bonds  == null) bonds  = BondLedger.EMPTY;

		PeriodStats stats = PeriodStats.from(result, period);
		boolean preferServer = period == Period.ALL_TIME && server != null && server.closedCount > 0;
		boolean hasLocal     = stats.matchedCount > 0;
		// Membership cost is a lifetime stat — it's the same number whether
		// the user is looking at Today or All time. Always render the row;
		// the period filter doesn't apply to it. A small (lifetime) hint
		// next to the gp value is what disambiguates when the user is on
		// a shorter window.
		boolean showBondsRow = true;

		// Keep the panel visible if there's any signal — flips, bonds, or
		// server stats. A user who's only redeemed bonds (no flips captured
		// yet) still sees the Membership cost line.
		if (!preferServer && !hasLocal && !showBondsRow)
		{
			setVisible(false);
			return;
		}
		setVisible(true);

		profitHeader.setText("Profit · " + period.label);

		// Total label always reads "Total"; the section header carries the
		// period name. Keeping the row label fixed avoids a layout shudder
		// when the user switches windows.
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
			// Server returns just the rate; W/L/E breakdown isn't in /tracker/stats.
			winRateValue.setText(String.format("%.0f%%", server.winRate * 100.0));
		}
		else if (hasLocal)
		{
			// Breakeven count is dropped — every sell is taxed, so an exact
			// 0 profit flip is effectively impossible. W/L is what the user
			// reads at a glance.
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

		// GE tax: exact sum across matched flips in the period.
		// ProfitCalculator applies OSRS rules per flip (2% with 5M/item
		// cap, exempt below 100 gp/item, exempt for bonds), so the period
		// sum is authoritative — the total profit shown above is already
		// net of this figure.
		if (hasLocal)
		{
			taxValue.setText(FlipItemPanel.formatGp(stats.totalTaxPaid) + " gp");
		}
		else
		{
			taxValue.setText("—");
		}
		taxValue.setForeground(Color.LIGHT_GRAY);

		// "Invested" snapshot — independent of the period filter. Counts
		// only what's tied up in live GE activity right now: gp in active
		// buy offers + cost basis of items currently sitting in active
		// SELL offers. Held inventory the user hasn't listed is excluded
		// (it's an asset but not "in the GE"), as are phantom open
		// positions where the matching sell has fallen out of the rolling
		// 200-row history. The 2% haircut on the held side is a
		// conservative estimate of the GE tax that'll be deducted when
		// the active sells fill.
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

		// Membership cost — lifetime stat, only rendered on All time. The
		// hide toggle lets the user mask the gp value (privacy / streaming)
		// without removing the row from the layout. Adjust… in the right-
		// click menu opens a dialog for manually seeding the ledger when
		// the migration couldn't recover historical bonds.
		if (showBondsRow)
		{
			if (membershipHidden)
			{
				bondsValue.setText("•••");
				bondsValue.setForeground(Color.LIGHT_GRAY);
				// + means "expand / reveal" — click to show the gp/count again.
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
				// − means "collapse / hide" — click to mask the value.
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

	// ── period stats ────────────────────────────────────────────────────────

	/**
	 * Snapshot of stats over a single {@link Period}. Mirrors the relevant
	 * fields of {@link ProfitCalculator.Stats} but is computed by
	 * re-walking {@code completedFlips} with a timestamp filter so it can
	 * answer "last 7 days" without re-running the FIFO matcher.
	 */
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
					// Phantom sells (no matching buy in tracked history) are
					// excluded from every aggregate stat in the same way
					// ProfitCalculator does — including them would inflate
					// totalProfit by the gross sale of items whose cost
					// basis pre-dates the plugin's recording window.
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

	// ── helpers ─────────────────────────────────────────────────────────────

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

	private static JPanel stackedRow(String labelText, JLabel value)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel l = rowLabel(labelText);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		value.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		row.add(l);
		row.add(value);
		return row;
	}

	/**
	 * Single-line row for the bond ledger.
	 *
	 * Layout:
	 * <pre>
	 *   Bonds bought  [+/-]                 0 gp · 0 bonds
	 * </pre>
	 *
	 * Label sits on the left with the +/- toggle next to it — {@code +}
	 * when the value is currently hidden (click to reveal), {@code -}
	 * when visible (click to collapse). Value renders on the right same
	 * as every other row in the panel. Right-click anywhere opens the
	 * "Adjust lifetime…" dialog for manual seeding.
	 *
	 * Built once in the constructor; the labels are mutated by
	 * {@link #update}.
	 */
	private JPanel stackedBondsRow()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		// Left side: "Bonds bought" label paired with the inline +/-
		// toggle. FlowLayout(LEFT, 4, 0) keeps the toggle tight against
		// the label without baseline shenanigans.
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

		// Right-click anywhere on the row opens the lifetime-adjust dialog.
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
