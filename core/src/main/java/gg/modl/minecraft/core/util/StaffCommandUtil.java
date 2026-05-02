package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class StaffCommandUtil {
    private StaffCommandUtil() {}

    public static StaffDisplay resolveActorDisplay(CommandActor actor, Platform platform, Cache cache,
                                                   String consoleFallback, String staffFallback,
                                                   boolean useUsername) {
        UUID uuid = actor.uniqueId();
        if (uuid == null) return new StaffDisplay(consoleFallback, consoleFallback);
        return resolvePlayerDisplay(uuid, platform, cache, staffFallback, useUsername);
    }

    public static StaffDisplay resolvePlayerDisplay(UUID uuid, Platform platform, Cache cache,
                                                    String staffFallback) {
        return resolvePlayerDisplay(uuid, platform, cache, staffFallback, false);
    }

    public static StaffDisplay resolvePlayerDisplay(UUID uuid, Platform platform, Cache cache,
                                                    String staffFallback, boolean useUsername) {
        AbstractPlayer player = platform.getPlayer(uuid);
        String inGameName = player != null ? playerName(player, useUsername) : staffFallback;
        String panelName = cache != null ? cache.getStaffDisplayName(uuid) : null;
        return new StaffDisplay(inGameName, panelName != null ? panelName : inGameName);
    }

    public static void enableStaffModeForActor(CommandActor actor, UUID staffUuid, StaffModeService staffModeService,
                                               Platform platform, BridgeService bridgeService,
                                               LocaleManager localeManager, StaffDisplay display) {
        staffModeService.enable(staffUuid);
        actor.reply(localeManager.getMessage("staff_mode.enabled"));
        broadcastStaffModeEnabled(platform, bridgeService, localeManager, staffUuid, display);
    }

    public static void enableStaffModeForPlayer(Platform platform, UUID staffUuid, StaffModeService staffModeService,
                                                BridgeService bridgeService, LocaleManager localeManager,
                                                StaffDisplay display) {
        staffModeService.enable(staffUuid);
        platform.sendMessage(staffUuid, localeManager.getMessage("staff_mode.enabled"));
        broadcastStaffModeEnabled(platform, bridgeService, localeManager, staffUuid, display);
    }

    private static void broadcastStaffModeEnabled(Platform platform, BridgeService bridgeService,
                                                  LocaleManager localeManager, UUID staffUuid,
                                                  StaffDisplay display) {
        platform.staffBroadcast(localeManager.getMessage("staff_mode.enabled_broadcast", mapOf(
                "staff", display.getPanelName(),
                "in-game-name", display.getInGameName()
        )));
        if (bridgeService != null) {
            bridgeService.sendStaffModeEnter(staffUuid.toString(), display.getInGameName(), display.getPanelName());
        }
    }

    private static String playerName(AbstractPlayer player, boolean useUsername) {
        return useUsername ? player.getUsername() : player.getName();
    }

    public static final class StaffDisplay {
        private final String inGameName;
        private final String panelName;

        private StaffDisplay(String inGameName, String panelName) {
            this.inGameName = inGameName;
            this.panelName = panelName;
        }

        public String getInGameName() {
            return inGameName;
        }

        public String getPanelName() {
            return panelName;
        }
    }
}
