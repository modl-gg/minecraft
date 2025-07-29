package gg.modl.minecraft.velocity;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.VelocityCommandManager;
import gg.modl.minecraft.core.PlatformCommandRegister;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class VelocityCommandRegister implements PlatformCommandRegister {
    private final VelocityCommandManager commandManager;

    @Override
    public void register(BaseCommand command) {
        commandManager.registerCommand(command);
    }
}
