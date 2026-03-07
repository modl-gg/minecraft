package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ConfigManager;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.cache.PlayerProfile;
import gg.modl.minecraft.core.impl.menus.StandingMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class StandingCommand extends BaseCommand {
    private static final long COOLDOWN_MS = 60_000;
    private static final String COOLDOWN_KEY = "standing";
    private static final int PREVIEW_TYPE_ORDINAL = 6;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final LocaleManager localeManager;
    private final ConfigManager configManager;
    private final Cache cache;

    @CommandAlias("%cmd_standing")
    @Description("View your current standing and punishment history")
    @Conditions("player")
    public void standing(CommandIssuer sender) {
        UUID uuid = sender.getUniqueId();

        if (!checkCooldown(sender, uuid)) return;

        PlayerProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null) profile.getCooldowns().set(COOLDOWN_KEY);

        sender.sendMessage(localeManager.getMessage("standing.loading"));

        ModlHttpClient httpClient = httpClientHolder.getClient();
        httpClient.getPlayerProfile(uuid).thenAccept(profileResponse -> {
            if (profileResponse == null) {
                sender.sendMessage(localeManager.getMessage("standing.error"));
                return;
            }

            Account account = profileResponse.getProfile();
            PunishmentPreviewResponse previewData = loadPreviewData(httpClient, uuid);
            Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = loadPunishmentTypes(httpClient);

            StandingMenu menu = new StandingMenu(
                platform, httpClient, uuid,
                platform.getAbstractPlayer(uuid, false).getUsername(),
                account, previewData, configManager.getStandingGuiConfig(), localeManager, typesByOrdinal);
            CirrusPlayerWrapper player = platform.getPlayerWrapper(uuid);
            menu.display(player);
        }).exceptionally(throwable -> {
            sender.sendMessage(localeManager.getMessage("standing.error"));
            return null;
        });
    }

    private boolean checkCooldown(CommandIssuer sender, UUID uuid) {
        PlayerProfile profile = cache.getPlayerProfile(uuid);
        if (profile == null) return true;
        if (!profile.getCooldowns().isOnCooldown(COOLDOWN_KEY, COOLDOWN_MS)) return true;

        long remaining = profile.getCooldowns().getRemainingMs(COOLDOWN_KEY, COOLDOWN_MS);
        int seconds = (int) Math.ceil(remaining / 1000.0);
        sender.sendMessage(localeManager.getMessage("standing.cooldown",
                Map.of("seconds", String.valueOf(seconds))));
        return false;
    }

    private PunishmentPreviewResponse loadPreviewData(ModlHttpClient httpClient, UUID uuid) {
        try {
            PunishmentPreviewResponse preview = httpClient.getPunishmentPreview(uuid, PREVIEW_TYPE_ORDINAL).join();
            return (preview != null && preview.isSuccess()) ? preview : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<Integer, PunishmentTypesResponse.PunishmentTypeData> loadPunishmentTypes(ModlHttpClient httpClient) {
        Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();
        try {
            PunishmentTypesResponse typesResponse = httpClient.getPunishmentTypes().join();
            if (typesResponse != null && typesResponse.isSuccess() && typesResponse.getData() != null)
                for (PunishmentTypesResponse.PunishmentTypeData type : typesResponse.getData())
                    typesByOrdinal.put(type.getOrdinal(), type);
        } catch (Exception ignored) {
        }
        return typesByOrdinal;
    }
}
