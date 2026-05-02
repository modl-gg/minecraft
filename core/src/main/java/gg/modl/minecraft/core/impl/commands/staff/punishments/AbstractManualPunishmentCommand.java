package gg.modl.minecraft.core.impl.commands.staff.punishments;

import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.util.PunishmentActionMessages;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.TimeUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractManualPunishmentCommand {
    protected final HttpClientHolder httpClientHolder;
    protected final Platform platform;
    protected final Cache cache;
    protected final LocaleManager localeManager;

    protected abstract int getOrdinal();
    protected abstract String getTypeName();
    protected abstract Set<Flag> getSupportedFlags();
    protected abstract long getDefaultDuration();

    protected ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    protected void executePunishment(CommandActor actor, Account target, String args) {
        if (target == null) {
            actor.reply(localeManager.getPunishmentMessage("general.player_not_found", mapOf()));
            return;
        }

        ParsedArgs parsed = parseArguments(args, getSupportedFlags());
        String issuerName = CommandUtil.resolveActorName(actor, cache, platform);
        String issuerId = CommandUtil.resolveActorId(actor, cache);
        String reason = parsed.reason.isEmpty() ? localeManager.getMessage("config.default_reason") : parsed.reason;

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("reason", reason);
        dataMap.put("silent", parsed.silent);
        if (parsed.flags.contains(Flag.ALT_BLOCKING)) dataMap.put("altBlocking", parsed.altBlocking);
        if (parsed.flags.contains(Flag.STAT_WIPE)) dataMap.put("wipeAfterExpiry", parsed.statWipe);
        long duration = parsed.duration > 0 ? parsed.duration : getDefaultDuration();
        if (duration > 0) dataMap.put("duration", duration);
        dataMap.put("issuedServer", actor.uniqueId() != null
            ? platform.getPlayerServer(actor.uniqueId())
            : platform.getServerName());

        PunishmentCreateRequest request = new PunishmentCreateRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            issuerId,
            reason,
            null, null,
            getOrdinal(),
            duration,
            dataMap,
            new ArrayList<>(),
            new ArrayList<>()
        );

        getHttpClient().createPunishmentWithResponse(request).thenAccept(response -> {
            if (response.isSuccess()) {
                String targetName = target.getUsernames().get(0).getUsername();

                LocaleManager.PunishmentMessageBuilder builder = localeManager.punishment()
                    .type(getTypeName())
                    .target(targetName)
                    .punishmentId(response.getPunishmentId());
                if (duration > 0) builder.duration(duration);
                actor.reply(builder.get("general.punishment_issued"));

                if (actor.uniqueId() != null && response.getPunishmentId() != null)
                    platform.runOnMainThread(() ->
                        PunishmentActionMessages.sendPunishmentActions(platform, actor.uniqueId(), response.getPunishmentId()));
            } else actor.reply(localeManager.getPunishmentMessage("general.punishment_error",
                    mapOf("error", localeManager.sanitizeErrorMessage(response.getMessage()))));
        }).exceptionally(throwable -> CommandUtil.handleApiError(actor, throwable, localeManager));
    }

    protected static ParsedArgs parseArguments(String args, Set<Flag> supportedFlags) {
        if (args == null || args.trim().isEmpty()) return new ParsedArgs(supportedFlags);
        String[] arguments = args.split(" ");
        ParsedArgs result = new ParsedArgs(supportedFlags);
        StringBuilder reasonBuilder = new StringBuilder();

        for (String arg : arguments) {
            if (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s")) result.silent = true;
            else if (supportedFlags.contains(Flag.ALT_BLOCKING) &&
                       (arg.equalsIgnoreCase("-alt-blocking") || arg.equalsIgnoreCase("-ab"))) result.altBlocking = true;
            else if (supportedFlags.contains(Flag.STAT_WIPE) &&
                       (arg.equalsIgnoreCase("-stat-wipe") || arg.equalsIgnoreCase("-sw"))) result.statWipe = true;
            else if (supportedFlags.contains(Flag.DURATION)) {
                long duration = TimeUtil.getDuration(arg);
                if (duration != -1L && result.duration == 0) result.duration = duration;
                else appendToReason(reasonBuilder, arg);
            } else appendToReason(reasonBuilder, arg);
        }

        result.reason = reasonBuilder.toString().trim();
        return result;
    }

    private static void appendToReason(StringBuilder builder, String arg) {
        if (builder.length() > 0) builder.append(" ");
        builder.append(arg);
    }

    public enum Flag {
        DURATION,
        ALT_BLOCKING,
        STAT_WIPE
    }

    protected static class ParsedArgs {
        final Set<Flag> flags;
        String reason = "";
        long duration = 0;
        boolean silent = false, altBlocking = false, statWipe = false;

        ParsedArgs(Set<Flag> flags) {
            this.flags = flags;
        }
    }
}
