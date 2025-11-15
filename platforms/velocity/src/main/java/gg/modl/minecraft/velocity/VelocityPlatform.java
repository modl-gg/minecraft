package gg.modl.minecraft.velocity;

import co.aikar.commands.CommandManager;
import co.aikar.commands.VelocityCommandManager;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.service.database.LiteBansDatabaseProvider;
import gg.modl.minecraft.core.util.WebPlayer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
//import dev.simplix.cirrus.velocity.wrapper.VelocityPlayerWrapper;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class VelocityPlatform implements Platform {
    private final ProxyServer server;
    private final VelocityCommandManager commandManager;
    private final Logger logger;
    private final File dataFolder;

    private static Component get(String string) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(string);
    }

    @Override
    public void broadcast(String string) {
        server.getAllPlayers().forEach(player -> player.sendMessage(get(string)));
    }

    @Override
    public void staffBroadcast(String string) {
        server.getAllPlayers().forEach(player -> {
            if (player.hasPermission("modl.staff")) player.sendMessage(get(string));
        });
    }

    @Override
    public void disconnect(UUID uuid, String message) {
        server.getPlayer(uuid).ifPresent(player -> player.disconnect(get(message)));
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        // Handle both escaped newlines and literal \n sequences
        String formattedMessage = message.replace("\\n", "\n").replace("\\\\n", "\n");
        server.getPlayer(uuid).orElseThrow().sendMessage(get(formattedMessage));
    }
    
    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        server.getPlayer(uuid).ifPresent(player -> {
            try {
                net.kyori.adventure.text.Component component = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(jsonMessage);
                player.sendMessage(component);
            } catch (Exception e) {
                e.printStackTrace();
                player.sendMessage(net.kyori.adventure.text.Component.text("Notification: " + jsonMessage));
            }
        });
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayer(uuid).isPresent();
    }

    @Override
    public CommandManager<?, ?, ?, ?, ?, ?> getCommandManager() {
        return commandManager;
    }

    private Player getOnlinePlayer(String username) {
        return server.getPlayer(username).isPresent() ? server.getPlayer(username).get() : null;
    }

    private Player getOnlinePlayer(UUID uuid) {
        return server.getPlayer(uuid).isPresent() ? server.getPlayer(uuid).get() : null;
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        Player player = getOnlinePlayer(uuid);

        if (player != null) {
            return new AbstractPlayer(player.getUniqueId(), player.getUsername(), true,
                player.getRemoteAddress().getAddress().getHostAddress());
        }
        if (!queryMojang) return null;

        WebPlayer webPlayer;
        try {
            webPlayer = WebPlayer.getSync(uuid); // Use sync wrapper for backward compatibility
        } catch (Exception ignored) {
            return null;
        }

        if (webPlayer == null) return null;
        return new AbstractPlayer(webPlayer.uuid(), webPlayer.name(), false, null);
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        Player player = getOnlinePlayer(username);

        if (player != null) {
            return new AbstractPlayer(player.getUniqueId(), player.getUsername(), true,
                player.getRemoteAddress().getAddress().getHostAddress());
        }
        if (!queryMojang) return null;

        WebPlayer webPlayer;
        try {
            webPlayer = WebPlayer.getSync(username); // Use sync wrapper for backward compatibility
        } catch (Exception ignored) {
            return null;
        }

        if (webPlayer == null) return null;
        return new AbstractPlayer(webPlayer.uuid(), webPlayer.name(), false, null);
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
//        return new VelocityPlayerWrapper(getPlayer(uuid));
        return null;
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return server.getAllPlayers().stream()
                .map(player -> new AbstractPlayer(
                        player.getUniqueId(),
                        player.getUsername(),
                        true,
                        player.getRemoteAddress().getAddress().getHostAddress()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public AbstractPlayer getPlayer(UUID uuid) {
        Player player = server.getPlayer(uuid).orElse(null);
        if (player == null) return null;
        
        return new AbstractPlayer(
                player.getUniqueId(),
                player.getUsername(),
                true,
                player.getRemoteAddress().getAddress().getHostAddress()
        );
    }

    @Override
    public int getMaxPlayers() {
        return server.getConfiguration().getShowMaxPlayers();
    }

    @Override
    public String getServerVersion() {
        return server.getVersion().getVersion();
    }

    @Override
    public void runOnMainThread(Runnable task) {
        // Velocity doesn't have a main thread concept like Bukkit
        // Execute immediately since we're already on the main event thread
        task.run();
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        Player velocityPlayer = server.getPlayer(player.getUuid()).orElse(null);
        if (velocityPlayer != null) {
            // Handle both escaped newlines and literal \n sequences
            String formattedReason = reason.replace("\\n", "\n").replace("\\\\n", "\n");
            velocityPlayer.disconnect(get(formattedReason));
        }
    }

    @Override
    public String getServerName() {
        return "velocity-proxy"; // Default server name, can be made configurable
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public DatabaseProvider createLiteBansDatabaseProvider() {
        try {
            // Check if LiteBans plugin is loaded
            if (server.getPluginManager().getPlugin("litebans").isPresent()) {
                // Verify LiteBans API is accessible
                Class.forName("litebans.api.Database");
                logger.info("[Migration] LiteBans plugin detected, using LiteBans API");
                return new LiteBansDatabaseProvider();
            }
        } catch (ClassNotFoundException e) {
            logger.info("[Migration] LiteBans API not found in classpath");
        } catch (Exception e) {
            logger.warn("[Migration] Error checking for LiteBans: " + e.getMessage());
        }

        return null;
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }
}
