package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class IpApiClient {
    private static final Logger logger = Logger.getLogger(IpApiClient.class.getName());
    private static final String IP_API_URL = "http://ip-api.com/json/%s?fields=status,message,countryCode,regionName,city,as,proxy,hosting";
    private static final Gson gson = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int HTTP_RATE_LIMITED = 429;
    private static final int HTTP_OK = 200;

    public static CompletableFuture<JsonObject> getIpInfo(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (isPrivateIp(ipAddress)) {
                    logger.fine("Skipping IP lookup for private/local IP: " + ipAddress);
                    return createLocalIpInfo();
                }
                
                String urlString = String.format(IP_API_URL, ipAddress);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", "modl-minecraft/1.0");
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HTTP_RATE_LIMITED) {
                    logger.warning("IP API rate limited, skipping lookup for " + ipAddress);
                    return null;
                }
                if (responseCode != HTTP_OK) {
                    logger.warning("IP API returned " + responseCode + " for " + ipAddress + ", skipping");
                    return null;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
                if (jsonResponse.has("status") && "success".equals(jsonResponse.get("status").getAsString())) {
                    logger.fine("Successfully retrieved IP info for " + ipAddress);
                    return jsonResponse;
                }
                String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unknown error";
                logger.warning("IP API returned error for " + ipAddress + ": " + message);
                return null;
            } catch (Exception e) {
                logger.warning("Failed to get IP info for " + ipAddress + ": " + e.getMessage());
                return null;
            }
        }).exceptionally(throwable -> {
            logger.warning("IP lookup failed for " + ipAddress + ": " + throwable.getMessage());
            return null;
        });
    }
    
    private static boolean isPrivateIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) return true;
        
        return ipAddress.startsWith("127.") ||
               ipAddress.startsWith("192.168.") ||
               ipAddress.startsWith("10.") ||
               ipAddress.startsWith("172.") ||
               ipAddress.equals("0:0:0:0:0:0:0:1") ||
               ipAddress.equals("::1") ||
               ipAddress.startsWith("fe80:") ||
               ipAddress.startsWith("fc00:") ||
               ipAddress.startsWith("fd00:");
    }
    
    private static JsonObject createLocalIpInfo() {
        JsonObject localInfo = new JsonObject();
        localInfo.addProperty("status", "success");
        localInfo.addProperty("countryCode", "XX");
        localInfo.addProperty("regionName", "Local");
        localInfo.addProperty("city", "Local");
        localInfo.addProperty("as", "Private Network");
        localInfo.addProperty("proxy", false);
        localInfo.addProperty("hosting", false);
        return localInfo;
    }
}