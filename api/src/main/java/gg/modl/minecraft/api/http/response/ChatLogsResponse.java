package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.http.ChatLogEntry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class ChatLogsResponse {
    private List<ChatLogEntry> entries;
}
