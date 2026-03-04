package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.Staff2faService;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Command for staff to verify their identity via 2FA.
 * Sends a clickable panel link for completing verification.
 */
@RequiredArgsConstructor
public class VerifyCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final Staff2faService staff2faService;
    private final String panelUrl;

    @CommandAlias("%cmd_verify")
    @Description("Verify your identity for staff 2FA")
    @Conditions("player|staff")
    public void verify(CommandIssuer sender) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("verify.only_players"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        // Check if 2FA is enabled
        if (!staff2faService.isEnabled()) {
            sender.sendMessage(localeManager.getMessage("verify.disabled"));
            return;
        }

        // Check if already authenticated
        if (staff2faService.isAuthenticated(senderUuid)) {
            sender.sendMessage(localeManager.getMessage("verify.already_verified"));
            return;
        }

        // Build verification link
        String verifyLink = panelUrl + "/verify?uuid=" + senderUuid.toString();

        // Send clickable verification message
        sender.sendMessage(localeManager.getMessage("verify.header"));
        sender.sendMessage(localeManager.getMessage("verify.instructions"));

        // Send the link as a clickable JSON message if possible, otherwise plain text
        String jsonMessage = "{\"text\":\""
                + localeManager.getMessage("verify.click_here")
                + "\",\"color\":\"green\",\"bold\":true,\"clickEvent\":{\"action\":\"open_url\",\"value\":\""
                + verifyLink
                + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Click to open verification page\"}}";

        platform.sendJsonMessage(senderUuid, jsonMessage);

        sender.sendMessage(localeManager.getMessage("verify.footer"));
    }
}
