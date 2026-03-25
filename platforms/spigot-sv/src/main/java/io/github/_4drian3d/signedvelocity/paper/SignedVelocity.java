package io.github._4drian3d.signedvelocity.paper;

import io.github._4drian3d.signedvelocity.shared.logger.DebugLogger;
import io.github._4drian3d.signedvelocity.common.queue.SignedQueue;
import io.github._4drian3d.signedvelocity.shared.SignedConstants;
import io.github._4drian3d.signedvelocity.paper.listener.EventListener;
import io.github._4drian3d.signedvelocity.paper.listener.PluginMessagingListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

public final class SignedVelocity {
    private static SignedQueue chatQueue;
    private static SignedQueue commandQueue;
    private static DebugLogger debugLogger;

    private SignedVelocity() {}

    public static void init(JavaPlugin plugin, Logger logger) {
        chatQueue = new SignedQueue();
        commandQueue = new SignedQueue();
        debugLogger = new DebugLogger.Slf4j(logger);

        plugin.getServer()
            .getMessenger()
            .registerIncomingPluginChannel(
                plugin,
                SignedConstants.SIGNED_PLUGIN_CHANNEL,
                new PluginMessagingListener(chatQueue, commandQueue, debugLogger)
            );

        EventListener.registerAll(plugin, chatQueue, commandQueue, debugLogger);
    }

    public static SignedQueue getChatQueue() {
        return chatQueue;
    }

    public static SignedQueue getCommandQueue() {
        return commandQueue;
    }

    public static DebugLogger debugLogger() {
        return debugLogger;
    }
}
