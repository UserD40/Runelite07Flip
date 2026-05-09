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
import com.o7flip.model.RecommendedPrices;
import com.o7flip.util.Fonts;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/**
 * Pop-up showing full per-item context: current buy/sell, recommended
 * buy/sell + profit, GE tax, 07Flip Score, ROI, and a one-click web link.
 *
 * Recommended prices are pulled from the plugin's per-item cache; if the
 * cache is cold, the dialog opens with those rows blank and a small
 * "fetching…" hint, then fills in once {@link O7FlipPlugin#getRecommendedPrices(int)}
 * resolves on the executor thread (the dialog polls the cache every 500 ms
 * for 5 seconds before giving up).
 */
public class ItemDetailDialog extends JDialog
{
	private static final Color BG       = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ROW_BG   = ColorScheme.DARK_GRAY_COLOR;
	private static final Color ORANGE   = new Color(0xFF981F);
	private static final Color GREEN    = new Color(0x00C27A);
	private static final Color RED      = new Color(0xFF7070);
	private static final Color HEADER   = new Color(0xC4A052);
	private static final Color SUBTLE   = new Color(0x888888);

	public static void show(Component owner, FlipItem flip, O7FlipPlugin plugin, ItemManager itemManager)
	{
		Window parent = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		ItemDetailDialog dialog = new ItemDetailDialog(parent, flip, plugin, itemManager);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}

	private final JLabel recBuyValue    = blankValue();
	private final JLabel recSellValue   = blankValue();
	private final JLabel recProfitValue = blankValue();
	private final JLabel taxValue       = blankValue();
	private final JLabel sampleValue    = blankValue();
	private final Timer  pollTimer;

	private ItemDetailDialog(Window parent, FlipItem flip, O7FlipPlugin plugin, ItemManager itemManager)
	{
		super(parent, "07Flip — " + (flip.name != null ? flip.name : "Item"), ModalityType.MODELESS);

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);
		root.setBorder(new EmptyBorder(10, 12, 10, 12));

		// Header row: icon + name + score chip
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBackground(BG);
		header.setBorder(new EmptyBorder(0, 0, 8, 0));
		header.add(FlipItemPanel.buildIcon(flip.itemId, itemManager), BorderLayout.WEST);

		JLabel name = new JLabel(flip.name != null ? flip.name : "");
		name.setFont(Fonts.TITLE);
		name.setForeground(Color.WHITE);
		header.add(name, BorderLayout.CENTER);

