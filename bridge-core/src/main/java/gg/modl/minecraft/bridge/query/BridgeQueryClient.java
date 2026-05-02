package gg.modl.minecraft.bridge.query;

import gg.modl.minecraft.bridge.BridgeScheduler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class BridgeQueryClient {
    private static final byte[] MAGIC = "modl".getBytes(StandardCharsets.US_ASCII);
    private static final long[] BACKOFF_DELAYS = {5, 10, 20, 40, 60};
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int MAX_FRAME_LENGTH = 65536;

    private final String host;
    private final int port;
    private final String secret;
    private final String serverName;
    private final Logger logger;
    private final BridgeScheduler scheduler;
    @Setter private BridgeMessageHandler messageHandler;

    private final EventLoopGroup group;
    private Channel channel;
    private volatile boolean connected = false;
    private volatile boolean shuttingDown = false;
    private int reconnectAttempt = 0;
    private volatile String cachedPanelUrl = "";

    public BridgeQueryClient(String host, int port, String secret, String serverName,
                             Logger logger, BridgeScheduler scheduler,
                             BridgeMessageHandler messageHandler) {
        this.host = host;
        this.port = port;
        this.secret = secret;
        this.serverName = serverName;
        this.logger = logger;
        this.scheduler = scheduler;
        this.messageHandler = messageHandler;
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
                logger.warning("[bridge] Failed to connect to proxy at " + host + ":" + port + ": " + cause);
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
            logger.warning("[bridge] Failed to send handshake: " + e.getMessage());
            channel.close();
        }
    }

    private void sendBridgeHello() {
        sendMessage("BRIDGE_HELLO", serverName);
        logger.info("[bridge] Sent BRIDGE_HELLO to proxy as '" + serverName + "'");
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
            logger.warning("[bridge] Failed to send " + action + ": " + e.getMessage());
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

    public String getCachedPanelUrl() {
        return cachedPanelUrl;
    }

    private void handleMessage(DataInputStream in) throws IOException {
        String action = in.readUTF();

        if ("PANEL_URL".equals(action)) {
            cachedPanelUrl = in.readUTF();
            if (messageHandler != null) messageHandler.onPanelUrl(cachedPanelUrl);
            logger.info("[bridge] Received panel URL from proxy");
        } else if ("STAT_WIPE".equals(action)) {
            String username = in.readUTF();
            String uuid = in.readUTF();
            String punishmentId = in.readUTF();
            if (messageHandler != null) messageHandler.onStatWipe(username, uuid, punishmentId);
        } else if ("FREEZE_PLAYER".equals(action)) {
            String targetUuid = in.readUTF();
            String staffUuid = in.readUTF();
            if (messageHandler != null) messageHandler.onFreeze(targetUuid, staffUuid);
        } else if ("UNFREEZE_PLAYER".equals(action)) {
            String targetUuid = in.readUTF();
            if (messageHandler != null) messageHandler.onUnfreeze(targetUuid);
        } else if ("STAFF_MODE_ENTER".equals(action)) {
            String staffUuid = in.readUTF();
            String staffName = in.readUTF();
            if (messageHandler != null) messageHandler.onStaffModeEnter(staffUuid, staffName);
        } else if ("STAFF_MODE_EXIT".equals(action)) {
            String staffUuid = in.readUTF();
            String staffName = in.readUTF();
            if (messageHandler != null) messageHandler.onStaffModeExit(staffUuid, staffName);
        } else if ("VANISH_ENTER".equals(action)) {
            String staffUuid = in.readUTF();
            String staffName = in.readUTF();
            if (messageHandler != null) messageHandler.onVanishEnter(staffUuid, staffName);
        } else if ("VANISH_EXIT".equals(action)) {
            String staffUuid = in.readUTF();
            String staffName = in.readUTF();
            if (messageHandler != null) messageHandler.onVanishExit(staffUuid, staffName);
        } else if ("TARGET_REQUEST".equals(action)) {
            String staffUuid = in.readUTF();
            String targetUuid = in.readUTF();
            if (messageHandler != null) messageHandler.onTargetRequest(staffUuid, targetUuid);
        } else if ("CONNECT_SERVER".equals(action)) {
            // no-op on backend
        } else if ("CAPTURE_REPLAY".equals(action)) {
            String targetUuid = in.readUTF();
            String targetName = in.readUTF();
            if (messageHandler != null) messageHandler.onCaptureReplay(targetUuid, targetName);
        } else {
            logger.info("[bridge] Unknown action from proxy: " + action);
        }
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
                            ctx.pipeline().addBefore("handler", "frameDecoder",
                                    new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
                            ctx.pipeline().addBefore("handler", "framePrepender",
                                    new LengthFieldPrepender(4));
                            logger.info("[bridge] Connected to proxy at " + host + ":" + port);
                            sendBridgeHello();
                        } else {
                            logger.warning("[bridge] Proxy rejected authentication");
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
                        logger.warning("[bridge] Failed to read message from proxy: " + e.getMessage());
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
                logger.info("[bridge] Disconnected from proxy");
            }
            scheduleReconnect();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warning("[bridge] Connection error: " + cause.getMessage());
            ctx.close();
        }
    }
}
