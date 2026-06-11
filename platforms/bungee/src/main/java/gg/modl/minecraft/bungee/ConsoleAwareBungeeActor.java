package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.util.CommandUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import revxrsal.commands.Lamp;
import revxrsal.commands.bungee.actor.ActorFactory;
import revxrsal.commands.bungee.actor.BungeeCommandActor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A {@link BungeeCommandActor} that maps the BungeeCord console to the canonical
 * {@link CommandUtil#CONSOLE_UUID} nil UUID, so {@link CommandUtil#isConsole(UUID)} recognises it.
 *
 * <p>Lamp's default Bungee actor ({@code BasicBungeeActor}) is the odd one out across the platforms
 * modl supports. Its Bukkit, Velocity and Fabric actors resolve {@code uniqueId()} with three
 * branches - player &rarr; real UUID, console &rarr; the nil {@code CONSOLE_UUID}, any other
 * non-player &rarr; {@code UUID.nameUUIDFromBytes(name)}. The Bungee actor has only the first and
 * last branch: it returns {@code nameUUIDFromBytes("CONSOLE")} for the console, a non-nil UUID that
 * {@link CommandUtil#isConsole(UUID)} does not recognise, so console command execution was treated
 * as an unknown, permission-less player.</p>
 *
 * <p>This actor restores the missing middle branch <em>without</em> widening it: only the actual
 * console singleton ({@link ProxyServer#getConsole()}) maps to {@link CommandUtil#CONSOLE_UUID}.
 * Real players keep their Mojang/offline UUID, and any other non-player {@link CommandSender}
 * (e.g. a synthetic sender another plugin passes to {@code dispatchCommand} to capture output)
 * keeps Lamp's default per-name identity, exactly as the Bukkit/Velocity/Fabric actors do. That
 * matters because console is granted every permission and bypasses staff/2fa gates - mapping all
 * non-players to console would let any such synthetic sender run moderation commands unchecked.</p>
 */
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
        // isPlayer()/asPlayer() are BungeeCommandActor defaults derived from sender().
        if (isPlayer()) {
            return asPlayer().getUniqueId();
        }
        // Reference equality against the documented console singleton - the same object modl uses
        // to dispatch console commands. Anything else is some other non-player sender and must NOT
        // be treated as the console; fall back to Lamp's default per-name identity so it stays
        // subject to the normal permission checks.
        if (sender == ProxyServer.getInstance().getConsole()) {
            return CommandUtil.CONSOLE_UUID;
        }
        return UUID.nameUUIDFromBytes(name().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Lamp<BungeeCommandActor> lamp() {
        return lamp;
    }

    /**
     * Builds {@link ConsoleAwareBungeeActor} instances. Installed via
     * {@code BungeeLamp.builder(plugin, factory)} so every actor Lamp creates for an executed
     * command resolves the console to the nil UUID.
     */
    public static final class Factory implements ActorFactory<BungeeCommandActor> {
        @Override
        public BungeeCommandActor create(CommandSender sender, Lamp<BungeeCommandActor> lamp) {
            return new ConsoleAwareBungeeActor(sender, lamp);
        }
    }
}
