package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.sync.StatWipeExecutor;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import gg.modl.minecraft.core.util.PluginLogger;
import lombok.Setter;

public class QueryStatWipeExecutor implements StatWipeExecutor {
    private final List<QueryClient> clients = new ArrayList<>();
    private final EventLoopGroup eventLoopGroup;
    private final PluginLogger logger;
    private final boolean debugMode;
    @Setter private BridgeMessageDispatcher bridgeMessageDispatcher;

    public QueryStatWipeExecutor(PluginLogger logger, boolean debugMode) {
        this.logger = logger;
        this.debugMode = debugMode;
        this.eventLoopGroup = new NioEventLoopGroup(1);
    }

    public void addBridge(String serverName, String host, int port, String secret) {
        QueryClient client = new QueryClient(
                serverName, host, port, secret,
                logger, this::handleMessage, eventLoopGroup
        );
        clients.add(client);
        client.connect();
        logger.info("Connecting to bridge on " + serverName + " (" + host + ":" + port + ")");
    }

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        String firstServerName = null;

        for (QueryClient client : clients) {
            if (!client.isConnected()) {
                if (debugMode) logger.info("[bridge] Skipping bridge on " + client.getServerName() + " (not connected)");
                continue;
            }

            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeUTF("STAT_WIPE");
                out.writeUTF(username);
                out.writeUTF(uuid);
                out.writeUTF(punishmentId);

                client.sendMessage(bytes.toByteArray());
                if (firstServerName == null) firstServerName = client.getServerName();
                logger.info("[bridge] Sent stat wipe request to bridge on " + client.getServerName() +
                        " for " + username + " (punishment: " + punishmentId + ")");
            } catch (IOException e) {
                logger.warning("[bridge] Failed to send stat wipe to bridge on " + client.getServerName() +
                        ": " + e.getMessage());
            }
        }

        if (firstServerName != null) callback.onComplete(true, firstServerName);
        else
            logger.warning("[bridge] No connected bridges available for stat wipe of " + username +
                    " — will retry on next sync");
    }

    private void handleMessage(String serverName, QueryClient.QueryMessage message) {
        if ("BRIDGE_HELLO".equals(message.getAction()))
            logger.info("modl-bridge detected on backend server '" + serverName + "' (TCP query)");
        else if (bridgeMessageDispatcher != null) bridgeMessageDispatcher.dispatch(message.getAction(), message.getData());
    }

    public void sendToAllBridges(String action, String... args) {
        for (QueryClient client : clients) if (client.isConnected()) client.sendTypedMessage(action, args);
    }

    public void shutdown() {
        for (QueryClient client : clients) client.shutdown();
        eventLoopGroup.shutdownGracefully();
    }
}
