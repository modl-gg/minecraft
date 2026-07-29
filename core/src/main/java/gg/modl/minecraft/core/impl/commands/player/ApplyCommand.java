package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.service.TicketService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ApplyCommand {
    private final TicketService ticketUtil;

    @Command("apply")
    @Description("Submit a staff application")
    @PlayerOnly
    public void staffApplication(CommandActor actor) {
        ticketUtil.submitPlayerFormTicket(actor, "staff", "Staff application", "Application: ");
    }
}
