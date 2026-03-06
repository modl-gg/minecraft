package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.ClaimTicketRequest;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.ReportMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.Constants;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class TicketCommands extends BaseCommand {
    private static final long COOLDOWN_MS = 60_000;
    private static final String CLICKABLE_TICKET_JSON =
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"\uD83D\uDCCB \",\"color\":\"gold\"}," +
            "{\"text\":\"%s: \",\"color\":\"gray\"}," +
            "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}";

    private final AsyncCommandExecutor commandExecutor;
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final ChatMessageCache chatMessageCache;

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    @CommandAlias("%cmd_report")
    @CommandCompletion("@players")
    @Description("Report a player")
    @Syntax("<player>")
    @Conditions("player")
    public void report(CommandIssuer sender, AbstractPlayer targetPlayer) {
        if (!checkCooldown(sender, "player")) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);

        if (targetPlayer == null) {
            sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            return;
        }

        if (targetPlayer.username().equalsIgnoreCase(reporter.username())) {
            sender.sendMessage(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }

        if (!targetPlayer.isOnline()) {
            sender.sendMessage(localeManager.getMessage("messages.player_not_online"));
            return;
        }

        commandExecutor.execute(() -> {
            ReportGuiConfig guiConfig = getOrLoadReportGuiConfig();

            UUID senderUuid = sender.getUniqueId();
            ReportMenu menu = new ReportMenu(
                reporter, targetPlayer, httpClient, localeManager, platform, panelUrl,
                guiConfig, chatMessageCache
            );
            CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
            menu.display(player);
        });
    }

    private ReportGuiConfig getOrLoadReportGuiConfig() {
        Cache cache = platform.getCache();
        if (cache != null) {
            ReportGuiConfig cached = cache.getCachedReportGuiConfig();
            if (cached != null) return cached;
        }
        ReportGuiConfig config = ReportGuiConfig.load(
                platform.getDataFolder().toPath(),
                java.util.logging.Logger.getLogger("modl"));
        if (cache != null) cache.cacheReportGuiConfig(config);
        return config;
    }

    @CommandAlias("%cmd_chatreport")
    @CommandCompletion("@players")
    @Description("Report a player for chat violations (automatically includes recent chat logs)")
    @Syntax("<player>")
    @Conditions("player")
    public void chatReport(CommandIssuer sender, AbstractPlayer targetPlayer) {
        if (!checkCooldown(sender, "chat")) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);

        if (targetPlayer.username().equalsIgnoreCase(reporter.username())) {
            sender.sendMessage(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }

        String chatLog = chatMessageCache.getChatLogForReport(
            targetPlayer.uuid().toString(),
            reporter.uuid().toString()
        );

        if (chatLog.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("messages.no_chat_logs_available", Map.of("player", targetPlayer.username())));
            return;
        }

        String description = "**Chat Report for " + targetPlayer.username() + "**\n\n" +
                             "Reported by: " + reporter.username() + "\n\n" +
                             "**Chat Log:**\n```\n" + chatLog + "\n```";

        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.uuid().toString(),
            reporter.username(),
            "chat",
            "Chat Report: " + targetPlayer.username(),
            description,
            targetPlayer.uuid().toString(),
            targetPlayer.username(),
            List.of(chatLog.split("\n")),
            List.of(),
            "normal",
            createdServer
        );

        submitFinishedTicket(sender, request, "Chat report", "chat");
    }

    @CommandAlias("%cmd_hackreport")
    @CommandCompletion("@players")
    @Description("Report a player for cheating/hacking")
    @Syntax("<player> [details]")
    @Conditions("player")
    public void hackReport(CommandIssuer sender, String targetName, @Optional String details) {
        if (!checkCooldown(sender, "player")) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);
        AbstractPlayer targetPlayer = platform.getAbstractPlayer(targetName, false);

        if (targetPlayer == null) {
            sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            return;
        }

        if (targetPlayer.username().equalsIgnoreCase(reporter.username())) {
            sender.sendMessage(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }

        String description = details != null && !details.isEmpty() ? details : null;

        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.uuid().toString(),
            reporter.username(),
            "player",
            "Cheating: " + targetPlayer.username(),
            description,
            targetPlayer.uuid().toString(),
            targetPlayer.username(),
            null,
            List.of("report", "cheating"),
            "normal",
            createdServer
        );

        submitFinishedTicket(sender, request, "Report", "player");
    }

    @CommandAlias("%cmd_apply")
    @Description("Submit a staff application")
    @Conditions("player")
    public void staffApplication(CommandIssuer sender) {
        if (!checkCooldown(sender, "staff")) return;

        AbstractPlayer applicant = platform.getAbstractPlayer(sender.getUniqueId(), false);

        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            applicant.uuid().toString(),
            applicant.username(),
            "staff",
            "Application: " + applicant.username(),
            null, null,
            null,
            null,
            List.of(),
            "normal",
            createdServer
        );

        submitUnfinishedTicket(sender, request, "Staff application", "staff");
    }

    @CommandAlias("%cmd_bugreport")
    @Description("Report a bug")
    @Syntax("<title...>")
    @Conditions("player")
    public void bugReport(CommandIssuer sender, String title) {
        if (!checkCooldown(sender, "bug")) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);

        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.uuid().toString(),
            reporter.username(),
            "bug",
            title,
            null,
            null,
            null,
            null,
            List.of(),
            "normal",
            createdServer
        );

        submitUnfinishedTicket(sender, request, "Bug report", "bug");
    }

    @CommandAlias("%cmd_support")
    @Description("Request support")
    @Syntax("<title...>")
    @Conditions("player")
    public void supportRequest(CommandIssuer sender, String title) {
        if (!checkCooldown(sender, "support")) return;

        AbstractPlayer requester = platform.getAbstractPlayer(sender.getUniqueId(), false);

        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            requester.uuid().toString(),
            requester.username(),
            "support",
            title,
            null,
            null,
            null,
            null,
            List.of(),
            "normal",
            createdServer
        );

        submitUnfinishedTicket(sender, request, "Support request", "support");
    }

    @CommandAlias("%cmd_tclaim")
    @Description("Link an unlinked ticket to your account")
    @Syntax("<ticket-id>")
    @Conditions("player")
    public void claimTicket(CommandIssuer sender, String ticketId) {
        AbstractPlayer player = platform.getAbstractPlayer(sender.getUniqueId(), false);

        sender.sendMessage(localeManager.getMessage("messages.claiming_ticket", Map.of("ticketId", ticketId)));

        ClaimTicketRequest request =
            new ClaimTicketRequest(
                ticketId,
                player.uuid().toString(),
                player.username()
            );

        httpClient.claimTicket(request).thenAccept(response -> {
            if (response.isSuccess()) {
                sender.sendMessage(localeManager.getMessage("messages.ticket_claimed_success",
                    Map.of("ticketId", ticketId, "subject", response.getSubject() != null ? response.getSubject() : Constants.UNKNOWN)));

                String ticketUrl = panelUrl + "/ticket/" + ticketId;
                sendClickableTicketMessage(sender, localeManager.getMessage("messages.view_ticket_label"), ticketUrl, ticketId);
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

    private boolean checkCooldown(CommandIssuer sender, String ticketType) {
        UUID uuid = sender.getUniqueId();
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return true;

        Long lastUsed = playerCooldowns.get(ticketType);
        if (lastUsed == null) return true;

        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= COOLDOWN_MS) return true;

        long remainingSeconds = (COOLDOWN_MS - elapsed) / 1000;
        sender.sendMessage(localeManager.getMessage("messages.ticket_cooldown",
                Map.of("seconds", String.valueOf(remainingSeconds))));
        return false;
    }

    private void setCooldown(UUID uuid, String ticketType) {
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(ticketType, System.currentTimeMillis());
    }

    private void submitFinishedTicket(CommandIssuer sender, CreateTicketRequest request, String ticketType, String cooldownType) {
        sender.sendMessage(localeManager.getMessage("messages.submitting", Map.of("type", ticketType.toLowerCase())));

        CompletableFuture<CreateTicketResponse> future = httpClient.createTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                setCooldown(sender.getUniqueId(), cooldownType);
                sender.sendMessage(localeManager.getMessage("messages.success", Map.of("type", ticketType)));
                sender.sendMessage(localeManager.getMessage("messages.ticket_id", Map.of("ticketId", response.getTicketId())));

                String ticketUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(sender, localeManager.getMessage("messages.view_ticket_label"), ticketUrl, response.getTicketId());
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

    private void submitUnfinishedTicket(CommandIssuer sender, CreateTicketRequest request, String ticketType, String cooldownType) {
        sender.sendMessage(localeManager.getMessage("messages.creating", Map.of("type", ticketType.toLowerCase())));

        CompletableFuture<CreateTicketResponse> future = httpClient.createUnfinishedTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                setCooldown(sender.getUniqueId(), cooldownType);
                sender.sendMessage(localeManager.getMessage("messages.created", Map.of("type", ticketType)));
                sender.sendMessage(localeManager.getMessage("messages.ticket_id", Map.of("ticketId", response.getTicketId())));

                String formUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(sender, localeManager.getMessage("messages.complete_form_label", Map.of("type", ticketType.toLowerCase())), formUrl, response.getTicketId());
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

    private void sendClickableTicketMessage(CommandIssuer sender, String message, String ticketUrl, String ticketId) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("messages.console_ticket_url", Map.of("message", message, "url", ticketUrl)));
            return;
        }

        String clickableMessage = String.format(CLICKABLE_TICKET_JSON, message, ticketUrl, ticketId);
        UUID senderUuid = sender.getUniqueId();
        platform.runOnMainThread(() -> platform.sendJsonMessage(senderUuid, clickableMessage));
    }
}