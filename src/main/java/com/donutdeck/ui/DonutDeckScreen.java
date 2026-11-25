package com.donutdeck.ui;

import com.donutdeck.BroadcastService;
import com.donutdeck.DeckState;
import com.donutdeck.DonutDeck;
import com.donutdeck.OpsecCompat;
import com.donutdeck.core.ActivityEvent;
import com.donutdeck.core.Allocation;
import com.donutdeck.core.BotProfile;
import com.donutdeck.core.BroadcastEvent;
import com.donutdeck.core.Holding;
import com.donutdeck.core.IntentStatus;
import com.donutdeck.core.InvestTicket;
import com.donutdeck.core.Money;
import com.donutdeck.core.PendingRequest;
import com.donutdeck.core.Quote;
import com.donutdeck.core.TradeIntent;
import com.donutdeck.core.TradeRoute;
import com.donutdeck.core.TradeSide;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The main terminal. This is a normal Minecraft screen: it pauses the game and
 * never injects input, opens containers, or talks to a server on its own.
 */
public final class DonutDeckScreen extends Screen {
	public enum Tab {
		DASHBOARD("Dashboard", "▦"),
		MARKET("Market", "◈"),
		TRADE("Trade desk", "⇄"),
		INVEST("Auto-invest", "◎"),
		BROADCAST("Broadcast", "◉"),
		COMPLIANCE("Compliance", "▣"),
		SETTINGS("Settings", "⚙");

		final String label;
		final String glyph;

		Tab(String label, String glyph) {
			this.label = label;
			this.glyph = glyph;
		}
	}

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
	private Tab tab;
	private int marketPage;
	private int tradePage;
	private String notice = "";
	private long noticeUntil;
	private String confirmKey = "";
	private long confirmUntil;

	private EditBox apiKeyBox;
	private EditBox refreshBox;
	private EditBox tradeCommandBox;
	private EditBox partyCommandBox;
	private EditBox taxBox;
	private EditBox reserveBox;
	private EditBox maxOrderBox;
	private EditBox minTicketBox;
	private EditBox bandBox;
	private EditBox maxTicketsBox;
	private EditBox cashBox;
	private EditBox webhookBox;

	public DonutDeckScreen(DonutDeck deck, Tab tab) {
		super(Component.literal("DonutDeck"));
		this.deck = deck;
		this.tab = tab == null ? Tab.DASHBOARD : tab;
	}

	private DeckState state() {
		return deck.state();
	}

	public void setTab(Tab next) {
		if (next == null || next == tab) {
			return;
		}
		collectAllFields();
		this.tab = next;
		this.marketPage = 0;
		this.tradePage = 0;
		this.confirmKey = "";
		this.rebuildWidgets();
	}

