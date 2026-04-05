package gg.modl.minecraft.core.impl.commands.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.response.LinkedAccountsResponse;
import gg.modl.minecraft.api.http.response.PlayerLookupResponse;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.api.http.response.PunishmentDetailResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.inspect.InspectMenu;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.util.PunishmentActionMessages;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentTypeCacheManager;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.command.CommandActor;

import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class InspectCommand {
    private static final String STATUS_ACTIVE = "Active", STATUS_PARDONED = "Pardoned", STATUS_INACTIVE = "Inactive",
            DURATION_PERMANENT = "Permanent", COLOR_YES = "&cYes", COLOR_NO = "&aNo",
            COLOR_ACTIVE = "&a", COLOR_INACTIVE = "&7",
            PROFILE_LINK_JSON =
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"  \",\"color\":\"gold\"}," +
            "{\"text\":\"View Web Profile\",\"color\":\"aqua\",\"underlined\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view %s's profile\"}}]}";
    private static final int MAX_NOTES_DISPLAYED = 3, MAX_LINKED_ACCOUNTS_DISPLAYED = 5;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;
    private final PunishmentTypeCacheManager punishmentTypeCache;

    @Command("inspect")
    @Description("Open the inspect menu for a player, or use -p to print info to chat")
    @PlayerOnly @StaffOnly
    public void inspect(CommandActor actor, @Named("player") String playerQuery, @revxrsal.commands.annotation.Optional String flags) {
        if (flags == null) flags = "";

        if (playerQuery.startsWith("#")) {
            String punishmentId = playerQuery.substring(1);
            printPunishmentDetail(actor, punishmentId);
            return;
        }

        boolean printMode = flags.equalsIgnoreCase("-p") || flags.equalsIgnoreCase("print");

        if (actor.uniqueId() == null || printMode) {
            printLookup(actor, playerQuery);
            return;
        }

        UUID senderUuid = actor.uniqueId();
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);
        httpClientHolder.getClient().lookupPlayerProfile(request).thenAccept(profileResponse -> {
            if (profileResponse.getStatus() == 200) {
                String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
                InspectContext context = new InspectContext(
                    profileResponse.getProfile(),
                    profileResponse.getPunishmentCount(),
                    profileResponse.getNoteCount()
                );
                InspectMenu menu = new InspectMenu(
                    platform, httpClientHolder.getClient(), senderUuid, senderName,
                    profileResponse.getProfile(), null, context
                );
                CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                menu.display(player);
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private void printPunishmentDetail(CommandActor actor, String punishmentId) {
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", "#" + punishmentId)));

        httpClientHolder.getClient().getPunishmentDetail(punishmentId).thenAccept(response -> {
            if (!response.isSuccess() || response.getPunishment() == null) {
                actor.reply(localeManager.getMessage("print.punishment_detail.not_found", mapOf("id", punishmentId)));
                return;
            }

            PunishmentDetailResponse.PunishmentDetail p = response.getPunishment();
            String typeName = getPunishmentTypeName(String.valueOf(p.getTypeOrdinal()));
            String playerName = p.getPlayerName() != null ? p.getPlayerName() : Constants.UNKNOWN;
            String issuerName = p.getIssuerName() != null ? p.getIssuerName() : Constants.UNKNOWN;
            String issued = p.getIssued() != null ? p.getIssued() : Constants.UNKNOWN;

            String status = STATUS_ACTIVE;
            boolean active = true;
            Map<String, Object> data = p.getData();
            if (data != null) {
                if (data.containsKey("pardoned") && Boolean.TRUE.equals(data.get("pardoned"))) {
                    status = STATUS_PARDONED;
                    active = false;
                } else if (data.containsKey("active") && Boolean.FALSE.equals(data.get("active"))) {
                    status = STATUS_INACTIVE;
                    active = false;
                }
            }

            String duration = DURATION_PERMANENT;
            if (data != null && data.containsKey("duration")) {
                Object dur = data.get("duration");
                if (dur instanceof Number) {
                    long millis = ((Number) dur).longValue();
                    if (millis > 0) duration = PunishmentMessages.formatDuration(millis);
                }
            }

            String reason = "";
            if (data != null && data.containsKey("reason")) {
                Object r = data.get("reason");
                if (r != null) reason = r.toString();
            }

            int evidenceCount = p.getEvidence() != null ? p.getEvidence().size() : 0;
            int notesCount = p.getNotes() != null ? p.getNotes().size() : 0;
            int modsCount = p.getModifications() != null ? p.getModifications().size() : 0;

            actor.reply(localeManager.getMessage("print.punishment_detail.header", mapOf("id", punishmentId)));
            actor.reply(localeManager.getMessage("print.punishment_detail.type", mapOf("type", typeName)));
            actor.reply(localeManager.getMessage("print.punishment_detail.player", mapOf("player", playerName)));
            actor.reply(localeManager.getMessage("print.punishment_detail.issuer", mapOf("issuer", issuerName)));
            actor.reply(localeManager.getMessage("print.punishment_detail.date", mapOf("date", issued)));
            actor.reply(localeManager.getMessage("print.punishment_detail.status", mapOf("status", (active ? COLOR_ACTIVE : COLOR_INACTIVE) + status)));
            actor.reply(localeManager.getMessage("print.punishment_detail.duration", mapOf("duration", duration)));
            if (!reason.isEmpty()) actor.reply(localeManager.getMessage("print.punishment_detail.reason", mapOf("reason", reason)));
            actor.reply(localeManager.getMessage("print.punishment_detail.evidence", mapOf("count", String.valueOf(evidenceCount))));
            actor.reply(localeManager.getMessage("print.punishment_detail.notes", mapOf("count", String.valueOf(notesCount))));
            actor.reply(localeManager.getMessage("print.punishment_detail.modifications", mapOf("count", String.valueOf(modsCount))));
            actor.reply(localeManager.getMessage("print.punishment_detail.footer"));

            if (actor.uniqueId() != null) {
                UUID senderUuid = actor.uniqueId();
                platform.runOnMainThread(() ->
                    PunishmentActionMessages.sendPunishmentActions(platform, senderUuid, punishmentId));
            }
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private void printLookup(CommandActor actor, String playerQuery) {
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID playerUuid = UUID.fromString(response.getData().getMinecraftUuid());
                httpClientHolder.getClient().getLinkedAccounts(playerUuid).thenAccept(linkedResponse -> displayPlayerInfo(actor, response.getData(), linkedResponse)).exceptionally(linkedThrowable -> {
                    if (linkedThrowable.getCause() instanceof PanelUnavailableException) actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
                    else displayPlayerInfo(actor, response.getData(), null);
                    return null;
                });
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private void displayPlayerInfo(CommandActor actor, PlayerLookupResponse.PlayerData data, LinkedAccountsResponse linkedResponse) {
        String playerName = data.getCurrentUsername() != null ? data.getCurrentUsername() : Constants.UNKNOWN;

        actor.reply(localeManager.getMessage("print.inspect.header", mapOf("player", playerName)));
        actor.reply(localeManager.getMessage("print.inspect.uuid", mapOf("player", playerName, "uuid", data.getMinecraftUuid())));

        UUID playerUuid = UUID.fromString(data.getMinecraftUuid());
        CachedProfile targetProfile = cache.getPlayerProfile(playerUuid);
        boolean isBanned = targetProfile != null && targetProfile.isBanned();
        boolean isMuted = targetProfile != null && targetProfile.isMuted();

        if (!isBanned || !isMuted) {
            if (data.getRecentPunishments() != null)
                for (PlayerLookupResponse.RecentPunishment punishment : data.getRecentPunishments()) {
                    if (punishment.isActive()) {
                        String type = punishment.getType();
                        if (type != null) {
                            String typeName = getPunishmentTypeName(type).toLowerCase();
                            if (!isBanned && (typeName.contains("ban") || typeName.equals("blacklist"))) isBanned = true;
                            if (!isMuted && (typeName.contains("mute") || typeName.equals("silence"))) isMuted = true;
                        }
                    }
                }
        }

        String bannedStatus = isBanned ? COLOR_YES : COLOR_NO;
        String mutedStatus = isMuted ? COLOR_YES : COLOR_NO;
        actor.reply(localeManager.getMessage("print.inspect.currently_banned", mapOf("status", bannedStatus)));
        actor.reply(localeManager.getMessage("print.inspect.currently_muted", mapOf("status", mutedStatus)));

        actor.reply(localeManager.getMessage("print.inspect.notes_label"));
        boolean hasNotes = false;
        if (linkedResponse != null)
            for (Account account : linkedResponse.getLinkedAccounts()) {
                if (!account.getNotes().isEmpty()) {
                    hasNotes = true;
                    int noteOrdinal = 1;
                    for (Note note : account.getNotes()) {
                        if (noteOrdinal > MAX_NOTES_DISPLAYED) break;
                        actor.reply(localeManager.getMessage("print.inspect.note_entry", mapOf(
                            "ordinal", String.valueOf(noteOrdinal),
                            "text", note.getText(),
                            "issuer", note.getIssuerName()
                        )));
                        noteOrdinal++;
                    }
                    break;
                }
            }
        if (!hasNotes) actor.reply(localeManager.getMessage("print.inspect.no_notes"));

        int totalPunishments = 0;
        if (data.getPunishmentStats() != null) totalPunishments = data.getPunishmentStats().getTotalPunishments();
        actor.reply(localeManager.getMessage("print.inspect.total_punishments", mapOf("count", String.valueOf(totalPunishments))));

        actor.reply(localeManager.getMessage("print.inspect.linked_accounts_label"));
        if (linkedResponse != null && !linkedResponse.getLinkedAccounts().isEmpty()) {
            int accountOrdinal = 1;
            for (Account account : linkedResponse.getLinkedAccounts()) {
                if (accountOrdinal > MAX_LINKED_ACCOUNTS_DISPLAYED) break;
                String currentName = !account.getUsernames().isEmpty()
                    ? account.getUsernames().get(account.getUsernames().size() - 1).getUsername()
                    : Constants.UNKNOWN;

                CachedProfile accountProfile = account.getMinecraftUuid() != null ? cache.getPlayerProfile(account.getMinecraftUuid()) : null;
                boolean accountBanned = accountProfile != null && accountProfile.isBanned();
                boolean accountMuted = accountProfile != null && accountProfile.isMuted();

                String status;
                if (accountBanned && accountMuted) status = localeManager.getMessage("player_lookup.status.banned_and_muted");
                else if (accountBanned) status = localeManager.getMessage("player_lookup.status.banned");
                else if (accountMuted) status = localeManager.getMessage("player_lookup.status.muted");
                else status = localeManager.getMessage("player_lookup.status.no_punishments");

                actor.reply(localeManager.getMessage("print.inspect.linked_account_entry", mapOf(
                    "ordinal", String.valueOf(accountOrdinal),
                    "username", currentName,
                    "status", status
                )));
                accountOrdinal++;
            }
            if (linkedResponse.getLinkedAccounts().size() > MAX_LINKED_ACCOUNTS_DISPLAYED)
                actor.reply(localeManager.getMessage("print.inspect.linked_account_more", mapOf(
                    "count", String.valueOf(linkedResponse.getLinkedAccounts().size() - MAX_LINKED_ACCOUNTS_DISPLAYED)
                )));
        } else actor.reply(localeManager.getMessage("print.inspect.no_linked_accounts"));

        int totalTickets = 0;
        if (data.getRecentTickets() != null) totalTickets = data.getRecentTickets().size();
        actor.reply(localeManager.getMessage("print.inspect.total_tickets", mapOf("count", String.valueOf(totalTickets))));

        actor.reply(localeManager.getMessage("print.inspect.footer"));

        if (data.getMinecraftUuid() != null) {
            String profileUrl = panelUrl + "/panel?player=" + data.getMinecraftUuid();

            if (actor.uniqueId() != null) {
                String profileMessage = String.format(PROFILE_LINK_JSON, profileUrl, playerName);
                UUID senderUuid = actor.uniqueId();
                platform.runOnMainThread(() -> platform.sendJsonMessage(senderUuid, profileMessage));
            } else {
                actor.reply(localeManager.getMessage("print.inspect.profile_fallback", mapOf("url", profileUrl)));
            }
        }
    }

    private String getPunishmentTypeName(String typeId) {
        if (typeId == null) return Constants.UNKNOWN;
        return punishmentTypeCache.getNameById(typeId);
    }

}
