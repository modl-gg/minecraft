package gg.modl.minecraft.core.punishment;

import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.util.ClickableJsonMessage;

import java.util.UUID;

public final class PunishmentActionMessageService {
    private final Platform platform;
    private final String commandPath;

    public PunishmentActionMessageService(Platform platform, String commandPath) {
        this.platform = platform;
        this.commandPath = commandPath;
    }

    public void sendPunishmentActions(UUID playerUuid, String punishmentId) {
        String json = buildActionButtonsJson(punishmentId);
        if (json == null) return;
        platform.sendJsonMessage(playerUuid, json);
    }

    private String buildActionButtonsJson(String punishmentId) {
        if (commandPath == null || commandPath.trim().isEmpty()) return null;

        return ClickableJsonMessage.empty()
                .extra(ClickableJsonMessage.text("Punishment #" + punishmentId + ": ").color("yellow"))
                .extra(ClickableJsonMessage.text("[Modify]")
                        .color("gold")
                        .runCommand("/" + commandPath + " modify " + punishmentId)
                        .hoverText("Click to modify this punishment"))
                .extra(ClickableJsonMessage.text(" "))
                .extra(ClickableJsonMessage.text("[Link Evidence]")
                        .color("aqua")
                        .runCommand("/" + commandPath + " link-evidence " + punishmentId)
                        .hoverText("Click to link a URL as evidence"))
                .extra(ClickableJsonMessage.text(" "))
                .extra(ClickableJsonMessage.text("[Upload Evidence]")
                        .color("green")
                        .runCommand("/" + commandPath + " upload-evidence " + punishmentId)
                        .hoverText("Click to upload files as evidence"))
                .toJson();
    }
}
