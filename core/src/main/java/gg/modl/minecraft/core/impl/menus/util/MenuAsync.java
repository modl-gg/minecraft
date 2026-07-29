package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.core.Platform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class MenuAsync {
    private MenuAsync() {}

    public static void displayWhenLoaded(Platform platform, CompletableFuture<Void> dataFuture,
                                         CirrusPlayerWrapper player, Consumer<CirrusPlayerWrapper> displayAction) {
        dataFuture.thenRun(() -> platform.runOnMainThread(() -> displayAction.accept(player)))
                .exceptionally(throwable -> {
                    logMenuLoadFailure(platform, player, throwable);
                    platform.runOnMainThread(() -> platform.sendMessage(player.uuid(),
                            PluginServices.locale().getMessage("api_errors.panel_restarting")));
                    return null;
                });
    }

    private static void logMenuLoadFailure(Platform platform, CirrusPlayerWrapper player, Throwable throwable) {
        Throwable root = throwable;
        while (root instanceof CompletionException && root.getCause() != null) {
            root = root.getCause();
        }

        String message = root.getMessage();
        platform.getLogger().warning("Menu data load failed for " + player.uuid()
                + " (" + root.getClass().getSimpleName() + ")"
                + (message != null && !message.isEmpty() ? ": " + message : ""));

        StringWriter stackTrace = new StringWriter();
        root.printStackTrace(new PrintWriter(stackTrace));
        platform.getLogger().warning(stackTrace.toString());
    }
}
