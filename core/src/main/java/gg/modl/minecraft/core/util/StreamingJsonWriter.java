package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StreamingJsonWriter implements AutoCloseable {
    private final JsonWriter jsonWriter;
    private final FileWriter fileWriter;
    private final Gson gson;
    private boolean closed = false;

    public StreamingJsonWriter(File outputFile) throws IOException {
        this.fileWriter = new FileWriter(outputFile);
        this.jsonWriter = new JsonWriter(fileWriter);
        this.jsonWriter.setIndent("  ");
        this.gson = new Gson();
        
        // Start root object and players array
        jsonWriter.beginObject();
        jsonWriter.name("players");
        jsonWriter.beginArray();
    }

    public void writePlayer(PlayerData playerData) throws IOException {
        if (closed) {
            throw new IllegalStateException("Writer is closed");
        }

        // Write player object
        jsonWriter.beginObject();
        
        jsonWriter.name("minecraftUuid").value(playerData.minecraftUuid);
        
        // Usernames array
        jsonWriter.name("usernames");
        jsonWriter.beginArray();
        for (UsernameEntry username : playerData.usernames) {
            jsonWriter.beginObject();
            jsonWriter.name("username").value(username.username);
            jsonWriter.name("date").value(username.date);
            jsonWriter.endObject();
        }
        jsonWriter.endArray();
        
        // Notes array
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
        
        // IP List array
        jsonWriter.name("ipList");
        jsonWriter.beginArray();
        for (IpEntry ip : playerData.ipList) {
            jsonWriter.beginObject();
            jsonWriter.name("ipAddress").value(ip.ipAddress);
            if (ip.country != null) {
                jsonWriter.name("country").value(ip.country);
            }
            jsonWriter.name("firstLogin").value(ip.firstLogin);
            jsonWriter.name("logins");
            jsonWriter.beginArray();
            for (String login : ip.logins) {
                jsonWriter.value(login);
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
        }
        jsonWriter.endArray();
        
        // Punishments array
        jsonWriter.name("punishments");
        jsonWriter.beginArray();
        for (PunishmentEntry punishment : playerData.punishments) {
            jsonWriter.beginObject();
            jsonWriter.name("_id").value(punishment.id);
            jsonWriter.name("type").value(punishment.type);
            jsonWriter.name("type_ordinal").value(punishment.typeOrdinal);
            jsonWriter.name("reason").value(punishment.reason);
            jsonWriter.name("issued").value(punishment.issued);
            jsonWriter.name("issuerName").value(punishment.issuerName);
            
            if (punishment.duration != null) {
                jsonWriter.name("duration").value(punishment.duration);
            }
            
            if (punishment.started != null) {
                jsonWriter.name("started").value(punishment.started);
            }
            
            if (punishment.data != null && !punishment.data.isEmpty()) {
                jsonWriter.name("data");
                gson.toJson(punishment.data, Map.class, jsonWriter);
            }
            
            jsonWriter.endObject();
        }
        jsonWriter.endArray();
        
        // Optional data field
        if (playerData.data != null && !playerData.data.isEmpty()) {
            jsonWriter.name("data");
            gson.toJson(playerData.data, Map.class, jsonWriter);
        }
        
        jsonWriter.endObject();
        
        // Flush periodically to avoid buffering too much in memory
        jsonWriter.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            // End players array and root object
            jsonWriter.endArray();
            jsonWriter.endObject();
            jsonWriter.close();
            fileWriter.close();
            closed = true;
        }
    }

    public static class PlayerData {
        public String minecraftUuid;
        public List<UsernameEntry> usernames;
        public List<NoteEntry> notes;
        public List<IpEntry> ipList;
        public List<PunishmentEntry> punishments;
        public Map<String, Object> data;

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
        public String username;
        public String date;

        public UsernameEntry(String username, String date) {
            this.username = username;
            this.date = date;
        }
    }

    public static class NoteEntry {
        public String text;
        public String date;
        public String issuerName;

        public NoteEntry(String text, String date, String issuerName) {
            this.text = text;
            this.date = date;
            this.issuerName = issuerName;
        }
    }

    public static class IpEntry {
        public String ipAddress;
        public String country;
        public String firstLogin;
        public List<String> logins;

        public IpEntry(String ipAddress, String country, String firstLogin, List<String> logins) {
            this.ipAddress = ipAddress;
            this.country = country;
            this.firstLogin = firstLogin;
            this.logins = logins;
        }
    }

    public static class PunishmentEntry {
        public String id;
        public String type;
        public int typeOrdinal;
        public String reason;
        public String issued;
        public String issuerName;
        public Long duration;
        public String started;
        public Map<String, Object> data;

        public PunishmentEntry(String id, String type, int typeOrdinal, String reason, String issued,
                              String issuerName, Long duration, String started, Map<String, Object> data) {
            this.id = id;
            this.type = type;
            this.typeOrdinal = typeOrdinal;
            this.reason = reason;
            this.issued = issued;
            this.issuerName = issuerName;
            this.duration = duration;
            this.started = started;
            this.data = data;
        }
    }
}

