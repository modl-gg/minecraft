package gg.modl.minecraft.core.packet;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PacketInterceptionDecision {
    boolean enabled;
    String reason;
    List<PipelineConflict> conflicts;

    static PacketInterceptionDecision enabled(String reason, List<PipelineConflict> conflicts) {
        return new PacketInterceptionDecision(true, reason, conflicts);
    }

    static PacketInterceptionDecision disabled(String reason, List<PipelineConflict> conflicts) {
        return new PacketInterceptionDecision(false, reason, conflicts);
    }
}
