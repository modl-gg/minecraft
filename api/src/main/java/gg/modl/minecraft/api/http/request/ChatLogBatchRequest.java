package gg.modl.minecraft.api.http.request;

import gg.modl.minecraft.api.http.ChatLogEntry;
import lombok.Value;

import java.util.List;

@Value
public class ChatLogBatchRequest {
    List<ChatLogEntry> entries;
}
