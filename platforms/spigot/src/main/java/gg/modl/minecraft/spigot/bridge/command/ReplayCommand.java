package gg.modl.minecraft.spigot.bridge.command;

import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.spigot.bridge.locale.BridgeLocaleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReplayCommand implements CommandExecutor, TabCompleter {

    private final ReplayService replayService;

    public ReplayCommand(ReplayService replayService) {
        this.replayService = replayService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "status" -> handleStatus(sender, args);
            case "capture" -> handleCapture(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleStatus(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 1);
        if (target == null) return;

        boolean available = replayService.isReplayAvailable(target.getUniqueId());
        sender.sendMessage(ChatColor.GRAY + "[Replay] " + ChatColor.WHITE + target.getName() + ": "
                + (available ? ChatColor.GREEN + "recording" : ChatColor.RED + "not recording"));
    }

    private void handleCapture(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 1);
        if (target == null) return;

        UUID uuid = target.getUniqueId();
        String name = target.getName();

        if (!replayService.isReplayAvailable(uuid)) {
            sender.sendMessage(ChatColor.RED + "[Replay] No active recording for " + name);
            return;
        }

        sender.sendMessage(ChatColor.GRAY + "[Replay] Capturing and uploading replay for " + name + "...");

        replayService.captureReplay(uuid, name).thenAccept(replayId -> {
            if (replayId != null) {
                sender.sendMessage(ChatColor.GREEN + "[Replay] Replay captured: " + replayId);
            } else {
                sender.sendMessage(ChatColor.RED + "[Replay] Failed to capture replay for " + name);
            }
        });
    }

    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player target = Bukkit.getPlayer(args[index]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "[Replay] Player not found: " + args[index]);
            }
            return target;
        }

        if (sender instanceof Player player) {
            return player;
        }

        sender.sendMessage(ChatColor.RED + "[Replay] Specify a player name from console");
        return null;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "[Replay] Usage:");
        sender.sendMessage(ChatColor.GRAY + "  /replay status [player]" + ChatColor.WHITE + " - Check recording status");
        sender.sendMessage(ChatColor.GRAY + "  /replay capture [player]" + ChatColor.WHITE + " - Capture and upload replay");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String sub : List.of("status", "capture")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
