package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ModlHttpClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Thin wrapper around {@link ModlHttpClient} that exists for reference stability.
 * Callers (commands, services, sync engine) hold a reference to this holder rather
 * than to the client directly. This allows the underlying client instance to be
 * swapped in the future (e.g. for hot-reload or fallback scenarios) without
 * invalidating every call site, even though the field is currently final.
 */
@RequiredArgsConstructor @Getter
public class HttpClientHolder {
    private @NotNull final ModlHttpClient client;
}
