package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandler;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.util.PunishmentActionMessages;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Punish Severity Menu - select severity level for a punishment.
 * Secondary menu accessed from PunishMenu.
 * Adapts layout based on punishment type:
 * - Single severity types show one central button
 * - Multi-severity types show lenient/regular/aggravated options
 */
public class PunishSeverityMenu extends BaseInspectMenu {

    private final PunishmentTypesResponse.PunishmentTypeData punishmentType;
    private final Consumer<CirrusPlayerWrapper> menuBackAction; // Goes back to PunishMenu
    private final Consumer<CirrusPlayerWrapper> rootBackAction; // Passed to primary tabs (e.g., back to Staff Menu)
    private boolean silentMode = false;
    private boolean altBlocking = false;
    private boolean statWipe = false;
    private List<String> linkedReportIds;
    private PunishmentPreviewResponse previewData;

    /**
     * Create a new severity menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param punishmentType The punishment type to issue
     * @param rootBackAction Root back action for primary tab navigation (e.g., back to Staff Menu)
     * @param menuBackAction Action to return to parent menu (PunishMenu)
     */
    public PunishSeverityMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                               Account targetAccount, PunishmentTypesResponse.PunishmentTypeData punishmentType,
                               Consumer<CirrusPlayerWrapper> rootBackAction, Consumer<CirrusPlayerWrapper> menuBackAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType, rootBackAction, menuBackAction, false, false, false, new ArrayList<>());
    }

    /**
     * Create a new severity menu with initial toggle states.
     */
    public PunishSeverityMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                               Account targetAccount, PunishmentTypesResponse.PunishmentTypeData punishmentType,
                               Consumer<CirrusPlayerWrapper> rootBackAction, Consumer<CirrusPlayerWrapper> menuBackAction,
                               boolean silentMode, boolean altBlocking, boolean statWipe, List<String> linkedReportIds) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, menuBackAction);
        this.punishmentType = punishmentType;
        this.menuBackAction = menuBackAction;
        this.rootBackAction = rootBackAction;
        this.silentMode = silentMode;
        this.altBlocking = altBlocking;
        this.statWipe = statWipe;
        this.linkedReportIds = linkedReportIds != null ? linkedReportIds : new ArrayList<>();

        title("Punish: " + punishmentType.getName());
        activeTab = InspectTab.PUNISH;

        // Load preview data
        loadPreviewData();
        buildMenu();
    }

    private void loadPreviewData() {
        try {
            httpClient.getPunishmentPreview(targetUuid, punishmentType.getOrdinal()).thenAccept(response -> {
                if (response.isSuccess()) {
                    this.previewData = response;
                }
            }).join();
        } catch (Exception e) {
            // Preview failed - continue without it
        }
    }

    private void buildMenu() {
        buildHeader();

        // Check if this is a single-severity type
        Boolean singleSeverity = punishmentType.getSingleSeverityPunishment();
        Boolean permUsername = punishmentType.getPermanentUntilUsernameChange();
        Boolean permSkin = punishmentType.getPermanentUntilSkinChange();

        boolean isSingleType = (singleSeverity != null && singleSeverity) ||
                               (permUsername != null && permUsername) ||
                               (permSkin != null && permSkin);

        if (isSingleType) {
            buildSingleSeverityLayout();
        } else {
            buildMultiSeverityLayout();
        }

        // Add toggle buttons
        buildToggleButtons();
    }

    private void buildSingleSeverityLayout() {
        // Single center button at slot 31
        PunishmentPreviewResponse.SeverityPreview preview = previewData != null ?
                previewData.getSingleSeverity() : null;

        boolean restriction = (punishmentType.getPermanentUntilUsernameChange() != null && punishmentType.getPermanentUntilUsernameChange()) ||
                              (punishmentType.getPermanentUntilSkinChange() != null && punishmentType.getPermanentUntilSkinChange());

        List<String> lore = new ArrayList<>();

        if (!restriction) {
            // Show what the punishment will be
            if (preview != null) {
                lore.add(MenuItems.COLOR_GRAY + "Punishment: " + MenuItems.COLOR_WHITE + preview.getDurationFormatted() +
                        (preview.isPermanent() ? " " + MenuItems.COLOR_RED + "(Permanent)" : ""));
                lore.add(MenuItems.COLOR_GRAY + "Type: " + MenuItems.COLOR_WHITE + capitalizeFirst(preview.getPunishmentType()));
                lore.add(MenuItems.COLOR_GRAY + "Points: " + MenuItems.COLOR_YELLOW + "+" + preview.getPoints());
                lore.add("");
            }

            // Show current/new offender level
            if (previewData != null && preview != null) {
                String category = punishmentType.getCategory();
                if ("Social".equalsIgnoreCase(category)) {
                    lore.add(MenuItems.COLOR_GRAY + "Offender Level: " + getOffenseLevelColor(previewData.getSocialStatus()) +
                            previewData.getSocialStatus() + MenuItems.COLOR_GRAY + " → " +
                            getOffenseLevelColor(preview.getNewSocialStatus()) + preview.getNewSocialStatus());
                } else {
                    lore.add(MenuItems.COLOR_GRAY + "Offender Level: " + getOffenseLevelColor(previewData.getGameplayStatus()) +
                            previewData.getGameplayStatus() + MenuItems.COLOR_GRAY + " → " +
                            getOffenseLevelColor(preview.getNewGameplayStatus()) + preview.getNewGameplayStatus());
                }
                lore.add("");
            }
        }


        // Special messages for permanent-until-change types
        if (punishmentType.getPermanentUntilUsernameChange() != null && punishmentType.getPermanentUntilUsernameChange()) {
            lore.add(MenuItems.COLOR_YELLOW + "Player will be restricted until");
            lore.add(MenuItems.COLOR_YELLOW + "they change their username");
            lore.add("");
        } else if (punishmentType.getPermanentUntilSkinChange() != null && punishmentType.getPermanentUntilSkinChange()) {
            lore.add(MenuItems.COLOR_YELLOW + "Player will be restricted until");
            lore.add(MenuItems.COLOR_YELLOW + "they change their skin");
            lore.add("");
        }

        lore.add(MenuItems.COLOR_GREEN + "Click to issue " + (silentMode ? "silent " : "") + "punishment");

        set(CirrusItem.of(
                CirrusItemType.YELLOW_WOOL,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Issue Punishment"),
                MenuItems.lore(lore)
        ).slot(31).actionHandler("issueSingle"));
    }

    private void buildMultiSeverityLayout() {
        // Slot 28: Lenient (lime wool)
        set(createSeverityButton("Lenient", 0, CirrusItemType.LIME_WOOL, MenuItems.COLOR_GREEN,
                previewData != null ? previewData.getLenient() : null, "issueLenient", MenuSlots.SEVERITY_LENIENT));

        // Slot 30: Regular (yellow wool)
        set(createSeverityButton("Regular", 1, CirrusItemType.YELLOW_WOOL, MenuItems.COLOR_YELLOW,
                previewData != null ? previewData.getRegular() : null, "issueRegular", MenuSlots.SEVERITY_REGULAR));

        // Slot 32: Aggravated (red wool)
        set(createSeverityButton("Aggravated", 2, CirrusItemType.RED_WOOL, MenuItems.COLOR_RED,
                previewData != null ? previewData.getAggravated() : null, "issueAggravated", MenuSlots.SEVERITY_AGGRAVATED));
    }

    private CirrusItem createSeverityButton(String name, int severityLevel, CirrusItemType itemType, String color,
                                             PunishmentPreviewResponse.SeverityPreview preview, String action, int slot) {
        List<String> lore = new ArrayList<>();

        lore.add(MenuItems.COLOR_GRAY + "Issue a " + name.toLowerCase() + " punishment");
        lore.add("");

        // Show preview data
        if (preview != null) {
            lore.add(MenuItems.COLOR_GRAY + "Punishment: " + MenuItems.COLOR_WHITE + preview.getDurationFormatted() +
                    (preview.isPermanent() ? " " + MenuItems.COLOR_RED + "(Permanent)" : ""));
            lore.add(MenuItems.COLOR_GRAY + "Type: " + MenuItems.COLOR_WHITE + capitalizeFirst(preview.getPunishmentType()));
            lore.add(MenuItems.COLOR_GRAY + "Points: " + MenuItems.COLOR_YELLOW + "+" + preview.getPoints());
            lore.add("");

            // Show offender level change
            String category = punishmentType.getCategory();
            if ("Social".equalsIgnoreCase(category)) {
                lore.add(MenuItems.COLOR_GRAY + "Offender Level: " + getOffenseLevelColor(previewData.getSocialStatus()) +
                        previewData.getSocialStatus() + MenuItems.COLOR_GRAY + " → " +
                        getOffenseLevelColor(preview.getNewSocialStatus()) + preview.getNewSocialStatus());
            } else {
                lore.add(MenuItems.COLOR_GRAY + "Offender Level: " + getOffenseLevelColor(previewData.getGameplayStatus()) +
                        previewData.getGameplayStatus() + MenuItems.COLOR_GRAY + " → " +
                        getOffenseLevelColor(preview.getNewGameplayStatus()) + preview.getNewGameplayStatus());
            }
            lore.add("");
        }

        lore.add(MenuItems.COLOR_YELLOW + "Click to issue " + (silentMode ? "silent " : "") + "punishment");

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(color + name),
                MenuItems.lore(lore)
        ).slot(slot).actionHandler(action);
    }

    private void buildToggleButtons() {
        // Slot 33: Link Reports button
        String linkTitle = MenuItems.COLOR_GOLD + "Link Reports";
        if (!linkedReportIds.isEmpty()) {
            linkTitle += MenuItems.COLOR_GREEN + " (" + linkedReportIds.size() + ")";
        }
        List<String> linkLore = new ArrayList<>();
        linkLore.add(MenuItems.COLOR_GRAY + "Select reports to link with this punishment");
        linkLore.add("");
        if (linkedReportIds.isEmpty()) {
            linkLore.add(MenuItems.COLOR_YELLOW + "Click to select reports");
        } else {
            linkLore.add(MenuItems.COLOR_GREEN + "Linked reports:");
            for (String reportId : linkedReportIds) {
                linkLore.add(MenuItems.COLOR_GRAY + "  - " + MenuItems.COLOR_WHITE + reportId);
            }
            linkLore.add("");
            linkLore.add(MenuItems.COLOR_YELLOW + "Click to modify selection");
        }
        set(CirrusItem.of(
                CirrusItemType.BOOK,
                CirrusChatElement.ofLegacyText(linkTitle),
                MenuItems.lore(linkLore)
        ).slot(MenuSlots.SEVERITY_LINK_REPORTS).actionHandler("linkReports"));

        // Slot 34: Silent Mode toggle
        set(CirrusItem.of(
                silentMode ? CirrusItemType.LIME_DYE : CirrusItemType.GRAY_DYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Silent Mode: " + (silentMode ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED +
                                                                                                                                     "Disabled")),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Toggle silent mode for this punishment",
                        "",
                        silentMode ? MenuItems.COLOR_GREEN + "Punishment will be silent" : MenuItems.COLOR_GRAY + "Punishment will be public"
                )
        ).slot(MenuSlots.SEVERITY_SILENT).actionHandler("toggleSilent"));

        // Slot 42: Alt-Blocking toggle (if punishment type allows it)
        if (punishmentType.getCanBeAltBlocking() != null && punishmentType.getCanBeAltBlocking()) {
            set(CirrusItem.of(
                    altBlocking ? CirrusItemType.TORCH : CirrusItemType.REDSTONE_TORCH,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Alt-Blocking: " + (altBlocking ? MenuItems.COLOR_GREEN + "Enabled" :
                                                                                         MenuItems.COLOR_RED + "Disabled")),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Block alt accounts from joining",
                            "",
                            altBlocking ? MenuItems.COLOR_GREEN + "Alt accounts will be blocked" : MenuItems.COLOR_GRAY + "Alt accounts can still join"
                    )
            ).slot(MenuSlots.SEVERITY_ALT_BLOCK).actionHandler("toggleAltBlock"));
        }

        // Slot 43: Stat-Wipe toggle (if punishment type allows it)
        if (punishmentType.getCanBeStatWiping() != null && punishmentType.getCanBeStatWiping()) {
            set(CirrusItem.of(
                    statWipe ? CirrusItemType.EXPERIENCE_BOTTLE : CirrusItemType.GLASS_BOTTLE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Stat-Wipe: " + (statWipe ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED +
                                                                                                                                     "Disabled")),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Wipe player stats after punishment",
                            "",
                            statWipe ? MenuItems.COLOR_GREEN + "Stats will be wiped" : MenuItems.COLOR_GRAY + "Stats will be preserved"
                    )
            ).slot(MenuSlots.SEVERITY_STAT_WIPE).actionHandler("toggleStatWipe"));
        }
    }

    private String getOffenseLevelColor(String offenseLevel) {
        if (offenseLevel == null) return MenuItems.COLOR_GRAY;
        return switch (offenseLevel.toLowerCase()) {
            case "low" -> MenuItems.COLOR_GREEN;
            case "medium" -> MenuItems.COLOR_YELLOW;
            case "habitual" -> MenuItems.COLOR_RED;
            default -> MenuItems.COLOR_GRAY;
        };
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Severity handlers
        registerActionHandler("issueSingle", (ActionHandler) click -> {
            issuePunishment(click, 1); // Regular severity for single
            return CallResult.DENY_GRABBING;
        });
        registerActionHandler("issueLenient", (ActionHandler) click -> {
            issuePunishment(click, 0);
            return CallResult.DENY_GRABBING;
        });
        registerActionHandler("issueRegular", (ActionHandler) click -> {
            issuePunishment(click, 1);
            return CallResult.DENY_GRABBING;
        });
        registerActionHandler("issueAggravated", (ActionHandler) click -> {
            issuePunishment(click, 2);
            return CallResult.DENY_GRABBING;
        });

        // Toggle handlers
        registerActionHandler("toggleSilent", this::handleToggleSilent);
        registerActionHandler("toggleAltBlock", this::handleToggleAltBlock);
        registerActionHandler("toggleStatWipe", this::handleToggleStatWipe);

        // Link Reports handler
        registerActionHandler("linkReports", (ActionHandler) click -> {
            handleLinkReports(click);
            return CallResult.DENY_GRABBING;
        });

        // Override header navigation - pass rootBackAction to preserve the back button on primary tabs
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));
    }

    private void issuePunishment(Click click, int severityLevel) {
        click.clickedMenu().close();

        // Prompt for reason
        ChatInputManager.requestInput(platform, viewerUuid, "Enter punishment reason for " + targetName + ":",
                reason -> {
                    // Create punishment request
                    String severityStr = severityLevel == 0 ? "lenient" : severityLevel == 1 ? "regular" : "aggravated";

                    // Build data map with optional flags
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    if (altBlocking) {
                        data.put("altBlocking", true);
                    }
                    if (statWipe) {
                        data.put("statWipe", true);
                    }
                    if (silentMode) {
                        data.put("silent", true);
                    }
                    data.put("issuedServer", platform.getPlayerServer(viewerUuid));

                    PunishmentCreateRequest request = new PunishmentCreateRequest(
                            targetUuid.toString(),
                            viewerName,
                            punishmentType.getOrdinal(),
                            reason,
                            null, // duration - let API determine from severity
                            data.isEmpty() ? null : data,
                            null, // notes
                            linkedReportIds.isEmpty() ? null : linkedReportIds,
                            severityStr,
                            silentMode ? "silent" : "active"
                    );

                    httpClient.createPunishmentWithResponse(request).thenAccept(response -> {
                        if (response.isSuccess()) {
                            LocaleManager localeManager = platform.getLocaleManager();

                            // Success message to issuer (same as command)
                            String successMessage = localeManager.punishment()
                                .type(punishmentType.getName())
                                .target(targetName)
                                .punishmentId(response.getPunishmentId())
                                .get("general.punishment_issued");
                            sendMessage(successMessage);

                            // Send action buttons
                            if (response.getPunishmentId() != null) {
                                platform.runOnMainThread(() -> {
                                    PunishmentActionMessages.sendPunishmentActions(platform, viewerUuid, response.getPunishmentId());
                                });
                            }

                            // Resolve linked reports
                            if (!linkedReportIds.isEmpty()) {
                                for (String reportId : linkedReportIds) {
                                    httpClient.resolveReport(reportId, viewerName,
                                            "Report accepted - punishment issued", response.getPunishmentId())
                                            .exceptionally(ex -> {
                                                // Log but don't fail the punishment for resolve errors
                                                return null;
                                            });
                                }
                                sendMessage(MenuItems.COLOR_GREEN + "Resolved " + linkedReportIds.size() + " linked report(s).");
                            }
                        } else {
                            sendMessage(MenuItems.COLOR_RED + "Failed to issue punishment: " + response.getMessage());
                        }
                    }).exceptionally(e -> {
                        sendMessage(MenuItems.COLOR_RED + "Failed to issue punishment: " + e.getMessage());
                        return null;
                    });
                },
                () -> {
                    sendMessage(MenuItems.COLOR_GRAY + "Punishment cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }

    private void handleToggleSilent(Click click) {
        // Refresh menu with toggled silent state
        ActionHandlers.openMenu(
                new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType, rootBackAction, menuBackAction, !silentMode, altBlocking, statWipe, linkedReportIds))
                .handle(click);
    }

    private void handleToggleAltBlock(Click click) {
        // Refresh menu with toggled alt-blocking state
        ActionHandlers.openMenu(
                new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType, rootBackAction, menuBackAction, silentMode, !altBlocking, statWipe, linkedReportIds))
                .handle(click);
    }

    private void handleToggleStatWipe(Click click) {
        // Refresh menu with toggled stat-wipe state
        ActionHandlers.openMenu(
                new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType, rootBackAction, menuBackAction, silentMode, altBlocking, !statWipe, linkedReportIds))
                .handle(click);
    }

    private void handleLinkReports(Click click) {
        // Open LinkReportsMenu, passing current linked IDs as pre-selected
        Set<String> preSelected = new HashSet<>(linkedReportIds);
        Consumer<Set<String>> onComplete = selectedIds -> {
            // Return to PunishSeverityMenu with updated linked report IDs
            List<String> newLinkedIds = new ArrayList<>(selectedIds);
            platform.runOnMainThread(() -> {
                new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType,
                        rootBackAction, menuBackAction, silentMode, altBlocking, statWipe, newLinkedIds)
                        .display(click.player());
            });
        };

        Consumer<CirrusPlayerWrapper> backToSeverity = player -> {
            new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType,
                    rootBackAction, menuBackAction, silentMode, altBlocking, statWipe, linkedReportIds)
                    .display(player);
        };

        ActionHandlers.openMenu(
                new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                        targetAccount, preSelected, backToSeverity, rootBackAction, onComplete))
                .handle(click);
    }
}
