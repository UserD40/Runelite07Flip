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
import java.util.Collections;
import java.util.Map;

public class O7FlipOverlay extends Overlay
{
	private static final Color HIGHLIGHT_FILL   = new Color(255, 215, 0, 80);
	private static final Color HIGHLIGHT_BORDER = new Color(255, 215, 0, 200);

	private static final Color GREEN_FILL   = new Color(0, 255, 0,  50);
	private static final Color GREEN_BORDER = new Color(0, 200, 0, 180);
	private static final Color RED_FILL     = new Color(255, 0,  0,  50);
	private static final Color RED_BORDER   = new Color(200, 0,  0, 180);

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
		// Pass 1 — Feature 1: GE slot price colouring
		if (plugin.getConfig().showGePriceColouring())
		{
			renderSlotColouring(graphics);
		}

		// Pass 2 — highlight "Enter price" button when pre-fill pending
		if (plugin.pendingGeInputPrice == -1)
		{
			return null;
		}

		Widget geSetup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (geSetup == null || geSetup.isHidden())
		{
			plugin.pendingGeInputPrice = -1;
			return null;
		}

		Widget[] children = geSetup.getDynamicChildren();
		if (children == null)
		{
			return null;
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
					return null;
				}
			}
		}

		return null;
	}

	private void renderSlotColouring(Graphics2D graphics)
	{
		Map<Integer, GrandExchangeOffer> offers = plugin.activeOffers;
		if (offers.isEmpty())
		{
			return;
		}

		// Check if GE is open — if the INDEX_0 slot widget is null/hidden, GE is closed.
		// Clear stale offer data if so.
		Widget firstSlot = client.getWidget(InterfaceID.GeOffers.INDEX_0);
		if (firstSlot == null || firstSlot.isHidden())
		{
			if (!offers.isEmpty())
			{
				plugin.activeOffers = Collections.emptyMap();
			}
			return;
		}

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
			if (tracked == null)
			{
				continue;
			}

			boolean isBuy = offer.getState() == GrandExchangeOfferState.BUYING
				|| offer.getState() == GrandExchangeOfferState.BOUGHT;

			Long comparePrice;
			if (isBuy)
			{
				comparePrice = tracked.flipBuyPrice != null ? tracked.flipBuyPrice
					: tracked.spikeBuyPrice != null ? tracked.spikeBuyPrice
					: tracked.dipBuyPrice;
			}
			else
			{
				comparePrice = tracked.flipSellPrice;
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
