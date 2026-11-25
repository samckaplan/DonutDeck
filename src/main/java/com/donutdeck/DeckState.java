package com.donutdeck;

import com.donutdeck.core.ActivityEvent;
import com.donutdeck.core.Allocation;
import com.donutdeck.core.BotProfile;
import com.donutdeck.core.BroadcastEvent;
import com.donutdeck.core.BroadcastType;
import com.donutdeck.core.IntentStatus;
import com.donutdeck.core.PendingRequest;
import com.donutdeck.core.Portfolio;
import com.donutdeck.core.PricePoint;
import com.donutdeck.core.Quote;
import com.donutdeck.core.SeedMarket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything DonutDeck knows, serialised as one local JSON file. API keys never
 * leave config/donutdeck/state.json (owner-only permissions).
 */
public final class DeckState {
	public static final int VERSION = 1;

	public int version = VERSION;

	// Connection
	public String apiKey = "";
	public boolean apiEnabled = true;
	public int refreshSeconds = 90;

	// Presentation / privacy
	public boolean showHud = true;
	public boolean privacyMode = true;
	public boolean onboardingSeen = false;
	public boolean showSamplePrices = false;

	// Broadcast
	public boolean broadcastsEnabled = false;
	public boolean fullSend = false;
	public boolean allowPublicBroadcast = false;
	public boolean autoBroadcastTrades = true;
	public boolean autoBroadcastInvest = false;
	public String lastWebhookStatus = "Webhook not tested yet.";
	public String broadcastChannel = "LOCAL";
	public String webhookUrl = "";
	public int broadcastCooldownSeconds = 20;
	public int broadcastMaxPerHour = 12;
	public String templateTrade = "Filled {side} {qty}x {item} @ {price} ({total}) with {counterparty}";
	public String templateInvest = "AutoInvest plan: {buys} buys / {sells} sells, {tickets} tickets, {total}. Reserve {reserve}.";
	public String templateStatus = "{message}";

	// Commands used for normal player actions
	public String tradeCommand = "trade";
	public String partyCommand = "p";
	public double buyerTaxPct = 2.0;

	// Auto-invest advisor (never executes automatically)
	public boolean autoInvestAdvisory = true;
	public int autoInvestMinutes = 60;
	public double reserveCash = 50_000_000;
	public double maxPerOrder = 100_000_000;
	public double minTicket = 1_000_000;
	public double rebalanceBandPct = 2.0;
	public boolean allowSells = true;
	public int maxTickets = 8;

	// Data
	public Portfolio portfolio = new Portfolio();
	public List<Allocation> allocations = new ArrayList<>();
	public List<BotProfile> bots = new ArrayList<>();
	public List<com.donutdeck.core.TradeIntent> intents = new ArrayList<>();
	public Map<String, Quote> quotes = new LinkedHashMap<>();
	public Map<String, List<PricePoint>> priceHistory = new LinkedHashMap<>();
	public List<BroadcastEvent> broadcastLog = new ArrayList<>();
	public List<ActivityEvent> activity = new ArrayList<>();
	public List<PendingRequest> pendingRequests = new ArrayList<>();

	public long lastMarketUpdate;
	public String marketStatus = "NO_DATA";
	public String marketMessage = "No live prices yet. Add a DonutSMP API key (/api) or set a manual price.";
	public boolean apiError;

	public long lastInvestRun;
	public long lastMilestone;
	public String lastMilestoneLabel = "";
	public long lastBroadcastAt;
	public List<Long> broadcastTimes = new ArrayList<>();

