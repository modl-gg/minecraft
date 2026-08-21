package gg.modl.minecraft.bridge;

import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class BridgeReloadPresenter {
    private final BridgeLocaleManager localeManager;
    private final Supplier<BridgeReloadResult> reloadHook;

    public BridgeReloadPresenter(BridgeLocaleManager localeManager, Supplier<BridgeReloadResult> reloadHook) {
        this.localeManager = localeManager;
        this.reloadHook = reloadHook;
    }

    public void reload(Consumer<String> output) {
        output.accept(localeManager.getMessage("command.modlbridge.reloading"));
        for (String message : describe(reloadHook.get())) {
            output.accept(message);
        }
    }

    public String usage() {
        return localeManager.getMessage("command.modlbridge.usage");
    }

    public String noPermission() {
        return localeManager.getMessage("command.modlbridge.no_permission");
    }

    private List<String> describe(BridgeReloadResult result) {
        List<String> messages = new ArrayList<>();
        messages.add(result.isSuccessful()
                ? localeManager.getMessage("command.modlbridge.reloaded")
                : localeManager.getMessage("command.modlbridge.reload_failed",
                        mapOf("files", result.describeFailures())));
        if (result.needsRestart()) {
            messages.add(localeManager.getMessage("command.modlbridge.restart_required",
                    mapOf("keys", result.describeRestartRequired())));
        }
        return messages;
    }
}
