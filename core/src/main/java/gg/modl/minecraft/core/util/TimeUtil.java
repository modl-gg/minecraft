package gg.modl.minecraft.core.util;


import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {
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
        Pattern p = Pattern.compile("[a-z]+|\\d+");
        Matcher m = p.matcher(arg.toLowerCase());

        int time = -1;
        String type = null;
        long duration = 0;
        while (m.find()) {
            String a = m.group();
            try {
                time = Integer.parseInt(a);
                if (time < 1) {
                    time = -1;
                }
            } catch (NumberFormatException ignored) {
                type = a;
            }

            if (time > 0 && type != null) {
                switch (type) {
                    case "seconds", "second", "sec", "s" -> duration += time * 1000L;
                    case "minutes", "minute", "m" -> duration += time * 60 * 1000L;
                    case "hours", "hrs", "hr", "h" -> duration += time * 60 * 60 * 1000L;
                    case "days", "day", "d" -> duration += time * 24 * 60 * 60 * 1000L;
                    case "weeks", "week", "w" -> duration += time * 7 * 24 * 60 * 60 * 1000L;
                    case "months", "month", "mo" -> duration += time * 30 * 24 * 60 * 60 * 1000L; // Approximation
                }

                time = -1;
                type = null;
            }
        }

        if (duration == 0) return -1L;

        return duration;
    }

    public static Date getTime(String arg) {
        Pattern p = Pattern.compile("[a-z]+|\\d+");
        Matcher m = p.matcher(arg.toLowerCase());

        int time = -1;
        String type = null;
        boolean isTemp = false;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        while (m.find()) {
            String a = m.group();
            try {
                time = Integer.parseInt(a);
                if (time < 1) {
                    time = -1;
                }
            } catch (NumberFormatException ignored) {
                type = a;
            }

            if (time > 0 && type != null) {
                switch (type) {
                    case "seconds", "second", "sec", "s" -> calendar.add(Calendar.SECOND, time);
                    case "minutes", "minute", "m" -> calendar.add(Calendar.MINUTE, time);
                    case "hours", "hrs", "hr", "h" -> calendar.add(Calendar.HOUR, time);
                    case "days", "day", "d" -> calendar.add(Calendar.HOUR, time * 24);
                    case "weeks", "week", "w" -> calendar.add(Calendar.HOUR, time * 24 * 7);
                    case "months", "month", "mo" -> calendar.add(Calendar.MONTH, time);
                }

                isTemp = true;
                time = -1;
                type = null;
            }
        }

        return isTemp ? calendar.getTime() : null;
    }

}