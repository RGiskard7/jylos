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

    /** What `reg query ... /s /f Jylos /d /e` prints on a Spanish Windows. */
    private static final String REG_OUTPUT = """

            HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9}
                DisplayName    REG_SZ    Jylos

            HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\JylosHelper
                DisplayName    REG_SZ    Jylos

            HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{11111111-2222-3333-4444-555555555555}
                DisplayName    REG_SZ    Jylos Companion

            Fin de la búsqueda: 3 coincidencia(s) encontrada(s).
            """;

    @Test
    void findsOnlyMsiProductCodesOfEntriesNamedExactlyLikeTheApp() {
        assertEquals(List.of("{0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9}"),
                LegacyWindowsInstall.productCodes(REG_OUTPUT, "Jylos"));
    }

    @Test
    void noEntriesMeansNothingToUninstall() {
        assertEquals(List.of(), LegacyWindowsInstall.productCodes("", "Jylos"));
        assertEquals(List.of(), LegacyWindowsInstall.productCodes(
                "ERROR: The system was unable to find the specified registry key or value.", "Jylos"));
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
