package com.donutdeck.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only chat parsers. They only look at messages the client already
 * received; they never alter chat or send anything.
 */
public final class ChatParser {
	private ChatParser() {
	}

	private static final String NAME = "[A-Za-z0-9_]{3,16}";
	private static final String AMOUNT = "\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?";

	private static final Pattern BALANCE = Pattern.compile(
			"(?i)^\\s*(?:your\\s+)?(?:balance|bal|money|cash)\\b\\s*(?:is|:|=|»)?\\s*" + AMOUNT);
	private static final Pattern BALANCE_ALT = Pattern.compile(
			"(?i)^\\s*" + AMOUNT + "\\s*(?:coins?)?\\s*(?:is\\s+)?(?:your\\s+)?\\b(?:balance|bal|money|cash)\\b");

	private static final Pattern TRADE_REQUEST_1 = Pattern.compile(
			"(?i)(?:^|\\]\\s*)(" + NAME + ")\\s+(?:has\\s+sent|sent|is\\s+sending)\\s+you\\s+(?:a\\s+)?trade\\s+request");
	private static final Pattern TRADE_REQUEST_2 = Pattern.compile(
			"(?i)(?:trade\\s+request|trade)\\s+(?:from|by)\\s+(" + NAME + ")");
	private static final Pattern TRADE_REQUEST_3 = Pattern.compile(
			"(?i)(?:^|\\]\\s*)(" + NAME + ")\\s+(?:wants|would\\s+like)\\s+to\\s+trade");
	private static final Pattern TRADE_REQUEST_4 = Pattern.compile(
			"(?i)(" + NAME + ")\\s+has\\s+requested\\s+to\\s+trade");

	private static final Pattern PAID = Pattern.compile(
			"(?i)you\\s+(?:paid|sent)\\s+" + AMOUNT + "(?:\\s+to\\s+" + NAME + ")?");
	private static final Pattern RECEIVED = Pattern.compile(
			"(?i)you\\s+(?:received|were\\s+paid|got)\\s+" + AMOUNT);
	private static final Pattern RECEIVED_FROM = Pattern.compile(
			"(?i)(" + NAME + ")\\s+(?:paid|sent)\\s+you\\s+" + AMOUNT);
	private static final Pattern SOLD_FOR = Pattern.compile(
			"(?i)you\\s+sold\\b.{0,120}?\\bfor\\s+" + AMOUNT);
	private static final Pattern EARNED = Pattern.compile(
			"(?i)you\\s+earned\\s+" + AMOUNT);

	private static final Pattern RANK_PREFIX = Pattern.compile("^\\s*(?:\\[[^\\]]{1,24}\\]\\s*)+");

	public static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.replaceAll("§.", "");
		s = RANK_PREFIX.matcher(s).replaceFirst("");
		return s.trim().replaceAll("\\s+", " ");
	}

	/** Balance lines only; avoids /baltop and trade price noise. */
	public static OptionalDouble parseBalance(String raw) {
		String line = clean(raw);
		if (line.isEmpty()) {
			return OptionalDouble.empty();
		}
		String lower = line.toLowerCase();
		if (lower.contains("baltop") || lower.contains("leaderboard")) {
			return OptionalDouble.empty();
		}
		Matcher m = BALANCE.matcher(line);
		if (m.find()) {
			double value = toAmount(m.group(1), m.group(2));
			if (Double.isFinite(value) && value >= 0) {
				return OptionalDouble.of(value);
			}
		}
		m = BALANCE_ALT.matcher(line);
		if (m.find()) {
			double value = toAmount(m.group(1), m.group(2));
			if (Double.isFinite(value) && value >= 0) {
				return OptionalDouble.of(value);
			}
		}
		return OptionalDouble.empty();
	}

	public static Optional<String> parseTradeRequest(String raw) {
		String line = clean(raw);
		if (line.isEmpty()) {
			return Optional.empty();
		}
		for (Pattern p : List.of(TRADE_REQUEST_1, TRADE_REQUEST_2, TRADE_REQUEST_3, TRADE_REQUEST_4)) {
			Matcher m = p.matcher(line);
			if (m.find()) {
				return Optional.of(m.group(1));
			}
		}
		return Optional.empty();
	}

	/** Signed cash delta from pay/receive/sell confirmations. */
	public static OptionalDouble parseCashDelta(String raw) {
		String line = clean(raw);
		if (line.isEmpty()) {
			return OptionalDouble.empty();
		}
		Matcher m = RECEIVED_FROM.matcher(line);
		if (m.find()) {
			double value = toAmount(m.group(2), m.group(3));
			if (Double.isFinite(value)) {
				return OptionalDouble.of(value);
			}
		}
		m = PAID.matcher(line);
		if (m.find()) {
			double value = toAmount(m.group(1), m.group(2));
			if (Double.isFinite(value)) {
				return OptionalDouble.of(-value);
			}
		}
		m = RECEIVED.matcher(line);
		if (m.find()) {
			double value = toAmount(m.group(1), m.group(2));
			if (Double.isFinite(value)) {
				return OptionalDouble.of(value);
			}
		}
		m = SOLD_FOR.matcher(line);
		if (m.find()) {
			double value = toAmount(m.group(1), m.group(2));
			if (Double.isFinite(value)) {
				return OptionalDouble.of(value);
			}
		}
		m = EARNED.matcher(line);
		if (m.find()) {
			double value = toAmount(m.group(1), m.group(2));
			if (Double.isFinite(value)) {
				return OptionalDouble.of(value);
			}
		}
		return OptionalDouble.empty();
	}

	/** Extracts plausible item names from a trade message for matching. */
	public static List<String> itemHints(String raw) {
		String line = clean(raw).toLowerCase();
		if (line.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		String[] candidates = {"elytra", "netherite", "ancient debris", "piglin head", "dragon head",
				"golden apple", "totem", "shulker", "spawner", "end crystal", "firework"};
		for (String c : candidates) {
			if (line.contains(c)) {
				out.add(c.replace(' ', '_'));
			}
		}
		return out;
	}

	public static double toAmount(String number, String suffix) {
		if (number == null) {
			return Double.NaN;
		}
		try {
			double value = Double.parseDouble(number.replace(",", ""));
			if (suffix == null) {
				return value;
			}
			return switch (suffix.toLowerCase()) {
				case "k" -> value * 1_000;
				case "m" -> value * 1_000_000;
				case "b" -> value * 1_000_000_000;
				case "t" -> value * 1_000_000_000_000.0;
				default -> value;
			};
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}
}
