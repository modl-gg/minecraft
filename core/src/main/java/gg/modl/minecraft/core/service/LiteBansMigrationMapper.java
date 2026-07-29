package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.migration.StreamingJsonWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LiteBansMigrationMapper {
    private LiteBansMigrationMapper() {
    }

    static StreamingJsonWriter.PlayerData toPlayerData(LiteBansMigrationRepository.PlayerRecord player) {
        return new StreamingJsonWriter.PlayerData(
                player.getMinecraftUuid(),
                convertUsernames(player.getUsernames()),
                Collections.emptyList(),
                convertIpList(player.getIpList()),
                convertPunishments(player.getPunishments()),
                null
        );
    }

    private static List<StreamingJsonWriter.UsernameEntry> convertUsernames(List<LiteBansMigrationRepository.UsernameRecord> usernames) {
        List<StreamingJsonWriter.UsernameEntry> result = new ArrayList<>();
        for (LiteBansMigrationRepository.UsernameRecord username : usernames) {
            result.add(new StreamingJsonWriter.UsernameEntry(username.getUsername(), username.getDate()));
        }
        return result;
    }

    private static List<StreamingJsonWriter.IpEntry> convertIpList(List<LiteBansMigrationRepository.IpRecord> ipList) {
        List<StreamingJsonWriter.IpEntry> result = new ArrayList<>();
        for (LiteBansMigrationRepository.IpRecord ip : ipList) {
            result.add(new StreamingJsonWriter.IpEntry(
                    ip.getIpAddress(),
                    ip.getCountry(),
                    ip.getRegion(),
                    ip.getAsn(),
                    ip.isProxy(),
                    ip.isHosting(),
                    ip.getFirstLogin(),
                    new ArrayList<>(ip.getLogins())
            ));
        }
        return result;
    }

    private static List<StreamingJsonWriter.PunishmentEntry> convertPunishments(List<LiteBansMigrationRepository.PunishmentRecord> punishments) {
        List<StreamingJsonWriter.PunishmentEntry> result = new ArrayList<>();
        for (LiteBansMigrationRepository.PunishmentRecord p : punishments) {
            result.add(new StreamingJsonWriter.PunishmentEntry(
                    p.getId(), p.getType(), p.getTypeOrdinal(), p.getReason(), p.getIssued(),
                    p.getIssuerName(), p.getDuration(), p.getStarted(), p.getData(),
                    p.getNotes(), p.getEvidence(), p.getAttachedTicketIds(), Collections.emptyList()
            ));
        }
        return result;
    }
}
