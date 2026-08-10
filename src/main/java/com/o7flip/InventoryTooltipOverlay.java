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

import com.o7flip.model.Models.TrackedItemData;
import com.o7flip.ui.FlipItemPanel;
import com.o7flip.util.ProfitCalculator;
import java.awt.Color;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Singleton
public class InventoryTooltipOverlay extends WidgetItemOverlay
{
	private static final Color PROFIT_COLOR_HEX = new Color(0x00C27A);
	private static final Color LOSS_COLOR_HEX   = new Color(0xE85050);

	private final Client client;
	private final O7FlipPlugin plugin;
	private final TooltipManager tooltipManager;

	@Inject
	public InventoryTooltipOverlay(Client client, O7FlipPlugin plugin, TooltipManager tooltipManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.tooltipManager = tooltipManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(java.awt.Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!plugin.getConfig().showInventoryTooltip())
		{
			return;
		}
		Rectangle slot = widgetItem.getCanvasBounds();
		if (slot == null)
		{
			return;
		}
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || !slot.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}

		String tooltip = buildTooltip(itemId);
		if (tooltip != null)
		{
			tooltipManager.add(new Tooltip(tooltip));
		}
	}

	private String buildTooltip(int itemId)
	{
		TrackedItemData tracked = plugin.trackedItems.get(itemId);
		Long unitCostBasis = openPositionUnitCost(itemId);
		if (tracked == null && unitCostBasis == null)
		{
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<col=ffaa00>07Flip</col>");

		if (tracked != null && tracked.flipSellPrice != null)
		{
			sb.append("</br>Sell @ ").append(FlipItemPanel.formatGp(tracked.flipSellPrice)).append(" gp");
		}
		else if (tracked != null && tracked.alertSellTarget != null)
		{
			sb.append("</br>Target @ ").append(FlipItemPanel.formatGp(tracked.alertSellTarget)).append(" gp");
		}

		if (unitCostBasis != null)
		{
			sb.append("</br>Cost basis ").append(FlipItemPanel.formatGp(unitCostBasis)).append(" gp");

			Long compareTo = (tracked != null && tracked.flipSellPrice != null)
				? tracked.flipSellPrice
				: (tracked != null ? tracked.alertSellTarget : null);
			if (compareTo != null && unitCostBasis > 0)
			{
				double pct = 100.0 * (compareTo - unitCostBasis) / unitCostBasis;
				String col = pct >= 0 ? colTag(PROFIT_COLOR_HEX) : colTag(LOSS_COLOR_HEX);
				sb.append(String.format("</br><col=%s>ROI %+.1f%%</col>", col, pct));
			}
		}

		return sb.toString();
	}

	private Long openPositionUnitCost(int itemId)
	{
		ProfitCalculator.Result r = ProfitCalculator.compute(plugin.tradeHistory);
		ProfitCalculator.OpenPosition pos = r.openPositions.get(itemId);
		if (pos == null || pos.remainingQty <= 0)
		{
			return null;
		}
		return pos.remainingCostBasis / pos.remainingQty;
	}

	private static String colTag(Color c)
	{
		return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}
