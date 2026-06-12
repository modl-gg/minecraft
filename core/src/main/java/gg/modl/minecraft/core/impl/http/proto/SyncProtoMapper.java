package gg.modl.minecraft.core.impl.http.proto;

import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.proto.modl.v1.SyncActiveStaffMember;
import gg.modl.proto.modl.v1.SyncChatLogEntry;
import gg.modl.proto.modl.v1.SyncCommandLogEntry;
import gg.modl.proto.modl.v1.SyncData;
import gg.modl.proto.modl.v1.SyncMigrationTask;
import gg.modl.proto.modl.v1.SyncModifiedPunishment;
import gg.modl.proto.modl.v1.SyncOnlinePlayer;
import gg.modl.proto.modl.v1.SyncPendingPunishment;
import gg.modl.proto.modl.v1.SyncPendingStatWipe;
import gg.modl.proto.modl.v1.SyncPlayerNotification;
import gg.modl.proto.modl.v1.SyncPunishmentModification;
import gg.modl.proto.modl.v1.SyncPunishmentWithModifications;
import gg.modl.proto.modl.v1.SyncStaff2faVerification;
import gg.modl.proto.modl.v1.SyncStaffNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the sync request/response between domain DTOs and proto V3. Inverse of the backend
 * {@code MinecraftSyncProtoMapper}.
 */
public final class SyncProtoMapper {

    private SyncProtoMapper() {
    }

    // ---- Request (domain -> proto) ----

    public static gg.modl.proto.modl.v1.SyncRequest toProto(SyncRequest request) {
        gg.modl.proto.modl.v1.SyncRequest.Builder builder = gg.modl.proto.modl.v1.SyncRequest.newBuilder()
            .setLastSyncTimestamp(request.getLastSyncTimestamp());

        if (request.getServerName() != null) builder.setServerName(request.getServerName());
        if (request.getServerInstanceId() != null) builder.setServerInstanceId(request.getServerInstanceId());

        if (request.getOnlinePlayers() != null) {
            request.getOnlinePlayers().forEach(player -> builder.addOnlinePlayers(SyncOnlinePlayer.newBuilder()
                .setUuid(player.getUuid())
                .setUsername(player.getUsername())
                .setIpAddress(player.getIpAddress())
                .setSessionDurationMs(player.getSessionDurationMs())
                .build()));
        }

        if (request.getChatLogs() != null) {
            request.getChatLogs().forEach(log -> builder.addChatLogs(SyncChatLogEntry.newBuilder()
                .setUuid(nullToEmpty(log.getUuid()))
                .setUsername(nullToEmpty(log.getUsername()))
                .setMessage(nullToEmpty(log.getMessage()))
                .setServer(nullToEmpty(log.getServer()))
                .setTimestamp(log.getTimestamp())
                .build()));
        }

        if (request.getCommandLogs() != null) {
            request.getCommandLogs().forEach(log -> builder.addCommandLogs(SyncCommandLogEntry.newBuilder()
                .setUuid(nullToEmpty(log.getUuid()))
                .setUsername(nullToEmpty(log.getUsername()))
                .setCommand(nullToEmpty(log.getCommand()))
                .setServer(nullToEmpty(log.getServer()))
                .setTimestamp(log.getTimestamp())
                .build()));
        }

        return builder.build();
    }

    // ---- Response (proto -> domain) ----

    public static SyncResponse toSyncResponse(gg.modl.proto.modl.v1.SyncResponse proto) {
        return new SyncResponse(proto.getTimestamp(), toSyncData(proto.getData()));
    }

