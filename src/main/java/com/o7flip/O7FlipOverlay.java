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
package com.o7flip;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;

public class O7FlipOverlay extends Overlay
{
	private static final Color HIGHLIGHT_FILL   = new Color(255, 215, 0, 80);
	private static final Color HIGHLIGHT_BORDER = new Color(255, 215, 0, 200);

	private static final Color QUEUE_HINT_FILL   = new Color(0, 200, 255, 60);
	private static final Color QUEUE_HINT_BORDER = new Color(0, 200, 255, 200);

	private final Client client;
	private final O7FlipPlugin plugin;

	@Inject
	public O7FlipOverlay(Client client, O7FlipPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.getConfig().showInGameOverlays())
		{
			return null;
		}

		if (plugin.hasOverlayQueue())
		{
			renderEmptySlotHints(graphics);
		}

		if (plugin.pendingGeInputPrice != -1 && plugin.getConfig().showGePriceHint()
			&& plugin.getConfig().autoFillGePrice())
		{
			renderEnterPriceHighlight(graphics);
		}

		if (plugin.confirmHighlightUntilMs > System.currentTimeMillis()
			&& plugin.getConfig().showGePriceHint())
		{
			renderConfirmHighlight(graphics);
		}

		return null;
	}

	private void renderConfirmHighlight(Graphics2D graphics)
	{
		Widget geSetup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (geSetup == null || geSetup.isHidden())
		{
			plugin.confirmHighlightUntilMs = 0L;
			return;
		}
		Widget[] children = geSetup.getDynamicChildren();
		if (children == null)
		{
			return;
		}
		for (Widget w : children)
		{
			String[] actions = w.getActions();
			if (actions == null)
			{
				continue;
			}
			for (String action : actions)
			{
				if (action != null && action.toLowerCase().contains("confirm"))
				{
					Rectangle bounds = w.getBounds();
					graphics.setColor(HIGHLIGHT_FILL);
					graphics.fill(bounds);
					graphics.setColor(HIGHLIGHT_BORDER);
					graphics.draw(bounds);
					return;
				}
			}
		}
	}

	private void renderEnterPriceHighlight(Graphics2D graphics)
	{
		Widget geSetup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (geSetup == null || geSetup.isHidden())
		{
			plugin.pendingGeInputPrice = -1;
			return;
		}

		Widget target = findCustomPriceButton(geSetup);
		if (target == null)
		{
			dumpSetupActionsOnce(geSetup);
			return;
		}
		Rectangle bounds = target.getBounds();
		if (bounds == null)
		{
			return;
		}
		graphics.setColor(HIGHLIGHT_FILL);
		graphics.fill(bounds);
		graphics.setColor(HIGHLIGHT_BORDER);
		graphics.draw(bounds);
	}

	private static Widget findCustomPriceButton(Widget parent)
	{
		if (parent == null) return null;
		if (hasCustomPriceAction(parent)) return parent;
		Widget[] dyn = parent.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn)
			{
				if (c == null || c.isHidden()) continue;
				Widget found = findCustomPriceButton(c);
				if (found != null) return found;
			}
		}
		Widget[] stat = parent.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat)
			{
				if (c == null || c.isHidden()) continue;
				Widget found = findCustomPriceButton(c);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static boolean hasCustomPriceAction(Widget w)
	{
		String[] actions = w.getActions();
		if (actions == null) return false;
		for (String a : actions)
		{
			if (a == null) continue;
			String lower = a.toLowerCase();
			if (lower.equals("enter price")
				|| lower.contains("custom price")
				|| lower.equals("set price"))
			{
				return true;
			}
		}
		return false;
	}

	private static int dumpCount = 0;
	private static void dumpSetupActionsOnce(Widget root)
	{
		if (dumpCount++ >= 4 || root == null) return;
		org.slf4j.LoggerFactory.getLogger("com.o7flip.O7FlipOverlay").debug(
			"[07Flip] custom-price button NOT found — dumping every action in the setup widget tree:");
		dumpActionsRecursive(root, "");
	}

	private static void dumpActionsRecursive(Widget w, String indent)
	{
		if (w == null) return;
		String[] acts = w.getActions();
		if (acts != null)
		{
			java.util.List<String> nonEmpty = new java.util.ArrayList<>();
			for (String a : acts) if (a != null && !a.isEmpty()) nonEmpty.add(a);
			if (!nonEmpty.isEmpty())
			{
				org.slf4j.LoggerFactory.getLogger("com.o7flip.O7FlipOverlay").debug(
					"[07Flip] {}actions={}", indent, nonEmpty);
			}
		}
		Widget[] dyn = w.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn) dumpActionsRecursive(c, indent + "  ");
		}
		Widget[] stat = w.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat) dumpActionsRecursive(c, indent + "  ");
		}
	}

	private void renderEmptySlotHints(Graphics2D graphics)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup != null && !setup.isHidden())
		{
			return;
		}
		Widget firstSlot = client.getWidget(InterfaceID.GeOffers.INDEX_0);
		if (firstSlot == null || firstSlot.isHidden())
		{
			return;
		}

		boolean wantBuy = plugin.overlayQueueIsBuy();

		Map<Integer, com.o7flip.model.Models.ActiveOfferSnapshot> offers = plugin.activeOffers;
		int baseId = InterfaceID.GeOffers.INDEX_0;
		for (int i = 0; i < 8; i++)
		{
			if (offers.containsKey(i))
			{
				continue;
			}
			Widget slot = client.getWidget(baseId + i);
			if (slot == null || slot.isHidden())
			{
				continue;
			}
			Widget btn = pickDirectionalButton(slot, wantBuy);
			if (btn == null) continue;
			Rectangle bounds = btn.getBounds();
			if (bounds == null) continue;
			graphics.setColor(QUEUE_HINT_FILL);
			graphics.fill(bounds);
			graphics.setColor(QUEUE_HINT_BORDER);
			graphics.draw(bounds);
		}
	}

	private static Widget pickDirectionalButton(Widget slot, boolean wantBuy)
	{
		java.util.List<Widget> clickable = new java.util.ArrayList<>();
		collectClickable(slot, clickable);

		String wantWord  = wantBuy ? "buy"  : "sell";
		String otherWord = wantBuy ? "sell" : "buy";
		Widget keywordHit = null;
		int keywordHitArea = 0;
		for (Widget c : clickable)
		{
			if (!actionMatchesDirection(c, wantWord, otherWord))
			{
				continue;
			}
			Rectangle b = c.getBounds();
			int area = b == null ? 0 : b.width * b.height;
			if (keywordHit == null || area > keywordHitArea)
			{
				keywordHit = c;
				keywordHitArea = area;
			}
		}
		if (keywordHit != null)
		{
			logPick("keyword", wantBuy, keywordHit, clickable);
			return keywordHit;
		}

		java.util.List<Widget> withSprite = new java.util.ArrayList<>();
		for (Widget c : clickable)
		{
			if (c.getSpriteId() > 0)
			{
				withSprite.add(c);
			}
		}
		java.util.List<Widget> bySize = withSprite.isEmpty() ? clickable : withSprite;
		if (bySize.size() < 2)
		{
			return null;
		}
		bySize.sort((a, b) ->
		{
			Rectangle ra = a.getBounds(), rb = b.getBounds();
			int ax = ra == null ? 0 : ra.x;
			int bx = rb == null ? 0 : rb.x;
			return Integer.compare(ax, bx);
		});
		Widget posHit = wantBuy ? bySize.get(0) : bySize.get(bySize.size() - 1);
		logPick(withSprite.isEmpty() ? "position-fallback" : "position-sprite", wantBuy, posHit, bySize);
		return posHit;
	}

	private static boolean actionMatchesDirection(Widget w, String wantWord, String otherWord)
	{
		String[] actions = w.getActions();
		if (actions == null || actions.length == 0) return false;
		String primary = actions[0];
		if (primary == null || primary.isEmpty()) return false;
		String lower = primary.toLowerCase();
		return lower.contains(wantWord) && !lower.contains(otherWord);
	}

	private static String lastPickKey = "";

	private static void logPick(String tier, boolean wantBuy, Widget chosen, java.util.List<Widget> all)
	{
		Rectangle bounds = chosen.getBounds();
		String boundsStr = bounds == null ? "null" : (bounds.x + "," + bounds.y);
		String key = tier + "|" + wantBuy + "|" + boundsStr;
		if (key.equals(lastPickKey)) return;
		lastPickKey = key;
		org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("com.o7flip.O7FlipOverlay");
		log.debug("[07Flip] pickDirectionalButton[{}]: wantBuy={}, chosen spriteId={} bounds={} actions={}",
			tier, wantBuy, chosen.getSpriteId(), chosen.getBounds(),
			chosen.getActions() == null ? "(none)" : java.util.Arrays.toString(chosen.getActions()));
		for (int i = 0; i < all.size(); i++)
		{
			Widget c = all.get(i);
			log.debug("[07Flip]   candidate[{}] spriteId={} itemId={} bounds={} actions={}",
				i, c.getSpriteId(), c.getItemId(), c.getBounds(),
				c.getActions() == null ? "(none)" : java.util.Arrays.toString(c.getActions()));
		}
	}

	private static void collectClickable(Widget w, java.util.List<Widget> out)
	{
		if (w == null || w.isHidden()) return;
		Widget[] dyn = w.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn)
			{
				if (c == null || c.isHidden()) continue;
				if (isIconSizedButton(c)) out.add(c);
				collectClickable(c, out);
			}
		}
		Widget[] stat = w.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat)
			{
				if (c == null || c.isHidden()) continue;
				if (isIconSizedButton(c)) out.add(c);
				collectClickable(c, out);
			}
		}
	}

	private static boolean isIconSizedButton(Widget w)
	{
		String[] actions = w.getActions();
		if (actions == null) return false;
		boolean hasAction = false;
		for (String a : actions)
		{
			if (a != null && !a.isEmpty()) { hasAction = true; break; }
		}
		if (!hasAction) return false;
		Rectangle b = w.getBounds();
		if (b == null) return false;
		return b.width > 8 && b.height > 8 && b.width < 80 && b.height < 80;
	}

}
