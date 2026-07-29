package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.Pagination;
import revxrsal.commands.command.CommandActor;

import java.util.List;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

final class PaginatedChatPrinter {
    private PaginatedChatPrinter() {}

    interface EntryRenderer {
        void render(int index, int ordinal);
    }

    static Pagination.Page printPage(List<?> entries, int perPage, int page, EntryRenderer renderer) {
        Pagination.Page pg = Pagination.paginate(entries, perPage, page);
        for (int i = pg.getStart(); i < pg.getEnd(); i++) {
            renderer.render(i, i + 1);
        }
        return pg;
    }

    static void printPageWithTotal(CommandActor actor, LocaleManager localeManager, List<?> entries,
                                   int perPage, int page, String totalKey, EntryRenderer renderer) {
        Pagination.Page pg = printPage(entries, perPage, page, renderer);
        actor.reply(localeManager.getMessage(totalKey, mapOf(
                "count", String.valueOf(entries.size()),
                "page", String.valueOf(pg.getPage()),
                "total_pages", String.valueOf(pg.getTotalPages())
        )));
    }
}
