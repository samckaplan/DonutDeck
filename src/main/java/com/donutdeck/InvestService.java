package com.donutdeck;

import com.donutdeck.core.InvestEngine;
import com.donutdeck.core.InvestTicket;
import com.donutdeck.core.Money;
import com.donutdeck.core.TradeIntent;
import com.donutdeck.core.TradeRoute;
import com.donutdeck.core.TradeSide;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Advisory rebalancing. Nothing here executes a trade: it only produces a plan
 * and, when the player clicks, turns one line into a draft trade intent.
 */
public final class InvestService {
	private final DeckState state;
	private final TradeService trade;
	private final Runnable save;

	private List<InvestTicket> lastPlan = new ArrayList<>();
	private long lastPlanAt;

	public InvestService(DeckState state, TradeService trade, Runnable save) {
		this.state = state;
		this.trade = trade;
		this.save = save;
	}

	public List<InvestTicket> plan() {
		InvestEngine.Settings settings = new InvestEngine.Settings();
		settings.reserveCash = state.reserveCash;
		settings.maxPerOrder = state.maxPerOrder;
		settings.minTicket = state.minTicket;
		settings.rebalanceBandPct = state.rebalanceBandPct;
		settings.allowSells = state.allowSells;
		settings.maxTickets = state.maxTickets;
		lastPlan = InvestEngine.plan(state.portfolio, state.quotes, state.allocations, settings);
		lastPlanAt = System.currentTimeMillis();
		return lastPlan;
	}

	public List<InvestTicket> lastPlan() {
		if (lastPlan.isEmpty()) {
			return plan();
		}
		return lastPlan;
	}

	public long lastPlanAt() {
		return lastPlanAt;
	}

	public TradeIntent queue(InvestTicket ticket) {
		if (ticket == null) {
			return null;
		}
		TradeRoute route = ticket.side == TradeSide.SELL ? TradeRoute.ORDER : TradeRoute.AUCTION;
		String counterparty = route == TradeRoute.ORDER ? "Orders" : "Auction House";
		String note = "Invest plan: " + ticket.reason;
		TradeIntent intent = trade.createIntent(counterparty, ticket.side, route, ticket.itemId,
				ticket.qty, ticket.unitPrice, note);
		if (intent != null) {
			state.recordActivity("◎", "Queued invest ticket: " + ticket.summary());
			save.run();
		}
		return intent;
	}

	public int queueAll() {
		int queued = 0;
		for (InvestTicket ticket : plan()) {
			if (queue(ticket) != null) {
				queued++;
			}
		}
		return queued;
	}

	public String settingsLine() {
		return "Reserve " + Money.format(state.reserveCash)
				+ " · max/order " + Money.format(state.maxPerOrder)
				+ " · band " + String.format(Locale.ROOT, "%.1f%%", state.rebalanceBandPct)
				+ (state.allowSells ? " · sells on" : " · buys only");
	}

	public String summary() {
		return summary(lastPlan());
	}

	public String summary(List<InvestTicket> tickets) {
		int buys = 0;
		int sells = 0;
		double total = 0;
		for (InvestTicket ticket : tickets) {
			if (ticket.side == TradeSide.BUY) {
				buys++;
			} else {
				sells++;
			}
			total += ticket.amount;
		}
		return buys + " buys / " + sells + " sells · " + Money.format(total);
	}

	public Component planSummaryComponent() {
		List<InvestTicket> tickets = lastPlan();
		if (tickets.isEmpty()) {
			return Component.literal("§eNo rebalance tickets. Add live/manual prices or adjust targets.");
		}
		return Component.literal("§aInvest plan: §f" + summary()
				+ "§7. Review each ticket in the Invest tab.");
	}

	public boolean hasTrustedPrices() {
		return state.hasTrustedPrices();
	}
}
