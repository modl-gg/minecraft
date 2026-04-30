package gg.modl.minecraft.fabric.v26;

import gg.modl.minecraft.bridge.AbstractBridgeComponent;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.core.service.DisabledReplayService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.fabric.v26.handler.FabricFreezeHandler;
import gg.modl.minecraft.fabric.v26.handler.FabricStaffModeHandler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public class FabricBridgeComponent extends AbstractBridgeComponent {
    private final MinecraftServer server;
    private final FabricBridgePluginContext fabricContext;
    private FabricFreezeHandler fabricFreezeHandler;
    private FabricStaffModeHandler fabricStaffModeHandler;


    public FabricBridgeComponent(FabricBridgePluginContext context, MinecraftServer server,
                                 String apiKey, String backendUrl, String panelUrl, PluginLogger pluginLogger) {
        super(context, apiKey, backendUrl, panelUrl, pluginLogger);
        this.server = server;
        this.fabricContext = context;
    }

    @Override
    protected void initFreezeHandler(BridgeLocaleManager localeManager) {
        fabricFreezeHandler = new FabricFreezeHandler(server, localeManager);
    }

    @Override
    protected void initStaffModeHandler(BridgeConfig bridgeConfig,
                                        BridgeLocaleManager localeManager,
                                        StaffModeConfig staffModeConfig) {
        fabricStaffModeHandler = new FabricStaffModeHandler(
                server, bridgeConfig, fabricFreezeHandler, localeManager, staffModeConfig);
        fabricStaffModeHandler.startScoreboardUpdater();
    }

    @Override
    protected BridgeMessageHandler createMessageHandler() {
        return new FabricBridgeMessageHandler(
                server, fabricFreezeHandler, fabricStaffModeHandler, statWipeHandler, this);
    }

    @Override
    protected void onBridgeClientCreated(BridgeQueryClient client) {
        fabricStaffModeHandler.setBridgeClient(client);
        fabricFreezeHandler.setBridgeClient(client);
    }

    @Override
    protected void registerAntiCheatHooks(List<AntiCheatHook> hooks) {
    }

    @Override
    protected void initReplayRecording(BridgeConfig config) {
        this.replayService = DisabledReplayService.FABRIC;
    }

    @Override
    protected void registerPlatformEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            fabricFreezeHandler.onTick();
            fabricStaffModeHandler.onTick();
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && fabricStaffModeHandler.isInStaffMode(player.getUUID())) {
                return false;
            }
            if (source.getDirectEntity() instanceof ServerPlayer player
                    && fabricStaffModeHandler.isInStaffMode(player.getUUID())) {
                return false;
            }
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraftServer) -> {
            ServerPlayer player = handler.getPlayer();
            fabricStaffModeHandler.onPlayerJoin(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) -> {
            ServerPlayer player = handler.getPlayer();
            UUID uuid = player.getUUID();
            fabricStaffModeHandler.onPlayerQuit(player);
            fabricFreezeHandler.onPlayerQuit(uuid);
            if (violationTracker != null) violationTracker.resetPlayer(uuid);
            if (autoReporter != null) autoReporter.clearCooldown(uuid);
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (fabricStaffModeHandler.isInStaffMode(player.getUUID())) return false;
            if (fabricFreezeHandler.isFrozen(player.getUUID())) return false;
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayer staff)) {
                return InteractionResult.PASS;
            }
            if (fabricFreezeHandler.isFrozen(staff.getUUID())) {
                return InteractionResult.FAIL;
            }

            if (fabricStaffModeHandler.isInStaffMode(staff.getUUID())) {
                if (fabricStaffModeHandler.isVanished(staff.getUUID())) {
                    BlockPos pos = hitResult.getBlockPos();
                    var blockEntity = world.getBlockEntity(pos);
                    if (blockEntity instanceof net.minecraft.world.Container container) {
                        fabricStaffModeHandler.openSilentContainer(staff, container, pos);
                        return InteractionResult.SUCCESS;
                    }
                }
                return InteractionResult.FAIL;
            }


            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer staff)) {
                return InteractionResult.PASS;
            }
            if (!fabricStaffModeHandler.isInStaffMode(staff.getUUID())) {
                return InteractionResult.PASS;
            }

            int slot = staff.getInventory().getSelectedSlot();
            Map<Integer, StaffModeConfig.HotbarItem> hotbar = fabricStaffModeHandler.getActiveHotbar(staff.getUUID());
            StaffModeConfig.HotbarItem item = hotbar != null ? hotbar.get(slot) : null;
            if (item != null && item.getAction() != null && !item.getAction().isEmpty()) {
                fabricStaffModeHandler.executeAction(staff, item);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer staff)) {
                return InteractionResult.PASS;
            }
            if (!fabricStaffModeHandler.isInStaffMode(staff.getUUID())) {
                return InteractionResult.PASS;
            }

            if (hand == InteractionHand.MAIN_HAND && entity instanceof ServerPlayer targetPlayer) {
                int slot = staff.getInventory().getSelectedSlot();
                Map<Integer, StaffModeConfig.HotbarItem> hotbar = fabricStaffModeHandler.getActiveHotbar(staff.getUUID());
                StaffModeConfig.HotbarItem item = hotbar != null ? hotbar.get(slot) : null;
                if (item != null && "target_selector".equals(item.getAction())) {
                    fabricStaffModeHandler.setTarget(staff.getUUID().toString(), targetPlayer.getUUID().toString());
                    staff.sendSystemMessage(Component.literal(localeManager.getMessage(
                            "staff_mode.target.now_targeting",
                            mapOf("player", targetPlayer.getName().getString()))), false);
                }
            }
            return InteractionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer staff)) {
                return InteractionResult.PASS;
            }
            if (!fabricStaffModeHandler.isInStaffMode(staff.getUUID())) {
                return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
        });

    }

    @Override
    protected void registerProxyCommand(BridgeQueryClient client) {
        try {
            var dispatcher = server.getCommands().getDispatcher();
            dispatcher.register(
                    net.minecraft.commands.Commands.literal("proxycmd")
                            .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_OWNERS))
                            .then(net.minecraft.commands.Commands.argument(
                                            "command", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String command = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "command");
                                        client.sendMessage("PROXY_CMD", command);
                                        ctx.getSource().sendSystemMessage(Component.literal("Sent to proxy: " + command));
                                        return 1;
                                    })));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        } catch (Exception e) {
            pluginLogger.warning("[bridge] Failed to register proxy command: " + e.getMessage());
        }
    }

    @Override
    protected void onDisable() {
        if (fabricStaffModeHandler != null) fabricStaffModeHandler.shutdown();
        ((FabricBridgeScheduler) fabricContext.getScheduler()).shutdown();
    }

    public FabricFreezeHandler getFabricFreezeHandler() {
        return fabricFreezeHandler;
    }

    public FabricStaffModeHandler getFabricStaffModeHandler() {
        return fabricStaffModeHandler;
    }
}
