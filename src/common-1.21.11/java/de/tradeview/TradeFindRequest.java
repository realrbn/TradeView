package de.tradeview;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Starts an enchanted book search in the open trade screen; a level of 0 stops the running search. */
public record TradeFindRequest(int containerId, Identifier enchantment, int level) implements CustomPacketPayload {
    public static final Type<TradeFindRequest> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("tradeview", "trade_find"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeFindRequest> CODEC = new StreamCodec<>() {
        @Override public TradeFindRequest decode(RegistryFriendlyByteBuf buf) {
            return new TradeFindRequest(buf.readVarInt(), buf.readIdentifier(), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, TradeFindRequest payload) {
            buf.writeVarInt(payload.containerId());
            buf.writeIdentifier(payload.enchantment());
            buf.writeVarInt(payload.level());
        }
    };

    public static TradeFindRequest stop() {
        return new TradeFindRequest(-1, Identifier.fromNamespaceAndPath("tradeview", "none"), 0);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
