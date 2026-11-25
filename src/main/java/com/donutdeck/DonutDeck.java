package com.donutdeck;

import com.donutdeck.core.InvestTicket;
import com.donutdeck.ui.DonutDeckScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Service locator and client lifecycle owner. One instance exists per game
 * session. All state is local; there is no server-side component.
 */
public final class DonutDeck {
	private final ConfigStore store;
	private final DeckState state;
	private final ActionGuard guard;
	private final MarketService market;
	private final TradeService trade;
	private final InvestService invest;
	private final BroadcastService broadcast;

	private long lastSaveAt;
	private long lastExpireAt;
	private boolean shuttingDown;

	public DonutDeck() {
		this.store = new ConfigStore();
		this.state = store.load();
		this.guard = new ActionGuard();
		this.market = new MarketService(state, store);
		this.trade = new TradeService(state, guard, this::notify, this::save);
		this.invest = new InvestService(state, trade, this::save);
		this.broadcast = new BroadcastService(state, this::save, this::notify);
		this.trade.setBroadcast(broadcast);
		this.lastSaveAt = System.currentTimeMillis();
	}

	public DeckState state() {
		return state;
	}

	public ConfigStore store() {
		return store;
	}

	public ActionGuard guard() {
		return guard;
	}

	public MarketService market() {
		return market;
	}

	public TradeService trade() {
		return trade;
	}

	public InvestService invest() {
		return invest;
	}

	public BroadcastService broadcast() {
		return broadcast;
	}

	public void tick() {
		long now = System.currentTimeMillis();
		market.tick();

		if (now - lastExpireAt > 60_000L) {
			lastExpireAt = now;
			state.expireIntents();
			if (state.autoInvestAdvisory && now - state.lastInvestRun
					> Math.max(5, state.autoInvestMinutes) * 60_000L) {
				state.lastInvestRun = now;
				List<InvestTicket> tickets = invest.plan();
				if (!tickets.isEmpty()) {
					state.recordActivity("◎", "Advisory plan ready: " + tickets.size()
							+ " rebalance ticket" + (tickets.size() == 1 ? "" : "s"));
					broadcast.onInvestPlan(tickets);
					save();
				}
			}
		}

		if (now - lastSaveAt > 300_000L) {
			save();
		}
	}

	public void onGameMessage(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		trade.onChat(text);
	}

	public void openScreen() {
		openScreen(DonutDeckScreen.Tab.DASHBOARD);
	}

	public void openScreen(DonutDeckScreen.Tab tab) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof DonutDeckScreen screen) {
			screen.setTab(tab);
			return;
		}
		minecraft.setScreen(new DonutDeckScreen(this, tab));
	}

	public void notify(String message) {
		if (message == null || message.isBlank()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.literal(message), false);
		}
	}

	public void save() {
		if (shuttingDown) {
			return;
		}
		store.save(state);
		lastSaveAt = System.currentTimeMillis();
	}

	public void shutdown() {
		shuttingDown = true;
		store.save(state);
	}

	public boolean isInGame() {
		return trade.isInGame();
	}
}
