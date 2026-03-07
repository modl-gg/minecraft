package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.ReportMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class ReportCommand extends BaseCommand {
    private final AsyncCommandExecutor commandExecutor;
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final ChatMessageCache chatMessageCache;
    private final TicketCommandUtil ticketUtil;

    @CommandAlias("%cmd_report")
    @CommandCompletion("@players")
    @Description("Report a player")
    @Syntax("<player>")
    @Conditions("player")
    public void report(CommandIssuer sender, AbstractPlayer targetPlayer) {
        if (ticketUtil.checkCooldown(sender, "player", localeManager)) return;

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
                platform.getLogger());

        if (cache != null) cache.cacheReportGuiConfig(config);
        return config;
    }
}
