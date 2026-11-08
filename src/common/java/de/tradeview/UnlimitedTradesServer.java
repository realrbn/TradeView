package de.tradeview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UnlimitedTradesServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeView");
    private static Path file;
    private static volatile boolean enabled = false;

    private UnlimitedTradesServer() {}

    public static void initialize(Path directory) {
        file = directory.resolve("tradeview-server.properties");
        enabled = false;
        if (Files.exists(file)) {
            try (var input = Files.newInputStream(file)) {
                Properties properties = new Properties();
                properties.load(input);
                enabled = Boolean.parseBoolean(properties.getProperty("unlimitedTrades", "false"));
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.error("Could not load TradeView server settings", e);
            }
        } else save();
    }

    public static boolean isEnabled() { return enabled; }

    public static void apply(MerchantOffers offers) {
        for (var offer : offers) ((UnlimitedOffer) offer).tradeview$setUnlimited(enabled);
    }

    public static boolean canChange(MinecraftServer server, ServerPlayer player) {
        NameAndId profile = new NameAndId(player.getGameProfile());
        return server.isSingleplayerOwner(profile) || server.getPlayerList().isOp(profile);
    }

    public static void request(MinecraftServer server, ServerPlayer player, boolean value) {
        if (canChange(server, player)) setEnabled(value, server);
        else sendStatus(server, player);
    }

    public static void setEnabled(boolean value, MinecraftServer server) {
        enabled = value;
        save();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof MerchantMenu menu) {
                MerchantOffers offers = menu.getOffers();
                apply(offers);
                menu.slotsChanged(menu.getSlot(0).container);
                player.sendMerchantOffers(menu.containerId, offers, menu.getTraderLevel(), menu.getTraderXp(),
                    menu.showProgressBar(), menu.canRestock());
                menu.broadcastChanges();
            }
            sendStatus(server, player);
        }
    }

    public static void sendStatus(MinecraftServer server, ServerPlayer player) {
        TradeViewNetwork.send(player, new UnlimitedTradesStatus(enabled, canChange(server, player)));
    }

    private static void save() {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "tradeview-server-", ".tmp");
            Properties properties = new Properties();
            properties.setProperty("unlimitedTrades", Boolean.toString(enabled));
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "TradeView server settings (applies to all players)");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) { LOGGER.error("Could not save TradeView server settings", e); }
        finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { LOGGER.warn("Could not remove temporary server settings", e); }
            }
        }
    }
}
