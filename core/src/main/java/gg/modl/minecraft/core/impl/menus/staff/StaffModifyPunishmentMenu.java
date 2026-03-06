package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
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
import gg.modl.minecraft.core.impl.menus.base.BaseStaffMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.PunishmentModificationActions;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;

import java.util.ArrayList;
import java.util.HashMap;
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

        boolean isBanType = punishment.getType() == Punishment.Type.BAN ||
                            punishment.getType() == Punishment.Type.SECURITY_BAN ||
                            punishment.getType() == Punishment.Type.LINKED_BAN ||
                            punishment.getType() == Punishment.Type.BLACKLIST;

        set(CirrusItem.of(
                CirrusItemType.OAK_SIGN,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Add Note"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Add a staff note to this punishment"
                )
        ).slot(MenuSlots.MODIFY_ADD_NOTE).actionHandler("addNote"));

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
        evidenceLore.add("");
        evidenceLore.add(MenuItems.COLOR_YELLOW + "Left-click to add evidence");
        if (!evidenceList.isEmpty())
            evidenceLore.add(MenuItems.COLOR_YELLOW + "Right-click to view in chat");

        set(CirrusItem.of(
                evidenceList.isEmpty() ? CirrusItemType.ARROW : CirrusItemType.SPECTRAL_ARROW,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_AQUA + "Evidence"),
                MenuItems.lore(evidenceLore)
        ).slot(MenuSlots.MODIFY_EVIDENCE).actionHandler("evidence"));

        set(CirrusItem.of(
                CirrusItemType.GOLDEN_APPLE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Pardon Punishment"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Remove punishment and clear",
                        MenuItems.COLOR_GRAY + "associated points"
                )
        ).slot(MenuSlots.MODIFY_PARDON).actionHandler("pardon"));

        Long effectiveDuration = punishment.getEffectiveDuration();
        set(CirrusItem.of(
                CirrusItemType.ANVIL,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Change Duration"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Shorten or lengthen punishment duration",
                        "",
                        MenuItems.COLOR_GRAY + "Current: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(effectiveDuration)
                )
        ).slot(MenuSlots.MODIFY_DURATION).actionHandler("changeDuration"));

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
        ticketLore.add(MenuItems.COLOR_YELLOW + "Right-click to link reports");

        set(CirrusItem.of(
                CirrusItemType.ENDER_EYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Linked Tickets"),
                MenuItems.lore(ticketLore)
        ).slot(MenuSlots.MODIFY_LINKED_TICKETS).actionHandler("linkedTickets"));

        if (isBanType) {
            boolean statWipe = punishment.getDataMap() != null &&
                    Boolean.TRUE.equals(punishment.getDataMap().get("wipeAfterExpiry"));
            set(CirrusItem.of(
                    statWipe ? CirrusItemType.EXPERIENCE_BOTTLE : CirrusItemType.GLASS_BOTTLE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Toggle Stat-Wipe"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + (statWipe ? "Disable" : "Enable") + " stat-wiping for this ban",
                            "",
                            MenuItems.COLOR_GRAY + "Current: " + (statWipe ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")
                    )
            ).slot(MenuSlots.MODIFY_STAT_WIPE).actionHandler("toggleStatWipe"));

            boolean altBlock = punishment.getDataMap() != null &&
                    Boolean.TRUE.equals(punishment.getDataMap().get("altBlocking"));
            set(CirrusItem.of(
                    altBlock ? CirrusItemType.TORCH : CirrusItemType.REDSTONE_TORCH,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Toggle Alt-Blocking"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + (altBlock ? "Disable" : "Enable") + " alt-blocking for this ban",
                            "",
                            MenuItems.COLOR_GRAY + "Current: " + (altBlock ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")
                    )
            ).slot(MenuSlots.MODIFY_ALT_BLOCK).actionHandler("toggleAltBlock"));
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                (name, handler) -> registerActionHandler(name, handler),
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
        Consumer<CirrusPlayerWrapper> backToModify = player -> {
            new StaffModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, punishment, isAdmin, panelUrl, menuBackAction)
                .display(player);
        };

        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            Set<String> currentIds = new LinkedHashSet<>(punishment.getAttachedTicketIds());

            StaffLinkReportsMenu linkMenu = new StaffLinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                    isAdmin, panelUrl, targetAccount, currentIds, backToModify, selectedIds -> {
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

                ModifyPunishmentTicketsRequest request = new ModifyPunishmentTicketsRequest(
                        punishment.getId(), addIds, removeIds, true, viewerName
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

            ActionHandlers.openMenu(linkMenu).handle(click);
        } else {
            List<String> ticketIds = punishment.getAttachedTicketIds();

            StaffViewLinkedTicketsMenu viewMenu = new StaffViewLinkedTicketsMenu(
                    platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, ticketIds, backToModify);
            ActionHandlers.openMenu(viewMenu).handle(click);
        }
    }

    private void refreshMenu(Click click) {
        httpClient.getRecentPunishments(48).thenAccept(response -> {
            if (response.isSuccess() && response.getPunishments() != null) {
                List<RecentPunishmentsMenu.PunishmentWithPlayer> freshData = new ArrayList<>();
                for (var p : response.getPunishments()) {
                    UUID playerUuid = null;
                    try {
                        if (p.getPlayerUuid() != null) {
                            playerUuid = UUID.fromString(p.getPlayerUuid());
                        }
                    } catch (Exception ignored) {}

                    Punishment freshPunishment = new Punishment();
                    freshPunishment.setId(p.getId());
                    freshPunishment.setIssuerName(p.getIssuerName());
                    freshPunishment.setIssued(p.getIssued());
                    freshPunishment.setStarted(p.getStarted());
                    freshPunishment.setTypeOrdinal(p.getTypeOrdinal());
                    freshPunishment.setModifications(p.getModifications());
                    freshPunishment.setNotes(new ArrayList<>(p.getNotes()));
                    freshPunishment.setEvidence(new ArrayList<>(p.getEvidence()));
                    freshPunishment.setDataMap(new HashMap<>(p.getData()));
                    freshPunishment.setAttachedTicketIds(p.getAttachedTicketIds() != null ? new ArrayList<>(p.getAttachedTicketIds()) : null);

                    if (p.getType() != null) {
                        try {
                            freshPunishment.setType(Punishment.Type.valueOf(p.getType()));
                        } catch (IllegalArgumentException ignored) {}
                    }

                    freshData.add(new RecentPunishmentsMenu.PunishmentWithPlayer(freshPunishment, playerUuid, p.getPlayerName(), null));
                }
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null, freshData)
                    .display(click.player());
            } else {
                menuBackAction.accept(click.player());
            }
        }).exceptionally(e -> {
            menuBackAction.accept(click.player());
            return null;
        });
    }

}
