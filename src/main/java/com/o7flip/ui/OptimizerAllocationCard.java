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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Renders a single allocation from the optimiser response. Compact dark
 * card matching the rest of the plugin's row aesthetic.
 *
 * <pre>
 *   ┌──────────────────────────────────────────────────────┐
 *   │ [icon]  Item name                          [state]   │
 *   │         Buy at: 38,432   Sell at: 41,201             │
 *   │         +2,769 profit per unit (after tax)           │
 *   │         14 × 47.59M · +431.4K total profit           │
 *   └──────────────────────────────────────────────────────┘
 * </pre>
 *
 * <ul>
 *   <li>Right-click anywhere on the card queues a Buy at {@code buy_price}.</li>
 *   <li>{@code profit_per_unit} / {@code expected_profit} are the server's
 *       after-tax numbers — never re-derived client-side.</li>
 *   <li>State pill (Buying/Filled/Selling/Closed) replaces the
 *       price-source/fill-confidence dots — clearer signal of progress.</li>
 *   <li>Sell-side auto-fill on the GE is triggered by the plugin the moment
 *       the slot transitions to {@code FILLED}, so listing the items uses
 *       the recommended ask without further user action.</li>
 * </ul>
 */
public class OptimizerAllocationCard extends JPanel
{
	private static final Color ODD_BG    = new Color(0x272727);
	private static final Color HOVER_BG  = new Color(0x3A3A3A);
	private static final Color BUY_FG    = new Color(0x7AB6FF);   // blue accent
	private static final Color SELL_FG   = new Color(0xFFC077);   // amber accent
	private static final Color GREEN     = new Color(0x00C27A);
	private static final Color MUTED     = new Color(0x888888);

	public OptimizerAllocationCard(OptimizeResult.Allocation a, ItemManager itemManager,
		boolean odd, O7FlipPlugin plugin)
	{
		this(a, itemManager, odd, plugin, null);
	}

