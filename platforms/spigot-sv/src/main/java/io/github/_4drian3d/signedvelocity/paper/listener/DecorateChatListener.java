package io.github._4drian3d.signedvelocity.paper.listener;

import io.github._4drian3d.signedvelocity.common.queue.SignedQueue;
import io.papermc.paper.event.player.AsyncChatDecorateEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public final class DecorateChatListener implements EventListener<AsyncChatDecorateEvent>, LocalExecutionDetector {
    private final SignedQueue chatQueue;

    public DecorateChatListener(final SignedQueue chatQueue) {
        this.chatQueue = chatQueue;
    }

    @Override
    public @NotNull EventPriority priority() {
        return EventPriority.LOWEST;
    }

    @Override
    public boolean ignoreCancelled() {
        return true;
    }

    @Override
    public void handle(@NotNull AsyncChatDecorateEvent event) {
        final Player player = event.player();
        if (player == null) {
            return;
        }
        // Skip the queue peek (and its blocking timeout wait) for non-proxied/local/synchronous
        // decoration, matching the sibling PlayerChatListener fast-path.
        if (CHECK_FOR_LOCAL_CHAT && (!event.isAsynchronous() || isLocal())) {
            return;
        }
        this.chatQueue.dataFrom(player.getUniqueId())
                .acceptNextResultWithoutAdvance(result -> {
                    final String modifiedChat = result.toModify();
                    if (modifiedChat != null) {
                        event.result(Component.text(modifiedChat));
                    }
                });
    }

    @Override
    public @NotNull Class<AsyncChatDecorateEvent> eventClass() {
        return AsyncChatDecorateEvent.class;
    }

    @Override
    public boolean isLocal() {
        return WALKER.walk(stream -> stream.limit(20)
                .map(StackWalker.StackFrame::getMethodName)
                .noneMatch(method -> method.contains("handleChat")));
    }
}
