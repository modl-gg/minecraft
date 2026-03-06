package gg.modl.minecraft.core.util;

import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
    private static final long MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;
    private static final long MILLIS_PER_WEEK = 7 * MILLIS_PER_DAY;
    private static final long MILLIS_PER_MONTH = 30 * MILLIS_PER_DAY;
    private static final Pattern DURATION_TOKEN_PATTERN = Pattern.compile("[a-z]+|\\d+");

    public static String formatTimeMillis(long millis) {
        long seconds = (millis / 1000L) + 1;

        if (seconds < 1) return "0 seconds";

        long minutes = seconds / 60;
        seconds = seconds % 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        long day = hours / 24;
        hours = hours % 24;
        long years = day / 365;
        day = day % 365;

        StringBuilder time = new StringBuilder();

        if (years != 0) time.append(years).append("y ");
        if (day != 0) time.append(day).append("d ");
        if (hours != 0) time.append(hours).append("h ");
        if (minutes != 0) time.append(minutes).append("m ");
        if (seconds != 0) time.append(seconds).append("s ");

        return time.toString().trim();
    }

    public static long getDuration(String arg) {
        Matcher m = DURATION_TOKEN_PATTERN.matcher(arg.toLowerCase());

        int time = -1;
        String type = null;
        long duration = 0;
        while (m.find()) {
            String token = m.group();
            try {
                time = Integer.parseInt(token);
                if (time < 1) time = -1;
            } catch (NumberFormatException ignored) {
                type = token;
            }

            if (time > 0 && type != null) {
                switch (type) {
                    case "seconds", "second", "sec", "s" -> duration += time * MILLIS_PER_SECOND;
                    case "minutes", "minute", "m" -> duration += time * MILLIS_PER_MINUTE;
                    case "hours", "hrs", "hr", "h" -> duration += time * MILLIS_PER_HOUR;
                    case "days", "day", "d" -> duration += time * MILLIS_PER_DAY;
                    case "weeks", "week", "w" -> duration += time * MILLIS_PER_WEEK;
                    case "months", "month", "mo" -> duration += time * MILLIS_PER_MONTH;
                }
                time = -1;
                type = null;
            }
        }

        return duration == 0 ? -1L : duration;
    }
}