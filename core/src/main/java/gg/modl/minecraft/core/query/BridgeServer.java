package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import gg.modl.minecraft.core.bridge.protocol.BridgeProtocol;
import gg.modl.minecraft.core.service.sync.StatWipeExecutor;
import gg.modl.minecraft.core.util.PluginLogger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BridgeServer implements StatWipeExecutor, BridgeBroadcaster {
    private static final byte AUTH_SUCCESS = 0x01;
    private static final byte AUTH_FAILURE = 0x00;

    private final int port;
    private final String secret;
    private final BridgeMessageDispatcher dispatcher;
    private final PluginLogger logger;
    private final String panelUrl;

    private final Map<String, Channel> connectedServers = new ConcurrentHashMap<>();
    private final Set<Channel> authenticatedChannels = ConcurrentHashMap.newKeySet();
    private final Queue<byte[]> pendingMessages = new ConcurrentLinkedQueue<>();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public BridgeServer(int port, String secret, BridgeMessageDispatcher dispatcher, PluginLogger logger) {
        this(port, secret, dispatcher, logger, "");
    }

    public BridgeServer(int port, String secret, BridgeMessageDispatcher dispatcher, PluginLogger logger, String panelUrl) {
        this.port = port;
        this.secret = secret;
        this.dispatcher = dispatcher;
        this.logger = logger;
        this.panelUrl = panelUrl != null ? panelUrl : "";
    }

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast("handler", new BridgeServerHandler());
                    }
                });

        try {
            serverChannel = bootstrap.bind(port).sync().channel();
            logger.info("[bridge] Server started on port " + port + ", waiting for backend connections");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.severe("[bridge] Interrupted while starting bridge server on port " + port);
        } catch (Exception e) {
            logger.severe("[bridge] Failed to start bridge server on port " + port + ": " + e.getMessage());
        }
    }

    @Override
    public int sendToAllBridges(String action, String... args) {
        byte[] data = buildMessage(action, args);
        if (data == null) return 0;

        if (authenticatedChannels.isEmpty()) {
            if (isImmediateOnlyAction(action)) {
                logger.warning("[bridge] No connected backends for " + action + ", not queued");
                return 0;
            }
            pendingMessages.add(data);
            logger.info("[bridge] No connected backends, queued " + action + " for delivery on connect");
            return 0;
        }

        int sent = 0;
        for (Channel ch : authenticatedChannels) {
            if (ch.isActive()) {
                sendRaw(ch, data);
                sent++;
            }
        }

        if (sent == 0) {
            if (isImmediateOnlyAction(action)) {
                logger.warning("[bridge] No active backends for " + action + ", not queued");
                return 0;
            }
            pendingMessages.add(data);
            logger.info("[bridge] No active backends, queued " + action + " for delivery on reconnect");
        }

        return sent;
    }

    private boolean isImmediateOnlyAction(String action) {
        return BridgeAction.CAPTURE_REPLAY.wire().equals(action);
    }

    @Override
    public boolean hasConnectedClients() {
        return authenticatedChannels.stream().anyMatch(Channel::isActive);
    }

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        byte[] data = buildMessage(BridgeAction.STAT_WIPE.wire(), username, uuid, punishmentId);
        if (data == null) {
            callback.onComplete(false, null);
            return;
        }

        Channel delivered = null;
        for (Channel ch : authenticatedChannels) {
            if (!ch.isActive()) continue;
            sendRaw(ch, data);
            if (delivered == null) delivered = ch;
            logger.info("[bridge] Sent stat wipe to " + describeChannel(ch) + " for " + username);
        }

        if (delivered != null) {
            callback.onComplete(true, describeChannel(delivered));
        } else {
            logger.warning("[bridge] No connected backends for stat wipe of " + username);
            callback.onComplete(false, null);
        }
    }

    private String describeChannel(Channel ch) {
        for (Map.Entry<String, Channel> e : connectedServers.entrySet()) {
            if (e.getValue().equals(ch)) return e.getKey();
        }
        return String.valueOf(ch.remoteAddress());
    }

    private void rebroadcastToOtherBackends(byte[] data, Channel origin) {
        for (Channel ch : authenticatedChannels) {
            if (ch.isActive() && !ch.equals(origin)) {
                sendRaw(ch, data);
            }
        }
    }

    private void flushPendingMessages() {
        byte[] data;
        int flushed = 0;
        while ((data = pendingMessages.poll()) != null) {
            for (Channel ch : authenticatedChannels) {
                if (ch.isActive()) {
                    sendRaw(ch, data);
                }
            }
            flushed++;
        }
        if (flushed > 0) {
            logger.info("[bridge] Flushed " + flushed + " pending message(s) to newly connected backend");
        }
    }

    byte[] buildMessage(String action, String... args) {
        try {
            return BridgeProtocol.encode(action, args);
        } catch (IOException e) {
            logger.warning("[bridge] Failed to build message for " + action + ": " + e.getMessage());
            return null;
        }
    }

    private void sendRaw(Channel channel, byte[] data) {
        ByteBuf buf = channel.alloc().buffer(data.length);
        buf.writeBytes(data);
        channel.writeAndFlush(buf);
    }

    private void sendPanelUrl(Channel channel) {
        if (panelUrl.isEmpty()) return;
        byte[] data = buildMessage(BridgeAction.PANEL_URL.wire(), panelUrl);
        if (data != null) {
            sendRaw(channel, data);
        }
    }

    public void shutdown() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }

    private class BridgeServerHandler extends ChannelInboundHandlerAdapter {
        private boolean authenticated = false;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                if (!authenticated) {
                    handleHandshake(ctx, buf);
                } else {
                    handleMessage(ctx, buf);
                }
            } finally {
                buf.release();
            }
        }

        private void handleHandshake(ChannelHandlerContext ctx, ByteBuf buf) {
            if (buf.readableBytes() < BridgeProtocol.magicLength() + 2) {
                logger.warning("[bridge] Handshake too short from " + ctx.channel().remoteAddress());
                ctx.close();
                return;
            }

            byte[] magic = new byte[BridgeProtocol.magicLength()];
            buf.readBytes(magic);

            if (!BridgeProtocol.matchesMagic(magic)) {
                logger.warning("[bridge] Invalid magic bytes from " + ctx.channel().remoteAddress());
                ctx.close();
                return;
            }

            try {
                byte[] remaining = new byte[buf.readableBytes()];
                buf.readBytes(remaining);
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(remaining));
                String clientSecret = in.readUTF();

                if (!secret.equals(clientSecret)) {
                    logger.warning("[bridge] Invalid secret from " + ctx.channel().remoteAddress());
                    sendResponse(ctx, AUTH_FAILURE);
                    ctx.close();
                    return;
                }

                authenticated = true;
                authenticatedChannels.add(ctx.channel());
                sendResponse(ctx, AUTH_SUCCESS);
                installFrameCodecs(ctx);
                logger.info("[bridge] Backend authenticated from " + ctx.channel().remoteAddress());
                sendPanelUrl(ctx.channel());
                flushPendingMessages();
            } catch (IOException e) {
                logger.warning("[bridge] Handshake error: " + e.getMessage());
                ctx.close();
            }
        }

        private void installFrameCodecs(ChannelHandlerContext ctx) {
            ctx.pipeline().addBefore("handler", "frameDecoder", BridgeProtocol.newFrameDecoder());
            ctx.pipeline().addBefore("handler", "framePrepender", BridgeProtocol.newFramePrepender());
        }

        private void sendResponse(ChannelHandlerContext ctx, byte status) {
            ByteBuf response = ctx.alloc().buffer(1);
            response.writeByte(status);
            ctx.writeAndFlush(response);
        }

        private void handleMessage(ChannelHandlerContext ctx, ByteBuf buf) {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);

            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                String action = in.readUTF();

                if (BridgeAction.BRIDGE_HELLO.wire().equals(action)) {
                    registerBackend(ctx, in.readUTF());
                    return;
                }

                dispatchLocally(action, data);
                rebroadcastToOtherBackends(data, ctx.channel());
            } catch (IOException e) {
                logger.warning("[bridge] Failed to read message: " + e.getMessage());
            }
        }

        private void registerBackend(ChannelHandlerContext ctx, String serverName) {
            Channel previous = connectedServers.put(serverName, ctx.channel());
            if (previous != null && previous != ctx.channel()) {
                logger.warning("[bridge] Backend name '" + serverName + "' re-registered on a new channel ("
                        + ctx.channel().remoteAddress() + "); replacing previous mapping (" + previous.remoteAddress() + ")");
            }
            logger.info("[bridge] Backend registered: " + serverName + " (" + ctx.channel().remoteAddress() + ")");
        }

        private void dispatchLocally(String action, byte[] data) throws IOException {
            DataInputStream payload = new DataInputStream(new ByteArrayInputStream(data));
            payload.readUTF();
            dispatcher.dispatch(action, payload);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            String serverName = null;
            for (Map.Entry<String, Channel> entry : connectedServers.entrySet()) {
                if (entry.getValue().equals(ctx.channel())) {
                    serverName = entry.getKey();
                    break;
                }
            }
            if (serverName != null) {
                connectedServers.remove(serverName);
                logger.info("[bridge] Backend disconnected: " + serverName);
            }
            authenticatedChannels.remove(ctx.channel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warning("[bridge] Connection error: "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            ctx.close();
        }
    }
}
