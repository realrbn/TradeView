package de.tradeview;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;

public final class TradeRerollGameTest {
    private Villager villager(GameTestHelper helper) {
        Villager v = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        v.setNoAi(true);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(1));
        v.getOffers();
        return v;
    }

    private MerchantMenu open(GameTestHelper helper, Villager villager) {
        var player = helper.makeMockServerPlayerInLevel();
        MerchantMenu menu = new MerchantMenu(1, player.getInventory(), villager);
        player.containerMenu = menu;
        villager.setTradingPlayer(player);
        return menu;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void untradedVillagerCanReroll(GameTestHelper helper) {
        Villager v = villager(helper);
        MerchantMenu menu = open(helper, v);
        var before = v.getOffers();
        int revision = ((PreviewVillager) v).tradeview$revision();
        helper.assertTrue(TradeRerollServer.canReroll(v), "Untraded novice must be rerollable");
        TradeRerollServer.request((net.minecraft.server.level.ServerPlayer) v.getTradingPlayer(), menu.containerId);
        helper.assertTrue(v.getOffers() != before && !v.getOffers().isEmpty(), "Reroll must generate a new offer list");
        helper.assertTrue(((PreviewVillager) v).tradeview$revision() != revision, "Reroll must invalidate the preview");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void tradedVillagerCannotReroll(GameTestHelper helper) {
        Villager v = villager(helper);
        MerchantMenu menu = open(helper, v);
        v.getOffers().get(0).increaseUses();
        var before = v.getOffers();
        helper.assertTrue(!TradeRerollServer.canReroll(v), "Used offers must block rerolling");
        TradeRerollServer.request((net.minecraft.server.level.ServerPlayer) v.getTradingPlayer(), menu.containerId);
        helper.assertTrue(v.getOffers() == before, "Rejected reroll must keep the offers");
        v.getOffers().get(0).resetUses();
        v.setVillagerXp(1);
        helper.assertTrue(!TradeRerollServer.canReroll(v), "Villager XP (e.g. after a restock) must block rerolling");
        helper.succeed();
    }
}
