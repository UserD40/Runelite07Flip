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
import com.o7flip.model.DumpItem;
import com.o7flip.model.FlipItem;
import com.o7flip.model.DipItem;
import com.o7flip.model.HighAlchItem;
import com.o7flip.model.ItemInsights;
import com.o7flip.model.MoonItem;
import com.o7flip.model.MoonSet;
import com.o7flip.model.OptimizeResult;
import com.o7flip.model.RecommendedPrices;
import com.o7flip.model.ScreenerMatch;
import com.o7flip.model.ScreenerPreset;
import com.o7flip.model.SearchResultItem;
import com.o7flip.model.SpikeItem;
import com.o7flip.model.TeleTablet;
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

	/** Epoch ms until which all requests should be skipped after a 429 response. */
	private volatile long backoffUntil = 0;

	/** One-shot log guard so we only report sanitisation once per plugin session. */
	private volatile boolean loggedKeySanitisation = false;

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

	/**
	 * Strips anything that isn't alphanumeric or a hyphen — defeats whitespace,
	 * quote characters, smart quotes, and trailing newlines that can sneak in
	 * when pasting from a "Copy API key" button. Returns null when the result
	 * is empty (no key configured).
	 */
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
	// Trade Tracker
	// -------------------------------------------------------------------------

	/**
	 * Bulk-equivalent of {@link #postTradeRecord} for {@code /tracker/bulk}.
	 * Idempotent on the server via {@code unique_trade(userId, itemId,
	 * tradedAt, isBuy)} — duplicates come back in the {@code duplicates}
	 * counter, not as errors. Server cap is 500 trades per request; the
	 * local trade window is bounded at MAX_TRADE_HISTORY which is well
	 * under that, so a single request handles a full backlog.
	 *
	 * Used at startup to recover any trades that were recorded locally
	 * but never reached the server (the May 14-onward zero-delta terminal-
	 * state regression). Cheap to run unconditionally because the server
	 * dedup keeps repeat submissions a no-op.
	 */
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

		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		// Parallel list of the TradeRecords that actually made it into the
		// request, in request-array order. The server's response trades[]
		// uses {@code index} into this array, so we use it to map each
		// returned {@code trade_id} back to the local row's
		// {@link TradeRecord#offerInstanceId}.
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

	/**
	 * Reads the {@code trades[]} array on a /tracker/bulk response and builds
	 * a map from local {@code offerInstanceId} → server {@code trade_id} for
	 * every row that has both. Returns an empty map when the server response
	 * pre-dates the {@code trades[]} field, so a stale server is a no-op
	 * (the plugin falls back to fingerprint dedup on next history sync).
	 */
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
				// Malformed row — skip it. The other rows still map cleanly.
			}
		}
		return out;
	}

	/** Result of a {@code /tracker/bulk} call. {@code ok} false means the
	 *  whole batch failed (network, 5xx, parse error); caller may retry.
	 *  {@link #tradeIdsByOfferInstanceId} is the per-row mapping the plugin
	 *  uses to stamp newly-synced rows with their canonical server id. */
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

	/**
	 * POSTs a single trade to {@code /tracker}. The callback fires with the
	 * server-issued {@code trade_id} (nullable) so the caller can stamp the
	 * local {@link TradeRecord} and avoid relying on fingerprint dedup on the
	 * next /tracker/history sync. The callback receives null on:
	 * <ul>
	 *   <li>HTTP failure (network, 4xx, 5xx)</li>
	 *   <li>response body that parses but omits {@code trade_id} (e.g.,
	 *       duplicate where the server's secondary lookup failed)</li>
	 *   <li>parse error</li>
	 * </ul>
	 * A null id is not a failure — the row will reconcile on the next history
	 * sync via fingerprint dedup as before. Callers may pass {@code null} for
	 * {@code onTradeId} when they don't care to stamp.
	 */
	public void postTradeRecord(TradeRecord trade, Consumer<Long> onTradeId)
	{
		JsonObject body = new JsonObject();
		body.addProperty("item_id",   trade.itemId);
		body.addProperty("name",      trade.name);
		body.addProperty("is_buy",    trade.isBuy);
		body.addProperty("quantity",  trade.quantity);
		body.addProperty("price_each", trade.priceEach);
		body.addProperty("total_gp",  trade.totalGp);
		body.addProperty("timestamp", trade.timestamp);
		body.addProperty("partial",   trade.partial);

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
				if (onTradeId != null) onTradeId.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] postTradeRecord HTTP {}", response.code());
						if (onTradeId != null) onTradeId.accept(null);
						return;
					}
					JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
					Long tradeId = getLongOrNull(root, "trade_id");
					if (onTradeId != null) onTradeId.accept(tradeId);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] postTradeRecord parse error: {}", e.getMessage());
					if (onTradeId != null) onTradeId.accept(null);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	/**
	 * Fetches the user's stored trade history from 07flip.com. Requires an
	 * API key — without one the callback receives an empty list and false.
	 *
	 * Callback receives (trades, hasMore). Called once with an empty list
	 * on any failure (network, 401, parse error, no key).
	 */
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
						markRateLimited();
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

	/**
	 * Server-authoritative My Trades stats — merges plugin trade_records with
	 * website-logged tracker_entries and de-dupes via flip_trade_links so the
	 * plugin doesn't have to. Requires an API key; returns null on any failure
	 * (no key, network, 401, parse error, 404 if endpoint not yet deployed).
	 *
	 * Callers should treat null as "fall back to local FIFO ProfitCalculator
	 * result" — same UX as a user with no key.
	 */
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
						markRateLimited();
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						// 404 is expected until the server deploys the endpoint —
						// log at debug, not warn, to avoid scaring early users.
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
					stats.totalInvestedOpen   = getLong(json, "total_invested_open",    0L);
					stats.closedCount         = getInt(json,  "closed_count",           0);
					stats.openCount           = getInt(json,  "open_count",             0);
					stats.winRate             = getDouble(json, "win_rate",             0.0);
					stats.hitRate             = getDouble(json, "hit_rate",             0.0);
					stats.updatedAt           = getString(json, "updated_at",           "");

					JsonElement bestEl = json.get("best_flip");
					if (bestEl != null && !bestEl.isJsonNull() && bestEl.isJsonObject())
					{
						JsonObject best = bestEl.getAsJsonObject();
						TrackerStats.BestFlip bf = new TrackerStats.BestFlip();
						bf.entryId       = getString(best, "entry_id",         "");
						bf.itemId        = getInt(best,    "item_id",          0);
						bf.name          = getString(best, "name",             "");
						bf.profit        = getLong(best,   "profit",           0L);
						bf.source        = getString(best, "source",           "declared");
						bf.fullyClosedAt = getString(best, "fully_closed_at",  "");
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

	/**
	 * Pins the current 07Flip rec_buy / rec_sell prices for an item to the
	 * user's account. Sell-side overlay later reads these via the {@code frozen}
	 * field on {@code /v2/item/{id}} so projected margin survives market drift
	 * between buy placement and sell setup. Best-effort — server-side state
	 * matters, the callback exists only for log / retry hooks.
	 */
	public void postFreeze(int itemId, long frozenBuy, long frozenSell, Consumer<Boolean> callback)
	{
		String key = sanitizedApiKey();
		if (key == null)
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

	/**
	 * Clears any active freeze for an item. Silent no-op server-side if none
	 * existed. Called by the plugin after a sell FIFO-matches a buy in
	 * tradeHistory, closing out the flip cycle.
	 */
	public void postUnfreeze(int itemId, Consumer<Boolean> callback)
	{
		String key = sanitizedApiKey();
		if (key == null)
		{
			if (callback != null) callback.accept(false);
			return;
		}
		// Empty JSON body — server only needs the {itemId, userId} pair.
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

	/**
	 * Fetches the per-item Insights blob shown on the Insights tab. Premium
	 * fields ({@code rec_*}, {@code score.signal}, {@code projection}) come
	 * back as null for free users — callers must check {@code premiumLocked}
	 * to decide whether to show the upsell card.
	 *
	 * Open to free + premium API keys, and even to anonymous requests
	 * (server returns the open subset). Callback receives null on any
	 * failure: 404 (unknown item), 400 (non-numeric id), network, parse.
	 */
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
						markRateLimited();
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
			out.ranges = r;
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
			f.frozenAt = getString(fz, "frozen_at", "");
			out.frozen = f;
		}

		out.sparkline24hBuy   = parseNullableLongArray(root, "sparkline_24h_buy");
		out.sparkline24hSell  = parseNullableLongArray(root, "sparkline_24h_sell");
		out.sparkline24hStart = getString(root, "sparkline_24h_start", "");

		return out;
	}

	/**
	 * Parses a JSON number array where individual elements may be null —
	 * preserved as Java nulls so callers can distinguish "no data here" from
	 * a real zero. Used for the buy/sell sparkline arrays where the current
	 * incomplete hour is sent as null.
	 */
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
		b.mid     = getLong(obj, "mid",  0L);
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

	// -------------------------------------------------------------------------
	// Auth
	// -------------------------------------------------------------------------

	/**
	 * @param onSuccess fired exactly once on a successful 200 response with
	 *                  parsed AuthStatus.
	 * @param onTransient fired once if the call failed in a way the server
	 *                    explicitly invites a retry on — currently HTTP 503
	 *                    (deploy warmup) or network failure. Callers should
	 *                    schedule a one-shot retry. NOT fired for permanent
	 *                    errors (401/403/parse errors) — auth state is
	 *                    deliberately left unchanged for those.
	 */
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
						// Any 5xx is treated as transient — covers the 503 warmup
						// guard our server uses after a deploy, plus 502/504 from
						// the gateway during the same window. Schedule a retry;
						// don't change the user-facing auth state in the meantime.
						log.debug("[07Flip] /auth returned {} — transient server error, will retry", code);
						if (onTransient != null)
						{
							onTransient.run();
						}
						return;
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						// Permanent error (401/403/etc) — leave existing auth state unchanged.
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

	/** Convenience overload kept for any callers that don't care about retry signalling. */
	public void fetchAuthStatus(Consumer<AuthStatus> callback)
	{
		fetchAuthStatus(callback, null);
	}

	// -------------------------------------------------------------------------
	// Paginated endpoints — callback receives (items, serverTotal)
	// serverTotal defaults to items.size() when the server does not return "total"
	// -------------------------------------------------------------------------

	/** Backwards-compatible overload — defaults sort to flip07Score (server default). */
	public void fetchFlips(String preset, long minProfit, long priceMin, long priceMax,
	                       int page, BiConsumer<List<FlipItem>, Integer> callback)
	{
		fetchFlips(preset, "flip07Score", minProfit, priceMin, priceMax, 0L, page, callback, null);
	}

	/**
	 * Full request including sort, optional cashStack annotation, and a
	 * separate callback for premium-gated rejections (HTTP 403). When the
	 * server returns 403 with {@code error: premium_required}, the empty
	 * list is delivered to {@code callback} and {@code onPremiumRequired}
	 * is also invoked with the {@code upgrade_url} so the UI can surface
	 * an "upgrade to unlock" prompt instead of just showing a blank list.
	 *
	 * @param sort  one of "flip07Score" | "potentialProfit" | "profit" | "roi" | "recProfit"
	 */
	public void fetchFlips(String preset, String sort, long minProfit, long priceMin, long priceMax,
	                       long cashStack, int page,
	                       BiConsumer<List<FlipItem>, Integer> callback,
	                       Consumer<String> onPremiumRequired)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/flips?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (preset != null && !preset.isEmpty())
		{
			url.append("&preset=").append(preset);
		}
		if (sort != null && !sort.isEmpty())
		{
			// "buyPriceDesc" / "sellPriceDesc" pseudo-keys are split here into
			// real (sort, order) pairs so the panel can offer ascending +
			// descending variants of the same field in one dropdown.
			String realSort = sort;
			String order = null;
			if (sort.endsWith("Desc"))
			{
				realSort = sort.substring(0, sort.length() - 4);
				order = "desc";
			}
			else if ("buyPrice".equals(sort) || "sellPrice".equals(sort))
			{
				// Price ascending is the natural intuition for "buy cheap" —
				// override the server's default (desc) for these two keys.
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

	/**
	 * Fetches the /dips feed — a mix of dip-window and ATL items distinguished
	 * by the {@code type} field on each row.
	 *
	 * @param sort one of {@code "recent"} (default), {@code "dip_pct"} or
	 *             {@code "atl_pct"}; pass null/empty for server default.
	 * @param window {@code "1d"} (default), {@code "7d"}, or {@code "30d"}.
	 *               Determines which dip window the server uses to populate
	 *               {@code dip_pct} and to gate the -5% filter.
	 */
	public void fetchDips(String sort, String window, int page,
	                      BiConsumer<List<DipItem>, Integer> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/dips?limit=").append(PAGE_LIMIT)
			.append("&page=").append(page);
		if (sort != null && !sort.isEmpty())
		{
			url.append("&sort=").append(sort);
		}
		// "1d" is the server default — sending it explicitly is redundant.
		// Anything else gets passed through verbatim.
		if (window != null && !window.isEmpty() && !"1d".equals(window))
		{
			url.append("&activity_window=").append(window);
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

	// -------------------------------------------------------------------------
	// /high-alch — High Alchemy profit list (anon, no auth)
	// -------------------------------------------------------------------------

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
					markRateLimited();
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

	// -------------------------------------------------------------------------
	// /tele-tablets — Tablet crafting profitability (anon, no auth)
	// -------------------------------------------------------------------------

	public void fetchTeleTablets(String sort, String spellbook, boolean profitableOnly,
	                             Consumer<List<TeleTablet>> callback)
	{
		StringBuilder url = new StringBuilder(BASE_URL + "/tele-tablets?");
		boolean first = true;
		if (sort != null && !sort.isEmpty())
		{
			url.append("sort=").append(sort);
			first = false;
		}
		if (spellbook != null && !spellbook.isEmpty())
		{
			if (!first) url.append('&');
			url.append("spellbook=").append(spellbook);
			first = false;
		}
		if (profitableOnly)
		{
			if (!first) url.append('&');
			url.append("profitable=true");
		}
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchTeleTablets failed: {}", e.getMessage());
				callback.accept(new ArrayList<>());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				callback.accept(parseArray(response, "tablets", O7FlipApiClient.this::parseTeleTablet));
			}
		});
	}

	private TeleTablet parseTeleTablet(JsonObject obj)
	{
		TeleTablet t = new TeleTablet();
		t.name         = getString(obj, "name", "Unknown");
		t.tabletId     = getInt(obj, "tablet_id", 0);
		t.spellbook    = getString(obj, "spellbook", "Standard");
		t.materialCost = getLong(obj, "material_cost", 0);
		t.sellPrice    = getLong(obj, "sell_price", 0);
		t.sellAfterTax = getLong(obj, "sell_after_tax", 0);
		t.profit       = getLong(obj, "profit", 0);
		t.roiPct       = getDouble(obj, "roi_pct", 0);
		t.dailyVolume  = getIntOrNull(obj, "daily_volume");
		JsonArray arr  = obj.getAsJsonArray("ingredients");
		if (arr != null)
		{
			for (int i = 0; i < arr.size(); i++)
			{
				try
				{
					JsonObject io = arr.get(i).getAsJsonObject();
					TeleTablet.Ingredient ing = new TeleTablet.Ingredient();
					ing.name       = getString(io, "name", "");
					ing.itemId     = getInt(io, "item_id", 0);
					ing.qty        = getInt(io, "qty", 0);
					ing.unitPrice  = getLong(io, "unit_price", 0);
					ing.totalPrice = getLong(io, "total_price", 0);
					t.ingredients.add(ing);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] tele-tablet ingredient parse skipped: {}", e.getMessage());
				}
			}
		}
		return t;
	}

	// -------------------------------------------------------------------------
	// /favourites — user's saved item list (auth required)
	// -------------------------------------------------------------------------

	/**
	 * Calls back with the user's favourites, enriched with the same flip-row
	 * fields {@link FlipItem} carries. Items the server marks {@code stale}
	 * are returned with whatever non-null fields it could populate — usually
	 * just item_id + name.
	 *
	 * No API key → callback fires once with an empty list.
	 */
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
				// Parse + log result count. The body has to be consumed before
				// we can read the parsed list, so we read the string once and
				// reuse it for the parse + the diagnostic.
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
						// Surfacing this at INFO is intentional — when the user
						// has favourites on the website but the plugin reads 0,
						// this is the diagnostic. (Could be apiKey↔userId
						// mismatch, replication lag, or an empty list.)
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

	/** Optional handler the plugin sets to be notified of 401s from
	 *  /favourites. Plugin uses it to surface a "key invalid" prompt. */
	private volatile Runnable onUnauthorized;

	/** Plugin wires in the 401 handler — invoked from the OkHttp callback
	 *  thread, so the handler is responsible for marshalling back to EDT. */
	public void setOnFavouritesUnauthorized(Runnable handler)
	{
		this.onUnauthorized = handler;
	}

	/**
	 * Adds {@code itemId} to the user's favourites. {@code onResult} is fired
	 * with {@code true} on a 2xx response and {@code false} otherwise. Always
	 * fired exactly once.
	 */
	public void addFavourite(int itemId, Consumer<Boolean> onResult)
	{
		mutateFavourite("POST", itemId, onResult);
	}

	/** Removes {@code itemId} from the user's favourites. */
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

	// -------------------------------------------------------------------------
	// /optimize — premium-gated 8-slot portfolio optimizer
	// -------------------------------------------------------------------------

	/**
	 * Calls {@code POST /api/runelite/optimize} with the user's chosen
	 * inputs and dispatches one of three callbacks depending on the
	 * response:
	 * <ul>
	 *   <li>{@code onSuccess(OptimizeResult)} on 200 — full allocation plan.</li>
	 *   <li>{@code onPremiumRequired(upgradeUrl)} on 403 — free user. The
	 *       URL is the server's upgrade target, ready to feed into LinkBrowser.</li>
	 *   <li>{@code onError(reason)} on validation 400, transport failure, or
	 *       parse failure. {@code reason} is the server's error code (e.g.
	 *       {@code invalid_capital}) or a short transport-error string.</li>
	 * </ul>
	 *
	 * Each callback fires at most once.
	 */
	public void fetchOptimize(long capital, int slots, String risk,
	                          int maxFillHours, Boolean members, java.util.List<Integer> excludeItemIds,
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
				JsonObject s = root.getAsJsonObject("summary");
				OptimizeResult.Summary sum = out.summary;
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
			}
			if (root.has("allocations") && root.get("allocations").isJsonArray())
			{
				JsonArray arr = root.getAsJsonArray("allocations");
				for (int i = 0; i < arr.size(); i++)
				{
					try
					{
						JsonObject a = arr.get(i).getAsJsonObject();
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
						// Live-tracking fields (only populated on /optimize/active responses)
						parseSlotFills(a, "buys",  al.buys);
						parseSlotFills(a, "sells", al.sells);
						al.state                 = com.o7flip.model.SlotState.fromWire(getString(a, "state", "pending"));
						out.allocations.add(al);
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

	// -------------------------------------------------------------------------
	// /optimize/active — cross-surface session sync (website + plugin)
	// -------------------------------------------------------------------------

	/**
	 * GET the currently-active optimiser session. The endpoint returns either
	 * HTTP 200 with the session body or HTTP 204 (no active session). On 204
	 * the callback receives null — that's the "fresh user, nothing to hydrate"
	 * signal, NOT an error.
	 *
	 * Bearer apiKey auth. 60/min/IP rate-limit on the server side.
	 */
	public void fetchActiveSession(Consumer<com.o7flip.model.OptimizerSession> callback)
	{
		String key = sanitizedApiKey();
		if (key == null) { callback.accept(null); return; }

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

	/**
	 * Upsert the active session. Body matches the GET response shape. The
	 * server is last-write-wins (no version check). Caller should debounce
	 * to ~1s so a flurry of local changes collapses into one POST.
	 */
	public void postActiveSession(com.o7flip.model.OptimizerSession session, Consumer<Boolean> onComplete)
	{
		String key = sanitizedApiKey();
		if (key == null) { if (onComplete != null) onComplete.accept(false); return; }
		if (session == null) { if (onComplete != null) onComplete.accept(false); return; }

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
					boolean ok = response.isSuccessful();
					if (!ok) log.warn("[07Flip] /optimize/active POST HTTP {}", response.code());
					if (onComplete != null) onComplete.accept(ok);
				}
				finally { response.close(); }
			}
		});
	}

	/** Clear the active session. 204 expected. */
	public void deleteActiveSession(Consumer<Boolean> onComplete)
	{
		String key = sanitizedApiKey();
		if (key == null) { if (onComplete != null) onComplete.accept(false); return; }

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
			// Server wraps the payload in a {"session": {...}} envelope (per
			// src/app/api/runelite/optimize/active/route.ts). Unwrap when
			// present; fall through to root-level if a future endpoint
			// version flattens it.
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
						JsonObject a = arr.get(i).getAsJsonObject();
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
						parseSlotFills(a, "buys",  al.buys);
						parseSlotFills(a, "sells", al.sells);
						al.state                 = com.o7flip.model.SlotState.fromWire(getString(a, "state", "pending"));
						s.slots.add(al);
					}
					catch (Exception ignored) {}
				}
			}
		}
		catch (Exception e)
		{
			log.warn("[07Flip] /optimize/active parse error: {}", e.getMessage());
		}
		return s;
	}

	/**
	 * Serialise a session back to the JSON shape the server expects. Mirrors
	 * the GET response: wrapped in a {@code {"session": {...}}} envelope.
	 * Keep this symmetric with {@link #parseSession} or POST round-trips
	 * silently drop fields.
	 */
	private String sessionToJson(com.o7flip.model.OptimizerSession session)
	{
		JsonObject body = new JsonObject();
		JsonObject inputs = new JsonObject();
		inputs.addProperty("capital", session.inputs.capital);
		inputs.addProperty("slots",   session.inputs.slots);
		if (session.inputs.maxFillHours != null) inputs.addProperty("max_fill_hours", session.inputs.maxFillHours);
		if (session.inputs.risk != null && !session.inputs.risk.isEmpty()) inputs.addProperty("risk", session.inputs.risk);
		if (session.inputs.members != null) inputs.addProperty("members", session.inputs.members);
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
				if (al.priceSource != null)     s.addProperty("price_source",  al.priceSource);
				if (al.rawBuyPrice != null)     s.addProperty("raw_buy_price", al.rawBuyPrice);
				if (al.rawSellPrice != null)    s.addProperty("raw_sell_price", al.rawSellPrice);
				if (al.rawProfitPerUnit != null) s.addProperty("raw_profit_per_unit", al.rawProfitPerUnit);
				if (al.estimatedFillHours != null) s.addProperty("estimated_fill_hours", al.estimatedFillHours);
				if (al.realisticQtyCap != null) s.addProperty("realistic_qty_cap", al.realisticQtyCap);
				if (al.hourlyTrend != null)
				{
					JsonArray ht = new JsonArray();
					for (int v : al.hourlyTrend) ht.add(v);
					s.add("hourly_trend", ht);
				}
				s.add("buys",  fillsToJson(al.buys));
				s.add("sells", fillsToJson(al.sells));
				s.addProperty("state", al.state == null ? "pending" : al.state.wire());
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
		// POST body is the raw session shape (not wrapped in a "session"
		// envelope). The envelope only appears on the GET RESPONSE side —
		// confirmed via a 400 from the server when we wrapped the POST too.
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

	// -------------------------------------------------------------------------
	// /screeners — technical screeners (premium gates matches)
	// -------------------------------------------------------------------------

	/**
	 * Fetches the list of all available screener presets and their current
	 * matches. For free / anonymous callers, {@code matches} is empty on every
	 * preset and the per-preset {@code premiumRequired} flag is set.
	 */
	public void fetchScreeners(Consumer<ScreenerPreset.Bundle> callback)
	{
		fetch(BASE_URL + "/screeners?limit=25", new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchScreeners failed: {}", e.getMessage());
				callback.accept(new ScreenerPreset.Bundle());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (response.code() == 429)
				{
					markRateLimited();
				}
				if (!response.isSuccessful() || response.body() == null)
				{
					log.warn("[07Flip] fetchScreeners HTTP {}", response.code());
					callback.accept(new ScreenerPreset.Bundle());
					return;
				}
				try
				{
					JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
					ScreenerPreset.Bundle bundle = new ScreenerPreset.Bundle();
					bundle.premium       = getBool(root, "premium", false);
					bundle.authenticated = getBool(root, "authenticated", false);
					bundle.updatedAt     = getString(root, "updated_at", "");
					bundle.systemPresets = parsePresetArray(root, "system_presets", false, bundle.premium);
					bundle.userPresets   = parsePresetArray(root, "user_presets",   true,  bundle.premium);
					callback.accept(bundle);
				}
				catch (Exception e)
				{
					log.warn("[07Flip] fetchScreeners parse error: {}", e.getMessage());
					callback.accept(new ScreenerPreset.Bundle());
				}
			}
		});
	}

	private List<ScreenerPreset> parsePresetArray(JsonObject root, String key, boolean userScope, boolean premium)
	{
		List<ScreenerPreset> out = new ArrayList<>();
		if (!root.has(key) || !root.get(key).isJsonArray())
		{
			return out;
		}
		JsonArray arr = root.getAsJsonArray(key);
		for (int i = 0; i < arr.size(); i++)
		{
			try
			{
				JsonObject obj = arr.get(i).getAsJsonObject();
				ScreenerPreset p = new ScreenerPreset();
				p.key         = getString(obj, "key", "");
				p.name        = getString(obj, "name", "Untitled");
				p.description = getString(obj, "description", "");
				p.timeframe   = getString(obj, "timeframe", "daily");
				p.scope       = getString(obj, "scope", userScope ? "user" : "system");
				p.count       = getInt(obj, "count", 0);
				// matches array may legitimately be empty for premium-gated rows.
				JsonArray m = obj.getAsJsonArray("matches");
				if (m != null)
				{
					for (int j = 0; j < m.size(); j++)
					{
						try
						{
							p.matches.add(parseScreenerMatch(m.get(j).getAsJsonObject()));
						}
						catch (Exception e)
						{
							log.warn("[07Flip] screener match parse skipped: {}", e.getMessage());
						}
					}
				}
				// Server marks per-row gating via a top-level premium_required on single-mode,
				// and implicit empty-matches on list-mode for non-premium callers.
				p.premiumRequired = !premium && p.count == 0 && p.matches.isEmpty();
				p.upgradeUrl      = getString(obj, "upgrade_url", "https://07flip.com/premium");
				out.add(p);
			}
			catch (Exception e)
			{
				log.warn("[07Flip] screener preset parse skipped: {}", e.getMessage());
			}
		}
		return out;
	}

	private ScreenerMatch parseScreenerMatch(JsonObject obj)
	{
		ScreenerMatch m = new ScreenerMatch();
		m.itemId       = getInt(obj, "item_id", 0);
		m.name         = getString(obj, "name", "Unknown");
		m.macdHist     = getDoubleOrNull(obj, "macd_hist");
		String cross   = getString(obj, "macd_cross", "");
		m.macdCross    = cross.isEmpty() ? null : cross;
		m.volSurge     = getDoubleOrNull(obj, "vol_surge");
		m.bbPosition   = getDoubleOrNull(obj, "bb_position");
		m.pricePos30d  = getDoubleOrNull(obj, "price_pos_30d");
		m.pricePos90d  = getDoubleOrNull(obj, "price_pos_90d");
		m.pct1d        = getDoubleOrNull(obj, "pct_1d");
		m.pct7d        = getDoubleOrNull(obj, "pct_7d");
		m.pct30d       = getDoubleOrNull(obj, "pct_30d");
		// Optional market-data fields — null when the server doesn't ship
		// them. The plugin enriches from cached Flip data as a fallback.
		m.buyPrice     = getLongOrNull(obj,   "buy_price");
		m.sellPrice    = getLongOrNull(obj,   "sell_price");
		m.profit       = getLongOrNull(obj,   "profit");
		m.roiPct       = getDoubleOrNull(obj, "roi_pct");
		m.flip07Score  = getIntOrNull(obj,    "flip07_score");
		return m;
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

	public void fetchDumps(String sort, long minProfit, long priceMin, long priceMax,
	                       int minScore, boolean activeOnly, String tier,
	                       int page, Consumer<DumpItem.Response> callback)
	{
		// confirmedOnly was dropped — the server now enforces confirmed_bot=true
		// as a base filter on /dumps, so every row in the response is verified.
		// Sending the param is a no-op; not sending it keeps the URL clean.
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
		// v5 tier filter — pass "confirmed" or "likely" to narrow, omit for All.
		// "all" is the server default; sending it explicitly is fine but verbose.
		if (tier != null && !tier.isEmpty() && !"all".equals(tier))
		{
			url.append("&tier=").append(tier);
		}
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchDumps failed: {}", e.getMessage());
				callback.accept(emptyDumpsResponse());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				callback.accept(parseDumpsResponse(response));
			}
		});
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
			markRateLimited();
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

	/**
	 * Fetches the bot-dumps feed — items currently being mass-dumped by
	 * automated price-collapse detectors. Same response shape as
	 * {@link #fetchDumps}, served from a different endpoint that pulls
	 * specifically from the bot-driven dump signal.
	 */
	public void fetchBotDumps(String sort, long minProfit, long priceMin, long priceMax,
	                          int minScore, boolean activeOnly, String tier,
	                          int page, Consumer<DumpItem.Response> callback)
	{
		// confirmedOnly removed from /bot-dumps for the same reason as /dumps —
		// server enforces the base confirmed-bot filter.
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
		fetch(url.toString(), new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("[07Flip] fetchBotDumps failed: {}", e.getMessage());
				callback.accept(emptyDumpsResponse());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				callback.accept(parseDumpsResponse(response));
			}
		});
	}

	/**
	 * Loads alerts in one shot — pagination dropped per the redesign. Server
	 * caps at 200; pending vs successful filtering happens client-side from
	 * the {@code status} field. Free users only ever receive successful
	 * alerts regardless of the {@code ?status=} query, so callers don't need
	 * to gate the request.
	 */
	public void fetchAlerts(BiConsumer<List<AlertItem>, Integer> callback)
	{
		String url = BASE_URL + "/alerts?limit=200&status=all";
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
		BiConsumer<List<DumpItem>, Integer>  onDumps,
		BiConsumer<List<AlertItem>, Integer> onAlerts,
		Consumer<List<BarrowsSet>>           onBarrows,
		Consumer<List<MoonSet>>              onMoon,
		Consumer<List<DecantItem>>           onDecanting,
		Consumer<String>                     onConnectUrl
	)
	{
		JsonObject body = new JsonObject();
		body.add("sections", sections);
		RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, gson.toJson(body));

		Request.Builder builder = new Request.Builder()
			// v2 bundle returns full-shape flips rows (members + flip07_score
			// + rec_buy_price/rec_sell_price/rec_profit). v1 still serves the
			// legacy 8-key flips section. Other endpoints stay on v1 — only
			// /bundle has a v2 equivalent right now.
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

		// 07Flip recommended-price aggregates — null-together when set has no rec data
		s.recTotalBrokenCost    = getLongOrNull(obj, "rec_total_broken_cost");
		s.recTotalNpcRepairCost = getLongOrNull(obj, "rec_total_npc_repair_cost");
		s.recTotalPohRepairCost = getLongOrNull(obj, "rec_total_poh_repair_cost");
		s.recNpcProfit          = getLongOrNull(obj, "rec_npc_profit");
		s.recPohProfit          = getLongOrNull(obj, "rec_poh_profit");
		s.recSetProfit          = getLongOrNull(obj, "rec_set_profit");
		s.recBestProfit         = getLongOrNull(obj, "rec_best_profit");
		String recStrat         = getString(obj, "rec_best_strategy", "");
		s.recBestStrategy       = recStrat.isEmpty() ? null : recStrat;

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
					item.recBrokenBuyPrice    = getLongOrNull(io, "rec_broken_buy_price");
					item.recRepairedSellPrice = getLongOrNull(io, "rec_repaired_sell_price");
					item.recRepairedAfterTax  = getLongOrNull(io, "rec_repaired_after_tax");
					item.recNpcProfit         = getLongOrNull(io, "rec_npc_profit");
					item.recPohProfit         = getLongOrNull(io, "rec_poh_profit");
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

	/**
	 * Fetch the per-item p10/p90 recommended buy/sell prices from
	 * {@code GET /api/runelite/recommended-prices?itemId=…}. Used by the GE
	 * overlay to populate the auto-fill price suggestion.
	 *
	 * Callback receives {@code null} when the item has insufficient recent
	 * trade data, or on any error (network / HTTP non-200). The plugin
	 * should treat null as "no recommendation available" and fall back to
	 * whatever it already shows.
	 */
	public void fetchRecommendedPrices(int itemId, Consumer<RecommendedPrices> callback)
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
					if (response.code() == 429)
					{
						markRateLimited();
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("[07Flip] fetchRecommendedPrices HTTP {}", response.code());
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
		// Volume fields — null on rows where server doesn't echo them.
		item.hourlyVolume    = getIntOrNull(obj, "hourly_volume");
		item.dailyVolume     = getIntOrNull(obj, "daily_volume");
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
		// v5 tier classification — null on older responses; the renderer
		// treats null the same way it treats "likely" for safety.
		String t = getString(obj, "tier", "");
		item.tier             = t.isEmpty() ? null : t;
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

		// ── v3 dump-engine fields. All nullable — older responses skip them ─
		item.roiPct              = getDoubleOrNull(obj, "roi_pct");
		item.maxProfitAtLimit    = getLongOrNull(obj,   "max_profit_at_limit");
		item.patternStale        = getBoolOrNull(obj,   "pattern_stale");
		item.dailyVolume         = getIntOrNull(obj,    "daily_volume");
		item.periodHours         = getIntOrNull(obj,    "period_hours");
		item.dumpPeakHourUtc     = getIntOrNull(obj,    "dump_peak_hour_utc");
		item.isClockAligned      = getBoolOrNull(obj,   "is_clock_aligned");
		item.confirmedBot        = getBoolOrNull(obj,   "confirmed_bot");
		item.sellRatio           = getDoubleOrNull(obj, "sell_ratio");
		item.avgBurstIntervalMin = getDoubleOrNull(obj, "avg_burst_interval_min");
		item.recoveryPct         = getDoubleOrNull(obj, "recovery_pct");
		item.recoveryHours       = getDoubleOrNull(obj, "recovery_hours");
		item.recoverySamples     = getIntOrNull(obj,    "recovery_samples");

		// hourly_volumes — 24-element int array. Tolerant of missing / wrong
		// length: anything other than a non-empty array becomes null and the
		// row simply skips the sparkline.
		JsonArray hv = obj.has("hourly_volumes") && obj.get("hourly_volumes").isJsonArray()
			? obj.getAsJsonArray("hourly_volumes") : null;
		if (hv != null && hv.size() > 0)
		{
			int[] arr = new int[hv.size()];
			for (int i = 0; i < hv.size(); i++)
			{
				try { arr[i] = hv.get(i).getAsInt(); }
				catch (Exception e) { arr[i] = 0; }
			}
			item.hourlyVolumes = arr;
		}
		return item;
	}

	private AlertItem parseAlertItem(JsonObject obj)
	{
		AlertItem alert = new AlertItem();
		alert.itemId         = getInt(obj, "item_id", 0);
		alert.name           = getString(obj, "name", "Unknown");
		alert.tier           = getString(obj, "tier", "");

		// Prefer the new starting_price field; fall back to current_price for the
		// short window between client and server deploys when one side might be
		// stale. After the next plugin release the fallback can be dropped.
		alert.startingPrice  = getLong(obj, "starting_price", getLong(obj, "current_price", 0L));
		alert.currentPrice   = alert.startingPrice;   // legacy mirror
		alert.livePrice      = getLongOrNull(obj, "live_price");
		alert.sellTarget     = getLong(obj, "sell_target", 0L);
		alert.upsidePct      = getDouble(obj, "upside_pct", 0.0);
		alert.holdTime       = getString(obj, "hold_time", "");
		alert.high90d        = getLong(obj, "high_90d", 0L);
		alert.low90d         = getLong(obj, "low_90d", 0L);
		alert.drawdownPct    = getDouble(obj, "drawdown_pct", 0.0);
		alert.detectedAt     = getString(obj, "detected_at", "");

		alert.status         = getString(obj, "status", "pending");
		alert.achievedPrice  = getLongOrNull(obj, "achieved_price");
		alert.achievedAt     = obj.has("achieved_at") && !obj.get("achieved_at").isJsonNull()
			? obj.get("achieved_at").getAsString() : null;
		alert.realisedProfit = getLongOrNull(obj, "realised_profit");
		alert.realisedRoiPct = getDoubleOrNull(obj, "realised_roi_pct");

		// Server picks timeframe per-alert: daily-since-detection for older
		// alerts, last-24h hourly fallback for any alert detected within
		// the past day. Same field names either way — plugin renders blind
		// to which path was chosen.
		alert.sparklineBuy   = parseNullableLongArray(obj, "sparkline_buy");
		alert.sparklineSell  = parseNullableLongArray(obj, "sparkline_sell");
		alert.sparklineStart = getString(obj, "sparkline_start", "");
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

		s.recTotalBrokenCost    = getLongOrNull(obj, "rec_total_broken_cost");
		s.recTotalNpcRepairCost = getLongOrNull(obj, "rec_total_npc_repair_cost");
		s.recTotalPohRepairCost = getLongOrNull(obj, "rec_total_poh_repair_cost");
		s.recNpcProfit          = getLongOrNull(obj, "rec_npc_profit");
		s.recPohProfit          = getLongOrNull(obj, "rec_poh_profit");
		s.recSetProfit          = getLongOrNull(obj, "rec_set_profit");
		s.recBestProfit         = getLongOrNull(obj, "rec_best_profit");
		String recStrat         = getString(obj, "rec_best_strategy", "");
		s.recBestStrategy       = recStrat.isEmpty() ? null : recStrat;

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
					mi.recBrokenBuyPrice    = getLongOrNull(io, "rec_broken_buy_price");
					mi.recRepairedSellPrice = getLongOrNull(io, "rec_repaired_sell_price");
					mi.recRepairedAfterTax  = getLongOrNull(io, "rec_repaired_after_tax");
					mi.recNpcProfit         = getLongOrNull(io, "rec_npc_profit");
					mi.recPohProfit         = getLongOrNull(io, "rec_poh_profit");
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

	/**
	 * Reads the new {@code expected_profit} (cycle total, after tax) with a
	 * fallback that tolerates two legacy shapes during the transition:
	 * <ol>
	 *   <li>Old {@code expected_profit_per_hour} × {@code estimated_fill_hours}
	 *       — the math that produced the old extrapolated rate, reversed to
	 *       approximate the new cycle figure.</li>
	 *   <li>{@code qty × profit_per_unit} — last-ditch derivation if neither
	 *       new nor old fields are present.</li>
	 * </ol>
	 * Sessions saved before the server's semantic change still carry the
	 * old field, so a stale-cache GET round-trip doesn't blank the card.
	 */
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

	/**
	 * Pulls a plain {@code int[]} out of a JSON array field. Returns null if
	 * the field is missing or null (matching the server's "null when the DB
	 * query failed" semantic for {@code hourly_trend}). Non-numeric entries
	 * are skipped silently.
	 */
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

	/**
	 * Reads a {@code SlotFill[]} JSON array onto an existing target list.
	 * Used for {@code buys} / {@code sells} on LiveSlot — present only on
	 * {@code /optimize/active} responses, absent on plain {@code /optimize}.
	 */
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
}
