package com.example.jylos.ui.controller;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.components.KanbanBoard;
import com.example.jylos.util.KanbanModel;

import javafx.scene.layout.StackPane;

/**
 * A board note ({@link KanbanModel#isBoard}) shown as its board in its own tab, as Obsidian's
 * Kanban plugin does, or as Markdown when asked ("view as Markdown", remembered per note for
 * the session).
 *
 * <p>The board draws the note's text and hands back its new Markdown on every change; the
 * editor puts it in its source and saves it as any edit, so unsaved state, conflicts with
 * outside changes and autosave are the editor's, as for any note.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class EditorKanbanSupport {

    /**
     * What the editor gives the board.
     *
     * @param layer       where the board is drawn, over the source and reading panes
     * @param text        the note's source, as edited
     * @param setText     replaces the note's source (a change made on the board)
     * @param saveEdit    the source changed: the editor re-checks and saves it
     * @param showBoard   {@code true} shows the board, {@code false} the note's panes
     * @param openLink    a card's link clicked
     * @param noteCreated a card turned into a note
     * @param darkTheme   whether the dark theme is on
     */
    record Host(Supplier<StackPane> layer, Supplier<String> text, Consumer<String> setText, Runnable saveEdit,
            Consumer<Boolean> showBoard, Consumer<String> openLink, Consumer<Note> noteCreated,
            BooleanSupplier darkTheme) {
    }

    private final Host host;
    private final Supplier<NoteService> noteService;
    private final Function<String, String> i18n;
    /** Board notes switched to Markdown in this session. */
    private final Set<String> asMarkdown = new HashSet<>();
    private KanbanBoard board;
    private boolean noteIsBoard;

    EditorKanbanSupport(Host host, Supplier<NoteService> noteService, Function<String, String> i18n) {
        this.host = host;
        this.noteService = noteService;
        this.i18n = i18n;
    }

    /**
     * Once a note is loaded: shows it as its board when it is one, unless it was switched to
     * Markdown.
     *
     * @return whether the note is a board (shown as a board or as Markdown)
     */
    boolean show(Note note) {
        board = null;
        noteIsBoard = note != null && KanbanModel.isBoard(host.text().get());
        if (!noteIsBoard) {
            return false;
        }
        if (asMarkdown.contains(note.getId())) {
            host.showBoard().accept(false);
            return true;
        }
        board = new KanbanBoard(noteService.get(), note.getId(), host.text().get(), host.openLink(),
                markdown -> {
                    host.setText().accept(markdown);
                    host.saveEdit().run();
                },
                host.noteCreated(), i18n);
        board.setDarkTheme(host.darkTheme().getAsBoolean());
        host.layer().get().getChildren().setAll(board);
        host.showBoard().accept(true);
        return true;
    }

    /** "View as Markdown" and back: switches the open board note and shows it again. */
    void toggle(Note note) {
        if (note == null || !noteIsBoard) {
            return;
        }
        if (!asMarkdown.remove(note.getId())) {
            asMarkdown.add(note.getId());
        }
        show(note);
    }

    /** Whether the open note is shown as its board (not as Markdown). */
    boolean isBoardShown() {
        return board != null;
    }

    void applyTheme(boolean dark) {
        if (board != null) {
            board.setDarkTheme(dark);
        }
    }

    /** The note left: nothing is shown as a board. */
    void clear() {
        board = null;
        noteIsBoard = false;
    }
}
