package de.tradeview;

import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class UnlimitedTradesGameTest {
    private MerchantOffer offer(int uses) {
        return new MerchantOffer(new ItemCost(Items.PAPER), Optional.empty(), new ItemStack(Items.EMERALD), uses, 4, 0, 0.05F);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unlimitedOfferPreservesSavedLimitAndSyncsEffectiveLimit(GameTestHelper helper) {
        MerchantOffer offer = offer(4);
        helper.assertTrue(offer.isOutOfStock(), "Initial offer should be sold out");
        ((UnlimitedOffer) offer).tradeview$setUnlimited(true);
        for (int i = 0; i < 100; i++) offer.increaseUses();
        helper.assertTrue(!offer.isOutOfStock() && offer.getMaxUses() == Integer.MAX_VALUE, "Unlimited offer locked");
        helper.assertTrue(offer.copy().getMaxUses() == Integer.MAX_VALUE && !offer.copy().isOutOfStock(),
            "The packet's offer copy must retain the unlimited policy");

        var ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        var saved = MerchantOffer.CODEC.encodeStart(ops, offer).getOrThrow();
        MerchantOffer restored = MerchantOffer.CODEC.parse(ops, saved).getOrThrow();
        helper.assertTrue(restored.getMaxUses() == 4 && restored.getUses() == 104,
            "Saving must preserve the original limit and usage count");
        ((UnlimitedOffer) restored).tradeview$setUnlimited(true);
        helper.assertTrue(!restored.isOutOfStock(), "Loaded exhausted offer should unlock");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            MerchantOffer.STREAM_CODEC.encode(buf, restored);
            MerchantOffer received = MerchantOffer.STREAM_CODEC.decode(buf);
            helper.assertTrue(received.getMaxUses() == Integer.MAX_VALUE && !received.isOutOfStock(),
                "Client must receive the effective stock limit");
        } finally { buf.release(); }
        ((UnlimitedOffer) restored).tradeview$setUnlimited(false);
        helper.assertTrue(restored.getMaxUses() == 4 && restored.isOutOfStock(), "Disabling must restore stock lock");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unlimitedCountersAndDemandDoNotOverflow(GameTestHelper helper) {
        MerchantOffer offer = offer(Integer.MAX_VALUE);
        ((UnlimitedOffer) offer).tradeview$setUnlimited(true);
        for (int i = 0; i < 10; i++) offer.increaseUses();
        helper.assertTrue(offer.getUses() == Integer.MAX_VALUE - 1 && !offer.isOutOfStock(), "Usage counter overflowed");
        offer.updateDemand();
        helper.assertTrue(offer.getDemand() == 4, "Unlimited stock must retain normal sold-out demand calculation");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ordinaryPlayersCannotChangeUnlimitedPolicy(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!UnlimitedTradesServer.canChange(helper.getLevel().getServer(), player),
            "A regular player must not be allowed to change server-wide trading rules");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unlimitedDefaultsOffAndRetainsSavedChoice(GameTestHelper helper) throws IOException {
        var directory = Files.createTempDirectory("tradeview-config-test-");
        var file = directory.resolve("tradeview-server.properties");
        try {
            UnlimitedTradesServer.initialize(directory);
            helper.assertTrue(!UnlimitedTradesServer.isEnabled(), "Fresh configuration must default to OFF");
            UnlimitedTradesServer.setEnabled(true, null);
            UnlimitedTradesServer.initialize(directory);
            helper.assertTrue(UnlimitedTradesServer.isEnabled(), "Saved ON must survive reinitialization");
            UnlimitedTradesServer.setEnabled(false, null);
            UnlimitedTradesServer.initialize(directory);
            helper.assertTrue(!UnlimitedTradesServer.isEnabled(), "Saved OFF must survive reinitialization");
            Files.writeString(file, "# Existing configuration without the setting\n");
            UnlimitedTradesServer.initialize(directory);
            helper.assertTrue(!UnlimitedTradesServer.isEnabled(), "Missing property must default to OFF");
        } finally {
            UnlimitedTradesServer.initialize(FabricLoader.getInstance().getConfigDir());
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void wanderingTraderReceivesUnlimitedOffers(GameTestHelper helper) {
        var trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        trader.setNoAi(true);
        MerchantOffers offers = trader.getOffers();
        offers.clear();
        offers.add(offer(4));
        boolean previous = UnlimitedTradesServer.isEnabled();
        try {
            UnlimitedTradesServer.setEnabled(true, null);
            helper.assertTrue(trader.getOffers().getFirst().getMaxUses() == Integer.MAX_VALUE
                && !trader.getOffers().getFirst().isOutOfStock(), "Wandering trader did not receive unlimited policy");
        } finally { UnlimitedTradesServer.setEnabled(previous, null); }
        helper.succeed();
    }
}
