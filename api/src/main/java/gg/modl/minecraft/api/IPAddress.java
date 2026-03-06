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
    private static final String UNKNOWN = "Unknown";

    private @SerializedName("proxy") boolean proxy;

    private @SerializedName("hosting") boolean hosting;

    private @SerializedName("ipAddress") String ipAddress;

    private @SerializedName("country") String country;

    private @SerializedName("region") String region;

    private @SerializedName("asn") String asn;

    private @SerializedName("firstLogin") Date firstLogin;

    private @SerializedName("logins") List<Date> logins;

    public @NotNull String getIpAddress() {
        return ipAddress != null ? ipAddress : "";
    }

    public @NotNull String getCountry() {
        return country != null ? country : UNKNOWN;
    }

    public @NotNull String getRegion() {
        return region != null ? region : UNKNOWN;
    }

    public @NotNull String getAsn() {
        return asn != null ? asn : "";
    }

    public @NotNull Date getFirstLogin() {
        return firstLogin != null ? firstLogin : new Date(0);
    }

    public @NotNull List<Date> getLogins() {
        return logins != null ? logins : Collections.emptyList();
    }
}
