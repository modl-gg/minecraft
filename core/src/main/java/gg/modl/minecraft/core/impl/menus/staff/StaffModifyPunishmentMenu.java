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
import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.request.AddPunishmentNoteRequest;
import gg.modl.minecraft.api.http.request.ChangePunishmentDurationRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.request.TogglePunishmentOptionRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffMenu;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Staff Modify Punishment Menu - allows modifying an existing punishment from the staff menu.
 * Uses the staff menu header instead of the inspect menu header.
 */
public class StaffModifyPunishmentMenu extends BaseStaffMenu {

    private final Account targetAccount;
    private final UUID targetUuid;
    private final Punishment punishment;
    private final Consumer<CirrusPlayerWrapper> menuBackAction;
    private final String panelUrl;

    /**
     * Create a new staff modify punishment menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param punishment The punishment to modify
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param menuBackAction Action to return to parent menu (RecentPunishmentsMenu)
     */
    public StaffModifyPunishmentMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                      Account targetAccount, Punishment punishment, boolean isAdmin, String panelUrl,
                                      Consumer<CirrusPlayerWrapper> menuBackAction) {
        super(platform, httpClient, viewerUuid, viewerName, isAdmin, menuBackAction);
        this.targetAccount = targetAccount;
        this.targetUuid = targetAccount.getMinecraftUuid();
        this.punishment = punishment;
        this.menuBackAction = menuBackAction;
        this.panelUrl = panelUrl;

        title("Modify Punishment");
        activeTab = StaffTab.PUNISHMENTS;
        buildMenu();
    }

    private void buildMenu() {
        buildHeader();

        String typeName = punishment.getType() != null ? punishment.getType().name() : "Unknown";
        boolean isBanType = punishment.getType() == Punishment.Type.BAN ||
                            punishment.getType() == Punishment.Type.SECURITY_BAN ||
                            punishment.getType() == Punishment.Type.LINKED_BAN ||
                            punishment.getType() == Punishment.Type.BLACKLIST;

        // Slot 28: Add Note
        set(CirrusItem.of(
                CirrusItemType.OAK_SIGN,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Add Note"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Add a staff note to this punishment"
                )
        ).slot(MenuSlots.MODIFY_ADD_NOTE).actionHandler("addNote"));

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

        // Slot 30: Pardon Punishment
        set(CirrusItem.of(
                CirrusItemType.GOLDEN_APPLE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Pardon Punishment"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Remove punishment and clear",
                        MenuItems.COLOR_GRAY + "associated points"
                )
        ).slot(MenuSlots.MODIFY_PARDON).actionHandler("pardon"));

        // Slot 31: Change Duration
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

        // Slot 33: Toggle Stat-Wipe (ban types only)
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

            // Slot 34: Toggle Alt-Blocking
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

        // Staff menu header navigation
        registerActionHandler("openOnlinePlayers", ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPunishments", ActionHandlers.openMenu(
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openTickets", ActionHandlers.openMenu(
                new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPanel", click -> {
            click.clickedMenu().close();
            String escapedUrl = panelUrl.replace("\"", "\\\"");
            String panelJson = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"Staff Panel: \",\"color\":\"gold\"}," +
                "{\"text\":\"%s\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to open in browser\"}}]}",
                escapedUrl, panelUrl
            );
            platform.sendJsonMessage(viewerUuid, panelJson);
        });

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        // Add note handler
        registerActionHandler("addNote", this::handleAddNote);

        // Evidence handler
        registerActionHandler("evidence", this::handleEvidence);

        // Pardon handler
        registerActionHandler("pardon", this::handlePardon);

        // Change duration handler
        registerActionHandler("changeDuration", this::handleChangeDuration);

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
                null,
                null
        );

        httpClient.pardonPunishment(request).thenAccept(response -> {
            if (response.hasPardoned()) {
                sendMessage(MenuItems.COLOR_GREEN + "Punishment pardoned successfully!");
                invalidateCache();
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

                    ChangePunishmentDurationRequest request = new ChangePunishmentDurationRequest(
                            punishment.getId(),
                            viewerName,
                            durationMs
                    );

                    httpClient.changePunishmentDuration(request).thenAccept(v -> {
                        sendMessage(MenuItems.COLOR_GREEN + "Duration changed successfully!");
                        invalidateCache();
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

    private void handleToggleStatWipe(Click click) {
        boolean currentStatus = punishment.getDataMap() != null &&
                Boolean.TRUE.equals(punishment.getDataMap().get("wipeAfterExpiry"));

        TogglePunishmentOptionRequest request = new TogglePunishmentOptionRequest(
                punishment.getId(),
                viewerName,
                "STAT_WIPE",
                !currentStatus
        );

        httpClient.togglePunishmentOption(request).thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Stat-wipe " + (!currentStatus ? "enabled" : "disabled") + " successfully!");
            refreshMenu(click);
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to toggle stat-wipe: " + e.getMessage());
            return null;
        });
    }

    private void handleToggleAltBlock(Click click) {
        boolean currentStatus = punishment.getDataMap() != null &&
                Boolean.TRUE.equals(punishment.getDataMap().get("altBlocking"));

        TogglePunishmentOptionRequest request = new TogglePunishmentOptionRequest(
                punishment.getId(),
                viewerName,
                "ALT_BLOCKING",
                !currentStatus
        );

        httpClient.togglePunishmentOption(request).thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Alt-blocking " + (!currentStatus ? "enabled" : "disabled") + " successfully!");
            refreshMenu(click);
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to toggle alt-blocking: " + e.getMessage());
            return null;
        });
    }

    private void refreshMenu(Click click) {
        platform.runOnMainThread(() -> {
            // Return to recent punishments menu
            new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)
                    .display(click.player());
        });
    }

    private void invalidateCache() {
        if (platform.getCache() != null) {
            platform.getCache().removeBan(targetUuid);
            platform.getCache().removeMute(targetUuid);
        }
    }
}
