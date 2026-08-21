package gg.modl.minecraft.bridge.staffmode;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketSidebar {
    private static final int SIDEBAR_DISPLAY_SLOT = 1;

    private final String objectiveName;
    private final Map<UUID, RenderedSidebar> rendered = new ConcurrentHashMap<>();

    public PacketSidebar(String objectiveName) {
        this.objectiveName = objectiveName;
    }

    public void show(UUID uuid, Object player, ScoreboardContent content) {
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || player == null) return;
        rendered.remove(uuid);
        send(api, player, objective(WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE, content.getTitle()));
        send(api, player, new WrapperPlayServerDisplayScoreboard(SIDEBAR_DISPLAY_SLOT, objectiveName));
        renderScores(api, uuid, player, content);
    }

    public void update(UUID uuid, Object player, ScoreboardContent content) {
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || player == null) return;
        RenderedSidebar previous = rendered.get(uuid);
        if (previous == null) {
            show(uuid, player, content);
            return;
        }
        if (!previous.title.equals(content.getTitle())) {
            send(api, player, objective(WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE, content.getTitle()));
        }
        renderScores(api, uuid, player, content);
    }

    public void hide(UUID uuid, Object player) {
        rendered.remove(uuid);
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || player == null) return;
        send(api, player, new WrapperPlayServerScoreboardObjective(objectiveName,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE, Component.empty(), null));
    }

    public void forget(UUID uuid) {
        rendered.remove(uuid);
    }

    public void forgetAll() {
        rendered.clear();
    }

    private void renderScores(PacketEventsAPI<?> api, UUID uuid, Object player, ScoreboardContent content) {
        RenderedSidebar previous = rendered.get(uuid);
        Map<String, Integer> scores = new HashMap<>();
        for (ScoreboardContent.Line line : content.getLines()) {
            scores.put(line.getText(), line.getScore());
            if (previous != null && Integer.valueOf(line.getScore()).equals(previous.scores.get(line.getText()))) {
                continue;
            }
            send(api, player, new WrapperPlayServerUpdateScore(line.getText(),
                    WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM, objectiveName,
                    line.getScore(), null, ScoreFormat.blankScore()));
        }
        if (previous != null) {
            for (String staleEntry : previous.scores.keySet()) {
                if (!scores.containsKey(staleEntry)) {
                    send(api, player, resetScore(api, staleEntry));
                }
            }
        }
        rendered.put(uuid, new RenderedSidebar(content.getTitle(), scores));
    }

    private PacketWrapper<?> resetScore(PacketEventsAPI<?> api, String entry) {
        if (api.getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_20_3)) {
            return new WrapperPlayServerResetScore(entry, objectiveName);
        }
        return new WrapperPlayServerUpdateScore(entry, WrapperPlayServerUpdateScore.Action.REMOVE_ITEM,
                objectiveName, Optional.<Integer>empty());
    }

    private WrapperPlayServerScoreboardObjective objective(
            WrapperPlayServerScoreboardObjective.ObjectiveMode mode, String title) {
        return new WrapperPlayServerScoreboardObjective(objectiveName, mode,
                LegacyComponentSerializer.legacySection().deserialize(title),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER, ScoreFormat.blankScore());
    }

    private static void send(PacketEventsAPI<?> api, Object player, PacketWrapper<?> packet) {
        api.getPlayerManager().sendPacket(player, packet);
    }

    private static final class RenderedSidebar {
        private final String title;
        private final Map<String, Integer> scores;

        private RenderedSidebar(String title, Map<String, Integer> scores) {
            this.title = title;
            this.scores = scores;
        }
    }
}
