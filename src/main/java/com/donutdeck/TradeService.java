package com.donutdeck;

import com.donutdeck.core.BotProfile;
import com.donutdeck.core.ChatParser;
import com.donutdeck.core.IntentStatus;
import com.donutdeck.core.Quote;
import com.donutdeck.core.TradeIntent;
import com.donutdeck.core.TradeRoute;
import com.donutdeck.core.TradeSide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manual-confirm trade desk. The only outbound actions are one /trade, /ah or
 * /order command after the player clicks twice (arm + send), or a single /bal
 * sync. It never accepts a trade, clicks a slot, moves an item, or sends chat.
 */
public final class TradeService {
	private final DeckState state;
	private final ActionGuard guard;
	private final Consumer<String> notifier;
	private final Runnable save;
	private BroadcastService broadcast;

	public TradeService(DeckState state, ActionGuard guard, Consumer<String> notifier, Runnable save) {
		this.state = state;
		this.guard = guard;
		this.notifier = notifier;
		this.save = save;
	}

	public void setBroadcast(BroadcastService broadcast) {
		this.broadcast = broadcast;
	}

	public TradeIntent createIntent(String counterparty, TradeSide side, TradeRoute route, String itemId,
			int qty, double unitPrice, String note) {
		if (counterparty == null || counterparty.isBlank()) {
			notifier.accept("§cPick a counterparty (bot or player name).");
			return null;
		}
		if (qty <= 0) {
			notifier.accept("§cQuantity must be at least 1.");
			return null;
		}
		if (!Double.isFinite(unitPrice) || unitPrice <= 0) {
			notifier.accept("§cUnit price must be a positive number.");
			return null;
		}
		String id = Quote.normalizeId(itemId);
		if (id.isBlank()) {
			notifier.accept("§cPick an item first.");
			return null;
		}
		Quote quote = state.quote(id);
		TradeIntent intent = new TradeIntent();
		intent.id = UUID.randomUUID().toString().substring(0, 8);
		intent.counterparty = counterparty.trim();
		intent.side = side == null ? TradeSide.BUY : side;
		intent.route = route == null ? TradeRoute.PLAYER : route;
		intent.itemId = id;
		intent.displayName = quote == null ? Quote.prettify(id) : quote.shortName();
		intent.qty = Math.min(qty, 10_000_000);
		intent.unitPrice = unitPrice;
		intent.note = note == null ? "" : note.trim();
		intent.status = IntentStatus.DRAFT;
		intent.bot = state.bot(intent.counterparty) != null;
		intent.createdAt = System.currentTimeMillis();
		intent.updatedAt = intent.createdAt;
		state.intents.add(0, intent);
		state.clearPending(intent.counterparty);
		state.recordActivity(intent.side == TradeSide.BUY ? "↗" : "↘",
				"Prepared " + intent.summary() + " · " + intent.counterparty);
		save.run();
		notifier.accept("§aIntent created: §f" + intent.summary() + " §7with §f" + intent.counterparty);
		return intent;
	}

	public long arm(String id) {
		guard.arm(id);
		return guard.armedFor(id);
	}

	public boolean send(String id) {
		TradeIntent intent = find(id);
		if (intent == null) {
			notifier.accept("§cThat trade intent no longer exists.");
			return false;
		}
		if (!intent.isOpen()) {
			notifier.accept("§eIntent is already " + intent.status.name().toLowerCase(Locale.ROOT) + ".");
			return false;
		}
		if (intent.status == IntentStatus.SENT) {
			notifier.accept("§eAlready sent. Mark it filled or cancel it in the Trade tab.");
			return false;
		}
		if (!guard.isArmed(id)) {
			long armed = arm(id);
			notifier.accept("§eArmed for " + (armed / 1000) + "s. Click §lSEND§r§e again to confirm.");
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.connection == null) {
			notifier.accept("§cJoin DonutSMP first.");
			return false;
		}
		String command = commandFor(intent);
		if (!guard.tryCommand(command)) {
			notifier.accept("§eRate limited. Wait " + (guard.millisUntilReady() + 999) / 1000 + "s and click again.");
			return false;
		}
		guard.clearArm(id);
		player.connection.sendCommand(command);
		intent.status = IntentStatus.SENT;
		intent.updatedAt = System.currentTimeMillis();
		state.clearPending(intent.counterparty);
		state.recordActivity("➤", "Sent " + intent.route.name().toLowerCase(Locale.ROOT) + " action for "
				+ intent.summary());
		save.run();
		if (intent.route == TradeRoute.PLAYER) {
			notifier.accept("§aTrade request sent to §f" + intent.counterparty
					+ "§a. Complete the exchange manually — DonutDeck never moves items.");
		} else {
			notifier.accept("§aOpened " + command + "§a. Buy/sell manually in the server menu.");
		}
		return true;
	}

