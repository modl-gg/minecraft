package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.core.command.AdminOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.command.CommandActor;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@RequiredArgsConstructor @Command("modl")
public class ModlReloadCommand {
    private final LocaleManager localeManager;
    private final Runnable reloadHook;

    @Subcommand("reload")
    @Description("Reload modl.gg configuration and locale files")
    @AdminOnly
    public void reload(CommandActor actor) {
        actor.reply(localeManager.getMessage("general.reloading"));
        try {
            localeManager.reloadLocale();
            reloadHook.run();
            actor.reply(localeManager.getMessage("general.reload_success"));
        } catch (Exception e) {
            actor.reply(localeManager.getMessage("general.reload_error", mapOf("error", e.getMessage())));
        }
    }
}
