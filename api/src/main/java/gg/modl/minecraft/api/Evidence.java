package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public final class Evidence {
    @SerializedName("text")
    private String text;

    @SerializedName("url")
    private String url;

    @SerializedName("type")
    private String type;

    @SerializedName("uploadedBy")
    private String uploadedBy;

    @SerializedName("uploadedAt")
    private Date uploadedAt;

    @SerializedName("fileName")
    private String fileName;

    @SerializedName("fileType")
    private String fileType;

    @SerializedName("fileSize")
    private Long fileSize;

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @NotNull
    public String getType() {
        return type != null ? type : "link";
    }

    @NotNull
    public String getUploadedBy() {
        return uploadedBy != null ? uploadedBy : "Unknown";
    }

    @NotNull
    public Date getUploadedAt() {
        return uploadedAt != null ? uploadedAt : new Date(0);
    }

    @Nullable
    public String getFileName() {
        return fileName;
    }

    @Nullable
    public String getFileType() {
        return fileType;
    }

    @Nullable
    public Long getFileSize() {
        return fileSize;
    }

    /**
     * Get a display-friendly representation of this evidence.
     */
    public String getDisplayText() {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        if (text != null && !text.isEmpty()) {
            return text;
        }
        if (fileName != null && !fileName.isEmpty()) {
            return fileName;
        }
        return "Evidence";
    }
}
