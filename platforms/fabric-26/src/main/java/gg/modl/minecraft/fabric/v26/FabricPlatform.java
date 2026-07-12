package gg.modl.minecraft.fabric.v26;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.StaffAudience;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.StringUtil;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;
import gg.modl.minecraft.core.integration.mojang.WebPlayer;
import lombok.Setter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.fabric.FabricLamp;
import revxrsal.commands.fabric.actor.FabricCommandActor;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class FabricPlatform implements Platform {
    private final MinecraftServer server;
    private final Path dataFolder;
    private final PluginLogger logger;
    private final FabricTextSerializer textSerializer;
    private @Setter StaffAudience staffAudience;
    private @Setter String serverName = "fabric-server";

    public FabricPlatform(MinecraftServer server, Path dataFolder, PluginLogger logger) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.textSerializer = new FabricTextSerializer(server, logger);
    }

    @Override
    public PluginLogger getLogger() {
        return logger;
    }

    @Override
    public void broadcast(String string) {
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                textSerializer.sendLegacyMessage(player, string);
            }
        });
    }

    @Override
    public void staffBroadcast(String string) {
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isAuthenticatedStaff(player.getUUID())) {
                    textSerializer.sendLegacyMessage(player, string);
                }
            }
        });
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isAuthenticatedStaff(player.getUUID())) {
                    textSerializer.sendJsonToPlayer(player, jsonMessage);
                }
            }
        });
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                textSerializer.sendLegacyMessage(player, StringUtil.unescapeNewlines(message));
            }
        });
    }

    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                textSerializer.sendJsonToPlayer(player, jsonMessage);
            }
        });
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Lamp<FabricCommandActor> buildLamp(Consumer<Lamp.Builder<? extends CommandActor>> configurator) {
        Lamp.Builder<FabricCommandActor> builder = FabricLamp.builder();
        configurator.accept((Lamp.Builder) builder);
        return builder.build();
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return new AbstractPlayer(player.getUUID(), player.getName().getString(), player.getIpAddress(), true);
        }
        if (!queryMojang) {
            return null;
        }

        try {
            WebPlayer webPlayer = MojangProfiles.client().getSync(uuid);
            if (webPlayer == null) {
                return null;
            }
            return new AbstractPlayer(webPlayer.getUuid(), webPlayer.getName(), null, false);
        } catch (Exception e) {
            logger.debug("Mojang profile lookup failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        ServerPlayer player = server.getPlayerList().getPlayer(username);
        if (player != null) {
            return new AbstractPlayer(player.getUUID(), player.getName().getString(), player.getIpAddress(), true);
        }
        if (!queryMojang) {
            return null;
        }

        try {
            WebPlayer webPlayer = MojangProfiles.client().getSync(username);
            if (webPlayer == null) {
                return null;
            }
            return new AbstractPlayer(webPlayer.getUuid(), webPlayer.getName(), null, false);
        } catch (Exception e) {
            logger.debug("Mojang profile lookup failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return null;
        }
        try {
            Class<?> wrapperClass = Class.forName("dev.simplix.cirrus.fabric.wrapper.FabricPlayerWrapper");
            return (CirrusPlayerWrapper) wrapperClass.getConstructor(ServerPlayer.class).newInstance(player);
        } catch (Exception e) {
            logger.debug("Failed to build Cirrus player wrapper for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return server.getPlayerList().getPlayers().stream()
                .map(player -> new AbstractPlayer(
                        player.getUUID(),
                        player.getName().getString(),
                        player.getIpAddress(),
                        true
                ))
                .collect(Collectors.toList());
    }

    @Override
    public AbstractPlayer getPlayer(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return null;
        }
        return new AbstractPlayer(player.getUUID(), player.getName().getString(), player.getIpAddress(), true);
    }

    @Override
    public int getMaxPlayers() {
        return server.getPlayerList().getMaxPlayers();
    }

    @Override
    public String getServerVersion() {
        return server.getServerVersion();
    }

    @Override
    public String getPlatformType() {
        return "FABRIC";
    }

    @Override
    public void runOnMainThread(Runnable task) {
        server.execute(task);
    }

    @Override
    public void runOnGameThread(Runnable task) {
        server.execute(task);
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        if (player == null) return;
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player.getUuid());
        if (serverPlayer != null) {
            serverPlayer.connection.disconnect(textSerializer.parseLegacyText(StringUtil.unescapeNewlines(reason)));
        }
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public File getDataFolder() {
        return dataFolder.toFile();
    }

    @Override
    public DatabaseProvider createLiteBansDatabaseProvider() {
        return null;
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        CommandSourceStack source = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, command);
    }

    @Override
    public String getPlayerSkinTexture(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return null;
        }
        GameProfile profile = player.getGameProfile();
        Property property = profile.properties()
                .get("textures")
                .stream()
                .findFirst()
                .orElse(null);
        return property != null ? property.value() : null;
    }

    private boolean isAuthenticatedStaff(UUID uuid) {
        return staffAudience != null && staffAudience.includes(uuid);
    }
}
