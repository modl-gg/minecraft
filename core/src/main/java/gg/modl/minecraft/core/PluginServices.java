package gg.modl.minecraft.core;

import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.service.TicketService;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.punishment.PunishmentActionMessageService;
import gg.modl.minecraft.core.punishment.PunishmentMessageService;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.staff.PermissionUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public final class PluginServices implements StaffAudience {
    private static volatile PluginServices instance;

    private final Cache cache;
    private final LocaleManager localeManager;
    private final Staff2faService staff2faService;
    private final StaffModeService staffModeService;
    private final BridgeService bridgeService;
    private final ChatInputManager chatInputManager;
    private final PunishmentMessageService punishmentMessageService;
    private final PunishmentActionMessageService punishmentActionMessageService;

    private volatile ReplayService replayService;
    private volatile TicketService ticketService;

    public static void install(PluginServices services) {
        instance = services;
    }

    public static PluginServices get() {
        return instance;
    }

    public static Cache cache() {
        return instance == null ? null : instance.cache;
    }

    public static LocaleManager locale() {
        return instance == null ? null : instance.localeManager;
    }

    public static StaffModeService staffMode() {
        return instance == null ? null : instance.staffModeService;
    }

    public static BridgeService bridge() {
        return instance == null ? null : instance.bridgeService;
    }

    public static ChatInputManager chatInput() {
        return instance == null ? null : instance.chatInputManager;
    }

    public static PunishmentMessageService punishmentMessages() {
        return instance == null ? null : instance.punishmentMessageService;
    }

    public static PunishmentActionMessageService punishmentActions() {
        return instance == null ? null : instance.punishmentActionMessageService;
    }

    public static ReplayService replay() {
        return instance == null ? null : instance.replayService;
    }

    public static TicketService ticket() {
        return instance == null ? null : instance.ticketService;
    }

    public void setReplayService(ReplayService replayService) {
        this.replayService = replayService;
    }

    public void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public boolean includes(UUID uuid) {
        return PermissionUtil.isStaff(uuid, cache)
                && (staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(uuid));
    }
}
