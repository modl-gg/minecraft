package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class IAmMutedCommand {
    private static final long COOLDOWN_DURATION = TimeUnit.MINUTES.toMillis(5);
    private static final String COOLDOWN_KEY = "iammuted";
    private static final int SECONDS_PER_MINUTE = 60;

    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @Command("iammuted")
    @Description("Send a message to another player informing them you are muted (only usable if you are actually muted)")
    @PlayerOnly
    public void iAmMuted(CommandActor actor, @Named("player") AbstractPlayer targetPlayer) {
        UUID senderUuid = actor.uniqueId();

        CachedProfile senderProfile = cache.getPlayerProfile(senderUuid);
        if (senderProfile == null || !senderProfile.isMuted()) {
            actor.reply(localeManager.getMessage("iammuted.not_muted"));
            return;
        }
        if (!checkAndNotifyCooldown(actor, senderUuid)) return;
        if (targetPlayer == null) {
            actor.reply(localeManager.getMessage("general.player_not_found"));
            return;
        }
        if (!targetPlayer.isOnline()) {
            actor.reply(localeManager.getMessage("messages.player_not_online"));
            return;
        }
        if (targetPlayer.getUuid().equals(senderUuid)) {
            actor.reply(localeManager.getMessage("iammuted.cannot_message_self"));
            return;
        }

        AbstractPlayer senderPlayer = platform.getAbstractPlayer(senderUuid, false);
        platform.sendMessage(targetPlayer.getUuid(), localeManager.getMessage("iammuted.notification_to_target", mapOf(
            "sender", senderPlayer.getUsername()
        )));
        actor.reply(localeManager.getMessage("iammuted.success_message", mapOf(
            "target", targetPlayer.getUsername()
        )));

        senderProfile.getCooldowns().set(COOLDOWN_KEY);
    }

    private boolean checkAndNotifyCooldown(CommandActor actor, UUID senderUuid) {
        CachedProfile profile = cache.getPlayerProfile(senderUuid);
        if (profile == null) return true;

        if (!profile.getCooldowns().isOnCooldown(COOLDOWN_KEY, COOLDOWN_DURATION)) return true;

        long remaining = profile.getCooldowns().getRemainingMs(COOLDOWN_KEY, COOLDOWN_DURATION);
        long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
        long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % SECONDS_PER_MINUTE;
        actor.reply(localeManager.getMessage("iammuted.cooldown_message", mapOf(
            "minutes", String.valueOf(remainingMinutes),
            "seconds", String.valueOf(remainingSeconds)
        )));
        return false;
    }
}
