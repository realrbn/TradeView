package de.tradeview;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-to-client transport supplied by each loader's entrypoint. */
public final class TradeViewNetwork {
    public interface Transport {
        boolean canSend(ServerPlayer player, ResourceLocation id);
        void send(ServerPlayer player, TradeViewPacket packet);
    }

    private static Transport transport;

    private TradeViewNetwork() {}

    public static void initialize(Transport value) { transport = value; }

    /** Players without TradeView (or the channel) are skipped silently. */
    public static void send(ServerPlayer player, TradeViewPacket packet) {
        if (transport != null && transport.canSend(player, packet.id())) transport.send(player, packet);
    }
}