	@Override
	protected void init() {
		this.clearWidgets();
		this.zones.clear();

		if (!state().onboardingSeen) {
			addRenderableWidget(Button.builder(Component.literal("I UNDERSTAND — OPEN THE DESK"), b -> {
				state().onboardingSeen = true;
				deck.save();
				this.rebuildWidgets();
			}).bounds(this.width / 2 - 100, this.height / 2 + 74, 200, 20).build());
			return;
		}

		switch (tab) {
			case MARKET -> {
				addButton("Refresh", contentX() + contentW() - 150, contentY() + 8, 64, 18,
						() -> {
							deck.market().refresh(true);
							flash("Refresh requested — watch the status badge.");
						});
				addButton("Set price", contentX() + contentW() - 80, contentY() + 8, 68, 18,
						() -> openManualPrice(""));
			}
			case TRADE -> {
				addButton("New trade", contentX() + contentW() - 158, contentY() + 8, 72, 18,
						() -> openComposer("", ""));
				addButton("Bots", contentX() + contentW() - 80, contentY() + 8, 68, 18,
						this::openBots);
			}
			case INVEST -> {
				addButton("Targets", contentX() + contentW() - 238, contentY() + 8, 70, 18,
						() -> {
							collectAllFields();
							deck.save();
							if (this.minecraft != null) {
								this.minecraft.setScreen(new AllocationsScreen(deck));
							}
						});
				addButton("Run plan", contentX() + contentW() - 158, contentY() + 8, 72, 18,
						() -> {
							if (!state().hasTrustedPrices()) {
								flash("No trusted prices. Use /api or set a manual price first.");
								return;
							}
							List<InvestTicket> tickets = deck.invest().plan();
							int count = tickets.size();
							if (count > 0) {
								deck.broadcast().onInvestPlan(tickets);
							}
							flash(count == 0 ? "Already balanced — no tickets."
									: count + " advisory ticket" + (count == 1 ? "" : "s") + " ready.");
						});
				addButton("Queue all", contentX() + contentW() - 80, contentY() + 8, 68, 18,
						() -> {
							int queued = deck.invest().queueAll();
							flash(queued == 0 ? "Nothing to queue." : queued + " draft intent(s) queued. Send each one manually.");
						});
			}
			case BROADCAST -> {
				int fieldW = Math.min(300, contentW() - 170);
				webhookBox = addField(contentX() + 12, contentY() + 216, fieldW, 18,
						state().webhookUrl, 512, "https://discord.com/api/webhooks/…");
				addButton("Save hook", contentX() + 12 + fieldW + 6, contentY() + 216, 64, 18,
						() -> {
							collectBroadcastFields();
							flash("Webhook " + (BroadcastService.isValidWebhook(state().webhookUrl)
									? "saved locally." : "not valid yet."));
						});
				addButton("Test", contentX() + 12 + fieldW + 74, contentY() + 216, 48, 18,
						() -> {
							collectBroadcastFields();
							String result = deck.broadcast().testDiscord();
							flash(result);
						});
			}
			case SETTINGS -> {
				int left = contentX() + 12;
				int right = contentX() + contentW() / 2 + 8;
				int fieldW = Math.min(238, contentW() / 2 - 32);
				apiKeyBox = addField(left, contentY() + 74, fieldW, 18, state().apiKey, 256,
						"paste your /api key");
				refreshBox = addField(left, contentY() + 108, fieldW, 18,
						String.valueOf(state().refreshSeconds), 6, "90");
				tradeCommandBox = addField(left, contentY() + 142, fieldW, 18,
						state().tradeCommand, 32, "trade");
				partyCommandBox = addField(left, contentY() + 176, fieldW, 18,
						state().partyCommand, 32, "p");
				taxBox = addField(left, contentY() + 210, fieldW, 18,
						String.valueOf(state().buyerTaxPct), 8, "2");
				cashBox = addField(left, contentY() + 244, fieldW, 18,
						Money.formatFull(state().portfolio.cash).replace("$", "").replace(",", ""), 24, "0");

				reserveBox = addField(right, contentY() + 74, fieldW, 18,
						Money.formatFull(state().reserveCash).replace("$", "").replace(",", ""), 24, "50000000");
				maxOrderBox = addField(right, contentY() + 108, fieldW, 18,
						Money.formatFull(state().maxPerOrder).replace("$", "").replace(",", ""), 24, "100000000");
				minTicketBox = addField(right, contentY() + 142, fieldW, 18,
						Money.formatFull(state().minTicket).replace("$", "").replace(",", ""), 24, "1000000");
				bandBox = addField(right, contentY() + 176, fieldW, 18,
						String.valueOf(state().rebalanceBandPct), 8, "2");
				maxTicketsBox = addField(right, contentY() + 210, fieldW, 18,
						String.valueOf(state().maxTickets), 4, "8");

				addButton("Save settings", contentX() + 12, contentY() + contentH() - 25, 92, 20,
						() -> {
							applySettings();
							deck.save();
							flash("Settings saved to the local state file.");
							this.rebuildWidgets();
						});
				addButton("Refresh market", contentX() + 110, contentY() + contentH() - 25, 92, 20,
						() -> {
							applySettings();
							deck.save();
							deck.market().refresh(true);
							flash("Refresh requested.");
						});
				addButton("Close", contentX() + 208, contentY() + contentH() - 25, 60, 20,
						this::onClose);
			}
			default -> {
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		zones.clear();
		drawChrome(g);
		if (!state().onboardingSeen) {
			drawOnboarding(g, mouseX, mouseY);
			super.render(g, mouseX, mouseY, partialTick);
			return;
		}

		drawSidebar(g, mouseX, mouseY);
		DeckUi.raised(g, contentX() - 1, contentY() - 1, contentW() + 2, contentH() + 2);

		switch (tab) {
			case DASHBOARD -> drawDashboard(g, mouseX, mouseY);
			case MARKET -> drawMarket(g, mouseX, mouseY);
			case TRADE -> drawTrade(g, mouseX, mouseY);
			case INVEST -> drawInvest(g, mouseX, mouseY);
			case BROADCAST -> drawBroadcast(g, mouseX, mouseY);
			case COMPLIANCE -> drawCompliance(g);
			case SETTINGS -> drawSettings(g, mouseX, mouseY);
		}

		drawFooter(g);
		super.render(g, mouseX, mouseY, partialTick);
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
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_J && !(this.getFocused() instanceof EditBox)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		collectAllFields();
		deck.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(null);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	private void drawChrome(GuiGraphics g) {
		g.fill(0, 0, width, height, DeckUi.BG);
		g.fillGradient(0, 0, width, 54, 0xFF101823, 0xFF090D13);
		g.fill(0, 54, width, 55, DeckUi.GOLD_DARK);
		g.fill(12, 14, 16, 38, DeckUi.GOLD);
		DeckUi.textShadow(g, font, "◉ DONUTDECK", 20, 15, DeckUi.GOLD);
		DeckUi.text(g, font, "ECONOMY DESK · MANUAL CONFIRM · OPSEC-SAFE", 20, 29, DeckUi.FAINT);

		String netWorth = "NW " + Money.format(state().netWorth());
		DeckUi.right(g, font, netWorth, width - 14, 16, DeckUi.TEXT);
		String status = DeckUi.statusLabel(state().marketStatus);
		DeckUi.right(g, font, status, width - 14, 30, DeckUi.statusColor(state().marketStatus));
		if (state().privacyMode) {
			DeckUi.right(g, font, "PRIVACY", width - 14, 42, DeckUi.PURPLE);
		}
	}

	private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
		int x = 10;
		int y = 66;
		int w = navWidth();
		int h = 30;
		for (Tab candidate : Tab.values()) {
			boolean selected = candidate == tab;
			boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, h - 2);
			if (selected) {
				g.fill(x, y, x + w, y + h - 2, DeckUi.PANEL_ALT);
				g.fill(x, y, x + 3, y + h - 2, DeckUi.GOLD);
			} else if (hover) {
				g.fill(x, y, x + w, y + h - 2, DeckUi.PANEL);
			}
			DeckUi.text(g, font, candidate.glyph, x + 9, y + 6, selected ? DeckUi.GOLD : DeckUi.MUTED);
			DeckUi.text(g, font, candidate.label, x + 23, y + 6, selected ? DeckUi.TEXT : DeckUi.MUTED);
			final Tab target = candidate;
			zones.add(new Zone(x, y, w, h - 2, () -> setTab(target)));
			y += h;
		}

		int noteY = contentY() + contentH() - 64;
		g.fill(x, noteY, x + w, noteY + 54, 0xB30A0F16);
		DeckUi.border(g, x, noteY, w, 54, DeckUi.BORDER_SOFT);
		DeckUi.text(g, font, "MANUAL ONLY", x + 8, noteY + 8, DeckUi.GOLD);
		DeckUi.text(g, font, "No auto-accept.", x + 8, noteY + 21, DeckUi.MUTED);
		DeckUi.text(g, font, "No packet tricks.", x + 8, noteY + 32, DeckUi.MUTED);
		DeckUi.text(g, font, "No unattended play.", x + 8, noteY + 43, DeckUi.MUTED);
	}

	private void drawFooter(GuiGraphics g) {
		int y = height - 16;
		long now = System.currentTimeMillis();
		String left;
		if (notice != null && !notice.isBlank() && now < noticeUntil) {
			left = notice;
		} else {
			left = state().showHud ? "HUD on" : "HUD off";
			left += " · API " + (state().apiEnabled ? "on" : "off");
			left += " · key " + state().maskedKey();
			left += " · config " + shortPath(deck.store().displayPath());
		}
		DeckUi.text(g, font, DeckUi.fit(font, left, width - 170), 12, y,
				now < noticeUntil ? DeckUi.GOLD : DeckUi.FAINT);
		String right = "J open/close · ESC close";
		DeckUi.right(g, font, right, width - 12, y, DeckUi.FAINT);
	}

	private void drawOnboarding(GuiGraphics g, int mouseX, int mouseY) {
		g.fill(0, 0, width, height, 0xB8000000);
		int w = Math.min(500, width - 40);
		int h = 252;
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		g.fill(x, y, x + w, y + h, 0xFF0E1520);
		DeckUi.border(g, x, y, w, h, DeckUi.GOLD_DARK);
		g.fill(x, y, x + w, y + 3, DeckUi.GOLD);
		DeckUi.textShadow(g, font, "WELCOME TO DONUTDECK", x + 18, y + 18, DeckUi.GOLD);
		DeckUi.text(g, font, "A DonutSMP economy terminal built around human decisions.", x + 18, y + 34, DeckUi.MUTED);

		String[] lines = {
				"• Every trade requires two clicks. Nothing is auto-accepted.",
				"• No macros, packet tricks, movement, combat, or container automation.",
				"• No invented prices: calculations use LIVE API or MANUAL prices only.",
				"• Sample prices are opt-in and never used for investment tickets.",
				"• Broadcasts are off by default. Privacy mode blocks external channels.",
				"• Read DonutSMP's rules. You are responsible for your account."
		};
		int lineY = y + 58;
		for (String line : lines) {
			DeckUi.text(g, font, DeckUi.fit(font, line, w - 36), x + 18, lineY, DeckUi.TEXT);
			lineY += 15;
		}
		DeckUi.text(g, font, "No client can guarantee a server will never flag it.", x + 18, y + 160, DeckUi.ORANGE);
		DeckUi.text(g, font, "DonutDeck avoids automation and evasion by design.", x + 18, y + 172, DeckUi.MUTED);
		DeckUi.text(g, font, "OpSec: " + OpsecCompat.summary(), x + 18, y + 192,
				OpsecCompat.isLoaded() ? DeckUi.GREEN : DeckUi.ORANGE);
		DeckUi.text(g, font, "Press the button below to accept and continue.", x + 18, y + 208, DeckUi.FAINT);
	}

	private void drawDashboard(GuiGraphics g, int mouseX, int mouseY) {
		drawTitle(g, "Dashboard", "Your net worth, market feed, and operator checklist.");
		int x = contentX() + 12;
		int y = contentY() + 46;
		int gap = 8;
		int cardW = (contentW() - 36) / 3;
		int cardH = 50;

		double invested = state().portfolio.investedValue(state().quotes);
		double unrealized = state().portfolio.unrealizedPnl(state().quotes);
		drawKpi(g, x, y, cardW, cardH, "NET WORTH", Money.format(state().netWorth()),
				state().trustedQuoteCount() + " trusted quotes", DeckUi.GOLD);
		drawKpi(g, x + cardW + gap, y, cardW, cardH, "CASH", Money.format(state().portfolio.cash),
				"sync with /bal", DeckUi.BLUE);
		drawKpi(g, x + (cardW + gap) * 2, y, cardW, cardH, "INVESTED", Money.format(invested),
				state().portfolio.positionCount() + " positions", DeckUi.PURPLE);

		int row2 = y + cardH + gap;
		drawKpi(g, x, row2, cardW, cardH, "UNREALIZED P/L", Money.format(unrealized),
				"marked to trusted prices", DeckUi.pnlColor(unrealized));
		drawKpi(g, x + cardW + gap, row2, cardW, cardH, "REALIZED P/L", Money.format(state().portfolio.realizedPnl),
				"from marked fills", DeckUi.pnlColor(state().portfolio.realizedPnl));
		drawKpi(g, x + (cardW + gap) * 2, row2, cardW, cardH, "OPEN INTENTS",
				String.valueOf(state().openIntentCount()),
				state().pendingRequests.size() + " pending request(s)", DeckUi.ORANGE);

		int feedY = row2 + cardH + 8;
		g.fill(x, feedY, x + contentW() - 24, feedY + 32, DeckUi.PANEL);
		DeckUi.border(g, x, feedY, contentW() - 24, 32, DeckUi.BORDER_SOFT);
		DeckUi.text(g, font, "MARKET", x + 8, feedY + 6, DeckUi.FAINT);
		DeckUi.badge(g, font, DeckUi.statusLabel(state().marketStatus), x + 58, feedY + 4,
				DeckUi.statusColor(state().marketStatus));
		DeckUi.text(g, font, DeckUi.fit(font, state().marketMessage, contentW() - 190),
				x + 138, feedY + 6, DeckUi.MUTED);
		DeckUi.right(g, font, "last " + timeAgo(state().lastMarketUpdate), x + contentW() - 32, feedY + 6, DeckUi.FAINT);

		int milestoneY = feedY + 40;
		long next = state().nextMilestone();
		double netWorth = state().netWorth();
		if (next > 0) {
			long previous = previousMilestone(next);
			double span = Math.max(1, next - previous);
			double progress = Math.max(0, Math.min(100, (netWorth - previous) / span * 100.0));
			DeckUi.text(g, font, "NEXT MILESTONE", x, milestoneY, DeckUi.FAINT);
			DeckUi.right(g, font, Money.format(netWorth) + " / " + Money.format(next),
					x + contentW() - 24, milestoneY, DeckUi.MUTED);
			DeckUi.bar(g, x, milestoneY + 12, contentW() - 24, 8, progress, DeckUi.GOLD);
		} else {
			DeckUi.text(g, font, "Top milestone reached. Set your own target in the docs.", x, milestoneY, DeckUi.GOLD);
		}

		int activityY = milestoneY + 28;
		DeckUi.label(g, font, "Recent activity", x, activityY);
		int rowY = activityY + 14;
		List<ActivityEvent> activity = state().activity;
		if (activity.isEmpty()) {
			DeckUi.text(g, font, "No activity yet. Refresh the market or create a trade intent.", x, rowY, DeckUi.FAINT);
		} else {
			int max = Math.min(5, activity.size());
			for (int i = 0; i < max; i++) {
				ActivityEvent event = activity.get(i);
				g.fillGradient(x, rowY, x + contentW() - 24, rowY + 14,
						i % 2 == 0 ? 0x59121A24 : 0x33121A24, 0x00121A24);
				DeckUi.text(g, font, event.icon, x + 5, rowY + 3, DeckUi.GOLD);
				DeckUi.text(g, font, DeckUi.fit(font, event.message, contentW() - 120), x + 18, rowY + 3, DeckUi.TEXT);
				DeckUi.right(g, font, timeAgo(event.time), x + contentW() - 31, rowY + 3, DeckUi.FAINT);
				rowY += 14;
			}
		}

		int actionsY = contentY() + contentH() - 26;
		int actionW = (contentW() - 48) / 4;
		dashboardAction(g, mouseX, mouseY, x, actionsY, actionW, "REFRESH", DeckUi.BLUE,
				() -> {
					deck.market().refresh(true);
					flash("Refresh requested.");
				});
		dashboardAction(g, mouseX, mouseY, x + actionW + 8, actionsY, actionW, "NEW TRADE", DeckUi.GOLD,
				() -> openComposer("", ""));
		dashboardAction(g, mouseX, mouseY, x + (actionW + 8) * 2, actionsY, actionW, "RUN PLAN", DeckUi.PURPLE,
				() -> setTab(Tab.INVEST));
		dashboardAction(g, mouseX, mouseY, x + (actionW + 8) * 3, actionsY, actionW, "BROADCAST", DeckUi.ORANGE,
				() -> setTab(Tab.BROADCAST));
	}

	private void drawKpi(GuiGraphics g, int x, int y, int w, int h, String label, String value, String sub, int color) {
		g.fillGradient(x, y, x + w, y + h, 0xFF121C27, 0xFF0E151E);
		DeckUi.border(g, x, y, w, h, DeckUi.BORDER_SOFT);
		g.fill(x, y, x + 2, y + h, color);
		DeckUi.text(g, font, label, x + 8, y + 6, DeckUi.FAINT);
		DeckUi.text(g, font, DeckUi.fit(font, value, w - 16), x + 8, y + 19, color);
		DeckUi.text(g, font, DeckUi.fit(font, sub, w - 16), x + 8, y + 34, DeckUi.MUTED);
	}

	private void dashboardAction(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w,
			String label, int color, Runnable action) {
		boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, 20);
		g.fill(x, y, x + w, y + 20, hover ? DeckUi.PANEL_HOVER : DeckUi.PANEL_ALT);
		DeckUi.border(g, x, y, w, 20, hover ? color : DeckUi.BORDER);
		DeckUi.centered(g, font, label, x + w / 2, y + 6, hover ? color : DeckUi.MUTED);
		zones.add(new Zone(x, y, w, 20, action));
	}

