package com.donutdeck;

import com.donutdeck.core.Money;
import com.donutdeck.ui.DeckUi;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Compact, non-interactive HUD. It never renders over a DonutDeck screen. */
public final class DeckHud implements HudElement {
	private final DonutDeck deck;

	public DeckHud(DonutDeck deck) {
		this.deck = deck;
	}

	@Override
	public void render(GuiGraphics g, DeltaTracker deltaTracker) {
		DeckState state = deck.state();
		Minecraft minecraft = Minecraft.getInstance();
		if (!state.showHud || minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) {
			return;
		}
		int screenW = minecraft.getWindow().getGuiScaledWidth();
		int screenH = minecraft.getWindow().getGuiScaledHeight();
		int width = 172;
		int height = 61;
		int x = screenW - width - 7;
		int y = screenH - height - 7;
		if (x < 4 || y < 4) {
			return;
		}

		g.fill(x, y, x + width, y + height, 0xE60A0F16);
		DeckUi.border(g, x, y, width, height, 0xFF2B3A4D);
		g.fill(x, y, x + 3, y + height, DeckUi.GOLD);
		g.drawString(minecraft.font, "DONUTDECK", x + 9, y + 6, DeckUi.GOLD, false);
		String status = DeckUi.statusLabel(state.marketStatus);
		int statusColor = DeckUi.statusColor(state.marketStatus);
		DeckUi.right(g, minecraft.font, status, x + width - 7, y + 6, statusColor);

		g.drawString(minecraft.font, "NW " + Money.format(state.netWorth()), x + 9, y + 20, DeckUi.TEXT, false);
		g.drawString(minecraft.font, "Cash " + Money.format(state.portfolio.cash), x + 9, y + 32, DeckUi.MUTED, false);
		double pnl = state.portfolio.unrealizedPnl(state.quotes);
		String pnlText = "P/L " + Money.format(pnl);
		g.drawString(minecraft.font, pnlText, x + 9, y + 44, DeckUi.pnlColor(pnl), false);

		int pending = state.pendingRequests.size();
		if (pending > 0) {
			String text = pending + " req";
			DeckUi.right(g, minecraft.font, text, x + width - 7, y + 32, DeckUi.RED);
		} else if (state.openIntentCount() > 0) {
			String text = state.openIntentCount() + " open";
			DeckUi.right(g, minecraft.font, text, x + width - 7, y + 32, DeckUi.BLUE);
		}
	}
}
