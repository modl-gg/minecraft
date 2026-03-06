package gg.modl.minecraft.core.query;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class QueryClient {
    private static final byte[] MAGIC = "modl".getBytes(StandardCharsets.US_ASCII);
    private static final long[] BACKOFF_DELAYS = {5, 10, 20, 40, 60};
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int MAX_FRAME_LENGTH = 65536;

    private final String serverName;
    private final String host;
    private final int port;
    private final String secret;
    private final Logger logger;
    private final BiConsumer<String, QueryMessage> messageHandler;
    private final EventLoopGroup group;

    private Channel channel;
    private volatile boolean connected = false;
    private volatile boolean shuttingDown = false;
    private int reconnectAttempt = 0;

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
                        ch.pipeline().addLast(
                                new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4),
                                new LengthFieldPrepender(4),
                                new QueryClientHandler()
                        );
                    }
                });

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                reconnectAttempt = 0;
                sendHandshake();
            } else scheduleReconnect();
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
            logger.warning("[modl] Failed to send handshake to bridge on " + serverName + ": " + e.getMessage());
            channel.close();
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown) return;

        int delayIndex = Math.min(reconnectAttempt, BACKOFF_DELAYS.length - 1);
        long delay = BACKOFF_DELAYS[delayIndex];
        reconnectAttempt++;

        group.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    public void sendMessage(byte[] data) {
        if (!connected || channel == null || !channel.isActive()) return;

        ByteBuf buf = channel.alloc().buffer();
        buf.writeBytes(data);
        channel.writeAndFlush(buf);
    }

    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }

    public String getServerName() {
        return serverName;
    }

    void sendTypedMessage(String action, String... args) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(action);
            for (String arg : args) dos.writeUTF(arg);
            dos.flush();
            sendMessage(baos.toByteArray());
        } catch (IOException e) {
            logger.warning("[QueryClient] Failed to send " + action + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        shuttingDown = true;
        connected = false;
        if (channel != null) channel.close();
        // EventLoopGroup is shared and owned by QueryStatWipeExecutor
    }

    private class QueryClientHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                if (!connected) {
                    if (buf.readableBytes() >= 1) {
                        byte status = buf.readByte();
                        if (status == 0x01) {
                            connected = true;
                            logger.info("[modl] Connected to bridge on " + serverName + " (TCP query)");
                        } else {
                            logger.warning("[modl] Bridge on " + serverName + " rejected authentication");
                            ctx.close();
                        }
                    }
                } else {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);

                    try {
                        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                        String action = in.readUTF();
                        messageHandler.accept(serverName, new QueryMessage(action, in));
                    } catch (IOException e) {
                        logger.warning("[modl] Failed to read query message from bridge on " +
                                serverName + ": " + e.getMessage());
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
            if (wasConnected) logger.info("[modl] Disconnected from bridge on " + serverName);
            scheduleReconnect();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warning("[modl] Query connection error with bridge on " + serverName + ": " + cause.getMessage());
            ctx.close();
        }
    }

    @lombok.AllArgsConstructor
    @lombok.Getter
    public static class QueryMessage {
        private final String action;
        private final DataInputStream data;
    }
}
