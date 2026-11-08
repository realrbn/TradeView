package de.tradeview.mixin;

import de.tradeview.UnlimitedTradesServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractVillager.class)
public abstract class UnlimitedMerchantMixin {
    @Inject(method = "getOffers", at = @At("RETURN"))
    private void tradeview$applyLimits(CallbackInfoReturnable<MerchantOffers> cir) {
        if (!((Entity) (Object) this).level().isClientSide()) UnlimitedTradesServer.apply(cir.getReturnValue());
    }
}
