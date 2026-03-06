package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@NoArgsConstructor @AllArgsConstructor @Getter
public final class Evidence {
    private static final String DEFAULT_DISPLAY_TEXT = "Evidence";

    private @SerializedName("text") @Getter String text;
    private @SerializedName("url") @Getter String url;
    private @SerializedName("type") String type;
    private @SerializedName("uploadedBy") String uploadedBy;
    private @SerializedName("uploadedAt") Date uploadedAt;
    private @SerializedName("fileName") String fileName;
    private @SerializedName("fileType") String fileType;
    private @SerializedName("fileSize") Long fileSize;

    public String getDisplayText() {
        if (url != null && !url.isEmpty()) return url;
        if (text != null && !text.isEmpty()) return text;
        if (fileName != null && !fileName.isEmpty()) return fileName;
        return DEFAULT_DISPLAY_TEXT;
    }
}
