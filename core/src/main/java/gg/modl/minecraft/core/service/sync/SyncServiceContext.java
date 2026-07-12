package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.punishment.PunishmentMessageService;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.staff.StaffPermissionService;
import gg.modl.minecraft.core.util.PluginLogger;
import lombok.Builder;
import lombok.Value;

import java.io.File;

@Value
@Builder
public class SyncServiceContext {
    Platform platform;
    HttpClientHolder httpClientHolder;
    Cache cache;
    PluginLogger logger;
    LocaleManager localeManager;
    String panelUrl;
    int pollingRateSeconds;
    File dataFolder;
    DatabaseConfig databaseConfig;
    boolean debugMode;
    Staff2faService staff2faService;
    ChatCommandLogService chatCommandLogService;
    PunishmentMessageService punishmentMessageService;
    StaffPermissionService staffPermissionService;
}