	public void fill(String id) {
		TradeIntent intent = find(id);
		if (intent == null || !intent.isOpen()) {
			return;
		}
		double realized = 0;
		if (intent.side == TradeSide.BUY) {
			state.portfolio.applyBuy(intent.itemId, intent.qty, intent.unitPrice);
		} else {
			realized = state.portfolio.applySell(intent.itemId, intent.qty, intent.unitPrice);
		}
		intent.status = IntentStatus.FILLED;
		intent.updatedAt = System.currentTimeMillis();
		BotProfile bot = state.bot(intent.counterparty);
		if (bot != null) {
			bot.trades++;
			bot.volume += intent.total();
			bot.lastTradeAt = intent.updatedAt;
		}
		state.recordActivity("✓", "Filled " + intent.summary() + " · " + intent.counterparty
				+ (intent.side == TradeSide.SELL ? " · P/L " + com.donutdeck.core.Money.format(realized) : ""));
		notifier.accept("§aMarked filled: §f" + intent.summary()
				+ (intent.side == TradeSide.SELL ? " §7(P/L " + com.donutdeck.core.Money.format(realized) + ")" : ""));
		if (broadcast != null) {
			broadcast.onTradeFilled(intent, realized);
		}
		save.run();
	}

	public void cancel(String id) {
		TradeIntent intent = find(id);
		if (intent == null || !intent.isOpen()) {
			return;
		}
		intent.status = IntentStatus.CANCELLED;
		intent.updatedAt = System.currentTimeMillis();
		state.recordActivity("×", "Cancelled " + intent.summary());
		save.run();
	}

	public TradeIntent find(String id) {
		for (TradeIntent intent : state.intents) {
			if (intent != null && id != null && id.equals(intent.id)) {
				return intent;
			}
		}
		return null;
	}

	public String commandFor(TradeIntent intent) {
		if (intent.route == TradeRoute.PLAYER) {
			return stripSlash(state.tradeCommand) + " " + intent.counterparty;
		}
		if (intent.route == TradeRoute.ORDER) {
			return "order " + searchQuery(intent.itemId);
		}
		return "ah " + searchQuery(intent.itemId);
	}

	public String previewCommand(TradeIntent intent) {
		return "/" + commandFor(intent);
	}

	public void openAuction(String itemId) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.connection == null) {
			notifier.accept("§cJoin DonutSMP first.");
			return;
		}
		String command = "ah " + searchQuery(itemId);
		if (!guard.tryCommand(command)) {
			notifier.accept("§eRate limited. Try again in a moment.");
			return;
		}
		player.connection.sendCommand(command);
		state.recordActivity("⌕", "Opened AH search: " + Quote.prettify(itemId));
		notifier.accept("§aOpened §f/" + command + "§a. Complete the purchase manually.");
	}

	public void syncBalance() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.connection == null) {
			notifier.accept("§cJoin DonutSMP first.");
			return;
		}
		if (!guard.tryCommand("bal")) {
			notifier.accept("§eRate limited. Try again in a moment.");
			return;
		}
		player.connection.sendCommand("bal");
		notifier.accept("§7Requested /bal. Your balance will sync when the server replies.");
	}

	/** Read-only chat observer. Never replies or accepts anything. */
	public void onChat(String raw) {
		String line = ChatParser.clean(raw);
		if (line.isEmpty()) {
			return;
		}

		OptionalDouble balance = ChatParser.parseBalance(line);
		if (balance.isPresent()) {
			double value = balance.getAsDouble();
			if (Math.abs(value - state.portfolio.cash) > 0.01) {
				state.portfolio.cash = value;
				state.recordActivity("$", "Balance synced: " + com.donutdeck.core.Money.format(value));
				save.run();
			}
			return;
		}

		// Cash is only changed by an explicit "mark filled" action or an exact /bal
		// reply. Parsing every payment/sale line would double-count auction cash.
		Optional<String> request = ChatParser.parseTradeRequest(line);
		if (request.isPresent()) {
			String player = request.get();
			if (Minecraft.getInstance().player != null
					&& player.equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) {
				return;
			}
			state.addPendingRequest(player, line);
			state.recordActivity("!", "Trade request from " + player + " — manual review");
			notifier.accept("§d" + player + "§f wants to trade. §7Open DonutDeck → Trade. Nothing is auto-accepted.");
			save.run();
		}
	}

	public List<TradeIntent> intents() {
		return state.intents;
	}

	public boolean isInGame() {
		return Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null;
	}

	private static String searchQuery(String itemId) {
		return Quote.prettify(Quote.normalizeId(itemId));
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
