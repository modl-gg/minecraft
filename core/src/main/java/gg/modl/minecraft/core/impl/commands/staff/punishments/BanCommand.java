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
import static gg.modl.minecraft.core.util.Java8Collections.setOf;

@Command("ban")
public class BanCommand extends AbstractManualPunishmentCommand {
    public BanCommand(HttpClientHolder httpClientHolder, Platform platform, Cache cache, LocaleManager localeManager) {
        super(httpClientHolder, platform, cache, localeManager);
    }

    @Override protected int getOrdinal() { return 2; }
    @Override protected String getTypeName() { return "ban"; }
    @Override protected long getDefaultDuration() { return 0; }
    @Override protected Set<Flag> getSupportedFlags() { return setOf(Flag.DURATION, Flag.ALT_BLOCKING, Flag.STAT_WIPE); }

    @RequiresPermission("punishment.apply.manual-ban")
    public void ban(CommandActor actor, @Named("target") Account target, @Optional @ConsumeRemaining String args) {
        executePunishment(actor, target, args);
    }
}
