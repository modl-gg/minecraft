package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
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

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractManualPunishmentCommand extends BaseCommand {
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

    protected void executePunishment(CommandIssuer sender, Account target, String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        ParsedArgs parsed = parseArguments(args, getSupportedFlags());
        String issuerName = CommandUtil.resolveIssuerName(sender, cache, platform);
        String reason = parsed.reason.isEmpty() ? localeManager.getMessage("config.default_reason") : parsed.reason;

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("reason", reason);
        dataMap.put("silent", parsed.silent);
        if (parsed.flags.contains(Flag.ALT_BLOCKING)) dataMap.put("altBlocking", parsed.altBlocking);
        if (parsed.flags.contains(Flag.STAT_WIPE)) dataMap.put("wipeAfterExpiry", parsed.statWipe);
        long duration = parsed.duration > 0 ? parsed.duration : getDefaultDuration();
        if (duration > 0) dataMap.put("duration", duration);
        dataMap.put("issuedServer", sender.isPlayer()
            ? platform.getPlayerServer(sender.getUniqueId())
            : platform.getServerName());

        PunishmentCreateRequest request = new PunishmentCreateRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
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
                sender.sendMessage(builder.get("general.punishment_issued"));

                if (sender.isPlayer() && response.getPunishmentId() != null)
                    platform.runOnMainThread(() ->
                        PunishmentActionMessages.sendPunishmentActions(platform, sender.getUniqueId(), response.getPunishmentId()));
            } else sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                    Map.of("error", localeManager.sanitizeErrorMessage(response.getMessage()))));
        }).exceptionally(throwable -> CommandUtil.handleApiError(sender, throwable, localeManager));
    }

    protected static ParsedArgs parseArguments(String args, Set<Flag> supportedFlags) {
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
        if (!builder.isEmpty()) builder.append(" ");
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