	private void drawMarket(GuiGraphics g, int mouseX, int mouseY) {
		drawTitle(g, "Market intelligence", "LIVE API and MANUAL prices only. Samples are opt-in and never drive advice.");
		int x = contentX() + 12;
		int y = contentY() + 44;
		int w = contentW() - 24;
		DeckUi.label(g, font, "Item", x + 28, y);
		DeckUi.label(g, font, "Price", x + w - 164, y);
		DeckUi.label(g, font, "CHG", x + w - 96, y);
		DeckUi.label(g, font, "Source", x + w - 48, y);
		y += 12;
		g.fill(x, y, x + w, y + 1, DeckUi.BORDER_SOFT);
		y += 3;

		List<Quote> quotes = new ArrayList<>(state().visibleQuotes());
		quotes.sort(Comparator.comparing((Quote q) -> !q.isTrusted())
				.thenComparing(Comparator.comparingDouble((Quote q) -> q.bestPrice()).reversed())
				.thenComparing(Quote::shortName, String.CASE_INSENSITIVE_ORDER));

		if (quotes.isEmpty()) {
			DeckUi.text(g, font, "No trusted prices yet.", x + 8, y + 14, DeckUi.ORANGE);
			DeckUi.text(g, font, "1. Run /api on DonutSMP and paste the key in Settings.", x + 8, y + 30, DeckUi.MUTED);
			DeckUi.text(g, font, "2. Or click Set price and enter a price you verified in game.", x + 8, y + 44, DeckUi.MUTED);
			DeckUi.text(g, font, "Sample values are intentionally blank so they cannot mislead you.", x + 8, y + 58, DeckUi.FAINT);
			return;
		}

		int rowH = 27;
		int rows = Math.max(3, (contentH() - 104) / rowH);
		int pages = Math.max(1, (quotes.size() + rows - 1) / rows);
		marketPage = Math.max(0, Math.min(marketPage, pages - 1));
		int from = marketPage * rows;
		int to = Math.min(quotes.size(), from + rows);
		long now = System.currentTimeMillis();

		for (int i = from; i < to; i++) {
			Quote quote = quotes.get(i);
			int rowY = y + (i - from) * rowH;
			boolean hover = DeckUi.inside(mouseX, mouseY, x, rowY, w, rowH - 2);
			if (hover) {
				g.fill(x, rowY, x + w, rowY + rowH - 2, DeckUi.PANEL_HOVER);
			} else if ((i - from) % 2 == 0) {
				g.fill(x, rowY, x + w, rowY + rowH - 2, 0x33141C26);
			}
			DeckUi.item(g, quote.itemId, x + 5, rowY + 5);
			DeckUi.text(g, font, DeckUi.fit(font, quote.shortName(), 210), x + 27, rowY + 4, DeckUi.TEXT);
			DeckUi.text(g, font, DeckUi.fit(font, quote.itemId, 210), x + 27, rowY + 14, DeckUi.FAINT);
			DeckUi.right(g, font, DeckUi.price(quote), x + w - 100, rowY + 4,
					quote.isTrusted() ? DeckUi.TEXT : DeckUi.FAINT);
			String change = quote.isTrusted() ? DeckUi.compactPct(quote.changePct) : "—";
			DeckUi.right(g, font, change, x + w - 54, rowY + 4, DeckUi.changeColor(quote.changePct));
			DeckUi.badge(g, font, DeckUi.sourceLabel(quote), x + w - 47, rowY + 5,
					quote.isLive() ? DeckUi.GREEN : quote.isManual() ? DeckUi.BLUE : DeckUi.FAINT);
			DeckUi.right(g, font, DeckUi.age(quote, now), x + w - 100, rowY + 14, DeckUi.FAINT);
			zones.add(new Zone(x, rowY, w, rowH - 2, () -> openComposer(quote.itemId, "")));
		}

		int pageY = contentY() + contentH() - 18;
		DeckUi.text(g, font, "Click a row to draft a trade. Page " + (marketPage + 1) + "/" + pages
				+ " · " + quotes.size() + " items", x, pageY, DeckUi.FAINT);
		if (pages > 1) {
			int prevX = x + w - 92;
			pageButton(g, mouseX, mouseY, prevX, pageY - 3, 40, 14, "PREV", () -> marketPage--);
			pageButton(g, mouseX, mouseY, prevX + 46, pageY - 3, 40, 14, "NEXT", () -> marketPage++);
		}
	}

