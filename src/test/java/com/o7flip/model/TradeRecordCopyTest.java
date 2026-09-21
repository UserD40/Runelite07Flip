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

import com.o7flip.model.Models.TradeRecord;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TradeRecordCopyTest
{
	@Test
	public void copyCarriesPlacedAtAndOfferIdentity()
	{
		TradeRecord t = new TradeRecord();
		t.itemId = 26_384;
		t.isBuy = false;
		t.quantity = 3;
		t.partial = true;
		t.offerInstanceId = 17_887_801_585_570L;
		t.placedAt = 1_788_778_902_113L;

		TradeRecord c = t.copy();
		assertEquals(t.offerInstanceId, c.offerInstanceId);
		assertEquals(t.placedAt, c.placedAt);
		assertEquals(t.partial, c.partial);
		assertEquals(t.isBuy, c.isBuy);
	}

	@Test
	public void copyKeepsUnknownPlacementUnknown()
	{
		TradeRecord t = new TradeRecord();
		t.offerInstanceId = 1L;
		assertNull(t.copy().placedAt);
	}
}
