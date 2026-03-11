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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.o7flip.model.AlertItem;
import com.o7flip.model.AuthStatus;
import com.o7flip.model.BarrowsItem;
import com.o7flip.model.BarrowsSet;
import com.o7flip.model.DecantItem;
import com.o7flip.model.DipItem;
import com.o7flip.model.DumpItem;
import com.o7flip.model.FlipItem;
import com.o7flip.model.MoonItem;
import com.o7flip.model.MoonSet;
import com.o7flip.model.SearchResultItem;
import com.o7flip.model.SpikeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Singleton
public class O7FlipApiClient
{
	private static final Logger log = LoggerFactory.getLogger(O7FlipApiClient.class);

	private static final String    BASE_URL        = "https://07flip.com/api/runelite";
	private static final String    USER_AGENT      = "07Flip-RuneLite/1.0";
	private static final int       PAGE_LIMIT      = 10;   // items per page — must match O7FlipPanel.PAGE_SIZE
	private static final MediaType MEDIA_TYPE_JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private O7FlipConfig config;

	/** Epoch ms until which all requests should be skipped after a 429 response. */
	private volatile long backoffUntil = 0;

	/** Returns true if the client is currently in a rate-limit backoff window. */
	boolean isRateLimited()
	{
		return System.currentTimeMillis() < backoffUntil;
	}

	private void markRateLimited()
	{
		backoffUntil = System.currentTimeMillis() + 60_000;
		log.warn("[07Flip] Rate limited (429) — pausing all requests for 60s");
	}

