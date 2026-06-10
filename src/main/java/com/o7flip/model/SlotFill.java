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

/**
 * Synthetic record of a matched trade against an active Optimiser slot.
 * Pushed into a {@link LiveSlot}'s {@code buys[]} or {@code sells[]} when
 * the plugin detects a trade for an item in the active allocation. Not a
 * raw GE offer — offer-level granularity is deliberately not tracked.
 */
public class SlotFill
{
	public int    qty;
	public long   priceEach;
	public String tradedAt;   // ISO datetime

	/** Deep copy. {@code foldFill} mutates {@code qty}/{@code priceEach} in place,
	 *  so an off-thread POST snapshot must clone the SlotFill itself, not just the
	 *  containing list, to avoid a torn read. */
	public SlotFill copy()
	{
		SlotFill c = new SlotFill();
		c.qty       = qty;
		c.priceEach = priceEach;
		c.tradedAt  = tradedAt;
		return c;
	}
}
