package com.donutdeck.ui;

import com.donutdeck.DeckState;
import com.donutdeck.DonutDeck;
import com.donutdeck.core.BotProfile;
import com.donutdeck.core.Money;
import com.donutdeck.core.Quote;
import com.donutdeck.core.TradeIntent;
import com.donutdeck.core.TradeRoute;
import com.donutdeck.core.TradeSide;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compose one draft trade. Creating a draft sends nothing; sending happens in the Trade tab. */
public final class TradeComposerScreen extends Screen {
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
	private final String presetCounterparty;

	private TradeSide side = TradeSide.BUY;
	private TradeRoute route = TradeRoute.PLAYER;
	private String error = "";

	private EditBox counterpartyBox;
	private EditBox itemBox;
	private EditBox qtyBox;
	private EditBox priceBox;
	private EditBox noteBox;

	public TradeComposerScreen(DonutDeck deck, String presetItem, String presetCounterparty) {
		super(Component.literal("New DonutDeck trade"));
		this.deck = deck;
		this.presetItem = presetItem == null ? "" : presetItem;
		this.presetCounterparty = presetCounterparty == null ? "" : presetCounterparty;
	}

	private DeckState state() {
		return deck.state();
	}

	@Override
	protected void init() {
		clearWidgets();
		zones.clear();
		if (!presetItem.isBlank() && presetCounterparty.isBlank()) {
			route = TradeRoute.AUCTION;
		}

		int leftX = 20;
		int panelW = Math.max(300, Math.min(410, width / 2 - 26));
		int fieldW = panelW - 24;
		Quote preset = state().quote(presetItem);
		String presetPrice = preset != null && preset.isTrusted() ? Money.formatFull(preset.bestPrice()).replace("$", "") : "";

		counterpartyBox = addField(leftX + 12, 116, fieldW, 18, presetCounterparty, 32, "bot or player name");
		itemBox = addField(leftX + 12, 162, fieldW, 18, presetItem, 64, "item id, e.g. netherite_ingot");
		qtyBox = addField(leftX + 12, 208, 82, 18, "1", 12, "1");
		priceBox = addField(leftX + 104, 208, fieldW - 92, 18, presetPrice, 24, "price per item");
		noteBox = addField(leftX + 12, 254, fieldW, 18, "", 120, "optional note");

		addRenderableWidget(Button.builder(Component.literal("Create draft"), b -> createDraft())
				.bounds(leftX + 12, height - 34, 92, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Manage bots"), b -> {
			deck.save();
			if (minecraft != null) {
				minecraft.setScreen(new BotsScreen(deck));
			}
		}).bounds(leftX + 110, height - 34, 86, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(leftX + 202, height - 34, 58, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		zones.clear();
		g.fill(0, 0, width, height, DeckUi.BG);
		g.fillGradient(0, 0, width, 50, 0xFF101823, 0xFF090D13);
		g.fill(0, 50, width, 51, DeckUi.GOLD_DARK);
		DeckUi.textShadow(g, font, "NEW TRADE", 20, 13, DeckUi.GOLD);
		DeckUi.text(g, font, "Draft only · nothing is sent from this screen", 20, 27, DeckUi.MUTED);
		DeckUi.right(g, font, "Two-step send in Trade desk", width - 20, 27, DeckUi.FAINT);

		int leftX = 20;
		int panelW = Math.max(300, Math.min(410, width / 2 - 26));
		int panelH = height - 100;
		int rightX = leftX + panelW + 12;
		int rightW = width - rightX - 20;
		DeckUi.raised(g, leftX, 60, panelW, panelH);
		DeckUi.raised(g, rightX, 60, rightW, panelH);

		DeckUi.label(g, font, "Direction", leftX + 12, 70);
		chip(g, mouseX, mouseY, leftX + 12, 81, 70, 16, "BUY", side == TradeSide.BUY,
				() -> side = TradeSide.BUY);
		chip(g, mouseX, mouseY, leftX + 86, 81, 70, 16, "SELL", side == TradeSide.SELL,
				() -> side = TradeSide.SELL);
		DeckUi.label(g, font, "Route", leftX + 174, 70);
		chip(g, mouseX, mouseY, leftX + 174, 81, 70, 16, "PLAYER", route == TradeRoute.PLAYER,
				() -> route = TradeRoute.PLAYER);
		chip(g, mouseX, mouseY, leftX + 248, 81, 46, 16, "/AH", route == TradeRoute.AUCTION,
				() -> route = TradeRoute.AUCTION);
		chip(g, mouseX, mouseY, leftX + 298, 81, 52, 16, "/ORDER", route == TradeRoute.ORDER,
				() -> route = TradeRoute.ORDER);

		DeckUi.label(g, font, "Counterparty", leftX + 12, 104);
		DeckUi.label(g, font, "Item id", leftX + 12, 150);
		DeckUi.label(g, font, "Quantity", leftX + 12, 196);
		DeckUi.label(g, font, "Unit price", leftX + 104, 196);
		DeckUi.label(g, font, "Note", leftX + 12, 242);

		DeckUi.label(g, font, "Bot profiles", leftX + 12, 288);
		int chipX = leftX + 12;
		int chipY = 299;
		List<BotProfile> bots = state().bots;
		if (bots.isEmpty()) {
			DeckUi.text(g, font, "No bots saved. Manage bots to add a counterparty profile.", leftX + 12, chipY + 3, DeckUi.FAINT);
		} else {
			for (int i = 0; i < Math.min(4, bots.size()); i++) {
				BotProfile bot = bots.get(i);
				int w = Math.min(100, font.width(bot.name) + 14);
				chip(g, mouseX, mouseY, chipX, chipY, w, 15, bot.name, bot.trusted, () -> {
					counterpartyBox.setValue(bot.name);
					if (route == TradeRoute.AUCTION || route == TradeRoute.ORDER) {
						route = TradeRoute.PLAYER;
					}
				});
				chipX += w + 5;
				if (chipX > leftX + panelW - 70) {
					chipX = leftX + 12;
					chipY += 19;
				}
			}
		}

		DeckUi.label(g, font, "Item shortcuts", leftX + 12, 342);
		chipX = leftX + 12;
		chipY = 353;
		List<Quote> quotes = new ArrayList<>(state().visibleQuotes());
		quotes.sort(java.util.Comparator.comparing((Quote q) -> !q.isTrusted())
				.thenComparing(Quote::shortName, String.CASE_INSENSITIVE_ORDER));
		if (quotes.isEmpty()) {
			DeckUi.text(g, font, "No item list yet. Type an id above or refresh the market.", leftX + 12, chipY + 3, DeckUi.FAINT);
		} else {
			for (int i = 0; i < Math.min(8, quotes.size()); i++) {
				Quote quote = quotes.get(i);
				int w = Math.min(112, font.width(quote.shortName()) + 14);
				chip(g, mouseX, mouseY, chipX, chipY, w, 15, quote.shortName(), quote.isTrusted(), () -> {
					itemBox.setValue(quote.itemId);
					if (quote.isTrusted()) {
						priceBox.setValue(Money.formatFull(quote.bestPrice()).replace("$", ""));
					} else {
						priceBox.setValue("");
						error = "This item has no trusted price. Enter one you verified in game.";
					}
				});
				chipX += w + 5;
				if (chipX > leftX + panelW - 80) {
					chipX = leftX + 12;
					chipY += 19;
				}
			}
		}

		drawPreview(g, rightX + 12, 70, rightW - 24);

		if (!error.isBlank()) {
			DeckUi.text(g, font, DeckUi.fit(font, error, panelW - 24), leftX + 12, height - 49, DeckUi.RED);
		}
		super.render(g, mouseX, mouseY, partialTick);
	}

	private void drawPreview(GuiGraphics g, int x, int y, int w) {
		DeckUi.label(g, font, "Order preview", x, y);
		y += 12;
		String rawItem = itemBox == null ? "" : itemBox.getValue();
		Quote quote = state().quote(rawItem);
		String name = quote != null ? quote.shortName() : (rawItem.isBlank() ? "Pick an item" : Quote.prettify(rawItem));
		g.fill(x, y, x + w, y + 48, 0xFF111A24);
		DeckUi.border(g, x, y, w, 48, DeckUi.BORDER_SOFT);
		DeckUi.item(g, rawItem, x + 7, y + 7);
		DeckUi.text(g, font, DeckUi.fit(font, name, w - 60), x + 30, y + 8, DeckUi.TEXT);
		DeckUi.text(g, font, DeckUi.fit(font, quote == null ? "unknown item" : quote.itemId, w - 60),
				x + 30, y + 20, DeckUi.FAINT);
		DeckUi.badge(g, font, DeckUi.sourceLabel(quote), x + 30, y + 31,
				quote != null && quote.isTrusted() ? DeckUi.GREEN : DeckUi.FAINT);
		DeckUi.right(g, font, DeckUi.price(quote), x + w - 7, y + 8, DeckUi.TEXT);
		DeckUi.right(g, font, "chg " + (quote != null && quote.isTrusted() ? DeckUi.compactPct(quote.changePct) : "—"),
				x + w - 7, y + 21, DeckUi.changeColor(quote == null ? 0 : quote.changePct));

		double qty = qtyBox == null ? 0 : Money.parse(qtyBox.getValue());
		double price = priceBox == null ? 0 : Money.parse(priceBox.getValue());
		if (!Double.isFinite(qty) || qty < 0) {
			qty = 0;
		}
		if (!Double.isFinite(price) || price < 0) {
			price = 0;
		}
		double total = qty * price;
		double tax = side == TradeSide.BUY && route == TradeRoute.AUCTION
				? total * Math.max(0, state().buyerTaxPct) / 100.0 : 0;
		y += 58;

		DeckUi.text(g, font, "Draft value", x, y, DeckUi.FAINT);
		DeckUi.right(g, font, Money.format(total), x + w, y, DeckUi.TEXT);
		y += 11;
		DeckUi.text(g, font, "Est. buyer tax (" + String.format(Locale.ROOT, "%.1f%%", state().buyerTaxPct) + ")",
				x, y, DeckUi.FAINT);
		DeckUi.right(g, font, tax > 0 ? Money.format(tax) : "not applicable", x + w, y,
				tax > 0 ? DeckUi.ORANGE : DeckUi.FAINT);
		y += 11;
		DeckUi.text(g, font, "Total if filled", x, y, DeckUi.FAINT);
		DeckUi.right(g, font, Money.format(total + tax), x + w, y, DeckUi.GOLD);
		y += 16;

		DeckUi.text(g, font, "Quote checks", x, y, DeckUi.FAINT);
		y += 11;
		DeckUi.text(g, font, "Best price", x, y, DeckUi.MUTED);
		DeckUi.right(g, font, DeckUi.priceFull(quote), x + w, y,
				quote != null && quote.isTrusted() ? DeckUi.GREEN : DeckUi.FAINT);
		y += 10;
		DeckUi.text(g, font, "Low ask", x, y, DeckUi.MUTED);
		DeckUi.right(g, font, quote != null && quote.lowAsk > 0 ? Money.format(quote.lowAsk) : "—",
				x + w, y, DeckUi.FAINT);
		y += 10;
		DeckUi.text(g, font, "Median sold", x, y, DeckUi.MUTED);
		DeckUi.right(g, font, quote != null && quote.medianSold > 0 ? Money.format(quote.medianSold) : "—",
				x + w, y, DeckUi.FAINT);
		y += 10;
		DeckUi.text(g, font, "Source", x, y, DeckUi.MUTED);
		DeckUi.right(g, font, DeckUi.sourceLabel(quote), x + w, y,
				quote != null && quote.isTrusted() ? DeckUi.GREEN : DeckUi.FAINT);
		y += 17;

		DeckUi.label(g, font, "Command preview (sent only after SEND)", x, y);
		y += 12;
		g.fill(x, y, x + w, y + 22, 0xFF0B1119);
		DeckUi.border(g, x, y, w, 22, DeckUi.BORDER_SOFT);
		DeckUi.text(g, font, DeckUi.fit(font, commandPreview(), w - 12), x + 6, y + 7, DeckUi.BLUE);
		y += 32;

		DeckUi.label(g, font, "Safety interlocks", x, y);
		y += 12;
		DeckUi.text(g, font, "✔ draft only; no server command yet", x, y, DeckUi.GREEN);
		y += 10;
		DeckUi.text(g, font, "✔ send requires arm + confirm", x, y, DeckUi.GREEN);
		y += 10;
		DeckUi.text(g, font, "✔ server trade GUI is completed by you", x, y, DeckUi.GREEN);
		y += 10;
		DeckUi.text(g, font, "✔ no item movement or auto-accept", x, y, DeckUi.GREEN);
	}

	private void chip(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
			String label, boolean selected, Runnable action) {
		boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, h);
		g.fill(x, y, x + w, y + h, selected ? 0xFF203044 : hover ? DeckUi.PANEL_HOVER : DeckUi.PANEL_ALT);
		DeckUi.border(g, x, y, w, h, selected ? DeckUi.GOLD : DeckUi.BORDER);
		DeckUi.centered(g, font, label, x + w / 2, y + (h - 9) / 2, selected ? DeckUi.GOLD : DeckUi.MUTED);
		zones.add(new Zone(x, y, w, h, action));
	}

	private void createDraft() {
		error = "";
		String itemRaw = itemBox.getValue().trim();
		if (itemRaw.isBlank()) {
			error = "Pick an item id first.";
			return;
		}
		String itemId = Quote.normalizeId(itemRaw);
		Quote quote = state().quote(itemId);
		double price = Money.parse(priceBox.getValue());
		if (!Double.isFinite(price) || price <= 0) {
			if (quote != null && quote.isTrusted()) {
				price = quote.bestPrice();
			}
		}
		if (!Double.isFinite(price) || price <= 0) {
			error = "Enter a unit price you verified in game. Sample prices are not used.";
			return;
		}
		double qtyValue = Money.parse(qtyBox.getValue());
		if (!Double.isFinite(qtyValue) || qtyValue <= 0) {
			error = "Quantity must be at least 1.";
			return;
		}
		int qty = (int) Math.min(10_000_000, Math.floor(qtyValue));
		if (qty <= 0) {
			error = "Quantity must be at least 1.";
			return;
		}
		String counterparty = counterpartyBox.getValue().trim();
		if (route == TradeRoute.PLAYER && counterparty.isBlank()) {
			error = "A direct player trade needs a counterparty name.";
			return;
		}
		if (counterparty.isBlank()) {
			counterparty = route == TradeRoute.ORDER ? "Orders" : "Auction House";
		}
		TradeIntent intent = deck.trade().createIntent(counterparty, side, route, itemId, qty, price,
				noteBox.getValue());
		if (intent == null) {
			error = "Draft rejected. Check the chat message for details.";
			return;
		}
		deck.save();
		if (minecraft != null) {
			minecraft.setScreen(new DonutDeckScreen(deck, DonutDeckScreen.Tab.TRADE));
		}
	}

	private String commandPreview() {
		String item = itemBox == null ? "" : Quote.prettify(Quote.normalizeId(itemBox.getValue()));
		String name = counterpartyBox == null ? "" : counterpartyBox.getValue().trim();
		return switch (route) {
			case PLAYER -> "/" + stripSlash(state().tradeCommand) + " " + (name.isBlank() ? "<player>" : name);
			case AUCTION -> "/ah " + item;
			case ORDER -> "/order " + item;
		};
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
			minecraft.setScreen(new DonutDeckScreen(deck, DonutDeckScreen.Tab.TRADE));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	private static String stripSlash(String command) {
		if (command == null || command.isBlank()) {
			return "trade";
		}
		String s = command.trim();
		while (s.startsWith("/")) {
			s = s.substring(1);
		}
		return s;
	}
}
