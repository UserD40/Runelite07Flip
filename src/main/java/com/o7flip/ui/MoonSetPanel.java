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
import com.o7flip.model.MoonItem;
import com.o7flip.model.MoonSet;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MoonSetPanel extends JPanel
{
	private static final Color BLOOD_BG    = new Color(0x231212);
	private static final Color BLOOD_ACC   = new Color(0x5C2A2A);
	private static final Color BLUE_BG     = new Color(0x0C1624);
	private static final Color BLUE_ACC    = new Color(0x1E3E60);
	private static final Color ECLIPSE_BG  = new Color(0x1C1808);
	private static final Color ECLIPSE_ACC = new Color(0x524418);
	private static final Color HOVER_BG    = new Color(0x3A3A3A);

	private static final Color GREEN  = new Color(0x00C27A);
	private static final Color ORANGE = new Color(0xFF981F);
	private static final Color WHITE  = Color.WHITE;

	private final O7FlipPlugin plugin;

	public MoonSetPanel(MoonSet set, ItemManager itemManager, boolean ignored, O7FlipPlugin plugin)
	{
		this.plugin = plugin;
		Color bg  = bgFor(set.setName);
		Color acc = accFor(set.setName);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(bg);
		setBorder(new CompoundBorder(
			new MatteBorder(0, 3, 0, 0, acc),
			new EmptyBorder(8, 10, 20, 10)));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(buildHeader(set, itemManager, bg, acc));
		add(Box.createVerticalStrut(6));

		boolean sellSet = "sell_set".equals(set.bestStrategy);
		JLabel bestLine = new JLabel(
			"<html><font color='#888888'>Best profit: </font>"
			+ "<font color='#00C27A'><b>+" + FlipItemPanel.formatGp(set.bestProfit) + " gp</b></font>"
			+ "<font color='#555555'>  (" + (sellSet ? "sell as set" : "sell individual") + ")</font></html>");
		bestLine.setFont(Fonts.SM);
		bestLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(bestLine);
		add(Box.createVerticalStrut(8));

		add(divider(acc));
		add(Box.createVerticalStrut(6));

		JLabel piecesHdr = new JLabel("PIECES");
		piecesHdr.setFont(Fonts.SM);
		piecesHdr.setForeground(new Color(0x777777));
		piecesHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(piecesHdr);
		add(Box.createVerticalStrut(4));

		for (MoonItem item : set.items)
		{
			add(buildItemRow(item, itemManager, bg));
			add(Box.createVerticalStrut(6));
		}

		add(divider(acc));
		add(Box.createVerticalStrut(6));

		long weaponCost = 0;
		for (MoonItem mi : set.items)
		{
			if (mi.itemIdBroken == 0)
			{
				weaponCost = mi.brokenBuyPrice;
				break;
			}
		}
		add(buildTotals(set, weaponCost));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private JPanel buildHeader(MoonSet set, ItemManager itemManager, Color bg, Color acc)
	{
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBackground(bg);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel icon = FlipItemPanel.buildIcon(set.iconItemId, itemManager);
		header.add(icon, BorderLayout.WEST);

		JPanel namePanel = new JPanel(new GridLayout(2, 1, 0, 1));
		namePanel.setBackground(bg);
		JLabel nameLabel = new JLabel(set.setName);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(WHITE);
		JLabel styleLabel = new JLabel(set.combatStyle);
		styleLabel.setFont(Fonts.SM);
		styleLabel.setForeground(new Color(0x888888));
		namePanel.add(nameLabel);
		namePanel.add(styleLabel);
		header.add(namePanel, BorderLayout.CENTER);

		boolean isSellSet = "sell_set".equals(set.bestStrategy);
		JLabel badge = new JLabel(isSellSet ? "SELL AS SET" : "SELL PIECES");
		badge.setFont(Fonts.SM);
		badge.setForeground(WHITE);
		badge.setOpaque(true);
		badge.setBackground(isSellSet ? ORANGE : GREEN);
		badge.setBorder(new EmptyBorder(2, 6, 2, 6));
		header.add(badge, BorderLayout.EAST);

		return header;
	}

	private JPanel buildItemRow(MoonItem item, ItemManager itemManager, Color bg)
	{
		boolean isWeapon = item.itemIdBroken == 0;

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(bg);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		int iconId = isWeapon ? item.itemIdRepaired : item.itemIdBroken;
		JLabel iconLabel = FlipItemPanel.buildIcon(iconId, itemManager);
		row.add(iconLabel, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(bg);
		text.setBorder(new EmptyBorder(0, 0, 6, 0));

		JLabel nameLabel = new JLabel(item.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(nameLabel);
		text.add(Box.createVerticalStrut(2));

		JLabel buyLbl = new JLabel(
			"<html><font color='#FF7070'>Buy: " + FlipItemPanel.formatGp(item.brokenBuyPrice) + "</font></html>");
		buyLbl.setFont(Fonts.SM);
		buyLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(buyLbl);
		text.add(Box.createVerticalStrut(2));

		JLabel repairLabel;
		if (item.npcRepairCost > 0 || item.pohRepairCost > 0)
		{
			repairLabel = new JLabel(
				"<html><font color='#888888'>NPC: </font>" + FlipItemPanel.formatGp(item.npcRepairCost)
				+ "  <font color='#888888'>POH: </font>" + FlipItemPanel.formatGp(item.pohRepairCost) + "</html>");
		}
		else
		{
			repairLabel = new JLabel("<html><font color='#555555'>No repair needed</font></html>");
		}
		repairLabel.setFont(Fonts.SM);
		repairLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(repairLabel);
		text.add(Box.createVerticalStrut(2));

		JLabel sellLbl = new JLabel(
			"<html><font color='#888888'>Sell: </font>" + FlipItemPanel.formatGp(item.repairedAfterTax)
			+ "<font color='#555555'>  (after 2% tax)</font></html>");
		sellLbl.setFont(Fonts.SM);
		sellLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(sellLbl);
		text.add(Box.createVerticalStrut(2));

		String profitColor = item.pohProfit >= 0 ? "#00C27A" : "#FF5555";
		String profitSign  = item.pohProfit >= 0 ? "+" : "";
		String profitLabel = isWeapon ? "Profit: " : "Profit (POH): ";
		JLabel profitLbl = new JLabel(
			"<html><font color='#888888'>" + profitLabel + "</font>"
			+ "<font color='" + profitColor + "'>" + profitSign + FlipItemPanel.formatGp(item.pohProfit) + "</font></html>");
		profitLbl.setFont(Fonts.SM);
		profitLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(profitLbl);

		row.add(text, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(HOVER_BG);
				text.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(bg);
				text.setBackground(bg);
			}
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					FlipItemPanel.openUrl("https://07flip.com/item/" + item.itemIdRepaired);
				}
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && plugin != null)
				{
					JPopupMenu menu = new JPopupMenu();
					JMenuItem buyItem = new JMenuItem("Buy on GE \u2014 " + FlipItemPanel.formatGp(item.repairedSellPrice));
					buyItem.addActionListener(ae -> plugin.queueGeBuy(item.itemIdRepaired, item.repairedSellPrice, item.name));
					menu.add(buyItem);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});

		return row;
	}

	private JPanel buildTotals(MoonSet set, long weaponCost)
	{
		Color bg = bgFor(set.setName);
		boolean hasSellSet = "sell_set".equals(set.bestStrategy) && set.setProfit > 0;

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(bg);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		panel.add(totalsRow(bg,
			"<html><font color='#888888'>Armour buy: </font><font color='#FF7070'>" + FlipItemPanel.formatGp(set.totalBrokenCost) + "</font></html>",
			"<html><font color='#888888'>NPC repair: </font>" + FlipItemPanel.formatGp(set.totalNpcRepairCost) + "</html>"));
		panel.add(Box.createVerticalStrut(4));

		panel.add(totalsRow(bg,
			"<html><font color='#888888'>NPC profit: </font><font color='#00C27A'>+" + FlipItemPanel.formatGp(set.npcProfit) + "</font></html>",
			"<html><font color='#888888'>POH repair: </font>" + FlipItemPanel.formatGp(set.totalPohRepairCost) + "</html>"));
		panel.add(Box.createVerticalStrut(4));

		String weaponHtml = weaponCost > 0
			? "<html><font color='#888888'>Weapon buy: </font><font color='#FF7070'>" + FlipItemPanel.formatGp(weaponCost) + "</font></html>"
			: "";
		panel.add(totalsRow(bg,
			"<html><font color='#888888'>POH profit: </font><font color='#00C27A'>+" + FlipItemPanel.formatGp(set.pohProfit) + "</font></html>",
			weaponHtml));
		panel.add(Box.createVerticalStrut(10));

		if (hasSellSet)
		{
			panel.add(totalsRow(bg,
				"<html><font color='#888888'>Set profit: </font><font color='#FF981F'><b>+" + FlipItemPanel.formatGp(set.setProfit) + "</b></font></html>",
				""));
		}

		return panel;
	}

	private static JPanel totalsRow(Color bg, String leftHtml, String rightHtml)
	{
		JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
		row.setBackground(bg);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(cell(leftHtml));
		row.add(cell(rightHtml.isEmpty() ? " " : rightHtml));
		return row;
	}

	private static JLabel cell(String html)
	{
		JLabel l = new JLabel(html);
		l.setFont(Fonts.SM);
		return l;
	}

	private static Color bgFor(String name)
	{
		if (name == null)
		{
			return ColorScheme.DARK_GRAY_COLOR;
		}
		if (name.contains("Blood"))
		{
			return BLOOD_BG;
		}
		if (name.contains("Blue"))
		{
			return BLUE_BG;
		}
		if (name.contains("Eclipse"))
		{
			return ECLIPSE_BG;
		}
		return ColorScheme.DARK_GRAY_COLOR;
	}

	private static Color accFor(String name)
	{
		if (name == null)
		{
			return new Color(0x4A4A4A);
		}
		if (name.contains("Blood"))
		{
			return BLOOD_ACC;
		}
		if (name.contains("Blue"))
		{
			return BLUE_ACC;
		}
		if (name.contains("Eclipse"))
		{
			return ECLIPSE_ACC;
		}
		return new Color(0x4A4A4A);
	}

	private static Component divider(Color color)
	{
		JPanel line = new JPanel();
		line.setBackground(color);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		line.setPreferredSize(new Dimension(0, 1));
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		return line;
	}
}
