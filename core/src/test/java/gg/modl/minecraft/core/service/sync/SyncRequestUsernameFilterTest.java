package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.http.request.SyncRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncRequestUsernameFilterTest {

    private static SyncRequest.ChatLogEntry chat(String username) {
        SyncRequest.ChatLogEntry entry = new SyncRequest.ChatLogEntry();
        entry.setUsername(username);
        entry.setUuid("uuid-" + username);
        entry.setMessage("msg");
        entry.setServer("srv");
        entry.setTimestamp(1L);
        return entry;
    }

    private static SyncRequest.CommandLogEntry command(String username) {
        SyncRequest.CommandLogEntry entry = new SyncRequest.CommandLogEntry();
        entry.setUsername(username);
        entry.setUuid("uuid-" + username);
        entry.setCommand("/cmd");
        entry.setServer("srv");
        entry.setTimestamp(1L);
        return entry;
    }

    @Test
    void all_valid_usernames_pass_through_unchanged() {
        List<SyncRequest.ChatLogEntry> input = Arrays.asList(chat("Notch"), chat("Player_1"), chat("a.b"));
        List<SyncRequest.ChatLogEntry> result = SyncService.filterByUsername(input, SyncRequest.ChatLogEntry::getUsername);
        assertEquals(3, result.size());
        assertEquals(input, result);
    }

    @Test
    void mixed_valid_and_invalid_retains_only_valid() {
        List<SyncRequest.ChatLogEntry> input = Arrays.asList(
                chat("Notch"),
                chat("bad username"),
                chat("ok_name"),
                chat("toolongusernameabcdef"),
                chat("a"));
        List<SyncRequest.ChatLogEntry> result = SyncService.filterByUsername(input, SyncRequest.ChatLogEntry::getUsername);
        assertEquals(2, result.size());
        assertEquals("Notch", result.get(0).getUsername());
        assertEquals("ok_name", result.get(1).getUsername());
    }

    @Test
    void all_invalid_returns_empty_list() {
        List<SyncRequest.CommandLogEntry> input = Arrays.asList(
                command("bad name"),
                command("x"),
                command("way-too-long-username"));
        List<SyncRequest.CommandLogEntry> result = SyncService.filterByUsername(input, SyncRequest.CommandLogEntry::getUsername);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void null_username_is_dropped() {
        List<SyncRequest.ChatLogEntry> input = new ArrayList<>();
        input.add(chat("Notch"));
        input.add(chat(null));
        List<SyncRequest.ChatLogEntry> result = SyncService.filterByUsername(input, SyncRequest.ChatLogEntry::getUsername);
        assertEquals(1, result.size());
        assertEquals("Notch", result.get(0).getUsername());
    }

    @Test
    void null_list_returns_empty_list() {
        List<SyncRequest.ChatLogEntry> result = SyncService.filterByUsername(null, SyncRequest.ChatLogEntry::getUsername);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void bedrock_default_username_passes_regex() {
        List<SyncRequest.ChatLogEntry> input = Arrays.asList(chat(".Player_1"));
        List<SyncRequest.ChatLogEntry> result = SyncService.filterByUsername(input, SyncRequest.ChatLogEntry::getUsername);
        assertEquals(1, result.size());
        assertEquals(".Player_1", result.get(0).getUsername());
    }
}
