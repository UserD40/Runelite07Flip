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
import com.o7flip.model.TeleTablet;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.JLabel;
import javax.swing.JPanel;
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

/**
 * Row component for /api/runelite/tele-tablets. Cast at a lectern, sell on
 * the GE. The card surfaces material cost, post-tax sell price, and the
 * net profit per tablet — ingredients ship in the model and are exposed
 * via the tooltip to keep the row tight.
 *
 * The spellbook is rendered as a coloured badge in the name-row's chip
 * slot, matching FlipItemPanel's right-edge alignment.
 */
public class TeleTabletPanel extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color HOVER_BG  = new Color(0x3A3A3A);
	private static final Color GREEN     = new Color(0x00C27A);
	private static final Color RED       = new Color(0xFF7070);

	public TeleTabletPanel(TeleTablet tab, ItemManager itemManager, boolean odd, O7FlipPlugin plugin)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(tab.tabletId, itemManager);

		JLabel nameLabel = new JLabel(tab.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		String spellbookColor = spellbookColor(tab.spellbook);
		JLabel bookBadge = new JLabel("<html><font color='" + spellbookColor + "'>" + tab.spellbook + "</font></html>");
		bookBadge.setFont(Fonts.BOLD);
		bookBadge.setToolTipText(tab.spellbook + " spellbook");

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(bg);
		nameRow.add(nameLabel, BorderLayout.CENTER);
		nameRow.add(bookBadge, BorderLayout.EAST);

		// Combined Cost + Sell line — keeps the card to three rows total
		// (name, cost+sell, ingredients). Daily volume rides on the end of
		// this line when the server ships it; otherwise it's omitted.
		String volText = "";
		if (tab.dailyVolume != null && tab.dailyVolume > 0)
		{
			volText = "  <font color='#888888'>· " + FlipItemPanel.formatGpCompact(tab.dailyVolume) + "/day</font>";
		}
		JLabel costSellLabel = new JLabel("<html>"
			+ "<font color='#FF7070'><b>Cost:</b> " + FlipItemPanel.formatGpCompact(tab.materialCost) + "</font>"
			+ "  <font color='#666666'>·</font>  "
			+ "<font color='#00C27A'><b>Sell:</b> " + FlipItemPanel.formatGpCompact(tab.sellAfterTax) + "</font>"
			+ volText
			+ "</html>");
		costSellLabel.setFont(Fonts.SM);
		costSellLabel.setToolTipText("<html>"
			+ "Material cost: <font color='#FF7070'>" + FlipItemPanel.formatGp(tab.materialCost) + "</font> gp<br>"
			+ "Post-tax GE sell: <font color='#00C27A'>" + FlipItemPanel.formatGp(tab.sellAfterTax) + "</font> gp<br>"
			+ "Pre-tax: " + FlipItemPanel.formatGp(tab.sellPrice) + " gp<br>"
			+ (tab.dailyVolume != null ? "Daily GE volume: " + FlipItemPanel.formatGp(tab.dailyVolume) + "<br>" : "")
			+ "</html>");

		// Ingredient pills row — each ingredient = quantity + a small item
		// icon. Replaces the old profit/ROI line. Visual makes it obvious at
		// a glance what runes/clay you need to make the tablet.
		JPanel ingredientsRow = buildIngredientsRow(tab, itemManager, bg, plugin);

		JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 2));
		textPanel.setBackground(bg);
		textPanel.add(nameRow);
		textPanel.add(costSellLabel);
		textPanel.add(ingredientsRow);

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		// Tablet itself: click → Item insights, dbl/shift → website. No
		// right-click action on the tablet body — the user crafts tablets
		// at a lectern, they don't buy them on the GE. Right-click instead
		// lives on the ingredient pills below, queuing buys for the runes /
		// soft clay you need to craft the tablet.
		ClickRouter.attach(this, plugin, tab.tabletId, tab.name);
		ClickRouter.attachClickOnly(nameLabel,   plugin, tab.tabletId, tab.name);
		ClickRouter.attachClickOnly(costSellLabel, plugin, tab.tabletId, tab.name);
		ClickRouter.attachClickOnly(bookBadge,   plugin, tab.tabletId, tab.name);

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
		});

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	/**
	 * Builds the inline ingredient row — for each {@link TeleTablet.Ingredient}
	 * we render a "{qty}×" label followed by a 16×16 item icon resolved
	 * through {@link ItemManager}. Tooltip on each pair surfaces the
	 * ingredient name + total gp cost.
	 *
	 * Right-clicking an ingredient pill queues a Buy of the rune / soft
	 * clay at its market unit price — this is where the actual GE purchase
	 * lives for the tablet-crafting flow.
	 *
	 * Uses FlowLayout so a tablet with many ingredients (rare) wraps cleanly
	 * to the next line rather than truncating.
	 */
	private static JPanel buildIngredientsRow(TeleTablet tab, ItemManager itemManager, Color bg,
		O7FlipPlugin plugin)
	{
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
		row.setBackground(bg);
		row.setBorder(new EmptyBorder(0, 0, 0, 0));
		if (tab.ingredients == null || tab.ingredients.isEmpty())
		{
			return row;
		}
		for (TeleTablet.Ingredient ing : tab.ingredients)
		{
			if (ing == null) continue;

			JLabel qty = new JLabel(ing.qty + "×");
			qty.setFont(Fonts.SM);
			qty.setForeground(new Color(0xAAAAAA));

			// Item icons are 32×32 native — at that size they overflow the
			// ingredient row and shove neighbours offscreen. We scale to
			// 16×16 once the async image lands so the row fits inside the
			// panel width.
			final int ICON_SIZE = 16;
			JLabel icon = new JLabel();
			java.awt.Dimension iconBox = new java.awt.Dimension(ICON_SIZE, ICON_SIZE);
			icon.setPreferredSize(iconBox);
			icon.setMinimumSize(iconBox);
			icon.setMaximumSize(iconBox);
			if (itemManager != null && ing.itemId > 0)
			{
				net.runelite.client.util.AsyncBufferedImage img = itemManager.getImage(ing.itemId);
				img.onLoaded(() ->
				{
					java.awt.Image scaled = img.getScaledInstance(ICON_SIZE, ICON_SIZE,
						java.awt.Image.SCALE_SMOOTH);
					icon.setIcon(new javax.swing.ImageIcon(scaled));
					icon.revalidate();
					icon.repaint();
				});
			}
			final String ingredientName = ing.name == null ? "Item " + ing.itemId : ing.name;
			String tooltip = "<html>" + escapeHtml(ingredientName)
				+ "<br><font color='#888888'>" + ing.qty + " × "
				+ FlipItemPanel.formatGp(ing.unitPrice) + " = "
				+ FlipItemPanel.formatGp(ing.totalPrice) + " gp</font>"
				+ "<br><font color='#666666'>Right-click to queue a Buy on the GE</font></html>";
			qty.setToolTipText(tooltip);
			icon.setToolTipText(tooltip);

			JPanel pair = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0));
			pair.setBackground(bg);
			pair.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			pair.add(qty);
			pair.add(icon);

			final int ingItemId = ing.itemId;
			final long ingUnitPrice = ing.unitPrice;
			MouseAdapter ingRightClickBuy = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown()
						&& plugin != null && ingItemId > 0 && ingUnitPrice > 0)
					{
						plugin.queueGeBuy(ingItemId, ingUnitPrice, ingredientName);
					}
				}
			};
			pair.addMouseListener(ingRightClickBuy);
			qty.addMouseListener(ingRightClickBuy);
			icon.addMouseListener(ingRightClickBuy);

			row.add(pair);
		}
		return row;
	}

	private static String spellbookColor(String book)
	{
		if (book == null) return "#AAAAAA";
		switch (book.toLowerCase())
		{
			case "standard": return "#7DA8FF";
			case "lunar":    return "#9E7DFF";
			case "ancient":  return "#FF7DCB";
			case "arceuus":  return "#FF981F";
			default:         return "#AAAAAA";
		}
	}

	private static String buildIngredientsTip(TeleTablet tab)
	{
		StringBuilder sb = new StringBuilder("<html><b>Materials</b><br>");
		if (tab.ingredients == null || tab.ingredients.isEmpty())
		{
			sb.append("(no ingredient data)");
		}
		else
		{
			for (TeleTablet.Ingredient ing : tab.ingredients)
			{
				sb.append(ing.qty).append("× ").append(escapeHtml(ing.name))
					.append("  <font color='#888888'>").append(FlipItemPanel.formatGp(ing.totalPrice))
					.append(" gp</font><br>");
			}
			sb.append("<b>Total:</b> ").append(FlipItemPanel.formatGp(tab.materialCost)).append(" gp");
		}
		sb.append("</html>");
		return sb.toString();
	}

	private static String escapeHtml(String s)
	{
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
