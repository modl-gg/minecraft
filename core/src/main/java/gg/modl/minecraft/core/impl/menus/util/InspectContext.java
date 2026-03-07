package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.api.Account;

public record InspectContext(Account account, int punishmentCount, int noteCount) {
}