	public void ensureDefaults() {
		if (version <= 0) {
			version = VERSION;
		}
		if (portfolio == null) {
			portfolio = new Portfolio();
		}
		if (portfolio.holdings == null) {
			portfolio.holdings = new ArrayList<>();
		}
		if (allocations == null) {
			allocations = new ArrayList<>();
		}
		if (allocations.isEmpty()) {
			allocations = Allocation.defaults();
		}
		if (bots == null) {
			bots = new ArrayList<>();
		}
		if (intents == null) {
			intents = new ArrayList<>();
		}
		if (quotes == null) {
			quotes = new LinkedHashMap<>();
		}
		if (priceHistory == null) {
			priceHistory = new LinkedHashMap<>();
		}
		if (broadcastLog == null) {
			broadcastLog = new ArrayList<>();
		}
		if (activity == null) {
			activity = new ArrayList<>();
		}
		if (pendingRequests == null) {
			pendingRequests = new ArrayList<>();
		}
		if (broadcastTimes == null) {
			broadcastTimes = new ArrayList<>();
		}
		if (refreshSeconds < 30) {
			refreshSeconds = 30;
		}
		if (autoInvestMinutes < 5) {
			autoInvestMinutes = 5;
		}
		if (broadcastCooldownSeconds < 10) {
			broadcastCooldownSeconds = 10;
		}
		if (broadcastMaxPerHour < 1) {
			broadcastMaxPerHour = 1;
		}
		if (broadcastMaxPerHour > 30) {
			broadcastMaxPerHour = 30;
		}
		if (maxTickets < 1) {
			maxTickets = 1;
		}
		if (maxTickets > 16) {
			maxTickets = 16;
		}
		if (reserveCash < 0) {
			reserveCash = 0;
		}
		if (maxPerOrder < 0) {
			maxPerOrder = 0;
		}
		if (minTicket < 0) {
			minTicket = 0;
		}
		if (rebalanceBandPct < 0) {
			rebalanceBandPct = 0;
		}
		if (buyerTaxPct < 0) {
			buyerTaxPct = 0;
		}
		if (buyerTaxPct > 25) {
			buyerTaxPct = 25;
		}
		if (broadcastChannel == null || broadcastChannel.isBlank()) {
			broadcastChannel = "LOCAL";
		}
		if (webhookUrl == null) {
			webhookUrl = "";
		}
		if (lastWebhookStatus == null || lastWebhookStatus.isBlank()) {
			lastWebhookStatus = "Webhook not tested yet.";
		}
		if (tradeCommand == null || tradeCommand.isBlank()) {
			tradeCommand = "trade";
		}
		if (partyCommand == null || partyCommand.isBlank()) {
			partyCommand = "p";
		}
		if (marketStatus == null || marketStatus.isBlank()) {
			marketStatus = "NO_DATA";
		}
		if (marketMessage == null) {
			marketMessage = "";
		}

		// Normalise quote keys and fill any missing curated fallbacks.
		Map<String, Quote> normalised = new LinkedHashMap<>();
		for (Map.Entry<String, Quote> e : quotes.entrySet()) {
			if (e.getValue() == null) {
				continue;
			}
			String id = Quote.normalizeId(e.getKey());
			Quote q = e.getValue();
			q.itemId = id;
			if (q.displayName == null || q.displayName.isBlank()) {
				q.displayName = Quote.prettify(id);
			}
			if (q.price <= 0 && q.medianSold <= 0) {
				q.price = q.lowAsk;
			}
			if (q.isSample()) {
				// Never let placeholder numbers look like usable market data.
				q.price = 0;
				q.lowAsk = 0;
				q.medianSold = 0;
				q.volume = 0;
				q.changePct = 0;
				q.updatedAt = 0;
				q.lowConfidence = true;
			}
			normalised.put(id, q);
		}
		for (Map.Entry<String, Quote> e : SeedMarket.quotes().entrySet()) {
			normalised.putIfAbsent(e.getKey(), e.getValue());
		}
		quotes = normalised;

		// Normalise holdings / allocations / bots.
		for (var h : portfolio.holdings) {
			h.itemId = Quote.normalizeId(h.itemId);
		}
		for (Allocation a : allocations) {
			a.itemId = Quote.normalizeId(a.itemId);
			a.targetPct = Math.max(0, a.targetPct);
		}
		List<BotProfile> cleanBots = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (BotProfile b : bots) {
			if (b == null || b.name == null) {
				continue;
			}
			b.name = b.name.trim();
			if (b.name.isEmpty() || !seen.add(b.name.toLowerCase())) {
				continue;
			}
			cleanBots.add(b);
		}
		bots = cleanBots;

		// Cap histories/logs.
		for (Map.Entry<String, List<PricePoint>> e : priceHistory.entrySet()) {
			List<PricePoint> points = e.getValue();
			if (points == null) {
				continue;
			}
			if (points.size() > 96) {
				e.setValue(new ArrayList<>(points.subList(points.size() - 96, points.size())));
			}
		}
		while (broadcastLog.size() > 60) {
			broadcastLog.remove(broadcastLog.size() - 1);
		}
		while (activity.size() > 60) {
			activity.remove(activity.size() - 1);
		}
		while (pendingRequests.size() > 8) {
			pendingRequests.remove(pendingRequests.size() - 1);
		}
		while (intents.size() > 100) {
			intents.remove(intents.size() - 1);
		}
	}

	public Quote quote(String itemId) {
		return quotes.get(Quote.normalizeId(itemId));
	}

