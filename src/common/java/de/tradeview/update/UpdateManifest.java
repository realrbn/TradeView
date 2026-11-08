package de.tradeview.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Resolves an update and its download link from the same matching manifest entry. */
public final class UpdateManifest {
    public static final URI SOURCE = URI.create("https://realrbn.github.io/update-check/update-check.json");
    private static final int MAX_BYTES = 256 * 1024;

    private UpdateManifest() {}

    public static CompletableFuture<Optional<URI>> check(String loader, String minecraftVersion, String installedVersion) {
        return fetch(SOURCE, Duration.ofSeconds(10))
            .thenApply(json -> findUpdate(json, loader, minecraftVersion, installedVersion));
    }

    static CompletableFuture<String> fetch(URI source, Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(source).timeout(timeout)
            .header("Accept", "application/json").header("User-Agent", "VillagerTradePreview-UpdateChecker")
            .GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), MAX_BYTES))
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .thenApply(response -> {
                if (response.statusCode() != 200)
                    throw new CompletionException(new IOException("Update endpoint returned HTTP " + response.statusCode()));
                return response.body();
            }).whenComplete((result, error) -> http.shutdownNow());
    }

    public static Optional<URI> findUpdate(String json, String minecraftVersion, String installedVersion) {
        return findUpdate(json, "fabric", minecraftVersion, installedVersion);
    }

    /** Only the entry of this loader and Minecraft version counts; its URL is the announced download. */
    public static Optional<URI> findUpdate(String json, String loader, String minecraftVersion, String installedVersion) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("schema") || !root.get("schema").isJsonPrimitive()
                || !root.get("schema").getAsJsonPrimitive().isNumber()
                || !root.get("schema").getAsString().equals("1")) return Optional.empty();
            var entry = root.getAsJsonObject("mods").getAsJsonObject("1699797")
                .getAsJsonObject(loader).getAsJsonObject(minecraftVersion);
            var latest = entry.get("latest");
            var url = entry.get("url");
            if (latest == null || !latest.isJsonPrimitive() || !latest.getAsJsonPrimitive().isString()
                || url == null || !url.isJsonPrimitive() || !url.getAsJsonPrimitive().isString()) return Optional.empty();
            URI download = URI.create(url.getAsString());
            if (!("https".equalsIgnoreCase(download.getScheme()) || "http".equalsIgnoreCase(download.getScheme()))
                || download.getHost() == null || download.getUserInfo() != null) return Optional.empty();
            String suffix = "-" + minecraftVersion + "-" + loader;
            String current = installedVersion.endsWith(suffix)
                ? installedVersion.substring(0, installedVersion.length() - suffix.length()) : installedVersion;
            ModVersion remote = ModVersion.parse(latest.getAsString());
            ModVersion local = ModVersion.parse(current);
            return remote.compareTo(local) > 0 ? Optional.of(download) : Optional.empty();
        } catch (RuntimeException e) {
            // Missing entries, invalid JSON and non-semantic versions are not update announcements.
            return Optional.empty();
        }
    }
}
