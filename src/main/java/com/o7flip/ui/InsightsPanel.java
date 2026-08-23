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

import com.o7flip.O7FlipConfig;
import com.o7flip.O7FlipPlugin;
import com.o7flip.model.Models.FlipItem;
import com.o7flip.model.Models.ItemInsights;
import com.o7flip.util.Fonts;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class InsightsPanel extends JPanel
{
	private static final Color SECTION_BG    = new Color(0x1F1F1F);
	private static final Color HEADER_COL    = new Color(0xC4A052);
	private static final Color PROFIT_COL    = new Color(0x00C27A);
	private static final Color LOSS_COL      = new Color(0xE85050);
	private static final Color LOCKED_COL    = new Color(0x808080);
	private static final Color UPSELL_BG     = new Color(0x2A2418);
	private static final Color UPSELL_BORD   = new Color(0xC4A052);

	private final ItemManager itemManager;
	private O7FlipPlugin plugin;
	private O7FlipConfig config;
	private List<FlipItem> recommended = Collections.emptyList();
	private boolean inEmptyState = true;
	private boolean inFailedState = false;

	private ItemInsights lastShown;

	private int desiredChartPeriodIdx = -1;

	private int     currentItemId;
	private String  currentItemName;
	private JLabel  starLabel;
	private JLabel  lockLabel;
	private javax.swing.Timer headerCooldownTimer;

	private static final Color CHIP_HOVER = new Color(0x3C3C3C);
	private static final Color FROZEN_COL = new Color(0xFFC845);

	public InsightsPanel(ItemManager itemManager, O7FlipPlugin plugin, O7FlipConfig config)
	{
		this.itemManager = itemManager;
		this.plugin      = plugin;
		this.config      = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(SECTION_BG);
		setBorder(new EmptyBorder(8, 0, 8, 0));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		showEmpty();
	}

	public void setRecommended(List<FlipItem> items, O7FlipPlugin p)
	{
		this.recommended = items != null ? items : Collections.emptyList();
		this.plugin = p;
		if (inEmptyState)
		{
			showEmpty();
		}
		else if (inFailedState)
		{
			showLoadFailed();
		}
	}

	public void showEmpty()
	{
		removeAll();
		inEmptyState = true;
		inFailedState = false;

		add(Box.createVerticalStrut(20));
		add(stretchedCenterLabel("Item Insights", HEADER_COL, Fonts.TITLE));
		add(Box.createVerticalStrut(8));
		add(stretchedWrappedCenter(
			"Left-click any item across the plugin (Flips, Alerts, My Trades, anywhere) "
				+ "to see live prices, charts, and 07Flip recommendations here.",
			ColorScheme.LIGHT_GRAY_COLOR, Fonts.SM));
		add(Box.createVerticalStrut(4));
		add(stretchedCenterLabel("Double-click to open the item page on 07flip.com",
			new Color(0x666666), Fonts.SM));
		add(Box.createVerticalStrut(20));

		if (!recommended.isEmpty() && plugin != null)
		{
			add(buildRecommendedSection());
		}

		revalidate();
		repaint();
	}

	private static JLabel stretchedCenterLabel(String text, Color colour, java.awt.Font font)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(colour);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(4, 12, 4, 12));
		int h = l.getPreferredSize().height;
		l.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		return l;
	}

	private static JLabel stretchedWrappedCenter(String text, Color colour, java.awt.Font font)
	{
		String html = "<html><div style='text-align:center'>"
			+ text.replace("<", "&lt;").replace(">", "&gt;")
			+ "</div></html>";
		JLabel l = new JLabel(html);
		l.setFont(font);
		l.setForeground(colour);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(0, 16, 0, 16));
		l.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
		return l;
	}

	private JPanel buildRecommendedSection()
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARK_GRAY_COLOR);
		section.setBorder(new EmptyBorder(0, 10, 0, 10));
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

		JLabel header = new JLabel("Recommended");
		header.setFont(Fonts.SM_BOLD);
		header.setForeground(HEADER_COL);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(new EmptyBorder(0, 0, 6, 0));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
		section.add(header);

		JLabel sub = new JLabel("Top-scoring flips right now");
		sub.setFont(Fonts.SM);
		sub.setForeground(new Color(0x888888));
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);
		sub.setBorder(new EmptyBorder(0, 0, 8, 0));
		sub.setMaximumSize(new Dimension(Integer.MAX_VALUE, sub.getPreferredSize().height));
		section.add(sub);

		int rendered = 0;
		for (FlipItem item : recommended)
		{
			if (item == null || item.itemId <= 0) continue;
			section.add(new RecommendedCard(item, itemManager, plugin));
			section.add(Box.createVerticalStrut(6));
			if (++rendered >= 3) break;
		}
		return section;
	}

	private static class RecommendedCard extends JPanel
	{
		private static final Color CARD_BG    = new Color(0x1F1F1F);
		private static final Color CARD_HOVER = new Color(0x2A2A2A);

		RecommendedCard(FlipItem item, ItemManager itemManager, O7FlipPlugin plugin)
		{
			setLayout(new BorderLayout(8, 0));
			setBackground(CARD_BG);
			setBorder(new EmptyBorder(8, 10, 8, 10));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			JLabel icon = FlipItemPanel.buildIcon(item.itemId, itemManager);

			JLabel name = new JLabel(item.name);
			name.setFont(Fonts.BOLD);
			name.setForeground(Color.WHITE);

			JLabel sub;
			if (item.flip07Score != null)
			{
				sub = new JLabel("Score " + item.flip07Score + " · " + FlipItemPanel.formatGpCompact(item.profit) + " gp profit");
			}
			else
			{
				sub = new JLabel(FlipItemPanel.formatGpCompact(item.profit) + " gp profit");
			}
			sub.setFont(Fonts.SM);
			sub.setForeground(new Color(0xC4A052));

			JPanel text = new JPanel();
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
			text.setBackground(CARD_BG);
			text.add(name);
			text.add(Box.createVerticalStrut(2));
			text.add(sub);

			add(icon, BorderLayout.WEST);
			add(text, BorderLayout.CENTER);

			ClickRouter.attachInsightsOnly(this, plugin, item.itemId, item.name);

			final Color base = CARD_BG;
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					setBackground(CARD_HOVER);
					text.setBackground(CARD_HOVER);
				}
				@Override
				public void mouseExited(MouseEvent e)
				{
					setBackground(base);
					text.setBackground(base);
				}
			});
		}
	}

	public void showLoading(int itemId, String fallbackName)
	{
		removeAll();
		inEmptyState = false;
		inFailedState = false;
		lastShown = null;
		add(buildHeader(itemId, fallbackName != null ? fallbackName : "Item " + itemId, false, 0, null, null, null, 0L));
		add(Box.createVerticalStrut(8));
		add(centeredLabel("Loading…", ColorScheme.LIGHT_GRAY_COLOR, Fonts.SM));
		revalidate();
		repaint();
	}

	public void show(ItemInsights ins)
	{
		if (ins == null)
		{
			showLoadFailed();
			return;
		}
		boolean sameItem = lastShown != null && lastShown.itemId == ins.itemId;
		javax.swing.JViewport vp = (javax.swing.JViewport)
			SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, this);
		final java.awt.Point savedScroll = (sameItem && vp != null) ? vp.getViewPosition() : null;
		if (!sameItem)
		{
			desiredChartPeriodIdx = -1;
		}

		removeAll();
		inEmptyState = false;
		inFailedState = false;
		lastShown = ins;

		long headerBuyTarget = 0L;
		if (ins.current != null)
		{
			headerBuyTarget = (ins.current.recBuy != null && ins.current.recBuy > 0)
				? ins.current.recBuy : ins.current.buyPrice;
		}
		add(buildHeader(ins.itemId, ins.name, ins.members, ins.buyLimit, ins.highAlch, ins.lowAlch,
			ins.volume != null ? Integer.valueOf(ins.volume.daily) : null, headerBuyTarget));
		add(Box.createVerticalStrut(4));

		add(buildActionRow(ins, headerBuyTarget));
		add(sectionDivider());

		if (sectionVisible(O7FlipConfig::itemTabLivePrices))
		{
			add(buildLivePrices(ins));
			add(sectionDivider());
		}

		if (sectionVisible(O7FlipConfig::itemTabChart))
		{
			add(buildChartSection(ins));
			add(sectionDivider());
		}

		if (sectionVisible(O7FlipConfig::itemTabPriceRange))
		{
			add(buildRanges(ins));
			add(sectionDivider());
		}

		if (sectionVisible(O7FlipConfig::itemTabVolume))
		{
			add(buildVolume(ins));
			add(sectionDivider());
		}

		if (!ins.premiumLocked)
		{
			if (sectionVisible(O7FlipConfig::itemTabRecommended))
			{
				add(build07FlipPrices(ins));
				add(sectionDivider());
			}
			JPanel plan = sectionVisible(O7FlipConfig::itemTabPlan) ? buildPlan(ins.itemId) : null;
			if (plan != null)
			{
				add(plan);
				add(sectionDivider());
			}
			if (sectionVisible(O7FlipConfig::itemTabScore))
			{
				add(buildScore(ins));
				add(sectionDivider());
			}
			if (ins.indicators != null && sectionVisible(O7FlipConfig::itemTabIndicators))
			{
				add(buildIndicators(ins.indicators));
				add(sectionDivider());
			}
			if (ins.liquidity != null && sectionVisible(O7FlipConfig::itemTabLiquidity))
			{
				add(buildLiquidity(ins.liquidity));
				add(sectionDivider());
			}
			if (ins.projection != null && sectionVisible(O7FlipConfig::itemTabProjection))
			{
				add(buildProjection(ins.projection));
				add(sectionDivider());
			}
		}

		if (ins.premiumLocked)
		{
			add(buildUpsell(ins.upgradeUrl != null && !ins.upgradeUrl.isEmpty() ? ins.upgradeUrl : "https://07flip.com/premium"));
		}

		if (plugin != null)
		{
			ClickRouter.attachToTree(this, plugin, ins.itemId, ins.name);
		}

		revalidate();
		repaint();

		if (savedScroll != null && vp != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				int maxY = Math.max(0, vp.getView().getHeight() - vp.getExtentSize().height);
				savedScroll.y = Math.min(savedScroll.y, maxY);
				vp.setViewPosition(savedScroll);
			});
		}
	}

	private void showLoadFailed()
	{
		removeAll();
		inEmptyState = false;
		inFailedState = true;

		add(Box.createVerticalStrut(20));
		add(stretchedCenterLabel("Insights unavailable", HEADER_COL, Fonts.TITLE));
		add(Box.createVerticalStrut(8));
		add(stretchedWrappedCenter(
			"Couldn't fetch this item's data just now — usually a brief connection blip. "
				+ "Retry below, or click any other item across the plugin.",
			ColorScheme.LIGHT_GRAY_COLOR, Fonts.SM));

		if (plugin != null && currentItemId > 0)
		{
			final int id = currentItemId;
			final String nm = currentItemName;
			boolean hasName = nm != null && !nm.isEmpty() && !"null".equalsIgnoreCase(nm);
			JButton retry = new JButton("Retry" + (hasName ? " — " + nm : ""));
			retry.setFont(Fonts.SM_BOLD);
			retry.setForeground(Color.WHITE);
			retry.setBackground(new Color(0x2A2A2A));
			retry.setFocusPainted(false);
			retry.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			retry.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x555555), 1),
				new EmptyBorder(6, 14, 6, 14)));
			retry.addActionListener(e -> plugin.openInsights(id, nm));

			JPanel wrap = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
			wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
			wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, retry.getPreferredSize().height + 8));
			wrap.add(retry);

			add(Box.createVerticalStrut(12));
			add(wrap);
		}

		add(Box.createVerticalStrut(20));
		if (!recommended.isEmpty() && plugin != null)
		{
			add(buildRecommendedSection());
		}

		revalidate();
		repaint();
	}

	private boolean sectionVisible(Predicate<O7FlipConfig> toggle)
	{
		return config == null || toggle.test(config);
	}

	public void refreshSectionVisibility()
	{
		if (!inEmptyState && !inFailedState && lastShown != null)
		{
			show(lastShown);
		}
	}

	private JPanel buildActionRow(ItemInsights ins, long buyTarget)
	{
		JPanel row = new JPanel(new java.awt.GridLayout(1, 0, 2, 0));
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 6, 2, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

		final int itemId  = ins.itemId;
		final String name = ins.name;
		final boolean hasKey = plugin != null && plugin.hasApiKeyPublic();

		boolean canAutofill = plugin != null && buyTarget > 0;
		row.add(actionChip("💰", VectorIcon.Kind.AUTOFILL, canAutofill
				? "Autofill — queue a GE buy at the recommended price"
				: "Autofill unavailable — no buy price for this item",
			canAutofill, () -> plugin.queueGeBuy(itemId, buyTarget, name)));

		final boolean lockable = hasKey && ins.current != null
			&& ins.current.recSell != null && ins.current.recSell > 0;
		final Long recBuy  = ins.current != null ? ins.current.recBuy  : null;
		final Long recSell = ins.current != null ? ins.current.recSell : null;
		lockLabel = newStateChip();
		paintLock(lockable);
		if (lockable)
		{
			lockLabel.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (plugin == null)
					{
						return;
					}
					if (plugin.isPriceLocked(itemId))
					{
						plugin.unlockPrice(itemId);
					}
					else
					{
						plugin.lockPrice(itemId, recBuy, recSell);
					}
					if (lastShown != null)
					{
						show(lastShown);
					}
					else
					{
						paintLock(true);
					}
					e.consume();
				}
			});
		}
		row.add(lockLabel);

		row.add(actionChip("🌐", VectorIcon.Kind.GLOBE, "Open this item on 07flip.com", true,
			() -> net.runelite.client.util.LinkBrowser.browse("https://07flip.com/item/" + itemId)));

		return row;
	}

	private JLabel actionChip(String winGlyph, VectorIcon.Kind kind, String tooltip, boolean enabled, Runnable onClick)
	{
		JLabel l = newStateChip();
		VectorIcon.apply(l, winGlyph, kind, 14, enabled ? Color.WHITE : new Color(0x555555));
		l.setToolTipText(tooltip);
		if (!enabled)
		{
			l.setForeground(new Color(0x555555));
			l.setCursor(Cursor.getDefaultCursor());
			return l;
		}
		l.setForeground(Color.WHITE);
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (onClick != null)
				{
					onClick.run();
				}
				e.consume();
			}
		});
		return l;
	}

	private JLabel newStateChip()
	{
		JLabel l = new JLabel("", SwingConstants.CENTER);
		l.setFont(l.getFont().deriveFont(14f));
		l.setOpaque(false);
		l.setBackground(CHIP_HOVER);
		l.setForeground(Color.WHITE);
		l.setBorder(new EmptyBorder(4, 9, 4, 9));
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		ClickRouter.markNoRoute(l);
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				l.setOpaque(true);
				l.repaint();
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				l.setOpaque(false);
				l.repaint();
			}
		});
		return l;
	}

	private void paintLock(boolean lockable)
	{
		if (lockLabel == null)
		{
			return;
		}
		if (!lockable)
		{
			VectorIcon.apply(lockLabel, "🔒", VectorIcon.Kind.LOCK, 14, new Color(0x555555));
			lockLabel.setForeground(new Color(0x555555));
			lockLabel.setCursor(Cursor.getDefaultCursor());
			lockLabel.setToolTipText("Lock pins the 07Flip recommended sell price (premium).");
			return;
		}
		boolean locked = plugin != null && plugin.isPriceLocked(currentItemId);
		Color lc = locked ? new Color(0xFFC845) : new Color(0xAAAAAA);
		VectorIcon.apply(lockLabel, locked ? "🔒" : "🔓",
			locked ? VectorIcon.Kind.LOCK : VectorIcon.Kind.UNLOCK, 14, lc);
		lockLabel.setForeground(lc);
		lockLabel.setToolTipText(locked
			? "Price locked — click to unlock."
			: "Click to lock the recommended sell price.");
	}

	private void startHeaderCooldownTimer()
	{
		if (headerCooldownTimer == null)
		{
			headerCooldownTimer = new javax.swing.Timer(1000, e ->
			{
				if (currentItemId > 0 && plugin != null && plugin.buyLimitCooldownMs(currentItemId) > 0)
				{
					repaint();
				}
				else
				{
					headerCooldownTimer.stop();
					if (lastShown != null && !inEmptyState && !inFailedState)
					{
						show(lastShown);
					}
				}
			});
			headerCooldownTimer.setRepeats(true);
		}
		if (!headerCooldownTimer.isRunning())
		{
			headerCooldownTimer.start();
		}
	}

	private java.awt.Component sectionDivider()
	{
		JPanel d = new JPanel();
		d.setBackground(new Color(0x2A2A2A));
		d.setAlignmentX(Component.LEFT_ALIGNMENT);
		d.setPreferredSize(new Dimension(0, 1));
		d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return d;
	}

	private JPanel buildHeader(int itemId, String name, boolean members, int buyLimit, Integer highAlch, Integer lowAlch, Integer dailyVolume, long buyTarget)
	{
		this.currentItemId   = itemId;
		this.currentItemName = name;

		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setBackground(SECTION_BG);
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

		JLabel icon = FlipItemPanel.buildIcon(itemId, itemManager);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		StringBuilder sub = new StringBuilder();
		if (buyLimit > 0)
		{
			sub.append("Limit ").append(buyLimit);
		}
		if (dailyVolume != null && dailyVolume > 0)
		{
			if (sub.length() > 0)
			{
				sub.append("  ·  ");
			}
			sub.append(FlipItemPanel.formatGpCompact(dailyVolume)).append("/day");
		}
		if (sub.length() == 0)
		{
			sub.append(members ? "Members" : "F2P");
		}
		JLabel subLabel = new JLabel(sub.toString());
		subLabel.setFont(Fonts.SM);
		subLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		subLabel.setToolTipText("Buy limit per 4h · daily trade volume");

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(SECTION_BG);
		text.add(nameLabel);
		text.add(Box.createVerticalStrut(2));
		boolean onCooldown = plugin != null && plugin.buyLimitCooldownMs(itemId) > 0;
		if (onCooldown)
		{
			BuyLimitBar bar = new BuyLimitBar(plugin, itemId);
			bar.setAlignmentX(Component.LEFT_ALIGNMENT);
			text.add(bar);
			startHeaderCooldownTimer();
		}
		else
		{
			text.add(subLabel);
			if (headerCooldownTimer != null)
			{
				headerCooldownTimer.stop();
			}
		}

		starLabel = new JLabel();
		starLabel.setFont(starLabel.getFont().deriveFont(20f));
		starLabel.setBorder(new EmptyBorder(0, 8, 0, 0));
		starLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		ClickRouter.markNoRoute(starLabel);
		paintStar();
		starLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onStarClicked();
			}
		});

		panel.add(icon,      BorderLayout.WEST);
		panel.add(text,      BorderLayout.CENTER);
		panel.add(starLabel, BorderLayout.EAST);

		if (plugin != null && buyTarget > 0)
		{
			MouseAdapter rightClickQueueBuy = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown())
					{
						plugin.queueGeBuy(itemId, buyTarget, name);
						e.consume();
					}
				}
			};
			panel.addMouseListener(rightClickQueueBuy);
			icon.addMouseListener(rightClickQueueBuy);
			text.addMouseListener(rightClickQueueBuy);
			nameLabel.addMouseListener(rightClickQueueBuy);
			subLabel.addMouseListener(rightClickQueueBuy);
		}

		return panel;
	}

	private void paintStar()
	{
		if (starLabel == null) return;
		boolean hasKey   = plugin != null && plugin.hasApiKeyPublic();
		boolean favourited = plugin != null && plugin.isFavourite(currentItemId);
		if (!hasKey)
		{
			VectorIcon.apply(starLabel, "☆", VectorIcon.Kind.STAR_OUTLINE, 18, new Color(0x444444));
			starLabel.setForeground(new Color(0x444444));
			starLabel.setToolTipText("<html>Paste your 07flip.com API key in the plugin config<br>"
				+ "to use favourites.</html>");
		}
		else if (favourited)
		{
			VectorIcon.apply(starLabel, "★", VectorIcon.Kind.STAR, 18, new Color(0xFFC845));
			starLabel.setForeground(new Color(0xFFC845));
			starLabel.setToolTipText("Favourited — click to remove from your list.");
		}
		else
		{
			VectorIcon.apply(starLabel, "☆", VectorIcon.Kind.STAR_OUTLINE, 18, new Color(0xAAAAAA));
			starLabel.setForeground(new Color(0xAAAAAA));
			starLabel.setToolTipText("Click to favourite this item — it'll appear in the Favs tab.");
		}
	}

	private void onStarClicked()
	{
		if (plugin == null || currentItemId <= 0) return;
		if (!plugin.hasApiKeyPublic())
		{
			return;
		}
		boolean wasFav = plugin.isFavourite(currentItemId);
		plugin.toggleFavourite(currentItemId, wasFav,
			() -> { },
			() ->
			{
				paintStar();
				if (starLabel != null)
				{
					starLabel.setToolTipText("Couldn't update favourites — try again in a moment.");
				}
			});
	}

	public void refreshFavouriteState()
	{
		paintStar();
	}

	private JPanel buildLivePrices(ItemInsights ins)
	{
		JPanel panel = sectionPanel("Live prices");
		ItemInsights.Current c = ins.current;
		if (c == null)
		{
			panel.add(row("No price data", "—"));
			return panel;
		}
		panel.add(rowGp("Buy",    c.buyPrice,  ageSuffix(c.buyAgeMinutes),  Color.WHITE));
		panel.add(rowGp("Sell",   c.sellPrice, ageSuffix(c.sellAgeMinutes), Color.WHITE));
		panel.add(rowGp("Margin", c.margin,    null,                         margingColor(c.margin)));
		panel.add(rowGp("Tax",    c.tax,       null,                         LOCKED_COL));
		panel.add(rowGp("Profit", c.profit,    null,                         margingColor(c.profit)));
		panel.add(rowText("ROI",  String.format("%+.2f%%", c.roiPct),       margingColor((long) c.roiPct)));
		return panel;
	}

	private JPanel build07FlipPrices(ItemInsights ins)
	{
		boolean locked = plugin != null && plugin.isPriceLocked(ins.itemId);
		Long frozenBuy  = locked && plugin != null ? plugin.getFrozenBuy(ins.itemId)  : null;
		Long frozenSell = locked && plugin != null ? plugin.getFrozenSell(ins.itemId) : null;
		if (locked && frozenBuy != null && frozenBuy > 0 && frozenSell != null && frozenSell > 0)
		{
			JPanel panel = sectionPanel("07Flip recommended (locked)");
			Long serverProfit = plugin.getFrozenProfit(ins.itemId);
			long fProfit = serverProfit != null
				? serverProfit
				: frozenSell - com.o7flip.util.ProfitCalculator.geTaxFor(ins.itemId, frozenSell, 1) - frozenBuy;
			panel.add(rowGp("Buy",    frozenBuy,  "frozen", FROZEN_COL));
			panel.add(rowGp("Sell",   frozenSell, "frozen", FROZEN_COL));
			panel.add(rowGp("Profit", fProfit,    null,     margingColor(fProfit)));
			return panel;
		}
		JPanel panel = sectionPanel("07Flip recommended");
		ItemInsights.Current c = ins.current;
		if (c == null || c.recBuy == null)
		{
			panel.add(row("No recommended prices", "—"));
			return panel;
		}
		panel.add(rowGp("Buy",    c.recBuy,    null, Color.WHITE));
		panel.add(rowGp("Sell",   c.recSell,   null, Color.WHITE));
		panel.add(rowGp("Profit", c.recProfit, null, margingColor(c.recProfit)));
		return panel;
	}

	private JPanel buildPlan(int itemId)
	{
		com.o7flip.model.Models.OptimizeResult.Allocation a =
			plugin != null ? plugin.planAllocationFor(itemId) : null;
		if (a == null || a.qty <= 0)
		{
			return null;
		}
		int bought = sumFills(a.buys);
		int sold   = sumFills(a.sells);

		JPanel panel = sectionPanel("Your plan");
		panel.add(rowGp("Buy",  a.buyPrice,  null, Color.WHITE));
		panel.add(rowGp("Sell", a.sellPrice, null, Color.WHITE));
		panel.add(rowText("Quantity", FlipItemPanel.formatGp(a.qty), Color.WHITE));
		panel.add(rowGp("Allocated", a.gpAllocated, null, ColorScheme.LIGHT_GRAY_COLOR));
		panel.add(rowGp("Expected profit", a.expectedProfit, null, margingColor(a.expectedProfit)));
		panel.add(rowText("Bought", FlipItemPanel.formatGp(bought) + " / " + FlipItemPanel.formatGp(a.qty),
			bought >= a.qty ? PROFIT_COL : ColorScheme.LIGHT_GRAY_COLOR));
		if (sold > 0 || bought > 0)
		{
			panel.add(rowText("Sold", FlipItemPanel.formatGp(sold) + " / " + FlipItemPanel.formatGp(bought),
				sold > 0 && sold >= bought ? PROFIT_COL : ColorScheme.LIGHT_GRAY_COLOR));
		}
		return panel;
	}

	private static int sumFills(java.util.List<com.o7flip.model.Models.SlotFill> fills)
	{
		if (fills == null)
		{
			return 0;
		}
		int total = 0;
		for (com.o7flip.model.Models.SlotFill f : fills)
		{
			if (f != null) total += f.qty;
		}
		return total;
	}

	private JPanel buildRanges(ItemInsights ins)
	{
		JPanel panel = sectionPanel("Price range");
		ItemInsights.Ranges r = ins.ranges;
		if (r == null)
		{
			panel.add(row("No range data", "—"));
			return panel;
		}
		panel.add(rowText("24h", FlipItemPanel.formatGpCompact(r.low24h) + " — " + FlipItemPanel.formatGpCompact(r.high24h), Color.WHITE));
		if (r.low7d > 0 || r.high7d > 0)
		{
			panel.add(rowText("7d", FlipItemPanel.formatGpCompact(r.low7d) + " — " + FlipItemPanel.formatGpCompact(r.high7d), Color.WHITE));
		}
		panel.add(rowText("90d", FlipItemPanel.formatGpCompact(r.low90d) + " — " + FlipItemPanel.formatGpCompact(r.high90d), Color.WHITE));
		if (r.position90dPct != null)
		{
			panel.add(rowText("90d position", String.format("%.0f%% of range", r.position90dPct), rangePositionColor(r.position90dPct)));
		}
		if (r.drawdownPctFrom90d != null)
		{
			panel.add(rowText("Drawdown", String.format("%.1f%% below 90d high", r.drawdownPctFrom90d), ColorScheme.LIGHT_GRAY_COLOR));
		}
		if (r.daysSince90dLow != null)
		{
			panel.add(rowText("90d low was", daysAgo(r.daysSince90dLow), ColorScheme.LIGHT_GRAY_COLOR));
		}
		if (r.daysSince90dHigh != null)
		{
			panel.add(rowText("90d high was", daysAgo(r.daysSince90dHigh), ColorScheme.LIGHT_GRAY_COLOR));
		}
		return panel;
	}

	private JPanel buildScore(ItemInsights ins)
	{
		JPanel panel = sectionPanel("07Flip score");
		ItemInsights.Score s = ins.score;
		if (s == null)
		{
			panel.add(row("No score data", "—"));
			return panel;
		}
		panel.add(rowText("Confidence", s.confidence + " / 100", confidenceColor(s.confidence)));
		panel.add(rowText("Tier",       s.tier != null ? capitalise(s.tier) : "—", tierColor(s.tier)));
		if (s.signal != null && !s.signal.isEmpty())
		{
			panel.add(rowText("Signal", s.signal, signalColor(s.signal)));
		}
		return panel;
	}

	private JPanel buildIndicators(ItemInsights.Indicators ind)
	{
		JPanel panel = sectionPanel("Technical indicators");
		boolean any = false;
		if (ind.rsi14 != null)
		{
			String suffix = ind.rsi14 <= 30 ? " · oversold" : (ind.rsi14 >= 70 ? " · overbought" : "");
			Color col = ind.rsi14 <= 30 ? PROFIT_COL : (ind.rsi14 >= 70 ? LOSS_COL : Color.WHITE);
			panel.add(rowText("RSI (14)", String.format("%.1f%s", ind.rsi14, suffix), col));
			any = true;
		}
		if (ind.macdCross != null)
		{
			panel.add(rowText("MACD cross", capitalise(ind.macdCross), crossColor(ind.macdCross)));
			any = true;
		}
		else if (ind.macdHist != null)
		{
			panel.add(rowText("MACD hist", String.format("%+.1f", ind.macdHist), signColor(ind.macdHist)));
			any = true;
		}
		if (ind.bbPositionPct != null)
		{
			Color col = ind.bbPositionPct <= 20 ? PROFIT_COL : (ind.bbPositionPct >= 80 ? LOSS_COL : Color.WHITE);
			panel.add(rowText("Bollinger", String.format("%.0f%% of band", ind.bbPositionPct), col));
			any = true;
		}
		if (ind.ma7d != null && ind.ma30d != null)
		{
			panel.add(rowText("MA 7d / 30d",
				FlipItemPanel.formatGpCompact(ind.ma7d) + " / " + FlipItemPanel.formatGpCompact(ind.ma30d),
				Color.WHITE));
			any = true;
		}
		if (ind.maCross != null)
		{
			panel.add(rowText("MA cross", capitalise(ind.maCross), crossColor(ind.maCross)));
			any = true;
		}
		any |= addPctChangeRow(panel, "Change 1h",  ind.pct1h);
		any |= addPctChangeRow(panel, "Change 24h", ind.pct24h);
		any |= addPctChangeRow(panel, "Change 7d",  ind.pct7d);
		any |= addPctChangeRow(panel, "Change 30d", ind.pct30d);
		if (ind.volSurge != null)
		{
			Color col = ind.volSurge >= 3 ? new Color(0xE8A838) : Color.WHITE;
			panel.add(rowText("Volume surge", String.format("%.1f× baseline", ind.volSurge), col));
			any = true;
		}
		if (!any)
		{
			panel.add(row("No indicator data", "—"));
		}
		return panel;
	}

	private JPanel buildLiquidity(ItemInsights.Liquidity l)
	{
		JPanel panel = sectionPanel("Liquidity");
		boolean any = false;
		if (l.hourlyBuyVolume != null)
		{
			panel.add(rowText("Buys / hr", String.format("%,d", l.hourlyBuyVolume), Color.WHITE));
			any = true;
		}
		if (l.hourlySellVolume != null)
		{
			panel.add(rowText("Sells / hr", String.format("%,d", l.hourlySellVolume), Color.WHITE));
			any = true;
		}
		if (l.volumeImbalancePct != null)
		{
			String side = l.volumeImbalancePct >= 0 ? "buy pressure" : "sell pressure";
			panel.add(rowText("Imbalance",
				String.format("%+.1f%% %s", l.volumeImbalancePct, side),
				signColor(l.volumeImbalancePct)));
			any = true;
		}
		if (l.crossedHours24h != null)
		{
			Color col = l.crossedHours24h == 0 ? PROFIT_COL : (l.crossedHours24h >= 6 ? LOSS_COL : new Color(0xE8A838));
			panel.add(rowText("Margin inverted", l.crossedHours24h + "h of last 24", col));
			any = true;
		}
		if (l.estHoursToFillLimit != null)
		{
			panel.add(rowText("Est. limit fill", "~" + formatHours(l.estHoursToFillLimit), Color.WHITE));
			any = true;
		}
		if (!any)
		{
			panel.add(row("No liquidity data", "—"));
		}
		return panel;
	}

	private static boolean addPctChangeRow(JPanel panel, String label, Double pct)
	{
		if (pct == null)
		{
			return false;
		}
		panel.add(rowText(label, String.format("%+.1f%%", pct), signColor(pct)));
		return true;
	}

	private static Color signColor(double v)
	{
		if (v > 0)
		{
			return PROFIT_COL;
		}
		if (v < 0)
		{
			return LOSS_COL;
		}
		return Color.LIGHT_GRAY;
	}

	private static Color crossColor(String cross)
	{
		if ("bullish".equalsIgnoreCase(cross))
		{
			return PROFIT_COL;
		}
		if ("bearish".equalsIgnoreCase(cross))
		{
			return LOSS_COL;
		}
		return Color.WHITE;
	}

	private static Color rangePositionColor(double positionPct)
	{
		if (positionPct <= 25)
		{
			return PROFIT_COL;
		}
		if (positionPct >= 75)
		{
			return LOSS_COL;
		}
		return Color.WHITE;
	}

	private static String daysAgo(int days)
	{
		if (days <= 0)
		{
			return "today";
		}
		if (days == 1)
		{
			return "yesterday";
		}
		return days + " days ago";
	}

	private static String formatHours(double hours)
	{
		if (hours < 1)
		{
			return Math.max(1, Math.round(hours * 60)) + "m";
		}
		if (hours < 48)
		{
			return String.format("%.1fh", hours);
		}
		return String.format("%.1fd", hours / 24);
	}

	private JPanel buildVolume(ItemInsights ins)
	{
		JPanel panel = sectionPanel("Volume");
		ItemInsights.Volume v = ins.volume;
		if (v == null)
		{
			panel.add(row("No volume data", "—"));
			return panel;
		}
		panel.add(rowText("Hourly", String.format("%,d / hr", v.hourly), Color.WHITE));
		panel.add(rowText("Daily",  String.format("%,d / day", v.daily), Color.WHITE));
		return panel;
	}

	private static class ChartPeriod
	{
		final String label;
		final Long[] buy;
		final Long[] sell;
		final String axisStart;
		final long   startEpochMs;
		final int    intervalMinutes;

		ChartPeriod(String label, Long[] buy, Long[] sell, String axisStart, String startIso, int intervalMinutes)
		{
			this.label           = label;
			this.buy             = buy;
			this.sell            = sell;
			this.axisStart       = axisStart;
			this.startEpochMs     = parseIsoToEpochMs(startIso);
			this.intervalMinutes = intervalMinutes;
		}
	}

	private static long parseIsoToEpochMs(String iso)
	{
		if (iso == null || iso.isEmpty())
		{
			return -1L;
		}
		try
		{
			return java.time.Instant.parse(iso).toEpochMilli();
		}
		catch (Exception e)
		{
			return -1L;
		}
	}

	private static boolean hasSeries(Long[] buy, Long[] sell)
	{
		return (buy != null && buy.length > 0) || (sell != null && sell.length > 0);
	}

	private static final String DEFAULT_CHART_PERIOD = "24h";

	private JPanel buildChartSection(ItemInsights ins)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(SECTION_BG);
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		final List<ChartPeriod> periods = new ArrayList<>();
		if (hasSeries(ins.sparkline2hBuy, ins.sparkline2hSell))
		{
			periods.add(new ChartPeriod("2h", ins.sparkline2hBuy, ins.sparkline2hSell, "−2h", ins.sparkline2hStart, 5));
		}
		if (hasSeries(ins.sparkline4hBuy, ins.sparkline4hSell))
		{
			periods.add(new ChartPeriod("4h", ins.sparkline4hBuy, ins.sparkline4hSell, "−4h", ins.sparkline4hStart, 5));
		}
		if (hasSeries(ins.sparkline24hBuy, ins.sparkline24hSell))
		{
			periods.add(new ChartPeriod("24h", ins.sparkline24hBuy, ins.sparkline24hSell, "−24h", ins.sparkline24hStart, 60));
		}
		if (hasSeries(ins.sparkline7dBuy, ins.sparkline7dSell))
		{
			periods.add(new ChartPeriod("7d", ins.sparkline7dBuy, ins.sparkline7dSell, "−7d", ins.sparkline7dStart, 240));
		}
		if (hasSeries(ins.sparkline30dBuy, ins.sparkline30dSell))
		{
			periods.add(new ChartPeriod("30d", ins.sparkline30dBuy, ins.sparkline30dSell, "−30d", ins.sparkline30dStart, 1440));
		}

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setBackground(SECTION_BG);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		headerRow.setBorder(new EmptyBorder(0, 0, 4, 0));

		JLabel header = new JLabel("Buy / Sell");
		header.setFont(Fonts.SM_BOLD);
		header.setForeground(HEADER_COL);
		headerRow.add(header, BorderLayout.WEST);
		panel.add(headerRow);

		if (periods.isEmpty())
		{
			panel.add(row("No chart data", "—"));
			return panel;
		}

		final JPanel chartHolder = new JPanel(new BorderLayout());
		chartHolder.setBackground(SECTION_BG);
		chartHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		chartHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

		final List<JLabel> chips = new ArrayList<>();
		if (periods.size() > 1)
		{
			JPanel chipRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
			chipRow.setBackground(SECTION_BG);
			for (int i = 0; i < periods.size(); i++)
			{
				final int idx = i;
				JLabel chip = new JLabel(periods.get(i).label);
				chip.setFont(Fonts.SM_BOLD);
				chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				ClickRouter.markNoRoute(chip);
				chip.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						selectChartPeriod(periods, chips, idx, chartHolder);
						e.consume();
					}
				});
				chips.add(chip);
				chipRow.add(chip);
			}
			headerRow.add(chipRow, BorderLayout.EAST);
		}

		panel.add(chartHolder);
		int startIdx = (desiredChartPeriodIdx >= 0 && desiredChartPeriodIdx < periods.size())
			? desiredChartPeriodIdx : defaultPeriodIndex(periods);
		selectChartPeriod(periods, chips, startIdx, chartHolder);

		JPanel legend = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
		legend.setBackground(SECTION_BG);
		legend.setBorder(new EmptyBorder(4, 0, 0, 0));
		legend.setAlignmentX(Component.LEFT_ALIGNMENT);
		legend.add(legendChip("Buy",  BuySellSparkline.BUY_COL));
		legend.add(legendChip("Sell", BuySellSparkline.SELL_COL));
		panel.add(legend);

		return panel;
	}

	private int defaultPeriodIndex(List<ChartPeriod> periods)
	{
		String pref = config != null ? config.defaultChartPeriod().chartLabel() : DEFAULT_CHART_PERIOD;
		for (int i = 0; i < periods.size(); i++)
		{
			if (pref.equals(periods.get(i).label))
			{
				return i;
			}
		}
		for (int i = 0; i < periods.size(); i++)
		{
			if (DEFAULT_CHART_PERIOD.equals(periods.get(i).label))
			{
				return i;
			}
		}
		return 0;
	}

	private void selectChartPeriod(List<ChartPeriod> periods, List<JLabel> chips, int idx, JPanel chartHolder)
	{
		desiredChartPeriodIdx = idx;
		ChartPeriod p = periods.get(idx);
		if (plugin != null)
		{
			plugin.selectedChartPeriod = p.label;
		}
		chartHolder.removeAll();
		chartHolder.add(new BuySellSparkline(p.buy, p.sell, 80, p.axisStart, "now", p.startEpochMs, p.intervalMinutes), BorderLayout.CENTER);
		for (int i = 0; i < chips.size(); i++)
		{
			chips.get(i).setForeground(i == idx ? HEADER_COL : new Color(0x777777));
		}
		chartHolder.revalidate();
		chartHolder.repaint();
	}

	private static JLabel legendChip(String label, Color colour)
	{
		JLabel l = new JLabel("■ " + label);
		l.setFont(Fonts.SM);
		l.setForeground(colour);
		return l;
	}

	private JPanel buildProjection(ItemInsights.Projection p)
	{
		JPanel panel = sectionPanel("Projection");
		if (p.band30d != null)
		{
			panel.add(rowText("30 days",
				FlipItemPanel.formatGpCompact(p.band30d.low) + " — " + FlipItemPanel.formatGpCompact(p.band30d.high)
					+ " (" + Math.round(p.band30d.hitRate * 100) + "%)",
				Color.WHITE));
		}
		if (p.band3m != null)
		{
			panel.add(rowText("3 months",
				FlipItemPanel.formatGpCompact(p.band3m.low) + " — " + FlipItemPanel.formatGpCompact(p.band3m.high)
					+ " (" + Math.round(p.band3m.hitRate * 100) + "%)",
				Color.WHITE));
		}
		return panel;
	}

	private JPanel buildUpsell(String upgradeUrl)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(UPSELL_BG);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(UPSELL_BORD, 1),
			new EmptyBorder(10, 12, 10, 12)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel headline = new JLabel("Unlock the full picture");
		headline.setFont(Fonts.BOLD);
		headline.setForeground(HEADER_COL);
		headline.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(headline);
		panel.add(Box.createVerticalStrut(4));

		JLabel pitch = new JLabel(
			"<html><div style='width:230px'>See 07Flip recommended buy/sell prices, the live Flip / Wait signal, "
				+ "technical indicators (RSI, MACD, moving averages), flip quality with GP/hr estimates, "
				+ "liquidity and dump-risk flags, and 30d / 3-month projection bands.</div></html>");
		pitch.setFont(Fonts.SM);
		pitch.setForeground(Color.WHITE);
		pitch.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(pitch);
		panel.add(Box.createVerticalStrut(8));

		JButton button = new JButton("Get Premium");
		button.setFont(Fonts.SM_BOLD);
		button.setForeground(Color.BLACK);
		button.setBackground(HEADER_COL);
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setBorder(new EmptyBorder(6, 14, 6, 14));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.addActionListener(e -> LinkBrowser.browse(upgradeUrl));
		panel.add(button);

		return panel;
	}

	private JPanel sectionPanel(String header)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(SECTION_BG);
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel h = new JLabel(header);
		h.setFont(Fonts.SM_BOLD);
		h.setForeground(HEADER_COL);
		h.setAlignmentX(Component.LEFT_ALIGNMENT);
		h.setBorder(new EmptyBorder(0, 0, 4, 0));
		panel.add(h);
		return panel;
	}

	private static JPanel row(String label, String value)
	{
		return rowText(label, value, Color.WHITE);
	}

	private static JPanel rowText(String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(SECTION_BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JLabel l = new JLabel(label);
		l.setFont(Fonts.SM);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel v = new JLabel(value, SwingConstants.RIGHT);
		v.setFont(Fonts.SM_BOLD);
		v.setForeground(valueColor);

		row.add(l, BorderLayout.WEST);
		row.add(v, BorderLayout.EAST);
		return row;
	}

	private static JPanel rowGp(String label, long gp, String suffix, Color valueColor)
	{
		String text = FlipItemPanel.formatGp(gp) + " gp";
		if (suffix != null && !suffix.isEmpty())
		{
			text = text + "  " + suffix;
		}
		return rowText(label, text, valueColor);
	}

	static String ageSuffix(Integer ageMinutes)
	{
		if (ageMinutes == null)
		{
			return null;
		}
		if (ageMinutes < 1)
		{
			return "now";
		}
		if (ageMinutes < 60)
		{
			return ageMinutes + "m";
		}
		long h = ageMinutes / 60;
		return h + "h";
	}

	private static JLabel centeredLabel(String text, Color colour, java.awt.Font font)
	{
		JLabel l = new JLabel(text, SwingConstants.CENTER);
		l.setFont(font);
		l.setForeground(colour);
		l.setAlignmentX(Component.CENTER_ALIGNMENT);
		l.setBorder(new EmptyBorder(8, 12, 8, 12));
		return l;
	}

	private static Color margingColor(long amount)
	{
		if (amount > 0)
		{
			return PROFIT_COL;
		}
		if (amount < 0)
		{
			return LOSS_COL;
		}
		return Color.LIGHT_GRAY;
	}

	private static Color confidenceColor(int score)
	{
		if (score >= 75)
		{
			return PROFIT_COL;
		}
		if (score >= 50)
		{
			return new Color(0xC4A052);
		}
		return LOCKED_COL;
	}

	private static Color tierColor(String tier)
	{
		if (tier == null)
		{
			return Color.LIGHT_GRAY;
		}
		switch (tier)
		{
			case "great": return new Color(0x00C27A);
			case "good":  return new Color(0xC4A052);
			case "fair":  return Color.LIGHT_GRAY;
			default:      return LOCKED_COL;
		}
	}

	private static Color signalColor(String signal)
	{
		if (signal == null)
		{
			return Color.LIGHT_GRAY;
		}
		if (signal.equalsIgnoreCase("flip"))
		{
			return PROFIT_COL;
		}
		if (signal.equalsIgnoreCase("wait") || signal.equalsIgnoreCase("hold"))
		{
			return new Color(0xE8A838);
		}
		return Color.WHITE;
	}

	private static String capitalise(String s)
	{
		if (s == null || s.isEmpty())
		{
			return "";
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

}
