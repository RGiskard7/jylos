package com.example.jylos.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.util.Versions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Checks GitHub Releases for a newer public Jylos version.
 *
 * <p>The checker is deliberately UI-agnostic: it fetches release metadata,
 * compares semantic versions numerically and returns a small result object. The
 * controller decides how and when to notify the user.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.4.1
 */
public final class UpdateChecker {

    /**
     * One downloadable file attached to a GitHub release.
     *
     * @param name canonical asset filename, e.g. {@code jylos-windows-x64.exe}
     * @param browserDownloadUrl direct download URL for the asset
     * @param size asset size in bytes, as reported by GitHub
     * @param digest content digest GitHub computed server-side when the asset was
     *         uploaded, formatted {@code "sha256:<hex>"} — {@code null} when GitHub
     *         did not report one (older releases, or a non-github.com host). Used by
     *         {@link com.example.jylos.service.UpdateInstaller} to verify a downloaded
     *         file was not corrupted or tampered with in transit before it is run.
     */
    public record AssetInfo(String name, String browserDownloadUrl, long size, String digest) {
    }

    /**
     * Metadata for the latest GitHub release.
     *
     * @param tagName release tag, usually prefixed with {@code v}
     * @param htmlUrl browser URL for the GitHub release page
     * @param assets downloadable files attached to the release (installers, JARs, …)
     * @param notes the release notes in Markdown (the version's changelog section); empty if none
     */
    public record ReleaseInfo(String tagName, String htmlUrl, List<AssetInfo> assets, String notes) {
    }

    /**
     * Result of an update check.
     *
     * @param release available newer release, or empty when already current
     * @param failed true when the check could not be completed
     */
    public record CheckResult(Optional<ReleaseInfo> release, boolean failed) {
        static CheckResult available(ReleaseInfo release) {
            return new CheckResult(Optional.of(release), false);
        }

        static CheckResult current() {
            return new CheckResult(Optional.empty(), false);
        }

        static CheckResult failure() {
            return new CheckResult(Optional.empty(), true);
        }
    }

    private static final Logger logger = LoggerConfig.getLogger(UpdateChecker.class);
    private static final URI LATEST_RELEASE_URI =
            URI.create("https://api.github.com/repos/RGiskard7/jylos/releases/latest");

    private final HttpClient httpClient;

    public UpdateChecker() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    UpdateChecker(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public CompletableFuture<Optional<ReleaseInfo>> checkForUpdateAsync(String currentVersion, Executor executor) {
        return checkLatestAsync(currentVersion, executor).thenApply(CheckResult::release);
    }

    public CompletableFuture<CheckResult> checkLatestAsync(String currentVersion, Executor executor) {
        return CompletableFuture.supplyAsync(() -> checkLatest(currentVersion), executor)
                .exceptionally(error -> {
                    logger.log(Level.FINE, "Update check failed", error);
                    return CheckResult.failure();
                });
    }

    public Optional<ReleaseInfo> checkForUpdate(String currentVersion) {
        return checkLatest(currentVersion).release();
    }

    public CheckResult checkLatest(String currentVersion) {
        try {
            ReleaseInfo latest = fetchLatestRelease();
            if (isNewerVersion(latest.tagName(), currentVersion)) {
                logger.info("New Jylos release available: " + latest.tagName() + " " + latest.htmlUrl());
                return CheckResult.available(latest);
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not contact GitHub releases API", e);
            return CheckResult.failure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.FINE, "Update check interrupted", e);
            return CheckResult.failure();
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not parse GitHub releases API response", e);
            return CheckResult.failure();
        }
        return CheckResult.current();
    }

    ReleaseInfo fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_URI)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Jylos-UpdateChecker")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub releases API returned HTTP " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String tagName = getRequiredString(json, "tag_name");
        String htmlUrl = getRequiredString(json, "html_url");
        return new ReleaseInfo(tagName, htmlUrl, parseAssets(json), parseNotes(json));
    }

    /** The release's notes (its changelog section, in Markdown); empty when it has none. */
    static String parseNotes(JsonObject releaseJson) {
        if (!releaseJson.has("body") || releaseJson.get("body").isJsonNull()) {
            return "";
        }
        return releaseJson.get("body").getAsString();
    }

    /** Package-private (not private) so {@code UpdateCheckerTest} can exercise it directly against a hand-built JSON payload, without a real network call. */
    static List<AssetInfo> parseAssets(JsonObject releaseJson) {
        List<AssetInfo> assets = new ArrayList<>();
        if (!releaseJson.has("assets") || !releaseJson.get("assets").isJsonArray()) {
            return assets;
        }
        JsonArray array = releaseJson.getAsJsonArray("assets");
        for (var element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            if (!asset.has("name") || !asset.has("browser_download_url")) {
                continue;
            }
            String name = asset.get("name").getAsString();
            String url = asset.get("browser_download_url").getAsString();
            long size = asset.has("size") && !asset.get("size").isJsonNull() ? asset.get("size").getAsLong() : -1;
            String digest = asset.has("digest") && !asset.get("digest").isJsonNull()
                    ? asset.get("digest").getAsString() : null;
            assets.add(new AssetInfo(name, url, size, digest));
        }
        return assets;
    }

    static boolean isNewerVersion(String candidate, String current) {
        return Versions.isNewer(candidate, current);
    }

    private static String getRequiredString(JsonObject json, String fieldName) {
        if (!json.has(fieldName) || json.get(fieldName).isJsonNull()) {
            throw new IllegalArgumentException("Missing JSON field: " + fieldName);
        }
        return json.get(fieldName).getAsString();
    }
}
