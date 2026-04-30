package gg.modl.minecraft.core.command;

import gg.modl.minecraft.api.AbstractPlayer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerQuerySuggestionsTest {

    @Test
    void onlinePlayerNamesReturnsSortedOnlineUsernamesOnly() {
        List<AbstractPlayer> players = Arrays.asList(
                new AbstractPlayer(UUID.randomUUID(), "zulu", true),
                new AbstractPlayer(UUID.randomUUID(), "Alpha", true),
                new AbstractPlayer(UUID.randomUUID(), "offline", false),
                new AbstractPlayer(UUID.randomUUID(), "bravo", true)
        );

        assertEquals(Arrays.asList("Alpha", "bravo", "zulu"), PlayerQuerySuggestions.onlinePlayerNames(players));
    }
}
