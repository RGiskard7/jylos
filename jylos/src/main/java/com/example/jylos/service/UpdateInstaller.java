package com.example.jylos.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongConsumer;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.service.UpdateChecker.AssetInfo;

/**
 * REMOVABLE: in-app updater — downloads, verifies and launches a Jylos update
 * installer without routing the download through the user's browser. See "Removing
 * this later" below for the full removal checklist.
 *
 * <h2>Why this exists</h2>
 * <p>Jylos releases are not code-signed or notarized: an Apple Developer Program
 * membership and a Windows EV code-signing certificate both cost money the project
 * cannot currently spend. A file a <em>browser</em> downloads gets an OS-level
 * "this came from the internet" marker — the {@code com.apple.quarantine} extended
 * attribute on macOS, the {@code Zone.Identifier} alternate data stream on Windows —
 * and an unsigned installer carrying that marker is blocked (macOS Gatekeeper) or
 * scarily flagged (Windows SmartScreen) on <em>every single download</em>, forcing
 * the user back into system settings to explicitly override it each time. That
 * override is unavoidable on the very first install of an unsigned app — there is
 * no signature to trust, full stop — but it should not have to be repeated for
 * every update afterward.</p>
 *
 * <p>A file downloaded by plain socket I/O ({@link HttpClient} here, not a browser)
 * never receives that marker, so the OS never re-runs its per-file
 * signature/reputation check on it. That is a deliberate, real difference from a
 * browser download — documented here so it is never mistaken for an accident or,
 * worse, hidden from anyone reading this code.</p>
 *
 * <h2>What this does and does not protect against</h2>
 * <p>Every downloaded asset is checked against the SHA-256 digest GitHub itself
 * computed server-side when the release asset was uploaded (the {@code digest}
 * field on the GitHub Releases API — independent of anything Jylos's own build or
 * release pipeline publishes) before the file is executed. That catches transport
 * corruption and man-in-the-middle tampering between GitHub and this machine. It
 * does <strong>not</strong> vouch for the release itself — it does not catch a
 * compromised Jylos release pipeline or GitHub account publishing a malicious asset
 * in the first place. Nothing short of real code signing closes that gap. When no
 * digest is available for the asset picked for this platform, this class refuses to
 * install it (see {@link #verifyDigest}); the caller is expected to fall back to
 * the normal browser download instead of running an unverified binary.</p>
 *
 * <h2>Removing this later</h2>
 * <p>The moment Jylos can afford a real Apple Developer ID (macOS notarization) and
 * a Windows code-signing certificate, this whole mechanism stops being necessary —
 * a normal signed browser download will no longer trigger repeated OS warnings. At
 * that point, full removal checklist:</p>
 * <ol>
 *   <li>Delete this class ({@code UpdateInstaller.java}) and
 *       {@code com.example.jylos.ui.controller.UpdateInstallSupport}.</li>
 *   <li>In {@code MainController}: grep for the exact string
 *       {@code "REMOVABLE: in-app updater"} — every touch point (the
 *       {@code updateInstallSupport} field, its {@code wire(...)} call, and the
 *       "Install now" {@code Hyperlink} in the update toast) is tagged with it, so
 *       that search alone finds everything to delete or revert.</li>
 *   <li>Revert the update toast back to a single "Open downloads" link (the plain
 *       {@code Hyperlink} already in that method, calling {@code openExternalUrl}).</li>
 *   <li>Remove {@code AppDataDirectory.getUpdatesDirectory()} (only this class used
 *       it) and the {@code update.install.*} keys from the three
 *       {@code messages*.properties} files (each has a matching
 *       "in-app updater" comment marking that block).</li>
 *   <li>Delete {@code UpdateInstallerTest.java}. Leave {@code UpdateChecker}'s
 *       {@code AssetInfo}/{@code digest} additions and their tests in
 *       {@code UpdateCheckerTest} alone — that is just release-asset metadata, not
 *       part of the bypass mechanism, and stays generically useful on its own.</li>
 *   <li>Delete the "In-app updater (unsigned builds)" section from
 *       {@code docs/PACKAGING.md} and {@code docs/es/PACKAGING.md}.</li>
 * </ol>
 *
 * @author Edu Díaz (RGiskard7)
 */
public final class UpdateInstaller {

    /** Prefix GitHub uses on the {@code digest} field of a release asset. */
    private static final String SHA256_PREFIX = "sha256:";

    private final HttpClient httpClient;

