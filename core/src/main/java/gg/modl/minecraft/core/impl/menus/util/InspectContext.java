package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.api.Account;

@lombok.Value
public class InspectContext {
    Account account;
    int punishmentCount;
    int noteCount;

    public Account account() { return this.account; }
    public int punishmentCount() { return this.punishmentCount; }
    public int noteCount() { return this.noteCount; }
}
