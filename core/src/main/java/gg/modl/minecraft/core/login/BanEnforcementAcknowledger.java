package gg.modl.minecraft.core.login;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.util.PluginLogger;

import java.time.Instant;

public final class BanEnforcementAcknowledger {
    private final HttpClientHolder httpClientHolder;
    private final PluginLogger logger;
    private final boolean debugMode;

    public BanEnforcementAcknowledger(HttpClientHolder httpClientHolder, PluginLogger logger, boolean debugMode) {
        this.httpClientHolder = httpClientHolder;
        this.logger = logger;
        this.debugMode = debugMode;
    }

    public void acknowledge(SimplePunishment ban, String playerUuid) {
        try {
            PunishmentAcknowledgeRequest request = new PunishmentAcknowledgeRequest(
                    ban.getId(),
                    playerUuid,
                    Instant.now().toString(),
                    null,
                    true
            );

            ModlHttpClient httpClient = httpClientHolder.getClient();
            httpClient.acknowledgePunishment(request).thenAccept(response -> {
                if (debugMode) logger.info("Successfully acknowledged ban enforcement for punishment " + ban.getId());
            }).exceptionally(throwable -> {
                logger.severe("Failed to acknowledge ban enforcement for punishment " + ban.getId() + ": " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.severe("Error acknowledging ban enforcement for punishment " + ban.getId() + ": " + e.getMessage());
        }
    }
}