		if (flip.flip07Score != null)
		{
			JLabel score = new JLabel(String.valueOf(flip.flip07Score));
			score.setFont(Fonts.BOLD);
			score.setForeground(scoreColor(flip.flip07Score));
			score.setToolTipText("07Flip Score (0–100): composite of price stability, after-tax margin and hourly volume.");
			score.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(scoreColor(flip.flip07Score), 1),
				new EmptyBorder(2, 6, 2, 6)));
			header.add(score, BorderLayout.EAST);
		}

		root.add(header, BorderLayout.NORTH);

		// Body
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(BG);

		body.add(section("Current market"));
		body.add(row("Buy",         FlipItemPanel.formatGp(flip.buyPrice)  + " gp", RED));
		body.add(row("Sell",        FlipItemPanel.formatGp(flip.sellPrice) + " gp", GREEN));
		body.add(row("Profit",      "+" + FlipItemPanel.formatGp(flip.profit) + " gp"
			+ "  (" + String.format("%.2f", flip.roiPct) + "% ROI)", GREEN));
		if (flip.buyLimit > 0)
		{
			body.add(row("GE limit",  String.valueOf(flip.buyLimit), Color.WHITE));
		}
		if (flip.affordableQty != null && flip.affordableQty > 0)
		{
			body.add(row("Affordable", flip.affordableQty + " for your cash", ORANGE));
		}

		body.add(Box.createVerticalStrut(8));
		body.add(section("07Flip Recommended"));
		body.add(rowWithLabel("Buy",    recBuyValue));
		body.add(rowWithLabel("Sell",   recSellValue));
		body.add(rowWithLabel("Profit", recProfitValue));
		body.add(rowWithLabel("Tax",    taxValue));
		body.add(rowWithLabel("Sample", sampleValue));

		root.add(body, BorderLayout.CENTER);

		// Footer
		JButton webBtn   = pillButton("View on 07flip.com");
		webBtn.setBackground(ORANGE);
		webBtn.setForeground(Color.BLACK);
		webBtn.addActionListener(ev -> LinkBrowser.browse("https://07flip.com/item/" + flip.itemId));

		JButton closeBtn = pillButton("Close");
		closeBtn.addActionListener(ev -> dispose());

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		footer.setBackground(BG);
		footer.setBorder(new EmptyBorder(10, 0, 0, 0));
		footer.add(webBtn);
		footer.add(closeBtn);
		root.add(footer, BorderLayout.SOUTH);

		setContentPane(root);
		pack();
		setSize(new Dimension(Math.max(300, getWidth()), getHeight()));
		setResizable(false);

		// Populate recommended-price rows from cache (or kick off a fetch + poll).
		populateRec(plugin.getRecommendedPrices(flip.itemId), flip);
		pollTimer = new Timer(500, ev ->
		{
			RecommendedPrices rp = plugin.getRecommendedPrices(flip.itemId);
			if (rp != null && rp.hasPrices())
			{
				populateRec(rp, flip);
				((Timer) ev.getSource()).stop();
			}
		});
		pollTimer.setRepeats(true);
		pollTimer.setInitialDelay(500);
		pollTimer.start();
		// Stop polling after 5s regardless.
		Timer stopTimer = new Timer(5000, ev -> pollTimer.stop());
		stopTimer.setRepeats(false);
		stopTimer.start();

		addWindowListener(new java.awt.event.WindowAdapter()
		{
			@Override
			public void windowClosed(java.awt.event.WindowEvent e)
			{
				pollTimer.stop();
				stopTimer.stop();
			}
		});
	}

	private void populateRec(RecommendedPrices rp, FlipItem flip)
	{
		if (rp == null || !rp.hasPrices())
		{
			recBuyValue.setText("fetching…");
			recBuyValue.setForeground(SUBTLE);
			recSellValue.setText("fetching…");
			recSellValue.setForeground(SUBTLE);
			recProfitValue.setText("fetching…");
			recProfitValue.setForeground(SUBTLE);
			taxValue.setText("—");
			taxValue.setForeground(SUBTLE);
			sampleValue.setText("—");
			sampleValue.setForeground(SUBTLE);
			// If FlipItem already carries rec_* values from the bundled response,
			// surface those as a partial fallback.
			if (flip.recBuyPrice != null && flip.recSellPrice != null)
			{
				recBuyValue.setText(FlipItemPanel.formatGp(flip.recBuyPrice) + " gp");
				recBuyValue.setForeground(RED);
				recSellValue.setText(FlipItemPanel.formatGp(flip.recSellPrice) + " gp");
				recSellValue.setForeground(GREEN);
				if (flip.recProfit != null)
				{
					recProfitValue.setText("+" + FlipItemPanel.formatGp(flip.recProfit) + " gp");
					recProfitValue.setForeground(GREEN);
				}
			}
			return;
		}
		recBuyValue.setText(FlipItemPanel.formatGp(rp.recBuyPrice) + " gp");
		recBuyValue.setForeground(RED);
		recSellValue.setText(FlipItemPanel.formatGp(rp.recSellPrice) + " gp");
		recSellValue.setForeground(GREEN);
		recProfitValue.setText("+" + FlipItemPanel.formatGp(rp.recProfit != null ? rp.recProfit : 0) + " gp");
		recProfitValue.setForeground(GREEN);
		taxValue.setText(rp.geTax != null ? FlipItemPanel.formatGp(rp.geTax) + " gp" : "—");
		taxValue.setForeground(Color.LIGHT_GRAY);
		sampleValue.setText(rp.sampleSize != null ? rp.sampleSize + " snapshots" : "—");
		sampleValue.setForeground(SUBTLE);
	}

	private static Color scoreColor(int score)
	{
		if (score >= 70)
		{
			return GREEN;
		}
		if (score >= 40)
		{
			return new Color(0xE8A838);
		}
		return RED;
	}

	private static JLabel section(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(Fonts.SM_BOLD);
		l.setForeground(HEADER);
		l.setBorder(new EmptyBorder(0, 0, 4, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JPanel row(String label, String value, Color valueColor)
	{
		JLabel v = new JLabel(value);
		v.setFont(Fonts.SM_BOLD);
		v.setForeground(valueColor);
		v.setHorizontalAlignment(SwingConstants.RIGHT);
		return rowWithLabel(label, v);
	}

	private static JPanel rowWithLabel(String label, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(BG);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

		JLabel l = new JLabel(label);
		l.setFont(Fonts.SM);
		l.setForeground(SUBTLE);
		row.add(l,     BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		return row;
	}

	private static JLabel blankValue()
	{
		JLabel l = new JLabel("—");
		l.setFont(Fonts.SM_BOLD);
		l.setForeground(SUBTLE);
		l.setHorizontalAlignment(SwingConstants.RIGHT);
		return l;
	}

	private static JButton pillButton(String label)
	{
		JButton b = new JButton(label);
		b.setFont(Fonts.SM);
		b.setBackground(ROW_BG);
		b.setForeground(Color.WHITE);
		b.setFocusable(false);
		b.setBorder(new EmptyBorder(4, 14, 4, 14));
		return b;
	}
}
