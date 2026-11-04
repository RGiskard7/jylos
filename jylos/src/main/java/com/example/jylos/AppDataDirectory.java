package com.example.jylos;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Where Jylos keeps its files at runtime: the layout {@link AppDirectories} resolves for
 * the real machine, resolved once.
 *
 * <p>An installed build follows each platform's convention: user data in
 * {@code %APPDATA%\Jylos}, {@code ~/Library/Application Support/Jylos} or
 * {@code ~/.local/share/jylos}; logs and caches in their own system folders. Development
 * runs and the uber-JAR keep using the working directory, the Windows portable ZIP the
 * folder it was unpacked to, and {@code -Djylos.data.dir} overrides all of them.</p>
 */
public final class AppDataDirectory {

    private static AppDirectories.Layout layout;
    private static LegacyDataMigration.Report migrationReport =
            new LegacyDataMigration.Report(List.of(), Optional.empty());

    private AppDataDirectory() {
    }

    /** The resolved layout (resolved on first use). */
    public static synchronized AppDirectories.Layout layout() {
        if (layout == null) {
            layout = AppDirectories.resolve(currentEnvironment());
        }
        return layout;
    }

    /**
     * Moves files an older installed Jylos left in the wrong place (its install folder, the
     * home folder) into the current layout. Runs once, before anything creates the new
     * folders; does nothing outside an installed build.
     *
     * @return what happened, to log once the logger is configured
     */
    public static synchronized LegacyDataMigration.Report migrateLegacyData() {
        AppDirectories.Environment env = currentEnvironment();
        Set<String> bundled = bundledPluginJarNames();
        List<LegacyDataMigration.Candidate> candidates = LegacyDataMigration.candidates(env, layout());
        if (!candidates.isEmpty()) {
            migrationReport = LegacyDataMigration.migrate(candidates, layout(), bundled);
        }
        // Only after the migration, and before the logger opens its files: the old
        // per-user install's uninstaller deletes %LOCALAPPDATA%\Jylos, the logs' parent.
        List<String> uninstall = LegacyWindowsInstall.removeIfMigrated(env, layout(), bundled);
        if (!uninstall.isEmpty()) {
            List<String> messages = new java.util.ArrayList<>(migrationReport.messages());
            messages.addAll(uninstall);
            migrationReport = new LegacyDataMigration.Report(List.copyOf(messages),
                    migrationReport.leftoverDefaultVault());
        }
        return migrationReport;
    }

    /**
     * Tests only: forgets the resolved layout so the next call re-reads the environment,
     * such as a {@code jylos.data.dir} set by the test. A running app resolves it once.
     */
    public static synchronized void resetForTesting() {
        layout = null;
        migrationReport = new LegacyDataMigration.Report(List.of(), Optional.empty());
    }

    /** An old default vault whose notes stayed in an older location, if the migration found one. */
    public static Optional<Path> legacyDefaultVault() {
        return migrationReport.leftoverDefaultVault();
    }

    /**
     * Gets the base directory for user data: the folder holding data/, backups/, plugins/,
     * themes/, snippets/ and history/.
     *
     * @return absolute base directory used for runtime data
     */
    public static String getBaseDirectory() {
        return layout().dataRoot().toAbsolutePath().toString();
    }

    private static AppDirectories.Environment currentEnvironment() {
        return new AppDirectories.Environment(
                AppDirectories.Os.from(System.getProperty("os.name", "")),
                AppConfig.getAppName(),
                Path.of(System.getProperty("user.home", ".")),
                Path.of(System.getProperty("user.dir", ".")).toAbsolutePath(),
                System.getenv(),
                System.getProperty("jpackage.app-path"),
                System.getProperty("jylos.data.dir"),
                path -> canWriteToDirectory(path.toFile()),
                Files::exists);
    }

