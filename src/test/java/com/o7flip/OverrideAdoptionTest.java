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
import com.o7flip.model.OptimizerSession;
import com.o7flip.model.SlotFill;
import com.o7flip.model.SlotState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OverrideAdoptionTest
{
	private static final int EYE_OF_AYAK = 28_409;
	private static final int BANDOS_CHESTPLATE = 11_832;

	private static SlotFill fill(int qty, long priceEach)
	{
		SlotFill f = new SlotFill();
		f.qty = qty;
		f.priceEach = priceEach;
		f.tradedAt = "2026-06-09T00:00:00Z";
		return f;
	}

	private static Allocation leg(int itemId, int qty, SlotState state, int overrideRev,
		String overrideSource, int appliedOverrideRev, boolean pending,
		List<SlotFill> buys, List<SlotFill> sells)
	{
		Allocation a = new Allocation();
		a.itemId = itemId;
		a.qty = qty;
		a.state = state;
		a.overrideRev = overrideRev;
		a.overrideSource = overrideSource;
		a.appliedOverrideRev = appliedOverrideRev;
		a.pendingOfflineReconcile = pending;
		a.buys = new ArrayList<>(buys);
		a.sells = new ArrayList<>(sells);
		return a;
	}

	private static OptimizerSession session(Allocation... legs)
	{
		OptimizerSession s = new OptimizerSession();
		s.slots = new ArrayList<>(Arrays.asList(legs));
		return s;
	}

	@Test
	public void adoptsAuthoritatively_whenOverrideRevAhead_bypassingIsEmpty()
	{
		Allocation local = leg(EYE_OF_AYAK, 10, SlotState.SELLING, 0, null, 0, true,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(4, 110)));
		Allocation remote = leg(EYE_OF_AYAK, 10, SlotState.CLOSED, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(10, 110)));

		boolean changed = O7FlipPlugin.mergeRemoteFills(session(local), session(remote));

		assertTrue(changed);
		assertEquals(SlotState.CLOSED, local.state);
		assertEquals(1, local.overrideRev);
		assertEquals("site", local.overrideSource);
		assertEquals(1, local.appliedOverrideRev);
		assertFalse(local.pendingOfflineReconcile);
		assertEquals(1, local.sells.size());
		assertEquals(10, local.sells.get(0).qty);
	}

	@Test
	public void doesNotReAdopt_whenRevNotAhead_localStaysAuthoritative()
	{
		Allocation local = leg(EYE_OF_AYAK, 10, SlotState.SELLING, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(3, 110)));
		Allocation remote = leg(EYE_OF_AYAK, 10, SlotState.CLOSED, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(10, 110)));

		boolean changed = O7FlipPlugin.mergeRemoteFills(session(local), session(remote));

		assertFalse(changed);
		assertEquals(1, local.sells.size());
		assertEquals(3, local.sells.get(0).qty);
		assertEquals(1, local.appliedOverrideRev);
		assertEquals(SlotState.SELLING, local.state);
	}

	@Test
	public void multiLeg_overridesOneLeg_leavesOtherLocalAuthoritative()
	{
		Allocation localA = leg(EYE_OF_AYAK, 10, SlotState.SELLING, 0, null, 0, true,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(4, 110)));
		Allocation remoteA = leg(EYE_OF_AYAK, 10, SlotState.CLOSED, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(10, 110)));
		Allocation localB = leg(BANDOS_CHESTPLATE, 5, SlotState.SELLING, 0, null, 0, false,
			Arrays.asList(fill(5, 200)), Arrays.asList(fill(2, 210)));
		Allocation remoteB = leg(BANDOS_CHESTPLATE, 5, SlotState.SELLING, 0, null, 0, false,
			Arrays.asList(fill(5, 200)), Arrays.asList(fill(5, 210)));

		boolean changed = O7FlipPlugin.mergeRemoteFills(
			session(localA, localB), session(remoteA, remoteB));

		assertTrue(changed);
		assertEquals(SlotState.CLOSED, localA.state);
		assertEquals(1, localA.appliedOverrideRev);
		assertEquals(10, localA.sells.get(0).qty);
		assertFalse(localA.pendingOfflineReconcile);
		assertEquals(0, localB.appliedOverrideRev);
		assertEquals(1, localB.sells.size());
		assertEquals(2, localB.sells.get(0).qty);
		assertEquals(SlotState.SELLING, localB.state);
	}

	@Test
	public void adoptsIntoEmptyLocalLeg_andRecordsRev()
	{
		Allocation local = leg(EYE_OF_AYAK, 10, SlotState.PENDING, 0, null, 0, false,
			new ArrayList<>(), new ArrayList<>());
		Allocation remote = leg(EYE_OF_AYAK, 10, SlotState.CLOSED, 2, "site", 2, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(10, 110)));

		boolean changed = O7FlipPlugin.mergeRemoteFills(session(local), session(remote));

		assertTrue(changed);
		assertEquals(2, local.appliedOverrideRev);
		assertEquals(SlotState.CLOSED, local.state);
		assertEquals(10, local.buys.get(0).qty);
	}

	@Test
	public void normalBranch_adoptsRemoteOfferInstanceId_intoFreshLeg()
	{
		Allocation local = leg(EYE_OF_AYAK, 10, SlotState.PENDING, 0, null, 0, false,
			new ArrayList<>(), new ArrayList<>());
		Allocation remote = leg(EYE_OF_AYAK, 10, SlotState.BUYING, 0, null, 0, false,
			Arrays.asList(fill(6, 100)), new ArrayList<>());
		remote.offerInstanceId = 17_000_000_003L;

		boolean changed = O7FlipPlugin.mergeRemoteFills(session(local), session(remote));

		assertTrue(changed);
		assertEquals(Long.valueOf(17_000_000_003L), local.offerInstanceId);
		assertEquals(6, local.buys.get(0).qty);
	}

	@Test
	public void normalBranch_doesNotOverwriteExistingOfferInstanceId()
	{
		Allocation local = leg(EYE_OF_AYAK, 10, SlotState.BUYING, 0, null, 0, false,
			Arrays.asList(fill(6, 100)), new ArrayList<>());
		local.offerInstanceId = 17_000_000_999L;
		Allocation remote = leg(EYE_OF_AYAK, 10, SlotState.BUYING, 0, null, 0, false,
			Arrays.asList(fill(6, 100)), new ArrayList<>());
		remote.offerInstanceId = 17_000_000_003L;

		O7FlipPlugin.mergeRemoteFills(session(local), session(remote));

		assertEquals(Long.valueOf(17_000_000_999L), local.offerInstanceId);
		assertEquals(1, local.buys.size());
	}

	@Test
	public void adoptedLegDoesNotAliasRemoteList()
	{
		Allocation local = leg(EYE_OF_AYAK, 10, SlotState.SELLING, 0, null, 0, false,
			Arrays.asList(fill(5, 100)), new ArrayList<>());
		Allocation remote = leg(EYE_OF_AYAK, 10, SlotState.CLOSED, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(10, 110)));

		O7FlipPlugin.mergeRemoteFills(session(local), session(remote));

		remote.sells.clear();
		assertEquals(1, local.sells.size());
		assertEquals(10, local.sells.get(0).qty);
	}

	private static final int CRYSTAL_SEED = 23_956;
	private static final String GEN_OLD = "2026-06-09T20:00:00Z";
	private static final String GEN_NEW = "2026-06-09T21:00:00Z";

	private static Allocation plain(int itemId, int qty)
	{
		return leg(itemId, qty, SlotState.PENDING, 0, null, 0, false, new ArrayList<>(), new ArrayList<>());
	}

	private static OptimizerSession sessionAt(String generatedAt, Allocation... legs)
	{
		OptimizerSession s = session(legs);
		s.generatedAt = generatedAt;
		return s;
	}

	private static java.util.Set<Integer> itemIds(OptimizerSession s)
	{
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (Allocation a : s.slots) ids.add(a.itemId);
		return ids;
	}

	@Test
	public void versionNewer_adoptsStructure_addsNewAndDropsRemoved()
	{
		OptimizerSession local  = sessionAt(GEN_OLD, plain(EYE_OF_AYAK, 10), plain(BANDOS_CHESTPLATE, 1));
		OptimizerSession remote = sessionAt(GEN_NEW, plain(EYE_OF_AYAK, 10), plain(CRYSTAL_SEED, 5));

		assertTrue(O7FlipPlugin.mergeRemoteFills(local, remote));

		assertEquals(2, local.slots.size());
		assertTrue(itemIds(local).contains(EYE_OF_AYAK));
		assertTrue(itemIds(local).contains(CRYSTAL_SEED));
		assertFalse(itemIds(local).contains(BANDOS_CHESTPLATE));
		assertEquals(GEN_NEW, local.generatedAt);
	}

	@Test
	public void versionNewer_reAttachesLocalFillsToSurvivingLeg_takingServerPlanFields()
	{
		Allocation lEye = leg(EYE_OF_AYAK, 10, SlotState.BUYING, 0, null, 0, false,
			Arrays.asList(fill(6, 100)), new ArrayList<>());
		lEye.offerInstanceId = 17_000_000_003L;
		lEye.buyPrice = 100;
		Allocation rEye = plain(EYE_OF_AYAK, 12);
		rEye.buyPrice = 105;

		OptimizerSession local = sessionAt(GEN_OLD, lEye);
		O7FlipPlugin.mergeRemoteFills(local, sessionAt(GEN_NEW, rEye));

		Allocation merged = local.slots.get(0);
		assertEquals(6, merged.buys.get(0).qty);
		assertEquals(Long.valueOf(17_000_000_003L), merged.offerInstanceId);
		assertEquals(105, merged.buyPrice);
		assertEquals(12, merged.qty);
	}

	@Test
	public void versionNewer_overrideWinsOverReAttach_onNewerRev()
	{
		Allocation lEye = leg(EYE_OF_AYAK, 10, SlotState.SELLING, 0, null, 0, false,
			Arrays.asList(fill(6, 100)), Arrays.asList(fill(2, 110)));
		lEye.offerInstanceId = 17_000_000_003L;
		Allocation rEye = leg(EYE_OF_AYAK, 10, SlotState.CLOSED, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(10, 110)));

		OptimizerSession local = sessionAt(GEN_OLD, lEye);
		O7FlipPlugin.mergeRemoteFills(local, sessionAt(GEN_NEW, rEye));

		Allocation adopted = local.slots.get(0);
		assertEquals(10, adopted.buys.get(0).qty);
		assertEquals(10, adopted.sells.get(0).qty);
		assertEquals(1, adopted.appliedOverrideRev);
		assertEquals(SlotState.CLOSED, adopted.state);
	}

	@Test
	public void sameVersion_keepsStructure_doesNotAddOrDrop()
	{
		OptimizerSession local = sessionAt(GEN_OLD, plain(EYE_OF_AYAK, 10), plain(BANDOS_CHESTPLATE, 1));
		O7FlipPlugin.mergeRemoteFills(local, sessionAt(GEN_OLD, plain(EYE_OF_AYAK, 10), plain(CRYSTAL_SEED, 5)));

		assertTrue(itemIds(local).contains(EYE_OF_AYAK));
		assertTrue(itemIds(local).contains(BANDOS_CHESTPLATE));
		assertFalse(itemIds(local).contains(CRYSTAL_SEED));
		assertEquals(GEN_OLD, local.generatedAt);
	}

	@Test
	public void isRemoteStructurallyNewer_versionRules()
	{
		assertTrue(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(GEN_OLD), sessionAt(GEN_NEW)));
		assertFalse(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(GEN_NEW), sessionAt(GEN_OLD)));
		assertFalse(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(GEN_OLD), sessionAt(GEN_OLD)));
		assertFalse(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(GEN_OLD), sessionAt("")));   // empty = no version
		assertTrue(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(null), sessionAt(GEN_OLD)));
	}

	@Test
	public void isIsoAfter_comparesInstants_notLexically()
	{
		assertTrue(O7FlipPlugin.isIsoAfter(GEN_NEW, GEN_OLD));
		assertFalse(O7FlipPlugin.isIsoAfter(GEN_OLD, GEN_NEW));
		assertFalse(O7FlipPlugin.isIsoAfter(GEN_OLD, GEN_OLD));
		assertTrue(O7FlipPlugin.isIsoAfter("2026-06-09T21:00:00.500Z", "2026-06-09T21:00:00Z"));
	}
}
