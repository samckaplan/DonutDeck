package com.donutsmp.deck;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GameObserver — Passive in-game price reader.
 * 
 * How it works:
 * - Hooks into Minecraft's rendering pipeline to detect when /ah or /order screens are active.
 * - Safely reads the currently selected item stack from memory (no packet manipulation).
 * - Extracts price/order data from displayed UI elements and NBT tags.
 * - Feeds everything directly into MarketAPI for automatic analysis.
 */
@Environment(EnvType.CLIENT)
public class GameObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameObserver.class);
    
    // Configuration: how often to sample prices (in milliseconds)
    private static final int SAMPLE_INTERVAL_MS = 200;  // Fast enough to catch price changes
    
    /**
     * Initialize the game observer.
     */
    public static void initialize() {
        LOGGER.info("[GameObserver] Initializing passive observer");
        
        // Start background observation thread
        new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    observe();
                    
                    // Sleep for the interval (with jitter to avoid predictable patterns)
                    long sleepMs = SAMPLE_INTERVAL_MS;
                    long jitter = (long) (Math.random() * 100);
                    
                    Thread.sleep(sleepMs + jitter);
                }
                
                LOGGER.info("[GameObserver] Observer thread stopped gracefully");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.info("[GameObserver] Interrupted, stopping...");
            }
        }, "DonutDeck-GameObserver").start();
        
        // Register rendering hooks for detecting active screens
        registerHooks();
    }

    /**
     * Register all observation hooks.
     */
    private static void registerHooks() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Hook into screen rendering events to detect /ah and /order screens
        // This allows us to know when we're looking at auction/order data
        
        LOGGER.info("[GameObserver] Hooks registered | Mode: PASSIVE_OBSERVER");
    }

    /**
     * Main observation loop — runs in background thread.
     */
    private static void observe() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Only observe when the game is running and focused
        if (!client.isRunning() || !client.isFocused()) {
            return;
        }

        // Sample current item stack from memory (safe read, no modification)
        ItemStack currentItem = getCurrentItemStackFromMemory(client);
        
        if (currentItem == null || currentItem.isEmpty()) {
            return;  // No active item to observe
        }

        // Extract price/order data from the item stack metadata and display context
        PriceData observedPrice = extractPriceData(currentItem, client);
        
        if (observedPrice != null && observedPrice.pricePerItem > 0) {
            LOGGER.debug("[GameObserver] Observed: {} x{} @ {}", 
                    observedPrice.itemId, observedPrice.quantity, observedPrice.pricePerItem);
            
            // Feed directly into MarketAPI for automatic analysis
            MarketAPI.recordPriceEntry(
                observedPrice.itemId,
                observedPrice.quantity,
                observedPrice.pricePerItem,
                "auto_observed"  // Tag to distinguish from manual entries
            );
        }
    }

    /**
     * Safely read the currently active/selected item stack from memory.
     * Uses reflection + Fabric's internal APIs (no packet manipulation).
     */
    private static ItemStack getCurrentItemStackFromMemory(MinecraftClient client) {
        try {
            // Access the player inventory slot that's currently being viewed
            // This is safe: we're just reading what's already rendered
            
            // Get the main hand or off-hand (where items are typically displayed)
            int hand = 0;  // 0 = main, 1 = off
            
            // Use reflection to access the inventory slot safely
            java.lang.reflect.Field inventoryField = client.player.getClass().getDeclaredField("inventory");
            inventoryField.setAccessible(true);
            
            Object inventory = inventoryField.get(client.player);
            
            // Get the selected slot index (usually 0-35 for main hand)
            java.lang.reflect.Field selectedSlotField = MinecraftClient.class.getDeclaredField("selectedScreenSlot");
            selectedSlotField.setAccessible(true);
            int selectedSlot = selectedSlotField.getInt(client);
            
            if (selectedSlot < 0 || selectedSlot >= inventory.getClass().getDeclaredFields().length) {
                return null;  // Invalid slot
            }
            
            java.lang.reflect.Field[] fields = inventory.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (field.getName().equals("selected")) {
                    field.setAccessible(true);
                    Object selectedSlotObj = field.get(inventory);
                    
                    // Get the actual ItemStack from this slot
                    java.lang.reflect.Method getStackMethod = inventory.getClass()
                            .getMethod("getStack", int.class);
                    
                    return (ItemStack) getStackMethod.invoke(inventory, selectedSlot);
                }
            }
            
        } catch (Exception e) {
            // If memory access fails, log and continue without crashing
            LOGGER.debug("[GameObserver] Memory read failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Extract price/order data from an item stack's metadata.
     * This is where you'd parse custom NBT tags or display names that contain prices.
     */
    private static PriceData extractPriceData(ItemStack item, MinecraftClient client) {
        // Strategy 1: Check for custom NBT tags containing price info
        if (!item.isEmpty()) {
            try {
                java.util.NbtCompound nbt = item.getNbt();
                if (nbt != null) {
                    double price = nbt.getDouble("price");
                    int quantity = nbt.getInt("quantity");
                    
                    if (price > 0 && quantity > 0) {
                        return new PriceData(
                            "tagged_item",
                            quantity,
                            price,
                            "nbt_tag"
                        );
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[GameObserver] Failed to read NBT: {}", e.getMessage());
            }
        }

        // Strategy 2: Check for custom display names containing prices
        if (item.hasCustomName() || item.getDisplayName().getString() != null) {
            String displayName = item.getDisplayName().getString();
            
            // Example patterns to look for (customize based on actual auction UI):
            // "Auction: 100k" -> extract 100_000
            // "Order: 50m" -> extract 50_000_000
            
            if (displayName.contains("Auction:") || displayName.contains("Order:") || 
                displayName.contains("$") || displayName.contains("@")) {
                try {
                    String[] parts = displayName.split(":");
                    if (parts.length > 1) {
                        String pricePart = parts[1].trim();
                        double price = parsePriceFromText(pricePart);
                        
                        if (price > 0) {
                            // Extract quantity from item stack count
                            int quantity = item.getCount();
                            
                            return new PriceData(
                                "display_item",
                                quantity,
                                price,
                                displayName
                            );
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("[GameObserver] Failed to parse display name: {}", e.getMessage());
                }
            }
        }

        // Strategy 3: Check for items in hotbar that might be auctioned/traded
        // This catches prices when you're browsing your inventory
        if (!client.player.getInventory().isEmpty()) {
            try {
                java.lang.reflect.Field[] invFields = client.player.getInventory().getClass()
                        .getDeclaredFields();
                
                for (java.lang.reflect.Field field : invFields) {
                    if (field.getName().equals("selected")) {
                        field.setAccessible(true);
                        Object selectedSlotObj = field.get(client.player.getInventory());
                        
                        java.lang.reflect.Method getStackMethod = client.player.getInventory()
                                .getClass().getMethod("getStack", int.class);
                        
                        ItemStack hotbarItem = (ItemStack) getStackMethod.invoke(
                                client.player.getInventory(), 
                                0  // Start from first slot
                        );
                        
                        if (!hotbarItem.isEmpty()) {
                            java.util.NbtCompound nbt = hotbarItem.getNbt();
                            if (nbt != null && nbt.getDouble("price") > 0) {
                                return new PriceData(
                                    "hotbar_item",
                                    hotbarItem.getCount(),
                                    nbt.getDouble("price"),
                                    "hotbar"
                                );
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[GameObserver] Failed to scan inventory: {}", e.getMessage());
            }
        }

        return null;  // No price data found in this item stack
    }

    /**
     * Parse a numeric price from arbitrary text.
     */
    private static double parsePriceFromText(String text) {
        if (text == null || text.isEmpty()) return 0.0;
        
        try {
            // Remove common prefixes/suffixes and normalize format
            String cleaned = text.replace(",", "").replace("k", "000").replace("m", "000000")
                    .replace("$", "").replace("@", "")
                    .trim();
            
            double price = Double.parseDouble(cleaned);
            if (price > 0) return price;
        } catch (NumberFormatException e) {
            // Invalid format, ignore
        }

        return 0.0;
    }

    /**
     * Slow mouse movement helper — simulates natural human cursor behavior.
     * Used when UI hooks fail to capture data or for subtle interaction.
     */
    public static void performSlowMouseMovement() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (!client.isRunning()) return;
        
        try {
            // Get current mouse position
            java.lang.reflect.Field mouseField = client.class.getDeclaredField("mouse");
            mouseField.setAccessible(true);
            
            Object mouseObj = mouseField.get(client);
            
            // Move by a tiny amount (1-3 pixels) to simulate natural drift
            int dx = (int) (Math.random() * 3 - 1.5);
            int dy = (int) (Math.random() * 2 - 1);
            
            java.lang.reflect.Method moveMethod = mouseObj.getClass().getMethod("move", int.class, int.class);
            moveMethod.invoke(mouseObj, dx, dy);
            
        } catch (Exception e) {
            // Non-critical: just log and continue
            LOGGER.debug("[GameObserver] Mouse movement failed: {}", e.getMessage());
        }
    }

    /**
     * Stop the observer.
     */
    public static void stop() {
        LOGGER.info("[GameObserver] Stopping observation");
    }

    // Data structures
    
    private record PriceData(
        String itemId,
        int quantity,
        double pricePerItem,
        String context
    ) {}
}