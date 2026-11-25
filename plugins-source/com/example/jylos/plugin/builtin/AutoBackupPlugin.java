package com.example.jylos.plugin.builtin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Stream;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginI18n;
import com.example.jylos.service.DatabaseBackupService;
import com.example.jylos.service.NoteService;
import com.example.jylos.util.AttachmentType;

import javafx.application.Platform;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * Auto Backup Plugin - backs up the notes to a folder.
 *
 * <p>This plugin provides:</p>
 * <ul>
 *   <li>A copy of every note, whole (see {@link NoteBackup}): in a folder vault, its files as
 *   they are</li>
 *   <li>A copy of the database file</li>
 *   <li>Both together, with a report</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class AutoBackupPlugin implements Plugin {
    
    private static final String ID = "auto-backup";
    private static final String NAME = "Auto Backup";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Backup notes to files and export database";
    private static final String AUTHOR = "Edu Díaz";
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private PluginContext context;

    // Loaded once, lazily — getDescription() can be called by the Plugin Manager before
    // initialize() ever runs, so this cannot wait for that.
    private static ResourceBundle bundle;

    private static String tr(String key, String fallback) {
        if (bundle == null) {
            bundle = PluginI18n.bundle(AutoBackupPlugin.class);
        }
        return PluginI18n.tr(bundle, key, fallback);
    }

    @Override
    public String getId() { return ID; }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDescription() { return tr("autobackup.plugin.description", DESCRIPTION); }

    @Override
    public String getAuthor() { return AUTHOR; }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;

        // Register commands
        context.registerCommand(
            "Backup: Export All Notes",
            tr("autobackup.command.exportAll.description", "Export all notes as Markdown files to a folder"),
            "Ctrl+Shift+B",
            this::exportAllNotes
        );

        context.registerCommand(
            "Backup: Database Backup",
            tr("autobackup.command.database.description", "Create a backup copy of the database file"),
            null,
            this::backupDatabase
        );

        context.registerCommand(
            "Backup: Full Backup",
            tr("autobackup.command.full.description", "Export notes and backup database"),
            null,
            this::fullBackup
        );

        context.registerCommand(
            "Backup: Export Current Note",
            tr("autobackup.command.exportCurrent.description", "Export the current note as a Markdown file"),
            null,
            this::exportCurrentNote
        );

        // Register menu items (dynamic plugin menu)
        context.registerMenuItem(tr("menuCategory.utilities", "Utilities"), tr("autobackup.menu.exportAll", "Export All Notes..."), "Ctrl+Shift+B", this::exportAllNotes);
        context.registerMenuItem(tr("menuCategory.utilities", "Utilities"), tr("autobackup.menu.exportCurrent", "Export Current Note..."), this::exportCurrentNote);
        context.addMenuSeparator(tr("menuCategory.utilities", "Utilities"));
        context.registerMenuItem(tr("menuCategory.utilities", "Utilities"), tr("autobackup.menu.database", "Database Backup..."), this::backupDatabase);
        context.registerMenuItem(tr("menuCategory.utilities", "Utilities"), tr("autobackup.menu.full", "Full Backup..."), this::fullBackup);
        
        context.log("Auto Backup Plugin initialized");
    }
    
    @Override
    public void shutdown() {
        context.unregisterCommand("Backup: Export All Notes");
        context.unregisterCommand("Backup: Database Backup");
        context.unregisterCommand("Backup: Full Backup");
        context.unregisterCommand("Backup: Export Current Note");
        context.log("Auto Backup Plugin shutdown");
    }
    
    /** Work done off the JavaFX thread: a big vault takes a while to copy. */
    @FunctionalInterface
    private interface BackupWork {
        void run() throws IOException;
    }

    private void inBackground(String name, BackupWork work) {
        Thread thread = new Thread(() -> {
            try {
                work.run();
            } catch (IOException | RuntimeException e) {
                context.logError("Backup failed", e);
                context.showError(tr("autobackup.error.title", "Backup Error"),
                        tr("autobackup.error.failed", "The backup could not be completed: %s").formatted(e.getMessage()));
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static File chooseFolder(String titleKey, String fallback) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(tr(titleKey, fallback));
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        return chooser.showDialog(null);
    }

    /** A line saying how many private notes were left to the database copy, when there were any. */
    private static String privateLine(NoteBackup.Result result) {
        return result.skippedPrivate() == 0 ? "" : "\n" + tr("autobackup.skippedPrivate",
                "Private notes not written in plain text (the database backup keeps them encrypted): %d")
                .formatted(result.skippedPrivate());
    }

    /**
     * Copies every note, whole, to a new folder (see {@link NoteBackup}): in a folder vault its
     * files as they are, folders and attachments included.
     */
    private void exportAllNotes() {
        Platform.runLater(() -> {
            File selectedDir = chooseFolder("autobackup.chooser.folder.title", "Select Backup Folder");
            if (selectedDir == null) {
                return;
            }
            Path backupDir = selectedDir.toPath().resolve("jylos-backup-" + LocalDateTime.now().format(TIMESTAMP_FORMAT));
            inBackground("backup-notes", () -> {
                NoteBackup.Result result = NoteBackup.backupAll(context.getNoteService(), backupDir);
                String message = tr("autobackup.exportAll.body",
                        "Backup completed!\n\nLocation: %s\nNotes exported: %d\nFailed: %d")
                        .formatted(backupDir, result.written(), result.failed()) + privateLine(result);
                context.showInfo(tr("autobackup.exportAll.title", "Backup Complete"),
                        tr("autobackup.exportAll.header", "Notes Exported"), message);
                context.log("Exported " + result.written() + " notes to " + backupDir);
            });
        });
    }

    /** Backs up the database file. */
    private void backupDatabase() {
        Platform.runLater(() -> {
            File selectedDir = chooseFolder("autobackup.chooser.location.title", "Select Backup Location");
            if (selectedDir == null) {
                return;
            }
            File sourceDb = DatabaseBackupService.databaseFile();
            if (!sourceDb.exists()) {
                context.showError(tr("autobackup.error.title", "Backup Error"),
                        tr("autobackup.error.dbNotFound", "Database file not found: %s").formatted(sourceDb.getPath()));
                return;
            }
            File targetDb = new File(selectedDir, "database-backup-" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".db");
            inBackground("backup-database", () -> {
                if (!DatabaseBackupService.backupDatabaseFile(sourceDb, targetDb)) {
                    context.showError(tr("autobackup.error.title", "Backup Error"),
                            tr("autobackup.error.dbFailed", "Failed to backup database."));
                    return;
                }
                context.showInfo(tr("autobackup.database.title", "Database Backup"),
                        tr("autobackup.database.header", "Backup Created"),
                        tr("autobackup.database.body", "Database backed up to:\n%s").formatted(targetDb.getPath()));
                context.log("Database backed up to " + targetDb.getPath());
            });
        });
    }

    /** Every note (see {@link #exportAllNotes}) and the database, with a report of what was done. */
    private void fullBackup() {
        Platform.runLater(() -> {
            File selectedDir = chooseFolder("autobackup.chooser.folder.title", "Select Backup Folder");
            if (selectedDir == null) {
                return;
            }
            Path backupDir = selectedDir.toPath().resolve("jylos-full-backup-" + LocalDateTime.now().format(TIMESTAMP_FORMAT));
            inBackground("backup-full", () -> {
                NoteBackup.Result result = NoteBackup.backupAll(context.getNoteService(), backupDir.resolve("notes"));

                File sourceDb = DatabaseBackupService.databaseFile();
                String database;
                if (!sourceDb.exists()) {
                    database = tr("autobackup.report.database.notFound", "Database: not found");
                } else if (DatabaseBackupService.backupDatabaseFile(sourceDb, backupDir.resolve("database.db").toFile())) {
                    database = tr("autobackup.report.database.backedUp", "Database: backed up");
                } else {
                    database = tr("autobackup.report.database.failed", "Database: backup failed");
                }

                String report = tr("autobackup.report.title", "Full Backup Report") + "\n"
                        + "==================\n\n"
                        + tr("autobackup.report.location", "Location:") + " " + backupDir + "\n\n"
                        + tr("autobackup.report.notesExported", "Notes exported:") + " " + result.written() + "\n"
                        + database + privateLine(result) + "\n\n"
                        + tr("autobackup.report.createdAt", "Backup created:") + " "
                        + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n";
                Files.writeString(backupDir.resolve("backup-report.txt"), report, StandardCharsets.UTF_8);

                context.showInfo(tr("autobackup.full.title", "Full Backup"), tr("autobackup.full.header", "Backup Complete"),
                        tr("autobackup.full.body", "Full backup created at:\n%s\n\nNotes: %d\n%s")
                                .formatted(backupDir, result.written(), database) + privateLine(result));
                context.log("Full backup completed: " + backupDir);
            });
        });
    }

    /** Exports one note, chosen from the list, whole (see {@link NoteBackup#exportNote}). */
    private void exportCurrentNote() {
        List<Note> allNotes = context.getNoteService().getAllNotes();

        if (allNotes.isEmpty()) {
            context.showInfo(tr("autobackup.exportNote.title", "Export Note"), tr("autobackup.exportNote.noNotes.header", "No Notes"),
                    tr("autobackup.exportNote.noNotes.body", "Create a note first."));
            return;
        }

        Platform.runLater(() -> {
            ChoiceDialog<Note> dialog = new ChoiceDialog<>(allNotes.get(0), allNotes);
            dialog.setTitle(tr("autobackup.exportNote.title", "Export Note"));
            dialog.setHeaderText(tr("autobackup.exportNote.header", "Select a note to export:"));
            dialog.setContentText(tr("autobackup.exportNote.content", "Note:"));

            context.showThemed(dialog).ifPresent(note -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle(tr("autobackup.saveNote.title", "Save Note As"));
                // An attachment or canvas keeps its own name (its title has the extension).
                fileChooser.setInitialFileName(AttachmentType.isAttachment(note.getId())
                        ? NoteBackup.sanitize(note.getTitle())
                        : NoteBackup.sanitize(note.getTitle()) + ".md");
                fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Markdown", "*.md"),
                    new FileChooser.ExtensionFilter(tr("autobackup.filter.text", "Text"), "*.txt"),
                    new FileChooser.ExtensionFilter(tr("autobackup.filter.all", "All files"), "*.*")
                );

                File file = fileChooser.showSaveDialog(null);
                if (file == null) {
                    return;
                }
                inBackground("export-note", () -> {
                    if (!NoteBackup.exportNote(context.getNoteService(), note, file.toPath())) {
                        context.showError(tr("autobackup.exportError.title", "Export Error"),
                                tr("autobackup.exportNote.private",
                                        "A private note is not exported in plain text: the database backup keeps it encrypted."));
                        return;
                    }
                    context.showInfo(tr("autobackup.exportComplete.title", "Export Complete"),
                            tr("autobackup.exportComplete.header", "Note Exported"),
                            tr("autobackup.exportComplete.body", "Note saved to:\n%s").formatted(file.getPath()));
                    context.log("Exported note to " + file.getPath());
                });
            });
        });
    }

    /**
     * Copies the notes of the open storage to a folder, whole and as they are stored:
     * <ul>
     * <li><b>Folder vault</b>: each listed file (note, attachment, canvas) byte for byte at its
     * path in the vault, with its modified time, plus the vault's {@code .jylos} folder (marks,
     * settings and the master password's key material, without which a private note cannot be
     * decrypted after a restore). A private note stays encrypted, as it is on disk.</li>
     * <li><b>SQLite</b>: each note's whole text with its properties, as a folder vault writes it
     * ({@link NoteService#asMarkdownFile}), named after its title (a repeated title gets
     * " (2)"…). A private note is not written: its decrypted text must not land in a plain file,
     * and the database copy keeps it encrypted.</li>
     * </ul>
     *
     * @since 2.7.0
     */
    static final class NoteBackup {

        /**
         * @param written        notes (and vault files) written
         * @param failed         notes that could not be read or written
         * @param skippedPrivate private notes of a SQLite storage, left to the database copy
         */
        record Result(int written, int failed, int skippedPrivate) {
        }

        private NoteBackup() {
        }

        /** Writes every note of {@code notes} into {@code target}, created if needed. */
        static Result backupAll(NoteService notes, Path target) throws IOException {
            Path base = Files.createDirectories(target).toAbsolutePath().normalize();
            Set<String> usedNames = new HashSet<>();
            int written = 0;
            int failed = 0;
            int skippedPrivate = 0;
            for (Note listed : notes.getAllNotes()) {
                if (listed == null || listed.getId() == null) {
                    continue;
                }
                try {
                    Optional<Path> file = notes.getNoteFilePath(listed.getId());
                    if (file.isPresent()) {
                        copyAsItIs(file.get(), inside(base, listed.getId()));
                        written++;
                    } else if (listed.isPrivate()) {
                        skippedPrivate++;
                    } else {
                        Note full = whole(notes, listed);
                        Files.writeString(base.resolve(uniqueName(full.getTitle(), usedNames) + ".md"),
                                notes.asMarkdownFile(full), StandardCharsets.UTF_8);
                        written++;
                    }
                } catch (IOException | RuntimeException e) {
                    failed++;
                }
            }
            Optional<Path> vault = notes.getStorageRoot();
            if (vault.isPresent() && !copyTree(vault.get().resolve(".jylos"), base.resolve(".jylos"))) {
                failed++;
            }
            return new Result(written, failed, skippedPrivate);
        }

        /**
         * Writes one note to {@code out}: its file as it is (folder vault), or its whole text with
         * its properties (SQLite).
         *
         * @return false for a private note of a SQLite storage, which is not written in plain text
         */
        static boolean exportNote(NoteService notes, Note note, Path out) throws IOException {
            Optional<Path> file = notes.getNoteFilePath(note.getId());
            if (file.isPresent()) {
                copyAsItIs(file.get(), out, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            if (note.isPrivate()) {
                return false;
            }
            Files.writeString(out, notes.asMarkdownFile(whole(notes, note)), StandardCharsets.UTF_8);
            return true;
        }

        /** The note read whole: a listed note may hold only the start of its text. */
        private static Note whole(NoteService notes, Note listed) throws IOException {
            return notes.getNoteById(listed.getId())
                    .orElseThrow(() -> new IOException("Note not found: " + listed.getId()));
        }

        /** Where the file of note {@code id} goes in the backup, its folders created. */
        private static Path inside(Path base, String id) throws IOException {
            Path destination = base.resolve(id.replace('/', File.separatorChar)).normalize();
            if (!destination.startsWith(base) || destination.equals(base)) {
                throw new IOException("Not a path inside the backup: " + id);
            }
            Files.createDirectories(destination.getParent());
            return destination;
        }

        /** Copies {@code source} with everything in it; true when there was nothing to copy. */
        private static boolean copyTree(Path source, Path destination) {
            if (!Files.isDirectory(source)) {
                return true;
            }
            try (Stream<Path> walk = Files.walk(source)) {
                List<Path> paths = walk.toList();
                for (Path path : paths) {
                    Path to = destination.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(to);
                    } else {
                        Files.createDirectories(to.getParent());
                        copyAsItIs(path, to);
                    }
                }
                return true;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }

        /**
         * Copies {@code source} to {@code target} with its attributes and its exact modified time.
         * On Linux {@code COPY_ATTRIBUTES} keeps the time only to the microsecond, while the file
         * system keeps nanoseconds, so the time is set again from the source.
         */
        private static void copyAsItIs(Path source, Path target, StandardCopyOption... options) throws IOException {
            StandardCopyOption[] all = Arrays.copyOf(options, options.length + 1);
            all[options.length] = StandardCopyOption.COPY_ATTRIBUTES;
            Files.copy(source, target, all);
            Files.setLastModifiedTime(target, Files.getLastModifiedTime(source));
        }

        /** A file name for {@code title} not used yet in this backup (names compare without case). */
        static String uniqueName(String title, Set<String> used) {
            String base = sanitize(title);
            String name = base;
            int suffix = 1;
            while (!used.add(name.toLowerCase(Locale.ROOT))) {
                name = base + " (" + (++suffix) + ")";
            }
            return name;
        }

        /** {@code name} without the characters a file name cannot have. */
        static String sanitize(String name) {
            if (name == null || name.isBlank()) {
                return "untitled";
            }
            String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
            return clean.isEmpty() ? "untitled" : clean;
        }
    }
}
