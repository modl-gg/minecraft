package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class IPAddress {
    @SerializedName("proxy")
    private final boolean proxy;

    @NotNull
    @SerializedName("ipAddress")
    private final String ipAddress;

    @NotNull
    @SerializedName("country")
    private final String country;

    @NotNull
    @SerializedName("region")
    private final String region;

    @NotNull
    @SerializedName("asn")
    private final String asn;

    @NotNull
    @SerializedName("firstLogin")
    private final Date firstLogin;

    @NotNull
    @SerializedName("logins")
    private final List<Date> logins;
}
