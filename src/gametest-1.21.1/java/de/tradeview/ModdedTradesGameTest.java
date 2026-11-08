package de.tradeview;

import de.tradeview.testmixin.TestVillagerAccessor;
import io.netty.buffer.Unpooled;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;

public class ModdedTradesGameTest {
    private Villager villager(GameTestHelper helper) {
        Villager v = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        v.setNoAi(true);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.ARMORER).setLevel(1));
        v.getOffers();
        return v;
    }

    private MerchantOffers wireCopy(GameTestHelper helper, Map<Integer, MerchantOffers> offers, int level) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            PreviewPayload.CODEC.encode(buf, new PreviewPayload(1, offers));
            return PreviewPayload.CODEC.decode(buf).levels().get(level);
        } finally { buf.release(); }
    }

    private long diamonds(MerchantOffers offers) { return offers.stream().filter(o -> o.getResult().is(Items.DIAMOND)).count(); }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void modTradeHookIsReservedAndUnlockedExactlyOnce(GameTestHelper helper) {
        Villager v = villager(helper);
        v.addTag("tradeview_test_extended");
        PreviewVillager preview = (PreviewVillager) v;
        MerchantOffers active = v.getOffers();
        var future = preview.tradeview$preview();
        helper.assertTrue(v.getOffers() == active && diamonds(active) == 0, "Preview modified live offers");
        for (int level = 2; level <= 5; level++) {
            helper.assertTrue(diamonds(future.get(level)) == 1, "Mod's generator hook missing at level " + level);
            ((TestVillagerAccessor) v).tradeview$levelUp();
            final int target = level;
            helper.assertTrue(v.getOffers().stream().filter(o -> o.getResult().is(Items.DIAMOND)
                && o.getResult().getCount() == target).count() == 1, "Reserved mod trade duplicated or lost");
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void failingModGeneratorRestoresLiveMerchant(GameTestHelper helper) {
        Villager v = villager(helper);
        var player = helper.makeMockServerPlayerInLevel();
        v.setTradingPlayer(player);
        MerchantOffers active = v.getOffers();
        var original = v.getVillagerData();
        v.addTag("tradeview_test_failure");
        boolean failed = false;
        try { ((PreviewVillager) v).tradeview$preview(player); }
        catch (IllegalStateException expected) { failed = true; }
        helper.assertTrue(failed, "Test hook must fail");
        helper.assertTrue(v.getOffers() == active && v.getTradingPlayer() == player
            && v.getVillagerData().equals(original), "Failed projection damaged live villager");
        v.removeTag("tradeview_test_failure");
        helper.assertTrue(((PreviewVillager) v).tradeview$preview(player).size() == 4, "Failure left preview recursion guard set");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void heroTradesFollowEffectAndNeverEnterReservations(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("hero_of_the_village")) { helper.succeed(); return; }
        Villager v = villager(helper);
        v.addTag("tradeview_test_extended");
        var player = helper.makeMockServerPlayerInLevel();
        var other = helper.makeMockServerPlayerInLevel();
        v.setTradingPlayer(player);
        MerchantOffers active = v.getOffers();
        PreviewVillager preview = (PreviewVillager) v;
        var base = preview.tradeview$preview();
        int normal = base.get(5).size();
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 600, 4));
        var hero = preview.tradeview$preview(player);
        helper.assertTrue(hero.get(5).size() == normal + 5, "Hero V must reveal all five master extras");
        helper.assertTrue(v.getOffers() == active && v.getTradingPlayer() == player
            && v.getVillagerData().getLevel() == 1, "Hero preview changed real villager");
        helper.assertTrue(preview.tradeview$preview().get(5).size() == normal, "Hero extras leaked into permanent reservations");
        helper.assertTrue(preview.tradeview$preview(other).get(5).size() == normal, "Hero extras leaked to another player");
        MerchantOffers encoded = wireCopy(helper, hero, 5);
        for (int i = normal; i < normal + 5; i++)
            helper.assertTrue(ItemStack.matches(hero.get(5).get(i).getResult(), encoded.get(i).getResult()),
                "Hero item components lost over network");
        helper.assertTrue(wireCopy(helper, hero, 2).stream().anyMatch(o -> o.getResult().is(Items.DIAMOND)
            && o.getResult().getCount() == 3), "Hero bonus yield missing from preview packet");
        player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 600, 0));
        helper.assertTrue(preview.tradeview$preview(player).get(5).size() == normal + 1, "Hero I should show only one extra");
        player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        helper.assertTrue(preview.tradeview$preview(player).get(5).size() == normal, "Expired hero effect left extras visible");
        // The player loses the effect before leveling up: none of the previewed extras may unlock.
        for (int i = 0; i < 4; i++) ((TestVillagerAccessor) v).tradeview$levelUp();
        int countWithoutEffect = v.getOffers().size();
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 600, 4));
        ((TestVillagerAccessor) v).tradeview$applyPlayerTrades(player);
        helper.assertTrue(v.getOffers().size() == countWithoutEffect + 5, "Actual master did not receive exactly five extras");
        for (int i = 0; i < 5; i++)
            helper.assertTrue(ItemStack.matches(hero.get(5).get(normal + i).getResult(),
                v.getOffers().get(countWithoutEffect + i).getResult()), "Actual hero reward differs from preview");
        helper.succeed();
    }
}
