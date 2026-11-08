package de.tradeview;

import de.tradeview.client.TradeViewForgeClient;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

@Mod("tradeview")
public final class TradeViewForge {
    // Optional, so TradeView clients still join servers without it and vice versa.
    public static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath("tradeview", "main"))
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
            if (payload.change()) UnlimitedTradesServer.request(player.level().getServer(), player, payload.enabled());
            else UnlimitedTradesServer.sendStatus(player.level().getServer(), player);
        })
        .addMain(TradeRerollRequest.TYPE, TradeRerollRequest.CODEC, (payload, context) -> {
            if (context.getSender() != null) TradeRerollServer.request(context.getSender(), payload.containerId());
        })
        .addMain(TradeFindRequest.TYPE, TradeFindRequest.CODEC, (payload, context) -> {
            ServerPlayer player = context.getSender();
            if (player != null) TradeFindServer.request(player.level().getServer(), player, payload);
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
        TickEvent.ServerTickEvent.Post.BUS.addListener(TradeViewForge::tick);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(TradeViewForge::join);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(TradeViewForge::disconnect);
        ServerStoppedEvent.BUS.addListener(TradeViewForge::stop);
        if (FMLEnvironment.dist == Dist.CLIENT) TradeViewForgeClient.initialize(context);
    }

    private static void tick(TickEvent.ServerTickEvent.Post event) {
        TradeFindServer.tick(event.server());
        TradeViewServer.INSTANCE.tick(event.server(), TradeViewNetwork::send);
    }

    private static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            UnlimitedTradesServer.sendStatus(player.level().getServer(), player);
    }

    private static void disconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TradeFindServer.disconnect(player);
            TradeViewServer.INSTANCE.disconnect(player);
        }
    }

    private static void stop(ServerStoppedEvent event) {
        TradeFindServer.stop();
        TradeViewServer.INSTANCE.stop();
    }
}
