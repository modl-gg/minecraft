package gg.modl.minecraft.core.impl.commands.staff.punishments;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Set;

public class BlacklistCommand extends AbstractManualPunishmentCommand {
    public BlacklistCommand(HttpClientHolder httpClientHolder, Platform platform, Cache cache, LocaleManager localeManager) {
        super(httpClientHolder, platform, cache, localeManager);
    }

    @Override protected int getOrdinal() { return 5; }
    @Override protected String getTypeName() { return "blacklist"; }
    @Override protected long getDefaultDuration() { return 0; }
    @Override protected Set<Flag> getSupportedFlags() { return Set.of(Flag.ALT_BLOCKING, Flag.STAT_WIPE); }

    @CommandCompletion("@players")
    @CommandAlias("%cmd_blacklist")
    @Syntax("<target> [reason...] [-silent] [-alt-blocking] [-stat-wipe]")
    @Conditions("permission:value=punishment.apply.blacklist")
    public void blacklist(CommandIssuer sender, @Name("target") Account target, @Default() String args) {
        executePunishment(sender, target, args);
    }
}
