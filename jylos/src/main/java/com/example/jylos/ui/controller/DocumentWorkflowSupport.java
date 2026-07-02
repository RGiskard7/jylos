package com.example.jylos.ui.controller;

import java.io.File;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * UI workflows for local note import/export.
 *
 * <p>{@link DocumentSupport} keeps the low-level file import/export mechanics.
 * This support owns only the file pickers, background export task and UI status
 * updates extracted from {@link MainController}.</p>
 */
final class DocumentWorkflowSupport {

    private static final Logger logger = LoggerConfig.getLogger(DocumentWorkflowSupport.class);

    private DocumentSupport documentSupport;
    private NoteService noteService;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Window> windowSupplier;
    private Supplier<Folder> currentFolderSupplier;
    private Supplier<Note> currentNoteSupplier;
    private Supplier<String> currentContentSupplier;
    private Runnable refreshAfterImport;

    void wire(DocumentSupport documentSupport, NoteService noteService, Function<String, String> i18n,
            Consumer<String> status, Supplier<Window> windowSupplier,
            Supplier<Folder> currentFolderSupplier, Supplier<Note> currentNoteSupplier,
            Supplier<String> currentContentSupplier, Runnable refreshAfterImport) {
        this.documentSupport = documentSupport;
        this.noteService = noteService;
        this.i18n = i18n;
        this.status = status;
        this.windowSupplier = windowSupplier;
        this.currentFolderSupplier = currentFolderSupplier;
        this.currentNoteSupplier = currentNoteSupplier;
        this.currentContentSupplier = currentContentSupplier;
        this.refreshAfterImport = refreshAfterImport;
    }

    void importFiles(boolean isFileSystem) {
        if (documentSupport == null) {
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(getString("dialog.import.title"));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(getString("file_filter.supported"), "*.md", "*.txt", "*.markdown"),
                new FileChooser.ExtensionFilter(getString("file_filter.markdown"), "*.md", "*.markdown"),
                new FileChooser.ExtensionFilter(getString("file_filter.text"), "*.txt"),
                new FileChooser.ExtensionFilter(getString("file_filter.all"), "*.*"));

        List<File> files = fileChooser.showOpenMultipleDialog(window());
        if (files == null || files.isEmpty()) {
            return;
        }

        DocumentSupport.ImportResult importResult =
                documentSupport.importFiles(files, currentFolder(), isFileSystem);
        if (refreshAfterImport != null) {
            refreshAfterImport.run();
        }

        String message = MessageFormat.format(getString("status.imported_notes"), importResult.importedCount());
        if (importResult.failedCount() > 0) {
            message += "\n" + MessageFormat.format(getString("status.import_failed_count"),
                    importResult.failedCount());
        }
        updateStatus(message);
        showAlert(Alert.AlertType.INFORMATION, getString("status.import_complete"),
                getString("dialog.import_finished"), message);
    }

    void exportCurrentNote() {
        exportNote(currentNoteSupplier != null ? currentNoteSupplier.get() : null);
    }

    void exportNote(Note note) {
        if (documentSupport == null) {
            return;
        }
        if (note == null) {
            showAlert(Alert.AlertType.WARNING, getString("dialog.export.title"),
                    getString("dialog.export.no_note_header"), getString("dialog.export.no_note_content"));
            return;
        }
        if (note.isPrivate()) {
            showAlert(Alert.AlertType.WARNING, getString("dialog.export.title"),
                    getString("dialog.export.private_header"), getString("dialog.export.private_content"));
            return;
        }

        Note currentNote = resolveFullNoteForExport(note);
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(getString("dialog.export.save_title"));
        fileChooser.setInitialFileName(documentSupport.sanitizeFileName(currentNote.getTitle()));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(getString("file_filter.markdown"), "*.md"),
                new FileChooser.ExtensionFilter(getString("file_filter.pdf"), "*.pdf"),
                new FileChooser.ExtensionFilter(getString("file_filter.html"), "*.html"),
                new FileChooser.ExtensionFilter(getString("file_filter.text"), "*.txt"),
                new FileChooser.ExtensionFilter(getString("file_filter.all"), "*.*"));

        File file = fileChooser.showSaveDialog(window());
        if (file == null) {
            return;
        }

