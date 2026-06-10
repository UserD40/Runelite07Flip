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

/**
 * Covers the SYNC_CONTRACT §7 manual-override carve-out in
 * {@link O7FlipPlugin#mergeRemoteFills}: a server leg whose {@code overrideRev}
 * is ahead of the plugin's applied rev is adopted authoritatively (bought/sold/
 * state), bypassing the local-authoritative {@code isEmpty()} rule for THAT leg
 * only; every other leg stays local-authoritative.
 */
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
		// Local leg is mid-flip (has its own buys, partially sold, flagged for
		// offline reconcile). The site entered a correction: bought 10 / sold 10,
		// CLOSED, overrideRev 1. The plugin must adopt it wholesale despite local
		// having fills (the isEmpty() rule is bypassed for this leg).
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
		assertEquals(10, local.sells.get(0).qty);   // authoritative sold count adopted
	}

	@Test
	public void doesNotReAdopt_whenRevNotAhead_localStaysAuthoritative()
	{
		// appliedOverrideRev already equals the server's overrideRev → no re-adopt.
		// Local leg keeps its own (different) fills; the local-authoritative
		// isEmpty() rule applies (non-empty local leg is untouched).
		Allocation local = leg(EYE_OF_AYAK, 10, SlotState.SELLING, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(3, 110)));
		Allocation remote = leg(EYE_OF_AYAK, 10, SlotState.CLOSED, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(10, 110)));

		boolean changed = O7FlipPlugin.mergeRemoteFills(session(local), session(remote));

		assertFalse(changed);
		// Local sells NOT overwritten by the server's count.
		assertEquals(1, local.sells.size());
		assertEquals(3, local.sells.get(0).qty);
		assertEquals(1, local.appliedOverrideRev);
		assertEquals(SlotState.SELLING, local.state);
	}

	@Test
	public void multiLeg_overridesOneLeg_leavesOtherLocalAuthoritative()
	{
		// Leg A: the site advanced overrideRev → adopt authoritatively.
		Allocation localA = leg(EYE_OF_AYAK, 10, SlotState.SELLING, 0, null, 0, true,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(4, 110)));
		Allocation remoteA = leg(EYE_OF_AYAK, 10, SlotState.CLOSED, 1, "site", 1, false,
			Arrays.asList(fill(10, 100)), Arrays.asList(fill(10, 110)));
		// Leg B: NO override. The local leg is non-empty, so it must stay
		// local-authoritative — a stale/ahead remote snapshot must NOT be unioned.
		Allocation localB = leg(BANDOS_CHESTPLATE, 5, SlotState.SELLING, 0, null, 0, false,
			Arrays.asList(fill(5, 200)), Arrays.asList(fill(2, 210)));
		Allocation remoteB = leg(BANDOS_CHESTPLATE, 5, SlotState.SELLING, 0, null, 0, false,
			Arrays.asList(fill(5, 200)), Arrays.asList(fill(5, 210)));

		boolean changed = O7FlipPlugin.mergeRemoteFills(
			session(localA, localB), session(remoteA, remoteB));

		assertTrue(changed);
		// A adopted authoritatively.
		assertEquals(SlotState.CLOSED, localA.state);
		assertEquals(1, localA.appliedOverrideRev);
		assertEquals(10, localA.sells.get(0).qty);
		assertFalse(localA.pendingOfflineReconcile);
		// B untouched — local-authoritative, no override, non-empty local leg.
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
		// No override. A freshly-Built (or post-restart) local leg has no identity
		// and no fills; the server-discovered leg carries an offerInstanceId. The
		// normal merge must adopt the fills AND the id so the next in-client fill
		// merges into the same leg instead of forking a new epoch.
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
		// The plugin already owns this leg (it has its own epoch + fills). A stale
		// remote snapshot must neither overwrite the id nor union its fills.
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

		// Mutating the remote (now-discarded) lists must not affect the adopted leg.
		remote.sells.clear();
		assertEquals(1, local.sells.size());
		assertEquals(10, local.sells.get(0).qty);
	}

	// ── Phase 4: version-gated structural adoption + no-clobber ─────────────────

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
		assertTrue(itemIds(local).contains(CRYSTAL_SEED));        // added
		assertFalse(itemIds(local).contains(BANDOS_CHESTPLATE));  // dropped
		assertEquals(GEN_NEW, local.generatedAt);                 // no-clobber: version caught up
	}

	@Test
	public void versionNewer_reAttachesLocalFillsToSurvivingLeg_takingServerPlanFields()
	{
		Allocation lEye = leg(EYE_OF_AYAK, 10, SlotState.BUYING, 0, null, 0, false,
			Arrays.asList(fill(6, 100)), new ArrayList<>());
		lEye.offerInstanceId = 17_000_000_003L;
		lEye.buyPrice = 100;
		Allocation rEye = plain(EYE_OF_AYAK, 12);   // new plan: qty 12, fresh, no fills
		rEye.buyPrice = 105;

		OptimizerSession local = sessionAt(GEN_OLD, lEye);
		O7FlipPlugin.mergeRemoteFills(local, sessionAt(GEN_NEW, rEye));

		Allocation merged = local.slots.get(0);
		assertEquals(6, merged.buys.get(0).qty);                       // local fills carried
		assertEquals(Long.valueOf(17_000_000_003L), merged.offerInstanceId); // epoch carried
		assertEquals(105, merged.buyPrice);                            // server plan field taken
		assertEquals(12, merged.qty);                                  // server plan field taken
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
		assertEquals(10, adopted.buys.get(0).qty);   // §7 override → server fills win, not local 6
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
		assertTrue(itemIds(local).contains(BANDOS_CHESTPLATE)); // local-only kept
		assertFalse(itemIds(local).contains(CRYSTAL_SEED));     // remote-only NOT added (same version)
		assertEquals(GEN_OLD, local.generatedAt);               // unchanged
	}

	@Test
	public void isRemoteStructurallyNewer_versionRules()
	{
		assertTrue(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(GEN_OLD), sessionAt(GEN_NEW)));
		assertFalse(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(GEN_NEW), sessionAt(GEN_OLD)));
		assertFalse(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(GEN_OLD), sessionAt(GEN_OLD)));
		assertFalse(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(GEN_OLD), sessionAt("")));   // empty = no version
		assertTrue(O7FlipPlugin.isRemoteStructurallyNewer(sessionAt(null), sessionAt(GEN_OLD))); // local has none
	}

	@Test
	public void isIsoAfter_comparesInstants_notLexically()
	{
		assertTrue(O7FlipPlugin.isIsoAfter(GEN_NEW, GEN_OLD));
		assertFalse(O7FlipPlugin.isIsoAfter(GEN_OLD, GEN_NEW));
		assertFalse(O7FlipPlugin.isIsoAfter(GEN_OLD, GEN_OLD));
		// differing precision still orders correctly (instant parse, not lexical compare)
		assertTrue(O7FlipPlugin.isIsoAfter("2026-06-09T21:00:00.500Z", "2026-06-09T21:00:00Z"));
	}
}
