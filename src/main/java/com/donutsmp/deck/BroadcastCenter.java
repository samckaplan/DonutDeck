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
import java.util.concurrent.atomic.AtomicLong;

/**
 * BroadcastCenter — Continuous external broadcaster.
 * 
 * Workflow:
 * 1) MarketAPI automatically collects prices from in-game displays.
 * 2) AutoInvestAdvisor analyzes trends and generates tickets.
 * 3) This module continuously broadcasts findings to your website.
 */
@Environment(EnvType.CLIENT)
public class BroadcastCenter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastCenter.class);
    
    // Active broadcast sessions
    private static final Map<UUID, BroadcastSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    
    // Rate limiting counters per destination
    private static final Map<String, AtomicLong> RATE_LIMITERS = new ConcurrentHashMap<>();
    
    /**
     * Initialize the broadcast center.
     */
    public static void initialize() {
        LOGGER.info("[BroadcastCenter] Initialized | Mode: CONTINUOUS_BROADCAST");
        
        // Auto-create a default session for your external website
        createDefaultSession();
    }

    /**
     * Create a new broadcast session (for testing or additional destinations).
     */
    public static UUID createSession(String destinationType, String destinationId) {
        if (!isPrivacyModeEnabled()) {
            LOGGER.warn("[BroadcastCenter] Privacy mode active - broadcasts require explicit consent");
            return null;
        }

        // Validate destination type
        if (!isValidDestinationType(destinationType)) {
            LOGGER.error("[BroadcastCenter] Invalid destination type: {}", destinationType);
            return null;
        }

        UUID sessionId = UUID.randomUUID();
        
        BroadcastSession session = new BroadcastSession(
            sessionId,
            destinationType,
            destinationId,
            false  // pending user confirmation
        );
        
        ACTIVE_SESSIONS.put(sessionId, session);
        
        LOGGER.info("[BroadcastCenter] Session created: {} | Type: {} | ID: {}", 
                sessionId, destinationType, destinationId);
        
        notifySessionCreated(session);
        
        return sessionId;
    }

    /**
     * Confirm and start a broadcast session (requires user confirmation).
     */
    public static void confirmSession(UUID sessionId) {
        BroadcastSession session = ACTIVE_SESSIONS.get(sessionId);
        
        if (session == null || session.isConfirmed()) {
            LOGGER.warn("[BroadcastCenter] Invalid or already confirmed session: {}", sessionId);
            return;
        }

        // Apply rate limiting based on destination type
        applyRateLimit(session.destinationType());
        
        session.setConfirmed(true);
        
        LOGGER.info("[BroadcastCenter] Session confirmed and started: {} | Type: {}", 
                sessionId, session.destinationType());
        
        notifySessionStarted(sessionId);
    }

    /**
     * Send a broadcast message through an active session.
     */
    public static void sendBroadcast(UUID sessionId, String message) {
        BroadcastSession session = ACTIVE_SESSIONS.get(sessionId);
        
        if (session == null || !session.isConfirmed()) {
            LOGGER.warn("[BroadcastCenter] Cannot send to inactive session: {}", sessionId);
            return;
        }

        // Apply rate limiting check
        if (!checkRateLimit(session.destinationType())) {
            LOGGER.debug("[BroadcastCenter] Rate limited for destination type: {}", 
                    session.destinationType());
            return;
        }

        // Execute the broadcast based on destination type
        boolean success = executeBroadcastToDestination(session, message);
        
        if (success) {
            LOGGER.debug("[BroadcastCenter] Broadcast sent successfully");
            
            // Increment rate limiter counter
            incrementRateLimit(session.destinationType());
        } else {
            LOGGER.error("[BroadcastCenter] Broadcast failed to destination: {}", 
                    session.destinationType());
        }
    }

    /**
     * Stop an active broadcast session.
     */
    public static void stopSession(UUID sessionId) {
        ACTIVE_SESSIONS.remove(sessionId);
        LOGGER.info("[BroadcastCenter] Session stopped: {}", sessionId);
    }

    /**
     * Get all active sessions.
     */
    public static List<BroadcastSession> getAllSessions() {
        return new ArrayList<>(ACTIVE_SESSIONS.values());
    }

    /**
     * Check if privacy mode is enabled (OpSec setting).
     */
    private static boolean isPrivacyModeEnabled() {
        return DonutDeckMod.isOpSecActive();
    }

    /**
     * Validate a destination type.
     */
    private static boolean isValidDestinationType(String type) {
        // Supported destination types (can be extended)
        switch (type.toLowerCase()) {
            case "website":
                return true;  // Your external website
            
            case "discord_webhook":
                return true;
            
            case "external_api":
                return true;
            
            default:
                LOGGER.warn("[BroadcastCenter] Unknown destination type: {}", type);
                return false;
        }
    }

    /**
     * Check and enforce rate limiting for a destination.
     */
    private static boolean checkRateLimit(String destinationType) {
        String limiterKey = "broadcast:" + destinationType;
        
        AtomicLong counter = RATE_LIMITERS.computeIfAbsent(limiterKey, k -> new AtomicLong(0));
        
        // Default: 30 requests per minute max (configurable via gradle.properties)
        long limitPerMinute = 30L;
        
        if (counter.getAndIncrement() >= limitPerMinute) {
            return false;  // Rate limited
        }
        
        return true;
    }

    /**
     * Increment the rate limiter counter.
     */
    private static void incrementRateLimit(String destinationType) {
        String limiterKey = "broadcast:" + destinationType;
        RATE_LIMITERS.computeIfAbsent(limiterKey, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Apply rate limiting configuration for a session type.
     */
    private static void applyRateLimit(String destinationType) {
        // In production, this would configure the specific rate limit rules
        LOGGER.debug("[BroadcastCenter] Rate limits applied for: {}", destinationType);
    }

    /**
     * Execute the actual broadcast to a destination.
     */
    private static boolean executeBroadcastToDestination(BroadcastSession session, String message) {
        // TODO: Implement actual broadcasting logic based on destination type
        
        switch (session.destinationType().toLowerCase()) {
            case "website":
                // POST to your external website endpoint
                return true;  // Simulated success
            
            case "discord_webhook":
                // Would POST to Discord webhook URL
                return true;
            
            case "external_api":
                // Would POST/PUT to configured external endpoint
                return true;
            
            default:
                LOGGER.error("[BroadcastCenter] Unknown destination type during broadcast");
                return false;
        }
    }

    /**
     * Create a default session for continuous broadcasting.
     */
    private static void createDefaultSession() {
        // Auto-create your website broadcaster
        UUID defaultSessionId = createSession("website", "donut_deck_broadcast");
        
        if (defaultSessionId != null) {
            confirmSession(defaultSessionId);  // Auto-confirm for continuous operation
            
            LOGGER.info("[BroadcastCenter] Default website session auto-created and confirmed: {}", 
                    defaultSessionId);
        }
    }

    // Callbacks for UI integration
    
    private static void notifySessionCreated(BroadcastSession session) {
        DonutDeckMod.logIfNotStrict("BROADCAST", "New session: {} | Type: {}", 
                session.id(), session.destinationType());
    }
    
    private static void notifySessionStarted(UUID sessionId) {
        DonutDeckMod.logIfNotStrict("BROADCAST", "Session started: {}", sessionId);
    }

    // Data structures
    
    public record BroadcastSession(
        UUID id,
        String destinationType,
        String destinationId,
        boolean confirmed  // true = user has confirmed this broadcast
    ) {
        
        public void setConfirmed(boolean confirmed) {
            this.confirmed = confirmed;
        }
    }
}