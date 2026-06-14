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
import com.o7flip.model.ScreenerMatch;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Row component for a single technical-indicator match inside a screener
 * preset. Designed to surface a lot of TA cleanly: each indicator family
 * gets its own dedicated line, with explicit vertical struts between
 * rows so neighbouring cards don't read as squashed.
 *
 * Layout (rows shown only when data is present):
 * <pre>
 *   [icon]  Name                                [↑ MACD] or [score]
 *           Buy 1.2K · Sell 1.4K · +200 (16.7%)        — if server enriched
 *           1d +19.6% · 7d +32.9% · 30d +25.9%
 *           BB 0.85 · 30d 0.92 · 90d 0.78
 *           MACD +25.0K · Vol 3.1×
 * </pre>
 */
public class ScreenerMatchPanel extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color HOVER_BG  = new Color(0x3A3A3A);
	private static final Color GREEN     = new Color(0x00C27A);
	private static final Color RED       = new Color(0xE85050);
	private static final Color MUTED     = new Color(0x888888);

	public ScreenerMatchPanel(ScreenerMatch m, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		// Slightly more vertical padding than other cards — screener rows
		// carry more density of info, so the extra breathing room helps the
		// adjacent rows not feel cramped.
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(m.itemId, itemManager);

		// ── Row 1: name + trend / score chip ─────────────────────────────────
		JLabel nameLabel = new JLabel(m.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		// Prefer the MACD-cross chip (action signal); fall back to the
		// flip07 score when available; otherwise nothing.
		JLabel chip = buildHeaderChip(m);

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(bg);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameRow.add(nameLabel, BorderLayout.CENTER);
		nameRow.add(chip,      BorderLayout.EAST);

		// ── Text column — BoxLayout so we can inject explicit struts between
		// rows. GridLayout's fixed gaps were too tight and made rows visually
		// run together.
		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(bg);
		textPanel.add(nameRow);

		boolean hasPrice = m.buyPrice != null && m.sellPrice != null;
		if (hasPrice || m.profit != null)
		{
			JLabel priceRow = mkRow(buildPriceRow(m), bg);
			textPanel.add(Box.createVerticalStrut(4));
			textPanel.add(priceRow);
		}

		// Band Flip engine row — the recurring price band + how often it's recurred.
		if (m.bandFloor != null || m.bandCeiling != null || m.bandRecurrence != null || m.bandMarginPct != null)
		{
			JLabel bandRow = mkRow(buildBandRow(m), bg);
			bandRow.setToolTipText("<html><b>Band Flip</b><br>"
				+ "Floor – ceiling: the recurring price band to flip within.<br>"
				+ "×N seen: how many times the band has recurred.<br>"
				+ "%: band margin (ceiling vs floor).</html>");
			textPanel.add(Box.createVerticalStrut(4));
			textPanel.add(bandRow);
		}

		// Event Recovery engine rows — entry signal (how far it fell / how close
		// to the floor) then the thesis (where it's headed / how long).
		boolean hasRecovery = m.drawdownPct != null || m.recoveryTarget != null
			|| m.recoveryFromFloor != null || (m.recoveryWindow != null && !m.recoveryWindow.isEmpty());
		if (hasRecovery)
		{
			String entry = buildRecoveryEntryRow(m);
			if (!"<html></html>".equals(entry))
			{
				JLabel r = mkRow(entry, bg);
				r.setToolTipText("<html><b>Event Recovery — entry</b><br>"
					+ "Drawdown: how far price fell below the event-period peak.<br>"
					+ "Off floor: current price ÷ 90-day low — 1.0× sits on the floor "
					+ "(earliest, cheapest entry); higher means it has already recovered some.</html>");
				textPanel.add(Box.createVerticalStrut(4));
				textPanel.add(r);
			}
			String thesis = buildRecoveryThesisRow(m);
			if (!"<html></html>".equals(thesis))
			{
				JLabel r = mkRow(thesis, bg);
				r.setToolTipText("<html><b>Event Recovery — thesis</b><br>"
					+ "Target: estimated recovery sell price.<br>"
					+ "Window: expected time to recover.</html>");
				textPanel.add(Box.createVerticalStrut(3));
				textPanel.add(r);
			}
		}

		// Always render the % changes row when we have any of the three —
		// it's the headline TA signal for screener views.
		if (m.pct1d != null || m.pct7d != null || m.pct30d != null)
		{
			JLabel pctRow = mkRow(buildPctRow(m), bg);
			textPanel.add(Box.createVerticalStrut(4));
			textPanel.add(pctRow);
		}

		// Range positions: BB / 30d / 90d. All in [0,1].
		if (m.bbPosition != null || m.pricePos30d != null || m.pricePos90d != null)
		{
			JLabel posRow = mkRow(buildPositionRow(m), bg);
			posRow.setToolTipText("<html><b>Range position</b><br>"
				+ "BB: position within the Bollinger Bands (0 = lower band, 1 = upper band).<br>"
				+ "30d/90d: where current price sits within the n-day high–low range.</html>");
			textPanel.add(Box.createVerticalStrut(3));
			textPanel.add(posRow);
		}

		// MACD histogram + volume surge — momentum signals.
		if (m.macdHist != null || m.volSurge != null)
		{
			JLabel macdRow = mkRow(buildMacdVolRow(m), bg);
			macdRow.setToolTipText("<html><b>Momentum</b><br>"
				+ "MACD: histogram of the MACD line vs its signal — positive = bullish.<br>"
				+ "Vol: today's volume as a multiple of the recent baseline.</html>");
			textPanel.add(Box.createVerticalStrut(3));
			textPanel.add(macdRow);
		}

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		ClickRouter.attach(this, plugin, m.itemId, m.name);
		ClickRouter.attachClickOnly(nameLabel, plugin, m.itemId, m.name);
		ClickRouter.attachClickOnly(chip,      plugin, m.itemId, m.name);

		// Right-click queues a Buy when we have a market price to use.
		final long buyTarget = hasPrice ? m.sellPrice : (m.buyPrice != null ? m.buyPrice : 0L);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
				nameRow.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				textPanel.setBackground(bg);
				nameRow.setBackground(bg);
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null && buyTarget > 0)
				{
					plugin.queueGeBuy(m.itemId, buyTarget, m.name);
				}
			}
		});

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static JLabel buildHeaderChip(ScreenerMatch m)
	{
		// MACD cross is the most action-worthy signal — bullish/bearish
		// is a state change, not a steady-state metric. Prefer it.
		if (m.macdCross != null)
		{
			boolean bullish = "bullish".equalsIgnoreCase(m.macdCross);
			JLabel chip = new JLabel("<html><font color='" + (bullish ? "#00C27A" : "#E85050") + "'>"
				+ (bullish ? "↑ MACD" : "↓ MACD") + "</font></html>");
			chip.setFont(Fonts.BOLD);
			chip.setToolTipText("MACD signal-line cross: " + m.macdCross);
			return chip;
		}
		if (m.flip07Score != null)
		{
			int s = m.flip07Score;
			String c = s >= 70 ? "#00C27A" : (s >= 40 ? "#E8A838" : "#E85050");
			JLabel chip = new JLabel("<html><font color='" + c + "'>" + s + "</font></html>");
			chip.setFont(Fonts.BOLD);
			chip.setToolTipText("07Flip merch algorithm score (0–100)");
			return chip;
		}
		JLabel chip = new JLabel("");
		chip.setFont(Fonts.BOLD);
		return chip;
	}

	private static JLabel mkRow(String html, Color bg)
	{
		JLabel l = new JLabel(html);
		l.setFont(Fonts.SM);
		l.setForeground(MUTED);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBackground(bg);
		return l;
	}

	/** "Buy 1.2K · Sell 1.4K · +200 (16.7%)" — only when server enriched. */
	private static String buildPriceRow(ScreenerMatch m)
	{
		StringBuilder sb = new StringBuilder("<html>");
		boolean first = true;
		if (m.buyPrice != null)
		{
			sb.append("<font color='#FF7070'>Buy ").append(FlipItemPanel.formatGpCompact(m.buyPrice)).append("</font>");
			first = false;
		}
		if (m.sellPrice != null)
		{
			if (!first) sb.append("  <font color='#666666'>·</font>  ");
			sb.append("<font color='#00C27A'>Sell ").append(FlipItemPanel.formatGpCompact(m.sellPrice)).append("</font>");
			first = false;
		}
		if (m.profit != null)
		{
			if (!first) sb.append("  <font color='#666666'>·</font>  ");
			String sign = m.profit >= 0 ? "+" : "";
			String col  = m.profit >= 0 ? "#00C27A" : "#E85050";
			sb.append("<font color='").append(col).append("'>").append(sign)
			  .append(FlipItemPanel.formatGpCompact(m.profit)).append("</font>");
			if (m.roiPct != null)
			{
				sb.append("  <font color='#888888'>(").append(String.format("%.1f", m.roiPct)).append("%)</font>");
			}
		}
		sb.append("</html>");
		return sb.toString();
	}

	/** "Band 45.9K – 48.4K · 67× seen · 3.3%" for the Band Flip screener. */
	private static String buildBandRow(ScreenerMatch m)
	{
		StringBuilder sb = new StringBuilder("<html>");
		boolean first = true;
		if (m.bandFloor != null && m.bandCeiling != null)
		{
			sb.append("<font color='#888888'>Band </font>")
				.append("<font color='#DDDDDD'>")
				.append(FlipItemPanel.formatGpCompact(m.bandFloor)).append(" – ")
				.append(FlipItemPanel.formatGpCompact(m.bandCeiling)).append("</font>");
			first = false;
		}
		if (m.bandRecurrence != null)
		{
			if (!first) sb.append("  <font color='#555555'>·</font>  ");
			sb.append("<font color='#DDDDDD'>").append(m.bandRecurrence).append("×</font>")
				.append("<font color='#888888'> seen</font>");
			first = false;
		}
		if (m.bandMarginPct != null)
		{
			if (!first) sb.append("  <font color='#555555'>·</font>  ");
			sb.append("<font color='#00C27A'>").append(String.format("%.1f", m.bandMarginPct)).append("%</font>");
		}
		sb.append("</html>");
		return sb.toString();
	}

	/** "Drawdown 74.9% · 1.32× off floor" — the entry signal. */
	private static String buildRecoveryEntryRow(ScreenerMatch m)
	{
		StringBuilder sb = new StringBuilder("<html>");
		boolean first = true;
		if (m.drawdownPct != null)
		{
			sb.append("<font color='#888888'>Drawdown </font>")
				.append("<font color='#E85050'>").append(String.format("%.1f", m.drawdownPct)).append("%</font>");
			first = false;
		}
		if (m.recoveryFromFloor != null)
		{
			if (!first) sb.append("  <font color='#555555'>·</font>  ");
			// Closer to 1.0× = earlier/cheaper entry → greener.
			double v = m.recoveryFromFloor;
			String col = v <= 1.3 ? "#00C27A" : (v <= 2.2 ? "#E8A838" : "#888888");
			sb.append("<font color='").append(col).append("'>")
				.append(String.format("%.2f", v)).append("×</font>")
				.append("<font color='#888888'> off floor</font>");
		}
		sb.append("</html>");
		return sb.toString();
	}

	/** "Target 16.8M · 2–5 weeks" — the upside thesis. */
	private static String buildRecoveryThesisRow(ScreenerMatch m)
	{
		StringBuilder sb = new StringBuilder("<html>");
		boolean first = true;
		if (m.recoveryTarget != null)
		{
			sb.append("<font color='#888888'>Target </font>")
				.append("<font color='#00C27A'>").append(FlipItemPanel.formatGpCompact(m.recoveryTarget)).append("</font>");
			first = false;
		}
		if (m.recoveryWindow != null && !m.recoveryWindow.isEmpty())
		{
			if (!first) sb.append("  <font color='#555555'>·</font>  ");
			sb.append("<font color='#DDDDDD'>").append(escapeHtml(m.recoveryWindow)).append("</font>");
		}
		sb.append("</html>");
		return sb.toString();
	}

	private static String escapeHtml(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String buildPctRow(ScreenerMatch m)
	{
		StringBuilder sb = new StringBuilder("<html>");
		appendPct(sb, "1d",  m.pct1d);
		sb.append("  <font color='#555555'>·</font>  ");
		appendPct(sb, "7d",  m.pct7d);
		sb.append("  <font color='#555555'>·</font>  ");
		appendPct(sb, "30d", m.pct30d);
		sb.append("</html>");
		return sb.toString();
	}

	private static void appendPct(StringBuilder sb, String label, Double pct)
	{
		sb.append("<font color='#888888'>").append(label).append(" </font>");
		if (pct == null)
		{
			sb.append("<font color='#666666'>—</font>");
			return;
		}
		String col = pct >= 0 ? "#00C27A" : "#E85050";
		String sign = pct > 0 ? "+" : "";
		sb.append("<font color='").append(col).append("'>").append(sign)
			.append(String.format("%.1f", pct)).append("%</font>");
	}

	private static String buildPositionRow(ScreenerMatch m)
	{
		StringBuilder sb = new StringBuilder("<html>");
		boolean first = true;
		if (m.bbPosition != null)
		{
			sb.append(positionToken("BB",  m.bbPosition));
			first = false;
		}
		if (m.pricePos30d != null)
		{
			if (!first) sb.append("  <font color='#555555'>·</font>  ");
			sb.append(positionToken("30d", m.pricePos30d));
			first = false;
		}
		if (m.pricePos90d != null)
		{
			if (!first) sb.append("  <font color='#555555'>·</font>  ");
			sb.append(positionToken("90d", m.pricePos90d));
		}
		sb.append("</html>");
		return sb.toString();
	}

	/** "BB 0.85" where the value is amber at the extremes (≤0.15 or ≥0.85). */
	private static String positionToken(String label, double v)
	{
		String col;
		if (v <= 0.15)      col = "#E85050"; // oversold / near low
		else if (v >= 0.85) col = "#00C27A"; // overbought / near high
		else                col = "#DDDDDD";
		return "<font color='#888888'>" + label + " </font>"
			+ "<font color='" + col + "'>" + String.format("%.2f", v) + "</font>";
	}

	private static String buildMacdVolRow(ScreenerMatch m)
	{
		StringBuilder sb = new StringBuilder("<html>");
		boolean first = true;
		if (m.macdHist != null)
		{
			String col = m.macdHist >= 0 ? "#00C27A" : "#E85050";
			String sign = m.macdHist > 0 ? "+" : "";
			sb.append("<font color='#888888'>MACD </font>")
				.append("<font color='").append(col).append("'>")
				.append(sign).append(formatMacd(m.macdHist)).append("</font>");
			first = false;
		}
		if (m.volSurge != null)
		{
			if (!first) sb.append("  <font color='#555555'>·</font>  ");
			String col = m.volSurge >= 2 ? "#FF981F" : (m.volSurge >= 1.5 ? "#E8A838" : "#888888");
			sb.append("<font color='").append(col).append("'>")
				.append(String.format("%.1f", m.volSurge)).append("×</font>")
				.append("<font color='#888888'> vol</font>");
		}
		sb.append("</html>");
		return sb.toString();
	}

	/**
	 * Compact format for MACD histogram values — the raw number can be in
	 * the tens of thousands for high-priced items, which reads as noise.
	 * Returns "+25.0K", "+1.2M", "+147", etc.
	 */
	private static String formatMacd(double v)
	{
		double abs = Math.abs(v);
		if (abs >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
		if (abs >= 1_000)     return String.format("%.1fK", v / 1_000);
		if (abs >= 100)       return String.format("%.0f", v);
		return String.format("%.2f", v);
	}
}
