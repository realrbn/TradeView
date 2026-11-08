package de.tradeview;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UnlimitedTradesRequest(boolean enabled, boolean change) implements CustomPacketPayload {
    public static final Type<UnlimitedTradesRequest> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("tradeview", "unlimited_trades_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnlimitedTradesRequest> CODEC = new StreamCodec<>() {
        @Override public UnlimitedTradesRequest decode(RegistryFriendlyByteBuf buf) {
            return new UnlimitedTradesRequest(buf.readBoolean(), buf.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, UnlimitedTradesRequest payload) {
            buf.writeBoolean(payload.enabled());
            buf.writeBoolean(payload.change());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
