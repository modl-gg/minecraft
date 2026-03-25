package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@CommandAlias("%cmd_replay")
@Conditions("staff")
@RequiredArgsConstructor
public class ReplayCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    @Default
    public void help(CommandIssuer sender) {
        for (String line : localeManager.getMessageList("replay.usage")) {
            sender.sendMessage(line);
        }
    }

    @Subcommand("status")
    @CommandCompletion("@players")
    @Description("Check replay recording status for a player")
    @Syntax("[player]")
    public void status(CommandIssuer sender, @Default String targetName) {
        if (!PermissionUtil.hasPermission(sender, cache, Permissions.MOD_ACTIONS)) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        ReplayService replayService = platform.getReplayService();
        if (replayService == null) {
            sender.sendMessage(localeManager.getMessage("replay.not_available"));
            return;
        }

        UUID targetUuid = resolveTargetUuid(sender, targetName);
        if (targetUuid == null) return;

        String resolvedName = resolveTargetName(targetUuid, targetName);
        boolean available = replayService.isReplayAvailable(targetUuid);
        String key = available ? "replay.status_recording" : "replay.status_not_recording";
        sender.sendMessage(localeManager.getMessage(key, mapOf("player", resolvedName)));
    }

    @Subcommand("capture")
    @CommandCompletion("@players")
    @Description("Capture and upload a replay for a player")
    @Syntax("[player]")
    public void capture(CommandIssuer sender, @Default String targetName) {
        if (!PermissionUtil.hasPermission(sender, cache, Permissions.MOD_ACTIONS)) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        ReplayService replayService = platform.getReplayService();
        if (replayService == null) {
            sender.sendMessage(localeManager.getMessage("replay.not_available"));
            return;
        }

        UUID targetUuid = resolveTargetUuid(sender, targetName);
        if (targetUuid == null) return;

        String resolvedName = resolveTargetName(targetUuid, targetName);

        if (!replayService.isReplayAvailable(targetUuid)) {
            sender.sendMessage(localeManager.getMessage("replay.no_active_recording", mapOf("player", resolvedName)));
            return;
        }

        sender.sendMessage(localeManager.getMessage("replay.capturing", mapOf("player", resolvedName)));

        replayService.captureReplay(targetUuid, resolvedName).thenAccept(replayId -> {
            if (replayId != null) {
                String replayLink = panelUrl + "/replay?id=" + replayId;
                sender.sendMessage(localeManager.getMessage("replay.capture_success", mapOf("player", resolvedName)));
                if (sender.isPlayer()) {
                    String json = String.format(
                            "{\"text\":\"\",\"extra\":["
                            + "{\"text\":\"\uD83C\uDFAC \",\"color\":\"gold\"},"
                            + "{\"text\":\"[Click to view replay]\",\"color\":\"aqua\",\"underlined\":true,"
                            + "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"},"
                            + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Open replay viewer\"}}]}",
                            replayLink);
                    platform.runOnMainThread(() -> platform.sendJsonMessage(sender.getUniqueId(), json));
                } else {
                    sender.sendMessage(localeManager.getMessage("replay.capture_link", mapOf("url", replayLink)));
                }
            } else {
                sender.sendMessage(localeManager.getMessage("replay.capture_failed", mapOf("player", resolvedName)));
            }
        });
    }

    private UUID resolveTargetUuid(CommandIssuer sender, String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            if (!sender.isPlayer()) {
                sender.sendMessage(localeManager.getMessage("replay.console_specify_player"));
                return null;
            }
            return sender.getUniqueId();
        }

        AbstractPlayer target = platform.getAbstractPlayer(targetName, false);
        if (target == null) {
            sender.sendMessage(localeManager.getMessage("general.player_not_found"));
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
