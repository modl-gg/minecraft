package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ApplyCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @Command("apply")
    @Description("Submit a staff application")
    @PlayerOnly
    public void staffApplication(CommandActor actor) {
        ticketUtil.submitPlayerFormTicket(actor, httpClient, platform, localeManager, panelUrl,
                "staff", "Staff application", "Application: ");
    }
}
