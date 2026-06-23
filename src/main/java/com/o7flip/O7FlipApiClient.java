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
import com.o7flip.model.AuthStatus;
import com.o7flip.model.DumpItem;
import com.o7flip.model.FlipItem;
import com.o7flip.model.DipItem;
import com.o7flip.model.HighAlchItem;
import com.o7flip.model.ItemInsights;
import com.o7flip.model.OptimizeResult;
import com.o7flip.model.RecommendedPrices;
import com.o7flip.model.SearchResultItem;
import com.o7flip.model.SpikeItem;
import com.o7flip.model.TrackerStats;
import com.o7flip.model.TradeRecord;
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

	private volatile long backoffUntil = 0;
	private int rateLimitIncidents = 0;

	private static final long RATE_LIMIT_BASE_MS  = 60_000L;
	private static final long RATE_LIMIT_MAX_MS   = 5L * 60_000L;
	private static final long RATE_LIMIT_RESET_MS = 5L * 60_000L;

	private volatile boolean loggedKeySanitisation = false;

	boolean isRateLimited()
	{
		return System.currentTimeMillis() < backoffUntil;
	}

	private synchronized void markRateLimited(Response response)
	{
		long now     = System.currentTimeMillis();
		long retryMs = parseRetryAfterMs(response);          // -1 when absent/unparseable
		long base    = retryMs > 0 ? retryMs : RATE_LIMIT_BASE_MS;

		if (now < backoffUntil)
		{
			if (retryMs > 0)
			{
				long until = now + withJitter(Math.min(base, RATE_LIMIT_MAX_MS));
				if (until > backoffUntil) backoffUntil = until;
			}
			return;
		}

		if (now - backoffUntil > RATE_LIMIT_RESET_MS) rateLimitIncidents = 0;
		rateLimitIncidents++;

		double mult     = Math.min(Math.pow(2, rateLimitIncidents - 1), 8.0);   // 1,2,4,8
		long   cooldown = Math.min((long) (base * mult), RATE_LIMIT_MAX_MS);
		backoffUntil    = now + withJitter(cooldown);
		log.warn("[07Flip] Rate limited (429) — pausing requests ~{}s (incident #{}, retry-after={})",
			(backoffUntil - now) / 1000, rateLimitIncidents,
			retryMs > 0 ? (retryMs / 1000) + "s" : "none");
	}

	private static long withJitter(long ms)
	{
		long jittered = ms + (long) (ms * 0.20 * Math.random());
		return Math.min(jittered, RATE_LIMIT_MAX_MS);
	}

	private static long parseRetryAfterMs(Response response)
	{
		if (response == null) return -1L;
		String raw = response.header("Retry-After");
		if (raw == null) return -1L;
		raw = raw.trim();
		if (raw.isEmpty()) return -1L;
		try
		{
			long secs = Long.parseLong(raw);
			return secs > 0 ? secs * 1000L : -1L;
		}
		catch (NumberFormatException notSeconds)
		{
		}
		try
		{
			java.time.ZonedDateTime when = java.time.ZonedDateTime.parse(
				raw, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
			long delta = when.toInstant().toEpochMilli() - System.currentTimeMillis();
			return delta > 0 ? delta : -1L;
		}
		catch (Exception notADate)
		{
			return -1L;
		}
	}

	private String sanitizedApiKey()
	{
		String raw = config != null ? config.apiKey() : null;
		if (raw == null)
		{
			return null;
		}
		String cleaned = raw.replaceAll("[^A-Za-z0-9-]", "");
		if (!loggedKeySanitisation && raw.length() != cleaned.length())
		{
			log.debug("[07Flip] API key sanitised: raw length {} -> cleaned length {}",
				raw.length(), cleaned.length());
			loggedKeySanitisation = true;
		}
		return cleaned.isEmpty() ? null : cleaned;
	}

	private void fetch(String url, Callback callback)
	{
		if (isRateLimited())
		{
			callback.onFailure(null, new IOException("07Flip: request skipped — rate-limit backoff active"));
			return;
		}
		Request.Builder builder = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT);
		String key = sanitizedApiKey();
		if (key != null)
		{
			builder.header("Authorization", "Bearer " + key);
		}
		okHttpClient.newCall(builder.build()).enqueue(callback);
	}

	// GET a paged list endpoint with the standard log-and-empty-on-failure + parse-on-success
	// callback. label is used only for the failure warn line.
	private <T> void fetchPaged(String url, String label, String arrayKey,
	                            JsonMapper<T> mapper, BiConsumer<List<T>, Integer> callback)
	{
		fetch(url, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] {} failed: {}", label, e.getMessage());
				callback.accept(new ArrayList<>(), 0);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				parsePagedResponse(response, arrayKey, mapper, callback);
			}
		});
	}

	// GET an endpoint that yields a plain list (no total), with log-and-empty-on-failure +
	// parseArray-on-success. label is used only for the failure warn line.
	private <T> void fetchList(String url, String label, String arrayKey,
	                           JsonMapper<T> mapper, Consumer<List<T>> callback)
	{
		fetch(url, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] {} failed: {}", label, e.getMessage());
				callback.accept(new ArrayList<>());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				callback.accept(parseArray(response, arrayKey, mapper));
			}
		});
	}

	@FunctionalInterface
	private interface ResponseParser<T>
	{
		T parse(Response response) throws IOException;
	}

	// GET an endpoint parsed via a custom Response parser, with log-and-default-on-failure.
	// The parser owns success/HTTP handling; the default is only used when the request itself
	// fails. label is used only for the failure warn line.
	private <T> void fetchParsed(String url, String label,
	                             ResponseParser<T> parser,
	                             T emptyDefault, Consumer<T> callback)
	{
		fetch(url, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] {} failed: {}", label, e.getMessage());
				callback.accept(emptyDefault);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				callback.accept(parser.parse(response));
			}
		});
	}


	public void fetchSearch(String query, Consumer<List<SearchResultItem>> callback)
	{
		try
		{
			String encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8");
			fetchList(BASE_URL + "/v2/search?q=" + encoded + "&limit=10", "fetchSearch", "items", obj ->
			{
				SearchResultItem item = new SearchResultItem();
				item.itemId         = getInt(obj, "item_id", 0);
				item.name           = getString(obj, "name", "Unknown");
				item.buyPrice       = getLongOrNull(obj, "buy_price");
				item.sellPrice      = getLongOrNull(obj, "sell_price");
				item.margin         = getLongOrNull(obj, "margin");
				item.profit         = getLongOrNull(obj, "profit");
				item.roi            = getDoubleOrNull(obj, "roi");
				item.recBuyPrice    = getLongOrNull(obj, "rec_buy_price");
				item.recSellPrice   = getLongOrNull(obj, "rec_sell_price");
				item.recProfit      = getLongOrNull(obj, "rec_profit");
				item.hourlyVolume   = getIntOrNull(obj, "hourly_volume");
				item.dailyVolume    = getIntOrNull(obj, "daily_volume");
				item.buyLimit       = getInt(obj, "buy_limit", 0);
				item.members        = getBool(obj, "members", false);
				item.highAlch       = getIntOrNull(obj, "high_alch");
				item.lastUpdated    = getString(obj, "last_updated", "");
				return item;
			}, callback);
		}
		catch (Exception e)
		{
			log.warn("[07Flip] fetchSearch encode error: {}", e.getMessage());
			callback.accept(new ArrayList<>());
		}
	}


	public void postTradeRecordsBulk(List<TradeRecord> trades, Consumer<BulkSyncResult> callback)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			if (callback != null) callback.accept(BulkSyncResult.empty(false));
			return;
		}
		if (trades == null || trades.isEmpty())
		{
			if (callback != null) callback.accept(BulkSyncResult.empty(true));
			return;
		}
		if (isRateLimited())
		{
			if (callback != null) callback.accept(BulkSyncResult.empty(false));
			return;
		}

		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		final List<TradeRecord> sentTrades = new ArrayList<>();
		for (TradeRecord t : trades)
		{
			if (t == null || t.quantity <= 0) continue;
			JsonObject row = new JsonObject();
			row.addProperty("item_id",   t.itemId);
			row.addProperty("name",      t.name);
			row.addProperty("is_buy",    t.isBuy);
			row.addProperty("quantity",  t.quantity);
			row.addProperty("price_each", t.priceEach);
			row.addProperty("total_gp",  t.totalGp);
			row.addProperty("timestamp", t.timestamp);
			row.addProperty("partial",   t.partial);
			if (t.offerInstanceId != null)
			{
				row.addProperty("offer_instance_id", t.offerInstanceId);
			}
			arr.add(row);
			sentTrades.add(t);
		}
		if (arr.size() == 0)
		{
			if (callback != null) callback.accept(BulkSyncResult.empty(true));
			return;
		}
		body.add("trades", arr);

		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(body));
		Request request = new Request.Builder()
			.url(BASE_URL + "/tracker/bulk")
			.post(requestBody)
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] postTradeRecordsBulk failed: {}", e.getMessage());
				if (callback != null) callback.accept(BulkSyncResult.empty(false));
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429) markRateLimited(response);
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] postTradeRecordsBulk HTTP {}", response.code());
						if (callback != null) callback.accept(BulkSyncResult.empty(false));
						return;
					}
					JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
					boolean ok      = getBool(root, "ok", false);
					int accepted    = getInt(root,  "accepted",   0);
					int duplicates  = getInt(root,  "duplicates", 0);
					int rejected    = root.has("rejected") && root.get("rejected").isJsonArray()
						? root.getAsJsonArray("rejected").size() : 0;
					java.util.Map<Long, Long> ids = parseBulkTradeIds(root, sentTrades);
					if (callback != null) callback.accept(new BulkSyncResult(ok, accepted, duplicates, rejected, ids));
				}
				catch (Exception e)
				{
					log.warn("[07Flip] postTradeRecordsBulk parse error: {}", e.getMessage());
					if (callback != null) callback.accept(BulkSyncResult.empty(false));
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	private java.util.Map<Long, Long> parseBulkTradeIds(JsonObject root, List<TradeRecord> sentTrades)
	{
		java.util.Map<Long, Long> out = new java.util.HashMap<>();
		if (root == null || !root.has("trades") || !root.get("trades").isJsonArray())
		{
			return out;
		}
		JsonArray arr = root.getAsJsonArray("trades");
		for (int i = 0; i < arr.size(); i++)
		{
			try
			{
				JsonObject t = arr.get(i).getAsJsonObject();
				int idx = getInt(t, "index", -1);
				Long tradeId = getLongOrNull(t, "trade_id");
				if (idx < 0 || idx >= sentTrades.size() || tradeId == null)
				{
					continue;
				}
				TradeRecord sent = sentTrades.get(idx);
				if (sent != null && sent.offerInstanceId != null)
				{
					out.put(sent.offerInstanceId, tradeId);
				}
			}
			catch (Exception ignored)
			{
			}
		}
		return out;
	}

	public static final class BulkSyncResult
	{
		public final boolean ok;
		public final int accepted;
		public final int duplicates;
		public final int rejected;
		public final java.util.Map<Long, Long> tradeIdsByOfferInstanceId;

		BulkSyncResult(boolean ok, int accepted, int duplicates, int rejected,
			java.util.Map<Long, Long> tradeIdsByOfferInstanceId)
		{
			this.ok                        = ok;
			this.accepted                  = accepted;
			this.duplicates                = duplicates;
			this.rejected                  = rejected;
			this.tradeIdsByOfferInstanceId = tradeIdsByOfferInstanceId != null
				? tradeIdsByOfferInstanceId
				: java.util.Collections.emptyMap();
		}

		static BulkSyncResult empty(boolean ok)
		{
			return new BulkSyncResult(ok, 0, 0, 0, java.util.Collections.emptyMap());
		}
	}

	public void postTradeRecord(TradeRecord trade, BiConsumer<Boolean, Long> onResult)
	{
		if (isRateLimited())
		{
			if (onResult != null) onResult.accept(false, null);
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("item_id",   trade.itemId);
		body.addProperty("name",      trade.name);
		body.addProperty("is_buy",    trade.isBuy);
		body.addProperty("quantity",  trade.quantity);
		body.addProperty("price_each", trade.priceEach);
		body.addProperty("total_gp",  trade.totalGp);
		body.addProperty("timestamp", trade.timestamp);
		body.addProperty("partial",   trade.partial);
		if (trade.offerInstanceId != null)
		{
			body.addProperty("offer_instance_id", trade.offerInstanceId);
		}

		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(body));
		Request.Builder builder = new Request.Builder()
			.url(BASE_URL + "/tracker")
			.post(requestBody)
			.header("User-Agent", USER_AGENT);
		String key = sanitizedApiKey();
		if (key != null)
		{
			builder.header("Authorization", "Bearer " + key);
		}
		okHttpClient.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] postTradeRecord failed: {}", e.getMessage());
				if (onResult != null) onResult.accept(false, null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429) markRateLimited(response);
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] postTradeRecord HTTP {}", response.code());
						if (onResult != null) onResult.accept(false, null);
						return;
					}
					Long tradeId = null;
					try
					{
						JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
						tradeId = getLongOrNull(root, "trade_id");
					}
					catch (Exception parse)
					{
						log.warn("[07Flip] postTradeRecord parse error: {}", parse.getMessage());
					}
					if (onResult != null) onResult.accept(true, tradeId);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	public void fetchTrackerHistory(Long since, int limit, BiConsumer<List<TradeRecord>, Boolean> callback)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			callback.accept(new ArrayList<>(), false);
			return;
		}
		StringBuilder url = new StringBuilder(BASE_URL).append("/tracker/history?limit=").append(limit);
		if (since != null && since > 0L)
		{
			url.append("&since=").append(since);
		}
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchTrackerHistory failed: {}", e.getMessage());
				callback.accept(new ArrayList<>(), false);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429)
					{
						markRateLimited(response);
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] fetchTrackerHistory HTTP {}", response.code());
						callback.accept(new ArrayList<>(), false);
						return;
					}
					JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
					List<TradeRecord> trades = parseArray(json, "trades", obj ->
					{
						TradeRecord t = new TradeRecord();
						t.tradeId   = getLongOrNull(obj, "trade_id");
						t.serverSynced = true;
						t.itemId    = getInt(obj, "item_id", 0);
						t.name      = getString(obj, "name", "");
						t.isBuy     = getBool(obj, "is_buy", false);
						t.quantity  = getInt(obj, "quantity", 0);
						t.totalGp   = getLong(obj, "total_gp", 0L);
						t.priceEach = getLong(obj, "price_each", 0L);
						t.timestamp = getLong(obj, "timestamp", 0L);
						t.partial   = getBool(obj, "partial", false);
						return t;
					});
					boolean hasMore = getBool(json, "has_more", false);
					callback.accept(trades, hasMore);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] fetchTrackerHistory parse error: {}", e.getMessage());
					callback.accept(new ArrayList<>(), false);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	public void fetchTrackerStats(Consumer<TrackerStats> callback)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			callback.accept(null);
			return;
		}
		fetch(BASE_URL + "/tracker/stats", new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchTrackerStats failed: {}", e.getMessage());
				callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429)
					{
						markRateLimited(response);
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						if (response.code() == 404)
						{
							log.debug("[07Flip] /tracker/stats not yet available (404)");
						}
						else
						{
							log.warn("[07Flip] fetchTrackerStats HTTP {}", response.code());
						}
						callback.accept(null);
						return;
					}
					JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
					TrackerStats stats = new TrackerStats();
					stats.totalRealisedProfit = getLong(json, "total_realised_profit", 0L);
					stats.verifiedProfit      = getLong(json, "verified_profit",        0L);
					stats.declaredProfit      = getLong(json, "declared_profit",        0L);
					stats.closedCount         = getInt(json,  "closed_count",           0);
					stats.winRate             = getDouble(json, "win_rate",             0.0);
					stats.hitRate             = getDouble(json, "hit_rate",             0.0);
					stats.updatedAt           = getString(json, "updated_at",           "");

					JsonElement bestEl = json.get("best_flip");
					if (bestEl != null && !bestEl.isJsonNull() && bestEl.isJsonObject())
					{
						JsonObject best = bestEl.getAsJsonObject();
						TrackerStats.BestFlip bf = new TrackerStats.BestFlip();
						bf.itemId        = getInt(best,    "item_id",          0);
						bf.name          = getString(best, "name",             "");
						bf.profit        = getLong(best,   "profit",           0L);
						bf.source        = getString(best, "source",           "declared");
						stats.bestFlip = bf;
					}

					callback.accept(stats);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] fetchTrackerStats parse error: {}", e.getMessage());
					callback.accept(null);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	public void postFreeze(int itemId, long frozenBuy, long frozenSell, Consumer<Boolean> callback)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			if (callback != null) callback.accept(false);
			return;
		}
		if (isRateLimited())
		{
			if (callback != null) callback.accept(false);
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("frozen_buy",  frozenBuy);
		body.addProperty("frozen_sell", frozenSell);
		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(body));
		Request.Builder builder = new Request.Builder()
			.url(BASE_URL + "/v2/item/" + itemId + "/freeze")
			.post(requestBody)
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key);
		okHttpClient.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] postFreeze({}) failed: {}", itemId, e.getMessage());
				if (callback != null) callback.accept(false);
			}
			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (response.code() == 429) markRateLimited(response);
				boolean ok = response.isSuccessful();
				response.close();
				if (!ok)
				{
					log.warn("[07Flip] postFreeze({}) HTTP {}", itemId, response.code());
				}
				if (callback != null) callback.accept(ok);
			}
		});
	}

	public void postUnfreeze(int itemId, Consumer<Boolean> callback)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			if (callback != null) callback.accept(false);
			return;
		}
		if (isRateLimited())
		{
			if (callback != null) callback.accept(false);
			return;
		}
		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, "{}");
		Request.Builder builder = new Request.Builder()
			.url(BASE_URL + "/v2/item/" + itemId + "/unfreeze")
			.post(requestBody)
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key);
		okHttpClient.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] postUnfreeze({}) failed: {}", itemId, e.getMessage());
				if (callback != null) callback.accept(false);
			}
			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (response.code() == 429) markRateLimited(response);
				boolean ok = response.isSuccessful();
				response.close();
				if (!ok)
				{
					log.warn("[07Flip] postUnfreeze({}) HTTP {}", itemId, response.code());
				}
				if (callback != null) callback.accept(ok);
			}
		});
	}

	public void fetchItemInsights(int itemId, Consumer<ItemInsights> callback)
	{
		fetch(BASE_URL + "/v2/item/" + itemId, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchItemInsights({}) failed: {}", itemId, e.getMessage());
				callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429)
					{
						markRateLimited(response);
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] fetchItemInsights({}) HTTP {}", itemId, response.code());
						callback.accept(null);
						return;
					}
					JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
					callback.accept(parseItemInsights(root));
				}
				catch (Exception e)
				{
					log.warn("[07Flip] fetchItemInsights({}) parse error: {}", itemId, e.getMessage());
					callback.accept(null);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	private ItemInsights parseItemInsights(JsonObject root)
	{
		ItemInsights out = new ItemInsights();
		out.itemId        = getInt(root, "item_id", 0);
		out.name          = getString(root, "name", "Unknown");
		out.members       = getBool(root, "members", false);
		out.buyLimit      = getInt(root, "buy_limit", 0);
		out.highAlch      = getIntOrNull(root, "high_alch");
		out.lowAlch       = getIntOrNull(root, "low_alch");
		out.premiumLocked = getBool(root, "premium_locked", false);
		out.upgradeUrl    = getString(root, "upgrade_url", "");
		out.updatedAt     = getString(root, "updated_at", "");

		JsonObject cur = optObject(root, "current");
		if (cur != null)
		{
			ItemInsights.Current c = new ItemInsights.Current();
			c.buyPrice       = getLong(cur, "buy_price",  0L);
			c.sellPrice      = getLong(cur, "sell_price", 0L);
			c.margin         = getLong(cur, "margin",     0L);
			c.tax            = getLong(cur, "tax",        0L);
			c.profit         = getLong(cur, "profit",     0L);
			c.roiPct         = getDouble(cur, "roi_pct",  0.0);
			c.recBuy         = getLongOrNull(cur, "rec_buy");
			c.recSell        = getLongOrNull(cur, "rec_sell");
			c.recProfit      = getLongOrNull(cur, "rec_profit");
			c.buyAgeMinutes  = getIntOrNull(cur, "buy_age_minutes");
			c.sellAgeMinutes = getIntOrNull(cur, "sell_age_minutes");
			out.current = c;
		}

		JsonObject vol = optObject(root, "volume");
		if (vol != null)
		{
			ItemInsights.Volume v = new ItemInsights.Volume();
			v.hourly = getInt(vol, "hourly", 0);
			v.daily  = getInt(vol, "daily",  0);
			out.volume = v;
		}

		JsonObject rng = optObject(root, "ranges");
		if (rng != null)
		{
			ItemInsights.Ranges r = new ItemInsights.Ranges();
			r.high24h            = getLong(rng, "high_24h", 0L);
			r.low24h             = getLong(rng, "low_24h",  0L);
			r.high7d             = getLong(rng, "high_7d",  0L);
			r.low7d              = getLong(rng, "low_7d",   0L);
			r.high90d            = getLong(rng, "high_90d", 0L);
			r.low90d             = getLong(rng, "low_90d",  0L);
			r.position90dPct     = getDoubleOrNull(rng, "position_90d_pct");
			r.drawdownPctFrom90d = getDoubleOrNull(rng, "drawdown_pct_from_90d");
			r.daysSince90dLow    = getIntOrNull(rng, "days_since_90d_low");
			r.daysSince90dHigh   = getIntOrNull(rng, "days_since_90d_high");
			out.ranges = r;
		}

		JsonObject ind = optObject(root, "indicators");
		if (ind != null)
		{
			ItemInsights.Indicators i = new ItemInsights.Indicators();
			i.rsi14         = getDoubleOrNull(ind, "rsi_14");
			i.macdHist      = getDoubleOrNull(ind, "macd_hist");
			i.macdCross     = getStringOrNull(ind, "macd_cross");
			i.bbPositionPct = getDoubleOrNull(ind, "bb_position_pct");
			i.volSurge      = getDoubleOrNull(ind, "vol_surge");
			i.ma7d          = getLongOrNull(ind, "ma_7d");
			i.ma30d         = getLongOrNull(ind, "ma_30d");
			i.maCross       = getStringOrNull(ind, "ma_cross");
			i.pct1h         = getDoubleOrNull(ind, "pct_1h");
			i.pct24h        = getDoubleOrNull(ind, "pct_24h");
			i.pct7d         = getDoubleOrNull(ind, "pct_7d");
			i.pct30d        = getDoubleOrNull(ind, "pct_30d");
			out.indicators = i;
		}

		JsonObject liq = optObject(root, "liquidity");
		if (liq != null)
		{
			ItemInsights.Liquidity l = new ItemInsights.Liquidity();
			l.hourlyBuyVolume     = getIntOrNull(liq, "hourly_buy_volume");
			l.hourlySellVolume    = getIntOrNull(liq, "hourly_sell_volume");
			l.volumeImbalancePct  = getDoubleOrNull(liq, "volume_imbalance_pct");
			l.crossedHours24h     = getIntOrNull(liq, "crossed_hours_24h");
			l.estHoursToFillLimit = getDoubleOrNull(liq, "est_hours_to_fill_limit");
			out.liquidity = l;
		}

		JsonObject qual = optObject(root, "quality");
		if (qual != null)
		{
			ItemInsights.Quality q = new ItemInsights.Quality();
			q.avgMargin24h      = getLongOrNull(qual, "avg_margin_24h");
			q.avgProfit24h      = getLongOrNull(qual, "avg_profit_24h");
			q.marginConsistency = getIntOrNull(qual, "margin_consistency");
			q.limitCycleProfit  = getLongOrNull(qual, "limit_cycle_profit");
			q.estGpPerHour      = getLongOrNull(qual, "est_gp_per_hour");
			out.quality = q;
		}

		JsonObject risk = optObject(root, "risk");
		if (risk != null)
		{
			ItemInsights.Risk rk = new ItemInsights.Risk();
			rk.dumpScore     = getIntOrNull(risk, "dump_score");
			rk.activeDump    = getBool(risk, "active_dump", false);
			rk.unusualVolume = getBool(risk, "unusual_volume", false);
			out.risk = rk;
		}

		JsonObject sc = optObject(root, "score");
		if (sc != null)
		{
			ItemInsights.Score s = new ItemInsights.Score();
			s.confidence = getInt(sc, "confidence", 0);
			s.tier       = getString(sc, "tier", "");
			s.signal     = sc.has("signal") && !sc.get("signal").isJsonNull() ? sc.get("signal").getAsString() : null;
			out.score = s;
		}

		JsonObject al = optObject(root, "alerts");
		if (al != null)
		{
			ItemInsights.Alerts a = new ItemInsights.Alerts();
			a.activeMerch = getBool(al, "active_merch", false);
			a.merchTarget = getLongOrNull(al, "merch_target");
			a.merchTier   = al.has("merch_tier") && !al.get("merch_tier").isJsonNull() ? al.get("merch_tier").getAsString() : null;
			a.spikePct24h = getDoubleOrNull(al, "spike_pct_24h");
			a.dipPct24h   = getDoubleOrNull(al, "dip_pct_24h");
			out.alerts = a;
		}

		JsonObject proj = optObject(root, "projection");
		if (proj != null)
		{
			ItemInsights.Projection p = new ItemInsights.Projection();
			p.band30d = parseBand(optObject(proj, "30d"));
			p.band3m  = parseBand(optObject(proj, "3m"));
			out.projection = p;
		}

		JsonObject fz = optObject(root, "frozen");
		if (fz != null)
		{
			ItemInsights.Frozen f = new ItemInsights.Frozen();
			f.buy      = getLong(fz, "buy",  0L);
			f.sell     = getLong(fz, "sell", 0L);
			out.frozen = f;
		}

		out.sparkline2hBuy    = parseNullableLongArray(root, "sparkline_2h_buy");
		out.sparkline2hSell   = parseNullableLongArray(root, "sparkline_2h_sell");
		out.sparkline2hStart  = getString(root, "sparkline_2h_start", "");
		out.sparkline4hBuy    = parseNullableLongArray(root, "sparkline_4h_buy");
		out.sparkline4hSell   = parseNullableLongArray(root, "sparkline_4h_sell");
		out.sparkline4hStart  = getString(root, "sparkline_4h_start", "");
		out.sparkline24hBuy   = parseNullableLongArray(root, "sparkline_24h_buy");
		out.sparkline24hSell  = parseNullableLongArray(root, "sparkline_24h_sell");
		out.sparkline24hStart = getString(root, "sparkline_24h_start", "");
		out.sparkline7dBuy    = parseNullableLongArray(root, "sparkline_7d_buy");
		out.sparkline7dSell   = parseNullableLongArray(root, "sparkline_7d_sell");
		out.sparkline7dStart  = getString(root, "sparkline_7d_start", "");
		out.sparkline30dBuy   = parseNullableLongArray(root, "sparkline_30d_buy");
		out.sparkline30dSell  = parseNullableLongArray(root, "sparkline_30d_sell");
		out.sparkline30dStart = getString(root, "sparkline_30d_start", "");

		return out;
	}

	private Long[] parseNullableLongArray(JsonObject parent, String key)
	{
		if (parent == null || !parent.has(key) || parent.get(key).isJsonNull() || !parent.get(key).isJsonArray())
		{
			return new Long[0];
		}
		JsonArray arr = parent.getAsJsonArray(key);
		Long[] out = new Long[arr.size()];
		for (int i = 0; i < arr.size(); i++)
		{
			JsonElement el = arr.get(i);
			out[i] = (el == null || el.isJsonNull()) ? null : el.getAsLong();
		}
		return out;
	}

	private ItemInsights.Band parseBand(JsonObject obj)
	{
		if (obj == null)
		{
			return null;
		}
		ItemInsights.Band b = new ItemInsights.Band();
		b.low     = getLong(obj, "low",  0L);
		b.high    = getLong(obj, "high", 0L);
		b.hitRate = getDouble(obj, "hit_rate", 0.0);
		return b;
	}

	private JsonObject optObject(JsonObject parent, String key)
	{
		if (parent == null || !parent.has(key))
		{
			return null;
		}
		JsonElement el = parent.get(key);
		return (el == null || el.isJsonNull() || !el.isJsonObject()) ? null : el.getAsJsonObject();
	}


	public void fetchAuthStatus(Consumer<AuthStatus> onSuccess, Runnable onTransient)
	{
		String key = sanitizedApiKey();
		log.debug("[07Flip] /auth call, keyLen={}", key == null ? 0 : key.length());
		fetch(BASE_URL + "/auth", new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchAuthStatus failed: {}", e.getMessage());
				if (onTransient != null)
				{
					onTransient.run();
				}
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					int code = response.code();
					if (code >= 500 && code <= 599)
					{
						log.debug("[07Flip] /auth returned {} — transient server error, will retry", code);
						if (onTransient != null)
						{
							onTransient.run();
						}
						return;
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] fetchAuthStatus HTTP {}", code);
						return;
					}
					JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
					AuthStatus status = new AuthStatus();
					status.authenticated = getBool(json, "authenticated", false);
					status.premium       = getBool(json, "premium",       false);
					onSuccess.accept(status);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] Auth parse error: {}", e.getMessage());
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	public void fetchAuthStatus(Consumer<AuthStatus> callback)
	{
		fetchAuthStatus(callback, null);
	}


	public void fetchFlips(String preset, long minProfit, long priceMin, long priceMax,
	                       int page, BiConsumer<List<FlipItem>, Integer> callback)
	{
		fetchFlips(preset, "flip07Score", minProfit, priceMin, priceMax, 0L, page, callback, null);
	}

	public void fetchFlips(String preset, String sort, long minProfit, long priceMin, long priceMax,
	                       long cashStack, int page,
	                       BiConsumer<List<FlipItem>, Integer> callback,
	                       Consumer<String> onPremiumRequired)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/v2/flips?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (preset != null && !preset.isEmpty())
		{
			url.append("&preset=").append(preset);
		}
		if (sort != null && !sort.isEmpty())
		{
			String realSort = sort;
			String order = null;
			if (sort.endsWith("Desc"))
			{
				realSort = sort.substring(0, sort.length() - 4);
				order = "desc";
			}
			else if ("buyPrice".equals(sort) || "sellPrice".equals(sort))
			{
				order = "asc";
			}
			url.append("&sort=").append(realSort);
			if (order != null)
			{
				url.append("&order=").append(order);
			}
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
		if (cashStack > 0)
		{
			url.append("&cashStack=").append(cashStack).append("&annotate=affordableQty");
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
				if (response.code() == 403)
				{
					handlePremiumRejection(response, onPremiumRequired);
					callback.accept(new ArrayList<>(), 0);
					return;
				}
				parsePagedResponse(response, "flips", O7FlipApiClient.this::parseFlipItem, callback);
			}
		});
	}

	private void handlePremiumRejection(Response response, Consumer<String> onPremiumRequired)
	{
		String upgradeUrl = "https://07flip.com/premium";
		try
		{
			if (response.body() != null)
			{
				JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
				String parsed = getString(json, "upgrade_url", "");
				if (!parsed.isEmpty())
				{
					upgradeUrl = parsed;
				}
			}
		}
		catch (Exception ignored)
		{
		}
		finally
		{
			response.close();
		}
		log.debug("[07Flip] Premium preset rejected (403). Upgrade URL: {}", upgradeUrl);
		if (onPremiumRequired != null)
		{
			onPremiumRequired.accept(upgradeUrl);
		}
	}

	public void fetchDips(String sort, String window, int page,
	                      BiConsumer<List<DipItem>, Integer> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/dips?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (sort != null && !sort.isEmpty())
		{
			url.append("&sort=").append(sort);
		}
		if (window != null && !window.isEmpty() && !"1d".equals(window))
		{
			url.append("&activity_window=").append(window);
		}
		fetchPaged(url.toString(), "fetchDips", "dips", this::parseDipItem, callback);
	}


	public void fetchHighAlch(String sort, int page, boolean fireStaff, boolean bryophyta,
	                          Consumer<HighAlchItem.Response> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/high-alch?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (sort != null && !sort.isEmpty())
		{
			url.append("&sort=").append(sort);
		}
		if (fireStaff)
		{
			url.append("&fireStaff=true");
		}
		if (bryophyta)
		{
			url.append("&bryophyta=true");
		}
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchHighAlch failed: {}", e.getMessage());
				callback.accept(emptyHighAlchResponse());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (response.code() == 429)
				{
					markRateLimited(response);
				}
				if (!response.isSuccessful() || response.body() == null)
				{
					log.warn("[07Flip] fetchHighAlch HTTP {}", response.code());
					callback.accept(emptyHighAlchResponse());
					return;
				}
				try
				{
					JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
					HighAlchItem.Response out = new HighAlchItem.Response();
					if (root.has("rune_prices") && root.get("rune_prices").isJsonObject())
					{
						JsonObject rp = root.getAsJsonObject("rune_prices");
						out.natureRunePrice = getLong(rp, "nature_rune_price", 0);
						out.fireRunePrice   = getLong(rp, "fire_rune_price", 0);
						out.alchCost        = getLong(rp, "alch_cost", 0);
						out.fireStaff       = getBool(rp, "fire_staff", false);
						out.bryophytaStaff  = getBool(rp, "bryophyta_staff", false);
					}
					out.items = parseArray(root, "items", O7FlipApiClient.this::parseHighAlchItem);
					out.total = getInt(root, "total", out.items.size());
					callback.accept(out);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] fetchHighAlch parse error: {}", e.getMessage());
					callback.accept(emptyHighAlchResponse());
				}
			}
		});
	}

	private static HighAlchItem.Response emptyHighAlchResponse()
	{
		HighAlchItem.Response r = new HighAlchItem.Response();
		r.items = new ArrayList<>();
		return r;
	}

	private HighAlchItem parseHighAlchItem(JsonObject obj)
	{
		HighAlchItem item = new HighAlchItem();
		item.itemId        = getInt(obj, "item_id", 0);
		item.name          = getString(obj, "name", "Unknown");
		item.buyPrice      = getLong(obj, "buy_price", 0);
		item.highAlchValue = getLong(obj, "high_alch_value", 0);
		item.runeCost      = getLong(obj, "rune_cost", 0);
		item.profit        = getLong(obj, "profit", 0);
		item.roiPct        = getDouble(obj, "roi_pct", 0);
		item.buyLimit      = getInt(obj, "buy_limit", 0);
		item.dailyVolume   = getInt(obj, "daily_volume", 0);
		item.lastUpdated   = getString(obj, "last_updated", "");
		return item;
	}


	public void fetchFavourites(Consumer<List<FlipItem>> callback)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			callback.accept(new ArrayList<>());
			return;
		}
		fetch(BASE_URL + "/favourites", new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchFavourites failed: {}", e.getMessage());
				callback.accept(new ArrayList<>());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				int code = response.code();
				if (code == 401 && onUnauthorized != null)
				{
					try { onUnauthorized.run(); } catch (Exception ignored) {}
					response.close();
					callback.accept(new ArrayList<>());
					return;
				}
				if (!response.isSuccessful() || response.body() == null)
				{
					log.warn("[07Flip] GET /favourites HTTP {}", code);
					response.close();
					callback.accept(new ArrayList<>());
					return;
				}
				try
				{
					String body = response.body().string();
					com.google.gson.JsonObject root = gson.fromJson(body, com.google.gson.JsonObject.class);
					List<FlipItem> items = new ArrayList<>();
					com.google.gson.JsonArray arr = root != null && root.has("favourites") && root.get("favourites").isJsonArray()
						? root.getAsJsonArray("favourites") : null;
					if (arr != null)
					{
						for (int i = 0; i < arr.size(); i++)
						{
							try { items.add(parseFlipItem(arr.get(i).getAsJsonObject())); }
							catch (Exception e) { log.warn("[07Flip] favourites item parse skipped: {}", e.getMessage()); }
						}
					}
					int serverCount = root != null && root.has("count") && !root.get("count").isJsonNull()
						? root.get("count").getAsInt() : items.size();
					if (items.isEmpty())
					{
						log.info("[07Flip] GET /favourites returned 0 items (server count={})", serverCount);
					}
					else
					{
						log.debug("[07Flip] GET /favourites returned {} items (server count={})",
							items.size(), serverCount);
					}
					callback.accept(items);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] GET /favourites parse error: {}", e.getMessage());
					callback.accept(new ArrayList<>());
				}
			}
		});
	}

	private volatile Runnable onUnauthorized;

	public void setOnFavouritesUnauthorized(Runnable handler)
	{
		this.onUnauthorized = handler;
	}

	public void addFavourite(int itemId, Consumer<Boolean> onResult)
	{
		mutateFavourite("POST", itemId, onResult);
	}

	public void removeFavourite(int itemId, Consumer<Boolean> onResult)
	{
		mutateFavourite("DELETE", itemId, onResult);
	}

	private void mutateFavourite(String method, int itemId, Consumer<Boolean> onResult)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			onResult.accept(false);
			return;
		}
		if (isRateLimited())
		{
			onResult.accept(false);
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("item_id", itemId);
		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(body));
		Request.Builder builder = new Request.Builder()
			.url(BASE_URL + "/favourites")
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key);
		if ("DELETE".equals(method))
		{
			builder.delete(requestBody);
		}
		else
		{
			builder.post(requestBody);
		}
		okHttpClient.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] {} /favourites failed: {}", method, e.getMessage());
				onResult.accept(false);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				int code = response.code();
				if (code == 429) markRateLimited(response);
				boolean ok = response.isSuccessful();
				if (!ok)
				{
					log.warn("[07Flip] {} /favourites HTTP {}", method, code);
					if (code == 401 && onUnauthorized != null)
					{
						try { onUnauthorized.run(); } catch (Exception ignored) {}
					}
				}
				response.close();
				onResult.accept(ok);
			}
		});
	}


	public void fetchOptimize(long capital, int slots, String risk,
	                          int maxFillHours, Boolean members, java.util.List<Integer> excludeItemIds,
	                          Double minProfitPct,
	                          Consumer<OptimizeResult> onSuccess,
	                          Consumer<String> onPremiumRequired,
	                          Consumer<String> onError)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			if (onError != null) onError.accept("no_api_key");
			return;
		}
		if (isRateLimited())
		{
			if (onError != null) onError.accept("rate_limited");
			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("capital", capital);
		body.addProperty("slots", slots);
		if (risk != null && !risk.isEmpty()) body.addProperty("risk", risk);
		if (maxFillHours > 0 && maxFillHours != 4) body.addProperty("max_fill_hours", maxFillHours);
		if (members != null) body.addProperty("members", members);
		if (excludeItemIds != null && !excludeItemIds.isEmpty())
		{
			JsonArray ex = new JsonArray();
			for (Integer id : excludeItemIds) if (id != null && id > 0) ex.add(id);
			body.add("exclude_item_ids", ex);
		}
		if (minProfitPct != null && minProfitPct > 0 && minProfitPct <= 10)
		{
			body.addProperty("min_profit_pct", minProfitPct);
		}

		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(body));
		Request request = new Request.Builder()
			.url(BASE_URL + "/optimize")
			.post(requestBody)
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] /optimize failed: {}", e.getMessage());
				if (onError != null) onError.accept("network_error");
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				int code = response.code();
				try
				{
					if (code == 429)
					{
						markRateLimited(response);
						if (onError != null) onError.accept("http_429");
						return;
					}
					if (code == 403)
					{
						String upgrade = "https://07flip.com/premium";
						try
						{
							JsonObject obj = gson.fromJson(response.body().string(), JsonObject.class);
							if (obj != null && obj.has("upgrade_url") && !obj.get("upgrade_url").isJsonNull())
							{
								upgrade = obj.get("upgrade_url").getAsString();
							}
						}
						catch (Exception ignored) {}
						if (onPremiumRequired != null) onPremiumRequired.accept(upgrade);
						return;
					}
					if (code == 400)
					{
						String reason = "invalid_body";
						try
						{
							JsonObject obj = gson.fromJson(response.body().string(), JsonObject.class);
							if (obj != null && obj.has("error")) reason = obj.get("error").getAsString();
						}
						catch (Exception ignored) {}
						log.warn("[07Flip] /optimize 400 {}", reason);
						if (onError != null) onError.accept(reason);
						return;
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] /optimize HTTP {}", code);
						if (onError != null) onError.accept("http_" + code);
						return;
					}
					OptimizeResult out = parseOptimizeResponse(response.body().string());
					if (onSuccess != null) onSuccess.accept(out);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	private OptimizeResult parseOptimizeResponse(String json)
	{
		OptimizeResult out = new OptimizeResult();
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			out.updatedAt = getString(root, "updated_at", "");
			if (root.has("summary") && root.get("summary").isJsonObject())
			{
				out.summary = parseSummary(root.getAsJsonObject("summary"));
			}
			if (root.has("allocations") && root.get("allocations").isJsonArray())
			{
				JsonArray arr = root.getAsJsonArray("allocations");
				for (int i = 0; i < arr.size(); i++)
				{
					try
					{
						out.allocations.add(parseAllocation(arr.get(i).getAsJsonObject()));
					}
					catch (Exception e)
					{
						log.warn("[07Flip] /optimize allocation parse skipped: {}", e.getMessage());
					}
				}
			}
		}
		catch (Exception e)
		{
			log.warn("[07Flip] /optimize parse error: {}", e.getMessage());
		}
		return out;
	}

	private OptimizeResult.Allocation parseAllocation(JsonObject a)
	{
		OptimizeResult.Allocation al = new OptimizeResult.Allocation();
		al.itemId                = getInt(a, "item_id", 0);
		al.name                  = getString(a, "name", "Unknown");
		al.qty                   = getInt(a, "qty", 0);
		al.gpAllocated           = getLong(a, "gp_allocated", 0);
		al.buyPrice              = getLong(a, "buy_price", 0);
		al.sellPrice             = getLong(a, "sell_price", 0);
		al.profitPerUnit         = getLong(a, "profit_per_unit", 0);
		al.expectedProfit        = readExpectedProfit(a);
		al.fillConfidence        = getDoubleOrNull(a, "fill_confidence");
		al.buyLimit              = getInt(a, "buy_limit", 0);
		al.hourlyVolume          = getIntOrNull(a, "hourly_volume");
		String src               = getString(a, "price_source", "");
		al.priceSource           = src.isEmpty() ? null : src;
		al.rawBuyPrice           = getLongOrNull(a, "raw_buy_price");
		al.rawSellPrice          = getLongOrNull(a, "raw_sell_price");
		al.rawProfitPerUnit      = getLongOrNull(a, "raw_profit_per_unit");
		al.estimatedFillHours    = getDoubleOrNull(a, "estimated_fill_hours");
		al.realisticQtyCap       = getIntOrNull(a, "realistic_qty_cap");
		al.hourlyTrend           = parseIntArray(a, "hourly_trend");
		al.profitPctOfBank       = getDoubleOrNull(a, "profit_pct_of_bank");
		al.belowWealthThreshold  = getBoolOrNull(a, "below_wealth_threshold");
		parseSlotFills(a, "buys",  al.buys);
		parseSlotFills(a, "sells", al.sells);
		al.state                 = com.o7flip.model.SlotState.fromWire(getString(a, "state", "pending"));
		al.partial               = getBool(a, "partial", false);
		al.sellListed            = getBool(a, "sell_listed", false);
		al.reservedGp            = getLong(a, "reserved_gp", 0);
		al.offerInstanceId       = getLongOrNull(a, "offer_instance_id");
		al.overrideRev           = getInt(a, "override_rev", 0);
		String overrideSource    = getString(a, "override_source", "");
		al.overrideSource        = overrideSource.isEmpty() ? null : overrideSource;
		al.appliedOverrideRev    = al.overrideRev;
		return al;
	}

	OptimizeResult.Summary parseSummary(JsonObject s)
	{
		OptimizeResult.Summary sum = new OptimizeResult.Summary();
		sum.capitalInput               = getLong(s, "capital_input", 0);
		sum.capitalDeployed            = getLong(s, "capital_deployed", 0);
		sum.capitalUnused              = getLong(s, "capital_unused", 0);
		sum.slotsUsed                  = getInt(s,  "slots_used", 0);
		sum.slotsRequested             = getInt(s,  "slots_requested", 0);
		sum.risk                       = getString(s, "risk", "medium");
		sum.members                    = getBoolOrNull(s, "members");
		sum.expectedProfitTotal        = getLong(s, "expected_profit_total", 0);
		sum.avgFillConfidence          = getDoubleOrNull(s, "avg_fill_confidence");
		sum.minFillConfidence          = getDoubleOrNull(s, "min_fill_confidence");
		sum.recommendedCount           = getInt(s, "recommended_count", 0);
		sum.rawCount                   = getInt(s, "raw_count", 0);
		sum.maxFillHours               = getIntOrNull(s, "max_fill_hours");
		sum.avgEstimatedFillHours      = getDoubleOrNull(s, "avg_estimated_fill_hours");
		sum.maxEstimatedFillHours      = getDoubleOrNull(s, "max_estimated_fill_hours");
		sum.fillConfidenceFormula      = getString(s, "fill_confidence_formula", "");
		sum.pricingNote                = getString(s, "pricing_note", "");
		sum.compositionNote            = getString(s, "composition_note", "");
		sum.realismNote                = getString(s, "realism_note", "");
		parseSummaryExtras(s, sum);
		return sum;
	}

	JsonObject summaryToJson(OptimizeResult.Summary sum)
	{
		JsonObject s = new JsonObject();
		s.addProperty("capital_input",         sum.capitalInput);
		s.addProperty("capital_deployed",      sum.capitalDeployed);
		s.addProperty("capital_unused",        sum.capitalUnused);
		s.addProperty("slots_used",            sum.slotsUsed);
		s.addProperty("slots_requested",       sum.slotsRequested);
		if (sum.risk != null)    s.addProperty("risk", sum.risk);
		if (sum.members != null) s.addProperty("members", sum.members);
		s.addProperty("expected_profit_total", sum.expectedProfitTotal);
		if (sum.avgFillConfidence != null)     s.addProperty("avg_fill_confidence", sum.avgFillConfidence);
		if (sum.minFillConfidence != null)     s.addProperty("min_fill_confidence", sum.minFillConfidence);
		s.addProperty("recommended_count",     sum.recommendedCount);
		s.addProperty("raw_count",             sum.rawCount);
		if (sum.maxFillHours != null)          s.addProperty("max_fill_hours", sum.maxFillHours);
		if (sum.avgEstimatedFillHours != null) s.addProperty("avg_estimated_fill_hours", sum.avgEstimatedFillHours);
		if (sum.maxEstimatedFillHours != null) s.addProperty("max_estimated_fill_hours", sum.maxEstimatedFillHours);
		if (sum.fillConfidenceFormula != null) s.addProperty("fill_confidence_formula", sum.fillConfidenceFormula);
		if (sum.pricingNote != null)           s.addProperty("pricing_note", sum.pricingNote);
		if (sum.compositionNote != null)       s.addProperty("composition_note", sum.compositionNote);
		if (sum.realismNote != null)           s.addProperty("realism_note", sum.realismNote);
		if (sum.emptyReason != null)           s.addProperty("empty_reason", sum.emptyReason);
		s.addProperty("degraded_trend_data",   sum.degradedTrendData);
		if (sum.minProfitPctApplied != null)   s.addProperty("min_profit_pct_applied", sum.minProfitPctApplied);
		if (sum.slotSuggestion != null)
		{
			JsonObject ss = new JsonObject();
			ss.addProperty("suggested_slots",             sum.slotSuggestion.suggestedSlots);
			ss.addProperty("additional_capital_deployed", sum.slotSuggestion.additionalCapitalDeployed);
			ss.addProperty("additional_expected_profit",  sum.slotSuggestion.additionalExpectedProfit);
			s.add("slot_suggestion", ss);
		}
		if (sum.eligibilityRejections != null && !sum.eligibilityRejections.isEmpty())
		{
			JsonObject er = new JsonObject();
			for (java.util.Map.Entry<String, Integer> e : sum.eligibilityRejections.entrySet())
			{
				if (e.getValue() != null) er.addProperty(e.getKey(), e.getValue());
			}
			s.add("eligibility_rejections", er);
		}
		return s;
	}

	private void parseSummaryExtras(JsonObject s, OptimizeResult.Summary sum)
	{
		String empty                = getString(s, "empty_reason", "");
		sum.emptyReason             = empty.isEmpty() ? null : empty;
		sum.degradedTrendData       = getBool(s, "degraded_trend_data", false);
		sum.minProfitPctApplied     = getDoubleOrNull(s, "min_profit_pct_applied");
		if (s.has("slot_suggestion") && s.get("slot_suggestion").isJsonObject())
		{
			JsonObject ss = s.getAsJsonObject("slot_suggestion");
			OptimizeResult.SlotSuggestion sug = new OptimizeResult.SlotSuggestion();
			sug.suggestedSlots            = getInt(ss, "suggested_slots", 0);
			sug.additionalCapitalDeployed = getLong(ss, "additional_capital_deployed", 0);
			sug.additionalExpectedProfit  = getLong(ss, "additional_expected_profit", 0);
			if (sug.suggestedSlots > 0) sum.slotSuggestion = sug;
		}
		if (s.has("eligibility_rejections") && s.get("eligibility_rejections").isJsonObject())
		{
			JsonObject er = s.getAsJsonObject("eligibility_rejections");
			for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : er.entrySet())
			{
				try
				{
					if (e.getValue() != null && !e.getValue().isJsonNull())
					{
						sum.eligibilityRejections.put(e.getKey(), e.getValue().getAsInt());
					}
				}
				catch (Exception ignored) {}
			}
		}
	}


	public void fetchActiveSession(Consumer<com.o7flip.model.OptimizerSession> callback)
	{
		String key = sanitizedApiKey();
		if (key == null) { callback.accept(null); return; }
		if (isRateLimited()) { callback.accept(null); return; }

		Request request = new Request.Builder()
			.url(BASE_URL + "/optimize/active")
			.get()
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key)
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] /optimize/active GET failed: {}", e.getMessage());
				callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429) markRateLimited(response);
					if (response.code() == 204)
					{
						callback.accept(null);
						return;
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] /optimize/active GET HTTP {}", response.code());
						callback.accept(null);
						return;
					}
					String json = response.body().string();
					com.o7flip.model.OptimizerSession session = parseSession(json);
					callback.accept(session);
				}
				finally { response.close(); }
			}
		});
	}

	public void postActiveSession(com.o7flip.model.OptimizerSession session, Consumer<Boolean> onComplete)
	{
		String key = sanitizedApiKey();
		if (key == null) { if (onComplete != null) onComplete.accept(false); return; }
		if (session == null) { if (onComplete != null) onComplete.accept(false); return; }
		if (isRateLimited()) { if (onComplete != null) onComplete.accept(false); return; }

		String bodyJson = sessionToJson(session);
		RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, bodyJson);
		Request request = new Request.Builder()
			.url(BASE_URL + "/optimize/active")
			.post(body)
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key)
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] /optimize/active POST failed: {}", e.getMessage());
				if (onComplete != null) onComplete.accept(false);
			}
			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429) markRateLimited(response);
					boolean ok = response.isSuccessful();
					if (!ok) log.warn("[07Flip] /optimize/active POST HTTP {}", response.code());
					if (onComplete != null) onComplete.accept(ok);
				}
				finally { response.close(); }
			}
		});
	}

	public void deleteActiveSession(Consumer<Boolean> onComplete)
	{
		String key = sanitizedApiKey();
		if (key == null) { if (onComplete != null) onComplete.accept(false); return; }
		if (isRateLimited()) { if (onComplete != null) onComplete.accept(false); return; }

		Request request = new Request.Builder()
			.url(BASE_URL + "/optimize/active")
			.delete()
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key)
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] /optimize/active DELETE failed: {}", e.getMessage());
				if (onComplete != null) onComplete.accept(false);
			}
			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429) markRateLimited(response);
					boolean ok = response.isSuccessful();
					if (!ok) log.warn("[07Flip] /optimize/active DELETE HTTP {}", response.code());
					if (onComplete != null) onComplete.accept(ok);
				}
				finally { response.close(); }
			}
		});
	}

	private com.o7flip.model.OptimizerSession parseSession(String json)
	{
		com.o7flip.model.OptimizerSession s = new com.o7flip.model.OptimizerSession();
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			if (root == null) return s;
			JsonObject body = root.has("session") && root.get("session").isJsonObject()
				? root.getAsJsonObject("session") : root;
			s.generatedAt = getString(body, "generated_at", "");
			s.updatedAt   = getString(body, "updated_at",   "");
			s.lastPollAt  = body.has("last_poll_at") && !body.get("last_poll_at").isJsonNull()
				? body.get("last_poll_at").getAsString() : null;
			if (body.has("inputs") && body.get("inputs").isJsonObject())
			{
				JsonObject inp = body.getAsJsonObject("inputs");
				s.inputs.capital      = getLong(inp, "capital", 0);
				s.inputs.slots        = getInt(inp,  "slots",   1);
				s.inputs.maxFillHours = getIntOrNull(inp, "max_fill_hours");
				s.inputs.risk         = getString(inp, "risk", "medium");
				s.inputs.members      = getBoolOrNull(inp, "members");
				s.inputs.minProfitPct = getDoubleOrNull(inp, "min_profit_pct");
				if (inp.has("exclude_item_ids") && inp.get("exclude_item_ids").isJsonArray())
				{
					JsonArray ex = inp.getAsJsonArray("exclude_item_ids");
					for (int i = 0; i < ex.size(); i++)
					{
						try { s.inputs.excludeItemIds.add(ex.get(i).getAsInt()); }
						catch (Exception ignored) {}
					}
				}
			}
			if (body.has("slots") && body.get("slots").isJsonArray())
			{
				JsonArray arr = body.getAsJsonArray("slots");
				for (int i = 0; i < arr.size(); i++)
				{
					try
					{
						s.slots.add(parseAllocation(arr.get(i).getAsJsonObject()));
					}
					catch (Exception ignored) {}
				}
			}
			if (body.has("summary") && body.get("summary").isJsonObject())
			{
				s.summary = parseSummary(body.getAsJsonObject("summary"));
			}
		}
		catch (Exception e)
		{
			log.warn("[07Flip] /optimize/active parse error: {}", e.getMessage());
		}
		return s;
	}

	private String sessionToJson(com.o7flip.model.OptimizerSession session)
	{
		JsonObject body = new JsonObject();
		JsonObject inputs = new JsonObject();
		inputs.addProperty("capital", session.inputs.capital);
		inputs.addProperty("slots",   session.inputs.slots);
		if (session.inputs.maxFillHours != null) inputs.addProperty("max_fill_hours", session.inputs.maxFillHours);
		if (session.inputs.risk != null && !session.inputs.risk.isEmpty()) inputs.addProperty("risk", session.inputs.risk);
		if (session.inputs.members != null) inputs.addProperty("members", session.inputs.members);
		if (session.inputs.minProfitPct != null && session.inputs.minProfitPct > 0)
		{
			inputs.addProperty("min_profit_pct", session.inputs.minProfitPct);
		}
		if (session.inputs.excludeItemIds != null && !session.inputs.excludeItemIds.isEmpty())
		{
			JsonArray ex = new JsonArray();
			for (Integer id : session.inputs.excludeItemIds) if (id != null && id > 0) ex.add(id);
			inputs.add("exclude_item_ids", ex);
		}
		body.add("inputs", inputs);

		JsonArray slots = new JsonArray();
		if (session.slots != null)
		{
			for (OptimizeResult.Allocation al : session.slots)
			{
				if (al == null) continue;
				JsonObject s = new JsonObject();
				s.addProperty("item_id",                  al.itemId);
				s.addProperty("name",                     al.name);
				s.addProperty("qty",                      al.qty);
				s.addProperty("gp_allocated",             al.gpAllocated);
				s.addProperty("buy_price",                al.buyPrice);
				s.addProperty("sell_price",               al.sellPrice);
				s.addProperty("profit_per_unit",          al.profitPerUnit);
				s.addProperty("expected_profit",          al.expectedProfit);
				if (al.fillConfidence != null) s.addProperty("fill_confidence", al.fillConfidence);
				s.addProperty("buy_limit",                al.buyLimit);
				if (al.hourlyVolume != null)    s.addProperty("hourly_volume", al.hourlyVolume);
				s.addProperty("price_source", al.priceSource != null ? al.priceSource : "raw");
				if (al.rawBuyPrice != null)     s.addProperty("raw_buy_price", al.rawBuyPrice);
				if (al.rawSellPrice != null)    s.addProperty("raw_sell_price", al.rawSellPrice);
				if (al.rawProfitPerUnit != null) s.addProperty("raw_profit_per_unit", al.rawProfitPerUnit);
				if (al.estimatedFillHours != null) s.addProperty("estimated_fill_hours", al.estimatedFillHours);
				if (al.realisticQtyCap != null) s.addProperty("realistic_qty_cap", al.realisticQtyCap);
				if (al.profitPctOfBank != null) s.addProperty("profit_pct_of_bank", al.profitPctOfBank);
				if (al.belowWealthThreshold != null) s.addProperty("below_wealth_threshold", al.belowWealthThreshold);
				if (al.hourlyTrend != null)
				{
					JsonArray ht = new JsonArray();
					for (int v : al.hourlyTrend) ht.add(v);
					s.add("hourly_trend", ht);
				}
				s.add("buys",  fillsToJson(al.buys));
				s.add("sells", fillsToJson(al.sells));
				s.addProperty("state", al.state == null ? "pending" : al.state.wire());
				if (al.sellListed) s.addProperty("sell_listed", true);
				if (al.partial)
				{
					s.addProperty("partial", true);
					if (al.reservedGp > 0) s.addProperty("reserved_gp", al.reservedGp);
				}
				if (al.offerInstanceId != null) s.addProperty("offer_instance_id", al.offerInstanceId);
				if (al.overrideRev > 0) s.addProperty("override_rev", al.overrideRev);
				if (al.overrideSource != null) s.addProperty("override_source", al.overrideSource);
				slots.add(s);
			}
		}
		body.add("slots", slots);

		if (session.generatedAt != null) body.addProperty("generated_at", session.generatedAt);
		if (session.lastPollAt != null)  body.addProperty("last_poll_at", session.lastPollAt);
		if (session.updatedAt != null && !session.updatedAt.isEmpty())
		{
			body.addProperty("updated_at", session.updatedAt);
		}
		if (session.summary != null)
		{
			body.add("summary", summaryToJson(session.summary));
		}
		return gson.toJson(body);
	}

	private JsonArray fillsToJson(java.util.List<com.o7flip.model.SlotFill> fills)
	{
		JsonArray arr = new JsonArray();
		if (fills == null) return arr;
		for (com.o7flip.model.SlotFill f : fills)
		{
			if (f == null) continue;
			JsonObject o = new JsonObject();
			o.addProperty("qty",        f.qty);
			o.addProperty("price_each", f.priceEach);
			if (f.tradedAt != null) o.addProperty("traded_at", f.tradedAt);
			arr.add(o);
		}
		return arr;
	}


	public void fetchCompletedPositions(Consumer<List<com.o7flip.model.CompletedPosition>> callback)
	{
		String key = sanitizedApiKey();
		if (key == null) { callback.accept(null); return; }
		if (isRateLimited()) { callback.accept(null); return; }

		Request request = new Request.Builder()
			.url(BASE_URL + "/optimize/completed")
			.get()
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key)
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] /optimize/completed GET failed: {}", e.getMessage());
				callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429) markRateLimited(response);
					if (response.code() == 204)
					{
						callback.accept(new ArrayList<>());
						return;
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] /optimize/completed GET HTTP {}", response.code());
						callback.accept(null);
						return;
					}
					callback.accept(parseCompletedPositions(response.body().string()));
				}
				finally { response.close(); }
			}
		});
	}

	public void postCompletedPosition(com.o7flip.model.CompletedPosition cp,
	                                  Consumer<List<com.o7flip.model.CompletedPosition>> callback)
	{
		String key = sanitizedApiKey();
		if (key == null || cp == null) { if (callback != null) callback.accept(null); return; }
		if (isRateLimited()) { if (callback != null) callback.accept(null); return; }

		RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(completedPositionToJson(cp)));
		Request request = new Request.Builder()
			.url(BASE_URL + "/optimize/completed")
			.post(body)
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + key)
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] /optimize/completed POST failed: {}", e.getMessage());
				if (callback != null) callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.code() == 429) markRateLimited(response);
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] /optimize/completed POST HTTP {}", response.code());
						if (callback != null) callback.accept(null);
						return;
					}
					if (callback != null) callback.accept(parseCompletedPositions(response.body().string()));
				}
				finally { response.close(); }
			}
		});
	}

	private List<com.o7flip.model.CompletedPosition> parseCompletedPositions(String json)
	{
		List<com.o7flip.model.CompletedPosition> out = new ArrayList<>();
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			if (root == null || !root.has("positions") || !root.get("positions").isJsonArray())
			{
				return out;
			}
			JsonArray arr = root.getAsJsonArray("positions");
			for (int i = 0; i < arr.size(); i++)
			{
				try
				{
					JsonObject o = arr.get(i).getAsJsonObject();
					com.o7flip.model.CompletedPosition cp = new com.o7flip.model.CompletedPosition();
					cp.itemId    = getInt(o, "item_id", 0);
					cp.name      = getString(o, "name", "Unknown");
					cp.qty       = getInt(o, "qty", 0);
					cp.buyGp     = getLong(o, "buy_gp", 0);
					cp.sellGp    = getLong(o, "sell_gp", 0);
					cp.profit    = getLong(o, "profit", 0);
					cp.partial   = getBool(o, "partial", false);
					cp.fillHours = getDoubleOrNull(o, "fill_hours");
					String closed = getString(o, "closed_at", "");
					cp.closedAt  = closed.isEmpty() ? null : closed;
					out.add(cp);
				}
				catch (Exception ignored) {}
			}
		}
		catch (Exception e)
		{
			log.warn("[07Flip] /optimize/completed parse error: {}", e.getMessage());
		}
		return out;
	}

	private JsonObject completedPositionToJson(com.o7flip.model.CompletedPosition cp)
	{
		JsonObject o = new JsonObject();
		o.addProperty("item_id",   cp.itemId);
		o.addProperty("name",      cp.name);
		o.addProperty("qty",       cp.qty);
		o.addProperty("buy_gp",    cp.buyGp);
		o.addProperty("sell_gp",   cp.sellGp);
		o.addProperty("profit",    cp.profit);
		o.addProperty("partial",   cp.partial);
		if (cp.fillHours != null) o.addProperty("fill_hours", cp.fillHours);
		if (cp.closedAt != null)  o.addProperty("closed_at",  cp.closedAt);
		return o;
	}

	public void fetchSpikes(String sort, int page, BiConsumer<List<SpikeItem>, Integer> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/spikes?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (sort != null && !sort.isEmpty())
		{
			url.append("&sort=").append(sort);
		}
		fetchPaged(url.toString(), "fetchSpikes", "spikes", this::parseSpikeItem, callback);
	}

	public void fetchDumps(String sort, long minProfit, long priceMin, long priceMax,
	                       int minScore, boolean activeOnly, String tier,
	                       int page, Consumer<DumpItem.Response> callback)
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
		if (minScore > 0)
		{
			url.append("&minScore=").append(minScore);
		}
		if (activeOnly)
		{
			url.append("&activeOnly=true");
		}
		if (tier != null && !tier.isEmpty() && !"all".equals(tier))
		{
			url.append("&tier=").append(tier);
		}
		fetchParsed(url.toString(), "fetchDumps", this::parseDumpsResponse, emptyDumpsResponse(), callback);
	}

	private static DumpItem.Response emptyDumpsResponse()
	{
		DumpItem.Response r = new DumpItem.Response();
		r.items = new ArrayList<>();
		return r;
	}

	private DumpItem.Response parseDumpsResponse(Response response) throws IOException
	{
		if (response.code() == 429)
		{
			markRateLimited(response);
		}
		if (!response.isSuccessful() || response.body() == null)
		{
			log.warn("[07Flip] /dumps HTTP {}", response.code());
			return emptyDumpsResponse();
		}
		try
		{
			JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
			DumpItem.Response out = new DumpItem.Response();
			out.items = parseArray(json, "dumps", O7FlipApiClient.this::parseDumpItem);
			out.total = getInt(json, "total", out.items.size());
			if (json.has("tier_totals") && json.get("tier_totals").isJsonObject())
			{
				JsonObject t = json.getAsJsonObject("tier_totals");
				out.confirmedCount = getInt(t, "confirmed", 0);
				out.likelyCount    = getInt(t, "likely",    0);
			}
			return out;
		}
		catch (Exception e)
		{
			log.warn("[07Flip] /dumps parse error: {}", e.getMessage());
			return emptyDumpsResponse();
		}
	}

	public void fetchBotDumps(String sort, long minProfit, long priceMin, long priceMax,
	                          int minScore, boolean activeOnly, String tier,
	                          int page, Consumer<DumpItem.Response> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/bot-dumps?limit=").append(PAGE_LIMIT)
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
		if (minScore > 0)
		{
			url.append("&minScore=").append(minScore);
		}
		if (activeOnly)
		{
			url.append("&activeOnly=true");
		}
		if (tier != null && !tier.isEmpty() && !"all".equals(tier))
		{
			url.append("&tier=").append(tier);
		}
		fetchParsed(url.toString(), "fetchBotDumps", this::parseDumpsResponse, emptyDumpsResponse(), callback);
	}


	public void fetchBundle(
		JsonObject sections,
		BiConsumer<List<FlipItem>, Integer>  onFlips,
		BiConsumer<List<SpikeItem>, Integer> onSpikes,
		BiConsumer<List<DumpItem>, Integer>  onDumps,
		Consumer<String>                     onConnectUrl
	)
	{
		if (isRateLimited())
		{
			return;
		}
		JsonObject body = new JsonObject();
		body.add("sections", sections);
		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(body));

		Request.Builder builder = new Request.Builder()
			.url(BASE_URL + "/v2/bundle")
			.post(requestBody)
			.header("User-Agent", USER_AGENT);
		String key = sanitizedApiKey();
		if (key != null)
		{
			builder.header("Authorization", "Bearer " + key);
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
					markRateLimited(response);
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
					if (onDumps != null && root.has("dumps"))
					{
						JsonObject sec = root.getAsJsonObject("dumps");
						List<DumpItem> items = parseArray(sec, "dumps", O7FlipApiClient.this::parseDumpItem);
						onDumps.accept(items, getInt(sec, "total", items.size()));
					}
					if (onConnectUrl != null && root.has("_auth"))
					{
						JsonObject auth = root.getAsJsonObject("_auth");
						boolean connected = getBool(auth, "connected", true);
						onConnectUrl.accept(connected ? null : getString(auth, "connect_url", ""));
					}
				}
				catch (Exception e)
				{
					log.warn("[07Flip] fetchBundle parse error: {}", e.getMessage());
				}
			}
		});
	}


	public void fetchRecommendedPrices(int itemId, Consumer<RecommendedPrices> callback)
	{
		fetchRecommendedPrices(itemId, callback, null);
	}

	public void fetchRecommendedPrices(int itemId, Consumer<RecommendedPrices> callback,
	                                   java.util.function.LongConsumer onRetryAfter)
	{
		if (itemId <= 0)
		{
			callback.accept(null);
			return;
		}
		fetch(BASE_URL + "/recommended-prices?itemId=" + itemId, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchRecommendedPrices failed: {}", e.getMessage());
				callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					int code = response.code();
					if (code == 429)
					{
						markRateLimited(response);
						callback.accept(null);
						return;
					}
					if (code == 503)
					{
						long retry = parseRetryAfterMs(response);
						if (retry <= 0) retry = 30_000L;
						log.debug("[07Flip] /recommended-prices 503 — retry in {}ms (keeping last-known)", retry);
						if (onRetryAfter != null) onRetryAfter.accept(retry);
						return;
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] fetchRecommendedPrices HTTP {}", code);
						callback.accept(null);
						return;
					}
					JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
					RecommendedPrices rp = parseRecommendedPrices(json);
					callback.accept(rp);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] Recommended prices parse error: {}", e.getMessage());
					callback.accept(null);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	private RecommendedPrices parseRecommendedPrices(JsonObject obj)
	{
		RecommendedPrices rp = new RecommendedPrices();
		rp.itemId       = getInt(obj, "item_id", 0);
		rp.recBuyPrice  = getLongOrNull(obj, "rec_buy_price");
		rp.recSellPrice = getLongOrNull(obj, "rec_sell_price");
		rp.geTax        = getLongOrNull(obj, "ge_tax");
		rp.recProfit    = getLongOrNull(obj, "rec_profit");
		rp.sampleSize   = getIntOrNull(obj, "sample_size");
		return rp;
	}

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
		item.affordableQty   = getIntOrNull(obj, "affordable_qty");
		item.flip07Score     = getIntOrNull(obj, "flip07_score");
		item.recBuyPrice     = getLongOrNull(obj, "rec_buy_price");
		item.recSellPrice    = getLongOrNull(obj, "rec_sell_price");
		item.recProfit       = getLongOrNull(obj, "rec_profit");
		item.hourlyVolume    = getIntOrNull(obj, "hourly_volume");
		item.dailyVolume     = getIntOrNull(obj, "daily_volume");
		item.bandProfit         = getLongOrNull(obj, "band_profit");
		item.bandMargin         = getLongOrNull(obj, "band_margin");
		item.bandFloor          = getLongOrNull(obj, "band_floor");
		item.bandCeiling        = getLongOrNull(obj, "band_ceiling");
		item.bandMarginPct      = getDouble(obj, "band_margin_pct", 0);
		item.bandVolumeCoverage = getIntOrNull(obj, "band_volume_coverage");
		return item;
	}

	private DipItem parseDipItem(JsonObject obj)
	{
		DipItem item = new DipItem();
		item.itemId       = getInt(obj, "item_id", 0);
		item.name         = getString(obj, "name", "Unknown");
		item.buyPrice     = getLong(obj, "buy_price", 0);
		item.hourlyVolume = getInt(obj, "hourly_volume", 0);
		item.dailyVolume  = getInt(obj, "daily_volume", 0);
		item.buyLimit     = getInt(obj, "buy_limit", 0);
		item.members      = getBool(obj, "members", true);
		item.lastUpdated  = getString(obj, "last_updated", "");
		item.type         = getString(obj, "type", "24h_dip");
		item.avg24hBuy    = getLongOrNull(obj,   "avg_24h_buy");
		item.dipPct       = getDoubleOrNull(obj, "dip_pct");
		item.atlFloor     = getLongOrNull(obj,   "atl_floor");
		item.buyVsAtlPct  = getDoubleOrNull(obj, "buy_vs_atl_pct");
		item.dipPct1d     = getDoubleOrNull(obj, "dip_pct_1d");
		item.dipPct7d     = getDoubleOrNull(obj, "dip_pct_7d");
		item.dipPct30d    = getDoubleOrNull(obj, "dip_pct_30d");
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

	private DumpItem parseDumpItem(JsonObject obj)
	{
		DumpItem item = new DumpItem();
		String t = getString(obj, "tier", "");
		item.tier             = t.isEmpty() ? null : t;
		item.itemId           = getInt(obj, "item_id", 0);
		item.name             = getString(obj, "name", "Unknown");
		item.buyPrice         = getLong(obj, "buy_price", 0);
		item.sellPrice        = getLong(obj, "sell_price", 0);
		item.profit           = getLong(obj, "profit", 0);
		if (item.buyPrice == 0)
		{
			item.buyPrice = getLong(obj, "current_price", 0);
		}
		item.dumpScore        = getInt(obj, "dump_score", 0);
		item.dumpPct          = getDouble(obj, "dump_pct", 0);
		item.dumpStatus       = getString(obj, "dump_status", "none");
		item.lastDumpHoursAgo = getDoubleOrNull(obj, "last_dump_hours_ago");
		item.hourlyVolume     = getInt(obj, "hourly_volume", 0);
		item.buyLimit         = getInt(obj, "buy_limit", 0);
		item.members          = getBool(obj, "members", true);

		item.roiPct              = getDoubleOrNull(obj, "roi_pct");
		item.patternStale        = getBoolOrNull(obj,   "pattern_stale");
		item.dailyVolume         = getIntOrNull(obj,    "daily_volume");
		item.periodHours         = getIntOrNull(obj,    "period_hours");
		item.dumpPeakHourUtc     = getIntOrNull(obj,    "dump_peak_hour_utc");
		item.isClockAligned      = getBoolOrNull(obj,   "is_clock_aligned");
		return item;
	}


	@FunctionalInterface
	private interface JsonMapper<T>
	{
		T map(JsonObject obj);
	}

	private <T> void parsePagedResponse(Response response, String arrayKey,
	                                    JsonMapper<T> mapper,
	                                    BiConsumer<List<T>, Integer> callback)
	{
		if (response.code() == 429)
		{
			markRateLimited(response);
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

	private long readExpectedProfit(JsonObject obj)
	{
		if (obj == null) return 0L;
		if (obj.has("expected_profit") && !obj.get("expected_profit").isJsonNull())
		{
			try { return obj.get("expected_profit").getAsLong(); }
			catch (Exception ignored) {}
		}
		Long perHour = getLongOrNull(obj, "expected_profit_per_hour");
		Double fillHrs = getDoubleOrNull(obj, "estimated_fill_hours");
		if (perHour != null && fillHrs != null && fillHrs > 0)
		{
			return Math.round(perHour * fillHrs);
		}
		int  qty   = getInt(obj,  "qty",             0);
		long perU  = getLong(obj, "profit_per_unit", 0);
		return (long) qty * perU;
	}

	private int[] parseIntArray(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
		JsonElement el = obj.get(key);
		if (!el.isJsonArray()) return null;
		JsonArray arr = el.getAsJsonArray();
		int[] out = new int[arr.size()];
		for (int i = 0; i < arr.size(); i++)
		{
			try { out[i] = arr.get(i).getAsInt(); }
			catch (Exception ignored) { out[i] = 0; }
		}
		return out;
	}

	private void parseSlotFills(JsonObject obj, String key, List<com.o7flip.model.SlotFill> target)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return;
		JsonElement el = obj.get(key);
		if (!el.isJsonArray()) return;
		JsonArray arr = el.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++)
		{
			try
			{
				JsonObject f = arr.get(i).getAsJsonObject();
				com.o7flip.model.SlotFill fill = new com.o7flip.model.SlotFill();
				fill.qty       = getInt(f,    "qty",        0);
				fill.priceEach = getLong(f,   "price_each", 0);
				fill.tradedAt  = getString(f, "traded_at",  "");
				target.add(fill);
			}
			catch (Exception ignored) {}
		}
	}

	private <T> List<T> parseArray(Response response, String arrayKey, JsonMapper<T> mapper)
	{
		if (response.code() == 429)
		{
			markRateLimited(response);
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

	private Boolean getBoolOrNull(JsonObject obj, String key)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? null : el.getAsBoolean();
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

	private String getStringOrNull(JsonObject obj, String key)
	{
		JsonElement el = obj.get(key);
		return (el == null || el.isJsonNull()) ? null : el.getAsString();
	}
}
