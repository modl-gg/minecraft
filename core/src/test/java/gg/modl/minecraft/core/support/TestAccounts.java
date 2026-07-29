package gg.modl.minecraft.core.support;

import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TestAccounts {
    private TestAccounts() {}

    public static Account.Username username(String name) {
        return new Account.Username(name, null);
    }

    public static Account account(UUID uuid, String username) {
        return account(uuid, Collections.singletonList(username(username)));
    }

    public static Account account(UUID uuid, List<Account.Username> usernames) {
        return new Account(
                "player-1",
                uuid,
                usernames,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()
        );
    }

    public static PlayerProfileResponse profileResponse(UUID uuid, String username) {
        return profileResponse(uuid, username, 200);
    }

    public static PlayerProfileResponse profileResponse(UUID uuid, String username, int status) {
        return new PlayerProfileResponse(account(uuid, username), status, -1, -1);
    }
}
