package de.tradeview;

import com.mojang.blaze3d.platform.InputConstants;
import de.tradeview.client.TradeViewSettingsScreen;
import de.tradeview.client.TradeFindClient;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.enchantment.Enchantments;
import de.tradeview.client.TradeRerollHotkey;
import de.tradeview.client.TradeRerollScreen;
import de.tradeview.client.TradeViewConfig;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/** Hotkey -> trade screen -> serverbound request -> authoritative reroll, plus the config switch. */
public final class TradeRerollClientTest implements FabricClientGameTest {
    private static UUID open(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> { if (client.player != null) client.player.closeContainer(); });
        world.getConnection().waitForServerboundPackets();
        UUID id = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.level();
            Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            villager.setPos(player.getX() + 1, player.getY(), player.getZ());
            villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess()
                .lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN)));
            level.addFreshEntity(villager);
            villager.mobInteract(player, InteractionHand.MAIN_HAND);
            return villager.getUUID();
        });
        context.waitFor(client -> client.gui.screen() instanceof MerchantScreen screen
            && !screen.getMenu().getOffers().isEmpty());
        return id;
    }

    private static int revision(TestSingleplayerContext world, UUID id) {
        return world.getServer().computeOnServer(server ->
            ((PreviewVillager) server.overworld().getEntity(id)).tradeview$revision());
    }

    private static void press(ClientGameTestContext context, TestSingleplayerContext world, int key) {
        context.getInput().pressKey(key);
        world.getConnection().waitForServerboundPackets();
        context.waitTicks(2);
    }

    private static String key(net.minecraft.network.chat.Component message) {
        return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : message.getString();
    }

    /** Chat command -> next trade screen -> server search across ticks -> book offered to the player. */
    private static void testFind(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> {
            var smite = client.getConnection().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SMITE);
            if (!key(TradeFindClient.start(smite, 6)).equals("tradeview.find.tooHigh") || TradeFindClient.isPending())
                throw new AssertionError("Levels above the maximum must be rejected on the client");
            client.getConnection().sendCommand("tradepreview find minecraft:smite 5");
            if (!TradeFindClient.isPending()) throw new AssertionError("Find command did not wait for a trade screen");
            client.getConnection().sendCommand("tradepreview find stop");
            if (TradeFindClient.isPending()) throw new AssertionError("Stop did not clear the pending search");
            // Without a trade screen within 60 real seconds the pending search expires.
            client.getConnection().sendCommand("tradepreview find smite 5");
            TradeFindClient.expirePendingForTest();
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (TradeFindClient.isPending()) throw new AssertionError("Pending search did not time out");
        });

        // Leaving the trade screen before a hit cancels the running search (Swift Sneak is never sold).
        context.runOnClient(client -> client.getConnection().sendCommand("tradepreview find swift_sneak 1"));
        open(context, world);
        world.getConnection().waitForServerboundPackets();
        if (!world.getServer().computeOnServer(server ->
            TradeFindServer.isSearching(server.getPlayerList().getPlayers().getFirst())))
            throw new AssertionError("Search did not start in the trade screen");
        context.runOnClient(client -> client.player.closeContainer());
        world.getConnection().waitForServerboundPackets();
        context.waitTicks(2);
        if (world.getServer().computeOnServer(server ->
            TradeFindServer.isSearching(server.getPlayerList().getPlayers().getFirst())))
            throw new AssertionError("Closing the trade screen did not cancel the search");

        context.runOnClient(client -> {
            // Smite V is sold by plains librarians with and without the Villager Trade Rebalance.
            client.getConnection().sendCommand("tradepreview find smite 5");
        });
        UUID id = open(context, world);
        if (context.computeOnClient(client -> TradeFindClient.isPending()))
            throw new AssertionError("Opening the trade screen did not start the search");
        boolean done = false;
        for (int i = 0; i < 60 && !done; i++) {
            context.waitTicks(10);
            done = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                return !TradeFindServer.isSearching(player);
            });
        }
        boolean found = world.getServer().computeOnServer(server -> {
            var villager = (Villager) server.overworld().getEntity(id);
            var smite = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SMITE);
            var match = TradeFindServer.locate(villager, smite, 5, null);
            return match != null && match.bookLevel() == 5;
        });
        if (!done || !found) throw new AssertionError("Search did not find Smite V (finished: " + done + ")");
        world.getConnection().waitForServerboundPackets();
        context.waitTicks(25); // Offers and the next preview poll reach the client.
        context.takeScreenshot("tradeview-find-result");
        context.runOnClient(client -> client.player.closeContainer());
    }

    @Override public void runTest(ClientGameTestContext context) {
        boolean rerollBefore = TradeViewConfig.isRerollEnabled(), enabledBefore = TradeViewConfig.isEnabled();
        boolean updateCheckBefore = TradeViewConfig.isUpdateCheckEnabled();
        context.runOnClient(client -> {
            TradeViewConfig.setUpdateCheckEnabled(false);
            TradeViewConfig.setEnabled(true);
            TradeViewConfig.setRerollEnabled(true);
            if (KeyMappingHelper.getBoundKeyOf(TradeRerollHotkey.KEY).getValue() != InputConstants.KEY_R)
                throw new AssertionError("Reroll hotkey must default to R");
            if (!java.util.Arrays.asList(client.options.keyMappings).contains(TradeRerollHotkey.KEY))
                throw new AssertionError("Reroll hotkey must be listed in the Controls screen");
        });
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            UUID id = open(context, world);

            int before = revision(world, id);
            press(context, world, InputConstants.KEY_R);
            if (revision(world, id) == before) throw new AssertionError("R did not reroll the untraded villager");

            // Switch it off through the TradeView config screen, then return to the open trade screen.
            context.runOnClient(client -> client.gui.setScreen(new TradeViewSettingsScreen(client.gui.screen())));
            context.clickScreenButton("Reroll button: ON");
            context.runOnClient(client -> {
                TradeViewConfig.load();
                if (TradeViewConfig.isRerollEnabled()) throw new AssertionError("Config toggle did not persist OFF");
                client.gui.screen().onClose();
            });
            context.waitFor(client -> client.gui.screen() instanceof MerchantScreen);
            before = revision(world, id);
            press(context, world, InputConstants.KEY_R);
            if (revision(world, id) != before) throw new AssertionError("Disabled reroll still reacted to R");
            context.runOnClient(client -> {
                if (((TradeRerollScreen) client.gui.screen()).tradeview$reroll())
                    throw new AssertionError("Disabled reroll button still sends requests");
            });
            context.runOnClient(client -> TradeViewConfig.setRerollEnabled(true));

            // Rebinding in Controls takes effect without restarting.
            context.runOnClient(client -> {
                TradeRerollHotkey.KEY.setKey(InputConstants.getKey("key.keyboard.g"));
                KeyMapping.resetMapping();
            });
            before = revision(world, id);
            press(context, world, InputConstants.KEY_R);
            if (revision(world, id) != before) throw new AssertionError("Old binding still rerolled");
            press(context, world, InputConstants.KEY_G);
            if (revision(world, id) == before) throw new AssertionError("Rebound hotkey did not reroll");
            context.runOnClient(client -> {
                TradeRerollHotkey.KEY.setKey(TradeRerollHotkey.KEY.getDefaultKey());
                KeyMapping.resetMapping();
            });

            // Outside the trade screen the key does nothing.
            context.runOnClient(client -> client.player.closeContainer());
            context.waitFor(client -> client.gui.screen() == null);
            before = revision(world, id);
            press(context, world, InputConstants.KEY_R);
            if (revision(world, id) != before) throw new AssertionError("R rerolled outside the trade screen");
            context.takeScreenshot("tradeview-reroll-outside");

            testFind(context, world);
        } finally {
            context.runOnClient(client -> {
                TradeViewConfig.setRerollEnabled(rerollBefore);
                TradeViewConfig.setEnabled(enabledBefore);
                TradeViewConfig.setUpdateCheckEnabled(updateCheckBefore);
            });
        }
    }
}
