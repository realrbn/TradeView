package de.tradeview.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TradeViewConfigScreen extends Screen {
    private final Screen parent;

    public TradeViewConfigScreen(Screen parent) {
        super(Component.translatable("tradeview.config.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int y = optionsTop();
        Button enabled = Button.builder(enabledLabel(), button -> {
            TradeViewConfig.setEnabled(!TradeViewConfig.isEnabled());
            button.setMessage(enabledLabel());
        }).bounds(width / 2 - 100, y, 200, 20).build();
        addRenderableWidget(enabled);
        addRenderableWidget(Button.builder(previewPositionLabel(), button -> {
            TradeViewConfig.setPreviewInTradeList(!TradeViewConfig.isPreviewInTradeList());
            button.setMessage(previewPositionLabel());
        }).bounds(width / 2 - 100, y + 30, 200, 20).build());
        int doneY = addExtraOptions(y + 60);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(width / 2 - 100, doneY, 200, 20).build());
    }

    protected int optionsTop() { return height / 2 - 10; }
    protected int addExtraOptions(int y) { return y; }

    private Component enabledLabel() {
        return Component.translatable("tradeview.config.enabled",
            Component.translatable(TradeViewConfig.isEnabled() ? "options.on" : "options.off"));
    }


    private Component previewPositionLabel() {
        return Component.translatable("tradeview.config.previewPosition",
            Component.translatable(TradeViewConfig.isPreviewInTradeList()
                ? "tradeview.config.previewPosition.inline" : "tradeview.config.previewPosition.side"));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawString(font, title, (width - font.width(title)) / 2, optionsTop() - 35, 0xFFFFFFFF);
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
    }
}
