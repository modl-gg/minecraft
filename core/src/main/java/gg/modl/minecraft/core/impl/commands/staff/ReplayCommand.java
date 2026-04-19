package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@Command("replay")
@StaffOnly
@RequiredArgsConstructor
public class ReplayCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    public void help(CommandActor actor) {
        for (String line : localeManager.getMessageList("replay.usage")) {
            actor.reply(line);
        }
    }

    @Subcommand("status")
    @Description("Check replay recording status for a player")
    public void status(CommandActor actor, @revxrsal.commands.annotation.Optional @Named("player") String targetName) {
        if (targetName == null) targetName = "";
        if (!PermissionUtil.hasPermission(actor, cache, Permissions.MOD_ACTIONS)) {
            actor.reply(localeManager.getMessage("general.no_permission"));
            return;
        }

        ReplayService replayService = platform.getReplayService();
        if (replayService == null) {
            actor.reply(localeManager.getMessage("replay.not_available"));
            return;
        }

        UUID targetUuid = resolveTargetUuid(actor, targetName);
        if (targetUuid == null) return;

        String resolvedName = resolveTargetName(targetUuid, targetName);
        boolean available = replayService.isReplayAvailable(targetUuid);
        String key = available ? "replay.status_recording" : "replay.status_not_recording";
        actor.reply(localeManager.getMessage(key, mapOf("player", resolvedName)));
    }

    @Subcommand("capture")
    @Description("Capture and upload a replay for a player")
    public void capture(CommandActor actor, @revxrsal.commands.annotation.Optional @Named("player") String targetName) {
        if (targetName == null) targetName = "";
        if (!PermissionUtil.hasPermission(actor, cache, Permissions.MOD_ACTIONS)) {
            actor.reply(localeManager.getMessage("general.no_permission"));
            return;
        }

        ReplayService replayService = platform.getReplayService();
        if (replayService == null) {
            actor.reply(localeManager.getMessage("replay.not_available"));
            return;
        }

        UUID targetUuid = resolveTargetUuid(actor, targetName);
        if (targetUuid == null) return;

        String resolvedName = resolveTargetName(targetUuid, targetName);

        if (!replayService.isReplayAvailable(targetUuid)) {
            actor.reply(localeManager.getMessage("replay.no_active_recording", mapOf("player", resolvedName)));
            return;
        }

        actor.reply(localeManager.getMessage("replay.capturing", mapOf("player", resolvedName)));

        replayService.captureReplay(targetUuid, resolvedName).thenAccept(replayId -> {
            if (replayId != null) {
                String replayLink = panelUrl + "/replay?id=" + replayId;
                actor.reply(localeManager.getMessage("replay.capture_success", mapOf("player", resolvedName)));
                if (actor.uniqueId() != null) {
                    String clickText = localeManager.getMessage("replay.click_to_view");
                    String hoverText = localeManager.getMessage("replay.click_to_view_hover");
                    String json = String.format(
                            "{\"text\":\"\",\"extra\":["
                            + "{\"text\":\"\",\"color\":\"gold\"},"
                            + "{\"text\":\"%s\",\"color\":\"aqua\",\"underlined\":true,"
                            + "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"},"
                            + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"%s\"}}]}",
                            clickText.replace("\"", "\\\""), replayLink, hoverText.replace("\"", "\\\""));
                    platform.runOnMainThread(() -> platform.sendJsonMessage(actor.uniqueId(), json));
                } else {
                    actor.reply(localeManager.getMessage("replay.capture_link", mapOf("url", replayLink)));
                }
            } else {
                actor.reply(localeManager.getMessage("replay.capture_failed", mapOf("player", resolvedName)));
            }
        });
    }

    private UUID resolveTargetUuid(CommandActor actor, String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            if (actor.uniqueId() == null) {
                actor.reply(localeManager.getMessage("replay.console_specify_player"));
                return null;
            }
            return actor.uniqueId();
        }

        AbstractPlayer target = platform.getAbstractPlayer(targetName, false);
        if (target == null) {
            actor.reply(localeManager.getMessage("general.player_not_found"));
            return null;
        }
        return target.getUuid();
    }

    private String resolveTargetName(UUID uuid, String fallback) {
        AbstractPlayer player = platform.getAbstractPlayer(uuid, false);
        if (player != null) return player.getUsername();
        return fallback != null && !fallback.isEmpty() ? fallback : uuid.toString();
    }
}
