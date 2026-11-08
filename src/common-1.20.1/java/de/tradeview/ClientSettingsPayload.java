package de.tradeview;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ClientSettingsPayload(boolean enabled) implements TradeViewPacket {
    public static final ResourceLocation ID = new ResourceLocation("tradeview", "client_settings");

    @Override public ResourceLocation id() { return ID; }

    public static ClientSettingsPayload read(FriendlyByteBuf buf) { return new ClientSettingsPayload(buf.readBoolean()); }

    @Override public void write(FriendlyByteBuf buf) { buf.writeBoolean(enabled); }
}
