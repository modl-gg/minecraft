package gg.modl.minecraft.core.impl.commands.staff.punishments;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Set;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@Command("mute")
public class MuteCommand extends AbstractManualPunishmentCommand {
    public MuteCommand(HttpClientHolder httpClientHolder, Platform platform, Cache cache, LocaleManager localeManager) {
        super(httpClientHolder, platform, cache, localeManager);
    }

    @Override protected int getOrdinal() { return 1; }
    @Override protected String getTypeName() { return "mute"; }
    @Override protected long getDefaultDuration() { return 0; }
    @Override protected Set<Flag> getSupportedFlags() { return setOf(Flag.DURATION); }

    @RequiresPermission("punishment.apply.manual-mute")
    public void mute(CommandActor actor, @Named("target") Account target, @Optional @ConsumeRemaining String args) {
        executePunishment(actor, target, args);
    }
}
