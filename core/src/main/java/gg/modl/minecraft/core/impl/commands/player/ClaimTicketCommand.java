package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.ClaimTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.Constants;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class ClaimTicketCommand extends BaseCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @CommandAlias("%cmd_tclaim")
    @Description("Link an unlinked ticket to your account")
    @Syntax("<ticket-id>")
    @Conditions("player")
    public void claimTicket(CommandIssuer sender, String ticketId) {
        AbstractPlayer player = platform.getAbstractPlayer(sender.getUniqueId(), false);

        sender.sendMessage(localeManager.getMessage("messages.claiming_ticket", Map.of("ticketId", ticketId)));

        ClaimTicketRequest request = new ClaimTicketRequest(
            ticketId,
            player.getUuid().toString(),
            player.getUsername()
        );

        httpClient.claimTicket(request).thenAccept(response -> {
            if (response.isSuccess()) {
                sender.sendMessage(localeManager.getMessage("messages.ticket_claimed_success",
                    Map.of("ticketId", ticketId, "subject", response.getSubject() != null ? response.getSubject() : Constants.UNKNOWN)));

                String ticketUrl = panelUrl + "/ticket/" + ticketId;
                ticketUtil.sendClickableTicketMessage(sender, platform, localeManager,
                        localeManager.getMessage("messages.view_ticket_label"), ticketUrl, ticketId);
            } else sender.sendMessage(localeManager.getMessage("messages.ticket_claim_failed",
                    Map.of("error", localeManager.sanitizeErrorMessage(response.getMessage()))));
        }).exceptionally(throwable -> {
            String errorMessage = throwable.getMessage();
            if (throwable.getCause() != null) errorMessage = throwable.getCause().getMessage();
            sender.sendMessage(localeManager.getMessage("messages.ticket_claim_failed",
                Map.of("error", localeManager.sanitizeErrorMessage(errorMessage))));
            return null;
        });
    }
}
