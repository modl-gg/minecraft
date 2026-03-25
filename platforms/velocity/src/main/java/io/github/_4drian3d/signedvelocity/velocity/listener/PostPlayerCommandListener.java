package io.github._4drian3d.signedvelocity.velocity.listener;

import com.velocitypowered.api.command.CommandResult;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.command.PostCommandInvocationEvent;
import com.velocitypowered.api.proxy.Player;
import io.github._4drian3d.signedvelocity.shared.types.QueueType;
import io.github._4drian3d.signedvelocity.velocity.SignedVelocity;
import io.github._4drian3d.signedvelocity.velocity.cache.ModificationCache;

public final class PostPlayerCommandListener implements Listener<PostCommandInvocationEvent> {
    private final EventManager eventManager;
    private final Object plugin;
    private final SignedVelocity sv;

    PostPlayerCommandListener(EventManager eventManager, Object plugin, SignedVelocity sv) {
        this.eventManager = eventManager;
        this.plugin = plugin;
        this.sv = sv;
    }

    @Override
    public void register() {
        this.eventManager.register(plugin, PostCommandInvocationEvent.class, Short.MIN_VALUE, this);
    }

    @Override
    public EventTask executeAsync(PostCommandInvocationEvent event) {
        return EventTask.async(() -> {
            if (event.getResult() == CommandResult.FORWARDED && event.getCommandSource() instanceof Player player) {
                sv.logDebug("Post Command Execution | Forwarded Command: " + event.getCommand());
                final String playerUUID = player.getUniqueId().toString();
                final ModificationCache cache = sv.modificationCache().getIfPresent(playerUUID);
                sv.modificationCache().invalidate(playerUUID);
                player.getCurrentServer()
                        .ifPresent(connection -> {
                            sv.logDebug("Post Command Execution | Server Available");
                            if (cache != null && cache.modifiedCommand().equals(event.getCommand())) {
                                sv.logDebug("Post Command Execution | Modified Command");
                                this.sendModifiedData(player, connection, QueueType.COMMAND, event.getCommand());
                            } else {
                                sv.logDebug("Post Command Execution | Non modified command");
                                sendAllowedData(player, connection, QueueType.COMMAND);
                            }
                        });
            }
        });
    }
}
