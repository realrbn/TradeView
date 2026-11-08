package de.tradeview;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record UnlimitedTradesStatus(boolean enabled, boolean canChange) implements TradeViewPacket {
    public static final ResourceLocation ID = new ResourceLocation("tradeview", "unlimited_trades_status");

    @Override public ResourceLocation id() { return ID; }

    public static UnlimitedTradesStatus read(FriendlyByteBuf buf) {
        return new UnlimitedTradesStatus(buf.readBoolean(), buf.readBoolean());
    }

    @Override public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeBoolean(canChange);
    }
}
