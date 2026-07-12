package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.util.CommandUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import revxrsal.commands.Lamp;
import revxrsal.commands.bungee.actor.ActorFactory;
import revxrsal.commands.bungee.actor.BungeeCommandActor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ConsoleAwareBungeeActor implements BungeeCommandActor {
    private final CommandSender sender;
    private final Lamp<BungeeCommandActor> lamp;

    public ConsoleAwareBungeeActor(CommandSender sender, Lamp<BungeeCommandActor> lamp) {
        this.sender = sender;
        this.lamp = lamp;
    }

    @Override
    public CommandSender sender() {
        return sender;
    }

    @Override
    public UUID uniqueId() {
        if (isPlayer()) {
            return asPlayer().getUniqueId();
        }
        if (isConsoleSender()) {
            return CommandUtil.CONSOLE_UUID;
        }
        return UUID.nameUUIDFromBytes(name().getBytes(StandardCharsets.UTF_8));
    }

    private boolean isConsoleSender() {
        return sender == ProxyServer.getInstance().getConsole();
    }

    @Override
    public Lamp<BungeeCommandActor> lamp() {
        return lamp;
    }

    public static final class Factory implements ActorFactory<BungeeCommandActor> {
        @Override
        public BungeeCommandActor create(CommandSender sender, Lamp<BungeeCommandActor> lamp) {
            return new ConsoleAwareBungeeActor(sender, lamp);
        }
    }
}
