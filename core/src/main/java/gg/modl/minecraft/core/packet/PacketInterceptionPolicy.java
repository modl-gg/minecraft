package gg.modl.minecraft.core.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class PacketInterceptionPolicy {
    public static final String CONFIG_KEY = "proxy.packet_interception";

    private PacketInterceptionPolicy() {}

    public static PacketInterceptionDecision decide(PacketInterceptionMode mode, ProxyPluginRegistry registry) {
        List<PipelineConflict> conflicts = detectConflicts(registry);

        if (mode == PacketInterceptionMode.DISABLED) {
            return PacketInterceptionDecision.disabled("'" + CONFIG_KEY + "' is set to disabled", conflicts);
        }
        if (conflicts.isEmpty()) {
            return PacketInterceptionDecision.enabled("no conflicting packet-rewriting plugin was detected", conflicts);
        }
        if (mode == PacketInterceptionMode.ENABLED) {
            return PacketInterceptionDecision.enabled("'" + CONFIG_KEY + "' is set to enabled, overriding the "
                    + names(conflicts) + " conflict. Players will hang during login unless you "
                    + remediations(conflicts), conflicts);
        }
        return PacketInterceptionDecision.disabled("packet rewriting by " + names(conflicts) + " is incompatible "
                + "with modl's, so proxy menus and secure-chat enforcement are off. To turn them back on, "
                + remediations(conflicts) + ", then set '" + CONFIG_KEY + ": enabled'", conflicts);
    }

    private static List<PipelineConflict> detectConflicts(ProxyPluginRegistry registry) {
        List<PipelineConflict> conflicts = new ArrayList<>();
        for (PipelineConflict conflict : PipelineConflict.known()) {
            if (registry.isPresent(conflict.getPluginId())) conflicts.add(conflict);
        }
        return Collections.unmodifiableList(conflicts);
    }

    private static String names(List<PipelineConflict> conflicts) {
        return join(conflicts, PipelineConflict::getDisplayName);
    }

    private static String remediations(List<PipelineConflict> conflicts) {
        return join(conflicts, PipelineConflict::getRemediation);
    }

    private static String join(List<PipelineConflict> conflicts, Function<PipelineConflict, String> part) {
        StringBuilder joined = new StringBuilder();
        for (int index = 0; index < conflicts.size(); index++) {
            if (index > 0) joined.append(index == conflicts.size() - 1 ? " and " : ", ");
            joined.append(part.apply(conflicts.get(index)));
        }
        return joined.toString();
    }
}
