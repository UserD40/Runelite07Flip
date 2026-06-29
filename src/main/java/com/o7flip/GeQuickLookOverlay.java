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

import com.o7flip.model.ActiveOfferSnapshot;
import com.o7flip.model.ItemInsights;
import com.o7flip.ui.FlipItemPanel;
import com.o7flip.ui.MiniChart;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

public class GeQuickLookOverlay extends Overlay
{
	private static final Color GOOD       = new Color(0x00C27A);
	private static final Color BAD        = new Color(0xE85050);
	private static final Color NEUTRAL    = new Color(0xC0A050);
	private static final Color HEADER     = new Color(0xFFD700);
	private static final Color INFO       = new Color(0xC0C0C0);
	private static final Color TOOLTIP_BG = new Color(15, 15, 15, 235);
	private static final Color MAG_LENS   = new Color(0xEFB83C);
	private static final int   GLYPH      = 16;
	private static final int   ICON_FILL_ALPHA  = 50;
	private static final int   ICON_FRAME_ALPHA = 210;
	private static final int   CHART_W    = 172;
	private static final int   CHART_H    = 32;

	private final Client client;
	private final O7FlipPlugin plugin;
	private final O7FlipConfig config;
	private final PanelComponent panel = new PanelComponent();

	@Inject
	public GeQuickLookOverlay(Client client, O7FlipPlugin plugin, O7FlipConfig config)
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
		if (!config.showGeQuickLook())
		{
			return null;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup != null && !setup.isHidden())
		{
			return null;
		}
		Widget firstSlot = client.getWidget(InterfaceID.GeOffers.INDEX_0);
		if (firstSlot == null || firstSlot.isHidden())
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final Point mouse = client.getMouseCanvasPosition();
		final int baseId = InterfaceID.GeOffers.INDEX_0;

		int hoveredIdx = -1;
		if (mouse != null)
		{
			for (int i = 0; i < 8; i++)
			{
				if (!isActive(i))
				{
					continue;
				}
				Widget slot = client.getWidget(baseId + i);
				if (slot == null || slot.isHidden())
				{
					continue;
				}
				Rectangle b = slot.getBounds();
				if (b != null && b.contains(mouse.getX(), mouse.getY()))
				{
					hoveredIdx = i;
					break;
				}
			}
		}
		final boolean tooltipUp = hoveredIdx >= 0;

		ActiveOfferSnapshot hoveredSnap = null;
		Rectangle hoveredBounds = null;
		Verdict hoveredVerdict = null;

