package io.github._4drian3d.signedvelocity.velocity.listener;

import com.velocitypowered.api.event.AwaitingEventExecutor;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import io.github._4drian3d.signedvelocity.shared.types.QueueType;
import io.github._4drian3d.signedvelocity.shared.types.ResultType;
import io.github._4drian3d.signedvelocity.velocity.SignedVelocity;

public interface Listener<E> extends AwaitingEventExecutor<E> {
    void register();

    static void register(final ProxyServer server, final Object pluginInstance, final SignedVelocity sv) {
        final Listener<?>[] listeners = {
            new PlayerChatListener(server.getEventManager(), pluginInstance, sv),
            new PlayerCommandListener(server.getEventManager(), server.getCommandManager(), pluginInstance, sv),
            new PostPlayerCommandListener(server.getEventManager(), pluginInstance, sv),
            new PluginMessageListener(server.getEventManager(), server, pluginInstance)
        };
        for (final Listener<?> listener : listeners) {
            listener.register();
        }
    }

    default void sendAllowedData(final Player player, final ServerConnection connection, final QueueType queueType) {
        connection.sendPluginMessage(SignedVelocity.SIGNEDVELOCITY_CHANNEL, output -> {
            output.writeUTF(player.getUniqueId().toString());
            output.writeUTF(queueType.value());
            output.writeUTF(ResultType.ALLOWED.value());
        });
    }

    default void sendCancelData(final Player player, final ServerConnection connection, final QueueType queueType) {
        connection.sendPluginMessage(SignedVelocity.SIGNEDVELOCITY_CHANNEL, output -> {
            output.writeUTF(player.getUniqueId().toString());
            output.writeUTF(queueType.value());
            output.writeUTF(ResultType.CANCEL.value());
        });
    }

    default void sendModifiedData(final Player player, final ServerConnection connection, final QueueType queueType, final String modifiedString) {
        connection.sendPluginMessage(SignedVelocity.SIGNEDVELOCITY_CHANNEL, output -> {
            output.writeUTF(player.getUniqueId().toString());
            output.writeUTF(queueType.value());
            output.writeUTF(ResultType.MODIFY.value());
            output.writeUTF(modifiedString);
        });
    }
}
