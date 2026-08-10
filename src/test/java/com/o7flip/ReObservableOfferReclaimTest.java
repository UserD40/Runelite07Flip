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

import com.o7flip.model.Models.TradeRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ReObservableOfferReclaimTest
{
	private static final int EYE_OF_AYAK = 28_409;
	private static final int BANDOS_CHESTPLATE = 11_832;
	private static final int SLOT = 3;
	private static final long OID_SLOT_3 = 1_700_000_000_000L * 10 + SLOT;
	private static final long OID_SLOT_4 = 1_700_000_000_000L * 10 + 4;

	private static TradeRecord row(int itemId, boolean isBuy, Integer totalQuantity,
		Long offerInstanceId, boolean partial, int quantity, long totalGp)
	{
		TradeRecord t = new TradeRecord();
		t.itemId          = itemId;
		t.isBuy           = isBuy;
		t.totalQuantity   = totalQuantity;
		t.offerInstanceId = offerInstanceId;
		t.partial         = partial;
		t.quantity        = quantity;
		t.totalGp         = totalGp;
		t.priceEach       = quantity > 0 ? totalGp / quantity : 0L;
		return t;
	}

	private static int find(List<TradeRecord> list, int currentQty, long currentGp)
	{
		return O7FlipPlugin.findReObservableActiveOfferRow(
			list, EYE_OF_AYAK, true, 100, SLOT, currentQty, currentGp);
	}

	@Test
	public void driftedPrice_stillReclaims()
	{
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 40, 3_800L)));
		assertEquals(0, find(list, /*currentQty*/ 50, /*currentGp*/ 5_000L));
	}

	@Test
	public void cancelledRowAheadOfLiveOffer_isNotReclaimed()
	{
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 60, 6_000L)));
		assertEquals(-1, find(list, /*currentQty*/ 10, /*currentGp*/ 1_000L));
	}

	@Test
	public void gpAheadOfLiveOffer_isNotReclaimed()
	{
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 40, 6_000L)));
		assertEquals(-1, find(list, /*currentQty*/ 50, /*currentGp*/ 5_000L));
	}

	@Test
	public void differentSlot_isNotReclaimed()
	{
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_4, true, 40, 3_800L)));
		assertEquals(-1, find(list, 50, 5_000L));
	}

	@Test
	public void terminalRow_isNotReclaimed()
	{
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, false, 40, 3_800L)));
		assertEquals(-1, find(list, 50, 5_000L));
	}

	@Test
	public void legacyRowWithoutOfferInstanceId_isNotReclaimed()
	{
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, null, true, 40, 3_800L)));
		assertEquals(-1, find(list, 50, 5_000L));
	}

	@Test
	public void differentItemOrOrderSize_isNotReclaimed()
	{
		List<TradeRecord> wrongItem = new ArrayList<>(Arrays.asList(
			row(BANDOS_CHESTPLATE, true, 100, OID_SLOT_3, true, 40, 3_800L)));
		assertEquals(-1, find(wrongItem, 50, 5_000L));

		List<TradeRecord> wrongSize = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 5, OID_SLOT_3, true, 40, 3_800L)));
		assertEquals(-1, find(wrongSize, 50, 5_000L));
	}

	@Test
	public void equalQty_reclaimsForLedgerAlignment()
	{
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 50, 5_000L)));
		assertEquals(0, find(list, 50, 5_000L));
	}

	@Test
	public void picksMostRecentMatchingRow()
	{
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 20, 1_900L),
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 40, 3_800L)));
		assertEquals(1, find(list, 50, 5_000L));
	}
}
