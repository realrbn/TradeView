package de.tradeview;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Starts an enchanted book search in the open trade screen; a level of 0 stops the running search. */
public record TradeFindRequest(int containerId, ResourceLocation enchantment, int level) implements CustomPacketPayload {
    public static final Type<TradeFindRequest> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("tradeview", "trade_find"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeFindRequest> CODEC = new StreamCodec<>() {
        @Override public TradeFindRequest decode(RegistryFriendlyByteBuf buf) {
            return new TradeFindRequest(buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, TradeFindRequest payload) {
            buf.writeVarInt(payload.containerId());
            buf.writeResourceLocation(payload.enchantment());
            buf.writeVarInt(payload.level());
        }
    };

    public static TradeFindRequest stop() {
        return new TradeFindRequest(-1, ResourceLocation.fromNamespaceAndPath("tradeview", "none"), 0);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
