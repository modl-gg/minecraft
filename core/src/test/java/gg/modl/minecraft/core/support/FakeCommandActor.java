package gg.modl.minecraft.core.support;

import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeCommandActor implements CommandActor {
    private final UUID uuid;
    private final String name;
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();

    public FakeCommandActor(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public List<String> messages() {
        return messages;
    }

    public String lastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public List<String> errors() {
        return errors;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public UUID uniqueId() {
        return uuid;
    }

    @Override
    public void reply(String message) {
        messages.add(message);
    }

    @Override
    public void sendRawMessage(String message) {
        messages.add(message);
    }

    @Override
    public void sendRawError(String message) {
        errors.add(message);
    }

    @Override
    public Lamp<?> lamp() {
        return null;
    }
}
