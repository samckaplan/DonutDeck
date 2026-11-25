package com.donutsmp.deck;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BroadcastLoop — Continuous background broadcaster thread.
 * 
 * Runs independently of the main game loop to ensure findings are always sent.
 */
@Environment(EnvType.CLIENT)
public class BroadcastLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastLoop.class);
    
    // Configuration: how often to broadcast aggregated findings (in seconds)
    private static final int BROADCAST_INTERVAL_SECONDS = 10;
    
    /**
     * Start the continuous broadcast loop.
     */
    public static void start() {
        LOGGER.info("[BroadcastLoop] Starting continuous broadcast thread");
        
        new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    runBroadcastCycle();
                    
                    // Sleep for the interval (with jitter to avoid predictable patterns)
                    long sleepMs = BROADCAST_INTERVAL_SECONDS * 1000L;
                    long jitter = (long) (Math.random() * 2000);
                    
                    Thread.sleep(sleepMs + jitter);
                }
                
                LOGGER.info("[BroadcastLoop] Broadcast loop stopped gracefully");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.info("[BroadcastLoop] Interrupted, stopping...");
            }
        }, "DonutDeck-Broadcast-Loop").start();
    }

    /**
     * Run a single broadcast cycle.
     */
    private static void runBroadcastCycle() {
        // Gather all market data from MarketAPI
        java.util.Map<String, CurrentSnapshot> snapshots = new java.util.concurrent.ConcurrentHashMap<>();
        
        for (java.util.Map.Entry<String, CurrentSnapshot> entry : MarketAPI.SNAPSHOTS.entrySet()) {
            String itemId = entry.getKey();
            double avgPrice = calculateAveragePrice(itemId);
            
            if (avgPrice > 0) {
                snapshots.put(itemId, new SnapshotData(
                    itemId,
                    avgPrice,
                    System.currentTimeMillis() / 1000L  // Unix timestamp in seconds
                ));
            }
        }

        if (snapshots.isEmpty()) {
            LOGGER.debug("[BroadcastLoop] No market data to broadcast");
            return;
        }

        // Generate a summary message
        String message = generateSummaryMessage(snapshots);
        
        // Broadcast to all active sessions
        for (BroadcastCenter.BroadcastSession session : BroadcastCenter.getAllSessions()) {
            if (session.isConfirmed()) {
                BroadcastCenter.sendBroadcast(session.id(), message);
                
                LOGGER.debug("[BroadcastLoop] Sent summary to {}: {}", 
                        session.destinationType(), message.substring(0, Math.min(100, message.length())) + "...");
            }
        }
    }

    /**
     * Generate a human-readable summary of market findings.
     */
    private static String generateSummaryMessage(java.util.Map<String, SnapshotData> snapshots) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== DonutDeck Market Report ===\n");
        sb.append("Timestamp: ").append(System.currentTimeMillis()).append("\n\n");
        
        // Sort by price (highest first) for interesting items
        snapshots.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.value.avgPrice, a.value.avgPrice))
            .limit(10)  // Top 10 items only to keep messages concise
            .forEach(entry -> {
                SnapshotData data = entry.getValue();
                
                sb.append("Item: ").append(data.itemId).append("\n");
                sb.append("  Avg Price: $").append(String.format("%.2f", data.avgPrice)).append("\n");
                sb.append("  Timestamp: ").append(data.timestamp).append("\n\n");
            });
        
        // Add investment recommendations from AutoInvestAdvisor
        int ticketCount = AutoInvestAdvisor.getAllTickets().size();
        if (ticketCount > 0) {
            sb.append("--- Investment Tickets ---\n");
            sb.append("Active tickets: ").append(ticketCount).append("\n\n");
        }
        
        return sb.toString();
    }

    /**
     * Calculate average price for an item.
     */
    private static double calculateAveragePrice(String itemId) {
        return MarketAPI.calculateAveragePrice(itemId);
    }

    /**
     * Stop the broadcast loop.
     */
    public static void stop() {
        LOGGER.info("[BroadcastLoop] Stopping...");
        
        // Interrupt the thread (it checks this flag on each sleep)
        Thread.currentThread().interrupt();
    }

    // Data structures
    
    private record SnapshotData(
        String itemId,
        double avgPrice,
        long timestamp  // Unix timestamp in seconds
    ) {}
}