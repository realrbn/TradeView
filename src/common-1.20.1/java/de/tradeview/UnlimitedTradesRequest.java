package de.tradeview;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record UnlimitedTradesRequest(boolean enabled, boolean change) implements TradeViewPacket {
    public static final ResourceLocation ID = new ResourceLocation("tradeview", "unlimited_trades_request");

    @Override public ResourceLocation id() { return ID; }

    public static UnlimitedTradesRequest read(FriendlyByteBuf buf) {
        return new UnlimitedTradesRequest(buf.readBoolean(), buf.readBoolean());
    }

    @Override public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeBoolean(change);
    }
}
