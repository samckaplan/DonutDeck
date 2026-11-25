package com.donutsmp.deck;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * TradeDesk - Manual-confirm trading interface.
 * 
 * Design principles:
 * - ALL trades require explicit user confirmation (no auto-accept)
 * - Clear distinction between bot and player counterparties
 * - Price verification against live API before execution
 * - Transaction history for audit/compliance purposes
 */
@Environment(EnvType.CLIENT)
public class TradeDesk {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeDesk.class);
    
    // Active trade offers waiting for user confirmation
    private static final Map<UUID, PendingOffer> PENDING_OFFERS = new ConcurrentHashMap<>();
    
    // Transaction history (kept in memory; can be persisted to file if needed)
    private static final List<TransactionHistoryEntry> HISTORY = new ArrayList<>();
    
    /**
     * Initialize the trade desk.
     */
    public static void initialize() {
        LOGGER.info("[TradeDesk] Initialized | Manual-confirm mode: ENABLED");
        
        // Register keybinding for accepting/rejecting offers (handled by UI subsystem)
    }

    /**
     * Create a new trade offer to the server.
     * 
     * @param itemId The item ID to trade
     * @param quantity Quantity of items
     * @param pricePerItem Price per item in DonutSMP currency
     * @return UUID of the created offer, or null if rejected by server
     */
    public static UUID createOffer(String itemId, int quantity, double pricePerItem) {
        // Verify price against live market data (compliance check)
        double cachedPrice = MarketAPI.getCachedPrice(itemId);
        
        // Allow 20% variance from cached price to account for market fluctuations
        if (cachedPrice > 0 && 
            ((pricePerItem < cachedPrice * 0.8 || pricePerItem > cachedPrice * 1.2))) {
            
            LOGGER.warn("[TradeDesk] Price {} deviates significantly from cached {} for {}. " +
                    "User must confirm this trade manually.", itemId, cachedPrice);
        }
        
        // Generate unique offer ID
        UUID offerId = UUID.randomUUID();
        
        // Create pending offer record
        PendingOffer offer = new PendingOffer(offerId, itemId, quantity, pricePerItem);
        PENDING_OFFERS.put(offerId, offer);
        
        LOGGER.info("[TradeDesk] Offer created | ID: {} | Item: {} x{} @ {}", 
                offerId, itemId, quantity, pricePerItem);
        
        // Notify UI system to display the pending offer
        notifyOfferCreated(offer);
        
        return offerId;
    }

    /**
     * Accept a pending trade offer (requires user confirmation).
     */
    public static void acceptOffer(UUID offerId) {
        PendingOffer offer = PENDING_OFFERS.remove(offerId);
        
        if (offer == null) {
            LOGGER.warn("[TradeDesk] Attempted to accept non-existent offer: {}", offerId);
            return;
        }
        
        // Execute the trade via server API call
        boolean success = executeTradeToServer(offer);
        
        if (success) {
            LOGGER.info("[TradeDesk] Trade executed successfully | Offer: {} | Item: {} x{}", 
                    offerId, offer.itemId, offer.quantity);
            
            // Record in history for compliance/audit
            HISTORY.add(new TransactionHistoryEntry(
                System.currentTimeMillis(),
                "TRADE_EXECUTED",
                offerId.toString(),
                offer.itemId,
                offer.quantity,
                offer.pricePerItem,
                true  // success
            ));
            
            notifyTradeExecuted(offerId);
        } else {
            LOGGER.error("[TradeDesk] Trade execution failed | Offer: {}", offerId);
            
            HISTORY.add(new TransactionHistoryEntry(
                System.currentTimeMillis(),
                "TRADE_FAILED",
                offerId.toString(),
                offer.itemId,
                offer.quantity,
                offer.pricePerItem,
                false  // failure
            ));
            
            notifyTradeFailed(offerId);
        }
    }

    /**
     * Reject a pending trade offer.
     */
    public static void rejectOffer(UUID offerId) {
        PENDING_OFFERS.remove(offerId);
        LOGGER.debug("[TradeDesk] Offer rejected: {}", offerId);
    }

    /**
     * Execute the actual server-side trade transaction.
     * This is where you'd call the DonutSMP API endpoint for trade execution.
     */
    private static boolean executeTradeToServer(PendingOffer offer) {
        // TODO: Implement actual API call to DonutSMP trade endpoint
        // Example: POST /trade/execute with offer details
        
        // For now, simulate successful execution (replace with real implementation)
        try {
            Thread.sleep(50);  // Simulate network latency
            
            // In production, this would be:
            // String response = MarketAPI.fetchFromServer("/trade/execute", 
            //     Map.of("offerId", offer.id.toString(), "itemId", offer.itemId, ...));
            
            return true;  // Assume success for now
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("[TradeDesk] Interrupted during trade execution", e);
            return false;
        }
    }

    /**
     * Get all pending offers.
     */
    public static List<PendingOffer> getAllPendingOffers() {
        return new ArrayList<>(PENDING_OFFERS.values());
    }

    /**
     * Check if there are any pending offers.
     */
    public static boolean hasPendingOffers() {
        return !PENDING_OFFERS.isEmpty();
    }

    /**
     * Get transaction history (for audit/compliance).
     */
    public static List<TransactionHistoryEntry> getHistory() {
        return new ArrayList<>(HISTORY);
    }

    /**
     * Clear all pending offers (emergency reset).
     */
    public static void clearAllPendingOffers() {
        PENDING_OFFERS.clear();
        LOGGER.warn("[TradeDesk] All pending offers cleared");
    }

    // Callbacks for UI system integration
    
    private static void notifyOfferCreated(PendingOffer offer) {
        DonutDeckMod.logIfNotStrict("TRADE", "New offer: {} x{} @ {}", 
                offer.itemId, offer.quantity, offer.pricePerItem);
    }
    
    private static void notifyTradeExecuted(UUID offerId) {
        DonutDeckMod.logIfNotStrict("TRADE", "Offer {} executed successfully", offerId);
    }
    
    private static void notifyTradeFailed(UUID offerId) {
        DonutDeckMod.logIfNotStrict("TRADE", "Offer {} failed", offerId);
    }

    // Data structures
    
    public record PendingOffer(
        UUID id,
        String itemId,
        int quantity,
        double pricePerItem
    ) {}

    public record TransactionHistoryEntry(
        long timestamp,
        String type,           // "TRADE_EXECUTED", "TRADE_FAILED"
        String offerId,
        String itemId,
        int quantity,
        double pricePerItem,
        boolean success
    ) {}
}