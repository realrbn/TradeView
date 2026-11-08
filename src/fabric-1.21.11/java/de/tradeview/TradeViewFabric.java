package de.tradeview;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class TradeViewFabric implements ModInitializer {
    @Override public void onInitialize() {
        TradeViewNetwork.initialize(new TradeViewNetwork.Transport() {
            @Override public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return ServerPlayNetworking.canSend(player, type);
            }
            @Override public void send(ServerPlayer player, CustomPacketPayload payload) {
                ServerPlayNetworking.send(player, payload);
            }
        });
        UnlimitedTradesServer.initialize(FabricLoader.getInstance().getConfigDir());
        PayloadTypeRegistry.playS2C().register(UnlimitedTradesStatus.TYPE, UnlimitedTradesStatus.CODEC);
        PayloadTypeRegistry.playC2S().register(UnlimitedTradesRequest.TYPE, UnlimitedTradesRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UnlimitedTradesRequest.TYPE, (payload, context) -> {
            if (payload.change()) UnlimitedTradesServer.request(context.server(), context.player(), payload.enabled());
            else UnlimitedTradesServer.sendStatus(context.server(), context.player());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            UnlimitedTradesServer.sendStatus(server, handler.player));
        PayloadTypeRegistry.playC2S().register(TradeRerollRequest.TYPE, TradeRerollRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TradeRerollRequest.TYPE,
            (payload, context) -> TradeRerollServer.request(context.player(), payload.containerId()));
        PayloadTypeRegistry.playC2S().register(TradeFindRequest.TYPE, TradeFindRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TradeFindRequest.TYPE,
            (payload, context) -> TradeFindServer.request(context.server(), context.player(), payload));
        ServerTickEvents.END_SERVER_TICK.register(TradeFindServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TradeFindServer.disconnect(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TradeFindServer.stop());
        PayloadTypeRegistry.playS2C().register(PreviewPayload.TYPE, PreviewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientSettingsPayload.TYPE, ClientSettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClientSettingsPayload.TYPE,
            (payload, context) -> TradeViewServer.INSTANCE.settings(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            TradeViewServer.INSTANCE.disconnect(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TradeViewServer.INSTANCE.stop());
        ServerTickEvents.END_SERVER_TICK.register(server ->
            TradeViewServer.INSTANCE.tick(server, TradeViewNetwork::send));
    }
}
