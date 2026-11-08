package de.tradeview.mixin;

import de.tradeview.TradeFindRequest;
import de.tradeview.TradeRerollRequest;
import de.tradeview.client.TradeRerollHotkey;
import de.tradeview.client.TradeRerollScreen;
import de.tradeview.client.TradeViewClientNetwork;
import de.tradeview.client.TradeViewConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class TradeRerollButtonMixin extends AbstractContainerScreen<MerchantMenu> implements TradeRerollScreen {
    @Shadow private int shopItem;
    @Shadow private int scrollOff;
    @Unique private Button tradeview$reroll;

    protected TradeRerollButtonMixin(MerchantMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void tradeview$addReroll(CallbackInfo ci) {
        Component tooltip = Component.translatable("tradeview.reroll.tooltip",
            TradeRerollHotkey.KEY.getTranslatedKeyMessage());
        // The symbol is drawn separately: vanilla's 2px label margin would clip it in such a small button.
        tradeview$reroll = addRenderableWidget(Button.builder(Component.empty(), button -> tradeview$reroll())
            .bounds(leftPos + 80, topPos + 3, 13, 13)
            .tooltip(Tooltip.create(tooltip))
            .createNarration(narration -> tooltip.copy()).build());
        tradeview$reroll.visible = tradeview$canReroll();
    }

    @Override public boolean tradeview$reroll() {
        if (!tradeview$canReroll()) return false;
        // The rerolled list may be shorter than the current selection.
        shopItem = 0;
        scrollOff = 0;
        TradeViewClientNetwork.send(new TradeRerollRequest(menu.containerId));
        return true;
    }

    @Override public void tradeview$find(Identifier enchantment, int level) {
        shopItem = 0;
        scrollOff = 0;
        TradeViewClientNetwork.send(new TradeFindRequest(menu.containerId, enchantment, level));
    }

    // The client only mirrors the server's rule to hide the button; the server re-checks every request.
    @Unique private boolean tradeview$canReroll() {
        var offers = menu.getOffers();
        return TradeViewConfig.isEnabled() && TradeViewConfig.isRerollEnabled()
            && TradeViewClientNetwork.canSend(TradeRerollRequest.TYPE)
            && menu.showProgressBar() && menu.getTraderLevel() <= 1 && menu.getTraderXp() == 0
            && !offers.isEmpty() && offers.stream().allMatch(offer -> offer.getUses() == 0);
    }

    @Inject(method = "renderBg", at = @At("HEAD"))
    private void tradeview$updateReroll(GuiGraphics graphics, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        // A reroll that arrives later (e.g. at the end of a find search) may leave fewer offers than selected.
        if (shopItem >= menu.getOffers().size()) shopItem = 0;
        if (tradeview$reroll == null) return;
        tradeview$reroll.visible = tradeview$canReroll();
        tradeview$reroll.setPosition(leftPos + 80, topPos + 3);
    }

    @Inject(method = "renderContents", at = @At("TAIL"))
    private void tradeview$drawRerollSymbol(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (tradeview$reroll == null || !tradeview$reroll.visible) return;
        String symbol = "↻";
        graphics.drawString(font, symbol, tradeview$reroll.getX() + (tradeview$reroll.getWidth() - font.width(symbol) + 1) / 2,
            tradeview$reroll.getY() + 1,tradeview$reroll.isHoveredOrFocused() ? 0xFFFFFFA0 : 0xFFFFFFFF);
    }
}
