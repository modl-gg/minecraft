package gg.modl.minecraft.core.impl.menus.util;

/**
 * Constants for menu slot positions in a 54-slot (6-row) inventory.
 */
public final class MenuSlots {
    private MenuSlots() {}

    // Header row (row 0: slots 0-8)
    public static final int HEADER_START = 0;
    public static final int HEADER_END = 8;
    public static final int HEADER_CENTER = 4;

    // Content area for primary menus (rows 1-2: slots 9-26)
    public static final int CONTENT_ROW_1_START = 9;
    public static final int CONTENT_ROW_2_START = 18;

    // Inspect menu primary layout
    public static final int INSPECT_PLAYER_HEAD = 10;
    public static final int INSPECT_NOTES = 12;
    public static final int INSPECT_ALTS = 13;
    public static final int INSPECT_HISTORY = 14;
    public static final int INSPECT_REPORTS = 15;
    public static final int INSPECT_PUNISH = 17;

    // Staff menu primary layout
    public static final int STAFF_ONLINE_PLAYERS = 10;
    public static final int STAFF_REPORTS = 11;
    public static final int STAFF_PUNISHMENTS = 12;
    public static final int STAFF_TICKETS = 13;
    public static final int STAFF_PANEL_LINK = 15;
    public static final int STAFF_SETTINGS = 16;

    // List menu content area (rows 2-3: slots 18-35 for 18 items, or rows 3-4 for 6-row menus)
    public static final int LIST_CONTENT_START = 27;
    public static final int LIST_CONTENT_END = 34;

    // Footer row for 5-row menu (row 4: slots 36-44)
    public static final int FOOTER_5ROW_START = 36;
    public static final int FOOTER_5ROW_END = 44;

    // Footer row for 6-row menu (row 5: slots 45-53)
    public static final int FOOTER_6ROW_START = 45;
    public static final int FOOTER_6ROW_END = 53;

    // Navigation slots
    // Row 4 (36-44): * * < * y * > * * where < is prev, y is filter/sort, > is next
    public static final int PAGE_PREV = 38;
    public static final int FILTER_BUTTON = 40;
    public static final int SORT_BUTTON = 40;  // Same position as filter
    public static final int PAGE_NEXT = 42;

    // Row 5 (45-53): Q position for back button at bottom left
    public static final int BACK_BUTTON = 45;

    // For Notes menu, sign button goes in filter position
    public static final int CREATE_NOTE_BUTTON = 40;

    // Modify punishment menu slots
    public static final int MODIFY_ADD_NOTE = 28;
    public static final int MODIFY_EVIDENCE = 29;
    public static final int MODIFY_PARDON = 30;
    public static final int MODIFY_DURATION = 31;
    public static final int MODIFY_STAT_WIPE = 33;
    public static final int MODIFY_ALT_BLOCK = 34;

    // Punish severity menu slots
    public static final int SEVERITY_LENIENT = 28;
    public static final int SEVERITY_REGULAR = 30;
    public static final int SEVERITY_AGGRAVATED = 32;
    public static final int SEVERITY_SILENT = 33;
    public static final int SEVERITY_ALT_BLOCK = 42;
    public static final int SEVERITY_STAT_WIPE = 43;

    // Settings menu slots
    public static final int SETTINGS_INFO = 28;
    public static final int SETTINGS_NOTIFICATIONS = 30;
    public static final int SETTINGS_TICKETS = 31;
    public static final int SETTINGS_ROLES = 32;
    public static final int SETTINGS_STAFF = 33;
    public static final int SETTINGS_RELOAD = 34;

    /**
     * Check if a slot is in the header row.
     */
    public static boolean isHeaderSlot(int slot) {
        return slot >= HEADER_START && slot <= HEADER_END;
    }

    /**
     * Check if a slot is in the footer row (6-row menu).
     */
    public static boolean isFooterSlot(int slot) {
        return slot >= FOOTER_6ROW_START && slot <= FOOTER_6ROW_END;
    }

    /**
     * Get all header slots.
     */
    public static int[] getHeaderSlots() {
        return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    }

    /**
     * Get all footer slots for a 6-row menu.
     */
    public static int[] getFooterSlots() {
        return new int[]{45, 46, 47, 48, 49, 50, 51, 52, 53};
    }
}