    public UpdateInstaller() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    UpdateInstaller(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Picks the release asset matching the platform this JVM is running on, using
     * the standardized asset names {@code scripts/package-*} and
     * {@code release-jylos.yml} both produce. Returns empty when the release has no
     * asset for this platform yet (e.g. macOS releases are not published by CI at
     * the time of writing) or the local Linux distribution could not be identified
     * as Debian- or RHEL-family — callers must treat that as "fall back to the
     * browser download", not as an error.
     */
    public static Optional<AssetInfo> pickAssetForCurrentPlatform(List<AssetInfo> assets) {
        return pickAsset(assets,
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                Files.exists(Path.of("/etc/debian_version")),
                Files.exists(Path.of("/etc/redhat-release")));
    }

    /** Pure asset-selection logic, factored out of {@link #pickAssetForCurrentPlatform} so it is testable without depending on the real OS/filesystem. */
    static Optional<AssetInfo> pickAsset(List<AssetInfo> assets, String osName, String osArch,
            boolean debianBased, boolean redhatBased) {
        if (assets == null || assets.isEmpty()) {
            return Optional.empty();
        }
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return findByName(assets, "jylos-windows-x64.exe")
                    .or(() -> findByName(assets, "jylos-windows-x64.msi"));
        }
        if (os.contains("mac")) {
            String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
            boolean arm = arch.contains("aarch64") || arch.contains("arm64");
            return findByName(assets, arm ? "jylos-macos-arm64.dmg" : "jylos-macos-x64.dmg");
        }
        if (os.contains("nux") || os.contains("nix")) {
            if (debianBased) {
                return findByName(assets, "jylos-linux-amd64.deb");
            }
            if (redhatBased) {
                return findByName(assets, "jylos-linux-amd64.rpm");
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<AssetInfo> findByName(List<AssetInfo> assets, String name) {
        return assets.stream().filter(a -> name.equals(a.name())).findFirst();
    }

    /**
     * Downloads {@code uri} to {@code destination}, reporting cumulative bytes
     * written via {@code onBytesDownloaded} (may be called from a background
     * thread — callers touching UI state must hop back to the FX thread
     * themselves). Writes to a sibling {@code .part} file first and only renames it
     * to the final name once the whole transfer succeeds, so a destination that
     * exists is always complete, never a partial download left over from an
     * interrupted attempt.
     */
    public void downloadWithProgress(URI uri, Path destination, LongConsumer onBytesDownloaded)
            throws IOException, InterruptedException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "Jylos-UpdateInstaller")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed: HTTP " + response.statusCode());
        }
        Path tempFile = destination.resolveSibling(destination.getFileName() + ".part");
        try (InputStream in = response.body();
                var out = Files.newOutputStream(tempFile,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
                if (onBytesDownloaded != null) {
                    onBytesDownloaded.accept(total);
                }
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
        Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Verifies {@code file} against a GitHub asset {@code digest} field
     * ({@code "sha256:<hex>"}). Returns {@code false} — never throws for a mismatch —
     * whenever the digest is missing, malformed, or does not match; callers must
     * treat any {@code false} as "do not run this file".
     */
    public static boolean verifyDigest(Path file, String expectedDigest) throws IOException {
        if (expectedDigest == null || !expectedDigest.toLowerCase(Locale.ROOT).startsWith(SHA256_PREFIX)) {
            return false;
        }
        String expectedHex = expectedDigest.substring(SHA256_PREFIX.length()).trim();
        if (expectedHex.isBlank()) {
            return false;
        }
        String actualHex = sha256Hex(file);
        return expectedHex.equalsIgnoreCase(actualHex);
    }

    /** SHA-256 of a file's contents, as lowercase hex. */
    static String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every JDK's default security provider is required to support SHA-256
            // (JCA standard algorithm names) — this cannot happen on a real JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Where a downloaded installer for {@code assetName} is staged before running. */
    public static Path stagingPath(String assetName) {
        return Path.of(AppDataDirectory.getUpdatesDirectory(), assetName);
    }

    /**
     * Launches {@code installer} with the OS's own "open this file" mechanism —
     * the same approach {@code SystemBrowser} and the reveal-in-file-manager actions
     * already use elsewhere in the app. For an installer package (.exe/.msi/.dmg/.deb/.rpm)
     * this hands off to the native installer UI; it does not run silently and does
     * not require elevated privileges from Jylos itself.
     */
    public void launch(Path installer) throws IOException {
        List<String> command = buildLaunchCommand(System.getProperty("os.name", ""), installer);
        new ProcessBuilder(command).start();
    }

    /** Pure command-building logic, factored out of {@link #launch} so it is testable without spawning a real process. */
    static List<String> buildLaunchCommand(String osName, Path installer) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String path = installer.toAbsolutePath().toString();
        if (os.contains("mac")) {
            return List.of("open", path);
        }
        if (os.contains("win")) {
            return List.of("explorer.exe", path);
        }
        return List.of("xdg-open", path);
    }
}
