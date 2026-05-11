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
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Single-click → switch to Insights tab and load the item.
 * Double-click → open the item page on 07flip.com.
 *
 * Swing fires {@code mouseClicked} once per click with a cumulative
 * {@code clickCount}, so the only way to disambiguate the gestures is a
 * short timer that fires the single-click action only if no second click
 * arrives within the platform double-click interval. The trade-off is a
 * ~{@value #DOUBLE_CLICK_DELAY_MS} ms perceptible delay on single-click —
 * acceptable for navigating an analytics tab.
 *
 * Right-click is not handled here — callers attach their own context-menu
 * {@code mousePressed} handler if they want one.
 */
public final class ClickRouter
{
	private static final int DOUBLE_CLICK_DELAY_MS = 250;

	private ClickRouter()
	{
	}

	/**
	 * Wires single + double left-click on the given component for an item id.
	 *
	 * @param component   the row / panel that should respond to clicks
	 * @param plugin      the plugin singleton (used to call openInsights)
	 * @param itemId      the item id to route to on click
	 * @param fallbackName name shown in the loading state before the fetch
	 *                    returns; null if unknown
	 */
	/** Shared tooltip text — applied wherever ClickRouter is wired so the gestures are discoverable. */
	public static final String CLICK_HINT = "Click for insights · Shift+click or double-click to open on 07flip.com";

	/**
	 * Variant for surfaces where double-click → website is undesirable —
	 * single-click triggers Insights immediately (no timer, no debounce).
	 * Shift+click still escapes to the website though, so the gesture is
	 * consistent across the plugin.
	 */
	public static void attachInsightsOnly(Component component, O7FlipPlugin plugin, int itemId, String fallbackName)
	{
		if (component == null || plugin == null || itemId <= 0)
		{
			return;
		}
		applyTooltip(component);
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.isShiftDown())
				{
					net.runelite.client.util.LinkBrowser.browse("https://07flip.com/item/" + itemId);
					return;
				}
				if (SwingUtilities.isLeftMouseButton(e))
				{
					plugin.openInsights(itemId, fallbackName);
				}
			}
		});
	}

	public static void attach(Component component, O7FlipPlugin plugin, int itemId, String fallbackName)
	{
		if (component == null || plugin == null || itemId <= 0)
		{
			return;
		}
		applyTooltip(component);

		final Timer[] pendingSingle = new Timer[]{ null };

		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				// Shift+click (either button) is an explicit "take me to the
				// website" gesture — cancel any pending single-click timer and
				// fire immediately. Right-click panel handlers should also
				// check e.isShiftDown() so the context menu doesn't open too.
				if (e.isShiftDown())
				{
					if (pendingSingle[0] != null)
					{
						pendingSingle[0].stop();
						pendingSingle[0] = null;
					}
					net.runelite.client.util.LinkBrowser.browse("https://07flip.com/item/" + itemId);
					return;
				}
				if (!SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}
				if (e.getClickCount() == 1)
				{
					if (pendingSingle[0] != null)
					{
						pendingSingle[0].stop();
					}
					Timer t = new Timer(DOUBLE_CLICK_DELAY_MS, ev ->
						plugin.openInsights(itemId, fallbackName));
					t.setRepeats(false);
					pendingSingle[0] = t;
					t.start();
				}
				else if (e.getClickCount() == 2)
				{
					if (pendingSingle[0] != null)
					{
						pendingSingle[0].stop();
						pendingSingle[0] = null;
					}
					net.runelite.client.util.LinkBrowser.browse("https://07flip.com/item/" + itemId);
				}
			}
		});
	}

	/**
	 * Attaches the same gesture handling as {@link #attach} but doesn't touch
	 * the component's tooltip. Used for child widgets inside a row (price
	 * labels, score badges) that already have their own informative tooltip —
	 * we want shift+click / double-click to work when the user clicks those
	 * areas, but the existing tooltip should stay.
	 */
	public static void attachClickOnly(Component component, O7FlipPlugin plugin, int itemId, String fallbackName)
	{
		if (component == null || plugin == null || itemId <= 0)
		{
			return;
		}
		final Timer[] pendingSingle = new Timer[]{ null };
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.isShiftDown())
				{
					if (pendingSingle[0] != null)
					{
						pendingSingle[0].stop();
						pendingSingle[0] = null;
					}
					net.runelite.client.util.LinkBrowser.browse("https://07flip.com/item/" + itemId);
					return;
				}
				if (!SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}
				if (e.getClickCount() == 1)
				{
					if (pendingSingle[0] != null)
					{
						pendingSingle[0].stop();
					}
					Timer t = new Timer(DOUBLE_CLICK_DELAY_MS, ev ->
						plugin.openInsights(itemId, fallbackName));
					t.setRepeats(false);
					pendingSingle[0] = t;
					t.start();
				}
				else if (e.getClickCount() == 2)
				{
					if (pendingSingle[0] != null)
					{
						pendingSingle[0].stop();
						pendingSingle[0] = null;
					}
					net.runelite.client.util.LinkBrowser.browse("https://07flip.com/item/" + itemId);
				}
			}
		});
	}

	/**
	 * Walks a container tree and wires {@link #attachClickOnly} to every
	 * component — root + every descendant. Used by the Item Insights panel
	 * so the navigation gestures work no matter where in the panel the user
	 * clicks (sections / labels / sparklines all otherwise capture their own
	 * mouse events and would block a panel-level listener).
	 */
	public static void attachToTree(Component root, O7FlipPlugin plugin, int itemId, String fallbackName)
	{
		if (root == null) return;
		attachClickOnly(root, plugin, itemId, fallbackName);
		if (root instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) root).getComponents())
			{
				attachToTree(child, plugin, itemId, fallbackName);
			}
		}
	}

	private static void applyTooltip(Component component)
	{
		if (component instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) component).setToolTipText(CLICK_HINT);
		}
	}
}
