package gg.modl.minecraft.api.http.request;

import gg.modl.minecraft.api.http.CommandLogEntry;
import lombok.Value;

import java.util.List;

@Value
public class CommandLogBatchRequest {
    List<CommandLogEntry> entries;
}
