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

import com.o7flip.model.Models.TradeRecord;
import java.util.List;

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
