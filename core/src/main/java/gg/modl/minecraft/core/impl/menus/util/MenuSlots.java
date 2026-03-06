package gg.modl.minecraft.core.impl.menus.util;

public final class MenuSlots {
    private MenuSlots() {}

    // Inspect menu primary layout
    public static final int INSPECT_PLAYER_HEAD = 10;
    public static final int INSPECT_NOTES = 11;
    public static final int INSPECT_ALTS = 12;
    public static final int INSPECT_HISTORY = 13;
    public static final int INSPECT_REPORTS = 14;
    public static final int INSPECT_PUNISH = 16;

    // Staff menu primary layout
    public static final int STAFF_ONLINE_PLAYERS = 10;
    public static final int STAFF_REPORTS = 11;
    public static final int STAFF_PUNISHMENTS = 12;
    public static final int STAFF_TICKETS = 13;
    public static final int STAFF_PANEL_LINK = 15;
    public static final int STAFF_SETTINGS = 16;

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
    public static final int MODIFY_LINKED_TICKETS = 32;
    public static final int MODIFY_STAT_WIPE = 33;
    public static final int MODIFY_ALT_BLOCK = 34;

    // Punish severity menu slots
    public static final int SEVERITY_LENIENT = 28;
    public static final int SEVERITY_REGULAR = 30;
    public static final int SEVERITY_AGGRAVATED = 32;
    public static final int SEVERITY_LINK_REPORTS = 33;
    public static final int SEVERITY_SILENT = 34;
    public static final int SEVERITY_ALT_BLOCK = 42;
    public static final int SEVERITY_STAT_WIPE = 43;

    // Settings menu slots
    public static final int SETTINGS_INFO = 28;
    public static final int SETTINGS_NOTIFICATIONS = 30;
    public static final int SETTINGS_TICKETS = 31;
    public static final int SETTINGS_ROLES = 32;
    public static final int SETTINGS_STAFF = 33;
    public static final int SETTINGS_RELOAD = 34;

    // Compact (3-row) menu slots - for initial /staff and /inspect menus
    // Row 0: Empty/decorative
    // Row 1: Navigation items
    // Row 2: Back button (if any)
    public static final int COMPACT_BACK_BUTTON = 18;

    // Compact staff menu layout (centered in row 1)
    public static final int COMPACT_STAFF_ONLINE = 10;
    public static final int COMPACT_STAFF_REPORTS = 11;
    public static final int COMPACT_STAFF_PUNISHMENTS = 12;
    public static final int COMPACT_STAFF_TICKETS = 13;
    public static final int COMPACT_STAFF_PANEL = 15;
    public static final int COMPACT_STAFF_SETTINGS = 16;

    // Compact inspect menu layout (centered in row 1)
    public static final int COMPACT_INSPECT_HEAD = 10;
    public static final int COMPACT_INSPECT_NOTES = 11;
    public static final int COMPACT_INSPECT_ALTS = 12;
    public static final int COMPACT_INSPECT_HISTORY = 13;
    public static final int COMPACT_INSPECT_REPORTS = 14;
    public static final int COMPACT_INSPECT_PUNISH = 16;

}
