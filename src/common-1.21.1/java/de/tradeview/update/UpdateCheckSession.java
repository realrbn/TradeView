package de.tradeview.update;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

/** One instance lives for the entire client process; disconnects never reset it. */
public final class UpdateCheckSession {
    private boolean firstSingleplayerJoined;

    public void onJoin(boolean singleplayer, boolean enabled, Supplier<CompletableFuture<Optional<URI>>> request,
                       Consumer<Runnable> dispatch, BooleanSupplier stillEligible, Consumer<URI> notify) {
        if (!singleplayer || firstSingleplayerJoined) return;
        firstSingleplayerJoined = true;
        if (!enabled) return;
        try {
            request.get().whenComplete((update, error) -> {
                if (error != null) {
                    LoggerFactory.getLogger("TradeView").debug("Update check unavailable", error);
                } else if (update != null && update.isPresent()) {
                    dispatch.accept(() -> {
                        if (stillEligible.getAsBoolean()) notify.accept(update.get());
                    });
                }
            });
        } catch (RuntimeException e) {
            LoggerFactory.getLogger("TradeView").debug("Could not start update check", e);
        }
    }
}
