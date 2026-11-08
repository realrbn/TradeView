package de.tradeview.client;

import de.tradeview.ClientSettingsPayload;
import de.tradeview.PreviewPayload;
import de.tradeview.ReliableRemoverCompat;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

public final class TradeViewClient {
    private TradeViewClient() {}

    public static void initialize(Path configDirectory, TradeViewClientNetwork.Transport network,
                                  boolean previewInTradeListByDefault) {
        TradeViewClientNetwork.initialize(network);
        TradeViewConfig.initialize(configDirectory, previewInTradeListByDefault);
    }

    public static void receive(PreviewPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.gui.screen() instanceof PreviewScreen screen
            && client.player.containerMenu.containerId == payload.containerId()) {
            // Also covers Reliable Remover installed only on this client, where vanilla trades are filtered client-side.
            screen.tradeview$accept(new PreviewPayload(payload.containerId(),
                ReliableRemoverCompat.filter(payload.levels())));
        }
    }

    public static void sendSettings() {
        TradeViewClientNetwork.send(new ClientSettingsPayload(TradeViewConfig.isEnabled()));
    }
}
