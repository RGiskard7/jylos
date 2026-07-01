package com.example.jylos.ui.controller;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.components.KanbanBoard;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Manages the two mutually-exclusive overlays that share the center {@code StackPane}:
 * the knowledge-graph view and the Kanban board. Owns the lazy-created
 * {@link KanbanBoard} and the show/hide/toggle logic, extracted from
 * {@code MainController}.
 *
 * <p>The graph FXML controller's {@code onClose}/{@code onOpenNote} callbacks are
 * pointed here, and note-open requests are delegated back to the owning shell
 * controller via a callback, keeping {@code MainController} the single source of truth
 * for what's loaded in the editor.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class OverlaySupport {

    private StackPane centerStack;
    private VBox graphView;
    private GraphController graphViewController;
    private NoteService noteService;
    private Supplier<Boolean> darkTheme;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Consumer<Note> openNote;

    /** Lazy-created Kanban board overlay, added to {@link #centerStack} on first use. */
    private KanbanBoard kanbanBoard;

    void wire(StackPane centerStack, VBox graphView, GraphController graphViewController,
            NoteService noteService, Supplier<Boolean> darkTheme, Function<String, String> i18n,
            Consumer<String> status, Consumer<Note> openNote) {
        this.centerStack = centerStack;
        this.graphView = graphView;
        this.graphViewController = graphViewController;
        this.noteService = noteService;
        this.darkTheme = darkTheme;
        this.i18n = i18n;
        this.status = status;
        this.openNote = openNote;
        setGraphVisible(false);
    }

    // ------------------------------------------------------------------
    // Graph overlay
    // ------------------------------------------------------------------

    void toggleGraph() {
        if (graphView == null || graphViewController == null) {
            return;
        }
        if (graphView.isVisible()) {
            hideGraph();
        } else {
            setGraphVisible(true);
            graphViewController.show(isDark());
            updateStatus(getString("status.graph_opened"));
        }
    }

    /** Hides the graph overlay (also the graph controller's onClose callback target). */
    void hideGraph() {
        setGraphVisible(false);
        if (graphViewController != null) {
            graphViewController.pause();
        }
    }

    /** Re-applies the theme to the graph when it is on screen. */
    void applyGraphThemeIfVisible() {
        if (graphViewController != null && graphView != null && graphView.isVisible()) {
            graphViewController.applyTheme(isDark());
        }
    }

    /** Opens a note clicked in the graph (the graph controller's onOpenNote target). */
    void openNoteFromGraph(String noteId) {
        if (noteId == null || noteService == null) {
            return;
        }
        noteService.getNoteById(noteId).ifPresent(note -> {
            hideGraph();
            publishOpen(note);
        });
    }

    private void setGraphVisible(boolean visible) {
        setShown(graphView, visible);
    }

    // ------------------------------------------------------------------
    // Kanban overlay
    // ------------------------------------------------------------------

    void toggleKanban() {
        if (centerStack == null) {
            return;
        }
        ensureKanban();
        if (kanbanBoard == null) {
            return;
        }
        if (kanbanBoard.isVisible()) {
            hideKanban();
        } else {
            if (graphView != null && graphView.isVisible()) {
                hideGraph(); // the two overlays share the center stack
            }
            kanbanBoard.setDarkTheme(isDark());
            kanbanBoard.reload();
            setShown(kanbanBoard, true);
            kanbanBoard.toFront();
            kanbanBoard.requestFocus(); // so Escape closes the board
            updateStatus(getString("status.kanban_opened"));
        }
    }

    void hideKanban() {
        setShown(kanbanBoard, false);
    }

    private void ensureKanban() {
        if (kanbanBoard != null || centerStack == null || noteService == null) {
            return;
        }
        kanbanBoard = new KanbanBoard(noteService, this::openNoteByTitle, this::hideKanban, i18n);
        setShown(kanbanBoard, false);
        centerStack.getChildren().add(kanbanBoard);
    }

    /** Opens a note referenced from a Kanban card ({@code [[Title]]}). */
    private void openNoteByTitle(String title) {
        if (title == null || title.isBlank() || noteService == null) {
            return;
        }
        noteService.findNoteByTitle(title).ifPresent(note -> {
            hideKanban();
            publishOpen(note);
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void publishOpen(Note note) {
        if (openNote != null && note != null) {
            openNote.accept(note);
        }
    }

    private boolean isDark() {
        return darkTheme != null && Boolean.TRUE.equals(darkTheme.get());
    }

    private static void setShown(Node node, boolean shown) {
        if (node != null) {
            node.setVisible(shown);
            node.setManaged(shown);
        }
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
