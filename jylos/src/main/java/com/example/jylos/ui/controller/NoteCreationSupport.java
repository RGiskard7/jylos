package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Window;

/**
 * UI-owned note creation workflows: regular note creation, daily notes and
 * note-from-template flows.
 *
 * <p>The underlying persistence remains in {@link NoteOperations}; this support
 * owns only the shell-level coordination required to open the created note,
 * refresh dependent views and prompt the user when a template must be chosen.</p>
 */
final class NoteCreationSupport {

    private NoteService noteService;
    private NoteOperations noteOperations;
    private BooleanSupplier fileSystemStorage;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Window> windowSupplier;
    private Supplier<Folder> currentFolderSupplier;
    private Consumer<Note> openNote;
    private Consumer<Note> publishCreatedNote;
    private Consumer<String> requestSelectAfterRefresh;
    private Runnable refreshNotes;
    private Runnable refreshSidebar;

    void wire(NoteService noteService, NoteOperations noteOperations, BooleanSupplier fileSystemStorage,
            Function<String, String> i18n, Consumer<String> status, Supplier<Window> windowSupplier,
            Supplier<Folder> currentFolderSupplier, Consumer<Note> openNote,
            Consumer<Note> publishCreatedNote, Consumer<String> requestSelectAfterRefresh,
            Runnable refreshNotes, Runnable refreshSidebar) {
        this.noteService = noteService;
        this.noteOperations = noteOperations;
        this.fileSystemStorage = fileSystemStorage;
        this.i18n = i18n;
        this.status = status;
        this.windowSupplier = windowSupplier;
        this.currentFolderSupplier = currentFolderSupplier;
        this.openNote = openNote;
        this.publishCreatedNote = publishCreatedNote;
        this.requestSelectAfterRefresh = requestSelectAfterRefresh;
        this.refreshNotes = refreshNotes;
        this.refreshSidebar = refreshSidebar;
    }

    void createNewNote() {
        if (noteOperations == null) {
            updateStatus(getString("status.error_creating_note"));
            return;
        }
        NoteOperations.NoteCreationResult creation = noteOperations.createNewNote(
                getString("action.new_note"), currentFolder(), isFileSystem());
        if (!creation.success() || creation.note() == null) {
            updateStatus(getString("status.error_creating_note"));
            return;
        }
        handleCreatedNote(creation.note());
    }

    void openDailyNote() {
        if (noteService == null) {
            return;
        }
        String title = com.example.jylos.util.NoteTemplates.dailyNoteTitle();
        Note existing = noteService.findNoteByTitle(title).orElse(null);
        if (existing != null) {
            publishOpen(noteService.getNoteById(existing.getId()).orElse(existing));
            updateStatus(MessageFormat.format(getString("status.note_loaded"), title));
            return;
        }
        String template = templateContentByName("daily");
        String content = template != null
                ? com.example.jylos.util.NoteTemplates.applyPlaceholders(template, title)
                : "# " + title + "\n\n";
        createAndOpenNote(title, content);
    }

    void createFromTemplate() {
        if (noteService == null) {
            return;
        }
        List<Note> templates = listTemplates();
        if (templates.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, getString("dialog.templates.title"),
                    getString("dialog.templates.none_header"), getString("dialog.templates.none_content"));
            return;
        }

        Map<String, Note> byTitle = new LinkedHashMap<>();
        for (Note template : templates) {
            byTitle.put(template.getTitle() != null ? template.getTitle() : getString("app.untitled"), template);
        }

        String first = byTitle.keySet().iterator().next();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(first, byTitle.keySet());
        dialog.setTitle(getString("dialog.templates.title"));
        dialog.setHeaderText(getString("dialog.templates.pick_header"));
        dialog.setContentText(getString("dialog.templates.pick_content"));
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(choice -> {
            Note template = byTitle.get(choice);
            if (template == null) {
                return;
            }
            Note full = noteService.getNoteById(template.getId()).orElse(template);
            String content = com.example.jylos.util.NoteTemplates.applyPlaceholders(
                    full.getContent() != null ? full.getContent() : "", choice);
            createAndOpenNote(choice, content);
        });
    }

    private void createAndOpenNote(String title, String content) {
        if (noteOperations == null) {
            updateStatus(getString("status.error_creating_note"));
            return;
        }
        NoteOperations.NoteCreationResult creation =
                noteOperations.createNewNote(title, content, currentFolder(), isFileSystem());
        if (!creation.success() || creation.note() == null) {
            updateStatus(getString("status.error_creating_note"));
            return;
        }
        handleCreatedNote(creation.note());
    }

    private void handleCreatedNote(Note note) {
        publishOpen(note);
        if (publishCreatedNote != null) {
            publishCreatedNote.accept(note);
        }
        if (requestSelectAfterRefresh != null && note.getId() != null) {
            requestSelectAfterRefresh.accept(note.getId());
        }
        if (refreshNotes != null) {
            refreshNotes.run();
        }
        if (refreshSidebar != null) {
            refreshSidebar.run();
        }
        updateStatus(getString("status.note_created"));
    }

    private List<Note> listTemplates() {
        List<Note> templates = new ArrayList<>();
        for (Note note : noteService.getAllNotes()) {
            if (note == null) {
                continue;
            }
            String id = note.getId() != null ? note.getId().replace('\\', '/').toLowerCase(Locale.ROOT) : "";
            boolean inTemplates = id.startsWith("templates/")
                    || (note.getParent() != null && "templates".equalsIgnoreCase(note.getParent().getTitle()));
            if (inTemplates) {
                templates.add(note);
            }
        }
        templates.sort((left, right) -> safeTitle(left).compareToIgnoreCase(safeTitle(right)));
        return templates;
    }

    private String templateContentByName(String name) {
        for (Note template : listTemplates()) {
            if (name.equalsIgnoreCase(template.getTitle())) {
                Note full = noteService.getNoteById(template.getId()).orElse(template);
                return full.getContent();
            }
        }
        return null;
    }

    private boolean isFileSystem() {
        return fileSystemStorage != null && fileSystemStorage.getAsBoolean();
    }

    private Folder currentFolder() {
        return currentFolderSupplier != null ? currentFolderSupplier.get() : null;
    }

    private void publishOpen(Note note) {
        if (openNote != null && note != null) {
            openNote.accept(note);
        }
    }

    private void styleDialog(javafx.scene.control.Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }
        Window window = windowSupplier != null ? windowSupplier.get() : null;
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

    private static String safeTitle(Note note) {
        return note.getTitle() != null ? note.getTitle() : "";
    }
}
