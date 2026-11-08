package de.tradeview;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientSettingsPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<ClientSettingsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("tradeview", "client_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientSettingsPayload> CODEC = new StreamCodec<>() {
        @Override public ClientSettingsPayload decode(RegistryFriendlyByteBuf buf) {
            return new ClientSettingsPayload(buf.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, ClientSettingsPayload payload) {
            buf.writeBoolean(payload.enabled());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
