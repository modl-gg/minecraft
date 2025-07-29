package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data
public class LinkedAccountsResponse {
    private int status;
    @NotNull
    private List<Account> linkedAccounts;
}