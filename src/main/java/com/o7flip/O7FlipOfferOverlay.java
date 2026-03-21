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
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.o7flip;

import com.o7flip.model.TrackedItemData;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class O7FlipOfferOverlay extends Overlay
{
	private static final int   PADDING      = 6;
	private static final int   LINE_HEIGHT  = 13;
	private static final Color BG_COLOR     = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color BORDER_COLOR = ColorScheme.DARK_GRAY_COLOR;
	private static final Color LABEL_COLOR  = Color.GRAY;
	private static final Color VALUE_COLOR  = Color.WHITE;
	private static final Color POS_COLOR    = new Color(0, 200, 0, 255);
	private static final Color NEG_COLOR    = new Color(200, 0, 0, 255);

	private final Client client;
	private final O7FlipConfig config;

	/** Set by O7FlipPlugin.onScriptPostFired. Cleared in render() when GE hides. */
	volatile TrackedItemData activeOfferData;

	@Inject
	public O7FlipOfferOverlay(Client client, O7FlipConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showGeOfferOverlay())
		{
			return null;
		}

		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			activeOfferData = null;
			return null;
		}

		TrackedItemData data = activeOfferData;
		if (data == null)
		{
			return null;
		}

		List<String[]> rows = buildRows(data);
		if (rows.isEmpty())
		{
			return null;
		}

		Rectangle setupBounds = setup.getBounds();
		int cardX = setupBounds.x;
		int cardW = setupBounds.width;
		int cardH = PADDING + rows.size() * LINE_HEIGHT + PADDING;
		int cardY = setupBounds.y + setupBounds.height + 2;

		graphics.setColor(BG_COLOR);
		graphics.fillRect(cardX, cardY, cardW, cardH);
		graphics.setColor(BORDER_COLOR);
		graphics.drawRect(cardX, cardY, cardW, cardH);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();
		int y = cardY + PADDING + fm.getAscent();

		for (String[] row : rows)
		{
			// row[0] = label, row[1] = value (may be null), row[2] = color hint ("pos"/"neg"/"")
			graphics.setColor(LABEL_COLOR);
			graphics.drawString(row[0], cardX + PADDING, y);
			if (row.length > 1 && row[1] != null)
			{
				Color valueColor = VALUE_COLOR;
				if (row.length > 2)
				{
					if ("pos".equals(row[2]))
					{
						valueColor = POS_COLOR;
					}
					else if ("neg".equals(row[2]))
					{
						valueColor = NEG_COLOR;
					}
				}
				int labelW = fm.stringWidth(row[0]);
				graphics.setColor(valueColor);
				graphics.drawString(row[1], cardX + PADDING + labelW + 4, y);
			}
			y += LINE_HEIGHT;
		}

		return null;
	}

	private List<String[]> buildRows(TrackedItemData data)
	{
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[]{data.name, null, ""});

		for (String tab : data.presentIn)
		{
			switch (tab)
			{
				case "Flips":
					if (data.flipBuyPrice != null)
					{
						rows.add(new String[]{"Buy: ", fmt(data.flipBuyPrice) + " gp", ""});
					}
					if (data.flipSellPrice != null)
					{
						rows.add(new String[]{"Sell: ", fmt(data.flipSellPrice) + " gp", ""});
					}
					if (data.flipProfit != null)
					{
						rows.add(new String[]{"Profit: ", fmt(data.flipProfit) + " gp",
							data.flipProfit >= 0 ? "pos" : "neg"});
					}
					if (data.flipRoiPct != null)
					{
						rows.add(new String[]{"ROI: ", String.format("%.2f%%", data.flipRoiPct),
							data.flipRoiPct >= 0 ? "pos" : "neg"});
					}
					break;
				case "Alerts":
					if (data.alertCurrentPrice != null)
					{
						rows.add(new String[]{"Alert price: ", fmt(data.alertCurrentPrice) + " gp", ""});
					}
					if (data.alertSellTarget != null)
					{
						rows.add(new String[]{"Target: ", fmt(data.alertSellTarget) + " gp", "pos"});
					}
					if (data.alertUpsidePct != null)
					{
						rows.add(new String[]{"Upside: ",
							String.format("+%.1f%%", data.alertUpsidePct), "pos"});
					}
					break;
				case "Dips":
					if (data.dipBuyPrice != null)
					{
						rows.add(new String[]{"Dip price: ", fmt(data.dipBuyPrice) + " gp", ""});
					}
					if (data.dipPct != null)
					{
						rows.add(new String[]{"Dip: ",
							String.format("-%.1f%%", data.dipPct), "neg"});
					}
					break;
				case "Dumps":
					if (data.dumpBuyPrice != null)
					{
						rows.add(new String[]{"Dump buy: ", fmt(data.dumpBuyPrice) + " gp", ""});
					}
					if (data.dumpSellPrice != null)
					{
						rows.add(new String[]{"Dump sell: ", fmt(data.dumpSellPrice) + " gp", ""});
					}
					if (data.dumpPct != null)
					{
						rows.add(new String[]{"Drop: ",
							String.format("-%.1f%%", data.dumpPct), "neg"});
					}
					break;
				case "Spikes":
					if (data.spikeBuyPrice != null)
					{
						rows.add(new String[]{"Spike: ", fmt(data.spikeBuyPrice) + " gp", ""});
					}
					break;
				default:
					break;
			}
		}

		rows.add(new String[]{"07flip.com", null, ""});
		return rows;
	}

	private static String fmt(long value)
	{
		return String.format("%,d", value);
	}
}
