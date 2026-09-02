package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.service.UpdateChecker.AssetInfo;

/**
 * REMOVABLE: in-app updater — delete this file alongside {@link UpdateInstaller}
 * (see its class docs, "Removing this later", for the full removal checklist).
 *
 * <p>The parts of {@link UpdateInstaller} that decide what gets downloaded and
 * whether it is safe to run — platform asset selection and checksum verification.
 * Both are pure/static so they are tested directly, without touching the network
 * or spawning a real process.
 */
class UpdateInstallerTest {

    // ------------------------------------------------------------------
    // pickAsset — platform/architecture asset selection
    // ------------------------------------------------------------------

    private static final AssetInfo WIN_EXE = asset("jylos-windows-x64.exe");
    private static final AssetInfo WIN_MSI = asset("jylos-windows-x64.msi");
    private static final AssetInfo MAC_ARM = asset("jylos-macos-arm64.dmg");
    private static final AssetInfo MAC_X64 = asset("jylos-macos-x64.dmg");
    private static final AssetInfo LINUX_DEB = asset("jylos-linux-amd64.deb");
    private static final AssetInfo LINUX_RPM = asset("jylos-linux-amd64.rpm");

    @Test
    void windowsPrefersTheExeInstallerOverTheMsiWhenBothArePublished() {
        Optional<AssetInfo> picked = UpdateInstaller.pickAsset(
                List.of(WIN_EXE, WIN_MSI), "Windows 11", "amd64", false, false);
        assertTrue(picked.isPresent());
        assertEquals("jylos-windows-x64.exe", picked.get().name());
    }

    @Test
    void windowsFallsBackToTheMsiWhenNoExeIsPublished() {
        Optional<AssetInfo> picked = UpdateInstaller.pickAsset(
                List.of(WIN_MSI), "Windows 11", "amd64", false, false);
        assertTrue(picked.isPresent());
        assertEquals("jylos-windows-x64.msi", picked.get().name());
    }

    @Test
    void windowsWithNoMatchingAssetReturnsEmpty() {
        assertTrue(UpdateInstaller.pickAsset(
                List.of(LINUX_DEB, LINUX_RPM), "Windows 11", "amd64", false, false).isEmpty());
    }

    @Test
    void macArm64PicksTheArm64DmgAndNeverFallsBackToTheWrongArchitecture() {
        Optional<AssetInfo> picked = UpdateInstaller.pickAsset(
                List.of(MAC_X64, MAC_ARM), "Mac OS X", "aarch64", false, false);
        assertTrue(picked.isPresent());
        assertEquals("jylos-macos-arm64.dmg", picked.get().name());

        // Only the x64 asset is published — an arm64 machine must NOT silently
        // install the wrong-architecture binary.
        assertTrue(UpdateInstaller.pickAsset(
                List.of(MAC_X64), "Mac OS X", "aarch64", false, false).isEmpty());
    }

    @Test
    void macX64PicksTheX64Dmg() {
        Optional<AssetInfo> picked = UpdateInstaller.pickAsset(
                List.of(MAC_X64, MAC_ARM), "Mac OS X", "x86_64", false, false);
        assertTrue(picked.isPresent());
        assertEquals("jylos-macos-x64.dmg", picked.get().name());
    }

    @Test
    void debianBasedLinuxPicksTheDebEvenWhenAnRpmIsAlsoPublished() {
        Optional<AssetInfo> picked = UpdateInstaller.pickAsset(
                List.of(LINUX_DEB, LINUX_RPM), "Linux", "amd64", true, false);
        assertTrue(picked.isPresent());
        assertEquals("jylos-linux-amd64.deb", picked.get().name());
    }

    @Test
    void redhatBasedLinuxPicksTheRpm() {
        Optional<AssetInfo> picked = UpdateInstaller.pickAsset(
                List.of(LINUX_DEB, LINUX_RPM), "Linux", "amd64", false, true);
        assertTrue(picked.isPresent());
        assertEquals("jylos-linux-amd64.rpm", picked.get().name());
    }

