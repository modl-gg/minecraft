package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.response.PunishmentDetailResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.ModifyPunishmentMenu;

import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class PunishmentActionCommand extends BaseCommand {
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

    @CommandAlias("%cmd_punishment_action")
    @Conditions("staff")
    public void punishmentAction(CommandIssuer sender, String action, String punishmentId) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("general.gui_requires_player"));
            return;
        }

        switch (action) {
            case "modify" -> openModifyMenu(sender, punishmentId);
            case "link-evidence" -> promptLinkEvidence(sender, punishmentId);
            case "upload-evidence" -> openUploadPage(sender, punishmentId);
            default -> sender.sendMessage(localeManager.getMessage("punishment_action.unknown_action", Map.of("action", action)));
        }
    }

    private void openModifyMenu(CommandIssuer sender, String punishmentId) {
        UUID senderUuid = sender.getUniqueId();
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", "#" + punishmentId)));

        httpClientHolder.getClient().getPunishmentDetail(punishmentId).thenAccept(response -> {
            if (!response.isSuccess() || response.getPunishment() == null) {
                sender.sendMessage(localeManager.getMessage("print.punishment_detail.not_found", Map.of("id", punishmentId)));
                return;
            }

            PunishmentDetailResponse.PunishmentDetail detail = response.getPunishment();
            UUID playerUuid = UUID.fromString(detail.getPlayerUuid());

            httpClientHolder.getClient().getPlayerProfile(playerUuid).thenAccept(profileResponse -> {
                if (profileResponse.getStatus() != 200) {
                    sender.sendMessage(localeManager.getMessage("general.player_not_found"));
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
                    sender.sendMessage(localeManager.getMessage("print.punishment_detail.not_found", Map.of("id", punishmentId)));
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
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                return null;
            });
        }).exceptionally(throwable -> {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            return null;
        });
    }

    private void promptLinkEvidence(CommandIssuer sender, String punishmentId) {
        UUID senderUuid = sender.getUniqueId();
        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
        platform.getChatInputManager().requestInput(senderUuid,
                localeManager.getMessage("punishment_action.enter_evidence_url", Map.of("id", punishmentId)),
                (url) -> {
                    if (url == null || url.isBlank()) {
                        platform.sendMessage(senderUuid, localeManager.getMessage("punishment_action.no_url"));
                        return;
                    }

                    AddPunishmentEvidenceRequest request = new AddPunishmentEvidenceRequest(
                            punishmentId, senderName, url
                    );

                    httpClientHolder.getClient().addPunishmentEvidence(request).thenAccept(v -> platform.sendMessage(senderUuid, localeManager.getMessage("punishment_action.evidence_linked", Map.of("id", punishmentId)))).exceptionally(throwable -> {
                        platform.sendMessage(senderUuid, localeManager.getMessage("punishment_action.evidence_link_failed", Map.of("error", throwable.getMessage())));
                        return null;
                    });
                },
                () -> platform.sendMessage(senderUuid, localeManager.getMessage("punishment_action.evidence_cancelled"))
        );
    }

    private void openUploadPage(CommandIssuer sender, String punishmentId) {
        UUID senderUuid = sender.getUniqueId();
        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);

        sender.sendMessage(localeManager.getMessage("punishment_action.generating_upload"));

        httpClientHolder.getClient().createEvidenceUploadToken(punishmentId, senderName).thenAccept(response -> {
            if (!response.isSuccess() || response.getToken() == null) {
                sender.sendMessage(localeManager.getMessage("punishment_action.upload_failed"));
                return;
            }

            String uploadUrl = panelUrl + "/upload-evidence/" + response.getToken();
            String json = String.format(UPLOAD_LINK_JSON, uploadUrl);

            platform.runOnMainThread(() -> platform.sendJsonMessage(senderUuid, json));
        }).exceptionally(throwable -> {
            sender.sendMessage(localeManager.getMessage("punishment_action.upload_failed"));
            return null;
        });
    }
}
