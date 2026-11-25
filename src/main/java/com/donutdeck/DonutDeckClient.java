package com.donutdeck;

import com.donutdeck.ui.DonutDeckScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DonutDeckClient implements ClientModInitializer {
	public static final String MOD_ID = "donutdeck";
	public static final Logger LOGGER = LoggerFactory.getLogger("DonutDeck");

	private static DonutDeck deck;
	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		deck = new DonutDeck();
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.donutdeck.open",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_J,
				KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				deck.openScreen(DonutDeckScreen.Tab.DASHBOARD);
			}
			deck.tick();
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				deck.onGameMessage(message.getString());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				DeckCommands.register(dispatcher));

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), new DeckHud(deck));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> deck.shutdown());

		LOGGER.info("DonutDeck ready · Minecraft 1.21.11 · manual-confirm trading · {}",
				OpsecCompat.summary());
	}

	public static DonutDeck deck() {
		if (deck == null) {
			throw new IllegalStateException("DonutDeck has not initialised yet");
		}
		return deck;
	}
}
