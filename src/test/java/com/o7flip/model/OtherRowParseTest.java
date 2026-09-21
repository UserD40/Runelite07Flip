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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.o7flip.model.Models.DecantItem;
import com.o7flip.model.Models.DipItem;
import com.o7flip.model.Models.DumpItem;
import com.o7flip.model.Models.FlipItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OtherRowParseTest
{
	private static final Gson GSON = new GsonBuilder()
		.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
		.create();

	@Test
	public void dumpsRowFillsTheDumpsCardFields()
	{
		DumpItem d = GSON.fromJson("{\"item_id\":22464,\"name\":\"Bastion potion(3)\",\"tier\":\"likely\","
			+ "\"buy_price\":10975,\"sell_price\":18672,\"profit\":7324,\"dump_score\":84,\"dump_status\":\"due_soon\","
			+ "\"last_dump_hours_ago\":0.5,\"pattern_stale\":false,\"period_hours\":2,\"dump_peak_hour_utc\":20,"
			+ "\"is_clock_aligned\":true,\"hourly_volume\":1639,\"buy_limit\":2000,\"members\":true}", DumpItem.class);
		assertEquals(22464, d.itemId);
		assertEquals("likely", d.tier);
		assertEquals(10975L, d.buyPrice);
		assertEquals(18672L, d.sellPrice);
		assertEquals(0.5, d.lastDumpHoursAgo, 0.0);
		assertEquals(Integer.valueOf(2), d.periodHours);
		assertEquals(Integer.valueOf(20), d.dumpPeakHourUtc);
		assertTrue(d.isClockAligned);
		assertEquals(Boolean.FALSE, d.patternStale);
	}

	@Test
	public void dipsRowFillsTheDipsCardFields()
	{
		DipItem d = GSON.fromJson("{\"item_id\":4724,\"name\":\"Guthan's helm\",\"buy_price\":230005,"
			+ "\"avg_24h_buy\":297306,\"dip_pct\":-22.6,\"hourly_volume\":28,\"daily_volume\":355,\"buy_limit\":15,"
			+ "\"members\":true}", DipItem.class);
		assertEquals(4724, d.itemId);
		assertEquals(230005L, d.buyPrice);
		assertEquals(Long.valueOf(297306), d.avg24hBuy);
		assertEquals(-22.6, d.dipPct, 0.0);
	}

	@Test
	public void decantRowCarriesTheBuyDoseItemId()
	{
		DecantItem d = GSON.fromJson("{\"item_id\":30131,\"name\":\"Prayer regeneration potion\","
			+ "\"strategy\":\"Buy 2-dose, sell as 3-dose\",\"profit_per_4dose\":14956,\"profit_per_dose\":3739,"
			+ "\"roi_pct\":24.8,\"daily_volume\":280,\"buy_dose\":2,\"sell_dose\":3}", DecantItem.class);
		assertEquals(30131, d.itemId);
		assertEquals("Prayer regeneration potion", d.name);
		assertEquals(2, d.buyDose);
		assertEquals(3, d.sellDose);
		assertEquals(24.8, d.roiPct, 0.0);
		assertEquals(0L, d.buyPrice);
	}

	@Test
	public void genericRowCarriesBadgeSignalAndNegativeProfit()
	{
		FlipItem f = GSON.fromJson("{\"item_id\":2581,\"name\":\"Robin Hood hat\",\"buy_price\":1870012,"
			+ "\"sell_price\":1875042,\"profit\":-32470,\"roi_pct\":-1.74,\"potential_profit\":0,\"buy_limit\":8,"
			+ "\"members\":true,\"flip07_score\":0,\"rec_buy_price\":null,\"rec_sell_price\":null,\"rec_profit\":null,"
			+ "\"hourly_volume\":8,\"daily_volume\":704,\"buy_age_minutes\":2,\"sell_age_minutes\":2,"
			+ "\"badge\":\"Squeezing\",\"signal\":\"+125.4% vs pre-squeeze · peak -87.8%\"}", FlipItem.class);
		assertEquals(2581, f.itemId);
		assertEquals(-32470L, f.profit);
		assertEquals(Integer.valueOf(0), f.flip07Score);
		assertNull(f.recBuyPrice);
		assertEquals("Squeezing", f.badge);
		assertEquals("+125.4% vs pre-squeeze · peak -87.8%", f.signal);
		assertEquals(Integer.valueOf(2), f.buyAgeMinutes);
	}

	@Test
	public void arbitrageRowCarriesPlanHours()
	{
		FlipItem f = GSON.fromJson("{\"item_id\":1623,\"name\":\"Uncut sapphire\",\"buy_price\":196,"
			+ "\"sell_price\":214,\"profit\":13,\"roi_pct\":6.8,\"potential_profit\":13000,\"buy_limit\":5000,"
			+ "\"members\":false,\"flip07_score\":null,\"rec_buy_price\":196,\"rec_sell_price\":214,\"rec_profit\":13,"
			+ "\"hourly_volume\":900,\"daily_volume\":21600,\"buy_age_minutes\":null,\"sell_age_minutes\":null,"
			+ "\"badge\":\"92% hit\",\"signal\":\"Buy 03:00 → sell 14:00 UTC\",\"buy_hour_utc\":3,"
			+ "\"sell_hour_utc\":14,\"sell_next_day\":false,\"units_per_batch\":1000}", FlipItem.class);
		assertEquals(Integer.valueOf(3), f.buyHourUtc);
		assertEquals(Integer.valueOf(14), f.sellHourUtc);
		assertEquals(Boolean.FALSE, f.sellNextDay);
		assertEquals(Long.valueOf(196), f.recBuyPrice);
	}

	@Test
	public void flipsRowWithoutBadgeLeavesBothNull()
	{
		FlipItem f = GSON.fromJson("{\"item_id\":1519,\"name\":\"Magic logs\",\"buy_price\":980,\"sell_price\":1050,"
			+ "\"profit\":49}", FlipItem.class);
		assertNull(f.badge);
		assertNull(f.signal);
	}
}
