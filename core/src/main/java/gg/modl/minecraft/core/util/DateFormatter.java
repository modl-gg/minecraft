package gg.modl.minecraft.core.util;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateFormatter {
    private static String dateFormatPattern = "MM/dd/yyyy HH:mm";
    private static TimeZone timeZone = null;

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

    /**
     * Set the timezone from config. Called during plugin initialization.
     * If null or empty, uses the server's default timezone.
     */
    public static void setTimezone(String timezoneId) {
        if (timezoneId != null && !timezoneId.isEmpty()) {
            timeZone = TimeZone.getTimeZone(timezoneId);
        } else {
            timeZone = null;
        }
    }

    public static String format(Date dateToFormat) {
        SimpleDateFormat date = new SimpleDateFormat(dateFormatPattern);
        if (timeZone != null) {
            date.setTimeZone(timeZone);
        }
        return date.format(dateToFormat);
    }
}
