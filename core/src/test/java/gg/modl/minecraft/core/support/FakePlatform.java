package gg.modl.minecraft.core.support;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.StaffAudience;
import gg.modl.minecraft.core.util.PluginLogger;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FakePlatform implements Platform {
    private final RecordingPluginLogger logger = new RecordingPluginLogger();
    private final Map<UUID, AbstractPlayer> playersByUuid = new HashMap<>();
    private final Map<String, AbstractPlayer> playersByName = new HashMap<>();
    private final Map<UUID, CirrusPlayerWrapper> wrappersByUuid = new HashMap<>();

    private final List<Runnable> scheduledTasks = new CopyOnWriteArrayList<>();
    private final AtomicInteger mainThreadScheduleCount = new AtomicInteger();
    private boolean autoRunMainThread = true;

    private final List<String> broadcasts = new CopyOnWriteArrayList<>();
    private final List<String> staffBroadcasts = new CopyOnWriteArrayList<>();
    private final List<String> staffJsonBroadcasts = new CopyOnWriteArrayList<>();
    private final List<JsonMessage> sentJsonMessages = new CopyOnWriteArrayList<>();
    private final List<Message> sentMessages = new CopyOnWriteArrayList<>();

    private String serverName = "test-server";

    public FakePlatform register(AbstractPlayer player) {
        playersByUuid.put(player.getUuid(), player);
        playersByName.put(player.getUsername(), player);
        return this;
    }

    public FakePlatform registerWrapper(UUID uuid, CirrusPlayerWrapper wrapper) {
        wrappersByUuid.put(uuid, wrapper);
        return this;
    }

    public FakePlatform withServerName(String serverName) {
        this.serverName = serverName;
        return this;
    }

    public FakePlatform autoRunMainThread(boolean autoRun) {
        this.autoRunMainThread = autoRun;
        return this;
    }

    public int mainThreadScheduleCount() {
        return mainThreadScheduleCount.get();
    }

    public void runScheduledTasks() {
        List<Runnable> pending = new ArrayList<>(scheduledTasks);
        scheduledTasks.clear();
        for (Runnable task : pending) task.run();
    }

    public List<String> broadcasts() {
        return broadcasts;
    }

    public List<String> staffBroadcasts() {
        return staffBroadcasts;
    }

    public List<String> staffJsonBroadcasts() {
        return staffJsonBroadcasts;
    }

    public String lastStaffJsonBroadcast() {
        return staffJsonBroadcasts.isEmpty() ? null : staffJsonBroadcasts.get(staffJsonBroadcasts.size() - 1);
    }

    public List<JsonMessage> sentJsonMessages() {
        return sentJsonMessages;
    }

    public String lastJson() {
        return sentJsonMessages.isEmpty() ? null : sentJsonMessages.get(sentJsonMessages.size() - 1).json();
    }

    public UUID lastJsonUuid() {
        return sentJsonMessages.isEmpty() ? null : sentJsonMessages.get(sentJsonMessages.size() - 1).uuid();
    }

    public List<Message> sentMessages() {
        return sentMessages;
    }

    public String lastMessage() {
        return sentMessages.isEmpty() ? null : sentMessages.get(sentMessages.size() - 1).message();
    }

    public RecordingPluginLogger recordingLogger() {
        return logger;
    }

    @Override
    public void broadcast(String string) {
        broadcasts.add(string);
    }

    @Override
    public void staffBroadcast(String string) {
        staffBroadcasts.add(string);
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        staffJsonBroadcasts.add(jsonMessage);
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        sentMessages.add(new Message(uuid, message));
    }

    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        sentJsonMessages.add(new JsonMessage(uuid, jsonMessage));
    }

    @Override
    public void setStaffAudience(StaffAudience staffAudience) {
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
        return wrappersByUuid.get(uuid);
    }

    @Override
    public int getMaxPlayers() {
        return 0;
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return playersByUuid.containsKey(uuid);
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        return playersByUuid.get(uuid);
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        return playersByName.get(username);
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return Collections.unmodifiableCollection(playersByUuid.values());
    }

    @Override
    public AbstractPlayer getPlayer(UUID uuid) {
        return playersByUuid.get(uuid);
    }

    @Override
    public Lamp<? extends CommandActor> buildLamp(Consumer<Lamp.Builder<? extends CommandActor>> configurator) {
        return null;
    }

    @Override
    public void runOnMainThread(Runnable task) {
        mainThreadScheduleCount.incrementAndGet();
        if (autoRunMainThread) {
            task.run();
        } else {
            scheduledTasks.add(task);
        }
    }

    @Override
    public PluginLogger getLogger() {
        return logger;
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }

    @Override
    public String getServerVersion() {
        return "test";
    }

    @Override
    public String getPlatformType() {
        return "test";
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public File getDataFolder() {
        return null;
    }

    @Override
    public DatabaseProvider createLiteBansDatabaseProvider() {
        return null;
    }

    public static final class JsonMessage {
        private final UUID uuid;
        private final String json;

        public JsonMessage(UUID uuid, String json) {
            this.uuid = uuid;
            this.json = json;
        }

        public UUID uuid() {
            return uuid;
        }

        public String json() {
            return json;
        }
    }

    public static final class Message {
        private final UUID uuid;
        private final String message;

        public Message(UUID uuid, String message) {
            this.uuid = uuid;
            this.message = message;
        }

        public UUID uuid() {
            return uuid;
        }

        public String message() {
            return message;
        }
    }
}
