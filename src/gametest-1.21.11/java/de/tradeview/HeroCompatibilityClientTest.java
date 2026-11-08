package de.tradeview;

import de.tradeview.client.PreviewRow;
import de.tradeview.client.TradeViewConfig;
import de.tradeview.testmixin.TestVillagerAccessor;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;

public class HeroCompatibilityClientTest implements FabricClientGameTest {
    @SuppressWarnings("unchecked")
    private static List<PreviewRow> rows(MerchantScreen screen) {
        try {
            var field = MerchantScreen.class.getDeclaredField("tradeview$rows");
            field.setAccessible(true);
            return List.copyOf((List<PreviewRow>) field.get(screen));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static long masterOffers(MerchantScreen screen) {
        return rows(screen).stream().filter(row -> row.level() == 5 && row.offer() != null).count();
    }

    @Override public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("hero_of_the_village")) return;
        boolean enabled = TradeViewConfig.isEnabled(), visible = TradeViewConfig.isPreviewVisible();
        boolean inline = TradeViewConfig.isPreviewInTradeList(), update = TradeViewConfig.isUpdateCheckEnabled();
        context.runOnClient(client -> {
            TradeViewConfig.setEnabled(true);
            TradeViewConfig.setPreviewVisible(true);
            TradeViewConfig.setPreviewInTradeList(false);
            TradeViewConfig.setUpdateCheckEnabled(false);
        });
        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            var id = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = server.overworld();
                Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
                villager.setNoAi(true);
                villager.setPos(player.getX() + 1, player.getY(), player.getZ());
                villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess()
                    .lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.ARMORER)));
                level.addFreshEntity(villager);
                player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, 4));
                villager.mobInteract(player, InteractionHand.MAIN_HAND);
                return villager.getUUID();
            });
            int normal = world.getServer().computeOnServer(server -> ((PreviewVillager) server.overworld().getEntity(id))
                .tradeview$preview().get(5).size());
            context.waitFor(client -> client.screen instanceof MerchantScreen screen && masterOffers(screen) == normal + 5);
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.screen;
                if (screen.getMenu().getTraderLevel() != 1 || screen.getMenu().getOffers().stream()
                    .anyMatch(offer -> offer.getResult().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)))
                    throw new AssertionError("Preview leaked future master trades into the actual menu");
                try {
                    var x = MerchantScreen.class.getDeclaredMethod("tradeview$x");
                    var y = MerchantScreen.class.getDeclaredMethod("tradeview$y");
                    x.setAccessible(true); y.setAccessible(true);
                    for (int i = 0; i < 30; i++) screen.mouseScrolled((int) x.invoke(screen) + 10, (int) y.invoke(screen) + 45, 0, -1);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            context.waitTicks(25); // Stable contextual refresh must retain the preview's scroll position.
            context.takeScreenshot("tradeview-hero-master-preview");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
                player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, 0));
            });
            context.waitFor(client -> client.screen instanceof MerchantScreen screen && masterOffers(screen) == normal + 1);
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().removeEffect(MobEffects.HERO_OF_THE_VILLAGE));
            context.waitFor(client -> client.screen instanceof MerchantScreen screen && masterOffers(screen) == normal);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, 4));
                Villager villager = (Villager) server.overworld().getEntity(id);
                for (int i = 0; i < 4; i++) ((TestVillagerAccessor) villager).tradeview$levelUp(server.overworld());
            });
            context.waitFor(client -> client.screen instanceof MerchantScreen screen && rows(screen).isEmpty());
            context.runOnClient(client -> client.player.closeContainer());
            TestSync.packets(context);
            world.getServer().runOnServer(server -> ((Villager) server.overworld().getEntity(id))
                .mobInteract(server.getPlayerList().getPlayers().getFirst(), InteractionHand.MAIN_HAND));
            context.waitFor(client -> client.screen instanceof MerchantScreen screen
                && screen.getMenu().getTraderLevel() == 5 && screen.getMenu().getOffers().stream()
                    .anyMatch(offer -> offer.getResult().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)));
            context.runOnClient(client -> client.player.closeContainer());
        } finally {
            context.runOnClient(client -> {
                TradeViewConfig.setEnabled(enabled);
                TradeViewConfig.setPreviewVisible(visible);
                TradeViewConfig.setPreviewInTradeList(inline);
                TradeViewConfig.setUpdateCheckEnabled(update);
            });
        }
    }
}
