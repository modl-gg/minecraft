package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class IPAddress {
    private @SerializedName("ipAddress") @Getter String ipAddress;
    private @SerializedName("country") @Getter String country;
    private @SerializedName("region") @Getter String region;
    private @SerializedName("asn") String asn;
    private @SerializedName("firstLogin") Date firstLogin;
    private @SerializedName("logins") List<Date> logins;
    private @SerializedName("proxy") boolean proxy;
    private @SerializedName("hosting") boolean hosting;
}
