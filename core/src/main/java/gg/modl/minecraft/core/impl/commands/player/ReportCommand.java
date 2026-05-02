package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.impl.menus.ReportMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class ReportCommand {
    private final AsyncCommandExecutor commandExecutor;
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final ChatMessageCache chatMessageCache;
    private final TicketCommandUtil ticketUtil;

    @Command("report")
    @Description("Report a player")
    @PlayerOnly
    public void report(CommandActor actor, AbstractPlayer targetPlayer) {
        if (ticketUtil.checkCooldown(actor, "player", localeManager)) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(actor.uniqueId(), false);

        if (targetPlayer == null) {
            actor.reply(localeManager.getMessage("general.player_not_found"));
            return;
        }

        if (ticketUtil.denySelfReport(actor, reporter, targetPlayer, localeManager)) return;

        if (!targetPlayer.isOnline()) {
            actor.reply(localeManager.getMessage("messages.player_not_online"));
            return;
        }

        commandExecutor.execute(() -> {
            ReportGuiConfig guiConfig = getOrLoadReportGuiConfig();

            UUID senderUuid = actor.uniqueId();
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
