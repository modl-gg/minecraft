package gg.modl.minecraft.core.impl.cache;

import com.google.gson.JsonObject;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoginCache {
    private static final int LOGIN_RESULT_EXPIRY_SECONDS = 30;
    private static final int PRE_LOGIN_RESULT_EXPIRY_SECONDS = 60;

    private final ConcurrentHashMap<UUID, CachedLoginResult> loginResultCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PreLoginResult> preLoginResults = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public LoginCache() {
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-login-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    public CachedLoginResult getCachedLoginResult(UUID playerUuid) {
        CachedLoginResult cached = loginResultCache.get(playerUuid);
        if (cached != null && !cached.isExpired()) return cached;
        return null;
    }

    public void cacheLoginResult(UUID playerUuid, PlayerLoginResponse response, JsonObject ipInfo, String skinHash) {
        loginResultCache.put(playerUuid, new CachedLoginResult(response, ipInfo, skinHash, Instant.now()));
    }

    public void storePreLoginResult(UUID playerUuid, PreLoginResult result) {
        preLoginResults.put(playerUuid, result);
    }

    public PreLoginResult getAndRemovePreLoginResult(UUID playerUuid) {
        return preLoginResults.remove(playerUuid);
    }

    private void cleanup() {
        loginResultCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        Instant cutoff = Instant.now().minusSeconds(PRE_LOGIN_RESULT_EXPIRY_SECONDS);
        preLoginResults.entrySet().removeIf(entry -> entry.getValue().getTimestamp().isBefore(cutoff));
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) cleanupExecutor.shutdownNow();
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        loginResultCache.clear();
        preLoginResults.clear();
    }

    @Getter
    public static class CachedLoginResult {
        private final PlayerLoginResponse response;
        private final JsonObject ipInfo;
        private final String skinHash;
        private final Instant timestamp;

        public CachedLoginResult(PlayerLoginResponse response, JsonObject ipInfo, String skinHash, Instant timestamp) {
            this.response = response;
            this.ipInfo = ipInfo;
            this.skinHash = skinHash;
            this.timestamp = timestamp;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(timestamp.plusSeconds(LOGIN_RESULT_EXPIRY_SECONDS));
        }
    }

    @Getter
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
