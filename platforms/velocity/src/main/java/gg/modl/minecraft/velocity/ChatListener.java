package gg.modl.minecraft.velocity;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentMessages.MessageContext;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import net.kyori.adventure.text.Component;

public class ChatListener {
    
    private final VelocityPlatform platform;
    private final Cache cache;
    private final ChatMessageCache chatMessageCache;
    private final gg.modl.minecraft.core.locale.LocaleManager localeManager;
    
    public ChatListener(VelocityPlatform platform, Cache cache, ChatMessageCache chatMessageCache, gg.modl.minecraft.core.locale.LocaleManager localeManager) {
        this.platform = platform;
        this.cache = cache;
        this.chatMessageCache = chatMessageCache;
        this.localeManager = localeManager;
    }
    
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        // Check for pending menu chat input FIRST
        if (ChatInputManager.handleChat(event.getPlayer().getUniqueId(), event.getMessage())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            return;
        }

        // Cache the chat message (before potentially cancelling for mute)
        // Use the player's current server name for cross-server compatibility
        String serverName = event.getPlayer().getCurrentServer().isPresent() ?
            event.getPlayer().getCurrentServer().get().getServerInfo().getName() : "unknown";
        chatMessageCache.addMessage(
            serverName,
            event.getPlayer().getUniqueId().toString(),
            event.getPlayer().getUsername(),
            event.getMessage()
        );

        if (cache.isMuted(event.getPlayer().getUniqueId())) {
            // Cancel the chat event
            event.setResult(PlayerChatEvent.ChatResult.denied());
            
            // Get cached mute and send message to player
            Cache.CachedPlayerData data = cache.getCache().get(event.getPlayer().getUniqueId());
            if (data != null) {
                String muteMessage;
                if (data.getSimpleMute() != null) {
                    muteMessage = PunishmentMessages.formatMuteMessage(data.getSimpleMute(), localeManager, MessageContext.CHAT);
                } else if (data.getMute() != null) {
                    // Fallback to old punishment format
                    muteMessage = formatMuteMessage(data.getMute());
                } else {
                    muteMessage = "§cYou are muted!";
                }
                // Handle both escaped newlines and literal \n sequences
                String formattedMessage = muteMessage.replace("\\n", "\n").replace("\\\\n", "\n");
                Component muteComponent = Colors.get(formattedMessage);
                event.getPlayer().sendMessage(muteComponent);
            }
        }
    }
    
    private String formatMuteMessage(Punishment mute) {
        String reason = mute.getReason() != null ? mute.getReason() : "No reason provided";
        
        StringBuilder message = new StringBuilder();
        message.append("§cYou are muted!\n");
        message.append("§7Reason: §f").append(reason).append("\n");
        
        if (mute.getExpires() != null) {
            long timeLeft = mute.getExpires().getTime() - System.currentTimeMillis();
            if (timeLeft > 0) {
                String timeString = PunishmentMessages.formatDuration(timeLeft);
                message.append("§7Time remaining: §f").append(timeString).append("\n");
            }
        } else {
            message.append("§4This mute is permanent.\n");
        }
        
        return message.toString();
    }
    
    public ChatMessageCache getChatMessageCache() {
        return chatMessageCache;
    }
}