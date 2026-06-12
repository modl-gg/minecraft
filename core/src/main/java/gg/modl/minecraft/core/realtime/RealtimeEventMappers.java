package gg.modl.minecraft.core.realtime;

import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.impl.http.proto.SyncProtoMapper;
import gg.modl.proto.modl.v1.ActiveStaffPushEvent;
import gg.modl.proto.modl.v1.MigrationTaskPushEvent;
import gg.modl.proto.modl.v1.PlayerNotificationPushEvent;
import gg.modl.proto.modl.v1.PunishmentPushEvent;
import gg.modl.proto.modl.v1.Staff2faPushEvent;
import gg.modl.proto.modl.v1.StaffNotificationPushEvent;
import gg.modl.proto.modl.v1.StatWipePushEvent;
import gg.modl.proto.modl.v1.SyncData;

/**
 * Converts the authoritative minecraft push events into the plain {@link SyncResponse.SyncData}
 * DTO shape that {@code SyncService}'s apply methods already consume.
 *
 * <p>Each push event wraps the same {@code sync.proto} messages that compose a {@code SyncData},
 * so we wrap the event payload back into a proto {@link SyncData} and reuse the existing
 * {@link SyncProtoMapper#toSyncResponse} converter. This keeps a single mapping implementation
 * shared between the HTTP baseline sync and the websocket push path.</p>
 */
final class RealtimeEventMappers {

    private RealtimeEventMappers() {
    }

    static SyncResponse.SyncData fromPunishmentPush(PunishmentPushEvent event) {
        return toSyncData(SyncData.newBuilder()
            .addAllPendingPunishments(event.getPendingList())
            .addAllRecentlyModifiedPunishments(event.getModifiedList())
            .build());
    }

    static SyncResponse.SyncData fromPlayerNotificationPush(PlayerNotificationPushEvent event) {
        return toSyncData(SyncData.newBuilder()
            .addAllPlayerNotifications(event.getNotificationsList())
            .build());
    }

    static SyncResponse.SyncData fromStaffNotificationPush(StaffNotificationPushEvent event) {
        return toSyncData(SyncData.newBuilder()
            .addAllStaffNotifications(event.getNotificationsList())
            .build());
    }

    static SyncResponse.SyncData fromStatWipePush(StatWipePushEvent event) {
        return toSyncData(SyncData.newBuilder()
            .addAllPendingStatWipes(event.getStatWipesList())
            .build());
    }

    static SyncResponse.SyncData fromStaff2faPush(Staff2faPushEvent event) {
        return toSyncData(SyncData.newBuilder()
            .addAllStaff2FaVerifications(event.getVerificationsList())
            .build());
    }

    static SyncResponse.SyncData fromActiveStaffPush(ActiveStaffPushEvent event) {
        return toSyncData(SyncData.newBuilder()
            .addAllActiveStaffMembers(event.getActiveStaffMembersList())
            .build());
    }

    static SyncResponse.MigrationTask fromMigrationTaskPush(MigrationTaskPushEvent event) {
        if (!event.hasTask()) return null;
        SyncResponse.SyncData data = toSyncData(SyncData.newBuilder()
            .setMigrationTask(event.getTask())
            .build());
        return data.getMigrationTask();
    }

    private static SyncResponse.SyncData toSyncData(SyncData data) {
        gg.modl.proto.modl.v1.SyncResponse response = gg.modl.proto.modl.v1.SyncResponse.newBuilder()
            .setData(data)
            .build();
        return SyncProtoMapper.toSyncResponse(response).getData();
    }
}
