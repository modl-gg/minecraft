package gg.modl.minecraft.api;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentActiveStateTest {

    private static final long ONE_HOUR = 3_600_000L;
    private static final long SEVEN_DAYS = 7L * 24L * ONE_HOUR;

    @Test
    void unstartedTimedBanIsNotActive() {
        Punishment punishment = ban(null, SEVEN_DAYS);

        assertFalse(punishment.isActive());
    }

    @Test
    void unstartedTimedBanHasNoEffectiveExpiry() {
        Punishment punishment = ban(null, SEVEN_DAYS);

        assertNull(punishment.getEffectiveExpiry());
    }

    @Test
    void unstartedPermanentBanIsNotActive() {
        Punishment punishment = ban(null, null);

        assertFalse(punishment.isActive());
    }

    @Test
    void unstartedTimedMuteIsNotActive() {
        Punishment punishment = mute(null, SEVEN_DAYS);

        assertFalse(punishment.isActive());
    }

    @Test
    void startedTimedBanIsActive() {
        Punishment punishment = ban(new Date(System.currentTimeMillis() - ONE_HOUR), SEVEN_DAYS);

        assertTrue(punishment.isActive());
    }

    @Test
    void startedTimedBanExpiryCountsFromStartNotNow() {
        Date started = new Date(System.currentTimeMillis() - ONE_HOUR);
        Punishment punishment = ban(started, SEVEN_DAYS);

        assertEquals(started.getTime() + SEVEN_DAYS, punishment.getEffectiveExpiry().getTime());
    }

    @Test
    void startedExpiredTimedBanIsNotActive() {
        Punishment punishment = ban(new Date(System.currentTimeMillis() - SEVEN_DAYS - ONE_HOUR), SEVEN_DAYS);

        assertFalse(punishment.isActive());
    }

    @Test
    void startedPermanentBanIsActive() {
        Punishment punishment = ban(new Date(System.currentTimeMillis() - ONE_HOUR), null);

        assertTrue(punishment.isActive());
        assertNull(punishment.getEffectiveExpiry());
    }

    private static Punishment ban(Date started, Long durationMillis) {
        return punishment(PunishmentTypeRegistry.ORDINAL_BAN, started, durationMillis);
    }

    private static Punishment mute(Date started, Long durationMillis) {
        return punishment(PunishmentTypeRegistry.ORDINAL_MUTE, started, durationMillis);
    }

    private static Punishment punishment(int typeOrdinal, Date started, Long durationMillis) {
        Punishment punishment = new Punishment();
        punishment.setTypeOrdinal(typeOrdinal);
        punishment.setStarted(started);

        Map<String, Object> data = new HashMap<>();
        if (durationMillis != null) {
            data.put("duration", durationMillis);
        }
        punishment.setDataMap(data);

        return punishment;
    }
}
