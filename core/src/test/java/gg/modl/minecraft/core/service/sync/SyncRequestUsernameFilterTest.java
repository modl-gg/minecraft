package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.http.ChatLogEntry;
import gg.modl.minecraft.api.http.CommandLogEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncRequestUsernameFilterTest {

    private static ChatLogEntry chat(String username) {
        return new ChatLogEntry("uuid-" + username, username, "msg", "srv", 1L);
    }

    private static CommandLogEntry command(String username) {
        return new CommandLogEntry("uuid-" + username, username, "/cmd", "srv", 1L);
    }

    @Test
    void allValidUsernamesPassThroughUnchanged() {
        List<ChatLogEntry> input = Arrays.asList(chat("Notch"), chat("Player_1"), chat("a.b"));
        List<ChatLogEntry> result = SyncService.filterByUsername(input, ChatLogEntry::getUsername);
        assertEquals(3, result.size());
        assertEquals(input, result);
    }

    @Test
    void mixedValidAndInvalidRetainsOnlyValid() {
        List<ChatLogEntry> input = Arrays.asList(
                chat("Notch"),
                chat("bad username"),
                chat("ok_name"),
                chat("toolongusernameabcdef"),
                chat("a"));
        List<ChatLogEntry> result = SyncService.filterByUsername(input, ChatLogEntry::getUsername);
        assertEquals(2, result.size());
        assertEquals("Notch", result.get(0).getUsername());
        assertEquals("ok_name", result.get(1).getUsername());
    }

    @Test
    void allInvalidReturnsEmptyList() {
        List<CommandLogEntry> input = Arrays.asList(
                command("bad name"),
                command("x"),
                command("way-too-long-username"));
        List<CommandLogEntry> result = SyncService.filterByUsername(input, CommandLogEntry::getUsername);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void nullUsernameIsDropped() {
        List<ChatLogEntry> input = new ArrayList<>();
        input.add(chat("Notch"));
        input.add(chat(null));
        List<ChatLogEntry> result = SyncService.filterByUsername(input, ChatLogEntry::getUsername);
        assertEquals(1, result.size());
        assertEquals("Notch", result.get(0).getUsername());
    }

    @Test
    void nullListReturnsEmptyList() {
        List<ChatLogEntry> result = SyncService.filterByUsername(null, ChatLogEntry::getUsername);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void bedrockDefaultUsernamePassesRegex() {
        List<ChatLogEntry> input = Arrays.asList(chat(".Player_1"));
        List<ChatLogEntry> result = SyncService.filterByUsername(input, ChatLogEntry::getUsername);
        assertEquals(1, result.size());
        assertEquals(".Player_1", result.get(0).getUsername());
    }
}
