package gg.modl.minecraft.core.impl.menus.util;

import lombok.Value;

import gg.modl.minecraft.api.Account;

@Value
public class InspectContext {
    Account account;
    int punishmentCount;
    int noteCount;

    public Account account() { return this.account; }
    public int punishmentCount() { return this.punishmentCount; }
    public int noteCount() { return this.noteCount; }
}
