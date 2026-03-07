package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.PlayerProfile;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TicketCommandUtil {
    private static final String CLICKABLE_TICKET_JSON =
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"\uD83D\uDCCB \",\"color\":\"gold\"}," +
            "{\"text\":\"%s: \",\"color\":\"gray\"}," +
            "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}";
    private static final long COOLDOWN_MS = 60_000;

    private final Cache cache;

    public TicketCommandUtil(Cache cache) {
        this.cache = cache;
    }

    public boolean checkCooldown(CommandIssuer sender, String ticketType, LocaleManager localeManager) {
        UUID uuid = sender.getUniqueId();
        PlayerProfile profile = cache.getPlayerProfile(uuid);
        if (profile == null) return false;

        String cooldownKey = "ticket:" + ticketType;
        if (!profile.getCooldowns().isOnCooldown(cooldownKey, COOLDOWN_MS)) return false;

        long remainingMs = profile.getCooldowns().getRemainingMs(cooldownKey, COOLDOWN_MS);
        long remainingSeconds = remainingMs / 1000;
        sender.sendMessage(localeManager.getMessage("messages.ticket_cooldown",
                Map.of("seconds", String.valueOf(remainingSeconds))));
        return true;
    }

    private void setCooldown(UUID uuid, String cooldownType) {
        PlayerProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null) profile.getCooldowns().set("ticket:" + cooldownType);
    }

    public void submitFinishedTicket(CommandIssuer sender, ModlHttpClient httpClient, Platform platform,
                                     LocaleManager localeManager, String panelUrl,
                                     CreateTicketRequest request, String ticketType, String cooldownType) {
        sender.sendMessage(localeManager.getMessage("messages.submitting", Map.of("type", ticketType.toLowerCase())));

        CompletableFuture<CreateTicketResponse> future = httpClient.createTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                setCooldown(sender.getUniqueId(), cooldownType);
                sender.sendMessage(localeManager.getMessage("messages.success", Map.of("type", ticketType)));
                sender.sendMessage(localeManager.getMessage("messages.ticket_id", Map.of("ticketId", response.getTicketId())));

                String ticketUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(sender, platform, localeManager,
                        localeManager.getMessage("messages.view_ticket_label"), ticketUrl, response.getTicketId());
                sender.sendMessage(localeManager.getMessage("messages.evidence_note"));
            } else {
                sender.sendMessage(localeManager.getMessage("messages.failed_submit", Map.of("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(response.getMessage()))));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            else {
                sender.sendMessage(localeManager.getMessage("messages.failed_submit", Map.of("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
            return null;
        });
    }

    public void submitUnfinishedTicket(CommandIssuer sender, ModlHttpClient httpClient, Platform platform,
                                       LocaleManager localeManager, String panelUrl,
                                       CreateTicketRequest request, String ticketType, String cooldownType) {
        sender.sendMessage(localeManager.getMessage("messages.creating", Map.of("type", ticketType.toLowerCase())));

        CompletableFuture<CreateTicketResponse> future = httpClient.createUnfinishedTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                setCooldown(sender.getUniqueId(), cooldownType);
                sender.sendMessage(localeManager.getMessage("messages.created", Map.of("type", ticketType)));
                sender.sendMessage(localeManager.getMessage("messages.ticket_id", Map.of("ticketId", response.getTicketId())));

                String formUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(sender, platform, localeManager,
                        localeManager.getMessage("messages.complete_form_label", Map.of("type", ticketType.toLowerCase())), formUrl, response.getTicketId());
            } else {
                sender.sendMessage(localeManager.getMessage("messages.failed_create", Map.of("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(response.getMessage()))));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            else {
                sender.sendMessage(localeManager.getMessage("messages.failed_create", Map.of("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
            return null;
        });
    }

    public void sendClickableTicketMessage(CommandIssuer sender, Platform platform, LocaleManager localeManager,
                                            String message, String ticketUrl, String ticketId) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("messages.console_ticket_url", Map.of("message", message, "url", ticketUrl)));
            return;
        }

        String clickableMessage = String.format(CLICKABLE_TICKET_JSON, message, ticketUrl, ticketId);
        UUID senderUuid = sender.getUniqueId();
        platform.runOnMainThread(() -> platform.sendJsonMessage(senderUuid, clickableMessage));
    }
}
