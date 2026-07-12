package gg.modl.minecraft.core.impl.commands.staff.punishments;

import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.punishment.PunishmentFlagParser;
import gg.modl.minecraft.core.punishment.PunishmentIssuer;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
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

        Set<Flag> supportedFlags = getSupportedFlags();
        PunishmentFlagParser.Flags parsed = PunishmentFlagParser.builder()
            .silent(true)
            .altBlocking(supportedFlags.contains(Flag.ALT_BLOCKING))
            .statWipe(supportedFlags.contains(Flag.STAT_WIPE))
            .duration(supportedFlags.contains(Flag.DURATION))
            .build()
            .parse(args);

        String issuerName = CommandUtil.resolveActorName(actor, cache, platform);
        String issuerId = CommandUtil.resolveActorId(actor, cache);
        String reason = parsed.getReason().isEmpty() ? localeManager.getMessage("config.default_reason") : parsed.getReason();

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("reason", reason);
        dataMap.put("silent", parsed.isSilent());
        if (supportedFlags.contains(Flag.ALT_BLOCKING)) dataMap.put("altBlocking", parsed.isAltBlocking());
        if (supportedFlags.contains(Flag.STAT_WIPE)) dataMap.put("wipeAfterExpiry", parsed.isStatWipe());
        long duration = parsed.getDuration() > 0 ? parsed.getDuration() : getDefaultDuration();
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

        String targetName = target.getUsernames().get(0).getUsername();
        new PunishmentIssuer(platform, localeManager)
            .issue(actor, getHttpClient().createPunishmentWithResponse(request), getTypeName(), targetName, duration);
    }

    public enum Flag {
        DURATION,
        ALT_BLOCKING,
        STAT_WIPE
    }
}
