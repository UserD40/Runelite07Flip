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

import com.o7flip.util.Fonts;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * Modal dialog that lets the user reorder the 07Flip panel tabs.
 *
 * Pick a tab in the list, click ▲ / ▼ to move it. Save commits the new
 * order back to the plugin via the {@code onSave} callback. Reset
 * restores the default order. Cancel closes without changes.
 *
 * Tab visibility (showFlips, showDumps, etc.) is controlled separately
 * in plugin config — this dialog is purely about ordering.
 */
public class TabOrderDialog extends JDialog
{
	private static final Color ORANGE = new Color(0xFF981F);
	private static final Color BG     = ColorScheme.DARK_GRAY_COLOR;
	private static final Color BG_ALT = ColorScheme.DARKER_GRAY_COLOR;

	public static void show(Component owner, List<String> currentOrder, List<String> defaultOrder,
		Consumer<List<String>> onSave)
	{
		java.awt.Window parent = owner == null ? null : javax.swing.SwingUtilities.getWindowAncestor(owner);
		TabOrderDialog dialog = new TabOrderDialog(parent, currentOrder, defaultOrder, onSave);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}

	private TabOrderDialog(java.awt.Window parent, List<String> currentOrder, List<String> defaultOrder,
		Consumer<List<String>> onSave)
	{
		super(parent, "07Flip — Tab Order", ModalityType.APPLICATION_MODAL);

		DefaultListModel<String> model = new DefaultListModel<>();
		for (String name : currentOrder)
		{
			model.addElement(name);
		}

		JList<String> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setBackground(BG_ALT);
		list.setForeground(Color.WHITE);
		list.setSelectionBackground(ORANGE);
		list.setSelectionForeground(Color.BLACK);
		list.setFont(Fonts.REG);
		list.setFixedCellHeight(24);
		list.setBorder(new EmptyBorder(4, 4, 4, 4));
		list.setSelectedIndex(0);

		JScrollPane listScroll = new JScrollPane(list);
		listScroll.setPreferredSize(new Dimension(180, 220));
		listScroll.setBorder(BorderFactory.createLineBorder(BG_ALT));

		JButton upBtn = arrowButton("▲ Move up");
		JButton dnBtn = arrowButton("▼ Move down");
		upBtn.addActionListener(ev -> move(list, model, -1));
		dnBtn.addActionListener(ev -> move(list, model, +1));

		JPanel arrowCol = new JPanel();
		arrowCol.setLayout(new BoxLayout(arrowCol, BoxLayout.Y_AXIS));
		arrowCol.setBackground(BG);
		arrowCol.setBorder(new EmptyBorder(0, 6, 0, 0));
		arrowCol.add(Box.createVerticalGlue());
		arrowCol.add(upBtn);
		arrowCol.add(Box.createVerticalStrut(6));
		arrowCol.add(dnBtn);
		arrowCol.add(Box.createVerticalGlue());

		JPanel content = new JPanel(new BorderLayout());
		content.setBackground(BG);
		content.setBorder(new EmptyBorder(10, 10, 8, 10));
		JLabel hint = new JLabel("Pick a tab and use the arrows to move it.");
		hint.setFont(Fonts.SM);
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setBorder(new EmptyBorder(0, 0, 8, 0));
		content.add(hint,       BorderLayout.NORTH);
		content.add(listScroll, BorderLayout.CENTER);
		content.add(arrowCol,   BorderLayout.EAST);

		JButton resetBtn  = pillButton("Reset");
		JButton cancelBtn = pillButton("Cancel");
		JButton saveBtn   = pillButton("Save");
		saveBtn.setBackground(ORANGE);
		saveBtn.setForeground(Color.BLACK);

		resetBtn.addActionListener(ev ->
		{
			model.clear();
			for (String name : defaultOrder)
			{
				model.addElement(name);
			}
			list.setSelectedIndex(0);
		});
		cancelBtn.addActionListener(ev -> dispose());
		saveBtn.addActionListener(ev ->
		{
			List<String> result = new ArrayList<>(model.getSize());
			for (int i = 0; i < model.getSize(); i++)
			{
				result.add(model.get(i));
			}
			onSave.accept(result);
			dispose();
		});

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		footer.setBackground(BG);
		footer.setBorder(new EmptyBorder(8, 10, 10, 10));
		footer.add(resetBtn);
		footer.add(Box.createHorizontalStrut(12));
		footer.add(cancelBtn);
		footer.add(saveBtn);

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);
		root.add(content, BorderLayout.CENTER);
		root.add(footer,  BorderLayout.SOUTH);

		setContentPane(root);
		pack();
		setResizable(false);
	}

	private static void move(JList<String> list, DefaultListModel<String> model, int delta)
	{
		int i = list.getSelectedIndex();
		if (i < 0)
		{
			return;
		}
		int j = i + delta;
		if (j < 0 || j >= model.getSize())
		{
			return;
		}
		String item = model.remove(i);
		model.add(j, item);
		list.setSelectedIndex(j);
		list.ensureIndexIsVisible(j);
	}

	private static JButton arrowButton(String label)
	{
		JButton b = new JButton(label);
		b.setFont(Fonts.SM);
		b.setBackground(BG_ALT);
		b.setForeground(Color.WHITE);
		b.setFocusable(false);
		b.setAlignmentX(Component.CENTER_ALIGNMENT);
		b.setMaximumSize(new Dimension(120, 28));
		b.setHorizontalAlignment(SwingConstants.CENTER);
		return b;
	}

	private static JButton pillButton(String label)
	{
		JButton b = new JButton(label);
		b.setFont(Fonts.SM);
		b.setBackground(BG_ALT);
		b.setForeground(Color.WHITE);
		b.setFocusable(false);
		b.setBorder(new EmptyBorder(4, 14, 4, 14));
		return b;
	}
}
