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
import com.o7flip.model.DumpItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DumpItemPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color HIGH     = new Color(0xFF4444);
	private static final Color MID      = new Color(0xFFAA00);
	private static final Color LOW      = new Color(0x888888);
	private static final Color CLOCK    = new Color(0xFF981F);
	private static final Color MUTED    = new Color(0x666666);
	private static final Color UNVERIFIED_FG = new Color(0xAAAAAA);
	private static final Color CONFIRMED_TIER = new Color(0x00C27A);
	private static final Color LIKELY_TIER    = new Color(0xE8A838);

	public DumpItemPanel(DumpItem item, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;
		boolean clockAligned = Boolean.TRUE.equals(item.isClockAligned);
		boolean stale = Boolean.TRUE.equals(item.patternStale);
		boolean likely = !"confirmed".equalsIgnoreCase(item.tier);
		Color bodyFg = stale ? UNVERIFIED_FG
			: (likely ? new Color(0xDDDDDD) : Color.WHITE);

		final Color tierStripe = stale ? MUTED
			: (likely ? LIKELY_TIER : CONFIRMED_TIER);
		setLayout(new BorderLayout(6, 0));
		setBackground(bg);
		setBorder(BorderFactory.createCompoundBorder(
			new javax.swing.border.MatteBorder(0, 3, 0, 0, tierStripe),
			new EmptyBorder(10, 8, 10, 8)));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(item.itemId, itemManager);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(bg);

		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(bodyFg);
		nameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);

		JPanel nameRow = new JPanel();
		nameRow.setLayout(new BoxLayout(nameRow, BoxLayout.X_AXIS));
		nameRow.setBackground(bg);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameRow.add(nameLabel);
		nameRow.add(Box.createHorizontalGlue());

		if (stale)
		{
			nameRow.add(buildBadge("Stale", MUTED,
				"Pattern hasn't fired recently. Kept for historical context."));
		}
		textPanel.add(nameRow);
		textPanel.add(Box.createVerticalStrut(3));

		JLabel buyLbl = new JLabel("Buy: " + FlipItemPanel.formatGpCompact(item.buyPrice));
		buyLbl.setFont(Fonts.SM);
		buyLbl.setForeground(stale ? UNVERIFIED_FG : new Color(0xFF7070));
		buyLbl.setToolTipText("Recommended buy price (post-tax): " + FlipItemPanel.formatGp(item.buyPrice) + " gp");

		JPanel buyRow = new JPanel();
		buyRow.setLayout(new BoxLayout(buyRow, BoxLayout.X_AXIS));
		buyRow.setBackground(bg);
		buyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		buyRow.add(buyLbl);
		buyRow.add(Box.createHorizontalGlue());
		textPanel.add(buyRow);
		textPanel.add(Box.createVerticalStrut(3));

		String lastStr = item.lastDumpHoursAgo != null
			? formatAgo(item.lastDumpHoursAgo) + " ago"
			: "—";
		JLabel lastLbl = new JLabel(lastStr);
		lastLbl.setFont(Fonts.SM);
		lastLbl.setForeground(MUTED);
		lastLbl.setToolTipText("Time since the last detected dump fired.");

		String cadenceText = buildCadenceText(item);
		JLabel cadenceLbl = new JLabel(cadenceText.isEmpty() ? "" : "  ·  " + cadenceText);
		cadenceLbl.setFont(Fonts.SM);
		cadenceLbl.setForeground(clockAligned ? CLOCK : MUTED);
		if (!cadenceText.isEmpty())
		{
			cadenceLbl.setToolTipText("Dump cadence (median time between bursts + UTC peak hour). "
				+ (clockAligned ? "Clock-aligned with game daily resets — highest-confidence pattern." : ""));
		}

		JPanel statusRow = new JPanel();
		statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
		statusRow.setBackground(bg);
		statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		statusRow.add(lastLbl);
		statusRow.add(cadenceLbl);
		statusRow.add(Box.createHorizontalGlue());
		textPanel.add(statusRow);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		ClickRouter.attach(this, plugin, item.itemId, item.name);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
				nameRow.setBackground(HOVER_BG);
				buyRow.setBackground(HOVER_BG);
				statusRow.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				textPanel.setBackground(bg);
				nameRow.setBackground(bg);
				buyRow.setBackground(bg);
				statusRow.setBackground(bg);
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null && item.sellPrice > 0)
				{
					plugin.queueGeBuy(item.itemId, item.sellPrice, item.name);
				}
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static JComponent buildBadge(String text, Color colour, String tooltip)
	{
		JLabel chip = new JLabel(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(colour);
				g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
				g2.setColor(Color.BLACK);
				g2.setFont(getFont());
				java.awt.FontMetrics fm = g2.getFontMetrics();
				int x = (getWidth() - fm.stringWidth(text)) / 2;
				int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
				g2.drawString(text, x, y);
				g2.dispose();
			}
			@Override public boolean isOpaque() { return false; }
		};
		chip.setFont(Fonts.SM);
		chip.setBorder(new EmptyBorder(2, 8, 2, 8));
		chip.setToolTipText(tooltip);
		return chip;
	}

	private static String buildCadenceText(DumpItem item)
	{
		if (item.periodHours == null || item.periodHours <= 0)
		{
			return "";
		}
		StringBuilder sb = new StringBuilder("every ").append(item.periodHours).append("h");
		if (item.dumpPeakHourUtc != null && item.dumpPeakHourUtc >= 0 && item.dumpPeakHourUtc < 24)
		{
			sb.append(" @").append(String.format("%02d", item.dumpPeakHourUtc));
		}
		return sb.toString();
	}

	private static String formatAgo(double hours)
	{
		if (hours < 2.0)
		{
			return (int) Math.round(hours * 60) + " min";
		}
		if (hours < 24.0)
		{
			return String.format("%.1fh", hours);
		}
		return String.format("%.1fd", hours / 24.0);
	}

	private static final class HourlyVolumesSparkline extends JPanel
	{
		private static final int WIDTH  = 64;
		private static final int HEIGHT = 24;
		private static final Color BAR_BG = new Color(0x3A3A3A);
		private static final Color BAR_FG = new Color(0x6FB8FF);

		private final int[] values;
		private final Color background;

		HourlyVolumesSparkline(int[] values, Color background)
		{
			this.values = values;
			this.background = background;
			setOpaque(false);
			Dimension d = new Dimension(WIDTH, HEIGHT);
			setPreferredSize(d);
			setMaximumSize(d);
			setMinimumSize(d);
			setAlignmentY(Component.CENTER_ALIGNMENT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setColor(background);
			g2.fillRect(0, 0, getWidth(), getHeight());

			int n = values.length;
			if (n <= 0)
			{
				g2.dispose();
				return;
			}
			int max = 1;
			for (int v : values) if (v > max) max = v;

			float barW = (float) getWidth() / (float) n;
			int gap = barW >= 3f ? 1 : 0;
			for (int i = 0; i < n; i++)
			{
				int v = values[i];
				int h = (int) Math.round((double) v / (double) max * (getHeight() - 2));
				if (h < 1 && v > 0) h = 1;
				int x = (int) Math.floor(i * barW);
				int w = Math.max(1, (int) Math.floor(barW) - gap);
				g2.setColor(v > 0 ? BAR_FG : BAR_BG);
				g2.fillRect(x, getHeight() - h - 1, w, h);
			}
			g2.dispose();
		}
	}
}
