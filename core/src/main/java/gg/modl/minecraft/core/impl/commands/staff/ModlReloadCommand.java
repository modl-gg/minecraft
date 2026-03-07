package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor @CommandAlias("%cmd_modl")
public class ModlReloadCommand extends BaseCommand {
    private final LocaleManager localeManager;
    private final Runnable reloadHook;

    @Subcommand("reload")
    @Description("Reload modl.gg configuration and locale files")
    @Conditions("admin")
    public void reload(CommandIssuer sender) {
        sender.sendMessage(localeManager.getMessage("general.reloading"));
        try {
            localeManager.reloadLocale();
            reloadHook.run();
            sender.sendMessage(localeManager.getMessage("general.reload_success"));
        } catch (Exception e) {
            sender.sendMessage(localeManager.getMessage("general.reload_error", Map.of("error", e.getMessage())));
        }
    }
}