    /** Names of the plugin JARs that ship with this installation, next to the app JAR. */
    private static Set<String> bundledPluginJarNames() {
        Set<String> names = new HashSet<>();
        Path appDir;
        try {
            Path location = Path.of(AppDataDirectory.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            appDir = Files.isRegularFile(location) ? location.getParent() : location;
        } catch (URISyntaxException | RuntimeException e) {
            return names;
        }
        if (appDir == null) {
            return names;
        }
        // jpackage --app-content lands next to the app folder (Windows: <install>\plugins,
        // Linux: /opt/jylos/lib/plugins); older layouts used the app folder itself.
        List<Path> folders = appDir.getParent() == null
                ? List.of(appDir, appDir.resolve("plugins"))
                : List.of(appDir, appDir.resolve("plugins"), appDir.getParent().resolve("plugins"));
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> jars = Files.list(folder)) {
                jars.map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase().endsWith(".jar") && !n.contains("jylos") && !n.contains("uber"))
                        .forEach(names::add);
            } catch (java.io.IOException ignored) {
                // An unreadable folder only means fewer names to exclude.
            }
        }
        return names;
    }

    /**
     * Checks if we can write to a directory (exists and writable, or can be
     * created).
     */
    private static boolean canWriteToDirectory(File dir) {
        try {
            if (dir.exists()) {
                return dir.isDirectory() && dir.canWrite();
            }
            // Try to create and delete to test write permission
            if (dir.mkdirs()) {
                dir.delete();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the data directory path (baseDir/data).
     *
     * @return absolute path to the data directory
     */
    public static String getDataDirectory() {
        return new File(getBaseDirectory(), "data").getAbsolutePath();
    }

    /**
     * Gets the logs directory: the system's log folder for an installed build, baseDir/logs otherwise.
     *
     * @return absolute path to the logs directory
     */
    public static String getLogsDirectory() {
        return layout().logsDir().toAbsolutePath().toString();
    }

    /**
     * Gets the backups directory path (baseDir/backups).
     *
     * @return absolute path to the backups directory
     */
    public static String getBackupsDirectory() {
        return new File(getBaseDirectory(), "backups").getAbsolutePath();
    }

    /**
     * Gets the CSS snippets directory path (baseDir/snippets).
     *
     * @return absolute path to the snippets directory
     */
    public static String getSnippetsDirectory() {
        return new File(getBaseDirectory(), "snippets").getAbsolutePath();
    }

    /**
     * REMOVABLE: in-app updater — see {@link com.example.jylos.service.UpdateInstaller}'s
     * class docs ("Removing this later") for the removal checklist; this method is
     * on that list. Directory path (cacheDir/updates) where downloaded update
     * installers are staged before the user runs them. Not created eagerly by
     * {@link #ensureDirectoriesExist()} — only needed while an update is in flight,
     * so {@code UpdateInstaller} creates it on demand.
     *
     * @return absolute path to the updates directory
     */
    public static String getUpdatesDirectory() {
        return layout().cacheDir().resolve("updates").toAbsolutePath().toString();
    }

    /**
     * Ensures runtime directories exist: data, logs, backups, plugins, themes, snippets.
     * Invoked from {@link Main} static initializer and at startup.
     *
     * @return true when required runtime directories exist and are writable
     */
    public static boolean ensureDirectoriesExist() {
        try {
            File dataDir = new File(getDataDirectory());
            File logsDir = new File(getLogsDirectory());
            File pluginsDir = new File(getBaseDirectory(), "plugins");
            File backupsDir = new File(getBackupsDirectory());
            File themesDir = new File(getBaseDirectory(), "themes");
            File snippetsDir = new File(getSnippetsDirectory());

            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            if (!pluginsDir.exists()) {
                pluginsDir.mkdirs();
            }
            if (!backupsDir.exists()) {
                backupsDir.mkdirs();
            }
            if (!themesDir.exists()) {
                themesDir.mkdirs();
            }
            // Snippets are optional UI tweaks; create the folder best-effort but do not
            // fail startup if it cannot be made.
            if (!snippetsDir.exists()) {
                snippetsDir.mkdirs();
            }

            return dataDir.exists() && dataDir.canWrite() &&
                    logsDir.exists() && logsDir.canWrite() &&
                    backupsDir.exists() && backupsDir.canWrite() &&
                    themesDir.exists() && themesDir.canWrite();
        } catch (Exception e) {
            return false;
        }
    }
}
