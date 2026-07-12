package gg.modl.minecraft.core.player;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerLookupService {
    private static final Logger logger = Logger.getLogger(PlayerLookupService.class.getName());

    private final Platform platform;
    private final HttpClientHolder httpClientHolder;
    private final boolean queryMojang;

    public PlayerLookupService(Platform platform, HttpClientHolder httpClientHolder, boolean queryMojang) {
        this.platform = platform;
        this.httpClientHolder = httpClientHolder;
        this.queryMojang = queryMojang;
    }

    public AbstractPlayer fetchPlayer(String target) {
        AbstractPlayer player = platform.getAbstractPlayer(target, false);
        if (player != null) return player;

        try {
            Account account = httpClientHolder.getClient().getPlayer(new PlayerNameRequest(target)).join().getPlayer();
            if (account != null) {
                String username = !account.getUsernames().isEmpty()
                        ? account.getUsernames().get(account.getUsernames().size() - 1).getUsername()
                        : target;
                return new AbstractPlayer(account.getMinecraftUuid(), username, false);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Backend player lookup failed for: " + target, e);
        }

        if (queryMojang)
            return platform.getAbstractPlayer(target, true);

        return null;
    }

    public Account fetchAccount(String target) {
        try {
            PlayerNameResponse response = httpClientHolder.getClient().getPlayer(new PlayerNameRequest(target)).join();
            if (response != null && response.isSuccess()) return response.getPlayer();
        } catch (Exception e) {
            logger.log(Level.FINE, "Backend account lookup failed for: " + target, e);
        }
        return null;
    }
}