    @Test
    void unrecognizedLinuxDistroReturnsEmptyRatherThanGuessing() {
        assertTrue(UpdateInstaller.pickAsset(
                List.of(LINUX_DEB, LINUX_RPM), "Linux", "amd64", false, false).isEmpty());
    }

    @Test
    void unknownOperatingSystemReturnsEmpty() {
        assertTrue(UpdateInstaller.pickAsset(
                List.of(WIN_EXE, MAC_ARM, LINUX_DEB), "SunOS", "sparc", false, false).isEmpty());
    }

    @Test
    void emptyOrNullAssetListReturnsEmpty() {
        assertTrue(UpdateInstaller.pickAsset(List.of(), "Windows 11", "amd64", false, false).isEmpty());
        assertTrue(UpdateInstaller.pickAsset(null, "Windows 11", "amd64", false, false).isEmpty());
    }

    private static AssetInfo asset(String name) {
        return new AssetInfo(name, "https://example.invalid/" + name, 1024, "sha256:deadbeef");
    }

    // ------------------------------------------------------------------
    // verifyDigest / sha256Hex — checksum verification
    // ------------------------------------------------------------------

    @TempDir
    Path tempDir;

    @Test
    void verifiesAKnownAnswerVectorForAnEmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.bin");
        Files.write(file, new byte[0]);
        // Well-known SHA-256 of the empty string/file (NIST test vector).
        assertTrue(UpdateInstaller.verifyDigest(file,
                "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
    }

    @Test
    void verifiesAKnownAnswerVectorForANonTrivialFile() throws IOException {
        Path file = tempDir.resolve("abc.bin");
        Files.write(file, "abc".getBytes(StandardCharsets.US_ASCII));
        // Well-known SHA-256("abc") NIST test vector.
        assertTrue(UpdateInstaller.verifyDigest(file,
                "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
    }

    @Test
    void digestComparisonIsCaseInsensitive() throws IOException {
        Path file = tempDir.resolve("abc.bin");
        Files.write(file, "abc".getBytes(StandardCharsets.US_ASCII));
        assertTrue(UpdateInstaller.verifyDigest(file,
                "sha256:BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"));
    }

    @Test
    void rejectsAMismatchedDigestWithoutThrowing() throws IOException {
        Path file = tempDir.resolve("abc.bin");
        Files.write(file, "abc".getBytes(StandardCharsets.US_ASCII));
        assertFalse(UpdateInstaller.verifyDigest(file,
                "sha256:0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void rejectsAMissingOrMalformedDigestRatherThanSkippingVerification() throws IOException {
        Path file = tempDir.resolve("abc.bin");
        Files.write(file, "abc".getBytes(StandardCharsets.US_ASCII));
        assertFalse(UpdateInstaller.verifyDigest(file, null));
        assertFalse(UpdateInstaller.verifyDigest(file, ""));
        assertFalse(UpdateInstaller.verifyDigest(file, "md5:900150983cd24fb0d6963f7d28e17f72"));
        assertFalse(UpdateInstaller.verifyDigest(file, "sha256:"));
    }

    // ------------------------------------------------------------------
    // buildLaunchCommand — per-OS "open this file" command
    // ------------------------------------------------------------------

    @Test
    void picksTheOpenCommandOnMacOS() {
        assertEquals("open", UpdateInstaller.buildLaunchCommand("Mac OS X", tempDir.resolve("Jylos.dmg")).get(0));
    }

    @Test
    void picksExplorerOnWindows() {
        assertEquals("explorer.exe", UpdateInstaller.buildLaunchCommand("Windows 11", tempDir.resolve("Jylos.exe")).get(0));
    }

    @Test
    void picksXdgOpenOnLinuxAndAsTheUnknownOsFallback() {
        assertEquals("xdg-open", UpdateInstaller.buildLaunchCommand("Linux", tempDir.resolve("jylos.deb")).get(0));
        assertEquals("xdg-open", UpdateInstaller.buildLaunchCommand("SunOS", tempDir.resolve("jylos")).get(0));
    }
}
