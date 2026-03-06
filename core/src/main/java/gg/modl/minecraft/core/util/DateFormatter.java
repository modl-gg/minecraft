package gg.modl.minecraft.core.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateFormatter {
    private static volatile String dateFormatPattern = "MM/dd/yyyy HH:mm";
    private static volatile TimeZone timeZone = null;

    private static final ThreadLocal<SimpleDateFormat> FORMAT_CACHE = new ThreadLocal<>();
    private static final ThreadLocal<String> CACHED_PATTERN = new ThreadLocal<>();
    private static final ThreadLocal<TimeZone> CACHED_TIMEZONE = new ThreadLocal<>();

    public static void setDateFormat(String pattern) {
        try {
            new SimpleDateFormat(pattern);
            dateFormatPattern = pattern;
            FORMAT_CACHE.remove();
        } catch (IllegalArgumentException ignored) {}
    }

    public static void setTimezone(String timezoneId) {
        if (timezoneId != null && !timezoneId.isEmpty()) timeZone = TimeZone.getTimeZone(timezoneId);
        else timeZone = null;
        FORMAT_CACHE.remove();
    }

    public static String format(Date dateToFormat) {
        SimpleDateFormat sdf = getFormatter();
        return sdf.format(dateToFormat);
    }

    private static SimpleDateFormat getFormatter() {
        String pattern = dateFormatPattern;
        TimeZone tz = timeZone;
        SimpleDateFormat sdf = FORMAT_CACHE.get();
        if (sdf == null || !pattern.equals(CACHED_PATTERN.get()) || tz != CACHED_TIMEZONE.get()) {
            sdf = new SimpleDateFormat(pattern);
            if (tz != null) sdf.setTimeZone(tz);
            FORMAT_CACHE.set(sdf);
            CACHED_PATTERN.set(pattern);
            CACHED_TIMEZONE.set(tz);
        }
        return sdf;
    }
}
