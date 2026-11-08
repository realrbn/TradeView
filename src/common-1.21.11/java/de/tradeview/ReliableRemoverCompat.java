package de.tradeview;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.LoggerFactory;

/**
 * Optional Reliable Remover support without a compile-time dependency.
 * Reliable Remover hides blocked trades in MerchantMenu.getOffers, which the preview never passes through,
 * so the same public API check is applied to previewed offers before they are shown.
 */
public final class ReliableRemoverCompat {
    private static final MethodHandle TRADE_BLOCKED = find();
    private static volatile boolean failed;

    private ReliableRemoverCompat() {}

    private static MethodHandle find() {
        try {
            Class<?> api = Class.forName("com.evandev.reliable_remover.api.ReliableRemoverAPI", false,
                ReliableRemoverCompat.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(api, "isTradeBlocked",
                MethodType.methodType(boolean.class, ItemStack.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    public static boolean isLoaded() { return TRADE_BLOCKED != null; }

    /** Mirrors Reliable Remover's own merchant filter: any blocked cost or result removes the offer. */
    public static boolean isBlocked(MerchantOffer offer) {
        return blocked(offer.getResult()) || blocked(offer.getBaseCostA()) || blocked(offer.getCostB());
    }

    private static boolean blocked(ItemStack stack) {
        if (TRADE_BLOCKED == null || failed || stack.isEmpty()) return false;
        try {
            return (boolean) TRADE_BLOCKED.invokeExact(stack);
        } catch (Throwable e) {
            // Never break the preview because of an incompatible Reliable Remover version.
            failed = true;
            LoggerFactory.getLogger("TradeView").warn("Reliable Remover trade check failed; preview filtering disabled", e);
            return false;
        }
    }

    /**
     * Returns filtered copies and leaves the given (possibly reserved) offer lists untouched, so changed
     * rules after /reload apply again. Levels without any remaining offer are omitted.
     */
    public static Map<Integer, MerchantOffers> filter(Map<Integer, MerchantOffers> levels) {
        if (TRADE_BLOCKED == null || failed || levels.isEmpty()) return levels;
        Map<Integer, MerchantOffers> visible = new TreeMap<>();
        levels.forEach((level, offers) -> {
            MerchantOffers kept = new MerchantOffers();
            for (MerchantOffer offer : offers) if (!isBlocked(offer)) kept.add(offer);
            if (!kept.isEmpty()) visible.put(level, kept);
        });
        return Map.copyOf(visible);
    }
}