	private void drawTrade(GuiGraphics g, int mouseX, int mouseY) {
		drawTitle(g, "Trade desk", "Draft → arm → send. The server's trade GUI is always completed by you.");
		int x = contentX() + 12;
		int y = contentY() + 44;
		int w = contentW() - 24;

		int pendingShown = Math.min(3, state().pendingRequests.size());
		if (pendingShown > 0) {
			DeckUi.label(g, font, "Incoming requests", x, y);
			DeckUi.badge(g, font, "MANUAL REVIEW", x + 110, y - 2, DeckUi.RED);
			y += 13;
			for (int i = 0; i < pendingShown; i++) {
				PendingRequest request = state().pendingRequests.get(i);
				g.fill(x, y, x + w, y + 18, 0x6621141A);
				DeckUi.text(g, font, "!", x + 5, y + 4, DeckUi.RED);
				DeckUi.text(g, font, DeckUi.fit(font, request.player + " · " + request.message, w - 132),
						x + 16, y + 4, DeckUi.TEXT);
				DeckUi.badge(g, font, "REVIEW", x + w - 104, y + 3, DeckUi.GOLD);
				zones.add(new Zone(x + w - 108, y + 1, 54, 16,
						() -> openComposer("", request.player)));
				DeckUi.badge(g, font, "X", x + w - 44, y + 3, DeckUi.MUTED);
				zones.add(new Zone(x + w - 48, y + 1, 20, 16, () -> {
					state().clearPending(request.player);
					deck.save();
					flash("Dismissed request from " + request.player + ".");
				}));
				y += 20;
			}
			y += 6;
		}

		DeckUi.label(g, font, "Trade intents", x, y);
		DeckUi.right(g, font, state().openIntentCount() + " open · " + state().intents.size() + " total",
				x + w, y, DeckUi.FAINT);
		y += 13;

		List<TradeIntent> intents = state().intents;
		if (intents.isEmpty()) {
			DeckUi.text(g, font, "No trade intents yet. Click New trade to draft one.", x + 4, y + 18, DeckUi.FAINT);
			DeckUi.text(g, font, "Buying uses /ah, selling uses /order, direct trades use /trade.", x + 4, y + 34, DeckUi.MUTED);
			return;
		}

		int rowH = 36;
		int rows = Math.max(2, (contentY() + contentH() - y - 24) / rowH);
		int pages = Math.max(1, (intents.size() + rows - 1) / rows);
		tradePage = Math.max(0, Math.min(tradePage, pages - 1));
		int from = tradePage * rows;
		int to = Math.min(intents.size(), from + rows);
		for (int i = from; i < to; i++) {
			TradeIntent intent = intents.get(i);
			int rowY = y + (i - from) * rowH;
			boolean hover = DeckUi.inside(mouseX, mouseY, x, rowY, w, rowH - 2);
			if (hover) {
				g.fill(x, rowY, x + w, rowY + rowH - 2, DeckUi.PANEL_HOVER);
			} else {
				g.fill(x, rowY, x + w, rowY + rowH - 2, i % 2 == 0 ? 0x33141C26 : 0x14141C26);
			}
			int statusColor = intentColor(intent);
			DeckUi.badge(g, font, intent.status.name(), x + 5, rowY + 4, statusColor);
			DeckUi.text(g, font, DeckUi.fit(font, intent.summary(), w - 190), x + 76, rowY + 4, DeckUi.TEXT);
			DeckUi.text(g, font, DeckUi.fit(font, intent.counterparty + " · " + intent.route.name().toLowerCase(Locale.ROOT)
					+ " · " + timeAgo(intent.createdAt), w - 190), x + 76, rowY + 16, DeckUi.MUTED);
			DeckUi.text(g, font, DeckUi.fit(font, intent.note, w - 190), x + 76, rowY + 26, DeckUi.FAINT);

			int actionX = x + w - 108;
			if (intent.status == IntentStatus.DRAFT) {
				boolean armed = deck.guard().isArmed(intent.id);
				actionChip(g, mouseX, mouseY, actionX, rowY + 10, 54, 16,
						armed ? "CONFIRM" : "SEND", armed ? DeckUi.ORANGE : DeckUi.GREEN,
						() -> {
							boolean sent = deck.trade().send(intent.id);
							if (!sent) {
								flash(armed ? "Send blocked. Check the chat message." : "Armed. Click CONFIRM within 6s.");
							}
						});
			} else if (intent.status == IntentStatus.SENT) {
				DeckUi.text(g, font, "waiting", actionX, rowY + 13, DeckUi.BLUE);
			}
			if (intent.isOpen()) {
				actionChip(g, mouseX, mouseY, actionX + 58, rowY + 3, 44, 14, "FILL", DeckUi.BLUE,
						() -> triggerConfirm("fill:" + intent.id, () -> deck.trade().fill(intent.id),
								"Click FILL again to mark " + intent.shortId() + " filled."));
				actionChip(g, mouseX, mouseY, actionX + 58, rowY + 19, 44, 14, "CANCEL", DeckUi.MUTED,
						() -> triggerConfirm("cancel:" + intent.id, () -> deck.trade().cancel(intent.id),
								"Click CANCEL again to cancel " + intent.shortId() + "."));
			}
		}

		int pageY = contentY() + contentH() - 18;
		DeckUi.text(g, font, "Page " + (tradePage + 1) + "/" + pages
				+ " · every trade send needs arm + confirm.", x, pageY, DeckUi.FAINT);
		if (pages > 1) {
			int prevX = x + w - 92;
			pageButton(g, mouseX, mouseY, prevX, pageY - 3, 40, 14, "PREV", () -> tradePage--);
			pageButton(g, mouseX, mouseY, prevX + 46, pageY - 3, 40, 14, "NEXT", () -> tradePage++);
		}
	}