	private void fetch(String url, Callback callback)
	{
		Request.Builder builder = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT);
		String key = config != null ? config.apiKey() : null;
		if (key != null && !key.trim().isEmpty())
		{
			builder.header("Authorization", "Bearer " + key.trim());
		}
		okHttpClient.newCall(builder.build()).enqueue(callback);
	}

	// -------------------------------------------------------------------------
	// Search
	// -------------------------------------------------------------------------

	public void fetchSearch(String query, Consumer<List<SearchResultItem>> callback)
	{
		try
		{
			String encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8");
			fetch(BASE_URL + "/search?q=" + encoded + "&limit=10", new Callback()
			{
				@Override
				public void onFailure(Call call, IOException e)
				{
					log.warn("[07Flip] fetchSearch failed: {}", e.getMessage());
					callback.accept(new ArrayList<>());
				}

				@Override
				public void onResponse(Call call, Response response) throws IOException
				{
					callback.accept(parseArray(response, "items", obj ->
					{
						SearchResultItem item = new SearchResultItem();
						item.itemId         = getInt(obj, "item_id", 0);
						item.name           = getString(obj, "name", "Unknown");
						item.buyPrice       = getLongOrNull(obj, "buy_price");
						item.sellPrice      = getLongOrNull(obj, "sell_price");
						item.margin         = getLongOrNull(obj, "margin");
						item.profit         = getLongOrNull(obj, "profit");
						item.roi            = getDoubleOrNull(obj, "roi");
						item.hourlyVolume   = getIntOrNull(obj, "hourly_volume");
						item.dailyVolume    = getIntOrNull(obj, "daily_volume");
						item.buyLimit       = getInt(obj, "buy_limit", 0);
						item.members        = getBool(obj, "members", false);
						item.highAlch       = getIntOrNull(obj, "high_alch");
						item.lastUpdated    = getString(obj, "last_updated", "");
						item.dataAgeMinutes = getIntOrNull(obj, "data_age_minutes");
						return item;
					}));
				}
			});
		}
		catch (Exception e)
		{
			log.warn("[07Flip] fetchSearch encode error: {}", e.getMessage());
			callback.accept(new ArrayList<>());
		}
	}

	// -------------------------------------------------------------------------
	// Auth
	// -------------------------------------------------------------------------

	public void fetchAuthStatus(Consumer<AuthStatus> callback)
	{
		fetch(BASE_URL + "/auth", new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				// Network failure — leave existing auth state unchanged rather than resetting to anonymous.
				log.warn("[07Flip] fetchAuthStatus failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (!response.isSuccessful() || response.body() == null)
				{
					// Server error — leave existing auth state unchanged.
					log.warn("[07Flip] fetchAuthStatus HTTP {}", response.code());
					return;
				}
				try
				{
					JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
					AuthStatus status = new AuthStatus();
					status.authenticated = getBool(json, "authenticated", false);
					status.premium       = getBool(json, "premium",       false);
					callback.accept(status);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] Auth parse error: {}", e.getMessage());
				}
			}
		});
	}

	// -------------------------------------------------------------------------
	// Paginated endpoints — callback receives (items, serverTotal)
	// serverTotal defaults to items.size() when the server does not return "total"
	// -------------------------------------------------------------------------

	public void fetchFlips(String preset, long minProfit, long priceMin, long priceMax,
	                       int page, BiConsumer<List<FlipItem>, Integer> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/flips?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (preset != null && !preset.isEmpty())
		{
			url.append("&preset=").append(preset);
		}
		if (minProfit > 0)
		{
			url.append("&minProfit=").append(minProfit);
		}
		if (priceMin > 0)
		{
			url.append("&priceMin=").append(priceMin);
		}
		if (priceMax < Long.MAX_VALUE)
		{
			url.append("&priceMax=").append(priceMax);
		}
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchFlips failed: {}", e.getMessage());
				callback.accept(new ArrayList<>(), 0);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				parsePagedResponse(response, "flips", O7FlipApiClient.this::parseFlipItem, callback);
			}
		});
	}

	public void fetchSpikes(String sort, int page, BiConsumer<List<SpikeItem>, Integer> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/spikes?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (sort != null && !sort.isEmpty())
		{
			url.append("&sort=").append(sort);
		}
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchSpikes failed: {}", e.getMessage());
				callback.accept(new ArrayList<>(), 0);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				parsePagedResponse(response, "spikes", O7FlipApiClient.this::parseSpikeItem, callback);
			}
		});
	}

	public void fetchDips(String sort, int page, BiConsumer<List<DipItem>, Integer> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/dips?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (sort != null && !sort.isEmpty())
		{
			url.append("&sort=").append(sort);
		}
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchDips failed: {}", e.getMessage());
				callback.accept(new ArrayList<>(), 0);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				parsePagedResponse(response, "dips", O7FlipApiClient.this::parseDipItem, callback);
			}
		});
	}

	public void fetchDumps(String sort, long minProfit, long priceMin, long priceMax,
	                       int page, BiConsumer<List<DumpItem>, Integer> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/dumps?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (sort != null && !sort.isEmpty())
		{
			url.append("&sort=").append(sort);
		}
		if (minProfit > 0)
		{
			url.append("&minProfit=").append(minProfit);
		}
		if (priceMin > 0)
		{
			url.append("&priceMin=").append(priceMin);
		}
		if (priceMax < Long.MAX_VALUE)
		{
			url.append("&priceMax=").append(priceMax);
		}
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchDumps failed: {}", e.getMessage());
				callback.accept(new ArrayList<>(), 0);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				parsePagedResponse(response, "dumps", O7FlipApiClient.this::parseDumpItem, callback);
			}
		});
	}

	public void fetchAlerts(int page, BiConsumer<List<AlertItem>, Integer> callback)
	{
		String url = BASE_URL + "/alerts?limit=" + PAGE_LIMIT + "&page=" + page;
		fetch(url, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchAlerts failed: {}", e.getMessage());
				callback.accept(new ArrayList<>(), 0);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				parsePagedResponse(response, "alerts", O7FlipApiClient.this::parseAlertItem, callback);
			}
		});
	}

	// -------------------------------------------------------------------------
	// Bundle endpoint — single POST replacing all scheduled individual calls
	// -------------------------------------------------------------------------

	public void fetchBundle(
		JsonObject sections,
		BiConsumer<List<FlipItem>, Integer>  onFlips,
		BiConsumer<List<SpikeItem>, Integer> onSpikes,
		BiConsumer<List<DipItem>, Integer>   onDips,
		BiConsumer<List<DumpItem>, Integer>  onDumps,
		BiConsumer<List<AlertItem>, Integer> onAlerts,
		Consumer<List<BarrowsSet>>           onBarrows,
		Consumer<List<MoonSet>>              onMoon,
		Consumer<List<DecantItem>>           onDecanting
	)
	{
		JsonObject body = new JsonObject();
		body.add("sections", sections);
		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(body));

		Request.Builder builder = new Request.Builder()
			.url(BASE_URL + "/bundle")
			.post(requestBody)
			.header("User-Agent", USER_AGENT);
		String key = config != null ? config.apiKey() : null;
		if (key != null && !key.trim().isEmpty())
		{
			builder.header("Authorization", "Bearer " + key.trim());
		}

		okHttpClient.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchBundle failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (response.code() == 429)
				{
					markRateLimited();
					return;
				}
				if (!response.isSuccessful() || response.body() == null)
				{
					log.warn("[07Flip] fetchBundle HTTP {}", response.code());
					return;
				}
				try
				{
					JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);

					if (onFlips != null && root.has("flips"))
					{
						JsonObject sec = root.getAsJsonObject("flips");
						List<FlipItem> items = parseArray(sec, "flips", O7FlipApiClient.this::parseFlipItem);
						onFlips.accept(items, getInt(sec, "total", items.size()));
					}
					if (onSpikes != null && root.has("spikes"))
					{
						JsonObject sec = root.getAsJsonObject("spikes");
						List<SpikeItem> items = parseArray(sec, "spikes", O7FlipApiClient.this::parseSpikeItem);
						onSpikes.accept(items, getInt(sec, "total", items.size()));
					}
					if (onDips != null && root.has("dips"))
					{
						JsonObject sec = root.getAsJsonObject("dips");
						List<DipItem> items = parseArray(sec, "dips", O7FlipApiClient.this::parseDipItem);
						onDips.accept(items, getInt(sec, "total", items.size()));
					}
					if (onDumps != null && root.has("dumps"))
					{
						JsonObject sec = root.getAsJsonObject("dumps");
						List<DumpItem> items = parseArray(sec, "dumps", O7FlipApiClient.this::parseDumpItem);
						onDumps.accept(items, getInt(sec, "total", items.size()));
					}
					if (onAlerts != null && root.has("alerts"))
					{
						JsonObject sec = root.getAsJsonObject("alerts");
						List<AlertItem> items = parseArray(sec, "alerts", O7FlipApiClient.this::parseAlertItem);
						onAlerts.accept(items, getInt(sec, "total", items.size()));
					}
					if (onBarrows != null && root.has("barrows"))
					{
						onBarrows.accept(parseArray(root.getAsJsonObject("barrows"), "sets", O7FlipApiClient.this::parseBarrowsSet));
					}
					if (onMoon != null && root.has("moon"))
					{
						onMoon.accept(parseArray(root.getAsJsonObject("moon"), "sets", O7FlipApiClient.this::parseMoonSet));
					}
					if (onDecanting != null && root.has("decanting"))
					{
						onDecanting.accept(parseArray(root.getAsJsonObject("decanting"), "decants", O7FlipApiClient.this::parseDecantItem));
					}
				}
				catch (Exception e)
				{
					log.warn("[07Flip] fetchBundle parse error: {}", e.getMessage());
				}
			}
		});
	}

	// -------------------------------------------------------------------------
	// Non-paginated endpoints (full dataset loaded once, client-side pagination)
	// -------------------------------------------------------------------------

	public void fetchBarrows(int smithingLevel, Consumer<List<BarrowsSet>> callback)
	{
		fetch(BASE_URL + "/barrows?set=all&smithingLevel=" + smithingLevel, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchBarrows failed: {}", e.getMessage());
				callback.accept(new ArrayList<>());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				callback.accept(parseArray(response, "sets", O7FlipApiClient.this::parseBarrowsSet));
			}
		});
	}

	public void fetchBarrowsDetail(String setParam, int smithingLevel, Consumer<BarrowsSet> callback)
	{
		fetch(BASE_URL + "/barrows?set=" + setParam + "&smithingLevel=" + smithingLevel, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchBarrowsDetail failed: {}", e.getMessage());
				callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				List<BarrowsSet> sets = parseArray(response, "sets", O7FlipApiClient.this::parseBarrowsSet);
				callback.accept(sets.isEmpty() ? null : sets.get(0));
			}
		});
	}

	private BarrowsSet parseBarrowsSet(JsonObject obj)
	{
		BarrowsSet s = new BarrowsSet();
		s.iconItemId         = getInt(obj, "icon_item_id", 0);
		s.setName            = getString(obj, "set_name", "");
		s.shortName          = getString(obj, "short_name", "");
		s.setParam           = getString(obj, "set_param", "");
		s.totalBrokenCost    = getLong(obj, "total_broken_cost", 0);
		s.totalNpcRepairCost = getLong(obj, "total_npc_repair_cost", 0);
		s.totalPohRepairCost = getLong(obj, "total_poh_repair_cost", 0);
		s.npcProfit          = getLong(obj, "npc_profit", 0);
		s.pohProfit          = getLong(obj, "poh_profit", 0);
		s.setProfit          = getLong(obj, "set_profit", 0);
		s.bestProfit         = getLong(obj, "best_profit", 0);
		s.bestStrategy       = getString(obj, "best_strategy", "sell_individual");
		s.dailyVolume        = getInt(obj, "daily_volume", 0);

		// Derive setParam if server did not return it
		if (s.setParam.isEmpty() && !s.shortName.isEmpty())
		{
			s.setParam = s.shortName.replace("'s", "").toLowerCase() + "s";
		}

		// Items — present in detail response (?set=X), absent in list response (?set=all)
		JsonArray itemsArr = obj.getAsJsonArray("items");
		if (itemsArr != null)
		{
			for (int i = 0; i < itemsArr.size(); i++)
			{
				try
				{
					JsonObject io = itemsArr.get(i).getAsJsonObject();
					BarrowsItem item = new BarrowsItem();
					item.itemIdBroken      = getInt(io, "item_id_broken", 0);
					item.itemIdRepaired    = getInt(io, "item_id_repaired", 0);
					item.name              = getString(io, "name", "");
					item.slot              = getString(io, "slot", "");
					item.brokenBuyPrice    = getLong(io, "broken_buy_price", 0);
					item.repairedSellPrice = getLong(io, "repaired_sell_price", 0);
					item.repairedAfterTax  = getLong(io, "repaired_after_tax", 0);
					item.npcRepairCost     = getLong(io, "npc_repair_cost", 0);
					item.pohRepairCost     = getLong(io, "poh_repair_cost", 0);
					item.npcProfit         = getLong(io, "npc_profit", 0);
					item.pohProfit         = getLong(io, "poh_profit", 0);
					item.npcRoiPct         = getDouble(io, "npc_roi_pct", 0);
					item.pohRoiPct         = getDouble(io, "poh_roi_pct", 0);
					item.dailyVolume       = getInt(io, "daily_volume", 0);
					s.items.add(item);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] Skipping malformed barrows item at index {}: {}", i, e.getMessage());
				}
			}
		}
		return s;
	}

	public void fetchMoon(int smithingLevel, Consumer<List<MoonSet>> callback)
	{
		fetch(BASE_URL + "/moon?smithingLevel=" + smithingLevel, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchMoon failed: {}", e.getMessage());
				callback.accept(new ArrayList<>());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				callback.accept(parseArray(response, "sets", O7FlipApiClient.this::parseMoonSet));
			}
		});
	}

	public void fetchDecanting(Consumer<List<DecantItem>> callback)
	{
		fetch(BASE_URL + "/decanting", new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchDecanting failed: {}", e.getMessage());
				callback.accept(new ArrayList<>());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				callback.accept(parseArray(response, "decants", O7FlipApiClient.this::parseDecantItem));
			}
		});
	}

	// -------------------------------------------------------------------------
	// Per-type parsers (shared by individual fetch methods and fetchBundle)
	// -------------------------------------------------------------------------

	private FlipItem parseFlipItem(JsonObject obj)
	{
		FlipItem item = new FlipItem();
		item.itemId          = getInt(obj, "item_id", 0);
		item.name            = getString(obj, "name", "Unknown");
		item.buyPrice        = getLong(obj, "buy_price", 0);
		item.sellPrice       = getLong(obj, "sell_price", 0);
		item.profit          = getLong(obj, "profit", 0);
		item.roiPct          = getDouble(obj, "roi_pct", 0);
		item.potentialProfit = getLong(obj, "potential_profit", 0);
		item.buyLimit        = getInt(obj, "buy_limit", 0);
		item.members         = getBool(obj, "members", true);
		return item;
	}

	private SpikeItem parseSpikeItem(JsonObject obj)
	{
		SpikeItem item = new SpikeItem();
		item.itemId       = getInt(obj, "item_id", 0);
		item.name         = getString(obj, "name", "Unknown");
		item.buyPrice     = getLong(obj, "buy_price", 0);
		item.avg24hBuy    = getLong(obj, "avg_24h_buy", 0);
		item.spikePct     = getDouble(obj, "spike_pct", 0);
		item.hourlyVolume = getInt(obj, "hourly_volume", 0);
		item.dailyVolume  = getInt(obj, "daily_volume", 0);
		item.buyLimit     = getInt(obj, "buy_limit", 0);
		item.members      = getBool(obj, "members", true);
		item.lastUpdated  = getString(obj, "last_updated", "");
		return item;
	}

	private DipItem parseDipItem(JsonObject obj)
	{
		DipItem item = new DipItem();
		item.itemId       = getInt(obj, "item_id", 0);
		item.name         = getString(obj, "name", "Unknown");
		item.buyPrice     = getLong(obj, "buy_price", 0);
		item.avg24hBuy    = getLong(obj, "avg_24h_buy", 0);
		item.dipPct       = getDouble(obj, "dip_pct", 0);
		item.hourlyVolume = getInt(obj, "hourly_volume", 0);
		item.dailyVolume  = getInt(obj, "daily_volume", 0);
		item.buyLimit     = getInt(obj, "buy_limit", 0);
		item.members      = getBool(obj, "members", true);
		item.lastUpdated  = getString(obj, "last_updated", "");
		return item;
	}

	private DumpItem parseDumpItem(JsonObject obj)
	{
		DumpItem item = new DumpItem();
		item.itemId           = getInt(obj, "item_id", 0);
		item.name             = getString(obj, "name", "Unknown");
		item.buyPrice         = getLong(obj, "buy_price", 0);
		item.sellPrice        = getLong(obj, "sell_price", 0);
		item.profit           = getLong(obj, "profit", 0);
		// Fallback: older API versions use current_price instead of buy_price
		if (item.buyPrice == 0)
		{
			item.buyPrice = getLong(obj, "current_price", 0);
		}
		item.dumpScore        = getInt(obj, "dump_score", 0);
		item.dumpPct          = getDouble(obj, "dump_pct", 0);
		item.dumpStatus       = getString(obj, "dump_status", "none");
		item.lastDumpHoursAgo = getDoubleOrNull(obj, "last_dump_hours_ago");
		item.nextDumpHours    = getDoubleOrNull(obj, "next_dump_hours");
		item.burstCount       = getIntOrNull(obj, "burst_count");
		item.hourlyVolume     = getInt(obj, "hourly_volume", 0);
		item.buyLimit         = getInt(obj, "buy_limit", 0);
		item.members          = getBool(obj, "members", true);
		return item;
	}

	private AlertItem parseAlertItem(JsonObject obj)
	{
		AlertItem alert = new AlertItem();
		alert.itemId       = getInt(obj, "item_id", 0);
		alert.name         = getString(obj, "name", "Unknown");
		alert.tier         = getString(obj, "tier", "");
		alert.currentPrice = getLong(obj, "current_price", 0);
		alert.sellTarget   = getLong(obj, "sell_target", 0);
		alert.upsidePct    = getDouble(obj, "upside_pct", 0);
		alert.holdTime     = getString(obj, "hold_time", "");
		alert.high90d      = getLong(obj, "high_90d", 0);
		alert.low90d       = getLong(obj, "low_90d", 0);
		alert.drawdownPct  = getDouble(obj, "drawdown_pct", 0);
		alert.detectedAt   = getString(obj, "detected_at", "");
		return alert;
	}

	private MoonSet parseMoonSet(JsonObject obj)
	{
		MoonSet s = new MoonSet();
		s.setName             = getString(obj, "set_name", "");
		s.shortName           = getString(obj, "short_name", "");
		s.combatStyle         = getString(obj, "combat_style", "");
		s.setId               = getInt(obj, "set_id", 0);
		s.iconItemId          = getInt(obj, "icon_item_id", 0);
		s.totalBrokenCost     = getLong(obj, "total_broken_cost", 0);
		s.totalNpcRepairCost  = getLong(obj, "total_npc_repair_cost", 0);
		s.totalPohRepairCost  = getLong(obj, "total_poh_repair_cost", 0);
		s.npcProfit           = getLong(obj, "npc_profit", 0);
		s.pohProfit           = getLong(obj, "poh_profit", 0);
		s.setPrice            = getLong(obj, "set_price", 0);
		s.setProfit           = getLong(obj, "set_profit", 0);
		s.bestStrategy        = getString(obj, "best_strategy", "sell_individual");
		s.bestProfit          = getLong(obj, "best_profit", 0);
		JsonArray itemsArr = obj.getAsJsonArray("items");
		if (itemsArr != null)
		{
			for (int i = 0; i < itemsArr.size(); i++)
			{
				try
				{
					JsonObject io = itemsArr.get(i).getAsJsonObject();
					MoonItem mi = new MoonItem();
					mi.itemIdBroken      = getInt(io, "item_id_broken", 0);
					mi.itemIdRepaired    = getInt(io, "item_id_repaired", 0);
					mi.name              = getString(io, "name", "");
					mi.slot              = getString(io, "slot", "");
					mi.degrades          = getBool(io, "degrades", false);
					mi.brokenBuyPrice    = getLong(io, "broken_buy_price", 0);
					mi.repairedSellPrice = getLong(io, "repaired_sell_price", 0);
					mi.repairedAfterTax  = getLong(io, "repaired_after_tax", 0);
					mi.npcRepairCost     = getLong(io, "npc_repair_cost", 0);
					mi.pohRepairCost     = getLong(io, "poh_repair_cost", 0);
					mi.npcProfit         = getLong(io, "npc_profit", 0);
					mi.pohProfit         = getLong(io, "poh_profit", 0);
					mi.npcRoiPct         = getDouble(io, "npc_roi_pct", 0);
					mi.pohRoiPct         = getDouble(io, "poh_roi_pct", 0);
					s.items.add(mi);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] Skipping malformed moon item at index {}: {}", i, e.getMessage());
				}
			}
		}
		return s;
	}

	private DecantItem parseDecantItem(JsonObject obj)
	{
		DecantItem item = new DecantItem();
		item.itemId           = getInt(obj, "item_id", 0);
		item.potionName       = getString(obj, "potion_name", "Unknown");
		item.strategy         = getString(obj, "strategy", "");
		item.profitPer4dose   = getLong(obj, "profit_per_4dose", 0);
		item.profitPerDose    = getLong(obj, "profit_per_dose", 0);
		item.roiPct           = getDouble(obj, "roi_pct", 0);
		item.minHourlyVolume  = getInt(obj, "min_hourly_volume", 0);
		item.dailyVolume      = getInt(obj, "daily_volume", 0);
		item.buyDose          = getInt(obj, "buy_dose", 0);
		item.sellDose         = getInt(obj, "sell_dose", 0);
		return item;
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	@FunctionalInterface
	private interface JsonMapper<T>
	{
		T map(JsonObject obj);
	}

	/**
	 * Parses a paginated response. Extracts the items array and the "total" field.
	 * If the server does not include "total", falls back to items.size().
	 */
	private <T> void parsePagedResponse(Response response, String arrayKey,
	                                    JsonMapper<T> mapper,
	                                    BiConsumer<List<T>, Integer> callback)
	{
		if (response.code() == 429)
		{
			markRateLimited();
		}
		if (!response.isSuccessful() || response.body() == null)
		{
			log.warn("[07Flip] HTTP {} for '{}'", response.code(), arrayKey);
			callback.accept(new ArrayList<>(), 0);
			return;
		}
		try
		{
			JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
			List<T> items = parseArray(json, arrayKey, mapper);
			int total = getInt(json, "total", items.size());
			callback.accept(items, total);
		}
		catch (Exception e)
		{
			log.warn("[07Flip] Parse error for '{}': {}", arrayKey, e.getMessage());
			callback.accept(new ArrayList<>(), 0);
		}
	}

	/** Parses an array from an already-decoded JsonObject. */
	private <T> List<T> parseArray(JsonObject json, String arrayKey, JsonMapper<T> mapper)
	{
		List<T> result = new ArrayList<>();
		JsonArray arr = json.getAsJsonArray(arrayKey);
		if (arr == null)
		{
			return result;
		}
		for (int i = 0; i < arr.size(); i++)
		{
			try
			{
				result.add(mapper.map(arr.get(i).getAsJsonObject()));
			}
			catch (Exception e)
			{
				log.warn("[07Flip] Skipping malformed item at index {}: {}", i, e.getMessage());
			}
		}
		return result;
	}

	/** Parses an array from an HTTP response (used by non-paginated endpoints). */
	private <T> List<T> parseArray(Response response, String arrayKey, JsonMapper<T> mapper)
	{
		if (response.code() == 429)
		{
			markRateLimited();
		}
		if (!response.isSuccessful() || response.body() == null)
		{
			log.warn("[07Flip] HTTP {} for key '{}'", response.code(), arrayKey);
			return new ArrayList<>();
		}
		try
		{
			JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
			return parseArray(json, arrayKey, mapper);
		}
		catch (Exception e)
		{
			log.warn("[07Flip] Parse error for '{}': {}", arrayKey, e.getMessage());
			return new ArrayList<>();
		}
	}

	private String getString(JsonObject obj, String key, String def)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? def : el.getAsString();
	}

	private long getLong(JsonObject obj, String key, long def)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? def : el.getAsLong();
	}

	private int getInt(JsonObject obj, String key, int def)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? def : el.getAsInt();
	}

	private double getDouble(JsonObject obj, String key, double def)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? def : el.getAsDouble();
	}

	private boolean getBool(JsonObject obj, String key, boolean def)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? def : el.getAsBoolean();
	}

	private Double getDoubleOrNull(JsonObject obj, String key)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? null : el.getAsDouble();
	}

	private Integer getIntOrNull(JsonObject obj, String key)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? null : el.getAsInt();
	}

	private Long getLongOrNull(JsonObject obj, String key)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? null : el.getAsLong();
	}
}
