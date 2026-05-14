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
import java.util.List;

/**
 * Lifetime tally of OSRS bonds the user has purchased on the GE.
 *
 * Bonds aren't flips. Each bond bought is a membership purchase, and the
 * user's "Membership cost" stat is the cumulative gp they've spent on bonds
 * minus anything they've sold back to the GE. Storing that in the 200-row
 * {@code tradeHistory} sliding window would silently truncate it after a
 * few weeks of heavy flipping — a year of membership is ~26 bonds, well
 * beyond the trade-row recycle horizon.
 *
 * This class is a small value object holding the running totals. The
 * O7FlipPlugin persists them as two config keys
 * ({@code o7flip.bondLedgerSpend}, {@code o7flip.bondLedgerCount}) and
 * updates them on every recorded bond trade. A future
 * {@code /api/runelite/bonds} endpoint can mirror these counters; the
 * presentation layer is expected to prefer server stats when present and
 * fall back to this client-side ledger when offline.
 *
 * Semantics:
 * <ul>
 *   <li><b>Bond buy:</b> {@code spend += totalGp}, {@code count += quantity}.
 *       The user has acquired a bond they'll consume for membership.</li>
 *   <li><b>Bond sell back to GE:</b> {@code spend -= totalGp},
 *       {@code count -= quantity}. The user changed their mind and
 *       offloaded the bond. Membership cost reduces correspondingly.</li>
 * </ul>
 *
 * Counters are clamped at zero — selling more bonds than the ledger
 * recorded as bought (possible if the user had bond history before the
 * ledger existed) shouldn't push the stat negative.
 *
 * Stateless transforms. Mutation lives in the plugin.
 */
public final class BondLedger
{
	public static final int BOND_ITEM_ID = ProfitCalculator.BOND_ITEM_ID;

	public final long spend;
	public final int  count;

	public static final BondLedger EMPTY = new BondLedger(0L, 0);

	public BondLedger(long spend, int count)
	{
		this.spend = Math.max(0L, spend);
		this.count = Math.max(0, count);
	}

	/**
	 * Returns a new ledger reflecting the application of {@code trade}.
	 * No-op if {@code trade} isn't a bond. Pure — does not mutate this.
	 */
	public BondLedger apply(TradeRecord trade)
	{
		if (trade == null || trade.itemId != BOND_ITEM_ID || trade.quantity <= 0)
		{
			return this;
		}
		if (trade.isBuy)
		{
			return new BondLedger(spend + trade.totalGp, count + trade.quantity);
		}
		return new BondLedger(spend - trade.totalGp, count - trade.quantity);
	}

	/**
	 * Builds a ledger by replaying every bond trade in {@code trades} in
	 * timestamp order. Used for the one-shot migration when an install
	 * upgrades to a plugin version that has the ledger — existing bond
	 * rows in tradeHistory get summed into the ledger so the user's
	 * Membership cost stat survives the upgrade.
	 *
	 * Trade order matters only for the clamp behaviour: an interleaved
	 * sell that would briefly take the count negative is clamped to 0 by
	 * {@link #apply(TradeRecord)} via the constructor floor.
	 */
	public static BondLedger seedFromHistory(List<TradeRecord> trades)
	{
		BondLedger l = EMPTY;
		if (trades == null) return l;
		for (TradeRecord t : trades)
		{
			l = l.apply(t);
		}
		return l;
	}
}
