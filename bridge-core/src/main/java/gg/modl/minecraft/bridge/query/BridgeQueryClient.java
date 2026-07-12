package gg.modl.minecraft.bridge.query;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import gg.modl.minecraft.core.bridge.protocol.BridgeProtocol;
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
import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class BridgeQueryClient {
    private static final long[] BACKOFF_DELAYS = {5, 10, 20, 40, 60};
    private static final int CONNECT_TIMEOUT_MS = 5000;

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
                .option(ChannelOption.TCP_NODELAY, true)
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
            BridgeProtocol.writeMagic(bytes);
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
        sendMessage(BridgeAction.BRIDGE_HELLO.wire(), serverName);
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
            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(BridgeProtocol.encode(action, args));
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
        BridgeAction bridgeAction = BridgeAction.fromWire(action);
        if (bridgeAction == null) {
            logger.info("[bridge] Unknown action from proxy: " + action);
            return;
        }

        switch (bridgeAction) {
            case PANEL_URL: {
                cachedPanelUrl = in.readUTF();
                if (messageHandler != null) messageHandler.onPanelUrl(cachedPanelUrl);
                logger.info("[bridge] Received panel URL from proxy");
                break;
            }
            case STAT_WIPE: {
                String username = in.readUTF();
                String uuid = in.readUTF();
                String punishmentId = in.readUTF();
                if (messageHandler != null) messageHandler.onStatWipe(username, uuid, punishmentId);
                break;
            }
            case FREEZE_PLAYER: {
                String targetUuid = in.readUTF();
                String staffUuid = in.readUTF();
                if (messageHandler != null) messageHandler.onFreeze(targetUuid, staffUuid);
                break;
            }
            case UNFREEZE_PLAYER: {
                String targetUuid = in.readUTF();
                if (messageHandler != null) messageHandler.onUnfreeze(targetUuid);
                break;
            }
            case STAFF_MODE_ENTER: {
                String staffUuid = in.readUTF();
                String staffName = in.readUTF();
                if (messageHandler != null) messageHandler.onStaffModeEnter(staffUuid, staffName);
                break;
            }
            case STAFF_MODE_EXIT: {
                String staffUuid = in.readUTF();
                String staffName = in.readUTF();
                if (messageHandler != null) messageHandler.onStaffModeExit(staffUuid, staffName);
                break;
            }
            case VANISH_ENTER: {
                String staffUuid = in.readUTF();
                String staffName = in.readUTF();
                if (messageHandler != null) messageHandler.onVanishEnter(staffUuid, staffName);
                break;
            }
            case VANISH_EXIT: {
                String staffUuid = in.readUTF();
                String staffName = in.readUTF();
                if (messageHandler != null) messageHandler.onVanishExit(staffUuid, staffName);
                break;
            }
            case TARGET_REQUEST: {
                String staffUuid = in.readUTF();
                String targetUuid = in.readUTF();
                if (messageHandler != null) messageHandler.onTargetRequest(staffUuid, targetUuid);
                break;
            }
            case CONNECT_SERVER:
                break;
            case CAPTURE_REPLAY: {
                String targetUuid = in.readUTF();
                String targetName = in.readUTF();
                if (messageHandler != null) messageHandler.onCaptureReplay(targetUuid, targetName);
                break;
            }
            default:
                logger.info("[bridge] Unhandled action from proxy: " + action);
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
                            ctx.pipeline().addBefore("handler", "frameDecoder", BridgeProtocol.newFrameDecoder());
                            ctx.pipeline().addBefore("handler", "framePrepender", BridgeProtocol.newFramePrepender());
                            logger.info("[bridge] Connected to proxy at " + host + ":" + port);
                            sendBridgeHello();
                            refeedRemainderThroughNewFrameDecoder(ctx, buf);
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

        private void refeedRemainderThroughNewFrameDecoder(ChannelHandlerContext ctx, ByteBuf buf) {
            if (buf.isReadable()) {
                ByteBuf remaining = buf.readRetainedSlice(buf.readableBytes());
                ctx.pipeline().fireChannelRead(remaining);
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
