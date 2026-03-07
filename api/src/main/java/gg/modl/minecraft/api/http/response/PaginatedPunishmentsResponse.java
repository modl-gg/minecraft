package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Punishment;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class PaginatedPunishmentsResponse {
    private List<Punishment> punishments;
    private int totalCount;
    private int page;
    private boolean hasMore;
    private int status;
}
