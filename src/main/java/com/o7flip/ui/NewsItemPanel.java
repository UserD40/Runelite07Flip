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

import com.o7flip.model.NewsItem;
import com.o7flip.util.Fonts;
import net.runelite.client.ui.ColorScheme;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * List-row card for one {@link NewsItem} in the News tab. Shows the title, a
 * category badge + relative date, and a short summary snippet; clicking the
 * card opens the full article detail view (relevant items + link to the post)
 * via the supplied {@code onOpen} callback.
 */
public class NewsItemPanel extends JPanel
{
	private static final Color EVEN_BG    = new Color(0x1F1F1F);
	private static final Color ODD_BG     = new Color(0x262626);
	private static final Color CARD_HOVER = new Color(0x2F2F2F);
	private static final Color CARD_BORD  = new Color(0x3A3A3A);

	public NewsItemPanel(NewsItem news, boolean odd, Runnable onOpen)
	{
		final Color base   = odd ? ODD_BG : EVEN_BG;
		final Color accent = accentFor(news.category);

		setLayout(new BorderLayout());
		setBackground(base);
		setBorder(BorderFactory.createLineBorder(CARD_BORD, 1));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Left category-coloured accent stripe — the main "article" cue.
		JPanel stripe = new JPanel();
		stripe.setBackground(accent);
		stripe.setPreferredSize(new Dimension(4, 10));
		add(stripe, BorderLayout.WEST);

		final JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(base);
		content.setBorder(new EmptyBorder(8, 10, 8, 10));

		JLabel title = new JLabel("<html>" + escapeHtml(news.title) + "</html>");
		title.setFont(Fonts.BOLD);
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
		content.add(title);

		// Meta row: category tag pill + relative time + item count.
		JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		metaRow.setOpaque(false);
		metaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		metaRow.setBorder(new EmptyBorder(3, 0, 0, 0));
		metaRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		if (news.category != null && !news.category.isEmpty())
		{
			JLabel tag = new JLabel(news.category.toUpperCase());
			tag.setFont(Fonts.SM_BOLD);
			tag.setForeground(accent);
			tag.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(accent, 1),
				new EmptyBorder(0, 5, 0, 5)));
			metaRow.add(tag);
		}
		StringBuilder sub = new StringBuilder();
		String rel = relativeTime(news.publishedAt);
		if (!rel.isEmpty())
		{
			sub.append(rel);
		}
		if (news.items != null && !news.items.isEmpty())
		{
			if (sub.length() > 0) sub.append("  ·  ");
			sub.append(news.items.size()).append(" item").append(news.items.size() == 1 ? "" : "s");
		}
		if (sub.length() > 0)
		{
			JLabel subLabel = new JLabel(sub.toString());
			subLabel.setFont(Fonts.SM);
			subLabel.setForeground(new Color(0x888888));
			metaRow.add(subLabel);
		}
		content.add(metaRow);

		String snippet = snippet(news.summary, 130);
		if (!snippet.isEmpty())
		{
			JLabel summary = new JLabel("<html><div style='width:235px'>" + escapeHtml(snippet) + "</div></html>");
			summary.setFont(Fonts.SM);
			summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			summary.setAlignmentX(Component.LEFT_ALIGNMENT);
			summary.setBorder(new EmptyBorder(6, 0, 0, 0));
			summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
			content.add(summary);
		}

		add(content, BorderLayout.CENTER);

		MouseAdapter open = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (onOpen != null)
				{
					onOpen.run();
				}
			}
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(CARD_HOVER);
				content.setBackground(CARD_HOVER);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(base);
				content.setBackground(base);
			}
		};
		addMouseListener(open);
		content.addMouseListener(open);
		title.addMouseListener(open);
	}

	/** Category → accent colour for the stripe + tag. Falls back to 07Flip gold. */
	private static Color accentFor(String category)
	{
		if (category == null)
		{
			return new Color(0xC4A052);
		}
		switch (category.toLowerCase())
		{
			case "qol":      return new Color(0x4A90D9);   // blue
			case "mixed":    return new Color(0x9B7EDE);   // purple
			case "update":   return new Color(0xE0922F);   // orange
			case "pvm":
			case "boss":     return new Color(0xE85050);   // red
			case "economy":
			case "ge":       return new Color(0x00C27A);   // green
			default:         return new Color(0xC4A052);   // gold
		}
	}

	private static String snippet(String s, int max)
	{
		if (s == null || s.isEmpty())
		{
			return "";
		}
		String t = s.trim();
		if (t.length() <= max)
		{
			return t;
		}
		return t.substring(0, max).trim() + "…";
	}

	/** "5m ago" / "3h ago" / "2d ago" from an ISO-8601 timestamp; "" on parse failure. */
	public static String relativeTime(String iso)
	{
		if (iso == null || iso.isEmpty())
		{
			return "";
		}
		try
		{
			Instant then = Instant.parse(iso);
			long mins = ChronoUnit.MINUTES.between(then, Instant.now());
			if (mins < 1)
			{
				return "just now";
			}
			if (mins < 60)
			{
				return mins + "m ago";
			}
			long hrs = mins / 60;
			if (hrs < 24)
			{
				return hrs + "h ago";
			}
			return (hrs / 24) + "d ago";
		}
		catch (Exception e)
		{
			return "";
		}
	}

	public static String capitalise(String s)
	{
		if (s == null || s.isEmpty())
		{
			return "";
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public static String escapeHtml(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
