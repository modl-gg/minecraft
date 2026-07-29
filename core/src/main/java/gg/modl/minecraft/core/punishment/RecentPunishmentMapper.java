package gg.modl.minecraft.core.punishment;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;

import java.util.ArrayList;
import java.util.HashMap;

public final class RecentPunishmentMapper {
    private RecentPunishmentMapper() {}

    public static Punishment toPunishment(RecentPunishmentsResponse.RecentPunishment source) {
        Punishment.Type type = null;
        if (source.getType() != null) {
            try {
                type = Punishment.Type.valueOf(source.getType());
            } catch (IllegalArgumentException ignored) {}
        }

        return Punishment.builder()
                .id(source.getId())
                .issuerName(source.getIssuerName())
                .issued(source.getIssued())
                .started(source.getStarted())
                .type(type)
                .typeOrdinal(source.getTypeOrdinal())
                .modifications(source.getModifications())
                .notes(new ArrayList<>(source.getNotes()))
                .evidence(new ArrayList<>(source.getEvidence()))
                .dataMap(new HashMap<>(source.getData()))
                .attachedTicketIds(source.getAttachedTicketIds() != null ? new ArrayList<>(source.getAttachedTicketIds()) : null)
                .build();
    }
}
