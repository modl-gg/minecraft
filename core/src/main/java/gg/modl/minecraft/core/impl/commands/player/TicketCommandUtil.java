package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.command.CommandActor;
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

    public boolean checkCooldown(CommandActor actor, String ticketType, LocaleManager localeManager) {
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

    private void setCooldown(UUID uuid, String cooldownType) {
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null) profile.getCooldowns().set("ticket:" + cooldownType);
    }

    public void submitFinishedTicket(CommandActor actor, ModlHttpClient httpClient, Platform platform,
                                     LocaleManager localeManager, String panelUrl,
                                     CreateTicketRequest request, String ticketType, String cooldownType) {
        actor.reply(localeManager.getMessage("messages.submitting", mapOf("type", ticketType.toLowerCase())));

        CompletableFuture<CreateTicketResponse> future = httpClient.createTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                setCooldown(actor.uniqueId(), cooldownType);
                actor.reply(localeManager.getMessage("messages.success", mapOf("type", ticketType)));
                actor.reply(localeManager.getMessage("messages.ticket_id", mapOf("ticketId", response.getTicketId())));

                String ticketUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(actor, platform, localeManager,
                        localeManager.getMessage("messages.view_ticket_label"), ticketUrl, response.getTicketId());
                actor.reply(localeManager.getMessage("messages.evidence_note"));
            } else {
                actor.reply(localeManager.getMessage("messages.failed_submit", mapOf("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(response.getMessage()))));
                actor.reply(localeManager.getMessage("messages.try_again"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
            else {
                actor.reply(localeManager.getMessage("messages.failed_submit", mapOf("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
                actor.reply(localeManager.getMessage("messages.try_again"));
            }
            return null;
        });
    }

    public void submitUnfinishedTicket(CommandActor actor, ModlHttpClient httpClient, Platform platform,
                                       LocaleManager localeManager, String panelUrl,
                                       CreateTicketRequest request, String ticketType, String cooldownType) {
        actor.reply(localeManager.getMessage("messages.creating", mapOf("type", ticketType.toLowerCase())));

        CompletableFuture<CreateTicketResponse> future = httpClient.createUnfinishedTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                setCooldown(actor.uniqueId(), cooldownType);
                actor.reply(localeManager.getMessage("messages.created", mapOf("type", ticketType)));
                actor.reply(localeManager.getMessage("messages.ticket_id", mapOf("ticketId", response.getTicketId())));

                String formUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(actor, platform, localeManager,
                        localeManager.getMessage("messages.complete_form_label", mapOf("type", ticketType.toLowerCase())), formUrl, response.getTicketId());
            } else {
                actor.reply(localeManager.getMessage("messages.failed_create", mapOf("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(response.getMessage()))));
                actor.reply(localeManager.getMessage("messages.try_again"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
            else {
                actor.reply(localeManager.getMessage("messages.failed_create", mapOf("type", ticketType.toLowerCase(), "error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
                actor.reply(localeManager.getMessage("messages.try_again"));
            }
            return null;
        });
    }

    public void sendClickableTicketMessage(CommandActor actor, Platform platform, LocaleManager localeManager,
                                            String message, String ticketUrl, String ticketId) {
        if (actor.uniqueId() == null) {
            actor.reply(localeManager.getMessage("messages.console_ticket_url", mapOf("message", message, "url", ticketUrl)));
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
        UUID senderUuid = actor.uniqueId();
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
