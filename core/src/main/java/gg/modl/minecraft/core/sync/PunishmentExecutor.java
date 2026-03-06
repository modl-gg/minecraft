package gg.modl.minecraft.core.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PunishmentMessages;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import gg.modl.minecraft.core.util.PluginLogger;

/**
 * Executes punishments (ban, mute, kick) and applies modifications (pardon, duration change).
 */
class PunishmentExecutor {
    private static final long ACK_TIMEOUT_SECONDS = 5;

    private final Platform platform;
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final PluginLogger logger;
    private final LocaleManager localeManager;
    private final boolean debugMode;

    PunishmentExecutor(Platform platform, HttpClientHolder httpClientHolder, Cache cache,
                       PluginLogger logger, LocaleManager localeManager, boolean debugMode) {
        this.platform = platform;
        this.httpClientHolder = httpClientHolder;
        this.cache = cache;
        this.logger = logger;
        this.localeManager = localeManager;
        this.debugMode = debugMode;
    }

    void processPendingPunishment(SyncResponse.PendingPunishment pending) {
        String playerUuid = pending.getMinecraftUuid();
        String username = pending.getUsername();
        SimplePunishment punishment = pending.getPunishment();

        if (debugMode) {
            logger.info(String.format("Processing pending punishment %s for %s - Type: '%s', Ordinal: %d, isBan: %s, isMute: %s, isKick: %s",
                    punishment.getId(), username, punishment.getType(), punishment.getOrdinal(),
                    punishment.isBan(), punishment.isMute(), punishment.isKick()));
        }

        platform.runOnGameThread(() -> {
            boolean success = executePunishment(playerUuid, username, punishment);
            acknowledgePunishment(punishment.getId(), playerUuid, success);
        });
    }

    void processModifiedPunishment(SyncResponse.ModifiedPunishment modified) {
        String playerUuid = modified.getMinecraftUuid();
        String username = modified.getUsername();
        SyncResponse.PunishmentWithModifications punishment = modified.getPunishment();

        platform.runOnMainThread(() -> {
            for (SyncResponse.PunishmentModification mod : punishment.getModifications()) {
                applyModification(playerUuid, username, punishment.getId(), mod);
            }
        });
    }

    // ── Punishment execution ─────────────────────────────────────────────

    private boolean executePunishment(String playerUuid, String username, SimplePunishment punishment) {
        try {
            UUID uuid = UUID.fromString(playerUuid);
            if (punishment.isBan()) return executeBan(uuid, username, punishment);
            if (punishment.isMute()) return executeMute(uuid, username, punishment);
            if (punishment.isKick()) return executeKick(uuid, username, punishment);

            logger.warning(String.format("Unknown punishment type for %s - Type: '%s', Ordinal: %d",
                    username, punishment.getType(), punishment.getOrdinal()));
            return false;
        } catch (Exception e) {
            logger.severe("Error executing punishment: " + e.getMessage());
            return false;
        }
    }

    private boolean executeMute(UUID uuid, String username, SimplePunishment punishment) {
        try {
            cache.cacheMute(uuid, punishment);
            platform.broadcast(PunishmentMessages.formatPunishmentBroadcast(username, punishment, "muted", localeManager));
            if (debugMode) logger.info("Executed mute for " + username + ": " + punishment.getDescription());
            return true;
        } catch (Exception e) {
            logger.severe("Error executing mute: " + e.getMessage());
            return false;
        }
    }

