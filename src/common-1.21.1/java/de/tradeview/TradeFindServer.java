package de.tradeview;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Rerolls an untraded villager until an enchanted book with exactly the requested enchantment level appears in its
 * current offers or in the reserved preview. Work is spread over server ticks with a time budget.
 */
public final class TradeFindServer {
    static final int MAX_REROLLS = 5000;
    /** Stops early for villagers whose trades never contain enchanted books (e.g. a non-librarian). */
    static final int MAX_REROLLS_WITHOUT_BOOKS = 200;
    /**
     * Stops early if the enchantment never appears at any level, e.g. with the Villager Trade Rebalance,
     * where each villager type only sells a few biome-specific enchantments.
     */
    static final int MAX_REROLLS_WITHOUT_ENCHANTMENT = 1000;
    private static final long BUDGET_NANOS = 10_000_000L;

    private static final class Search {
        final AbstractContainerMenu menu;
        final Villager villager;
        final Holder<Enchantment> enchantment;
        final int level;
        int rerolls;
        int withoutBooks;
        boolean sawBook;
        boolean sawEnchantment;

        Search(AbstractContainerMenu menu, Villager villager, Holder<Enchantment> enchantment, int level) {
            this.menu = menu;
            this.villager = villager;
            this.enchantment = enchantment;
            this.level = level;
        }
    }

    /** Where the book was found: the current offers (preview false) or a reserved future level. */
    record Match(int bookLevel, int merchantLevel, boolean preview) {}

    private static final Map<ServerPlayer, Search> searches = new WeakHashMap<>();

    private TradeFindServer() {}

    public static void request(MinecraftServer server, ServerPlayer player, TradeFindRequest request) {
        if (request.level() <= 0) {
            if (searches.remove(player) != null) player.sendSystemMessage(Component.translatable("tradeview.find.cancelled"));
            return;
        }
        Villager villager = TradeRerollServer.trader(player, request.containerId());
        if (villager == null) return;
        Optional<? extends Holder<Enchantment>> enchantment = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .get(ResourceKey.create(Registries.ENCHANTMENT, request.enchantment()));
        if (enchantment.isEmpty()) {
            player.sendSystemMessage(Component.translatable("tradeview.find.unknown", request.enchantment().toString()));
            return;
        }
        Holder<Enchantment> holder = enchantment.get();
        if (request.level() > holder.value().getMaxLevel()) {
            player.sendSystemMessage(Component.translatable("tradeview.find.tooHigh",
                Enchantment.getFullname(holder, 1), holder.value().getMaxLevel()));
            return;
        }
        searches.put(player, new Search(player.containerMenu, villager, holder, request.level()));
        player.sendSystemMessage(Component.translatable("tradeview.find.started", name(holder, request.level())));
    }

    public static void disconnect(ServerPlayer player) { searches.remove(player); }
    public static void stop() { searches.clear(); }
    static boolean isSearching(ServerPlayer player) { return searches.containsKey(player); }

    public static void tick(MinecraftServer server) {
        if (searches.isEmpty()) return;
        for (Iterator<Map.Entry<ServerPlayer, Search>> it = searches.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ServerPlayer player = entry.getKey();
            Search search = entry.getValue();
            if (player.hasDisconnected()) { it.remove(); continue; }
            Component result = step(player, search, System.nanoTime() + BUDGET_NANOS);
            if (result == null) {
                if (server.getTickCount() % 20 == 0) player.sendSystemMessage(Component.translatable(
                    "tradeview.find.progress", name(search.enchantment, search.level), search.rerolls), true);
                continue;
            }
            it.remove();
            if (search.rerolls > 0 && player.containerMenu == search.menu) TradeRerollServer.sync(player, search.villager);
            player.sendSystemMessage(Component.empty(), true);
            player.sendSystemMessage(result);
        }
    }

    /** Runs until the budget is used up; returns the final message once the search ends, otherwise null. */
    private static Component step(ServerPlayer player, Search search, long deadline) {
        Villager villager = search.villager;
        while (true) {
            if (player.containerMenu != search.menu || villager.isRemoved() || villager.getTradingPlayer() != player)
                return Component.translatable("tradeview.find.closed", name(search.enchantment, search.level));
            search.sawBook = false;
            Match match = locate(villager, search.enchantment, search.level, search);
            if (match != null) return found(search, match);
            search.withoutBooks = search.sawBook ? 0 : search.withoutBooks + 1;
            if (search.withoutBooks >= MAX_REROLLS_WITHOUT_BOOKS)
                return Component.translatable("tradeview.find.noBooks", search.rerolls);
            if (!search.sawEnchantment && search.rerolls >= MAX_REROLLS_WITHOUT_ENCHANTMENT)
                return Component.translatable("tradeview.find.neverOffered",
                    Enchantment.getFullname(search.enchantment, 1), search.rerolls);
            if (search.rerolls >= MAX_REROLLS)
                return Component.translatable("tradeview.find.limit", name(search.enchantment, search.level), search.rerolls);
            if (!TradeRerollServer.canReroll(villager))
                return Component.translatable(search.rerolls == 0 ? "tradeview.find.notRerollable" : "tradeview.find.cancelled");
            if (System.nanoTime() > deadline) return null;
            TradeRerollServer.reroll(villager);
            search.rerolls++;
        }
    }

    private static Component found(Search search, Match match) {
        Component book = Enchantment.getFullname(search.enchantment, match.bookLevel());
        return match.preview()
            ? Component.translatable("tradeview.find.found.preview", book, search.rerolls,
                Component.translatable("merchant.level." + match.merchantLevel()))
            : Component.translatable("tradeview.find.found.current", book, search.rerolls);
    }

    /** Checks the current offers first, then the reserved future levels, as the trade screen shows them. */
    static Match locate(Villager villager, Holder<Enchantment> enchantment, int level, Search search) {
        Match current = matchIn(villager.getOffers(), enchantment, level, villager.getVillagerData().getLevel(), false, search);
        if (current != null) return current;
        if (villager.getVillagerData().getLevel() >= 5) return null;
        for (var entry : ((PreviewVillager) villager).tradeview$preview().entrySet()) {
            Match future = matchIn(entry.getValue(), enchantment, level, entry.getKey(), true, search);
            if (future != null) return future;
        }
        return null;
    }

    private static Match matchIn(MerchantOffers offers, Holder<Enchantment> enchantment, int level,
                                int merchantLevel, boolean preview, Search search) {
        for (MerchantOffer offer : offers) {
            // Trades hidden by Reliable Remover are never shown, so they cannot count as a hit.
            if (!offer.getResult().is(Items.ENCHANTED_BOOK) || ReliableRemoverCompat.isBlocked(offer)) continue;
            if (search != null) search.sawBook = true;
            var stored = offer.getResult().get(DataComponents.STORED_ENCHANTMENTS);
            int found = stored == null ? 0 : stored.getLevel(enchantment);
            if (search != null && found > 0) search.sawEnchantment = true;
            // Only the exact requested level counts; e.g. Sharpness V does not satisfy a search for Sharpness III.
            if (found == level) return new Match(found, merchantLevel, preview);
        }
        return null;
    }

    private static Component name(Holder<Enchantment> enchantment, int level) {
        return Enchantment.getFullname(enchantment, level);
    }
}
