package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.HistoryMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.PunishmentTypeCacheManager;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class HistoryCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final PunishmentTypeCacheManager punishmentTypeCache;

    @CommandCompletion("@players")
    @CommandAlias("%cmd_history")
    @Syntax("<player> [-p]")
    @Description("Open the punishment history menu for a player, or use -p to print to chat")
    @Conditions("player|staff")
    public void history(CommandIssuer sender, @Name("player") String playerQuery, @Default("") String flags) {
        boolean printMode = flags.equalsIgnoreCase("-p") || flags.equalsIgnoreCase("print");

        if (!sender.isPlayer() || printMode) {
            printHistory(sender, playerQuery);
            return;
        }

        UUID senderUuid = sender.getUniqueId();
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);
        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
                        HistoryMenu menu = new HistoryMenu(
                            platform, httpClientHolder.getClient(), senderUuid, senderName,
                            profileResponse.getProfile(), null
                        );
                        CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                        menu.display(player);
                    } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
                }).exceptionally(throwable -> {
                    handleException(sender, throwable);
                    return null;
                });
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            handleException(sender, throwable);
            return null;
        });
    }

    private void printHistory(CommandIssuer sender, String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        Account profile = profileResponse.getProfile();
                        displayHistory(sender, playerName, profile);
                    } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
                }).exceptionally(throwable -> {
                    handleException(sender, throwable);
                    return null;
                });
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            handleException(sender, throwable);
            return null;
        });
    }

    private void displayHistory(CommandIssuer sender, String playerName, Account profile) {
        sender.sendMessage(localeManager.getMessage("print.history.header", Map.of("player", playerName)));

        if (profile.getPunishments().isEmpty()) sender.sendMessage(localeManager.getMessage("print.history.empty"));
        else {
            int ordinal = 1;
            for (Punishment punishment : profile.getPunishments()) {
                String type = punishmentTypeCache.getNameByOrdinal(punishment.getTypeOrdinal());
                String id = punishment.getId() != null ? punishment.getId() : "?";
                String issuer = punishment.getIssuerName() != null ? punishment.getIssuerName() : Constants.UNKNOWN;
                String date = localeManager.formatDate(punishment.getIssued());
                String ordinalStr = String.valueOf(ordinal);
                String reason = punishment.getReason() != null ? punishment.getReason() : "";

                boolean isKick = punishment.isKickType();

                Long effectiveDuration = punishment.getEffectiveDuration();
                String duration;
                if (isKick) duration = "";
                else if (effectiveDuration == null || effectiveDuration <= 0) duration = "permanent";
                else duration = MenuItems.formatDuration(effectiveDuration);

                Map<String, String> vars = new HashMap<>();
                vars.put("ordinal", ordinalStr);
                vars.put("type", type);
                vars.put("id", id);
                vars.put("issuer", issuer);
                vars.put("date", date);
                vars.put("reason", reason);
                vars.put("duration", duration);

                if (isKick)
                    sender.sendMessage(localeManager.getMessage("print.history.entry_kick", vars));
                else {
                    Date pardonDate = findPardonDate(punishment);

                    if (pardonDate != null) {
                        long pardonedAgo = System.currentTimeMillis() - pardonDate.getTime();
                        vars.put("pardoned_ago", MenuItems.formatDuration(pardonedAgo > 0 ? pardonedAgo : 0));
                        sender.sendMessage(localeManager.getMessage("print.history.entry_pardoned", vars));
                    } else if (punishment.getStarted() == null)
                        sender.sendMessage(localeManager.getMessage("print.history.entry_unstarted", vars));
                    else if (punishment.isActive()) {
                        if (effectiveDuration == null || effectiveDuration <= 0) sender.sendMessage(localeManager.getMessage("print.history.entry_permanent", vars));
                        else {
                            Date effectiveExpiry = punishment.getEffectiveExpiry();
                            long remaining = effectiveExpiry != null
                                    ? effectiveExpiry.getTime() - System.currentTimeMillis() : 0;
                            vars.put("expiry", MenuItems.formatDuration(remaining > 0 ? remaining : 0));
                            sender.sendMessage(localeManager.getMessage("print.history.entry_active", vars));
                        }
                    } else {
                        Date effectiveExpiry = punishment.getEffectiveExpiry();
                        if (effectiveExpiry != null) {
                            long expiredAgo = System.currentTimeMillis() - effectiveExpiry.getTime();
                            vars.put("expired_ago", MenuItems.formatDuration(expiredAgo > 0 ? expiredAgo : 0));
                        } else vars.put("expired_ago", "N/A");
                        sender.sendMessage(localeManager.getMessage("print.history.entry_expired", vars));
                    }
                }
                ordinal++;
            }
            sender.sendMessage(localeManager.getMessage("print.history.total", Map.of(
                    "count", String.valueOf(profile.getPunishments().size())
            )));
        }

        sender.sendMessage(localeManager.getMessage("print.history.footer"));
    }

    private Date findPardonDate(Punishment punishment) {
        for (Modification mod : punishment.getModifications())
            if (mod.getType() == Modification.Type.MANUAL_PARDON ||
                mod.getType() == Modification.Type.APPEAL_ACCEPT)
                return mod.getIssued();
        return null;
    }

    private void handleException(CommandIssuer sender, Throwable throwable) {
        if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        else sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
    }
}
