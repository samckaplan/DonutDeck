package com.donutsmp.deck;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DonutDeck - A high-quality Fabric client mod for DonutSMP.
 * 
 * Features:
 * - Passive price observation from in-game /ah and /order screens
 * - Continuous market analysis with automatic averaging and trend detection
 * - Auto-invest advisory engine with one-click ticket execution
 * - Broadcast center for external destinations (opt-in, privacy-gated)
 * - OpSec-safe by design: no telemetry, manual confirmations required
 */
@Environment(EnvType.CLIENT)
public class DonutDeckMod implements ClientModInitializer {

    public static final String MOD_ID = "donutdeck";
    public static final Logger LOGGER = LoggerFactory.getLogger(DonutDeckMod.class);

    private static DonutDeckMod instance;

    @Override
    public void onInitializeClient() {
        instance = this;
        
        // Initialize core systems in order of dependency
        MarketAPI.initialize();
        TradeDesk.initialize();
        BotManager.initialize();
        AutoInvestAdvisor.initialize();
        GameObserver.initialize();  // Passive price observer starts here
        BroadcastCenter.initialize();  // Sets up broadcast sessions
        OpSecGuard.initialize();

        // Start continuous broadcasting loop
        BroadcastLoop.start();

        LOGGER.info("=== DonutDeck v1.0.0 initialized ===");
        LOGGER.info("Target: Minecraft 1.21.11 | Fabric API 0.141.6+");
        LOGGER.info("Mode: PASSIVE_OBSERVER + CONTINUOUS_BROADCAST");
        
        // Register keybindings and UI hooks via event bus (handled by subsystems)
    }

    public static DonutDeckMod getInstance() {
        return instance;
    }

    /**
     * Get the current Minecraft client safely.
     */
    public static MinecraftClient getClient() {
        return MinecraftClient.getInstance();
    }

    /**
     * Check if OpSec privacy mode is active.
     */
    public static boolean isOpSecActive() {
        return OpSecGuard.isPrivacyModeEnabled();
    }

    /**
     * Log a message only if not in strict OpSec mode (reduces telemetry).
     */
    public static void logIfNotStrict(String category, String message) {
        if (!OpSecGuard.isStrictMode()) {
            LOGGER.debug("[DonutDeck] {}: {}", category, message);
        }
    }
}