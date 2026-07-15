package com.example.jylos.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Checks GitHub Releases for a newer public Jylos version.
 */
public final class UpdateChecker {

    public record ReleaseInfo(String tagName, String htmlUrl) {
    }

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
        return new ReleaseInfo(tagName, htmlUrl);
    }

    static boolean isNewerVersion(String candidate, String current) {
        int[] candidateParts = parseSemVer(candidate);
        int[] currentParts = parseSemVer(current);
        int max = Math.max(candidateParts.length, currentParts.length);
        for (int i = 0; i < max; i++) {
            int candidatePart = i < candidateParts.length ? candidateParts[i] : 0;
            int currentPart = i < currentParts.length ? currentParts[i] : 0;
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        return false;
    }

    private static int[] parseSemVer(String version) {
        if (version == null || version.isBlank()) {
            return new int[] {0, 0, 0};
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int suffixIndex = normalized.indexOf('-');
        if (suffixIndex >= 0) {
            normalized = normalized.substring(0, suffixIndex);
        }
        String[] rawParts = normalized.split("\\.");
        int[] parts = new int[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            parts[i] = parsePart(rawParts[i]);
        }
        return parts;
    }

    private static int parsePart(String part) {
        String digits = part == null ? "" : part.replaceAll("[^0-9].*$", "");
        if (digits.isBlank()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }

    private static String getRequiredString(JsonObject json, String fieldName) {
        if (!json.has(fieldName) || json.get(fieldName).isJsonNull()) {
            throw new IllegalArgumentException("Missing JSON field: " + fieldName);
        }
        return json.get(fieldName).getAsString();
    }
}