	private void drawInvest(GuiGraphics g, int mouseX, int mouseY) {
		drawTitle(g, "Auto-invest (advisory)", deck.invest().settingsLine());
		int x = contentX() + 12;
		int y = contentY() + 46;
		int w = contentW() - 24;

		if (!state().hasTrustedPrices()) {
			g.fill(x, y, x + w, y + 46, 0x55211408);
			DeckUi.border(g, x, y, w, 46, DeckUi.ORANGE);
			DeckUi.text(g, font, "No trusted prices. The planner is intentionally disabled.", x + 8, y + 7, DeckUi.ORANGE);
			DeckUi.text(g, font, "Use /api for live data, or /donutdeck price <item> <value> for a price you checked.", x + 8, y + 21, DeckUi.MUTED);
			DeckUi.text(g, font, "Sample prices are never used for tickets or valuations.", x + 8, y + 33, DeckUi.FAINT);
			return;
		}

		double base = state().investableBase();
		DeckUi.label(g, font, "Target allocation", x, y);
		DeckUi.right(g, font, "base " + Money.format(base), x + w, y, DeckUi.MUTED);
		y += 13;

		int rowH = 21;
		int rows = Math.min(8, state().allocations.size());
		for (int i = 0; i < rows; i++) {
			Allocation allocation = state().allocations.get(i);
			Quote quote = state().quote(allocation.itemId);
			double currentPct = state().portfolio.pctOf(allocation.itemId, state().quotes, base);
			boolean trusted = quote != null && quote.isTrusted();
			int rowY = y + i * rowH;
			DeckUi.item(g, allocation.itemId, x + 2, rowY + 2);
			DeckUi.text(g, font, DeckUi.fit(font, quote == null ? Quote.prettify(allocation.itemId) : quote.shortName(), 160),
					x + 22, rowY + 6, trusted ? DeckUi.TEXT : DeckUi.MUTED);
			int barX = x + 205;
			int barW = Math.max(40, w - 330);
			DeckUi.bar(g, barX, rowY + 7, barW, 8, currentPct, trusted ? DeckUi.GOLD : DeckUi.FAINT);
			DeckUi.right(g, font, String.format(Locale.ROOT, "%.1f%%", currentPct), barX + barW + 44, rowY + 6, DeckUi.MUTED);
			DeckUi.right(g, font, String.format(Locale.ROOT, "→ %.0f%%", allocation.targetPct), x + w, rowY + 6,
					trusted ? DeckUi.GOLD : DeckUi.FAINT);
		}
		y += rows * rowH + 5;

		List<InvestTicket> tickets = deck.invest().plan();
		DeckUi.label(g, font, "Tickets", x, y);
		DeckUi.right(g, font, deck.invest().summary(tickets), x + w, y, DeckUi.FAINT);
		y += 13;
		if (tickets.isEmpty()) {
			DeckUi.text(g, font, "No rebalance tickets. Lower the band, change targets, or refresh prices.", x + 4, y + 10, DeckUi.MUTED);
			return;
		}
		int max = Math.min(3, tickets.size());
		for (int i = 0; i < max; i++) {
			InvestTicket ticket = tickets.get(i);
			int rowY = y + i * 28;
			g.fill(x, rowY, x + w, rowY + 25, i % 2 == 0 ? 0x33141C26 : 0x14141C26);
			DeckUi.badge(g, font, ticket.side.name(), x + 4, rowY + 6,
					ticket.side == TradeSide.BUY ? DeckUi.GREEN : DeckUi.RED);
			DeckUi.text(g, font, DeckUi.fit(font, ticket.displayName + "  " + ticket.qty + " @ "
					+ Money.format(ticket.unitPrice) + " = " + Money.format(ticket.amount), w - 100),
					x + 54, rowY + 4, DeckUi.TEXT);
			DeckUi.text(g, font, DeckUi.fit(font, ticket.reason, w - 100), x + 54, rowY + 14, DeckUi.FAINT);
			actionChip(g, mouseX, mouseY, x + w - 48, rowY + 5, 44, 15, "QUEUE", DeckUi.GOLD, () -> {
				deck.invest().queue(ticket);
				flash("Draft intent queued. Send it manually in Trade desk.");
			});
		}
		if (tickets.size() > max) {
			DeckUi.text(g, font, "+" + (tickets.size() - max) + " more ticket(s). Raise max tickets or queue these first.",
					x + 4, y + max * 28 + 2, DeckUi.FAINT);
		}
	}

