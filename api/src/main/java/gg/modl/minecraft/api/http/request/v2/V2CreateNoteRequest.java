package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 API create note request matching backend's CreateNoteRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2CreateNoteRequest {
    private String text;
    private String issuerName;
}
