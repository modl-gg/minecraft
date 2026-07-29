package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.Staff2faTokenResponse;
import gg.modl.minecraft.api.http.response.StaffListResponse;
import gg.modl.minecraft.api.http.response.StaffPermissionsResponse;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface StaffApi {

    @NotNull CompletableFuture<StaffPermissionsResponse> getStaffPermissions();

    @NotNull CompletableFuture<StaffListResponse> getStaffList();

    @NotNull CompletableFuture<Void> reportStaffDisconnect(@NotNull String minecraftUuid, long sessionDurationMs);

    @NotNull CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName, String actingStaffId);

    @NotNull CompletableFuture<RolesListResponse> getRoles();

    @NotNull CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions, String actingStaffId);

    @NotNull CompletableFuture<Staff2faTokenResponse> generateStaff2faToken(@NotNull String minecraftUuid, @NotNull String ip);
}
