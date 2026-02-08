package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.api.http.response.PunishmentCreateResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.PunishMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentTypeParser;
import gg.modl.minecraft.core.util.WebPlayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PunishCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    // Helper to get the current HTTP client
    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    // Cache for punishment types - loaded once at startup and manually refreshed
    private volatile List<PunishmentTypesResponse.PunishmentTypeData> cachedPunishmentTypes = new ArrayList<>();
    private volatile boolean cacheInitialized = false;

    private static final Set<String> VALID_SEVERITIES = Set.of("low", "regular", "severe");

    @CommandCompletion("@players @punishment-types")
    @CommandAlias("punish|p")
    @Syntax("<target> [type] [reason...] [-lenient|regular|severe] [-ab (alt block)] [-s (silent)] [-sw (stat-wipe)]")
    @Description("Issue a punishment to a player. With no type specified and as a player, opens the punishment GUI.")
    @Conditions("staff")
    public void punish(CommandIssuer sender, @Name("target") Account target, @Name("args") @Optional String[] args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        // If no args provided and sender is a player, open the punishment GUI
        if ((args == null || args.length == 0) && sender.isPlayer()) {
            openPunishmentGui(sender, target);
            return;
        }

        // Console or with args: require type argument
        if (args == null || args.length == 0) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.invalid_syntax", Map.of()));
            return;
        }

        // Check if cache is initialized, if not show error
        if (!cacheInitialized || cachedPunishmentTypes.isEmpty()) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_types_not_loaded", Map.of()));
            return;
        }

        // Use cached punishment types
        List<PunishmentTypesResponse.PunishmentTypeData> punishmentTypes = cachedPunishmentTypes;
        
        // Parse punishment type and remaining arguments
        ParsedCommand parsed = parsePunishmentTypeAndArgs(args, punishmentTypes);
        if (parsed == null) {
            String availableTypes = punishmentTypes.stream()
                    .map(PunishmentTypesResponse.PunishmentTypeData::getName)
                    .collect(Collectors.joining(", "));
            sender.sendMessage(localeManager.getPunishmentMessage("general.invalid_punishment_type", 
                Map.of("types", availableTypes)));
            return;
        }

        final PunishmentTypesResponse.PunishmentTypeData punishmentType = parsed.punishmentType;

        // Check permission for this specific punishment type
        String punishmentPermission = PermissionUtil.formatPunishmentPermission(punishmentType.getName());
        if (!PermissionUtil.hasPermission(sender, cache, punishmentPermission)) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.no_permission_punishment", 
                Map.of("type", punishmentType.getName())));
            return;
        }

        // Parse arguments
        PunishmentArgs punishmentArgs = parseArguments(parsed.remainingArgs);
        
        // Validate severity
        if (punishmentArgs.severity != null && !VALID_SEVERITIES.contains(punishmentArgs.severity)) {
            sender.sendMessage(localeManager.getMessage("punishment_commands.invalid_severity"));
            return;
        }

        // Validate punishment type compatibility
        String validationError = validatePunishmentCompatibility(punishmentArgs, punishmentType);
        if (validationError != null) {
            sender.sendMessage(validationError);
            return;
        }

        // Set default values if not specified (matching panel logic)
        if (punishmentArgs.severity == null) {
            punishmentArgs.severity = "regular"; // Default severity
        }

        // Calculate offense level automatically based on player status (matching panel AI logic)
        String calculatedOffenseLevel = calculateOffenseLevel(target, punishmentType);
        punishmentArgs.offenseLevel = calculatedOffenseLevel;

        // Get issuer information
        final String issuerName = sender.isPlayer() ? 
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Build punishment data (matching panel logic)
        Map<String, Object> data = buildPunishmentData(punishmentArgs, punishmentType, target);

        // Create notes list (matching panel logic)
        List<String> notes = new ArrayList<>();
        if (!punishmentArgs.reason.isEmpty()) {
            notes.add(punishmentArgs.reason);
        }

        // Create punishment request matching panel API structure
        PunishmentCreateRequest request = new PunishmentCreateRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            punishmentType.getOrdinal(), // Use integer ordinal
            punishmentArgs.reason.isEmpty() ? "No reason specified" : punishmentArgs.reason,
            punishmentArgs.duration > 0 ? punishmentArgs.duration : null,
            data,
            notes,
            new ArrayList<>(), // attachedTicketIds
            punishmentArgs.severity, // severity for punishment calculation
            punishmentArgs.offenseLevel // status (offense level) for punishment calculation
        );

        // Make copies for lambda usage
        final String punishmentTypeName = punishmentType.getName();
        final boolean silentPunishment = punishmentArgs.silent;
        final int ordinal = punishmentType.getOrdinal();

        // For V1 API with manual punishment types (ordinals 0-5), use the manual endpoint
        if (httpClientHolder.getApiVersion() == ApiVersion.V1 && ordinal <= 5) {
            // Convert to CreatePunishmentRequest for manual endpoint
            com.google.gson.JsonObject dataJson = new com.google.gson.JsonObject();
            dataJson.addProperty("reason", punishmentArgs.reason.isEmpty() ? "No reason specified" : punishmentArgs.reason);
            dataJson.addProperty("silent", punishmentArgs.silent);
            dataJson.addProperty("altBlocking", punishmentArgs.altBlocking);
            dataJson.addProperty("wipeAfterExpiry", punishmentArgs.statWipe);
            if (punishmentArgs.duration > 0) {
                dataJson.addProperty("duration", punishmentArgs.duration);
            }

            gg.modl.minecraft.api.http.request.CreatePunishmentRequest manualRequest =
                new gg.modl.minecraft.api.http.request.CreatePunishmentRequest(
                    target.getMinecraftUuid().toString(),
                    issuerName,
                    ordinal,
                    punishmentArgs.reason.isEmpty() ? "No reason specified" : punishmentArgs.reason,
                    punishmentArgs.duration,
                    dataJson,
                    new ArrayList<>(),
                    new ArrayList<>()
                );

            getHttpClient().createPunishment(manualRequest).thenAccept(response -> {
                String targetName = target.getUsernames().get(0).getUsername();

                // Success message
                sender.sendMessage(localeManager.punishment()
                    .type(punishmentTypeName)
                    .target(targetName)
                    .get("general.punishment_issued"));

                // Staff notification
                String staffMessage = localeManager.punishment()
                    .issuer(issuerName)
                    .type(punishmentTypeName)
                    .target(targetName)
                    .get("general.staff_notification");
                platform.staffBroadcast(staffMessage);

            }).exceptionally(throwable -> {
                if (throwable.getCause() instanceof PanelUnavailableException) {
                    sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                } else {
                    sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                        Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
                }
                return null;
            });
            return;
        }

        // For V2 or dynamic punishments (ordinals > 5), use the dynamic endpoint
        CompletableFuture<PunishmentCreateResponse> future = getHttpClient().createPunishmentWithResponse(request);

        future.thenAccept(response -> {
            if (response.isSuccess()) {
                String targetName = target.getUsernames().get(0).getUsername();

                // Success message to issuer
                sender.sendMessage(localeManager.punishment()
                    .type(punishmentTypeName)
                    .target(targetName)
                    .punishmentId(response.getPunishmentId())
                    .get("general.punishment_issued"));


                // Staff notification
                String staffMessage = localeManager.punishment()
                    .issuer(issuerName)
                    .type(punishmentTypeName)
                    .target(targetName)
                    .punishmentId(response.getPunishmentId())
                    .get("general.staff_notification");
                platform.staffBroadcast(staffMessage);

            } else {
                sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                    Map.of("error", localeManager.sanitizeErrorMessage(response.getMessage()))));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                    Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
            }
            return null;
        });
    }

    /**
     * Open the punishment GUI for a player target
     */
    private void openPunishmentGui(CommandIssuer sender, Account target) {
        // Menus require V2 API
        if (httpClientHolder.getApiVersion() == ApiVersion.V1) {
            sender.sendMessage(localeManager.getMessage("api_errors.menus_require_v2"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        platform.runOnMainThread(() -> {
            // Get sender name
            String senderName = "Staff";
            if (platform.getPlayer(senderUuid) != null) {
                senderName = platform.getPlayer(senderUuid).username();
            }

            // Open the punish menu
            PunishMenu menu = new PunishMenu(
                    platform,
                    getHttpClient(),
                    senderUuid,
                    senderName,
                    target,
                    null // No parent menu when opened from command
            );

            // Get CirrusPlayerWrapper and display
            CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
            menu.display(player);
        });
    }

    /**
     * Initialize punishment types cache - called once at startup
     */
    public void initializePunishmentTypes() {
        // Load punishment types
        getHttpClient().getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) {
                // Populate the punishment type registry for ban/mute detection
                PunishmentTypeParser.populateRegistry(response.getData());

                // Filter out manual punishment types (ordinals 0-5: kick, manual_mute, manual_ban, security_ban, linked_ban, blacklist)
                cachedPunishmentTypes = response.getData().stream()
                        .filter(pt -> pt.getOrdinal() > 5)
                        .collect(Collectors.toList());
                cacheInitialized = true;
                platform.runOnMainThread(() -> {
                    // Log successful initialization
                    platform.log("[MODL] Loaded " + cachedPunishmentTypes.size() + " punishment types from API");
                });
            } else {
                platform.runOnMainThread(() -> {
                    platform.log("[MODL] Failed to load punishment types from API: " + response.getStatus());
                });
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                platform.runOnMainThread(() -> {
                    platform.log("[MODL] Panel restarting, cannot load punishment types: " + throwable.getMessage());
                });
            } else {
                platform.runOnMainThread(() -> {
                    platform.log("[MODL] Error loading punishment types: " + throwable.getMessage());
                });
            }
            return null;
        });

        // Load staff permissions
        loadStaffPermissions();
    }
    
    /**
     * Load staff permissions into cache
     */
    private void loadStaffPermissions() {
        getHttpClient().getStaffPermissions().thenAccept(response -> {
            cache.clearStaffPermissions();
            
            for (var staffMember : response.getData().getStaff()) {
                if (staffMember.getMinecraftUuid() != null) {
                    try {
                        UUID uuid = UUID.fromString(staffMember.getMinecraftUuid());
                        cache.cacheStaffPermissions(uuid, staffMember.getStaffRole(), staffMember.getPermissions());
                    } catch (IllegalArgumentException e) {
                        platform.runOnMainThread(() -> {
                            platform.log("[MODL] Invalid UUID for staff member " + staffMember.getStaffUsername() + ": " + staffMember.getMinecraftUuid());
                        });
                    }
                }
            }
            
            platform.runOnMainThread(() -> {
                platform.log("[MODL] Loaded permissions for " + response.getData().getStaff().size() + " staff members");
            });
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                platform.runOnMainThread(() -> {
                    System.err.println("[MODL] Panel restarting, cannot load staff permissions: " + throwable.getMessage());
                });
            } else {
                platform.runOnMainThread(() -> {
                    System.err.println("[MODL] Error loading staff permissions: " + throwable.getMessage());
                });
            }
            return null;
        });
    }


    /**
     * Get available punishment type names for tab completion
     */
    public List<String> getPunishmentTypeNames() {
        return cachedPunishmentTypes.stream()
                .map(PunishmentTypesResponse.PunishmentTypeData::getName)
                .collect(Collectors.toList());
    }
    
    /**
     * Update punishment types cache (called by reload command)
     */
    public void updatePunishmentTypesCache(List<PunishmentTypesResponse.PunishmentTypeData> allTypes) {
        // Populate the punishment type registry for ban/mute detection
        PunishmentTypeParser.populateRegistry(allTypes);

        // Filter out manual punishment types (ordinals 0-5: kick, manual_mute, manual_ban, security_ban, linked_ban, blacklist)
        cachedPunishmentTypes = allTypes.stream()
                .filter(pt -> pt.getOrdinal() > 5)
                .collect(Collectors.toList());
        cacheInitialized = true;
    }
    
    /**
     * Get public notification message for punishment
     */
    private String getPublicNotificationMessage(String punishmentTypeName, String targetName, long duration, int ordinal, String reason) {
        // Find the punishment type to get description and other details
        PunishmentTypesResponse.PunishmentTypeData punishmentType = cachedPunishmentTypes.stream()
                .filter(pt -> pt.getOrdinal() == ordinal)
                .findFirst()
                .orElse(null);
                
        Map<String, String> variables = new HashMap<>();
        variables.put("target", targetName);
        variables.put("duration", localeManager.formatDuration(duration));
        variables.put("reason", reason != null ? reason : "No reason specified");
        variables.put("description", punishmentType != null ? punishmentType.getName() : punishmentTypeName);
        // Appeal URL - derive from api.url
        String panelUrl = localeManager.getPanelUrl();
        if (panelUrl != null && !panelUrl.isEmpty()) {
            variables.put("appeal_url", panelUrl + "/appeal");
        } else {
            variables.put("appeal_url", "https://server.modl.gg/appeal");
        }
        
        // Get public notification using new locale format
        return localeManager.getPublicNotificationMessage(ordinal, variables);
    }
    
    /**
     * Map punishment type name to basic category for default messages
     */
    private String getBasicPunishmentCategory(String punishmentTypeName) {
        String lower = punishmentTypeName.toLowerCase();
        if (lower.contains("kick")) return "kick";
        if (lower.contains("mute")) return "mute";
        if (lower.contains("ban")) return "ban";
        if (lower.contains("blacklist")) return "blacklist";
        
        // Default to ban for unknown types
        return "ban";
    }

    private PunishmentArgs parseArguments(String args) {
        String[] arguments = args.split(" ");
        PunishmentArgs result = new PunishmentArgs();
        
        StringBuilder reasonBuilder = new StringBuilder();
        
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            
            if (arg.equalsIgnoreCase("-severity") && i + 1 < arguments.length) {
                String severityInput = arguments[++i].toLowerCase();
                // Map UI severity names to API severity names (matching panel logic)
                switch (severityInput) {
                    case "lenient":
                        result.severity = "low";
                        break;
                    case "normal":
                    case "regular":
                        result.severity = "regular";
                        break;
                    case "aggravated":
                    case "severe":
                        result.severity = "severe";
                        break;
                    default:
                        result.severity = severityInput; // Use as-is for lenient/regular/severe
                }
            } else if (arg.equalsIgnoreCase("-lenient")) {
                result.severity = "low";
            } else if (arg.equalsIgnoreCase("-regular") || arg.equalsIgnoreCase("-normal")) {
                result.severity = "regular";
            } else if (arg.equalsIgnoreCase("-severe")) {
                result.severity = "severe";
            // Removed manual offense level - now calculated automatically
            } else if (arg.equalsIgnoreCase("-alt-blocking") || arg.equalsIgnoreCase("-ab")) {
                result.altBlocking = true;
            } else if (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s")) {
                result.silent = true;
            } else if (arg.equalsIgnoreCase("-stat-wipe") || arg.equalsIgnoreCase("-sw")) {
                result.statWipe = true;
            } else {
                // For dynamic punishments, durations are calculated automatically based on severity and offense level
                // Any non-flag argument is treated as part of the reason
                if (reasonBuilder.length() > 0) {
                    reasonBuilder.append(" ");
                }
                reasonBuilder.append(arg);
            }
        }
        
        result.reason = reasonBuilder.toString().trim();
        return result;
    }

    private Map<String, Object> buildPunishmentData(PunishmentArgs args, PunishmentTypesResponse.PunishmentTypeData punishmentType, Account target) {
        Map<String, Object> data = new HashMap<>();
        
        // Initialize default fields (matching panel backend logic)
        data.put("duration", 0L);
        
        // Set blockedName and blockedSkin for "permanent until" punishment types
        if (Boolean.TRUE.equals(punishmentType.getPermanentUntilUsernameChange())) {
            String currentUsername = target.getUsernames() != null && !target.getUsernames().isEmpty()
                ? target.getUsernames().get(target.getUsernames().size() - 1).getUsername()
                : "Unknown";
            data.put("blockedName", currentUsername);
        } else {
            data.put("blockedName", null);
        }
        
        if (Boolean.TRUE.equals(punishmentType.getPermanentUntilSkinChange())) {
            String currentSkinHash = null;
            try {
                WebPlayer webPlayer = WebPlayer.getSync(target.getMinecraftUuid()); // Use sync wrapper
                if (webPlayer != null && webPlayer.valid()) {
                    currentSkinHash = webPlayer.skin();
                }
            } catch (Exception e) {
                // Log warning but continue with null skin hash
                System.err.println("Failed to get skin hash for " + target.getUsernames().get(0).getUsername() + ": " + e.getMessage());
            }
            data.put("blockedSkin", currentSkinHash);
        } else {
            data.put("blockedSkin", null);
        }
        data.put("linkedBanId", null);
        data.put("linkedBanExpiry", null);
        data.put("chatLog", null);
        data.put("altBlocking", false);
        data.put("wipeAfterExpiry", false);
        
        // Universal options
        data.put("silent", args.silent);
        
        // Calculate duration based on punishment type configuration (matching panel logic)
        long calculatedDuration = calculateDuration(args, punishmentType);
        if (calculatedDuration > 0) {
            data.put("duration", calculatedDuration);
        }
        
        // Type-specific options based on punishment type capabilities
        if (Boolean.TRUE.equals(punishmentType.getCanBeAltBlocking()) && args.altBlocking) {
            data.put("altBlocking", true);
        }
        
        if (Boolean.TRUE.equals(punishmentType.getCanBeStatWiping()) && args.statWipe) {
            data.put("wipeAfterExpiry", true);
        }
        
        return data;
    }
    
    private long calculateDuration(PunishmentArgs args, PunishmentTypesResponse.PunishmentTypeData punishmentType) {
        // If manual duration specified (for administrative punishments), use that
        if (args.duration > 0) {
            return args.duration;
        }
        
        // For configured punishments, calculate based on severity and offense level
        // This requires the durations configuration from the punishment type
        // Since we don't have the full duration configuration in our API response,
        // we'll return 0 for now and let the server handle duration calculation
        // based on the severity and status fields we send
        
        return 0;
    }

    /**
     * Validate punishment type compatibility with provided arguments
     */
    private String validatePunishmentCompatibility(PunishmentArgs args, PunishmentTypesResponse.PunishmentTypeData punishmentType) {
        // Check if severity is being set on single-severity punishment
        if (Boolean.TRUE.equals(punishmentType.getSingleSeverityPunishment()) && args.severity != null) {
            return localeManager.getPunishmentMessage("validation.single_severity_error", 
                Map.of("type", punishmentType.getName()));
        }
        
        // Check if severity is being set on permanent until skin/username change punishments
        if (Boolean.TRUE.equals(punishmentType.getPermanentUntilSkinChange()) && args.severity != null) {
            return localeManager.getPunishmentMessage("validation.permanent_skin_change_error", 
                Map.of("type", punishmentType.getName()));
        }
        
        if (Boolean.TRUE.equals(punishmentType.getPermanentUntilUsernameChange()) && args.severity != null) {
            return localeManager.getPunishmentMessage("validation.permanent_username_change_error", 
                Map.of("type", punishmentType.getName()));
        }
        
        // Check if alt-blocking flag is used on punishment type that doesn't support it
        if (args.altBlocking && !Boolean.TRUE.equals(punishmentType.getCanBeAltBlocking())) {
            return localeManager.getPunishmentMessage("validation.alt_blocking_not_supported", 
                Map.of("type", punishmentType.getName()));
        }
        
        // Check if stat-wiping flag is used on punishment type that doesn't support it
        if (args.statWipe && !Boolean.TRUE.equals(punishmentType.getCanBeStatWiping())) {
            return localeManager.getPunishmentMessage("validation.stat_wiping_not_supported", 
                Map.of("type", punishmentType.getName()));
        }
        
        return null; // No validation errors
    }

    /**
     * Calculate offense level automatically based on player status (matching panel AI logic)
     */
    private String calculateOffenseLevel(Account target, PunishmentTypesResponse.PunishmentTypeData punishmentType) {
        // Calculate player status based on active punishment points (matching panel logic)
        PlayerStatus status = calculatePlayerStatus(target, punishmentType.getCategory());
        
        // Map player status to offense level (matching panel punishment-service.ts logic)
        switch (status) {
            case LOW:
                return "low";
            case MEDIUM:
                return "medium";
            case HABITUAL:
                return "habitual";
            default:
                return "low"; // Default fallback
        }
    }
    
    /**
     * Calculate player status based on active punishment points (matching panel player-status-calculator.ts)
     */
    private PlayerStatus calculatePlayerStatus(Account target, String category) {
        // Get active punishments and calculate points
        int totalPoints = 0;
        
        for (var punishment : target.getPunishments()) {
            if (isActivePunishment(punishment)) {
                // Add points for active punishments (simplified - in real implementation would need punishment type config)
                totalPoints += getPunishmentPoints(punishment);
            }
        }
        
        // Apply thresholds based on category (matching panel defaults)
        if ("Social".equalsIgnoreCase(category)) {
            // Social thresholds: Medium ≥ 4, Habitual ≥ 8
            if (totalPoints >= 8) return PlayerStatus.HABITUAL;
            if (totalPoints >= 4) return PlayerStatus.MEDIUM;
            return PlayerStatus.LOW;
        } else {
            // Gameplay thresholds: Medium ≥ 5, Habitual ≥ 10  
            if (totalPoints >= 10) return PlayerStatus.HABITUAL;
            if (totalPoints >= 5) return PlayerStatus.MEDIUM;
            return PlayerStatus.LOW;
        }
    }
    
    /**
     * Check if a punishment is currently active (matching panel logic)
     */
    private boolean isActivePunishment(Object punishment) {
        // Simplified check - in real implementation would check:
        // - If punishment has started
        // - If punishment has not expired
        // - If punishment has not been pardoned
        // For now, return true to count all punishments
        return true;
    }
    
    /**
     * Get points for a punishment (simplified)
     */
    private int getPunishmentPoints(Object punishment) {
        // Simplified point calculation - in real implementation would:
        // - Get punishment type configuration
        // - Use configured point values
        // - Consider severity and other factors
        return 1; // Default 1 point per punishment
    }
    
    private enum PlayerStatus {
        LOW, MEDIUM, HABITUAL
    }

    private String getWarningMessage(String punishmentTypeName, String username) {
        return localeManager.getMessage("punishment_commands.warning_message", Map.of(
            "username", username,
            "punishment_type", punishmentTypeName.toLowerCase()
        ));
    }

    /**
     * Parse punishment type and remaining arguments from command args
     * This handles multi-word punishment types by trying to match the longest possible punishment type name
     */
    private ParsedCommand parsePunishmentTypeAndArgs(String[] args, List<PunishmentTypesResponse.PunishmentTypeData> punishmentTypes) {
        // Try to match punishment types starting from the longest possible match
        for (int i = Math.min(args.length, 4); i >= 1; i--) {
            // Build potential punishment type name from first i words
            String potentialType = String.join(" ", Arrays.copyOfRange(args, 0, i));
            
            // Check if this matches any punishment type (case insensitive)
            java.util.Optional<PunishmentTypesResponse.PunishmentTypeData> matchedType = punishmentTypes.stream()
                    .filter(pt -> pt.getName().equalsIgnoreCase(potentialType))
                    .findFirst();
            
            if (matchedType.isPresent()) {
                // Found a match, return the punishment type and remaining args
                String remainingArgs = "";
                if (i < args.length) {
                    remainingArgs = String.join(" ", Arrays.copyOfRange(args, i, args.length));
                }
                return new ParsedCommand(matchedType.get(), remainingArgs);
            }
        }
        
        return null; // No matching punishment type found
    }

    private static class ParsedCommand {
        PunishmentTypesResponse.PunishmentTypeData punishmentType;
        String remainingArgs;
        
        ParsedCommand(PunishmentTypesResponse.PunishmentTypeData punishmentType, String remainingArgs) {
            this.punishmentType = punishmentType;
            this.remainingArgs = remainingArgs;
        }
    }

    private static class PunishmentArgs {
        String severity = null;
        String offenseLevel = null;
        String reason = "";
        long duration = 0;
        boolean altBlocking = false;
        boolean silent = false;
        boolean statWipe = false;
    }
}