package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.service.sync.StatWipeExecutor;
import gg.modl.minecraft.core.util.PluginLogger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BridgeServer implements StatWipeExecutor, BridgeBroadcaster {
    private static final byte[] MAGIC = "modl".getBytes(StandardCharsets.US_ASCII);
    private static final String ACTION_CAPTURE_REPLAY = "CAPTURE_REPLAY";
    private static final int MAX_FRAME_LENGTH = 65536;
    private static final int LENGTH_FIELD_LENGTH = 4;
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
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // No frame codecs yet — handshake uses raw bytes.
                        // Frame codecs are added after auth succeeds.
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
        return ACTION_CAPTURE_REPLAY.equals(action);
    }

    @Override
    public boolean hasConnectedClients() {
        return authenticatedChannels.stream().anyMatch(Channel::isActive);
    }

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        String firstServerName = null;

        for (Map.Entry<String, Channel> entry : connectedServers.entrySet()) {
            Channel ch = entry.getValue();
            if (!ch.isActive()) continue;

            byte[] data = buildMessage("STAT_WIPE", username, uuid, punishmentId);
            if (data != null) {
                sendRaw(ch, data);
                if (firstServerName == null) firstServerName = entry.getKey();
                logger.info("[bridge] Sent stat wipe to " + entry.getKey() + " for " + username);
            }
        }

        if (firstServerName != null) {
            callback.onComplete(true, firstServerName);
        } else {
            logger.warning("[bridge] No connected backends for stat wipe of " + username);
        }
    }

    private void broadcastToOthers(byte[] data, Channel except) {
        for (Channel ch : authenticatedChannels) {
            if (ch.isActive() && !ch.equals(except)) {
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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(action);
            for (String arg : args) {
                dos.writeUTF(arg != null ? arg : "");
            }
            dos.flush();
            return baos.toByteArray();
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
        byte[] data = buildMessage("PANEL_URL", panelUrl);
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
            if (buf.readableBytes() < MAGIC.length + 2) {
                logger.warning("[bridge] Handshake too short from " + ctx.channel().remoteAddress());
                ctx.close();
                return;
            }

            byte[] magic = new byte[MAGIC.length];
            buf.readBytes(magic);

            if (!Arrays.equals(magic, MAGIC)) {
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
                // Now that auth is done, add frame codecs for length-prefixed messages
                ctx.pipeline().addBefore("handler", "frameDecoder",
                        new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_LENGTH, 0, LENGTH_FIELD_LENGTH));
                ctx.pipeline().addBefore("handler", "framePrepender",
                        new LengthFieldPrepender(LENGTH_FIELD_LENGTH));
                logger.info("[bridge] Backend authenticated from " + ctx.channel().remoteAddress());
                sendPanelUrl(ctx.channel());
                flushPendingMessages();
            } catch (IOException e) {
                logger.warning("[bridge] Handshake error: " + e.getMessage());
                ctx.close();
            }
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

                if ("BRIDGE_HELLO".equals(action)) {
                    String serverName = in.readUTF();
                    connectedServers.put(serverName, ctx.channel());
                    logger.info("[bridge] Backend registered: " + serverName + " (" + ctx.channel().remoteAddress() + ")");
                    return;
                }

                // Dispatch locally on proxy
                DataInputStream dispatchIn = new DataInputStream(new ByteArrayInputStream(data));
                dispatchIn.readUTF(); // skip action
                dispatcher.dispatch(action, dispatchIn);

                // Re-broadcast to all other connected backends
                broadcastToOthers(data, ctx.channel());
            } catch (IOException e) {
                logger.warning("[bridge] Failed to read message: " + e.getMessage());
            }
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
            logger.warning("[bridge] Connection error: " + cause.getMessage());
            ctx.close();
        }
    }
}
