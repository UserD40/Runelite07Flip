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
import java.awt.GridLayout;
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
 * Customise-top-row picker. Two lists: "Top Row" (max 4) on the left, "In
 * Other" on the right. Items can be moved between lists with the &lt;/&gt;
 * buttons, reordered within Top Row with ▲ / ▼, and (where Swing's drag
 * support cooperates) dragged between lists with the mouse.
 *
 * The bottom-row tabs (Flips · My Trades · Item · Other) aren't shown
 * here — they're fixed and out of scope for this picker.
 */
public class TabOrderDialog extends JDialog
{
	private static final Color ORANGE = new Color(0xFF981F);
	private static final Color BG     = ColorScheme.DARK_GRAY_COLOR;
	private static final Color BG_ALT = ColorScheme.DARKER_GRAY_COLOR;
	private static final int   MAX_TOP = 4;

	/** Marker DataFlavor for inter-list drags so we only accept our own payloads. */
	private static final DataFlavor STRING_LIST_FLAVOR =
		new DataFlavor(String.class, "TabOrderDialog/list-item");

	/**
	 * @param currentTop  the user's current top-row selection (≤ 4 items)
	 * @param pool        all candidates that may live in either list
	 * @param defaultTop  what "Reset" restores
	 * @param onSave      receives the new top-row selection in display order
	 */
	public static void show(Component owner, List<String> currentTop, List<String> pool,
		List<String> defaultTop, Consumer<List<String>> onSave)
	{
		java.awt.Window parent = owner == null ? null : javax.swing.SwingUtilities.getWindowAncestor(owner);
		TabOrderDialog dialog = new TabOrderDialog(parent, currentTop, pool, defaultTop, onSave);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}

