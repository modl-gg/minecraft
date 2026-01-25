package gg.modl.minecraft.core.util;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateFormatter {
    public static String format(Date dateToFormat) {
        SimpleDateFormat date = new SimpleDateFormat("MM/dd/yyyy HH:mm");
        date.setTimeZone(TimeZone.getTimeZone("EST"));

        return date.format(dateToFormat) + " EST";
    }
}
