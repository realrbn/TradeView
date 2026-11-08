package de.tradeview;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class TradeViewFabric implements ModInitializer {
    @Override public void onInitialize() {
        TradeViewNetwork.initialize(new TradeViewNetwork.Transport() {
            @Override public boolean canSend(ServerPlayer player, ResourceLocation id) {
                return ServerPlayNetworking.canSend(player, id);
            }
            @Override public void send(ServerPlayer player, TradeViewPacket packet) {
                ServerPlayNetworking.send(player, packet.id(), buf(packet));
            }
        });
        UnlimitedTradesServer.initialize(FabricLoader.getInstance().getConfigDir());
        receive(UnlimitedTradesRequest.ID, UnlimitedTradesRequest::read, (server, player, payload) -> {
            if (payload.change()) UnlimitedTradesServer.request(server, player, payload.enabled());
            else UnlimitedTradesServer.sendStatus(server, player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            UnlimitedTradesServer.sendStatus(server, handler.player));
        receive(TradeRerollRequest.ID, TradeRerollRequest::read,
            (server, player, payload) -> TradeRerollServer.request(player, payload.containerId()));
        receive(TradeFindRequest.ID, TradeFindRequest::read, TradeFindServer::request);
        ServerTickEvents.END_SERVER_TICK.register(TradeFindServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TradeFindServer.disconnect(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TradeFindServer.stop());
        receive(ClientSettingsPayload.ID, ClientSettingsPayload::read,
            (server, player, payload) -> TradeViewServer.INSTANCE.settings(player, payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            TradeViewServer.INSTANCE.disconnect(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TradeViewServer.INSTANCE.stop());
        ServerTickEvents.END_SERVER_TICK.register(server ->
            TradeViewServer.INSTANCE.tick(server, TradeViewNetwork::send));
    }

    public interface Handler<T> { void handle(MinecraftServer server, ServerPlayer player, T payload); }

    /** Decodes on the network thread and handles the packet on the server thread. */
    private static <T> void receive(ResourceLocation id, Function<FriendlyByteBuf, T> reader, Handler<T> handler) {
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, listener, buf, sender) -> {
            T payload = reader.apply(buf);
            server.execute(() -> handler.handle(server, player, payload));
        });
    }

    public static FriendlyByteBuf buf(TradeViewPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        return buf;
    }
}
