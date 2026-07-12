package gg.modl.minecraft.core;

import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import java.util.function.Consumer;

public interface PlatformCommands {
    Lamp<? extends CommandActor> buildLamp(Consumer<Lamp.Builder<? extends CommandActor>> configurator);

    default void finalizeLampRegistration(Lamp<? extends CommandActor> lamp) {}
    default void dispatchPlayerCommand(UUID uuid, String command) {}
    default void dispatchConsoleCommand(String command) {}
}
