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

        return ClickableJsonMessage.empty()
                .extra(ClickableJsonMessage.text("Punishment #" + punishmentId + ": ").color("yellow"))
                .extra(ClickableJsonMessage.text("[Modify]")
                        .color("gold")
                        .runCommand("/" + configuredCommandPath + " modify " + punishmentId)
                        .hoverText("Click to modify this punishment"))
                .extra(ClickableJsonMessage.text(" "))
                .extra(ClickableJsonMessage.text("[Link Evidence]")
                        .color("aqua")
                        .runCommand("/" + configuredCommandPath + " link-evidence " + punishmentId)
                        .hoverText("Click to link a URL as evidence"))
                .extra(ClickableJsonMessage.text(" "))
                .extra(ClickableJsonMessage.text("[Upload Evidence]")
                        .color("green")
                        .runCommand("/" + configuredCommandPath + " upload-evidence " + punishmentId)
                        .hoverText("Click to upload files as evidence"))
                .toJson();
    }
}
