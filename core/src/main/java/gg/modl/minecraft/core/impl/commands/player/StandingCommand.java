package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.StandingGuiConfig;
import gg.modl.minecraft.core.impl.menus.StandingMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class StandingCommand extends BaseCommand {
    private static final long COOLDOWN_MS = 60_000;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final LocaleManager localeManager;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandAlias("%cmd_standing")
    @Description("View your current standing and punishment history")
    @Conditions("player")
    public void standing(CommandIssuer sender) {
        UUID uuid = sender.getUniqueId();

        long now = System.currentTimeMillis();
        Long lastUsed = cooldowns.get(uuid);
        if (lastUsed != null) {
            long remaining = COOLDOWN_MS - (now - lastUsed);
            if (remaining > 0) {
                int seconds = (int) Math.ceil(remaining / 1000.0);
                sender.sendMessage(localeManager.getMessage("standing.cooldown",
                        Map.of("seconds", String.valueOf(seconds))));
                return;
            }
        }
        cooldowns.put(uuid, now);

        sender.sendMessage(localeManager.getMessage("standing.loading"));

        ModlHttpClient httpClient = getHttpClient();

        // Fetch player profile (contains punishments)
        httpClient.getPlayerProfile(uuid).thenAccept(profileResponse -> {
            if (profileResponse == null || profileResponse.getProfile() == null) {
                sender.sendMessage(localeManager.getMessage("standing.error"));
                return;
            }

            Account account = profileResponse.getProfile();

            // Fetch standing status (social/gameplay) using any type ordinal
            PunishmentPreviewResponse previewData = null;
            try {
                previewData = httpClient.getPunishmentPreview(uuid, 6).join();
                if (previewData != null && !previewData.isSuccess()) {
                    previewData = null;
                }
            } catch (Exception ignored) {
                // Status data unavailable - menu will show "Unknown"
            }

            // Load punishment types for display names
            Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();
            try {
                PunishmentTypesResponse typesResponse = httpClient.getPunishmentTypes().join();
                if (typesResponse != null && typesResponse.isSuccess() && typesResponse.getData() != null) {
                    for (PunishmentTypesResponse.PunishmentTypeData type : typesResponse.getData()) {
                        typesByOrdinal.put(type.getOrdinal(), type);
                    }
                }
            } catch (Exception ignored) {
                // Will use fallback type detection
            }

            // Load config
            StandingGuiConfig guiConfig = StandingGuiConfig.load(
                    platform.getDataFolder().toPath(),
                    Logger.getLogger("MODL"));

            // Build and display menu on main thread
            PunishmentPreviewResponse finalPreviewData = previewData;
            platform.runOnMainThread(() -> {
                StandingMenu menu = new StandingMenu(
                        platform, httpClient, uuid,
                        platform.getAbstractPlayer(uuid, false).username(),
                        account, finalPreviewData, guiConfig, typesByOrdinal);
                CirrusPlayerWrapper player = platform.getPlayerWrapper(uuid);
                menu.display(player);
            });
        }).exceptionally(throwable -> {
            sender.sendMessage(localeManager.getMessage("standing.error"));
            return null;
        });
    }
}
