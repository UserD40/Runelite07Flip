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

import com.o7flip.model.TrackedItemData;
import com.o7flip.util.ProfitCalculator;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
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
		// Pass 1 — empty-slot hint when a panel right-click is awaiting a slot pick.
		if (plugin.hasOverlayQueue())
		{
			renderEmptySlotHints(graphics);
		}

		// Pass 2 — yellow highlight on the "Enter price" / custom-price button
		// when an auto-fill is armed (covers both buy and sell setup screens).
		// Suppressed when auto-fill is disabled: the price won't be typed, so
		// pointing the user at the button as if it will is misleading.
		if (plugin.pendingGeInputPrice != -1 && plugin.getConfig().showGePriceHint()
			&& plugin.getConfig().autoFillGePrice())
		{
			renderEnterPriceHighlight(graphics);
		}

		// Pass 4 — yellow highlight on the "Confirm offer" button right after auto-fill.
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

		// Walk static + dynamic children (the custom-price button on the sell
		// screen sits in a different sub-widget tree than the buy screen's
		// "Enter price" button). Match any action whose lowered label looks
		// like a custom-price entry — covers "Enter price", "Set custom price",
		// and any future variants without needing exact strings.
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
			// "Enter price" (buy setup), "Set custom price" / "Custom price"
			// (sell setup variants), all match. Avoid plain "price" so we
			// don't catch the "Price per item:" header.
			if (lower.equals("enter price")
				|| lower.contains("custom price")
				|| lower.equals("set price"))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Diagnostic that fires up to 4 times if we land on a setup screen and
	 * can't find the custom-price button. Logs every action across the widget
	 * tree so we can update the matcher with the real label. Capped low to
	 * avoid log spam once the matcher does work.
	 */
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
		// Only highlight when the GE main view is showing (not the setup screen).
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

		// Only highlight the button matching the queued direction — Buy slot
		// for queued buys, Sell slot for queued sells. Painting both would
		// suggest either is a valid target when only one actually is.
		boolean wantBuy = plugin.overlayQueueIsBuy();

		Map<Integer, com.o7flip.model.ActiveOfferSnapshot> offers = plugin.activeOffers;
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
			// Position-based detection: empty slot has 2 icon buttons laid out
			// left-to-right (Buy on left, Sell on right). Action-label matching
			// was unreliable because OSRS exposes both directions on shared
			// widgets; widget bounds are deterministic.
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

	private static int pickerLogCount = 0;

	/**
	 * Picks the Buy or Sell icon-button inside a GE empty slot.
	 *
	 * Two-tier heuristic, in priority order:
	 * <ol>
	 *   <li><b>Action-keyword match.</b> Find widgets whose action text contains
	 *       the wanted direction's keyword ("buy" / "sell") and not the
	 *       opposite. This is the most reliable — OSRS labels its
	 *       create-offer actions distinctly per button.</li>
	 *   <li><b>Position fallback.</b> If no widget has a directional action
	 *       label (the widget tree exposes only generic "Make-offer" text),
	 *       sort the icon-sized candidates by X and pick leftmost for Buy,
	 *       rightmost for Sell.</li>
	 * </ol>
	 *
	 * Returns null when no candidate matches either path so we don't draw
	 * the highlight on the wrong button — a visible mistake (wrong direction
	 * highlighted) is worse than no hint at all.
	 */
	private static Widget pickDirectionalButton(Widget slot, boolean wantBuy)
	{
		java.util.List<Widget> clickable = new java.util.ArrayList<>();
		collectClickable(slot, clickable);

		// Tier 1: action-keyword match. Prefer a widget whose action text
		// names the direction unambiguously. Tracks which widgets matched so
		// the position fallback can run only when tier 1 finds nothing.
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
			// If multiple widgets carry the direction keyword (e.g. icon +
			// container), prefer the largest visible one — the actual button
			// icon usually has the biggest hitbox among directional matches.
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

		// Tier 2: position fallback over visible icon candidates only.
		//
		// The full clickable list also contains non-icon widgets (invisible
		// anchor children, tooltip-zone overlays, slot frames) whose bounds
		// don't correspond to anything the user can see. Sorting all of them
		// by X and grabbing the extreme has been unreliable because those
		// invisible widgets land at unexpected coordinates and push the real
		// Buy / Sell icons out of the leftmost / rightmost slots.
		//
		// Filter to widgets that actually render an icon — those have a
		// non-default spriteId (i.e. > -1). Visually leftmost of THAT subset
		// is the Buy icon in OSRS's empty-slot layout.
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

	/**
	 * True when widget {@code w}'s primary (first / left-click) action contains
	 * {@code wantWord} (case-insensitive) and not {@code otherWord}. Checking
	 * the primary action only — not the whole menu — is more discriminating
	 * because OSRS slot buttons often expose every related option (Buy / Sell /
	 * Cancel / Examine) as menu siblings, while their primary left-click action
	 * is direction-specific.
	 */
	private static boolean actionMatchesDirection(Widget w, String wantWord, String otherWord)
	{
		String[] actions = w.getActions();
		if (actions == null || actions.length == 0) return false;
		String primary = actions[0];
		if (primary == null || primary.isEmpty()) return false;
		String lower = primary.toLowerCase();
		return lower.contains(wantWord) && !lower.contains(otherWord);
	}

	/**
	 * Diagnostic. Logs each pick decision so we can verify (or debug) which
	 * tier fired and what the candidate set looked like. Deduped per chosen
	 * widget bounds — same widget chosen repeatedly across frames stays quiet,
	 * but a DIFFERENT widget (different bounds = different slot) emits a new
	 * log line so we can see every slot's pick independently.
	 */
	private static String lastPickKey = "";

	private static void logPick(String tier, boolean wantBuy, Widget chosen, java.util.List<Widget> all)
	{
		Rectangle bounds = chosen.getBounds();
		String boundsStr = bounds == null ? "null" : (bounds.x + "," + bounds.y);
		String key = tier + "|" + wantBuy + "|" + boundsStr;
		if (key.equals(lastPickKey)) return;
		lastPickKey = key;
		pickerLogCount++;
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

	/**
	 * Icon-button heuristic: has at least one non-empty action AND a roughly
	 * square / icon-sized bounds (the Buy and Sell icons in OSRS GE are
	 * approx 32×32 px). Excludes the "Empty" label and slot-spanning bg
	 * widgets that share menu options with the buttons.
	 */
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

	@SuppressWarnings("unused")
	private static int collectLogCount = 0;

	@SuppressWarnings("unused")
	private static java.util.List<Widget> collectCreateOfferButtons(Widget slot, boolean wantBuy)
	{
		// Kept as a reference / fallback. The position-based pickDirectionalButton
		// above is the active path. Action-label matching was unreliable so this
		// helper is no longer called from the render loop.
		java.util.List<Widget> out = new java.util.ArrayList<>(1);
		Widget[] dyn = slot.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn)
			{
				if (c == null || c.isHidden()) continue;
				if (matchesCreateOfferAction(c, wantBuy)) out.add(c);
			}
		}
		Widget[] stat = slot.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat)
			{
				if (c == null || c.isHidden()) continue;
				if (matchesCreateOfferAction(c, wantBuy)) out.add(c);
			}
		}
		return out;
	}

	@SuppressWarnings("unused")
	private static boolean matchesCreateOfferAction(Widget w, boolean wantBuy)
	{
		String[] actions = w.getActions();
		if (actions == null) return false;
		String wanted = wantBuy ? "buy" : "sell";
		for (String a : actions)
		{
			if (a == null) continue;
			String lower = a.toLowerCase();
			if (lower.contains("create") && lower.contains("offer") && lower.contains(wanted))
			{
				return true;
			}
		}
		return false;
	}

}
