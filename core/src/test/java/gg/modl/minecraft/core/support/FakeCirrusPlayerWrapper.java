package gg.modl.minecraft.core.support;

import dev.simplix.cirrus.model.SimpleSound;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeCirrusPlayerWrapper implements CirrusPlayerWrapper {
    private final UUID uuid;
    private final List<String> messages = new CopyOnWriteArrayList<>();

    public FakeCirrusPlayerWrapper(UUID uuid) {
        this.uuid = uuid;
    }

    public List<String> messages() {
        return messages;
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public <T> T handle() {
        return null;
    }

    @Override
    public int protocolVersion() {
        return 0;
    }

    @Override
    public void play(SimpleSound sound) {
    }

    @Override
    public void sendMessage(String message) {
        messages.add(message);
    }
}
