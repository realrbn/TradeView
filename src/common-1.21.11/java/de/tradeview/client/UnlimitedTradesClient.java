package de.tradeview.client;

import de.tradeview.UnlimitedTradesRequest;
import de.tradeview.UnlimitedTradesServer;
import de.tradeview.UnlimitedTradesStatus;
import net.minecraft.client.Minecraft;

public final class UnlimitedTradesClient {
    private static boolean received;
    private static boolean enabled;
    private static boolean canChange;

    private UnlimitedTradesClient() {}

    public static void reset() { received = false; enabled = false; canChange = false; }
    public static void receive(UnlimitedTradesStatus status) {
        received = true;
        enabled = status.enabled();
        canChange = status.canChange();
    }
    private static boolean offline() { return Minecraft.getInstance().getConnection() == null; }
    public static boolean isSupported() { return offline() || received; }
    public static boolean isEnabled() { return offline() ? UnlimitedTradesServer.isEnabled() : received && enabled; }
    public static boolean canChange() { return offline() || received && canChange; }

    public static void refresh() {
        if (!offline() && TradeViewClientNetwork.canSend(UnlimitedTradesRequest.TYPE))
            TradeViewClientNetwork.send(new UnlimitedTradesRequest(false, false));
    }

    public static void setEnabled(boolean value) {
        if (offline()) UnlimitedTradesServer.setEnabled(value, null);
        else if (canChange() && TradeViewClientNetwork.canSend(UnlimitedTradesRequest.TYPE))
            TradeViewClientNetwork.send(new UnlimitedTradesRequest(value, true));
    }
}
