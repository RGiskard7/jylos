package com.example.jylos.ui.controller;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.example.jylos.config.AppContext;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * Import/export of notes as Markdown/text files.
 *
 * <p>Stateless utility: pulls the note/folder services it needs from
 * {@link AppContext}; the controller only supplies the files and target folder.</p>
 */
class DocumentIO {

    private static final Logger logger = LoggerConfig.getLogger(DocumentIO.class);
    private static final String ROOT_ID = "ROOT";
    private static final String ALL_NOTES_VIRTUAL_ID = "ALL_NOTES_VIRTUAL";

    record ImportResult(int importedCount, int failedCount, List<String> failures) {
    }

    record ExportResult(boolean success, String errorMessage) {
    }

    ImportResult importFiles(List<File> files, Folder currentFolder, boolean isFileSystem) {
        if (files == null || files.isEmpty()) {
            return new ImportResult(0, 0, List.of());
        }

        int imported = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        for (File file : files) {
            try {
                String content = Files.readString(file.toPath());
                String title = extractTitleFromFileName(file.getName());

                Note newNote = new Note(title, content);
                if (isFileSystem && isConcreteFolder(currentFolder)) {
                    String safeTitle = sanitizeFileName(title);
                    newNote.setId(currentFolder.getId() + File.separator + safeTitle);
                }

                Note createdNote = AppContext.getNoteService().createNote(newNote);
                if (createdNote == null || createdNote.getId() == null || createdNote.getId().isBlank()) {
                    throw new IllegalStateException("Created note has null/blank ID");
                }
                newNote.setId(createdNote.getId());

                if (!isFileSystem && isConcreteFolder(currentFolder)) {
                    AppContext.getFolderService().addNoteToFolder(currentFolder, createdNote);
                }
                imported++;
            } catch (Exception e) {
                failed++;
                String msg = "Failed to import file " + file.getName() + ": " + e.getMessage();
                failures.add(msg);
                logger.warning(msg);
            }
        }

        return new ImportResult(imported, failed, failures);
    }

    ExportResult exportNote(Note note, File targetFile) {
        if (note == null) {
            return new ExportResult(false, "Note is null");
        }
        if (targetFile == null) {
            return new ExportResult(false, "Target file is null");
        }

        String name = targetFile.getName().toLowerCase();
        try {
            java.nio.file.Path baseDir = resolveBaseDir(note);
            if (name.endsWith(".html") || name.endsWith(".htm")) {
                com.example.jylos.util.NoteExporter.exportHtml(note, targetFile, baseDir);
                return new ExportResult(true, null);
            }
            if (name.endsWith(".pdf")) {
                String baseUri = baseDir != null ? baseDir.toUri().toString() : null;
                com.example.jylos.util.NoteExporter.exportPdf(note, targetFile, baseUri);
                return new ExportResult(true, null);
            }
            // Markdown / plain text (default).
            try (FileWriter writer = new FileWriter(targetFile)) {
                if (name.endsWith(".md")) {
                    writer.write("# " + note.getTitle() + "\n\n");
                }
                writer.write(note.getContent() != null ? note.getContent() : "");
            }
            return new ExportResult(true, null);
        } catch (Exception e) {
            logger.warning("Export failed for " + targetFile.getName() + ": " + e.getMessage());
            return new ExportResult(false, e.getMessage());
        }
    }

    /**
     * The note's source folder, used to resolve relative resources (images) when
     * exporting, so {@code ![](img.png)} resolves correctly. Null if unknown.
     */
    private java.nio.file.Path resolveBaseDir(Note note) {
        try {
            var path = AppContext.getNoteService().getNoteFilePath(note.getId());
            if (path.isPresent() && path.get().getParent() != null) {
                return path.get().getParent();
            }
        } catch (Exception e) {
            logger.fine("Could not resolve base dir for export: " + e.getMessage());
        }
        return null;
    }

    String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "untitled";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9\\-_ ]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 50));
    }

    private String extractTitleFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Untitled";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private boolean isConcreteFolder(Folder folder) {
        return folder != null
                && folder.getId() != null
                && !folder.getId().isBlank()
                && !ROOT_ID.equals(folder.getId())
                && !ALL_NOTES_VIRTUAL_ID.equals(folder.getId());
    }
}
