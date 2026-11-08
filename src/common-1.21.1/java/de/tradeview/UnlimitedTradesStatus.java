package de.tradeview;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UnlimitedTradesStatus(boolean enabled, boolean canChange) implements CustomPacketPayload {
    public static final Type<UnlimitedTradesStatus> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("tradeview", "unlimited_trades_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnlimitedTradesStatus> CODEC = new StreamCodec<>() {
        @Override public UnlimitedTradesStatus decode(RegistryFriendlyByteBuf buf) {
            return new UnlimitedTradesStatus(buf.readBoolean(), buf.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, UnlimitedTradesStatus payload) {
            buf.writeBoolean(payload.enabled());
            buf.writeBoolean(payload.canChange());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
