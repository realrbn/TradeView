package de.tradeview;

import de.tradeview.client.TradeViewNeoForgeClient;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod("tradeview")
public final class TradeViewNeoForge {
    public TradeViewNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
        TradeViewNetwork.initialize(new TradeViewNetwork.Transport() {
            @Override public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return player.connection.hasChannel(type);
            }
            @Override public void send(ServerPlayer player, CustomPacketPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        });
        UnlimitedTradesServer.initialize(FMLPaths.CONFIGDIR.get());
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::join);
        NeoForge.EVENT_BUS.addListener(this::disconnect);
        NeoForge.EVENT_BUS.addListener(this::stop);
        if (dist == Dist.CLIENT) TradeViewNeoForgeClient.initialize(modBus, container);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(PreviewPayload.TYPE, PreviewPayload.CODEC);
        registrar.playToClient(UnlimitedTradesStatus.TYPE, UnlimitedTradesStatus.CODEC);
        registrar.playToServer(ClientSettingsPayload.TYPE, ClientSettingsPayload.CODEC,
            (payload, context) -> TradeViewServer.INSTANCE.settings((ServerPlayer) context.player(), payload));
        registrar.playToServer(UnlimitedTradesRequest.TYPE, UnlimitedTradesRequest.CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (payload.change()) UnlimitedTradesServer.request(player.level().getServer(), player, payload.enabled());
            else UnlimitedTradesServer.sendStatus(player.level().getServer(), player);
        });
        registrar.playToServer(TradeRerollRequest.TYPE, TradeRerollRequest.CODEC,
            (payload, context) -> TradeRerollServer.request((ServerPlayer) context.player(), payload.containerId()));
        registrar.playToServer(TradeFindRequest.TYPE, TradeFindRequest.CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            TradeFindServer.request(player.level().getServer(), player, payload);
        });
    }

    private void tick(ServerTickEvent.Post event) {
        TradeFindServer.tick(event.getServer());
        TradeViewServer.INSTANCE.tick(event.getServer(), TradeViewNetwork::send);
    }

    private void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            UnlimitedTradesServer.sendStatus(player.level().getServer(), player);
    }

    private void disconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TradeFindServer.disconnect(player);
            TradeViewServer.INSTANCE.disconnect(player);
        }
    }

    private void stop(ServerStoppedEvent event) {
        TradeFindServer.stop();
        TradeViewServer.INSTANCE.stop();
    }
}
