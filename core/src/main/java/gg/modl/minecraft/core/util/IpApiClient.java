package gg.modl.minecraft.core.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IpApiClient {
    private IpApiClient() {}

    private static final Logger logger = Logger.getLogger(IpApiClient.class.getName());
    private static final int LOOKUP_QUEUE_CAPACITY = 64;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5L;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 5_000;
    private static final String IP_PLACEHOLDER = "{ip}";

    static final boolean DEFAULT_ENABLED = true;
    static final String DEFAULT_URL_TEMPLATE = "https://ipwho.is/{ip}";

    private static final Object EXECUTOR_LOCK = new Object();
    private static volatile ThreadPoolExecutor LOOKUP_EXECUTOR = createLookupExecutor();

    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile String urlTemplate = DEFAULT_URL_TEMPLATE;
    private static final AtomicBoolean disabledWarningEmitted = new AtomicBoolean();

    public static void initialize(boolean enabledFlag, String urlTemplateValue) {
        enabled = enabledFlag;
        String resolved = (urlTemplateValue == null || urlTemplateValue.trim().isEmpty())
                ? DEFAULT_URL_TEMPLATE
                : urlTemplateValue.trim();
        urlTemplate = resolved;
        if (enabledFlag) disabledWarningEmitted.set(false);
    }

    public static CompletableFuture<Map<String, Object>> getIpInfo(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            if (isPrivateIp(ipAddress)) {
                logger.fine("Skipping IP lookup for private/local/malformed IP: " + ipAddress);
                return createLocalIpInfo();
            }
            if (!enabled) {
                if (disabledWarningEmitted.compareAndSet(false, true)) {
                    logger.warning("Public IP enrichment is disabled; pendingIpLookups will not be resolved.");
                }
                return null;
            }
            return lookupRemote(ipAddress);
        }, lookupExecutor()).exceptionally(throwable -> {
            logger.warning("IP lookup failed for " + ipAddress + ": " + throwable.getMessage());
            return null;
        });
    }

    public static void shutdown() {
        ThreadPoolExecutor executor;
        synchronized (EXECUTOR_LOCK) {
            executor = LOOKUP_EXECUTOR;
            LOOKUP_EXECUTOR = createLookupExecutor();
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Object> lookupRemote(String ipAddress) {
        String requestUrl = urlTemplate.replace(IP_PLACEHOLDER, ipAddress);
        URL url;
        try {
            url = new URL(requestUrl);
        } catch (IOException e) {
            logger.warning("Invalid IP lookup URL for " + ipAddress + ": " + e.getMessage());
            return null;
        }
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            logger.warning("Refusing IP lookup over non-HTTPS scheme '" + url.getProtocol() + "' for " + ipAddress);
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                logger.fine("IP lookup HTTP " + responseCode + " for " + ipAddress);
                return null;
            }
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                char[] buffer = new char[1024];
                int read;
                while ((read = reader.read(buffer)) != -1) body.append(buffer, 0, read);
                return parseIpWhoIsResponse(body.toString());
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "IP lookup IO failure for " + ipAddress + ": " + e.getMessage());
            return null;
        } catch (RuntimeException e) {
            logger.warning("IP lookup parse error for " + ipAddress + ": " + e.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Map<String, Object> parseIpWhoIsResponse(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) return null;
        JsonObject json = root.getAsJsonObject();

        boolean success = json.has("success") && !json.get("success").isJsonNull() && json.get("success").getAsBoolean();
        if (!success) return null;

        Map<String, Object> info = new HashMap<>();
        info.put("status", "success");
        info.put("countryCode", optString(json, "country_code"));
        info.put("regionName", optString(json, "region"));

        String as = null;
        if (json.has("connection") && json.get("connection").isJsonObject()) {
            as = optString(json.getAsJsonObject("connection"), "isp");
        }
        info.put("as", as);

        boolean proxy = false;
        if (json.has("security") && json.get("security").isJsonObject()) {
            JsonObject security = json.getAsJsonObject("security");
            if (security.has("is_proxy") && !security.get("is_proxy").isJsonNull()) {
                proxy = security.get("is_proxy").getAsBoolean();
            }
        }
        info.put("proxy", proxy);
        info.put("hosting", false);
        return info;
    }

    private static String optString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return null;
        return json.get(key).getAsString();
    }

    private static ThreadPoolExecutor lookupExecutor() {
        ThreadPoolExecutor executor = LOOKUP_EXECUTOR;
        if (!executor.isShutdown() && !executor.isTerminated()) return executor;
        synchronized (EXECUTOR_LOCK) {
            executor = LOOKUP_EXECUTOR;
            if (executor.isShutdown() || executor.isTerminated()) {
                LOOKUP_EXECUTOR = createLookupExecutor();
            }
            return LOOKUP_EXECUTOR;
        }
    }

    private static ThreadPoolExecutor createLookupExecutor() {
        AtomicInteger threadCounter = new AtomicInteger();
        return new ThreadPoolExecutor(
                1,
                2,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(LOOKUP_QUEUE_CAPACITY),
                r -> {
                    Thread thread = new Thread(r, "modl-ip-api-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private static boolean isPrivateIp(String ipAddress) {
        InetAddress address = parseIpLiteral(ipAddress);
        if (address == null) return true;

        return address.isAnyLocalAddress() ||
                address.isLoopbackAddress() ||
                address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() ||
                isUniqueLocalIpv6(address);
    }

    private static InetAddress parseIpLiteral(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) return null;
        String trimmedIpAddress = ipAddress.trim();
        if (trimmedIpAddress.indexOf(':') >= 0) return parseIpv6Literal(trimmedIpAddress);
        return parseIpv4Literal(trimmedIpAddress);
    }

    private static InetAddress parseIpv4Literal(String ipAddress) {
        String[] octets = ipAddress.split("\\.", -1);
        if (octets.length != 4) return null;
        byte[] bytes = new byte[4];
        try {
            for (int index = 0; index < octets.length; index++) {
                if (octets[index].isEmpty()) return null;
                int value = Integer.parseInt(octets[index]);
                if (value < 0 || value > 255) return null;
                bytes[index] = (byte) value;
            }
            return InetAddress.getByAddress(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private static InetAddress parseIpv6Literal(String ipAddress) {
        for (int index = 0; index < ipAddress.length(); index++) {
            char character = ipAddress.charAt(index);
            boolean hexadecimal = (character >= '0' && character <= '9') ||
                    (character >= 'a' && character <= 'f') ||
                    (character >= 'A' && character <= 'F');
            if (!hexadecimal && character != ':' && character != '.') return null;
        }
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            if (!(address instanceof Inet6Address)) return null;
            return address;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) return false;
        byte firstByte = address.getAddress()[0];
        return (firstByte & (byte) 0xfe) == (byte) 0xfc;
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
