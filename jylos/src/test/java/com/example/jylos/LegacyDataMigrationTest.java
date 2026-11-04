package com.example.jylos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.AppDirectories.Environment;
import com.example.jylos.AppDirectories.Layout;
import com.example.jylos.AppDirectories.Mode;
import com.example.jylos.AppDirectories.Os;
import com.example.jylos.LegacyDataMigration.Candidate;
import com.example.jylos.LegacyDataMigration.Report;

class LegacyDataMigrationTest {

    @TempDir
    Path tmp;

    private Layout target() {
        return new Layout(Mode.INSTALLED, tmp.resolve("new-data"), tmp.resolve("new-logs"), tmp.resolve("new-cache"));
    }

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void writePluginJar(Path jar, boolean jylosPlugin) throws IOException {
        Files.createDirectories(jar.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (jylosPlugin) {
            manifest.getMainAttributes().putValue("Plugin-Class", "com.example.Mine");
        }
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream ignored = new JarOutputStream(out, manifest)) {
            // manifest only
        }
    }

    @Test
    void aDedicatedOldFolderIsMovedWhole() throws IOException {
        Path old = tmp.resolve("Local/Jylos");
        write(old.resolve("data/workspaces.dat"), "ws");
        write(old.resolve("data/Notas/idea.md"), "# idea");
        write(old.resolve("backups/2026-10-01_10-00-00.db"), "b");
        write(old.resolve("themes/oscuro/theme.properties"), "id=oscuro");
        write(old.resolve("snippets/compacto.css"), "a{}");
        write(old.resolve("history/note/2026.md"), "v1");
        write(old.resolve("logs/app.log.0"), "log");

        Report report = LegacyDataMigration.migrate(List.of(new Candidate(old, true)), target(), Set.of());

        Path data = target().dataRoot();
        assertEquals("ws", Files.readString(data.resolve("data/workspaces.dat")));
        assertEquals("# idea", Files.readString(data.resolve("data/Notas/idea.md")));
        assertTrue(Files.exists(data.resolve("backups/2026-10-01_10-00-00.db")));
        assertTrue(Files.exists(data.resolve("themes/oscuro/theme.properties")));
        assertTrue(Files.exists(data.resolve("snippets/compacto.css")));
        assertTrue(Files.exists(data.resolve("history/note/2026.md")));
        assertTrue(Files.exists(target().logsDir().resolve("app.log.0")));
        assertFalse(Files.exists(old.resolve("data")), "the old data folder must be gone, not duplicated");
        assertFalse(report.messages().isEmpty(), "the migration must be reported in the log");
        assertEquals(Optional.empty(), report.leftoverDefaultVault());
    }

    @Test
    void inTheHomeFolderOnlyFilesJylosCreatedAreMoved() throws IOException {
        Path home = tmp.resolve("home");
        write(home.resolve("data/database.db"), "sqlite");
        write(home.resolve("data/database.db-wal"), "wal");
        write(home.resolve("data/mis-cosas.txt"), "mine");
        write(home.resolve("data/Notas/idea.md"), "# idea");
        write(home.resolve("backups/2026-10-01_10-00-00.db"), "b");
        write(home.resolve("backups/foto.jpg"), "jpg");
        write(home.resolve("logs/app.log.0"), "log");
        write(home.resolve("snippets/compacto.css"), "a{}");
        write(home.resolve("themes/oscuro/theme.properties"), "id=oscuro");
        write(home.resolve("themes/otra-cosa/readme.txt"), "not a theme");
        write(home.resolve("history/note/2026.md"), "v1");
        writePluginJar(home.resolve("plugins/Mine.jar"), true);
        writePluginJar(home.resolve("plugins/other-tool.jar"), false);

        Report report = LegacyDataMigration.migrate(List.of(new Candidate(home, false)), target(), Set.of());

        Path data = target().dataRoot();
        assertEquals("sqlite", Files.readString(data.resolve("data/database.db")));
        assertEquals("wal", Files.readString(data.resolve("data/database.db-wal")));
        assertTrue(Files.exists(data.resolve("backups/2026-10-01_10-00-00.db")));
        assertTrue(Files.exists(data.resolve("snippets/compacto.css")));
        assertTrue(Files.exists(data.resolve("themes/oscuro/theme.properties")));
        assertTrue(Files.exists(data.resolve("history/note/2026.md")));
        assertTrue(Files.exists(data.resolve("plugins/Mine.jar")));
        assertTrue(Files.exists(target().logsDir().resolve("app.log.0")));

        // Everything that is not unmistakably Jylos's stays exactly where it was.
        assertEquals("mine", Files.readString(home.resolve("data/mis-cosas.txt")));
        assertEquals("# idea", Files.readString(home.resolve("data/Notas/idea.md")));
        assertTrue(Files.exists(home.resolve("backups/foto.jpg")));
        assertTrue(Files.exists(home.resolve("themes/otra-cosa/readme.txt")));
        assertTrue(Files.exists(home.resolve("plugins/other-tool.jar")));
        assertFalse(Files.exists(data.resolve("data/mis-cosas.txt")));

        // Folders left empty are removed; the others are kept.
        assertFalse(Files.exists(home.resolve("logs")));
        assertFalse(Files.exists(home.resolve("snippets")));
        assertFalse(Files.exists(home.resolve("history")));
        assertTrue(Files.exists(home.resolve("data")));

        // Notes in an old default vault are reported so they can keep opening from there.
        assertEquals(Optional.of(home.resolve("data")), report.leftoverDefaultVault());
    }

