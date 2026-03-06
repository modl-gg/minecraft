package gg.modl.minecraft.core;

import co.aikar.commands.BaseCommand;

/**
 * Interface for platform-specific command registration.
 *
 * <p>Each platform (Spigot, BungeeCord, Velocity) implements this to register
 * ACF {@link BaseCommand} instances with the platform's command manager.</p>
 */
public interface PlatformCommandRegister {

    /**
     * Registers a command with the platform's command manager.
     *
     * @param command the ACF command instance to register
     */
    void register(BaseCommand command);
}
