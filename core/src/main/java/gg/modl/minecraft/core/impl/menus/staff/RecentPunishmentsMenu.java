package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.inspect.ModifyPunishmentMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

        // TODO: Fetch recent punishments when endpoint GET /v1/panel/punishments/recent is available
        // For now, list is empty
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
        // Handle placeholder for empty list
        if (pwp.getPunishment() == null) {
            return createEmptyPlaceholder("No recent punishments");
        }

        Punishment punishment = pwp.getPunishment();
        List<String> lore = new ArrayList<>();

        // Player name
        lore.add(MenuItems.COLOR_GRAY + "Player: " + MenuItems.COLOR_RED + pwp.getPlayerName());

        // Type and status
        String typeName = punishment.getType() != null ? punishment.getType().name() : "Unknown";
        boolean isActive = punishment.isActive();

        lore.add(MenuItems.COLOR_GRAY + "Type: " + (isActive ? MenuItems.COLOR_RED : MenuItems.COLOR_WHITE) + typeName);
        lore.add(MenuItems.COLOR_GRAY + "Status: " + (isActive ? MenuItems.COLOR_RED + "Active" : MenuItems.COLOR_GREEN + "Expired/Pardoned"));

        // Issuer
        lore.add(MenuItems.COLOR_GRAY + "Issued by: " + MenuItems.COLOR_WHITE + punishment.getIssuerName());

        // Duration
        Long duration = punishment.getDuration();
        if (duration != null && duration > 0) {
            lore.add(MenuItems.COLOR_GRAY + "Duration: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(duration));
        } else {
            lore.add(MenuItems.COLOR_GRAY + "Duration: " + MenuItems.COLOR_RED + "Permanent");
        }

        // Reason
        lore.add("");
        lore.add(MenuItems.COLOR_GRAY + "Reason:");
        lore.addAll(MenuItems.wrapText(punishment.getReason(), 6));

        // Action hint
        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Click to modify punishment");

        // Get appropriate item type based on punishment type
        ItemType itemType = getPunishmentItemType(punishment);

        return CirrusItem.of(
                itemType,
                ChatElement.ofLegacyText((isActive ? MenuItems.COLOR_RED : MenuItems.COLOR_GRAY) + typeName + " - " + MenuItems.formatDate(punishment.getIssued())),
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
        click.clickedMenu().close();

        Consumer<CirrusPlayerWrapper> returnToPunishments = p ->
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p);

        if (pwp.getAccount() != null) {
            new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                    pwp.getAccount(), pwp.getPunishment(), returnToPunishments)
                    .display(click.player());
        } else {
            // Fetch the account first
            httpClient.getPlayerProfile(pwp.getPlayerUuid()).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    platform.runOnMainThread(() -> {
                        new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                                response.getProfile(), pwp.getPunishment(), returnToPunishments)
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
