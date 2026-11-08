package de.tradeview.testmixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Villager.class)
public interface TestVillagerAccessor {
    @Invoker("increaseMerchantCareer") void tradeview$levelUp();
    @Invoker("updateSpecialPrices") void tradeview$applyPlayerTrades(Player player);
}
