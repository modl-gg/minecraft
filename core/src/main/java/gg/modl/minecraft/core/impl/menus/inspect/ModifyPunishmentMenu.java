package gg.modl.minecraft.core.impl.menus.inspect;

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
import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.request.AddPunishmentNoteRequest;
import gg.modl.minecraft.api.http.request.ChangePunishmentDurationRequest;
import gg.modl.minecraft.api.http.request.ModifyPunishmentTicketsRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.request.TogglePunishmentOptionRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Modify Punishment Menu - allows modifying an existing punishment.
 * Secondary menu accessed from History menu.
 */
public class ModifyPunishmentMenu extends BaseInspectMenu {

    private final Punishment punishment;
    private final Consumer<CirrusPlayerWrapper> menuBackAction; // Goes back to HistoryMenu
    private final Consumer<CirrusPlayerWrapper> rootBackAction; // Passed to primary tabs (e.g., back to Staff Menu)

    /**
     * Create a new modify punishment menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param punishment The punishment to modify
     * @param rootBackAction Root back action for primary tab navigation (e.g., back to Staff Menu)
     * @param menuBackAction Action to return to parent menu (HistoryMenu)
     */
    public ModifyPunishmentMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                 Account targetAccount, Punishment punishment, Consumer<CirrusPlayerWrapper> rootBackAction,
                                 Consumer<CirrusPlayerWrapper> menuBackAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, menuBackAction);
        this.punishment = punishment;
        this.menuBackAction = menuBackAction;
        this.rootBackAction = rootBackAction;

        title("Modify Punishment");
        activeTab = InspectTab.HISTORY;
        buildMenu();
    }

    private void buildMenu() {
        buildHeader();

        Cache cache = platform.getCache();
        boolean canModifyNote = cache != null && cache.hasPermission(viewerUuid, "punishment.modify.note");
        boolean canModifyEvidence = cache != null && cache.hasPermission(viewerUuid, "punishment.modify.evidence");
        boolean canPardon = cache != null && cache.hasPermission(viewerUuid, "punishment.modify.pardon");
        boolean canModifyDuration = cache != null && cache.hasPermission(viewerUuid, "punishment.modify.duration");
        boolean canModifyOptions = cache != null && cache.hasPermission(viewerUuid, "punishment.modify.options");

        String typeName = punishment.getType() != null ? punishment.getType().name() : "Unknown";
        boolean isBanType = punishment.getType() == Punishment.Type.BAN ||
                            punishment.getType() == Punishment.Type.SECURITY_BAN ||
                            punishment.getType() == Punishment.Type.LINKED_BAN ||
                            punishment.getType() == Punishment.Type.BLACKLIST;

        // Slot 28: Add Note
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

        // Slot 29: Evidence
        List<String> evidenceLore = new ArrayList<>();
        List<Evidence> evidenceList = punishment.getEvidence();
        if (evidenceList.isEmpty()) {
            evidenceLore.add(MenuItems.COLOR_DARK_GRAY + "(No evidence attached)");
        } else {
            evidenceLore.add(MenuItems.COLOR_GRAY + "Current evidence (" + evidenceList.size() + "):");
            for (int i = 0; i < Math.min(evidenceList.size(), 5); i++) {
                Evidence ev = evidenceList.get(i);
                String display = ev.getDisplayText();
                if (display.length() > 40) {
                    display = display.substring(0, 37) + "...";
                }
                evidenceLore.add(MenuItems.COLOR_WHITE + "- " + display);
            }
            if (evidenceList.size() > 5) {
                evidenceLore.add(MenuItems.COLOR_DARK_GRAY + "... and " + (evidenceList.size() - 5) + " more");
            }
        }

        if (canModifyEvidence) {
            evidenceLore.add("");
            evidenceLore.add(MenuItems.COLOR_YELLOW + "Left-click to add evidence");
            if (!evidenceList.isEmpty()) {
                evidenceLore.add(MenuItems.COLOR_YELLOW + "Right-click to view in chat");
            }

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

        // Slot 30: Pardon Punishment
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

        // Slot 31: Change Duration
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

        // Slot 32: Linked Tickets
        boolean canModifyTickets = cache != null && cache.hasPermission(viewerUuid, "punishment.modify.tickets");
        List<String> ticketIds = punishment.getAttachedTicketIds();
        List<String> ticketLore = new ArrayList<>();
        if (ticketIds.isEmpty()) {
            ticketLore.add(MenuItems.COLOR_DARK_GRAY + "(No linked tickets)");
        } else {
            ticketLore.add(MenuItems.COLOR_GRAY + "Linked tickets (" + ticketIds.size() + "):");
            for (int i = 0; i < Math.min(ticketIds.size(), 5); i++) {
                ticketLore.add(MenuItems.COLOR_WHITE + "- " + ticketIds.get(i));
            }
            if (ticketIds.size() > 5) {
                ticketLore.add(MenuItems.COLOR_DARK_GRAY + "... and " + (ticketIds.size() - 5) + " more");
            }
        }
        ticketLore.add("");
        ticketLore.add(MenuItems.COLOR_YELLOW + "Left-click to view tickets");
        if (canModifyTickets) {
            ticketLore.add(MenuItems.COLOR_YELLOW + "Right-click to link reports");
        }

        set(CirrusItem.of(
                CirrusItemType.ENDER_EYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Linked Tickets"),
                MenuItems.lore(ticketLore)
        ).slot(MenuSlots.MODIFY_LINKED_TICKETS).actionHandler("linkedTickets"));

        // Slot 33: Toggle Stat-Wipe (ban types only)
        if (isBanType) {
            // Get actual stat-wipe status from punishment data
            boolean statWipe = punishment.getDataMap() != null &&
                    Boolean.TRUE.equals(punishment.getDataMap().get("wipeAfterExpiry"));

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

            // Slot 34: Toggle Alt-Blocking
            // Get actual alt-blocking status from punishment data
            boolean altBlock = punishment.getDataMap() != null &&
                    Boolean.TRUE.equals(punishment.getDataMap().get("altBlocking"));

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

        // Override header navigation - pass rootBackAction to preserve the back button on primary tabs
        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        // Add note handler
        registerActionHandler("addNote", this::handleAddNote);

        // Evidence handler
        registerActionHandler("evidence", this::handleEvidence);

        // Pardon handler
        registerActionHandler("pardon", this::handlePardon);

        // Change duration handler
        registerActionHandler("changeDuration", this::handleChangeDuration);

        // Linked tickets handler
        registerActionHandler("linkedTickets", this::handleLinkedTickets);

        // Toggle stat-wipe handler
        registerActionHandler("toggleStatWipe", this::handleToggleStatWipe);

        // Toggle alt-blocking handler
        registerActionHandler("toggleAltBlock", this::handleToggleAltBlock);
    }

    private void handleAddNote(Click click) {
        click.clickedMenu().close();

        ChatInputManager.requestInput(platform, viewerUuid, "Enter note to add to this punishment:",
                input -> {
                    AddPunishmentNoteRequest request = new AddPunishmentNoteRequest(
                            punishment.getId(),
                            viewerName,
                            input
                    );

                    httpClient.addPunishmentNote(request).thenAccept(v -> {
                        sendMessage(MenuItems.COLOR_GREEN + "Note added successfully!");
                        // Refresh by fetching updated player profile
                        refreshMenu(click);
                    }).exceptionally(e -> {
                        sendMessage(MenuItems.COLOR_RED + "Failed to add note: " + e.getMessage());
                        platform.runOnMainThread(() -> display(click.player()));
                        return null;
                    });
                },
                () -> {
                    sendMessage(MenuItems.COLOR_GRAY + "Note cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }

    private void handleEvidence(Click click) {
        // Right-click: View evidence in chat
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            List<Evidence> evidenceList = punishment.getEvidence();
            if (evidenceList.isEmpty()) {
                sendMessage(MenuItems.COLOR_GRAY + "No evidence attached to this punishment.");
                return;
            }

            sendMessage("");
            sendMessage(MenuItems.COLOR_GOLD + "Evidence for punishment #" + punishment.getId() + ":");
            for (int i = 0; i < evidenceList.size(); i++) {
                Evidence ev = evidenceList.get(i);
                String display = ev.getDisplayText();
                sendMessage(MenuItems.COLOR_WHITE + (i + 1) + ". " + display);
                sendMessage(MenuItems.COLOR_GRAY + "   Added by " + ev.getUploadedBy() + " on " + MenuItems.formatDate(ev.getUploadedAt()));
            }
            sendMessage("");
            return;
        }

        // Left-click: Add evidence
        click.clickedMenu().close();

        ChatInputManager.requestInput(platform, viewerUuid, "Enter evidence URL:",
                input -> {
                    AddPunishmentEvidenceRequest request = new AddPunishmentEvidenceRequest(
                            punishment.getId(),
                            viewerName,
                            input
                    );

                    httpClient.addPunishmentEvidence(request).thenAccept(v -> {
                        sendMessage(MenuItems.COLOR_GREEN + "Evidence added successfully!");
                        // Refresh by fetching updated player profile
                        refreshMenu(click);
                    }).exceptionally(e -> {
                        sendMessage(MenuItems.COLOR_RED + "Failed to add evidence: " + e.getMessage());
                        platform.runOnMainThread(() -> display(click.player()));
                        return null;
                    });
                },
                () -> {
                    sendMessage(MenuItems.COLOR_GRAY + "Evidence cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }

    private void handlePardon(Click click) {
        PardonPunishmentRequest request = new PardonPunishmentRequest(
                punishment.getId(),
                viewerName,
                null, // reason - automatic note added by backend
                null // expectedType
        );

        httpClient.pardonPunishment(request).thenAccept(response -> {
            if (response.hasPardoned()) {
                sendMessage(MenuItems.COLOR_GREEN + "Punishment pardoned successfully!");
                // Invalidate cache so the pardon takes effect immediately
                invalidateCache();
                // Return to history menu
                click.clickedMenu().close();
                refreshMenu(click);
            } else {
                sendMessage(MenuItems.COLOR_GRAY + "Punishment is already inactive or has been pardoned.");
            }
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to pardon punishment: " + e.getMessage());
            return null;
        });
    }

    private void handleChangeDuration(Click click) {
        click.clickedMenu().close();

        ChatInputManager.requestInput(platform, viewerUuid,
                "Enter new duration (e.g., 30d, 2h, 30m, 1d2h30m, or 'perm' for permanent):",
                input -> {
                    Long durationMs = parseDuration(input);
                    if (durationMs == null && !input.equalsIgnoreCase("perm") && !input.equalsIgnoreCase("permanent")) {
                        sendMessage(MenuItems.COLOR_RED + "Invalid duration format. Examples: 30d, 2h, 30m, 1d2h30m");
                        platform.runOnMainThread(() -> display(click.player()));
                        return;
                    }

                    // null duration means permanent
                    ChangePunishmentDurationRequest request = new ChangePunishmentDurationRequest(
                            punishment.getId(),
                            viewerName,
                            durationMs
                    );

                    httpClient.changePunishmentDuration(request).thenAccept(v -> {
                        sendMessage(MenuItems.COLOR_GREEN + "Duration changed successfully!");
                        // Invalidate cache so the new duration takes effect
                        invalidateCache();
                        // Refresh by fetching updated player profile
                        refreshMenu(click);
                    }).exceptionally(e -> {
                        sendMessage(MenuItems.COLOR_RED + "Failed to change duration: " + e.getMessage());
                        platform.runOnMainThread(() -> display(click.player()));
                        return null;
                    });
                },
                () -> {
                    sendMessage(MenuItems.COLOR_GRAY + "Duration change cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }

    /**
     * Parse a duration string (e.g., "30d", "2h", "30m", "1d2h30m") to milliseconds.
     * Returns null for "perm" or "permanent" (indicating permanent punishment).
     */
    private Long parseDuration(String input) {
        if (input == null || input.isEmpty()) return null;
        if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) return null;

        long total = 0;
        StringBuilder number = new StringBuilder();

        for (char c : input.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else if (c == 'd' || c == 'h' || c == 'm' || c == 's') {
                if (number.length() == 0) continue;
                long value = Long.parseLong(number.toString());
                number.setLength(0);

                switch (c) {
                    case 'd' -> total += value * 24 * 60 * 60 * 1000;
                    case 'h' -> total += value * 60 * 60 * 1000;
                    case 'm' -> total += value * 60 * 1000;
                    case 's' -> total += value * 1000;
                }
            }
        }

        return total > 0 ? total : null;
    }

    private void handleLinkedTickets(Click click) {
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            // Right-click: Open LinkReportsMenu for modifying ticket links
            Cache cache = platform.getCache();
            boolean canModifyTickets = cache != null && cache.hasPermission(viewerUuid, "punishment.modify.tickets");
            if (!canModifyTickets) {
                sendMessage(MenuItems.COLOR_RED + "You don't have permission to modify linked tickets.");
                return;
            }

            Set<String> currentIds = new LinkedHashSet<>(punishment.getAttachedTicketIds());
            Consumer<CirrusPlayerWrapper> backToModify = player -> {
                platform.runOnMainThread(() -> {
                    new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                            targetAccount, punishment, rootBackAction, menuBackAction)
                            .display(player);
                });
            };

            LinkReportsMenu linkMenu = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                    targetAccount, currentIds, backToModify, rootBackAction, selectedIds -> {
                // Compute diff
                List<String> originalIds = punishment.getAttachedTicketIds();
                List<String> addIds = new ArrayList<>();
                List<String> removeIds = new ArrayList<>();

                for (String id : selectedIds) {
                    if (!originalIds.contains(id)) {
                        addIds.add(id);
                    }
                }
                for (String id : originalIds) {
                    if (!selectedIds.contains(id)) {
                        removeIds.add(id);
                    }
                }

                if (addIds.isEmpty() && removeIds.isEmpty()) {
                    sendMessage(MenuItems.COLOR_GRAY + "No changes to linked tickets.");
                    platform.runOnMainThread(() -> backToModify.accept(click.player()));
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
                    platform.runOnMainThread(() -> backToModify.accept(click.player()));
                    return null;
                });
            });

            dev.simplix.cirrus.actionhandler.ActionHandlers.openMenu(linkMenu).handle(click);
        } else {
            // Left-click: View linked tickets
            List<String> ticketIds = punishment.getAttachedTicketIds();
            Consumer<CirrusPlayerWrapper> backToModify = player -> {
                platform.runOnMainThread(() -> {
                    new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                            targetAccount, punishment, rootBackAction, menuBackAction)
                            .display(player);
                });
            };

            ViewLinkedTicketsMenu viewMenu = new ViewLinkedTicketsMenu(
                    platform, httpClient, viewerUuid, viewerName, targetAccount, ticketIds, backToModify, rootBackAction);
            dev.simplix.cirrus.actionhandler.ActionHandlers.openMenu(viewMenu).handle(click);
        }
    }

    private void handleToggleStatWipe(Click click) {
        // Get current status from punishment data
        boolean currentStatus = punishment.getDataMap() != null &&
                Boolean.TRUE.equals(punishment.getDataMap().get("wipeAfterExpiry"));

        TogglePunishmentOptionRequest request = new TogglePunishmentOptionRequest(
                punishment.getId(),
                viewerName,
                "STAT_WIPE",
                !currentStatus // Toggle to opposite
        );

        httpClient.togglePunishmentOption(request).thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Stat-wipe " + (!currentStatus ? "enabled" : "disabled") + " successfully!");
            // Refresh menu to show new status
            refreshMenu(click);
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to toggle stat-wipe: " + e.getMessage());
            return null;
        });
    }

    private void handleToggleAltBlock(Click click) {
        // Get current status from punishment data
        boolean currentStatus = punishment.getDataMap() != null &&
                Boolean.TRUE.equals(punishment.getDataMap().get("altBlocking"));

        TogglePunishmentOptionRequest request = new TogglePunishmentOptionRequest(
                punishment.getId(),
                viewerName,
                "ALT_BLOCKING",
                !currentStatus // Toggle to opposite
        );

        httpClient.togglePunishmentOption(request).thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Alt-blocking " + (!currentStatus ? "enabled" : "disabled") + " successfully!");
            // Refresh menu to show new status
            refreshMenu(click);
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to toggle alt-blocking: " + e.getMessage());
            return null;
        });
    }

    /**
     * Refresh the menu by fetching the updated player profile and returning to history menu.
     */
    private void refreshMenu(Click click) {
        platform.runOnMainThread(() -> {
            httpClient.getPlayerProfile(targetUuid).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    platform.runOnMainThread(() -> {
                        new HistoryMenu(platform, httpClient, viewerUuid, viewerName,
                                response.getProfile(), menuBackAction)
                                .display(click.player());
                    });
                }
            });
        });
    }

    /**
     * Invalidate the cache for the target player to ensure updated punishment data takes effect.
     */
    private void invalidateCache() {
        if (platform.getCache() != null) {
            // Clear both ban and mute cache for this player since we don't know which type the punishment is
            platform.getCache().removeBan(targetUuid);
            platform.getCache().removeMute(targetUuid);
        }
    }
}
