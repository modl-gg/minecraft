package gg.modl.minecraft.spigot.bridge.query;

import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import gg.modl.minecraft.spigot.bridge.statwipe.StatWipeHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BridgeQueryClient {
    private static final byte[] MAGIC = "modl".getBytes(StandardCharsets.US_ASCII);
    private static final long[] BACKOFF_DELAYS = {5, 10, 20, 40, 60};
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int MAX_FRAME_LENGTH = 65536;

    private final String host;
    private final int port;
    private final String secret;
    private final String serverName;
    private final StatWipeHandler statWipeHandler;
    private final FreezeHandler freezeHandler;
    private final StaffModeHandler staffModeHandler;
    private final JavaPlugin plugin;

    @Setter private ReplayService replayService;

    private final EventLoopGroup group;
    private Channel channel;
    private volatile boolean connected = false;
    private volatile boolean shuttingDown = false;
    private int reconnectAttempt = 0;

    public BridgeQueryClient(String host, int port, String secret, String serverName,
                             StatWipeHandler statWipeHandler, FreezeHandler freezeHandler,
                             StaffModeHandler staffModeHandler, JavaPlugin plugin) {
        this.host = host;
        this.port = port;
        this.secret = secret;
        this.serverName = serverName;
        this.statWipeHandler = statWipeHandler;
        this.freezeHandler = freezeHandler;
        this.staffModeHandler = staffModeHandler;
        this.plugin = plugin;
        this.group = new NioEventLoopGroup(1);
    }

    public void connect() {
        shuttingDown = false;
        doConnect();
    }

    private void doConnect() {
        if (shuttingDown) return;

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // No frame codecs yet — handshake uses raw bytes.
                        // Frame codecs are added after auth succeeds.
                        ch.pipeline().addLast("handler", new BridgeClientHandler());
                    }
                });

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                reconnectAttempt = 0;
                sendHandshake();
            } else {
                String cause = future.cause() != null ? future.cause().getMessage() : "unknown";
                plugin.getLogger().warning("[bridge] Failed to connect to proxy at " + host + ":" + port + ": " + cause);
                scheduleReconnect();
            }
        });
    }

    private void sendHandshake() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(MAGIC);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(secret);

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(bytes.toByteArray());
            channel.writeAndFlush(buf);
        } catch (IOException e) {
            plugin.getLogger().warning("[bridge] Failed to send handshake: " + e.getMessage());
            channel.close();
        }
    }

    private void sendBridgeHello() {
        sendMessage("BRIDGE_HELLO", serverName);
        plugin.getLogger().info("[bridge] Sent BRIDGE_HELLO to proxy as '" + serverName + "'");
    }

    private void scheduleReconnect() {
        if (shuttingDown) return;

        int delayIndex = Math.min(reconnectAttempt, BACKOFF_DELAYS.length - 1);
        long delay = BACKOFF_DELAYS[delayIndex];
        reconnectAttempt++;

        group.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    public void sendMessage(String action, String... args) {
        if (!connected || channel == null || !channel.isActive()) return;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(action);
            for (String arg : args) {
                dos.writeUTF(arg != null ? arg : "");
            }
            dos.flush();

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(baos.toByteArray());
            channel.writeAndFlush(buf);
        } catch (IOException e) {
            plugin.getLogger().warning("[bridge] Failed to send " + action + ": " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }

    public void shutdown() {
        shuttingDown = true;
        connected = false;
        if (channel != null) channel.close();
        group.shutdownGracefully();
    }

    private void handleMessage(DataInputStream in) throws IOException {
        String action = in.readUTF();

        if ("STAT_WIPE".equals(action)) {
            handleStatWipe(in);
        } else if ("FREEZE_PLAYER".equals(action)) {
            String targetUuid = in.readUTF();
            String staffUuid = in.readUTF();
            freezeHandler.freeze(targetUuid, staffUuid);
        } else if ("UNFREEZE_PLAYER".equals(action)) {
            String targetUuid = in.readUTF();
            freezeHandler.unfreeze(targetUuid);
        } else if ("STAFF_MODE_ENTER".equals(action)) {
            String staffUuid = in.readUTF();
            in.readUTF(); // staffName — not needed locally
            staffModeHandler.enterStaffMode(staffUuid);
        } else if ("STAFF_MODE_EXIT".equals(action)) {
            String staffUuid = in.readUTF();
            in.readUTF();
            staffModeHandler.exitStaffMode(staffUuid);
        } else if ("VANISH_ENTER".equals(action)) {
            String staffUuid = in.readUTF();
            in.readUTF();
            staffModeHandler.vanishFromBridge(staffUuid);
        } else if ("VANISH_EXIT".equals(action)) {
            String staffUuid = in.readUTF();
            in.readUTF();
            staffModeHandler.unvanishFromBridge(staffUuid);
        } else if ("TARGET_REQUEST".equals(action)) {
            String staffUuid = in.readUTF();
            String targetUuid = in.readUTF();
            handleTargetRequest(staffUuid, targetUuid);
        } else if ("CONNECT_SERVER".equals(action)) {
            // no-op on backend — only meaningful on proxy
        } else if ("CAPTURE_REPLAY".equals(action)) {
            String targetUuid = in.readUTF();
            String targetName = in.readUTF();
            handleCaptureReplay(targetUuid, targetName);
        } else {
            plugin.getLogger().info("[bridge] Unknown action from proxy: " + action);
        }
    }

    private void handleStatWipe(DataInputStream in) throws IOException {
        String username = in.readUTF();
        String uuid = in.readUTF();
        String punishmentId = in.readUTF();

        plugin.getLogger().info("[bridge] Processing stat-wipe for " + username + " (punishment: " + punishmentId + ")");
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = statWipeHandler.execute(username, uuid, punishmentId);
            plugin.getLogger().info("[bridge] Stat-wipe for " + username + " " +
                    (success ? "succeeded" : "failed") + " (punishment: " + punishmentId + ")");
        });
    }

    private void handleTargetRequest(String staffUuid, String targetUuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player target = Bukkit.getPlayer(UUID.fromString(targetUuid));
            if (target == null || !target.isOnline()) return;

            sendMessage("TARGET_RESPONSE", staffUuid, targetUuid, serverName);
            staffModeHandler.setTarget(staffUuid, targetUuid);
        });
    }

    private void handleCaptureReplay(String targetUuid, String targetName) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            UUID uuid = UUID.fromString(targetUuid);
            org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;

            if (replayService == null) {
                sendMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, "");
                return;
            }

            replayService.captureReplay(uuid, targetName)
                    .thenAccept(replayId -> sendMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, replayId != null ? replayId : ""))
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[bridge] CAPTURE_REPLAY failed for " + targetName + ": " + ex.getMessage());
                        sendMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, "");
                        return null;
                    });
        });
    }

    private class BridgeClientHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                if (!connected) {
                    if (buf.readableBytes() >= 1) {
                        byte status = buf.readByte();
                        if (status == 0x01) {
                            connected = true;
                            // Now that auth is done, add frame codecs for length-prefixed messages
                            ctx.pipeline().addBefore("handler", "frameDecoder",
                                    new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
                            ctx.pipeline().addBefore("handler", "framePrepender",
                                    new LengthFieldPrepender(4));
                            plugin.getLogger().info("[bridge] Connected to proxy at " + host + ":" + port);
                            sendBridgeHello();
                        } else {
                            plugin.getLogger().warning("[bridge] Proxy rejected authentication");
                            ctx.close();
                        }
                    }
                } else {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);

                    try {
                        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                        handleMessage(in);
                    } catch (IOException e) {
                        plugin.getLogger().warning("[bridge] Failed to read message from proxy: " + e.getMessage());
                    }
                }
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            boolean wasConnected = connected;
            connected = false;
            if (wasConnected) {
                plugin.getLogger().info("[bridge] Disconnected from proxy");
            }
            scheduleReconnect();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            plugin.getLogger().warning("[bridge] Connection error: " + cause.getMessage());
            ctx.close();
        }
    }
}
