package de.tradeview.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Keyboard-only binding, with an optional second key. Modifier keys accept either side. */
public final class AutoTradeHotkey {
    private AutoTradeHotkey() {}

    public static int key() { return readKey(TradeViewConfig.getAutoTradeKey(), InputConstants.KEY_T); }
    public static int modifier() { return readKey(TradeViewConfig.getAutoTradeModifier(), InputConstants.KEY_LSHIFT); }

    private static int readKey(String name, int fallback) {
        if (name.equals("key.keyboard.unknown")) return -1;
        try {
            var key = InputConstants.getKey(name);
            return key.getName().startsWith("key.keyboard.") ? key.getValue() : fallback;
        } catch (IllegalArgumentException e) { return fallback; }
    }

    public static Component label(int key) {
        return key == -1 ? Component.translatable("tradeview.config.autoTrade.none")
            : InputConstants.getKey(new KeyEvent(key, 0, 0)).getDisplayName();
    }

    public static String name(int key) {
        return key == -1 ? "key.keyboard.unknown" : InputConstants.getKey(new KeyEvent(key, 0, 0)).getName();
    }

    public static Component bindingLabel() {
        if (key() == -1 || modifier() == -1) return label(key());
        return label(modifier()).copy().append(" + ").append(label(key()));
    }

    public static boolean isModifier(int key) {
        int normalized = normalize(key);
        return normalized == InputConstants.KEY_LSHIFT || normalized == InputConstants.KEY_LCONTROL
            || normalized == InputConstants.KEY_LALT;
    }

    public static int normalize(int key) {
        return switch (key) {
            case InputConstants.KEY_RSHIFT -> InputConstants.KEY_LSHIFT;
            case InputConstants.KEY_RCONTROL -> InputConstants.KEY_LCONTROL;
            case InputConstants.KEY_RALT -> InputConstants.KEY_LALT;
            default -> key;
        };
    }

    public static boolean matches(KeyEvent event, Set<Integer> pressedKeys) {
        int primary = normalize(key()), second = normalize(modifier()), pressed = normalize(event.key());
        if (primary == -1) return false;
        return pressed == primary && (second == -1 || isDown(second, event, pressedKeys))
            || second != -1 && pressed == second && isDown(primary, event, pressedKeys);
    }

    private static boolean isDown(int key, KeyEvent event, Set<Integer> pressedKeys) {
        int mask = switch (key) {
            case InputConstants.KEY_LSHIFT -> InputConstants.MOD_SHIFT;
            case InputConstants.KEY_LCONTROL -> InputConstants.MOD_CONTROL;
            case InputConstants.KEY_LALT -> InputConstants.MOD_ALT;
            default -> 0;
        };
        return pressedKeys.contains(key) || mask != 0 && (event.modifiers() & mask) != 0;
    }
}
