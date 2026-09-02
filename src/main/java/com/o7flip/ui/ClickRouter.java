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

public final class ClickRouter
{
	private static final int DOUBLE_CLICK_DELAY_MS = 250;

	private ClickRouter()
	{
	}

	public static final String CLICK_HINT = "Click for insights · Shift+click or double-click to open on 07flip.com";

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
		attachClickOnly(component, plugin, itemId, fallbackName);
	}

	public static void attachClickOnly(Component component, O7FlipPlugin plugin, int itemId, String fallbackName)
	{
		if (component == null || plugin == null || itemId <= 0)
		{
			return;
		}
		for (java.awt.event.MouseListener l : component.getMouseListeners())
		{
			if (l instanceof Route)
			{
				component.removeMouseListener(l);
			}
		}
		component.addMouseListener(new Route(plugin, itemId, fallbackName));
	}

	private static final class Route extends MouseAdapter
	{
		private final O7FlipPlugin plugin;
		private final int itemId;
		private final String fallbackName;
		private Timer pendingSingle;

		Route(O7FlipPlugin plugin, int itemId, String fallbackName)
		{
			this.plugin = plugin;
			this.itemId = itemId;
			this.fallbackName = fallbackName;
		}

		@Override
		public void mouseClicked(MouseEvent e)
		{
			if (e.isShiftDown())
			{
				cancelSingle();
				net.runelite.client.util.LinkBrowser.browse("https://07flip.com/item/" + itemId);
				return;
			}
			if (!SwingUtilities.isLeftMouseButton(e))
			{
				return;
			}
			if (e.getClickCount() == 1)
			{
				cancelSingle();
				pendingSingle = new Timer(DOUBLE_CLICK_DELAY_MS, ev -> plugin.openInsights(itemId, fallbackName));
				pendingSingle.setRepeats(false);
				pendingSingle.start();
			}
			else if (e.getClickCount() == 2)
			{
				cancelSingle();
				net.runelite.client.util.LinkBrowser.browse("https://07flip.com/item/" + itemId);
			}
		}

		private void cancelSingle()
		{
			if (pendingSingle != null)
			{
				pendingSingle.stop();
				pendingSingle = null;
			}
		}
	}

	private static final String NO_ROUTE_PROP = "o7flip.noRoute";

	public static void markNoRoute(javax.swing.JComponent c)
	{
		if (c != null)
		{
			c.putClientProperty(NO_ROUTE_PROP, Boolean.TRUE);
		}
	}

	private static boolean isInteractiveControl(Component c)
	{
		if (c instanceof javax.swing.AbstractButton)
		{
			return true;
		}
		if (c instanceof javax.swing.JComponent)
		{
			return Boolean.TRUE.equals(((javax.swing.JComponent) c).getClientProperty(NO_ROUTE_PROP));
		}
		return false;
	}

	public static void attachToTree(Component root, O7FlipPlugin plugin, int itemId, String fallbackName)
	{
		if (root == null || isInteractiveControl(root)) return;
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
