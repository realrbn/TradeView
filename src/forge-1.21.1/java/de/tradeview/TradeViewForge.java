package de.tradeview;

import de.tradeview.client.TradeViewForgeClient;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

@Mod("tradeview")
public final class TradeViewForge {
    // Optional, so TradeView clients still join servers without it and vice versa.
    public static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath("tradeview", "main"))
        .networkProtocolVersion(2).optional().payloadChannel()
        .play().clientbound()
        // The lambdas only load client classes when invoked.
        .addMain(PreviewPayload.TYPE, PreviewPayload.CODEC,
            (payload, context) -> de.tradeview.client.TradeViewClient.receive(payload))
        .addMain(UnlimitedTradesStatus.TYPE, UnlimitedTradesStatus.CODEC,
            (payload, context) -> de.tradeview.client.UnlimitedTradesClient.receive(payload))
        .serverbound()
        .addMain(ClientSettingsPayload.TYPE, ClientSettingsPayload.CODEC, (payload, context) -> {
            if (context.getSender() != null) TradeViewServer.INSTANCE.settings(context.getSender(), payload);
        })
        .addMain(UnlimitedTradesRequest.TYPE, UnlimitedTradesRequest.CODEC, (payload, context) -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (payload.change()) UnlimitedTradesServer.request(player.server, player, payload.enabled());
            else UnlimitedTradesServer.sendStatus(player.server, player);
        })
        .addMain(TradeRerollRequest.TYPE, TradeRerollRequest.CODEC, (payload, context) -> {
            if (context.getSender() != null) TradeRerollServer.request(context.getSender(), payload.containerId());
        })
        .addMain(TradeFindRequest.TYPE, TradeFindRequest.CODEC, (payload, context) -> {
            ServerPlayer player = context.getSender();
            if (player != null) TradeFindServer.request(player.server, player, payload);
        })
        .build();

    public TradeViewForge(FMLJavaModLoadingContext context) {
        TradeViewNetwork.initialize(new TradeViewNetwork.Transport() {
            @Override public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return CHANNEL.isRemotePresent(player.connection.getConnection());
            }
            @Override public void send(ServerPlayer player, CustomPacketPayload payload) {
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
            }
        });
        UnlimitedTradesServer.initialize(FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::join);
        MinecraftForge.EVENT_BUS.addListener(this::disconnect);
        MinecraftForge.EVENT_BUS.addListener(this::stop);
        if (FMLEnvironment.dist == Dist.CLIENT) TradeViewForgeClient.initialize(context);
    }

    private void tick(TickEvent.ServerTickEvent.Post event) {
        TradeFindServer.tick(event.getServer());
        TradeViewServer.INSTANCE.tick(event.getServer(), TradeViewNetwork::send);
    }

    private void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) UnlimitedTradesServer.sendStatus(player.server, player);
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
