package de.tradeview;

import de.tradeview.mixin.MerchantMenuAccessor;
import de.tradeview.mixin.VillagerPriceInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;

/** Regenerates the offers of a novice villager nobody has traded with yet. */
public final class TradeRerollServer {
    private TradeRerollServer() {}

    /** Mirrors vanilla's "never traded" rule (no XP) and also rejects any used offer. */
    public static boolean canReroll(Villager villager) {
        if (villager.isBaby() || villager.getVillagerData().getLevel() > 1 || villager.getVillagerXp() != 0) return false;
        MerchantOffers offers = villager.getOffers();
        return !offers.isEmpty() && offers.stream().allMatch(offer -> offer.getUses() == 0);
    }

    /** The villager this player is actively trading with in the given menu, or null. */
    public static Villager trader(ServerPlayer player, int containerId) {
        if (!(player.containerMenu instanceof MerchantMenu menu) || menu.containerId != containerId
            || !(((MerchantMenuAccessor) menu).tradeview$getTrader() instanceof Villager villager)
            || villager.getTradingPlayer() != player) return null;
        return villager;
    }

    public static void request(ServerPlayer player, int containerId) {
        Villager villager = trader(player, containerId);
        if (villager == null || !canReroll(villager)) return;
        reroll(villager);
        sync(player, villager);
    }

    /** Regenerates the offers without notifying anyone; callers finish with {@link #sync}. */
    static void reroll(Villager villager) {
        // setOffers also invalidates the reserved future levels, so the preview follows the new profession roll.
        villager.setOffers(null);
        villager.getOffers();
    }

    static void sync(ServerPlayer player, Villager villager) {
        if (!(player.containerMenu instanceof MerchantMenu menu)) return;
        MerchantOffers offers = villager.getOffers();
        // Vanilla applies reputation/Hero of the Village discounts when trading starts; keep them for the new offers.
        ((VillagerPriceInvoker) villager).tradeview$updateSpecialPrices(player);
        menu.slotsChanged(menu.getSlot(0).container);
        player.sendMerchantOffers(menu.containerId, offers, villager.getVillagerData().getLevel(),
            villager.getVillagerXp(), villager.showProgressBar(), villager.canRestock());
        menu.broadcastChanges();
    }
}