	public int trustedQuoteCount() {
		int count = 0;
		for (Quote q : quotes.values()) {
			if (q != null && q.isTrusted() && q.hasPrice()) {
				count++;
			}
		}
		return count;
	}

	public boolean hasTrustedPrices() {
		return trustedQuoteCount() > 0;
	}

	/** Quotes the user should see in the Market tab. Samples are opt-in only. */
	public List<Quote> visibleQuotes() {
		List<Quote> out = new ArrayList<>();
		for (Quote q : quotes.values()) {
			if (q == null) {
				continue;
			}
			if (!q.isTrusted() && !q.hasPrice() && !showSamplePrices) {
				continue;
			}
			if (q.isSample() && !showSamplePrices) {
				continue;
			}
			out.add(q);
		}
		return out;
	}

	public double netWorth() {
		return portfolio.netWorth(quotes);
	}

	public double investableBase() {
		return Math.max(0, netWorth() - Math.max(0, reserveCash));
	}

	public Set<String> watchlist() {
		LinkedHashSet<String> ids = new LinkedHashSet<>();
		for (Allocation a : allocations) {
			ids.add(Quote.normalizeId(a.itemId));
		}
		for (String id : quotes.keySet()) {
			ids.add(Quote.normalizeId(id));
		}
		int cap = 18;
		if (ids.size() > cap) {
			return new LinkedHashSet<>(new ArrayList<>(ids).subList(0, cap));
		}
		return ids;
	}

	public BotProfile bot(String name) {
		if (name == null) {
			return null;
		}
		for (BotProfile b : bots) {
			if (b.name.equalsIgnoreCase(name)) {
				return b;
			}
		}
		return null;
	}

	public void recordActivity(String icon, String message) {
		activity.add(0, new ActivityEvent(icon, message, System.currentTimeMillis()));
		while (activity.size() > 60) {
			activity.remove(activity.size() - 1);
		}
	}

	public void recordBroadcast(BroadcastEvent event) {
		broadcastLog.add(0, event);
		while (broadcastLog.size() > 60) {
			broadcastLog.remove(broadcastLog.size() - 1);
		}
	}

	public void addPendingRequest(String player, String message) {
		long now = System.currentTimeMillis();
		for (PendingRequest p : pendingRequests) {
			if (p.player.equalsIgnoreCase(player) && now - p.time < 60_000) {
				p.message = message;
				p.time = now;
				return;
			}
		}
		pendingRequests.add(0, new PendingRequest(player, message, now));
		while (pendingRequests.size() > 8) {
			pendingRequests.remove(pendingRequests.size() - 1);
		}
	}

	public void clearPending(String player) {
		pendingRequests.removeIf(p -> p.player.equalsIgnoreCase(player));
	}

	public int openIntentCount() {
		int count = 0;
		for (com.donutdeck.core.TradeIntent i : intents) {
			if (i != null && i.isOpen()) {
				count++;
			}
		}
		return count;
	}

	public String maskedKey() {
		if (apiKey == null || apiKey.isBlank()) {
			return "not set";
		}
		String k = apiKey.trim();
		if (k.length() <= 8) {
			return "••••" + k.substring(k.length() - 2);
		}
		return k.substring(0, 4) + "••••••••" + k.substring(k.length() - 4);
	}

	public long nextMilestone() {
		long[] milestones = {1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L, 10_000_000_000L,
				100_000_000_000L, 1_000_000_000_000L, 10_000_000_000_000L};
		double nw = netWorth();
		for (long m : milestones) {
			if (nw < m) {
				return m;
			}
		}
		return 0;
	}

	public int broadcastsLastHour() {
		long cutoff = System.currentTimeMillis() - 60L * 60L * 1000L;
		broadcastTimes.removeIf(t -> t < cutoff);
		return broadcastTimes.size();
	}

	public void expireIntents() {
		long now = System.currentTimeMillis();
		for (com.donutdeck.core.TradeIntent intent : intents) {
			if (intent != null && intent.isOpen() && now - intent.createdAt > 24L * 60L * 60L * 1000L) {
				intent.status = IntentStatus.EXPIRED;
				intent.updatedAt = now;
			}
		}
	}

	public void addBroadcastTime(long time) {
		broadcastTimes.add(time);
		while (broadcastTimes.size() > 120) {
			broadcastTimes.remove(0);
		}
	}

	public static BroadcastEvent event(BroadcastType type, String message, String channel, boolean delivered) {
		return new BroadcastEvent(type, message, channel, delivered, System.currentTimeMillis());
	}
}
