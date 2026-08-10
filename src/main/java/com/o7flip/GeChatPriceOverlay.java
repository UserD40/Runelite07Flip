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

import com.o7flip.model.Models.ItemInsights;
import com.o7flip.ui.FlipItemPanel;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class GeChatPriceOverlay extends Overlay implements MouseListener
{
	private static final Color CLEAR_BG    = new Color(0xC8302E);
	private static final Color PRICE       = new Color(0x14358C);
	private static final Color PRICE_HOVER = new Color(0x3A6BE6);

	private final Client client;
	private final O7FlipPlugin plugin;
	private final O7FlipConfig config;

	private final List<Zone> zones = new ArrayList<>();
	private Point hover;

	@Inject
	public GeChatPriceOverlay(Client client, O7FlipPlugin plugin, O7FlipConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		zones.clear();

		if (!config.showInGameOverlays())
		{
			return null;
		}

		if (!config.showGeChatPrice())
		{
			return null;
		}
		boolean priceOpen = plugin.isGePriceEntryOpen();
		if (!priceOpen && !plugin.isGeQuantityEntryOpen())
		{
			return null;
		}

		int itemId = plugin.currentGeSetupItemId();
		if (itemId <= 0)
		{
			return null;
		}

		ItemInsights ins = plugin.getOverlayInsights(itemId);
		if (ins == null || ins.current == null)
		{
			return null;
		}

		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (input == null)
		{
			return null;
		}

		boolean sell = plugin.isGeSellSetup();

		graphics.setFont(GpDropOverlay.buildFont(config.geChatFontType(), config.geChatFontSize()));
		FontMetrics fm = graphics.getFontMetrics();
		final int chipH = fm.getHeight();
		Rectangle ib = input.getBounds();

		if (!priceOpen)
		{
			if (!sell && ins.buyLimit > 0)
			{
				drawPriceItem(graphics, fm, "Buy limit: ", FlipItemPanel.formatGp(ins.buyLimit), "",
					ib.x + 4, ib.y - 40 - 2 * chipH, chipH, ins.buyLimit);
			}
			return null;
		}

		long base    = plugin.ladderPrice(itemId, sell, O7FlipConfig.GePriceDefault.SEVEN_FLIP);
		long quick   = plugin.ladderPrice(itemId, sell, O7FlipConfig.GePriceDefault.QUICK);
		long patient = plugin.ladderPrice(itemId, sell, O7FlipConfig.GePriceDefault.PATIENT);
		long market  = plugin.ladderPrice(itemId, sell, O7FlipConfig.GePriceDefault.MARKET);
		if (base <= 0)
		{
			return null;
		}

		List<Chip> chips = new ArrayList<>();
		chips.add(new Chip("07Flip", base));
		chips.add(new Chip("Quick", quick));
		chips.add(new Chip("Patient", patient));
		if (market > 0 && market != base)
		{
			chips.add(new Chip("Market", market));
		}

		final int padX = 6;
		int totalChipW = 0;
		for (Chip c : chips)
		{
			c.label = c.tag + " " + FlipItemPanel.formatGpCompact(c.price);
			c.w = fm.stringWidth(c.label);
			totalChipW += c.w;
		}

		int priceTop = ib.y - 32 - chipH;
		int clearTop = priceTop - chipH - 8;
		if (clearTop < 4)
		{
			clearTop = ib.y + ib.height + 4;
			priceTop = clearTop + chipH + 2;
		}

		String clearLabel = "Clear";
		int clearW = fm.stringWidth(clearLabel) + padX * 2;
		int clearX = ib.x + ib.width - 4 - clearW;
		if (clearX + clearW > client.getCanvasWidth() - 4)
		{
			clearX = client.getCanvasWidth() - clearW - 4;
		}
		graphics.setColor(CLEAR_BG);
		graphics.fillRoundRect(clearX, clearTop, clearW, chipH, 7, 7);
		graphics.setColor(Color.WHITE);
		graphics.drawString(clearLabel, clearX + padX, clearTop + chipH - fm.getDescent());
		zones.add(new Zone(new Rectangle(clearX, clearTop, clearW, chipH), 0, true));

		int n = chips.size();
		int usableW = ib.width - 28;
		int gap = n > 1 && usableW > totalChipW ? (usableW - totalChipW) / (n - 1) : 12;
		int rowW = totalChipW + gap * (n - 1);
		int cx = ib.x + (ib.width - rowW) / 2;
		if (cx < 4)
		{
			cx = 4;
		}
		for (Chip c : chips)
		{
			drawPriceItem(graphics, fm, c.tag + " ", FlipItemPanel.formatGpCompact(c.price), "",
				cx, priceTop, chipH, c.price);
			cx += c.w + gap;
		}

		long lastPrice = sell ? ins.current.sellPrice : ins.current.buyPrice;
		Integer lastAge = sell ? ins.current.sellAgeMinutes : ins.current.buyAgeMinutes;
		if (lastPrice > 0)
		{
			String prefix = sell ? "Last sale " : "Last buy ";
			String value = FlipItemPanel.formatGp(lastPrice);
			String suffix = lastAge != null && lastAge >= 0 ? "  " + lastAge + "m ago" : "";
			int lastLineW = fm.stringWidth(prefix + value + suffix);
			int lx = ib.x + (ib.width - lastLineW) / 2;
			drawPriceItem(graphics, fm, prefix, value, suffix, lx, ib.y + ib.height + 3, chipH, lastPrice);
		}

		return null;
	}

	private void drawPriceItem(Graphics2D g, FontMetrics fm, String prefix, String value, String suffix,
		int x, int top, int chipH, long price)
	{
		int pw = fm.stringWidth(prefix);
		int vw = fm.stringWidth(value);
		int totalW = pw + vw + fm.stringWidth(suffix);
		int baseline = top + chipH - fm.getDescent();
		boolean hov = hover != null && x <= hover.x && hover.x <= x + totalW && top <= hover.y && hover.y <= top + chipH;
		g.setColor(Color.BLACK);
		g.drawString(prefix, x, baseline);
		g.setColor(hov ? PRICE_HOVER : PRICE);
		g.drawString(value, x + pw, baseline);
		if (!suffix.isEmpty())
		{
			g.setColor(Color.BLACK);
			g.drawString(suffix, x + pw + vw, baseline);
		}
		zones.add(new Zone(new Rectangle(x, top, totalW, chipH), price, false));
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (zones.isEmpty() || !javax.swing.SwingUtilities.isLeftMouseButton(e))
		{
			return e;
		}
		Point p = e.getPoint();
		for (Zone z : zones)
		{
			if (z.rect.contains(p))
			{
				if (z.clear)
				{
					plugin.clearPriceInput();
				}
				else
				{
					plugin.fillPriceInputForced(z.price);
				}
				e.consume();
				return e;
			}
		}
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		hover = null;
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		hover = e.getPoint();
		return e;
	}

	private static final class Chip
	{
		final String tag;
		final long price;
		String label;
		int w;

		Chip(String tag, long price)
		{
			this.tag = tag;
			this.price = price;
		}
	}

	private static final class Zone
	{
		final Rectangle rect;
		final long price;
		final boolean clear;

		Zone(Rectangle rect, long price, boolean clear)
		{
			this.rect = rect;
			this.price = price;
			this.clear = clear;
		}
	}
}
