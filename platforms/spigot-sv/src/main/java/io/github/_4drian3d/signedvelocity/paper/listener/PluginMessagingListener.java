package io.github._4drian3d.signedvelocity.paper.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import io.github._4drian3d.signedvelocity.common.queue.SignedQueue;
import io.github._4drian3d.signedvelocity.common.queue.SignedResult;
import io.github._4drian3d.signedvelocity.shared.SignedConstants;
import io.github._4drian3d.signedvelocity.shared.logger.DebugLogger;
import io.github._4drian3d.signedvelocity.shared.types.QueueType;
import io.github._4drian3d.signedvelocity.shared.types.ResultType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class PluginMessagingListener implements PluginMessageListener {
    private final SignedQueue chatQueue;
    private final SignedQueue commandQueue;
    private final DebugLogger debugLogger;

    public PluginMessagingListener(final SignedQueue chatQueue, final SignedQueue commandQueue,
                                   final DebugLogger debugLogger) {
        this.chatQueue = chatQueue;
        this.commandQueue = commandQueue;
        this.debugLogger = debugLogger;
    }

    @Override
    public void onPluginMessageReceived(
        @NotNull final String channel,
        @NotNull final Player player,
        final byte[] message
    ) {
        if (!Objects.equals(channel, SignedConstants.SIGNED_PLUGIN_CHANNEL)) {
            return;
        }
        debugLogger.debug(() -> "[Plugin Message] Received on: " + System.currentTimeMillis());
        final ByteArrayDataInput input = ByteStreams.newDataInput(message);

        final UUID playerId = UUID.fromString(input.readUTF());
        final String source = input.readUTF();
        final String result = input.readUTF();

        final QueueType queueType = QueueType.getOrThrow(source);
        final SignedQueue queue;
        if (queueType == QueueType.COMMAND) {
            queue = commandQueue;
        } else {
            queue = chatQueue;
        }

        final ResultType resultType = ResultType.getOrThrow(result);
        final SignedResult resulted;
        if (resultType == ResultType.CANCEL) {
            resulted = SignedResult.cancel();
        } else if (resultType == ResultType.MODIFY) {
            resulted = SignedResult.modify(input.readUTF());
        } else {
            resulted = SignedResult.allowed();
        }

        queue.dataFrom(playerId).complete(resulted);
        debugLogger.debugMultiple(() -> new String[]{
            "[Plugin Message] Received Valid Message",
            "| Queue: " + source,
            "| Result: " + result,
            "| Message: " + resulted.message()
        });
    }
}
