package com.donutdeck.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small, dependency-free statistics helpers used by the price engine. */
public final class MarketMath {
	private MarketMath() {
	}

	public static double median(List<Double> values) {
		if (values == null || values.isEmpty()) {
			return 0;
		}
		List<Double> copy = new ArrayList<>(values);
		Collections.sort(copy);
		int n = copy.size();
		if (n % 2 == 1) {
			return copy.get(n / 2);
		}
		return (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2.0;
	}

	public static double blend(double sold, double ask) {
		if (sold > 0 && ask > 0) {
			return sold * 0.7 + ask * 0.3;
		}
		return sold > 0 ? sold : ask;
	}

	public static double changePct(double previous, double current) {
		if (previous <= 0 || current <= 0) {
			return 0;
		}
		return (current - previous) / previous * 100.0;
	}

	/** 24h change from a price history, falling back to the previous scan. */
	public static double change24h(List<PricePoint> history, double current, long now) {
		if (history == null || history.isEmpty() || current <= 0) {
			return 0;
		}
		long cutoff = now - 24L * 60L * 60L * 1000L;
		PricePoint best = null;
		for (PricePoint p : history) {
			if (p.time <= cutoff) {
				best = p;
			}
		}
		if (best == null) {
			best = history.get(0);
		}
		return changePct(best.price, current);
	}

	public static String compactChange(double pct) {
		return Money.pct(pct);
	}
}