	private void drawBroadcast(GuiGraphics g, int mouseX, int mouseY) {
		drawTitle(g, "Broadcast center", "Opt-in, rate-limited, and blocked by privacy mode.");
		int x = contentX() + 12;
		int y = contentY() + 44;
		int w = contentW() - 24;

		DeckUi.label(g, font, "Channel", x, y);
		y += 11;
		String[] channels = {"LOCAL", "PARTY", "PUBLIC", "DISCORD"};
		int gap = 6;
		int channelW = (w - gap * 3) / 4;
		for (int i = 0; i < channels.length; i++) {
			String channel = channels[i];
			boolean selected = channel.equals(BroadcastService.normalizeChannel(state().broadcastChannel));
			int bx = x + i * (channelW + gap);
			boolean hover = DeckUi.inside(mouseX, mouseY, bx, y, channelW, 22);
			g.fill(bx, y, bx + channelW, y + 22, selected ? 0xFF203044 : hover ? DeckUi.PANEL_HOVER : DeckUi.PANEL);
			DeckUi.border(g, bx, y, channelW, 22, selected ? DeckUi.GOLD : DeckUi.BORDER);
			DeckUi.centered(g, font, channel, bx + channelW / 2, y + 7, selected ? DeckUi.GOLD : DeckUi.MUTED);
			final String value = channel;
			zones.add(new Zone(bx, y, channelW, 22, () -> {
				if ("PUBLIC".equals(value) && !state().allowPublicBroadcast) {
					flash("Public chat is locked. Enable 'Public chat permission' below first.");
					return;
				}
				state().broadcastChannel = value;
				deck.save();
				flash("Channel set to " + BroadcastService.channelLabel(value) + ".");
			}));
		}
		y += 27;
		DeckUi.text(g, font, DeckUi.fit(font, "Active: " + BroadcastService.channelLabel(state().broadcastChannel)
				+ " · " + state().broadcastsLastHour() + "/" + state().broadcastMaxPerHour + " this hour", w),
				x, y, DeckUi.FAINT);
		y += 14;

		toggleRow(g, mouseX, mouseY, x, y, w, "Broadcasts enabled", "master switch for external channels",
				state().broadcastsEnabled, () -> {
					state().broadcastsEnabled = !state().broadcastsEnabled;
					deck.save();
				});
		y += 19;
		toggleRow(g, mouseX, mouseY, x, y, w, "Privacy mode", "forces LOCAL and blocks chat/webhook",
				state().privacyMode, () -> {
					state().privacyMode = !state().privacyMode;
					deck.save();
				});
		y += 19;
		toggleRow(g, mouseX, mouseY, x, y, w, "Auto-broadcast marked fills", "one rate-limited message per fill",
				state().autoBroadcastTrades, () -> {
					state().autoBroadcastTrades = !state().autoBroadcastTrades;
					deck.save();
				});
		y += 19;
		toggleRow(g, mouseX, mouseY, x, y, w, "Full portfolio details", "include more positions in summaries",
				state().fullSend, () -> {
					state().fullSend = !state().fullSend;
					deck.save();
				});
		y += 19;
		toggleRow(g, mouseX, mouseY, x, y, w, "Public chat permission", "required before PUBLIC can be selected",
				state().allowPublicBroadcast, () -> {
					state().allowPublicBroadcast = !state().allowPublicBroadcast;
					deck.save();
				});
		int webhookLabelY = contentY() + 203;
		DeckUi.label(g, font, "Discord webhook (optional)", x, webhookLabelY);
		DeckUi.text(g, font, DeckUi.fit(font, "official discord.com/api/webhooks only · " + state().lastWebhookStatus, w - 150),
				x + 142, webhookLabelY, DeckUi.FAINT);
		// The EditBox and buttons are added in init at fixed positions.
		int actionY = contentY() + 241;
		int actionW = (w - 8) / 2;
		actionChip(g, mouseX, mouseY, x, actionY, actionW, 22,
				confirmKey.equals("portfolio") ? "CONFIRM PORTFOLIO" : "BROADCAST PORTFOLIO", DeckUi.GOLD,
				() -> triggerConfirm("portfolio", () -> {
					deck.broadcast().broadcastPortfolio();
					flash("Broadcast attempt logged. Check the log below.");
				}, "Portfolio summary prepared. Click again to send to " + BroadcastService.channelLabel(state().broadcastChannel) + "."));
		actionChip(g, mouseX, mouseY, x + actionW + 8, actionY, actionW, 22,
				confirmKey.equals("status") ? "CONFIRM STATUS" : "BROADCAST STATUS", DeckUi.BLUE,
				() -> triggerConfirm("status", () -> {
					deck.broadcast().broadcastStatus();
					flash("Status broadcast attempt logged.");
				}, "Status prepared. Click again to send."));

		int logY = contentY() + 271;
		DeckUi.label(g, font, "Recent attempts", x, logY);
		logY += 12;
		List<BroadcastEvent> log = state().broadcastLog;
		if (log.isEmpty()) {
			DeckUi.text(g, font, "No broadcast attempts yet.", x, logY, DeckUi.FAINT);
			return;
		}
		int max = Math.min(4, log.size());
		for (int i = 0; i < max; i++) {
			BroadcastEvent event = log.get(i);
			int rowY = logY + i * 15;
			DeckUi.text(g, font, event.delivered ? "●" : "○", x, rowY,
					event.delivered ? DeckUi.GREEN : DeckUi.FAINT);
			DeckUi.text(g, font, DeckUi.fit(font, event.channel + " · " + event.message, w - 52),
					x + 12, rowY, event.delivered ? DeckUi.MUTED : DeckUi.RED);
			DeckUi.right(g, font, timeAgo(event.time), x + w, rowY, DeckUi.FAINT);
		}
	}

