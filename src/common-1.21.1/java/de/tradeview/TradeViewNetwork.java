package de.tradeview;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Server-to-client transport supplied by each loader's entrypoint. */
public final class TradeViewNetwork {
    public interface Transport {
        boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);
        void send(ServerPlayer player, CustomPacketPayload payload);
    }

    private static Transport transport;

    private TradeViewNetwork() {}

    public static void initialize(Transport value) { transport = value; }

    /** Players without TradeView (or the channel) are skipped silently. */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (transport != null && transport.canSend(player, payload.type())) transport.send(player, payload);
    }
}
