package gg.modl.minecraft.core.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@FunctionalInterface
public interface HttpConnectionOpener {
    HttpConnectionOpener SYSTEM = url -> (HttpURLConnection) url.openConnection();

    HttpURLConnection open(URL url) throws IOException;
}
