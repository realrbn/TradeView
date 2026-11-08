package de.tradeview;

import de.tradeview.mixin.MerchantMenuAccessor;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.LoggerFactory;

/** Loader-independent preview lifecycle; entrypoints supply the packet transport. */
public final class TradeViewServer {
    public static final TradeViewServer INSTANCE = new TradeViewServer();

    private record Sent(MerchantMenu menu, int revision, int level, int nextCheck, Map<Integer, MerchantOffers> levels) {}
    private final Map<ServerPlayer, Sent> sent = new WeakHashMap<>();
    private final Set<ServerPlayer> enabledPlayers = Collections.newSetFromMap(new WeakHashMap<>());

    private TradeViewServer() {}

    public void settings(ServerPlayer player, ClientSettingsPayload payload) {
        if (payload.enabled()) enabledPlayers.add(player);
        else {
            enabledPlayers.remove(player);
            sent.remove(player);
        }
    }

    public void disconnect(ServerPlayer player) {
        sent.remove(player);
        enabledPlayers.remove(player);
    }

    public void stop() {
        sent.clear();
        enabledPlayers.clear();
    }

    public void tick(MinecraftServer server, BiConsumer<ServerPlayer, PreviewPayload> send) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!enabledPlayers.contains(player)
                || !(player.containerMenu instanceof MerchantMenu menu)
                || !(((MerchantMenuAccessor) menu).tradeview$getTrader() instanceof Villager villager)) {
                sent.remove(player);
                continue;
            }
            PreviewVillager preview = (PreviewVillager) villager;
            Sent before = sent.get(player);
            int revision = preview.tradeview$revision(), level = villager.getVillagerData().getLevel();
            boolean changed = before == null || before.menu() != menu || before.revision() != revision || before.level() != level;
            if (!changed && server.getTickCount() < before.nextCheck()) continue;
            try {
                var levels = level < 5 ? ReliableRemoverCompat.filter(preview.tradeview$preview(player))
                    : Map.<Integer, MerchantOffers>of();
                if (changed || !samePreview(before.levels(), levels))
                    send.accept(player, new PreviewPayload(menu.containerId, levels));
                // Poll contextual trade changes without repeatedly resetting the client's scroll position.
                sent.put(player, new Sent(menu, preview.tradeview$revision(), level, server.getTickCount() + 20, levels));
            } catch (RuntimeException e) {
                LoggerFactory.getLogger("TradeView").debug("Could not preview modded villager trades", e);
                send.accept(player, new PreviewPayload(menu.containerId, Map.of()));
                sent.put(player, new Sent(menu, preview.tradeview$revision(), level, server.getTickCount() + 200, Map.of()));
            }
        }
    }

    private static boolean samePreview(Map<Integer, MerchantOffers> first, Map<Integer, MerchantOffers> second) {
        if (!first.keySet().equals(second.keySet())) return false;
        for (var entry : first.entrySet()) {
            MerchantOffers a = entry.getValue(), b = second.get(entry.getKey());
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                if (!ItemStack.matches(a.get(i).getBaseCostA(), b.get(i).getBaseCostA())
                    || !ItemStack.matches(a.get(i).getCostB(), b.get(i).getCostB())
                    || !ItemStack.matches(a.get(i).getResult(), b.get(i).getResult())) return false;
            }
        }
        return true;
    }
}
