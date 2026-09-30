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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.o7flip.model.Models.TradeRecord;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TradeWireLongPriceTest
{
	private final O7FlipApiClient client = new O7FlipApiClient();

	@Test
	public void priceAboveIntRange_serialisesAsPlainNumber()
	{
		TradeRecord t = new TradeRecord();
		t.itemId          = 20997;
		t.name            = "Twisted bow";
		t.isBuy           = true;
		t.quantity        = 1;
		t.priceEach       = 8_200_000_000L;
		t.totalGp         = 8_200_000_000L;
		t.timestamp       = 1_790_000_000_000L;
		t.offerInstanceId = 17_900_000_000_003L;

		JsonObject row = client.tradeJson(t);
		String json = new Gson().toJson(row);

		assertTrue(json, json.contains("\"price_each\":8200000000,"));
		assertTrue(json, json.contains("\"total_gp\":8200000000,"));
		assertTrue(json, json.contains("\"offer_instance_id\":17900000000003"));
		assertEquals(8_200_000_000L, row.get("price_each").getAsLong());
		assertEquals(8_200_000_000L, row.get("total_gp").getAsLong());
		assertTrue(row.get("price_each").getAsJsonPrimitive().isNumber());
	}
}
