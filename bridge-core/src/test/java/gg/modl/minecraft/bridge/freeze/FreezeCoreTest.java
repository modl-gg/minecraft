package gg.modl.minecraft.bridge.freeze;

import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreezeCoreTest {

    private final BridgeLocaleManager localeManager = new BridgeLocaleManager(PluginLogger.fromJul(Logger.getLogger("freeze-core-test")));
    private final RecordingFreezeOps ops = new RecordingFreezeOps();
    private final FreezeCore core = new FreezeCore(localeManager, ops);

    @Test
    void freezeMarksPlayerAndNotifies() {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();

        core.freeze(target.toString(), staff.toString());

        assertTrue(core.isFrozen(target));
        assertEquals(1, ops.sent.size());
        assertEquals(target, ops.sent.get(0).uuid);
    }

    @Test
    void unfreezeClearsPlayerAndNotifies() {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        core.freeze(target.toString(), staff.toString());
        ops.sent.clear();

        core.unfreeze(target.toString());

        assertFalse(core.isFrozen(target));
        assertEquals(1, ops.sent.size());
    }

    @Test
    void releaseOnDisconnectReturnsTrueForFrozenPlayerAndClearsState() {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        ops.names.put(target, "Frozen");
        core.freeze(target.toString(), staff.toString());

        boolean wasFrozen = core.releaseOnDisconnect(target);

        assertTrue(wasFrozen);
        assertFalse(core.isFrozen(target));
    }

    @Test
    void releaseOnDisconnectReturnsFalseForUnfrozenPlayer() {
        assertFalse(core.releaseOnDisconnect(UUID.randomUUID()));
    }

    @Test
    void freezeInvokesOnFrozenHook() {
        UUID target = UUID.randomUUID();

        core.freeze(target.toString(), UUID.randomUUID().toString());

        assertEquals(Arrays.asList("onFrozen:" + target), ops.hookEvents);
    }

    @Test
    void unfreezeInvokesOnUnfrozenHook() {
        UUID target = UUID.randomUUID();
        core.freeze(target.toString(), UUID.randomUUID().toString());
        ops.hookEvents.clear();

        core.unfreeze(target.toString());

        assertEquals(Arrays.asList("onUnfrozen:" + target), ops.hookEvents);
    }

    @Test
    void releaseOnDisconnectInvokesOnUnfrozenHookAfterReadingName() {
        UUID target = UUID.randomUUID();
        ops.names.put(target, "Frozen");
        core.freeze(target.toString(), UUID.randomUUID().toString());
        ops.hookEvents.clear();

        core.releaseOnDisconnect(target);

        assertEquals(Arrays.asList("onUnfrozen:" + target), ops.hookEvents);
    }

    @Test
    void repeatedFreezeDoesNotReapplyHooksOrNotify() {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();

        core.freeze(target.toString(), staff.toString());
        core.freeze(target.toString(), UUID.randomUUID().toString());

        assertTrue(core.isFrozen(target));
        assertEquals(1, ops.sent.size());
        assertEquals(Arrays.asList("onFrozen:" + target), ops.hookEvents);
    }

    @Test
    void unfreezingAPlayerWhoIsNotFrozenDoesNothing() {
        UUID target = UUID.randomUUID();

        core.unfreeze(target.toString());

        assertFalse(core.isFrozen(target));
        assertTrue(ops.sent.isEmpty());
        assertTrue(ops.hookEvents.isEmpty());
    }

    @Test
    void refreezeAfterUnfreezeCapturesFreshAnchor() {
        UUID target = UUID.randomUUID();

        core.freeze(target.toString(), UUID.randomUUID().toString());
        core.unfreeze(target.toString());
        core.freeze(target.toString(), UUID.randomUUID().toString());

        assertEquals(Arrays.asList(
                "onFrozen:" + target,
                "onUnfrozen:" + target,
                "onFrozen:" + target), ops.hookEvents);
        assertTrue(core.isFrozen(target));
    }

    private static final class RecordingFreezeOps implements FreezeOps {
        final Map<UUID, String> names = new HashMap<>();
        final List<Sent> sent = new ArrayList<>();
        final List<String> hookEvents = new ArrayList<>();

        @Override
        public String playerName(UUID uuid) {
            return names.get(uuid);
        }

        @Override
        public void sendMessage(UUID uuid, String message) {
            sent.add(new Sent(uuid, message));
        }

        @Override
        public void onFrozen(UUID target) {
            hookEvents.add("onFrozen:" + target);
        }

        @Override
        public void onUnfrozen(UUID target) {
            hookEvents.add("onUnfrozen:" + target);
        }
    }

    private static final class Sent {
        final UUID uuid;
        final String message;

        Sent(UUID uuid, String message) {
            this.uuid = uuid;
            this.message = message;
        }
    }
}
