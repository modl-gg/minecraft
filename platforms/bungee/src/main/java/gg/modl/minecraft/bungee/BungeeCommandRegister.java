package gg.modl.minecraft.bungee;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.BungeeCommandManager;
import gg.modl.minecraft.core.PlatformCommandRegister;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BungeeCommandRegister implements PlatformCommandRegister {
    private final BungeeCommandManager commandManager;

    @Override
    public void register(BaseCommand command) {
        commandManager.registerCommand(command);
    }
}