    private static SyncResponse.SyncData toSyncData(SyncData proto) {
        SyncResponse.SyncData data = new SyncResponse.SyncData();

        List<SyncResponse.PendingPunishment> pending = new ArrayList<>();
        proto.getPendingPunishmentsList().forEach(p -> pending.add(toPendingPunishment(p)));
        data.setPendingPunishments(pending);

        List<SyncResponse.PendingPunishment> recentlyStarted = new ArrayList<>();
        proto.getRecentlyStartedPunishmentsList().forEach(p -> recentlyStarted.add(toPendingPunishment(p)));
        data.setRecentlyStartedPunishments(recentlyStarted);

        List<SyncResponse.ModifiedPunishment> recentlyModified = new ArrayList<>();
        proto.getRecentlyModifiedPunishmentsList().forEach(p -> recentlyModified.add(toModifiedPunishment(p)));
        data.setRecentlyModifiedPunishments(recentlyModified);

        List<SyncResponse.PlayerNotification> playerNotifications = new ArrayList<>();
        proto.getPlayerNotificationsList().forEach(n -> playerNotifications.add(toPlayerNotification(n)));
        data.setPlayerNotifications(playerNotifications);

        List<SyncResponse.ActiveStaffMember> activeStaff = new ArrayList<>();
        proto.getActiveStaffMembersList().forEach(s -> activeStaff.add(toActiveStaffMember(s)));
        data.setActiveStaffMembers(activeStaff);

        List<SyncResponse.StaffNotification> staffNotifications = new ArrayList<>();
        proto.getStaffNotificationsList().forEach(n -> staffNotifications.add(toStaffNotification(n)));
        data.setStaffNotifications(staffNotifications);

        List<SyncResponse.PendingStatWipe> statWipes = new ArrayList<>();
        proto.getPendingStatWipesList().forEach(w -> statWipes.add(toPendingStatWipe(w)));
        data.setPendingStatWipes(statWipes);

        List<SyncResponse.Staff2faVerification> verifications = new ArrayList<>();
        proto.getStaff2FaVerificationsList().forEach(v -> verifications.add(toStaff2faVerification(v)));
        data.setStaff2faVerifications(verifications);

        if (proto.hasMigrationTask()) data.setMigrationTask(toMigrationTask(proto.getMigrationTask()));
        if (proto.hasStaffPermissionsUpdatedAt()) data.setStaffPermissionsUpdatedAt(proto.getStaffPermissionsUpdatedAt());
        if (proto.hasPunishmentTypesUpdatedAt()) data.setPunishmentTypesUpdatedAt(proto.getPunishmentTypesUpdatedAt());
        return data;
    }

    private static SyncResponse.PendingPunishment toPendingPunishment(SyncPendingPunishment proto) {
        return new SyncResponse.PendingPunishment(
            proto.getMinecraftUuid(),
            proto.getUsername(),
            PlayerProtoMapper.toSimplePunishment(proto.getPunishment()));
    }

    private static SyncResponse.ModifiedPunishment toModifiedPunishment(SyncModifiedPunishment proto) {
        return new SyncResponse.ModifiedPunishment(
            proto.getMinecraftUuid(),
            proto.getUsername(),
            toPunishmentWithModifications(proto.getPunishment()));
    }

    private static SyncResponse.PunishmentWithModifications toPunishmentWithModifications(
        SyncPunishmentWithModifications proto) {
        List<SyncResponse.PunishmentModification> modifications = new ArrayList<>();
        proto.getModificationsList().forEach(m -> modifications.add(toPunishmentModification(m)));
        return new SyncResponse.PunishmentWithModifications(proto.getId(), modifications);
    }

    private static SyncResponse.PunishmentModification toPunishmentModification(SyncPunishmentModification proto) {
        return new SyncResponse.PunishmentModification(
            proto.getType(),
            proto.hasTimestamp() ? proto.getTimestamp() : null,
            proto.hasEffectiveDuration() ? proto.getEffectiveDuration() : null);
    }

    private static SyncResponse.PlayerNotification toPlayerNotification(SyncPlayerNotification proto) {
        return new SyncResponse.PlayerNotification(
            proto.getId(),
            proto.getMessage(),
            proto.getType(),
            proto.hasTargetPlayerUuid() ? proto.getTargetPlayerUuid() : null,
            proto.hasData() ? ProtoConversions.structToMap(proto.getData()) : null,
            proto.hasTimestamp() ? proto.getTimestamp() : null);
    }

    private static SyncResponse.ActiveStaffMember toActiveStaffMember(SyncActiveStaffMember proto) {
        return new SyncResponse.ActiveStaffMember(
            proto.getMinecraftUuid(),
            proto.getMinecraftUsername(),
            proto.getStaffUsername(),
            proto.getStaffRole(),
            proto.getEmail(),
            proto.getStaffId(),
            new ArrayList<>(proto.getPermissionsList()),
            proto.hasTwoFactorSessionValid() ? proto.getTwoFactorSessionValid() : null);
    }

    private static SyncResponse.StaffNotification toStaffNotification(SyncStaffNotification proto) {
        return new SyncResponse.StaffNotification(
            proto.getId(),
            proto.getType(),
            proto.getMessage(),
            proto.hasData() ? ProtoConversions.structToMap(proto.getData()) : null,
            proto.hasTimestamp() ? proto.getTimestamp() : null);
    }

    private static SyncResponse.PendingStatWipe toPendingStatWipe(SyncPendingStatWipe proto) {
        return new SyncResponse.PendingStatWipe(
            proto.getMinecraftUuid(),
            proto.getUsername(),
            proto.getPunishmentId());
    }

    private static SyncResponse.Staff2faVerification toStaff2faVerification(SyncStaff2faVerification proto) {
        SyncResponse.Staff2faVerification verification = new SyncResponse.Staff2faVerification();
        verification.setMinecraftUuid(proto.getMinecraftUuid());
        return verification;
    }

    private static SyncResponse.MigrationTask toMigrationTask(SyncMigrationTask proto) {
        return new SyncResponse.MigrationTask(proto.getTaskId(), proto.getType());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
