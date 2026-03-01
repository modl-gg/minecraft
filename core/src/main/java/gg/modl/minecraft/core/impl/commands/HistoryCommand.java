package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.HistoryMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command to open the History Menu GUI for a player,
 * or print punishment history to chat with the -p flag.
 */
public class HistoryCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    // Cache for punishment type ordinal -> name mapping (used by -p print mode)
    private final Map<Integer, String> punishmentTypeNames = new ConcurrentHashMap<>();

    public HistoryCommand(HttpClientHolder httpClientHolder, Platform platform, Cache cache, LocaleManager localeManager) {
        this.httpClientHolder = httpClientHolder;
        this.platform = platform;
        this.cache = cache;
        this.localeManager = localeManager;
    }

    /**
     * Initialize punishment types cache - called once at startup
     */
    public void initializePunishmentTypes() {
        getHttpClient().getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) {
                updatePunishmentTypesCache(response.getData());
            }
        }).exceptionally(throwable -> null);
    }

    /**
     * Update punishment types cache (called by reload/sync)
     */
    public void updatePunishmentTypesCache(List<PunishmentTypesResponse.PunishmentTypeData> allTypes) {
        punishmentTypeNames.clear();
        allTypes.forEach(pt -> punishmentTypeNames.put(pt.getOrdinal(), pt.getName()));
    }

    private String getPunishmentTypeName(int ordinal) {
        String name = punishmentTypeNames.get(ordinal);
        if (name != null) return name;
        // Fallback to basic category detection
        switch (ordinal) {
            case 0: return "Kick";
            case 1: return "Mute";
            case 2: return "Ban";
            case 3: return "Security Ban";
            case 4: return "Linked Ban";
            case 5: return "Blacklist";
            default: return "Unknown";
        }
    }

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("%cmd_history")
    @Syntax("<player> [-p]")
    @Description("Open the punishment history menu for a player, or use -p to print to chat")
    @Conditions("player|staff")
    public void history(CommandIssuer sender, @Name("player") String playerQuery, @Default("") String flags) {
        boolean printMode = flags.equalsIgnoreCase("-p") || flags.equalsIgnoreCase("print");

        // Console always uses print mode
        if (!sender.isPlayer()) {
            printHistory(sender, playerQuery);
            return;
        }

        if (printMode) {
            printHistory(sender, playerQuery);
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        // Look up the player
        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                // Fetch full profile for the history menu
                getHttpClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        platform.runOnMainThread(() -> {
                            // Get sender name (prefer panel username)
                            String senderName = cache.getStaffDisplayName(senderUuid);
                            if (senderName == null && platform.getPlayer(senderUuid) != null) {
                                senderName = platform.getPlayer(senderUuid).username();
                            }
                            if (senderName == null) senderName = "Staff";

                            // Open the history menu
                            HistoryMenu menu = new HistoryMenu(
                                    platform,
                                    getHttpClient(),
                                    senderUuid,
                                    senderName,
                                    profileResponse.getProfile(),
                                    null // No parent menu when opened from command
                            );

                            // Get CirrusPlayerWrapper and display
                            CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                            menu.display(player);
                        });
                    } else {
                        sender.sendMessage(localeManager.getMessage("general.player_not_found"));
                    }
                }).exceptionally(throwable -> {
                    handleException(sender, throwable, playerQuery);
                    return null;
                });
            } else {
                sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            }
        }).exceptionally(throwable -> {
            handleException(sender, throwable, playerQuery);
            return null;
        });
    }

    private void printHistory(CommandIssuer sender, String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                getHttpClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        Account profile = profileResponse.getProfile();
                        displayHistory(sender, playerName, profile);
                    } else {
                        sender.sendMessage(localeManager.getMessage("general.player_not_found"));
                    }
                }).exceptionally(throwable -> {
                    handleException(sender, throwable, playerQuery);
                    return null;
                });
            } else {
                sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            }
        }).exceptionally(throwable -> {
            handleException(sender, throwable, playerQuery);
            return null;
        });
    }

    private void displayHistory(CommandIssuer sender, String playerName, Account profile) {

        sender.sendMessage(localeManager.getMessage("print.history.header", Map.of("player", playerName)));

        if (profile.getPunishments().isEmpty()) {
            sender.sendMessage(localeManager.getMessage("print.history.empty"));
        } else {
            int ordinal = 1;
            for (Punishment punishment : profile.getPunishments()) {
                String type = getPunishmentTypeName(punishment.getTypeOrdinal());
                String id = punishment.getId() != null ? punishment.getId() : "?";
                String issuer = punishment.getIssuerName() != null ? punishment.getIssuerName() : "Unknown";
                String date = localeManager.formatDate(punishment.getIssued());
                String ordinalStr = String.valueOf(ordinal);
                String reason = punishment.getReason() != null ? punishment.getReason() : "";

                boolean isKick = punishment.isKickType();

                // Use the same effective duration logic as the GUI (HistoryMenu)
                Long effectiveDuration = punishment.getEffectiveDuration();

                // Duration display string
                String duration;
                if (isKick) {
                    duration = "";
                } else if (effectiveDuration == null || effectiveDuration <= 0) {
                    duration = "permanent";
                } else {
                    duration = MenuItems.formatDuration(effectiveDuration);
                }

                // Build locale variables
                Map<String, String> vars = new HashMap<>();
                vars.put("ordinal", ordinalStr);
                vars.put("type", type);
                vars.put("id", id);
                vars.put("issuer", issuer);
                vars.put("date", date);
                vars.put("reason", reason);
                vars.put("duration", duration);

                if (isKick) {
                    // Kicks are instant — no active/inactive status
                    sender.sendMessage(localeManager.getMessage("print.history.entry_kick", vars));
                } else {
                    // Check for pardon (same logic as HistoryMenu.findPardonDate)
                    Date pardonDate = findPardonDate(punishment);

                    if (pardonDate != null) {
                        long pardonedAgo = System.currentTimeMillis() - pardonDate.getTime();
                        vars.put("pardoned_ago", MenuItems.formatDuration(pardonedAgo > 0 ? pardonedAgo : 0));
                        sender.sendMessage(localeManager.getMessage("print.history.entry_pardoned", vars));
                    } else if (punishment.getStarted() == null) {
                        // Queued / not yet started
                        sender.sendMessage(localeManager.getMessage("print.history.entry_unstarted", vars));
                    } else if (punishment.isActive()) {
                        // Active — permanent or timed
                        if (effectiveDuration == null || effectiveDuration <= 0) {
                            sender.sendMessage(localeManager.getMessage("print.history.entry_permanent", vars));
                        } else {
                            // Calculate remaining time using effective expiry
                            Date effectiveExpiry = punishment.getEffectiveExpiry();
                            long remaining = effectiveExpiry != null
                                    ? effectiveExpiry.getTime() - System.currentTimeMillis() : 0;
                            vars.put("expiry", MenuItems.formatDuration(remaining > 0 ? remaining : 0));
                            sender.sendMessage(localeManager.getMessage("print.history.entry_active", vars));
                        }
                    } else {
                        // Naturally expired — show how long ago
                        Date effectiveExpiry = punishment.getEffectiveExpiry();
                        if (effectiveExpiry != null) {
                            long expiredAgo = System.currentTimeMillis() - effectiveExpiry.getTime();
                            vars.put("expired_ago", MenuItems.formatDuration(expiredAgo > 0 ? expiredAgo : 0));
                        } else {
                            vars.put("expired_ago", "N/A");
                        }
                        sender.sendMessage(localeManager.getMessage("print.history.entry_expired", vars));
                    }
                }
                ordinal++;
            }
            sender.sendMessage(localeManager.getMessage("print.history.total", Map.of(
                    "count", String.valueOf(profile.getPunishments().size())
            )));
        }

        sender.sendMessage(localeManager.getMessage("print.history.footer"));
    }

    /**
     * Find the date when a punishment was pardoned.
     * Same logic as HistoryMenu.findPardonDate.
     */
    private Date findPardonDate(Punishment punishment) {
        for (Modification mod : punishment.getModifications()) {
            if (mod.getType() == Modification.Type.MANUAL_PARDON ||
                mod.getType() == Modification.Type.APPEAL_ACCEPT) {
                return mod.getIssued();
            }
        }
        return null;
    }

    private void handleException(CommandIssuer sender, Throwable throwable, String playerQuery) {
        if (throwable.getCause() instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
        }
    }
}
