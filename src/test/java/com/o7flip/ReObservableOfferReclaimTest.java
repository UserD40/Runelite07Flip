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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Covers {@link O7FlipPlugin#findReObservableActiveOfferRow} — the post-restart
 * reclaim matcher that adopts a still-active offer's existing local row instead
 * of appending a duplicate when {@code slotRecordedFills} was lost.
 *
 * <p>Pins the P7 fix: it matches on offer-epoch (slot) continuity rather than
 * exact priceEach (which DRIFTS as the integer-divided running average changes
 * while the plugin is offline), and guards with the fills-accumulate invariant
 * so a terminal CANCELLED partial row from a prior flip in the same slot — which
 * keeps {@code partial == true} — is never reclaimed and made to swallow the new
 * offer's fills.
 */
public class ReObservableOfferReclaimTest
{
	private static final int EYE_OF_AYAK = 28_409;
	private static final int BANDOS_CHESTPLATE = 11_832;
	private static final int SLOT = 3;
	/** offerInstanceId minting is currentTimeMillis()*10 + slot, so % 10 == slot. */
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
		// The P7 bug: the row's priceEach (3800/40 = 95) differs from the live
		// offer's average (~100) because more filled while offline. Exact-price
		// matching missed it; slot-continuity + accumulate-invariant catches it.
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 40, 3_800L)));
		assertEquals(0, find(list, /*currentQty*/ 50, /*currentGp*/ 5_000L));
	}

	@Test
	public void cancelledRowAheadOfLiveOffer_isNotReclaimed()
	{
		// A prior flip's cancelled buy (cancellation keeps partial=true) recorded
		// 60 units in this slot. A fresh re-list of the same item/size has only
		// filled 10 so far. The stale row is AHEAD of the live offer, so it must
		// NOT be reclaimed (else the existingQty >= currentQty early-return would
		// swallow the new offer's fills).
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 60, 6_000L)));
		assertEquals(-1, find(list, /*currentQty*/ 10, /*currentGp*/ 1_000L));
	}

	@Test
	public void gpAheadOfLiveOffer_isNotReclaimed()
	{
		// Qty is behind but recorded gp is ahead of the live cumulative — still a
		// different/terminal offer, not the one we're observing.
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 40, 6_000L)));
		assertEquals(-1, find(list, /*currentQty*/ 50, /*currentGp*/ 5_000L));
	}

	@Test
	public void differentSlot_isNotReclaimed()
	{
		// Same item/side/size but the row's offerInstanceId encodes slot 4, while
		// we're observing slot 3 — a still-active offer never changes slot.
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_4, true, 40, 3_800L)));
		assertEquals(-1, find(list, 50, 5_000L));
	}

	@Test
	public void terminalRow_isNotReclaimed()
	{
		// A completed (non-partial) row is not an in-progress offer.
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, false, 40, 3_800L)));
		assertEquals(-1, find(list, 50, 5_000L));
	}

	@Test
	public void legacyRowWithoutOfferInstanceId_isNotReclaimed()
	{
		// Legacy rows (no offerInstanceId) are the domain of
		// findClaimableLegacyOfferRow, not this matcher.
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
		// Same offer, no new fills since the row was last written — quantity equals
		// the live cumulative. Still a valid reclaim (recordIfNewFills then aligns
		// the slot ledger and records nothing new).
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 50, 5_000L)));
		assertEquals(0, find(list, 50, 5_000L));
	}

	@Test
	public void picksMostRecentMatchingRow()
	{
		// Two reclaimable rows for the same slot/item (e.g. accumulated from the
		// pre-fix duplicate bug). The back-to-front scan returns the latest.
		List<TradeRecord> list = new ArrayList<>(Arrays.asList(
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 20, 1_900L),
			row(EYE_OF_AYAK, true, 100, OID_SLOT_3, true, 40, 3_800L)));
		assertEquals(1, find(list, 50, 5_000L));
	}
}
