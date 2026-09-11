package com.example.jylos.plugin.builtin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginI18n;
import com.example.jylos.service.DatabaseBackupService;

import javafx.application.Platform;
import javafx.stage.DirectoryChooser;

/**
 * Auto Backup Plugin - Automatically backs up notes to files.
 * 
 * <p>This plugin provides:</p>
 * <ul>
 *   <li>Export all notes to Markdown files</li>
 *   <li>Backup database file</li>
 *   <li>Scheduled automatic backups (manual trigger for now)</li>
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
    private static final String AUTHOR = "Jylos Team";
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String DB_PATH = "data/database.db";
    
    private PluginContext context;

    // Loaded once, lazily — getDescription() can be called by the Plugin Manager before
    // initialize() ever runs, so this cannot wait for that.
    private static java.util.ResourceBundle bundle;

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
    
    /**
     * Exports all notes as Markdown files.
     */
    private void exportAllNotes() {
        Platform.runLater(() -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(tr("autobackup.chooser.folder.title", "Select Backup Folder"));
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File selectedDir = chooser.showDialog(null);
            if (selectedDir == null) {
                return;
            }

            // Create backup subfolder with timestamp
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            File backupDir = new File(selectedDir, "jylos-backup-" + timestamp);

            if (!backupDir.mkdirs()) {
                context.showError(tr("autobackup.error.title", "Backup Error"),
                        tr("autobackup.error.createFolder", "Failed to create backup folder: %s").formatted(backupDir.getPath()));
                return;
            }
            
            // Export notes
            List<Note> allNotes = context.getNoteService().getAllNotes();
            int exported = 0;
            int failed = 0;
            
            for (Note note : allNotes) {
                try {
                    String fileName = sanitizeFileName(note.getTitle()) + ".md";
                    File noteFile = new File(backupDir, fileName);
                    
                    String content = buildExportContent(note);
                    
                    try (FileWriter writer = new FileWriter(noteFile)) {
                        writer.write(content);
                    }
                    
                    exported++;
                } catch (Exception e) {
                    context.logError("Failed to export note: " + note.getTitle(), e);
                    failed++;
                }
            }
            
            // Show result
            String message = String.format(
                tr("autobackup.exportAll.body",
                    "Backup completed!\n\n" +
                    "Location: %s\n" +
                    "Notes exported: %d\n" +
                    "Failed: %d"),
                backupDir.getPath(), exported, failed
            );

            context.showInfo(tr("autobackup.exportAll.title", "Backup Complete"), tr("autobackup.exportAll.header", "Notes Exported"), message);
            context.log("Exported " + exported + " notes to " + backupDir.getPath());
        });
    }
    
    /**
     * Backs up the database file.
     */
    private void backupDatabase() {
        Platform.runLater(() -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(tr("autobackup.chooser.location.title", "Select Backup Location"));
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File selectedDir = chooser.showDialog(null);
            if (selectedDir == null) {
                return;
            }

            File sourceDb = new File(DB_PATH);
            if (!sourceDb.exists()) {
                context.showError(tr("autobackup.error.title", "Backup Error"),
                        tr("autobackup.error.dbNotFound", "Database file not found: %s").formatted(DB_PATH));
                return;
            }

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String backupName = "database-backup-" + timestamp + ".db";
            File targetDb = new File(selectedDir, backupName);

            if (!DatabaseBackupService.backupDatabaseFile(sourceDb, targetDb)) {
                context.showError(tr("autobackup.error.title", "Backup Error"), tr("autobackup.error.dbFailed", "Failed to backup database."));
                return;
            }

            context.showInfo(tr("autobackup.database.title", "Database Backup"), tr("autobackup.database.header", "Backup Created"),
                    tr("autobackup.database.body", "Database backed up to:\n%s").formatted(targetDb.getPath()));
            context.log("Database backed up to " + targetDb.getPath());
        });
    }
    
    /**
     * Performs a full backup (notes + database).
     */
    private void fullBackup() {
        Platform.runLater(() -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(tr("autobackup.chooser.folder.title", "Select Backup Folder"));
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File selectedDir = chooser.showDialog(null);
            if (selectedDir == null) {
                return;
            }

            // Create backup folder
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            File backupDir = new File(selectedDir, "jylos-full-backup-" + timestamp);
            File notesDir = new File(backupDir, "notes");

            if (!notesDir.mkdirs()) {
                context.showError(tr("autobackup.error.title", "Backup Error"), tr("autobackup.error.createFolders", "Failed to create backup folders"));
                return;
            }

            StringBuilder report = new StringBuilder();
            report.append(tr("autobackup.report.title", "Full Backup Report")).append("\n");
            report.append("==================\n\n");
            report.append(tr("autobackup.report.location", "Location:")).append(" ").append(backupDir.getPath()).append("\n\n");
            
            // Export notes
            List<Note> allNotes = context.getNoteService().getAllNotes();
            int exported = 0;
            
            for (Note note : allNotes) {
                try {
                    String fileName = sanitizeFileName(note.getTitle()) + ".md";
                    File noteFile = new File(notesDir, fileName);
                    
                    String content = buildExportContent(note);
                    
                    try (FileWriter writer = new FileWriter(noteFile)) {
                        writer.write(content);
                    }
                    
                    exported++;
                } catch (Exception e) {
                    context.logError("Failed to export note: " + note.getTitle(), e);
                }
            }
            
            report.append(tr("autobackup.report.notesExported", "Notes exported:")).append(" ").append(exported).append("\n");

            File sourceDb = new File(DB_PATH);
            if (sourceDb.exists()) {
                File targetDb = new File(backupDir, "database.db");
                if (DatabaseBackupService.backupDatabaseFile(sourceDb, targetDb)) {
                    report.append(tr("autobackup.report.database.backedUp", "Database: backed up")).append("\n");
                } else {
                    report.append(tr("autobackup.report.database.failed", "Database: backup failed")).append("\n");
                }
            } else {
                report.append(tr("autobackup.report.database.notFound", "Database: not found")).append("\n");
            }

            // Write report
            try {
                File reportFile = new File(backupDir, "backup-report.txt");
                try (FileWriter writer = new FileWriter(reportFile)) {
                    writer.write(report.toString());
                    writer.write("\n" + tr("autobackup.report.createdAt", "Backup created:") + " "
                            + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
            } catch (IOException e) {
                context.logError("Failed to write backup report", e);
            }

            context.showInfo(tr("autobackup.full.title", "Full Backup"), tr("autobackup.full.header", "Backup Complete"),
                tr("autobackup.full.body", "Full backup created at:\n%s\n\nNotes: %d\nDatabase: included")
                        .formatted(backupDir.getPath(), exported));
            
            context.log("Full backup completed: " + backupDir.getPath());
        });
    }
    
    /**
     * Exports the current note.
     */
    private void exportCurrentNote() {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        
        if (allNotes.isEmpty()) {
            context.showInfo(tr("autobackup.exportNote.title", "Export Note"), tr("autobackup.exportNote.noNotes.header", "No Notes"),
                    tr("autobackup.exportNote.noNotes.body", "Create a note first."));
            return;
        }

        Platform.runLater(() -> {
            javafx.scene.control.ChoiceDialog<Note> dialog = new javafx.scene.control.ChoiceDialog<>(
                allNotes.get(0), allNotes
            );
            dialog.setTitle(tr("autobackup.exportNote.title", "Export Note"));
            dialog.setHeaderText(tr("autobackup.exportNote.header", "Select a note to export:"));
            dialog.setContentText(tr("autobackup.exportNote.content", "Note:"));

            context.showThemed(dialog).ifPresent(note -> {
                javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
                fileChooser.setTitle(tr("autobackup.saveNote.title", "Save Note As"));
                fileChooser.setInitialFileName(sanitizeFileName(note.getTitle()) + ".md");
                fileChooser.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter("Markdown", "*.md"),
                    new javafx.stage.FileChooser.ExtensionFilter(tr("autobackup.filter.text", "Text"), "*.txt")
                );

                File file = fileChooser.showSaveDialog(null);
                if (file != null) {
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(buildExportContent(note));
                        context.showInfo(tr("autobackup.exportComplete.title", "Export Complete"), tr("autobackup.exportComplete.header", "Note Exported"),
                            tr("autobackup.exportComplete.body", "Note saved to:\n%s").formatted(file.getPath()));
                        context.log("Exported note to " + file.getPath());
                    } catch (IOException e) {
                        context.logError("Failed to export note", e);
                        context.showError(tr("autobackup.exportError.title", "Export Error"),
                                tr("autobackup.exportError.body", "Failed to save note: %s").formatted(e.getMessage()));
                    }
                }
            });
        });
    }
    
    /**
     * Builds the export content for a note with metadata.
     */
    private String buildExportContent(Note note) {
        StringBuilder sb = new StringBuilder();
        
        // Add YAML frontmatter
        sb.append("---\n");
        sb.append("title: ").append(note.getTitle()).append("\n");
        if (note.getCreatedDate() != null) {
            sb.append("created: ").append(note.getCreatedDate()).append("\n");
        }
        if (note.getModifiedDate() != null) {
            sb.append("modified: ").append(note.getModifiedDate()).append("\n");
        }
        if (note.isFavorite()) {
            sb.append("favorite: true\n");
        }
        sb.append("---\n\n");
        
        // Add content
        if (note.getContent() != null) {
            sb.append(note.getContent());
        }
        
        return sb.toString();
    }
    
    /**
     * Sanitizes a filename by removing invalid characters.
     */
    private String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "untitled";
        }
        
        return name
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