		for (int i = 0; i < 8; i++)
		{
			ActiveOfferSnapshot snap = plugin.activeOffers.get(i);
			if (snap == null
				|| (snap.state != GrandExchangeOfferState.BUYING
					&& snap.state != GrandExchangeOfferState.SELLING))
			{
				continue;
			}
			Widget slot = client.getWidget(baseId + i);
			if (slot == null || slot.isHidden())
			{
				continue;
			}
			Rectangle b = slot.getBounds();
			if (b == null)
			{
				continue;
			}
			Verdict v = evaluate(snap);
			Color c = v == null ? NEUTRAL : (v.competitive ? GOOD : BAD);

			Widget icon = findItemIcon(slot);
			if (icon != null && !icon.isHidden())
			{
				Rectangle ib = icon.getBounds();
				if (ib != null)
				{
					int x = ib.x - 1, y = ib.y - 1, w = ib.width + 2, h = ib.height + 2;
					graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), ICON_FILL_ALPHA));
					graphics.fillRoundRect(x, y, w, h, 7, 7);
					Stroke prevS = graphics.getStroke();
					graphics.setStroke(new BasicStroke(2f));
					graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), ICON_FRAME_ALPHA));
					graphics.drawRoundRect(x, y, w, h, 8, 8);
					graphics.setStroke(prevS);
				}
			}

			Stroke old = graphics.getStroke();
			graphics.setStroke(new BasicStroke(2.5f));
			graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
			graphics.drawRoundRect(b.x + 1, b.y + 1, b.width - 3, b.height - 3, 14, 14);
			graphics.setStroke(old);

			if (!tooltipUp)
			{
				Rectangle glyph = new Rectangle(b.x + b.width - GLYPH - 3, b.y + b.height - GLYPH - 3, GLYPH, GLYPH);
				drawMagnifier(graphics, glyph);
			}

			if (i == hoveredIdx && v != null)
			{
				hoveredSnap = snap;
				hoveredBounds = b;
				hoveredVerdict = v;
			}
		}

		if (hoveredSnap != null)
		{
			renderQuickLook(graphics, hoveredSnap, hoveredVerdict, hoveredBounds);
		}
		return null;
	}

	private boolean isActive(int slot)
	{
		ActiveOfferSnapshot snap = plugin.activeOffers.get(slot);
		return snap != null
			&& (snap.state == GrandExchangeOfferState.BUYING || snap.state == GrandExchangeOfferState.SELLING);
	}

	private void drawMagnifier(Graphics2D g, Rectangle r)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		float s = r.width;
		float cx = r.x + s * 0.40f;
		float cy = r.y + s * 0.40f;
		float lens = s * 0.30f;
		Ellipse2D.Float ring = new Ellipse2D.Float(cx - lens, cy - lens, lens * 2f, lens * 2f);
		Line2D.Float handle = new Line2D.Float(cx + lens * 0.70f, cy + lens * 0.70f, r.x + s - 1.5f, r.y + s - 1.5f);
		g2.setStroke(new BasicStroke(s / 4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(new Color(0, 0, 0, 180));
		g2.draw(ring);
		g2.draw(handle);
		g2.setStroke(new BasicStroke(s / 9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(MAG_LENS);
		g2.draw(ring);
		g2.draw(handle);
		g2.dispose();
	}

	private void renderQuickLook(Graphics2D graphics, ActiveOfferSnapshot snap, Verdict v, Rectangle slot)
	{
		ItemInsights.Current c = v.ins.current;
		panel.getChildren().clear();
		panel.setPreferredSize(new Dimension(184, 0));
		panel.setBackgroundColor(TOOLTIP_BG);

		panel.getChildren().add(TitleComponent.builder()
			.text(truncate(snap.name, 22) + (snap.isBuy() ? "  (Buy)" : "  (Sell)"))
			.color(HEADER)
			.build());

		BufferedImage chart = buildChart(v.ins);
		if (chart != null)
		{
			panel.getChildren().add(LineComponent.builder().left(" ").build());
			panel.getChildren().add(new ImageComponent(chart));
			panel.getChildren().add(LineComponent.builder().left(" ").build());
		}

		panel.getChildren().add(line("Buy",  FlipItemPanel.formatGp(c.buyPrice) + " gp",  INFO));
		panel.getChildren().add(line("Sell", FlipItemPanel.formatGp(c.sellPrice) + " gp", INFO));
		panel.getChildren().add(line("Your price", FlipItemPanel.formatGp(snap.price) + " gp", Color.WHITE));
		panel.getChildren().add(line(snap.isBuy() ? "07Flip buy" : "07Flip sell",
			FlipItemPanel.formatGp(v.benchmark) + " gp", v.competitive ? INFO : HEADER));
		Integer age = snap.isBuy() ? c.buyAgeMinutes : c.sellAgeMinutes;
		if (age != null)
		{
			panel.getChildren().add(line("Updated", ageText(age), INFO));
		}

		com.o7flip.model.RepriceResult rp = plugin.getReprice(snap.itemId, snap.isBuy(),
			Math.max(1, snap.totalQuantity - snap.quantitySold), snap.price, plugin.offerHeldMinutes(snap.slot));
		boolean repriceShown = rp != null && addRepriceSection(snap, rp);
		if (!repriceShown)
		{
			if (v.competitive)
			{
				panel.getChildren().add(TitleComponent.builder().text("Competitive").color(GOOD).build());
			}
			else
			{
				panel.getChildren().add(TitleComponent.builder()
					.text(snap.isBuy() ? "Raise price to fill" : "Lower price to fill")
					.color(BAD)
					.build());
			}
		}

		final int width = 192;
		int px = slot.x + slot.width + 4;
		int py = slot.y;
		if (px + width > client.getCanvasWidth())
		{
			px = slot.x - width - 4;
		}
		if (px < 0)
		{
			px = slot.x;
		}
		py = Math.min(py, Math.max(0, client.getCanvasHeight() - 210));
		Graphics2D g2 = (Graphics2D) graphics.create();
		g2.translate(px, py);
		panel.render(g2);
		g2.dispose();
	}

	private boolean addRepriceSection(ActiveOfferSnapshot snap, com.o7flip.model.RepriceResult rp)
	{
		if (rp.status == null)
		{
			return false;
		}
		switch (rp.status)
		{
			case "clears_with_profit":
				panel.getChildren().add(line(snap.isBuy() ? "Re-bid at" : "Re-list at",
					FlipItemPanel.formatGp(rp.suggestedPrice) + " gp", HEADER));
				panel.getChildren().add(TitleComponent.builder()
					.text("+" + FlipItemPanel.formatGp(rp.netMarginEach) + "/ea  ~" + rp.etaMinutes + "m")
					.color(GOOD)
					.build());
				return true;
			case "break_even_only":
				panel.getChildren().add(line("Break-even", FlipItemPanel.formatGp(rp.breakEvenPrice) + " gp", HEADER));
				panel.getChildren().add(TitleComponent.builder()
					.text("Hold - margin should recover")
					.color(HEADER)
					.build());
				return true;
			case "underwater":
				panel.getChildren().add(line("Cut loss at", FlipItemPanel.formatGp(rp.clearingPrice) + " gp", BAD));
				panel.getChildren().add(TitleComponent.builder()
					.text(FlipItemPanel.formatGp(rp.cutLossMarginEach) + "/ea to exit now")
					.color(BAD)
					.build());
				return true;
			default:
				return false;
		}
	}

	private BufferedImage buildChart(ItemInsights ins)
	{
		String period = plugin.selectedChartPeriod;
		if (period == null)
		{
			period = config.defaultChartPeriod().chartLabel();
		}
		Long[] buy  = sparklineBuyFor(ins, period);
		Long[] sell = sparklineSellFor(ins, period);
		if ((buy == null || buy.length == 0) && (sell == null || sell.length == 0))
		{
			buy  = ins.sparkline24hBuy;
			sell = ins.sparkline24hSell;
		}
		if ((buy == null || buy.length == 0) && (sell == null || sell.length == 0))
		{
			return null;
		}
		return MiniChart.render(CHART_W, CHART_H, buy, sell);
	}

	private static Long[] sparklineBuyFor(ItemInsights ins, String period)
	{
		switch (period)
		{
			case "2h":  return ins.sparkline2hBuy;
			case "4h":  return ins.sparkline4hBuy;
			case "7d":  return ins.sparkline7dBuy;
			case "30d": return ins.sparkline30dBuy;
			default:    return ins.sparkline24hBuy;
		}
	}

	private static Long[] sparklineSellFor(ItemInsights ins, String period)
	{
		switch (period)
		{
			case "2h":  return ins.sparkline2hSell;
			case "4h":  return ins.sparkline4hSell;
			case "7d":  return ins.sparkline7dSell;
			case "30d": return ins.sparkline30dSell;
			default:    return ins.sparkline24hSell;
		}
	}

	private Verdict evaluate(ActiveOfferSnapshot snap)
	{
		if (snap == null || snap.price <= 0)
		{
			return null;
		}
		ItemInsights ins = plugin.getOverlayInsights(snap.itemId);
		if (ins == null || ins.current == null)
		{
			return null;
		}
		ItemInsights.Current c = ins.current;
		boolean isBuy = snap.isBuy();
		Long rec = isBuy ? c.recBuy : c.recSell;
		long live = isBuy ? c.buyPrice : c.sellPrice;
		long benchmark = (rec != null && rec > 0) ? rec : live;
		if (benchmark <= 0)
		{
			return null;
		}
		boolean competitive = isBuy ? snap.price >= benchmark : snap.price <= benchmark;
		return new Verdict(benchmark, competitive, ins);
	}

	private static Widget findItemIcon(Widget w)
	{
		if (w == null || w.isHidden())
		{
			return null;
		}
		if (w.getItemId() > 0)
		{
			return w;
		}
		Widget[] dyn = w.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget child : dyn)
			{
				Widget found = findItemIcon(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		Widget[] stat = w.getStaticChildren();
		if (stat != null)
		{
			for (Widget child : stat)
			{
				Widget found = findItemIcon(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static LineComponent line(String left, String right, Color rightColor)
	{
		return LineComponent.builder()
			.left(left)
			.leftColor(INFO)
			.right(right)
			.rightColor(rightColor)
			.build();
	}

	private static String ageText(int minutes)
	{
		if (minutes < 1)
		{
			return "now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		return (minutes / 60) + "h ago";
	}

	private static String truncate(String s, int max)
	{
		if (s == null)
		{
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "...";
	}

	private static final class Verdict
	{
		final long benchmark;
		final boolean competitive;
		final ItemInsights ins;

		Verdict(long benchmark, boolean competitive, ItemInsights ins)
		{
			this.benchmark   = benchmark;
			this.competitive = competitive;
			this.ins         = ins;
		}
	}
}
