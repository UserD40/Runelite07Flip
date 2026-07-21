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
import com.o7flip.model.FlipItem;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class FlipItemPanel extends JPanel
{
	private static final Color ODD_BG   = new Color(0x272727);
	private static final Color HOVER_BG = new Color(0x3A3A3A);
	private static final Color GREEN    = new Color(0x00C27A);

	public FlipItemPanel(FlipItem flip, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		this(flip, itemManager, odd, plugin, false);
	}

	public FlipItemPanel(FlipItem flip, ItemManager itemManager, boolean odd, O7FlipPlugin plugin, boolean favouritesRow)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		final boolean isPremium = plugin != null && plugin.panel != null && plugin.panel.isPremium();
		final boolean showRecPrices = isPremium;

		final boolean band = flip.isBand();

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = buildIcon(flip.itemId, itemManager);

		JLabel nameLabel = new JLabel(flip.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		JLabel scoreLabel = null;
		if (!favouritesRow)
		{
			if (flip.flip07Score != null)
			{
				int s = flip.flip07Score;
				String scoreColor = s >= 70 ? "#00C27A" : (s >= 40 ? "#E8A838" : "#E85050");
				scoreLabel = new JLabel("<html><font color='" + scoreColor + "'>" + s + "</font></html>");
				scoreLabel.setFont(Fonts.BOLD);
				scoreLabel.setToolTipText("07Flip merch algorithm score (0–100)");
			}
			else
			{
				scoreLabel = new JLabel("—");
				scoreLabel.setFont(Fonts.BOLD);
				scoreLabel.setForeground(new Color(0xAAAAAA));
				scoreLabel.setToolTipText("07Flip merch algorithm score — unavailable for this item right now (insufficient recent trade data).");
			}
		}

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(bg);
		nameRow.add(nameLabel, BorderLayout.CENTER);
		if (scoreLabel != null)
		{
			nameRow.add(scoreLabel, BorderLayout.EAST);
		}

		final long buyShown = band ? flip.bandFloor : flip.buyPrice;
		String buyHtml = "<html><b>Buy:</b>  " + formatGpCompact(buyShown);
		if (!band && showRecPrices && flip.recBuyPrice != null)
		{
			buyHtml += "  <font color='#888888'>· Rec " + formatGpCompact(flip.recBuyPrice) + "</font>";
		}
		buyHtml += "</html>";
		JLabel buyLabel = new JLabel(buyHtml);
		buyLabel.setFont(Fonts.SM);
		buyLabel.setForeground(new Color(0xFF7070));

		StringBuilder buyTip = new StringBuilder("<html><b>").append(escapeHtml(flip.name)).append("</b><br>");
		if (band)
		{
			buyTip.append("Dependable buy <font color='#888888'>(14-day floor)</font>: <font color='#FF7070'>")
				.append(formatGp(flip.bandFloor)).append("</font><br>");
		}
		else
		{
			buyTip.append("Market buy: <font color='#FF7070'>").append(formatGp(flip.buyPrice)).append("</font><br>");
			if (flip.recBuyPrice != null)
			{
				buyTip.append("07Flip rec: <font color='#FF981F'>").append(formatGp(flip.recBuyPrice)).append("</font><br>");
			}
		}
		buyTip.append("<font color='#666666'>Right-click anywhere to queue a Buy on the GE · Click for insights · Shift+click or double-click to open on 07flip.com</font></html>");
		buyLabel.setToolTipText(buyTip.toString());
		buyLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		final long buyTarget = band
			? flip.bandFloor
			: ((showRecPrices && flip.recBuyPrice != null && flip.recBuyPrice > 0)
				? flip.recBuyPrice : flip.buyPrice);

		final long sellShown = band ? flip.bandCeiling : flip.sellPrice;
		String sellHtml = "<html><b>Sell:</b>  " + formatGpCompact(sellShown);
		if (!band && showRecPrices && flip.recSellPrice != null)
		{
			sellHtml += "  <font color='#888888'>· Rec " + formatGpCompact(flip.recSellPrice) + "</font>";
		}
		sellHtml += "</html>";
		JLabel sellLabel = new JLabel(sellHtml);
		sellLabel.setFont(Fonts.SM);
		sellLabel.setForeground(GREEN);

		StringBuilder sellTip = new StringBuilder("<html><b>").append(escapeHtml(flip.name)).append("</b><br>");
		if (band)
		{
			sellTip.append("Dependable sell <font color='#888888'>(14-day ceiling)</font>: <font color='#00C27A'>")
				.append(formatGp(flip.bandCeiling)).append("</font><br>");
		}
		else
		{
			sellTip.append("Market sell: <font color='#00C27A'>").append(formatGp(flip.sellPrice)).append("</font><br>");
			if (flip.recSellPrice != null)
			{
				sellTip.append("07Flip rec: <font color='#FF981F'>").append(formatGp(flip.recSellPrice)).append("</font><br>");
			}
		}
		sellTip.append("<font color='#666666'>Right-click anywhere to queue a Buy on the GE · Click for insights · Shift+click or double-click to open on 07flip.com</font></html>");
		sellLabel.setToolTipText(sellTip.toString());
		sellLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		final long sellTarget = (showRecPrices && flip.recSellPrice != null && flip.recSellPrice > 0)
			? flip.recSellPrice : flip.buyPrice;

		JLabel profitLabel;
		StringBuilder tip = new StringBuilder("<html><b>");
		tip.append(escapeHtml(flip.name)).append("</b><br>");
		if (band)
		{
			long bp = flip.bandProfit != null ? flip.bandProfit : 0L;
			long bm = flip.bandMargin != null ? flip.bandMargin : 0L;
			profitLabel = new JLabel("<html><font color='#00C27A'><b>+" + formatGpCompact(bp) + "</b></font>"
				+ "  <font color='#888888'>· " + formatGpCompact(bm) + "/ea ("
				+ String.format("%.1f", flip.bandMarginPct) + "%)</font></html>");
			profitLabel.setForeground(GREEN);

			tip.append("Cycle profit <font color='#888888'>(margin × 4h limit)</font>: ")
				.append("<font color='#00C27A'>+").append(formatGp(bp)).append("</font><br>");
			tip.append("Margin / item: <font color='#00C27A'>+").append(formatGp(bm)).append("</font>")
				.append("  (").append(String.format("%.1f", flip.bandMarginPct)).append("%)<br>");
			if (flip.buyLimit > 0)
			{
				tip.append("GE buy limit: ").append(formatGp(flip.buyLimit))
					.append(" <font color='#888888'>(per 4 hours)</font><br>");
			}
			if (flip.dailyVolume != null && flip.dailyVolume > 0)
			{
				tip.append("Daily volume: ").append(formatGp(flip.dailyVolume)).append("<br>");
			}
			if (flip.bandVolumeCoverage != null)
			{
				tip.append("Traded ").append(flip.bandVolumeCoverage)
					.append("% <font color='#888888'>of window hours</font><br>");
			}
		}
		else
		{
			String profitColor = flip.profit >= 0 ? "#00C27A" : "#E85050";
			String profitSign  = flip.profit >= 0 ? "+" : "";
			profitLabel = new JLabel("<html><font color='" + profitColor + "'>"
				+ profitSign + formatGpCompact(flip.profit) + "</font></html>");
			profitLabel.setForeground(flip.profit >= 0 ? GREEN : new Color(0xE85050));

			tip.append("Market margin: <font color='#00C27A'>+").append(formatGp(flip.profit)).append("</font>");
			tip.append("  (").append(String.format("%.2f", flip.roiPct)).append("% ROI)<br>");
			if (flip.recProfit != null && flip.recProfit > 0)
			{
				double recRoi = (flip.recBuyPrice != null && flip.recBuyPrice > 0)
					? 100.0 * flip.recProfit / flip.recBuyPrice
					: 0.0;
				tip.append("07Flip margin: <font color='#FF981F'>+").append(formatGp(flip.recProfit)).append("</font>");
				tip.append("  (").append(String.format("%.2f", recRoi)).append("%)<br>");
			}
			if (flip.buyLimit > 0)
			{
				tip.append("GE buy limit: ").append(flip.buyLimit)
					.append(" <font color='#888888'>(resets every 4 hours)</font><br>");
			}
			if (flip.affordableQty != null && flip.affordableQty > 0)
			{
				tip.append("Affordable: ").append(flip.affordableQty).append("<br>");
			}
			if (flip.flip07Score != null)
			{
				tip.append("07Flip Score: ").append(flip.flip07Score).append("/100<br>");
			}
		}
		profitLabel.setFont(Fonts.SM);
		tip.append("</html>");
		profitLabel.setToolTipText(tip.toString());

		JPanel textPanel = new JPanel(new GridLayout(4, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameRow);
		textPanel.add(buyLabel);
		textPanel.add(sellLabel);
		textPanel.add(profitLabel);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		ClickRouter.attach(this, plugin, flip.itemId, flip.name);

		ClickRouter.attachClickOnly(buyLabel,    plugin, flip.itemId, flip.name);
		ClickRouter.attachClickOnly(sellLabel,   plugin, flip.itemId, flip.name);
		ClickRouter.attachClickOnly(profitLabel, plugin, flip.itemId, flip.name);
		ClickRouter.attachClickOnly(nameLabel,   plugin, flip.itemId, flip.name);
		if (scoreLabel != null)
		{
			ClickRouter.attachClickOnly(scoreLabel, plugin, flip.itemId, flip.name);
		}

		MouseAdapter rightClickQueueBuy = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null)
				{
					plugin.queueGeBuy(flip.itemId, buyTarget, flip.name);
					e.consume();
				}
			}
		};
		buyLabel.addMouseListener(rightClickQueueBuy);
		sellLabel.addMouseListener(rightClickQueueBuy);
		profitLabel.addMouseListener(rightClickQueueBuy);
		nameLabel.addMouseListener(rightClickQueueBuy);
		if (scoreLabel != null)
		{
			scoreLabel.addMouseListener(rightClickQueueBuy);
		}

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(bg);
				textPanel.setBackground(bg);
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null)
				{
					plugin.queueGeBuy(flip.itemId, buyTarget, flip.name);
				}
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	public static JLabel buildIcon(int itemId, ItemManager itemManager)
	{
		JLabel lbl = new JLabel();
		lbl.setPreferredSize(new Dimension(32, 32));
		lbl.setHorizontalAlignment(SwingConstants.CENTER);
		if (itemManager != null && itemId > 0)
		{
			AsyncBufferedImage img = itemManager.getImage(itemId);
			img.addTo(lbl);
		}
		return lbl;
	}

	public static String formatGp(long amount)
	{
		return String.format("%,d", amount);
	}

	public static String formatGpCompact(long amount)
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
		int dot = s.indexOf('.');
		if (dot < 0)
		{
			return s;
		}
		int end = s.length();
		while (end > dot && s.charAt(end - 1) == '0')
		{
			end--;
		}
		if (end > dot && s.charAt(end - 1) == '.')
		{
			end--;
		}
		return s.substring(0, end);
	}

	public static String escapeHtml(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
