package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.commands.punishments.PunishCommand;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PermissionUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class ModlReloadCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final PunishCommand punishCommand;
    private final PlayerLookupCommand playerLookupCommand;

    @CommandAlias("modl")
    @Subcommand("reload")
    @Description("Reload all MODL data including punishment types, staff permissions, and locale files")
    @Syntax("reload [component]")
    public void reload(CommandIssuer sender, @Default("all") String component) {
        // Check if user has admin permissions to reload
        if (!PermissionUtil.hasAnyPermission(sender, cache, "admin.settings.view", "admin.settings.modify", "admin.reload")) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        String validComponents = "all, punishment-types, staff-permissions, locale";
        
        switch (component.toLowerCase()) {
            case "all":
                reloadAll(sender);
                break;
            case "punishment-types":
            case "punishments":
            case "types":
                reloadPunishmentTypes(sender);
                break;
            case "staff-permissions":
            case "staff":
            case "permissions":
                reloadStaffPermissions(sender);
                break;
            case "locale":
            case "locales":
            case "messages":
                reloadLocale(sender);
                break;
            default:
                sender.sendMessage(localeManager.getMessage("reload.invalid_component", 
                    Map.of("component", component, "valid", validComponents)));
        }
    }

    @CommandAlias("modl")
    @Subcommand("status")
    @Description("Show MODL plugin status and loaded data")
    public void status(CommandIssuer sender) {
        if (!PermissionUtil.hasAnyPermission(sender, cache, "admin.settings.view", "admin.status")) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        sender.sendMessage("§6=== MODL Plugin Status ===");
        
        // Show punishment types status
        int punishmentTypeCount = punishCommand.getPunishmentTypeNames().size();
        sender.sendMessage("§ePunishment Types: §f" + punishmentTypeCount + " loaded");
        
        // Show staff permissions status
        int staffCount = cache.getStaffCount();
        sender.sendMessage("§eStaff Permissions: §f" + staffCount + " staff members cached");
        
        // Show online players with cached data
        int onlinePlayersWithCache = cache.getCachedPlayerCount();
        sender.sendMessage("§eCached Players: §f" + onlinePlayersWithCache + " players with punishment data");
        
        // Show locale status
        String localeFile = localeManager.getCurrentLocale();
        sender.sendMessage("§eLocale: §f" + localeFile);
        
        sender.sendMessage("§6========================");
    }

    private void reloadAll(CommandIssuer sender) {
        sender.sendMessage("§6[MODL] §eStarting full reload...");
        
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(3); // punishment types, staff permissions, locale
        
        // Reload punishment types
        reloadPunishmentTypesAsync(sender, () -> {
            if (completed.incrementAndGet() == total.get()) {
                sender.sendMessage("§6[MODL] §aFull reload completed successfully!");
            }
        });
        
        // Reload staff permissions
        reloadStaffPermissionsAsync(sender, () -> {
            if (completed.incrementAndGet() == total.get()) {
                sender.sendMessage("§6[MODL] §aFull reload completed successfully!");
            }
        });
        
        // Reload locale
        reloadLocaleSync(sender);
        if (completed.incrementAndGet() == total.get()) {
            sender.sendMessage("§6[MODL] §aFull reload completed successfully!");
        }
    }

    private void reloadPunishmentTypes(CommandIssuer sender) {
        sender.sendMessage("§6[MODL] §eReloading punishment types...");
        reloadPunishmentTypesAsync(sender, () -> {
            sender.sendMessage("§6[MODL] §aPunishment types reload completed!");
        });
    }

    private void reloadStaffPermissions(CommandIssuer sender) {
        sender.sendMessage("§6[MODL] §eReloading staff permissions...");
        reloadStaffPermissionsAsync(sender, () -> {
            sender.sendMessage("§6[MODL] §aStaff permissions reload completed!");
        });
    }

    private void reloadLocale(CommandIssuer sender) {
        sender.sendMessage("§6[MODL] §eReloading locale files...");
        reloadLocaleSync(sender);
        sender.sendMessage("§6[MODL] §aLocale reload completed!");
    }

    private void reloadPunishmentTypesAsync(CommandIssuer sender, Runnable onComplete) {
        CompletableFuture.allOf(
            // Reload punishment types for punish command
            reloadPunishCommandTypes(sender),
            // Reload punishment types for player lookup command
            reloadPlayerLookupTypes(sender)
        ).thenRun(() -> {
            sender.sendMessage("§aPunishment types reloaded successfully");
            if (onComplete != null) onComplete.run();
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage("§cFailed to reload punishment types: " + throwable.getMessage());
            }
            if (onComplete != null) onComplete.run();
            return null;
        });
    }

    private CompletableFuture<Void> reloadPunishCommandTypes(CommandIssuer sender) {
        return httpClient.getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) {
                punishCommand.updatePunishmentTypesCache(response.getData());
                sender.sendMessage("§7- Punish command types: §a" + 
                    punishCommand.getPunishmentTypeNames().size() + " loaded");
            } else {
                sender.sendMessage("§7- Punish command types: §cFailed (Status: " + response.getStatus() + ")");
            }
        });
    }

    private CompletableFuture<Void> reloadPlayerLookupTypes(CommandIssuer sender) {
        return httpClient.getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) {
                playerLookupCommand.updatePunishmentTypesCache(response.getData());
                sender.sendMessage("§7- Player lookup types: §a" + 
                    response.getData().size() + " loaded");
            } else {
                sender.sendMessage("§7- Player lookup types: §cFailed (Status: " + response.getStatus() + ")");
            }
        });
    }

    private void reloadStaffPermissionsAsync(CommandIssuer sender, Runnable onComplete) {
        httpClient.getStaffPermissions().thenAccept(response -> {
            cache.clearStaffPermissions();
            
            int loadedCount = 0;
            for (var staffMember : response.getData().getStaff()) {
                if (staffMember.getMinecraftUuid() != null) {
                    try {
                        UUID uuid = UUID.fromString(staffMember.getMinecraftUuid());
                        cache.cacheStaffPermissions(uuid, staffMember.getStaffRole(), staffMember.getPermissions());
                        loadedCount++;
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage("§7- Invalid UUID for staff member " + 
                            staffMember.getStaffUsername() + ": " + staffMember.getMinecraftUuid());
                    }
                }
            }
            
            sender.sendMessage("§aStaff permissions reloaded: " + loadedCount + " staff members");
            if (onComplete != null) onComplete.run();
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage("§cFailed to reload staff permissions: " + throwable.getMessage());
            }
            if (onComplete != null) onComplete.run();
            return null;
        });
    }

    private void reloadLocaleSync(CommandIssuer sender) {
        try {
            localeManager.reloadLocale();
            sender.sendMessage("§aLocale files reloaded successfully");
        } catch (Exception e) {
            sender.sendMessage("§cFailed to reload locale: " + e.getMessage());
        }
    }
}