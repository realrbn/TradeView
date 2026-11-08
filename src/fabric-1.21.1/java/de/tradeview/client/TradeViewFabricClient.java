package de.tradeview.client;

import de.tradeview.PreviewPayload;
import de.tradeview.UnlimitedTradesStatus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class TradeViewFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        TradeViewClient.initialize(FabricLoader.getInstance().getConfigDir(), new TradeViewClientNetwork.Transport() {
            @Override public boolean canSend(CustomPacketPayload.Type<?> type) { return ClientPlayNetworking.canSend(type); }
            @Override public void send(CustomPacketPayload payload) { ClientPlayNetworking.send(payload); }
        }, true);
        KeyBindingHelper.registerKeyBinding(TradeRerollHotkey.create());
        FabricLoader loader = FabricLoader.getInstance();
        UpdateChecker.initialize("fabric",
            loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString(),
            loader.getModContainer("tradeview").orElseThrow().getMetadata().getVersion().getFriendlyString());
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            AutoTrade trading = AutoTrade.attach(client, screen);
            if (trading == null) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scanCode, modifiers) ->
                !trading.keyPressed(key, modifiers) && !TradeRerollHotkey.keyPressed(client, s, key, scanCode));
            ScreenKeyboardEvents.beforeKeyRelease(screen).register((s, key, scanCode, modifiers) -> trading.keyReleased(key));
            ScreenMouseEvents.beforeMouseClick(screen).register((s, mouseX, mouseY, button) -> trading.mouseClicked());
            ScreenEvents.beforeTick(screen).register(s -> trading.tick());
            ScreenEvents.remove(screen).register(s -> trading.removed());
            TradeFindClient.screenInitialized(screen);
        });
        ClientTickEvents.END_CLIENT_TICK.register(TradeFindClient::tick);
        ClientPlayConnectionEvents.INIT.register((handler, client) -> UnlimitedTradesClient.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            UnlimitedTradesClient.reset();
            TradeFindClient.disconnect();
        });
        ClientPlayNetworking.registerGlobalReceiver(UnlimitedTradesStatus.TYPE,
            (payload, context) -> UnlimitedTradesClient.receive(payload));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TradeViewClient.sendSettings();
            UpdateChecker.joined(client, handler);
        });
        ClientPlayNetworking.registerGlobalReceiver(PreviewPayload.TYPE,
            (payload, context) -> TradeViewClient.receive(payload));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
            dispatcher.register(TradeViewCommands.<FabricClientCommandSource>create(buildContext,
                FabricClientCommandSource::sendFeedback)));
    }
}
