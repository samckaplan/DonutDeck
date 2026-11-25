package com.donutdeck.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes rebalance tickets from target weights. It never sends commands and
 * never moves items: every ticket is a proposal the player must confirm.
 */
public final class InvestEngine {
	private InvestEngine() {
	}

	public static final class Settings {
		public double reserveCash = 50_000_000;
		public double maxPerOrder = 100_000_000;
		public double minTicket = 1_000_000;
		public double rebalanceBandPct = 2.0;
		public boolean allowSells = true;
		public int maxTickets = 8;

		public Settings() {
		}
	}

	public static List<InvestTicket> plan(Portfolio portfolio, Map<String, Quote> quotes,
			List<Allocation> allocations, Settings settings) {
		List<InvestTicket> tickets = new ArrayList<>();
		if (portfolio == null || quotes == null || allocations == null || settings == null) {
			return tickets;
		}

		double netWorth = portfolio.netWorth(quotes);
		double base = Math.max(0, netWorth - Math.max(0, settings.reserveCash));
		if (base <= 0) {
			return tickets;
		}
		double cashAvailable = Math.max(0, portfolio.cash - Math.max(0, settings.reserveCash));
		double band = Math.max(settings.minTicket, base * Math.max(0, settings.rebalanceBandPct) / 100.0);

		for (Allocation allocation : allocations) {
			if (allocation == null || allocation.targetPct <= 0) {
				continue;
			}
			String id = Quote.normalizeId(allocation.itemId);
			Quote quote = quotes.get(id);
			if (quote == null || !quote.isTrusted()) {
				continue;
			}
			double price = quote.bestPrice();
			if (price <= 0) {
				continue;
			}

			double currentPct = portfolio.pctOf(id, quotes, base);
			double desiredValue = base * allocation.targetPct / 100.0;
			double currentValue = currentPct / 100.0 * base;
			double gap = desiredValue - currentValue;
			String name = quote.shortName();

			if (gap > band) {
				double amount = Math.min(Math.min(gap, settings.maxPerOrder), cashAvailable);
				int qty = (int) Math.floor(amount / price);
				if (qty <= 0) {
					continue;
				}
				double total = qty * price;
				if (total < settings.minTicket) {
					continue;
				}
				String reason = String.format(Locale.ROOT, "Underweight: %.1f%% vs %.1f%% target", currentPct,
						allocation.targetPct);
				tickets.add(new InvestTicket(TradeSide.BUY, id, name, qty, price, currentPct,
						allocation.targetPct, reason, quote.lowConfidence));
				cashAvailable -= total;
			} else if (gap < -band && settings.allowSells) {
				double held = portfolio.qty(id);
				int qty = (int) Math.min(held, Math.floor(Math.min(-gap, settings.maxPerOrder) / price));
				if (qty <= 0) {
					continue;
				}
				double total = qty * price;
				if (total < settings.minTicket) {
					continue;
				}
				String reason = String.format(Locale.ROOT, "Overweight: %.1f%% vs %.1f%% target", currentPct,
						allocation.targetPct);
				tickets.add(new InvestTicket(TradeSide.SELL, id, name, qty, price, currentPct,
						allocation.targetPct, reason, quote.lowConfidence));
			}
		}

		tickets.sort(Comparator.comparingDouble((InvestTicket t) -> Math.abs(t.amount)).reversed());
		if (tickets.size() > Math.max(1, settings.maxTickets)) {
			return new ArrayList<>(tickets.subList(0, Math.max(1, settings.maxTickets)));
		}
		return tickets;
	}

	public static double investableBase(Portfolio portfolio, Map<String, Quote> quotes, double reserveCash) {
		return Math.max(0, portfolio.netWorth(quotes) - Math.max(0, reserveCash));
	}
}
