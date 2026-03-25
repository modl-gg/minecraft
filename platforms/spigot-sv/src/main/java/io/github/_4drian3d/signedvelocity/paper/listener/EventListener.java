package io.github._4drian3d.signedvelocity.paper.listener;

import io.github._4drian3d.signedvelocity.common.queue.SignedQueue;
import io.github._4drian3d.signedvelocity.shared.logger.DebugLogger;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public interface EventListener<E extends Event> extends Listener, EventExecutor {
    @NotNull EventPriority priority();

    boolean ignoreCancelled();

    void handle(final @NotNull E event);

    @NotNull Class<E> eventClass();

    @SuppressWarnings("unchecked")
    @Override
    default void execute(final @NotNull Listener listener, final @NotNull Event event) {
        this.handle((E) event);
    }

    static void registerAll(final JavaPlugin plugin, final SignedQueue chatQueue,
                            final SignedQueue commandQueue, final DebugLogger debugLogger) {
        final PluginManager pluginManager = plugin.getServer().getPluginManager();
        final EventListener<?>[] listeners = {
            new DecorateChatListener(chatQueue),
            new PlayerChatListener(chatQueue, debugLogger),
            new PlayerCommandListener(commandQueue, debugLogger),
            new PlayerQuitListener(chatQueue, commandQueue)
        };
        for (final EventListener<?> listener : listeners) {
            pluginManager.registerEvent(
                listener.eventClass(),
                listener,
                listener.priority(),
                listener,
                plugin,
                listener.ignoreCancelled()
            );
        }
    }
}
