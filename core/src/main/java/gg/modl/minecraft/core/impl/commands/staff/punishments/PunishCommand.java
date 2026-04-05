package gg.modl.minecraft.core.impl.commands.staff.punishments;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.command.CommandActor;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.api.http.response.PunishmentCreateResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.inspect.PunishMenu;
import gg.modl.minecraft.core.util.PunishmentActionMessages;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentTypeParser;
import gg.modl.minecraft.core.util.StaffPermissionLoader;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
@Command("punish")
public class PunishCommand {
    private static final Map<String, String> SEVERITY_ALIASES = mapOf(
        "lenient", "low",
        "normal", "regular",
        "regular", "regular",
        "aggravated", "severe",
        "severe", "severe",
        "low", "low"
    );

    private static final Set<String> VALID_SEVERITIES = setOf("low", "regular", "severe");
    private static final String DEFAULT_SEVERITY = "regular";
    private static final int MANUAL_PUNISHMENT_MAX_ORDINAL = 5, MAX_TYPE_WORD_LENGTH = 4;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private volatile List<PunishmentTypesResponse.PunishmentTypeData> cachedPunishmentTypes = new ArrayList<>();
    private volatile boolean cacheInitialized = false;

    @Description("Issue a punishment to a player. With no type specified and as a player, opens the punishment GUI.")
    @StaffOnly
    public void punish(CommandActor actor, @Named("target") Account target, @Named("args") String[] args) {
        if (target == null) {
            actor.reply(localeManager.getPunishmentMessage("general.player_not_found", mapOf()));
            return;
        }

        if ((args == null || args.length == 0) && actor.uniqueId() != null) {
            openPunishmentGui(actor, target);
            return;
        }

        if (args == null || args.length == 0) {
            actor.reply(localeManager.getPunishmentMessage("general.invalid_syntax", mapOf()));
            return;
        }

        if (!cacheInitialized || cachedPunishmentTypes.isEmpty()) {
            actor.reply(localeManager.getPunishmentMessage("general.punishment_types_not_loaded", mapOf()));
            return;
        }

        List<PunishmentTypesResponse.PunishmentTypeData> punishmentTypes = cachedPunishmentTypes;
        ParsedCommand parsed = parsePunishmentTypeAndArgs(args, punishmentTypes);
        if (parsed == null) {
            String availableTypes = punishmentTypes.stream()
                    .map(PunishmentTypesResponse.PunishmentTypeData::getName)
                    .collect(Collectors.joining(", "));
            actor.reply(localeManager.getPunishmentMessage("general.invalid_punishment_type",
                mapOf("types", availableTypes)));
            return;
        }

        final PunishmentTypesResponse.PunishmentTypeData punishmentType = parsed.punishmentType;
        String punishmentPermission = PermissionUtil.formatPunishmentPermission(punishmentType.getName());
        if (!PermissionUtil.hasPermission(actor, cache, punishmentPermission)) {
            actor.reply(localeManager.getPunishmentMessage("general.no_permission_punishment",
                mapOf("type", punishmentType.getName())));
            return;
        }

        PunishmentArgs punishmentArgs = parseArguments(parsed.remainingArgs);
        if (punishmentArgs.severity != null && !VALID_SEVERITIES.contains(punishmentArgs.severity)) {
            actor.reply(localeManager.getMessage("punishment_commands.invalid_severity"));
            return;
        }

        String validationError = validatePunishmentCompatibility(punishmentArgs, punishmentType);
        if (validationError != null) {
            actor.reply(validationError);
            return;
        }

        if (punishmentArgs.severity == null) punishmentArgs.severity = DEFAULT_SEVERITY;
        final String issuerName = CommandUtil.resolveActorName(actor, cache, platform);
        final String issuerId = CommandUtil.resolveActorId(actor, cache);
        Map<String, Object> data = buildPunishmentData(punishmentArgs, punishmentType, target);

        data.put("issuedServer", actor.uniqueId() != null
            ? platform.getPlayerServer(actor.uniqueId())
            : platform.getServerName());

        List<String> notes = new ArrayList<>();
        if (!punishmentArgs.reason.isEmpty()) notes.add(punishmentArgs.reason);

        PunishmentCreateRequest request = new PunishmentCreateRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            issuerId,
            punishmentArgs.reason.isEmpty() ? localeManager.getMessage("config.default_reason") : punishmentArgs.reason,
            punishmentArgs.severity,
            null,
            punishmentType.getOrdinal(),
            punishmentArgs.duration > 0 ? punishmentArgs.duration : null,
            data,
            notes,
            new ArrayList<>()
        );

