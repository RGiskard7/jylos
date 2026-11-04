package com.example.jylos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.jylos.AppDirectories.Environment;
import com.example.jylos.AppDirectories.Layout;
import com.example.jylos.AppDirectories.Mode;
import com.example.jylos.AppDirectories.Os;

class AppDirectoriesTest {

    private static final Path HOME = Path.of("/home/ana");
    private static final Path CWD = Path.of("/home/ana/projects");

    /** The working directory is writable: before the fix, every launch mode used it. */
    private static Environment env(Os os, Map<String, String> vars, String appPath, String override,
            Set<Path> existing) {
        return new Environment(os, "Jylos", HOME, CWD, vars, appPath, override,
                dir -> true, existing::contains);
    }

    private static void assertLayout(Layout layout, Mode mode, String data, String logs, String cache) {
        assertEquals(mode, layout.mode(), "mode");
        assertEquals(Path.of(data), layout.dataRoot(), "data root");
        assertEquals(Path.of(logs), layout.logsDir(), "logs");
        assertEquals(Path.of(cache), layout.cacheDir(), "cache");
    }

    @Test
    void installedOnWindowsUsesRoamingForDataAndLocalForLogsAndCache() {
        Layout layout = AppDirectories.resolve(env(Os.WINDOWS,
                Map.of("APPDATA", "/u/AppData/Roaming", "LOCALAPPDATA", "/u/AppData/Local"),
                "/u/AppData/Local/Programs/Jylos/Jylos.exe", null, Set.of()));

        assertLayout(layout, Mode.INSTALLED, "/u/AppData/Roaming/Jylos",
                "/u/AppData/Local/Jylos/logs", "/u/AppData/Local/Jylos/cache");
    }

    @Test
    void installedOnWindowsWithoutAppDataVariablesFallsBackUnderTheHome() {
        Layout layout = AppDirectories.resolve(env(Os.WINDOWS, Map.of(),
                "/x/Jylos/Jylos.exe", null, Set.of()));

        assertLayout(layout, Mode.INSTALLED, "/home/ana/AppData/Roaming/Jylos",
                "/home/ana/AppData/Local/Jylos/logs", "/home/ana/AppData/Local/Jylos/cache");
    }

    @Test
    void installedOnMacUsesLibraryFolders() {
        Layout layout = AppDirectories.resolve(env(Os.MAC, Map.of(),
                "/Applications/Jylos.app/Contents/MacOS/Jylos", null, Set.of()));

        assertLayout(layout, Mode.INSTALLED, "/home/ana/Library/Application Support/Jylos",
                "/home/ana/Library/Logs/Jylos", "/home/ana/Library/Caches/Jylos");
    }

    @Test
    void installedOnLinuxUsesXdgDefaults() {
        Layout layout = AppDirectories.resolve(env(Os.LINUX, Map.of(), "/opt/jylos/bin/Jylos", null, Set.of()));

        assertLayout(layout, Mode.INSTALLED, "/home/ana/.local/share/jylos",
                "/home/ana/.local/state/jylos/logs", "/home/ana/.cache/jylos");
    }

    @Test
    void installedOnLinuxHonoursAbsoluteXdgVariablesAndIgnoresRelativeOnes() {
        Layout absolute = AppDirectories.resolve(env(Os.LINUX,
                Map.of("XDG_DATA_HOME", "/d", "XDG_STATE_HOME", "/s", "XDG_CACHE_HOME", "/c"),
                "/opt/jylos/bin/Jylos", null, Set.of()));
        assertLayout(absolute, Mode.INSTALLED, "/d/jylos", "/s/jylos/logs", "/c/jylos");

        // The XDG spec says relative values are invalid and must be ignored.
        Layout relative = AppDirectories.resolve(env(Os.LINUX,
                Map.of("XDG_DATA_HOME", "d", "XDG_STATE_HOME", "s", "XDG_CACHE_HOME", "c"),
                "/opt/jylos/bin/Jylos", null, Set.of()));
        assertLayout(relative, Mode.INSTALLED, "/home/ana/.local/share/jylos",
                "/home/ana/.local/state/jylos/logs", "/home/ana/.cache/jylos");
    }

    @Test
    void windowsPortableKeepsEverythingNextToTheLauncher() {
        Layout layout = AppDirectories.resolve(env(Os.WINDOWS, Map.of("APPDATA", "/r", "LOCALAPPDATA", "/l"),
                "/usb/Jylos/Jylos.exe", null, Set.of(Path.of("/usb/Jylos/jylos.portable"))));

        assertLayout(layout, Mode.PORTABLE, "/usb/Jylos", "/usb/Jylos/logs", "/usb/Jylos");
    }

    @Test
    void overrideWinsOverAnInstalledLayout() {
        Layout layout = AppDirectories.resolve(env(Os.LINUX, Map.of(), "/opt/jylos/bin/Jylos", "/data/jy",
                Set.of()));

        assertLayout(layout, Mode.OVERRIDE, "/data/jy", "/data/jy/logs", "/data/jy");
    }

    @Test
    void developmentRunUsesTheWorkingDirectoryWhenWritable() {
        Layout layout = AppDirectories.resolve(env(Os.LINUX, Map.of(), null, null, Set.of()));

        assertLayout(layout, Mode.DEVELOPMENT, "/home/ana/projects", "/home/ana/projects/logs",
                "/home/ana/projects");
    }

    @Test
    void developmentRunFallsBackToThePlatformFoldersWhenTheWorkingDirectoryIsReadOnly() {
        // An uber-JAR double-clicked in Finder starts in "/", which is not writable.
        Environment env = new Environment(Os.MAC, "Jylos", HOME, Path.of("/"), Map.of(), null, null,
                dir -> false, path -> false);

        assertLayout(AppDirectories.resolve(env), Mode.DEVELOPMENT,
                "/home/ana/Library/Application Support/Jylos", "/home/ana/Library/Logs/Jylos",
                "/home/ana/Library/Caches/Jylos");
    }
}
