package gg.modl.minecraft.fabric.v1_21_1;

import com.mojang.brigadier.CommandDispatcher;
import gg.modl.minecraft.bridge.AbstractBridgeComponent;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.core.service.DisabledReplayService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.fabric.v1_21_1.handler.FabricFreezeHandler;
import gg.modl.minecraft.fabric.v1_21_1.handler.FabricStaffModeHandler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import lombok.Getter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public class FabricBridgeComponent extends AbstractBridgeComponent {
    private final MinecraftServer server;
    private final FabricBridgePluginContext fabricContext;
    @Getter private FabricFreezeHandler fabricFreezeHandler;
    @Getter private FabricStaffModeHandler fabricStaffModeHandler;


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
        fabricStaffModeHandler = new FabricStaffModeHandler(server, bridgeConfig, fabricFreezeHandler,
                localeManager, staffModeConfig);
        fabricStaffModeHandler.startScoreboardUpdater();
    }

    @Override
    protected BridgeMessageHandler createMessageHandler() {
        return new FabricBridgeMessageHandler(server, fabricFreezeHandler, fabricStaffModeHandler,
                statWipeHandler, this);
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
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            fabricFreezeHandler.onTick();
            fabricStaffModeHandler.onTick();
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && fabricStaffModeHandler.isInStaffMode(player.getUuid())) {
                return false;
            }
            if (source.getSource() instanceof ServerPlayerEntity player
                    && fabricStaffModeHandler.isInStaffMode(player.getUuid())) {
                return false;
            }
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayerEntity player = handler.getPlayer();
            fabricStaffModeHandler.onPlayerJoin(player);

        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID uuid = player.getUuid();
            fabricStaffModeHandler.onPlayerQuit(player);
            fabricFreezeHandler.onPlayerQuit(uuid);
            if (violationTracker != null) violationTracker.resetPlayer(uuid);
            if (autoReporter != null) autoReporter.clearCooldown(uuid);
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (fabricStaffModeHandler.isInStaffMode(player.getUuid())) return false;
            if (fabricFreezeHandler.isFrozen(player.getUuid())) return false;
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (fabricFreezeHandler.isFrozen(sp.getUuid())) return ActionResult.FAIL;

            if (fabricStaffModeHandler.isInStaffMode(sp.getUuid())) {
                // Silent container viewing for vanished staff
                if (fabricStaffModeHandler.isVanished(sp.getUuid())) {
                    BlockPos pos = hitResult.getBlockPos();
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof Inventory container) {
                        fabricStaffModeHandler.openSilentContainer(sp, container, pos);
                        return ActionResult.SUCCESS;
                    }
                }
                return ActionResult.FAIL;
            }


            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != Hand.MAIN_HAND)
                return TypedActionResult.pass(ItemStack.EMPTY);
            if (!(player instanceof ServerPlayerEntity sp))
                return TypedActionResult.pass(ItemStack.EMPTY);
            if (!fabricStaffModeHandler.isInStaffMode(sp.getUuid()))
                return TypedActionResult.pass(ItemStack.EMPTY);

            int slot = sp.getInventory().selectedSlot;
            Map<Integer, StaffModeConfig.HotbarItem> hotbar = fabricStaffModeHandler.getActiveHotbar(sp.getUuid());
            StaffModeConfig.HotbarItem item = hotbar != null ? hotbar.get(slot) : null;
            if (item != null && item.getAction() != null && !item.getAction().isEmpty()) {
                fabricStaffModeHandler.executeAction(sp, item);
                return TypedActionResult.success(sp.getMainHandStack());
            }
            return TypedActionResult.pass(ItemStack.EMPTY);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!fabricStaffModeHandler.isInStaffMode(sp.getUuid())) return ActionResult.PASS;

            if (hand == Hand.MAIN_HAND && entity instanceof ServerPlayerEntity targetPlayer) {
                int slot = sp.getInventory().selectedSlot;
                Map<Integer, StaffModeConfig.HotbarItem> hotbar = fabricStaffModeHandler.getActiveHotbar(sp.getUuid());
                StaffModeConfig.HotbarItem item = hotbar != null ? hotbar.get(slot) : null;
                if (item != null && "target_selector".equals(item.getAction())) {
                    fabricStaffModeHandler.setTarget(sp.getUuid().toString(), targetPlayer.getUuid().toString());
                    sp.sendMessage(Text.literal(localeManager.getMessage("staff_mode.target.now_targeting",
                            mapOf("player", targetPlayer.getName().getString()))), false);
                }
            }
            return ActionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!fabricStaffModeHandler.isInStaffMode(sp.getUuid())) return ActionResult.PASS;
            return ActionResult.FAIL;
        });

    }

    @Override
    protected void registerProxyCommand(BridgeQueryClient client) {
        try {
            CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();
            dispatcher.register(
                    CommandManager.literal("proxycmd")
                            .requires(source -> source.hasPermissionLevel(4))
                            .then(CommandManager.argument("command",
                                            StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String cmd = StringArgumentType.getString(ctx, "command");
                                        client.sendMessage("PROXY_CMD", cmd);
                                        ctx.getSource().sendMessage(Text.literal("Sent to proxy: " + cmd));
                                        return 1;
                                    })));
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                server.getCommandManager().sendCommandTree(player);
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
}
