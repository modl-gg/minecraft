package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;

public class RecentPunishmentsMenu extends BaseStaffListMenu<RecentPunishmentsMenu.PunishmentWithPlayer> {
    @Getter
    public static class PunishmentWithPlayer {
        private final Punishment punishment;
        private final UUID playerUuid;
        private final String playerName;
        private final Account account;

        public PunishmentWithPlayer(Punishment punishment, UUID playerUuid, String playerName, Account account) {
            this.punishment = punishment;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.account = account;
        }

    }

    private final List<PunishmentWithPlayer> recentPunishments;
    private final String panelUrl;

    public RecentPunishmentsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                  boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction, null);
    }

    public RecentPunishmentsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                  boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction,
                                  List<PunishmentWithPlayer> preloadedData) {
        super("Recent Punishments", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.recentPunishments = preloadedData != null ? new ArrayList<>(preloadedData) : new ArrayList<>();
        activeTab = StaffTab.PUNISHMENTS;

        if (preloadedData == null)
            fetchRecentPunishments();
    }

    private void fetchRecentPunishments() {
        try {
            httpClient.getRecentPunishments(48).thenAccept(response -> {
                if (response.isSuccess() && response.getPunishments() != null) {
                    recentPunishments.clear();
                    for (RecentPunishmentsResponse.RecentPunishment p : response.getPunishments()) {
                        UUID playerUuid = null;
                        try {
                            if (p.getPlayerUuid() != null) {
                                playerUuid = UUID.fromString(p.getPlayerUuid());
                            }
                        } catch (Exception ignored) {}

                        Punishment punishment = new Punishment();
                        punishment.setId(p.getId());
                        punishment.setIssuerName(p.getIssuerName());
                        punishment.setIssued(p.getIssued());
                        punishment.setStarted(p.getStarted());
                        punishment.setTypeOrdinal(p.getTypeOrdinal());
                        punishment.setModifications(p.getModifications());
                        punishment.setNotes(new ArrayList<>(p.getNotes()));
                        punishment.setEvidence(new ArrayList<>(p.getEvidence()));
                        punishment.setDataMap(new HashMap<>(p.getData()));
                        punishment.setAttachedTicketIds(p.getAttachedTicketIds() != null ? new ArrayList<>(p.getAttachedTicketIds()) : null);

                        if (p.getType() != null) {
                            try {
                                punishment.setType(Punishment.Type.valueOf(p.getType()));
                            } catch (IllegalArgumentException ignored) {}
                        }

                        recentPunishments.add(new PunishmentWithPlayer(punishment, playerUuid, p.getPlayerName(), null));
                    }
                }
            }).join();
        } catch (Exception ignored) {}
    }

    @Override
    protected Collection<PunishmentWithPlayer> elements() {
        if (recentPunishments.isEmpty())
            return Collections.singletonList(new PunishmentWithPlayer(null, null, null, null));

        List<PunishmentWithPlayer> sorted = new ArrayList<>(recentPunishments);
        sorted.sort((p1, p2) -> p2.getPunishment().getIssued().compareTo(p1.getPunishment().getIssued()));
        return sorted;
    }

    @Override
    protected CirrusItem map(PunishmentWithPlayer pwp) {
        LocaleManager locale = platform.getLocaleManager();

        if (pwp.getPunishment() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.history"));

        Punishment punishment = pwp.getPunishment();

        Object typeNameObj = punishment.getDataMap().get("typeName");
        String typeName = typeNameObj != null ? typeNameObj.toString() : punishment.getTypeCategory();

        boolean isKick = typeName != null && typeName.toLowerCase().contains("kick");
        boolean isBan = typeName != null && (typeName.toLowerCase().contains("ban") || typeName.toLowerCase().contains("blacklist"));
        boolean isMute = typeName != null && typeName.toLowerCase().contains("mute");

        Long effectiveDuration = getEffectiveDuration(punishment);

        boolean isActive = !isKick && isPunishmentEffectivelyActive(punishment, effectiveDuration);

        String initialDuration = "";
        if (!isKick) {
            Long duration = punishment.getDuration();
            if (duration == null || duration <= 0) {
                initialDuration = "Permanent";
            } else {
                initialDuration = MenuItems.formatDuration(duration);
            }
        }

        String spaceBanMuteOrKick = "";
        if (isKick) {
            spaceBanMuteOrKick = "Kick";
        } else if (isBan) {
            spaceBanMuteOrKick = " Ban";
        } else if (isMute) {
            spaceBanMuteOrKick = " Mute";
        }

        String statusLine;
        Date pardonDate = isKick ? null : findPardonDate(punishment);
        if (isKick) {
            statusLine = ""; // Don't show status for kicks
        } else if (pardonDate != null) {
            long pardonedAgo = System.currentTimeMillis() - pardonDate.getTime();
            String pardonedFormatted = MenuItems.formatDuration(pardonedAgo > 0 ? pardonedAgo : 0);
            statusLine = locale.getMessage("menus.history_item.status_pardoned",
                    Map.of("pardoned", pardonedFormatted));
        } else if (punishment.getStarted() == null) {
            statusLine = locale.getMessage("menus.history_item.status_unstarted");
        } else if (isActive) {
            if (effectiveDuration == null || effectiveDuration <= 0) {
                statusLine = locale.getMessage("menus.history_item.status_permanent");
            } else {
                long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
                long remaining = expiryTime - System.currentTimeMillis();
                String expiryFormatted = MenuItems.formatDuration(remaining > 0 ? remaining : 0);
                statusLine = locale.getMessage("menus.history_item.status_active",
                        Map.of("expiry", expiryFormatted));
            }
        } else {
            if (effectiveDuration != null && effectiveDuration > 0 && punishment.getStarted() != null) {
                long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
                long expiredAgo = System.currentTimeMillis() - expiryTime;
                String expiredFormatted = MenuItems.formatDuration(expiredAgo > 0 ? expiredAgo : 0);
                statusLine = locale.getMessage("menus.history_item.status_inactive",
                        Map.of("expired", expiredFormatted));
            } else {
                statusLine = locale.getMessage("menus.history_item.status_inactive",
                        Map.of("expired", "N/A"));
            }
        }

        StringBuilder notesBuilder = new StringBuilder();
        List<Note> notes = punishment.getNotes();
        if (!notes.isEmpty()) {
            String noteFormat = locale.getMessage("menus.history_item.note_format");
            for (int i = 0; i < notes.size(); i++) {
                Note note = notes.get(i);
                String noteDate = MenuItems.formatDate(note.getDate());
                String noteIssuer = note.getIssuerName();
                String noteText = note.getText();
                String formattedNote = noteFormat
                        .replace("{note_date}", noteDate)
                        .replace("{note_issuer}", noteIssuer)
                        .replace("{note}", noteText);
                if (i > 0)
                    notesBuilder.append("\n");
                notesBuilder.append(formattedNote);
            }
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("punishment_id", punishment.getId());
        vars.put("punishment_type", typeName);
        vars.put("initial_duration_if_not_kick", initialDuration);
        vars.put("space_ban_mute_or_kick", spaceBanMuteOrKick);
        vars.put("status_line", statusLine);
        vars.put("notes", notesBuilder.toString());
        vars.put("reason", punishment.getReason() != null ? punishment.getReason() : "No reason");
        vars.put("issuer", punishment.getIssuerName());
        vars.put("issued_date", MenuItems.formatDate(punishment.getIssued()));
        Object issuedServerObj = punishment.getDataMap().get("issuedServer");
        vars.put("issued_server", issuedServerObj instanceof String ? (String) issuedServerObj : "");
        vars.put("player", pwp.getPlayerName() != null ? pwp.getPlayerName() : "Unknown");

        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.history_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (processed.contains("\n"))
                lore.addAll(Arrays.asList(processed.split("\n")));
            else if (!processed.isEmpty())
                lore.add(processed);
        }

        String titleKey = isActive ? "menus.history_item.title_active" : "menus.history_item.title_inactive";
        String title = locale.getMessage(titleKey, vars);

        CirrusItemType itemType = getPunishmentItemType(punishment);

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private CirrusItemType getPunishmentItemType(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();

        Cache cache = platform.getCache();
        if (cache != null) {
            Map<Integer, String> items = cache.getPunishmentTypeItems();
            if (items != null) {
                String itemId = items.get(ordinal);
                if (itemId != null) return CirrusItemType.of(itemId);
            }
        }

        if (punishment.isBanType()) return CirrusItemType.BARRIER;
        if (punishment.isMuteType()) return CirrusItemType.PAPER;
        if (punishment.isKickType()) return CirrusItemType.LEATHER_BOOTS;
        return CirrusItemType.PAPER;
    }

    @Override
    protected void handleClick(Click click, PunishmentWithPlayer pwp) {
        if (pwp.getPunishment() == null) return;

        List<PunishmentWithPlayer> currentData = new ArrayList<>(recentPunishments);
        Consumer<CirrusPlayerWrapper> returnToPunishments = p ->
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null, currentData).display(p);

        if (pwp.getAccount() != null) {
            ActionHandlers.openMenu(
                    new StaffModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                            pwp.getAccount(), pwp.getPunishment(), isAdmin, panelUrl, returnToPunishments))
                    .handle(click);
        } else {
            click.clickedMenu().close();
            httpClient.getPlayerProfile(pwp.getPlayerUuid()).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    new StaffModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                        response.getProfile(), pwp.getPunishment(), isAdmin, panelUrl, returnToPunishments)
                        .display(click.player());
                } else {
                    sendMessage(MenuItems.COLOR_RED + "Failed to load player profile");
                }
            }).exceptionally(e -> {
                sendMessage(MenuItems.COLOR_RED + "Failed to load player profile: " + e.getMessage());
                return null;
            });
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("openPunishments", click -> {});
    }

    private Date findPardonDate(Punishment punishment) {
        List<Modification> modifications = punishment.getModifications();
        if (modifications.isEmpty())
            return null;

        for (Modification mod : modifications) {
            if (mod.getType() == Modification.Type.MANUAL_PARDON ||
                mod.getType() == Modification.Type.APPEAL_ACCEPT) {
                return mod.getIssued();
            }
        }
        return null;
    }

    private Long getEffectiveDuration(Punishment punishment) {
        List<Modification> modifications = punishment.getModifications();
        if (modifications.isEmpty())
            return punishment.getDuration();

        Long effectiveDuration = punishment.getDuration();
        for (Modification mod : modifications) {
            if (mod.getType() == Modification.Type.MANUAL_DURATION_CHANGE ||
                mod.getType() == Modification.Type.APPEAL_DURATION_CHANGE) {
                Long modDuration = mod.getEffectiveDuration();
                if (modDuration == null || modDuration <= 0)
                    effectiveDuration = null;
                else
                    effectiveDuration = modDuration;
            }
        }
        return effectiveDuration;
    }

    private boolean isPunishmentEffectivelyActive(Punishment punishment, Long effectiveDuration) {
        if (findPardonDate(punishment) != null)
            return false;

        if (!punishment.isActive())
            return false;

        if (punishment.getStarted() == null)
            return false;

        if (effectiveDuration == null || effectiveDuration <= 0)
            return true;

        long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
        return System.currentTimeMillis() < expiryTime;
    }
}
