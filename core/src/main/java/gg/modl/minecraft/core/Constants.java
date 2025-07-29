package gg.modl.minecraft.core;

public interface Constants {
    String DATE_FORMAT = "MM/dd/yy hh:mm:ss aa";

    // enable this if you want to ban/report players that have never joined (not in db)
    // could lead to rate limit errors so not recommended
    boolean QUERY_MOJANG = false;
}
