package de.tradeview.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Reroll hotkey, configurable in Minecraft's Controls screen. Key mappings are not ticked while a screen
 * is open, so the key is matched directly in the trade screen and has no effect anywhere else.
 */
public final class TradeRerollHotkey {
    public static final String CATEGORY = "key.category.tradeview.main";
    /** Created by the loader, which also registers it together with its category. */
    public static KeyMapping KEY;

    private TradeRerollHotkey() {}

    public static KeyMapping create() {
        return KEY = new KeyMapping("key.tradeview.reroll", InputConstants.KEY_R, CATEGORY);
    }

    /** Only consumes the key when it actually rerolled, so an unusable key press stays unchanged. */
    public static boolean keyPressed(Minecraft client, Screen screen, int key, int scanCode) {
        if (KEY == null || !(screen instanceof MerchantScreen) || !(screen instanceof TradeRerollScreen reroll)
            || !KEY.matches(key, scanCode) || !reroll.tradeview$reroll()) return false;
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }
}