    @Test
    void aFolderWithoutJylosFilesIsNotTouched() throws IOException {
        Path home = tmp.resolve("home");
        write(home.resolve("data/mis-cosas.txt"), "mine");
        write(home.resolve("logs/server.log"), "other app");
        write(home.resolve("backups/old.db"), "someone else's database");

        Report report = LegacyDataMigration.migrate(List.of(new Candidate(home, false)), target(), Set.of());

        assertTrue(report.messages().isEmpty(), "nothing to report: " + report.messages());
        assertTrue(Files.exists(home.resolve("backups/old.db")));
        assertTrue(Files.exists(home.resolve("logs/server.log")));
        assertFalse(Files.exists(target().dataRoot().resolve("backups/old.db")));
    }

    private static void touch(Path path, String isoDate) throws IOException {
        Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.from(
                java.time.LocalDate.parse(isoDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
    }

    /** Siblings of {@code path} named {@code <name>.before-migration-*}, i.e. what was set aside. */
    private static List<Path> setAside(Path path) throws IOException {
        try (var siblings = Files.list(path.getParent())) {
            return siblings.filter(p -> p.getFileName().toString()
                    .startsWith(path.getFileName() + ".before-migration-")).toList();
        }
    }

    @Test
    void existingDataAtTheDestinationIsNeverOverwritten() throws IOException {
        Path old = tmp.resolve("Local/Jylos");
        write(old.resolve("data/workspaces.dat"), "old");
        write(target().dataRoot().resolve("data/workspaces.dat"), "new");
        touch(old.resolve("data/workspaces.dat"), "2026-06-01");
        touch(target().dataRoot().resolve("data/workspaces.dat"), "2026-11-01");

        Report report = LegacyDataMigration.migrate(List.of(new Candidate(old, true)), target(), Set.of());

        assertEquals("new", Files.readString(target().dataRoot().resolve("data/workspaces.dat")));
        assertEquals("old", Files.readString(old.resolve("data/workspaces.dat")));
        assertTrue(report.messages().stream().anyMatch(m -> m.contains(old.resolve("data").toString())),
                "the kept-behind folder must be named in the log: " + report.messages());
    }

    @Test
    void aMoreRecentlyUsedOldFolderReplacesStaleDataWhichIsSetAsideNotDeleted() throws IOException {
        // The user's Windows: Roaming\Jylos holds a June database from an older run; the
        // install folder holds what Jylos has been using since.
        Path destinationData = target().dataRoot().resolve("data");
        write(destinationData.resolve("database.db"), "june");
        touch(destinationData.resolve("database.db"), "2026-06-12");
        Path old = tmp.resolve("Local/Jylos");
        write(old.resolve("data/workspaces.dat"), "current");
        touch(old.resolve("data/workspaces.dat"), "2026-11-04");

        Report report = LegacyDataMigration.migrate(List.of(new Candidate(old, true)), target(), Set.of());

        assertEquals("current", Files.readString(destinationData.resolve("workspaces.dat")));
        assertFalse(Files.exists(destinationData.resolve("database.db")), "the stale database is not in use any more");
        List<Path> aside = setAside(destinationData);
        assertEquals(1, aside.size(), "the stale folder must be kept under another name: " + aside);
        assertEquals("june", Files.readString(aside.get(0).resolve("database.db")));
        assertFalse(Files.exists(old.resolve("data")));
        assertTrue(report.messages().stream().anyMatch(m -> m.contains(aside.get(0).toString())),
                "the set-aside folder must be named in the log: " + report.messages());
    }

    @Test
    void aSqliteDatabaseMovesWithItsOwnJournalFilesNeverMixedWithAnotherOne() throws IOException {
        Path destinationData = target().dataRoot().resolve("data");
        write(destinationData.resolve("database.db"), "stale");
        write(destinationData.resolve("database.db-wal"), "stale wal");
        touch(destinationData.resolve("database.db"), "2026-06-12");
        touch(destinationData.resolve("database.db-wal"), "2026-06-12");
        Path home = tmp.resolve("home");
        write(home.resolve("data/database.db"), "current");
        touch(home.resolve("data/database.db"), "2026-11-04");

        LegacyDataMigration.migrate(List.of(new Candidate(home, false)), target(), Set.of());

        assertEquals("current", Files.readString(destinationData.resolve("database.db")));
        // A write-ahead log left from the other database would be replayed into this one.
        assertFalse(Files.exists(destinationData.resolve("database.db-wal")),
                "the stale database's WAL must not stay next to the moved database");
        assertEquals(1, setAside(destinationData.resolve("database.db")).size());
        assertEquals(1, setAside(destinationData.resolve("database.db-wal")).size());
    }

    @Test
    void anOlderDatabaseInTheHomeFolderStaysWithItsJournalFiles() throws IOException {
        Path destinationData = target().dataRoot().resolve("data");
        write(destinationData.resolve("database.db"), "current");
        touch(destinationData.resolve("database.db"), "2026-11-04");
        Path home = tmp.resolve("home");
        write(home.resolve("data/database.db"), "old");
        write(home.resolve("data/database.db-wal"), "old wal");
        touch(home.resolve("data/database.db"), "2026-06-12");
        touch(home.resolve("data/database.db-wal"), "2026-06-12");

        LegacyDataMigration.migrate(List.of(new Candidate(home, false)), target(), Set.of());

        assertEquals("current", Files.readString(destinationData.resolve("database.db")));
        assertFalse(Files.exists(destinationData.resolve("database.db-wal")), "the old WAL must not join the current database");
        assertEquals("old wal", Files.readString(home.resolve("data/database.db-wal")));
        assertEquals("old", Files.readString(home.resolve("data/database.db")));
    }

    @Test
    void theWindowsInstallFolderKeepsTheProgramItsPluginsAndTheNewLogs() throws IOException {
        // jpackage installs per-user apps in %LOCALAPPDATA%\Jylos, which is also the parent of
        // the new logs folder: only the user's data may leave it, on every start.
        Path local = tmp.resolve("Local");
        Path install = local.resolve("Jylos");
        write(install.resolve("Jylos.exe"), "exe");
        write(install.resolve("app/jylos-2.5.8-uber.jar"), "jar");
        write(install.resolve("runtime/release"), "jdk");
        writePluginJar(install.resolve("plugins/AIPlugin.jar"), true);
        write(install.resolve("logs/app.log.0"), "log");
        write(install.resolve("data/workspaces.dat"), "ws");
        Layout windows = new Layout(Mode.INSTALLED, tmp.resolve("Roaming/Jylos"), install.resolve("logs"),
                install.resolve("cache"));
        Environment env = new Environment(Os.WINDOWS, "Jylos", tmp, tmp, Map.of("LOCALAPPDATA", local.toString()),
                install.resolve("Jylos.exe").toString(), null, dir -> true, path -> false);
        List<Candidate> candidates = LegacyDataMigration.candidates(env, windows);
        assertEquals(List.of(new Candidate(install, true)), candidates, "the same folder, once");

        LegacyDataMigration.migrate(candidates, windows, Set.of("AIPlugin.jar"));
        Report second = LegacyDataMigration.migrate(candidates, windows, Set.of("AIPlugin.jar"));

        assertEquals("ws", Files.readString(windows.dataRoot().resolve("data/workspaces.dat")));
        assertTrue(Files.exists(install.resolve("Jylos.exe")));
        assertTrue(Files.exists(install.resolve("app/jylos-2.5.8-uber.jar")));
        assertTrue(Files.exists(install.resolve("runtime/release")));
        assertTrue(Files.exists(install.resolve("plugins/AIPlugin.jar")));
        assertEquals("log", Files.readString(install.resolve("logs/app.log.0")));
        assertFalse(Files.exists(windows.dataRoot().resolve("plugins/AIPlugin.jar")));
        assertTrue(second.messages().isEmpty(), "later starts find nothing to move: " + second.messages());
    }

    @Test
    void pluginsThatShipWithTheAppAreNotMigrated() throws IOException {
        Path old = tmp.resolve("Local/Jylos");
        write(old.resolve("logs/app.log"), "log");
        writePluginJar(old.resolve("plugins/AIPlugin.jar"), true);
        writePluginJar(old.resolve("plugins/Mine.jar"), true);
        write(old.resolve("plugins/.bundled-plugins-copied"), "marker");

        LegacyDataMigration.migrate(List.of(new Candidate(old, true)), target(), Set.of("AIPlugin.jar"));

        Path plugins = target().dataRoot().resolve("plugins");
        assertTrue(Files.exists(plugins.resolve("Mine.jar")));
        assertFalse(Files.exists(plugins.resolve("AIPlugin.jar")), "an old copy of a bundled plugin");
        assertFalse(Files.exists(plugins.resolve(".bundled-plugins-copied")),
                "the new folder must receive the current bundled plugins");
    }

    @Test
    void runningAgainDoesNothing() throws IOException {
        Path old = tmp.resolve("Local/Jylos");
        write(old.resolve("data/workspaces.dat"), "ws");
        write(old.resolve("logs/app.log"), "log");
        List<Candidate> candidates = List.of(new Candidate(old, true));
        LegacyDataMigration.migrate(candidates, target(), Set.of());

        Report second = LegacyDataMigration.migrate(candidates, target(), Set.of());

        assertTrue(second.messages().isEmpty(), "second run: " + second.messages());
        assertEquals("ws", Files.readString(target().dataRoot().resolve("data/workspaces.dat")));
    }

    @Test
    void candidatesPerPlatform() {
        Path home = Path.of("/home/ana");
        Environment windows = new Environment(Os.WINDOWS, "Jylos", home, home, Map.of("LOCALAPPDATA", "/l"),
                "/l/Programs/Jylos/Jylos.exe", null, dir -> true, path -> false);
        Environment linux = new Environment(Os.LINUX, "Jylos", home, home, Map.of(),
                "/opt/jylos/bin/Jylos", null, dir -> true, path -> false);
        Environment mac = new Environment(Os.MAC, "Jylos", home, home, Map.of(),
                "/Applications/Jylos.app/Contents/MacOS/Jylos", null, dir -> true, path -> false);
        Environment dev = new Environment(Os.LINUX, "Jylos", home, home, Map.of(), null, null,
                dir -> true, path -> false);

        assertEquals(List.of(new Candidate(Path.of("/l/Programs/Jylos"), true), new Candidate(Path.of("/l/Jylos"), true)),
                LegacyDataMigration.candidates(windows, AppDirectories.resolve(windows)));
        assertEquals(List.of(new Candidate(home.resolve(".config/Jylos"), true), new Candidate(home, false)),
                LegacyDataMigration.candidates(linux, AppDirectories.resolve(linux)));
        assertEquals(List.of(), LegacyDataMigration.candidates(mac, AppDirectories.resolve(mac)));
        assertEquals(List.of(), LegacyDataMigration.candidates(dev, AppDirectories.resolve(dev)),
                "only installed builds migrate");
    }
}
