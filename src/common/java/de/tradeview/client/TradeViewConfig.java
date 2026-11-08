package de.tradeview.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client preferences shared by the trade screen and the optional Mod Menu screen. */
public final class TradeViewConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeView");
    private static Path file;
    private static boolean enabled = true;
    private static boolean previewVisible = true;
    private static boolean previewInTradeList = false;
    private static boolean previewInTradeListDefault = false;
    private static boolean updateCheckEnabled = true;
    private static boolean rerollEnabled = true;
    private static String autoTradeKey = "key.keyboard.t";
    private static String autoTradeModifier = "key.keyboard.left.shift";

    private TradeViewConfig() {}

    public static synchronized void initialize(Path configDirectory) {
        initialize(configDirectory, previewInTradeListDefault);
    }

    /** Each loader chooses where the preview appears until the player saves a position. */
    public static synchronized void initialize(Path configDirectory, boolean inlineByDefault) {
        file = configDirectory.resolve("tradeview.properties");
        previewInTradeListDefault = inlineByDefault;
        enabled = true;
        previewVisible = true;
        previewInTradeList = inlineByDefault;
        updateCheckEnabled = true;
        rerollEnabled = true;
        autoTradeKey = "key.keyboard.t";
        autoTradeModifier = "key.keyboard.left.shift";
        load();
    }

    public static synchronized void load() {
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            previewVisible = Boolean.parseBoolean(properties.getProperty("previewVisible", "true"));
            previewInTradeList = Boolean.parseBoolean(properties.getProperty("previewInTradeList",
                Boolean.toString(previewInTradeListDefault)));
            updateCheckEnabled = Boolean.parseBoolean(properties.getProperty("updateCheckEnabled", "true"));
            rerollEnabled = Boolean.parseBoolean(properties.getProperty("rerollEnabled", "true"));
            autoTradeKey = properties.getProperty("autoTradeKey", "key.keyboard.t");
            autoTradeModifier = properties.getProperty("autoTradeModifier", "key.keyboard.left.shift");
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("Could not load TradeView settings from {}", file, e);
        }
    }

    public static synchronized boolean isEnabled() { return enabled; }
    public static synchronized boolean isUpdateCheckEnabled() { return updateCheckEnabled; }

    public static synchronized void setUpdateCheckEnabled(boolean value) {
        updateCheckEnabled = value;
        save();
    }
    public static synchronized boolean isRerollEnabled() { return rerollEnabled; }

    public static synchronized void setRerollEnabled(boolean value) {
        rerollEnabled = value;
        save();
    }
    public static synchronized boolean isPreviewVisible() { return previewVisible; }
    public static synchronized boolean isPreviewInTradeList() { return previewInTradeList; }
    public static synchronized String getAutoTradeKey() { return autoTradeKey; }
    public static synchronized String getAutoTradeModifier() { return autoTradeModifier; }

    public static synchronized void setAutoTradeHotkey(String key, String modifier) {
        autoTradeKey = key;
        autoTradeModifier = modifier;
        save();
    }

    public static synchronized void setEnabled(boolean value) {
        enabled = value;
        save();
        TradeViewClient.sendSettings();
    }

    public static synchronized void setPreviewVisible(boolean value) {
        previewVisible = value;
        save();
    }

    public static synchronized void setPreviewInTradeList(boolean value) {
        previewInTradeList = value;
        save();
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("previewVisible", Boolean.toString(previewVisible));
        properties.setProperty("previewInTradeList", Boolean.toString(previewInTradeList));
        properties.setProperty("updateCheckEnabled", Boolean.toString(updateCheckEnabled));
        properties.setProperty("rerollEnabled", Boolean.toString(rerollEnabled));
        properties.setProperty("autoTradeKey", autoTradeKey);
        properties.setProperty("autoTradeModifier", autoTradeModifier);
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "tradeview-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "TradeView client settings");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Could not save TradeView settings to {}", file, e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { LOGGER.warn("Could not remove temporary TradeView settings file {}", temporary, e); }
            }
        }
    }
}
