package de.tradeview.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.tradeview.UnlimitedOffer;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantOffer.class)
public abstract class UnlimitedMerchantOfferMixin implements UnlimitedOffer {
    @Shadow @Final private int maxUses;
    @Shadow private int uses;
    @Unique private boolean tradeview$unlimited;

    @Override public void tradeview$setUnlimited(boolean enabled) {
        tradeview$unlimited = enabled;
        if (enabled && uses == Integer.MAX_VALUE) uses = Integer.MAX_VALUE - 1;
    }
    @Override public boolean tradeview$isUnlimited() { return tradeview$unlimited; }

    @Inject(method = "<init>(Lnet/minecraft/world/item/trading/MerchantOffer;)V", at = @At("TAIL"))
    private void tradeview$copyPolicy(MerchantOffer original, CallbackInfo ci) {
        tradeview$unlimited = ((UnlimitedOffer) original).tradeview$isUnlimited();
    }

    // The network codec calls this getter. The disk codec reads the original field directly,
    // so disabling the feature (even after a save/restart) restores the original offer limit.
    @Inject(method = "getMaxUses", at = @At("HEAD"), cancellable = true)
    private void tradeview$effectiveLimit(CallbackInfoReturnable<Integer> cir) {
        if (tradeview$unlimited) cir.setReturnValue(Integer.MAX_VALUE);
    }

    @Inject(method = "isOutOfStock", at = @At("HEAD"), cancellable = true)
    private void tradeview$available(CallbackInfoReturnable<Boolean> cir) {
        if (tradeview$unlimited) cir.setReturnValue(false);
    }

    @Inject(method = "increaseUses", at = @At("HEAD"), cancellable = true)
    private void tradeview$preventOverflow(CallbackInfo ci) {
        // Also applies to client copies received with the effective network limit.
        if ((tradeview$unlimited || maxUses == Integer.MAX_VALUE) && uses >= Integer.MAX_VALUE - 1) ci.cancel();
    }

    @ModifyExpressionValue(method = "updateDemand", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/item/trading/MerchantOffer;uses:I"))
    private int tradeview$boundedDemand(int original) {
        // Unlimited sales must not overflow demand or inflate it beyond a normal sold-out cycle.
        return tradeview$unlimited ? Math.min(original, maxUses) : original;
    }
}
