package gg.modl.minecraft.core.util;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

public final class Pagination {

    /**
     * Parses a page number from a string argument.
     * Returns 1 on invalid input or values below 1.
     */
    public static int parsePage(String pageArg) {
        try {
            int page = Integer.parseInt(pageArg);
            return Math.max(1, page);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Computes pagination bounds for a list of entries.
     *
     * @param totalEntries total number of entries
     * @param perPage      entries per page
     * @param page         1-based page number (clamped to valid range)
     * @return a {@link Page} with resolved bounds
     */
    public static Page paginate(int totalEntries, int perPage, int page) {
        int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / perPage));
        int clampedPage = Math.max(1, Math.min(page, totalPages));
        int start = (clampedPage - 1) * perPage;
        int end = Math.min(start + perPage, totalEntries);
        return new Page(clampedPage, totalPages, start, end);
    }

    /**
     * Convenience overload that takes a list directly.
     */
    public static <T> Page paginate(List<T> entries, int perPage, int page) {
        return paginate(entries == null ? 0 : entries.size(), perPage, page);
    }

    /**
     * Parses a flags string like "-p", "-p 2", "print", "print 3" used by
     * print-mode commands. Returns the page number if print mode is detected,
     * or 0 if the flags do not indicate print mode.
     */
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
