package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.response.LinkedAccountsResponse;
import gg.modl.minecraft.api.http.response.PlayerLookupResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.InspectMenu;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command to open the Inspect Menu GUI for a player,
 * or print text-based lookup info with the -p flag.
 */
public class InspectCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    // Cache for punishment type ID -> name mapping (used by -p print mode)
    private final Map<String, String> punishmentTypeNames = new ConcurrentHashMap<>();
    private boolean punishmentTypesLoaded = false;

    public InspectCommand(HttpClientHolder httpClientHolder, Platform platform, Cache cache, LocaleManager localeManager, String panelUrl) {
        this.httpClientHolder = httpClientHolder;
        this.platform = platform;
        this.cache = cache;
        this.localeManager = localeManager;
        this.panelUrl = panelUrl;
    }

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    /**
     * Initialize punishment types cache - called once at startup
     */
    public void initializePunishmentTypes() {
        getHttpClient().getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) {
                response.getData().forEach(pt -> {
                    punishmentTypeNames.put(String.valueOf(pt.getId()), pt.getName());
                    punishmentTypeNames.put(String.valueOf(pt.getOrdinal()), pt.getName());
                });
                punishmentTypesLoaded = true;
            }
        }).exceptionally(throwable -> {
            platform.runOnMainThread(() -> {
                platform.log("[MODL] Error loading punishment types for lookup display: " + throwable.getMessage());
            });
            return null;
        });
    }

    /**
     * Update punishment types cache (called by reload/sync)
     */
    public void updatePunishmentTypesCache(List<PunishmentTypesResponse.PunishmentTypeData> allTypes) {
        punishmentTypeNames.clear();
        allTypes.forEach(pt -> {
            punishmentTypeNames.put(String.valueOf(pt.getId()), pt.getName());
            punishmentTypeNames.put(String.valueOf(pt.getOrdinal()), pt.getName());
        });
        punishmentTypesLoaded = true;
    }

    @CommandCompletion("@players")
    @CommandAlias("inspect|ins|check|lookup|look|info")
    @Syntax("<player> [-p]")
    @Description("Open the inspect menu for a player, or use -p to print info to chat")
    @Conditions("player|staff")
    public void inspect(CommandIssuer sender, @Name("player") String playerQuery, @Default("") String flags) {
        boolean printMode = flags.equalsIgnoreCase("-p") || flags.equalsIgnoreCase("print");

        if (printMode) {
            printLookup(sender, playerQuery);
            return;
        }

        // GUI mode - requires V2 API
        if (httpClientHolder.getApiVersion() == ApiVersion.V1) {
            sender.sendMessage(localeManager.getMessage("api_errors.menus_require_v2"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        // Look up the player
        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                // Fetch full profile for the inspect menu
                getHttpClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        platform.runOnMainThread(() -> {
                            // Get sender name
                            String senderName = "Staff";
                            if (platform.getPlayer(senderUuid) != null) {
                                senderName = platform.getPlayer(senderUuid).username();
                            }

                            // Open the inspect menu
                            InspectMenu menu = new InspectMenu(
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

    // --- Print mode (formerly /lookup) ---

    private void printLookup(CommandIssuer sender, String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID playerUuid = UUID.fromString(response.getData().getMinecraftUuid());
                getHttpClient().getLinkedAccounts(playerUuid).thenAccept(linkedResponse -> {
                    displayPlayerInfo(sender, response.getData(), linkedResponse);
                }).exceptionally(linkedThrowable -> {
                    if (linkedThrowable.getCause() instanceof PanelUnavailableException) {
                        sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                    } else {
                        displayPlayerInfo(sender, response.getData(), null);
                    }
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

    private void displayPlayerInfo(CommandIssuer sender, PlayerLookupResponse.PlayerData data, LinkedAccountsResponse linkedResponse) {
        sender.sendMessage(localeManager.getMessage("player_lookup.uuid", Map.of("uuid", data.getMinecraftUuid())));

        UUID playerUuid = UUID.fromString(data.getMinecraftUuid());
        boolean isBanned = cache.isBanned(playerUuid);
        boolean isMuted = cache.isMuted(playerUuid);

        if (!isBanned || !isMuted) {
            if (data.getRecentPunishments() != null) {
                for (PlayerLookupResponse.RecentPunishment punishment : data.getRecentPunishments()) {
                    if (punishment.isActive()) {
                        String type = punishment.getType();
                        if (type != null) {
                            String typeName = getPunishmentTypeName(type).toLowerCase();
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

        sender.sendMessage(localeManager.getMessage("player_lookup.staff_notes_label"));
        boolean hasNotes = false;
        if (linkedResponse != null && linkedResponse.getLinkedAccounts() != null) {
            for (Account account : linkedResponse.getLinkedAccounts()) {
                if (account.getNotes() != null && !account.getNotes().isEmpty()) {
                    hasNotes = true;
                    int noteCount = 0;
                    for (Note note : account.getNotes()) {
                        if (noteCount >= 3) break;
                        sender.sendMessage(localeManager.getMessage("player_lookup.staff_note_format", Map.of(
                            "text", note.getText(),
                            "issuer", note.getIssuerName()
                        )));
                        noteCount++;
                    }
                    break;
                }
            }
        }
        if (!hasNotes) {
            sender.sendMessage(localeManager.getMessage("player_lookup.no_staff_notes"));
        }

        int totalPunishments = 0;
        if (data.getPunishmentStats() != null) {
            totalPunishments = data.getPunishmentStats().getTotalPunishments();
        }
        sender.sendMessage(localeManager.getMessage("player_lookup.total_punishments", Map.of("count", String.valueOf(totalPunishments))));

        sender.sendMessage(localeManager.getMessage("player_lookup.linked_accounts_label"));
        if (linkedResponse != null && linkedResponse.getLinkedAccounts() != null && !linkedResponse.getLinkedAccounts().isEmpty()) {
            int accountCount = 0;
            for (Account account : linkedResponse.getLinkedAccounts()) {
                if (accountCount >= 5) break;
                String currentName = account.getUsernames() != null && !account.getUsernames().isEmpty()
                    ? account.getUsernames().get(account.getUsernames().size() - 1).getUsername()
                    : "Unknown";

                boolean accountBanned = cache.isBanned(account.getMinecraftUuid());
                boolean accountMuted = cache.isMuted(account.getMinecraftUuid());

                String status;
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

        int totalTickets = 0;
        if (data.getRecentTickets() != null) {
            totalTickets = data.getRecentTickets().size();
        }
        sender.sendMessage(localeManager.getMessage("player_lookup.total_tickets", Map.of("count", String.valueOf(totalTickets))));

        sender.sendMessage("");
        if (data.getMinecraftUuid() != null) {
            String profileUrl = panelUrl + "/panel?player=" + data.getMinecraftUuid();
            String profileMessage = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"📋 \",\"color\":\"gold\"}," +
                "{\"text\":\"View Web Profile\",\"color\":\"aqua\",\"underlined\":true," +
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

    private String getPunishmentTypeName(String typeId) {
        if (typeId == null) return "Unknown";

        String name = punishmentTypeNames.get(typeId);
        if (name != null) return name;

        try {
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
            String lower = typeId.toLowerCase();
            if (lower.contains("ban")) return "Ban";
            if (lower.contains("mute")) return "Mute";
            if (lower.contains("kick")) return "Kick";
            if (lower.contains("warn")) return "Warning";
            if (lower.contains("blacklist")) return "Blacklist";
            if (lower.contains("silence")) return "Silence";
        }

        return typeId;
    }

    // --- Common ---

    private void handleException(CommandIssuer sender, Throwable throwable, String playerQuery) {
        if (throwable.getCause() instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
        }
    }
}
