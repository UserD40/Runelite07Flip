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

import com.o7flip.model.FlipItem;
import com.o7flip.util.Fonts;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * Favourites reorder / remove popup. Shows the user's favourites as a single
 * draggable list — drag a row (or use ▲ / ▼) to reposition it, and Remove to
 * drop it from favourites. On Save the new order (and the implied removals)
 * are handed back to the panel.
 */
public class FavouritesReorderDialog extends JDialog
{
	private static final Color ORANGE = new Color(0xFF981F);
	private static final Color BG     = ColorScheme.DARK_GRAY_COLOR;
	private static final Color BG_ALT = ColorScheme.DARKER_GRAY_COLOR;

	private static final DataFlavor ENTRY_FLAVOR =
		new DataFlavor(Entry.class, "FavouritesReorderDialog/entry");

	/** One favourite row — renders its name, carries its item id. */
	private static final class Entry
	{
		final int itemId;
		final String name;

		Entry(int itemId, String name)
		{
			this.itemId = itemId;
			this.name = name != null ? name : "Item " + itemId;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	/**
	 * @param favourites current favourites in display order
	 * @param onSave     receives the kept item ids in their new order; the
	 *                   panel diffs this against the originals to find removals
	 */
	public static void show(Component owner, List<FlipItem> favourites, Consumer<List<Integer>> onSave)
	{
		java.awt.Window parent = owner == null ? null : javax.swing.SwingUtilities.getWindowAncestor(owner);
		FavouritesReorderDialog dialog = new FavouritesReorderDialog(parent, favourites, onSave);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}

	private FavouritesReorderDialog(java.awt.Window parent, List<FlipItem> favourites, Consumer<List<Integer>> onSave)
	{
		super(parent, "07Flip — Reorder favourites", ModalityType.APPLICATION_MODAL);

		DefaultListModel<Entry> model = new DefaultListModel<>();
		if (favourites != null)
		{
			for (FlipItem f : favourites)
			{
				if (f != null && f.itemId > 0)
				{
					model.addElement(new Entry(f.itemId, f.name));
				}
			}
		}

		JList<Entry> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setBackground(BG_ALT);
		list.setForeground(Color.WHITE);
		list.setSelectionBackground(ORANGE);
		list.setSelectionForeground(Color.BLACK);
		list.setFont(Fonts.REG);
		list.setFixedCellHeight(24);
		list.setBorder(new EmptyBorder(4, 4, 4, 4));
		list.setTransferHandler(new ReorderHandler(model));
		list.setDragEnabled(true);
		list.setDropMode(DropMode.INSERT);

		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(220, 280));
		scroll.setBorder(BorderFactory.createLineBorder(BG_ALT));

		// ▲ ▼ Remove column.
		JButton up     = arrowButton("▲");
		JButton down   = arrowButton("▼");
		JButton remove = arrowButton("Remove");
		remove.setMaximumSize(new Dimension(80, 24));
		remove.setPreferredSize(new Dimension(80, 24));
		remove.setForeground(new Color(0xE85050));
		up.setToolTipText("Move up");
		down.setToolTipText("Move down");
		remove.setToolTipText("Remove from favourites");
		up.addActionListener(ev -> reorder(list, model, -1));
		down.addActionListener(ev -> reorder(list, model, +1));
		remove.addActionListener(ev ->
		{
			int i = list.getSelectedIndex();
			if (i >= 0)
			{
				model.remove(i);
				if (model.getSize() > 0)
				{
					list.setSelectedIndex(Math.min(i, model.getSize() - 1));
				}
			}
		});

		JPanel btnCol = new JPanel();
		btnCol.setLayout(new BoxLayout(btnCol, BoxLayout.Y_AXIS));
		btnCol.setBackground(BG);
		btnCol.add(Box.createVerticalGlue());
		btnCol.add(up);
		btnCol.add(Box.createVerticalStrut(4));
		btnCol.add(down);
		btnCol.add(Box.createVerticalStrut(16));
		btnCol.add(remove);
		btnCol.add(Box.createVerticalGlue());

		JPanel content = new JPanel(new BorderLayout(6, 0));
		content.setBackground(BG);
		content.setBorder(new EmptyBorder(8, 10, 6, 10));
		content.add(scroll, BorderLayout.CENTER);
		content.add(btnCol, BorderLayout.EAST);

		JLabel hint = new JLabel("Drag to reorder, or use ▲ / ▼. Remove drops an item from favourites.");
		hint.setFont(Fonts.SM);
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setBorder(new EmptyBorder(8, 12, 0, 12));

		JButton cancelBtn = pillButton("Cancel");
		JButton saveBtn   = pillButton("Save");
		saveBtn.setBackground(ORANGE);
		saveBtn.setForeground(Color.BLACK);
		cancelBtn.addActionListener(ev -> dispose());
		saveBtn.addActionListener(ev ->
		{
			List<Integer> ordered = new ArrayList<>(model.getSize());
			for (int i = 0; i < model.getSize(); i++)
			{
				ordered.add(model.get(i).itemId);
			}
			onSave.accept(ordered);
			dispose();
		});

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		footer.setBackground(BG);
		footer.setBorder(new EmptyBorder(8, 10, 10, 10));
		footer.add(cancelBtn);
		footer.add(saveBtn);

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);
		root.add(hint,    BorderLayout.NORTH);
		root.add(content, BorderLayout.CENTER);
		root.add(footer,  BorderLayout.SOUTH);

		setContentPane(root);
		pack();
		setResizable(false);
	}

	private static void reorder(JList<Entry> list, DefaultListModel<Entry> model, int delta)
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
		Entry e = model.remove(i);
		model.add(j, e);
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
		b.setMaximumSize(new Dimension(60, 24));
		b.setPreferredSize(new Dimension(50, 24));
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

	/** Single-list drag-to-reorder. */
	private static final class ReorderHandler extends TransferHandler
	{
		private final DefaultListModel<Entry> model;

		ReorderHandler(DefaultListModel<Entry> model)
		{
			this.model = model;
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return MOVE;
		}

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			@SuppressWarnings("unchecked")
			JList<Entry> list = (JList<Entry>) c;
			final Entry item = list.getSelectedValue();
			if (item == null)
			{
				return null;
			}
			return new Transferable()
			{
				@Override
				public DataFlavor[] getTransferDataFlavors()
				{
					return new DataFlavor[]{ENTRY_FLAVOR};
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor)
				{
					return ENTRY_FLAVOR.equals(flavor);
				}

				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
				{
					if (!ENTRY_FLAVOR.equals(flavor))
					{
						throw new UnsupportedFlavorException(flavor);
					}
					return item;
				}
			};
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			return support.isDataFlavorSupported(ENTRY_FLAVOR);
		}

		@Override
		public boolean importData(TransferSupport support)
		{
			if (!canImport(support))
			{
				return false;
			}
			try
			{
				Entry item = (Entry) support.getTransferable().getTransferData(ENTRY_FLAVOR);
				JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
				int dropIndex = dl.getIndex();
				int existing = model.indexOf(item);
				if (existing < 0)
				{
					return false;
				}
				model.remove(existing);
				if (dropIndex > existing)
				{
					dropIndex--;
				}
				if (dropIndex < 0)
				{
					dropIndex = 0;
				}
				if (dropIndex > model.getSize())
				{
					dropIndex = model.getSize();
				}
				model.add(dropIndex, item);
				return true;
			}
			catch (Exception e)
			{
				return false;
			}
		}
	}
}
