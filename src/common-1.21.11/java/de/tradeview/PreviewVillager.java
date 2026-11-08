package de.tradeview;

import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;

public interface PreviewVillager {
    Map<Integer, MerchantOffers> tradeview$preview();
    Map<Integer, MerchantOffers> tradeview$preview(Player player);
    int tradeview$revision();
}
