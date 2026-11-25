package com.donutdeck;

import com.donutdeck.core.BroadcastEvent;
import com.donutdeck.core.BroadcastType;
import com.donutdeck.core.Holding;
import com.donutdeck.core.InvestTicket;
import com.donutdeck.core.Money;
import com.donutdeck.core.Quote;
import com.donutdeck.core.TradeIntent;
import com.donutdeck.core.TradeSide;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Broadcast Center. It can write to the local log, party chat, public chat or a
 * Discord webhook. Every external destination is opt-in, rate-limited and
 * blocked while privacy mode is on. No background spam and no chat automation.
 */
public final class BroadcastService {
	private static final String LOCAL = "LOCAL";
	private static final String PARTY = "PARTY";
	private static final String PUBLIC = "PUBLIC";
	private static final String DISCORD = "DISCORD";
	private static final int CHAT_LIMIT = 240;

	private final DeckState state;
	private final Runnable save;
	private final Consumer<String> notifier;
	private final HttpClient http;

	public BroadcastService(DeckState state, Runnable save, Consumer<String> notifier) {
		this.state = state;
		this.save = save;
		this.notifier = notifier;
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	public boolean broadcast(BroadcastType type, String body) {
		if (body == null || body.isBlank()) {
			notifier.accept("§cNothing to broadcast.");
			return false;
		}
		String channel = normalizeChannel(state.broadcastChannel);
		boolean local = LOCAL.equals(channel);

		if (!local && state.privacyMode) {
			blocked(type, channel, "Privacy mode blocks external broadcasts.");
			return false;
		}
		if (!local && !state.broadcastsEnabled) {
			blocked(type, channel, "Enable broadcasts in the Broadcast Center first.");
			return false;
		}
		if (PUBLIC.equals(channel) && !state.allowPublicBroadcast) {
			blocked(type, channel, "Public chat is locked. Enable it explicitly in Broadcast Center.");
			return false;
		}
		if (DISCORD.equals(channel)) {
			String validation = validateWebhook(state.webhookUrl);
			if (validation != null) {
				blocked(type, channel, validation);
				return false;
			}
		}

		long now = System.currentTimeMillis();
		if (!local) {
			long since = now - state.lastBroadcastAt;
			long cooldown = Math.max(10, state.broadcastCooldownSeconds) * 1000L;
			if (state.lastBroadcastAt > 0 && since < cooldown) {
				long wait = (cooldown - since + 999) / 1000;
				blocked(type, channel, "Cooldown: wait " + wait + "s.");
				return false;
			}
			if (state.broadcastsLastHour() >= Math.max(1, state.broadcastMaxPerHour)) {
				blocked(type, channel, "Hourly broadcast limit reached.");
				return false;
			}
		}

		String message = trim(body, DISCORD.equals(channel) ? 1800 : CHAT_LIMIT);
		BroadcastEvent event = DeckState.event(type, message, channel, local);
		state.recordBroadcast(event);

		if (local) {
			state.recordActivity("◉", "Local broadcast: " + shortText(message, 70));
			save.run();
			notifier.accept("§aLocal broadcast recorded. §7It was not sent to any chat.");
			return true;
		}

		if (PARTY.equals(channel) || PUBLIC.equals(channel)) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null || player.connection == null) {
				event.delivered = false;
				state.recordActivity("!", "Broadcast blocked: not in a world.");
				save.run();
				notifier.accept("§cJoin DonutSMP before broadcasting to chat.");
				return false;
			}
			try {
				if (PARTY.equals(channel)) {
					player.connection.sendCommand(stripSlash(state.partyCommand) + " " + message);
				} else {
					player.connection.sendChat(message);
				}
				event.delivered = true;
				state.lastBroadcastAt = now;
				state.addBroadcastTime(now);
				state.recordActivity("◉", "Broadcast to " + channel + ": " + shortText(message, 60));
				save.run();
				notifier.accept("§aBroadcast sent to " + channel.toLowerCase(Locale.ROOT) + ".");
				return true;
			} catch (Exception e) {
				event.delivered = false;
				state.recordActivity("!", "Broadcast failed: " + safe(e));
				save.run();
				notifier.accept("§cBroadcast failed: " + safe(e));
				return false;
			}
		}

		if (DISCORD.equals(channel)) {
			sendDiscordAsync(message, event);
			state.lastBroadcastAt = now;
			state.addBroadcastTime(now);
			state.recordActivity("◉", "Discord broadcast queued: " + shortText(message, 60));
			save.run();
			notifier.accept("§aDiscord broadcast queued. §7Check the broadcast log for delivery.");
			return true;
		}

