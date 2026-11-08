package de.tradeview;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;

/** The 1.21.11 client gametest API has no packet barriers; the integrated server answers within a few ticks. */
final class TestSync {
    private static final int PACKET_TICKS = 4;
    private static final int SERVER_TIMEOUT_TICKS = 200;

    private TestSync() {}

    static void packets(ClientGameTestContext context) { context.waitTicks(PACKET_TICKS); }

    static void server(ClientGameTestContext context, TestSingleplayerContext world, Predicate<MinecraftServer> condition) {
        for (int tick = 0; tick < SERVER_TIMEOUT_TICKS; tick++) {
            if (world.getServer().computeOnServer(condition::test)) return;
            context.waitTick();
        }
        throw new AssertionError("Timed out waiting for the server state");
    }
}
