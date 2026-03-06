package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.core.Platform;

public final class PlayerLookupUtil {

    private PlayerLookupUtil() {}

    public static AbstractPlayer fetchPlayer(String target, Platform platform, ModlHttpClient httpClient, boolean queryMojang) {
        AbstractPlayer player = platform.getAbstractPlayer(target, false);
        if (player != null) return player;

        try {
            Account account = httpClient.getPlayer(new PlayerNameRequest(target)).join().getPlayer();
            if (account != null) {
                String username = !account.getUsernames().isEmpty()
                        ? account.getUsernames().get(account.getUsernames().size() - 1).getUsername()
                        : target;
                return new AbstractPlayer(account.getMinecraftUuid(), username, false);
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(PlayerLookupUtil.class.getName()).log(java.util.logging.Level.FINE, "Backend player lookup failed for: " + target, e);
        }

        if (queryMojang)
            return platform.getAbstractPlayer(target, true);

        return null;
    }

    public static Account fetchAccount(String target, ModlHttpClient httpClient) {
        try {
            PlayerNameResponse response = httpClient.getPlayer(new PlayerNameRequest(target)).join();
            if (response != null && response.isSuccess()) return response.getPlayer();
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(PlayerLookupUtil.class.getName()).log(java.util.logging.Level.FINE, "Backend account lookup failed for: " + target, e);
        }
        return null;
    }
}
