package gg.modl.minecraft.bridge.freeze;

import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class FreezeCore {
    private static final String UNKNOWN_NAME = "Unknown";

    private final BridgeLocaleManager localeManager;
    private final FreezeOps ops;
    private final Map<UUID, UUID> frozenPlayerToStaff = new ConcurrentHashMap<>();

    @Setter private BridgeQueryClient bridgeClient;

    public void freeze(String targetUuid, String staffUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayerToStaff.put(target, UUID.fromString(staffUuid));
        ops.onFrozen(target);
        ops.sendMessage(target, localeManager.getMessage("freeze.frozen"));
    }

    public void unfreeze(String targetUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayerToStaff.remove(target);
        ops.onUnfrozen(target);
        ops.sendMessage(target, localeManager.getMessage("freeze.unfrozen"));
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayerToStaff.containsKey(uuid);
    }

    public boolean handleQuit(UUID uuid) {
        if (frozenPlayerToStaff.remove(uuid) == null) return false;
        String name = ops.playerName(uuid);
        if (name == null) name = UNKNOWN_NAME;
        if (bridgeClient != null) {
            bridgeClient.sendMessage(BridgeAction.FREEZE_LOGOUT.wire(), uuid.toString(), name);
        }
        ops.onUnfrozen(uuid);
        return true;
    }
}
