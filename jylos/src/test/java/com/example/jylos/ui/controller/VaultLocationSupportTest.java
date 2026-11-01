package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Which directory the filesystem backend opens. A configured vault that cannot be found
 * (unplugged drive, unmounted cloud folder, renamed directory) must be reported as missing
 * — never silently swapped for the default data directory, where the user would see an
 * empty or unrelated vault and keep writing notes in the wrong place.
 */
class VaultLocationSupportTest {

    @TempDir
    Path sandbox;

    @Test
    void noConfiguredPathUsesTheDefaultDirectory() {
        assertEquals(VaultLocationSupport.Kind.DEFAULT, VaultLocationSupport.resolve(null, Files::isDirectory).kind());
        assertEquals(VaultLocationSupport.Kind.DEFAULT, VaultLocationSupport.resolve("  ", Files::isDirectory).kind());
    }

    @Test
    void anExistingConfiguredVaultIsUsed() {
        VaultLocationSupport.Resolution resolution =
                VaultLocationSupport.resolve(sandbox.toString(), Files::isDirectory);
        assertEquals(VaultLocationSupport.Kind.CONFIGURED, resolution.kind());
        assertEquals(sandbox.toString(), resolution.path());
    }

    @Test
    void aConfiguredVaultThatIsGoneIsReportedMissingNotReplaced() {
        String gone = sandbox.resolve("unplugged-drive").resolve("vault").toString();
        VaultLocationSupport.Resolution resolution = VaultLocationSupport.resolve(gone, Files::isDirectory);
        assertEquals(VaultLocationSupport.Kind.MISSING, resolution.kind());
        assertEquals(gone, resolution.path());
    }

    @Test
    void aConfiguredPathThatIsAFileIsMissingToo() throws Exception {
        Path file = Files.writeString(sandbox.resolve("not-a-folder.md"), "x");
        assertEquals(VaultLocationSupport.Kind.MISSING,
                VaultLocationSupport.resolve(file.toString(), Files::isDirectory).kind());
    }
}
