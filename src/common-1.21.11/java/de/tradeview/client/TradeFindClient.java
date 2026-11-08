package de.tradeview.client;

import de.tradeview.TradeFindRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * "/tradepreview find": remembers the wanted book until the next villager trade screen opens and then
 * hands the search to the server, which rerolls the villager authoritatively.
 */
public final class TradeFindClient {
    /** Real time, so a paused game or lag does not extend the wait. */
    static final long PENDING_TIMEOUT_NANOS = 60_000_000_000L;

    private record Target(Identifier enchantment, int level, Component name, long expiresAt) {}
    private static Target pending;

    private TradeFindClient() {}

    /** Called by the loader after a screen was initialized. */
    public static void screenInitialized(Screen screen) {
        if (pending == null || !(screen instanceof MerchantScreen) || !(screen instanceof TradeRerollScreen trade)) return;
        Target target = pending;
        pending = null;
        trade.tradeview$find(target.enchantment(), target.level());
    }

    /** Called by the loader at the end of each client tick. */
    public static void tick(Minecraft client) {
        Target target = pending;
        if (target == null || System.nanoTime() - target.expiresAt() < 0) return;
        pending = null;
        if (client.player != null)
            client.player.displayClientMessage(Component.translatable("tradeview.find.timeout", target.name()), false);
    }

    public static void disconnect() { pending = null; }

    public static boolean isPending() { return pending != null; }

    /** Test hook: lets the pending search run into its timeout without waiting a real minute. */
    public static void expirePendingForTest() {
        if (pending != null) pending = new Target(pending.enchantment(), pending.level(), pending.name(), System.nanoTime());
    }

    /** Returns the chat feedback for the command. */
    public static Component start(Holder.Reference<Enchantment> enchantment, int level) {
        if (!TradeViewConfig.isEnabled()) return Component.translatable("tradeview.find.disabled");
        if (!TradeViewClientNetwork.canSend(TradeFindRequest.TYPE)) return Component.translatable("tradeview.find.unavailable");
        int max = enchantment.value().getMaxLevel();
        if (level > max) return Component.translatable("tradeview.find.tooHigh", Enchantment.getFullname(enchantment, 1), max);
        Component name = Enchantment.getFullname(enchantment, level);
        pending = new Target(enchantment.key().identifier(), level, name, System.nanoTime() + PENDING_TIMEOUT_NANOS);
        Component message = Component.translatable("tradeview.find.pending", name);
        // Only a hint: other mods may still sell books with enchantments outside the vanilla tag.
        return enchantment.is(EnchantmentTags.TRADEABLE) ? message
            : message.copy().append("\n").append(Component.translatable("tradeview.find.notTradeable", name));
    }

    public static Component stop() {
        pending = null;
        if (TradeViewClientNetwork.canSend(TradeFindRequest.TYPE)) TradeViewClientNetwork.send(TradeFindRequest.stop());
        return Component.translatable("tradeview.find.cancelled");
    }
}
