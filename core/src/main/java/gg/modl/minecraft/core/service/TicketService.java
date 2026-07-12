package gg.modl.minecraft.core.service;

import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.ClickableJsonMessage;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@RequiredArgsConstructor
public class TicketService {
    private static final long COOLDOWN_MS = 60_000;

    private final Cache cache;
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final LocaleManager localeManager;
    private final String panelUrl;

    public boolean checkCooldown(CommandActor actor, String ticketType) {
        UUID uuid = actor.uniqueId();
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile == null) return false;

        String cooldownKey = "ticket:" + ticketType;
        if (!profile.getCooldowns().isOnCooldown(cooldownKey, COOLDOWN_MS)) return false;

        long remainingMs = profile.getCooldowns().getRemainingMs(cooldownKey, COOLDOWN_MS);
        long remainingSeconds = remainingMs / 1000;
        actor.reply(localeManager.getMessage("messages.ticket_cooldown",
                mapOf("seconds", String.valueOf(remainingSeconds))));
        return true;
    }

    public void setCooldown(UUID uuid, String cooldownType) {
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null) profile.getCooldowns().set("ticket:" + cooldownType);
    }

    public void submitPlayerFormTicket(CommandActor actor, String ticketType, String displayType, String titlePrefix) {
        if (checkCooldown(actor, ticketType)) return;

        AbstractPlayer player = platform.getAbstractPlayer(actor.uniqueId(), false);
        String createdServer = platform.getPlayerServer(actor.uniqueId());
        String title = titlePrefix == null ? null : titlePrefix + player.getUsername();

        CreateTicketRequest request = CreateTicketRequest.builder()
            .creatorUuid(player.getUuid().toString())
            .type(ticketType)
            .creatorName(player.getUsername())
            .subject(title)
            .priority("normal")
            .createdServer(createdServer)
            .tags(listOf())
            .build();

        submitUnfinishedTicket(actor, request, displayType, ticketType);
    }

    public void submitFinishedTicket(CommandActor actor, CreateTicketRequest request, String ticketType, String cooldownType) {
        submitTicket(actor, ticketType, cooldownType, () -> httpClient.createTicket(request), true);
    }

    public void submitUnfinishedTicket(CommandActor actor, CreateTicketRequest request, String ticketType, String cooldownType) {
        submitTicket(actor, ticketType, cooldownType, () -> httpClient.createUnfinishedTicket(request), false);
    }

    public boolean denySelfReport(CommandActor actor, AbstractPlayer reporter, AbstractPlayer targetPlayer) {
        if (reporter == null || targetPlayer == null) return false;
        if (!reporter.getUuid().equals(targetPlayer.getUuid())) return false;

        actor.reply(localeManager.getMessage("messages.cannot_report_self"));
        return true;
    }

    private void submitTicket(CommandActor actor, String ticketType, String cooldownType,
                              Supplier<CompletableFuture<CreateTicketResponse>> submitter, boolean finished) {
        String lowerTicketType = ticketType.toLowerCase();
        String startKey = finished ? "messages.submitting" : "messages.creating";
        actor.reply(localeManager.getMessage(startKey, mapOf("type", lowerTicketType)));

        CompletableFuture<CreateTicketResponse> future = submitter.get();
        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                handleTicketSuccess(actor, response, ticketType, cooldownType, lowerTicketType, finished);
            } else {
                handleTicketFailure(actor, ticketType, response.getMessage(), finished);
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
            else handleTicketFailure(actor, ticketType, throwable.getMessage(), finished);
            return null;
        });
    }

    private void handleTicketSuccess(CommandActor actor, CreateTicketResponse response, String ticketType,
                                     String cooldownType, String lowerTicketType, boolean finished) {
        setCooldown(actor.uniqueId(), cooldownType);

        String successKey = finished ? "messages.success" : "messages.created";
        actor.reply(localeManager.getMessage(successKey, mapOf("type", ticketType)));
        actor.reply(localeManager.getMessage("messages.ticket_id", mapOf("ticketId", response.getTicketId())));

        String ticketUrl = panelUrl + "/ticket/" + response.getTicketId();
        String label = finished
                ? localeManager.getMessage("messages.view_ticket_label")
                : localeManager.getMessage("messages.complete_form_label", mapOf("type", lowerTicketType));
        sendClickableTicketMessage(actor, label, ticketUrl, response.getTicketId());

        if (finished) actor.reply(localeManager.getMessage("messages.evidence_note"));
    }

    private void handleTicketFailure(CommandActor actor, String ticketType, String errorMessage, boolean finished) {
        String lowerTicketType = ticketType.toLowerCase();
        String failureKey = finished ? "messages.failed_submit" : "messages.failed_create";
        actor.reply(localeManager.getMessage(failureKey,
                mapOf("type", lowerTicketType, "error", localeManager.sanitizeErrorMessage(errorMessage))));
        actor.reply(localeManager.getMessage("messages.try_again"));
    }

    public void sendClickableTicketMessage(CommandActor actor, String message, String ticketUrl, String ticketId) {
        if (CommandUtil.isConsole(actor)) {
            actor.reply(localeManager.getMessage("messages.console_ticket_url", mapOf("message", message, "url", ticketUrl)));
            return;
        }

        String clickText = localeManager.getMessage("messages.click_to_view");
        String hoverText = localeManager.getMessage("messages.click_to_view_hover", mapOf("ticketId", ticketId));
        String json = ClickableJsonMessage.empty()
                .extra(ClickableJsonMessage.text("").color("gold"))
                .extra(ClickableJsonMessage.text(message + ": ").color("gray"))
                .extra(ClickableJsonMessage.text(clickText)
                        .color("aqua")
                        .underlined(true)
                        .openUrl(ticketUrl)
                        .hoverText(hoverText))
                .toJson();
        UUID senderUuid = actor.uniqueId();
        platform.runOnMainThread(() -> platform.sendJsonMessage(senderUuid, json));
    }
}
