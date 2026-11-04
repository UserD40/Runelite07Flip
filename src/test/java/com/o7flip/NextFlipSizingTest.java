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

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class NextFlipSizingTest
{
	@Test
	public void cashIsFlooredToACoarseTier()
	{
		assertEquals(1_000_000L, O7FlipPlugin.cashTier(0L));
		assertEquals(1_000_000L, O7FlipPlugin.cashTier(999_999L));
		assertEquals(2_500_000L, O7FlipPlugin.cashTier(2_500_000L));
		assertEquals(10_000_000L, O7FlipPlugin.cashTier(24_600_000L));
		assertEquals(25_000_000L, O7FlipPlugin.cashTier(25_000_001L));
		assertEquals(2_000_000_000L, O7FlipPlugin.cashTier(9_000_000_000L));
	}

	@Test
	public void quantityIsTheSmallestOfCashLimitAndServer()
	{
		assertEquals(15, O7FlipPlugin.sizeNextFlip(null, 24_600_000L, 1_620_000L, 70));
		assertEquals(10, O7FlipPlugin.sizeNextFlip(null, 24_600_000L, 1_620_000L, 10));
		assertEquals(8, O7FlipPlugin.sizeNextFlip(8, 24_600_000L, 1_620_000L, 70));
	}

	@Test
	public void nothingToBuyGivesZero()
	{
		assertEquals(0, O7FlipPlugin.sizeNextFlip(5, 0L, 100L, 10));
		assertEquals(0, O7FlipPlugin.sizeNextFlip(5, 1_000L, 0L, 10));
		assertEquals(0, O7FlipPlugin.sizeNextFlip(5, 1_000L, 100L, 0));
		assertEquals(0, O7FlipPlugin.sizeNextFlip(null, 50L, 100L, 10));
	}
}
