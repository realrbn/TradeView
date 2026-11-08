package de.tradeview.client;

import de.tradeview.mixin.MerchantScreenAccessor;
import de.tradeview.mixin.MerchantTradeAccessor;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.network.chat.Component;

/** Runs ordinary, predicted container clicks; the server remains authoritative for every trade. */
public final class AutoTrade {
    private final Minecraft client;
    private final MerchantScreen screen;
    private final MerchantMenu menu;
    private final MerchantScreenAccessor selection;
    private final Set<Integer> pressedKeys = new HashSet<>();
    private MerchantOffer offer;
    private int selectedTrade;
    private int completed;
    private int cooldown;
    private int reportedUses;

    private AutoTrade(Minecraft client, MerchantScreen screen) {
        this.client = client;
        this.screen = screen;
        menu = screen.getMenu();
        selection = (MerchantScreenAccessor) screen;
    }

    /**
     * Created for each initialized merchant screen. The loader forwards that screen's key presses and
     * releases, mouse clicks, ticks and its removal.
     */
    public static AutoTrade attach(Minecraft client, Screen screen) {
        return screen instanceof MerchantScreen merchant ? new AutoTrade(client, merchant) : null;
    }

    /** Returns true when the key press belongs to the hotkey and must not reach the screen. */
    public boolean keyPressed(KeyEvent event) {
        boolean firstPress = pressedKeys.add(AutoTradeHotkey.normalize(event.key()));
        if (!TradeViewConfig.isEnabled() || !AutoTradeHotkey.matches(event, pressedKeys)) return false;
        if (firstPress) toggle();
        return true;
    }

    public void keyReleased(KeyEvent event) { pressedKeys.remove(AutoTradeHotkey.normalize(event.key())); }

    /** Any manual inventory interaction cancels the run before vanilla handles the click. */
    public void mouseClicked() { stop(); }

    public void removed() { stop(); }

    private void toggle() {
        if (offer != null) { stop(); return; }
        selectedTrade = selection.tradeview$selectedTrade();
        if (!ready() || selectedTrade < 0 || selectedTrade >= menu.getOffers().size()) return;
        MerchantOffer selected = menu.getOffers().get(selectedTrade);
        if (selected.isOutOfStock()) return;
        offer = selected;
        reportedUses = selected.getUses();
        completed = 0;
        cooldown = 0;
    }

    private boolean ready() {
        return TradeViewConfig.isEnabled() && client.screen == screen && client.player != null
            && client.gameMode != null && client.player.containerMenu == menu && menu.getCarried().isEmpty();
    }

    public void tick() {
        if (offer == null) return;
        if (!ready() || selection.tradeview$selectedTrade() != selectedTrade
            || selectedTrade >= menu.getOffers().size()) { stop(); return; }
        MerchantOffer current = menu.getOffers().get(selectedTrade);
        if (current != offer) {
            // Vanilla resends the offers when trading changes reputation/prices. Accept updates to
            // this offer, but stop if it was replaced or its uses were reset (e.g. Trade Cycling).
            if (!sameTrade(offer, current) || current.getUses() < reportedUses) { stop(); return; }
            reportedUses = current.getUses();
            offer = current;
        }
        if (offer.isOutOfStock()) { stop(); return; }
        if (cooldown-- > 0) return;

        var payments = ((MerchantTradeAccessor) menu).tradeview$tradeContainer();
        if (menu.getSlot(2).getItem().isEmpty() || payments.getActiveOffer() != offer) {
            // Exactly the same auto-fill and SelectTrade packet as clicking the selected vanilla row.
            selection.tradeview$fillSelectedTrade();
        }
        ItemStack result = menu.getSlot(2).getItem();
        if (result.isEmpty() || payments.getActiveOffer() != offer
            || !ItemStack.isSameItemSameComponents(result, offer.getResult())
            || result.getCount() != offer.getResult().getCount() || !hasCapacity(result)) { stop(); return; }

        // Pick up one complete result and put it in the inventory. QUICK_MOVE can perform multiple
        // trades internally and partially insert a final result, so it cannot enforce our capacity check.
        click(2);
        if (menu.getCarried().isEmpty()) { stop(); return; }
        for (int pass = 0; pass < 2 && !menu.getCarried().isEmpty(); pass++) {
            for (int i = 3; i < menu.slots.size() && !menu.getCarried().isEmpty(); i++) {
                var slot = menu.getSlot(i);
                ItemStack carried = menu.getCarried(), existing = slot.getItem();
                if (!slot.mayPlace(carried)) continue;
                if ((pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, carried)
                    && existing.getCount() < slot.getMaxStackSize(carried)) || (pass == 1 && existing.isEmpty()))
                    click(i);
            }
        }
        if (!menu.getCarried().isEmpty()) { stop(); return; }
        completed++;
        cooldown = 1; // At most ten complete trades per second; no unbounded loop or packet burst.
    }

    private boolean hasCapacity(ItemStack result) {
        int needed = result.getCount();
        for (int i = 3; i < menu.slots.size(); i++) {
            var slot = menu.getSlot(i);
            if (!slot.mayPlace(result)) continue;
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) needed -= slot.getMaxStackSize(result);
            else if (ItemStack.isSameItemSameComponents(existing, result))
                needed -= Math.max(0, slot.getMaxStackSize(result) - existing.getCount());
            if (needed <= 0) return true;
        }
        return false;
    }

    private static boolean sameTrade(MerchantOffer first, MerchantOffer second) {
        return sameStack(first.getBaseCostA(), second.getBaseCostA())
            && sameStack(first.getCostB(), second.getCostB()) && sameStack(first.getResult(), second.getResult())
            && first.getMaxUses() == second.getMaxUses() && first.getXp() == second.getXp()
            && first.getPriceMultiplier() == second.getPriceMultiplier();
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.isEmpty() && second.isEmpty()
            || first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    private void click(int slot) {
        client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, client.player);
    }

    private void stop() {
        if (offer == null) return;
        offer = null;
        if (client.player != null)
            client.player.displayClientMessage(Component.translatable("tradeview.autoTrade.finished", completed), true);
    }
}
