package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.api.Account;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportRenderUtilTest {
    @Test
    void getPlayerName_tolerates_missing_username_dates() {
        Account account = new Account(
                "player-1",
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                List.of(
                        new Account.Username("oldername", null),
                        new Account.Username("modltarget", null)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        assertEquals("modltarget", ReportRenderUtil.getPlayerName(account));
    }
}
