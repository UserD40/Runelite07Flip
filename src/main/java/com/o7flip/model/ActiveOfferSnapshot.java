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

import net.runelite.api.GrandExchangeOfferState;

/**
 * Immutable, EDT-safe snapshot of a live GE offer. Captured on the game
 * thread (where {@link net.runelite.api.Client#getItemDefinition} is legal)
 * so the panel can render rows without crossing thread boundaries.
 *
 * Replaces direct use of {@link net.runelite.api.GrandExchangeOffer} from
 * Swing code — that class works fine for primitive accessors but resolving
 * the item name requires the client thread and asserts otherwise.
 */
public class ActiveOfferSnapshot
{
	public final int slot;
	public final int itemId;
	public final String name;
	public final long price;
	public final int quantitySold;
	public final int totalQuantity;
	public final GrandExchangeOfferState state;

	public ActiveOfferSnapshot(int slot, int itemId, String name, long price,
		int quantitySold, int totalQuantity, GrandExchangeOfferState state)
	{
		this.slot          = slot;
		this.itemId        = itemId;
		this.name          = name != null ? name : "Item " + itemId;
		this.price         = price;
		this.quantitySold  = quantitySold;
		this.totalQuantity = totalQuantity;
		this.state         = state;
	}

	public boolean isBuy()
	{
		return state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;
	}
}
