package io.github._4drian3d.signedvelocity.velocity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.github._4drian3d.signedvelocity.velocity.cache.ModificationCache;
import io.github._4drian3d.signedvelocity.velocity.listener.Listener;
import io.github._4drian3d.signedvelocity.velocity.packet.PacketAdapter;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public final class SignedVelocity {
    private static final boolean DEBUG = Boolean.getBoolean("io.github._4drian3d.signedvelocity.debug");
    public static final ChannelIdentifier SIGNEDVELOCITY_CHANNEL = MinecraftChannelIdentifier.create(
            "signedvelocity", "main"
    );
    private final Cache<String, ModificationCache> modificationCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.of(1, ChronoUnit.SECONDS))
            .build();

    private final ProxyServer server;
    private final Object pluginInstance;
    private final Logger logger;

    public SignedVelocity(ProxyServer server, Object pluginInstance, Logger logger) {
        this.server = server;
        this.pluginInstance = pluginInstance;
        this.logger = logger;
    }

    public void init() {
        logger.info("Starting SignedVelocity");

        Listener.register(server, pluginInstance, this);
        PacketAdapter.registerAdapter();
    }

    public Cache<String, ModificationCache> modificationCache() {
        return modificationCache;
    }

    public void logDebug(String string) {
        if (DEBUG) {
            logger.info("SIGNEDVELOCITY DEBUG | {}", string);
        }
    }

    public ProxyServer server() {
        return server;
    }

    public Object pluginInstance() {
        return pluginInstance;
    }

    public Logger logger() {
        return logger;
    }
}
