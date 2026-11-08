package de.tradeview.testmixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An independent mod-style trade hook, enabled only for explicitly tagged test villagers. */
@Mixin(Villager.class)
public class TestTradeExtensionMixin {
    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void addTestTrade(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (villager.getTags().contains("tradeview_test_failure")) throw new IllegalStateException("Test trade generator failed");
        if (villager.getTags().contains("tradeview_test_extended"))
            villager.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD),
                new ItemStack(Items.DIAMOND, villager.getVillagerData().getLevel()), 7, 3, 0.0F));
    }
}
