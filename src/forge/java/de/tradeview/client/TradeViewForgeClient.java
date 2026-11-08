package de.tradeview.client;

import de.tradeview.TradeViewForge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

public final class TradeViewForgeClient {
    /** Auto trading for the open merchant screen; Forge's screen events are global, not per screen. */
    private static AutoTrade trading;

    private TradeViewForgeClient() {}

    public static void initialize(FMLJavaModLoadingContext context) {
        TradeViewClient.initialize(FMLPaths.CONFIGDIR.get(), new TradeViewClientNetwork.Transport() {
            @Override public boolean canSend(CustomPacketPayload.Type<?> type) {
                var connection = Minecraft.getInstance().getConnection();
                return connection != null && TradeViewForge.CHANNEL.isRemotePresent(connection.getConnection());
            }
            @Override public void send(CustomPacketPayload payload) {
                TradeViewForge.CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
            }
        }, false);
        TradeRerollHotkey.create(KeyMapping.Category.register(TradeRerollHotkey.CATEGORY_ID));
        UpdateChecker.initialize("forge",
            ModList.getModContainerById("minecraft").orElseThrow().getModInfo().getVersion().toString(),
            context.getContainer().getModInfo().getVersion().toString());
        context.getContainer().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory(TradeViewSettingsScreen::new));
        RegisterKeyMappingsEvent.BUS.addListener(TradeViewForgeClient::registerKeys);
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(TradeViewForgeClient::join);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(TradeViewForgeClient::leave);
        RegisterClientCommandsEvent.BUS.addListener(TradeViewForgeClient::registerCommands);
        ScreenEvent.Init.Post.BUS.addListener(TradeViewForgeClient::screenInit);
        ScreenEvent.KeyPressed.Pre.BUS.addListener(TradeViewForgeClient::keyPressed);
        ScreenEvent.KeyReleased.Pre.BUS.addListener(TradeViewForgeClient::keyReleased);
        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener(TradeViewForgeClient::mouseClicked);
        ScreenEvent.Closing.BUS.addListener(TradeViewForgeClient::screenClosing);
        TickEvent.ClientTickEvent.Pre.BUS.addListener(TradeViewForgeClient::tickStart);
        TickEvent.ClientTickEvent.Post.BUS.addListener(TradeViewForgeClient::tickEnd);
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

    /** Returning true cancels the event, so the screen does not handle the key as well. */
    private static boolean keyPressed(ScreenEvent.KeyPressed.Pre event) {
        return trading != null && trading.keyPressed(event.getInfo())
            || TradeRerollHotkey.keyPressed(Minecraft.getInstance(), event.getScreen(), event.getInfo());
    }

    private static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (trading != null) trading.keyReleased(event.getInfo());
    }

    private static void mouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (trading != null) trading.mouseClicked();
    }

    private static void screenClosing(ScreenEvent.Closing event) {
        if (trading == null) return;
        trading.removed();
        trading = null;
    }

    private static void tickStart(TickEvent.ClientTickEvent.Pre event) {
        if (trading != null) trading.tick();
    }

    private static void tickEnd(TickEvent.ClientTickEvent.Post event) {
        TradeFindClient.tick(Minecraft.getInstance());
    }
}
