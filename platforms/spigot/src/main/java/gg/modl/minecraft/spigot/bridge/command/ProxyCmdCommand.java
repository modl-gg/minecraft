package gg.modl.minecraft.spigot.bridge.command;

import gg.modl.minecraft.spigot.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.spigot.bridge.query.BridgeQueryServer;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class ProxyCmdCommand implements CommandExecutor {
    private static final String PROXY_CMD_ACTION = "PROXY_CMD";

    private final JavaPlugin plugin;
    private final BridgeLocaleManager localeManager;
    private final BridgeQueryServer queryServer;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.console_only"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.usage"));
            return true;
        }

        if (!queryServer.hasConnectedClients()) {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.not_connected"));
            return true;
        }

        String fullCommand = String.join(" ", args);

        if (queryServer.sendToAllClients(PROXY_CMD_ACTION, fullCommand)) {
            plugin.getLogger().info("Forwarded command to proxy: " + fullCommand);
            sender.sendMessage(localeManager.getMessage("command.proxycmd.sent", mapOf("command", fullCommand)));
        } else {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.failed"));
        }

        return true;
    }
}
