package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PunishmentMessages;

import static gg.modl.minecraft.core.util.Java8Collections.orTimeout;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import gg.modl.minecraft.core.util.PluginLogger;

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
            CachedProfile profile = cache.getPlayerProfile(uuid);
            if (profile != null) profile.setActiveMute(punishment);
            platform.broadcast(PunishmentMessages.formatPunishmentBroadcast(username, punishment, localeManager));
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
            platform.broadcast(PunishmentMessages.formatPunishmentBroadcast(username, punishment, localeManager));
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
            platform.broadcast(PunishmentMessages.formatPunishmentBroadcast(username, punishment, localeManager));
            if (debugMode) logger.info("Executed kick for " + username + ": " + punishment.getDescription());
            return true;
        } catch (Exception e) {
            logger.severe("Error executing kick: " + e.getMessage());
            return false;
        }
    }

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

            if (modType == Modification.Type.MANUAL_PARDON || modType == Modification.Type.SYSTEM_PARDON || modType == Modification.Type.APPEAL_ACCEPT) {
                handlePardon(uuid, username, punishmentId);
            } else if (modType == Modification.Type.MANUAL_DURATION_CHANGE || modType == Modification.Type.APPEAL_DURATION_CHANGE) {
                handleDurationChange(uuid, username, punishmentId, modification.getEffectiveDuration(), modification.getTimestamp());
            }
        } catch (Exception e) {
            logger.severe("Error handling punishment modification: " + e.getMessage());
        }
    }

    private void handlePardon(UUID uuid, String username, String punishmentId) {
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null) {
            boolean matched = removeCachedPunishmentById(profile, punishmentId);
            if (!matched) {
                profile.setActiveMute(null);
                profile.setActiveBan(null);
            }
        }
        if (debugMode) logger.info("Pardoned punishment " + punishmentId + " for " + username);
    }

    private boolean removeCachedPunishmentById(CachedProfile profile, String punishmentId) {
        boolean removed = false;
        SimplePunishment cachedMute = profile.getActiveMute();
        if (cachedMute != null && cachedMute.getId().equals(punishmentId)) {
            profile.setActiveMute(null);
            removed = true;
        }
        SimplePunishment cachedBan = profile.getActiveBan();
        if (cachedBan != null && cachedBan.getId().equals(punishmentId)) {
            profile.setActiveBan(null);
            removed = true;
        }
        return removed;
    }

    private void handleDurationChange(UUID uuid, String username, String punishmentId,
                                       Long newDuration, Long modificationTimestamp) {
        long baseTime = (modificationTimestamp != null) ? modificationTimestamp : System.currentTimeMillis();
        Long newExpiration = (newDuration != null && newDuration > 0) ? baseTime + newDuration : null;

        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null) {
            updateCachedExpiration(profile.getActiveMute(), punishmentId, newExpiration);
            updateCachedExpiration(profile.getActiveBan(), punishmentId, newExpiration);
        }

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

    private void acknowledgePunishment(String punishmentId, String playerUuid, boolean success) {
        PunishmentAcknowledgeRequest request = new PunishmentAcknowledgeRequest(
                punishmentId, playerUuid, Instant.now().toString(), null, success);

        orTimeout(httpClientHolder.getClient().acknowledgePunishment(request)
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
                }), ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
