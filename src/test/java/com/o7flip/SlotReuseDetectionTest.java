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

import com.o7flip.model.TradeRecord;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers {@link O7FlipPlugin#slotBaselineIsStale} — the reused-GE-slot
 * detector that keeps a completed trade from vanishing when the slot's EMPTY
 * transition was never observed (collected + re-listed across a logout, or
 * while the plugin was unloaded).
 */
public class SlotReuseDetectionTest
{
	private static final int EYE_OF_AYAK = 28_409;
	private static final int BANDOS_CHESTPLATE = 11_832;

	private static TradeRecord row(int itemId, Integer totalQuantity)
	{
		TradeRecord t = new TradeRecord();
		t.itemId = itemId;
		t.isBuy = true;
		t.totalQuantity = totalQuantity;
		t.offerInstanceId = 12_345L;
		return t;
	}

	/**
	 * The reported bug. The slot's stored baseline points at an older 1×
	 * Eye-of-ayak buy; the slot was re-listed (unobserved) to a 5× buy of the
	 * same item. Same item id, different order size → stale. Before the fix
	 * this slipped through (item id matched, cumulative qty did not drop below
	 * the stale 1), so the 5× buy's fills were diffed against the 1× baseline
	 * and effectively lost.
	 */
	@Test
	public void sameItem_differentOrderSize_isStale()
	{
		assertTrue(slotBaselineIsStale(row(EYE_OF_AYAK, 1), EYE_OF_AYAK, 5));
	}

	@Test
	public void differentItem_isStale()
	{
		assertTrue(slotBaselineIsStale(row(BANDOS_CHESTPLATE, 1), EYE_OF_AYAK, 5));
	}

	@Test
	public void sameItem_sameOrderSize_isNotStale()
	{
		// The normal mid-fill case: the baseline row belongs to the very offer
		// we're still observing. Must never reset, or a multi-fill offer would
		// be re-recorded from scratch on every tick.
		assertFalse(slotBaselineIsStale(row(EYE_OF_AYAK, 5), EYE_OF_AYAK, 5));
	}

	@Test
	public void nullBaselineRow_isNotStale()
	{
		// Referenced row rolled out of the 200-row window — we can't prove
		// reuse, so preserve existing behaviour rather than risk re-recording
		// an in-flight offer.
		assertFalse(slotBaselineIsStale(null, EYE_OF_AYAK, 5));
	}

	@Test
	public void legacyRowWithoutTotalQuantity_fallsBackToItemId()
	{
		// Pre-upgrade rows have a null totalQuantity. Same item → not provably
		// stale (no order size to compare); different item → stale.
		assertFalse(slotBaselineIsStale(row(EYE_OF_AYAK, null), EYE_OF_AYAK, 5));
		assertTrue(slotBaselineIsStale(row(BANDOS_CHESTPLATE, null), EYE_OF_AYAK, 5));
	}

	@Test
	public void unknownCurrentOrderSize_doesNotFalselyReset()
	{
		// A zero/unknown current totalQuantity (offer not yet readable) must
		// not be read as an order-size change against a known baseline.
		assertFalse(slotBaselineIsStale(row(EYE_OF_AYAK, 5), EYE_OF_AYAK, 0));
	}

	private static boolean slotBaselineIsStale(TradeRecord baselineRow, int itemId, int totalQuantity)
	{
		return O7FlipPlugin.slotBaselineIsStale(baselineRow, itemId, totalQuantity);
	}
}
