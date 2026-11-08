package de.tradeview;

import com.evandev.reliable_remover.api.ReliableRemoverAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffers;

public final class ReliableRemoverGameTest {
    private Villager villager(GameTestHelper helper) {
        Villager v = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        v.setNoAi(true);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(1));
        v.getOffers();
        return v;
    }

    private static boolean contains(Map<Integer, MerchantOffers> levels, Item item) {
        return levels.values().stream().flatMap(List::stream).anyMatch(offer -> offer.getResult().is(item)
            || offer.getBaseCostA().is(item) || offer.getCostB().is(item));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void removedItemsAreHiddenFromPreview(GameTestHelper helper) {
        Villager v = villager(helper);
        Map<Integer, MerchantOffers> reserved = ((PreviewVillager) v).tradeview$preview();
        Item removed = reserved.get(2).get(0).getResult().getItem();
        var player = helper.makeMockServerPlayerInLevel();
        MerchantMenu menu = new MerchantMenu(7, player.getInventory(), v);
        player.containerMenu = menu;
        v.setTradingPlayer(player);
        List<PreviewPayload> sent = new ArrayList<>();
        try {
            helper.assertTrue(ReliableRemoverCompat.isLoaded(), "Reliable Remover API must be detected");
            ReliableRemoverAPI.tradeBlocked = stack -> stack.is(removed);
            TradeViewServer.INSTANCE.settings(player, new ClientSettingsPayload(true));
            TradeViewServer.INSTANCE.tick(helper.getLevel().getServer(), (target, payload) -> {
                if (target == player) sent.add(payload);
            });
            helper.assertTrue(sent.size() == 1, "Preview must still be sent");
            helper.assertTrue(!contains(sent.get(0).levels(), removed), "Removed item must not appear in the preview");
            helper.assertTrue(contains(reserved, removed) && contains(((PreviewVillager) v).tradeview$preview(), removed),
                "Filtering must not discard reserved trades, so rule changes after /reload apply again");

            ReliableRemoverAPI.tradeBlocked = stack -> false;
            helper.assertTrue(contains(ReliableRemoverCompat.filter(reserved), removed), "Unblocked items must reappear");
            ReliableRemoverAPI.tradeBlocked = stack -> true;
            helper.assertTrue(ReliableRemoverCompat.filter(reserved).isEmpty(), "Levels without trades must be omitted");
        } finally {
            ReliableRemoverAPI.tradeBlocked = stack -> false;
            TradeViewServer.INSTANCE.disconnect(player);
        }
        helper.succeed();
    }
}
