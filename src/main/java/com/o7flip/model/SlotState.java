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

import java.util.List;

/**
 * Lifecycle state of a {@link LiveSlot} derived from cumulative
 * {@code buys[]} / {@code sells[]} quantities. The website re-derives this
 * after every fill; the wire-format value is the snapshot at last save —
 * useful for fast UI display, but the buys/sells arrays are authoritative.
 */
public enum SlotState
{
	PENDING("pending"),
	BUYING("buying"),
	FILLED("filled"),
	SELLING("selling"),
	CLOSED("closed");

	private final String wire;

	SlotState(String wire) { this.wire = wire; }

	public String wire() { return wire; }

	public static SlotState fromWire(String s)
	{
		if (s == null) return PENDING;
		switch (s)
		{
			case "buying":  return BUYING;
			case "filled":  return FILLED;
			case "selling": return SELLING;
			case "closed":  return CLOSED;
			case "pending":
			default:        return PENDING;
		}
	}

	/**
	 * Re-derives state from the cumulative fill totals. Mirrors the website's
	 * {@code deriveState()} so a session reconstructed plugin-side carries
	 * the same status as a session saved on the web.
	 */
	public static SlotState derive(int targetQty, List<SlotFill> buys, List<SlotFill> sells)
	{
		int bought = sum(buys);
		int sold   = sum(sells);
		if (sold >= bought && bought >= targetQty) return CLOSED;
		if (sold > 0)                              return SELLING;
		if (bought >= targetQty)                   return FILLED;
		if (bought > 0)                            return BUYING;
		return PENDING;
	}

	private static int sum(List<SlotFill> fills)
	{
		if (fills == null) return 0;
		int total = 0;
		for (SlotFill f : fills)
		{
			if (f != null) total += f.qty;
		}
		return total;
	}
}
