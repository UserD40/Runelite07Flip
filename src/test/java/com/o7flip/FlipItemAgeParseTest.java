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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.o7flip.model.Models.FlipItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FlipItemAgeParseTest
{
	private static Gson parser()
	{
		return new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.create();
	}

	@Test
	public void parsesAgeMinutesWhenServerSendsThem()
	{
		FlipItem item = parser().fromJson(
			"{\"item_id\":26384,\"name\":\"Torva platebody\",\"buy_price\":173886268,"
				+ "\"sell_price\":178000000,\"buy_age_minutes\":8,\"sell_age_minutes\":19}",
			FlipItem.class);

		assertEquals(Integer.valueOf(8), item.buyAgeMinutes);
		assertEquals(Integer.valueOf(19), item.sellAgeMinutes);
	}

	@Test
	public void leavesAgeNullOnTodaysPayload()
	{
		FlipItem item = parser().fromJson(
			"{\"item_id\":28338,\"name\":\"Soulreaper axe\",\"buy_price\":395351394,"
				+ "\"sell_price\":402667700,\"hourly_volume\":9,\"daily_volume\":390}",
			FlipItem.class);

		assertNull(item.buyAgeMinutes);
		assertNull(item.sellAgeMinutes);
	}
}
