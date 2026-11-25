package com.donutdeck;

import com.donutdeck.core.Money;
import com.donutdeck.core.Quote;
import com.donutdeck.ui.DonutDeckScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** Client-side commands. They only run when the player explicitly types them. */
public final class DeckCommands {
	private DeckCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommandManager.literal("donutdeck")
				.executes(context -> {
					DonutDeck deck = DonutDeckClient.deck();
					context.getSource().getClient().execute(deck::openScreen);
					return 1;
				})
				.then(ClientCommandManager.literal("market")
						.executes(context -> {
							DonutDeck deck = DonutDeckClient.deck();
							deck.market().refresh(true);
							feedback(context.getSource(), "§aMarket refresh requested. Check Dashboard for status.");
							return 1;
						}))
				.then(ClientCommandManager.literal("balance")
						.executes(context -> {
							DonutDeckClient.deck().trade().syncBalance();
							return 1;
						})
						.then(ClientCommandManager.argument("value", StringArgumentType.string())
								.executes(context -> {
									String value = StringArgumentType.getString(context, "value");
									String result = setCash(DonutDeckClient.deck(), value);
									feedback(context.getSource(), result);
									return 1;
								})))
				.then(ClientCommandManager.literal("cash")
						.then(ClientCommandManager.argument("value", StringArgumentType.string())
								.executes(context -> {
									String value = StringArgumentType.getString(context, "value");
									String result = setCash(DonutDeckClient.deck(), value);
									feedback(context.getSource(), result);
									return 1;
								})))
				.then(ClientCommandManager.literal("invest")
						.executes(context -> {
							DonutDeck deck = DonutDeckClient.deck();
							context.getSource().getClient().execute(() ->
									context.getSource().sendFeedback(deck.invest().planSummaryComponent()));
							return 1;
						}))
				.then(ClientCommandManager.literal("broadcast")
						.executes(context -> {
							feedback(context.getSource(), "§e/donutdeck broadcast status §7or §e/donutdeck broadcast portfolio");
							return 1;
						})
						.then(ClientCommandManager.literal("status")
								.executes(context -> {
									DonutDeckClient.deck().broadcast().broadcastStatus();
									return 1;
								}))
						.then(ClientCommandManager.literal("portfolio")
								.executes(context -> {
									DonutDeckClient.deck().broadcast().broadcastPortfolio();
									return 1;
								})))
				.then(ClientCommandManager.literal("price")
						.then(ClientCommandManager.argument("item", StringArgumentType.string())
								.then(ClientCommandManager.argument("value", StringArgumentType.string())
										.executes(context -> {
											String item = StringArgumentType.getString(context, "item");
											String value = StringArgumentType.getString(context, "value");
											String result = setManualPrice(DonutDeckClient.deck(), item, value);
											feedback(context.getSource(), result);
											return 1;
										}))))
				.then(ClientCommandManager.literal("compliance")
						.executes(context -> {
							DonutDeckClient.deck().openScreen(DonutDeckScreen.Tab.COMPLIANCE);
							return 1;
						}))
				.then(ClientCommandManager.literal("bots")
						.executes(context -> {
							context.getSource().getClient().execute(() ->
									context.getSource().getClient().setScreen(new com.donutdeck.ui.BotsScreen(DonutDeckClient.deck())));
							return 1;
						}))
				.then(ClientCommandManager.literal("hud")
						.executes(context -> {
							DonutDeck deck = DonutDeckClient.deck();
							deck.state().showHud = !deck.state().showHud;
							deck.save();
							feedback(context.getSource(), "§aHUD " + (deck.state().showHud ? "on" : "off") + ".");
							return 1;
						}))
				.then(ClientCommandManager.literal("status")
						.executes(context -> {
							feedback(context.getSource(), "§6" + DonutDeckClient.deck().broadcast().statusMessage());
							return 1;
						}))
				.then(ClientCommandManager.literal("help")
						.executes(context -> {
							feedback(context.getSource(), "§6DonutDeck §7· /donutdeck · market · balance [value] · cash <value> · invest · price <item> <value> · broadcast status|portfolio · compliance · bots · hud");
							return 1;
						})));
	}

	public static String setManualPrice(DonutDeck deck, String rawItem, String rawValue) {
		String id = Quote.normalizeId(rawItem);
		double value = Money.parse(rawValue);
		if (id.isBlank()) {
			return "§cItem id required. Example: /donutdeck price netherite_ingot 20m";
		}
		if (!Double.isFinite(value) || value <= 0) {
			return "§cPrice must be positive. Examples: 250k, 20m, 1.2b";
		}
		Quote quote = deck.state().quote(id);
		if (quote == null) {
			quote = new Quote();
			quote.itemId = id;
			quote.displayName = Quote.prettify(id);
			deck.state().quotes.put(id, quote);
		}
		quote.price = value;
		quote.lowAsk = value;
		quote.medianSold = value;
		quote.source = "MANUAL";
		quote.lowConfidence = false;
		quote.updatedAt = System.currentTimeMillis();
		quote.changePct = 0;
		deck.state().marketStatus = deck.state().marketStatus == null ? "NO_DATA" : deck.state().marketStatus;
		deck.state().recordActivity("✎", "Manual price set: " + quote.shortName() + " = " + Money.format(value));
		deck.save();
		return "§aManual price saved: §f" + quote.shortName() + " §7= §f" + Money.formatFull(value)
				+ "§7. It is used for planning until live API data replaces it.";
	}

	public static String setCash(DonutDeck deck, String rawValue) {
		double value = Money.parse(rawValue);
		if (!Double.isFinite(value) || value < 0) {
			return "§cCash must be 0 or higher. Examples: /donutdeck cash 25m, /donutdeck balance 1.2b";
		}
		deck.state().portfolio.cash = value;
		deck.state().recordActivity("$", "Cash set manually: " + Money.format(value));
		deck.save();
		return "§aCash set to §f" + Money.formatFull(value) + "§7. Use /bal sync only if chat parsing works on your server.";
	}

	private static void feedback(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal(message));
	}
}
