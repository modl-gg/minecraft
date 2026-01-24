package gg.modl.minecraft.core.impl.menus.inspect;

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
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Alts Menu - displays linked/alternate accounts for a player.
 */
public class AltsMenu extends BaseInspectListMenu<Account> {

    private List<Account> linkedAccounts = new ArrayList<>();
    private final Consumer<CirrusPlayerWrapper> backAction;

    /**
     * Create a new alts menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("Alts: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.backAction = backAction;
        activeTab = InspectTab.ALTS;

        // Fetch linked accounts
        loadLinkedAccounts();
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

    private void loadLinkedAccounts() {
        // Fetch linked accounts from API
        httpClient.getLinkedAccounts(targetUuid).thenAccept(response -> {
            if (response.getStatus() == 200 && response.getLinkedAccounts() != null) {
                linkedAccounts = new ArrayList<>(response.getLinkedAccounts());
            }
        }).exceptionally(e -> {
            // API call failed - leave list empty
            return null;
        });
    }

    @Override
    protected Collection<Account> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (linkedAccounts.isEmpty()) {
            return Collections.singletonList(new Account(null, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList()));
        }
        return linkedAccounts;
    }

    @Override
    protected CirrusItem map(Account alt) {
        // Handle placeholder for empty list
        if (alt.getMinecraftUuid() == null) {
            return createEmptyPlaceholder("No linked accounts");
        }

        String altName = getPlayerName(alt);
        List<String> lore = new ArrayList<>();

        // UUID
        lore.add(MenuItems.COLOR_GRAY + "UUID: " + MenuItems.COLOR_WHITE + alt.getMinecraftUuid().toString());

        // Punishment count
        long activePunishments = alt.getPunishments().stream()
                .filter(Punishment::isActive)
                .count();
        if (activePunishments > 0) {
            lore.add(MenuItems.COLOR_RED + "Active Punishments: " + activePunishments);
        } else {
            lore.add(MenuItems.COLOR_GRAY + "Punishments: " + alt.getPunishments().size());
        }

        // IP info
        if (!alt.getIpList().isEmpty()) {
            lore.add(MenuItems.COLOR_GRAY + "Known IPs: " + alt.getIpList().size());
        }

        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Click to inspect " + altName);

        return CirrusItem.of(
                ItemType.PLAYER_HEAD,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + altName),
                MenuItems.lore(lore)
        );
    }

    @Override
    protected void handleClick(Click click, Account alt) {
        // Handle placeholder - do nothing
        if (alt.getMinecraftUuid() == null) {
            return;
        }

        // Open inspect menu for the alt account - this is a new primary view, no back button
        click.clickedMenu().close();
        new InspectMenu(platform, httpClient, viewerUuid, viewerName, alt, null)
                .display(click.player());
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Override header navigation handlers
        // Primary tabs should NOT have back button when switching between them - pass null
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null)));
        registerActionHandler("openAlts", click -> {
            // Already on alts, do nothing
        });
        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null)));
        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null)));
        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null)));
    }
}
