package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.ClaimTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.Constants;
import lombok.RequiredArgsConstructor;

import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class ClaimTicketCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @Command("tclaim")
    @Description("Link an unlinked ticket to your account")
    @PlayerOnly
    public void claimTicket(CommandActor actor, String ticketId) {
        AbstractPlayer player = platform.getAbstractPlayer(actor.uniqueId(), false);

        actor.reply(localeManager.getMessage("messages.claiming_ticket", mapOf("ticketId", ticketId)));

        ClaimTicketRequest request = new ClaimTicketRequest(
            ticketId,
            player.getUuid().toString(),
            player.getUsername()
        );

        httpClient.claimTicket(request).thenAccept(response -> {
            if (response.isSuccess()) {
                actor.reply(localeManager.getMessage("messages.ticket_claimed_success",
                    mapOf("ticketId", ticketId, "subject", response.getSubject() != null ? response.getSubject() : Constants.UNKNOWN)));

                String ticketUrl = panelUrl + "/ticket/" + ticketId;
                ticketUtil.sendClickableTicketMessage(actor, platform, localeManager,
                        localeManager.getMessage("messages.view_ticket_label"), ticketUrl, ticketId);
            } else actor.reply(localeManager.getMessage("messages.ticket_claim_failed",
                    mapOf("error", localeManager.sanitizeErrorMessage(response.getMessage()))));
        }).exceptionally(throwable -> {
            String errorMessage = throwable.getMessage();
            if (throwable.getCause() != null) errorMessage = throwable.getCause().getMessage();
            actor.reply(localeManager.getMessage("messages.ticket_claim_failed",
                mapOf("error", localeManager.sanitizeErrorMessage(errorMessage))));
            return null;
        });
    }
}
