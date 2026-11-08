package de.tradeview.update;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UpdateCheckerGameTest {
    private static String manifest(String latest) {
        return "{\"schema\":1,\"mods\":{\"1699797\":{\"fabric\":{\"26.2\":{\"latest\":\"" + latest
            + "\",\"url\":\"https://example.org/fabric-26.2\"},\"26.3\":{\"latest\":\"1.2.0\",\"url\":\"https://example.org/fabric-26.3\"}}}}}";
    }

    private static boolean isNewer(String json, String minecraftVersion, String installedVersion) {
        return UpdateManifest.findUpdate(json, minecraftVersion, installedVersion).isPresent();
    }

    @GameTest
    public void downloadLinkComesFromMatchingEntry(GameTestHelper helper) {
        String json = manifest("1.2.0");
        for (String version : new String[] {"26.2", "26.3"})
            helper.assertTrue(UpdateManifest.findUpdate(json, version, "1.1.0").orElseThrow()
                .equals(URI.create("https://example.org/fabric-" + version)), "Wrong entry's URL selected");
        String changed = json.replace("https://example.org/fabric-26.2", "https://downloads.example.net/new-page?release=2#files");
        helper.assertTrue(UpdateManifest.findUpdate(changed, "26.2", "1.1.0").orElseThrow()
            .equals(URI.create("https://downloads.example.net/new-page?release=2#files")), "Manifest link change ignored");
        for (String invalid : new String[] {"", "relative/path", "https://", "https://bad host/path",
                "javascript:alert(1)", "file:///tmp/mod.jar", "https://user:password@example.org/file"})
            helper.assertTrue(UpdateManifest.findUpdate(json.replace("https://example.org/fabric-26.2", invalid),
                "26.2", "1.1.0").isEmpty(), "Invalid URL must suppress the update: " + invalid);
        for (String malformed : new String[] {
                json.replace(",\"url\":\"https://example.org/fabric-26.2\"", ""),
                json.replace("\"https://example.org/fabric-26.2\"", "null"),
                json.replace("\"https://example.org/fabric-26.2\"", "42")})
            helper.assertTrue(UpdateManifest.findUpdate(malformed, "26.2", "1.1.0").isEmpty(), "Missing/invalid URL accepted");
        helper.succeed();
    }

    @GameTest
    public void manifestMatchesExactTargetAndComparesVersions(GameTestHelper helper) {
        helper.assertTrue(isNewer(manifest("1.2.0"), "26.2", "1.1.0-26.2-fabric"), "New release missed");
        helper.assertTrue(isNewer(manifest("1.10.0"), "26.2", "1.9.0-26.2-fabric"), "Comparison must be numeric");
        helper.assertTrue(isNewer(manifest("1.0.0"), "26.3", "1.1.0-26.3-fabric"), "Wrong MC entry selected");
        helper.assertTrue(isNewer(manifest("1.2.0"), "26.2", "1.2.0-rc.1-26.2-fabric"), "Release must follow prerelease");
        for (String latest : new String[] {"1.0.0", "1.1.0", "1.1.0+build.7", "1.1.0-rc.1", "garbage", "", "2.x"})
            helper.assertTrue(!isNewer(manifest(latest), "26.2", "1.1.0-26.2-fabric"), "False update: " + latest);
        String valid = manifest("1.2.0");
        for (String invalid : new String[] {"", "<html>Unavailable</html>", "null", "[]", "{}", "{broken",
                valid.replace("1699797", "1713176"), valid.replace("fabric", "forge"),
                valid.replace("26.2", "1.21.1"), valid.replace("\"schema\":1", "\"schema\":2"),
                valid.replace("\"1.2.0\"", "null"), valid.replace("\"1.2.0\"", "12")})
            helper.assertTrue(!isNewer(invalid, "26.2", "1.1.0-26.2-fabric"), "Invalid/missing entry accepted");
        helper.assertTrue(!isNewer(valid, "26.4", "1.1.0"), "Must not fall back to another MC version");
        helper.assertTrue(!isNewer(valid, "26.2", "unknown"), "Unknown local version must not announce");
        helper.succeed();
    }

    @GameTest
    public void checkRunsOnlyOnceAndDiscardsStaleNotifications(GameTestHelper helper) {
        var session = new UpdateCheckSession();
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger messages = new AtomicInteger();
        URI download = URI.create("https://example.org/from-manifest");
        java.util.function.Consumer<URI> notify = url -> {
            helper.assertTrue(download.equals(url), "Session changed the manifest URL");
            messages.incrementAndGet();
        };
        AtomicBoolean eligible = new AtomicBoolean(true);
        var result = new CompletableFuture<Optional<URI>>();
        var queue = new ArrayList<Runnable>();
        java.util.function.Supplier<CompletableFuture<Optional<URI>>> request = () -> { requests.incrementAndGet(); return result; };
        session.onJoin(false, true, request, queue::add, eligible::get, notify);
        helper.assertTrue(requests.get() == 0, "Multiplayer must not request updates");
        session.onJoin(true, true, request, queue::add, eligible::get, notify);
        session.onJoin(true, true, request, queue::add, eligible::get, notify);
        result.complete(Optional.of(download));
        helper.assertTrue(requests.get() == 1 && queue.size() == 1 && messages.get() == 0, "One request, client-thread dispatch required");
        eligible.set(false); // Disconnected, moved to multiplayer or disabled the setting before dispatch.
        queue.getFirst().run();
        helper.assertTrue(messages.get() == 0, "Stale result displayed");

        var disabled = new UpdateCheckSession();
        disabled.onJoin(true, false, request, Runnable::run, () -> true, notify);
        disabled.onJoin(true, true, request, Runnable::run, () -> true, notify);
        helper.assertTrue(requests.get() == 1, "Disabled first join must skip the entire session");
        var successful = new UpdateCheckSession();
        successful.onJoin(true, true, request, Runnable::run, () -> true, notify);
        successful.onJoin(true, true, request, Runnable::run, () -> true, notify);
        helper.assertTrue(messages.get() == 1, "Expected exactly one local announcement");
        var failed = new UpdateCheckSession();
        failed.onJoin(true, true, () -> CompletableFuture.failedFuture(new IOException("Offline")),
            Runnable::run, () -> true, notify);
        failed.onJoin(true, true, request, Runnable::run, () -> true, notify);
        helper.assertTrue(requests.get() == 2 && messages.get() == 1, "Failed check must not retry or notify");
        helper.succeed();
    }

    @GameTest
    public void httpFailuresTimeoutsAndOversizedResponsesAreHandled(GameTestHelper helper) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(task -> Thread.ofVirtual().start(task));
        server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/slow")) {
                    try { Thread.sleep(500); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                byte[] body = (path.equals("/large") ? "x".repeat(256 * 1024 + 1) : manifest("1.2.0"))
                    .getBytes(StandardCharsets.UTF_8);
                int status = path.equals("/error") ? 503 : path.equals("/redirect") ? 302 : 200;
                if (status == 302) exchange.getResponseHeaders().add("Location", "/ok");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException ignored) { /* The timeout/size-limit cases intentionally close the connection. */ }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            String body = UpdateManifest.fetch(URI.create(origin + "/ok"), Duration.ofSeconds(3)).join();
            helper.assertTrue(isNewer(body, "26.2", "1.1.0"), "HTTP success did not parse");
            for (String path : new String[] {"/error", "/redirect", "/large", "/slow"}) {
                boolean failed = false;
                try {
                    UpdateManifest.fetch(URI.create(origin + path), path.equals("/slow")
                        ? Duration.ofMillis(100) : Duration.ofSeconds(3)).join();
                } catch (CompletionException expected) { failed = true; }
                helper.assertTrue(failed, "Expected controlled failure for " + path);
            }
        } finally { server.stop(0); }
        boolean offline = false;
        try { UpdateManifest.fetch(URI.create(origin + "/ok"), Duration.ofSeconds(1)).join(); }
        catch (CompletionException expected) { offline = true; }
        helper.assertTrue(offline, "Unreachable host must fail without a chat message");
        helper.succeed();
    }
}