	private void drawCompliance(GuiGraphics g) {
		drawTitle(g, "Compliance center", "Facts, not guarantees. DonutSMP rules still apply.");
		int x = contentX() + 12;
		int y = contentY() + 44;
		int w = contentW() - 24;

		g.fill(x, y, x + w, y + 34, 0x55231008);
		DeckUi.border(g, x, y, w, 34, OpsecCompat.isLoaded() ? DeckUi.GREEN : DeckUi.ORANGE);
		DeckUi.text(g, font, "OpSec: " + OpsecCompat.summary(), x + 8, y + 6,
				OpsecCompat.isLoaded() ? DeckUi.GREEN : DeckUi.ORANGE);
		DeckUi.text(g, font, "DonutDeck has no custom payload channels, brand spoofing, packet hooks, or telemetry.",
				x + 8, y + 19, DeckUi.MUTED);
		y += 42;

		DeckUi.label(g, font, "Compliance checklist", x, y);
		y += 13;
		for (String line : OpsecCompat.checklist(state())) {
			DeckUi.text(g, font, DeckUi.fit(font, line, w - 8), x + 2, y, DeckUi.TEXT);
			y += 12;
		}

		y += 4;
		DeckUi.label(g, font, "Never present in this mod", x, y);
		y += 13;
		String[] never = {
				"Auto-accept trade requests, auto-click slots, or move items",
				"Combat, movement, aim, reach, or container automation",
				"Custom network channels, client-brand spoofing, or packet manipulation",
				"API keys or player data sent anywhere except api.donutsmp.net",
				"Background chat spam or unattended play"
		};
		for (String line : never) {
			DeckUi.text(g, font, "✕ " + DeckUi.fit(font, line, w - 18), x + 2, y, DeckUi.MUTED);
			y += 11;
		}

		y += 4;
		DeckUi.label(g, font, "Server rules reminder", x, y);
		y += 13;
		DeckUi.text(g, font, "DonutSMP bans macros, scripts, auto-clickers, off-platform trading, and unattended bots.",
				x + 2, y, DeckUi.ORANGE);
		y += 12;
		DeckUi.text(g, font, "No mod can guarantee a server will never flag it. Use this at your own risk.",
				x + 2, y, DeckUi.ORANGE);
	}

	private void drawSettings(GuiGraphics g, int mouseX, int mouseY) {
		drawTitle(g, "Settings", "Everything is stored locally in an owner-only JSON file.");
		int left = contentX() + 12;
		int right = contentX() + contentW() / 2 + 8;
		int leftW = contentW() / 2 - 24;
		int rightW = contentW() / 2 - 24;

		DeckUi.label(g, font, "API & market", left, contentY() + 46);
		DeckUi.label(g, font, "Auto-invest & risk", right, contentY() + 46);

		fieldLabel(g, "API key (from /api in game)", left, contentY() + 62, leftW);
		fieldLabel(g, "Refresh seconds (min 30)", left, contentY() + 96, leftW);
		fieldLabel(g, "Direct trade command", left, contentY() + 130, leftW);
		fieldLabel(g, "Party chat command", left, contentY() + 164, leftW);
		fieldLabel(g, "Buyer tax estimate %", left, contentY() + 198, leftW);
		fieldLabel(g, "Cash on hand (manual)", left, contentY() + 232, leftW);

		fieldLabel(g, "Cash reserve", right, contentY() + 62, rightW);
		fieldLabel(g, "Max per order", right, contentY() + 96, rightW);
		fieldLabel(g, "Min ticket size", right, contentY() + 130, rightW);
		fieldLabel(g, "Rebalance band %", right, contentY() + 164, rightW);
		fieldLabel(g, "Max tickets", right, contentY() + 198, rightW);

		int toggleY = contentY() + 270;
		toggleRow(g, mouseX, mouseY, left, toggleY, leftW, "Live API enabled", "off = manual prices only",
				state().apiEnabled, () -> {
					state().apiEnabled = !state().apiEnabled;
					deck.save();
				});
		toggleY += 20;
		toggleRow(g, mouseX, mouseY, left, toggleY, leftW, "HUD", "small net-worth panel in game",
				state().showHud, () -> {
					state().showHud = !state().showHud;
					deck.save();
				});
		toggleY += 20;
		toggleRow(g, mouseX, mouseY, left, toggleY, leftW, "Privacy mode", "blocks external broadcasts",
				state().privacyMode, () -> {
					state().privacyMode = !state().privacyMode;
					deck.save();
				});
		toggleY += 20;
		toggleRow(g, mouseX, mouseY, left, toggleY, leftW, "Show sample badges", "blank reference items, never priced",
				state().showSamplePrices, () -> {
					state().showSamplePrices = !state().showSamplePrices;
					deck.save();
				});

		int riskY = contentY() + 270;
		toggleRow(g, mouseX, mouseY, right, riskY, rightW, "Allow sells", "planner may propose sells",
				state().allowSells, () -> {
					state().allowSells = !state().allowSells;
					deck.save();
				});
		riskY += 20;
		toggleRow(g, mouseX, mouseY, right, riskY, rightW, "Advisory auto-plan", "periodic internal plan, never executes",
				state().autoInvestAdvisory, () -> {
					state().autoInvestAdvisory = !state().autoInvestAdvisory;
					deck.save();
				});
		riskY += 24;
		DeckUi.text(g, font, "Plan reserve: " + Money.format(state().reserveCash), right, riskY, DeckUi.MUTED);
		DeckUi.text(g, font, "Buyer tax: " + String.format(Locale.ROOT, "%.1f%%", state().buyerTaxPct),
				right, riskY + 12, DeckUi.MUTED);
		DeckUi.text(g, font, "Config: " + shortPath(deck.store().displayPath()), right, riskY + 24, DeckUi.FAINT);

		int noteY = contentY() + contentH() - 48;
		DeckUi.text(g, font, "Saving never contacts a server. API calls happen only on market refresh.",
				left, noteY, DeckUi.FAINT);
		DeckUi.text(g, font, "Manual: /donutdeck price <item> <value>  ·  /donutdeck cash <value>  (example: /donutdeck cash 25m)",
				left, noteY + 11, DeckUi.FAINT);
	}

