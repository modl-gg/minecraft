package gg.modl.minecraft.api.http;

public interface ModlHttpClient extends PlayerApi, PunishmentApi, TicketApi, ReportApi, StaffApi, LogApi, SyncApi, MigrationApi {

    void shutdown();
}
