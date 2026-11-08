package de.tradeview.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
            : InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName();
    }

    public static String name(int key) {
        return key == -1 ? "key.keyboard.unknown" : InputConstants.Type.KEYSYM.getOrCreate(key).getName();
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

    public static boolean matches(int keyCode, int modifiers, Set<Integer> pressedKeys) {
        int primary = normalize(key()), second = normalize(modifier()), pressed = normalize(keyCode);
        if (primary == -1) return false;
        return pressed == primary && (second == -1 || isDown(second, modifiers, pressedKeys))
            || second != -1 && pressed == second && isDown(primary, modifiers, pressedKeys);
    }

    private static boolean isDown(int key, int modifiers, Set<Integer> pressedKeys) {
        int mask = switch (key) {
            case InputConstants.KEY_LSHIFT -> GLFW.GLFW_MOD_SHIFT;
            case InputConstants.KEY_LCONTROL -> GLFW.GLFW_MOD_CONTROL;
            case InputConstants.KEY_LALT -> GLFW.GLFW_MOD_ALT;
            default -> 0;
        };
        return pressedKeys.contains(key) || mask != 0 && (modifiers & mask) != 0;
    }
}
