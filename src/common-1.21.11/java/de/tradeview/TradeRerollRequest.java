package de.tradeview;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TradeRerollRequest(int containerId) implements CustomPacketPayload {
    public static final Type<TradeRerollRequest> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("tradeview", "trade_reroll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeRerollRequest> CODEC = new StreamCodec<>() {
        @Override public TradeRerollRequest decode(RegistryFriendlyByteBuf buf) {
            return new TradeRerollRequest(buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, TradeRerollRequest payload) {
            buf.writeVarInt(payload.containerId());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
