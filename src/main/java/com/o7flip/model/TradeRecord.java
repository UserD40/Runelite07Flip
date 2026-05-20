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
package com.o7flip.model;

public class TradeRecord
{
	public int    itemId;
	public String name;
	public boolean isBuy;
	public int    quantity;
	public long   priceEach;  // effective fill price = totalGp / quantity
	public long   totalGp;    // buy: gross gp spent. sell: NET gp received
	                          //   (post-tax — what GrandExchangeOffer.getSpent()
	                          //   reports in current RuneLite, NOT pre-tax gross)
	public long   timestamp;  // System.currentTimeMillis() when recorded
	public boolean partial;   // true when CANCELLED with a non-zero partial fill

	// Server-issued ID once this trade has been synced or fetched from
	// 07flip.com. Null for trades recorded locally that have not been
	// reconciled with the server yet. Backwards-compatible with serialised
	// TradeRecord JSON written before this field existed.
	public Long tradeId;

	/**
	 * Local identifier for "this trade row represents one GE offer". Every
	 * fill the plugin observes for the same active offer slot reuses the same
	 * offerInstanceId, so {@code recordTrade} can merge incremental fills
	 * into a single TradeRecord row instead of appending one row per fill.
	 *
	 * Null for legacy records written before this field existed and for
	 * server-fetched records — those stay as individual rows.
	 */
	public Long offerInstanceId;

	/**
	 * Total quantity the user originally set on this offer (not what filled).
	 * Captured at first observation from {@code GrandExchangeOffer.getTotalQuantity()}
	 * so the row can render a progress bar and "filled / total" counter the
	 * same way the Active view does.
	 *
	 * Null for legacy records — display falls back to using {@link #quantity}
	 * as both filled and total (so they render as a full bar).
	 */
	public Integer totalQuantity;

	/**
	 * Stable fingerprint used for de-duplication when merging server-fetched
	 * trades with locally-recorded ones that pre-date the {@code tradeId}
	 * field. Mirrors the server's composite uniqueness key.
	 */
	public String fingerprint()
	{
		return itemId + "|" + (isBuy ? "B" : "S") + "|" + quantity + "|" + totalGp + "|" + timestamp;
	}
}
