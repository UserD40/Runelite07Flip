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
import com.o7flip.model.OptimizeResult;
import com.o7flip.model.SlotState;
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
import javax.swing.border.LineBorder;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class OptimizerAllocationCard extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color HOVER_BG  = new Color(0x3A3A3A);

	public OptimizerAllocationCard(OptimizeResult.Allocation a, ItemManager itemManager,
		boolean odd, O7FlipPlugin plugin, Runnable onSwapClicked, int slotIndex)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(a.itemId, itemManager);

		JLabel nameLabel = new JLabel(a.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		JComponent stateChipForName = buildStateChip(a);

		JPanel chipCluster = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		chipCluster.setBackground(bg);
		if (a.partial)
		{
			chipCluster.add(buildPartialBadge());
		}
		if (stateChipForName != null)
		{
			chipCluster.add(stateChipForName);
		}

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(bg);
		nameRow.add(nameLabel, BorderLayout.CENTER);
		if (chipCluster.getComponentCount() > 0)
		{
			nameRow.add(chipCluster, BorderLayout.EAST);
		}

		JLabel buyLine = new JLabel("<html>"
			+ "<font color='#7AB6FF'>Buy at:</font> "
			+ "<font color='#FFFFFF'><b>" + FlipItemPanel.formatGp(a.buyPrice) + "</b></font>"
			+ "</html>");
		buyLine.setFont(Fonts.SM);

		JLabel sellLine = new JLabel("<html>"
			+ "<font color='#FFC077'>Sell at:</font> "
			+ "<font color='#FFFFFF'><b>" + FlipItemPanel.formatGp(a.sellPrice) + "</b></font>"
			+ "</html>");
		sellLine.setFont(Fonts.SM);

		String pricesTooltip = "<html><b>Recommended bid / ask</b>"
			+ "<br><font color='#888888'>" + priceSourceLabel(a) + "</font>"
			+ (a.rawBuyPrice != null && a.rawSellPrice != null
				? "<br><font color='#888888'>Raw spot: "
					+ FlipItemPanel.formatGp(a.rawBuyPrice) + " / "
					+ FlipItemPanel.formatGp(a.rawSellPrice) + " gp</font>"
				: "")
			+ "<br><font color='#666666'>Right-click anywhere to queue a Buy. Sell price auto-fills when you re-list.</font></html>";
		buyLine.setToolTipText(pricesTooltip);
		sellLine.setToolTipText(pricesTooltip);

		JLabel profitPerUnit = new JLabel("<html><font color='#00C27A'><b>+"
			+ FlipItemPanel.formatGp(a.profitPerUnit) + "</b></font>"
			+ "<font color='#888888'> profit per unit (after tax)</font></html>");
		profitPerUnit.setFont(Fonts.SM);
		profitPerUnit.setToolTipText("<html>After-tax profit at the recommended bid/ask."
			+ "<br><font color='#888888'>Server-computed — tax edge cases (≤50gp exempt,"
			+ " bonds/teleports/tools exempt, 5M cap per item) are pre-applied.</font></html>");

		StringBuilder line4 = new StringBuilder("<html>")
			.append("<font color='#FFFFFF'>").append(FlipItemPanel.formatGp(a.qty)).append("</font>")
			.append("<font color='#888888'>×</font> ")
			.append("<font color='#FFE07A'>").append(FlipItemPanel.formatGpCompact(a.gpAllocated)).append("</font>")
			.append("</html>");
		JLabel allocLine = new JLabel(line4.toString());
		allocLine.setFont(Fonts.SM);
		allocLine.setToolTipText("Units to buy × total gp allocated to this slot.");

		JPanel bottomRow = new JPanel(new BorderLayout(6, 0));
		bottomRow.setBackground(bg);
		bottomRow.add(allocLine, BorderLayout.CENTER);

		int boughtSoFar = sumQty(a.buys);
		JLabel progressLabel = buildProgressLabel(a);
		JComponent progressBar = buildProgressBar(a);

		JButton stopBuyingBtn = null;
		if (a.state == SlotState.BUYING && !a.partial && boughtSoFar > 0 && plugin != null && slotIndex >= 0)
		{
			stopBuyingBtn = buildStopBuyingButton(boughtSoFar);
			final int idx = slotIndex;
			stopBuyingBtn.addActionListener(e -> plugin.markPartial(idx));
		}

		JButton regenerateBtn = null;
		if (onSwapClicked != null && isSoldComplete(a))
		{
			regenerateBtn = roundedPill("↻ Regenerate");
			regenerateBtn.setBackground(new Color(0x3E3E3E));
			regenerateBtn.setForeground(Color.WHITE);
			regenerateBtn.setToolTipText("Replace this completed slot with a fresh item (updates 07flip.com too)");
			regenerateBtn.addActionListener(e -> onSwapClicked.run());
		}

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(bg);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		buyLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		sellLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		profitPerUnit.setAlignmentX(Component.LEFT_ALIGNMENT);
		bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(nameRow);
		textPanel.add(Box.createVerticalStrut(3));
		textPanel.add(buyLine);
		textPanel.add(sellLine);
		textPanel.add(Box.createVerticalStrut(2));
		textPanel.add(profitPerUnit);
		textPanel.add(Box.createVerticalStrut(2));
		textPanel.add(bottomRow);
		progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(Box.createVerticalStrut(3));
		textPanel.add(progressLabel);
		textPanel.add(Box.createVerticalStrut(3));
		textPanel.add(progressBar);
		if (stopBuyingBtn != null || regenerateBtn != null)
		{
			JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			actionRow.setBackground(bg);
			actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (stopBuyingBtn != null) actionRow.add(stopBuyingBtn);
			if (regenerateBtn != null) actionRow.add(regenerateBtn);
			textPanel.add(Box.createVerticalStrut(4));
			textPanel.add(actionRow);
		}

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		final boolean swappable = a.state == null || a.state == SlotState.PENDING;
		if (onSwapClicked != null && swappable)
		{
			JLabel swap = new JLabel("↻");
			swap.setFont(swap.getFont().deriveFont(16f));
			swap.setForeground(new Color(0x666666));
			swap.setBorder(new EmptyBorder(0, 4, 0, 4));
			swap.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			swap.setToolTipText("Replace this auto-picked item with the next best one");
			swap.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e) { swap.setForeground(Color.WHITE); }
				@Override
				public void mouseExited(MouseEvent e)  { swap.setForeground(new Color(0x666666)); }
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (!SwingUtilities.isRightMouseButton(e)) onSwapClicked.run();
				}
			});
			add(swap, BorderLayout.EAST);
		}

		ClickRouter.attach(this, plugin, a.itemId, a.name);
		ClickRouter.attachClickOnly(nameLabel,     plugin, a.itemId, a.name);
		ClickRouter.attachClickOnly(profitPerUnit, plugin, a.itemId, a.name);
		ClickRouter.attachClickOnly(allocLine,     plugin, a.itemId, a.name);
		ClickRouter.attachClickOnly(buyLine,       plugin, a.itemId, a.name);
		ClickRouter.attachClickOnly(sellLine,      plugin, a.itemId, a.name);

		MouseAdapter rightClickQueueBuy = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null && a.buyPrice > 0)
				{
					plugin.queueGeBuy(a.itemId, a.buyPrice, a.name);
					e.consume();
				}
			}
		};
		nameLabel.addMouseListener(rightClickQueueBuy);
		profitPerUnit.addMouseListener(rightClickQueueBuy);
		allocLine.addMouseListener(rightClickQueueBuy);
		buyLine.addMouseListener(rightClickQueueBuy);
		sellLine.addMouseListener(rightClickQueueBuy);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_BG);
				textPanel.setBackground(HOVER_BG);
				nameRow.setBackground(HOVER_BG);
				bottomRow.setBackground(HOVER_BG);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				java.awt.Point p = e.getPoint();
				if (contains(p)) return;
				setBackground(bg);
				textPanel.setBackground(bg);
				nameRow.setBackground(bg);
				bottomRow.setBackground(bg);
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && plugin != null && a.buyPrice > 0)
				{
					plugin.queueGeBuy(a.itemId, a.buyPrice, a.name);
				}
			}
		});

	}

	@Override
	public Dimension getMaximumSize()
	{
		Dimension pref = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, pref.height);
	}

	static boolean isSoldComplete(OptimizeResult.Allocation a)
	{
		if (a == null) return false;
		if (a.state == SlotState.CLOSED) return true;
		return a.state == SlotState.SELLING && sumQty(a.sells) >= Math.max(1, a.qty);
	}

	private static JComponent buildStateChip(OptimizeResult.Allocation a)
	{
		if (a.state == null || a.state == SlotState.PENDING) return null;
		String text;
		Color bg;
		if (isSoldComplete(a))
		{
			text = "Sold";
			bg = new Color(0x3A2A4A);
		}
		else if (a.state == SlotState.FILLED && a.sellListed)
		{
			text = "Selling";
			bg = new Color(0x4A3B17);
		}
		else
		{
			switch (a.state)
			{
				case BUYING:  text = "Buying";  bg = new Color(0x1E3556); break;
				case FILLED:  text = "Filled";  bg = new Color(0x004D2E); break;
				case SELLING: text = "Selling"; bg = new Color(0x4A3B17); break;
				case CLOSED:  text = "Sold";    bg = new Color(0x3A2A4A); break;
				default:      return null;
			}
		}
		int bought = sumQty(a.buys);
		int sold   = sumQty(a.sells);
		String tooltip = "<html><b>" + text + "</b><br>"
			+ "Bought: " + FlipItemPanel.formatGp(bought) + " / " + FlipItemPanel.formatGp(a.qty) + "<br>"
			+ "Sold: " + FlipItemPanel.formatGp(sold) + " / " + FlipItemPanel.formatGp(bought) + "</html>";

		JLabel chip = makeChip(text, bg, Color.WHITE);
		chip.setToolTipText(tooltip);
		return chip;
	}

	private static JLabel makeChip(String text, Color bg, Color fg)
	{
		JLabel chip = new JLabel(text);
		chip.setFont(Fonts.SM);
		chip.setOpaque(true);
		chip.setBackground(bg);
		chip.setForeground(fg);
		chip.setBorder(BorderFactory.createCompoundBorder(
			new LineBorder(new Color(0, 0, 0, 60), 1, true),
			new EmptyBorder(2, 6, 2, 6)));
		return chip;
	}

	private static int sumQty(java.util.List<com.o7flip.model.SlotFill> fills)
	{
		if (fills == null) return 0;
		int total = 0;
		for (com.o7flip.model.SlotFill f : fills)
		{
			if (f != null) total += f.qty;
		}
		return total;
	}

	private static JComponent buildPartialBadge()
	{
		JLabel chip = makeChip("partial", new Color(0x4A3B17), new Color(0xFFC077));
		chip.setToolTipText("<html>You stopped buying this slot early.<br>"
			+ "<font color='#888888'>It holds at Filled until the bought units sell, "
			+ "then the freed capital redeploys.</font></html>");
		return chip;
	}

	private static JButton roundedPill(String text)
	{
		JButton btn = new JButton(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int arc = getHeight();
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
				g2.setColor(getForeground());
				g2.setFont(getFont());
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
					(getHeight() + fm.getAscent() - fm.getDescent()) / 2);
				g2.dispose();
			}

			@Override
			protected void paintBorder(Graphics g) { }

			@Override
			public boolean isOpaque() { return false; }
		};
		btn.setFont(Fonts.SM);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(new EmptyBorder(3, 10, 3, 10));
		return btn;
	}

	private static JButton buildStopBuyingButton(int bought)
	{
		JButton btn = roundedPill("Stop buying · sell " + FlipItemPanel.formatGp(bought) + " & recycle");
		btn.setBackground(new Color(0x4A3B17));
		btn.setForeground(new Color(0xFFC077));
		btn.setToolTipText("<html>Cap this slot to the units you've already bought.<br>"
			+ "<font color='#888888'>The rest of its budget is freed and redeploys once these sell.</font></html>");
		return btn;
	}

	private static JLabel buildProgressLabel(OptimizeResult.Allocation a)
	{
		int target = Math.max(0, a.qty);
		int bought = Math.min(sumQty(a.buys), target);
		int sold   = Math.min(sumQty(a.sells), target);
		StringBuilder sb = new StringBuilder("<html><font color='#888888'>");
		if (isSellPhase(a))
		{
			sb.append(FlipItemPanel.formatGp(sold)).append(" / ").append(FlipItemPanel.formatGp(target)).append(" sold");
		}
		else
		{
			sb.append(FlipItemPanel.formatGp(bought)).append(" / ").append(FlipItemPanel.formatGp(a.qty)).append(" bought");
			if (sold > 0)
			{
				sb.append("  ·  ").append(FlipItemPanel.formatGp(sold)).append(" sold");
			}
		}
		sb.append("</font></html>");
		JLabel l = new JLabel(sb.toString());
		l.setFont(Fonts.SM);
		l.setToolTipText("Buy/sell progress for this slot.");
		return l;
	}

	private static boolean isSellPhase(OptimizeResult.Allocation a)
	{
		return a.state == SlotState.SELLING
			|| a.state == SlotState.CLOSED
			|| (a.state == SlotState.FILLED && a.sellListed);
	}

	private static JComponent buildProgressBar(OptimizeResult.Allocation a)
	{
		int bought = sumQty(a.buys);
		int sold   = sumQty(a.sells);
		boolean selling = isSellPhase(a);
		final Color fill = selling ? new Color(0x9B59B6) : new Color(0x00C27A);
		int target = Math.max(1, a.qty);
		double f = selling
			? Math.min(1.0, sold / (double) target)
			: Math.min(1.0, bought / (double) target);
		final double fraction = Math.max(0.0, f);

		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int w = getWidth();
				int h = getHeight();
				int arc = h;
				g2.setColor(new Color(0x3E3E3E));
				g2.fillRoundRect(0, 0, w, h, arc, arc);
				int fw = (int) Math.round(w * fraction);
				if (fw > 0)
				{
					g2.setColor(fill);
					g2.fillRoundRect(0, 0, Math.max(fw, h), h, arc, arc);
				}
				g2.dispose();
			}
		};
		bar.setOpaque(false);
		bar.setPreferredSize(new java.awt.Dimension(0, 8));
		bar.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 8));
		bar.setMinimumSize(new java.awt.Dimension(40, 8));
		return bar;
	}

	private static String priceSourceLabel(OptimizeResult.Allocation a)
	{
		return "recommended".equalsIgnoreCase(a.priceSource)
			? "Source: last-hour p25/p75 fills (high confidence)"
			: "Source: raw instant spot (fewer recent fills)";
	}

}
