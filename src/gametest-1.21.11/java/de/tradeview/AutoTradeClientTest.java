package de.tradeview;

import com.mojang.blaze3d.platform.InputConstants;
import de.tradeview.client.AutoTradeHotkey;
import de.tradeview.client.TradeViewSettingsScreen;
import de.tradeview.client.TradeViewConfig;
import de.tradeview.client.UnlimitedTradesClient;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** End-to-end tests: keyboard event -> real client clicks -> authoritative server inventory. */
public final class AutoTradeClientTest implements FabricClientGameTest {
    private static MerchantOffer offer(ItemCost first, ItemCost second, ItemStack result, int limit) {
        // HotVP treats maxUses=3 plus multiplier=0 as its own exclusive-trade marker and
        // removes such offers on opening. Keep this synthetic stock-limit fixture distinct.
        float multiplier = limit == 3 && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("hero_of_the_village")
            ? Float.MIN_VALUE : 0.0F;
        return new MerchantOffer(first, Optional.ofNullable(second), result, 0, limit, 0, multiplier);
    }

    private static UUID open(ClientGameTestContext context, TestSingleplayerContext world,
                             Consumer<Inventory> inventory, MerchantOffer... trades) {
        context.runOnClient(client -> { if (client.player != null) client.player.closeContainer(); });
        TestSync.packets(context);
        UUID id = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            inventory.accept(player.getInventory());
            var level = player.level();
            Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            villager.setPos(player.getX() + 1, player.getY(), player.getZ());
            villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess()
                .lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN)));
            MerchantOffers offers = new MerchantOffers();
            for (MerchantOffer trade : trades) offers.add(trade);
            villager.setOffers(offers);
            level.addFreshEntity(villager);
            villager.mobInteract(player, InteractionHand.MAIN_HAND);
            return villager.getUUID();
        });
        try {
            context.waitFor(client -> client.screen instanceof MerchantScreen screen
                && screen.getMenu().getOffers().size() == trades.length);
        } catch (AssertionError failure) {
            String clientState = context.computeOnClient(client -> "screen=" + client.screen
                + ", menu=" + client.player.containerMenu.getClass().getSimpleName()
                + (client.screen instanceof MerchantScreen screen ? ", offers=" + screen.getMenu().getOffers().size() : ""));
            String serverState = world.getServer().computeOnServer(server -> {
                var villager = (Villager) server.overworld().getEntity(id);
                var player = server.getPlayerList().getPlayers().getFirst();
                return "offers=" + villager.getOffers().size() + ", level=" + villager.getVillagerData().level()
                    + ", trading=" + (villager.getTradingPlayer() == player) + ", distance=" + villager.distanceTo(player);
            });
            context.takeScreenshot("auto-trade-open-timeout");
            throw new AssertionError("Merchant did not open: " + clientState + "; server " + serverState, failure);
        }
        TestSync.packets(context);
        return id;
    }

    private static void select(ClientGameTestContext context, TestSingleplayerContext world, int index) {
        context.runOnClient(client -> {
            var screen = (MerchantScreen) client.screen;
            var buttons = screen.children().stream().filter(child -> child instanceof Button
                && child.getClass().getSimpleName().equals("TradeOfferButton")).toList();
            ((Button) buttons.get(index)).onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        });
        TestSync.packets(context);
        TestSync.packets(context);
    }

    private static void start(ClientGameTestContext context) {
        context.getInput().holdShift();
        context.getInput().pressKey(InputConstants.KEY_T);
        context.getInput().releaseShift();
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) if (inventory.getItem(i).is(item)) count += inventory.getItem(i).getCount();
        return count;
    }

    private static void assertTrades(ClientGameTestContext context, TestSingleplayerContext world,
                                     UUID id, int index, int expected) {
        try {
            TestSync.server(context, world, server -> ((Villager) server.overworld().getEntity(id)).getOffers().get(index).getUses() == expected);
        } catch (AssertionError error) {
            int actual = world.getServer().computeOnServer(server ->
                ((Villager) server.overworld().getEntity(id)).getOffers().get(index).getUses());
            String slots = context.computeOnClient(client -> {
                var menu = ((MerchantScreen) client.screen).getMenu();
                return "inputs=" + menu.getSlot(0).getItem() + ", " + menu.getSlot(1).getItem()
                    + "; output=" + menu.getSlot(2).getItem() + "; carried=" + menu.getCarried();
            });
            throw new AssertionError("Expected " + expected + " trades, got " + actual + "; " + slots, error);
        }
        context.waitTicks(12);
        TestSync.packets(context);
        world.getServer().runOnServer(server -> {
            int uses = ((Villager) server.overworld().getEntity(id)).getOffers().get(index).getUses();
            if (uses != expected) throw new AssertionError("Expected " + expected + " trades, got " + uses);
        });
        context.runOnClient(client -> {
            if (!((MerchantScreen) client.screen).getMenu().getCarried().isEmpty())
                throw new AssertionError("Automatic trading left a result on the cursor");
        });
    }

    @Override public void runTest(ClientGameTestContext context) {
        String keyBefore = TradeViewConfig.getAutoTradeKey(), modifierBefore = TradeViewConfig.getAutoTradeModifier();
        boolean enabledBefore = TradeViewConfig.isEnabled();
        boolean updateCheckBefore = TradeViewConfig.isUpdateCheckEnabled();
        boolean unlimitedBefore = UnlimitedTradesServer.isEnabled();
        context.runOnClient(client -> {
            // Keep live network requests out of the integration test; HTTP is covered by a local fixture.
            TradeViewConfig.setUpdateCheckEnabled(false);
            TradeViewConfig.setEnabled(true);
            TradeViewConfig.setAutoTradeHotkey("key.keyboard.t", "key.keyboard.left.shift");
        });
        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.waitFor(client -> UnlimitedTradesClient.isSupported() && UnlimitedTradesClient.canChange());
            world.getServer().runOnServer(server -> UnlimitedTradesServer.setEnabled(false, server));
            context.waitFor(client -> !UnlimitedTradesClient.isEnabled());
            // Select the second offer, honour its discounted price, and refill across several stacks.
            MerchantOffer discounted = offer(new ItemCost(Items.PAPER, 7), null, new ItemStack(Items.EMERALD), 100);
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 2000, 0)));
            UUID id = open(context, world, inv -> {
                inv.setItem(0, new ItemStack(Items.PAPER, 64));
                inv.setItem(1, new ItemStack(Items.PAPER, 64));
                inv.setItem(2, new ItemStack(Items.PAPER, 7));
            }, offer(new ItemCost(Items.PAPER, 7), null, new ItemStack(Items.DIAMOND), 100), discounted);
            select(context, world, 1);
            context.runOnClient(client -> {
                if (((MerchantScreen) client.screen).getMenu().getOffers().get(1).getCostA().getCount() != 5)
                    throw new AssertionError("Test discount missing");
            });
            context.getInput().pressKey(InputConstants.KEY_T); // Modifier is required.
            context.waitTicks(4);
            world.getServer().runOnServer(server -> {
                if (((Villager) server.overworld().getEntity(id)).getOffers().get(1).getUses() != 0)
                    throw new AssertionError("T alone started trading");
            });
            start(context);
            assertTrades(context, world, id, 1, 27);
            world.getServer().runOnServer(server -> {
                var inv = server.getPlayerList().getPlayers().getFirst().getInventory();
                if (count(inv, Items.EMERALD) != 27 || count(inv, Items.DIAMOND) != 0)
                    throw new AssertionError("Wrong selected trade or result count");
                server.getPlayerList().getPlayers().getFirst().removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
            });

            UUID twoCosts = open(context, world, inv -> {
                inv.setItem(0, new ItemStack(Items.EMERALD, 11));
                inv.setItem(1, new ItemStack(Items.BOOK, 3));
            }, offer(new ItemCost(Items.EMERALD, 2), new ItemCost(Items.BOOK), new ItemStack(Items.BREAD, 3), 100));
            select(context, world, 0);
            context.getInput().holdKey(InputConstants.KEY_RSHIFT);
            context.getInput().pressKey(InputConstants.KEY_T);
            context.getInput().releaseKey(InputConstants.KEY_RSHIFT);
            assertTrades(context, world, twoCosts, 0, 3);
            world.getServer().runOnServer(server -> {
                if (count(server.getPlayerList().getPlayers().getFirst().getInventory(), Items.BREAD) != 9)
                    throw new AssertionError("Second ingredient limit or multi-item result incorrect");
            });

            // Components on ingredients must match, while the result's components remain intact.
            Component wanted = Component.literal("Wanted book"), reward = Component.literal("Reward");
            ItemStack named = new ItemStack(Items.BOOK, 5);
            named.set(DataComponents.CUSTOM_NAME, wanted);
            ItemStack result = new ItemStack(Items.EMERALD);
            result.set(DataComponents.CUSTOM_NAME, reward);
            UUID components = open(context, world, inv -> {
                inv.setItem(0, new ItemStack(Items.BOOK, 64));
                inv.setItem(1, named);
            }, offer(new ItemCost(Items.BOOK).withComponents(builder -> builder.expect(DataComponents.CUSTOM_NAME, wanted)),
                null, result, 100));
            select(context, world, 0);
            start(context);
            assertTrades(context, world, components, 0, 5);
            world.getServer().runOnServer(server -> {
                var inv = server.getPlayerList().getPlayers().getFirst().getInventory();
                if (count(inv, Items.BOOK) != 64 || count(inv, Items.EMERALD) != 5)
                    throw new AssertionError("Ingredient components were ignored");
                for (int i = 0; i < 36; i++) if (inv.getItem(i).is(Items.EMERALD)
                    && !reward.equals(inv.getItem(i).get(DataComponents.CUSTOM_NAME)))
                    throw new AssertionError("Result components were lost");
            });

            UUID capacity = open(context, world, inv -> {
                for (int i = 0; i < 36; i++) inv.setItem(i, new ItemStack(Items.STONE, 64));
                inv.setItem(0, new ItemStack(Items.PAPER, 64));
                inv.setItem(1, new ItemStack(Items.BREAD, 62));
            }, offer(new ItemCost(Items.PAPER), null, new ItemStack(Items.BREAD, 3), 100));
            select(context, world, 0);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
                player.containerMenu.broadcastChanges();
            });
            TestSync.packets(context);
            start(context);
            assertTrades(context, world, capacity, 0, 0);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(1, new ItemStack(Items.BREAD, 60));
                player.containerMenu.broadcastChanges();
            });
            TestSync.packets(context);
            start(context);
            assertTrades(context, world, capacity, 0, 1);
            world.getServer().runOnServer(server -> {
                if (count(server.getPlayerList().getPlayers().getFirst().getInventory(), Items.BREAD) != 63)
                    throw new AssertionError("Partial inventory capacity lost a result");
            });

            // A custom single key works, and the offer's use limit stops it before materials run out.
            context.runOnClient(client -> {
                TradeViewConfig.setAutoTradeHotkey("key.keyboard.g", "key.keyboard.unknown");
                TradeViewConfig.load();
                if (AutoTradeHotkey.key() != InputConstants.KEY_G || AutoTradeHotkey.modifier() != -1)
                    throw new AssertionError("Custom single-key binding was not persisted");
            });
            UUID limit = open(context, world, inv -> inv.setItem(0, new ItemStack(Items.PAPER, 64)),
                offer(new ItemCost(Items.PAPER), null, new ItemStack(Items.EMERALD), 4));
            select(context, world, 0);
            context.getInput().pressKey(InputConstants.KEY_G);
            assertTrades(context, world, limit, 0, 4);

            UUID pair = open(context, world, inv -> inv.setItem(0, new ItemStack(Items.PAPER, 64)),
                offer(new ItemCost(Items.PAPER), null, new ItemStack(Items.EMERALD), 3));
            select(context, world, 0);
            context.runOnClient(client -> TradeViewConfig.setAutoTradeHotkey("key.keyboard.g", "key.keyboard.h"));
            context.getInput().pressKey(InputConstants.KEY_G);
            assertTrades(context, world, pair, 0, 0);
            context.getInput().holdKey(InputConstants.KEY_H);
            context.getInput().pressKey(InputConstants.KEY_G);
            context.getInput().releaseKey(InputConstants.KEY_H);
            assertTrades(context, world, pair, 0, 3);

            // Missing second ingredient stops, and newly arriving items require a fresh hotkey press.
            context.runOnClient(client -> TradeViewConfig.setAutoTradeHotkey("key.keyboard.g", "key.keyboard.unknown"));
            UUID missing = open(context, world, inv -> inv.setItem(0, new ItemStack(Items.EMERALD, 64)),
                offer(new ItemCost(Items.EMERALD), new ItemCost(Items.BOOK), new ItemStack(Items.BREAD), 100));
            select(context, world, 0);
            context.getInput().pressKey(InputConstants.KEY_G);
            assertTrades(context, world, missing, 0, 0);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(1, new ItemStack(Items.BOOK, 5));
                player.containerMenu.broadcastChanges();
            });
            TestSync.packets(context);
            assertTrades(context, world, missing, 0, 0);
            context.getInput().pressKey(InputConstants.KEY_G);
            assertTrades(context, world, missing, 0, 5);

            UUID cancel = open(context, world, inv -> inv.setItem(0, new ItemStack(Items.PAPER, 64)),
                offer(new ItemCost(Items.PAPER), null, new ItemStack(Items.EMERALD), 100));
            select(context, world, 0);
            context.getInput().pressKey(InputConstants.KEY_G);
            TestSync.server(context, world, server -> ((Villager) server.overworld().getEntity(cancel)).getOffers().getFirst().getUses() > 0);
            context.getInput().pressKey(InputConstants.KEY_G);
            TestSync.packets(context);
            int cancelledUses = world.getServer().computeOnServer(server ->
                ((Villager) server.overworld().getEntity(cancel)).getOffers().getFirst().getUses());
            assertTrades(context, world, cancel, 0, cancelledUses);
            context.runOnClient(client -> TradeViewConfig.setEnabled(false));
            context.getInput().pressKey(InputConstants.KEY_G);
            assertTrades(context, world, cancel, 0, cancelledUses);
            context.runOnClient(client -> TradeViewConfig.setEnabled(true));
            context.getInput().pressKey(InputConstants.KEY_G);
            context.waitTicks(2);
            context.runOnClient(client -> client.player.closeContainer());
            TestSync.packets(context);
            int closedUses = world.getServer().computeOnServer(server ->
                ((Villager) server.overworld().getEntity(cancel)).getOffers().getFirst().getUses());
            context.waitTicks(8);
            world.getServer().runOnServer(server -> {
                if (((Villager) server.overworld().getEntity(cancel)).getOffers().getFirst().getUses() != closedUses)
                    throw new AssertionError("Closing the trading screen did not stop automatic trading");
            });

            // Changing the selected offer stops the old run and must not start the newly selected one.
            UUID switching = open(context, world, inv -> inv.setItem(0, new ItemStack(Items.PAPER, 64)),
                offer(new ItemCost(Items.PAPER), null, new ItemStack(Items.EMERALD), 100),
                offer(new ItemCost(Items.PAPER), null, new ItemStack(Items.DIAMOND), 100));
            select(context, world, 0);
            context.getInput().pressKey(InputConstants.KEY_G);
            TestSync.server(context, world, server -> ((Villager) server.overworld().getEntity(switching)).getOffers().getFirst().getUses() > 0);
            select(context, world, 1);
            context.waitTicks(8);
            world.getServer().runOnServer(server -> {
                if (((Villager) server.overworld().getEntity(switching)).getOffers().get(1).getUses() != 0)
                    throw new AssertionError("Switching offers continued automatic trading");
            });
            context.runOnClient(client -> client.player.closeContainer());

            // One button records both single keys and overlapping pairs; no reset or modifier button.
            context.runOnClient(client -> TradeViewConfig.setUpdateCheckEnabled(true));
            context.getInput().pressKey(InputConstants.KEY_T);
            context.getInput().typeChars("/tradepreview config");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitFor(client -> client.screen instanceof TradeViewSettingsScreen);
            context.clickScreenButton("Update check: ON");
            context.runOnClient(client -> {
                TradeViewConfig.initialize(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
                if (TradeViewConfig.isUpdateCheckEnabled()) throw new AssertionError("Update check OFF was not persisted");
            });
            context.clickScreenButton("Update check: OFF");
            context.runOnClient(client -> {
                TradeViewConfig.initialize(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
                if (!TradeViewConfig.isUpdateCheckEnabled()) throw new AssertionError("Update check ON was not persisted");
                var download = java.net.URI.create("https://example.org/updated-mod-page?version=1.2.0");
                var message = de.tradeview.client.UpdateChecker.message(download);
                if (!message.getString().equals("[Villager Trade Preview: New Version is available. Update on Curseforge]"))
                    throw new AssertionError("Unexpected update message");
                var link = message.getSiblings().getFirst();
                if (!link.getString().equals("Update on Curseforge")
                    || !(link.getStyle().getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.OpenUrl url)
                    || !url.uri().equals(download)
                    || message.getStyle().getClickEvent() != null
                    || message.getSiblings().get(1).getStyle().getClickEvent() != null)
                    throw new AssertionError("Only the CurseForge text should link to the supplied download URL");
            });
            context.clickScreenButton("Auto trade: G");
            context.getInput().pressKey(InputConstants.KEY_H);
            context.runOnClient(client -> {
                TradeViewConfig.load();
                if (AutoTradeHotkey.key() != InputConstants.KEY_H || AutoTradeHotkey.modifier() != -1)
                    throw new AssertionError("Single-key capture failed");
                long buttons = client.screen.children().stream().filter(Button.class::isInstance).count();
                if (buttons != 7)
                    throw new AssertionError("Expected one hotkey button, update and reroll toggles and no reset button");
            });
            context.clickScreenButton("Auto trade: H");
            context.getInput().holdShift();
            context.getInput().pressKey(InputConstants.KEY_T);
            context.getInput().releaseShift();
            context.runOnClient(client -> {
                TradeViewConfig.load();
                if (AutoTradeHotkey.key() != InputConstants.KEY_T || AutoTradeHotkey.modifier() != InputConstants.KEY_LSHIFT)
                    throw new AssertionError("Combination capture failed");
            });
            context.clickScreenButton("Auto trade: Left Shift + T");
            context.getInput().holdKey(InputConstants.KEY_G);
            context.getInput().pressKey(InputConstants.KEY_H);
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.runOnClient(client -> {
                if (AutoTradeHotkey.key() != InputConstants.KEY_H || AutoTradeHotkey.modifier() != InputConstants.KEY_G)
                    throw new AssertionError("Arbitrary key pair capture failed");
            });
            context.clickScreenButton("Auto trade: G + H");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.runOnClient(client -> {
                if (AutoTradeHotkey.key() != -1 || AutoTradeHotkey.modifier() != -1)
                    throw new AssertionError("Escape did not clear binding");
            });
            context.clickScreenButton("Auto trade: None");
            context.getInput().holdKey(InputConstants.KEY_T);
            context.getInput().pressKey(InputConstants.KEY_LSHIFT);
            context.getInput().releaseKey(InputConstants.KEY_T);
            context.runOnClient(client -> {
                if (AutoTradeHotkey.key() != InputConstants.KEY_T || AutoTradeHotkey.modifier() != InputConstants.KEY_LSHIFT)
                    throw new AssertionError("Reversed combination capture failed");
            });
            context.clickScreenButton("Unlimited Trades: OFF");
            context.waitFor(client -> UnlimitedTradesClient.isEnabled());
            context.waitTicks(2);
            context.takeScreenshot("tradeview-auto-trade-config");
            context.runOnClient(client -> client.setScreen(null));

            // Enable through the real menu packet, then trade past the original maxUses on the server.
            UUID unlimited = open(context, world, inv -> inv.setItem(0, new ItemStack(Items.PAPER, 20)),
                offer(new ItemCost(Items.PAPER), null, new ItemStack(Items.EMERALD), 2));
            select(context, world, 0);
            context.runOnClient(client -> {
                if (((MerchantScreen) client.screen).getMenu().getOffers().getFirst().getMaxUses() != Integer.MAX_VALUE)
                    throw new AssertionError("Unlimited limit was not sent to client");
            });
            start(context);
            assertTrades(context, world, unlimited, 0, 20);
            context.runOnClient(client -> UnlimitedTradesClient.setEnabled(false));
            context.waitFor(client -> !UnlimitedTradesClient.isEnabled()
                && ((MerchantScreen) client.screen).getMenu().getOffers().getFirst().isOutOfStock());
            world.getServer().runOnServer(server -> {
                var trade = ((Villager) server.overworld().getEntity(unlimited)).getOffers().getFirst();
                if (trade.getMaxUses() != 2 || !trade.isOutOfStock())
                    throw new AssertionError("Original stock limit was not restored");
                UnlimitedTradesServer.initialize(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
                if (UnlimitedTradesServer.isEnabled()) throw new AssertionError("Disabled unlimited setting was not persisted");
            });
            context.runOnClient(client -> UnlimitedTradesClient.setEnabled(true));
            context.waitFor(client -> UnlimitedTradesClient.isEnabled()
                && !((MerchantScreen) client.screen).getMenu().getOffers().getFirst().isOutOfStock());
            // Reloading the server preferences retains the switch without changing original offers.
            world.getServer().runOnServer(server -> {
                UnlimitedTradesServer.initialize(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
                if (!UnlimitedTradesServer.isEnabled()) throw new AssertionError("Unlimited setting was not persisted");
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(0, new ItemStack(Items.PAPER, 5));
                player.containerMenu.broadcastChanges();
            });
            TestSync.packets(context);
            start(context);
            assertTrades(context, world, unlimited, 0, 25);
            context.runOnClient(client -> client.player.closeContainer());
        } finally {
            UnlimitedTradesServer.setEnabled(unlimitedBefore, null);
            context.runOnClient(client -> {
                TradeViewConfig.setAutoTradeHotkey(keyBefore, modifierBefore);
                TradeViewConfig.setEnabled(enabledBefore);
                TradeViewConfig.setUpdateCheckEnabled(updateCheckBefore);
            });
        }
    }
}
