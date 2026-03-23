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
import static gg.modl.minecraft.core.util.Java8Collections.*;

public class KickCommand extends AbstractManualPunishmentCommand {
    public KickCommand(HttpClientHolder httpClientHolder, Platform platform, Cache cache, LocaleManager localeManager) {
        super(httpClientHolder, platform, cache, localeManager);
    }

    @Override protected int getOrdinal() { return 0; }
    @Override protected String getTypeName() { return "kick"; }
    @Override protected long getDefaultDuration() { return 0; }
    @Override protected Set<Flag> getSupportedFlags() { return setOf(); }

    @CommandCompletion("@players")
    @CommandAlias("%cmd_kick")
    @Syntax("<target> [reason...] [-silent]")
    @Conditions("permission:value=punishment.apply.kick")
    public void kick(CommandIssuer sender, @Name("target") Account target, @Default() String args) {
        executePunishment(sender, target, args);
    }
}
