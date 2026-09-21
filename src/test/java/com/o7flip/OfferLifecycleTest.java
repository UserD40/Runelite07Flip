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

import com.o7flip.O7FlipPlugin.SlotOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;
import static com.o7flip.O7FlipPlugin.dueOfferEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferLifecycleTest
{
	private static final long T = 1_789_000_000_000L;

	private static SlotOffer offer(int phase, int filled, int sentFilled, long sentAt)
	{
		SlotOffer so = new SlotOffer();
		so.phase = phase;
		so.filled = filled;
		so.sentFilled = sentFilled;
		so.sentAt = sentAt;
		return so;
	}

	@Test
	public void newOfferIsPlaced()
	{
		assertEquals("placed", dueOfferEvent(offer(0, 0, 0, 0L), GrandExchangeOfferState.SELLING, false, 1_000L));
	}

	@Test
	public void firstFillUpdatesImmediatelyThenThrottles()
	{
		assertEquals("updated", dueOfferEvent(offer(1, 3, 0, 0L), GrandExchangeOfferState.SELLING, false, T + 5_000L));
		assertNull(dueOfferEvent(offer(1, 5, 3, T + 5_000L), GrandExchangeOfferState.SELLING, false, T + 30_000L));
		assertEquals("updated", dueOfferEvent(offer(1, 5, 3, T + 5_000L), GrandExchangeOfferState.SELLING, false, T + 65_000L));
		assertNull(dueOfferEvent(offer(1, 5, 5, T + 5_000L), GrandExchangeOfferState.SELLING, false, T + 65_000L));
	}

	@Test
	public void loginResyncAlwaysDescribesOpenOffer()
	{
		assertEquals("updated", dueOfferEvent(offer(1, 5, 5, T + 5_000L), GrandExchangeOfferState.BUYING, true, T + 6_000L));
	}

	@Test
	public void terminalStateClosesOnce()
	{
		assertEquals("closed", dueOfferEvent(offer(0, 0, 0, 0L), GrandExchangeOfferState.CANCELLED_SELL, false, 1L));
		assertEquals("closed", dueOfferEvent(offer(1, 7_000, 7_000, 0L), GrandExchangeOfferState.SOLD, false, 1L));
		assertNull(dueOfferEvent(offer(2, 7_000, 7_000, 0L), GrandExchangeOfferState.SOLD, true, 1L));
	}

	@Test
	public void csvRoundTripKeepsIdentityAndProgress()
	{
		SlotOffer so = new SlotOffer();
		so.listedAt = 1_789_011_000_000L;
		so.placedKnown = true;
		so.itemId = 9_375;
		so.totalQty = 17_000;
		so.isBuy = false;
		so.price = 130L;
		so.filled = 7_000;
		so.oid = 17_897_704_033_130L;
		so.phase = 1;
		so.sentFilled = 6_000;

		String[] parts = so.toCsv(3).split(":");
		assertEquals("3", parts[0]);
		SlotOffer back = SlotOffer.fromParts(parts);
		assertEquals(so.listedAt, back.listedAt);
		assertTrue(back.placedKnown);
		assertEquals(so.itemId, back.itemId);
		assertEquals(so.totalQty, back.totalQty);
		assertFalse(back.isBuy);
		assertEquals(so.price, back.price);
		assertEquals(so.filled, back.filled);
		assertEquals(so.oid, back.oid);
		assertEquals(so.phase, back.phase);
		assertEquals(so.sentFilled, back.sentFilled);
	}

	@Test
	public void legacyEntriesLoadWithUnknownPlacementAndNoOfferId()
	{
		SlotOffer four = SlotOffer.fromParts("2:1700000000000:4151:1".split(":"));
		assertFalse(four.placedKnown);
		assertEquals(0L, four.oid);
		SlotOffer five = SlotOffer.fromParts("2:1700000000000:4151:1:1".split(":"));
		assertTrue(five.placedKnown);
		assertEquals(0L, five.oid);
	}
}
