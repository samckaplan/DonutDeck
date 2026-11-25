package com.donutdeck.ui;

import com.donutdeck.DeckCommands;
import com.donutdeck.DeckState;
import com.donutdeck.DonutDeck;
import com.donutdeck.core.Money;
import com.donutdeck.core.Quote;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calibration screen for prices the player has personally verified in game.
 * These manual quotes are clearly marked MANUAL and can drive the planner.
 */
public final class ManualPriceScreen extends Screen {
	private static final class Zone {
		final int x;
		final int y;
		final int w;
		final int h;
		final Runnable action;

		Zone(int x, int y, int w, int h, Runnable action) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.action = action;
		}

		boolean hit(double mx, double my) {
			return DeckUi.inside(mx, my, x, y, w, h);
		}
	}

	private final DonutDeck deck;
	private final List<Zone> zones = new ArrayList<>();
	private final String presetItem;
	private String error = "";
	private int page;

	private EditBox itemBox;
	private EditBox priceBox;

	public ManualPriceScreen(DonutDeck deck, String presetItem) {
		super(Component.literal("DonutDeck manual price"));
		this.deck = deck;
		this.presetItem = presetItem == null ? "" : presetItem;
	}

	private DeckState state() {
		return deck.state();
	}

	@Override
	protected void init() {
		clearWidgets();
		zones.clear();
		int leftX = 20;
		int leftW = Math.max(280, Math.min(380, width / 2 - 30));
		itemBox = addField(leftX + 12, 116, leftW - 24, 18, presetItem, 64, "item id, e.g. netherite_ingot");
		priceBox = addField(leftX + 12, 162, leftW - 24, 18, "", 24, "per-unit price, e.g. 20m");
		addRenderableWidget(Button.builder(Component.literal("Save price"), b -> savePrice())
				.bounds(leftX + 12, 198, 92, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(leftX + 110, 198, 58, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		zones.clear();
		g.fill(0, 0, width, height, DeckUi.BG);
		g.fillGradient(0, 0, width, 50, 0xFF101823, 0xFF090D13);
		g.fill(0, 50, width, 51, DeckUi.GOLD_DARK);
		DeckUi.textShadow(g, font, "MANUAL PRICE", 20, 13, DeckUi.GOLD);
		DeckUi.text(g, font, "Only use a price you personally checked in game", 20, 27, DeckUi.MUTED);

		int leftX = 20;
		int leftW = Math.max(280, Math.min(380, width / 2 - 30));
		int panelH = height - 100;
		int rightX = leftX + leftW + 12;
		int rightW = width - rightX - 20;
		DeckUi.raised(g, leftX, 60, leftW, panelH);
		DeckUi.raised(g, rightX, 60, rightW, panelH);

		DeckUi.text(g, font, "Manual quotes are planning inputs, not live market data.", leftX + 12, 72, DeckUi.MUTED);
		DeckUi.label(g, font, "Item id", leftX + 12, 104);
		DeckUi.label(g, font, "Unit price", leftX + 12, 150);
		DeckUi.text(g, font, "Examples: 20m · 250k · 1.2b · 3400000", leftX + 12, 185, DeckUi.FAINT);
		DeckUi.text(g, font, "Buyer tax is shown separately in the composer.", leftX + 12, 224, DeckUi.FAINT);
		DeckUi.text(g, font, "This never sends a chat message or command.", leftX + 12, 236, DeckUi.FAINT);
		if (!error.isBlank()) {
			DeckUi.text(g, font, DeckUi.fit(font, error, leftW - 24), leftX + 12, 262, DeckUi.RED);
		}

		DeckUi.label(g, font, "Known items", rightX + 10, 70);
		List<Quote> quotes = new ArrayList<>(state().quotes.values());
		quotes.sort(Comparator.comparing((Quote q) -> !q.isTrusted())
				.thenComparing(Quote::shortName, String.CASE_INSENSITIVE_ORDER));
		int rows = Math.max(4, (panelH - 44) / 24);
		int pages = Math.max(1, (quotes.size() + rows - 1) / rows);
		page = Math.max(0, Math.min(page, pages - 1));
		int from = page * rows;
		int to = Math.min(quotes.size(), from + rows);
		int y = 84;
		for (int i = from; i < to; i++) {
			Quote quote = quotes.get(i);
			int rowY = y + (i - from) * 24;
			boolean hover = DeckUi.inside(mouseX, mouseY, rightX + 6, rowY, rightW - 12, 22);
			if (hover) {
				g.fill(rightX + 6, rowY, rightX + rightW - 6, rowY + 22, DeckUi.PANEL_HOVER);
			}
			DeckUi.item(g, quote.itemId, rightX + 10, rowY + 3);
			DeckUi.text(g, font, DeckUi.fit(font, quote.shortName(), rightW - 120), rightX + 32, rowY + 3, DeckUi.TEXT);
			DeckUi.text(g, font, DeckUi.fit(font, quote.itemId, rightW - 120), rightX + 32, rowY + 13, DeckUi.FAINT);
			DeckUi.right(g, font, DeckUi.price(quote), rightX + rightW - 10, rowY + 3, DeckUi.MUTED);
			DeckUi.right(g, font, DeckUi.sourceLabel(quote), rightX + rightW - 10, rowY + 13, DeckUi.FAINT);
			zones.add(new Zone(rightX + 6, rowY, rightW - 12, 22, () -> {
				itemBox.setValue(quote.itemId);
				priceBox.setValue(quote.isTrusted() ? Money.formatFull(quote.bestPrice()).replace("$", "") : "");
				if (!quote.isTrusted()) {
					error = "No trusted price for this item. Enter what you verified.";
				} else {
					error = "";
				}
			}));
		}
		int pageY = 60 + panelH - 18;
		DeckUi.text(g, font, "Page " + (page + 1) + "/" + pages, rightX + 10, pageY, DeckUi.FAINT);
		if (pages > 1) {
			pageButton(g, mouseX, mouseY, rightX + rightW - 86, pageY - 3, 36, 14, "PREV", () -> page--);
			pageButton(g, mouseX, mouseY, rightX + rightW - 46, pageY - 3, 38, 14, "NEXT", () -> page++);
		}
		super.render(g, mouseX, mouseY, partialTick);
	}

	private void savePrice() {
		String result = DeckCommands.setManualPrice(deck, itemBox == null ? "" : itemBox.getValue(),
				priceBox == null ? "" : priceBox.getValue());
		if (result.startsWith("§a")) {
			deck.save();
			if (minecraft != null) {
				minecraft.setScreen(new DonutDeckScreen(deck, DonutDeckScreen.Tab.MARKET));
			}
		} else {
			error = result.replaceAll("§.", "");
		}
	}

	private void pageButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
			String label, Runnable action) {
		boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, h);
		g.fill(x, y, x + w, y + h, hover ? DeckUi.PANEL_HOVER : DeckUi.PANEL_ALT);
		DeckUi.border(g, x, y, w, h, DeckUi.BORDER);
		DeckUi.centered(g, font, label, x + w / 2, y + 3, hover ? DeckUi.GOLD : DeckUi.MUTED);
		zones.add(new Zone(x, y, w, h, action));
	}

	private EditBox addField(int x, int y, int w, int h, String value, int maxLength, String hint) {
		EditBox box = new EditBox(this.font, x, y, w, h, Component.empty());
		box.setMaxLength(maxLength);
		box.setValue(value == null ? "" : value);
		box.setHint(Component.literal(hint == null ? "" : hint));
		return addRenderableWidget(box);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (super.mouseClicked(event, doubled)) {
			return true;
		}
		if (event.button() != 0) {
			return false;
		}
		for (int i = zones.size() - 1; i >= 0; i--) {
			Zone zone = zones.get(i);
			if (zone.hit(event.x(), event.y())) {
				zone.action.run();
				return true;
			}
		}
		return false;
	}

	@Override
	public void onClose() {
		deck.save();
		if (minecraft != null) {
			minecraft.setScreen(new DonutDeckScreen(deck, DonutDeckScreen.Tab.MARKET));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
