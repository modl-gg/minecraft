package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data
public class LinkedAccountsResponse {
    private @NotNull List<Account> linkedAccounts;
    private int status;
    private int totalCount = -1;
    private int page = -1;
    private boolean hasMore;

    public int getTotalCount() {
        return totalCount >= 0 ? totalCount : linkedAccounts.size();
    }
}
