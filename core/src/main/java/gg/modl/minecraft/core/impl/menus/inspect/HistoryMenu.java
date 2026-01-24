package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * History Menu - displays punishment history for a player.
 */
public class HistoryMenu extends BaseInspectListMenu<Punishment> {

    private final Consumer<CirrusPlayerWrapper> backAction;

    /**
     * Create a new history menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public HistoryMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("History: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.backAction = backAction;
        activeTab = InspectTab.HISTORY;
    }

    private static String getPlayerNameStatic(Account account) {
        if (account.getUsernames() != null && !account.getUsernames().isEmpty()) {
            return account.getUsernames().stream()
                    .max((u1, u2) -> u1.getDate().compareTo(u2.getDate()))
                    .map(Account.Username::getUsername)
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    @Override
    protected Collection<Punishment> elements() {
        // Punishments are stored in the account object
        // Sort by date, newest first
        List<Punishment> punishments = new ArrayList<>(targetAccount.getPunishments());
        punishments.sort((p1, p2) -> p2.getIssued().compareTo(p1.getIssued()));
        return punishments;
    }

    @Override
    protected CirrusItem map(Punishment punishment) {
        List<String> lore = new ArrayList<>();

        // Type and status
        String typeName = punishment.getType() != null ? punishment.getType().name() : "Unknown";
        boolean isActive = punishment.isActive();

        lore.add(MenuItems.COLOR_GRAY + "Type: " + (isActive ? MenuItems.COLOR_RED : MenuItems.COLOR_WHITE) + typeName);
        lore.add(MenuItems.COLOR_GRAY + "Status: " + (isActive ? MenuItems.COLOR_RED + "Active" : MenuItems.COLOR_GREEN + "Expired/Pardoned"));

        // Issuer
        lore.add(MenuItems.COLOR_GRAY + "Issued by: " + MenuItems.COLOR_WHITE + punishment.getIssuerName());

        // Date
        lore.add(MenuItems.COLOR_GRAY + "Date: " + MenuItems.COLOR_WHITE + MenuItems.formatDate(punishment.getIssued()));

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
    protected void handleClick(Click click, Punishment punishment) {
        // Open modify punishment menu
        click.clickedMenu().close();
        new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishment, backAction)
                .display(click.player());
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Override header navigation handlers
        registerActionHandler("openNotes", click -> {
            click.clickedMenu().close();
            new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openAlts", click -> {
            click.clickedMenu().close();
            new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openHistory", click -> {
            // Already on history, do nothing
        });
        registerActionHandler("openReports", click -> {
            click.clickedMenu().close();
            new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openPunish", click -> {
            click.clickedMenu().close();
            new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
    }
}
