package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class HistoryMenu extends BaseInspectListMenu<Punishment> {
    private static final int PAGE_SIZE = 7;

    private final Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();
    private final PaginatedDataSource<Punishment> dataSource;
    private int pendingPage = -1;

    public HistoryMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public HistoryMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super("History: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        activeTab = InspectTab.HISTORY;

        loadPunishmentTypes();

        int totalCount = inspectContext != null ? inspectContext.punishmentCount() : targetAccount.getPunishments().size();
        dataSource = new PaginatedDataSource<>(PAGE_SIZE, (page, limit) -> {
            CompletableFuture<PaginatedDataSource.FetchResult<Punishment>> future = new CompletableFuture<>();
            httpClient.getPlayerPunishments(targetUuid, page, limit).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    future.complete(new PaginatedDataSource.FetchResult<>(response.getPunishments(), response.getTotalCount()));
                } else {
                    future.complete(new PaginatedDataSource.FetchResult<>(listOf(), 0));
                }
            }).exceptionally(e -> {
                future.complete(new PaginatedDataSource.FetchResult<>(listOf(), totalCount));
                return null;
            });
            return future;
        });

        List<Punishment> initial = new ArrayList<>(targetAccount.getPunishments());
        initial.sort((p1, p2) -> p2.getIssued().compareTo(p1.getIssued()));
        dataSource.initialize(initial, totalCount);
    }

    private void loadPunishmentTypes() {
        try {
            httpClient.getPunishmentTypes().thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    for (PunishmentTypesResponse.PunishmentTypeData type : response.getData()) {
                        typesByOrdinal.put(type.getOrdinal(), type);
                    }
                }
            }).join();
        } catch (Exception ignored) {}
    }

    @Override
    protected boolean interceptNextPage(Click click) {
        int nextPage = currentPageIndex().get() + 1;
        if (!dataSource.isPageLoaded(nextPage)) {
            pendingPage = nextPage;
            dataSource.setOnDataLoaded(() -> {
                pendingPage = -1;
                HistoryMenu newMenu = new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
                newMenu.dataSource.initialize(dataSource.getAllLoadedItems(), dataSource.getTotalCount());
                newMenu.display(click.player());
                newMenu.setInitialPage(nextPage);
                click.player().sendMessage(""); // force display refresh
                newMenu.display(click.player());
            });
            dataSource.fetchPage(dataSource.getAllLoadedItems().size() / PAGE_SIZE + 1);
            return true;
        }
        dataSource.prefetchIfNeeded(nextPage);
        return false;
    }

    @Override
    public boolean hasNextPage() {
        return currentPageIndex().get() < dataSource.getTotalMenuPages() - 1;
    }

    @Override
    protected Collection<Punishment> elements() {
        List<Punishment> punishments = dataSource.getAllLoadedItems();
        if (punishments.isEmpty())
            return Collections.singletonList(new Punishment());
        return punishments;
    }

    @Override
    protected CirrusItem map(Punishment punishment) {
        LocaleManager locale = platform.getLocaleManager();

        if (punishment.getId() == null || punishment.getId().isEmpty())
            return createEmptyPlaceholder(locale.getMessage("menus.empty.history"));

        String typeName = getTypeName(punishment);
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        boolean isKick = typeData != null && typeData.isKick();
        boolean isBan = typeData != null && typeData.isBan();
        boolean isMute = typeData != null && typeData.isMute();

        Long effectiveDuration = getEffectiveDuration(punishment);
        boolean isActive = !isKick && isPunishmentEffectivelyActive(punishment, effectiveDuration);

        String initialDuration = "";
        if (!isKick) {
            Long duration = punishment.getDuration();
            if (duration == null || duration <= 0) {
                initialDuration = "Permanent";
            } else {
                initialDuration = MenuItems.formatDuration(duration);
            }
        }

        String spaceBanMuteOrKick = "";
        if (isKick) {
            spaceBanMuteOrKick = "Kick";
        } else if (isBan) {
            spaceBanMuteOrKick = " Ban";
        } else if (isMute) {
            spaceBanMuteOrKick = " Mute";
        }

        String statusLine;
        Date pardonDate = isKick ? null : findPardonDate(punishment);
        if (isKick) {
            statusLine = "";
        } else if (pardonDate != null) {
            long pardonedAgo = System.currentTimeMillis() - pardonDate.getTime();
            String pardonedFormatted = MenuItems.formatDuration(pardonedAgo > 0 ? pardonedAgo : 0);
            statusLine = locale.getMessage("menus.history_item.status_pardoned",
                    mapOf("pardoned", pardonedFormatted));
        } else if (punishment.getStarted() == null) {
            statusLine = locale.getMessage("menus.history_item.status_unstarted");
        } else if (isActive) {
            if (effectiveDuration == null || effectiveDuration <= 0) {
                statusLine = locale.getMessage("menus.history_item.status_permanent");
            } else {
                long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
                long remaining = expiryTime - System.currentTimeMillis();
                String expiryFormatted = MenuItems.formatDuration(remaining > 0 ? remaining : 0);
                statusLine = locale.getMessage("menus.history_item.status_active",
                        mapOf("expiry", expiryFormatted));
            }
        } else {
            if (effectiveDuration != null && effectiveDuration > 0 && punishment.getStarted() != null) {
                long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
                long expiredAgo = System.currentTimeMillis() - expiryTime;
                String expiredFormatted = MenuItems.formatDuration(expiredAgo > 0 ? expiredAgo : 0);
                statusLine = locale.getMessage("menus.history_item.status_inactive",
                        mapOf("expired", expiredFormatted));
            } else {
                statusLine = locale.getMessage("menus.history_item.status_inactive",
                        mapOf("expired", "N/A"));
            }
        }

        StringBuilder notesBuilder = new StringBuilder();
        List<Note> notes = punishment.getNotes();
        if (!notes.isEmpty()) {
            String noteFormat = locale.getMessage("menus.history_item.note_format");
            for (int i = 0; i < notes.size(); i++) {
                Note note = notes.get(i);
                String noteDate = MenuItems.formatDate(note.getDate());
                String noteIssuer = note.getIssuerName();
                String noteText = note.getText();
                String formattedNote = noteFormat
                        .replace("{note_date}", noteDate)
                        .replace("{note_issuer}", noteIssuer)
                        .replace("{note}", noteText);
                if (i > 0)
                    notesBuilder.append("\n");
                notesBuilder.append(formattedNote);
            }
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("punishment_id", punishment.getId());
        vars.put("punishment_type", typeName);
        vars.put("initial_duration_if_not_kick", initialDuration);
        vars.put("space_ban_mute_or_kick", spaceBanMuteOrKick);
        vars.put("status_line", statusLine);
        vars.put("notes", notesBuilder.toString());
        vars.put("reason", punishment.getReason() != null ? punishment.getReason() : "No reason");
        vars.put("issuer", punishment.getIssuerName());
        vars.put("issued_date", MenuItems.formatDate(punishment.getIssued()));
        Object issuedServerObj = punishment.getDataMap().get("issuedServer");
        vars.put("issued_server", issuedServerObj instanceof String ? (String) issuedServerObj : "");

        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.history_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (processed.contains("\n"))
                lore.addAll(Arrays.asList(processed.split("\n")));
            else if (!processed.isEmpty())
                lore.add(processed);
        }

        String titleKey = isActive ? "menus.history_item.title_active" : "menus.history_item.title_inactive";
        String title = locale.getMessage(titleKey, vars);
        CirrusItemType itemType = getPunishmentItemType(punishment);

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private String getTypeName(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        if (typeData != null && typeData.getName() != null) {
            return typeData.getName();
        }

        Object typeName = punishment.getDataMap().get("typeName");
        if (typeName instanceof String && !((String) typeName).isEmpty()) {
            return (String) typeName;
        }

        return punishment.getTypeCategory();
    }

    private CirrusItemType getPunishmentItemType(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();

        Cache cache = platform.getCache();
        if (cache != null) {
            Map<Integer, String> items = cache.getPunishmentTypeItems();
            if (items != null) {
                String itemId = items.get(ordinal);
                if (itemId != null) return CirrusItemType.of(itemId);
            }
        }

        if (punishment.isBanType()) return CirrusItemType.BARRIER;
        if (punishment.isMuteType()) return CirrusItemType.PAPER;
        if (punishment.isKickType()) return CirrusItemType.LEATHER_BOOTS;
        return CirrusItemType.PAPER;
    }

    @Override
    protected void handleClick(Click click, Punishment punishment) {
        if (punishment.getId() == null || punishment.getId().isEmpty())
            return;

        ActionHandlers.openMenu(
                new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishment, backAction,
                        p -> new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext).display(p)))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        registerActionHandler("openHistory", click -> {});
    }

    private Date findPardonDate(Punishment punishment) {
        List<Modification> modifications = punishment.getModifications();
        if (modifications.isEmpty())
            return null;

        for (Modification mod : modifications) {
            if (mod.getType() == Modification.Type.MANUAL_PARDON ||
                mod.getType() == Modification.Type.APPEAL_ACCEPT) {
                return mod.getIssued();
            }
        }
        return null;
    }

    private Long getEffectiveDuration(Punishment punishment) {
        List<Modification> modifications = punishment.getModifications();
        if (modifications.isEmpty())
            return punishment.getDuration();

        Long effectiveDuration = punishment.getDuration();
        for (Modification mod : modifications) {
            if (mod.getType() == Modification.Type.MANUAL_DURATION_CHANGE ||
                mod.getType() == Modification.Type.APPEAL_DURATION_CHANGE) {
                Long modDuration = mod.getEffectiveDuration();
                if (modDuration == null || modDuration <= 0)
                    effectiveDuration = null;
                else
                    effectiveDuration = modDuration;
            }
        }
        return effectiveDuration;
    }

    private boolean isPunishmentEffectivelyActive(Punishment punishment, Long effectiveDuration) {
        if (findPardonDate(punishment) != null)
            return false;

        if (!punishment.isActive())
            return false;

        if (punishment.getStarted() == null)
            return false;

        if (effectiveDuration == null || effectiveDuration <= 0)
            return true;

        long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
        return System.currentTimeMillis() < expiryTime;
    }
}
