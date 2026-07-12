package gg.modl.minecraft.core.impl.menus.util;

import lombok.Value;
import lombok.experimental.Accessors;

import gg.modl.minecraft.api.Account;

@Value
@Accessors(fluent = true)
public class InspectContext {
    Account account;
    int punishmentCount;
    int noteCount;
}
