package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.*;

public class TicketCommandUtil {
    private static final long COOLDOWN_MS = 60_000;

    private final Cache cache;

    public TicketCommandUtil(Cache cache) {
        this.cache = cache;
    }

    public boolean checkCooldown(CommandIssuer sender, String ticketType, LocaleManager localeManager) {
        UUID uuid = sender.getUniqueId();
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile == null) return false;

        String cooldownKey = "ticket:" + ticketType;
        if (!profile.getCooldowns().isOnCooldown(cooldownKey, COOLDOWN_MS)) return false;

        long remainingMs = profile.getCooldowns().getRemainingMs(cooldownKey, COOLDOWN_MS);
        long remainingSeconds = remainingMs / 1000;
        sender.sendMessage(localeManager.getMessage("messages.ticket_cooldown",
                mapOf("seconds", String.valueOf(remainingSeconds))));
        return true;
    }

    private void setCooldown(UUID uuid, String cooldownType) {
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null) profile.getCooldowns().set("ticket:" + cooldownType);
    }

    public void submitFinishedTicket(CommandIssuer sender, ModlHttpClient httpClient, Platform platform,
                                     LocaleManager localeManager, String panelUrl,
                                     CreateTicketRequest request, String ticketType, String cooldownType) {
        sender.sendMessage(localeManager.getMessage("messages.submitting", mapOf("type", ticketType.toLowerCase())));

        CompletableFuture<CreateTicketResponse> future = httpClient.createTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                setCooldown(sender.getUniqueId(), cooldownType);
                sender.sendMessage(localeManager.getMessage("messages.success", mapOf("type", ticketType)));
                sender.sendMessage(localeManager.getMessage("messages.ticket_id", mapOf("ticketId", response.getTicketId())));

                String ticketUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(sender, platform, localeManager,
                        localeManager.getMessage("messages.view_ticket_label"), ticketUrl, response.getTicketId());
                sender.sendMessage(localeManager.getMessage("messages.evidence_note"));
            } else {
                sender.sendMessage(localeManager.getMessage("messages.failed_submit", mapOf("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(response.getMessage()))));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            else {
                sender.sendMessage(localeManager.getMessage("messages.failed_submit", mapOf("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
            return null;
        });
    }

    public void submitUnfinishedTicket(CommandIssuer sender, ModlHttpClient httpClient, Platform platform,
                                       LocaleManager localeManager, String panelUrl,
                                       CreateTicketRequest request, String ticketType, String cooldownType) {
        sender.sendMessage(localeManager.getMessage("messages.creating", mapOf("type", ticketType.toLowerCase())));

        CompletableFuture<CreateTicketResponse> future = httpClient.createUnfinishedTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                setCooldown(sender.getUniqueId(), cooldownType);
                sender.sendMessage(localeManager.getMessage("messages.created", mapOf("type", ticketType)));
                sender.sendMessage(localeManager.getMessage("messages.ticket_id", mapOf("ticketId", response.getTicketId())));

                String formUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(sender, platform, localeManager,
                        localeManager.getMessage("messages.complete_form_label", mapOf("type", ticketType.toLowerCase())), formUrl, response.getTicketId());
            } else {
                sender.sendMessage(localeManager.getMessage("messages.failed_create", mapOf("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(response.getMessage()))));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            else {
                sender.sendMessage(localeManager.getMessage("messages.failed_create", mapOf("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
            return null;
        });
    }

    public void sendClickableTicketMessage(CommandIssuer sender, Platform platform, LocaleManager localeManager,
                                            String message, String ticketUrl, String ticketId) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("messages.console_ticket_url", mapOf("message", message, "url", ticketUrl)));
            return;
        }

        String clickText = localeManager.getMessage("messages.click_to_view");
        String hoverText = localeManager.getMessage("messages.click_to_view_hover", mapOf("ticketId", ticketId));
        String json = String.format(
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"\",\"color\":\"gold\"}," +
            "{\"text\":\"%s: \",\"color\":\"gray\"}," +
            "{\"text\":\"%s\",\"color\":\"aqua\",\"underlined\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"%s\"}}]}",
            escapeJson(message), escapeJson(clickText), ticketUrl, escapeJson(hoverText));
        UUID senderUuid = sender.getUniqueId();
        platform.runOnMainThread(() -> platform.sendJsonMessage(senderUuid, json));
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "")
                   .replace("\t", "\\t");
    }
}
