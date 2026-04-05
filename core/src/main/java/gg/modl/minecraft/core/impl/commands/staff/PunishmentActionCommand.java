package gg.modl.minecraft.core.impl.commands.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.response.PunishmentDetailResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.inspect.ModifyPunishmentMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class PunishmentActionCommand {
    private static final String UPLOAD_LINK_JSON =
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"Click here to upload evidence\",\"color\":\"green\",\"underlined\":true,\"bold\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Opens in your browser\"}}" +
            "]}";
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    @Command("punishment_action")
    @StaffOnly
    public void punishmentAction(CommandActor actor, String action, String punishmentId) {
        if (actor.uniqueId() == null) {
            actor.reply(localeManager.getMessage("general.gui_requires_player"));
            return;
        }

        if ("modify".equals(action)) {
            openModifyMenu(actor, punishmentId);
        } else if ("link-evidence".equals(action)) {
            promptLinkEvidence(actor, punishmentId);
        } else if ("upload-evidence".equals(action)) {
            openUploadPage(actor, punishmentId);
        } else {
            actor.reply(localeManager.getMessage("punishment_action.unknown_action", mapOf("action", action)));
        }
    }

    private void openModifyMenu(CommandActor actor, String punishmentId) {
        UUID senderUuid = actor.uniqueId();
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", "#" + punishmentId)));

        httpClientHolder.getClient().getPunishmentDetail(punishmentId).thenAccept(response -> {
            if (!response.isSuccess() || response.getPunishment() == null) {
                actor.reply(localeManager.getMessage("print.punishment_detail.not_found", mapOf("id", punishmentId)));
                return;
            }

            PunishmentDetailResponse.PunishmentDetail detail = response.getPunishment();
            UUID playerUuid = UUID.fromString(detail.getPlayerUuid());

            httpClientHolder.getClient().getPlayerProfile(playerUuid).thenAccept(profileResponse -> {
                if (profileResponse.getStatus() != 200) {
                    actor.reply(localeManager.getMessage("general.player_not_found"));
                    return;
                }

                Account account = profileResponse.getProfile();
                Punishment punishment = null;
                for (Punishment p : account.getPunishments())
                    if (p.getId().equals(punishmentId)) {
                        punishment = p;
                        break;
                    }

                if (punishment == null) {
                    actor.reply(localeManager.getMessage("print.punishment_detail.not_found", mapOf("id", punishmentId)));
                    return;
                }

                String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);

                ModifyPunishmentMenu menu = new ModifyPunishmentMenu(
                    platform, httpClientHolder.getClient(), senderUuid, senderName,
                    account, punishment, null, null
                );

                CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                menu.display(player);
            }).exceptionally(throwable -> {
                actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
                return null;
            });
        }).exceptionally(throwable -> {
            actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
            return null;
        });
    }

    private void promptLinkEvidence(CommandActor actor, String punishmentId) {
        UUID senderUuid = actor.uniqueId();
        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
        String issuerId = cache.getStaffId(senderUuid);
        platform.getChatInputManager().requestInput(senderUuid,
                localeManager.getMessage("punishment_action.enter_evidence_url", mapOf("id", punishmentId)),
                (url) -> {
                    if (url == null || url.trim().isEmpty()) {
                        platform.sendMessage(senderUuid, localeManager.getMessage("punishment_action.no_url"));
                        return;
                    }

                    AddPunishmentEvidenceRequest request = new AddPunishmentEvidenceRequest(
                            punishmentId, senderName, issuerId, url
                    );

                    httpClientHolder.getClient().addPunishmentEvidence(request).thenAccept(v -> platform.sendMessage(senderUuid, localeManager.getMessage("punishment_action.evidence_linked", mapOf("id", punishmentId)))).exceptionally(throwable -> {
                        platform.sendMessage(senderUuid, localeManager.getMessage("punishment_action.evidence_link_failed", mapOf("error", throwable.getMessage())));
                        return null;
                    });
                },
                () -> platform.sendMessage(senderUuid, localeManager.getMessage("punishment_action.evidence_cancelled"))
        );
    }

    private void openUploadPage(CommandActor actor, String punishmentId) {
        UUID senderUuid = actor.uniqueId();
        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);

        actor.reply(localeManager.getMessage("punishment_action.generating_upload"));

        httpClientHolder.getClient().createEvidenceUploadToken(punishmentId, senderName).thenAccept(response -> {
            if (!response.isSuccess() || response.getToken() == null) {
                actor.reply(localeManager.getMessage("punishment_action.upload_failed"));
                return;
            }

            String uploadUrl = panelUrl + "/upload-evidence/" + response.getToken();
            String json = String.format(UPLOAD_LINK_JSON, uploadUrl);

            platform.runOnMainThread(() -> platform.sendJsonMessage(senderUuid, json));
        }).exceptionally(throwable -> {
            actor.reply(localeManager.getMessage("punishment_action.upload_failed"));
            return null;
        });
    }
}
