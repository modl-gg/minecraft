package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.core.support.TestAccounts;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportRenderUtilTest {
    @Test
    void getPlayerNameToleratesMissingUsernameDates() {
        Account account = TestAccounts.account(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                Arrays.asList(TestAccounts.username("oldername"), TestAccounts.username("modltarget")));

        assertEquals("modltarget", ReportRenderUtil.getPlayerName(account));
    }
}
