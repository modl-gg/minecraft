package io.github._4drian3d.signedvelocity.velocity.listener;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import io.github._4drian3d.signedvelocity.shared.types.QueueType;
import io.github._4drian3d.signedvelocity.velocity.SignedVelocity;

import java.util.Objects;

final class PlayerChatListener implements Listener<PlayerChatEvent> {
    private final EventManager eventManager;
    private final Object plugin;
    private final SignedVelocity sv;

    PlayerChatListener(EventManager eventManager, Object plugin, SignedVelocity sv) {
        this.eventManager = eventManager;
        this.plugin = plugin;
        this.sv = sv;
    }

    @Override
    public EventTask executeAsync(final PlayerChatEvent event) {
        final PlayerChatEvent.ChatResult result = event.getResult();

        return EventTask.withContinuation(continuation -> {
            final Player player = event.getPlayer();

            if (!result.isAllowed() && player.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_19_1) < 0) {
                continuation.resume();
                return;
            }

            final ServerConnection server = player.getCurrentServer().orElseThrow();

            if (result == PlayerChatEvent.ChatResult.allowed()) {
                this.sendAllowedData(player, server, QueueType.CHAT);
                continuation.resume();
                return;
            }

            //noinspection deprecation
            event.setResult(PlayerChatEvent.ChatResult.allowed());

            final String finalMessage = result.getMessage().orElse(null);

            if (finalMessage == null) {
                this.sendCancelData(player, server, QueueType.CHAT);
                continuation.resume();
                return;
            }

            if (Objects.equals(finalMessage, event.getMessage())) {
                this.sendAllowedData(player, server, QueueType.CHAT);
                continuation.resume();
                return;
            }

            this.sendModifiedData(player, server, QueueType.CHAT, finalMessage);

            continuation.resume();
        });
    }

    @Override
    public void register() {
        eventManager.register(plugin, PlayerChatEvent.class, Short.MIN_VALUE, this);
    }
}