        DocumentSupport.ExportResult exportResult = documentSupport.exportNote(currentNote, file);
        if (exportResult.success()) {
            updateStatus(MessageFormat.format(getString("status.exported"), file.getName()));
            showAlert(Alert.AlertType.INFORMATION, getString("status.export_success"),
                    getString("dialog.export.success_header"),
                    MessageFormat.format(getString("dialog.export.saved_to"), file.getAbsolutePath()));
            return;
        }

        String errorMessage = exportResult.errorMessage() == null ? "" : exportResult.errorMessage();
        logger.warning("Failed to export note '"
                + (currentNote.getId() != null ? currentNote.getId() : currentNote.getTitle())
                + "': " + errorMessage);
        showAlert(Alert.AlertType.ERROR, getString("status.export_failed"),
                getString("dialog.export.failed_header"), errorMessage);
    }

    void exportVault() {
        if (noteService == null || documentSupport == null) {
            return;
        }
        javafx.scene.control.ChoiceDialog<String> formatDialog =
                new javafx.scene.control.ChoiceDialog<>("PDF", List.of("PDF", "HTML"));
        formatDialog.setTitle(getString("dialog.export_vault.title"));
        formatDialog.setHeaderText(getString("dialog.export_vault.header"));
        formatDialog.setContentText(getString("dialog.export_vault.format"));
        styleDialog(formatDialog);
        java.util.Optional<String> chosen = formatDialog.showAndWait();
        if (chosen.isEmpty()) {
            return;
        }
        String extension = chosen.get().equalsIgnoreCase("PDF") ? ".pdf" : ".html";

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(getString("dialog.export_vault.choose_dir"));
        File directory = chooser.showDialog(window());
        if (directory == null) {
            return;
        }

        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() {
                return exportAllNotes(directory, extension);
            }
        };
        updateStatus(getString("dialog.export_vault.title"));
        task.setOnSucceeded(event -> {
            int[] result = task.getValue();
            String message = MessageFormat.format(getString("status.export_vault_done"), result[0], result[1]);
            updateStatus(message);
            showAlert(Alert.AlertType.INFORMATION, getString("dialog.export_vault.title"),
                    getString("dialog.export.success_header"), message + "\n" + directory.getAbsolutePath());
        });
        task.setOnFailed(event -> showAlert(Alert.AlertType.ERROR, getString("dialog.export_vault.title"),
                getString("dialog.export.failed_header"),
                task.getException() != null ? task.getException().getMessage() : ""));
        Thread thread = new Thread(task, "export-vault");
        thread.setDaemon(true);
        thread.start();
    }

    private int[] exportAllNotes(File directory, String extension) {
        Set<String> usedNames = new HashSet<>();
        int ok = 0;
        int fail = 0;
        for (Note listNote : noteService.getAllNotes()) {
            if (listNote == null || listNote.getId() == null
                    || com.example.jylos.util.AttachmentType.isAttachment(listNote.getId())) {
                continue;
            }
            if (listNote.isPrivate()) {
                continue;
            }
            Note full = noteService.getNoteById(listNote.getId()).orElse(listNote);
            String base = documentSupport.sanitizeFileName(
                    full.getTitle() != null && !full.getTitle().isBlank() ? full.getTitle() : "untitled");
            String name = base;
            int suffix = 1;
            while (!usedNames.add(name.toLowerCase(Locale.ROOT))) {
                name = base + " (" + (++suffix) + ")";
            }
            DocumentSupport.ExportResult result =
                    documentSupport.exportNote(full, new File(directory, name + extension));
            if (result.success()) {
                ok++;
            } else {
                fail++;
            }
        }
        return new int[] { ok, fail };
    }

    private Note resolveFullNoteForExport(Note note) {
        Note openNote = currentNoteSupplier != null ? currentNoteSupplier.get() : null;
        if (openNote != null && Objects.equals(openNote.getId(), note.getId())) {
            String live = currentContentSupplier != null ? currentContentSupplier.get() : null;
            if (live != null) {
                return new Note(openNote.getId(), openNote.getTitle(), live);
            }
        }
        if (noteService != null) {
            return noteService.getNoteById(note.getId()).orElse(note);
        }
        return note;
    }

    private Folder currentFolder() {
        return currentFolderSupplier != null ? currentFolderSupplier.get() : null;
    }

    private Window window() {
        return windowSupplier != null ? windowSupplier.get() : null;
    }

    private void styleDialog(javafx.scene.control.Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }
        Window window = window();
        if (window != null) {
            dialog.initOwner(window);
        }
        com.example.jylos.ui.UiDialogs.apply(dialog);
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        com.example.jylos.ui.UiDialogs.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }
}
