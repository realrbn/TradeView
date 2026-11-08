package de.tradeview.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.tradeview.PreviewPayload;
import de.tradeview.client.PreviewScreen;
import de.tradeview.client.PreviewRow;
import de.tradeview.client.TradeViewConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> implements PreviewScreen {
    @Unique private static final ResourceLocation tradeview$widgets =
        new ResourceLocation("textures/gui/widgets.png");
    @Unique private static final ResourceLocation tradeview$villager =
        new ResourceLocation("textures/gui/container/villager2.png");
    @Shadow private int scrollOff;
    @Unique private final List<PreviewRow> tradeview$rows = new ArrayList<>();
    @Unique private final List<PreviewRow> tradeview$inlineOffers = new ArrayList<>();
    @Unique private int tradeview$scroll;
    @Unique private boolean tradeview$inlinePage;
    @Unique private Button tradeview$button;
    @Unique private Consumer<GuiGraphics> tradeview$tooltip;

    protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override public void tradeview$accept(PreviewPayload payload) {
        tradeview$rows.clear();
        tradeview$inlineOffers.clear();
        payload.levels().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            tradeview$rows.add(new PreviewRow(entry.getKey(), null));
            entry.getValue().forEach(offer -> {
                PreviewRow row = new PreviewRow(entry.getKey(), offer);
                tradeview$rows.add(row);
                tradeview$inlineOffers.add(row);
            });
        });
        tradeview$scroll = 0;
        tradeview$inlinePage = false;
        tradeview$layout();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tradeview$init(CallbackInfo ci) {
        tradeview$inlinePage = false;
        tradeview$button = addRenderableWidget(Button.builder(tradeview$toggleLabel(), b -> {
            TradeViewConfig.setPreviewVisible(!TradeViewConfig.isPreviewVisible());
            tradeview$layout();
        }).bounds(leftPos + imageWidth - 80, Math.max(0, topPos - 20), 80, 18).build());
        tradeview$button.visible = TradeViewConfig.isEnabled() && !tradeview$rows.isEmpty();
        tradeview$layout();
    }

    @Unique private static Component tradeview$toggleLabel() {
        return Component.translatable("tradeview.toggle",
            Component.translatable(TradeViewConfig.isPreviewVisible() ? "tradeview.toggle.on" : "tradeview.toggle.off"));
    }

    @Unique private boolean tradeview$inline() { return TradeViewConfig.isPreviewInTradeList(); }
    @Unique private int tradeview$width() {
        return tradeview$inline() ? 89 : Math.max(0, Math.min(140, width - imageWidth - 12));
    }
    @Unique private void tradeview$layout() {
        // The preview page may only stay open while the inline preview can actually be shown. Otherwise
        // (e.g. the preview was switched off while scrolled onto it) the vanilla trade buttons would stay
        // hidden and the trade list would be drawn without its row backgrounds.
        if (tradeview$inlinePage && !(tradeview$inline() && TradeViewConfig.isEnabled()
            && TradeViewConfig.isPreviewVisible() && !tradeview$rows.isEmpty() && menu.getTraderLevel() < 5))
            tradeview$closeInlinePage();
        if (tradeview$button != null) tradeview$button.setMessage(tradeview$toggleLabel());
        int centered = (width - imageWidth) / 2;
        int target = tradeview$shown() && !tradeview$inline()
            ? Math.max(4, Math.min(centered, width - imageWidth - tradeview$width() - 8)) : centered;
        int shift = target - leftPos;
        leftPos = target;
        if (shift != 0) {
            for (var child : children()) {
                if (child instanceof AbstractWidget widget) widget.setX(widget.getX() + shift);
            }
        }
    }

    @Inject(method = "renderBg", at = @At("HEAD"))
    private void tradeview$position(GuiGraphics graphics, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        tradeview$layout();
    }

    // Vanilla computes these origins from the screen width instead of using leftPos.
    // Keep its background, offer icons and scrollbar hitbox aligned with the shifted slots/widgets.
    @ModifyExpressionValue(method = {"renderBg", "render", "mouseClicked"},
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/MerchantScreen;width:I"))
    private int tradeview$merchantWidth(int original) { return leftPos * 2 + imageWidth; }

    @Unique private int tradeview$x() {
        return tradeview$inline() ? leftPos + 5 : leftPos + imageWidth + 4;
    }
    @Unique private int tradeview$y() {
        return tradeview$inline()
            ? topPos + 18 + (tradeview$inlinePage ? 0 : 20 * Math.min(7, menu.getOffers().size()))
            : topPos;
    }
    @Unique private int tradeview$height() {
        return tradeview$inline() ? Math.max(0, topPos + 158 - tradeview$y())
            : Math.min(imageHeight, height - tradeview$y() - 4);
    }
    @Unique private int tradeview$rowHeight(PreviewRow row) {
        return row.offer() == null || tradeview$width() >= 84 ? 20 : row.offer().getCostB().isEmpty() ? 44 : 62;
    }
    @Unique private int tradeview$count() {
        if (tradeview$inline()) {
            int start = Mth.clamp(tradeview$scroll, 0, tradeview$maxScroll());
            return Math.max(0, Math.min(tradeview$inlineOffers.size() - start, tradeview$height() / 20));
        }
        int used = 0, count = 0;
        for (int i = tradeview$scroll; i < tradeview$rows.size(); i++) {
            used += tradeview$rowHeight(tradeview$rows.get(i));
            if (used > tradeview$height() - 38) break;
            count++;
        }
        return count;
    }
    @Unique private int tradeview$maxScroll() {
        if (tradeview$inline())
            return Math.max(0, tradeview$inlineOffers.size() - tradeview$height() / 20);
        int used = 0, start = tradeview$rows.size();
        while (start > 0 && used + tradeview$rowHeight(tradeview$rows.get(start - 1)) <= tradeview$height() - 38)
            used += tradeview$rowHeight(tradeview$rows.get(--start));
        return Math.max(0, start);
    }
    @Unique private boolean tradeview$shown() {
        return TradeViewConfig.isEnabled() && TradeViewConfig.isPreviewVisible()
            && !tradeview$rows.isEmpty() && menu.getTraderLevel() < 5 && tradeview$width() >= 24
            && (!tradeview$inline() || tradeview$inlinePage || menu.getOffers().size() <= 4);
    }
    @Unique private boolean tradeview$listInside(double x, double y) {
        return x >= leftPos + 5 && x < leftPos + 94
            && y >= topPos + 18 && y < topPos + 158;
    }
    @Unique private boolean tradeview$inside(double x, double y) {
        return tradeview$shown() && x >= tradeview$x() && x < tradeview$x() + tradeview$width()
            && y >= tradeview$y() && y < tradeview$y() + tradeview$height();
    }

    // Vanilla's TradeOfferButton is a package-private inner class whose name is obfuscated in production.
    @Unique private static boolean tradeview$isOfferButton(Object child) {
        return child instanceof Button && child.getClass().getEnclosingClass() == MerchantScreen.class;
    }

    // Hidden widgets skip vanilla's per-frame hover update, so a TradeOfferButton hovered before switching
    // to the preview page stays "hovered". Vanilla's tooltip loop would then show that stale trade's item,
    // and since the first tooltip of a frame wins, it would hide the preview item tooltip.
    @Unique private void tradeview$hideVanillaOffers() {
        if (!tradeview$inlinePage || !tradeview$inline()) return;
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && tradeview$isOfferButton(child)) {
                widget.visible = false;
                ((AbstractWidgetAccessor) widget).tradeview$setHovered(false);
            }
        }
    }

    // Leaves the preview page and restores the vanilla trade buttons right away (vanilla would only
    // re-show them at the end of the next frame, after they were already skipped while rendering).
    @Unique private void tradeview$closeInlinePage() {
        tradeview$inlinePage = false;
        int index = 0, offers = menu.getOffers().size();
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && tradeview$isOfferButton(child))
                widget.visible = index++ < offers;
        }
    }

    // Runs before vanilla renders the buttons and evaluates their tooltips, covering the first preview frame too.
    @Inject(method = "render", at = @At("HEAD"))
    private void tradeview$hideBeforeVanilla(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        tradeview$hideVanillaOffers();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tradeview$draw(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (tradeview$button != null) tradeview$button.visible = TradeViewConfig.isEnabled()
            && !tradeview$rows.isEmpty() && menu.getTraderLevel() < 5;
        // Vanilla re-shows the buttons at the end of extractContents; keep them hidden (and unclickable) until the next frame.
        tradeview$hideVanillaOffers();
        if (!tradeview$shown()) return;
        // Before 1.21.6 there are no GUI strata: depth keeps the preview above vanilla's offer items (z 150-200).
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        tradeview$tooltip = null;
        try {
            if (tradeview$inside(mouseX, mouseY)) hoveredSlot = null;
            if (tradeview$inline()) tradeview$drawInline(graphics, mouseX, mouseY);
            else tradeview$drawSide(graphics, mouseX, mouseY);
            if (tradeview$tooltip != null) tradeview$tooltip.accept(graphics);
        } finally {
            tradeview$tooltip = null;
            graphics.pose().popPose();
        }
    }

    // Tooltips are drawn after the whole preview, so later rows cannot cover them. The first one wins, as in vanilla.
    @Unique private void tradeview$setTooltip(Consumer<GuiGraphics> tooltip) {
        if (tradeview$tooltip == null) tradeview$tooltip = tooltip;
    }

    @Unique private void tradeview$drawSide(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = tradeview$x(), y = tradeview$y(), h = tradeview$height(), w = tradeview$width();
        graphics.fill(x, y, x + w, y + h, 0xFF9B8A64);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF20252B);
        tradeview$text(graphics, w < 50 ? Component.literal("?") : Component.translatable("tradeview.title"),
            x + 4, y + 6, w - 8, 0xFFFFDA86);
        tradeview$scroll = Mth.clamp(tradeview$scroll, 0, tradeview$maxScroll());
        int count = tradeview$count();
        int rowY = y + 21;
        for (int i = 0; i < count && tradeview$scroll + i < tradeview$rows.size(); i++) {
            PreviewRow row = tradeview$rows.get(tradeview$scroll + i);
            if (row.offer() == null) {
                Component level = Component.translatable("merchant.level." + row.level());
                tradeview$text(graphics, w < 84 ? Component.literal(Integer.toString(row.level())) : level,
                    x + 4, rowY + 4, w - 8, 0xFF99D1FF);
                if (mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + 20)
                    tradeview$setTooltip(g -> g.renderTooltip(font, font.split(level, 180), mouseX, mouseY));
            } else {
                graphics.fill(x + 2, rowY, x + w - 2, rowY + tradeview$rowHeight(row) - 1, 0xFF323940);
                if (w >= 84) {
                    int first = x + 6, second = x + (w - 16) / 2, result = x + w - 22;
                    tradeview$item(graphics, row.offer().getBaseCostA(), first, rowY + 1, mouseX, mouseY);
                    if (!row.offer().getCostB().isEmpty()) graphics.drawString(font, "+", (first + 16 + second) / 2 - 2, rowY + 5, 0xFFAAAAAA);
                    tradeview$item(graphics, row.offer().getCostB(), second, rowY + 1, mouseX, mouseY);
                    graphics.drawString(font, "→", (second + 16 + result) / 2 - 2, rowY + 5, 0xFFFFDA86);
                    tradeview$item(graphics, row.offer().getResult(), result, rowY + 1, mouseX, mouseY);
                } else {
                    int itemX = x + (w - 16) / 2;
                    int extra = row.offer().getCostB().isEmpty() ? 0 : 18;
                    tradeview$item(graphics, row.offer().getBaseCostA(), itemX, rowY + 1, mouseX, mouseY);
                    if (extra != 0) {
                        graphics.drawString(font, "+", x + 2, rowY + 23, 0xFFAAAAAA);
                        tradeview$item(graphics, row.offer().getCostB(), itemX, rowY + 19, mouseX, mouseY);
                    }
                    graphics.drawString(font, "↓", x + w / 2 - 2, rowY + 18 + extra, 0xFFFFDA86);
                    tradeview$item(graphics, row.offer().getResult(), itemX, rowY + 27 + extra, mouseX, mouseY);
                }
            }
            rowY += tradeview$rowHeight(row);
        }
        String page = (tradeview$scroll + 1) + "–" + (tradeview$scroll + count) + "/" + tradeview$rows.size();
        tradeview$text(graphics, Component.literal(w < 84 ? (tradeview$scroll + 1) + " ↕" : page), x + 3, y + h - 12, w - 6, 0xFFB6BEC9);
        if (mouseX >= x && mouseX < x + w && mouseY >= y + h - 16 && mouseY < y + h)
            tradeview$setTooltip(g -> g.renderTooltip(font, font.split(Component.literal(page + " ↕"), 180), mouseX, mouseY));
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20)
            tradeview$setTooltip(g -> g.renderTooltip(font, font.split(Component.translatable("tradeview.title").append("\n")
                .append(Component.translatable("tradeview.hint")), Math.min(220, width - 20)), mouseX, mouseY));
    }

    @Unique private void tradeview$drawInline(GuiGraphics graphics, int mouseX, int mouseY) {
        tradeview$scroll = Mth.clamp(tradeview$scroll, 0, tradeview$maxScroll());
        int x = tradeview$x(), y = tradeview$y(), count = tradeview$count();
        if (tradeview$inlinePage) {
            // The preview page replaces the seven visible vanilla rows, including their background.
            graphics.fill(x, y, x + 89, y + tradeview$height(), 0xFF8B8B8B);
        }
        for (int i = 0; i < count; i++) {
            PreviewRow row = tradeview$inlineOffers.get(tradeview$scroll + i);
            MerchantOffer offer = row.offer();
            int rowY = y + i * 20;
            // The disabled button and trade arrow, as drawn by vanilla before GUI sprites existed (1.20.2).
            graphics.blitNineSliced(tradeview$widgets, x, rowY, 88, 20, 20, 4, 200, 20, 0, 46);
            tradeview$inlineItem(graphics, offer.getBaseCostA(), x + 5, rowY + 1, mouseX, mouseY);
            tradeview$inlineItem(graphics, offer.getCostB(), x + 35, rowY + 1, mouseX, mouseY);
            graphics.blit(tradeview$villager, x + 55, rowY + 4, 0, 15.0F, 171.0F, 10, 9, 512, 256);
            tradeview$inlineItem(graphics, offer.getResult(), x + 68, rowY + 1, mouseX, mouseY);
            // Above the row's items and their counts (z +150 and +200).
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 210);
            graphics.fill(x + 1, rowY + 1, x + 87, rowY + 19, 0xA0909090);
            graphics.pose().popPose();
            if (mouseX >= x && mouseX < x + 88 && mouseY >= rowY && mouseY < rowY + 20
                && !(mouseX < x + 22 || mouseX >= x + 34 && mouseX < x + 53 || mouseX >= x + 67)) {
                tradeview$setTooltip(g -> g.renderTooltip(font,
                    Component.translatable("tradeview.locked", Component.translatable("merchant.level." + row.level())),
                    mouseX, mouseY));
            }
        }
    }

    @Unique private void tradeview$inlineItem(GuiGraphics graphics, ItemStack stack,
                                               int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;
        graphics.renderFakeItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18)
            tradeview$setTooltip(g -> g.renderTooltip(font, stack, mouseX, mouseY));
    }

    @Unique private void tradeview$text(GuiGraphics graphics, Component text, int x, int y, int available, int color) {
        String label = text.getString();
        if (font.width(label) > available) label = font.plainSubstrByWidth(label, Math.max(0, available - font.width("…"))) + "…";
        graphics.drawString(font, label, x, y, color);
    }

    @Unique private void tradeview$item(GuiGraphics graphics, ItemStack stack, int x, int y, int mx, int my) {
        if (stack.isEmpty()) return;
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        if (mx >= x && mx < x + 18 && my >= y && my < y + 18)
            tradeview$setTooltip(g -> g.renderTooltip(font, stack, mx, my));
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void tradeview$scroll(double x, double y, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (tradeview$inline() && tradeview$listInside(x, y) && TradeViewConfig.isEnabled()
            && TradeViewConfig.isPreviewVisible() && !tradeview$rows.isEmpty()
            && menu.getTraderLevel() < 5 && menu.getOffers().size() >= 5) {
            if (!tradeview$inlinePage) {
                if (vertical < 0 && scrollOff >= Math.max(0, menu.getOffers().size() - 7)) {
                    tradeview$inlinePage = true;
                    tradeview$scroll = 0;
                    cir.setReturnValue(true);
                }
                return;
            }
            if (vertical > 0 && tradeview$scroll == 0) {
                tradeview$closeInlinePage();
                cir.setReturnValue(true);
                return;
            }
        }
        if (!tradeview$inside(x, y)) return;
        tradeview$scroll = Mth.clamp(tradeview$scroll - (int) Math.signum(vertical), 0,
            tradeview$maxScroll());
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tradeview$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        // Preview items are never merchant slots.
        if (tradeview$inside(x, y)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void tradeview$drag(double x, double y, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (tradeview$inside(x, y)) cir.setReturnValue(true);
    }

    // MerchantScreen does not override mouseReleased before 1.21.9, so this adds the override.
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (tradeview$inside(x, y)) return true;
        return super.mouseReleased(x, y, button);
    }
}
