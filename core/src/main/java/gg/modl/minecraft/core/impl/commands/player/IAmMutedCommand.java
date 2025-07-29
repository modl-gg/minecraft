package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Map;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class IAmMutedCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    
    // Cooldown cache: player UUID -> last use timestamp
    private static final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_DURATION = TimeUnit.MINUTES.toMillis(5); // 5 minute cooldown
    
    @CommandCompletion("@players")
    @CommandAlias("iammuted|iam")
    @Syntax("<player> [message]")
    @Description("Send a message to another player informing them you are muted (only usable if you are actually muted)")
    public void iAmMuted(CommandIssuer sender, @Name("player") AbstractPlayer targetPlayer, @Optional @Name("message") String customMessage) {
        // Command can only be used by players
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("iammuted.only_players"));
            return;
        }
        
        UUID senderUuid = sender.getUniqueId();
        
        // Check if the player is actually muted
        if (!cache.isMuted(senderUuid)) {
            sender.sendMessage(localeManager.getMessage("iammuted.not_muted"));
            return;
        }
        
        // Check cooldown
        Long lastUsed = cooldowns.get(senderUuid);
        long currentTime = System.currentTimeMillis();
        
        if (lastUsed != null && (currentTime - lastUsed) < COOLDOWN_DURATION) {
            long remainingTime = COOLDOWN_DURATION - (currentTime - lastUsed);
            long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime);
            long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60;
            
            sender.sendMessage(localeManager.getMessage("iammuted.cooldown_message", Map.of(
                "minutes", String.valueOf(remainingMinutes),
                "seconds", String.valueOf(remainingSeconds)
            )));
            return;
        }
        
        // Check if target player exists and is online
        if (targetPlayer == null) {
            sender.sendMessage(localeManager.getMessage("iammuted.player_not_found"));
            return;
        }
        
        if (!targetPlayer.isOnline()) {
            sender.sendMessage(localeManager.getMessage("iammuted.player_not_online"));
            return;
        }
        
        // Don't allow messaging yourself
        AbstractPlayer senderPlayer = platform.getAbstractPlayer(senderUuid, false);
        if (targetPlayer.uuid().equals(senderUuid)) {
            sender.sendMessage(localeManager.getMessage("iammuted.cannot_message_self"));
            return;
        }
        
        // Create the message
        String baseMessage = localeManager.getMessage("iammuted.notification_to_target", Map.of(
            "sender", senderPlayer.username()
        ));
        
        String fullMessage;
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            fullMessage = baseMessage + "\n" + localeManager.getMessage("iammuted.custom_message_prefix") + customMessage.trim();
        } else {
            fullMessage = baseMessage;
        }
        
        // Send message to target player
        UUID targetUuid = targetPlayer.uuid();
        platform.sendMessage(targetUuid, fullMessage);
        
        // Confirm to sender
        sender.sendMessage(localeManager.getMessage("iammuted.success_message", Map.of(
            "target", targetPlayer.username()
        )));
        
        // Update cooldown
        cooldowns.put(senderUuid, currentTime);
        
        // Clean up old cooldowns to prevent memory leaks
        cleanupOldCooldowns();
    }
    
    /**
     * Clean up cooldown entries that are older than the cooldown duration
     */
    private void cleanupOldCooldowns() {
        long currentTime = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > COOLDOWN_DURATION * 2 // Clean up entries older than 2x cooldown
        );
    }
}
