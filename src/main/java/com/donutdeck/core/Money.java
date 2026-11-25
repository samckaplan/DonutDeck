package com.donutdeck.core;

import java.util.Locale;

/**
 * DonutSMP money formatting/parsing. The server uses coins ("money"), usually
 * displayed with a dollar sign. Players type shorthand such as 1.5b or 250m.
 */
public final class Money {
	private Money() {
	}

	/** Parses 1.5b, $250m, 3,400,000, 62k, etc. Returns NaN when unparseable. */
	public static double parse(String input) {
		if (input == null) {
			return Double.NaN;
		}
		String s = input.trim().toLowerCase(Locale.ROOT)
				.replace("$", "")
				.replace(",", "")
				.replace("_", "")
				.replace(" ", "");
		if (s.isEmpty()) {
			return Double.NaN;
		}

		double multiplier = 1.0;
		char last = s.charAt(s.length() - 1);
		switch (last) {
			case 'k' -> {
				multiplier = 1_000.0;
				s = s.substring(0, s.length() - 1);
			}
			case 'm' -> {
				multiplier = 1_000_000.0;
				s = s.substring(0, s.length() - 1);
			}
			case 'b' -> {
				multiplier = 1_000_000_000.0;
				s = s.substring(0, s.length() - 1);
			}
			case 't' -> {
				multiplier = 1_000_000_000_000.0;
				s = s.substring(0, s.length() - 1);
			}
			default -> {
			}
		}

		try {
			double value = Double.parseDouble(s);
			if (!Double.isFinite(value)) {
				return Double.NaN;
			}
			return value * multiplier;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** Compact display: $1.24B, $350M, $12.5k, $42. */
	public static String format(double value) {
		if (!Double.isFinite(value)) {
			return "—";
		}
		double abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000_000.0) {
			return sign + "$" + trim(abs / 1_000_000_000_000.0) + "T";
		}
		if (abs >= 1_000_000_000.0) {
			return sign + "$" + trim(abs / 1_000_000_000.0) + "B";
		}
		if (abs >= 1_000_000.0) {
			return sign + "$" + trim(abs / 1_000_000.0) + "M";
		}
		if (abs >= 1_000.0) {
			return sign + "$" + trim(abs / 1_000.0) + "k";
		}
		if (abs >= 10.0 || abs == 0.0) {
			return sign + "$" + String.format(Locale.ROOT, "%.0f", abs);
		}
		return sign + "$" + trim(abs);
	}

	/** Full precision with separators: $1,234,567. */
	public static String formatFull(double value) {
		if (!Double.isFinite(value)) {
			return "—";
		}
		return (value < 0 ? "-$" : "$") + String.format(Locale.ROOT, "%,.0f", Math.abs(value));
	}

	/** Signed percentage, e.g. +3.2% / -1.0%. */
	public static String pct(double value) {
		if (!Double.isFinite(value)) {
			return "—";
		}
		return String.format(Locale.ROOT, "%+.1f%%", value);
	}

	public static String qty(double value) {
		if (!Double.isFinite(value)) {
			return "—";
		}
		if (Math.abs(value - Math.rint(value)) < 0.0001) {
			return String.format(Locale.ROOT, "%,.0f", value);
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static String trim(double value) {
		String s = String.format(Locale.ROOT, "%.2f", value);
		while (s.endsWith("0")) {
			s = s.substring(0, s.length() - 1);
		}
		if (s.endsWith(".")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}
}
