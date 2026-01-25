package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IPAddress {
    @SerializedName("proxy")
    private boolean proxy;

    @SerializedName("hosting")
    private boolean hosting;

    @SerializedName("ipAddress")
    private String ipAddress;

    @SerializedName("country")
    private String country;

    @SerializedName("region")
    private String region;

    @SerializedName("asn")
    private String asn;

    @SerializedName("firstLogin")
    private Date firstLogin;

    @SerializedName("logins")
    private List<Date> logins;

    @NotNull
    public String getIpAddress() {
        return ipAddress != null ? ipAddress : "";
    }

    @NotNull
    public String getCountry() {
        return country != null ? country : "Unknown";
    }

    @NotNull
    public String getRegion() {
        return region != null ? region : "Unknown";
    }

    @NotNull
    public String getAsn() {
        return asn != null ? asn : "";
    }

    @NotNull
    public Date getFirstLogin() {
        return firstLogin != null ? firstLogin : new Date(0);
    }

    @NotNull
    public List<Date> getLogins() {
        return logins != null ? logins : Collections.emptyList();
    }
}
