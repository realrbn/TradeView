package de.tradeview;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.trading.MerchantOffers;

public record PreviewPayload(int containerId, Map<Integer, MerchantOffers> levels) implements TradeViewPacket {
    public static final ResourceLocation ID = new ResourceLocation("tradeview", "preview");

    @Override public ResourceLocation id() { return ID; }

    public static PreviewPayload read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > 4) throw new IllegalArgumentException("Invalid preview size");
        Map<Integer, MerchantOffers> levels = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            int level = buf.readVarInt();
            if (level < 2 || level > 5 || levels.containsKey(level)) throw new IllegalArgumentException("Invalid level");
            levels.put(level, MerchantOffers.createFromStream(buf));
        }
        return new PreviewPayload(id, levels);
    }

    @Override public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(levels.size());
        levels.forEach((level, offers) -> {
            buf.writeVarInt(level);
            offers.writeToStream(buf);
        });
    }
}
