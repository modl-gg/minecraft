package gg.modl.minecraft.core.impl.http.proto;

import gg.modl.minecraft.api.http.response.DashboardStatsResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.StaffListResponse;
import gg.modl.minecraft.api.http.response.StaffPermissionsResponse;
import gg.modl.proto.modl.v1.MinecraftDashboardStatsResponse;
import gg.modl.proto.modl.v1.MinecraftRole;
import gg.modl.proto.modl.v1.MinecraftStaffPermissionsResponse;
import gg.modl.proto.modl.v1.MinecraftStaffSummaryResponse;
import gg.modl.proto.modl.v1.PunishmentTypesResponse.PunishmentTypeData;

import java.util.ArrayList;
import java.util.List;

public final class StaffRoleProtoMapper {

    private StaffRoleProtoMapper() {
    }

    public static gg.modl.proto.modl.v1.UpdateStaffRoleRequest toUpdateStaffRoleRequest(String roleName) {
        return gg.modl.proto.modl.v1.UpdateStaffRoleRequest.newBuilder()
            .setRole(roleName)
            .build();
    }

    public static gg.modl.proto.modl.v1.StaffDisconnectRequest toStaffDisconnectRequest(
        String minecraftUuid, long sessionDurationMs) {
        return gg.modl.proto.modl.v1.StaffDisconnectRequest.newBuilder()
            .setMinecraftUuid(minecraftUuid)
            .setSessionDurationMs(sessionDurationMs)
            .build();
    }

    public static gg.modl.proto.modl.v1.UpdateRolePermissionsRequest toUpdateRolePermissionsRequest(
        List<String> permissions) {
        return gg.modl.proto.modl.v1.UpdateRolePermissionsRequest.newBuilder()
            .addAllPermissions(permissions)
            .build();
    }

    public static StaffListResponse toStaffListResponse(gg.modl.proto.modl.v1.StaffListResponse proto) {
        List<StaffListResponse.StaffEntry> staff = new ArrayList<>();
        proto.getStaffList().forEach(s -> staff.add(toStaffEntry(s)));
        return new StaffListResponse(staff, proto.getStatus());
    }

    private static StaffListResponse.StaffEntry toStaffEntry(MinecraftStaffSummaryResponse proto) {
        return new StaffListResponse.StaffEntry(
            proto.getId(),
            proto.getUsername(),
            proto.getEmail(),
            proto.getRole(),
            proto.getMinecraftUuid(),
            proto.getMinecraftUsername(),
            proto.getLastServer(),
            new ArrayList<>(proto.getPermissionsList()),
            ProtoConversions.dateFromMillis(proto.getLastSeen()),
            proto.getTotalPlaytimeMs(),
            proto.getPunishmentsIssuedCount());
    }

    public static StaffPermissionsResponse toStaffPermissionsResponse(
        gg.modl.proto.modl.v1.StaffPermissionsListResponse proto) {
        List<StaffPermissionsResponse.StaffMember> members = new ArrayList<>();
        proto.getData().getStaffList().forEach(s -> members.add(toStaffMember(s)));
        return new StaffPermissionsResponse(
            new StaffPermissionsResponse.StaffData(members),
            proto.getStatus());
    }

    private static StaffPermissionsResponse.StaffMember toStaffMember(MinecraftStaffPermissionsResponse proto) {
        return new StaffPermissionsResponse.StaffMember(
            proto.getMinecraftUuid(),
            proto.getMinecraftUsername(),
            proto.getStaffUsername(),
            proto.getStaffId(),
            proto.getStaffRole(),
            proto.getEmail(),
            new ArrayList<>(proto.getPermissionsList()));
    }

    public static RolesListResponse toRolesListResponse(gg.modl.proto.modl.v1.MinecraftRoleListResponse proto) {
        List<RolesListResponse.RoleEntry> roles = new ArrayList<>();
        proto.getRolesList().forEach(r -> roles.add(toRoleEntry(r)));
        return new RolesListResponse(roles, proto.getStatus());
    }

    private static RolesListResponse.RoleEntry toRoleEntry(MinecraftRole proto) {
        return new RolesListResponse.RoleEntry(
            proto.getId(),
            proto.getName(),
            proto.getDescription(),
            new ArrayList<>(proto.getPermissionsList()),
            proto.getIsDefault(),
            proto.getOrder());
    }

    public static DashboardStatsResponse toDashboardStatsResponse(gg.modl.proto.modl.v1.MinecraftDashboardResponse proto) {
        MinecraftDashboardStatsResponse stats = proto.getStats();
        return new DashboardStatsResponse(
            new DashboardStatsResponse.Stats(
                stats.getUnresolvedReports(),
                stats.getUnresolvedTickets(),
                stats.getOnlineStaff(),
                stats.getOnlinePlayers(),
                stats.getActiveBans(),
                stats.getActiveMutes(),
                stats.getTotalActivePunishments(),
                stats.getTotalPlayers()),
            proto.getStatus());
    }

    public static PunishmentTypesResponse toPunishmentTypesResponse(gg.modl.proto.modl.v1.PunishmentTypesResponse proto) {
        List<PunishmentTypesResponse.PunishmentTypeData> types = new ArrayList<>();
        proto.getDataList().forEach(t -> types.add(toPunishmentTypeData(t)));
        return new PunishmentTypesResponse(types, proto.getStatus());
    }

    private static PunishmentTypesResponse.PunishmentTypeData toPunishmentTypeData(PunishmentTypeData proto) {
        return new PunishmentTypesResponse.PunishmentTypeData(
            proto.getName(),
            proto.getCategory(),
            proto.getStaffDescription(),
            proto.getPlayerDescription(),
            ProtoConversions.structToMap(proto.getDurations()),
            ProtoConversions.structToMap(proto.getPoints()),
            proto.hasCustomPoints() ? proto.getCustomPoints() : null,
            proto.hasCanBeAltBlocking() ? proto.getCanBeAltBlocking() : null,
            proto.hasCanBeStatWiping() ? proto.getCanBeStatWiping() : null,
            proto.hasSingleSeverityPunishment() ? proto.getSingleSeverityPunishment() : null,
            proto.hasPermanentUntilSkinChange() ? proto.getPermanentUntilSkinChange() : null,
            proto.hasPermanentUntilUsernameChange() ? proto.getPermanentUntilUsernameChange() : null,
            proto.getId(),
            proto.getOrdinal(),
            proto.getIsCustomizable());
    }
}
