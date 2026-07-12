package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.service.TicketService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SupportCommand {
    private final TicketService ticketUtil;

    @Command("support")
    @Description("Request support")
    @PlayerOnly
    public void supportRequest(CommandActor actor) {
        ticketUtil.submitPlayerFormTicket(actor, "support", "Support request", null);
    }
}
