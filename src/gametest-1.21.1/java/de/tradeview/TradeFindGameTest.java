package de.tradeview;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;

public final class TradeFindGameTest {
    private Villager villager(GameTestHelper helper, VillagerProfession profession) {
        Villager v = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        v.setNoAi(true);
        v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(1));
        v.getOffers();
        return v;
    }

    private ServerPlayer open(GameTestHelper helper, Villager villager) {
        var player = helper.makeMockServerPlayerInLevel();
        // The server closes merchant menus of players out of reach, which would cancel the search.
        player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
        player.containerMenu = new MerchantMenu(3, player.getInventory(), villager);
        villager.setTradingPlayer(player);
        return player;
    }

    private Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static void find(GameTestHelper helper, ServerPlayer player, ResourceLocation enchantment, int level) {
        TradeFindServer.request(helper.getLevel().getServer(), player,
            new TradeFindRequest(player.containerMenu.containerId, enchantment, level));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 2000)
    public void searchRerollsUntilBookAppears(GameTestHelper helper) {
        Villager v = villager(helper, VillagerProfession.LIBRARIAN);
        ServerPlayer player = open(helper, v);
        // Smite V is sold by plains librarians both with and without the Villager Trade Rebalance
        // (which Fabric's test server enables), and it only appears at the highest level.
        Holder<Enchantment> smite = enchantment(helper, Enchantments.SMITE);
        find(helper, player, Enchantments.SMITE.location(), 5);
        helper.assertTrue(TradeFindServer.isSearching(player), "Search must start for a librarian");
        helper.succeedWhen(() -> {
            helper.assertTrue(!TradeFindServer.isSearching(player), "Search still running");
            helper.assertTrue(TradeFindServer.locate(v, smite, 5, null) != null,
                "Search ended without Smite V in the trades or the preview");
            helper.assertTrue(TradeRerollServer.canReroll(v), "Searching must not consume the untraded state");
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 2000)
    public void neverOfferedEnchantmentsStopEarly(GameTestHelper helper) {
        Villager v = villager(helper, VillagerProfession.LIBRARIAN);
        ServerPlayer player = open(helper, v);
        // Swift Sneak is a treasure enchantment that villagers never sell.
        find(helper, player, Enchantments.SWIFT_SNEAK.location(), 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!TradeFindServer.isSearching(player), "Search for an unsold enchantment still running");
            helper.assertTrue(TradeFindServer.locate(v, enchantment(helper, Enchantments.SWIFT_SNEAK), 1, null) == null,
                "Swift Sneak must not be found");
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bookAlreadyOfferedNeedsNoReroll(GameTestHelper helper) {
        Villager v = villager(helper, VillagerProfession.LIBRARIAN);
        ServerPlayer player = open(helper, v);
        // Put a known book into the current offers so the first check hits.
        Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);
        var book = new net.minecraft.world.item.ItemStack(Items.ENCHANTED_BOOK);
        var stored = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
            net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        stored.set(sharpness, 5);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        v.getOffers().add(new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 20),
            java.util.Optional.empty(), book, 0, 12, 1, 0.2F));
        var offers = v.getOffers();
        int revision = ((PreviewVillager) v).tradeview$revision();
        // Only the exact level counts: the Sharpness V book is no hit for Sharpness III.
        var lower = TradeFindServer.locate(v, sharpness, 3, null);
        helper.assertTrue(lower == null || lower.bookLevel() == 3, "A higher level must not satisfy another level");
        find(helper, player, Enchantments.SHARPNESS.location(), 5);
        TradeFindServer.tick(helper.getLevel().getServer());
        helper.assertTrue(!TradeFindServer.isSearching(player), "A present book must end the search immediately");
        helper.assertTrue(v.getOffers() == offers && ((PreviewVillager) v).tradeview$revision() == revision,
            "A hit without searching must keep the offers");
        var match = TradeFindServer.locate(v, sharpness, 5, null);
        helper.assertTrue(match != null && match.bookLevel() == 5 && !match.preview(), "Exact level must be found");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void closingTheTradeScreenCancelsTheSearch(GameTestHelper helper) {
        Villager v = villager(helper, VillagerProfession.LIBRARIAN);
        ServerPlayer player = open(helper, v);
        find(helper, player, Enchantments.SWIFT_SNEAK.location(), 1);
        helper.assertTrue(TradeFindServer.isSearching(player), "Search must start");
        player.containerMenu = player.inventoryMenu;
        TradeFindServer.tick(helper.getLevel().getServer());
        helper.assertTrue(!TradeFindServer.isSearching(player), "Leaving the trade screen must cancel the search");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void villagersWithoutBooksStopEarly(GameTestHelper helper) {
        Villager v = villager(helper, VillagerProfession.FARMER);
        ServerPlayer player = open(helper, v);
        find(helper, player, Enchantments.MENDING.location(), 1);
        helper.succeedWhen(() -> helper.assertTrue(!TradeFindServer.isSearching(player),
            "A villager without enchanted books must stop the search"));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void tradedOrInvalidRequestsDoNotReroll(GameTestHelper helper) {
        Villager v = villager(helper, VillagerProfession.LIBRARIAN);
        ServerPlayer player = open(helper, v);
        v.getOffers().getFirst().increaseUses();
        var offers = v.getOffers();
        // Swift Sneak is never sold, so a traded villager can only end with "not rerollable".
        find(helper, player, Enchantments.SWIFT_SNEAK.location(), 1);
        TradeFindServer.tick(helper.getLevel().getServer());
        helper.assertTrue(!TradeFindServer.isSearching(player) && v.getOffers() == offers,
            "A traded villager must never be rerolled");

        find(helper, player, Enchantments.SHARPNESS.location(), 6);
        helper.assertTrue(!TradeFindServer.isSearching(player), "Levels above the maximum must be rejected");
        find(helper, player, ResourceLocation.fromNamespaceAndPath("tradeview", "missing"), 1);
        helper.assertTrue(!TradeFindServer.isSearching(player), "Unknown enchantments must be rejected");
        find(helper, player, Enchantments.SHARPNESS.location(), 1);
        TradeFindServer.request(helper.getLevel().getServer(), player, TradeFindRequest.stop());
        helper.assertTrue(!TradeFindServer.isSearching(player), "Stop must cancel the search");
        helper.succeed();
    }
}
