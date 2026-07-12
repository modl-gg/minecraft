package gg.modl.minecraft.core.util;

import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateFormatterTest {
    @AfterEach
    void resetFormatterState() {
        MenuItems.setDateFormatter(new DateFormatter(DateFormatter.DEFAULT_PATTERN, null));
    }

    @Test
    void menuItemsFormatDateDelegatesToConfiguredFormatter() {
        Date date = new Date(0L);
        DateFormatter formatter = new DateFormatter("yyyy-MM-dd HH:mm z", "UTC");

        MenuItems.setDateFormatter(formatter);

        assertEquals("1970-01-01 00:00 UTC", formatter.format(date));
        assertEquals(formatter.format(date), MenuItems.formatDate(date));
    }

    @Test
    void menuItemsFormatDateKeepsUnknownForNullDate() {
        assertEquals("Unknown", MenuItems.formatDate(null));
    }

    @Test
    void emptyTimezoneUsesSystemDefault() {
        Date date = new Date(0L);
        MenuItems.setDateFormatter(new DateFormatter("yyyy-MM-dd HH:mm z", ""));

        assertEquals(new SimpleDateFormat("yyyy-MM-dd HH:mm z").format(date), MenuItems.formatDate(date));
    }

    @Test
    void invalidPatternFallsBackToDefault() {
        Date date = new Date(0L);
        DateFormatter formatter = new DateFormatter("not-a-]pattern[", null);

        assertEquals(new SimpleDateFormat(DateFormatter.DEFAULT_PATTERN).format(date), formatter.format(date));
    }
}
