package com.donutdeck;

import com.donutdeck.core.MarketMath;
import com.donutdeck.core.PricePoint;
import com.donutdeck.core.Quote;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Talks only to the official DonutSMP public API. Requests are asynchronous,
 * rate-limited and never contain anything except the user's own API key and an
 * item search. No chat, player names or session data is uploaded.
 */
public final class MarketService {
	private static final Logger LOGGER = DonutDeckClient.LOGGER;
	private static final String BASE = "https://api.donutsmp.net/v1/auction/";
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final DeckState state;
	private final ConfigStore store;
	private final HttpClient http;
	private final AtomicBoolean inFlight = new AtomicBoolean(false);
	private final Set<String> warned = new java.util.HashSet<>();

	private volatile long nextRefreshAt;
	private volatile long lastAttemptAt;

	public MarketService(DeckState state, ConfigStore store) {
		this.state = state;
		this.store = store;
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	public void tick() {
		if (!state.apiEnabled || state.apiKey == null || state.apiKey.isBlank()) {
			if (!"NO_DATA".equals(state.marketStatus) && state.lastMarketUpdate == 0) {
				state.marketStatus = "NO_DATA";
				state.marketMessage = "Add a DonutSMP API key (/api in game) or set a manual price.";
			}
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= nextRefreshAt && state.lastMarketUpdate > 0) {
			refresh(false);
		} else if (state.lastMarketUpdate == 0 && now >= nextRefreshAt) {
			refresh(false);
		}
	}

	public void refresh(boolean manual) {
		if (inFlight.get()) {
			return;
		}
		if (state.apiKey == null || state.apiKey.isBlank()) {
			state.marketStatus = "NO_DATA";
			state.marketMessage = "No API key. Run /api in DonutSMP, then paste it in DonutDeck → Settings.";
			return;
		}
		if (!state.apiEnabled) {
			state.marketStatus = "OFF";
			state.marketMessage = "Live market API disabled.";
			return;
		}
		long now = System.currentTimeMillis();
		if (!manual && now < nextRefreshAt) {
			return;
		}
		if (manual && lastAttemptAt > 0 && now - lastAttemptAt < 8_000L) {
			state.marketMessage = "Refresh cooling down for " + ((8_000L - (now - lastAttemptAt)) / 1000 + 1) + "s.";
			return;
		}
		inFlight.set(true);
		lastAttemptAt = now;
		nextRefreshAt = now + Math.max(30, state.refreshSeconds) * 1000L;
		String key = state.apiKey.trim();

		List<String> ids = new ArrayList<>(state.watchlist());
		if (ids.isEmpty()) {
			ids = new ArrayList<>(com.donutdeck.core.SeedMarket.quotes().keySet());
		}
		final List<String> requestedIds = ids;

		List<CompletableFuture<FetchResult>> futures = new ArrayList<>();
		for (String id : requestedIds) {
			futures.add(fetchListings(key, id));
		}
		CompletableFuture<FetchResult> transactions = fetchTransactions(key);

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
				.thenCombine(transactions, (ignored, tx) -> tx)
				.whenComplete((tx, error) -> {
					List<FetchResult> listings = new ArrayList<>();
					for (CompletableFuture<FetchResult> f : futures) {
						try {
							listings.add(f.join());
						} catch (Exception e) {
							listings.add(FetchResult.error(safeMessage(e)));
						}
					}
					FetchResult txResult = tx;
					if (txResult == null) {
						txResult = FetchResult.error(error == null ? "transaction request failed" : safeMessage(error));
					}
					FetchResult finalTx = txResult;
					Minecraft.getInstance().execute(() -> apply(listings, finalTx, requestedIds, manual));
				});
	}

	private CompletableFuture<FetchResult> fetchListings(String key, String itemId) {
		Quote known = state.quote(itemId);
		String search = known != null && known.displayName != null && !known.displayName.isBlank()
				? known.displayName : Quote.prettify(itemId);
		String body = "{\"search\":\"" + escape(search)
				+ "\",\"sort\":\"lowest_price\"}";
		return send(key, BASE + "list/1", body).handle((root, error) -> {
			if (error != null) {
				return FetchResult.error(safeMessage(error));
			}
			return FetchResult.ok(root, itemId);
		});
	}

	private CompletableFuture<FetchResult> fetchTransactions(String key) {
		// Five pages = up to 500 recent sales. More pages would increase API load
		// without materially improving a 24h sample on a busy auction house.
		List<CompletableFuture<JsonObject>> pages = new ArrayList<>();
		for (int page = 1; page <= 5; page++) {
			pages.add(send(key, BASE + "transactions/" + page, null));
		}
		return CompletableFuture.allOf(pages.toArray(new CompletableFuture[0]))
				.handle((ignored, error) -> {
					JsonArray combined = new JsonArray();
					String firstError = null;
					for (CompletableFuture<JsonObject> page : pages) {
						try {
							JsonObject root = page.join();
							JsonArray arr = array(root, "result");
							if (arr != null) {
								for (JsonElement element : arr) {
									combined.add(element);
								}
							}
						} catch (Exception e) {
							if (firstError == null) {
								firstError = safeMessage(e);
							}
						}
					}
					if (combined.size() == 0) {
						return FetchResult.error(firstError == null ? "transaction request failed" : firstError);
					}
					JsonObject root = new JsonObject();
					root.add("result", combined);
					root.addProperty("status", 200);
					return FetchResult.ok(root, null);
				});
	}

	private CompletableFuture<JsonObject> send(String key, String url, String body) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.timeout(TIMEOUT)
				.header("Authorization", "Bearer " + key)
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.header("User-Agent", "DonutDeck/1.0 (client-side economy terminal)");
		if (body == null) {
			builder.GET();
		} else {
			// The official API documents an optional JSON body on this GET.
			builder.method("GET", HttpRequest.BodyPublishers.ofString(body));
		}

		return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
				.thenCompose(response -> {
					int code = response.statusCode();
					String text = response.body() == null ? "" : response.body();
					if (code == 200) {
						try {
							JsonElement parsed = JsonParser.parseString(text);
							if (parsed.isJsonObject()) {
								return CompletableFuture.completedFuture(parsed.getAsJsonObject());
							}
							return CompletableFuture.failedFuture(new IllegalStateException("API returned non-object JSON"));
						} catch (Exception e) {
							return CompletableFuture.failedFuture(new IllegalStateException("Could not parse API JSON"));
						}
					}
					if (code == 401) {
						return CompletableFuture.failedFuture(new IllegalStateException("API key rejected — run /api in game"));
					}
					if (code == 429) {
						return CompletableFuture.failedFuture(new IllegalStateException("API rate limit — backing off"));
					}
					if (code == 400 || code == 405) {
						// Some intermediaries dislike GET bodies; try the plain page.
						HttpRequest fallback = HttpRequest.newBuilder(URI.create(url))
								.timeout(TIMEOUT)
								.header("Authorization", "Bearer " + key)
								.header("Accept", "application/json")
								.header("User-Agent", "DonutDeck/1.0 (client-side economy terminal)")
								.GET()
								.build();
						return http.sendAsync(fallback, HttpResponse.BodyHandlers.ofString())
								.thenCompose(r2 -> {
									if (r2.statusCode() == 200) {
										try {
											JsonElement parsed = JsonParser.parseString(r2.body());
											if (parsed.isJsonObject()) {
												return CompletableFuture.completedFuture(parsed.getAsJsonObject());
											}
										} catch (Exception ignored) {
										}
									}
									return CompletableFuture.failedFuture(
											new IllegalStateException("API HTTP " + r2.statusCode()));
								});
					}
					return CompletableFuture.failedFuture(new IllegalStateException("API HTTP " + code));
				});
	}

