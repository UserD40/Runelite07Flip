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
import net.runelite.client.util.LinkBrowser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── ICON ──────────────────────────────────────────────────────────────
		JLabel iconLabel = buildIcon(flip.itemId, itemManager);

		// ── NAME (with 07Flip Score chip when available) ─────────────────────
		String nameHtml;
		if (flip.flip07Score != null)
		{
			int s = flip.flip07Score;
			String scoreColor = s >= 70 ? "#00C27A" : (s >= 40 ? "#E8A838" : "#E85050");
			nameHtml = "<html>" + escapeHtml(flip.name)
				+ " <font color='" + scoreColor + "'>" + s + "</font></html>";
		}
		else
		{
			nameHtml = flip.name;
		}
		JLabel nameLabel = new JLabel(nameHtml);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);
		if (flip.flip07Score != null)
		{
			nameLabel.setToolTipText("07Flip Score (0–100): composite of price stability, after-tax margin and hourly volume.");
		}

		// ── BUY (red) ─────────────────────────────────────────────────────────
		String buyHtml = "<html><b>Buy:</b>  " + formatGpCompact(flip.buyPrice);
		if (flip.recBuyPrice != null)
		{
			buyHtml += "  <font color='#888888'>· Rec " + formatGpCompact(flip.recBuyPrice) + "</font>";
		}
		buyHtml += "</html>";
		JLabel buyLabel = new JLabel(buyHtml);
		buyLabel.setFont(Fonts.SM);
		buyLabel.setForeground(new Color(0xFF7070));
		buyLabel.setToolTipText("Buy: instant-buy price (top ask). Rec: 07Flip recommended bid (p10 of last-hour fills).");

		// ── SELL (green) ──────────────────────────────────────────────────────
		String sellHtml = "<html><b>Sell:</b>  " + formatGpCompact(flip.sellPrice);
		if (flip.recSellPrice != null)
		{
			sellHtml += "  <font color='#888888'>· Rec " + formatGpCompact(flip.recSellPrice) + "</font>";
		}
		sellHtml += "</html>";
		JLabel sellLabel = new JLabel(sellHtml);
		sellLabel.setFont(Fonts.SM);
		sellLabel.setForeground(GREEN);
		sellLabel.setToolTipText("Sell: instant-sell price (top bid). Rec: 07Flip recommended ask (p90 of last-hour fills).");

		// ── PROFIT + ROI ───────────────────────────────────────────────────────
		String limitText = flip.buyLimit > 0 ? "  \u00B7  L" + flip.buyLimit : "";
		String affText   = (flip.affordableQty != null && flip.affordableQty > 0)
			? "  \u00B7  Buy " + flip.affordableQty
			: "";
		String recProfitText = "";
		if (flip.recProfit != null && flip.recProfit > 0)
		{
			double recRoi = (flip.recBuyPrice != null && flip.recBuyPrice > 0)
				? 100.0 * flip.recProfit / flip.recBuyPrice
				: 0.0;
			recProfitText = "  ·  <font color='#FF981F'>07F +" + formatGpCompact(flip.recProfit)
				+ "</font>  <font color='#888888'>(" + String.format("%.1f", recRoi) + "%)</font>";
		}
		String profitHtml = "<html><font color='#00C27A'>+" + formatGpCompact(flip.profit) + "</font>"
			+ "  (" + String.format("%.1f", flip.roiPct) + "% ROI)"
			+ recProfitText + limitText + affText + "</html>";
		JLabel profitLabel = new JLabel(profitHtml);
		profitLabel.setFont(Fonts.SM);
		profitLabel.setForeground(GREEN);
		if (flip.affordableQty != null || flip.recProfit != null)
		{
			profitLabel.setToolTipText("Market profit, then 07Flip margin (after-tax at rec_buy/rec_sell). 'L' = GE buy limit; 'Buy N' = affordable qty.");
		}

		JPanel textPanel = new JPanel(new GridLayout(4, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameLabel);
		textPanel.add(buyLabel);
		textPanel.add(sellLabel);
		textPanel.add(profitLabel);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

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
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					openUrl("https://07flip.com/item/" + flip.itemId);
				}
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && plugin != null)
				{
					JPopupMenu menu = new JPopupMenu();
					// Prefer 07Flip recommended prices (rec_buy / rec_sell) when
					// available \u2014 they target the patient-flipper p10/p90 entry
					// points. Fall back to the instant-fill market prices when
					// the server hasn't computed recommendations for this item.
					long buyTarget  = (flip.recBuyPrice  != null && flip.recBuyPrice  > 0) ? flip.recBuyPrice  : flip.sellPrice;
					long sellTarget = (flip.recSellPrice != null && flip.recSellPrice > 0) ? flip.recSellPrice : flip.buyPrice;
					JMenuItem buyItem = new JMenuItem("Buy on GE \u2014 " + formatGp(buyTarget));
					buyItem.addActionListener(ae -> plugin.queueGeBuy(flip.itemId, buyTarget, flip.name));
					menu.add(buyItem);
					boolean inInventory = plugin.inventoryItemIds.contains(flip.itemId);
					if (!plugin.getConfig().inventoryCheckOnSell() || inInventory)
					{
						JMenuItem sellItem = new JMenuItem("Sell on GE \u2014 " + formatGp(sellTarget));
						sellItem.addActionListener(ae -> plugin.queueGeSell(flip.itemId, sellTarget, flip.name));
						menu.add(sellItem);
					}
					JMenuItem detailsItem = new JMenuItem("Details\u2026");
					detailsItem.addActionListener(ae ->
						ItemDetailDialog.show(FlipItemPanel.this, flip, plugin, itemManager));
					menu.add(detailsItem);
					menu.addSeparator();
					addHideMenuItem(menu, plugin, flip.itemId, flip.name);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	// =========================================================================
	// Static helpers shared by all item panels
	// =========================================================================

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

	/**
	 * Compact gp formatter used in narrow row labels — billions, millions
	 * and thousands get B/M/K suffixes so very large numbers don't blow
	 * out the row width. Use {@link #formatGp(long)} where exact precision
	 * matters (Details dialog, GE auto-fill, trade history).
	 *
	 * Examples:
	 *   1,460,546,000 -> "1.46B"
	 *   22,500,000    -> "22.5M"
	 *   318,842       -> "318.8K"
	 *   6,800         -> "6.8K"
	 *   42            -> "42"
	 */
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
		// Strip a trailing ".0" or ".X0" so "22.50" becomes "22.5" and "5.00"
		// becomes "5". Keeps the compact form readable without losing useful
		// precision (e.g. "1.46" stays "1.46" not "1.5").
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

	private static String escapeHtml(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static void openUrl(String url)
	{
		LinkBrowser.browse(url);
	}

	/**
	 * Appends a "Hide" menu item to a row's right-click popup. Shared by
	 * every panel that lists 07flip.com items (Flips, Dumps, Spikes, Dips,
	 * Alerts) so users can hide unwanted items consistently across tabs.
	 */
	public static void addHideMenuItem(JPopupMenu menu, com.o7flip.O7FlipPlugin plugin, int itemId, String name)
	{
		if (plugin == null || itemId <= 0)
		{
			return;
		}
		JMenuItem hide = new JMenuItem("Hide — " + name);
		hide.addActionListener(ae -> plugin.addToBlocklist(itemId));
		menu.add(hide);
	}
}
