package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Client for querying IP geolocation data from ip-api.com
 * Based on HammerV2 implementation pattern
 */
public class IpApiClient {
    private static final Logger logger = Logger.getLogger(IpApiClient.class.getName());
    private static final String IP_API_URL = "http://ip-api.com/json/%s?fields=status,message,countryCode,regionName,city,as,proxy,hosting";
    private static final Gson gson = new Gson();
    
    /**
     * Query IP information asynchronously
     * 
     * @param ipAddress The IP address to query
     * @return CompletableFuture containing IP information or null if failed
     */
    public static CompletableFuture<JsonObject> getIpInfo(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Skip private/local IP addresses
                if (isPrivateIp(ipAddress)) {
                    logger.fine("Skipping IP lookup for private/local IP: " + ipAddress);
                    return createLocalIpInfo();
                }
                
                String urlString = String.format(IP_API_URL, ipAddress);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Set request properties
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000); // 5 second timeout
                connection.setReadTimeout(5000);    // 5 second read timeout
                connection.setRequestProperty("User-Agent", "modl-minecraft/1.0");
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    // Read response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Parse JSON response
                    JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
                    
                    // Check if the API returned success
                    if (jsonResponse.has("status") && "success".equals(jsonResponse.get("status").getAsString())) {
                        logger.fine("Successfully retrieved IP info for " + ipAddress);
                        return jsonResponse;
                    } else {
                        String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unknown error";
                        logger.warning("IP API returned error for " + ipAddress + ": " + message);
                        return null;
                    }
                } else {
                    logger.warning("IP API request failed with code " + responseCode + " for IP " + ipAddress);
                    return null;
                }
            } catch (Exception e) {
                logger.warning("Failed to get IP info for " + ipAddress + ": " + e.getMessage());
                return null;
            }
        }).exceptionally(throwable -> {
            logger.warning("IP lookup failed for " + ipAddress + ": " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Check if an IP address is private/local
     * 
     * @param ipAddress The IP address to check
     * @return true if the IP is private/local
     */
    private static boolean isPrivateIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return true;
        }
        
        // Common private/local IP patterns
        return ipAddress.startsWith("127.") ||           // Loopback
               ipAddress.startsWith("192.168.") ||       // Private Class C
               ipAddress.startsWith("10.") ||            // Private Class A
               ipAddress.startsWith("172.") ||           // Private Class B (rough check)
               ipAddress.equals("0:0:0:0:0:0:0:1") ||    // IPv6 loopback
               ipAddress.equals("::1") ||                // IPv6 loopback short
               ipAddress.startsWith("fe80:") ||          // IPv6 link-local
               ipAddress.startsWith("fc00:") ||          // IPv6 unique local
               ipAddress.startsWith("fd00:");            // IPv6 unique local
    }
    
    /**
     * Create a default IP info object for local/private IPs
     * 
     * @return JsonObject with default local IP information
     */
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
    
    /**
     * Data class for IP information (for type safety)
     */
    @Data
    public static class IpInfo {
        private String status;
        private String message;
        private String countryCode;
        private String regionName;
        private String city;
        private String as;
        private boolean proxy;
        private boolean hosting;
        
        public static IpInfo fromJson(JsonObject json) {
            return gson.fromJson(json, IpInfo.class);
        }
    }
}