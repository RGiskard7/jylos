package com.example.jylos.ui.controller;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.NoteService;

import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Renders the right-panel <em>backlinks</em> section: the notes that link to the open
 * note, computed off the JavaFX thread by {@link BacklinkService}. Clicking an entry
 * opens that note. Extracted from {@code MainController} as a cohesive panel helper.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class BacklinksSupport {

    private VBox backlinksContent;
    private BacklinkService backlinkService;
    private NoteService noteService;
    private Function<String, String> i18n;
    private Consumer<Note> openNote;
    private Supplier<Boolean> backlinksVisible = () -> true;

    /** Guards against stale results: a task completing after a newer one replaces it. */
    private volatile Task<List<Note>> currentTask;

    void wire(VBox backlinksContent, BacklinkService backlinkService, NoteService noteService,
            Function<String, String> i18n, Consumer<Note> openNote, Supplier<Boolean> backlinksVisible) {
        this.backlinksContent = backlinksContent;
        this.backlinkService = backlinkService;
        this.noteService = noteService;
        this.i18n = i18n;
        this.openNote = openNote;
        this.backlinksVisible = backlinksVisible != null ? backlinksVisible : () -> true;
    }

    /** Recomputes (off the FX thread) and renders the backlinks for {@code note}. */
    void refresh(Note note) {
        if (backlinksContent == null) {
            return;
        }
        if (backlinksVisible != null && !backlinksVisible.get()) {
            backlinksContent.getChildren().clear();
            return;
        }
        if (backlinkService == null || note == null || note.getId() == null) {
            backlinksContent.getChildren().clear();
            return;
        }
        Task<List<Note>> prev = currentTask;
        if (prev != null) {
            prev.cancel();
        }

        Task<List<Note>> task = new Task<>() {
            @Override
            protected List<Note> call() {
                return backlinkService.backlinksFor(note);
            }
        };
        task.setOnSucceeded(e -> {
            // Discard result if a newer task already started (guard against TOCTOU).
            if (currentTask == task) {
                render(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            if (currentTask == task) {
                backlinksContent.getChildren().clear();
            }
        });
        currentTask = task;
        Thread thread = new Thread(task, "backlinks");
        thread.setDaemon(true);
        thread.start();
    }

    private void render(List<Note> links) {
        backlinksContent.getChildren().clear();
        if (links == null || links.isEmpty()) {
            Label none = new Label(getString("info.no_backlinks"));
            none.getStyleClass().add("info-value-small");
            backlinksContent.getChildren().add(none);
            return;
        }
        for (Note link : links) {
            Label item = new Label(link.getTitle() != null && !link.getTitle().isBlank()
                    ? link.getTitle() : getString("app.untitled"));
            item.getStyleClass().add("backlink-item");
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnMouseClicked(e -> open(link));
            backlinksContent.getChildren().add(item);
        }
    }

    private void open(Note link) {
        Note full = noteService != null ? noteService.getNoteById(link.getId()).orElse(link) : link;
        if (openNote != null) {
            openNote.accept(full);
        }
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }
}
