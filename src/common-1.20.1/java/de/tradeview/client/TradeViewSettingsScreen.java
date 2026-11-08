package de.tradeview.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TradeViewSettingsScreen extends TradeViewConfigScreen {
    private Button hotkeyButton;
    private Button unlimitedButton;
    private boolean capturing;
    private final Set<Integer> heldKeys = new HashSet<>();
    private int firstKey = -1;
    private int secondKey = -1;

    public TradeViewSettingsScreen(Screen parent) { super(parent); }

    @Override protected int optionsTop() { return Math.max(40, height / 2 - 85); }

    @Override protected int addExtraOptions(int y) {
        hotkeyButton = addRenderableWidget(Button.builder(hotkeyLabel(), button -> {
            capturing = true;
            heldKeys.clear();
            firstKey = secondKey = -1;
            updateLabel();
        }).bounds(width / 2 - 100, y, 200, 20).build());
        hotkeyButton.setTooltip(Tooltip.create(Component.translatable("tradeview.config.autoTrade.hint")));
        unlimitedButton = addRenderableWidget(Button.builder(unlimitedLabel(), button ->
            UnlimitedTradesClient.setEnabled(!UnlimitedTradesClient.isEnabled()))
            .bounds(width / 2 - 100, y + 30, 200, 20).build());
        UnlimitedTradesClient.refresh();
        updateUnlimitedButton();
        Button updateCheck = addRenderableWidget(Button.builder(updateCheckLabel(), button -> {
            TradeViewConfig.setUpdateCheckEnabled(!TradeViewConfig.isUpdateCheckEnabled());
            button.setMessage(updateCheckLabel());
        }).bounds(width / 2 - 100, y + 60, 200, 20).build());
        updateCheck.setTooltip(Tooltip.create(Component.translatable("tradeview.config.updateCheck.hint")));
        Button reroll = addRenderableWidget(Button.builder(rerollLabel(), button -> {
            TradeViewConfig.setRerollEnabled(!TradeViewConfig.isRerollEnabled());
            button.setMessage(rerollLabel());
        }).bounds(width / 2 - 100, y + 90, 200, 20).build());
        reroll.setTooltip(Tooltip.create(Component.translatable("tradeview.config.reroll.hint",
            TradeRerollHotkey.KEY.getTranslatedKeyMessage())));
        return y + 120;
    }

    private Component rerollLabel() {
        return Component.translatable("tradeview.config.reroll", Component.translatable(
            TradeViewConfig.isRerollEnabled() ? "options.on" : "options.off"));
    }

    private Component updateCheckLabel() {
        return Component.translatable("tradeview.config.updateCheck", Component.translatable(
            TradeViewConfig.isUpdateCheckEnabled() ? "options.on" : "options.off"));
    }

    private Component unlimitedLabel() {
        return Component.translatable("tradeview.config.unlimitedTrades", Component.translatable(
            !UnlimitedTradesClient.isSupported() ? "tradeview.config.unlimitedTrades.unavailable"
                : UnlimitedTradesClient.isEnabled() ? "options.on" : "options.off"));
    }

    private void updateUnlimitedButton() {
        unlimitedButton.setMessage(unlimitedLabel());
        unlimitedButton.active = UnlimitedTradesClient.canChange();
        unlimitedButton.setTooltip(Tooltip.create(Component.translatable(!UnlimitedTradesClient.isSupported()
            ? "tradeview.config.unlimitedTrades.requiresServer" : !UnlimitedTradesClient.canChange()
                ? "tradeview.config.unlimitedTrades.requiresOperator" : "tradeview.config.unlimitedTrades.hint")));
    }

    @Override public void tick() {
        super.tick();
        updateUnlimitedButton();
    }

    private Component hotkeyLabel() {
        Component binding = AutoTradeHotkey.bindingLabel();
        if (capturing) binding = firstKey == -1 ? Component.translatable("tradeview.config.autoTrade.pressKey")
            : AutoTradeHotkey.label(firstKey).copy().append(secondKey == -1 ? " …" : " + ")
                .append(secondKey == -1 ? Component.empty() : AutoTradeHotkey.label(secondKey));
        return Component.translatable("tradeview.config.autoTrade.key", binding);
    }

    private void updateLabel() {
        hotkeyButton.setMessage(hotkeyLabel());
    }

    private void finishCapture(boolean save) {
        if (save) {
            int primary = secondKey == -1 ? firstKey : secondKey;
            int modifier = secondKey == -1 ? -1 : firstKey;
            if (secondKey != -1 && AutoTradeHotkey.isModifier(secondKey) && !AutoTradeHotkey.isModifier(firstKey)) {
                primary = firstKey;
                modifier = secondKey;
            }
            TradeViewConfig.setAutoTradeHotkey(AutoTradeHotkey.name(primary), AutoTradeHotkey.name(modifier));
        }
        capturing = false;
        heldKeys.clear();
        updateLabel();
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!capturing) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == InputConstants.KEY_ESCAPE) {
            firstKey = secondKey = -1;
            finishCapture(true);
            return true;
        }
        int key = AutoTradeHotkey.normalize(keyCode);
        if (key == InputConstants.UNKNOWN.getValue() || heldKeys.contains(key)) return true;
        // Commit on release, so the first key can be held while adding a second key.
        if (firstKey == -1) firstKey = key;
        else if (secondKey == -1 && key != firstKey) secondKey = key;
        else return true;
        heldKeys.add(key);
        updateLabel();
        return true;
    }

    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!capturing) return super.keyReleased(keyCode, scanCode, modifiers);
        heldKeys.remove(AutoTradeHotkey.normalize(keyCode));
        if (firstKey != -1 && heldKeys.isEmpty()) finishCapture(true);
        return true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (capturing) finishCapture(false);
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
