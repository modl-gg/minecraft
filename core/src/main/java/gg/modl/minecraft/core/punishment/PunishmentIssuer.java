package gg.modl.minecraft.core.punishment;

import gg.modl.minecraft.api.http.response.PunishmentCreateResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.PluginServices;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import revxrsal.commands.command.CommandActor;

import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class PunishmentIssuer {
    private final Platform platform;
    private final LocaleManager localeManager;

    public PunishmentIssuer(Platform platform, LocaleManager localeManager) {
        this.platform = platform;
        this.localeManager = localeManager;
    }

    public void issue(CommandActor actor, CompletableFuture<PunishmentCreateResponse> future,
                      String typeName, String targetName, Long confirmedDuration) {
        future.thenAccept(response -> {
            if (response.isSuccess()) {
                LocaleManager.PunishmentMessageBuilder builder = localeManager.punishment()
                    .type(typeName)
                    .target(targetName)
                    .punishmentId(response.getPunishmentId());
                if (confirmedDuration != null && confirmedDuration > 0) builder.duration(confirmedDuration);
                actor.reply(builder.get("general.punishment_issued"));

                if (actor.uniqueId() != null && response.getPunishmentId() != null)
                    platform.runOnMainThread(() ->
                        PluginServices.punishmentActions().sendPunishmentActions(actor.uniqueId(), response.getPunishmentId()));
            } else actor.reply(localeManager.getPunishmentMessage("general.punishment_error",
                    mapOf("error", localeManager.sanitizeErrorMessage(response.getMessage()))));
        }).exceptionally(throwable -> CommandUtil.handleApiError(actor, throwable, localeManager));
    }
}
