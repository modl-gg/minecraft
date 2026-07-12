package gg.modl.minecraft.core.login;

import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LoginRequestBuilder {
    private final PluginLogger logger;

    public LoginRequestBuilder(PluginLogger logger) {
        this.logger = logger;
    }

    public PlayerLoginRequest build(String uuid, String username, String ipAddress, String serverName,
                                    CompletableFuture<Map<String, Object>> ipInfoFuture,
                                    CompletableFuture<String> skinHashFuture,
                                    long awaitTimeoutSeconds) {
        Map<String, Object> ipInfo = awaitQuietly(ipInfoFuture, awaitTimeoutSeconds, "ipInfo");
        String skinHash = awaitQuietly(skinHashFuture, awaitTimeoutSeconds, "skinHash");

        return new PlayerLoginRequest(uuid, username, ipAddress, skinHash, serverName, ipInfo,
                StartupClient.getServerInstanceId());
    }

    private <T> T awaitQuietly(CompletableFuture<T> future, long timeoutSeconds, String label) {
        if (future == null) return null;
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.warning("Login " + label + " lookup did not complete in time: " + e.getMessage());
            return null;
        }
    }
}
