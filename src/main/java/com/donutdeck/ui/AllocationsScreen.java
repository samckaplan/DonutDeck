package com.donutdeck.ui;

import com.donutdeck.DeckState;
import com.donutdeck.DonutDeck;
import com.donutdeck.core.Allocation;
import com.donutdeck.core.Quote;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Editable target weights for the advisory planner. */
public final class AllocationsScreen extends Screen {
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
	private EditBox itemBox;
	private EditBox targetBox;
	private String error = "";

	public AllocationsScreen(DonutDeck deck) {
		super(Component.literal("DonutDeck targets"));
		this.deck = deck;
	}

	private DeckState state() {
		return deck.state();
	}

	@Override
	protected void init() {
		clearWidgets();
		zones.clear();
		itemBox = addField(20, height - 82, 200, 18, "", 64, "item id");
		targetBox = addField(226, height - 82, 60, 18, "10", 5, "%");
		addRenderableWidget(Button.builder(Component.literal("Add target"), b -> addTarget())
				.bounds(292, height - 82, 78, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Defaults"), b -> {
			state().allocations = Allocation.defaults();
			deck.save();
			error = "";
		}).bounds(376, height - 82, 66, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(448, height - 82, 54, 18).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		zones.clear();
		g.fill(0, 0, width, height, DeckUi.BG);
		g.fillGradient(0, 0, width, 56, 0xFF101823, 0xFF090D13);
		g.fill(0, 56, width, 57, DeckUi.GOLD_DARK);
		DeckUi.textShadow(g, font, "TARGET ALLOCATION", 20, 14, DeckUi.GOLD);
		DeckUi.text(g, font, "Percent of investable net worth. The planner proposes, you confirm every order.", 20, 29, DeckUi.MUTED);

		int x = 20;
		int w = width - 40;
		int y = 70;
		DeckUi.label(g, font, "Item", x + 28, y);
		DeckUi.label(g, font, "Current", x + w - 270, y);
		DeckUi.label(g, font, "Target", x + w - 190, y);
		DeckUi.label(g, font, "Adjust", x + w - 120, y);
		y += 13;
		g.fill(x, y, x + w, y + 1, DeckUi.BORDER_SOFT);
		y += 4;

		List<Allocation> allocations = state().allocations;
		int rowH = 29;
		int rows = Math.min(8, Math.max(2, (height - 190) / rowH));
		double base = state().investableBase();
		int shown = Math.min(rows, allocations.size());
		for (int i = 0; i < shown; i++) {
			Allocation allocation = allocations.get(i);
			Quote quote = state().quote(allocation.itemId);
			double current = state().portfolio.pctOf(allocation.itemId, state().quotes, base);
			int rowY = y + i * rowH;
			boolean hover = DeckUi.inside(mouseX, mouseY, x, rowY, w, rowH - 2);
			g.fill(x, rowY, x + w, rowY + rowH - 2, hover ? DeckUi.PANEL_HOVER : i % 2 == 0 ? 0x33141C26 : 0x14141C26);
			DeckUi.item(g, allocation.itemId, x + 4, rowY + 5);
			DeckUi.text(g, font, DeckUi.fit(font, quote == null ? Quote.prettify(allocation.itemId) : quote.shortName(), 240),
					x + 26, rowY + 3, DeckUi.TEXT);
			DeckUi.text(g, font, DeckUi.fit(font, allocation.itemId, 240), x + 26, rowY + 13, DeckUi.FAINT);
			DeckUi.right(g, font, String.format(Locale.ROOT, "%.1f%%", current), x + w - 210, rowY + 7, DeckUi.MUTED);
			DeckUi.text(g, font, String.format(Locale.ROOT, "%.0f%%", allocation.targetPct),
					x + w - 170, rowY + 7, DeckUi.GOLD);
			int chipX = x + w - 120;
			chip(g, mouseX, mouseY, chipX, rowY + 5, 25, 16, "-5", () -> adjust(allocation, -5));
			chip(g, mouseX, mouseY, chipX + 29, rowY + 5, 25, 16, "-1", () -> adjust(allocation, -1));
			chip(g, mouseX, mouseY, chipX + 58, rowY + 5, 25, 16, "+1", () -> adjust(allocation, 1));
			chip(g, mouseX, mouseY, chipX + 87, rowY + 5, 25, 16, "+5", () -> adjust(allocation, 5));
			chip(g, mouseX, mouseY, chipX + 116, rowY + 5, 22, 16, "X", () -> {
				state().allocations.remove(allocation);
				deck.save();
			});
		}
		if (allocations.size() > shown) {
			DeckUi.text(g, font, "+" + (allocations.size() - shown) + " more — remove some or use the defaults.",
					x, y + shown * rowH + 4, DeckUi.ORANGE);
		}

		DeckUi.label(g, font, "Add / replace target", 20, height - 96);
		DeckUi.text(g, font, "The percentages do not need to total 100; the remainder stays as cash/reserve.",
				20, height - 60, DeckUi.FAINT);
		if (!error.isBlank()) {
			DeckUi.text(g, font, error, 20, height - 48, DeckUi.RED);
		}
		DeckUi.text(g, font, "Total targets: " + String.format(Locale.ROOT, "%.0f%%", totalTargets()),
				20, height - 36, DeckUi.MUTED);
		DeckUi.right(g, font, "Saves locally · no server action", width - 20, height - 36, DeckUi.FAINT);
		super.render(g, mouseX, mouseY, partialTick);
	}

	private void adjust(Allocation allocation, double delta) {
		allocation.targetPct = Math.max(0, Math.min(100, allocation.targetPct + delta));
		deck.save();
	}

	private void addTarget() {
		String id = Quote.normalizeId(itemBox == null ? "" : itemBox.getValue());
		if (id.isBlank()) {
			error = "Item id required.";
			return;
		}
		double value;
		try {
			value = Double.parseDouble(targetBox.getValue().replace("%", "").trim());
		} catch (Exception e) {
			error = "Target must be a number from 0 to 100.";
			return;
		}
		if (!Double.isFinite(value) || value < 0 || value > 100) {
			error = "Target must be from 0 to 100.";
			return;
		}
		for (Allocation allocation : state().allocations) {
			if (allocation.itemId.equalsIgnoreCase(id)) {
				allocation.targetPct = value;
				deck.save();
				error = "";
				return;
			}
		}
		state().allocations.add(new Allocation(id, value));
		deck.save();
		error = "";
		itemBox.setValue("");
	}

	private double totalTargets() {
		double total = 0;
		for (Allocation allocation : state().allocations) {
			total += allocation.targetPct;
		}
		return total;
	}

	private void chip(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String label, Runnable action) {
		boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, h);
		g.fill(x, y, x + w, y + h, hover ? DeckUi.PANEL_HOVER : DeckUi.PANEL_ALT);
		DeckUi.border(g, x, y, w, h, hover ? DeckUi.GOLD : DeckUi.BORDER);
		DeckUi.centered(g, font, label, x + w / 2, y + (h - 9) / 2, hover ? DeckUi.GOLD : DeckUi.MUTED);
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
			minecraft.setScreen(new DonutDeckScreen(deck, DonutDeckScreen.Tab.INVEST));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
