package com.example.jylos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyWindowsInstallTest {

    @TempDir
    Path tmp;

    /**
     * What the Windows Installer query prints: one related product per line, its product
     * code and assignment type (0 = installed for this user, 1 = for the whole machine).
     */
    private static final String RELATED_PRODUCTS = """
            {0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9}	0
            {11111111-2222-3333-4444-555555555555}	1
            not a product line
            """;

    @Test
    void uninstallsOnlyTheRelatedProductsInstalledForThisUser() {
        assertEquals(List.of("{0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9}"),
                LegacyWindowsInstall.perUserProductCodes(RELATED_PRODUCTS));
    }

    @Test
    void noRelatedProductsMeansNothingToUninstall() {
        assertEquals(List.of(), LegacyWindowsInstall.perUserProductCodes(""));
        assertEquals(List.of(), LegacyWindowsInstall.perUserProductCodes(
                "New-Object : Retrieving the COM class factory failed"));
    }

    private Path oldInstall() throws IOException {
        Path dir = tmp.resolve("Local/Jylos");
        Files.createDirectories(dir.resolve("app"));
        Files.createDirectories(dir.resolve("runtime"));
        Files.createDirectories(dir.resolve("logs"));
        Files.createDirectories(dir.resolve("plugins"));
        Files.writeString(dir.resolve("Jylos.exe"), "exe");
        Files.writeString(dir.resolve("app/jylos-2.5.7-uber.jar"), "jar");
        Files.writeString(dir.resolve("logs/app.log.0"), "log");
        Files.writeString(dir.resolve("plugins/AIPlugin.jar"), "bundled");
        Files.writeString(dir.resolve("plugins/.bundled-plugins-copied"), "marker");
        return dir;
    }

    @Test
    void anOldInstallWithOnlyTheProgramMayBeUninstalled() throws IOException {
        assertTrue(LegacyWindowsInstall.onlyProgramLeft(oldInstall(), "Jylos", Set.of("AIPlugin.jar")));
    }

    @Test
    void anythingOfTheUsersLeftBlocksTheUninstall() throws IOException {
        Path withData = oldInstall();
        Files.createDirectories(withData.resolve("data"));
        Files.writeString(withData.resolve("data/database.db"), "notes");
        assertFalse(LegacyWindowsInstall.onlyProgramLeft(withData, "Jylos", Set.of("AIPlugin.jar")),
                "data that could not be migrated would be deleted");

        Files.delete(withData.resolve("data/database.db"));
        Files.delete(withData.resolve("data"));
        Files.writeString(withData.resolve("plugins/Mine.jar"), "a plugin the user added");
        assertFalse(LegacyWindowsInstall.onlyProgramLeft(withData, "Jylos", Set.of("AIPlugin.jar")),
                "a plugin the user added");

        Files.delete(withData.resolve("plugins/Mine.jar"));
        Files.writeString(withData.resolve("notas.txt"), "something the user put there");
        assertFalse(LegacyWindowsInstall.onlyProgramLeft(withData, "Jylos", Set.of("AIPlugin.jar")),
                "an unknown file");
    }

    @Test
    void aFolderWithoutTheOldLauncherIsNotAnInstall() throws IOException {
        Path dir = oldInstall();
        Files.delete(dir.resolve("Jylos.exe"));
        assertFalse(LegacyWindowsInstall.onlyProgramLeft(dir, "Jylos", Set.of("AIPlugin.jar")));
        assertFalse(LegacyWindowsInstall.onlyProgramLeft(tmp.resolve("missing"), "Jylos", Set.of()));
    }
}
