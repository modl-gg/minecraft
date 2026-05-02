package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Evidence;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.ModifyPunishmentTicketsRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.PunishmentModificationActions;
import gg.modl.minecraft.core.util.Permissions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import dev.simplix.cirrus.actionhandler.ActionHandlers;

public class ModifyPunishmentMenu extends BaseInspectMenu {
    private final Punishment punishment;
    private final Consumer<CirrusPlayerWrapper> menuBackAction, rootBackAction;
    private final PunishmentModificationActions modActions;

    public ModifyPunishmentMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                 Account targetAccount, Punishment punishment, Consumer<CirrusPlayerWrapper> rootBackAction,
                                 Consumer<CirrusPlayerWrapper> menuBackAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, menuBackAction);
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

        Cache cache = platform.getCache();
        boolean canModifyNote = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_NOTE);
        boolean canModifyEvidence = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_EVIDENCE);
        boolean canPardon = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_PARDON);
        boolean canModifyDuration = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_DURATION);
        boolean canModifyOptions = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_OPTIONS);

        boolean isBanType = punishment.getType() == Punishment.Type.BAN ||
                            punishment.getType() == Punishment.Type.SECURITY_BAN ||
                            punishment.getType() == Punishment.Type.LINKED_BAN ||
                            punishment.getType() == Punishment.Type.BLACKLIST;

        if (canModifyNote) {
            set(CirrusItem.of(
                    CirrusItemType.OAK_SIGN,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Add Note"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Add a staff note to this punishment"
                    )
            ).slot(MenuSlots.MODIFY_ADD_NOTE).actionHandler("addNote"));
        } else {
            set(CirrusItem.of(
                    CirrusItemType.OAK_SIGN,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Add Note"),
                    MenuItems.lore(
                            MenuItems.COLOR_RED + "No Permission"
                    )
            ).slot(MenuSlots.MODIFY_ADD_NOTE));
        }

        List<String> evidenceLore = new ArrayList<>();
        List<Evidence> evidenceList = punishment.getEvidence();
        if (evidenceList.isEmpty()) {
            evidenceLore.add(MenuItems.COLOR_DARK_GRAY + "(No evidence attached)");
        } else {
            evidenceLore.add(MenuItems.COLOR_GRAY + "Current evidence (" + evidenceList.size() + "):");
            for (int i = 0; i < Math.min(evidenceList.size(), 5); i++) {
                Evidence ev = evidenceList.get(i);
                String display = ev.getDisplayText();
                if (display.length() > 40) display = display.substring(0, 37) + "...";
                evidenceLore.add(MenuItems.COLOR_WHITE + "- " + display);
            }
            if (evidenceList.size() > 5)
                evidenceLore.add(MenuItems.COLOR_DARK_GRAY + "... and " + (evidenceList.size() - 5) + " more");
        }

        if (canModifyEvidence) {
            evidenceLore.add("");
            evidenceLore.add(MenuItems.COLOR_YELLOW + "Left-click to add evidence");
            if (!evidenceList.isEmpty())
                evidenceLore.add(MenuItems.COLOR_YELLOW + "Right-click to view in chat");

            set(CirrusItem.of(
                    evidenceList.isEmpty() ? CirrusItemType.ARROW : CirrusItemType.SPECTRAL_ARROW,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_AQUA + "Evidence"),
                    MenuItems.lore(evidenceLore)
            ).slot(MenuSlots.MODIFY_EVIDENCE).actionHandler("evidence"));
        } else {
            evidenceLore.add("");
            evidenceLore.add(MenuItems.COLOR_RED + "No Permission to modify");

            set(CirrusItem.of(
                    evidenceList.isEmpty() ? CirrusItemType.ARROW : CirrusItemType.SPECTRAL_ARROW,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Evidence"),
                    MenuItems.lore(evidenceLore)
            ).slot(MenuSlots.MODIFY_EVIDENCE));
        }

        if (canPardon) {
            set(CirrusItem.of(
                    CirrusItemType.GOLDEN_APPLE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Pardon Punishment"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Remove punishment and clear",
                            MenuItems.COLOR_GRAY + "associated points"
                    )
            ).slot(MenuSlots.MODIFY_PARDON).actionHandler("pardon"));
        } else {
            set(CirrusItem.of(
                    CirrusItemType.GOLDEN_APPLE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Pardon Punishment"),
                    MenuItems.lore(
                            MenuItems.COLOR_RED + "No Permission"
                    )
            ).slot(MenuSlots.MODIFY_PARDON));
        }

        Long effectiveDuration = punishment.getEffectiveDuration();
        if (canModifyDuration) {
            set(CirrusItem.of(
                    CirrusItemType.ANVIL,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Change Duration"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Shorten or lengthen punishment duration",
                            "",
                            MenuItems.COLOR_GRAY + "Current: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(effectiveDuration)
                    )
            ).slot(MenuSlots.MODIFY_DURATION).actionHandler("changeDuration"));
        } else {
            set(CirrusItem.of(
                    CirrusItemType.ANVIL,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Change Duration"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Current: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(effectiveDuration),
                            "",
                            MenuItems.COLOR_RED + "No Permission"
                    )
            ).slot(MenuSlots.MODIFY_DURATION));
        }

        boolean canModifyTickets = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_TICKETS);
        List<String> ticketIds = punishment.getAttachedTicketIds();
        List<String> ticketLore = new ArrayList<>();
        if (ticketIds.isEmpty()) {
            ticketLore.add(MenuItems.COLOR_DARK_GRAY + "(No linked tickets)");
        } else {
            ticketLore.add(MenuItems.COLOR_GRAY + "Linked tickets (" + ticketIds.size() + "):");
            for (int i = 0; i < Math.min(ticketIds.size(), 5); i++) {
                ticketLore.add(MenuItems.COLOR_WHITE + "- " + ticketIds.get(i));
            }
            if (ticketIds.size() > 5)
                ticketLore.add(MenuItems.COLOR_DARK_GRAY + "... and " + (ticketIds.size() - 5) + " more");
        }
        ticketLore.add("");
        ticketLore.add(MenuItems.COLOR_YELLOW + "Left-click to view tickets");
        if (canModifyTickets)
            ticketLore.add(MenuItems.COLOR_YELLOW + "Right-click to link reports");

        set(CirrusItem.of(
                CirrusItemType.ENDER_EYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Linked Tickets"),
                MenuItems.lore(ticketLore)
        ).slot(MenuSlots.MODIFY_LINKED_TICKETS).actionHandler("linkedTickets"));

        if (isBanType) {
            boolean statWipe = Boolean.TRUE.equals(punishment.getDataMap().get("wipeAfterExpiry"));

            if (canModifyOptions) {
                set(CirrusItem.of(
                        statWipe ? CirrusItemType.EXPERIENCE_BOTTLE : CirrusItemType.GLASS_BOTTLE,
                        CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Toggle Stat-Wipe"),
                        MenuItems.lore(
                                MenuItems.COLOR_GRAY + (statWipe ? "Disable" : "Enable") + " stat-wiping for this ban",
                                "",
                                MenuItems.COLOR_GRAY + "Current: " + (statWipe ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")
                        )
                ).slot(MenuSlots.MODIFY_STAT_WIPE).actionHandler("toggleStatWipe"));
            } else {
                set(CirrusItem.of(
                        statWipe ? CirrusItemType.EXPERIENCE_BOTTLE : CirrusItemType.GLASS_BOTTLE,
                        CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Toggle Stat-Wipe"),
                        MenuItems.lore(
                                MenuItems.COLOR_GRAY + "Current: " + (statWipe ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled"),
                                "",
                                MenuItems.COLOR_RED + "No Permission"
                        )
                ).slot(MenuSlots.MODIFY_STAT_WIPE));
            }

            boolean altBlock = Boolean.TRUE.equals(punishment.getDataMap().get("altBlocking"));

            if (canModifyOptions) {
                set(CirrusItem.of(
                        altBlock ? CirrusItemType.TORCH : CirrusItemType.REDSTONE_TORCH,
                        CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Toggle Alt-Blocking"),
                        MenuItems.lore(
                                MenuItems.COLOR_GRAY + (altBlock ? "Disable" : "Enable") + " alt-blocking for this ban",
                                "",
                                MenuItems.COLOR_GRAY + "Current: " + (altBlock ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")
                        )
                ).slot(MenuSlots.MODIFY_ALT_BLOCK).actionHandler("toggleAltBlock"));
            } else {
                set(CirrusItem.of(
                        altBlock ? CirrusItemType.TORCH : CirrusItemType.REDSTONE_TORCH,
                        CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Toggle Alt-Blocking"),
                        MenuItems.lore(
                                MenuItems.COLOR_GRAY + "Current: " + (altBlock ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled"),
                                "",
                                MenuItems.COLOR_RED + "No Permission"
                        )
                ).slot(MenuSlots.MODIFY_ALT_BLOCK));
            }
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction);

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
            Cache cache = platform.getCache();
            boolean canModifyTickets = cache != null && cache.hasPermission(viewerUuid, Permissions.PUNISHMENT_MODIFY_TICKETS);
            if (!canModifyTickets) {
                sendMessage(platform.getLocaleManager().getMessage("menus.modify_punishment.no_permission_tickets"));
                return;
            }

            Set<String> currentIds = new LinkedHashSet<>(punishment.getAttachedTicketIds());
            Consumer<CirrusPlayerWrapper> backToModify = player -> new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, punishment, rootBackAction, menuBackAction)
                .display(player);

            LinkReportsMenu linkMenu = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                    targetAccount, currentIds, backToModify, rootBackAction, selectedIds -> {
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
                    sendMessage(platform.getLocaleManager().getMessage("menus.modify_punishment.no_changes"));
                    backToModify.accept(click.player());
                    return;
                }

                String issuerId = platform.getCache() != null ? platform.getCache().getStaffId(viewerUuid) : null;
                ModifyPunishmentTicketsRequest request = new ModifyPunishmentTicketsRequest(
                        punishment.getId(), viewerName, issuerId, addIds, removeIds, true
                );

                httpClient.modifyPunishmentTickets(request).thenAccept(v -> {
                    sendMessage(platform.getLocaleManager().getMessage("menus.modify_punishment.tickets_updated"));
                    refreshMenu(click);
                }).exceptionally(e -> {
                    sendMessage(platform.getLocaleManager().getMessage("menus.modify_punishment.tickets_update_failed"));
                    platform.runOnMainThread(() -> backToModify.accept(click.player()));
                    return null;
                });
            });

            ActionHandlers.openMenu(linkMenu).handle(click);
        } else {
            List<String> ticketIds = punishment.getAttachedTicketIds();
            Consumer<CirrusPlayerWrapper> backToModify = player -> new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, punishment, rootBackAction, menuBackAction)
                .display(player);

            ViewLinkedTicketsMenu viewMenu = new ViewLinkedTicketsMenu(
                    platform, httpClient, viewerUuid, viewerName, targetAccount, ticketIds, backToModify, rootBackAction);
            ActionHandlers.openMenu(viewMenu).handle(click);
        }
    }

    private void refreshMenu(Click click) {
        httpClient.getPlayerProfile(targetUuid).thenAccept(response -> {
            if (response.getStatus() == 200) {
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName,
                    response.getProfile(), menuBackAction)
                    .display(click.player());
            }
        });
    }

}
