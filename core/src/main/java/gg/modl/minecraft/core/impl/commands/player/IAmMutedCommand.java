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
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class IAmMutedCommand extends BaseCommand {
    private static final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_DURATION = TimeUnit.MINUTES.toMillis(5);
    private static final long CLEANUP_THRESHOLD = COOLDOWN_DURATION * 2;
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

        if (!cache.isMuted(senderUuid)) {
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
        if (targetPlayer.uuid().equals(senderUuid)) {
            sender.sendMessage(localeManager.getMessage("iammuted.cannot_message_self"));
            return;
        }

        AbstractPlayer senderPlayer = platform.getAbstractPlayer(senderUuid, false);
        platform.sendMessage(targetPlayer.uuid(), localeManager.getMessage("iammuted.notification_to_target", Map.of(
            "sender", senderPlayer.username()
        )));
        sender.sendMessage(localeManager.getMessage("iammuted.success_message", Map.of(
            "target", targetPlayer.username()
        )));

        cooldowns.put(senderUuid, System.currentTimeMillis());
        cleanupExpiredCooldowns();
    }

    private boolean checkAndNotifyCooldown(CommandIssuer sender, UUID senderUuid) {
        Long lastUsed = cooldowns.get(senderUuid);
        if (lastUsed == null) return true;

        long remaining = COOLDOWN_DURATION - (System.currentTimeMillis() - lastUsed);
        if (remaining <= 0) return true;

        long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
        long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % SECONDS_PER_MINUTE;
        sender.sendMessage(localeManager.getMessage("iammuted.cooldown_message", Map.of(
            "minutes", String.valueOf(remainingMinutes),
            "seconds", String.valueOf(remainingSeconds)
        )));
        return false;
    }

    private void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> (now - entry.getValue()) > CLEANUP_THRESHOLD);
    }

    public static void clearOnDisconnect(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
