package de.tradeview;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record TradeRerollRequest(int containerId) implements TradeViewPacket {
    public static final ResourceLocation ID = new ResourceLocation("tradeview", "trade_reroll");

    @Override public ResourceLocation id() { return ID; }

    public static TradeRerollRequest read(FriendlyByteBuf buf) { return new TradeRerollRequest(buf.readVarInt()); }

    @Override public void write(FriendlyByteBuf buf) { buf.writeVarInt(containerId); }
}