	public OptimizerAllocationCard(OptimizeResult.Allocation a, ItemManager itemManager,
		boolean odd, O7FlipPlugin plugin, Runnable onSwapClicked)
	{
		Color bg = odd ? ODD_BG : ColorScheme.DARK_GRAY_COLOR;

		setLayout(new BorderLayout(8, 0));
		setBackground(bg);
		setBorder(new EmptyBorder(8, 10, 8, 10));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = FlipItemPanel.buildIcon(a.itemId, itemManager);

		// ── Row 1: name + dots + (optional swap) ─────────────────────────────
		JLabel nameLabel = new JLabel(a.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		// State pill (Buying/Filled/Selling/Closed) replaces the old
		// price-source + fill-confidence dots — clearer signal of progress,
		// less visual noise.
		JComponent stateChipForName = buildStateChip(a);

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(bg);
		nameRow.add(nameLabel, BorderLayout.CENTER);
		if (stateChipForName != null)
		{
			nameRow.add(stateChipForName, BorderLayout.EAST);
		}

		// ── Row 2: two chips — Buy at / Sell at, exact gp ────────────────────
		// Buy on one line, Sell on the next — keeps long prices (8-digit gp)
		// readable without wrapping mid-number.
		JLabel buyLine = new JLabel("<html>"
			+ "<font color='#7AB6FF'>Buy at:</font> "
			+ "<font color='#FFFFFF'><b>" + formatExactGp(a.buyPrice) + "</b></font>"
			+ "</html>");
		buyLine.setFont(Fonts.SM);

		JLabel sellLine = new JLabel("<html>"
			+ "<font color='#FFC077'>Sell at:</font> "
			+ "<font color='#FFFFFF'><b>" + formatExactGp(a.sellPrice) + "</b></font>"
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

		// ── Row 3: after-tax profit per unit ─────────────────────────────────
		JLabel profitPerUnit = new JLabel("<html><font color='#00C27A'><b>+"
			+ formatExactGp(a.profitPerUnit) + "</b></font>"
			+ "<font color='#888888'> profit per unit (after tax)</font></html>");
		profitPerUnit.setFont(Fonts.SM);
		profitPerUnit.setToolTipText("<html>After-tax profit at the recommended bid/ask."
			+ "<br><font color='#888888'>Server-computed — tax edge cases (≤50gp exempt,"
			+ " bonds/teleports/tools exempt, 5M cap per item) are pre-applied.</font></html>");

		// ── Row 4: allocation + expected cycle profit + fill time + state ──
		// expected_profit is the after-tax total for ONE complete buy→sell
		// cycle (the server changed from a per-hour extrapolation). The
		// label is "profit", not "/hr" — see card tooltip.
		StringBuilder line4 = new StringBuilder("<html>")
			.append("<font color='#FFFFFF'>").append(formatNumber(a.qty)).append("</font>")
			.append("<font color='#888888'>×</font> ")
			.append("<font color='#FFE07A'>").append(FlipItemPanel.formatGpCompact(a.gpAllocated)).append("</font>")
			.append("  <font color='#555555'>·</font>  ")
			.append("<font color='#00C27A'>+").append(FlipItemPanel.formatGpCompact(a.expectedProfit))
			.append("</font><font color='#888888'> total profit</font>");
		line4.append("</html>");
		JLabel allocLine = new JLabel(line4.toString());
		allocLine.setFont(Fonts.SM);
		allocLine.setToolTipText("Total profit if this slot completes one buy/sell cycle, after GE tax. Not a per-hour rate.");

		JPanel bottomRow = new JPanel(new BorderLayout(6, 0));
		bottomRow.setBackground(bg);
		bottomRow.add(allocLine, BorderLayout.CENTER);

		// ── Stack rows ───────────────────────────────────────────────────────
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

		add(iconLabel, BorderLayout.WEST);
		add(textPanel, BorderLayout.CENTER);

		// Hover-shown swap button — only when a swap handler was supplied.
		final JLabel[] swapHolder = new JLabel[1];
		if (onSwapClicked != null)
		{
			JLabel swap = new JLabel("⟳");
			swap.setFont(swap.getFont().deriveFont(16f));
			swap.setForeground(new Color(0x666666));
			swap.setBorder(new EmptyBorder(0, 4, 0, 4));
			swap.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			swap.setToolTipText("Swap this slot for a different item");
			swap.setVisible(false);
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
			swapHolder[0] = swap;
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
				if (swapHolder[0] != null) swapHolder[0].setVisible(true);
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
				if (swapHolder[0] != null) swapHolder[0].setVisible(false);
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

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static JComponent buildStateChip(OptimizeResult.Allocation a)
	{
		// PENDING is the empty state — no point rendering a chip for "nothing
		// has happened yet". Show the chip from BUYING onward.
		if (a.state == null || a.state == SlotState.PENDING) return null;
		String text;
		Color bg;
		switch (a.state)
		{
			case BUYING:  text = "Buying";  bg = new Color(0x1E3556); break;
			case FILLED:  text = "Filled";  bg = new Color(0x004D2E); break;
			case SELLING: text = "Selling"; bg = new Color(0x4A3B17); break;
			case CLOSED:  text = "Closed";  bg = new Color(0x2A2A2A); break;
			default:      return null;
		}
		int bought = sumQty(a.buys);
		int sold   = sumQty(a.sells);
		String tooltip = "<html><b>" + text + "</b><br>"
			+ "Bought: " + formatNumber(bought) + " / " + formatNumber(a.qty) + "<br>"
			+ "Sold: " + formatNumber(sold) + " / " + formatNumber(bought) + "</html>";

		JLabel chip = new JLabel(text);
		chip.setFont(Fonts.SM);
		chip.setOpaque(true);
		chip.setBackground(bg);
		chip.setForeground(Color.WHITE);
		chip.setBorder(BorderFactory.createCompoundBorder(
			new LineBorder(new Color(0, 0, 0, 60), 1, true),
			new EmptyBorder(2, 6, 2, 6)));
		chip.setToolTipText(tooltip);
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

	private static String priceSourceLabel(OptimizeResult.Allocation a)
	{
		return "recommended".equalsIgnoreCase(a.priceSource)
			? "Source: last-hour p25/p75 fills (high confidence)"
			: "Source: raw instant spot (fewer recent fills)";
	}

	/**
	 * Exact gp with comma grouping — the actionable Buy / Sell prices the
	 * user types into the GE need exact precision, not the abbreviated
	 * "38.4K" form used elsewhere. Matches the website's chip rendering.
	 */
	private static String formatExactGp(long n)
	{
		return String.format("%,d", n);
	}

	private static String formatNumber(long n)
	{
		return String.format("%,d", n);
	}
}
