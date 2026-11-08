package de.tradeview.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-to-server transport supplied by each loader's client entrypoint. */
public final class TradeViewClientNetwork {
    public interface Transport {
        boolean canSend(CustomPacketPayload.Type<?> type);
        void send(CustomPacketPayload payload);
    }

    private static Transport transport;

    private TradeViewClientNetwork() {}

    public static void initialize(Transport value) { transport = value; }

    /** True when the connected server registered this TradeView channel. */
    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return transport != null && transport.canSend(type);
    }

    public static void send(CustomPacketPayload payload) {
        if (canSend(payload.type())) transport.send(payload);
    }
}
