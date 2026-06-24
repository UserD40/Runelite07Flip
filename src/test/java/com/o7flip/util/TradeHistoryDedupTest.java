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
package com.o7flip.util;

import com.o7flip.model.TradeRecord;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TradeHistoryDedupTest
{
	private static final int CRYSTAL_SEED = 23956;
	private static final int MACUAHUITL   = 28997;
	private static final int BOND         = 13190;

	@Test
	public void empty_and_singleton_are_returned_as_is()
	{
		assertTrue(TradeHistoryDedup.scrub(null).isEmpty());
		assertTrue(TradeHistoryDedup.scrub(Collections.emptyList()).isEmpty());

		TradeRecord one = merged(1, "X", true, 1, 100L, 1000L);
		List<TradeRecord> out = TradeHistoryDedup.scrub(Collections.singletonList(one));
		assertEquals(1, out.size());
		assertSame(one, out.get(0));
	}

	@Test
	public void serverFills_summingToLocalMergedRow_areDropped()
	{
		TradeRecord local = merged(MACUAHUITL, "Dual macuahuitl", true, 7, 42_224_000L, 1_000_000L);
		List<TradeRecord> input = new java.util.ArrayList<>();
		input.add(local);
		for (int i = 0; i < 7; i++)
		{
			input.add(serverFill(MACUAHUITL, "Dual macuahuitl", true, 1, 6_032_000L,
				1_000_000L + i * 60_000L, 2382 + i));
		}

		List<TradeRecord> out = TradeHistoryDedup.scrub(input);

		assertEquals(1, out.size());
		assertSame(local, out.get(0));
	}

	@Test
	public void serverSellFills_summingToLocalSellMergedRow_areDropped()
	{
		TradeRecord local = merged(CRYSTAL_SEED, "Crystal armour seed", false, 6, 41_218_674L, 1_500_000L);
		TradeRecord fill1 = serverFill(CRYSTAL_SEED, "Crystal armour seed", false, 4, 27_479_116L, 1_500_000L, 2485);
		TradeRecord fill2 = serverFill(CRYSTAL_SEED, "Crystal armour seed", false, 2, 13_739_558L, 1_500_500L, 2486);

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(local, fill1, fill2));

		assertEquals(1, out.size());
		assertSame(local, out.get(0));
	}

	@Test
	public void serverFills_notSummingToLocal_areKept()
	{
		TradeRecord local = merged(MACUAHUITL, "Dual macuahuitl", true, 7, 42_224_000L, 1_000_000L);
		TradeRecord fill  = serverFill(MACUAHUITL, "Dual macuahuitl", true, 3, 18_096_000L, 1_000_000L, 100);

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(local, fill));

		assertEquals(2, out.size());
	}

	@Test
	public void staleTwins_dropPartialKeepsTerminal()
	{
		TradeRecord stale = merged(MACUAHUITL, "Dual macuahuitl", false, 7, 43_694_098L, 1_000_000L);
		stale.partial = true;
		TradeRecord terminal = merged(MACUAHUITL, "Dual macuahuitl", false, 7, 43_694_098L, 2_000_000L);
		terminal.partial = false;
		terminal.tradeId = 2445L;

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(stale, terminal));

		assertEquals(1, out.size());
		assertSame(terminal, out.get(0));
	}

	@Test
	public void combined_staleTwinPlusSubsumedFills_collapses_to_one_row()
	{
		TradeRecord stale = merged(MACUAHUITL, "Dual macuahuitl", false, 7, 43_694_098L, 1_000_000L);
		stale.partial = true;
		TradeRecord terminal = merged(MACUAHUITL, "Dual macuahuitl", false, 7, 43_694_098L, 2_000_000L);
		terminal.partial = false;
		terminal.tradeId = 2445L;
		TradeRecord fill1 = serverFill(MACUAHUITL, "Dual macuahuitl", false, 1, 6_242_014L, 1_000_000L, 2403);
		TradeRecord fill2 = serverFill(MACUAHUITL, "Dual macuahuitl", false, 2, 12_484_028L, 1_500_000L, 2425);
		TradeRecord fill3 = serverFill(MACUAHUITL, "Dual macuahuitl", false, 4, 24_968_056L, 1_500_500L, 2426);

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(stale, fill1, fill2, fill3, terminal));

		assertEquals(1, out.size());
		assertSame(terminal, out.get(0));
	}

	@Test
	public void multipleSeparateOfferRows_ofIdenticalShape_areKept()
	{
		TradeRecord bondA = merged(BOND, "Old school bond", true, 1, 15_174_000L, 1_000_000L);
		bondA.partial = false;
		TradeRecord bondB = merged(BOND, "Old school bond", true, 1, 15_174_000L, 1_000_001L);
		bondB.partial = false;
		TradeRecord bondC = merged(BOND, "Old school bond", true, 1, 15_174_000L, 1_000_002L);
		bondC.partial = false;

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(bondA, bondB, bondC));

		assertEquals(3, out.size());
	}

	@Test
	public void multipleBondBuys_allPartialTrue_areKept()
	{
		TradeRecord bondA = merged(BOND, "Old school bond", true, 1, 15_174_000L, 1_000_000L);
		TradeRecord bondB = merged(BOND, "Old school bond", true, 1, 15_174_000L, 1_000_001L);
		TradeRecord bondC = merged(BOND, "Old school bond", true, 1, 15_174_000L, 1_000_002L);

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(bondA, bondB, bondC));

		assertEquals(3, out.size());
	}

	@Test
	public void localTwins_farApart_inTime_areKept()
	{
		TradeRecord a = merged(MACUAHUITL, "Dual macuahuitl", true, 7, 42_224_000L, 1_000_000L);
		long farFuture = 1_000_000L + 25L * 60 * 60 * 1000;
		TradeRecord b = merged(MACUAHUITL, "Dual macuahuitl", true, 7, 42_224_000L, farFuture);

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(a, b));

		assertEquals(2, out.size());
	}

	@Test
	public void differentItems_doNotMatch()
	{
		TradeRecord local = merged(CRYSTAL_SEED, "Crystal armour seed", false, 6, 41_218_674L, 1_500_000L);
		TradeRecord wrongItem = serverFill(MACUAHUITL, "Dual macuahuitl", false, 6, 41_218_674L, 1_500_000L, 99);

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(local, wrongItem));

		assertEquals(2, out.size());
	}

	@Test
	public void integration_crystalSeed_panicSell_collapsesToOneSell()
	{
		TradeRecord local = merged(CRYSTAL_SEED, "Crystal armour seed", false, 6, 41_218_674L, 1_778_694_533_005L);
		TradeRecord fill4 = serverFill(CRYSTAL_SEED, "Crystal armour seed", false, 4, 27_479_116L, 1_778_694_533_005L, 2485);
		TradeRecord fill2 = serverFill(CRYSTAL_SEED, "Crystal armour seed", false, 2, 13_739_558L, 1_778_694_605_586L, 2486);

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(local, fill4, fill2));

		assertEquals(1, out.size());
		assertSame(local, out.get(0));
	}


	@Test
	public void stuckPartialDuplicates_areCollapsed()
	{
		long base = 1_778_500_000_000L;
		List<TradeRecord> rows = new java.util.ArrayList<>();
		for (int i = 0; i < 14; i++)
		{
			TradeRecord t = merged(13239, "Primordial boots", false, 1, 22_142_881L, base + i * 60L * 60_000L);
			t.totalQuantity = 2;
			rows.add(t);
		}

		List<TradeRecord> out = TradeHistoryDedup.scrub(rows);

		assertEquals(1, out.size());
		assertEquals(base, out.get(0).timestamp);
	}

	@Test
	public void stuckPartialDuplicates_outsideWindow_areKept()
	{
		TradeRecord a = merged(13239, "Primordial boots", false, 1, 22_142_881L, 1_000_000L);
		a.totalQuantity = 2;
		TradeRecord b = merged(13239, "Primordial boots", false, 1, 22_142_881L,
			1_000_000L + 8L * 24 * 60 * 60_000L);
		b.totalQuantity = 2;

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(a, b));

		assertEquals(2, out.size());
	}

	@Test
	public void stuckPartialDuplicates_differentTotalQty_areKept()
	{
		TradeRecord a = merged(13239, "Primordial boots", false, 1, 22_142_881L, 1_000_000L);
		a.totalQuantity = 2;
		TradeRecord b = merged(13239, "Primordial boots", false, 1, 22_142_881L, 1_001_000L);
		b.totalQuantity = 4;

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(a, b));

		assertEquals(2, out.size());
	}

	@Test
	public void twoStuckRows_overTwoDays_areCollapsed()
	{
		TradeRecord original = merged(13239, "Primordial boots", false, 1, 22_142_881L, 1_000_000L);
		original.totalQuantity = 2;
		TradeRecord reObs    = merged(13239, "Primordial boots", false, 1, 22_142_881L,
			1_000_000L + 60L * 60_000L * 60);
		reObs.totalQuantity = 2;

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(original, reObs));

		assertEquals(1, out.size());
		assertEquals(1_000_000L, out.get(0).timestamp);
	}

	@Test
	public void stuckPartialDuplicates_terminalRowsAreNotCollapsed()
	{
		TradeRecord a = merged(13239, "Primordial boots", false, 1, 22_142_881L, 1_000_000L);
		a.totalQuantity = 2;
		a.partial = false;
		TradeRecord b = merged(13239, "Primordial boots", false, 1, 22_142_881L, 1_001_000L);
		b.totalQuantity = 2;
		b.partial = false;

		List<TradeRecord> out = TradeHistoryDedup.scrub(Arrays.asList(a, b));

		assertEquals(2, out.size());
	}


	@Test
	public void heal_rewritesBackdatedBuyToObservedTime()
	{
		long observedReal = 1_778_697_167_813L;
		long backdatedTs  = 1_778_687_668_071L;
		long offerId      = observedReal * 10L + 2L;

		TradeRecord buy3 = merged(MACUAHUITL, "Dual macuahuitl", true, 3, 188_722_932L, backdatedTs);
		buy3.offerInstanceId = offerId;

		List<TradeRecord> out = TradeHistoryDedup.healBackdatedTimestamps(Arrays.asList(buy3));

		assertEquals(1, out.size());
		assertEquals(observedReal, out.get(0).timestamp);
	}

	@Test
	public void heal_doesNotTouchTimestampsWithinTolerance()
	{
		long observedReal = 1_778_697_167_813L;
		long offerId      = observedReal * 10L + 1L;
		long storedTs     = observedReal + 2L * 60_000L;

		TradeRecord row = merged(MACUAHUITL, "Dual macuahuitl", true, 1, 6_000_000L, storedTs);
		row.offerInstanceId = offerId;

		List<TradeRecord> out = TradeHistoryDedup.healBackdatedTimestamps(Collections.singletonList(row));

		assertSame(row, out.get(0));
	}

	@Test
	public void heal_ignoresRowsWithoutOfferInstanceId()
	{
		TradeRecord row = new TradeRecord();
		row.itemId = MACUAHUITL;
		row.name = "Dual macuahuitl";
		row.isBuy = true;
		row.quantity = 1;
		row.totalGp = 6_000_000L;
		row.timestamp = 1L;
		row.tradeId = 999L;

		List<TradeRecord> out = TradeHistoryDedup.healBackdatedTimestamps(Collections.singletonList(row));

		assertSame(row, out.get(0));
	}


	private static TradeRecord merged(int itemId, String name, boolean isBuy, int qty, long totalGp, long ts)
	{
		TradeRecord t = new TradeRecord();
		t.itemId = itemId;
		t.name = name;
		t.isBuy = isBuy;
		t.quantity = qty;
		t.totalGp = totalGp;
		t.priceEach = qty > 0 ? totalGp / qty : 0;
		t.timestamp = ts;
		t.partial = true;
		t.offerInstanceId = ts * 10L + itemId % 10;
		return t;
	}

	private static TradeRecord serverFill(int itemId, String name, boolean isBuy, int qty, long totalGp, long ts, long tradeId)
	{
		TradeRecord t = new TradeRecord();
		t.itemId = itemId;
		t.name = name;
		t.isBuy = isBuy;
		t.quantity = qty;
		t.totalGp = totalGp;
		t.priceEach = qty > 0 ? totalGp / qty : 0;
		t.timestamp = ts;
		t.partial = true;
		t.tradeId = tradeId;
		return t;
	}
}
