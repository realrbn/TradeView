package de.tradeview;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** A TradeView packet; before 1.20.5 each loader sends it as raw bytes on a channel named by its id. */
public interface TradeViewPacket {
    ResourceLocation id();

    void write(FriendlyByteBuf buf);
}
