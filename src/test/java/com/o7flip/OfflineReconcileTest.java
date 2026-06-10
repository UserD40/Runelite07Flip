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

import com.o7flip.model.OptimizeResult.Allocation;
import com.o7flip.model.SlotState;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers {@link O7FlipPlugin#isOfflineSellCompletion} — the offline-sell
 * completion signature used by the login reconcile (SYNC_CONTRACT §7). Only a
 * leg the plugin still thinks is SELLING, whose item has NO live GE offer, is a
 * candidate; everything else must be left alone so the non-destructive prompt
 * doesn't fire spuriously.
 */
public class OfflineReconcileTest
{
	private static final int EYE_OF_AYAK = 28_409;

	private static Allocation leg(SlotState state)
	{
		Allocation a = new Allocation();
		a.itemId = EYE_OF_AYAK;
		a.state = state;
		return a;
	}

	private static Set<Integer> live(int... itemIds)
	{
		Set<Integer> s = new HashSet<>();
		for (int id : itemIds) s.add(id);
		return s;
	}

	@Test
	public void sellingWithNoLiveOffer_isCompletionSignature()
	{
		assertTrue(O7FlipPlugin.isOfflineSellCompletion(leg(SlotState.SELLING), Collections.emptySet()));
	}

	@Test
	public void sellingWithLiveOffer_isNotComplete()
	{
		// The sell is still sitting in the GE — the position is not done.
		assertFalse(O7FlipPlugin.isOfflineSellCompletion(leg(SlotState.SELLING), live(EYE_OF_AYAK)));
	}

	@Test
	public void filledWaitingToList_isNotFlagged()
	{
		// Bought, no sell listed yet — empty slot is normal, not a completion.
		assertFalse(O7FlipPlugin.isOfflineSellCompletion(leg(SlotState.FILLED), Collections.emptySet()));
	}

	@Test
	public void buyingPendingClosed_areNotFlagged()
	{
		assertFalse(O7FlipPlugin.isOfflineSellCompletion(leg(SlotState.BUYING), Collections.emptySet()));
		assertFalse(O7FlipPlugin.isOfflineSellCompletion(leg(SlotState.PENDING), Collections.emptySet()));
		assertFalse(O7FlipPlugin.isOfflineSellCompletion(leg(SlotState.CLOSED), Collections.emptySet()));
	}

	@Test
	public void nullLeg_isNotFlagged()
	{
		assertFalse(O7FlipPlugin.isOfflineSellCompletion(null, Collections.emptySet()));
	}
}