    private boolean executeBan(UUID uuid, String username, SimplePunishment punishment) {
        try {
            AbstractPlayer player = platform.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                platform.kickPlayer(player, PunishmentMessages.formatBanMessage(punishment, localeManager, PunishmentMessages.MessageContext.SYNC));
            }
            platform.broadcast(PunishmentMessages.formatPunishmentBroadcast(username, punishment, "banned", localeManager));
            if (debugMode) logger.info("Executed ban for " + username + ": " + punishment.getDescription());
            return true;
        } catch (Exception e) {
            logger.severe("Error executing ban: " + e.getMessage());
            return false;
        }
    }

    private boolean executeKick(UUID uuid, String username, SimplePunishment punishment) {
        try {
            AbstractPlayer player = platform.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                if (debugMode) logger.info("Player " + username + " is not online, kick ignored");
                return true;
            }
            platform.kickPlayer(player, PunishmentMessages.formatKickMessage(punishment, localeManager, PunishmentMessages.MessageContext.SYNC));
            platform.broadcast(PunishmentMessages.formatPunishmentBroadcast(username, punishment, "kicked", localeManager));
            if (debugMode) logger.info("Executed kick for " + username + ": " + punishment.getDescription());
            return true;
        } catch (Exception e) {
            logger.severe("Error executing kick: " + e.getMessage());
            return false;
        }
    }

    // ── Modification handling ────────────────────────────────────────────

    private void applyModification(String playerUuid, String username, String punishmentId,
                                   SyncResponse.PunishmentModification modification) {
        try {
            UUID uuid = UUID.fromString(playerUuid);
            Modification.Type modType;
            try {
                modType = Modification.Type.valueOf(modification.getType());
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown modification type: " + modification.getType());
                return;
            }

            switch (modType) {
                case MANUAL_PARDON, SYSTEM_PARDON, APPEAL_ACCEPT ->
                    handlePardon(uuid, username, punishmentId);
                case MANUAL_DURATION_CHANGE, APPEAL_DURATION_CHANGE ->
                    handleDurationChange(uuid, username, punishmentId, modification.getEffectiveDuration(), modification.getTimestamp());
                default -> {}
            }
        } catch (Exception e) {
            logger.severe("Error handling punishment modification: " + e.getMessage());
        }
    }

    private void handlePardon(UUID uuid, String username, String punishmentId) {
        boolean matched = removeCachedPunishmentById(uuid, punishmentId);
        if (!matched) {
            // Unknown which punishment was pardoned — clear both caches as fallback
            cache.removeMute(uuid);
            cache.removeBan(uuid);
        }
        if (debugMode) logger.info("Pardoned punishment " + punishmentId + " for " + username);
    }

    /** @return true if a cached mute or ban matched the punishment ID and was removed */
    private boolean removeCachedPunishmentById(UUID uuid, String punishmentId) {
        boolean removed = false;
        SimplePunishment cachedMute = cache.getSimpleMute(uuid);
        if (cachedMute != null && cachedMute.getId().equals(punishmentId)) {
            cache.removeMute(uuid);
            removed = true;
        }
        SimplePunishment cachedBan = cache.getSimpleBan(uuid);
        if (cachedBan != null && cachedBan.getId().equals(punishmentId)) {
            cache.removeBan(uuid);
            removed = true;
        }
        return removed;
    }

    private void handleDurationChange(UUID uuid, String username, String punishmentId,
                                       Long newDuration, Long modificationTimestamp) {
        long baseTime = (modificationTimestamp != null) ? modificationTimestamp : System.currentTimeMillis();
        Long newExpiration = (newDuration != null && newDuration > 0) ? baseTime + newDuration : null;

        updateCachedExpiration(cache.getSimpleMute(uuid), punishmentId, newExpiration);
        updateCachedExpiration(cache.getSimpleBan(uuid), punishmentId, newExpiration);

        if (debugMode) {
            String durationStr = newDuration != null ? newDuration + " ms" : "permanent";
            logger.info("Updated punishment " + punishmentId + " duration for " + username + " to " + durationStr);
        }
    }

    private void updateCachedExpiration(SimplePunishment cached, String punishmentId, Long newExpiration) {
        if (cached != null && cached.getId().equals(punishmentId)) {
            cached.setExpiration(newExpiration);
        }
    }

    // ── Acknowledgment ──────────────────────────────────────────────────

    private void acknowledgePunishment(String punishmentId, String playerUuid, boolean success) {
        PunishmentAcknowledgeRequest request = new PunishmentAcknowledgeRequest(
                punishmentId, playerUuid, Instant.now().toString(), success, null);

        httpClientHolder.getClient().acknowledgePunishment(request)
                .thenAccept(response -> {
                    if (debugMode) logger.info("Acknowledged punishment " + punishmentId + ": " + (success ? "SUCCESS" : "FAILED"));
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof PanelUnavailableException) {
                        logger.warning("Failed to acknowledge punishment " + punishmentId + ": Panel temporarily unavailable");
                    } else {
                        logger.warning("Failed to acknowledge punishment " + punishmentId + ": " + throwable.getMessage());
                    }
                    return null;
                })
                .orTimeout(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
