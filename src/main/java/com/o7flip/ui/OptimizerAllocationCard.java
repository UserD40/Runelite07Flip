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
		this(a, itemManager, odd, plugin, null, -1);
	}

	public OptimizerAllocationCard(OptimizeResult.Allocation a, ItemManager itemManager,
		boolean odd, O7FlipPlugin plugin, Runnable onSwapClicked)
	{
		this(a, itemManager, odd, plugin, onSwapClicked, -1);
	}

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

		// ── Row 1: name + dots + (optional swap) ─────────────────────────────
		JLabel nameLabel = new JLabel(a.name);
		nameLabel.setFont(Fonts.BOLD);
		nameLabel.setForeground(Color.WHITE);

		// State pill (Buying/Filled/Selling/Closed) replaces the old
		// price-source + fill-confidence dots — clearer signal of progress,
		// less visual noise. A "partial" badge sits alongside it when the slot
		// was capped via "Stop buying".
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
			.append("</html>");
		JLabel allocLine = new JLabel(line4.toString());
		allocLine.setFont(Fonts.SM);
		allocLine.setToolTipText("Units to buy × total gp allocated to this slot.");

		JPanel bottomRow = new JPanel(new BorderLayout(6, 0));
		bottomRow.setBackground(bg);
		bottomRow.add(allocLine, BorderLayout.CENTER);

		// ── Buy/sell progress label + bar — mirrors the website's card UI ─────
		int boughtSoFar = sumQty(a.buys);
		JLabel progressLabel = buildProgressLabel(a);
		JComponent progressBar = buildProgressBar(a);

		// ── C — "Stop buying" on still-buying, not-yet-partial slots ─────────
		JButton stopBuyingBtn = null;
		if (a.state == SlotState.BUYING && !a.partial && boughtSoFar > 0 && plugin != null && slotIndex >= 0)
		{
			stopBuyingBtn = buildStopBuyingButton(boughtSoFar);
			final int idx = slotIndex;
			stopBuyingBtn.addActionListener(e -> plugin.markPartial(idx));
		}

		// Regenerate — on a sold-complete slot, replace the finished position with
		// a fresh pick. Reuses the swap path (swapPlanSlot), which now bumps
		// generated_at so the new item also propagates to the site.
		JButton regenerateBtn = null;
		if (onSwapClicked != null && isSoldComplete(a))
		{
			regenerateBtn = roundedPill("↻ Regenerate");
			regenerateBtn.setBackground(new Color(0x3E3E3E));
			regenerateBtn.setForeground(Color.WHITE);
			regenerateBtn.setToolTipText("Replace this completed slot with a fresh item (updates 07flip.com too)");
			regenerateBtn.addActionListener(e -> onSwapClicked.run());
		}

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

		// Hover-shown swap button — only on PENDING slots (the auto-picked
		// recommendation hasn't been acted on yet). Swapping a slot you're
		// already buying/holding would silently drop a tracked position and its
		// fill ledger from the plan, so it's intentionally unavailable there —
		// matching the website's "reload" affordance, which is pending-only.
		final boolean swappable = a.state == null || a.state == SlotState.PENDING;
		if (onSwapClicked != null && swappable)
		{
			JLabel swap = new JLabel("⟳");
			swap.setFont(swap.getFont().deriveFont(16f));
			swap.setForeground(new Color(0x666666));
			swap.setBorder(new EmptyBorder(0, 4, 0, 4));
			swap.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			swap.setToolTipText("Replace this auto-picked item with the next best one");
			// Persistent (not hover-gated): a panel is far less hover-discoverable
			// than a web card, so the swap affordance stays visible on pending slots.
			swap.setVisible(true);
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

	/**
	 * Computed lazily so the HTML labels' preferred heights are correct at the
	 * point BoxLayout queries us. Capturing getPreferredSize() in the
	 * constructor measures the profit/price lines BEFORE they wrap to the
	 * sidebar's actual width, so wrapped cards came out too short and the
	 * progress bar at the bottom was clipped.
	 */
	@Override
	public Dimension getMaximumSize()
	{
		Dimension pref = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, pref.height);
	}

	/** True when the slot's sells have completed its target — CLOSED, or SELLING
	 *  with {@code sold >=} target qty (covers the bought=0 / site-tracked-sold
	 *  case where {@code SlotState.derive} can't reach CLOSED). */
	static boolean isSoldComplete(OptimizeResult.Allocation a)
	{
		if (a == null) return false;
		if (a.state == SlotState.CLOSED) return true;
		return a.state == SlotState.SELLING && sumQty(a.sells) >= Math.max(1, a.qty);
	}

	private static JComponent buildStateChip(OptimizeResult.Allocation a)
	{
		// PENDING is the empty state — no point rendering a chip for "nothing
		// has happened yet". Show the chip from BUYING onward.
		if (a.state == null || a.state == SlotState.PENDING) return null;
		String text;
		Color bg;
		if (isSoldComplete(a))
		{
			// Fully sold — show "Sold", not "Selling"/"Closed".
			text = "Sold";
			bg = new Color(0x3A2A4A);
		}
		else if (a.state == SlotState.FILLED && a.sellListed)
		{
			// §10 — fully bought AND a live GE sell offer exists: the user is
			// waiting on a buyer, so say "Selling" instead of the stale "Filled".
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

	/** Amber "partial" badge shown alongside the state chip on capped slots. */
	private static JComponent buildPartialBadge()
	{
		JLabel chip = new JLabel("partial");
		chip.setFont(Fonts.SM);
		chip.setOpaque(true);
		chip.setBackground(new Color(0x4A3B17));
		chip.setForeground(new Color(0xFFC077));
		chip.setBorder(BorderFactory.createCompoundBorder(
			new LineBorder(new Color(0, 0, 0, 60), 1, true),
			new EmptyBorder(2, 6, 2, 6)));
		chip.setToolTipText("<html>You stopped buying this slot early.<br>"
			+ "<font color='#888888'>It holds at Filled until the bought units sell, "
			+ "then the freed capital redeploys.</font></html>");
		return chip;
	}

	/** Shared rounded-pill button template for the card's action controls. */
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

	/** Pill button: "Stop buying · sell N & recycle". */
	private static JButton buildStopBuyingButton(int bought)
	{
		JButton btn = roundedPill("Stop buying · sell " + formatNumber(bought) + " & recycle");
		btn.setBackground(new Color(0x4A3B17));
		btn.setForeground(new Color(0xFFC077));
		btn.setToolTipText("<html>Cap this slot to the units you've already bought.<br>"
			+ "<font color='#888888'>The rest of its budget is freed and redeploys once these sell.</font></html>");
		return btn;
	}

	/** "B / Q bought · S sold" — the text beside the progress bar. The optimiser
	 *  counts only up to the plan target, so clamp the displayed counts to qty
	 *  (belt-and-suspenders for any legacy over-filled leg). */
	private static JLabel buildProgressLabel(OptimizeResult.Allocation a)
	{
		int target = Math.max(0, a.qty);
		int bought = Math.min(sumQty(a.buys), target);
		int sold   = Math.min(sumQty(a.sells), target);
		StringBuilder sb = new StringBuilder("<html><font color='#888888'>");
		if (isSellPhase(a))
		{
			// Selling phase — sold/target is the number that matters now; keep
			// it readable for partials ("200 / 1,400 sold").
			sb.append(formatNumber(sold)).append(" / ").append(formatNumber(target)).append(" sold");
		}
		else
		{
			sb.append(formatNumber(bought)).append(" / ").append(formatNumber(a.qty)).append(" bought");
			if (sold > 0)
			{
				sb.append("  ·  ").append(formatNumber(sold)).append(" sold");
			}
		}
		sb.append("</font></html>");
		JLabel l = new JLabel(sb.toString());
		l.setFont(Fonts.SM);
		l.setToolTipText("Buy/sell progress for this slot.");
		return l;
	}

	/** §10 — true once the slot is in its SELL phase: a sell fill has landed
	 *  (SELLING/CLOSED) or a live GE sell offer is listed on a bought slot. */
	private static boolean isSellPhase(OptimizeResult.Allocation a)
	{
		return a.state == SlotState.SELLING
			|| a.state == SlotState.CLOSED
			|| (a.state == SlotState.FILLED && a.sellListed);
	}

	/**
	 * Thin rounded progress bar mirroring the website's card: GREEN fill = buy
	 * progress (bought / qty) while the slot is pending/buying; PURPLE fill = sell
	 * progress (sold / qty) once selling/closed. Empty track when nothing has
	 * filled yet.
	 */
	private static JComponent buildProgressBar(OptimizeResult.Allocation a)
	{
		int bought = sumQty(a.buys);
		int sold   = sumQty(a.sells);
		boolean selling = isSellPhase(a);
		final Color fill = selling ? new Color(0x9B59B6) : new Color(0x00C27A);
		// Both phases measure against the slot's TARGET qty: green = bought/qty
		// while buying, purple = sold/qty while selling/closed. Using qty (not
		// bought) as the sell denominator matches the site and stays correct when
		// the tracked bought count is 0/unknown for an already-sold position.
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
				// Visible track (clearly lighter than the card bg) so an empty bar
				// still reads as a progress bar at 0%.
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
