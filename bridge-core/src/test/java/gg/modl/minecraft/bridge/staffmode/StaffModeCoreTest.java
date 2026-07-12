package gg.modl.minecraft.bridge.staffmode;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.freeze.FreezeCore;
import gg.modl.minecraft.bridge.freeze.FreezeOps;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffModeCoreTest {

    private static final Logger LOGGER = Logger.getLogger("staff-mode-core-test");

    @TempDir
    Path tempDir;

    private final FakeStaffModeOps ops = new FakeStaffModeOps();
    private final BridgeLocaleManager localeManager = new BridgeLocaleManager(LOGGER);

    private StaffModeCore newCore(StaffModeConfig config) {
        FreezeCore freezeCore = new FreezeCore(localeManager, new NoOpFreezeOps());
        return new StaffModeCore(new BridgeConfig(), config, localeManager,
                new ImmediateBridgeScheduler(), freezeCore, ops);
    }

    @Test
    void enterStaffModeSavesSnapshotAndSetsCreative() throws IOException {
        StaffModeConfig config = config("vanish_on_enable: false");
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        ops.connect(staff, "Staffer");

        core.enterStaffMode(staff.toString());

        assertTrue(core.isInStaffMode(staff));
        assertTrue(ops.hasSnapshot(staff));
        assertEquals(1, ops.saved.size());
        assertTrue(ops.gameModes.contains(StaffGameMode.CREATIVE));
    }

    @Test
    void offlineExitRestoresSnapshotOnRejoin() throws IOException {
        StaffModeConfig config = config("vanish_on_enable: false");
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        ops.connect(staff, "Staffer");
        core.enterStaffMode(staff.toString());

        ops.online.remove(staff);
        core.exitStaffMode(staff.toString());

        assertFalse(core.isInStaffMode(staff));
        assertTrue(ops.hasSnapshot(staff));
        assertTrue(ops.restored.isEmpty());

        ops.online.add(staff);
        core.handlePlayerJoin(staff);

        assertTrue(ops.restored.contains(staff));
        assertFalse(ops.hasSnapshot(staff));
    }

    @Test
    void offlineEnterAppliesSetupOnJoin() throws IOException {
        StaffModeConfig config = config("vanish_on_enable: false");
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        ops.connect(staff, "Staffer");
        core.enterStaffMode(staff.toString());

        ops.snapshots.remove(staff);
        ops.saved.clear();
        ops.gameModes.clear();

        core.handlePlayerJoin(staff);

        assertTrue(core.isInStaffMode(staff));
        assertTrue(ops.hasSnapshot(staff));
        assertTrue(ops.saved.contains(staff));
        assertTrue(ops.gameModes.contains(StaffGameMode.CREATIVE));
    }

    @Test
    void regularJoinDoesNotReapplySetupForActiveStaffWithSnapshot() throws IOException {
        StaffModeConfig config = config("vanish_on_enable: false");
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        ops.connect(staff, "Staffer");
        core.enterStaffMode(staff.toString());

        ops.saved.clear();
        ops.gameModes.clear();
        ops.restored.clear();

        core.handlePlayerJoin(staff);

        assertTrue(ops.saved.isEmpty());
        assertTrue(ops.gameModes.isEmpty());
        assertTrue(ops.restored.isEmpty());
    }

    @Test
    void vanishOnEnableHidesStaffFromNonStaffObservers() throws IOException {
        StaffModeConfig config = config("vanish_on_enable: true");
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        UUID observer = UUID.randomUUID();
        ops.connect(staff, "Staffer");
        ops.connect(observer, "Observer");

        core.enterStaffMode(staff.toString());

        assertTrue(core.isVanished(staff));
        assertTrue(ops.hideCalls.contains(observer + "<-" + staff));
    }

    @Test
    void quitRestoresSnapshotAndClearsStaffState() throws IOException {
        StaffModeConfig config = config("vanish_on_enable: false");
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        ops.connect(staff, "Staffer");
        core.enterStaffMode(staff.toString());

        core.handlePlayerQuit(staff);

        assertFalse(core.isInStaffMode(staff));
        assertTrue(ops.restored.contains(staff));
        assertFalse(ops.hasSnapshot(staff));
    }

    @Test
    void scoreboardResolvesPlaceholders() throws IOException {
        StaffModeConfig config = config(
                "vanish_on_enable: false",
                "staff_scoreboard:",
                "  enabled: true",
                "  title: \"&bStaff {server}\"",
                "  lines:",
                "    - \"Players: {online}\"",
                "    - \"Me: {player_name}\""
        );
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        ops.connect(staff, "Staffer");

        core.enterStaffMode(staff.toString());

        ScoreboardContent content = ops.scoreboards.get(staff);
        assertNotNull(content);
        assertTrue(content.getTitle().contains("Server 1"));
        assertEquals("Players: 1", content.getLines().get(0).getText());
        assertEquals("Me: Staffer", content.getLines().get(1).getText());
        assertEquals(2, content.getLines().get(0).getScore());
        assertEquals(1, content.getLines().get(1).getScore());
    }

    @Test
    void scoreboardTruncatesLongLinesToFortyChars() throws IOException {
        String longLine = repeat("ABCDEFGHIJ", 5);
        StaffModeConfig config = config(
                "vanish_on_enable: false",
                "staff_scoreboard:",
                "  enabled: true",
                "  title: \"Staff\"",
                "  lines:",
                "    - \"" + longLine + "\""
        );
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        ops.connect(staff, "Staffer");

        core.enterStaffMode(staff.toString());

        List<ScoreboardContent.Line> lines = ops.scoreboards.get(staff).getLines();
        assertEquals(40, lines.get(0).getText().length());
        assertEquals(longLine.substring(0, 40), lines.get(0).getText());
    }

    @Test
    void scoreboardDeduplicatesIdenticalLongLines() throws IOException {
        String longLine = repeat("ABCDEFGHIJ", 5);
        StaffModeConfig config = config(
                "vanish_on_enable: false",
                "staff_scoreboard:",
                "  enabled: true",
                "  title: \"Staff\"",
                "  lines:",
                "    - \"" + longLine + "\"",
                "    - \"" + longLine + "\""
        );
        StaffModeCore core = newCore(config);
        UUID staff = UUID.randomUUID();
        ops.connect(staff, "Staffer");

        core.enterStaffMode(staff.toString());

        List<ScoreboardContent.Line> lines = ops.scoreboards.get(staff).getLines();
        assertEquals(2, lines.size());
        assertNotEquals(lines.get(0).getText(), lines.get(1).getText());
    }

    private StaffModeConfig config(String... lines) throws IOException {
        Files.write(tempDir.resolve("staff_mode.yml"),
                String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
        return StaffModeConfig.load(tempDir, LOGGER);
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static final class NoOpFreezeOps implements FreezeOps {
        @Override
        public String playerName(UUID uuid) {
            return null;
        }

        @Override
        public void sendMessage(UUID uuid, String message) {
        }

        @Override
        public void onFrozen(UUID target) {
        }

        @Override
        public void onUnfrozen(UUID target) {
        }
    }
}