		blocked(type, channel, "Unknown broadcast channel.");
		return false;
	}

	public boolean broadcastStatus() {
		return broadcast(BroadcastType.STATUS, statusMessage());
	}

	public boolean broadcastPortfolio() {
		return broadcast(BroadcastType.MILESTONE, portfolioMessage());
	}

	public void onTradeFilled(TradeIntent intent, double realized) {
		if (!state.broadcastsEnabled || !state.autoBroadcastTrades) {
			return;
		}
		String message = state.templateTrade
				.replace("{side}", intent.side.name())
				.replace("{qty}", String.valueOf(intent.qty))
				.replace("{item}", intent.displayName)
				.replace("{price}", Money.format(intent.unitPrice))
				.replace("{total}", Money.format(intent.total()))
				.replace("{counterparty}", intent.counterparty)
				.replace("{pnl}", intent.side == TradeSide.SELL ? Money.format(realized) : "—");
		broadcast(BroadcastType.TRADE_FILLED, message);
	}

	public void onInvestPlan(List<InvestTicket> tickets) {
		if (tickets == null || tickets.isEmpty()) {
			return;
		}
		if (!state.broadcastsEnabled || !state.autoBroadcastInvest) {
			return;
		}
		int buys = 0;
		int sells = 0;
		double total = 0;
		for (InvestTicket ticket : tickets) {
			if (ticket == null) {
				continue;
			}
			if (ticket.side == TradeSide.BUY) {
				buys++;
			} else {
				sells++;
			}
			total += ticket.amount;
		}
		String message = state.templateInvest
				.replace("{tickets}", String.valueOf(tickets.size()))
				.replace("{buys}", String.valueOf(buys))
				.replace("{sells}", String.valueOf(sells))
				.replace("{total}", Money.format(total))
				.replace("{reserve}", Money.format(state.reserveCash));
		broadcast(BroadcastType.INVEST_PLAN, message);
	}

	public String statusMessage() {
		String pnl = Money.format(state.portfolio.unrealizedPnl(state.quotes));
		String status = state.marketStatus == null ? "NO_DATA" : state.marketStatus;
		return "DonutDeck | " + status + " | NW " + Money.format(state.netWorth())
				+ " | cash " + Money.format(state.portfolio.cash)
				+ " | unrealized " + pnl
				+ " | " + state.portfolio.positionCount() + " positions"
				+ " | manual-confirm trading";
	}

	public String portfolioMessage() {
		StringBuilder out = new StringBuilder();
		out.append("DonutDeck | NW ").append(Money.format(state.netWorth()))
				.append(" | cash ").append(Money.format(state.portfolio.cash))
				.append(" | invested ").append(Money.format(state.portfolio.investedValue(state.quotes)))
				.append(" | P/L ").append(Money.format(state.portfolio.unrealizedPnl(state.quotes)))
				.append(" | ").append(state.portfolio.positionCount()).append(" positions");
		List<Holding> holdings = new ArrayList<>(state.portfolio.holdings);
		holdings.sort(Comparator.comparingDouble((Holding h) -> {
			Quote q = state.quote(h.itemId);
			double price = q != null && q.isTrusted() ? q.bestPrice() : h.avgCost;
			return -(h.qty * price);
		}));
		if (!holdings.isEmpty()) {
			out.append(" | ");
			int limit = state.fullSend ? Math.min(holdings.size(), 6) : Math.min(holdings.size(), 3);
			for (int i = 0; i < limit; i++) {
				Holding h = holdings.get(i);
				Quote q = state.quote(h.itemId);
				String name = q == null ? Quote.prettify(h.itemId) : q.shortName();
				if (i > 0) {
					out.append(", ");
				}
				out.append(name).append(' ').append(Money.qty(h.qty));
			}
			if (holdings.size() > limit) {
				out.append(", +").append(holdings.size() - limit).append(" more");
			}
		}
		return trim(out.toString(), 240);
	}

	public String testDiscord() {
		String validation = validateWebhook(state.webhookUrl);
		if (validation != null) {
			state.lastWebhookStatus = validation;
			save.run();
			return validation;
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("content", "DonutDeck webhook test (manual). No automation is running.");
		payload.addProperty("username", "DonutDeck");
		JsonObject mentions = new JsonObject();
		mentions.add("parse", new com.google.gson.JsonArray());
		payload.add("allowed_mentions", mentions);
		sendWebhook(payload.toString(), result -> {
			state.lastWebhookStatus = result;
			save.run();
		});
		state.lastWebhookStatus = "Test queued…";
		save.run();
		return "Test queued.";
	}

	private void sendDiscordAsync(String message, BroadcastEvent event) {
		JsonObject payload = new JsonObject();
		payload.addProperty("content", message);
		payload.addProperty("username", "DonutDeck");
		JsonObject mentions = new JsonObject();
		mentions.add("parse", new com.google.gson.JsonArray());
		payload.add("allowed_mentions", mentions);
		sendWebhook(payload.toString(), result -> {
			boolean delivered = result.startsWith("Delivered");
			Minecraft.getInstance().execute(() -> {
				event.delivered = delivered;
				state.lastWebhookStatus = result;
				state.recordActivity(delivered ? "◉" : "!", "Discord: " + result);
				save.run();
			});
		});
	}

	private void sendWebhook(String json, Consumer<String> callback) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(state.webhookUrl.trim()))
					.timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/json")
					.header("User-Agent", "DonutDeck/1.0")
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();
			http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
					.whenComplete((response, error) -> {
						if (error != null) {
							callback.accept("Failed: " + safe(error));
							return;
						}
						int code = response.statusCode();
						if (code >= 200 && code < 300) {
							callback.accept("Delivered (HTTP " + code + ")");
							return;
						}
						String body = response.body() == null ? "" : response.body();
						String reason = "";
						try {
							JsonObject parsed = JsonParser.parseString(body).getAsJsonObject();
							if (parsed.has("message")) {
								reason = parsed.get("message").getAsString();
							}
						} catch (Exception ignored) {
						}
						callback.accept("HTTP " + code + (reason.isBlank() ? "" : ": " + shortText(reason, 60)));
					});
		} catch (Exception e) {
			callback.accept("Failed: " + safe(e));
		}
	}

	/** Only Discord's official webhook hosts are accepted, never arbitrary URLs. */
	public static String validateWebhook(String url) {
		if (url == null || url.isBlank()) {
			return "Paste a Discord webhook URL first.";
		}
		try {
			URI uri = URI.create(url.trim());
			if (!"https".equalsIgnoreCase(uri.getScheme())) {
				return "Webhook must use https://";
			}
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (!(host.equals("discord.com") || host.equals("discordapp.com")
					|| host.equals("ptb.discord.com") || host.equals("canary.discord.com"))) {
				return "Only official Discord webhook hosts are allowed.";
			}
			String path = uri.getPath() == null ? "" : uri.getPath();
			if (!path.startsWith("/api/webhooks/")) {
				return "That is not a Discord webhook path.";
			}
			if (uri.getUserInfo() != null) {
				return "Webhook URL must not contain credentials.";
			}
			return null;
		} catch (Exception e) {
			return "Invalid webhook URL.";
		}
	}

	public static boolean isValidWebhook(String url) {
		return validateWebhook(url) == null;
	}

	public static String normalizeChannel(String channel) {
		if (channel == null) {
			return LOCAL;
		}
		String upper = channel.trim().toUpperCase(Locale.ROOT);
		return switch (upper) {
			case "PARTY", "PUBLIC", "DISCORD" -> upper;
			default -> LOCAL;
		};
	}

	public static String channelLabel(String channel) {
		return switch (normalizeChannel(channel)) {
			case PARTY -> "Party chat";
			case PUBLIC -> "Public chat";
			case DISCORD -> "Discord webhook";
			default -> "Local log only";
		};
	}

	private void blocked(BroadcastType type, String channel, String reason) {
		state.recordBroadcast(DeckState.event(type, reason, channel, false));
		state.recordActivity("!", "Broadcast blocked: " + reason);
		save.run();
		notifier.accept("§e" + reason);
	}

	private static String stripSlash(String command) {
		if (command == null || command.isBlank()) {
			return "p";
		}
		String s = command.trim();
		while (s.startsWith("/")) {
			s = s.substring(1);
		}
		return s;
	}

	private static String trim(String text, int max) {
		String clean = text.replace('\n', ' ').replace('\r', ' ').trim();
		if (clean.length() <= max) {
			return clean;
		}
		return clean.substring(0, Math.max(0, max - 1)) + "…";
	}

	private static String shortText(String text, int max) {
		return trim(text, max);
	}

	private static String safe(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			message = error.getClass().getSimpleName();
		}
		return message.length() > 80 ? message.substring(0, 80) : message;
	}
}
