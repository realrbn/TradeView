package de.tradeview.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TradeViewNeoForgeClient {
    /** Auto trading for the open merchant screen; NeoForge's screen events are global, not per screen. */
    private static AutoTrade trading;

    private TradeViewNeoForgeClient() {}

    public static void initialize(IEventBus modBus, ModContainer container) {
        TradeViewClient.initialize(FMLPaths.CONFIGDIR.get(), new TradeViewClientNetwork.Transport() {
            @Override public boolean canSend(CustomPacketPayload.Type<?> type) {
                var connection = Minecraft.getInstance().getConnection();
                return connection != null && connection.hasChannel(type);
            }
            @Override public void send(CustomPacketPayload payload) { PacketDistributor.sendToServer(payload); }
        }, false);
        TradeRerollHotkey.create();
        UpdateChecker.initialize("neoforge",
            ModList.get().getModContainerById("minecraft").orElseThrow().getModInfo().getVersion().toString(),
            container.getModInfo().getVersion().toString());
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (IConfigScreenFactory) (ignored, parent) -> new TradeViewSettingsScreen(parent));
        modBus.addListener(TradeViewNeoForgeClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::join);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::leave);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::registerCommands);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::screenInit);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::keyPressed);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::keyReleased);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::mouseClicked);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::screenClosing);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::tickStart);
        NeoForge.EVENT_BUS.addListener(TradeViewNeoForgeClient::tickEnd);
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

    private static void tickStart(ClientTickEvent.Pre event) {
        if (trading != null) trading.tick();
    }

    private static void tickEnd(ClientTickEvent.Post event) {
        TradeFindClient.tick(Minecraft.getInstance());
    }
}
