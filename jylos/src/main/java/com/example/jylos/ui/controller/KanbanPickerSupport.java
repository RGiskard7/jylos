package com.example.jylos.ui.controller;

import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.components.QuickSwitcher;

import javafx.stage.Stage;

/**
 * Which Kanban board to open (Ctrl/Cmd+K, "Kanban board" in a new tab): the quick switcher
 * listing the vault's boards, with "New board" at its foot.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class KanbanPickerSupport {

    private final Supplier<Stage> owner;
    private final Supplier<ResourceBundle> bundle;
    private final Consumer<Note> open;
    private final Runnable newBoard;
    private QuickSwitcher switcher;

    /**
     * @param owner    the window it shows over
     * @param bundle   translations (read when first shown)
     * @param open     opens the board picked
     * @param newBoard creates a board and opens it
     */
    KanbanPickerSupport(Supplier<Stage> owner, Supplier<ResourceBundle> bundle, Consumer<Note> open,
            Runnable newBoard) {
        this.owner = owner;
        this.bundle = bundle;
        this.open = open;
        this.newBoard = newBoard;
    }

    /** Shows {@code boards} to pick one from, or to make a new one. */
    void show(List<Note> boards) {
        if (switcher == null) {
            switcher = new QuickSwitcher(owner.get());
            switcher.setBundle(bundle.get());
            switcher.setPlaceholder(text("kanban.search_placeholder", "Find a board…"));
            switcher.setFooterAction("+ " + text("kanban.new_board", "New board"), () -> newBoard.run());
            switcher.setOnNoteSelected(note -> open.accept(note));
        }
        switcher.setNotes(boards);
        switcher.show();
    }

    private String text(String key, String fallback) {
        ResourceBundle texts = bundle.get();
        return texts != null && texts.containsKey(key) ? texts.getString(key) : fallback;
    }
}
