package gg.modl.minecraft.core.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Logger;

public final class DateFormatter {
    public static final String DEFAULT_PATTERN = "MM/dd/yyyy HH:mm";

    private static final Logger logger = Logger.getLogger(DateFormatter.class.getName());

    private final String pattern;
    private final TimeZone timeZone;
    private final ThreadLocal<SimpleDateFormat> formatCache;

    public DateFormatter(String pattern, String timezoneId) {
        this.pattern = validatedPattern(pattern);
        this.timeZone = resolveTimeZone(timezoneId);
        this.formatCache = ThreadLocal.withInitial(this::newFormatter);
    }

    public String format(Date dateToFormat) {
        return formatCache.get().format(dateToFormat);
    }

    private SimpleDateFormat newFormatter() {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        if (timeZone != null) sdf.setTimeZone(timeZone);
        return sdf;
    }

    private static String validatedPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) return DEFAULT_PATTERN;
        try {
            new SimpleDateFormat(pattern);
            return pattern;
        } catch (IllegalArgumentException e) {
            logger.warning("Ignoring invalid date format pattern '" + pattern + "': " + e.getMessage());
            return DEFAULT_PATTERN;
        }
    }

    private static TimeZone resolveTimeZone(String timezoneId) {
        if (timezoneId == null || timezoneId.isEmpty()) return null;
        return TimeZone.getTimeZone(timezoneId);
    }
}
