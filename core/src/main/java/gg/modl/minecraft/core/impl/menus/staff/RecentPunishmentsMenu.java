package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.inspect.ModifyPunishmentMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Recent Punishments Menu - displays recently issued punishments.
 */
public class RecentPunishmentsMenu extends BaseStaffListMenu<RecentPunishmentsMenu.PunishmentWithPlayer> {

    // Wrapper class to hold punishment with player info
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

        public Punishment getPunishment() { return punishment; }
        public UUID getPlayerUuid() { return playerUuid; }
        public String getPlayerName() { return playerName; }
        public Account getAccount() { return account; }
    }

    private List<PunishmentWithPlayer> recentPunishments = new ArrayList<>();
    private final String panelUrl;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    /**
     * Create a new recent punishments menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param backAction Action to return to parent menu
     */
    public RecentPunishmentsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                  boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Recent Punishments", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        activeTab = StaffTab.PUNISHMENTS;

        // Fetch recent punishments from API
        fetchRecentPunishments();
    }

    private void fetchRecentPunishments() {
        httpClient.getRecentPunishments(48).thenAccept(response -> {
            if (response.isSuccess() && response.getPunishments() != null) {
                recentPunishments.clear();
                for (var p : response.getPunishments()) {
                    UUID playerUuid = null;
                    try {
                        if (p.getPlayerUuid() != null) {
                            playerUuid = UUID.fromString(p.getPlayerUuid());
                        }
                    } catch (Exception ignored) {}

                    // Create a simple Punishment object for display
                    Punishment punishment = new Punishment();
                    punishment.setId(p.getId());
                    punishment.setIssuerName(p.getIssuerName());
                    punishment.setIssued(p.getIssuedAt());

                    // Set data map with reason and active flag
                    Map<String, Object> dataMap = new java.util.HashMap<>();
                    dataMap.put("reason", p.getReason());
                    dataMap.put("active", p.isActive());
                    dataMap.put("typeName", p.getType());
                    punishment.setDataMap(dataMap);

                    // Add first note for reason display
                    if (p.getReason() != null) {
                        Note reasonNote = new Note(p.getReason(), new Date(), "System", null);
                        punishment.setNotes(Collections.singletonList(reasonNote));
                    }

                    recentPunishments.add(new PunishmentWithPlayer(punishment, playerUuid, p.getPlayerName(), null));
                }
            }
        }).exceptionally(e -> {
            // Failed to fetch - list remains empty
            return null;
        });
    }

    @Override
    protected Collection<PunishmentWithPlayer> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (recentPunishments.isEmpty()) {
            return Collections.singletonList(new PunishmentWithPlayer(null, null, null, null));
        }

        // Sort by date, newest first
        List<PunishmentWithPlayer> sorted = new ArrayList<>(recentPunishments);
        sorted.sort((p1, p2) -> p2.getPunishment().getIssued().compareTo(p1.getPunishment().getIssued()));
        return sorted;
    }

    @Override
    protected CirrusItem map(PunishmentWithPlayer pwp) {
        LocaleManager locale = platform.getLocaleManager();

        // Handle placeholder for empty list
        if (pwp.getPunishment() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.history"));
        }

        Punishment punishment = pwp.getPunishment();

        // Type and status - get from dataMap or fall back to registry
        Object typeNameObj = punishment.getDataMap().get("typeName");
        String typeName = typeNameObj != null ? typeNameObj.toString() : punishment.getTypeCategory();

        // Check if this is a kick (kicks don't have duration or active status)
        boolean isKick = typeName != null && typeName.toLowerCase().contains("kick");
        boolean isBan = typeName != null && (typeName.toLowerCase().contains("ban") || typeName.toLowerCase().contains("blacklist"));
        boolean isMute = typeName != null && typeName.toLowerCase().contains("mute");
        boolean isActive = !isKick && punishment.isActive();

        // Calculate initial duration (empty for kicks)
        String initialDuration = "";
        if (!isKick) {
            Long duration = punishment.getDuration();
            if (duration == null || duration <= 0) {
                initialDuration = "Permanent";
            } else {
                initialDuration = MenuItems.formatDuration(duration);
            }
        }

        // Determine type category for title
        String spaceBanMuteOrKick = "";
        if (isKick) {
            spaceBanMuteOrKick = "Kick";
        } else if (isBan) {
            spaceBanMuteOrKick = " Ban";
        } else if (isMute) {
            spaceBanMuteOrKick = " Mute";
        }

        // Build status line using history_item locale keys
        String statusLine;
        if (isKick) {
            statusLine = ""; // Don't show status for kicks
        } else if (isActive) {
            Long duration = punishment.getDuration();
            if (duration == null || duration <= 0) {
                // Permanent
                statusLine = locale.getMessage("menus.history_item.status_permanent");
            } else {
                // Calculate remaining time
                Date started = punishment.getStarted() != null ? punishment.getStarted() : punishment.getIssued();
                long expiryTime = started.getTime() + duration;
                long remaining = expiryTime - System.currentTimeMillis();
                String expiryFormatted = MenuItems.formatDuration(remaining > 0 ? remaining : 0);
                statusLine = locale.getMessage("menus.history_item.status_active",
                        Map.of("expiry", expiryFormatted));
            }
        } else {
            // Inactive
            Long duration = punishment.getDuration();
            if (duration != null && duration > 0) {
                Date started = punishment.getStarted() != null ? punishment.getStarted() : punishment.getIssued();
                long expiryTime = started.getTime() + duration;
                long expiredAgo = System.currentTimeMillis() - expiryTime;
                String expiredFormatted = MenuItems.formatDuration(expiredAgo > 0 ? expiredAgo : 0);
                statusLine = locale.getMessage("menus.history_item.status_inactive",
                        Map.of("expired", expiredFormatted));
            } else {
                statusLine = locale.getMessage("menus.history_item.status_inactive",
                        Map.of("expired", "N/A"));
            }
        }

        // Build notes section - each note on a new line using note_format
        StringBuilder notesBuilder = new StringBuilder();
        List<Note> notes = punishment.getNotes();
        if (notes != null && !notes.isEmpty()) {
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
                if (i > 0) {
                    notesBuilder.append("\n");
                }
                notesBuilder.append(formattedNote);
            }
        }

        // Build variables map using HashMap to allow more than 10 entries
        Map<String, String> vars = new HashMap<>();
        vars.put("punishment_id", punishment.getId() != null ? punishment.getId() : "Unknown");
        vars.put("punishment_type", typeName);
        vars.put("initial_duration_if_not_kick", initialDuration);
        vars.put("space_ban_mute_or_kick", spaceBanMuteOrKick);
        vars.put("status_line", statusLine);
        vars.put("notes", notesBuilder.toString());
        vars.put("reason", punishment.getReason() != null ? punishment.getReason() : "No reason");
        vars.put("issuer", punishment.getIssuerName() != null ? punishment.getIssuerName() : "Unknown");
        vars.put("issued_date", MenuItems.formatDate(punishment.getIssued()));
        // Additional variable for recent punishments - player name
        vars.put("player", pwp.getPlayerName() != null ? pwp.getPlayerName() : "Unknown");

        // Get lore from locale - use history_item format
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.history_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            // Handle {notes} which may contain newlines - split into multiple lore lines
            if (processed.contains("\n")) {
                for (String subLine : processed.split("\n")) {
                    lore.add(subLine);
                }
            } else if (!processed.isEmpty()) {
                lore.add(processed);
            }
        }

        // Get title from locale - use history_item format
        String titleKey = isActive ? "menus.history_item.title_active" : "menus.history_item.title_inactive";
        String title = locale.getMessage(titleKey, vars);

        // Get appropriate item type based on punishment type
        ItemType itemType = getPunishmentItemType(punishment);

        return CirrusItem.of(
                itemType,
                ChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private ItemType getPunishmentItemType(Punishment punishment) {
        if (punishment.getType() == null) return ItemType.PAPER;

        switch (punishment.getType()) {
            case BAN:
            case SECURITY_BAN:
            case LINKED_BAN:
            case BLACKLIST:
                return ItemType.BARRIER;
            case MUTE:
                return ItemType.PAPER;
            case KICK:
                return ItemType.LEATHER_BOOTS;
            default:
                return ItemType.PAPER;
        }
    }

    @Override
    protected void handleClick(Click click, PunishmentWithPlayer pwp) {
        // Handle placeholder - do nothing
        if (pwp.getPunishment() == null) {
            return;
        }

        // Open modify punishment menu with staff menu as parent
        // rootBackAction = return to staff menu (null since we're at top level)
        // menuBackAction = return to this punishments list
        Consumer<CirrusPlayerWrapper> returnToPunishments = p ->
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p);

        if (pwp.getAccount() != null) {
            ActionHandlers.openMenu(
                    new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                            pwp.getAccount(), pwp.getPunishment(), null, returnToPunishments))
                    .handle(click);
        } else {
            // Fetch the account first
            click.clickedMenu().close();
            httpClient.getPlayerProfile(pwp.getPlayerUuid()).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    platform.runOnMainThread(() -> {
                        new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                                response.getProfile(), pwp.getPunishment(), null, returnToPunishments)
                                .display(click.player());
                    });
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

        // Override header navigation - primary tabs should NOT pass backAction
        registerActionHandler("openOnlinePlayers", ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPunishments", click -> {
            // Already here, do nothing
        });

        registerActionHandler("openTickets", ActionHandlers.openMenu(
                new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPanel", click -> {
            sendMessage("");
            sendMessage(MenuItems.COLOR_GOLD + "Staff Panel:");
            sendMessage(MenuItems.COLOR_AQUA + panelUrl);
            sendMessage("");
        });

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));
    }
}
