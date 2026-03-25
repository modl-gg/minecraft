package io.github._4drian3d.signedvelocity.velocity.listener;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github._4drian3d.signedvelocity.velocity.SignedVelocity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Objects;

import static io.github._4drian3d.signedvelocity.velocity.SignedVelocity.SIGNEDVELOCITY_CHANNEL;

public final class PluginMessageListener implements Listener<PluginMessageEvent> {
    private final EventManager eventManager;
    private final ProxyServer proxyServer;
    private final Object plugin;

    PluginMessageListener(EventManager eventManager, ProxyServer proxyServer, Object plugin) {
        this.eventManager = eventManager;
        this.proxyServer = proxyServer;
        this.plugin = plugin;
    }

    @Override
    public void register() {
        proxyServer.getChannelRegistrar().register(SIGNEDVELOCITY_CHANNEL);
        eventManager.register(plugin, PluginMessageEvent.class, this);
    }

    @Override
    public EventTask executeAsync(PluginMessageEvent event) {
        return EventTask.async(() -> {
            if (Objects.equals(event.getIdentifier(), SIGNEDVELOCITY_CHANNEL)
                    && event.getSource() instanceof Player player) {
                player.disconnect(Component.translatable("velocity.error.internal-server-connection-error", NamedTextColor.RED));
                event.setResult(PluginMessageEvent.ForwardResult.handled());
            }
        });
    }
}
