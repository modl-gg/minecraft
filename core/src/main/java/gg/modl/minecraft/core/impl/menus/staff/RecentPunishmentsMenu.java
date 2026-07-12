package gg.modl.minecraft.core.impl.menus.staff;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.PunishmentItemRenderer;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.punishment.RecentPunishmentMapper;
import lombok.Getter;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;
import java.util.function.Consumer;

public class RecentPunishmentsMenu extends BaseStaffListMenu<RecentPunishmentsMenu.PunishmentWithPlayer> {
    @Value
    public static class PunishmentWithPlayer {
        Punishment punishment;
        UUID playerUuid;
        String playerName;
        Account account;
    }

    private final List<PunishmentWithPlayer> recentPunishments;
    private final String panelUrl;
    @Getter private CompletableFuture<Void> dataFuture;

    public RecentPunishmentsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                  boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction, null);
    }

    public RecentPunishmentsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                  boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction,
                                  List<PunishmentWithPlayer> preloadedData) {
        super("Recent Punishments", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.recentPunishments = preloadedData != null ? new ArrayList<>(preloadedData) : new ArrayList<>();
        activeTab = StaffTab.PUNISHMENTS;

        if (preloadedData == null)
            this.dataFuture = fetchRecentPunishments();
        else
            this.dataFuture = CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> fetchRecentPunishments() {
        return httpClient.getRecentPunishments(48).thenAccept(response -> {
            if (response.isSuccess() && response.getPunishments() != null) {
                recentPunishments.clear();
                for (RecentPunishmentsResponse.RecentPunishment p : response.getPunishments()) {
                    UUID playerUuid = null;
                    try {
                        if (p.getPlayerUuid() != null) {
                            playerUuid = UUID.fromString(p.getPlayerUuid());
                        }
                    } catch (Exception ignored) {}

                    recentPunishments.add(new PunishmentWithPlayer(
                            RecentPunishmentMapper.toPunishment(p), playerUuid, p.getPlayerName(), null));
                }
            }
        }).exceptionally(e -> null);
    }

    @Override
    protected Collection<PunishmentWithPlayer> elements() {
        if (recentPunishments.isEmpty())
            return Collections.singletonList(new PunishmentWithPlayer(null, null, null, null));

        List<PunishmentWithPlayer> sorted = new ArrayList<>(recentPunishments);
        sorted.sort(Comparator.comparing((PunishmentWithPlayer p) -> p.getPunishment().getIssued(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return sorted;
    }

    @Override
    protected CirrusItem map(PunishmentWithPlayer pwp) {
        LocaleManager locale = PluginServices.locale();

        if (pwp.getPunishment() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.history"));

        Punishment punishment = pwp.getPunishment();

        Object typeNameObj = punishment.getDataMap().get("typeName");
        String typeName = typeNameObj != null ? typeNameObj.toString() : punishment.getTypeCategory();

        boolean isKick = typeName != null && typeName.toLowerCase().contains("kick");
        boolean isBan = typeName != null && (typeName.toLowerCase().contains("ban") || typeName.toLowerCase().contains("blacklist"));
        boolean isMute = typeName != null && typeName.toLowerCase().contains("mute");

        Map<String, String> extraVars = mapOf("player", pwp.getPlayerName() != null ? pwp.getPlayerName() : "Unknown");

        return PunishmentItemRenderer.render(punishment, isKick, isBan, isMute, typeName,
                "menus.recent_item", extraVars);
    }

    @Override
    protected void handleClick(Click click, PunishmentWithPlayer pwp) {
        if (pwp.getPunishment() == null) return;

        List<PunishmentWithPlayer> currentData = new ArrayList<>(recentPunishments);
        Consumer<CirrusPlayerWrapper> returnToPunishments = p -> {
            RecentPunishmentsMenu m = new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null, currentData);
            StaffNavigationHandlers.displayWhenLoaded(platform, m.getDataFuture(), p, m::display);
        };

        if (pwp.getAccount() != null) {
            ActionHandlers.openMenu(
                    new StaffModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                            pwp.getAccount(), pwp.getPunishment(), isAdmin, panelUrl, returnToPunishments))
                    .handle(click);
        } else {
            click.clickedMenu().close();
            httpClient.getPlayerProfile(pwp.getPlayerUuid()).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    new StaffModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName,
                        response.getProfile(), pwp.getPunishment(), isAdmin, panelUrl, returnToPunishments)
                        .display(click.player());
                } else {
                    sendMessage(MenuItems.COLOR_RED + "Failed to load player profile");
                }
            }).exceptionally(e -> {
                sendMessage(MenuItems.COLOR_RED + "Failed to load player profile: " + e.getMessage());
                return null;
            });
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("openPunishments", click -> {});
    }
}
