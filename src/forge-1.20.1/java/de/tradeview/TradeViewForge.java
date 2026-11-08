package de.tradeview;

import de.tradeview.client.TradeViewForgeClient;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod("tradeview")
public final class TradeViewForge {
    private static final String PROTOCOL = "2";
    // Optional, so TradeView clients still join servers without it and vice versa.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("tradeview", "main"), () -> PROTOCOL,
        NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    private static int nextId;

    public TradeViewForge() {
        // The lambdas only load client classes when invoked.
        register(PreviewPayload.class, PreviewPayload::read, NetworkDirection.PLAY_TO_CLIENT,
            (payload, player) -> de.tradeview.client.TradeViewClient.receive(payload));
        register(UnlimitedTradesStatus.class, UnlimitedTradesStatus::read, NetworkDirection.PLAY_TO_CLIENT,
            (payload, player) -> de.tradeview.client.UnlimitedTradesClient.receive(payload));
        register(ClientSettingsPayload.class, ClientSettingsPayload::read, NetworkDirection.PLAY_TO_SERVER,
            (payload, player) -> TradeViewServer.INSTANCE.settings(player, payload));
        register(UnlimitedTradesRequest.class, UnlimitedTradesRequest::read, NetworkDirection.PLAY_TO_SERVER,
            (payload, player) -> {
                if (payload.change()) UnlimitedTradesServer.request(player.server, player, payload.enabled());
                else UnlimitedTradesServer.sendStatus(player.server, player);
            });
        register(TradeRerollRequest.class, TradeRerollRequest::read, NetworkDirection.PLAY_TO_SERVER,
            (payload, player) -> TradeRerollServer.request(player, payload.containerId()));
        register(TradeFindRequest.class, TradeFindRequest::read, NetworkDirection.PLAY_TO_SERVER,
            (payload, player) -> TradeFindServer.request(player.server, player, payload));
        TradeViewNetwork.initialize(new TradeViewNetwork.Transport() {
            @Override public boolean canSend(ServerPlayer player, ResourceLocation id) {
                return CHANNEL.isRemotePresent(player.connection.connection);
            }
            @Override public void send(ServerPlayer player, TradeViewPacket packet) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        });
        UnlimitedTradesServer.initialize(FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::join);
        MinecraftForge.EVENT_BUS.addListener(this::disconnect);
        MinecraftForge.EVENT_BUS.addListener(this::stop);
        if (FMLEnvironment.dist == Dist.CLIENT) TradeViewForgeClient.initialize();
    }

    /** Handlers run on the main thread; the player is null for packets sent to the client. */
    private static <T extends TradeViewPacket> void register(Class<T> type, Function<FriendlyByteBuf, T> reader,
                                                             NetworkDirection direction, BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.messageBuilder(type, nextId++, direction)
            .encoder(TradeViewPacket::write).decoder(reader)
            .consumerMainThread((T payload, Supplier<NetworkEvent.Context> context) -> {
                ServerPlayer player = context.get().getSender();
                if (direction == NetworkDirection.PLAY_TO_CLIENT || player != null) handler.accept(payload, player);
            })
            .add();
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
