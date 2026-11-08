package com.evandev.reliable_remover.api;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/** Test stand-in with the same public signature as Reliable Remover's API (3.5.x). */
public final class ReliableRemoverAPI {
    public static volatile Predicate<ItemStack> tradeBlocked = stack -> false;

    private ReliableRemoverAPI() {}

    public static boolean isTradeBlocked(ItemStack stack) { return tradeBlocked.test(stack); }
}
