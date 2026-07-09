package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.core.service.ReplayCaptureResult;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeComponentTest {
    @TempDir
    Path tempDir;

    public interface ModernBlockAccess {
        Object getBlockData();
    }

    @Test
    void resolveBlockStateIdUsesModernBlockDataWhenAvailable() {
        Block block = mock(Block.class, Mockito.withSettings().extraInterfaces(ModernBlockAccess.class));
        Object blockData = new Object();
        AtomicBoolean legacyUsed = new AtomicBoolean(false);

        when(((ModernBlockAccess) block).getBlockData()).thenReturn(blockData);

        int stateId = BridgeComponent.resolveBlockStateId(
                block,
                data -> {
                    assertEquals(blockData, data);
                    return 1234;
                },
                (material, data) -> {
                    legacyUsed.set(true);
                    return -1;
                });

        assertEquals(1234, stateId);
        verify(((ModernBlockAccess) block)).getBlockData();
        verify(block, never()).getType();
        verify(block, never()).getData();
        assertEquals(false, legacyUsed.get());
    }

    @Test
    void resolveBlockStateIdFallsBackToLegacyWhenModernApiIsMissing() {
        Block block = mock(Block.class);

        when(block.getType()).thenReturn(Material.STONE);
        when(block.getData()).thenReturn((byte) 5);

        int stateId = BridgeComponent.resolveBlockStateId(
                block,
                data -> -1,
                (material, data) -> material == Material.STONE && data == (byte) 5 ? 77 : -1);

        assertEquals(77, stateId);
        verify(block).getType();
        verify(block).getData();
    }

    @Test
    void cleanupDeletesReplayFileAfterSuccessfulUploadWhenLocalSaveIsDisabled() throws IOException {
        File replayFile = createReplayFile();

        BridgeComponent.cleanupReplayFileAfterUpload(replayFile, false, ReplayCaptureResult.ok("replay-id"), null);

        assertFalse(replayFile.exists());
    }

    @Test
    void cleanupKeepsReplayFileAfterFailedUploadWhenLocalSaveIsDisabled() throws IOException {
        File replayFile = createReplayFile();

        BridgeComponent.cleanupReplayFileAfterUpload(replayFile, false, null, new RuntimeException("upload failed"));

        assertTrue(replayFile.exists());
    }

    @Test
    void cleanupKeepsReplayFileWhenUploadResultIsMissing() throws IOException {
        File replayFile = createReplayFile();

        BridgeComponent.cleanupReplayFileAfterUpload(replayFile, false, null, null);

        assertTrue(replayFile.exists());
    }

    @Test
    void cleanupKeepsReplayFileWhenUploadResultIsNotOk() throws IOException {
        File replayFile = createReplayFile();

        BridgeComponent.cleanupReplayFileAfterUpload(replayFile, false, ReplayCaptureResult.error(), null);

        assertTrue(replayFile.exists());
    }

    @Test
    void cleanupKeepsReplayFileWhenLocalSaveIsEnabled() throws IOException {
        File replayFile = createReplayFile();

        BridgeComponent.cleanupReplayFileAfterUpload(replayFile, true, ReplayCaptureResult.ok("replay-id"), null);

        assertTrue(replayFile.exists());
    }

    @Test
    void cleanupKeepsReplayFileAfterFailedUploadWhenLocalSaveIsEnabled() throws IOException {
        File replayFile = createReplayFile();

        BridgeComponent.cleanupReplayFileAfterUpload(replayFile, true, null, new RuntimeException("upload failed"));

        assertTrue(replayFile.exists());
    }

    @Test
    void recordingDeltaMsReturnsElapsedTimeSinceRecordingStarted() {
        assertEquals(2500, BridgeComponent.recordingDeltaMs(12_500L, 10_000L));
    }

    @Test
    void recordingDeltaMsClampsNegativeElapsedTimeToZero() {
        assertEquals(0, BridgeComponent.recordingDeltaMs(9_500L, 10_000L));
    }

    @Test
    void recordingDeltaMsClampsElapsedTimeAboveIntegerMaxValue() {
        assertEquals(Integer.MAX_VALUE, BridgeComponent.recordingDeltaMs(Integer.MAX_VALUE + 2L, 0L));
    }

    private File createReplayFile() throws IOException {
        File replayFile = tempDir.resolve("capture.modlreplay").toFile();
        assertTrue(replayFile.createNewFile());
        return replayFile;
    }
}
