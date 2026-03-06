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
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.PunishGuiConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.util.PermissionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class PunishMenu extends BaseInspectMenu {
    private List<PunishmentTypesResponse.PunishmentTypeData> punishmentTypes = new ArrayList<>();
    private final Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();
    private final PunishGuiConfig guiConfig;
    private PunishmentPreviewResponse previewData;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    private static final int[] GUI_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public PunishMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                      Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.parentBackAction = backAction;

        title("Punish: " + targetName);
        activeTab = InspectTab.PUNISH;

        Cache configCache = platform.getCache();
        PunishGuiConfig cached = configCache != null ? configCache.getCachedPunishGuiConfig() : null;
        if (cached != null) {
            this.guiConfig = cached;
        } else {
            this.guiConfig = PunishGuiConfig.load(platform.getDataFolder().toPath(),
                    platform.getLogger());
            if (configCache != null) {
                configCache.cachePunishGuiConfig(this.guiConfig);
            }
        }

        loadPunishmentTypes();
        loadPreviewData();
        buildHeader();
        buildPunishmentGrid();
    }

    private void loadPunishmentTypes() {
        Cache cache = platform.getCache();

        PunishmentTypesResponse cached = cache != null ? cache.getCachedPunishmentTypes() : null;
        if (cached != null && cached.isSuccess() && cached.getData() != null) {
            punishmentTypes = new ArrayList<>(cached.getData());
            for (PunishmentTypesResponse.PunishmentTypeData type : punishmentTypes) {
                typesByOrdinal.put(type.getOrdinal(), type);
            }
            return;
        }

        try {
            httpClient.getPunishmentTypes().thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    punishmentTypes = new ArrayList<>(response.getData());
                    for (PunishmentTypesResponse.PunishmentTypeData type : punishmentTypes) {
                        typesByOrdinal.put(type.getOrdinal(), type);
                    }
                    if (cache != null) cache.cachePunishmentTypes(response);
                }
            }).join();
        } catch (Exception ignored) {}
    }

    private void loadPreviewData() {
        try {
            int firstOrdinal = guiConfig.getEnabledSlots().stream()
                    .findFirst()
                    .map(PunishGuiConfig.PunishSlotConfig::getOrdinal)
                    .orElse(6);

            httpClient.getPunishmentPreview(targetUuid, firstOrdinal).thenAccept(response -> {
                if (response.isSuccess()) {
                    this.previewData = response;
                }
            }).join();
        } catch (Exception ignored) {}
    }

    private void buildPunishmentGrid() {
        Cache cache = platform.getCache();

        for (int configSlot = 1; configSlot <= 14; configSlot++) {
            PunishGuiConfig.PunishSlotConfig slotConfig = guiConfig.getSlot(configSlot);

            if (slotConfig == null || !slotConfig.isEnabled()) continue;

            int guiSlot = GUI_SLOTS[configSlot - 1];

            PunishmentTypesResponse.PunishmentTypeData type = typesByOrdinal.get(slotConfig.getOrdinal());

            if (type != null) {
                String permission = PermissionUtil.formatPunishmentPermission(type.getName());
                boolean hasPermission = cache != null && cache.hasPermission(viewerUuid, permission);

                if (hasPermission)
                    set(createPunishmentTypeItem(type, slotConfig).slot(guiSlot));
                else
                    set(createDisabledPunishmentTypeItem(slotConfig).slot(guiSlot));
            } else {
                set(createPlaceholderItem(slotConfig).slot(guiSlot));
            }
        }

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

        for (String line : slotConfig.getDescription()) {
            String processed = processPlaceholder(line, type);
            lore.add(processed);
        }

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

        CirrusItemType itemType = parseItemType(slotConfig.getItem());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + slotConfig.getTitle()),
                MenuItems.lore(lore)
        ).actionHandler("punishType_" + type.getOrdinal());
    }

    private CirrusItem createDisabledPunishmentTypeItem(PunishGuiConfig.PunishSlotConfig slotConfig) {
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

        result = result.replace("&7", MenuItems.COLOR_GRAY);
        result = result.replace("&f", MenuItems.COLOR_WHITE);
        result = result.replace("&c", MenuItems.COLOR_RED);
        result = result.replace("&a", MenuItems.COLOR_GREEN);
        result = result.replace("&e", MenuItems.COLOR_YELLOW);
        result = result.replace("&6", MenuItems.COLOR_GOLD);
        result = result.replace("&b", MenuItems.COLOR_AQUA);

        if (type.getStaffDescription() != null)
            result = result.replace("{staff-description}", type.getStaffDescription());
        else
            result = result.replace("{staff-description}", "");

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
        if (itemString == null || itemString.isEmpty()) return CirrusItemType.PAPER;

        return CirrusItemType.of(itemString);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        for (PunishmentTypesResponse.PunishmentTypeData type : punishmentTypes) {
            registerActionHandler("punishType_" + type.getOrdinal(), (ActionHandler) click -> {
                handlePunishmentType(click, type);
                return CallResult.DENY_GRABBING;
            });
        }

        InspectNavigationHandlers.registerAll(
                (name, handler) -> registerActionHandler(name, handler),
                platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction);
        registerActionHandler("openPunish", click -> {});
    }

    private void handlePunishmentType(Click click, PunishmentTypesResponse.PunishmentTypeData type) {
        ActionHandlers.openMenu(
                new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, type, parentBackAction,
                        player -> new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction).display(player)))
                .handle(click);
    }
}
