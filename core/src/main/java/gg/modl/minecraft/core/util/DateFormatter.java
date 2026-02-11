package gg.modl.minecraft.core.util;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateFormatter {
    private static String dateFormatPattern = "MM/dd/yyyy HH:mm";

    /**
     * Set the date format pattern from config. Called during plugin initialization.
     */
    public static void setDateFormat(String pattern) {
        try {
            new SimpleDateFormat(pattern); // validate
            dateFormatPattern = pattern;
        } catch (IllegalArgumentException ignored) {
            // Keep default if pattern is invalid
        }
    }

    public static String format(Date dateToFormat) {
        SimpleDateFormat date = new SimpleDateFormat(dateFormatPattern);
        date.setTimeZone(TimeZone.getTimeZone("EST"));

        return date.format(dateToFormat) + " EST";
    }
}
