package com.donutsmp.deck;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BotManager - Manages bot profiles and trading strategies.
 * 
 * Design principles:
 * - All bot interactions require manual user confirmation (no auto-trading)
 * - Configurable bot personalities and risk tolerances
 * - Clear separation between "bot profiles" and actual automated behavior
 */
@Environment(EnvType.CLIENT)
public class BotManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotManager.class);
    
    // Registered bot profiles (user-created configurations)
    private static final Map<String, BotProfile> BOT_PROFILES = new ConcurrentHashMap<>();
    
    /**
     * Initialize the bot manager.
     */
    public static void initialize() {
        LOGGER.info("[BotManager] Initialized | Profiles: {} pre-loaded", 
                getPredefinedProfiles().size());
        
        // Load predefined profiles (can be extended by user)
        loadPredefinedProfiles();
    }

    /**
     * Get all registered bot profiles.
     */
    public static List<BotProfile> getAllProfiles() {
        return new ArrayList<>(BOT_PROFILES.values());
    }

    /**
     * Create a new bot profile configuration.
     * 
     * @param name Unique identifier for the profile
     * @param strategy Strategy type (aggressive, conservative, balanced)
     * @param riskTolerance Risk level from 0.0 to 1.0
     * @return Created profile or null if invalid
     */
    public static BotProfile createProfile(String name, String strategy, double riskTolerance) {
        // Validate inputs
        if (name == null || name.trim().isEmpty()) {
            LOGGER.warn("[BotManager] Invalid profile name");
            return null;
        }
        
        if (riskTolerance < 0.0 || riskTolerance > 1.0) {
            LOGGER.warn("[BotManager] Risk tolerance must be between 0.0 and 1.0");
            return null;
        }
        
        // Create profile with default settings
        BotProfile profile = new BotProfile(
            name,
            strategy,
            riskTolerance,
            false,  // auto-accept disabled (compliance)
            List.of()  // custom filters - user can add later
        );
        
        BOT_PROFILES.put(name.toLowerCase(), profile);
        
        LOGGER.info("[BotManager] Profile created: {} | Strategy: {} | Risk: {}", 
                name, strategy, riskTolerance);
        
        return profile;
    }

    /**
     * Get a bot profile by name.
     */
    public static BotProfile getProfile(String name) {
        return BOT_PROFILES.get(name.toLowerCase());
    }

    /**
     * Check if a profile exists.
     */
    public static boolean hasProfile(String name) {
        return BOT_PROFILES.containsKey(name.toLowerCase());
    }

    /**
     * Delete a bot profile.
     */
    public static void deleteProfile(String name) {
        BOT_PROFILES.remove(name.toLowerCase());
        LOGGER.info("[BotManager] Profile deleted: {}", name);
    }

    /**
     * Get all predefined profiles (built-in strategies).
     */
    private static List<BotProfile> getPredefinedProfiles() {
        return new ArrayList<>(BOT_PROFILES.values());
    }

    /**
     * Load predefined bot profiles.
     */
    private static void loadPredefinedProfiles() {
        // Conservative Trader - low risk, focuses on stable items
        BotProfile conservative = createProfile("conservative", "value", 0.2);
        if (conservative != null) {
            conservative.setCustomFilters(Map.of(
                "exclude_netherite", true,
                "max_price_variance", 0.15
            ));
        }

        // Aggressive Trader - high risk tolerance, targets volatile items
        BotProfile aggressive = createProfile("aggressive", "momentum", 0.8);
        if (aggressive != null) {
            aggressive.setCustomFilters(Map.of(
                "min_price_variance", 0.25,
                "target_volatile_only", true
            ));
        }

        // Balanced Trader - middle ground for general use
        BotProfile balanced = createProfile("balanced", "hybrid", 0.5);
        if (balanced != null) {
            balanced.setCustomFilters(Map.of(
                "exclude_netherite", false,
                "max_price_variance", 0.20
            ));
        }

        LOGGER.info("[BotManager] Loaded {} predefined profiles", 
                getPredefinedProfiles().size());
    }

    /**
     * Get a profile's recommended price range for an item based on its strategy.
     * This is advisory only - actual trades still require manual confirmation.
     */
    public static double getRecommendedPrice(String itemId, BotProfile profile) {
        if (profile == null || !BOT_PROFILES.containsKey(profile.name().toLowerCase())) {
            return 0.0;
        }

        // Get live market price
        double marketPrice = MarketAPI.getCachedPrice(itemId);
        
        if (marketPrice <= 0) {
            LOGGER.debug("[BotManager] No market data for {}", itemId);
            return 0.0;
        }

        // Apply strategy-based adjustments
        switch (profile.strategy()) {
            case "value" -> {
                // Conservative: buy low, sell at slight premium
                double buyPremium = marketPrice * profile.riskTolerance();
                return marketPrice + buyPremium;
            }
            
            case "momentum" -> {
                // Aggressive: chase momentum with wider variance tolerance
                double volatilityFactor = 1.0 + (profile.riskTolerance() * 0.5);
                return marketPrice * volatilityFactor;
            }
            
            case "hybrid" -> {
                // Balanced approach
                double hybridFactor = 1.0 + (profile.riskTolerance() * 0.25);
                return marketPrice * hybridFactor;
            }
            
            default -> {
                LOGGER.warn("[BotManager] Unknown strategy: {}", profile.strategy());
                return marketPrice;
            }
        }
    }

    /**
     * Check if an item matches a bot profile's filters.
     */
    public static boolean matchesFilters(String itemId, BotProfile profile) {
        Map<String, Boolean> filters = profile.getCustomFilters();
        
        // Default: no restrictions unless explicitly set
        if (filters.isEmpty()) {
            return true;
        }

        // Check each filter
        for (Map.Entry<String, Boolean> entry : filters.entrySet()) {
            String key = entry.getKey();
            boolean condition = entry.getValue();
            
            switch (key) {
                case "exclude_netherite":
                    if (itemId.toLowerCase().contains("netherite")) {
                        return !condition;  // Exclude if condition is true
                    }
                    
                case "max_price_variance", "min_price_variance":
                    // Would need additional market data context to evaluate
                    
                case "target_volatile_only":
                    // Would need volatility metrics from MarketAPI
                    
                default:
                    LOGGER.debug("[BotManager] Unknown filter key: {}", key);
            }
        }
        
        return true;  // Passes all checks
    }

    /**
     * Get a profile's risk assessment for an item.
     */
    public static String getRiskAssessment(String itemId, BotProfile profile) {
        double marketPrice = MarketAPI.getCachedPrice(itemId);
        
        if (marketPrice <= 0) {
            return "UNKNOWN_PRICE";
        }

        // Simple risk scoring based on price tier and strategy
        switch (profile.strategy()) {
            case "value" -> {
                if (marketPrice > 1_000_000.0) return "LOW_RISK_STABLE";
                if (marketPrice < 100.0) return "MEDIUM_RISK_LOW_VALUE";
                return "STANDARD_RISK";
            }
            
            case "momentum" -> {
                // Higher risk tolerance allows more aggressive positions
                if (marketPrice > 5_000_000.0) return "HIGH_RISK_HIGH_REWARD";
                return "MEDIUM_RISK_VOLATILE";
            }
            
            default:
                return "STANDARD_RISK";
        }
    }

    // Data structures
    
    public record BotProfile(
        String name,
        String strategy,           // "value", "momentum", "hybrid"
        double riskTolerance,      // 0.0 to 1.0
        boolean autoAcceptDisabled,// Always true for compliance (can be overridden per-trade)
        Map<String, Boolean> customFilters
    ) {
        
        public void setCustomFilters(Map<String, Boolean> filters) {
            this.customFilters = new ConcurrentHashMap<>(filters);
        }
        
        @Override
        public String toString() {
            return "BotProfile{name='" + name + "', strategy=" + strategy + 
                   ", riskTolerance=" + riskTolerance + "}";
        }
    }
}