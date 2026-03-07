package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class IpApiClient {
    private static final Logger logger = Logger.getLogger(IpApiClient.class.getName());
    private static final String IP_API_URL = "http://ip-api.com/json/%s?fields=status,message,countryCode,regionName,city,as,proxy,hosting";
    private static final Gson gson = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 3000, READ_TIMEOUT_MS = 3000, HTTP_RATE_LIMITED = 429, HTTP_OK = 200;

    public static CompletableFuture<Map<String, Object>> getIpInfo(String ipAddress) {
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

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }

                Map<String, Object> mapResponse = gson.fromJson(response.toString(), new TypeToken<Map<String, Object>>(){}.getType());
                if (mapResponse.containsKey("status") && "success".equals(mapResponse.get("status"))) {
                    logger.fine("Successfully retrieved IP info for " + ipAddress);
                    return mapResponse;
                }
                String message = mapResponse.containsKey("message") ? (String) mapResponse.get("message") : "Unknown error";
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
               isPrivate172(ipAddress) ||
               ipAddress.equals("0:0:0:0:0:0:0:1") ||
               ipAddress.equals("::1") ||
               ipAddress.startsWith("fe80:") ||
               ipAddress.startsWith("fc00:") ||
               ipAddress.startsWith("fd00:");
    }
    
    private static boolean isPrivate172(String ipAddress) {
        if (!ipAddress.startsWith("172.")) return false;
        try {
            int secondOctet = Integer.parseInt(ipAddress.split("\\.")[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    private static Map<String, Object> createLocalIpInfo() {
        Map<String, Object> localInfo = new HashMap<>();
        localInfo.put("status", "success");
        localInfo.put("countryCode", "XX");
        localInfo.put("regionName", "Local");
        localInfo.put("city", "Local");
        localInfo.put("as", "Private Network");
        localInfo.put("proxy", false);
        localInfo.put("hosting", false);
        return localInfo;
    }
}