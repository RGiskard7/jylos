package com.example.jylos;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import com.example.jylos.AppDirectories.Environment;
import com.example.jylos.AppDirectories.Layout;
import com.example.jylos.AppDirectories.Mode;

/**
 * Moves the files an installed Jylos used to keep in the wrong place into the folders
 * {@link AppDirectories} now chooses.
 *
 * <p>Before, an installed Jylos used whatever folder it was launched from when it could
 * write there: the install folder on Windows ({@code AppData\Local\Jylos}), the home
 * folder on Linux. Only files Jylos itself creates are moved, nothing is overwritten, and
 * whatever is not recognised stays where it is: a home folder's {@code data/} may well
 * hold the user's own files.</p>
 */
public final class LegacyDataMigration {

    /** Left in an old folder whose contents had to be copied rather than moved. */
    static final String MIGRATED_MARKER = ".jylos-migrated";

    /** A SQLite database and its journal files: they only ever move or stay together. */
    private static final List<String> DATABASE_FILES = List.of(
            "database.db", "database.db-wal", "database.db-shm", "database.db-journal");
    private static final String BUNDLED_PLUGINS_MARKER = ".bundled-plugins-copied";

    /**
     * A place an older Jylos may have used.
     *
     * @param dedicated true when the folder belonged to Jylos alone (its install folder, its
     *                  old app-data folder); false for a folder shared with other things,
     *                  such as the user's home, where only files Jylos creates are touched
     */
    public record Candidate(Path base, boolean dedicated) {
    }

    /**
     * @param messages             what was moved, copied or left behind, for the log
     * @param leftoverDefaultVault an old default vault with notes that stayed where it was
     */
    public record Report(List<String> messages, Optional<Path> leftoverDefaultVault) {
    }

    private LegacyDataMigration() {
    }

    /** The folders an older installed Jylos may have written to; none for other modes. */
    public static List<Candidate> candidates(Environment env, Layout layout) {
        if (layout.mode() != Mode.INSTALLED) {
            return List.of();
        }
        Set<Candidate> candidates = new LinkedHashSet<>();
        Path home = env.userHome();
        switch (env.os()) {
            case WINDOWS -> {
                // Older builds kept their data in the per-user install folder, but upgrading
                // runs the old uninstaller first and it deletes that folder: by the time this
                // version starts there is nothing left to move (docs/PACKAGING.md).
            }
            case LINUX -> {
                // The folder older builds documented (but only used when the working
                // directory was read-only), then the home folder desktop launchers start in.
                Path config = AppDirectories.envDir(env, "XDG_CONFIG_HOME", home.resolve(".config"));
                candidates.add(new Candidate(config.resolve(env.appName()), true));
                candidates.add(new Candidate(home, false));
            }
            case MAC -> {
                // Launched from Finder the working directory is "/", which is read-only, so
                // older builds already used ~/Library/Application Support/Jylos.
            }
        }
        candidates.removeIf(c -> c.base().equals(layout.dataRoot()));
        return List.copyOf(candidates);
    }

