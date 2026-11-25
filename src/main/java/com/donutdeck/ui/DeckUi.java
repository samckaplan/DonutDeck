package com.donutdeck.ui;

import com.donutdeck.core.MarketMath;
import com.donutdeck.core.Money;
import com.donutdeck.core.Quote;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Shared visual language for every DonutDeck screen. */
public final class DeckUi {
	public static final int BG = 0xF0090D13;
	public static final int PANEL = 0xF20F151E;
	public static final int PANEL_ALT = 0xF416202B;
	public static final int PANEL_HOVER = 0xF21B2938;
	public static final int BORDER = 0xFF243244;
	public static final int BORDER_SOFT = 0xFF1A2635;
	public static final int TEXT = 0xFFE8EFF8;
	public static final int MUTED = 0xFF8EA2BB;
	public static final int FAINT = 0xFF5B6E86;
	public static final int GOLD = 0xFFFFBE55;
	public static final int GOLD_DARK = 0xFF8A5A1F;
	public static final int GREEN = 0xFF5BD98D;
	public static final int RED = 0xFFFF7070;
	public static final int BLUE = 0xFF6FB7FF;
	public static final int PURPLE = 0xFFB58CFF;
	public static final int ORANGE = 0xFFFF9D5C;

	private DeckUi() {
	}

	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL);
		border(g, x, y, w, h, BORDER_SOFT);
	}

	public static void raised(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL_ALT);
		border(g, x, y, w, h, BORDER);
	}

	public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	public static void accentLine(GuiGraphics g, int x, int y, int w, int color) {
		g.fill(x, y, x + w, y + 2, color);
	}

	public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
		g.drawString(font, text == null ? "" : text, x, y, color, false);
	}

	public static void textShadow(GuiGraphics g, Font font, String text, int x, int y, int color) {
		g.drawString(font, text == null ? "" : text, x, y, color, true);
	}

	public static void right(GuiGraphics g, Font font, String text, int rightX, int y, int color) {
		String s = text == null ? "" : text;
		g.drawString(font, s, rightX - font.width(s), y, color, false);
	}

	public static void centered(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
		String s = text == null ? "" : text;
		g.drawString(font, s, centerX - font.width(s) / 2, y, color, false);
	}

	public static void label(GuiGraphics g, Font font, String text, int x, int y) {
		text(g, font, text == null ? "" : text.toUpperCase(Locale.ROOT), x, y, FAINT);
	}

	public static void badge(GuiGraphics g, Font font, String text, int x, int y, int color) {
		String s = text == null ? "" : text;
		int w = font.width(s) + 8;
		g.fill(x, y, x + w, y + 12, withAlpha(color, 32));
		border(g, x, y, w, 12, withAlpha(color, 90));
		g.drawString(font, s, x + 4, y + 2, color, false);
	}

	public static void bar(GuiGraphics g, int x, int y, int w, int h, double pct, int color) {
		g.fill(x, y, x + w, y + h, 0xFF1A2431);
		int filled = (int) Math.round(w * Math.max(0, Math.min(100, pct)) / 100.0);
		if (filled > 0) {
			g.fill(x, y, x + filled, y + h, color);
		}
		border(g, x, y, w, h, BORDER_SOFT);
	}

	public static void item(GuiGraphics g, String itemId, int x, int y) {
		try {
			String id = Quote.normalizeId(itemId);
			Identifier identifier = Identifier.tryParse(id);
			if (identifier == null) {
				return;
			}
			Item item = BuiltInRegistries.ITEM.getValue(identifier);
			if (item == null || item == Items.AIR) {
				return;
			}
			g.renderItem(new ItemStack(item), x, y);
		} catch (Exception ignored) {
			// An unknown or modded item must never break a screen.
		}
	}

	public static String price(Quote quote) {
		if (quote == null || !quote.hasPrice()) {
			return "—";
		}
		return Money.format(quote.bestPrice());
	}

	public static String priceFull(Quote quote) {
		if (quote == null || !quote.hasPrice()) {
			return "no price";
		}
		return Money.formatFull(quote.bestPrice());
	}

	public static int changeColor(double pct) {
		if (pct > 0.05) {
			return GREEN;
		}
		if (pct < -0.05) {
			return RED;
		}
		return MUTED;
	}

	public static int pnlColor(double value) {
		if (value > 0.01) {
			return GREEN;
		}
		if (value < -0.01) {
			return RED;
		}
		return MUTED;
	}

	public static int statusColor(String status) {
		if (status == null) {
			return FAINT;
		}
		return switch (status.toUpperCase(Locale.ROOT)) {
			case "LIVE", "OK" -> GREEN;
			case "PARTIAL", "STALE" -> ORANGE;
			case "ERROR", "OFF" -> RED;
			default -> FAINT;
		};
	}

	public static String statusLabel(String status) {
		if (status == null) {
			return "NO DATA";
		}
		return switch (status.toUpperCase(Locale.ROOT)) {
			case "NO_DATA" -> "NO DATA";
			case "LIVE" -> "LIVE";
			case "PARTIAL" -> "PARTIAL";
			case "ERROR" -> "ERROR";
			case "OFF" -> "API OFF";
			default -> status.toUpperCase(Locale.ROOT);
		};
	}

	public static String fit(Font font, String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		String s = text;
		while (!s.isEmpty() && font.width(s + ellipsis) > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + ellipsis;
	}

	public static String age(Quote quote, long now) {
		return quote == null ? "—" : quote.ageLabel(now);
	}

	public static String sourceLabel(Quote quote) {
		if (quote == null) {
			return "—";
		}
		if (quote.isLive()) {
			return "live";
		}
		if (quote.isManual()) {
			return "manual";
		}
		if (quote.isSample()) {
			return "sample";
		}
		return quote.source == null ? "?" : quote.source.toLowerCase(Locale.ROOT);
	}

	public static String compactPct(double pct) {
		return MarketMath.compactChange(pct);
	}

	public static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	public static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}

	public static int mix(int a, int b, double t) {
		double clamped = Math.max(0, Math.min(1, t));
		int aa = (a >>> 24) & 0xFF;
		int ar = (a >>> 16) & 0xFF;
		int ag = (a >>> 8) & 0xFF;
		int ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF;
		int br = (b >>> 16) & 0xFF;
		int bg = (b >>> 8) & 0xFF;
		int bb = b & 0xFF;
		int oa = (int) Math.round(aa + (ba - aa) * clamped);
		int or = (int) Math.round(ar + (br - ar) * clamped);
		int og = (int) Math.round(ag + (bg - ag) * clamped);
		int ob = (int) Math.round(ab + (bb - ab) * clamped);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}
}
