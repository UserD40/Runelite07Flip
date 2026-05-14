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
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class BondLedgerTest
{
	private static final int BOND       = BondLedger.BOND_ITEM_ID;
	private static final int OTHER_ITEM = 28997; // Dual macuahuitl — anything non-bond

	@Test
	public void empty_constants_are_zero()
	{
		assertEquals(0L, BondLedger.EMPTY.spend);
		assertEquals(0,  BondLedger.EMPTY.count);
	}

	@Test
	public void nonBondTrade_isIgnored()
	{
		BondLedger l = BondLedger.EMPTY.apply(buy(OTHER_ITEM, 7, 42_000_000L));
		assertSame(BondLedger.EMPTY, l.spend == 0L && l.count == 0 ? BondLedger.EMPTY : l);
		assertEquals(0L, l.spend);
		assertEquals(0,  l.count);
	}

	@Test
	public void bondBuy_addsToLedger()
	{
		BondLedger l = BondLedger.EMPTY.apply(buy(BOND, 1, 15_174_000L));
		assertEquals(15_174_000L, l.spend);
		assertEquals(1, l.count);
	}

	@Test
	public void bondSellBack_subtractsFromLedger()
	{
		BondLedger l = BondLedger.EMPTY
			.apply(buy(BOND, 1, 15_174_000L))
			.apply(buy(BOND, 1, 15_174_000L))
			.apply(sell(BOND, 1, 15_092_000L)); // sold one back at the GE buy price

		// 2 buys, 1 sell back → 1 net bond consumed; spend is 30,348,000 − 15,092,000.
		assertEquals(15_256_000L, l.spend);
		assertEquals(1, l.count);
	}

	@Test
	public void sellMoreThanBought_clampsAtZero()
	{
		// The user might sell bonds back that pre-date the ledger (legacy
		// data we don't have a buy record for). The stat should bottom out
		// at zero rather than going negative.
		BondLedger l = BondLedger.EMPTY
			.apply(buy(BOND, 1, 15_174_000L))
			.apply(sell(BOND, 5, 75_460_000L));

		assertEquals(0L, l.spend);
		assertEquals(0,  l.count);
	}

	/**
	 * The headline migration scenario — a year's worth of bonds sitting in
	 * tradeHistory get summed once into the ledger so the user's
	 * Membership cost stat survives the upgrade to the bond-ledger build.
	 */
	@Test
	public void seedFromHistory_aggregatesAllBondTradesAcrossMixedRecords()
	{
		BondLedger l = BondLedger.seedFromHistory(Arrays.asList(
			buy(BOND, 1, 15_174_000L),
			buy(OTHER_ITEM, 7, 42_000_000L),       // non-bond — ignored
			buy(BOND, 1, 15_174_000L),
			sell(BOND, 1, 15_092_000L),            // sold one back
			buy(BOND, 1, 15_174_000L)
		));

		// 3 bond buys at 15.174M, 1 sell at 15.092M:
		// spend = 3 × 15_174_000 − 15_092_000 = 30_430_000
		// count = 3 − 1 = 2
		assertEquals(30_430_000L, l.spend);
		assertEquals(2, l.count);
	}

	@Test
	public void seedFromHistory_handlesNullAndEmpty()
	{
		assertEquals(0L, BondLedger.seedFromHistory(null).spend);
		assertEquals(0,  BondLedger.seedFromHistory(null).count);
		assertEquals(0L, BondLedger.seedFromHistory(Collections.emptyList()).spend);
	}

	@Test
	public void zeroQuantityTrade_isIgnored()
	{
		BondLedger l = BondLedger.EMPTY.apply(buy(BOND, 0, 0L));
		assertEquals(0L, l.spend);
		assertEquals(0,  l.count);
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private static TradeRecord buy(int itemId, int qty, long totalGp)
	{
		TradeRecord t = new TradeRecord();
		t.itemId = itemId;
		t.name = "Test";
		t.isBuy = true;
		t.quantity = qty;
		t.totalGp = totalGp;
		t.timestamp = 1_000_000L;
		return t;
	}

	private static TradeRecord sell(int itemId, int qty, long totalGp)
	{
		TradeRecord t = new TradeRecord();
		t.itemId = itemId;
		t.name = "Test";
		t.isBuy = false;
		t.quantity = qty;
		t.totalGp = totalGp;
		t.timestamp = 2_000_000L;
		return t;
	}
}
