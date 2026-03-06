package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        jsonWriter.beginObject();
        jsonWriter.name("players");
        jsonWriter.beginArray();
    }

    public void writePlayer(PlayerData playerData) throws IOException {
        if (closed) throw new IllegalStateException("Writer is closed");

        jsonWriter.beginObject();
        jsonWriter.name("minecraftUuid").value(playerData.minecraftUuid);

        jsonWriter.name("usernames");
        jsonWriter.beginArray();
        for (UsernameEntry username : playerData.usernames) {
            jsonWriter.beginObject();
            jsonWriter.name("username").value(username.username);
            jsonWriter.name("date").value(username.date);
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        jsonWriter.name("notes");
        jsonWriter.beginArray();
        for (NoteEntry note : playerData.notes) {
            jsonWriter.beginObject();
            jsonWriter.name("text").value(note.text);
            jsonWriter.name("date").value(note.date);
            jsonWriter.name("issuerName").value(note.issuerName);
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        jsonWriter.name("ipList");
        jsonWriter.beginArray();
        for (IpEntry ip : playerData.ipList) {
            jsonWriter.beginObject();
            jsonWriter.name("ipAddress").value(ip.ipAddress);
            if (ip.country != null) jsonWriter.name("country").value(ip.country);
            if (ip.region != null) jsonWriter.name("region").value(ip.region);
            if (ip.asn != null) jsonWriter.name("asn").value(ip.asn);
            if (ip.proxy != null) jsonWriter.name("proxy").value(ip.proxy);
            if (ip.hosting != null) jsonWriter.name("hosting").value(ip.hosting);
            jsonWriter.name("firstLogin").value(Objects.requireNonNullElse(ip.firstLogin, ""));
            jsonWriter.name("logins");
            jsonWriter.beginArray();
            if (ip.logins != null) {
                for (String login : ip.logins) {
                    if (login != null) jsonWriter.value(login);
                }
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        jsonWriter.name("punishments");
        jsonWriter.beginArray();
        for (PunishmentEntry punishment : playerData.punishments) {
            jsonWriter.beginObject();
            jsonWriter.name("_id").value(punishment.id);
            jsonWriter.name("type").value(punishment.type);
            jsonWriter.name("typeOrdinal").value(punishment.typeOrdinal);
            jsonWriter.name("reason").value(punishment.reason != null ? punishment.reason : defaultReason);
            jsonWriter.name("issued").value(punishment.issued != null ? punishment.issued : "");
            jsonWriter.name("issuerName").value(punishment.issuerName != null ? punishment.issuerName : Constants.DEFAULT_CONSOLE_NAME);
            
            if (punishment.started != null) jsonWriter.name("started").value(punishment.started);

            jsonWriter.name("notes");
            jsonWriter.beginArray();
            if (punishment.notes != null) {
                for (Map<String, Object> note : punishment.notes) {
                    jsonWriter.beginObject();
                    String text = (String) note.get("text");
                    String issuerName = (String) note.get("issuerName");
                    String date = (String) note.get("date");
                    jsonWriter.name("text").value(text != null ? text : "");
                    jsonWriter.name("issuerName").value(issuerName != null ? issuerName : Constants.UNKNOWN);
                    jsonWriter.name("date").value(date != null ? date : "");
                    jsonWriter.endObject();
                }
            }
            jsonWriter.endArray();

            jsonWriter.name("evidence");
            jsonWriter.beginArray();
            if (punishment.evidence != null) {
                for (Object evidenceItem : punishment.evidence) {
                    if (evidenceItem instanceof String) {
                        jsonWriter.value((String) evidenceItem);
                    } else {
                        gson.toJson(evidenceItem, Object.class, jsonWriter);
                    }
                }
            }
            jsonWriter.endArray();

            jsonWriter.name("attachedTicketIds");
            jsonWriter.beginArray();
            if (punishment.attachedTicketIds != null) {
                for (String ticketId : punishment.attachedTicketIds) jsonWriter.value(ticketId);
            }
            jsonWriter.endArray();

            jsonWriter.name("modifications");
            jsonWriter.beginArray();
            if (punishment.modifications != null) {
                for (Object modification : punishment.modifications) gson.toJson(modification, Object.class, jsonWriter);
            }
            jsonWriter.endArray();

            if (punishment.data != null && !punishment.data.isEmpty()) {
                jsonWriter.name("data");
                gson.toJson(punishment.data, Map.class, jsonWriter);
            }

            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        if (playerData.data != null && !playerData.data.isEmpty()) {
            jsonWriter.name("data");
            gson.toJson(playerData.data, Map.class, jsonWriter);
        }

        jsonWriter.endObject();
        jsonWriter.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        jsonWriter.endArray();
        jsonWriter.endObject();
        jsonWriter.close();
        fileWriter.close();
        closed = true;
    }

    public static class PlayerData {
        public final String minecraftUuid;
        public final List<UsernameEntry> usernames;
        public final List<NoteEntry> notes;
        public final List<IpEntry> ipList;
        public final List<PunishmentEntry> punishments;
        public final Map<String, Object> data;

        public PlayerData(String minecraftUuid, List<UsernameEntry> usernames, List<NoteEntry> notes,
                         List<IpEntry> ipList, List<PunishmentEntry> punishments, Map<String, Object> data) {
            this.minecraftUuid = minecraftUuid;
            this.usernames = usernames;
            this.notes = notes;
            this.ipList = ipList;
            this.punishments = punishments;
            this.data = data;
        }
    }

    public static class UsernameEntry {
        public final String username;
        public final String date;

        public UsernameEntry(String username, String date) {
            this.username = username;
            this.date = date;
        }
    }

    public static class NoteEntry {
        public final String text;
        public final String date;
        public final String issuerName;

        public NoteEntry(String text, String date, String issuerName) {
            this.text = text;
            this.date = date;
            this.issuerName = issuerName;
        }
    }

    public static class IpEntry {
        public final String ipAddress;
        public final String country;
        public final String region;
        public final String asn;
        public final Boolean proxy;
        public final Boolean hosting;
        public final String firstLogin;
        public final List<String> logins;

        public IpEntry(String ipAddress, String country, String region, String asn, 
                      Boolean proxy, Boolean hosting, String firstLogin, List<String> logins) {
            this.ipAddress = ipAddress;
            this.country = country;
            this.region = region;
            this.asn = asn;
            this.proxy = proxy;
            this.hosting = hosting;
            this.firstLogin = firstLogin;
            this.logins = logins;
        }
    }

    public static class PunishmentEntry {
        public final String id;
        public final String type;
        public final int typeOrdinal;
        public final String reason;
        public final String issued;
        public final String issuerName;
        public final Long duration;
        public final String started;
        public final Map<String, Object> data;
        public final List<Map<String, Object>> notes;
        public final List<Object> evidence;
        public final List<String> attachedTicketIds;
        public final List<Object> modifications;

        public PunishmentEntry(String id, String type, int typeOrdinal, String reason, String issued,
                              String issuerName, Long duration, String started, Map<String, Object> data,
                              List<Map<String, Object>> notes, List<Object> evidence, List<String> attachedTicketIds) {
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
            this.modifications = new ArrayList<>();
        }
    }
}

