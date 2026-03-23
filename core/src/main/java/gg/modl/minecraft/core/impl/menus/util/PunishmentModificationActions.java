package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Evidence;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.request.AddPunishmentNoteRequest;
import gg.modl.minecraft.api.http.request.ChangePunishmentDurationRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.request.TogglePunishmentOptionRequest;
import gg.modl.minecraft.core.Platform;

import gg.modl.minecraft.core.cache.CachedProfile;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class PunishmentModificationActions {

    private static final long MS_PER_SECOND = 1000L, MS_PER_MINUTE = 60 * MS_PER_SECOND, MS_PER_HOUR = 60 * MS_PER_MINUTE, MS_PER_DAY = 24 * MS_PER_HOUR;

    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final UUID viewerUuid;
    private final String viewerName;
    private final UUID targetUuid;
    private final Punishment punishment;
    private final Consumer<String> sendMessage;
    private final Consumer<Click> refreshMenu;
    private final Consumer<CirrusPlayerWrapper> displayMenu;

    public PunishmentModificationActions(Platform platform, ModlHttpClient httpClient,
                                          UUID viewerUuid, String viewerName, UUID targetUuid,
                                          Punishment punishment, Consumer<String> sendMessage,
                                          Consumer<Click> refreshMenu, Consumer<CirrusPlayerWrapper> displayMenu) {
        this.platform = platform;
        this.httpClient = httpClient;
        this.viewerUuid = viewerUuid;
        this.viewerName = viewerName;
        this.targetUuid = targetUuid;
        this.punishment = punishment;
        this.sendMessage = sendMessage;
        this.refreshMenu = refreshMenu;
        this.displayMenu = displayMenu;
    }

    private String resolveViewerIssuerId() {
        return platform.getCache() != null ? platform.getCache().getStaffId(viewerUuid) : null;
    }

    public void handleAddNote(Click click) {
        click.clickedMenu().close();

        platform.getChatInputManager().requestInput(viewerUuid, "Enter note to add to this punishment:",
                input -> {
                    AddPunishmentNoteRequest request = new AddPunishmentNoteRequest(
                            punishment.getId(), viewerName, resolveViewerIssuerId(), input);

                    httpClient.addPunishmentNote(request).thenAccept(v -> {
                        sendMessage.accept(MenuItems.COLOR_GREEN + "Note added successfully!");
                        refreshMenu.accept(click);
                    }).exceptionally(e -> {
                        sendMessage.accept(MenuItems.COLOR_RED + "Failed to add note: " + e.getMessage());
                        displayMenu.accept(click.player());
                        return null;
                    });
                },
                () -> {
                    sendMessage.accept(MenuItems.COLOR_GRAY + "Note cancelled.");
                    displayMenu.accept(click.player());
                }
        );
    }

    public void handleEvidence(Click click) {
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            List<Evidence> evidenceList = punishment.getEvidence();
            if (evidenceList.isEmpty()) {
                sendMessage.accept(MenuItems.COLOR_GRAY + "No evidence attached to this punishment.");
                return;
            }

            sendMessage.accept("");
            sendMessage.accept(MenuItems.COLOR_GOLD + "Evidence for punishment #" + punishment.getId() + ":");
            for (int i = 0; i < evidenceList.size(); i++) {
                Evidence ev = evidenceList.get(i);
                String display = ev.getDisplayText();
                sendMessage.accept(MenuItems.COLOR_WHITE + (i + 1) + ". " + display);
                sendMessage.accept(MenuItems.COLOR_GRAY + "   Added by " + ev.getUploadedBy() + " on " + MenuItems.formatDate(ev.getUploadedAt()));
            }
            sendMessage.accept("");
            return;
        }

        click.clickedMenu().close();

        platform.getChatInputManager().requestInput(viewerUuid, "Enter evidence URL:",
                input -> {
                    AddPunishmentEvidenceRequest request = new AddPunishmentEvidenceRequest(
                            punishment.getId(), viewerName, resolveViewerIssuerId(), input);

                    httpClient.addPunishmentEvidence(request).thenAccept(v -> {
                        sendMessage.accept(MenuItems.COLOR_GREEN + "Evidence added successfully!");
                        refreshMenu.accept(click);
                    }).exceptionally(e -> {
                        sendMessage.accept(MenuItems.COLOR_RED + "Failed to add evidence: " + e.getMessage());
                        displayMenu.accept(click.player());
                        return null;
                    });
                },
                () -> {
                    sendMessage.accept(MenuItems.COLOR_GRAY + "Evidence cancelled.");
                    displayMenu.accept(click.player());
                }
        );
    }

    public void handlePardon(Click click) {
        PardonPunishmentRequest request = new PardonPunishmentRequest(
                punishment.getId(), viewerName, resolveViewerIssuerId(), null, null);

        httpClient.pardonPunishment(request).thenAccept(response -> {
            if (response.hasPardoned()) {
                sendMessage.accept(MenuItems.COLOR_GREEN + "Punishment pardoned successfully!");
                invalidateCache();
                click.clickedMenu().close();
                refreshMenu.accept(click);
            } else {
                sendMessage.accept(MenuItems.COLOR_GRAY + "Punishment is already inactive or has been pardoned.");
            }
        }).exceptionally(e -> {
            sendMessage.accept(MenuItems.COLOR_RED + "Failed to pardon punishment: " + e.getMessage());
            return null;
        });
    }

    public void handleChangeDuration(Click click) {
        click.clickedMenu().close();

        platform.getChatInputManager().requestInput(viewerUuid,
                "Enter new duration (e.g., 30d, 2h, 30m, 1d2h30m, or 'perm' for permanent):",
                input -> {
                    Long durationMs = parseDuration(input);
                    if (durationMs == null && !input.equalsIgnoreCase("perm") && !input.equalsIgnoreCase("permanent")) {
                        sendMessage.accept(MenuItems.COLOR_RED + "Invalid duration format. Examples: 30d, 2h, 30m, 1d2h30m");
                        displayMenu.accept(click.player());
                        return;
                    }

                    ChangePunishmentDurationRequest request = new ChangePunishmentDurationRequest(
                            punishment.getId(), viewerName, resolveViewerIssuerId(), durationMs);

                    httpClient.changePunishmentDuration(request).thenAccept(v -> {
                        sendMessage.accept(MenuItems.COLOR_GREEN + "Duration changed successfully!");
                        invalidateCache();
                        refreshMenu.accept(click);
                    }).exceptionally(e -> {
                        sendMessage.accept(MenuItems.COLOR_RED + "Failed to change duration: " + e.getMessage());
                        displayMenu.accept(click.player());
                        return null;
                    });
                },
                () -> {
                    sendMessage.accept(MenuItems.COLOR_GRAY + "Duration change cancelled.");
                    displayMenu.accept(click.player());
                }
        );
    }

    public void handleToggleStatWipe(Click click) {
        boolean currentStatus = Boolean.TRUE.equals(punishment.getDataMap().get("wipeAfterExpiry"));

        TogglePunishmentOptionRequest request = new TogglePunishmentOptionRequest(
                punishment.getId(), viewerName, resolveViewerIssuerId(), "STAT_WIPE", !currentStatus);

        httpClient.togglePunishmentOption(request).thenAccept(v -> {
            sendMessage.accept(MenuItems.COLOR_GREEN + "Stat-wipe " + (!currentStatus ? "enabled" : "disabled") + " successfully!");
            refreshMenu.accept(click);
        }).exceptionally(e -> {
            sendMessage.accept(MenuItems.COLOR_RED + "Failed to toggle stat-wipe: " + e.getMessage());
            return null;
        });
    }

    public void handleToggleAltBlock(Click click) {
        boolean currentStatus = Boolean.TRUE.equals(punishment.getDataMap().get("altBlocking"));

        TogglePunishmentOptionRequest request = new TogglePunishmentOptionRequest(
                punishment.getId(), viewerName, resolveViewerIssuerId(), "ALT_BLOCKING", !currentStatus);

        httpClient.togglePunishmentOption(request).thenAccept(v -> {
            sendMessage.accept(MenuItems.COLOR_GREEN + "Alt-blocking " + (!currentStatus ? "enabled" : "disabled") + " successfully!");
            refreshMenu.accept(click);
        }).exceptionally(e -> {
            sendMessage.accept(MenuItems.COLOR_RED + "Failed to toggle alt-blocking: " + e.getMessage());
            return null;
        });
    }

    public void invalidateCache() {
        if (platform.getCache() != null) {
            CachedProfile profile = platform.getCache().getPlayerProfile(targetUuid);
            if (profile != null) {
                profile.setActiveBan(null);
                profile.setActiveMute(null);
            }
        }
    }

    public static Long parseDuration(String input) {
        if (input == null || input.isEmpty()) return null;
        if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) return null;

        long total = 0;
        StringBuilder number = new StringBuilder();

        for (char c : input.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else if (c == 'd' || c == 'h' || c == 'm' || c == 's') {
                if (number.length() == 0) continue;
                long value = Long.parseLong(number.toString());
                number.setLength(0);

                switch (c) {
                    case 'd': total += value * MS_PER_DAY; break;
                    case 'h': total += value * MS_PER_HOUR; break;
                    case 'm': total += value * MS_PER_MINUTE; break;
                    case 's': total += value * MS_PER_SECOND; break;
                }
            }
        }

        return total > 0 ? total : null;
    }
}