	private void apply(List<FetchResult> listings, FetchResult transactions, List<String> requested, boolean manual) {
		try {
			long now = System.currentTimeMillis();
			Map<String, List<Double>> sold = new HashMap<>();
			Map<String, Double> asks = new HashMap<>();
			Map<String, String> names = new HashMap<>();
			List<String> errors = new ArrayList<>();

			for (FetchResult result : listings) {
				if (result.error != null) {
					errors.add(result.error);
					continue;
				}
				if (result.root == null) {
					continue;
				}
				parseListings(result.root, result.requestedId, asks, names);
			}
			boolean txOk = false;
			if (transactions.error == null && transactions.root != null) {
				parseTransactions(transactions.root, sold, names);
				txOk = true;
			} else if (transactions.error != null) {
				errors.add(transactions.error);
			}

			int updated = 0;
			Set<String> candidates = new java.util.LinkedHashSet<>(requested);
			candidates.addAll(asks.keySet());
			candidates.addAll(sold.keySet());
			candidates.addAll(state.quotes.keySet());

			for (String rawId : candidates) {
				String id = Quote.normalizeId(rawId);
				Quote old = state.quotes.get(id);
				double lowAsk = asks.getOrDefault(id, 0.0);
				List<Double> sales = sold.getOrDefault(id, List.of());
				double median = MarketMath.median(sales);
				double price = MarketMath.blend(median, lowAsk);
				if (price <= 0) {
					continue;
				}

				Quote q = old == null ? new Quote() : old;
				q.itemId = id;
				if (names.containsKey(id)) {
					q.displayName = names.get(id);
				} else if (q.displayName == null || q.displayName.isBlank()) {
					q.displayName = Quote.prettify(id);
				}
				q.lowAsk = lowAsk > 0 ? lowAsk : q.lowAsk;
				q.medianSold = median > 0 ? median : q.medianSold;
				q.price = price;
				q.volume = sales.size();
				q.source = "LIVE";
				q.updatedAt = now;
				q.lowConfidence = false;

				List<PricePoint> history = state.priceHistory.computeIfAbsent(id, k -> new ArrayList<>());
				history.add(new PricePoint(now, price));
				while (history.size() > 96) {
					history.remove(0);
				}
				q.changePct = MarketMath.change24h(history, price, now);
				state.quotes.put(id, q);
				updated++;
			}

			state.lastMarketUpdate = now;
			state.apiError = updated == 0;
			if (updated > 0) {
				state.marketStatus = txOk ? "LIVE" : "PARTIAL";
				state.marketMessage = updated + " live quotes · " + requested.size() + " searches"
						+ (errors.isEmpty() ? "" : " · " + shortError(errors.get(0)));
				state.recordActivity("◈", "Market refreshed: " + updated + " live quotes");
			} else {
				state.marketStatus = "ERROR";
				state.marketMessage = errors.isEmpty() ? "No live quotes returned; keeping last data."
						: shortError(errors.get(0));
				state.apiError = true;
			}
			store.save(state);
		} finally {
			inFlight.set(false);
		}
	}

