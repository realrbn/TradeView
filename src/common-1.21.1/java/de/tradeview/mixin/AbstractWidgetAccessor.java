package de.tradeview.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractWidget.class)
public interface AbstractWidgetAccessor {
    // Vanilla only refreshes this while the widget is visible, so hidden widgets keep a stale value.
    @Accessor("isHovered") void tradeview$setHovered(boolean hovered);
}
