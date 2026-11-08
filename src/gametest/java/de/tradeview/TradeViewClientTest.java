package de.tradeview;

import de.tradeview.client.PreviewRow;
import de.tradeview.client.TradeViewConfig;
import de.tradeview.client.TradeViewConfigScreen;
import de.tradeview.client.TradeViewModMenu;
import de.tradeview.mixin.AbstractWidgetAccessor;
import de.tradeview.testmixin.TestVillagerAccessor;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class TradeViewClientTest implements FabricClientGameTest {
    private static void testCommands(Minecraft client) {
        var dispatcher = ClientCommands.getActiveDispatcher();
        if (dispatcher == null || dispatcher.getRoot().getChild("tradepreview") == null)
            throw new AssertionError("Client command was not registered");
        AtomicInteger feedback = new AtomicInteger();
        var source = (FabricClientCommandSource) Proxy.newProxyInstance(
            FabricClientCommandSource.class.getClassLoader(), new Class<?>[] {FabricClientCommandSource.class},
            (proxy, method, args) -> {
                if (method.getName().equals("sendFeedback")) { feedback.incrementAndGet(); return null; }
                if (method.getName().equals("getClient")) return client;
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
        try {
            dispatcher.execute("tradepreview off", source);
            TradeViewConfig.load();
            if (TradeViewConfig.isEnabled()) throw new AssertionError("Command did not disable TradeView");
            dispatcher.execute("tradepreview on", source);
            TradeViewConfig.load();
            if (!TradeViewConfig.isEnabled()) throw new AssertionError("Command did not enable TradeView");
            dispatcher.execute("tradepreview side left", source);
            TradeViewConfig.load();
            if (!TradeViewConfig.isPreviewInTradeList()) throw new AssertionError("Left command did not select inline view");
            dispatcher.execute("tradepreview side right", source);
            TradeViewConfig.load();
            if (TradeViewConfig.isPreviewInTradeList()) throw new AssertionError("Right command did not select side view");
            if (feedback.get() != 4) throw new AssertionError("Client commands did not report their result");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("Client command failed", e);
        } finally {
            TradeViewConfig.setEnabled(true);
            TradeViewConfig.setPreviewInTradeList(false);
        }
    }
    private static int containerField(MerchantScreen screen, String name) {
        try {
            var field = AbstractContainerScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return (int) field.get(screen);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void assertLayout(MerchantScreen screen) {
        int left = containerField(screen, "leftPos"), guiWidth = containerField(screen, "imageWidth");
        int x = coordinate(screen, "tradeview$x"), w = coordinate(screen, "tradeview$width");
        if (left < 0 || x < left + guiWidth + 4 || x + w > screen.width)
            throw new AssertionError("Preview overlaps the merchant or leaves the screen at width " + screen.width);
        if (coordinate(screen, "tradeview$y") != containerField(screen, "topPos"))
            throw new AssertionError("Preview top edge is misaligned");
        if (coordinate(screen, "tradeview$count") < 1) throw new AssertionError("Preview has no visible rows");
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && child.getClass().getSimpleName().equals("TradeOfferButton")
                && widget.getX() != left + 5) throw new AssertionError("Merchant trade button did not follow the GUI");
        }
    }

    private static void assertInlineLayout(MerchantScreen screen) {
        int left = containerField(screen, "leftPos"), top = containerField(screen, "topPos");
        int guiWidth = containerField(screen, "imageWidth");
        int x = coordinate(screen, "tradeview$x"), y = coordinate(screen, "tradeview$y");
        int w = coordinate(screen, "tradeview$width"), h = coordinate(screen, "tradeview$height");
        if (left != (screen.width - guiWidth) / 2 || x != left + 5 || w != 89
            || y != top + 18 + 20 * Math.min(7, screen.getMenu().getOffers().size())
            || y + h != top + 158 || x + w > screen.width)
            throw new AssertionError("Inline preview is not aligned with the trade list at width " + screen.width);
        if (coordinate(screen, "tradeview$count") < 1)
            throw new AssertionError("Inline preview has no visible rows");
    }

    private static void testConfigReload() {
        var original = FabricLoader.getInstance().getConfigDir();
        java.nio.file.Path temporary = null;
        try {
            temporary = Files.createTempDirectory(original, "tradeview-test-");
            TradeViewConfig.initialize(temporary);
            TradeViewConfig.setPreviewVisible(false);
            TradeViewConfig.setPreviewInTradeList(true);
            TradeViewConfig.initialize(temporary);
            if (TradeViewConfig.isPreviewVisible()) throw new AssertionError("Hidden setting lost on initialization");
            if (!TradeViewConfig.isPreviewInTradeList()) throw new AssertionError("Inline setting lost on initialization");
            TradeViewConfig.setPreviewVisible(true);
            TradeViewConfig.setPreviewInTradeList(false);
            TradeViewConfig.initialize(temporary);
            if (!TradeViewConfig.isPreviewVisible()) throw new AssertionError("Visible setting lost on initialization");
            if (TradeViewConfig.isPreviewInTradeList()) throw new AssertionError("Side setting lost on initialization");
            TradeViewConfig.setPreviewVisible(false);
            var file = temporary.resolve("tradeview.properties");
            Files.delete(file);
            Files.createDirectory(file);
            TradeViewConfig.load();
            if (TradeViewConfig.isPreviewVisible()) throw new AssertionError("Read failure reset the hidden setting");
            Files.delete(file);
            Files.writeString(file, "previewVisible=" + (char) 92 + "uINVALID");
            TradeViewConfig.load();
            if (TradeViewConfig.isPreviewVisible()) throw new AssertionError("Malformed file reset the hidden setting");
        } catch (IOException e) { throw new AssertionError(e); }
        finally {
            TradeViewConfig.initialize(original);
            if (temporary != null) {
                try { Files.deleteIfExists(temporary.resolve("tradeview.properties")); Files.delete(temporary); }
                catch (IOException e) { throw new AssertionError(e); }
            }
        }
    }

    private static int coordinate(MerchantScreen screen, String method) {
        try {
            var accessor = MerchantScreen.class.getDeclaredMethod(method);
            accessor.setAccessible(true);
            return (int) accessor.invoke(screen);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static boolean inlinePage(MerchantScreen screen) {
        try {
            var field = MerchantScreen.class.getDeclaredField("tradeview$inlinePage");
            field.setAccessible(true);
            return field.getBoolean(screen);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static long visibleOfferButtons(MerchantScreen screen) {
        return screen.children().stream().filter(child -> child instanceof AbstractWidget widget
            && child.getClass().getSimpleName().equals("TradeOfferButton") && widget.visible).count();
    }

    private static boolean offerButtonHovered(MerchantScreen screen) {
        return screen.children().stream().anyMatch(child -> child instanceof AbstractWidget widget
            && child.getClass().getSimpleName().equals("TradeOfferButton") && widget.isHovered());
    }
    @SuppressWarnings("unchecked")
    private static List<PreviewRow> rows(MerchantScreen screen) {
        try {
            var field = MerchantScreen.class.getDeclaredField("tradeview$rows");
            field.setAccessible(true);
            return List.copyOf((List<PreviewRow>) field.get(screen));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> testConfigReload());
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            context.runOnClient(client -> {
                if (!(new TradeViewModMenu().getModConfigScreenFactory().create(client.gui.screen()) instanceof TradeViewConfigScreen)) {
                    throw new AssertionError("Mod Menu did not receive the TradeView config screen");
                }
            });
        }
        context.runOnClient(client -> {
            boolean enabledBefore = TradeViewConfig.isEnabled();
            boolean previewBefore = TradeViewConfig.isPreviewVisible();
            boolean inlineBefore = TradeViewConfig.isPreviewInTradeList();
            try {
                TradeViewConfig.setPreviewVisible(false);
                TradeViewConfig.load();
                if (TradeViewConfig.isPreviewVisible()) throw new AssertionError("Hidden preview setting was not persisted");
                TradeViewConfig.setEnabled(false);
                TradeViewConfig.load();
                if (TradeViewConfig.isEnabled()) throw new AssertionError("Disabled mod setting was not persisted");
            } finally {
                TradeViewConfig.setEnabled(enabledBefore);
                TradeViewConfig.setPreviewVisible(previewBefore);
                TradeViewConfig.setPreviewInTradeList(inlineBefore);
            }
            TradeViewConfig.setEnabled(true);
            TradeViewConfig.setPreviewVisible(true);
        });
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            UUID id = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
                villager.setPos(player.getX() + 1, player.getY(), player.getZ());
                villager.setNoAi(true);
                villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess()
                    .lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN)));
                villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), villager.blockPosition()));
                level.addFreshEntity(villager);
                villager.mobInteract(player, InteractionHand.MAIN_HAND);
                return villager.getUUID();
            });
            context.waitFor(client -> client.gui.screen() instanceof MerchantScreen screen && rows(screen).size() >= 8);
            context.runOnClient(TradeViewClientTest::testCommands);
            context.waitTicks(3);
            context.takeScreenshot("tradeview-preview");
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.gui.screen();
                for (int width : new int[] {320, 360, 427, 640}) {
                    screen.resize(width, 240);
                    assertLayout(screen);
                }
                screen.resize(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                assertLayout(screen);
            });
            for (int scale : new int[] {1, 0, 4}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    assertLayout((MerchantScreen) client.gui.screen());
                });
                context.waitTicks(3);
                context.takeScreenshot("tradeview-scale-" + scale);
            }
            int windowWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
            int windowHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
            context.runOnClient(client -> client.getWindow().setWindowed(640, 480));
            context.waitFor(client -> client.gui.screen() instanceof MerchantScreen screen && screen.width == 320);
            context.runOnClient(client -> assertLayout((MerchantScreen) client.gui.screen()));
            context.waitTicks(3);
            context.takeScreenshot("tradeview-narrow");
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.gui.screen();
                var previewRows = rows(screen);
                int x = coordinate(screen, "tradeview$x") + 10, y = coordinate(screen, "tradeview$y") + 30;
                for (int i = 0; i < previewRows.size(); i++) {
                    if (previewRows.get(i).offer() != null && !previewRows.get(i).offer().getCostB().isEmpty()) break;
                    screen.mouseScrolled(x, y, 0, -1);
                }
                assertLayout(screen);
            });
            context.waitTicks(3);
            context.takeScreenshot("tradeview-narrow-two-costs");
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.gui.screen();
                int x = coordinate(screen, "tradeview$x") + 10, y = coordinate(screen, "tradeview$y") + 30;
                for (int i = 0; i < 20; i++) screen.mouseScrolled(x, y, 0, -1);
                assertLayout(screen);
            });
            context.waitTicks(3);
            context.takeScreenshot("tradeview-narrow-end");
            context.runOnClient(client -> client.getWindow().setWindowed(windowWidth, windowHeight));
            context.waitFor(client -> client.getWindow().getScreenWidth() == windowWidth);
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.gui.screen();
                int x = coordinate(screen, "tradeview$x") + 10, y = coordinate(screen, "tradeview$y") + 30;
                for (int i = 0; i < 20; i++) {
                    if (!screen.mouseScrolled(x, y, 0, -1)) throw new AssertionError("Preview did not consume scroll");
                }
            });
            context.waitTicks(2);
            context.takeScreenshot("tradeview-master-preview");
            context.runOnClient(client -> {
                TradeViewConfig.setPreviewInTradeList(true);
                var screen = (MerchantScreen) client.gui.screen();
                for (int width : new int[] {320, 360, 427, 640}) {
                    screen.resize(width, 240);
                    assertInlineLayout(screen);
                }
                screen.resize(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                assertInlineLayout(screen);
            });
            for (int scale : new int[] {1, 0, 4}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    assertInlineLayout((MerchantScreen) client.gui.screen());
                });
                context.waitTicks(2);
                context.takeScreenshot("tradeview-inline-scale-" + scale);
            }
            context.runOnClient(client -> client.getWindow().setWindowed(640, 480));
            context.waitFor(client -> client.gui.screen() instanceof MerchantScreen screen && screen.width == 320);
            context.runOnClient(client -> assertInlineLayout((MerchantScreen) client.gui.screen()));
            context.waitTicks(2);
            context.takeScreenshot("tradeview-inline-narrow");
            context.runOnClient(client -> client.getWindow().setWindowed(windowWidth, windowHeight));
            context.waitFor(client -> client.getWindow().getScreenWidth() == windowWidth);
            context.runOnClient(client -> TradeViewConfig.setPreviewInTradeList(false));
            context.waitTicks(2);
            context.runOnClient(client -> assertLayout((MerchantScreen) client.gui.screen()));
            context.clickScreenButton("Preview: On");
            context.runOnClient(client -> {
                if (TradeViewConfig.isPreviewVisible()) throw new AssertionError("Preview toggle did not hide preview");
                TradeViewConfig.load();
                if (TradeViewConfig.isPreviewVisible()) throw new AssertionError("Hidden preview was not saved");
            });
            context.waitTicks(2);
            context.takeScreenshot("tradeview-hidden");
            context.runOnClient(client -> client.player.closeContainer());
            context.waitTicks(2);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ((Villager) server.overworld().getEntity(id)).mobInteract(player, InteractionHand.MAIN_HAND);
            });
            context.waitFor(client -> client.gui.screen() instanceof MerchantScreen screen && rows(screen).size() >= 8);
            context.runOnClient(client -> {
                if (TradeViewConfig.isPreviewVisible()) throw new AssertionError("Reopening trade reset the hidden setting");
                var screen = (MerchantScreen) client.gui.screen();
                if (containerField(screen, "leftPos") != (screen.width - containerField(screen, "imageWidth")) / 2)
                    throw new AssertionError("Hidden preview left the merchant off-center");
            });
            context.clickScreenButton("Preview: Off");
            context.runOnClient(client -> {
                TradeViewConfig.load();
                if (!TradeViewConfig.isPreviewVisible()) throw new AssertionError("Shown preview was not saved");
            });
            if (FabricLoader.getInstance().isModLoaded("trade_cycling")) {
                var before = context.computeOnClient(client -> rows((MerchantScreen) client.gui.screen()));
                int revision = world.getServer().computeOnServer(server -> ((PreviewVillager) server.overworld().getEntity(id)).tradeview$revision());
                context.runOnClient(client -> {
                    try {
                        var packet = (CustomPacketPayload) Class.forName("de.maxhenkel.tradecycling.net.CycleTradesPacket").getConstructor().newInstance();
                        ClientPlayNetworking.send(packet);
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                });
                context.waitFor(client -> client.gui.screen() instanceof MerchantScreen screen && rows(screen).get(1) != before.get(1));
                int after = world.getServer().computeOnServer(server -> ((PreviewVillager) server.overworld().getEntity(id)).tradeview$revision());
                if (after == revision) throw new AssertionError("Real Trade Cycling packet did not invalidate preview");
                context.waitTicks(3);
                context.takeScreenshot("tradeview-after-cycle");
            }
            world.getServer().runOnServer(server -> {
                Villager villager = (Villager) server.overworld().getEntity(id);
                for (int i = 0; i < 3; i++) ((TestVillagerAccessor) villager).tradeview$levelUp(server.overworld());
            });
            context.runOnClient(client -> client.player.closeContainer());
            context.waitTicks(2);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ((Villager) server.overworld().getEntity(id)).mobInteract(player, InteractionHand.MAIN_HAND);
            });
            context.waitFor(client -> client.gui.screen() instanceof MerchantScreen screen
                && screen.getMenu().getOffers().size() >= 7 && !rows(screen).isEmpty());
            context.runOnClient(client -> TradeViewConfig.setPreviewInTradeList(true));
            // Seed a hovered vanilla row immediately before switching pages. This isolates the stale
            // tooltip regression from the GLFW/SDL cursor backend and from intervening rendered frames.
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.gui.screen();
                var button = screen.children().stream().filter(child -> child instanceof AbstractWidget widget
                    && child.getClass().getSimpleName().equals("TradeOfferButton") && widget.visible).findFirst().orElseThrow();
                ((AbstractWidgetAccessor) button).tradeview$setHovered(true);
                int left = containerField(screen, "leftPos"), top = containerField(screen, "topPos");
                for (int i = 0; i < 20 && !inlinePage(screen); i++)
                    screen.mouseScrolled(left + 20, top + 50, 0, -1);
                if (!inlinePage(screen) || coordinate(screen, "tradeview$y") != top + 18)
                    throw new AssertionError("Preview page did not open inside a full trade list");
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (offerButtonHovered((MerchantScreen) client.gui.screen()))
                    throw new AssertionError("Hidden vanilla trade kept a stale hover over the preview page");
            });
            context.takeScreenshot("tradeview-inline-full-list");
            // Switching the preview off while its page is open must bring back the normal trade rows.
            context.clickScreenButton("Preview: On");
            context.waitTicks(2);
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.gui.screen();
                if (inlinePage(screen)) throw new AssertionError("Hidden preview left its page open");
                long expected = Math.min(7, screen.getMenu().getOffers().size());
                if (visibleOfferButtons(screen) != expected)
                    throw new AssertionError("Hiding the preview page left vanilla trades without buttons");
            });
            context.takeScreenshot("tradeview-inline-hidden-from-page");
            context.clickScreenButton("Preview: Off");
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.gui.screen();
                int left = containerField(screen, "leftPos"), top = containerField(screen, "topPos");
                for (int i = 0; i < 20 && !inlinePage(screen); i++)
                    screen.mouseScrolled(left + 20, top + 50, 0, -1);
                if (!inlinePage(screen)) throw new AssertionError("Preview page did not reopen after showing it again");
            });
            context.runOnClient(client -> {
                var screen = (MerchantScreen) client.gui.screen();
                int left = containerField(screen, "leftPos"), top = containerField(screen, "topPos");
                screen.mouseScrolled(left + 20, top + 50, 0, 1);
                if (inlinePage(screen)) throw new AssertionError("Preview page did not return to normal trades");
                TradeViewConfig.setPreviewInTradeList(false);
            });
            world.getServer().runOnServer(server -> {
                Villager villager = (Villager) server.overworld().getEntity(id);
                ((TestVillagerAccessor) villager).tradeview$levelUp(server.overworld());
            });
            context.waitFor(client -> client.gui.screen() instanceof MerchantScreen screen && rows(screen).isEmpty());
            context.waitTicks(2);
            context.takeScreenshot("tradeview-at-master");
            context.runOnClient(client -> client.player.closeContainer());
            if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                context.runOnClient(client -> {
                    var screen = new TradeViewModMenu().getModConfigScreenFactory().create(client.gui.screen());
                    if (!(screen instanceof TradeViewConfigScreen)) throw new AssertionError("Mod Menu config screen missing");
                    client.gui.setScreen(screen);
                });
                context.clickScreenButton("TradeView enabled: ON");
                context.runOnClient(client -> {
                    TradeViewConfig.load();
                    if (TradeViewConfig.isEnabled()) throw new AssertionError("Disabled state was not saved");
                });
                context.clickScreenButton("TradeView enabled: OFF");
                context.clickScreenButton("Preview position: Beside trades");
                context.runOnClient(client -> {
                    TradeViewConfig.load();
                    if (!TradeViewConfig.isPreviewInTradeList()) throw new AssertionError("Inline position was not saved");
                });
                context.clickScreenButton("Preview position: In trade list");
                context.runOnClient(client -> {
                    TradeViewConfig.load();
                    if (!TradeViewConfig.isEnabled()) throw new AssertionError("Enabled state was not saved");
                    if (TradeViewConfig.isPreviewInTradeList()) throw new AssertionError("Side position was not restored");
                    client.gui.setScreen(null);
                });
            }
        }
    }
}
