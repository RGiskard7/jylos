package com.example.jylos.ui.controller;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.NoteHistoryService;
import com.example.jylos.service.NoteService;
import com.example.jylos.util.LineDiff;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;

/**
 * Note version-history dialog: lists the local snapshots of the open note, shows a
 * line diff (snapshot → current content) for the selected one, and can restore a
 * snapshot into the editor. Snapshots come from {@link NoteHistoryService}; private
 * notes' snapshots are stored encrypted and are only readable while unlocked.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class HistorySupport {

    private static final Logger logger = LoggerConfig.getLogger(HistorySupport.class);

    private NoteService noteService;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Scene> sceneSupplier;
    private Supplier<Note> currentNote;
    private Consumer<Note> reloadNote;

    void wire(NoteService noteService, Function<String, String> i18n, Consumer<String> status,
            Supplier<Scene> sceneSupplier, Supplier<Note> currentNote, Consumer<Note> reloadNote) {
        this.noteService = noteService;
        this.i18n = i18n;
        this.status = status;
        this.sceneSupplier = sceneSupplier;
        this.currentNote = currentNote;
        this.reloadNote = reloadNote;
    }

    /** Opens the history dialog for the currently open note. */
    void showHistoryDialog() {
        Note note = currentNote != null ? currentNote.get() : null;
        NoteHistoryService history = noteService != null ? noteService.getHistoryService() : null;
        if (note == null || note.getId() == null) {
            updateStatus(getString("status.no_note_selected"));
            return;
        }
        if (history == null) {
            updateStatus(getString("history.unavailable"));
            return;
        }
        List<NoteHistoryService.Snapshot> snapshots = history.list(note.getId());
        if (snapshots.isEmpty()) {
            updateStatus(getString("history.empty"));
            return;
        }

        ListView<NoteHistoryService.Snapshot> snapshotList = new ListView<>();
        snapshotList.getItems().setAll(snapshots);
        snapshotList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(NoteHistoryService.Snapshot item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayTime());
            }
        });
        snapshotList.setPrefWidth(190);

        ListView<LineDiff.Line> diffView = new ListView<>();
        diffView.setCellFactory(lv -> new DiffCell());
        diffView.setPlaceholder(new Label(getString("history.select_snapshot")));

        snapshotList.getSelectionModel().selectedItemProperty().addListener((obs, old, snap) -> {
            if (snap != null) {
                diffView.getItems().setAll(diffFor(history, snap, note));
            }
        });
        snapshotList.getSelectionModel().selectFirst();

        SplitPane split = new SplitPane(snapshotList, diffView);
        split.setDividerPositions(0.28);
        VBox box = new VBox(split);
        box.setPadding(new Insets(8));
        VBox.setVgrow(split, javafx.scene.layout.Priority.ALWAYS);

        Dialog<ButtonType> dialog = new Dialog<>();
        Scene scene = sceneSupplier != null ? sceneSupplier.get() : null;
        if (scene != null) {
            dialog.initOwner(scene.getWindow());
        }
        com.example.jylos.ui.UiDialogs.apply(dialog);
        dialog.setTitle(getString("history.title"));
        dialog.setHeaderText(note.getTitle());
        ButtonType restoreType = new ButtonType(getString("history.restore"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(restoreType, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setPrefSize(760, 520);

        dialog.showAndWait().ifPresent(choice -> {
            if (choice == restoreType) {
                NoteHistoryService.Snapshot selected = snapshotList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    restore(history, selected, note);
                }
            }
        });
    }

    /** Builds the snapshot→current diff, decrypting both sides when needed/possible. */
    private List<LineDiff.Line> diffFor(NoteHistoryService history,
            NoteHistoryService.Snapshot snapshot, Note note) {
        try {
            String old = readable(history.read(snapshot));
            String current = readable(currentStoredContent(note));
            return LineDiff.diff(old, current);
        } catch (Exception e) {
            logger.log(Level.WARNING, "History diff failed", e);
            return List.of(new LineDiff.Line(LineDiff.Type.SAME, getString("history.diff_failed")));
        }
    }

    private void restore(NoteHistoryService history, NoteHistoryService.Snapshot snapshot, Note note) {
        try {
            String content = readable(history.read(snapshot));
            if (EncryptionService.isEncrypted(content)) {
                updateStatus(getString("history.locked"));
                return;
            }
            // Saving the restore makes the pre-restore state itself a new snapshot,
            // so a restore can always be undone from the same dialog.
            Note full = noteService.getNoteById(note.getId()).orElse(note);
            full.setContent(content);
            noteService.updateNote(full);
            if (reloadNote != null) {
                reloadNote.accept(full);
            }
            updateStatus(getString("history.restored"));
        } catch (Exception e) {
            logger.log(Level.WARNING, "History restore failed", e);
            updateStatus(getString("history.restore_failed"));
        }
    }

    /** Stored content of the note as currently persisted (raw via service read). */
    private String currentStoredContent(Note note) {
        return noteService.getNoteById(note.getId()).map(Note::getContent).orElse("");
    }

    /** Decrypts {@code JENC1:} content when the key is held this session; otherwise as-is. */
    private String readable(String content) {
        if (EncryptionService.isEncrypted(content) && EncryptionService.getInstance().hasKey()) {
            try {
                return EncryptionService.getInstance().decrypt(content);
            } catch (Exception e) {
                logger.fine("Snapshot decrypt failed: " + e.getMessage());
            }
        }
        return content;
    }

    /** Diff line renderer: colored via .diff-added / .diff-removed style classes. */
    private static final class DiffCell extends ListCell<LineDiff.Line> {
        @Override
        protected void updateItem(LineDiff.Line line, boolean empty) {
            super.updateItem(line, empty);
            getStyleClass().removeAll("diff-added", "diff-removed");
            if (empty || line == null) {
                setText(null);
                return;
            }
            switch (line.type()) {
                case ADDED -> {
                    setText("+ " + line.text());
                    getStyleClass().add("diff-added");
                }
                case REMOVED -> {
                    setText("- " + line.text());
                    getStyleClass().add("diff-removed");
                }
                default -> setText("  " + line.text());
            }
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