        final String punishmentTypeName = punishmentType.getName();

        CompletableFuture<PunishmentCreateResponse> future = httpClientHolder.getClient().createPunishmentWithResponse(request);

        future.thenAccept(response -> {
            if (response.isSuccess()) {
                String targetName = target.getUsernames().get(0).getUsername();

                actor.reply(localeManager.punishment()
                    .type(punishmentTypeName)
                    .target(targetName)
                    .punishmentId(response.getPunishmentId())
                    .get("general.punishment_issued"));

                if (actor.uniqueId() != null && response.getPunishmentId() != null)
                    platform.runOnMainThread(() ->
                        PunishmentActionMessages.sendPunishmentActions(platform, actor.uniqueId(), response.getPunishmentId()));
            } else actor.reply(localeManager.getPunishmentMessage("general.punishment_error",
                    mapOf("error", localeManager.sanitizeErrorMessage(response.getMessage()))));
        }).exceptionally(throwable -> CommandUtil.handleApiError(actor, throwable, localeManager));
    }

    private void openPunishmentGui(CommandActor actor, Account target) {
        UUID senderUuid = actor.uniqueId();
        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);

        PunishMenu menu = new PunishMenu(
            platform, httpClientHolder.getClient(), senderUuid, senderName, target, null
        );
        CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
        menu.display(player);
    }

    public void initializePunishmentTypes() {
        httpClientHolder.getClient().getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) {
                PunishmentTypeParser.populateRegistry(response.getData());
                cachedPunishmentTypes = response.getData().stream()
                        .filter(pt -> pt.getOrdinal() > MANUAL_PUNISHMENT_MAX_ORDINAL)
                        .collect(Collectors.toList());
                cacheInitialized = true;
                platform.runOnMainThread(() ->
                    platform.log("Loaded " + cachedPunishmentTypes.size() + " punishment types from API"));
            } else {
                platform.runOnMainThread(() ->
                    platform.log("Failed to load punishment types from API: " + response.getStatus()));
            }
        }).exceptionally(throwable -> {
            platform.runOnMainThread(() ->
                platform.log("Error loading punishment types: " + throwable.getMessage()));
            return null;
        });

        StaffPermissionLoader.load(
            httpClientHolder.getClient(), cache, platform.getLogger(), false, true);
    }

    public List<String> getPunishmentTypeNames() {
        return cachedPunishmentTypes.stream()
                .map(PunishmentTypesResponse.PunishmentTypeData::getName)
                .collect(Collectors.toList());
    }

    public void updatePunishmentTypesCache(List<PunishmentTypesResponse.PunishmentTypeData> allTypes) {
        PunishmentTypeParser.populateRegistry(allTypes);
        cachedPunishmentTypes = allTypes.stream()
                .filter(pt -> pt.getOrdinal() > MANUAL_PUNISHMENT_MAX_ORDINAL)
                .collect(Collectors.toList());
        cacheInitialized = true;
    }

    private PunishmentArgs parseArguments(String args) {
        String[] arguments = args.split(" ");
        PunishmentArgs result = new PunishmentArgs();
        StringBuilder reasonBuilder = new StringBuilder();

        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];

            if (arg.equalsIgnoreCase("-severity") && i + 1 < arguments.length) {
                String severityInput = arguments[++i].toLowerCase();
                result.severity = SEVERITY_ALIASES.getOrDefault(severityInput, severityInput);
            } else if (arg.equalsIgnoreCase("-lenient")) result.severity = "low";
            else if (arg.equalsIgnoreCase("-regular") || arg.equalsIgnoreCase("-normal")) result.severity = DEFAULT_SEVERITY;
            else if (arg.equalsIgnoreCase("-severe")) result.severity = "severe";
            else if (arg.equalsIgnoreCase("-alt-blocking") || arg.equalsIgnoreCase("-ab")) result.altBlocking = true;
            else if (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s")) result.silent = true;
            else if (arg.equalsIgnoreCase("-stat-wipe") || arg.equalsIgnoreCase("-sw")) result.statWipe = true;
            else {
                if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
                reasonBuilder.append(arg);
            }
        }

        result.reason = reasonBuilder.toString().trim();
        return result;
    }

    private Map<String, Object> buildPunishmentData(PunishmentArgs args, PunishmentTypesResponse.PunishmentTypeData punishmentType, Account target) {
        Map<String, Object> data = new HashMap<>();
        data.put("duration", 0L);
        data.put("blockedName", resolveBlockedName(punishmentType, target));
        data.put("blockedSkin", resolveBlockedSkin(punishmentType, target));
        data.put("linkedBanId", null);
        data.put("linkedBanExpiry", null);
        data.put("chatLog", null);
        data.put("altBlocking", Boolean.TRUE.equals(punishmentType.getCanBeAltBlocking()) && args.altBlocking);
        data.put("wipeAfterExpiry", Boolean.TRUE.equals(punishmentType.getCanBeStatWiping()) && args.statWipe);
        data.put("silent", args.silent);

        if (args.duration > 0) data.put("duration", args.duration);

        return data;
    }

    private String resolveBlockedName(PunishmentTypesResponse.PunishmentTypeData punishmentType, Account target) {
        if (!Boolean.TRUE.equals(punishmentType.getPermanentUntilUsernameChange())) return null;
        return !target.getUsernames().isEmpty()
            ? target.getUsernames().get(target.getUsernames().size() - 1).getUsername()
            : Constants.UNKNOWN;
    }

    private String resolveBlockedSkin(PunishmentTypesResponse.PunishmentTypeData punishmentType, Account target) {
        if (!Boolean.TRUE.equals(punishmentType.getPermanentUntilSkinChange())) return null;
        try {
            WebPlayer webPlayer = WebPlayer.getSync(target.getMinecraftUuid());
            return (webPlayer != null && webPlayer.isValid()) ? webPlayer.getSkin() : null;
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("modl").warning(
                "Failed to get skin hash for " + target.getUsernames().get(0).getUsername() + ": " + e.getMessage());
            return null;
        }
    }

    private String validatePunishmentCompatibility(PunishmentArgs args, PunishmentTypesResponse.PunishmentTypeData punishmentType) {
        if (Boolean.TRUE.equals(punishmentType.getSingleSeverityPunishment()) && args.severity != null)
            return localeManager.getPunishmentMessage("validation.single_severity_error",
                mapOf("type", punishmentType.getName()));
        if (Boolean.TRUE.equals(punishmentType.getPermanentUntilSkinChange()) && args.severity != null)
            return localeManager.getPunishmentMessage("validation.permanent_skin_change_error",
                mapOf("type", punishmentType.getName()));
        if (Boolean.TRUE.equals(punishmentType.getPermanentUntilUsernameChange()) && args.severity != null)
            return localeManager.getPunishmentMessage("validation.permanent_username_change_error",
                mapOf("type", punishmentType.getName()));
        if (args.altBlocking && !Boolean.TRUE.equals(punishmentType.getCanBeAltBlocking()))
            return localeManager.getPunishmentMessage("validation.alt_blocking_not_supported",
                mapOf("type", punishmentType.getName()));
        if (args.statWipe && !Boolean.TRUE.equals(punishmentType.getCanBeStatWiping()))
            return localeManager.getPunishmentMessage("validation.stat_wiping_not_supported",
                mapOf("type", punishmentType.getName()));
        return null;
    }

    private ParsedCommand parsePunishmentTypeAndArgs(String[] args, List<PunishmentTypesResponse.PunishmentTypeData> punishmentTypes) {
        for (int i = Math.min(args.length, MAX_TYPE_WORD_LENGTH); i >= 1; i--) {
            String potentialType = String.join(" ", Arrays.copyOfRange(args, 0, i));

            java.util.Optional<PunishmentTypesResponse.PunishmentTypeData> matchedType = punishmentTypes.stream()
                    .filter(pt -> pt.getName().equalsIgnoreCase(potentialType))
                    .findFirst();

            if (matchedType.isPresent()) {
                String remainingArgs = (i < args.length) ? String.join(" ", Arrays.copyOfRange(args, i, args.length)) : "";
                return new ParsedCommand(matchedType.get(), remainingArgs);
            }
        }
        return null;
    }

    private static class ParsedCommand {
        final PunishmentTypesResponse.PunishmentTypeData punishmentType;
        final String remainingArgs;

        ParsedCommand(PunishmentTypesResponse.PunishmentTypeData punishmentType, String remainingArgs) {
            this.punishmentType = punishmentType;
            this.remainingArgs = remainingArgs;
        }
    }

    private static class PunishmentArgs {
        String severity = null, reason = "";
        long duration = 0;
        boolean altBlocking = false, silent = false, statWipe = false;
    }
}
