package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.service.TicketService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BugReportCommand {
    private final TicketService ticketUtil;

    @Command("bugreport")
    @Description("Report a bug")
    @PlayerOnly
    public void bugReport(CommandActor actor) {
        ticketUtil.submitPlayerFormTicket(actor, "bug", "Bug report", null);
    }
}
