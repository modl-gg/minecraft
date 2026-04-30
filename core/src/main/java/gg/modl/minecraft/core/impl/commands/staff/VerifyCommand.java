package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffNo2fa;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.Staff2faService;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class VerifyCommand {
    private static final Logger logger = Logger.getLogger(VerifyCommand.class.getName());
    private static final String VERIFY_LINK_JSON =
            "{\"text\":\"%s\",\"color\":\"green\",\"bold\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Click to open verification page\"}}";

    private final Platform platform;
    private final LocaleManager localeManager;
    private final Staff2faService staff2faService;
    private final HttpClientHolder httpClientHolder;

    @Command("verify")
    @Description("Verify your identity for staff 2FA")
    @PlayerOnly @StaffNo2fa
    public void verify(CommandActor actor) {
        UUID senderUuid = actor.uniqueId();

        if (!staff2faService.isEnabled()) {
            actor.reply(localeManager.getMessage("verify.disabled"));
            return;
        }
        if (staff2faService.isAuthenticated(senderUuid)) {
            actor.reply(localeManager.getMessage("verify.already_verified"));
            return;
        }

        AbstractPlayer player = platform.getPlayer(senderUuid);
        if (player == null) {
            actor.reply(localeManager.getMessage("verify.only_players"));
            return;
        }

        httpClientHolder.getClient().generateStaff2faToken(senderUuid.toString(), player.getIpAddress()).thenAccept(response -> {
            actor.reply(localeManager.getMessage("verify.header"));
            actor.reply(localeManager.getMessage("verify.instructions"));

            String jsonMessage = String.format(VERIFY_LINK_JSON,
                    localeManager.getMessage("verify.click_here"), response.getVerifyUrl());
            platform.sendJsonMessage(senderUuid, jsonMessage);

            actor.reply(localeManager.getMessage("verify.footer"));
        }).exceptionally(ex -> {
            logger.warning("Failed to generate 2FA token for " + senderUuid + ": " + ex.getMessage());
            actor.reply(localeManager.getMessage("verify.error"));
            return null;
        });
    }
}
