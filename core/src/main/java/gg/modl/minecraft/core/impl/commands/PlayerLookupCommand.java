package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.response.PlayerLookupResponse;
import gg.modl.minecraft.api.http.response.LinkedAccountsResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PlayerLookupCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;
    
    // Cache for punishment type ID -> name mapping
    private final Map<String, String> punishmentTypeNames = new ConcurrentHashMap<>();
    private boolean punishmentTypesLoaded = false;
    
    /**
     * Initialize punishment types cache - called once at startup
     */
    public void initializePunishmentTypes() {
        httpClient.getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) {
                // Map all punishment types (ID -> name)
                response.getData().forEach(pt -> {
                    punishmentTypeNames.put(String.valueOf(pt.getId()), pt.getName());
                    punishmentTypeNames.put(String.valueOf(pt.getOrdinal()), pt.getName());
                });
                punishmentTypesLoaded = true;
                platform.runOnMainThread(() -> {
                    platform.log("[MODL] Loaded " + response.getData().size() + " punishment types for lookup display");
                });
            } else {
                platform.runOnMainThread(() -> {
                    platform.log("[MODL] Failed to load punishment types for lookup display: " + response.getStatus());
                });
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                platform.runOnMainThread(() -> {
                    platform.log("[MODL] Panel restarting, cannot load punishment types: " + throwable.getMessage());
                });
            } else {
                platform.runOnMainThread(() -> {
                    platform.log("[MODL] Error loading punishment types for lookup display: " + throwable.getMessage());
                });
            }
            return null;
        });
    }
    
    /**
     * Update punishment types cache (called by reload command)
     */
    public void updatePunishmentTypesCache(List<PunishmentTypesResponse.PunishmentTypeData> allTypes) {
        punishmentTypeNames.clear();
        // Map all punishment types (ID -> name)
        allTypes.forEach(pt -> {
            punishmentTypeNames.put(String.valueOf(pt.getId()), pt.getName());
            punishmentTypeNames.put(String.valueOf(pt.getOrdinal()), pt.getName());
        });
        punishmentTypesLoaded = true;
    }

    @CommandCompletion("@players")
    @CommandAlias("lookup|look|check|info")
    @Syntax("<player>")
    @Description("Look up detailed information about a player (online or offline)")
    @Conditions("staff")
    public void lookup(CommandIssuer sender, @Name("player") String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);
        
        CompletableFuture<PlayerLookupResponse> future = httpClient.lookupPlayer(request);
        
        future.thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                // Also fetch linked accounts to get staff notes
                UUID playerUuid = UUID.fromString(response.getData().getMinecraftUuid());
                httpClient.getLinkedAccounts(playerUuid).thenAccept(linkedResponse -> {
                    displayPlayerInfo(sender, response.getData(), linkedResponse);
                }).exceptionally(linkedThrowable -> {
                    if (linkedThrowable.getCause() instanceof PanelUnavailableException) {
                        sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                    } else {
                        // Still display info even if linked accounts fetch fails
                        displayPlayerInfo(sender, response.getData(), null);
                    }
                    return null;
                });
            } else {
                sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
            }
            return null;
        });
    }

    private void displayPlayerInfo(CommandIssuer sender, PlayerLookupResponse.PlayerData data, LinkedAccountsResponse linkedResponse) {
        // UUID
        sender.sendMessage(localeManager.getMessage("player_lookup.uuid", Map.of("uuid", data.getMinecraftUuid())));
        
        // Currently Banned/Muted Status - Use cache for accuracy and supplement with API data
        UUID playerUuid = UUID.fromString(data.getMinecraftUuid());
        boolean isBanned = cache.isBanned(playerUuid);
        boolean isMuted = cache.isMuted(playerUuid);
        
        // Supplement with API data if not found in cache (for offline players)
        if (!isBanned || !isMuted) {
            if (data.getRecentPunishments() != null) {
                for (PlayerLookupResponse.RecentPunishment punishment : data.getRecentPunishments()) {
                    if (punishment.isActive()) {
                        String type = punishment.getType();
                        if (type != null) {
                            String typeName = getPunishmentTypeName(type).toLowerCase();
                            // More comprehensive type checking
                            if (!isBanned && (typeName.contains("ban") || typeName.equals("blacklist"))) {
                                isBanned = true;
                            }
                            if (!isMuted && (typeName.contains("mute") || typeName.equals("silence"))) {
                                isMuted = true;
                            }
                        }
                    }
                }
            }
        }
        
        String bannedStatus = isBanned ? "&cYes" : "&aNo";
        String mutedStatus = isMuted ? "&cYes" : "&aNo";
        sender.sendMessage(localeManager.getMessage("player_lookup.currently_banned", Map.of("status", bannedStatus)));
        sender.sendMessage(localeManager.getMessage("player_lookup.currently_muted", Map.of("status", mutedStatus)));

        // Staff Notes
        sender.sendMessage(localeManager.getMessage("player_lookup.staff_notes_label"));
        boolean hasNotes = false;
        if (linkedResponse != null && linkedResponse.getLinkedAccounts() != null) {
            for (Account account : linkedResponse.getLinkedAccounts()) {
                if (account.getNotes() != null && !account.getNotes().isEmpty()) {
                    hasNotes = true;
                    int noteCount = 0;
                    for (Note note : account.getNotes()) {
                        if (noteCount >= 3) break; // Limit to 3 most recent notes
                        sender.sendMessage(localeManager.getMessage("player_lookup.staff_note_format", Map.of(
                            "text", note.getText(),
                            "issuer", note.getIssuerName()
                        )));
                        noteCount++;
                    }
                    break; // Only show notes from first account with notes
                }
            }
        }
        if (!hasNotes) {
            sender.sendMessage(localeManager.getMessage("player_lookup.no_staff_notes"));
        }
        
        // Total Punishments
        int totalPunishments = 0;
        if (data.getPunishmentStats() != null) {
            totalPunishments = data.getPunishmentStats().getTotalPunishments();
        }
        sender.sendMessage(localeManager.getMessage("player_lookup.total_punishments", Map.of("count", String.valueOf(totalPunishments))));

        // Linked Accounts
        sender.sendMessage(localeManager.getMessage("player_lookup.linked_accounts_label"));
        if (linkedResponse != null && linkedResponse.getLinkedAccounts() != null && !linkedResponse.getLinkedAccounts().isEmpty()) {
            int accountCount = 0;
            for (Account account : linkedResponse.getLinkedAccounts()) {
                if (accountCount >= 5) break; // Limit to 5 accounts
                String currentName = account.getUsernames() != null && !account.getUsernames().isEmpty() 
                    ? account.getUsernames().get(account.getUsernames().size() - 1).getUsername()
                    : "Unknown";
                
                // Determine punishment status
                String status;
                
                // Check both ban and mute status using cache
                boolean accountBanned = cache.isBanned(account.getMinecraftUuid());
                boolean accountMuted = cache.isMuted(account.getMinecraftUuid());
                
                if (accountBanned && accountMuted) {
                    status = localeManager.getMessage("player_lookup.status.banned_and_muted");
                } else if (accountBanned) {
                    status = localeManager.getMessage("player_lookup.status.banned");
                } else if (accountMuted) {
                    status = localeManager.getMessage("player_lookup.status.muted");
                } else {
                    status = localeManager.getMessage("player_lookup.status.no_punishments");
                }
                
                sender.sendMessage(localeManager.getMessage("player_lookup.linked_account_format", Map.of(
                    "username", currentName,
                    "status", status
                )));
                accountCount++;
            }
            if (linkedResponse.getLinkedAccounts().size() > 5) {
                sender.sendMessage(localeManager.getMessage("player_lookup.linked_account_more", Map.of(
                    "count", String.valueOf(linkedResponse.getLinkedAccounts().size() - 5)
                )));
            }
        } else {
            sender.sendMessage(localeManager.getMessage("player_lookup.no_linked_accounts"));
        }
        
        // Total Tickets
        int totalTickets = 0;
        if (data.getRecentTickets() != null) {
            totalTickets = data.getRecentTickets().size();
        }
        sender.sendMessage(localeManager.getMessage("player_lookup.total_tickets", Map.of("count", String.valueOf(totalTickets))));

        // Profile Links
        sender.sendMessage("");
        if (data.getMinecraftUuid() != null) {
            // Create clickable JSON message for profile link
            String profileUrl = panelUrl + "/panel?player=" + data.getMinecraftUuid();
            String profileMessage = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"📋 \",\"color\":\"gold\"}," +
                "{\"text\":\"View Full Profile\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view %s's profile\"}}]}",
                profileUrl, data.getCurrentUsername()
            );
            
            if (sender.isPlayer()) {
                UUID senderUuid = sender.getUniqueId();
                platform.runOnMainThread(() -> {
                    platform.sendJsonMessage(senderUuid, profileMessage);
                });
            } else {
                sender.sendMessage(localeManager.getMessage("player_lookup.profile_fallback", Map.of("url", profileUrl)));
            }
        }
    }

    private String formatDate(String isoDate) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(isoDate);
            return new SimpleDateFormat("MM/dd/yyyy HH:mm").format(date);
        } catch (Exception e) {
            return isoDate; // Fallback to original string
        }
    }

    private String getStatusColor(String status) {
        if (status == null) return "&7";
        switch (status.toLowerCase()) {
            case "low": return "&a";
            case "medium": return "&e";
            case "habitual": return "&c";
            default: return "&7";
        }
    }

    private String getPunishmentTypeColor(String type) {
        if (type == null) return "&7";
        String lower = type.toLowerCase();
        if (lower.contains("ban")) return "&4";
        if (lower.contains("mute")) return "&6";
        if (lower.contains("kick")) return "&e";
        if (lower.contains("warn")) return "&c";
        return "&7";
    }

    private String getTicketStatusColor(String status) {
        if (status == null) return "&7";
        switch (status.toLowerCase()) {
            case "open": return "&a";
            case "pending": return "&e";
            case "closed": return "&8";
            case "resolved": return "&2";
            default: return "&7";
        }
    }
    
    /**
     * Get the display name for a punishment type
     * @param typeId The punishment type ID or ordinal from the API
     * @return The human-readable name or "Unknown" if not found
     */
    private String getPunishmentTypeName(String typeId) {
        if (typeId == null) return "Unknown";
        
        // Try to get the name from the cache
        String name = punishmentTypeNames.get(typeId);
        if (name != null) return name;
        
        // Enhanced fallback - try to parse the type ID if it looks like a known punishment
        try {
            // If it's a number, map common punishment type IDs
            int id = Integer.parseInt(typeId);
            switch (id) {
                case 1: return "Ban";
                case 2: return "Mute";
                case 3: return "Kick";
                case 4: return "Warning";
                case 5: return "Blacklist";
                case 6: return "Silence";
                default: break;
            }
        } catch (NumberFormatException ignored) {
            // Check if the typeId itself contains punishment information
            String lower = typeId.toLowerCase();
            if (lower.contains("ban")) return "Ban";
            if (lower.contains("mute")) return "Mute";
            if (lower.contains("kick")) return "Kick";
            if (lower.contains("warn")) return "Warning";
            if (lower.contains("blacklist")) return "Blacklist";
            if (lower.contains("silence")) return "Silence";
        }
        
        // If not found in cache, return the type ID as fallback
        return typeId;
    }
}