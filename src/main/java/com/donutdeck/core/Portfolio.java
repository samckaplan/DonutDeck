package com.donutdeck.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cash + item positions. Every mutation takes the unit price so cost basis and
 * realised profit stay honest. No Minecraft classes here: this is unit tested.
 */
public final class Portfolio {
	public double cash;
	public List<Holding> holdings = new ArrayList<>();
	public double realizedPnl;

	public Portfolio() {
	}

	public Holding find(String itemId) {
		String id = Quote.normalizeId(itemId);
		for (Holding h : holdings) {
			if (id.equals(Quote.normalizeId(h.itemId))) {
				return h;
			}
		}
		return null;
	}

	public double qty(String itemId) {
		Holding h = find(itemId);
		return h == null ? 0 : h.qty;
	}

	public void applyBuy(String itemId, double qty, double unitPrice) {
		if (qty <= 0 || unitPrice <= 0) {
			return;
		}
		String id = Quote.normalizeId(itemId);
		cash -= qty * unitPrice;
		Holding h = find(id);
		if (h == null) {
			h = new Holding(id, 0, 0);
			holdings.add(h);
		}
		double totalCost = h.avgCost * h.qty + unitPrice * qty;
		h.qty += qty;
		h.avgCost = h.qty <= 0 ? 0 : totalCost / h.qty;
	}

	/** Returns realised profit/loss for this sale. */
	public double applySell(String itemId, double qty, double unitPrice) {
		if (qty <= 0 || unitPrice <= 0) {
			return 0;
		}
		Holding h = find(itemId);
		if (h == null) {
			cash += qty * unitPrice;
			return 0;
		}
		double sold = Math.min(qty, h.qty);
		double realized = (unitPrice - h.avgCost) * sold;
		cash += sold * unitPrice;
		h.qty -= sold;
		realizedPnl += realized;
		if (h.qty <= 0.000001) {
			holdings.remove(h);
		}
		return realized;
	}

	public double investedValue(Map<String, Quote> quotes) {
		double total = 0;
		for (Holding h : holdings) {
			Quote q = quotes.get(Quote.normalizeId(h.itemId));
			double price = q == null || !q.isTrusted() ? h.avgCost : q.bestPrice();
			total += h.qty * price;
		}
		return total;
	}

	public double netWorth(Map<String, Quote> quotes) {
		return cash + investedValue(quotes);
	}

	public double unrealizedPnl(Map<String, Quote> quotes) {
		double total = 0;
		for (Holding h : holdings) {
			Quote q = quotes.get(Quote.normalizeId(h.itemId));
			double price = q == null || !q.isTrusted() ? h.avgCost : q.bestPrice();
			total += (price - h.avgCost) * h.qty;
		}
		return total;
	}

	public double pctOf(String itemId, Map<String, Quote> quotes, double base) {
		if (base <= 0) {
			return 0;
		}
		Holding h = find(itemId);
		if (h == null) {
			return 0;
		}
		Quote q = quotes.get(Quote.normalizeId(itemId));
		double price = q == null || !q.isTrusted() ? h.avgCost : q.bestPrice();
		return h.qty * price / base * 100.0;
	}

	public int positionCount() {
		return holdings.size();
	}
}
