package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Note;
import lombok.Getter;

import java.util.List;

@Getter
public class PaginatedNotesResponse extends PaginatedResponse<Note> {
    private List<Note> notes;

    public PaginatedNotesResponse() {
    }

    public PaginatedNotesResponse(List<Note> notes, int totalCount, int page, boolean hasMore, int status) {
        super(totalCount, page, hasMore, status);
        this.notes = notes;
    }

    @Override
    public List<Note> getItems() {
        return notes;
    }
}
