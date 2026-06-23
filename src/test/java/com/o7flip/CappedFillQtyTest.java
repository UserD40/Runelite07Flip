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

import com.o7flip.model.OptimizeResult;
import com.o7flip.model.SlotFill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CappedFillQtyTest
{
	private static OptimizeResult.Allocation slot(int qty, int bought, int sold)
	{
		OptimizeResult.Allocation a = new OptimizeResult.Allocation();
		a.qty = qty;
		if (bought > 0) a.buys.add(fill(bought));
		if (sold > 0) a.sells.add(fill(sold));
		return a;
	}

	private static SlotFill fill(int qty)
	{
		SlotFill f = new SlotFill();
		f.qty = qty;
		f.priceEach = 1;
		return f;
	}

	@Test
	public void buy_underTarget_countsFull()
	{
		assertEquals(300, O7FlipPlugin.cappedFillQty(true, slot(1000, 0, 0), 300));
	}

	@Test
	public void buy_partiallyExceedsTarget_countsOnlyHeadroom()
	{
		assertEquals(50, O7FlipPlugin.cappedFillQty(true, slot(1000, 950, 0), 100));
	}

	@Test
	public void buy_alreadyAtTarget_countsNothing()
	{
		assertEquals(0, O7FlipPlugin.cappedFillQty(true, slot(1000, 1000, 0), 100));
	}

	@Test
	public void sell_cannotExceedCountedBought()
	{
		assertEquals(400, O7FlipPlugin.cappedFillQty(false, slot(1000, 1000, 600), 500));
	}

	@Test
	public void sell_fullySold_countsNothing()
	{
		assertEquals(0, O7FlipPlugin.cappedFillQty(false, slot(1000, 1000, 1000), 100));
	}

	@Test
	public void nullSlot_isZero()
	{
		assertEquals(0, O7FlipPlugin.cappedFillQty(true, null, 100));
	}
}
