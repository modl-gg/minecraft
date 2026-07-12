package gg.modl.minecraft.core.migration;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import gg.modl.minecraft.core.util.Constants;
import lombok.Getter;
import lombok.Value;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StreamingJsonWriter implements AutoCloseable {
    private final JsonWriter jsonWriter;
    private final FileWriter fileWriter;
    private final Gson gson;
    private final String defaultReason;
    private boolean closed = false;

    public StreamingJsonWriter(File outputFile, String defaultReason) throws IOException {
        this.fileWriter = new FileWriter(outputFile);
        this.jsonWriter = new JsonWriter(fileWriter);
        this.jsonWriter.setIndent("  ");
        this.gson = new Gson();
        this.defaultReason = defaultReason;
        try {
            jsonWriter.beginObject();
            jsonWriter.name("players");
            jsonWriter.beginArray();
        } catch (IOException e) {
            try {
                jsonWriter.close();
            } catch (IOException ignored) {}
            throw e;
        }
    }

    public void writePlayer(PlayerData playerData) throws IOException {
        if (closed) throw new IllegalStateException("Writer is closed");

        jsonWriter.beginObject();
        jsonWriter.name("minecraftUuid").value(playerData.getMinecraftUuid());

        jsonWriter.name("usernames");
        jsonWriter.beginArray();
        for (UsernameEntry username : playerData.getUsernames()) {
            jsonWriter.beginObject();
            jsonWriter.name("username").value(username.getUsername());
            jsonWriter.name("date").value(username.getDate());
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        jsonWriter.name("notes");
        jsonWriter.beginArray();
        for (NoteEntry note : playerData.getNotes()) {
            jsonWriter.beginObject();
            jsonWriter.name("text").value(note.getText());
            jsonWriter.name("date").value(note.getDate());
            jsonWriter.name("issuerName").value(note.getIssuerName());
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        jsonWriter.name("ipList");
        jsonWriter.beginArray();
        for (IpEntry ip : playerData.getIpList()) {
            jsonWriter.beginObject();
            jsonWriter.name("ipAddress").value(ip.getIpAddress());
            if (ip.getCountry() != null) jsonWriter.name("country").value(ip.getCountry());
            if (ip.getRegion() != null) jsonWriter.name("region").value(ip.getRegion());
            if (ip.getAsn() != null) jsonWriter.name("asn").value(ip.getAsn());
            if (ip.getProxy() != null) jsonWriter.name("proxy").value(ip.getProxy());
            if (ip.getHosting() != null) jsonWriter.name("hosting").value(ip.getHosting());
            jsonWriter.name("firstLogin").value(ip.getFirstLogin() != null ? ip.getFirstLogin() : "");
            jsonWriter.name("logins");
            jsonWriter.beginArray();
            if (ip.getLogins() != null) {
                for (String login : ip.getLogins()) {
                    if (login != null) jsonWriter.value(login);
                }
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        jsonWriter.name("punishments");
        jsonWriter.beginArray();
        for (PunishmentEntry punishment : playerData.getPunishments()) {
            jsonWriter.beginObject();
            jsonWriter.name("_id").value(punishment.getId());
            jsonWriter.name("type").value(punishment.getType());
            jsonWriter.name("typeOrdinal").value(punishment.getTypeOrdinal());
            jsonWriter.name("reason").value(punishment.getReason() != null ? punishment.getReason() : defaultReason);
            jsonWriter.name("issued").value(punishment.getIssued() != null ? punishment.getIssued() : "");
            jsonWriter.name("issuerName").value(punishment.getIssuerName() != null ? punishment.getIssuerName() : Constants.DEFAULT_CONSOLE_NAME);
            jsonWriter.name("duration").value(punishment.getDuration());

            if (punishment.getStarted() != null) jsonWriter.name("started").value(punishment.getStarted());

            jsonWriter.name("notes");
            jsonWriter.beginArray();
            for (Map<String, Object> note : punishment.getNotes()) {
                jsonWriter.beginObject();
                String text = (String) note.get("text");
                String issuerName = (String) note.get("issuerName");
                String date = (String) note.get("date");
                jsonWriter.name("text").value(text != null ? text : "");
                jsonWriter.name("issuerName").value(issuerName != null ? issuerName : Constants.UNKNOWN);
                jsonWriter.name("date").value(date != null ? date : "");
                jsonWriter.endObject();
            }
            jsonWriter.endArray();

            jsonWriter.name("evidence");
            jsonWriter.beginArray();
            for (Object evidenceItem : punishment.getEvidence()) {
                if (evidenceItem instanceof String) {
                    jsonWriter.value((String) evidenceItem);
                } else {
                    gson.toJson(evidenceItem, Object.class, jsonWriter);
                }
            }
            jsonWriter.endArray();

            jsonWriter.name("attachedTicketIds");
            jsonWriter.beginArray();
            for (String ticketId : punishment.getAttachedTicketIds()) jsonWriter.value(ticketId);
            jsonWriter.endArray();

            jsonWriter.name("modifications");
            jsonWriter.beginArray();
            for (Object modification : punishment.getModifications()) gson.toJson(modification, Object.class, jsonWriter);
            jsonWriter.endArray();

            if (punishment.getData() != null && !punishment.getData().isEmpty()) {
                jsonWriter.name("data");
                gson.toJson(punishment.getData(), Map.class, jsonWriter);
            }

            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        if (playerData.getData() != null && !playerData.getData().isEmpty()) {
            jsonWriter.name("data");
            gson.toJson(playerData.getData(), Map.class, jsonWriter);
        }

        jsonWriter.endObject();
        jsonWriter.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            jsonWriter.endArray();
            jsonWriter.endObject();
        } finally {
            jsonWriter.close();
        }
    }

    @Value
    public static class PlayerData {
        String minecraftUuid;
        List<UsernameEntry> usernames;
        List<NoteEntry> notes;
        List<IpEntry> ipList;
        List<PunishmentEntry> punishments;
        Map<String, Object> data;
    }

    @Value
    public static class UsernameEntry {
        String username;
        String date;
    }

    @Value
    public static class NoteEntry {
        String text;
        String date;
        String issuerName;
    }

    @Value
    public static class IpEntry {
        String ipAddress;
        String country;
        String region;
        String asn;
        Boolean proxy;
        Boolean hosting;
        String firstLogin;
        List<String> logins;
    }

    @Getter
    public static class PunishmentEntry {
        private final String id, type, reason, issued, issuerName, started;
        private final Long duration;
        private final Map<String, Object> data;
        private final List<Map<String, Object>> notes;
        private final List<Object> evidence, modifications;
        private final List<String> attachedTicketIds;
        private final int typeOrdinal;

        public PunishmentEntry(String id, String type, int typeOrdinal, String reason, String issued,
                              String issuerName, Long duration, String started, Map<String, Object> data,
                              List<Map<String, Object>> notes, List<Object> evidence, List<String> attachedTicketIds,
                              List<Object> modifications) {
            this.id = id;
            this.type = type;
            this.typeOrdinal = typeOrdinal;
            this.reason = reason;
            this.issued = issued;
            this.issuerName = issuerName;
            this.duration = duration;
            this.started = started;
            this.data = data;
            this.notes = notes != null ? notes : new ArrayList<>();
            this.evidence = evidence != null ? evidence : new ArrayList<>();
            this.attachedTicketIds = attachedTicketIds != null ? attachedTicketIds : new ArrayList<>();
            this.modifications = modifications != null ? modifications : new ArrayList<>();
        }
    }
}
