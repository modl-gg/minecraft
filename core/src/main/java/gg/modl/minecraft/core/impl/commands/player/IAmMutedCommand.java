package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.PlayerProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class IAmMutedCommand extends BaseCommand {
    private static final long COOLDOWN_DURATION = TimeUnit.MINUTES.toMillis(5);
    private static final String COOLDOWN_KEY = "iammuted";
    private static final int SECONDS_PER_MINUTE = 60;

    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("%cmd_iammuted")
    @Syntax("<player>")
    @Description("Send a message to another player informing them you are muted (only usable if you are actually muted)")
    public void iAmMuted(CommandIssuer sender, @Name("player") AbstractPlayer targetPlayer) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("iammuted.only_players"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        PlayerProfile senderProfile = cache.getPlayerProfile(senderUuid);
        if (senderProfile == null || !senderProfile.isMuted()) {
            sender.sendMessage(localeManager.getMessage("iammuted.not_muted"));
            return;
        }
        if (!checkAndNotifyCooldown(sender, senderUuid)) return;
        if (targetPlayer == null) {
            sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            return;
        }
        if (!targetPlayer.isOnline()) {
            sender.sendMessage(localeManager.getMessage("messages.player_not_online"));
            return;
        }
        if (targetPlayer.getUuid().equals(senderUuid)) {
            sender.sendMessage(localeManager.getMessage("iammuted.cannot_message_self"));
            return;
        }

        AbstractPlayer senderPlayer = platform.getAbstractPlayer(senderUuid, false);
        platform.sendMessage(targetPlayer.getUuid(), localeManager.getMessage("iammuted.notification_to_target", Map.of(
            "sender", senderPlayer.getUsername()
        )));
        sender.sendMessage(localeManager.getMessage("iammuted.success_message", Map.of(
            "target", targetPlayer.getUsername()
        )));

        senderProfile.getCooldowns().set(COOLDOWN_KEY);
    }

    private boolean checkAndNotifyCooldown(CommandIssuer sender, UUID senderUuid) {
        PlayerProfile profile = cache.getPlayerProfile(senderUuid);
        if (profile == null) return true;

        if (!profile.getCooldowns().isOnCooldown(COOLDOWN_KEY, COOLDOWN_DURATION)) return true;

        long remaining = profile.getCooldowns().getRemainingMs(COOLDOWN_KEY, COOLDOWN_DURATION);
        long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
        long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % SECONDS_PER_MINUTE;
        sender.sendMessage(localeManager.getMessage("iammuted.cooldown_message", Map.of(
            "minutes", String.valueOf(remainingMinutes),
            "seconds", String.valueOf(remainingSeconds)
        )));
        return false;
    }

    /** @deprecated No cooldown state is destroyed with the player profile on disconnect. */
    public static void clearOnDisconnect(UUID uuid) {}
}
