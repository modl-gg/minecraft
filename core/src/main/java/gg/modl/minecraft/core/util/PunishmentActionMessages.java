package gg.modl.minecraft.core.util;

import gg.modl.minecraft.core.Platform;

import java.util.UUID;

public final class PunishmentActionMessages {
    private static volatile String commandPath;

    private PunishmentActionMessages() {}

    public static void setCommandPath(String commandPath) {
        PunishmentActionMessages.commandPath = commandPath;
    }

    public static void sendPunishmentActions(Platform platform, UUID playerUuid, String punishmentId) {
        String json = buildActionButtonsJson(punishmentId);
        if (json == null) return;
        platform.sendJsonMessage(playerUuid, json);
    }

    private static String buildActionButtonsJson(String punishmentId) {
        String configuredCommandPath = commandPath;
        if (configuredCommandPath == null || configuredCommandPath.trim().isEmpty()) return null;

        return String.format(
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"Punishment #%s: \",\"color\":\"yellow\"}," +
            "{\"text\":\"[Modify]\",\"color\":\"gold\"," +
             "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/%s modify %s\"}," +
             "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to modify this punishment\"}}," +
            "{\"text\":\" \"}," +
            "{\"text\":\"[Link Evidence]\",\"color\":\"aqua\"," +
             "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/%s link-evidence %s\"}," +
             "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to link a URL as evidence\"}}," +
            "{\"text\":\" \"}," +
            "{\"text\":\"[Upload Evidence]\",\"color\":\"green\"," +
             "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/%s upload-evidence %s\"}," +
             "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to upload files as evidence\"}}" +
            "]}",
            punishmentId, configuredCommandPath, punishmentId, configuredCommandPath, punishmentId,
            configuredCommandPath, punishmentId
        );
    }
}
