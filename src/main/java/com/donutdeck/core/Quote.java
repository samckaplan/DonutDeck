package com.donutdeck.core;

import java.util.Locale;

/** A market quote for one item. Mutable so Gson can persist it. */
public final class Quote {
	public String itemId = "";
	public String displayName = "";
	/** Best usable unit value (blended live value, or a seed estimate). */
	public double price;
	public double lowAsk;
	public double highBid;
	public double medianSold;
	public long volume;
	public double changePct;
	public String source = "SEED";
	public long updatedAt;
	public boolean lowConfidence;

	public Quote() {
	}

	public static Quote seed(String id, String name, double price, boolean lowConfidence) {
		Quote q = new Quote();
		q.itemId = id;
		q.displayName = name;
		q.price = price;
		q.lowAsk = price;
		q.medianSold = price;
		q.source = "SEED";
		q.lowConfidence = lowConfidence;
		q.updatedAt = 0L;
		return q;
	}

	public double bestPrice() {
		if (price > 0) {
			return price;
		}
		if (medianSold > 0) {
			return medianSold;
		}
		return Math.max(0, lowAsk);
	}

	public boolean isLive() {
		return "LIVE".equals(source) && updatedAt > 0;
	}

	public boolean isManual() {
		return "MANUAL".equals(source) && updatedAt > 0;
	}

	public boolean isSample() {
		return source != null && (source.startsWith("SAMPLE") || source.startsWith("SEED"));
	}

	/** Only live API data and prices the player entered themselves may drive money decisions. */
	public boolean isTrusted() {
		return isLive() || isManual();
	}

	public boolean hasPrice() {
		return Math.max(price, Math.max(medianSold, lowAsk)) > 0;
	}

	/** Valuation price, or 0 when this is only an illustrative sample. */
	public double valuationPrice() {
		return isTrusted() ? bestPrice() : 0;
	}

	public String ageLabel(long now) {
		if (updatedAt <= 0) {
			return "SEED".equals(source) ? "seed" : "unknown";
		}
		long seconds = Math.max(0, (now - updatedAt) / 1000L);
		if (seconds < 60) {
			return seconds + "s";
		}
		if (seconds < 3600) {
			return (seconds / 60) + "m";
		}
		return (seconds / 3600) + "h";
	}

	public String shortName() {
		if (displayName != null && !displayName.isBlank()) {
			return displayName;
		}
		return prettify(itemId);
	}

	public static String prettify(String id) {
		if (id == null || id.isBlank()) {
			return "Unknown";
		}
		String s = id;
		int colon = s.indexOf(':');
		if (colon >= 0) {
			s = s.substring(colon + 1);
		}
		s = s.replace('_', ' ').trim();
		if (s.isEmpty()) {
			return id;
		}
		StringBuilder out = new StringBuilder(s.length());
		boolean cap = true;
		for (char c : s.toCharArray()) {
			if (cap) {
				out.append(Character.toUpperCase(c));
				cap = false;
			} else {
				out.append(c);
			}
			if (c == ' ') {
				cap = true;
			}
		}
		return out.toString();
	}

	public static String normalizeId(String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		if (s.isEmpty()) {
			return "";
		}
		if (!s.contains(":")) {
			s = "minecraft:" + s;
		}
		return s;
	}
}
