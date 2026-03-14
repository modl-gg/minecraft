package gg.modl.minecraft.spigot.bridge.query;

import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import gg.modl.minecraft.spigot.bridge.statwipe.StatWipeHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class BridgeQueryServer {
    private static final int MAX_FRAME_LENGTH = 65536;
    private static final int LENGTH_FIELD_LENGTH = 4;

    private final int port;
    private final String secret;
    private final StatWipeHandler statWipeHandler;
    private final FreezeHandler freezeHandler;
    private final StaffModeHandler staffModeHandler;
    private final JavaPlugin plugin;
    @Setter private ReplayService replayService;
    private final Map<String, Channel> connectedServers = new ConcurrentHashMap<>();
    private final Set<Channel> authenticatedChannels = ConcurrentHashMap.newKeySet();
    private final Queue<byte[]> pendingMessages = new ConcurrentLinkedQueue<>();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public void addAuthenticatedChannel(Channel channel) {
        authenticatedChannels.add(channel);
        flushPendingMessages();
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
            plugin.getLogger().info("Flushed " + flushed + " pending message(s) to newly connected client");
        }
    }

    public void unregisterServer(Channel channel) {
        connectedServers.entrySet().removeIf(entry -> entry.getValue().equals(channel));
        authenticatedChannels.remove(channel);
    }

    public void broadcastMessage(byte[] data, Channel except) {
        connectedServers.values().stream()
                .filter(ch -> ch.isActive() && !ch.equals(except))
                .forEach(ch -> sendRaw(ch, data));
    }

    public boolean sendToAllClients(String action, String... args) {
        byte[] data = buildMessage(action, args);
        if (data == null) return false;

        if (authenticatedChannels.isEmpty()) {
            pendingMessages.add(data);
            plugin.getLogger().info("No connected clients, queued " + action + " for delivery on connect");
            return false;
        }

        boolean sent = false;
        for (Channel ch : authenticatedChannels) {
            if (ch.isActive()) {
                sendRaw(ch, data);
                sent = true;
            }
        }

        if (!sent) {
            pendingMessages.add(data);
            plugin.getLogger().info("No active clients, queued " + action + " for delivery on reconnect");
        }
        return sent;
    }

    public boolean hasConnectedClients() {
        return authenticatedChannels.stream().anyMatch(Channel::isActive);
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
                        BridgeQueryHandler handler = new BridgeQueryHandler(secret, statWipeHandler, freezeHandler,
                                staffModeHandler, BridgeQueryServer.this, plugin);
                        handler.setReplayService(replayService);
                        ch.pipeline().addLast(
                                new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_LENGTH, 0, LENGTH_FIELD_LENGTH),
                                new LengthFieldPrepender(LENGTH_FIELD_LENGTH),
                                handler
                        );
                    }
                });

        try {
            serverChannel = bootstrap.bind(port).sync().channel();
            plugin.getLogger().info("Query server started on port " + port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Interrupted while starting query server on port " + port);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start query server on port " + port + ": " + e.getMessage());
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
            plugin.getLogger().warning("Failed to build message for " + action + ": " + e.getMessage());
            return null;
        }
    }

    private void sendRaw(Channel channel, byte[] data) {
        ByteBuf buf = channel.alloc().buffer(data.length);
        buf.writeBytes(data);
        channel.writeAndFlush(buf);
    }

    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
