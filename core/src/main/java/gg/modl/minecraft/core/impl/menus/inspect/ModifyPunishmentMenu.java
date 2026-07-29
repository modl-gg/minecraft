package gg.modl.minecraft.core.impl.menus.inspect;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.ModifyPunishmentTicketsRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.LinkReportsMenu;
import gg.modl.minecraft.core.impl.menus.ViewLinkedTicketsMenu;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.base.InspectChrome;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuAsync;
import gg.modl.minecraft.core.impl.menus.util.PunishmentModificationActions;
import gg.modl.minecraft.core.impl.menus.util.PunishmentModifyItemFactory;
import gg.modl.minecraft.core.util.Permissions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class ModifyPunishmentMenu extends BaseInspectMenu {
    private final Punishment punishment;
    private final Consumer<CirrusPlayerWrapper> menuBackAction, rootBackAction;
    private final PunishmentModificationActions modActions;

    public ModifyPunishmentMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                 Account targetAccount, Punishment punishment, Consumer<CirrusPlayerWrapper> rootBackAction,
                                 Consumer<CirrusPlayerWrapper> menuBackAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, menuBackAction, rootBackAction);
        this.punishment = punishment;
        this.menuBackAction = menuBackAction;
        this.rootBackAction = rootBackAction;
        this.modActions = new PunishmentModificationActions(platform, httpClient, viewerUuid, viewerName,
                targetAccount.getMinecraftUuid(), punishment, this::sendMessage, this::refreshMenu, this::display);

        title("Modify Punishment");
        activeTab = InspectTab.HISTORY;
        buildMenu();
    }

    private void buildMenu() {
        buildHeader();

        Cache cache = PluginServices.cache();
        boolean canModifyNote = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_NOTE);
        boolean canModifyEvidence = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_EVIDENCE);
        boolean canPardon = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_PARDON);
        boolean canModifyDuration = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_DURATION);
        boolean canModifyOptions = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_OPTIONS);
        boolean canModifyTickets = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_TICKETS);

        PunishmentModifyItemFactory items = new PunishmentModifyItemFactory(punishment);
        set(items.addNote(canModifyNote));
        set(items.evidence(canModifyEvidence));
        set(items.pardon(canPardon));
        set(items.duration(canModifyDuration));
        set(items.linkedTickets(canModifyTickets));

        if (items.isBanType()) {
            set(items.statWipe(canModifyOptions));
            set(items.altBlock(canModifyOptions));
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("addNote", modActions::handleAddNote);
        registerActionHandler("evidence", modActions::handleEvidence);
        registerActionHandler("pardon", modActions::handlePardon);
        registerActionHandler("changeDuration", modActions::handleChangeDuration);
        registerActionHandler("linkedTickets", this::handleLinkedTickets);
        registerActionHandler("toggleStatWipe", modActions::handleToggleStatWipe);
        registerActionHandler("toggleAltBlock", modActions::handleToggleAltBlock);
    }

    private void handleLinkedTickets(Click click) {
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            Cache cache = PluginServices.cache();
            boolean canModifyTickets = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_TICKETS);
            if (!canModifyTickets) {
                sendMessage(PluginServices.locale().getMessage("menus.modify_punishment.no_permission_tickets"));
                return;
            }

            Set<String> currentIds = new LinkedHashSet<>(punishment.getAttachedTicketIds());
            Consumer<CirrusPlayerWrapper> backToModify = player -> new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, punishment, rootBackAction, menuBackAction)
                .display(player);

            LinkReportsMenu linkMenu = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                    new InspectChrome(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction, null),
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
                    sendMessage(PluginServices.locale().getMessage("menus.modify_punishment.no_changes"));
                    backToModify.accept(click.player());
                    return;
                }

                String issuerId = PluginServices.cache() != null ? PluginServices.cache().getStaffId(viewerUuid) : null;
                ModifyPunishmentTicketsRequest request = new ModifyPunishmentTicketsRequest(
                        punishment.getId(), viewerName, issuerId, addIds, removeIds, true
                );

                httpClient.modifyPunishmentTickets(request).thenAccept(v -> {
                    sendMessage(PluginServices.locale().getMessage("menus.modify_punishment.tickets_updated"));
                    refreshMenu(click);
                }).exceptionally(e -> {
                    sendMessage(PluginServices.locale().getMessage("menus.modify_punishment.tickets_update_failed"));
                    platform.runOnMainThread(() -> backToModify.accept(click.player()));
                    return null;
                });
            });

            MenuAsync.displayWhenLoaded(platform, linkMenu.getDataFuture(), click.player(), linkMenu::display);
        } else {
            List<String> ticketIds = punishment.getAttachedTicketIds();
            Consumer<CirrusPlayerWrapper> backToModify = player -> new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, punishment, rootBackAction, menuBackAction)
                .display(player);

            ViewLinkedTicketsMenu viewMenu = new ViewLinkedTicketsMenu(
                    platform, httpClient, viewerUuid, viewerName,
                    new InspectChrome(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction, null),
                    ticketIds, backToModify);
            MenuAsync.displayWhenLoaded(platform, viewMenu.getDataFuture(), click.player(), viewMenu::display);
        }
    }

    private void refreshMenu(Click click) {
        httpClient.getPlayerProfile(targetUuid).thenAccept(response -> {
            if (response.getStatus() == 200) {
                HistoryMenu menu = new HistoryMenu(platform, httpClient, viewerUuid, viewerName,
                        response.getProfile(), menuBackAction);
                MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
            }
        });
    }

}