	private TabOrderDialog(java.awt.Window parent, List<String> currentTop, List<String> pool,
		List<String> defaultTop, Consumer<List<String>> onSave)
	{
		super(parent, "07Flip — Customise top row", ModalityType.APPLICATION_MODAL);

		DefaultListModel<String> topModel = new DefaultListModel<>();
		DefaultListModel<String> otherModel = new DefaultListModel<>();
		for (String name : currentTop) topModel.addElement(name);
		for (String name : pool)
		{
			if (!currentTop.contains(name)) otherModel.addElement(name);
		}

		JList<String> topList = buildList(topModel);
		JList<String> otherList = buildList(otherModel);

		// Drag-and-drop between lists. The reorder-within behaviour is built
		// into Swing's MOVE action when we wire a TransferHandler that knows
		// how to accept the dragged item; cross-list drops use the same code
		// path because each list's handler removes the dragged item from its
		// source model and inserts into the target.
		topList.setTransferHandler(new TabTransferHandler(topModel, otherModel, true));
		otherList.setTransferHandler(new TabTransferHandler(otherModel, topModel, false));
		topList.setDragEnabled(true);
		otherList.setDragEnabled(true);
		topList.setDropMode(DropMode.INSERT);
		otherList.setDropMode(DropMode.INSERT);

		JScrollPane topScroll = new JScrollPane(topList);
		topScroll.setPreferredSize(new Dimension(150, 200));
		topScroll.setBorder(BorderFactory.createLineBorder(BG_ALT));

		JScrollPane otherScroll = new JScrollPane(otherList);
		otherScroll.setPreferredSize(new Dimension(150, 200));
		otherScroll.setBorder(BorderFactory.createLineBorder(BG_ALT));

		JLabel topHeader = listHeader("Top row (max " + MAX_TOP + ")");
		JLabel otherHeader = listHeader("In Other tab");

		JPanel topCol = new JPanel(new BorderLayout());
		topCol.setBackground(BG);
		topCol.add(topHeader, BorderLayout.NORTH);
		topCol.add(topScroll, BorderLayout.CENTER);

		JPanel otherCol = new JPanel(new BorderLayout());
		otherCol.setBackground(BG);
		otherCol.add(otherHeader, BorderLayout.NORTH);
		otherCol.add(otherScroll, BorderLayout.CENTER);

		// ── Middle button column: ◀ ▶ ▲ ▼ ───────────────────────────────────
		JButton toTop    = arrowButton("◀");
		JButton toOther  = arrowButton("▶");
		JButton moveUp   = arrowButton("▲");
		JButton moveDown = arrowButton("▼");
		toTop.setToolTipText("Move to Top row");
		toOther.setToolTipText("Move to Other");
		moveUp.setToolTipText("Move up within Top row");
		moveDown.setToolTipText("Move down within Top row");

		toTop.addActionListener(ev -> moveBetween(otherList, otherModel, topList, topModel, true));
		toOther.addActionListener(ev -> moveBetween(topList, topModel, otherList, otherModel, false));
		moveUp.addActionListener(ev -> reorder(topList, topModel, -1));
		moveDown.addActionListener(ev -> reorder(topList, topModel, +1));

		JPanel arrowCol = new JPanel();
		arrowCol.setLayout(new BoxLayout(arrowCol, BoxLayout.Y_AXIS));
		arrowCol.setBackground(BG);
		arrowCol.add(Box.createVerticalGlue());
		arrowCol.add(toTop);
		arrowCol.add(Box.createVerticalStrut(4));
		arrowCol.add(toOther);
		arrowCol.add(Box.createVerticalStrut(16));
		arrowCol.add(moveUp);
		arrowCol.add(Box.createVerticalStrut(4));
		arrowCol.add(moveDown);
		arrowCol.add(Box.createVerticalGlue());

		JPanel content = new JPanel(new GridLayout(1, 3, 4, 0));
		content.setBackground(BG);
		content.setBorder(new EmptyBorder(8, 10, 6, 10));
		content.add(topCol);
		content.add(arrowCol);
		content.add(otherCol);

		JLabel hint = new JLabel("Drag items between lists, or use the arrow buttons.");
		hint.setFont(Fonts.SM);
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setBorder(new EmptyBorder(8, 12, 0, 12));

		JButton resetBtn  = pillButton("Reset");
		JButton cancelBtn = pillButton("Cancel");
		JButton saveBtn   = pillButton("Save");
		saveBtn.setBackground(ORANGE);
		saveBtn.setForeground(Color.BLACK);

		resetBtn.addActionListener(ev ->
		{
			topModel.clear();
			otherModel.clear();
			for (String name : defaultTop) topModel.addElement(name);
			for (String name : pool)
			{
				if (!defaultTop.contains(name)) otherModel.addElement(name);
			}
		});
		cancelBtn.addActionListener(ev -> dispose());
		saveBtn.addActionListener(ev ->
		{
			if (topModel.getSize() > MAX_TOP)
			{
				// Defensive: shouldn't happen because we cap moves, but trim
				// any overflow before persisting so the panel layout is sane.
				while (topModel.getSize() > MAX_TOP) topModel.remove(topModel.getSize() - 1);
			}
			List<String> result = new ArrayList<>(topModel.getSize());
			for (int i = 0; i < topModel.getSize(); i++) result.add(topModel.get(i));
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
		root.add(hint,    BorderLayout.NORTH);
		root.add(content, BorderLayout.CENTER);
		root.add(footer,  BorderLayout.SOUTH);

		setContentPane(root);
		pack();
		setResizable(false);
	}

	private static JList<String> buildList(DefaultListModel<String> model)
	{
		JList<String> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setBackground(BG_ALT);
		list.setForeground(Color.WHITE);
		list.setSelectionBackground(ORANGE);
		list.setSelectionForeground(Color.BLACK);
		list.setFont(Fonts.REG);
		list.setFixedCellHeight(24);
		list.setBorder(new EmptyBorder(4, 4, 4, 4));
		return list;
	}

	private static JLabel listHeader(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(Fonts.BOLD);
		l.setForeground(ORANGE);
		l.setBorder(new EmptyBorder(4, 4, 6, 4));
		return l;
	}

	private void moveBetween(JList<String> srcList, DefaultListModel<String> srcModel,
		JList<String> dstList, DefaultListModel<String> dstModel, boolean intoTop)
	{
		int i = srcList.getSelectedIndex();
		if (i < 0) return;
		// Cap the top row at MAX_TOP — extra moves silently no-op so the user
		// can't end up with a 5-item top row.
		if (intoTop && dstModel.getSize() >= MAX_TOP) return;
		String item = srcModel.remove(i);
		dstModel.addElement(item);
		dstList.setSelectedValue(item, true);
	}

	private static void reorder(JList<String> list, DefaultListModel<String> model, int delta)
	{
		int i = list.getSelectedIndex();
		if (i < 0) return;
		int j = i + delta;
		if (j < 0 || j >= model.getSize()) return;
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

	/**
	 * TransferHandler that supports drag-and-drop reorder within a single
	 * list AND drag between two lists. Each list owns one handler instance;
	 * the {@code source} is its own model, and {@code peer} is the other
	 * list's model. The handler enforces the top-row 4-item cap.
	 */
	private static final class TabTransferHandler extends TransferHandler
	{
		private final DefaultListModel<String> source;
		private final DefaultListModel<String> peer;
		private final boolean sourceIsTop;
		private int dragFromIndex = -1;

		TabTransferHandler(DefaultListModel<String> source, DefaultListModel<String> peer, boolean sourceIsTop)
		{
			this.source = source;
			this.peer = peer;
			this.sourceIsTop = sourceIsTop;
		}

		@Override
		public int getSourceActions(JComponent c) { return MOVE; }

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			@SuppressWarnings("unchecked")
			JList<String> list = (JList<String>) c;
			dragFromIndex = list.getSelectedIndex();
			final String item = list.getSelectedValue();
			if (item == null) return null;
			return new Transferable()
			{
				@Override
				public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{STRING_LIST_FLAVOR}; }
				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor) { return STRING_LIST_FLAVOR.equals(flavor); }
				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
				{
					if (!STRING_LIST_FLAVOR.equals(flavor)) throw new UnsupportedFlavorException(flavor);
					return item;
				}
			};
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			if (!support.isDataFlavorSupported(STRING_LIST_FLAVOR)) return false;
			// Enforce the top-row cap: refuse cross-list drops that would
			// push the top row to 5+ entries.
			if (sourceIsTop) return true; // reordering within top is always fine
			if (source.getSize() >= MAX_TOP) return false;
			return true;
		}

		@Override
		public boolean importData(TransferSupport support)
		{
			if (!canImport(support)) return false;
			try
			{
				String item = (String) support.getTransferable().getTransferData(STRING_LIST_FLAVOR);
				JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
				int dropIndex = dl.getIndex();

				// Reorder within the same list — figure this out by checking
				// whether the dragged item is already in our model.
				int existingIdx = source.indexOf(item);
				if (existingIdx >= 0)
				{
					source.remove(existingIdx);
					if (dropIndex > existingIdx) dropIndex--;
					if (dropIndex < 0) dropIndex = 0;
					if (dropIndex > source.getSize()) dropIndex = source.getSize();
					source.add(dropIndex, item);
					return true;
				}

				// Cross-list drop — remove from peer, insert into us.
				int peerIdx = peer.indexOf(item);
				if (peerIdx < 0) return false;
				peer.remove(peerIdx);
				if (dropIndex < 0) dropIndex = 0;
				if (dropIndex > source.getSize()) dropIndex = source.getSize();
				source.add(dropIndex, item);
				return true;
			}
			catch (Exception e)
			{
				return false;
			}
		}

		@Override
		protected void exportDone(JComponent source, Transferable data, int action) { dragFromIndex = -1; }
	}
}
