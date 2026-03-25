package io.github._4drian3d.signedvelocity.velocity.listener;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import io.github._4drian3d.signedvelocity.shared.types.QueueType;
import io.github._4drian3d.signedvelocity.velocity.SignedVelocity;
import io.github._4drian3d.signedvelocity.velocity.cache.ModificationCache;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;

final class PlayerCommandListener implements Listener<CommandExecuteEvent> {
    private final EventManager eventManager;
    private final CommandManager commandManager;
    private final Object plugin;
    private final SignedVelocity sv;

    PlayerCommandListener(EventManager eventManager, CommandManager commandManager,
                          Object plugin, SignedVelocity sv) {
        this.eventManager = eventManager;
        this.commandManager = commandManager;
        this.plugin = plugin;
        this.sv = sv;
    }

    @Override
    public void register() {
        eventManager.register(plugin, CommandExecuteEvent.class, (short) -32760, this);
    }

    @Override
    public @Nullable EventTask executeAsync(final CommandExecuteEvent event) {
        final CommandExecuteEvent.InvocationInfo invocationInfo = event.getInvocationInfo();
        if (invocationInfo.source() == CommandExecuteEvent.Source.API) return null;
        if (!(event.getCommandSource() instanceof Player player)) return null;

        return EventTask.withContinuation(continuation -> {
            final ServerConnection server = player.getCurrentServer().orElse(null);

            if (server == null) {
                sv.logDebug("Command Execution | Null Server");
                continuation.resume();
                return;
            }

            final CommandExecuteEvent.CommandResult result = event.getResult();
            final String finalCommand = result.getCommand().orElse(null);

            if (result.isForwardToServer()) {
                sv.logDebug("Command Execution | Forward to Server");
                if (finalCommand != null) {
                    sv.logDebug("Command Execution | Signed Command Executed, modified and forwarded");
                    event.setResult(CommandExecuteEvent.CommandResult.forwardToServer());
                    this.sendModifiedData(player, server, QueueType.COMMAND, finalCommand);
                    continuation.resume();
                    return;
                }
                sv.logDebug("Command Execution | Command Forwarded to server");
                this.sendAllowedData(player, server, QueueType.COMMAND);
                continuation.resume();
                return;
            }

            final boolean isProxyCommand = this.isProxyCommand(event.getCommand());
            if (result == CommandExecuteEvent.CommandResult.allowed() || Objects.equals(finalCommand, event.getCommand())) {
                sv.logDebug("Command Execution | Allowed Command");
                if (!isProxyCommand) {
                    sv.logDebug("Command Execution | Allowed non proxied command");
                }
                continuation.resume();
                return;
            }

            if (!result.isAllowed() && player.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
                sv.logDebug("Command Execution | Old player version, denied command");
                event.setResult(CommandExecuteEvent.CommandResult.denied());
                continuation.resume();
                return;
            }

            if (finalCommand == null) {
                sv.logDebug("Command Execution | Cancelled command execution");
                this.sendCancelData(player, server, QueueType.COMMAND);
                event.setResult(CommandExecuteEvent.CommandResult.forwardToServer());
                continuation.resume();
                return;
            }

            sv.logDebug("Command Execution | Modification Section");
            if (!isProxyCommand) {
                sv.logDebug("Command Execution | Non proxied command");
                this.sendModifiedData(player, server, QueueType.COMMAND, finalCommand);
                event.setResult(CommandExecuteEvent.CommandResult.forwardToServer());
                continuation.resume();
                return;
            }

            sv.logDebug("Command Execution | Modified Command sent to Velocity Command Dispatcher");

            event.setResult(CommandExecuteEvent.CommandResult.command(finalCommand));
            sv.modificationCache().put(player.getUniqueId().toString(), new ModificationCache(event.getCommand(), finalCommand));
            continuation.resume();
        });
    }

    private boolean isProxyCommand(final String command) {
        final int firstIndexOfSpace = command.indexOf(' ');

        if (firstIndexOfSpace == -1) {
            return commandManager.hasCommand(command);
        } else if (firstIndexOfSpace == 0) {
            final String[] arguments = command.split(" ");
            for (final String argument : arguments) {
                if (argument.isBlank()) continue;
                return commandManager.hasCommand(argument);
            }
            final String firstArgument = command.substring(0, firstIndexOfSpace);
            return commandManager.hasCommand(firstArgument);
        } else {
            final String firstArgument = command.substring(0, firstIndexOfSpace);
            return commandManager.hasCommand(firstArgument);
        }
    }
}
