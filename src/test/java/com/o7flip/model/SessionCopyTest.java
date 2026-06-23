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

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class SessionCopyTest
{
	private static SlotFill fill(int qty, long priceEach)
	{
		SlotFill f = new SlotFill();
		f.qty = qty;
		f.priceEach = priceEach;
		f.tradedAt = "2026-06-09T00:00:00Z";
		return f;
	}

	private static OptimizerSession sample()
	{
		OptimizeResult.Allocation a = new OptimizeResult.Allocation();
		a.itemId = 28_409;
		a.name = "Eye of ayak";
		a.qty = 10;
		a.state = SlotState.SELLING;
		a.offerInstanceId = 17_000_000_003L;
		a.overrideRev = 2;
		a.overrideSource = "site";
		a.appliedOverrideRev = 2;
		a.pendingOfflineReconcile = true;
		a.hourlyTrend = new int[]{1, 2, 3};
		a.buys = new ArrayList<>(Arrays.asList(fill(10, 100)));
		a.sells = new ArrayList<>(Arrays.asList(fill(4, 110)));

		OptimizerSession s = new OptimizerSession();
		s.generatedAt = "2026-06-09T00:00:01Z";
		s.lastPollAt = "2026-06-09T00:00:02Z";
		s.updatedAt = "2026-06-09T00:00:03Z";
		s.inputs.capital = 5_000_000L;
		s.inputs.slots = 8;
		s.inputs.excludeItemIds = new ArrayList<>(Arrays.asList(1, 2));
		s.slots = new ArrayList<>(Arrays.asList(a));
		return s;
	}

	@Test
	public void copyIsValueEqualButDistinctObjects()
	{
		OptimizerSession orig = sample();
		OptimizerSession copy = orig.copy();

		assertNotSame(orig, copy);
		assertNotSame(orig.slots, copy.slots);
		assertNotSame(orig.inputs, copy.inputs);
		assertNotSame(orig.inputs.excludeItemIds, copy.inputs.excludeItemIds);

		OptimizeResult.Allocation oa = orig.slots.get(0);
		OptimizeResult.Allocation ca = copy.slots.get(0);
		assertNotSame(oa, ca);
		assertNotSame(oa.buys, ca.buys);
		assertNotSame(oa.buys.get(0), ca.buys.get(0));
		assertNotSame(oa.hourlyTrend, ca.hourlyTrend);

		assertEquals(28_409, ca.itemId);
		assertEquals(SlotState.SELLING, ca.state);
		assertEquals(Long.valueOf(17_000_000_003L), ca.offerInstanceId);
		assertEquals(2, ca.overrideRev);
		assertEquals("site", ca.overrideSource);
		assertEquals(2, ca.appliedOverrideRev);
		assertTrue(ca.pendingOfflineReconcile);
		assertEquals(10, ca.buys.get(0).qty);
		assertEquals(4, ca.sells.get(0).qty);
		assertEquals("2026-06-09T00:00:01Z", copy.generatedAt);
		assertEquals(5_000_000L, copy.inputs.capital);
	}

	@Test
	public void mutatingLiveFillInPlaceDoesNotTearTheSnapshot()
	{
		OptimizerSession orig = sample();
		OptimizerSession copy = orig.copy();

		orig.slots.get(0).buys.get(0).qty = 999;
		orig.slots.get(0).buys.get(0).priceEach = 7;

		assertEquals(10, copy.slots.get(0).buys.get(0).qty);
		assertEquals(100, copy.slots.get(0).buys.get(0).priceEach);
	}

	@Test
	public void structuralEditsToLiveListsDoNotAffectSnapshot()
	{
		OptimizerSession orig = sample();
		OptimizerSession copy = orig.copy();

		orig.slots.clear();                         // recycle/replace style edit
		orig.inputs.excludeItemIds.add(99);

		assertEquals(1, copy.slots.size());
		assertEquals(2, copy.inputs.excludeItemIds.size());
	}
}
