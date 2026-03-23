package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class IPAddress {
    private @SerializedName("ipAddress") String ipAddress;
    private @SerializedName("country") String country;
    private @SerializedName("region") String region;
    private @SerializedName("asn") String asn;
    private @SerializedName("firstLogin") Date firstLogin;
    private @SerializedName("logins") List<Date> logins;
    private @SerializedName("proxy") boolean proxy;
    private @SerializedName("hosting") boolean hosting;
}
