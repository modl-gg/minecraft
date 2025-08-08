package gg.modl.minecraft.core.impl.cache;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Temporary cache for login check results to prevent repeated API calls
 * and store async operation results for synchronous event handlers.
 */
@RequiredArgsConstructor
public class LoginCache {
    private static final Logger logger = Logger.getLogger(LoginCache.class.getName());
    
    // Cache for completed login checks (to prevent repeated API calls)
    private final ConcurrentHashMap<UUID, CachedLoginResult> loginResultCache = new ConcurrentHashMap<>();
    
    // Temporary storage for async pre-login results (used by Spigot)
    private final ConcurrentHashMap<UUID, PreLoginResult> preLoginResults = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService cleanupExecutor;
    private final int cacheExpirySeconds;
    
    public LoginCache() {
        this(30); // Default 30 seconds cache
    }
    
    public LoginCache(int cacheExpirySeconds) {
        this.cacheExpirySeconds = cacheExpirySeconds;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-login-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule cleanup every 30 seconds
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Check if we have a recent login result for this player
     */
    public CachedLoginResult getCachedLoginResult(UUID playerUuid) {
        CachedLoginResult cached = loginResultCache.get(playerUuid);
        if (cached != null && !cached.isExpired()) {
            logger.fine("Using cached login result for " + playerUuid);
            return cached;
        }
        return null;
    }
    
    /**
     * Cache a login result
     */
    public void cacheLoginResult(UUID playerUuid, PlayerLoginResponse response, JsonObject ipInfo, String skinHash) {
        CachedLoginResult cached = new CachedLoginResult(response, ipInfo, skinHash, Instant.now());
        loginResultCache.put(playerUuid, cached);
        logger.fine("Cached login result for " + playerUuid);
    }
    
    /**
     * Store pre-login result for Spigot async->sync pattern
     */
    public void storePreLoginResult(UUID playerUuid, PreLoginResult result) {
        preLoginResults.put(playerUuid, result);
        logger.fine("Stored pre-login result for " + playerUuid);
    }
    
    /**
     * Get and remove pre-login result for Spigot async->sync pattern
     */
    public PreLoginResult getAndRemovePreLoginResult(UUID playerUuid) {
        PreLoginResult result = preLoginResults.remove(playerUuid);
        if (result != null) {
            logger.fine("Retrieved pre-login result for " + playerUuid);
        }
        return result;
    }
    
    /**
     * Clean up expired entries
     */
    private void cleanup() {
        // Count actual removals by tracking sizes before and after
        int loginResultsBefore = loginResultCache.size();
        loginResultCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removedLoginResults = loginResultsBefore - loginResultCache.size();

        // Clean up old pre-login results (older than 1 minute)
        Instant cutoff = Instant.now().minusSeconds(60);
        int preLoginResultsBefore = preLoginResults.size();
        preLoginResults.entrySet().removeIf(entry -> entry.getValue().getTimestamp().isBefore(cutoff));
        int removedPreLoginResults = preLoginResultsBefore - preLoginResults.size();

        if (removedLoginResults > 0 || removedPreLoginResults > 0) {
            logger.fine(String.format("Cleaned up %d expired login results and %d old pre-login results",
                    removedLoginResults, removedPreLoginResults));
        }
    }
    
    /**
     * Shutdown the cache
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        loginResultCache.clear();
        preLoginResults.clear();
        logger.info("Login cache shutdown complete");
    }
    
    @Data
    public static class CachedLoginResult {
        private final PlayerLoginResponse response;
        private final JsonObject ipInfo;
        private final String skinHash;
        private final Instant timestamp;
        private final int expirySeconds;
        
        public CachedLoginResult(PlayerLoginResponse response, JsonObject ipInfo, String skinHash, Instant timestamp) {
            this.response = response;
            this.ipInfo = ipInfo;
            this.skinHash = skinHash;
            this.timestamp = timestamp;
            this.expirySeconds = 30; // Default 30 seconds
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(timestamp.plusSeconds(expirySeconds));
        }
    }
    
    @Data
    public static class PreLoginResult {
        private final PlayerLoginResponse response;
        private final JsonObject ipInfo;
        private final String skinHash;
        private final Instant timestamp;
        private final Exception error;
        
        public PreLoginResult(PlayerLoginResponse response, JsonObject ipInfo, String skinHash) {
            this.response = response;
            this.ipInfo = ipInfo;
            this.skinHash = skinHash;
            this.timestamp = Instant.now();
            this.error = null;
        }
        
        public PreLoginResult(Exception error) {
            this.response = null;
            this.ipInfo = null;
            this.skinHash = null;
            this.timestamp = Instant.now();
            this.error = error;
        }
        
        public boolean hasError() {
            return error != null;
        }
        
        public boolean isSuccess() {
            return error == null && response != null;
        }
    }
}
