package gg.modl.minecraft.core.util;

import gg.modl.minecraft.core.Platform;

import java.util.UUID;

public class PunishmentActionMessages {
    public static void sendPunishmentActions(Platform platform, UUID playerUuid, String punishmentId) {
        String json = buildActionButtonsJson(punishmentId);
        platform.sendJsonMessage(playerUuid, json);
    }

    private static String buildActionButtonsJson(String punishmentId) {
        return String.format(
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"Punishment #%s: \",\"color\":\"yellow\"}," +
            "{\"text\":\"[Modify]\",\"color\":\"gold\"," +
             "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/modl:punishment-action modify %s\"}," +
             "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to modify this punishment\"}}," +
            "{\"text\":\" \"}," +
            "{\"text\":\"[Link Evidence]\",\"color\":\"aqua\"," +
             "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/modl:punishment-action link-evidence %s\"}," +
             "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to link a URL as evidence\"}}," +
            "{\"text\":\" \"}," +
            "{\"text\":\"[Upload Evidence]\",\"color\":\"green\"," +
             "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/modl:punishment-action upload-evidence %s\"}," +
             "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to upload files as evidence\"}}" +
            "]}",
            punishmentId, punishmentId, punishmentId, punishmentId
        );
    }
}
