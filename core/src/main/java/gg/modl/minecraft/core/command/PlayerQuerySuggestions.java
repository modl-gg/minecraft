package gg.modl.minecraft.core.command;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.autocomplete.SuggestionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class PlayerQuerySuggestions {
    private PlayerQuerySuggestions() {
    }

    public static <A extends CommandActor> SuggestionProvider<A> onlinePlayerNames(final Platform platform) {
        return context -> onlinePlayerNames(platform.getOnlinePlayers());
    }

    static List<String> onlinePlayerNames(Collection<? extends AbstractPlayer> onlinePlayers) {
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<String>();
        for (AbstractPlayer player : onlinePlayers) {
            if (player == null || !player.isOnline()) {
                continue;
            }

            String username = player.getUsername();
            if (username != null && !username.isEmpty()) {
                names.add(username);
            }
        }

        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
