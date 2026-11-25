package com.donutdeck.ui;

import com.donutdeck.DeckState;
import com.donutdeck.DonutDeck;
import com.donutdeck.core.BotProfile;
import com.donutdeck.core.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Whitelisted counterparties and player-run trade bots. Stats update on marked fills. */
public final class BotsScreen extends Screen {
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
	private EditBox nameBox;
	private String error = "";
	private int page;
	private String removeKey = "";
	private long removeUntil;

	public BotsScreen(DonutDeck deck) {
		super(Component.literal("DonutDeck bots"));
		this.deck = deck;
	}

	private DeckState state() {
		return deck.state();
	}

	@Override
	protected void init() {
		clearWidgets();
		zones.clear();
		nameBox = addField(20, 108, 220, 18, "", 32, "bot or player name");
		addRenderableWidget(Button.builder(Component.literal("Add profile"), b -> addBot())
				.bounds(20, 132, 92, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back to desk"), b -> onClose())
				.bounds(118, 132, 100, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		zones.clear();
		g.fill(0, 0, width, height, DeckUi.BG);
		g.fillGradient(0, 0, width, 56, 0xFF101823, 0xFF090D13);
		g.fill(0, 56, width, 57, DeckUi.GOLD_DARK);
		DeckUi.textShadow(g, font, "COUNTERPARTIES", 20, 14, DeckUi.GOLD);
		DeckUi.text(g, font, "Saved names make drafts faster. A profile is never trusted automatically for money.", 20, 29, DeckUi.MUTED);
		DeckUi.right(g, font, "No auto-accept · no auto-trade", width - 20, 29, DeckUi.FAINT);

		DeckUi.label(g, font, "Add a bot or trusted player", 20, 96);
		DeckUi.label(g, font, "Saved profiles", 270, 70);
		if (!error.isBlank()) {
			DeckUi.text(g, font, error, 20, 160, DeckUi.RED);
		}

		int panelX = 264;
		int panelW = width - panelX - 20;
		int y = 84;
		List<BotProfile> bots = state().bots;
		int rows = Math.max(4, (height - 180) / 34);
		int pages = Math.max(1, (bots.size() + rows - 1) / rows);
		page = Math.max(0, Math.min(page, pages - 1));
		int from = page * rows;
		int to = Math.min(bots.size(), from + rows);
		if (bots.isEmpty()) {
			DeckUi.text(g, font, "No profiles yet. Add the bot or player you trade with most.", panelX, y + 20, DeckUi.FAINT);
			DeckUi.text(g, font, "Profiles are local only and never uploaded.", panelX, y + 34, DeckUi.FAINT);
			return;
		}
		for (int i = from; i < to; i++) {
			BotProfile bot = bots.get(i);
			int rowY = y + (i - from) * 34;
			boolean hover = DeckUi.inside(mouseX, mouseY, panelX, rowY, panelW, 31);
			g.fill(panelX, rowY, panelX + panelW, rowY + 31, hover ? DeckUi.PANEL_HOVER : 0x33141C26);
			DeckUi.border(g, panelX, rowY, panelX + panelW, rowY + 31, DeckUi.BORDER_SOFT);
			DeckUi.text(g, font, bot.name, panelX + 8, rowY + 5, DeckUi.TEXT);
			DeckUi.badge(g, font, bot.trusted ? "TRUSTED" : "REVIEW", panelX + 8, rowY + 17,
					bot.trusted ? DeckUi.GREEN : DeckUi.ORANGE);
			DeckUi.text(g, font, bot.trades + " fills · " + Money.format(bot.volume),
					panelX + 92, rowY + 5, DeckUi.MUTED);
			DeckUi.text(g, font, "last " + (bot.lastTradeAt <= 0 ? "never" : timeAgo(bot.lastTradeAt)),
					panelX + 92, rowY + 17, DeckUi.FAINT);
			DeckUi.text(g, font, DeckUi.fit(font, bot.notes, panelW - 340), panelX + 220, rowY + 11, DeckUi.FAINT);

			int actionX = panelX + panelW - 118;
			actionChip(g, mouseX, mouseY, actionX, rowY + 7, 54, 16,
					bot.trusted ? "REVIEW" : "TRUST", bot.trusted ? DeckUi.MUTED : DeckUi.GREEN, () -> {
						bot.trusted = !bot.trusted;
						deck.save();
					});
			boolean confirming = removeKey.equals(bot.name) && System.currentTimeMillis() < removeUntil;
			actionChip(g, mouseX, mouseY, actionX + 58, rowY + 7, 54, 16,
					confirming ? "SURE?" : "REMOVE", DeckUi.RED, () -> {
						long now = System.currentTimeMillis();
						if (removeKey.equals(bot.name) && now < removeUntil) {
							state().bots.remove(bot);
							removeKey = "";
							removeUntil = 0;
							deck.save();
							error = "";
						} else {
							removeKey = bot.name;
							removeUntil = now + 5_000L;
						}
					});
		}
		if (pages > 1) {
			DeckUi.right(g, font, "Page " + (page + 1) + "/" + pages, width - 20, height - 26, DeckUi.FAINT);
			actionChip(g, mouseX, mouseY, width - 150, height - 32, 54, 16, "PREV", DeckUi.MUTED, () -> page--);
			actionChip(g, mouseX, mouseY, width - 92, height - 32, 54, 16, "NEXT", DeckUi.MUTED, () -> page++);
		}
		super.render(g, mouseX, mouseY, partialTick);
	}

	private void addBot() {
		String name = nameBox == null ? "" : nameBox.getValue().trim();
		if (name.isEmpty() || !name.matches("[A-Za-z0-9_]{1,32}")) {
			error = "Use 1–32 letters, numbers, or underscores.";
			return;
		}
		if (state().bot(name) != null) {
			error = "That profile already exists.";
			return;
		}
		state().bots.add(new BotProfile(name));
		deck.save();
		nameBox.setValue("");
		error = "";
		page = Math.max(0, (state().bots.size() - 1) / 6);
	}

	private void actionChip(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
			String label, int color, Runnable action) {
		boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, h);
		g.fill(x, y, x + w, y + h, hover ? DeckUi.PANEL_HOVER : DeckUi.PANEL_ALT);
		DeckUi.border(g, x, y, w, h, hover ? color : DeckUi.BORDER);
		DeckUi.centered(g, font, label, x + w / 2, y + (h - 9) / 2, hover ? color : DeckUi.MUTED);
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
			minecraft.setScreen(new DonutDeckScreen(deck, DonutDeckScreen.Tab.TRADE));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	private static String timeAgo(long timestamp) {
		long seconds = Math.max(0, (System.currentTimeMillis() - timestamp) / 1000L);
		if (seconds < 60) {
			return seconds + "s";
		}
		if (seconds < 3600) {
			return (seconds / 60) + "m";
		}
		if (seconds < 86_400) {
			return (seconds / 3600) + "h";
		}
		return (seconds / 86_400) + "d";
	}
}
