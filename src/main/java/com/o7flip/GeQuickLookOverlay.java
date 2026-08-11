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

import com.o7flip.model.Models.ActiveOfferSnapshot;
import com.o7flip.model.Models.ItemInsights;
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
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.FontManager;
import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;

public class GeQuickLookOverlay extends Overlay
{
	private static final Color NEUTRAL    = new Color(0xC0A050);
	private static final Color HEADER     = new Color(0xFFD700);
	private static final Color INFO       = new Color(0xC0C0C0);
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
		if (!config.showInGameOverlays())
		{
			return null;
		}
		final boolean wantQuickLook = config.showGeQuickLook();
		final boolean wantTimer = config.showGeSlotTimer();
		final boolean wantFill = config.activeFillCounter() || config.activeLastFillAge();
		if (!wantQuickLook && !wantTimer && !wantFill)
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
		Rectangle hoveredSlotBounds = null;
		if ((wantQuickLook || wantTimer || wantFill) && mouse != null)
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
					hoveredSlotBounds = b;
					break;
				}
			}
		}
		final Rectangle panelRect = (wantQuickLook && config.showGeQuickLookTooltip() && hoveredSlotBounds != null)
			? quickLookRect(hoveredSlotBounds) : null;

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

			if (wantQuickLook)
			{
				Verdict v = evaluate(snap);
				Color c = v == null ? NEUTRAL : colorForTier(v.tier);
				drawSlotHighlight(graphics, slot, b, c);
				if (i == hoveredIdx && v != null)
				{
					hoveredSnap = snap;
					hoveredBounds = b;
					hoveredVerdict = v;
				}
			}

			if (wantTimer)
			{
				drawSlotTimer(graphics, b, snap.slot, panelRect);
			}

			if (wantFill)
			{
				drawSlotFillInfo(graphics, slot, b, snap, panelRect);
			}
		}

		if (hoveredSnap != null && config.showGeQuickLookTooltip())
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

	private void drawSlotHighlight(Graphics2D graphics, Widget slot, Rectangle b, Color c)
	{
		Widget icon = findItemIcon(slot);
		if (icon != null && !icon.isHidden())
		{
			Rectangle ib = icon.getBounds();
			if (ib != null)
			{
				int x = ib.x - 3, y = ib.y - 3, w = ib.width + 2, h = ib.height + 2;
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
	}

	private void drawSlotTimer(Graphics2D graphics, Rectangle b, int slot, Rectangle panelRect)
	{
		long listedAt = plugin.offerListedAtMs(slot);
		if (listedAt <= 0)
		{
			return;
		}
		long elapsed = System.currentTimeMillis() - listedAt;
		if (elapsed < 0)
		{
			elapsed = 0;
		}
		long totalSec = elapsed / 1000L;
		String text = config.geTimerCompact()
			? String.format("%02d:%02d", totalSec / 3600L, (totalSec % 3600L) / 60L)
			: String.format("%02d:%02d:%02d", totalSec / 3600L, (totalSec % 3600L) / 60L, totalSec % 60L);
		long mins = totalSec / 60L;
		Color col = mins < config.geTimerWhiteMins() ? config.geBorderGood()
			: (mins < config.geTimerRedMins() ? config.geBorderMid() : config.geBorderBad());

		graphics.setFont(FontManager.getRunescapeFont());
		int width = graphics.getFontMetrics().stringWidth(text);
		int x = b.x + b.width - width - 8;
		if (panelRect != null && panelRect.intersects(new Rectangle(x - 2, b.y + 4, width + 6, 18)))
		{
			return;
		}
		TextComponent tc = new TextComponent();
		tc.setText(text);
		tc.setColor(col);
		tc.setPosition(new java.awt.Point(x, b.y + 18));
		tc.render(graphics);
	}

	private void drawSlotFillInfo(Graphics2D graphics, Widget slot, Rectangle b,
		ActiveOfferSnapshot snap, Rectangle panelRect)
	{
		if (snap.totalQuantity <= 0)
		{
			return;
		}
		Rectangle bar = progressBarBounds(slot, b);
		if (bar == null)
		{
			return;
		}

		String counter = config.activeFillCounter()
			? snap.quantitySold + "/" + snap.totalQuantity
			: "";

		String age = "";
		if (config.activeLastFillAge())
		{
			ItemInsights ins = plugin.getOverlayInsights(snap.itemId);
			if (ins != null && ins.current != null)
			{
				Integer minutes = snap.isBuy() ? ins.current.buyAgeMinutes : ins.current.sellAgeMinutes;
				if (minutes != null && minutes >= 0)
				{
					age = ageCompact(minutes);
				}
			}
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();

		final int pad = 4;
		int counterW = counter.isEmpty() ? 0 : fm.stringWidth(counter);
		int ageW = age.isEmpty() ? 0 : fm.stringWidth(age);
		if (counterW > 0 && ageW > 0 && counterW + ageW + pad * 3 > bar.width)
		{
			age = "";
			ageW = 0;
		}
		if (counterW + ageW == 0 || counterW + ageW + pad * 2 > bar.width)
		{
			return;
		}

		int y = bar.y + (bar.height + fm.getAscent() - fm.getDescent()) / 2;
		if (panelRect != null && panelRect.intersects(bar))
		{
			return;
		}

		graphics.setColor(config.lastFillColour());
		if (counterW > 0 && ageW > 0)
		{
			graphics.drawString(counter, bar.x + pad, y);
			graphics.drawString(age, bar.x + bar.width - pad - ageW, y);
		}
		else
		{
			String only = counterW > 0 ? counter : age;
			graphics.drawString(only, bar.x + (bar.width - counterW - ageW) / 2, y);
		}
	}

	private static String ageCompact(int minutes)
	{
		if (minutes < 60)
		{
			return minutes + "m";
		}
		int hours = minutes / 60;
		return hours < 24 ? hours + "h" : (hours / 24) + "d";
	}

	private static Rectangle progressBarBounds(Widget slot, Rectangle slotBounds)
	{
		Rectangle best = null;
		java.util.Deque<Widget> pending = new java.util.ArrayDeque<>();
		pending.push(slot);
		while (!pending.isEmpty())
		{
			Widget w = pending.pop();
			if (w == null || w.isHidden())
			{
				continue;
			}
			Rectangle wb = w.getBounds();
			String text = w.getText();
			if (wb != null
				&& w.getItemId() <= 0
				&& (text == null || text.isEmpty())
				&& wb.height >= 4 && wb.height <= 24
				&& wb.width >= slotBounds.width / 2
				&& wb.y >= slotBounds.y + slotBounds.height / 3
				&& (best == null || wb.width > best.width))
			{
				best = wb;
			}
			Widget[] dyn = w.getDynamicChildren();
			if (dyn != null)
			{
				for (Widget c : dyn)
				{
					pending.push(c);
				}
			}
			Widget[] stat = w.getStaticChildren();
			if (stat != null)
			{
				for (Widget c : stat)
				{
					pending.push(c);
				}
			}
		}
		if (best != null)
		{
			return best;
		}
		Widget icon = findItemIcon(slot);
		Rectangle ib = icon != null ? icon.getBounds() : null;
		if (ib == null)
		{
			return null;
		}
		int top = ib.y + ib.height;
		int bottom = slotBounds.y + slotBounds.height;
		return new Rectangle(slotBounds.x + 6, (top + bottom) / 2 - 7, slotBounds.width - 12, 14);
	}

	private void renderQuickLook(Graphics2D graphics, ActiveOfferSnapshot snap, Verdict v, Rectangle slot)
	{
		ItemInsights.Current c = v.ins.current;
		panel.getChildren().clear();
		panel.setPreferredSize(new Dimension(184, 0));
		panel.setBackgroundColor(config.geTooltipBg());

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

		if (snap.totalQuantity > 0)
		{
			ProgressBarComponent pb = new ProgressBarComponent();
			pb.setMaximum(snap.totalQuantity);
			pb.setValue(snap.quantitySold);
			pb.setForegroundColor(new Color(0x9B59B6));
			pb.setBackgroundColor(new Color(0x3E3E3E));
			pb.setCenterLabel((snap.isBuy() ? "Bought " : "Sold ") + snap.quantitySold + " / " + snap.totalQuantity);
			pb.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
			panel.getChildren().add(pb);
		}

		panel.getChildren().add(line("Buy",  FlipItemPanel.formatGp(c.buyPrice) + " gp",  INFO));
		panel.getChildren().add(line("Sell", FlipItemPanel.formatGp(c.sellPrice) + " gp", INFO));
		panel.getChildren().add(line("Your price", FlipItemPanel.formatGp(snap.price) + " gp", Color.WHITE));
		panel.getChildren().add(line(snap.isBuy() ? "07Flip buy" : "07Flip sell",
			FlipItemPanel.formatGp(v.benchmark) + " gp", v.tier == 0 ? INFO : HEADER));
		Integer age = snap.isBuy() ? c.buyAgeMinutes : c.sellAgeMinutes;
		if (age != null)
		{
			panel.getChildren().add(line("Updated", ageText(age), INFO));
		}

		com.o7flip.model.Models.RepriceResult rp = plugin.getReprice(snap.itemId, snap.isBuy(),
			Math.max(1, snap.totalQuantity - snap.quantitySold), snap.price, plugin.offerHeldMinutes(snap.slot));
		boolean repriceShown = rp != null && addRepriceSection(snap, rp);
		if (!repriceShown)
		{
			if (v.tier == 0)
			{
				panel.getChildren().add(TitleComponent.builder().text("Competitive").color(config.geBorderGood()).build());
			}
			else if (v.tier == 1)
			{
				panel.getChildren().add(TitleComponent.builder()
					.text(snap.isBuy() ? "Priced a bit low" : "Priced a bit high")
					.color(config.geBorderMid())
					.build());
			}
			else
			{
				panel.getChildren().add(TitleComponent.builder()
					.text(snap.isBuy() ? "Raise price to fill" : "Lower price to fill")
					.color(config.geBorderBad())
					.build());
			}
		}

		Rectangle r = quickLookRect(slot);
		Graphics2D g2 = (Graphics2D) graphics.create();
		g2.translate(r.x, r.y);
		panel.render(g2);
		g2.dispose();
	}

	private Rectangle quickLookRect(Rectangle slot)
	{
		final int width = 192;
		int px = slot.x + slot.width + 4;
		if (px + width > client.getCanvasWidth())
		{
			px = slot.x - width - 4;
		}
		if (px < 0)
		{
			px = slot.x;
		}
		int py = Math.min(slot.y, Math.max(0, client.getCanvasHeight() - 210));
		return new Rectangle(px, py, width, 230);
	}

	private boolean addRepriceSection(ActiveOfferSnapshot snap, com.o7flip.model.Models.RepriceResult rp)
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
					.color(config.geBorderGood())
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
				panel.getChildren().add(line("Cut loss at", FlipItemPanel.formatGp(rp.clearingPrice) + " gp", config.geBorderBad()));
				panel.getChildren().add(TitleComponent.builder()
					.text(FlipItemPanel.formatGp(rp.cutLossMarginEach) + "/ea to exit now")
					.color(config.geBorderBad())
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
		double wrongness = isBuy
			? (benchmark - snap.price) / (double) benchmark
			: (snap.price - benchmark) / (double) benchmark;
		int tier = O7FlipPlugin.competitiveTier(wrongness);
		return new Verdict(benchmark, tier, ins);
	}

	private Color colorForTier(int tier)
	{
		return tier == 0 ? config.geBorderGood() : (tier == 1 ? config.geBorderMid() : config.geBorderBad());
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
		final int tier;
		final ItemInsights ins;

		Verdict(long benchmark, int tier, ItemInsights ins)
		{
			this.benchmark = benchmark;
			this.tier      = tier;
			this.ins       = ins;
		}
	}
}
