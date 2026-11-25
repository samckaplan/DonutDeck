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
 * AutoInvestAdvisor — Fully automated investment engine.
 * 
 * Workflow:
 * 1) MarketAPI automatically reads prices from in-game displays.
 * 2) This module analyzes trends, volatility, and opportunities.
 * 3) Generates one-click tickets that execute trades via TradeDesk.
 */
@Environment(EnvType.CLIENT)
public class AutoInvestAdvisor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoInvestAdvisor.class);
    
    // Active investment tickets (auto-executed based on analysis)
    private static final Map<UUID, InvestmentTicket> ACTIVE_TICKETS = new ConcurrentHashMap<>();
    
    /**
     * Initialize the advisor.
     */
    public static void initialize() {
        LOGGER.info("[AutoInvestAdvisor] Initialized | Mode: AUTOMATED_ANALYSIS");
        
        // Register analysis hooks (handled by UI subsystem)
    }

    /**
     * Continuously analyze market data and generate investment tickets automatically.
     * Called periodically after MarketAPI updates its journal.
     */
    public static void runAnalysisCycle() {
        // Get all items we've tracked
        Map<String, Double> snapshots = new ConcurrentHashMap<>();
        for (Map.Entry<String, CurrentSnapshot> entry : MarketAPI.SNAPSHOTS.entrySet()) {
            String itemId = entry.getKey();
            double avgPrice = calculateAveragePrice(itemId);
            
            if (avgPrice > 0) {
                snapshots.put(itemId, avgPrice);
            }
        }

        if (snapshots.isEmpty()) {
            LOGGER.debug("[AutoInvestAdvisor] No market data to analyze");
            return;
        }

        // Analyze each item for investment opportunities
        List<InvestmentTicket> tickets = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : snapshots.entrySet()) {
            String itemId = entry.getKey();
            double avgPrice = entry.getValue();
            
            if (avgPrice <= 0) continue;
            
            // Generate a ticket based on current price and volatility
            InvestmentTicket ticket = generateTicket(itemId, avgPrice);
            
            if (ticket != null) {
                tickets.add(ticket);
                
                LOGGER.info("[AutoInvestAdvisor] Auto-ticket generated: {} @ {}", 
                        itemId, avgPrice);
            }
        }

        // Execute all generated tickets automatically
        for (InvestmentTicket ticket : tickets) {
            executeTicket(ticket.id());
        }
    }

    /**
     * Generate an investment ticket based on current market analysis.
     */
    private static InvestmentTicket generateTicket(String itemId, double avgPrice) {
        // Simple strategy: always buy at average price with a fixed quantity
        // In production, you'd add more complex logic here
        
        int quantity = (int) Math.floor(100_000.0 / avgPrice);  // Example capital allocation
        
        if (quantity <= 0) {
            return null;  // Price too high for our strategy
        }

        UUID ticketId = UUID.randomUUID();
        
        return new InvestmentTicket(
            ticketId,
            itemId,
            quantity,
            avgPrice,
            "auto",
            false  // Will be executed immediately
        );
    }

    /**
     * Execute an investment ticket (triggers TradeDesk).
     */
    public static void executeTicket(UUID ticketId) {
        InvestmentTicket ticket = ACTIVE_TICKETS.get(ticketId);
        
        if (ticket == null) {
            LOGGER.warn("[AutoInvestAdvisor] Attempted to execute non-existent ticket: {}", ticketId);
            return;
        }

        // Execute the trade via TradeDesk
        boolean success = TradeDesk.createOffer(
            ticket.itemId, 
            ticket.quantity, 
            ticket.pricePerItem
        );

        if (success) {
            LOGGER.info("[AutoInvestAdvisor] Auto-ticket executed: {} x{} @ {}", 
                    ticket.itemId, ticket.quantity, ticket.pricePerItem);
            
            notifyTicketExecuted(ticketId);
        } else {
            LOGGER.error("[AutoInvestAdvisor] Auto-ticket execution failed: {}", ticketId);
            
            notifyTicketFailed(ticketId);
        }
    }

    /**
     * Calculate average price for an item from journal data.
     */
    private static double calculateAveragePrice(String itemId) {
        return MarketAPI.calculateAveragePrice(itemId);
    }

    // Callbacks for UI integration
    
    private static void notifyTicketExecuted(UUID ticketId) {
        DonutDeckMod.logIfNotStrict("INVEST", "Auto-ticket {} executed", ticketId);
    }
    
    private static void notifyTicketFailed(UUID ticketId) {
        DonutDeckMod.logIfNotStrict("INVEST", "Auto-ticket {} failed", ticketId);
    }

    // Data structures
    
    public record InvestmentTicket(
        UUID id,
        String itemId,
        int quantity,
        double pricePerItem,
        String profileName,
        boolean pendingConfirmation  // false = auto-execute
    ) {
        
        @Override
        public String toString() {
            return "InvestmentTicket{itemId=" + itemId + 
                   ", qty=" + quantity + 
                   ", price=" + pricePerItem + "}";
        }
    }
}