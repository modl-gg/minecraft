package gg.modl.minecraft.spigot.bridge.query;

import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import gg.modl.minecraft.spigot.bridge.statwipe.StatWipeHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@RequiredArgsConstructor
public class BridgeQueryHandler extends ChannelInboundHandlerAdapter {
    private static final byte[] MAGIC = "modl".getBytes(StandardCharsets.US_ASCII);
    private static final int MIN_HANDSHAKE_BYTES = 6;
    private static final byte AUTH_FAILURE = 0x00;
    private static final byte AUTH_SUCCESS = 0x01;

    private final String secret;
    private final StatWipeHandler statWipeHandler;
    private final FreezeHandler freezeHandler;
    private final StaffModeHandler staffModeHandler;
    private final BridgeQueryServer queryServer;
    private final JavaPlugin plugin;

    @Setter private ReplayService replayService;
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
        if (buf.readableBytes() < MIN_HANDSHAKE_BYTES) {
            plugin.getLogger().warning("Query handshake too short from " + ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        byte[] magic = new byte[MAGIC.length];
        buf.readBytes(magic);

        if (!Arrays.equals(magic, MAGIC)) {
            plugin.getLogger().warning("Query handshake failed: invalid magic bytes from " + ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        try {
            DataInputStream in = readBuf(buf);
            String clientSecret = in.readUTF();

            if (!secret.equals(clientSecret)) {
                plugin.getLogger().warning("Query handshake failed: invalid secret from " + ctx.channel().remoteAddress());
                sendResponse(ctx, AUTH_FAILURE);
                ctx.close();
                return;
            }

            authenticated = true;
            queryServer.addAuthenticatedChannel(ctx.channel());
            sendResponse(ctx, AUTH_SUCCESS);
            plugin.getLogger().info("Query client authenticated from " + ctx.channel().remoteAddress());

            sendBridgeHello(ctx);
        } catch (IOException e) {
            plugin.getLogger().warning("Query handshake error: " + e.getMessage());
            ctx.close();
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, byte status) {
        ByteBuf response = ctx.alloc().buffer(1);
        response.writeByte(status);
        ctx.writeAndFlush(response);
    }

    private void sendBridgeHello(ChannelHandlerContext ctx) {
        byte[] data = queryServer.buildMessage("BRIDGE_HELLO", Bukkit.getServer().getName());
        if (data != null) {
            ByteBuf buf = ctx.alloc().buffer(data.length);
            buf.writeBytes(data);
            ctx.writeAndFlush(buf);
            plugin.getLogger().info("Sent BRIDGE_HELLO via TCP query");
        }
    }

    private void handleMessage(ChannelHandlerContext ctx, ByteBuf buf) {
        try {
            DataInputStream in = readBuf(buf);
            String action = in.readUTF();

            switch (action) {
                case "STAT_WIPE" -> handleStatWipe(in);
                case "FREEZE_PLAYER" -> {
                    String targetUuid = in.readUTF();
                    String staffUuid = in.readUTF();
                    broadcastAndRun(ctx, action, () -> freezeHandler.freeze(targetUuid, staffUuid), targetUuid, staffUuid);
                }
                case "UNFREEZE_PLAYER" -> {
                    String targetUuid = in.readUTF();
                    broadcastAndRun(ctx, action, () -> freezeHandler.unfreeze(targetUuid), targetUuid);
                }
                case "FREEZE_LOGOUT" -> {
                    String playerUuid = in.readUTF();
                    String playerName = in.readUTF();
                    broadcastMessage(ctx, action, playerUuid, playerName);
                }
                case "STAFF_MODE_ENTER" -> {
                    String staffUuid = in.readUTF();
                    String staffName = in.readUTF();
                    broadcastAndRun(ctx, action, () -> staffModeHandler.enterStaffMode(staffUuid), staffUuid, staffName);
                }
                case "STAFF_MODE_EXIT" -> {
                    String staffUuid = in.readUTF();
                    String staffName = in.readUTF();
                    broadcastAndRun(ctx, action, () -> staffModeHandler.exitStaffMode(staffUuid), staffUuid, staffName);
                }
                case "VANISH_ENTER" -> {
                    String staffUuid = in.readUTF();
                    String staffName = in.readUTF();
                    broadcastAndRun(ctx, action, () -> staffModeHandler.vanishFromBridge(staffUuid), staffUuid, staffName);
                }
                case "VANISH_EXIT" -> {
                    String staffUuid = in.readUTF();
                    String staffName = in.readUTF();
                    broadcastAndRun(ctx, action, () -> staffModeHandler.unvanishFromBridge(staffUuid), staffUuid, staffName);
                }
                case "TARGET_REQUEST" -> {
                    String staffUuid = in.readUTF();
                    String targetUuid = in.readUTF();
                    handleTargetRequest(ctx, staffUuid, targetUuid);
                }
                case "CONNECT_SERVER" -> {
                    String playerUuid = in.readUTF();
                    String serverName = in.readUTF();
                    broadcastMessage(ctx, action, playerUuid, serverName);
                }
                case "CAPTURE_REPLAY" -> {
                    String targetUuid = in.readUTF();
                    String targetName = in.readUTF();
                    handleCaptureReplay(ctx, targetUuid, targetName);
                }
                default -> plugin.getLogger().info("Received unknown query action: " + action);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read query message: " + e.getMessage());
        }
    }

    private void handleStatWipe(DataInputStream in) throws IOException {
        String username = in.readUTF();
        String uuid = in.readUTF();
        String punishmentId = in.readUTF();

        plugin.getLogger().info("Processing stat-wipe for " + username +
                " (punishment: " + punishmentId + ") via TCP query");

        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = statWipeHandler.execute(username, uuid, punishmentId);
            plugin.getLogger().info("Stat-wipe for " + username + " " +
                    (success ? "succeeded" : "failed") +
                    " (punishment: " + punishmentId + ")");
        });
    }

    private void broadcastAndRun(ChannelHandlerContext ctx, String action, Runnable localAction, String... args) {
        broadcastMessage(ctx, action, args);
        localAction.run();
    }

    private void broadcastMessage(ChannelHandlerContext ctx, String action, String... args) {
        byte[] data = queryServer.buildMessage(action, args);
        if (data != null) {
            queryServer.broadcastMessage(data, ctx.channel());
        }
    }

    private void handleTargetRequest(ChannelHandlerContext ctx, String staffUuid, String targetUuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player target = Bukkit.getPlayer(UUID.fromString(targetUuid));
            if (target == null || !target.isOnline()) return;

            byte[] responseData = queryServer.buildMessage("TARGET_RESPONSE", staffUuid, targetUuid, Bukkit.getServer().getName());
            if (responseData != null) {
                ByteBuf buf = ctx.alloc().buffer(4 + responseData.length);
                buf.writeInt(responseData.length);
                buf.writeBytes(responseData);
                ctx.writeAndFlush(buf);
            }

            staffModeHandler.setTarget(staffUuid, targetUuid);
        });
    }

    private void handleCaptureReplay(ChannelHandlerContext ctx, String targetUuid, String targetName) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            UUID uuid = UUID.fromString(targetUuid);
            org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return; // not on this server, ignore

            if (replayService == null) {
                sendCaptureResponse(ctx, targetUuid, "");
                return;
            }

            replayService.captureReplay(uuid, targetName)
                    .thenAccept(replayId -> sendCaptureResponse(ctx, targetUuid, replayId != null ? replayId : ""))
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("CAPTURE_REPLAY failed for " + targetName + ": " + ex.getMessage());
                        sendCaptureResponse(ctx, targetUuid, "");
                        return null;
                    });
        });
    }

    private void sendCaptureResponse(ChannelHandlerContext ctx, String targetUuid, String replayId) {
        byte[] data = queryServer.buildMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, replayId);
        if (data != null && ctx.channel().isActive()) {
            ByteBuf buf = ctx.alloc().buffer(data.length);
            buf.writeBytes(data);
            ctx.writeAndFlush(buf);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        plugin.getLogger().warning("Query connection error: " + cause.getMessage());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (authenticated) {
            queryServer.unregisterServer(ctx.channel());
            plugin.getLogger().info("Query client disconnected: " + ctx.channel().remoteAddress());
        }
    }

    private static DataInputStream readBuf(ByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new DataInputStream(new ByteArrayInputStream(data));
    }
}
