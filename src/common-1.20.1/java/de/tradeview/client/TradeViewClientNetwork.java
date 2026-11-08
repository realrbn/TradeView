package de.tradeview.client;

import de.tradeview.TradeViewPacket;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server transport supplied by each loader's client entrypoint. */
public final class TradeViewClientNetwork {
    public interface Transport {
        boolean canSend(ResourceLocation id);
        void send(TradeViewPacket packet);
    }

    private static Transport transport;

    private TradeViewClientNetwork() {}

    public static void initialize(Transport value) { transport = value; }

    /** True when the connected server registered this TradeView channel. */
    public static boolean canSend(ResourceLocation id) {
        return transport != null && transport.canSend(id);
    }

    public static void send(TradeViewPacket packet) {
        if (canSend(packet.id())) transport.send(packet);
    }
}
