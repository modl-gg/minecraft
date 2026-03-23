package gg.modl.minecraft.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private TimeUtil() {}
    private static final Pattern DURATION_TOKEN_PATTERN = Pattern.compile("[a-z]+|\\d+");
    private static final long MILLIS_PER_SECOND = 1000L,
            MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND,
            MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE,
            MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR,
            MILLIS_PER_WEEK = 7 * MILLIS_PER_DAY,
            MILLIS_PER_MONTH = 30 * MILLIS_PER_DAY;

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
                    case "seconds": case "second": case "sec": case "s":
                        duration += time * MILLIS_PER_SECOND; break;
                    case "minutes": case "minute": case "m":
                        duration += time * MILLIS_PER_MINUTE; break;
                    case "hours": case "hrs": case "hr": case "h":
                        duration += time * MILLIS_PER_HOUR; break;
                    case "days": case "day": case "d":
                        duration += time * MILLIS_PER_DAY; break;
                    case "weeks": case "week": case "w":
                        duration += time * MILLIS_PER_WEEK; break;
                    case "months": case "month": case "mo":
                        duration += time * MILLIS_PER_MONTH; break;
                }
                time = -1;
                type = null;
            }
        }

        return duration == 0 ? -1L : duration;
    }
}