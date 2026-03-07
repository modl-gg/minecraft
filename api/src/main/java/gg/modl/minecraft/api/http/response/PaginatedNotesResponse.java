package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Note;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class PaginatedNotesResponse {
    private List<Note> notes;
    private int totalCount;
    private int page;
    private boolean hasMore;
    private int status;
}
