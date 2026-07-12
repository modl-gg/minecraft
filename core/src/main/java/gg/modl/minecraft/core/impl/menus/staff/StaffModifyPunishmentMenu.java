package gg.modl.minecraft.core.impl.menus.staff;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.ModifyPunishmentTicketsRequest;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.LinkReportsMenu;
import gg.modl.minecraft.core.impl.menus.ViewLinkedTicketsMenu;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffMenu;
import gg.modl.minecraft.core.impl.menus.base.StaffChrome;
import gg.modl.minecraft.core.impl.menus.util.MenuAsync;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.PunishmentModificationActions;
import gg.modl.minecraft.core.impl.menus.util.PunishmentModifyItemFactory;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.punishment.RecentPunishmentMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class StaffModifyPunishmentMenu extends BaseStaffMenu {
    private final Account targetAccount;
    private final Punishment punishment;
    private final Consumer<CirrusPlayerWrapper> menuBackAction;
    private final String panelUrl;
    private final PunishmentModificationActions modActions;

    public StaffModifyPunishmentMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                      Account targetAccount, Punishment punishment, boolean isAdmin, String panelUrl,
                                      Consumer<CirrusPlayerWrapper> menuBackAction) {
        super(platform, httpClient, viewerUuid, viewerName, isAdmin, menuBackAction);
        this.targetAccount = targetAccount;
        this.punishment = punishment;
        this.menuBackAction = menuBackAction;
        this.panelUrl = panelUrl;
        this.modActions = new PunishmentModificationActions(platform, httpClient, viewerUuid, viewerName,
                targetAccount.getMinecraftUuid(), punishment, this::sendMessage, this::refreshMenu, this::display);

        title("Modify Punishment");
        activeTab = StaffTab.PUNISHMENTS;
        buildMenu();
    }

    private void buildMenu() {
        buildHeader();

        PunishmentModifyItemFactory items = new PunishmentModifyItemFactory(punishment);
        set(items.addNote(true));
        set(items.evidence(true));
        set(items.pardon(true));
        set(items.duration(true));
        set(items.linkedTickets(true));

        if (items.isBanType()) {
            set(items.statWipe(true));
            set(items.altBlock(true));
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("addNote", modActions::handleAddNote);
        registerActionHandler("evidence", modActions::handleEvidence);
        registerActionHandler("pardon", modActions::handlePardon);
        registerActionHandler("changeDuration", modActions::handleChangeDuration);
        registerActionHandler("linkedTickets", this::handleLinkedTickets);
        registerActionHandler("toggleStatWipe", modActions::handleToggleStatWipe);
        registerActionHandler("toggleAltBlock", modActions::handleToggleAltBlock);
    }

    private void handleLinkedTickets(Click click) {
        Consumer<CirrusPlayerWrapper> backToModify = player -> new StaffModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
            targetAccount, punishment, isAdmin, panelUrl, menuBackAction)
            .display(player);

        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            Set<String> currentIds = new LinkedHashSet<>(punishment.getAttachedTicketIds());

            LinkReportsMenu linkMenu = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                    new StaffChrome(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl),
                    targetAccount, currentIds, backToModify, selectedIds -> {
                List<String> originalIds = punishment.getAttachedTicketIds();
                List<String> addIds = new ArrayList<>();
                List<String> removeIds = new ArrayList<>();

                for (String id : selectedIds)
                    if (!originalIds.contains(id))
                        addIds.add(id);
                for (String id : originalIds)
                    if (!selectedIds.contains(id))
                        removeIds.add(id);

                if (addIds.isEmpty() && removeIds.isEmpty()) {
                    sendMessage(MenuItems.COLOR_GRAY + "No changes to linked tickets.");
                    backToModify.accept(click.player());
                    return;
                }

                String issuerId = PluginServices.cache() != null ? PluginServices.cache().getStaffId(viewerUuid) : null;
                ModifyPunishmentTicketsRequest request = new ModifyPunishmentTicketsRequest(
                        punishment.getId(), viewerName, issuerId, addIds, removeIds, true
                );

                httpClient.modifyPunishmentTickets(request).thenAccept(v -> {
                    sendMessage(MenuItems.COLOR_GREEN + "Linked tickets updated successfully!");
                    refreshMenu(click);
                }).exceptionally(e -> {
                    sendMessage(MenuItems.COLOR_RED + "Failed to update linked tickets: " + e.getMessage());
                    backToModify.accept(click.player());
                    return null;
                });
            });

            MenuAsync.displayWhenLoaded(platform, linkMenu.getDataFuture(), click.player(), linkMenu::display);
        } else {
            List<String> ticketIds = punishment.getAttachedTicketIds();

            ViewLinkedTicketsMenu viewMenu = new ViewLinkedTicketsMenu(
                    platform, httpClient, viewerUuid, viewerName,
                    new StaffChrome(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl),
                    ticketIds, backToModify);
            MenuAsync.displayWhenLoaded(platform, viewMenu.getDataFuture(), click.player(), viewMenu::display);
        }
    }

    private void refreshMenu(Click click) {
        httpClient.getRecentPunishments(48).thenAccept(response -> {
            if (response.isSuccess() && response.getPunishments() != null) {
                List<RecentPunishmentsMenu.PunishmentWithPlayer> freshData = new ArrayList<>();
                for (RecentPunishmentsResponse.RecentPunishment p : response.getPunishments()) {
                    UUID playerUuid = null;
                    try {
                        if (p.getPlayerUuid() != null) {
                            playerUuid = UUID.fromString(p.getPlayerUuid());
                        }
                    } catch (Exception ignored) {}

                    freshData.add(new RecentPunishmentsMenu.PunishmentWithPlayer(
                            RecentPunishmentMapper.toPunishment(p), playerUuid, p.getPlayerName(), null));
                }
                RecentPunishmentsMenu menu = new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null, freshData);
                StaffNavigationHandlers.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
            } else {
                menuBackAction.accept(click.player());
            }
        }).exceptionally(e -> {
            menuBackAction.accept(click.player());
            return null;
        });
    }

}
