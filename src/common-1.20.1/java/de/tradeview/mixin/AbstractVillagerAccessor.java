package de.tradeview.mixin;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {
    // The public setter stops trading and resets prices. Preview isolation must do neither.
    @Accessor("tradingPlayer") void tradeview$setTradingPlayerDirect(Player player);
}