	private void parseListings(JsonObject root, String requestedId, Map<String, Double> asks,
			Map<String, String> names) {
		JsonArray results = array(root, "result");
		if (results == null) {
			return;
		}
		for (JsonElement element : results) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject listing = element.getAsJsonObject();
			JsonObject item = object(listing, "item");
			String id = itemId(item, listing);
			if (id.isEmpty()) {
				id = requestedId;
			}
			id = Quote.normalizeId(id);
			if (requestedId != null && !id.equals(Quote.normalizeId(requestedId))
					&& !requestedId.equalsIgnoreCase(id)) {
				// Search can return related items; keep only the requested item when we can identify it.
				continue;
			}
			int count = Math.max(1, intValue(item, listing, "count", 1));
			double total = doubleValue(listing, "price", 0);
			double unit = doubleValue(listing, "price_per_unit", 0);
			if (unit <= 0 && total > 0) {
				unit = total / count;
			}
			if (unit <= 0 || !Double.isFinite(unit)) {
				continue;
			}
			String display = stringValue(item, "display_name");
			if (display == null || display.isBlank()) {
				display = stringValue(item, "name");
			}
			if (display != null && !display.isBlank()) {
				names.putIfAbsent(id, display);
			}
			double prev = asks.getOrDefault(id, Double.MAX_VALUE);
			asks.put(id, Math.min(prev, unit));
		}
	}

	private void parseTransactions(JsonObject root, Map<String, List<Double>> sold, Map<String, String> names) {
		JsonArray results = array(root, "result");
		if (results == null) {
			return;
		}
		long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
		for (JsonElement element : results) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject tx = element.getAsJsonObject();
			JsonObject item = object(tx, "item");
			String id = Quote.normalizeId(itemId(item, tx));
			if (id.isEmpty() || "minecraft:".equals(id)) {
				continue;
			}
			long time = longValue(tx, "unixMillisDateSold", 0);
			if (time > 0 && time < cutoff) {
				continue;
			}
			int count = Math.max(1, intValue(item, tx, "count", 1));
			double total = doubleValue(tx, "price", 0);
			double unit = doubleValue(tx, "price_per_unit", 0);
			if (unit <= 0 && total > 0) {
				unit = total / count;
			}
			if (unit <= 0 || !Double.isFinite(unit)) {
				continue;
			}
			sold.computeIfAbsent(id, k -> new ArrayList<>()).add(unit);
			String display = stringValue(item, "display_name");
			if (display != null && !display.isBlank()) {
				names.putIfAbsent(id, display);
			}
		}
	}

	private static String itemId(JsonObject item, JsonObject fallback) {
		if (item != null) {
			for (String key : List.of("id", "item_id", "name", "display_name")) {
				String value = stringValue(item, key);
				if (value != null && !value.isBlank()) {
					return value;
				}
			}
		}
		return stringValue(fallback, "item_id");
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (parent != null && parent.has(key) && parent.get(key).isJsonObject()) {
			return parent.getAsJsonObject(key);
		}
		return null;
	}

	private static JsonArray array(JsonObject parent, String key) {
		if (parent != null && parent.has(key) && parent.get(key).isJsonArray()) {
			return parent.getAsJsonArray(key);
		}
		return null;
	}

	private static String stringValue(JsonObject object, String key) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
			return null;
		}
		try {
			return object.get(key).getAsString();
		} catch (Exception e) {
			return null;
		}
	}

	private static double doubleValue(JsonObject object, String key, double fallback) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return object.get(key).getAsDouble();
		} catch (Exception e) {
			try {
				return Double.parseDouble(object.get(key).getAsString().replace(",", ""));
			} catch (Exception ignored) {
				return fallback;
			}
		}
	}

	private static long longValue(JsonObject object, String key, long fallback) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return object.get(key).getAsLong();
		} catch (Exception e) {
			return fallback;
		}
	}

	private static int intValue(JsonObject a, JsonObject b, String key, int fallback) {
		if (a != null && a.has(key)) {
			try {
				return a.get(key).getAsInt();
			} catch (Exception ignored) {
			}
		}
		if (b != null && b.has(key)) {
			try {
				return b.get(key).getAsInt();
			} catch (Exception ignored) {
			}
		}
		return fallback;
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String safeMessage(Throwable error) {
		Throwable cause = error;
		while (cause.getCause() != null && cause.getMessage() == null) {
			cause = cause.getCause();
		}
		String message = cause.getMessage();
		if (message == null || message.isBlank()) {
			message = cause.getClass().getSimpleName();
		}
		return message.length() > 90 ? message.substring(0, 90) : message;
	}

	private static String shortError(String error) {
		if (error == null) {
			return "unknown error";
		}
		String cleaned = error.replaceAll("[\\r\\n]", " ").trim();
		return cleaned.length() > 70 ? cleaned.substring(0, 70) + "…" : cleaned;
	}

	public long nextRefreshAt() {
		return nextRefreshAt;
	}

	public long lastAttemptAt() {
		return lastAttemptAt;
	}

	public boolean isRefreshing() {
		return inFlight.get();
	}

	private static final class FetchResult {
		final JsonObject root;
		final String requestedId;
		final String error;

		private FetchResult(JsonObject root, String requestedId, String error) {
			this.root = root;
			this.requestedId = requestedId;
			this.error = error;
		}

		static FetchResult ok(JsonObject root, String requestedId) {
			return new FetchResult(root, requestedId, null);
		}

		static FetchResult error(String message) {
			return new FetchResult(null, null, message);
		}
	}
}
