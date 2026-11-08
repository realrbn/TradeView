package de.tradeview;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Starts an enchanted book search in the open trade screen; a level of 0 stops the running search. */
public record TradeFindRequest(int containerId, ResourceLocation enchantment, int level) implements TradeViewPacket {
    public static final ResourceLocation ID = new ResourceLocation("tradeview", "trade_find");

    @Override public ResourceLocation id() { return ID; }

    public static TradeFindRequest read(FriendlyByteBuf buf) {
        return new TradeFindRequest(buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt());
    }

    @Override public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeResourceLocation(enchantment);
        buf.writeVarInt(level);
    }

    public static TradeFindRequest stop() {
        return new TradeFindRequest(-1, new ResourceLocation("tradeview", "none"), 0);
    }
}
