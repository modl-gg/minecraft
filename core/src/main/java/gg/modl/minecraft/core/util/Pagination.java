package gg.modl.minecraft.core.util;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

public final class Pagination {
    private Pagination() {}

    public static int parsePage(String pageArg) {
        try {
            int page = Integer.parseInt(pageArg);
            return Math.max(1, page);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static Page paginate(int totalEntries, int perPage, int page) {
        int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / perPage));
        int clampedPage = Math.max(1, Math.min(page, totalPages));
        int start = (clampedPage - 1) * perPage;
        int end = Math.min(start + perPage, totalEntries);
        return new Page(clampedPage, totalPages, start, end);
    }

    public static <T> Page paginate(List<T> entries, int perPage, int page) {
        return paginate(entries == null ? 0 : entries.size(), perPage, page);
    }

    public static int parsePrintFlags(String flags) {
        if (flags == null || flags.isEmpty()) return 0;
        String[] parts = flags.trim().split("\\s+");
        boolean printMode = false;
        int page = 1;
        for (String part : parts) {
            if (part.equalsIgnoreCase("-p") || part.equalsIgnoreCase("print")) printMode = true;
            else {
                try {
                    page = Math.max(1, Integer.parseInt(part));
                } catch (NumberFormatException ignored) {}
            }
        }
        return printMode ? page : 0;
    }

    @Data @AllArgsConstructor
    public static class Page {
        private final int page;
        private final int totalPages;
        private final int start;
        private final int end;

        public boolean hasNextPage() {
            return page < totalPages;
        }

        public boolean isOutOfRange() {
            return page > totalPages;
        }
    }
}
