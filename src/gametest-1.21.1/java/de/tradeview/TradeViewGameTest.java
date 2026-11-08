package de.tradeview;

import de.tradeview.testmixin.TestVillagerAccessor;

import io.netty.buffer.Unpooled;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;

public class TradeViewGameTest {
    private Villager villager(GameTestHelper helper) {
        Villager v = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        v.setNoAi(true);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(1));
        v.getOffers();
        return v;
    }

    private Object encoded(GameTestHelper helper, MerchantOffers offers) {
        return MerchantOffers.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess()), offers).getOrThrow();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void previewUnlocksExactOffers(GameTestHelper helper) {
        Villager v = villager(helper);
        PreviewVillager preview = (PreviewVillager) v;
        var initial = encoded(helper, v.getOffers());
        Map<Integer, MerchantOffers> future = preview.tradeview$preview();
        helper.assertTrue(future.size() == 4, "All four future levels must exist");
        helper.assertTrue(v.getVillagerData().getLevel() == 1, "Preview must not level up villager");
        helper.assertTrue(initial.equals(encoded(helper, v.getOffers())), "Preview must preserve current offers");
        helper.assertTrue(encoded(helper, future.get(2)).equals(encoded(helper, preview.tradeview$preview().get(2))), "Reopening must preserve offers");
        for (int level = 2; level <= 5; level++) {
            int oldSize = v.getOffers().size();
            ((TestVillagerAccessor) v).tradeview$levelUp();
            MerchantOffers added = new MerchantOffers();
            added.addAll(v.getOffers().subList(oldSize, v.getOffers().size()));
            helper.assertTrue(!added.isEmpty(), "Librarian must gain trades at level " + level);
            helper.assertTrue(encoded(helper, future.get(level)).equals(encoded(helper, added)), "Unlocked offers must match preview at level " + level);
        }
        helper.assertTrue(preview.tradeview$preview().isEmpty(), "Master must have no preview");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cycleInvalidatesAndRegenerates(GameTestHelper helper) {
        Villager v = villager(helper);
        PreviewVillager preview = (PreviewVillager) v;
        var before = preview.tradeview$preview();
        int revision = preview.tradeview$revision();
        // The exact reset sequence used by Trade Cycling 1.0.21+26.2.
        v.setOffers(null);
        v.getOffers();
        var after = preview.tradeview$preview();
        helper.assertTrue(preview.tradeview$revision() != revision, "Cycle must invalidate client revision");
        helper.assertTrue(before.get(2) != after.get(2), "Cycle must regenerate future offers even if RNG chooses identical items");
        int count = v.getOffers().size();
        ((TestVillagerAccessor) v).tradeview$levelUp();
        MerchantOffers added = new MerchantOffers();
        added.addAll(v.getOffers().subList(count, v.getOffers().size()));
        helper.assertTrue(encoded(helper, after.get(2)).equals(encoded(helper, added)), "Level-up must use post-cycle preview");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void saveReloadAndNetworkPreserveComponents(GameTestHelper helper) {
        Villager v = villager(helper);
        var future = ((PreviewVillager) v).tradeview$preview();
        var output = new net.minecraft.nbt.CompoundTag();
        v.saveWithoutId(output);
        Villager restored = villager(helper);
        restored.load(output);
        var loaded = ((PreviewVillager) restored).tradeview$preview();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            PreviewPayload.CODEC.encode(buf, new PreviewPayload(7, loaded));
            PreviewPayload decoded = PreviewPayload.CODEC.decode(buf);
            helper.assertTrue(decoded.containerId() == 7, "Menu ID must survive networking");
            for (int level = 2; level <= 5; level++) {
                helper.assertTrue(encoded(helper, future.get(level)).equals(encoded(helper, loaded.get(level))), "Persistence must preserve full offers");
                helper.assertTrue(encoded(helper, loaded.get(level)).equals(encoded(helper, decoded.levels().get(level))), "Packet must preserve item components");
            }
        } finally { buf.release(); }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void professionChangeClearsPreview(GameTestHelper helper) {
        Villager v = villager(helper);
        PreviewVillager preview = (PreviewVillager) v;
        var before = preview.tradeview$preview();
        int revision = preview.tradeview$revision();
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
        v.getOffers();
        helper.assertTrue(preview.tradeview$revision() != revision, "Profession change must invalidate revision");
        helper.assertTrue(preview.tradeview$preview().get(2) != before.get(2), "Profession change must discard previous offers");
        helper.succeed();
    }
}
