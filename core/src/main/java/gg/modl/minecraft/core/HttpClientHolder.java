package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ModlHttpClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor @Getter
public class HttpClientHolder {
    private @NotNull final ModlHttpClient client;
}
