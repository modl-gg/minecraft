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
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.PunishGuiConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.util.PermissionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Punish Menu - shows available punishment types.
 * Primary menu for issuing punishments.
 * Uses punish_gui config for slot configuration.
 */
public class PunishMenu extends BaseInspectMenu {

    private List<PunishmentTypesResponse.PunishmentTypeData> punishmentTypes = new ArrayList<>();
    private Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();
    private PunishGuiConfig guiConfig;
    private PunishmentPreviewResponse previewData;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    // Slot mapping: config slots 1-14 map to GUI slots 28-34, 37-43
    private static final int[] GUI_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    /**
     * Create a new punish menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public PunishMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                      Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.parentBackAction = backAction;

        title("Punish: " + targetName);
        activeTab = InspectTab.PUNISH;

        // Load config (cached after first load)
        Cache configCache = platform.getCache();
        PunishGuiConfig cached = configCache != null ? configCache.getCachedPunishGuiConfig() : null;
        if (cached != null) {
            this.guiConfig = cached;
        } else {
            this.guiConfig = PunishGuiConfig.load(platform.getDataFolder().toPath(),
                    java.util.logging.Logger.getLogger("MODL-PunishMenu"));
            if (configCache != null) {
                configCache.cachePunishGuiConfig(this.guiConfig);
            }
        }

        // Fetch punishment types and build menu
        loadPunishmentTypes();
        loadPreviewData();
        buildHeader();
        buildPunishmentGrid();
    }

    private void loadPunishmentTypes() {
        Cache cache = platform.getCache();

        // Try cache first
        PunishmentTypesResponse cached = cache != null ? cache.getCachedPunishmentTypes() : null;
        if (cached != null && cached.isSuccess() && cached.getData() != null) {
            punishmentTypes = new ArrayList<>(cached.getData());
            for (PunishmentTypesResponse.PunishmentTypeData type : punishmentTypes) {
                typesByOrdinal.put(type.getOrdinal(), type);
            }
            return;
        }

        // Fetch from API and cache the result
        try {
            httpClient.getPunishmentTypes().thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    punishmentTypes = new ArrayList<>(response.getData());
                    for (PunishmentTypesResponse.PunishmentTypeData type : punishmentTypes) {
                        typesByOrdinal.put(type.getOrdinal(), type);
                    }
                    if (cache != null) {
                        cache.cachePunishmentTypes(response);
                    }
                }
            }).join();
        } catch (Exception e) {
            // API call failed - use empty list
        }
    }

    private void loadPreviewData() {
        // Fetch preview data for the player to get their current status
        try {
            // Use first enabled punishment type ordinal if available, otherwise use 6
            int firstOrdinal = guiConfig.getEnabledSlots().stream()
                    .findFirst()
                    .map(PunishGuiConfig.PunishSlotConfig::getOrdinal)
                    .orElse(6);

            httpClient.getPunishmentPreview(targetUuid, firstOrdinal).thenAccept(response -> {
                if (response.isSuccess()) {
                    this.previewData = response;
                }
            }).join();
        } catch (Exception e) {
            // Preview failed - continue without it
        }
    }

    private void buildPunishmentGrid() {
        Cache cache = platform.getCache();

        // Use config to place items
        for (int configSlot = 1; configSlot <= 14; configSlot++) {
            PunishGuiConfig.PunishSlotConfig slotConfig = guiConfig.getSlot(configSlot);

            if (slotConfig == null || !slotConfig.isEnabled()) {
                continue;
            }

            int guiSlot = GUI_SLOTS[configSlot - 1];

            // Find the punishment type by ordinal
            PunishmentTypesResponse.PunishmentTypeData type = typesByOrdinal.get(slotConfig.getOrdinal());

            if (type != null) {
                // Check if user has permission for this punishment type
                String permission = PermissionUtil.formatPunishmentPermission(type.getName());
                boolean hasPermission = cache != null && cache.hasPermission(viewerUuid, permission);

                if (hasPermission) {
                    set(createPunishmentTypeItem(type, slotConfig).slot(guiSlot));
                } else {
                    set(createDisabledPunishmentTypeItem(type, slotConfig).slot(guiSlot));
                }
            } else {
                // Show placeholder for missing type
                set(createPlaceholderItem(slotConfig).slot(guiSlot));
            }
        }

        // If no punishment types loaded and no config, show error placeholder
        if (punishmentTypes.isEmpty() && guiConfig.getEnabledSlots().isEmpty()) {
            set(CirrusItem.of(
                    CirrusItemType.BARRIER,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "No Punishment Types"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Failed to load punishment types",
                            MenuItems.COLOR_GRAY + "from the API"
                    )
            ).slot(31));
        }
    }

    private CirrusItem createPunishmentTypeItem(PunishmentTypesResponse.PunishmentTypeData type,
                                                 PunishGuiConfig.PunishSlotConfig slotConfig) {
        List<String> lore = new ArrayList<>();

        // Process description from config, replacing placeholders
        for (String line : slotConfig.getDescription()) {
            String processed = processPlaceholder(line, type);
            lore.add(processed);
        }

        // Add click instruction
        Boolean singleSeverity = type.getSingleSeverityPunishment();
        Boolean permUsername = type.getPermanentUntilUsernameChange();
        Boolean permSkin = type.getPermanentUntilSkinChange();

        boolean isSingleType = (singleSeverity != null && singleSeverity) ||
                               (permUsername != null && permUsername) ||
                               (permSkin != null && permSkin);

        if (lore.isEmpty() || !lore.get(lore.size() - 1).contains("Click")) {
            lore.add("");
            if (isSingleType) {
                lore.add(MenuItems.COLOR_YELLOW + "Click to issue punishment");
            } else {
                lore.add(MenuItems.COLOR_YELLOW + "Click to select severity");
            }
        }

        // Determine item type from config or fallback
        CirrusItemType itemType = parseItemType(slotConfig.getItem());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + slotConfig.getTitle()),
                MenuItems.lore(lore)
        ).actionHandler("punishType_" + type.getOrdinal());
    }

    private CirrusItem createDisabledPunishmentTypeItem(PunishmentTypesResponse.PunishmentTypeData type,
                                                          PunishGuiConfig.PunishSlotConfig slotConfig) {
        // Determine item type from config or fallback
        CirrusItemType itemType = parseItemType(slotConfig.getItem());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + slotConfig.getTitle()),
                MenuItems.lore(
                        MenuItems.COLOR_RED + "No Permission",
                        MenuItems.COLOR_GRAY + "You don't have permission",
                        MenuItems.COLOR_GRAY + "to issue this punishment type"
                )
        );
        // No action handler - item is not clickable
    }

    private CirrusItem createPlaceholderItem(PunishGuiConfig.PunishSlotConfig slotConfig) {
        CirrusItemType itemType = parseItemType(slotConfig.getItem());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + slotConfig.getTitle()),
                MenuItems.lore(
                        MenuItems.COLOR_RED + "Punishment type not found",
                        MenuItems.COLOR_GRAY + "Ordinal: " + slotConfig.getOrdinal()
                )
        );
    }

    private String processPlaceholder(String line, PunishmentTypesResponse.PunishmentTypeData type) {
        String result = line;

        // Replace color codes
        result = result.replace("&7", MenuItems.COLOR_GRAY);
        result = result.replace("&f", MenuItems.COLOR_WHITE);
        result = result.replace("&c", MenuItems.COLOR_RED);
        result = result.replace("&a", MenuItems.COLOR_GREEN);
        result = result.replace("&e", MenuItems.COLOR_YELLOW);
        result = result.replace("&6", MenuItems.COLOR_GOLD);
        result = result.replace("&b", MenuItems.COLOR_AQUA);

        // Replace placeholders
        if (type.getStaffDescription() != null) {
            result = result.replace("{staff-description}", type.getStaffDescription());
        } else {
            result = result.replace("{staff-description}", "");
        }

        if (previewData != null) {
            result = result.replace("{social-status}", previewData.getSocialStatus() != null ?
                    previewData.getSocialStatus() : "Unknown");
            result = result.replace("{gameplay-status}", previewData.getGameplayStatus() != null ?
                    previewData.getGameplayStatus() : "Unknown");
            result = result.replace("{social-points}", String.valueOf(previewData.getSocialPoints()));
            result = result.replace("{gameplay-points}", String.valueOf(previewData.getGameplayPoints()));
        } else {
            result = result.replace("{social-status}", "Loading...");
            result = result.replace("{gameplay-status}", "Loading...");
            result = result.replace("{social-points}", "?");
            result = result.replace("{gameplay-points}", "?");
        }

        return result;
    }

    private CirrusItemType parseItemType(String itemString) {
        if (itemString == null || itemString.isEmpty()) {
            return CirrusItemType.PAPER;
        }

        return CirrusItemType.of(itemString);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Register handler for each punishment type ordinal
        for (PunishmentTypesResponse.PunishmentTypeData type : punishmentTypes) {
            registerActionHandler("punishType_" + type.getOrdinal(), (ActionHandler) click -> {
                handlePunishmentType(click, type);
                return CallResult.DENY_GRABBING;
            });
        }

        // Override header navigation - pass parentBackAction to preserve the back button
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)));

        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)));

        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)));

        registerActionHandler("openPunish", (ActionHandler) click -> {
            // Already on punish, do nothing
            return CallResult.DENY_GRABBING;
        });
    }

    private void handlePunishmentType(Click click, PunishmentTypesResponse.PunishmentTypeData type) {
        // Open severity selection menu - this is a secondary menu, back button returns to PunishMenu
        // Pass parentBackAction so when navigating back to primary tabs, the original back button is preserved
        ActionHandlers.openMenu(
                new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, type, parentBackAction,
                        player -> new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction).display(player)))
                .handle(click);
    }
}