    public static Report migrate(List<Candidate> candidates, Layout target, Set<String> bundledPluginJars) {
        List<String> messages = new ArrayList<>();
        Path leftoverVault = null;
        for (Candidate candidate : candidates) {
            Path base = candidate.base();
            if (!isJylosFolder(base)) {
                continue;
            }
            Migration migration = new Migration(messages);
            if (candidate.dedicated()) {
                for (String name : List.of("data", "backups", "themes", "snippets", "history")) {
                    migration.moveFolder(base.resolve(name), target.dataRoot().resolve(name));
                }
                if (!base.resolve("logs").equals(target.logsDir())) {
                    migration.moveFolder(base.resolve("logs"), target.logsDir());
                }
            } else {
                Path data = base.resolve("data");
                migration.moveDatabase(data, target.dataRoot().resolve("data"));
                migration.moveFile(data.resolve("workspaces.dat"), target.dataRoot().resolve("data"));
                migration.moveMatching(base.resolve("backups"), target.dataRoot().resolve("backups"),
                        path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".db"));
                migration.moveMatching(base.resolve("snippets"), target.dataRoot().resolve("snippets"),
                        path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".css"));
                migration.moveMatching(base.resolve("logs"), target.logsDir(),
                        path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith("app.log"));
                migration.moveMatching(base.resolve("themes"), target.dataRoot().resolve("themes"),
                        path -> Files.isRegularFile(path.resolve("theme.properties")));
                migration.moveMatching(base.resolve("history"), target.dataRoot().resolve("history"),
                        LegacyDataMigration::isHistoryFolder);
            }
            migration.moveMatching(base.resolve("plugins"), target.dataRoot().resolve("plugins"),
                    path -> isUserPlugin(path, bundledPluginJars, candidate.dedicated()));
            for (String name : List.of("data", "backups", "logs", "snippets", "themes", "history", "plugins")) {
                deleteIfEmpty(base.resolve(name));
            }
            if (migration.copiedInsteadOfMoved) {
                migration.markCopied(base, target);
            }
            if (leftoverVault == null && containsNotes(base.resolve("data"))) {
                leftoverVault = base.resolve("data");
                messages.add("Notes left in " + leftoverVault + " (not moved: the folder holds other files"
                        + " or the destination was not empty)");
            }
        }
        return new Report(List.copyOf(messages), Optional.ofNullable(leftoverVault));
    }

    /** A folder counts as an old Jylos one only if it holds a file Jylos itself creates. */
    static boolean isJylosFolder(Path base) {
        if (!Files.isDirectory(base) || Files.exists(base.resolve(MIGRATED_MARKER))) {
            return false;
        }
        Path data = base.resolve("data");
        if (Files.isRegularFile(data.resolve("database.db")) || Files.isRegularFile(data.resolve("workspaces.dat"))) {
            return true;
        }
        Path logs = base.resolve("logs");
        if (!Files.isDirectory(logs)) {
            return false;
        }
        try (Stream<Path> files = Files.list(logs)) {
            return files.anyMatch(f -> f.getFileName().toString().startsWith("app.log"));
        } catch (IOException e) {
            return false;
        }
    }

    /** NoteHistoryService keeps one folder per note holding {@code <timestamp>.md} files. */
    private static boolean isHistoryFolder(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(path)) {
            return entries.allMatch(e -> Files.isRegularFile(e) && e.getFileName().toString().endsWith(".md"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * A plugin the user added: never a copy of one that ships with the app (it would load
     * next to the current one), and, in a shared folder, only a real Jylos plugin JAR.
     */
    private static boolean isUserPlugin(Path path, Set<String> bundledPluginJars, boolean dedicated) {
        String name = path.getFileName().toString();
        if (!Files.isRegularFile(path) || !name.toLowerCase().endsWith(".jar") || bundledPluginJars.contains(name)) {
            return false;
        }
        if (dedicated) {
            return true;
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getManifest() != null && jar.getManifest().getMainAttributes().getValue("Plugin-Class") != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsNotes(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.anyMatch(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".md"));
        } catch (IOException e) {
            return false;
        }
    }

    /** Last-modified time of a file, or of the newest file inside a folder. */
    private static long newestModification(Path path) {
        try (Stream<Path> tree = Files.walk(path)) {
            return tree.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.getLastModifiedTime(f).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            }).max().orElse(0L);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void deleteIfEmpty(Path folder) {
        if (isEmptyFolder(folder)) {
            try {
                Files.delete(folder);
            } catch (IOException ignored) {
                // Only ever an empty folder: leaving it is harmless.
            }
        }
    }

    private static boolean isEmptyFolder(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /** The moves of one old folder, collecting what happened. */
    private static final class Migration {
        private final List<String> messages;
        private boolean copiedInsteadOfMoved;

        Migration(List<String> messages) {
            this.messages = messages;
        }

        void moveFolder(Path source, Path destination) {
            if (!Files.isDirectory(source) || isEmptyFolder(source)) {
                return;
            }
            if (makeRoom(source, destination)) {
                transfer(source, destination);
            }
        }

        void moveFile(Path source, Path destinationFolder) {
            if (!Files.isRegularFile(source)) {
                return;
            }
            Path destination = destinationFolder.resolve(source.getFileName().toString());
            if (makeRoom(source, destination)) {
                transfer(source, destination);
            }
        }

        /**
         * Moves {@code database.db} and its journal files as one unit, decided by the
         * database file: a write-ahead log replayed into a different database corrupts it.
         */
        void moveDatabase(Path sourceFolder, Path destinationFolder) {
            Path sourceDb = sourceFolder.resolve(DATABASE_FILES.get(0));
            if (!Files.isRegularFile(sourceDb)) {
                return;
            }
            Path destinationDb = destinationFolder.resolve(DATABASE_FILES.get(0));
            if (Files.exists(destinationDb) && newestModification(sourceDb) <= newestModification(destinationDb)) {
                messages.add("Kept " + sourceDb + " and its journal files: " + destinationDb + " was used more recently");
                return;
            }
            for (String name : DATABASE_FILES) {
                Path destination = destinationFolder.resolve(name);
                if (Files.exists(destination)) {
                    setAside(destination);
                }
            }
            for (String name : DATABASE_FILES) {
                Path source = sourceFolder.resolve(name);
                if (Files.isRegularFile(source)) {
                    transfer(source, destinationFolder.resolve(name));
                }
            }
        }

        /**
         * Whether {@code source} may take {@code destination}'s place. Free or empty: yes.
         * Otherwise the one used more recently wins; the other is kept, never deleted: an
         * older destination is renamed aside, an older source stays where it was.
         */
        private boolean makeRoom(Path source, Path destination) {
            if (!Files.exists(destination) || isEmptyFolder(destination)) {
                return true;
            }
            if (newestModification(source) > newestModification(destination)) {
                return setAside(destination);
            }
            messages.add("Kept " + source + ": " + destination + " was used more recently");
            return false;
        }

        /** Renames {@code path} to {@code <name>.before-migration-<date>}. */
        private boolean setAside(Path path) {
            String base = path.getFileName() + ".before-migration-" + java.time.LocalDate.now();
            Path aside = path.resolveSibling(base);
            for (int i = 2; Files.exists(aside); i++) {
                aside = path.resolveSibling(base + "-" + i);
            }
            try {
                Files.move(path, aside);
                messages.add("Set aside older " + path + " as " + aside);
                return true;
            } catch (IOException e) {
                messages.add("Kept " + path + " (could not set it aside: " + e.getMessage() + ")");
                return false;
            }
        }

        void moveMatching(Path sourceFolder, Path destinationFolder, Predicate<Path> belongsToJylos) {
            if (!Files.isDirectory(sourceFolder)) {
                return;
            }
            List<Path> entries;
            try (Stream<Path> listing = Files.list(sourceFolder)) {
                entries = listing.filter(e -> !e.getFileName().toString().equals(BUNDLED_PLUGINS_MARKER))
                        .filter(belongsToJylos).toList();
            } catch (IOException e) {
                messages.add("Could not read " + sourceFolder + ": " + e.getMessage());
                return;
            }
            for (Path entry : entries) {
                Path destination = destinationFolder.resolve(entry.getFileName().toString());
                if (makeRoom(entry, destination)) {
                    transfer(entry, destination);
                }
            }
        }

        /**
         * Moves {@code source} to {@code destination}, which must not hold files. Within one
         * disk this is a rename; when a folder cannot be renamed (another disk), it is
         * copied and the original kept.
         */
        private void transfer(Path source, Path destination) {
            try {
                Files.createDirectories(destination.getParent());
                if (isEmptyFolder(destination)) {
                    Files.delete(destination);
                }
                Files.move(source, destination);
                messages.add("Moved " + source + " to " + destination);
            } catch (DirectoryNotEmptyException e) {
                copyTree(source, destination);
            } catch (IOException e) {
                messages.add("Could not move " + source + " to " + destination + ": " + e.getMessage());
            }
        }

        private void copyTree(Path source, Path destination) {
            try (Stream<Path> tree = Files.walk(source)) {
                for (Path path : (Iterable<Path>) tree::iterator) {
                    Path copy = destination.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(path, copy);
                    }
                }
                copiedInsteadOfMoved = true;
                messages.add("Copied " + source + " to " + destination + " (original kept)");
            } catch (IOException e) {
                messages.add("Could not copy " + source + " to " + destination + ": " + e.getMessage());
            }
        }

        /** So the copied originals are not migrated again on every start. */
        void markCopied(Path base, Layout target) {
            try {
                Files.writeString(base.resolve(MIGRATED_MARKER),
                        "Jylos copied its files from here to " + target.dataRoot() + "\n");
            } catch (IOException e) {
                messages.add("Could not mark " + base + " as migrated: " + e.getMessage());
            }
        }
    }
}
