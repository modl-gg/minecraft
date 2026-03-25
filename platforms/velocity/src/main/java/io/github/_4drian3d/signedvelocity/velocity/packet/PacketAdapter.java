package io.github._4drian3d.signedvelocity.velocity.packet;

public interface PacketAdapter {
    void register();

    static void registerAdapter() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            new PacketEventsAdapter().register();
        } catch (ClassNotFoundException ignored) {
            // PacketEvents not available — packet adaptation disabled
        }
    }
}
