package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.http.CommandLogEntry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class CommandLogsResponse {
    private List<CommandLogEntry> entries;
}
