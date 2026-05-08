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

	private static final Color GREEN_FILL   = new Color(0, 255, 0,  50);
	private static final Color GREEN_BORDER = new Color(0, 200, 0, 180);
	private static final Color RED_FILL     = new Color(255, 0,  0,  50);
	private static final Color RED_BORDER   = new Color(200, 0,  0, 180);

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
		// Pass 1 — slot price colouring on existing offers.
		if (plugin.getConfig().showGePriceColouring())
		{
			renderSlotColouring(graphics);
		}

		// Pass 2 — empty-slot hint when a panel right-click is awaiting a slot pick.
		if (plugin.hasOverlayQueue())
		{
			renderEmptySlotHints(graphics);
		}

		// Pass 3 — yellow highlight on the "Enter price" button when an auto-fill is armed.
		if (plugin.pendingGeInputPrice != -1 && plugin.getConfig().showGePriceHint())
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
				if ("Enter price".equals(action))
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

		Map<Integer, GrandExchangeOffer> offers = plugin.activeOffers;
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
			if (!isSlotActionable(slot))
			{
				continue;
			}
			Rectangle bounds = slot.getBounds();
			graphics.setColor(QUEUE_HINT_FILL);
			graphics.fill(bounds);
			graphics.setColor(QUEUE_HINT_BORDER);
			graphics.draw(bounds);
		}
	}

	/**
	 * A GE slot is actionable when one of its children exposes a
	 * "Create ... Offer" menu action — locked F2P slots are visible widgets but
	 * have no such action, so we skip them.
	 */
	private static boolean isSlotActionable(Widget slot)
	{
		if (hasCreateOfferAction(slot)) return true;
		Widget[] dyn = slot.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn)
			{
				if (c == null || c.isHidden()) continue;
				if (hasCreateOfferAction(c)) return true;
			}
		}
		Widget[] stat = slot.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat)
			{
				if (c == null || c.isHidden()) continue;
				if (hasCreateOfferAction(c)) return true;
			}
		}
		return false;
	}

	private static boolean hasCreateOfferAction(Widget w)
	{
		String[] actions = w.getActions();
		if (actions == null) return false;
		for (String a : actions)
		{
			if (a == null) continue;
			String lower = a.toLowerCase();
			if (lower.contains("create") && lower.contains("offer"))
			{
				return true;
			}
		}
		return false;
	}

	private void renderSlotColouring(Graphics2D graphics)
	{
		Map<Integer, GrandExchangeOffer> offers = plugin.activeOffers;
		if (offers.isEmpty())
		{
			return;
		}

		// Don't paint when the main GE view isn't showing. Do NOT wipe activeOffers
		// here — offers persist in-game across UI close/open, and GrandExchangeOfferChanged
		// only fires on state changes, so wiping would leave us empty until the next change
		// and falsely treat occupied slots as free.
		Widget firstSlot = client.getWidget(InterfaceID.GeOffers.INDEX_0);
		if (firstSlot == null || firstSlot.isHidden())
		{
			return;
		}

		// Compute cost-basis once per render (single ProfitCalculator pass) so all
		// 8 slots can share the open-position lookup.
		ProfitCalculator.Result fifo = ProfitCalculator.compute(plugin.tradeHistory);

		// Slot widgets INDEX_0 through INDEX_7 are sequential integers.
		int baseId = InterfaceID.GeOffers.INDEX_0;

		for (Map.Entry<Integer, GrandExchangeOffer> entry : offers.entrySet())
		{
			int slotIndex = entry.getKey();
			if (slotIndex < 0 || slotIndex > 7)
			{
				continue;
			}

			GrandExchangeOffer offer = entry.getValue();
			TrackedItemData tracked = plugin.trackedItems.get(offer.getItemId());

			boolean isBuy = offer.getState() == GrandExchangeOfferState.BUYING
				|| offer.getState() == GrandExchangeOfferState.BOUGHT;

			Long comparePrice = null;
			if (!isBuy)
			{
				// Sells: prefer cost-basis from the user's actual trade history. This
				// turns the slot colour into "is this offer profitable for me?"
				ProfitCalculator.OpenPosition pos = fifo.openPositions.get(offer.getItemId());
				if (pos != null && pos.remainingQty > 0)
				{
					comparePrice = pos.remainingCostBasis / pos.remainingQty;
				}
				else if (tracked != null)
				{
					comparePrice = tracked.flipSellPrice;
				}
			}
			else if (tracked != null)
			{
				comparePrice = tracked.flipBuyPrice != null ? tracked.flipBuyPrice
					: tracked.spikeBuyPrice != null ? tracked.spikeBuyPrice
					: tracked.dipBuyPrice;
			}

			if (comparePrice == null)
			{
				continue;
			}

			boolean isGood = isBuy
				? offer.getPrice() <= comparePrice
				: offer.getPrice() >= comparePrice;

			Widget slotWidget = client.getWidget(baseId + slotIndex);
			if (slotWidget == null || slotWidget.isHidden())
			{
				continue;
			}

			Rectangle bounds = slotWidget.getBounds();
			graphics.setColor(isGood ? GREEN_FILL : RED_FILL);
			graphics.fill(bounds);
			graphics.setColor(isGood ? GREEN_BORDER : RED_BORDER);
			graphics.draw(bounds);
		}
	}
}