	private void drawTitle(GuiGraphics g, String title, String subtitle) {
		DeckUi.textShadow(g, font, title, contentX() + 12, contentY() + 12, DeckUi.TEXT);
		DeckUi.text(g, font, DeckUi.fit(font, subtitle, contentW() - 190),
				contentX() + 12, contentY() + 27, DeckUi.MUTED);
	}

	private void fieldLabel(GuiGraphics g, String label, int x, int y, int w) {
		DeckUi.text(g, font, DeckUi.fit(font, label, w), x, y, DeckUi.FAINT);
	}

	private void toggleRow(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w,
			String label, String hint, boolean on, Runnable action) {
		boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, 18);
		g.fill(x, y, x + w, y + 18, hover ? DeckUi.PANEL_HOVER : 0x33141C26);
		DeckUi.text(g, font, DeckUi.fit(font, label, w - 70), x + 4, y + 2, on ? DeckUi.TEXT : DeckUi.MUTED);
		DeckUi.text(g, font, DeckUi.fit(font, hint, w - 70), x + 4, y + 11, DeckUi.FAINT);
		DeckUi.badge(g, font, on ? "ON" : "OFF", x + w - 36, y + 3, on ? DeckUi.GREEN : DeckUi.FAINT);
		zones.add(new Zone(x, y, w, 18, action));
	}

	private void actionChip(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
			String label, int color, Runnable action) {
		boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, h);
		g.fill(x, y, x + w, y + h, hover ? DeckUi.PANEL_HOVER : 0xCC111923);
		DeckUi.border(g, x, y, w, h, hover ? color : DeckUi.BORDER);
		DeckUi.centered(g, font, label, x + w / 2, y + (h - 9) / 2, hover ? color : DeckUi.MUTED);
		zones.add(new Zone(x, y, w, h, action));
	}

	private void pageButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
			String label, Runnable action) {
		boolean hover = DeckUi.inside(mouseX, mouseY, x, y, w, h);
		g.fill(x, y, x + w, y + h, hover ? DeckUi.PANEL_HOVER : DeckUi.PANEL_ALT);
		DeckUi.border(g, x, y, w, h, DeckUi.BORDER);
		DeckUi.centered(g, font, label, x + w / 2, y + 3, hover ? DeckUi.GOLD : DeckUi.MUTED);
		zones.add(new Zone(x, y, w, h, action));
	}

	private void triggerConfirm(String key, Runnable action, String prompt) {
		long now = System.currentTimeMillis();
		if (key.equals(confirmKey) && now < confirmUntil) {
			confirmKey = "";
			confirmUntil = 0;
			action.run();
			return;
		}
		confirmKey = key;
		confirmUntil = now + 6_000L;
		flash(prompt);
	}

	private void flash(String message) {
		this.notice = message == null ? "" : message;
		this.noticeUntil = System.currentTimeMillis() + 6_000L;
	}

	private void addButton(String label, int x, int y, int w, int h, Runnable action) {
		addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
				.bounds(x, y, w, h).build());
	}

	private EditBox addField(int x, int y, int w, int h, String value, int maxLength, String hint) {
		EditBox box = new EditBox(this.font, x, y, w, h, Component.empty());
		box.setMaxLength(maxLength);
		box.setValue(value == null ? "" : value);
		box.setHint(Component.literal(hint == null ? "" : hint));
		return addRenderableWidget(box);
	}

	private void collectAllFields() {
		applySettings();
		collectBroadcastFields();
	}

	private void applySettings() {
		if (apiKeyBox == null) {
			return;
		}
		DeckState s = state();
		s.apiKey = apiKeyBox.getValue().trim();
		s.refreshSeconds = (int) clamp(parse(refreshBox, s.refreshSeconds), 30, 3600);
		s.tradeCommand = fallback(tradeCommandBox.getValue(), "trade");
		s.partyCommand = fallback(partyCommandBox.getValue(), "p");
		s.buyerTaxPct = clamp(parse(taxBox, s.buyerTaxPct), 0, 25);
		s.portfolio.cash = Math.max(0, parse(cashBox, s.portfolio.cash));
		s.reserveCash = Math.max(0, parse(reserveBox, s.reserveCash));
		s.maxPerOrder = Math.max(0, parse(maxOrderBox, s.maxPerOrder));
		s.minTicket = Math.max(0, parse(minTicketBox, s.minTicket));
		s.rebalanceBandPct = Math.max(0, parse(bandBox, s.rebalanceBandPct));
		s.maxTickets = (int) clamp(parse(maxTicketsBox, s.maxTickets), 1, 16);
		s.ensureDefaults();
	}

	private void collectBroadcastFields() {
		if (webhookBox != null) {
			state().webhookUrl = webhookBox.getValue().trim();
		}
	}

	private double parse(EditBox box, double fallback) {
		if (box == null) {
			return fallback;
		}
		double value = Money.parse(box.getValue());
		return Double.isFinite(value) ? value : fallback;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String fallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private void openComposer(String itemId, String counterparty) {
		collectAllFields();
		deck.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(new TradeComposerScreen(deck, itemId, counterparty));
		}
	}

	private void openManualPrice(String itemId) {
		collectAllFields();
		deck.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(new ManualPriceScreen(deck, itemId));
		}
	}

	private void openBots() {
		collectAllFields();
		deck.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(new BotsScreen(deck));
		}
	}

	private int navWidth() {
		return width < 700 ? 92 : 108;
	}

	private int contentX() {
		return 10 + navWidth() + 10;
	}

	private int contentY() {
		return 64;
	}

	private int contentW() {
		return width - contentX() - 10;
	}

	private int contentH() {
		return height - contentY() - 24;
	}

	private int intentColor(TradeIntent intent) {
		return switch (intent.status) {
			case DRAFT -> DeckUi.GOLD;
			case SENT -> DeckUi.BLUE;
			case FILLED -> DeckUi.GREEN;
			case CANCELLED -> DeckUi.MUTED;
			case EXPIRED -> DeckUi.ORANGE;
		};
	}

	private static long previousMilestone(long next) {
		if (next <= 1_000_000L) {
			return 0L;
		}
		if (next <= 10_000_000L) {
			return 1_000_000L;
		}
		if (next <= 100_000_000L) {
			return 10_000_000L;
		}
		if (next <= 1_000_000_000L) {
			return 100_000_000L;
		}
		if (next <= 10_000_000_000L) {
			return 1_000_000_000L;
		}
		if (next <= 100_000_000_000L) {
			return 10_000_000_000L;
		}
		if (next <= 1_000_000_000_000L) {
			return 100_000_000_000L;
		}
		return 1_000_000_000_000L;
	}

	private static String timeAgo(long timestamp) {
		if (timestamp <= 0) {
			return "never";
		}
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

	private static String shortPath(String path) {
		if (path == null) {
			return "";
		}
		String home = System.getProperty("user.home");
		if (home != null && path.startsWith(home)) {
			return "~" + path.substring(home.length());
		}
		return path;
	}
}
