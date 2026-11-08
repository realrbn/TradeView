package de.tradeview.client;

import de.tradeview.TradeViewForge;
import de.tradeview.TradeViewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

public final class TradeViewForgeClient {
    /** Auto trading for the open merchant screen; Forge's screen events are global, not per screen. */
    private static AutoTrade trading;

    private TradeViewForgeClient() {}

    public static void initialize() {
        TradeViewClient.initialize(FMLPaths.CONFIGDIR.get(), new TradeViewClientNetwork.Transport() {
            @Override public boolean canSend(ResourceLocation id) {
                var connection = Minecraft.getInstance().getConnection();
                return connection != null && TradeViewForge.CHANNEL.isRemotePresent(connection.getConnection());
            }
            @Override public void send(TradeViewPacket packet) { TradeViewForge.CHANNEL.sendToServer(packet); }
        }, false);
        TradeRerollHotkey.create();
        ModLoadingContext context = ModLoadingContext.get();
        UpdateChecker.initialize("forge",
            ModList.get().getModContainerById("minecraft").orElseThrow().getModInfo().getVersion().toString(),
            context.getActiveContainer().getModInfo().getVersion().toString());
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new TradeViewSettingsScreen(parent)));
        FMLJavaModLoadingContext.get().getModEventBus().addListener(TradeViewForgeClient::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::join);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::leave);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::screenInit);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::keyPressed);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::keyReleased);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::mouseClicked);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::screenClosing);
        MinecraftForge.EVENT_BUS.addListener(TradeViewForgeClient::tick);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TradeRerollHotkey.KEY);
    }

    private static void join(ClientPlayerNetworkEvent.LoggingIn event) {
        UnlimitedTradesClient.reset();
        TradeViewClient.sendSettings();
        Minecraft client = Minecraft.getInstance();
        UpdateChecker.joined(client, client.getConnection());
    }

    private static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        UnlimitedTradesClient.reset();
        TradeFindClient.disconnect();
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(TradeViewCommands.<CommandSourceStack>create(event.getBuildContext(),
            (source, message) -> source.sendSuccess(() -> message, false)));
    }

    private static void screenInit(ScreenEvent.Init.Post event) {
        // Re-initialization (e.g. resizing) creates a new run, as the per-screen Fabric hooks do.
        if (trading != null) trading.removed();
        trading = AutoTrade.attach(Minecraft.getInstance(), event.getScreen());
        if (trading != null) TradeFindClient.screenInitialized(event.getScreen());
    }

    private static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (trading != null && trading.keyPressed(event.getKeyCode(), event.getModifiers())
            || TradeRerollHotkey.keyPressed(Minecraft.getInstance(), event.getScreen(), event.getKeyCode(), event.getScanCode()))
            event.setCanceled(true);
    }

    private static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (trading != null) trading.keyReleased(event.getKeyCode());
    }

    private static void mouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (trading != null) trading.mouseClicked();
    }

    private static void screenClosing(ScreenEvent.Closing event) {
        if (trading == null) return;
        trading.removed();
        trading = null;
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (trading != null) trading.tick();
        } else TradeFindClient.tick(Minecraft.getInstance());
    }
}
