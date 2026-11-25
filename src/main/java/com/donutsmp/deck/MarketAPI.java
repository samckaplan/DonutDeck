package com.donutsmp.deck;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * MarketAPI — Manual market research & price journal.
 * 
 * Workflow:
 * 1) You observe prices in-game (auctions, trades, player offers).
 * 2) You enter them via the UI into this system.
 * 3) The mod tracks trends, averages, and volatility over time.
 */
@Environment(EnvType.CLIENT)
public class MarketAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketAPI.class);
    
    // Manually entered price entries (keyed by item ID)
    private static final Map<String, List<PriceEntry>> PRICE_JOURNAL = new ConcurrentHashMap<>();
    
    // Snapshot for quick reads in UI/analysis tools
    private static final Map<String, CurrentSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    
    /**
     * Initialize the market journal.
     */
    public static void initialize() {
        LOGGER.info("[MarketAPI] Initialized | Mode: MANUAL_JOURNAL");
        
        // Register UI hooks for entering prices (handled by UI subsystem)
    }

    /**
     * Record a manually observed price entry.
     * 
     * @param itemId The item ID as seen in-game
     * @param quantity Quantity involved in the observation
     * @param pricePerItem Price per unit you observed
     * @param context Optional notes (e.g., "auction", "private trade")
     */
    public static void recordPriceEntry(String itemId, int quantity, double pricePerItem, String context) {
        if (itemId == null || itemId.trim().isEmpty()) {
            LOGGER.warn("[MarketAPI] Invalid item ID for entry");
            return;
        }

        // Normalize item ID to a consistent key
        String normalizedId = normalizeItemId(itemId);
        
        PRICE_JOURNAL.computeIfAbsent(normalizedId, k -> new ArrayList<>()).add(
            new PriceEntry(System.currentTimeMillis(), quantity, pricePerItem, context)
        );
        
        // Update snapshot for quick reads
        updateSnapshot(normalizedId, pricePerItem);
        
        LOGGER.debug("[MarketAPI] Recorded: {} x{} @ {} | {}", 
                normalizedId, quantity, pricePerItem, context != null ? context : "(no notes)");
    }

    /**
     * Get the current best-known snapshot for an item.
     */
    public static CurrentSnapshot getSnapshot(String itemId) {
        return SNAPSHOTS.get(normalizeItemId(itemId));
    }

    /**
     * Get all historical entries for an item (for trend analysis).
     */
    public static List<PriceEntry> getHistory(String itemId) {
        return new ArrayList<>(PRICE_JOURNAL.getOrDefault(normalizeItemId(itemId), List.of()));
    }

    /**
     * Calculate current average price across all recorded entries.
     */
    public static double calculateAveragePrice(String itemId) {
        List<PriceEntry> history = getHistory(itemId);
        if (history.isEmpty()) return 0.0;
        
        // Weighted average by quantity observed
        long totalQuantity = 0;
        double weightedSum = 0.0;
        
        for (PriceEntry entry : history) {
            totalQuantity += entry.quantity();
            weightedSum += entry.pricePerItem() * entry.quantity();
        }
        
        return totalQuantity > 0 ? weightedSum / totalQuantity : 0.0;
    }

    /**
     * Calculate recent trend (last N entries).
     */
    public static double calculateTrend(String itemId, int lookbackCount) {
        List<PriceEntry> history = getHistory(itemId);
        if (history.size() < 2) return 0.0;
        
        // Get last N entries or all if fewer exist
        int actualLookback = Math.min(lookbackCount, history.size());
        List<PriceEntry> recent = new ArrayList<>(history.subList(history.size() - actualLookback, history.size()));
        
        double firstAvg = calculateAveragePriceForEntries(recent);
        // For trend direction, we'd need timestamps; simplified here:
        return 0.0;  // Implement timestamp-based slope if needed later
    }

    /**
     * Calculate volatility (standard deviation) of recorded prices.
     */
    public static double calculateVolatility(String itemId) {
        List<PriceEntry> history = getHistory(itemId);
        if (history.size() < 2) return 0.0;
        
        // Compute mean price per entry
        double avgPrice = 0.0;
        for (PriceEntry e : history) {
            avgPrice += e.pricePerItem();
        }
        avgPrice /= history.size();
        
        // Compute variance
        double varianceSum = 0.0;
        for (PriceEntry e : history) {
            double diff = e.pricePerItem() - avgPrice;
            varianceSum += diff * diff;
        }
        
        return Math.sqrt(varianceSum / history.size());
    }

    /**
     * Get the most recent entry timestamp for an item.
     */
    public static long getLastUpdateTimestamp(String itemId) {
        List<PriceEntry> history = getHistory(itemId);
        if (history.isEmpty()) return 0L;
        
        // Find max timestamp
        long maxTs = Long.MIN_VALUE;
        for (PriceEntry e : history) {
            if (e.timestamp() > maxTs) maxTs = e.timestamp();
        }
        return maxTs;
    }

    /**
     * Clear all journal data (for testing or privacy reset).
     */
    public static void clearAllData() {
        PRICE_JOURNAL.clear();
        SNAPSHOTS.clear();
        LOGGER.info("[MarketAPI] All journal data cleared");
    }

    // Snapshot maintenance
    
    private static void updateSnapshot(String itemId, double pricePerItem) {
        CurrentSnapshot snapshot = new CurrentSnapshot(
            calculateAveragePrice(itemId),
            System.currentTimeMillis(),
            getHistory(itemId).size()
        );
        
        SNAPSHOTS.put(itemId, snapshot);
    }

    // Utility: normalize item IDs for consistent tracking
    
    private static String normalizeItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        
        // Trim and lowercase; you can customize this based on in-game naming conventions
        return itemId.trim().toLowerCase();
    }

    // Inner classes
    
    public record CurrentSnapshot(
        double avgPrice,
        long timestamp,
        int entryCount
    ) {}

    private static class PriceEntry {
        final long timestamp;
        final int quantity;
        final double pricePerItem;
        final String context;
        
        PriceEntry(long timestamp, int quantity, double pricePerItem, String context) {
            this.timestamp = timestamp;
            this.quantity = quantity;
            this.pricePerItem = pricePerItem;
            this.context = context;
        }
    }
}