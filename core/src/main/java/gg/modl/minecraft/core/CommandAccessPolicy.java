package gg.modl.minecraft.core;

import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.AdminOnly;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.command.StaffNo2fa;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.staff.PermissionUtil;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.list.AnnotationList;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.exception.SendableException;

@RequiredArgsConstructor
public final class CommandAccessPolicy {
    private final Cache cache;
    private final LocaleManager localeManager;
    private final Staff2faService staff2faService;

    public void enforce(AnnotationList annotations, CommandActor actor) {
        if (annotations.contains(PlayerOnly.class) && CommandUtil.isConsole(actor)) {
            throw deny(localeManager.getMessage("general.players_only"));
        }

        if (CommandUtil.isConsole(actor)) {
            return;
        }

        boolean staffOnly = annotations.contains(StaffOnly.class);
        boolean adminOnly = annotations.contains(AdminOnly.class);
        RequiresPermission requiresPermission = annotations.get(RequiresPermission.class);
        boolean staffNo2fa = annotations.contains(StaffNo2fa.class);

        if (staffOnly && !PermissionUtil.isStaff(actor, cache)) {
            throw deny(localeManager.getMessage("general.no_permission"));
        }

        if (adminOnly && !cache.hasPermission(actor.uniqueId(), Permissions.ADMIN)) {
            throw deny(localeManager.getMessage("general.no_permission"));
        }

        if (requiresPermission != null && !PermissionUtil.hasPermission(actor, cache, requiresPermission.value())) {
            throw deny(localeManager.getMessage("general.no_permission"));
        }

        if (staffNo2fa && !PermissionUtil.isStaff(actor, cache)) {
            throw deny(localeManager.getMessage("general.no_permission"));
        }

        boolean staffScoped = staffOnly || adminOnly || requiresPermission != null;
        if (requiresAuthenticated2fa(staffScoped, staffNo2fa)
                && !staff2faService.isAuthenticated(actor.uniqueId())) {
            throw deny(localeManager.getMessage("staff_2fa.not_verified"));
        }
    }

    private boolean requiresAuthenticated2fa(boolean staffScoped, boolean staffNo2faExempt) {
        return staffScoped && !staffNo2faExempt && staff2faService != null && staff2faService.isEnabled();
    }

    static SendableException deny(String message) {
        return new SendableException(message) {
            @Override
            public void sendTo(CommandActor actor) {
                actor.reply(message);
            }
        };
    }
}
