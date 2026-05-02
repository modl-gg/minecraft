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
        DateFormatter.setDateFormat("MM/dd/yyyy HH:mm");
        DateFormatter.setTimezone("");
    }

    @Test
    void menuItemsFormatDateDelegatesToDateFormatterConfiguration() {
        Date date = new Date(0L);

        MenuItems.setDateFormat("yyyy-MM-dd HH:mm z");
        MenuItems.setTimezone("UTC");

        assertEquals("1970-01-01 00:00 UTC", DateFormatter.format(date));
        assertEquals(DateFormatter.format(date), MenuItems.formatDate(date));
    }

    @Test
    void menuItemsFormatDateKeepsUnknownForNullDate() {
        assertEquals("Unknown", MenuItems.formatDate(null));
    }

    @Test
    void menuItemsCanClearConfiguredTimezone() {
        Date date = new Date(0L);
        MenuItems.setDateFormat("yyyy-MM-dd HH:mm z");
        MenuItems.setTimezone("UTC");
        MenuItems.setTimezone("");

        assertEquals(new SimpleDateFormat("yyyy-MM-dd HH:mm z").format(date), MenuItems.formatDate(date));
    }
}
