package gg.modl.minecraft.core.impl.commands.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.inspect.HistoryMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Pagination;
import gg.modl.minecraft.core.util.PunishmentTypeCacheManager;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.command.CommandActor;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class HistoryCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final PunishmentTypeCacheManager punishmentTypeCache;

    @Command("history")
    @Description("Open the punishment history menu for a player, or use -p to print to chat")
    @PlayerOnly @StaffOnly
    public void history(CommandActor actor, @Named("player") String playerQuery, @revxrsal.commands.annotation.Optional String flags) {
        if (flags == null) flags = "";
        int page = Pagination.parsePrintFlags(flags);
        boolean printMode = page > 0;

        if (actor.uniqueId() == null || printMode) {
            printHistory(actor, playerQuery, Math.max(1, page));
            return;
        }

        UUID senderUuid = actor.uniqueId();
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        StaffProfileLookup.lookupPlayerProfile(httpClientHolder.getClient(), platform, playerQuery).thenAccept(profileResponse -> {
            if (profileResponse.getStatus() == 200) {
                String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
                HistoryMenu menu = new HistoryMenu(
                    platform, httpClientHolder.getClient(), senderUuid, senderName,
                    profileResponse.getProfile(), null
                );
                CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                menu.display(player);
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private void printHistory(CommandActor actor, String playerQuery, int page) {
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        StaffProfileLookup.lookupPlayerProfile(httpClientHolder.getClient(), platform, playerQuery).thenAccept(profileResponse -> {
            if (profileResponse.getStatus() == 200) {
                Account profile = profileResponse.getProfile();
                List<Account.Username> usernames = profile.getUsernames();
                String playerName = !usernames.isEmpty() ? usernames.get(usernames.size() - 1).getUsername() : playerQuery;
                displayHistory(actor, playerName, profile, page);
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private static final int ENTRIES_PER_PAGE = 8;

    private void displayHistory(CommandActor actor, String playerName, Account profile, int page) {
        List<Punishment> punishments = profile.getPunishments();
        actor.reply(localeManager.getMessage("print.history.header", mapOf("player", playerName)));

        if (punishments.isEmpty()) actor.reply(localeManager.getMessage("print.history.empty"));
        else {
            Pagination.Page pg = Pagination.paginate(punishments, ENTRIES_PER_PAGE, page);
            for (int i = pg.getStart(); i < pg.getEnd(); i++) {
                int ordinal = i + 1;
                Punishment punishment = punishments.get(i);
                String type = punishmentTypeCache.getNameByOrdinal(punishment.getTypeOrdinal());
                String id = punishment.getId();
                String issuer = punishment.getIssuerName();
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
                    actor.reply(localeManager.getMessage("print.history.entry_kick", vars));
                else {
                    Date pardonDate = findPardonDate(punishment);

                    if (pardonDate != null) {
                        long pardonedAgo = System.currentTimeMillis() - pardonDate.getTime();
                        vars.put("pardoned_ago", MenuItems.formatDuration(pardonedAgo > 0 ? pardonedAgo : 0));
                        actor.reply(localeManager.getMessage("print.history.entry_pardoned", vars));
                    } else if (punishment.getStarted() == null)
                        actor.reply(localeManager.getMessage("print.history.entry_unstarted", vars));
                    else if (punishment.isActive()) {
                        if (effectiveDuration == null || effectiveDuration <= 0) actor.reply(localeManager.getMessage("print.history.entry_permanent", vars));
                        else {
                            Date effectiveExpiry = punishment.getEffectiveExpiry();
                            long remaining = effectiveExpiry != null
                                    ? effectiveExpiry.getTime() - System.currentTimeMillis() : 0;
                            vars.put("expiry", MenuItems.formatDuration(remaining > 0 ? remaining : 0));
                            actor.reply(localeManager.getMessage("print.history.entry_active", vars));
                        }
                    } else {
                        Date effectiveExpiry = punishment.getEffectiveExpiry();
                        if (effectiveExpiry != null) {
                            long expiredAgo = System.currentTimeMillis() - effectiveExpiry.getTime();
                            vars.put("expired_ago", MenuItems.formatDuration(expiredAgo > 0 ? expiredAgo : 0));
                        } else vars.put("expired_ago", "N/A");
                        actor.reply(localeManager.getMessage("print.history.entry_expired", vars));
                    }
                }
            }
            actor.reply(localeManager.getMessage("print.history.total", mapOf(
                    "count", String.valueOf(punishments.size()),
                    "page", String.valueOf(pg.getPage()),
                    "total_pages", String.valueOf(pg.getTotalPages())
            )));
        }

        actor.reply(localeManager.getMessage("print.history.footer"));
    }

    private Date findPardonDate(Punishment punishment) {
        for (Modification mod : punishment.getModifications())
            if (mod.getType() == Modification.Type.MANUAL_PARDON ||
                mod.getType() == Modification.Type.APPEAL_ACCEPT)
                return mod.getIssued();
        return null;
    }

}
