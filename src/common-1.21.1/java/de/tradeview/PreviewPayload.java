package de.tradeview;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.trading.MerchantOffers;

public record PreviewPayload(int containerId, Map<Integer, MerchantOffers> levels) implements CustomPacketPayload {
    public static final Type<PreviewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("tradeview", "preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewPayload> CODEC = new StreamCodec<>() {
        @Override public PreviewPayload decode(RegistryFriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > 4) throw new IllegalArgumentException("Invalid preview size");
            Map<Integer, MerchantOffers> levels = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                int level = buf.readVarInt();
                if (level < 2 || level > 5 || levels.containsKey(level)) throw new IllegalArgumentException("Invalid level");
                levels.put(level, MerchantOffers.STREAM_CODEC.decode(buf));
            }
            return new PreviewPayload(id, levels);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, PreviewPayload payload) {
            buf.writeVarInt(payload.containerId());
            buf.writeVarInt(payload.levels().size());
            payload.levels().forEach((level, offers) -> {
                buf.writeVarInt(level);
                MerchantOffers.STREAM_CODEC.encode(buf, offers);
            });
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
