package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.PunishmentTypeClassifier;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ConfigManager;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.impl.menus.StandingMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@RequiredArgsConstructor
public class StandingCommand {
    private static final long COOLDOWN_MS = 60_000;
    private static final String COOLDOWN_KEY = "standing";
    private static final int PREVIEW_TYPE_ORDINAL = PunishmentTypeClassifier.ORDINAL_BLACKLIST + 1;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final LocaleManager localeManager;
    private final ConfigManager configManager;
    private final Cache cache;

    @Command("standing")
    @Description("View your current standing and punishment history")
    @PlayerOnly
    public void standing(CommandActor actor) {
        UUID uuid = actor.uniqueId();

        if (!checkCooldown(actor, uuid)) return;

        replyOnMainThread(actor, "standing.loading");

        ModlHttpClient httpClient = httpClientHolder.getClient();
        httpClient.getPlayerProfile(uuid).thenCompose(profileResponse -> {
            if (profileResponse == null) {
                replyOnMainThread(actor, "standing.error");
                return CompletableFuture.completedFuture(null);
            }

            Account account = profileResponse.getProfile();
            if (account == null) {
                replyOnMainThread(actor, "standing.error");
                return CompletableFuture.completedFuture(null);
            }

            CompletableFuture<PunishmentPreviewResponse> previewFuture = loadPreviewData(httpClient, uuid);
            CompletableFuture<Map<Integer, PunishmentTypesResponse.PunishmentTypeData>> typesFuture = loadPunishmentTypes(httpClient);

            return previewFuture.thenCombine(typesFuture,
                    (previewData, typesByOrdinal) -> new StandingData(account, previewData, typesByOrdinal));
        }).thenAccept(data -> {
            if (data == null) return;
            platform.runOnMainThread(() -> {
                if (displayStandingMenu(httpClient, uuid, data)) {
                    CachedProfile profile = cache.getPlayerProfile(uuid);
                    if (profile != null) profile.getCooldowns().set(COOLDOWN_KEY);
                } else {
                    actor.reply(localeManager.getMessage("standing.error"));
                }
            });
        }).exceptionally(throwable -> {
            replyOnMainThread(actor, "standing.error");
            return null;
        });
    }

    private boolean checkCooldown(CommandActor actor, UUID uuid) {
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile == null) return true;
        if (!profile.getCooldowns().isOnCooldown(COOLDOWN_KEY, COOLDOWN_MS)) return true;

        long remaining = profile.getCooldowns().getRemainingMs(COOLDOWN_KEY, COOLDOWN_MS);
        int seconds = (int) Math.ceil(remaining / 1000.0);
        platform.runOnMainThread(() -> actor.reply(localeManager.getMessage("standing.cooldown",
                mapOf("seconds", String.valueOf(seconds)))));
        return false;
    }

    private boolean displayStandingMenu(ModlHttpClient httpClient, UUID uuid, StandingData data) {
        AbstractPlayer abstractPlayer = platform.getAbstractPlayer(uuid, false);
        CirrusPlayerWrapper player = platform.getPlayerWrapper(uuid);
        if (abstractPlayer == null || player == null) {
            return false;
        }

        StandingMenu menu = createMenu(httpClient, uuid, abstractPlayer.getUsername(),
                data.getAccount(), data.getPreviewData(), data.getTypesByOrdinal());
        displayMenu(menu, player);
        return true;
    }

    protected StandingMenu createMenu(ModlHttpClient httpClient, UUID uuid, String username, Account account,
                                      PunishmentPreviewResponse previewData,
                                      Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal) {
        return new StandingMenu(
                platform, httpClient, uuid, username,
                account, previewData, configManager.getStandingGuiConfig(), localeManager, typesByOrdinal);
    }

    protected void displayMenu(StandingMenu menu, CirrusPlayerWrapper player) {
        menu.display(player);
    }

    private void replyOnMainThread(CommandActor actor, String messagePath) {
        platform.runOnMainThread(() -> actor.reply(localeManager.getMessage(messagePath)));
    }

    private CompletableFuture<PunishmentPreviewResponse> loadPreviewData(ModlHttpClient httpClient, UUID uuid) {
        return httpClient.getPunishmentPreview(uuid, PREVIEW_TYPE_ORDINAL).handle((preview, throwable) -> {
            if (throwable != null) return null;
            return (preview != null && preview.isSuccess()) ? preview : null;
        });
    }

    private CompletableFuture<Map<Integer, PunishmentTypesResponse.PunishmentTypeData>> loadPunishmentTypes(ModlHttpClient httpClient) {
        return httpClient.getPunishmentTypes().handle((typesResponse, throwable) -> {
            Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();
            if (throwable != null) return typesByOrdinal;
            if (typesResponse != null && typesResponse.isSuccess() && typesResponse.getData() != null)
                for (PunishmentTypesResponse.PunishmentTypeData type : typesResponse.getData())
                    typesByOrdinal.put(type.getOrdinal(), type);
            return typesByOrdinal;
        });
    }

    @Value
    private static class StandingData {
        Account account;
        PunishmentPreviewResponse previewData;
        Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal;
    }
}
