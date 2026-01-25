package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

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
            if (ip.region != null) {
                jsonWriter.name("region").value(ip.region);
            }
            if (ip.asn != null) {
                jsonWriter.name("asn").value(ip.asn);
            }
            if (ip.proxy != null) {
                jsonWriter.name("proxy").value(ip.proxy);
            }
            if (ip.hosting != null) {
                jsonWriter.name("hosting").value(ip.hosting);
            }
            // firstLogin is required by schema - should never be null, but add safety check
            if (ip.firstLogin != null) {
                jsonWriter.name("firstLogin").value(ip.firstLogin);
            } else {
                // Fallback - shouldn't happen but safety check
                jsonWriter.name("firstLogin").value("");
            }
            jsonWriter.name("logins");
            jsonWriter.beginArray();
            if (ip.logins != null) {
                for (String login : ip.logins) {
                    if (login != null) {
                        jsonWriter.value(login);
                    }
                }
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
            jsonWriter.name("typeOrdinal").value(punishment.typeOrdinal);
            jsonWriter.name("reason").value(punishment.reason != null ? punishment.reason : "No reason provided");
            jsonWriter.name("issued").value(punishment.issued != null ? punishment.issued : "");
            jsonWriter.name("issuerName").value(punishment.issuerName != null ? punishment.issuerName : "Console");
            
            if (punishment.started != null) {
                jsonWriter.name("started").value(punishment.started);
            }
            
            // Write notes array
            jsonWriter.name("notes");
            jsonWriter.beginArray();
            if (punishment.notes != null) {
                for (Map<String, Object> note : punishment.notes) {
                    jsonWriter.beginObject();
                    String text = (String) note.get("text");
                    String issuerName = (String) note.get("issuerName");
                    String date = (String) note.get("date");
                    jsonWriter.name("text").value(text != null ? text : "");
                    jsonWriter.name("issuerName").value(issuerName != null ? issuerName : "Unknown");
                    jsonWriter.name("date").value(date != null ? date : "");
                    jsonWriter.endObject();
                }
            }
            jsonWriter.endArray();
            
            // Write evidence array
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
            
            // Write attachedTicketIds array
            jsonWriter.name("attachedTicketIds");
            jsonWriter.beginArray();
            if (punishment.attachedTicketIds != null) {
                for (String ticketId : punishment.attachedTicketIds) {
                    jsonWriter.value(ticketId);
                }
            }
            jsonWriter.endArray();
            
            // Write modifications array
            jsonWriter.name("modifications");
            jsonWriter.beginArray();
            if (punishment.modifications != null) {
                for (Object modification : punishment.modifications) {
                    gson.toJson(modification, Object.class, jsonWriter);
                }
            }
            jsonWriter.endArray();
            
            // Write data map
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
        public String region;
        public String asn;
        public Boolean proxy;
        public Boolean hosting;
        public String firstLogin;
        public List<String> logins;

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
        public String id;
        public String type;
        public int typeOrdinal;
        public String reason;
        public String issued;
        public String issuerName;
        public Long duration;
        public String started;
        public Map<String, Object> data;
        public List<Map<String, Object>> notes;
        public List<Object> evidence;
        public List<String> attachedTicketIds;
        public List<Object> modifications;

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

