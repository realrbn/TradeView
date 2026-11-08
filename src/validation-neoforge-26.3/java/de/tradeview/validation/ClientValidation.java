package de.tradeview.validation;

import de.tradeview.PreviewVillager;
import de.tradeview.client.TradeViewSettingsScreen;
import de.tradeview.client.TradeRerollScreen;
import de.tradeview.client.TradeViewConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scripted client run for Minecraft versions without Fabric's client gametest API: a real world, a real
 * villager and trade screen, screenshots of both preview positions, a reroll and the settings screen.
 * Run with {@code gradlew :<version>-neoforge:runValidation}; the result is written to validation-result.txt.
 */
@Mod(value = "tradeview_validation", dist = Dist.CLIENT)
public final class ClientValidation {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeView validation");
    private static final List<String> passed = new ArrayList<>();
    private static int step, wait;
    private static UUID villager;
    private static boolean previousInline, finished;
    private static BooleanSupplier rerolled;

    public ClientValidation() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        passed.add(message);
    }

    private static <T> T onServer(Minecraft mc, Function<MinecraftServer, T> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        return server.submit(() -> action.apply(server)).join();
    }

    private static Villager villager(MinecraftServer server) {
        return (Villager) server.overworld().getEntity(villager);
    }

    private static void tick(Minecraft mc) {
        if (finished || mc.gui.overlay() != null) return;
        try {
            if (wait > 0) { wait--; return; }
            switch (step) {
                case 0 -> {
                    if (!(mc.gui.screen() instanceof TitleScreen)) {
                        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)
                            mc.gui.setScreen(new TitleScreen());
                        return;
                    }
                    previousInline = TradeViewConfig.isPreviewInTradeList();
                    TradeViewConfig.setEnabled(true);
                    TradeViewConfig.setPreviewVisible(true);
                    TradeViewConfig.setPreviewInTradeList(false);
                    String name = "tradeview-validation-" + System.currentTimeMillis();
                    mc.createWorldOpenFlows().createFreshLevel(name,
                        new LevelSettings(name, GameType.CREATIVE,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
                    step++;
                }
                case 1 -> {
                    if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.gui.screen() != null) return;
                    wait = 60;
                    step++;
                }
                case 2 -> {
                    villager = onServer(mc, server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        Villager v = EntityTypes.VILLAGER.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        v.setPos(player.getX() + 1.5, player.getY(), player.getZ());
                        v.setNoAi(true);
                        v.setVillagerData(v.getVillagerData().withProfession(player.level().registryAccess(), VillagerProfession.LIBRARIAN));
                        player.level().addFreshEntity(v);
                        v.mobInteract(player, InteractionHand.MAIN_HAND);
                        return v.getUUID();
                    });
                    step++;
                }
                case 3 -> {
                    if (!(mc.gui.screen() instanceof MerchantScreen screen) || screen.getMenu().getOffers().isEmpty()) return;
                    int levels = onServer(mc, server -> ((PreviewVillager) villager(server)).tradeview$preview().size());
                    check(levels == 4, "Server reserved all four future levels");
                    wait = 30; // Preview packet and a few rendered frames.
                    step++;
                }
                case 4 -> {
                    shot(mc, "tradeview-validation-side");
                    TradeViewConfig.setPreviewInTradeList(true);
                    wait = 10;
                    step++;
                }
                case 5 -> {
                    shot(mc, "tradeview-validation-inline");
                    MerchantOffers before = onServer(mc, server -> villager(server).getOffers());
                    check(((TradeRerollScreen) mc.gui.screen()).tradeview$reroll(), "Reroll available for an untraded villager");
                    rerolled = () -> onServer(mc, server -> villager(server).getOffers() != before);
                    wait = 10;
                    step++;
                }
                case 6 -> {
                    check(rerolled.getAsBoolean(), "Reroll replaced the offers on the server");
                    wait = 20;
                    step++;
                }
                case 7 -> {
                    shot(mc, "tradeview-validation-rerolled");
                    onServer(mc, server -> {
                        var inventory = server.getPlayerList().getPlayers().get(0).getInventory();
                        var offer = villager(server).getOffers().get(0);
                        inventory.add(offer.getCostA().copyWithCount(offer.getCostA().getCount() * 2));
                        if (!offer.getCostB().isEmpty()) inventory.add(offer.getCostB().copyWithCount(offer.getCostB().getCount() * 2));
                        return null;
                    });
                    wait = 10;
                    step = 70;
                }
                case 70 -> {
                    // The default auto trade hotkey, through NeoForge's screen key events (KeyboardHandler.keyPress is private).
                    Screen screen = mc.gui.screen();
                    NeoForge.EVENT_BUS.post(new ScreenEvent.KeyPressed.Pre(screen, new KeyEvent(InputConstants.KEY_LSHIFT, 0, 0)));
                    check(NeoForge.EVENT_BUS.post(new ScreenEvent.KeyPressed.Pre(screen,
                        new KeyEvent(InputConstants.KEY_T, 0, InputConstants.MOD_SHIFT))).isCanceled(), "Shift + T is consumed as the auto trade hotkey");
                    NeoForge.EVENT_BUS.post(new ScreenEvent.KeyReleased.Pre(screen, new KeyEvent(InputConstants.KEY_T, 0, InputConstants.MOD_SHIFT)));
                    NeoForge.EVENT_BUS.post(new ScreenEvent.KeyReleased.Pre(screen, new KeyEvent(InputConstants.KEY_LSHIFT, 0, 0)));
                    wait = 60;
                    step = 71;
                }
                case 71 -> {
                    int uses = onServer(mc, server -> villager(server).getOffers().get(0).getUses());
                    check(uses == 2, "Auto trade (Shift + T) completed both affordable trades (" + uses + ")");
                    shot(mc, "tradeview-validation-auto-trade");
                    onServer(mc, server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        Villager v = villager(server);
                        var offer = v.getOffers().get(0);
                        for (int i = 0; i < 4; i++) v.getOffers().add(offer.copy());
                        player.sendMerchantOffers(player.containerMenu.containerId, v.getOffers(), v.getVillagerData().level(),
                            v.getVillagerXp(), v.showProgressBar(), v.canRestock());
                        return null;
                    });
                    wait = 10;
                    step = 72;
                }
                case 72 -> {
                    var screen = (MerchantScreen) mc.gui.screen();
                    check(screen.getMenu().getOffers().size() >= 5, "Five or more offers for the inline preview page");
                    // Scrolling down at the end of the trade list opens the preview page.
                    double x = (screen.width - 276) / 2.0 + 50, y = (screen.height - 166) / 2.0 + 50;
                    for (int i = 0; i < 3; i++) screen.mouseScrolled(x, y, 0, -1);
                    wait = 5;
                    step = 73;
                }
                case 73 -> {
                    long shown = mc.gui.screen().children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button
                        && child.getClass().getEnclosingClass() == MerchantScreen.class
                        && ((net.minecraft.client.gui.components.Button) child).visible).count();
                    check(shown == 0, "Preview page hides the vanilla trade buttons (" + shown + " visible)");
                    shot(mc, "tradeview-validation-inline-page");
                    mc.player.closeContainer();
                    mc.gui.setScreen(new TradeViewSettingsScreen(null));
                    wait = 10;
                    step = 8;
                }
                case 8 -> {
                    shot(mc, "tradeview-validation-config");
                    mc.gui.setScreen(null);
                    finish(mc, "PASS");
                }
                default -> { }
            }
        } catch (Throwable e) {
            LOGGER.error("TradeView validation failed", e);
            passed.add("FAILED: " + e);
            finish(mc, "FAIL");
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
        passed.add("Screenshot " + name);
    }

    private static void finish(Minecraft mc, String result) {
        finished = true;
        TradeViewConfig.setPreviewInTradeList(previousInline);
        String report = result + "\n" + String.join("\n", passed) + "\n";
        try {
            Files.writeString(Path.of("validation-result.txt"), report);
        } catch (Exception e) {
            LOGGER.error("Could not write the validation result", e);
        }
        LOGGER.info("TradeView validation {}:\n{}", result, report);
        mc.stop();
    }
}
