package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.Staff2faService;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class VerifyCommand extends BaseCommand {
    private static final Logger logger = Logger.getLogger(VerifyCommand.class.getName());
    private static final String VERIFY_LINK_JSON =
            "{\"text\":\"%s\",\"color\":\"green\",\"bold\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Click to open verification page\"}}";

    private final Platform platform;
    private final LocaleManager localeManager;
    private final Staff2faService staff2faService;
    private final HttpClientHolder httpClientHolder;

    @CommandAlias("%cmd_verify")
    @Description("Verify your identity for staff 2FA")
    @Conditions("player|staff_no2fa")
    public void verify(CommandIssuer sender) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("verify.only_players"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        if (!staff2faService.isEnabled()) {
            sender.sendMessage(localeManager.getMessage("verify.disabled"));
            return;
        }
        if (staff2faService.isAuthenticated(senderUuid)) {
            sender.sendMessage(localeManager.getMessage("verify.already_verified"));
            return;
        }

        AbstractPlayer player = platform.getPlayer(senderUuid);
        if (player == null) {
            sender.sendMessage(localeManager.getMessage("verify.only_players"));
            return;
        }

        httpClientHolder.getClient().generateStaff2faToken(senderUuid.toString(), player.getIpAddress()).thenAccept(response -> {
            sender.sendMessage(localeManager.getMessage("verify.header"));
            sender.sendMessage(localeManager.getMessage("verify.instructions"));

            String jsonMessage = String.format(VERIFY_LINK_JSON,
                    localeManager.getMessage("verify.click_here"), response.getVerifyUrl());
            platform.sendJsonMessage(senderUuid, jsonMessage);

            sender.sendMessage(localeManager.getMessage("verify.footer"));
        }).exceptionally(ex -> {
            logger.warning("Failed to generate 2FA token for " + senderUuid + ": " + ex.getMessage());
            sender.sendMessage(localeManager.getMessage("verify.error"));
            return null;
        });
    }
}
