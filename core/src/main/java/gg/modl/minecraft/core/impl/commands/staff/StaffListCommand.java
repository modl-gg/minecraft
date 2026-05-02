package gg.modl.minecraft.core.impl.commands.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.staff.StaffMembersMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.Pagination;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.command.CommandActor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@RequiredArgsConstructor
public class StaffListCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final VanishService vanishService;
    private final HttpClientHolder httpClientHolder;
    private final String panelUrl;

    @Command("stafflist")
    @Description("List all online staff members")
    @StaffOnly
    public void staffList(CommandActor actor, @Optional String flag) {
        int page = flag != null ? Pagination.parsePrintFlags(flag) : 0;
        boolean printMode = page > 0;

        if (printMode || actor.uniqueId() == null) {
            printStaffList(actor, Math.max(1, page));
            return;
        }

        UUID viewerUuid = actor.uniqueId();
        AbstractPlayer viewer = platform.getAbstractPlayer(viewerUuid, false);
        String viewerName = viewer != null ? viewer.getName() : viewerUuid.toString();
        boolean isAdmin = PermissionUtil.hasAnyPermission(actor, cache,
                Permissions.SETTINGS_VIEW, Permissions.SETTINGS_MODIFY);

        StaffMembersMenu menu = new StaffMembersMenu(
                platform, httpClientHolder.getClient(), viewerUuid, viewerName, isAdmin, panelUrl, null);
        menu.getDataFuture().thenRun(() -> {
            CirrusPlayerWrapper player = platform.getPlayerWrapper(viewerUuid);
            if (player != null) menu.display(player);
        });
    }

    private static final int ENTRIES_PER_PAGE = 8;

    private void printStaffList(CommandActor actor, int page) {
        List<StaffEntry> staffEntries = collectOnlineStaff();

        actor.reply(localeManager.getMessage("print.staff_list.header", mapOf(
                "count", String.valueOf(staffEntries.size())
        )));

        if (staffEntries.isEmpty()) {
            actor.reply(localeManager.getMessage("print.staff_list.empty"));
        } else {
            Pagination.Page pg = Pagination.paginate(staffEntries, ENTRIES_PER_PAGE, page);
            for (int i = pg.getStart(); i < pg.getEnd(); i++) {
                StaffEntry entry = staffEntries.get(i);
                String vanishTag = entry.isVanished() ? localeManager.getMessage("print.staff_list.vanish") : "";
                actor.reply(localeManager.getMessage("print.staff_list.entry", mapOf(
                        "role", entry.getRole(),
                        "player", entry.getDisplayName(),
                        "in-game-name", entry.getInGameName(),
                        "server", entry.getServer(),
                        "v", vanishTag
                )));
            }
            actor.reply(localeManager.getMessage("print.staff_list.total", mapOf(
                    "count", String.valueOf(staffEntries.size()),
                    "page", String.valueOf(pg.getPage()),
                    "total_pages", String.valueOf(pg.getTotalPages())
            )));
        }
    }

    private List<StaffEntry> collectOnlineStaff() {
        List<StaffEntry> entries = new ArrayList<>();
        for (UUID uuid : cache.getRegistry().getOnlinePlayers()) {
            if (!PermissionUtil.isStaff(uuid, cache)) continue;

            AbstractPlayer player = platform.getPlayer(uuid);
            String inGameName = player != null ? player.getName() : uuid.toString();
            String displayName = cache.getStaffDisplayName(uuid);
            String role = cache.getStaffRole(uuid);
            String server = platform.getPlayerServer(uuid);

            entries.add(new StaffEntry(
                    displayName != null ? displayName : inGameName,
                    inGameName,
                    role != null ? role : Constants.DEFAULT_STAFF_NAME,
                    server != null ? server : Constants.UNKNOWN,
                    vanishService.isVanished(uuid)
            ));
        }
        return entries;
    }

    @Data @AllArgsConstructor
    private static class StaffEntry {
        private final String displayName;
        private final String inGameName;
        private final String role;
        private final String server;
        private final boolean vanished;
    }
}
