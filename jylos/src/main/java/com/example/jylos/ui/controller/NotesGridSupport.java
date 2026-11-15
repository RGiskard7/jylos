package com.example.jylos.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.components.NewTabGesture;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The notes list's grid view: the notes as cards in rows, built only for the rows on screen,
 * re-flowed when the panel's width changes, and swapped with the list in the notes panel. A
 * card opens its note, shows the note's context menu and can be dragged onto a folder.
 */
final class NotesGridSupport {

    /** What the grid reads from, and asks of, the notes list. */
    interface Host {
        /** The notes list: what the grid shows, and the node it takes the place of. */
        ListView<Note> notesList();

        /** Selects {@code note} in the list without opening it (before showing its menu). */
        void selectWithoutOpening(Note note);

        boolean dragInProgress();

        void setDragInProgress(boolean inProgress);

        void openInNewTab(Note note);

        /** The note's context menu, whose "open" runs {@code open}. */
        ContextMenu menuFor(Note note, Runnable open);
    }

    private final Host host;

    NotesGridSupport(Host host) {
        this.host = host;
    }

    /**
     * The grid, as a virtualized list of rows: each item is one row of cards.
     *
     * <p>It used to be a {@code TilePane} of every card inside a {@code ScrollPane}, which
     * builds one card — six-odd nodes each — for every note in the vault and keeps them
     * all in the scene graph. A few thousand notes is tens of thousands of live nodes.
     * A {@code ListView} only materialises the rows on screen, and the card itself is
     * unchanged: {@link #createNoteCard} still produces it.</p>
     */
    private ListView<List<Note>> notesGridView;
    private VBox notesPanelContainer;
    private String lastGridSignature = "";
    /** Notes currently shown in the grid, kept so a width change can re-chunk them. */
    private List<Note> gridNotes = List.of();
    private int gridColumns = 0;

    /** Card width and gap from {@link #createNoteCard}; used to work out how many fit. */
    private static final double GRID_CARD_WIDTH = 180;
    private static final double GRID_GAP = 12;
    private static final double GRID_ROW_HEIGHT = 152; // card prefHeight (140) + gap

    void initialize() {
        if (notesGridView != null) {
            return;
        }
        notesGridView = new ListView<>();
        notesGridView.getStyleClass().add("notes-grid-scroll");
        notesGridView.setStyle("-fx-background-color: transparent;");
        notesGridView.setFixedCellSize(GRID_ROW_HEIGHT);
        notesGridView.setFocusTraversable(false);
        // The selection model is left alone on purpose: nulling it is a documented source
        // of NPEs inside JavaFX's own list behaviour, and it buys nothing here — the row
        // cell paints a transparent background inline, which already wins over the
        // selected-row highlight.
        notesGridView.setCellFactory(view -> new GridRowCell());

        // The TilePane reflowed its columns as the panel resized; keep that. Re-chunking
        // only when the column count actually changes means a drag resize does not rebuild
        // rows on every pixel.
        notesGridView.widthProperty().addListener((obs, previous, width) -> {
            if (columnsFor(width.doubleValue()) != gridColumns) {
                rebuildGridRows();
            }
        });

        ListView<Note> notesListView = host.notesList();
        if (notesListView != null && notesListView.getParent() instanceof VBox parent) {
            notesPanelContainer = parent;
        }
    }

    /**
     * How many cards fit across {@code width}, at least one. Subtracts the row's own left
     * and right padding first: counting on the full width overfills the row at just over
     * a multiple of the card pitch (768px gave four cards needing 780px) and clips the
     * last card.
     */
    private static int columnsFor(double width) {
        double usable = width - (2 * GRID_GAP);
        if (usable <= 0) {
            return 1;
        }
        // +GRID_GAP because n cards need only n-1 gaps between them.
        return Math.max(1, (int) ((usable + GRID_GAP) / (GRID_CARD_WIDTH + GRID_GAP)));
    }

    /** Re-chunks {@link #gridNotes} into rows for the current width. */
    private void rebuildGridRows() {
        if (notesGridView == null) {
            return;
        }
        gridColumns = columnsFor(notesGridView.getWidth());
        List<List<Note>> rows = new ArrayList<>();
        for (int start = 0; start < gridNotes.size(); start += gridColumns) {
            rows.add(List.copyOf(gridNotes.subList(start, Math.min(start + gridColumns, gridNotes.size()))));
        }
        notesGridView.getItems().setAll(rows);
    }

    /** Renders one row of note cards. Cells are reused, so the row is rebuilt on update. */
    private final class GridRowCell extends ListCell<List<Note>> {
        private final HBox row = new HBox(GRID_GAP);

        private GridRowCell() {
            row.setPadding(new javafx.geometry.Insets(GRID_GAP / 2, GRID_GAP, GRID_GAP / 2, GRID_GAP));
            getStyleClass().add("notes-grid-row");
            setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        }

        @Override
        protected void updateItem(List<Note> notes, boolean empty) {
            super.updateItem(notes, empty);
            if (empty || notes == null || gridCardFactory == null) {
                setGraphic(null);
                return;
            }
            row.getChildren().clear();
            for (Note note : notes) {
                row.getChildren().add(gridCardFactory.apply(note));
            }
            setGraphic(row);
        }
    }

    /**
     * Builds a card for one note, capturing the theme/i18n/callbacks the last refresh was
     * given. Cells are created lazily as the user scrolls, long after
     * {@link #refreshGridView} returned, so those arguments have to be reachable from here.
     */
    private Function<Note, VBox> gridCardFactory;

    private VBox applyNotesViewMode(
            boolean isGridMode,
            ListView<Note> notesListView,
            javafx.scene.Node gridNode,
            VBox currentNotesPanelContainer,
            Runnable refreshGridViewAction,
            Consumer<String> warningLogger) {
        if (notesListView == null || gridNode == null) {
            return currentNotesPanelContainer;
        }

        VBox notesPanelContainer = currentNotesPanelContainer;
        if (notesPanelContainer == null) {
            Parent parent = notesListView.getParent();
            if (parent instanceof VBox) {
                notesPanelContainer = (VBox) parent;
            } else {
                parent = gridNode.getParent();
                if (parent instanceof VBox) {
                    notesPanelContainer = (VBox) parent;
                }
            }
        }

        if (notesPanelContainer == null) {
            if (warningLogger != null) {
                warningLogger.accept("Could not find notes panel container");
            }
            return null;
        }

        VBox resolvedContainer = notesPanelContainer;
        Runnable applyViewSwitch = () -> {
            if (isGridMode) {
                if (resolvedContainer.getChildren().contains(notesListView)) {
                    resolvedContainer.getChildren().remove(notesListView);
                }
                if (!resolvedContainer.getChildren().contains(gridNode)) {
                    resolvedContainer.getChildren().add(gridNode);
                    VBox.setVgrow(gridNode, Priority.ALWAYS);
                }
                if (refreshGridViewAction != null) {
                    refreshGridViewAction.run();
                }
            } else {
                if (resolvedContainer.getChildren().contains(gridNode)) {
                    resolvedContainer.getChildren().remove(gridNode);
                }
                if (!resolvedContainer.getChildren().contains(notesListView)) {
                    resolvedContainer.getChildren().add(notesListView);
                    VBox.setVgrow(notesListView, Priority.ALWAYS);
                }
            }
        };

        if (Platform.isFxApplicationThread()) {
            applyViewSwitch.run();
        } else {
            Platform.runLater(applyViewSwitch);
        }

        return notesPanelContainer;
    }

    void refresh(
            ListView<Note> notesListView,
            boolean isDarkTheme,
            Function<String, String> i18n,
            Consumer<Note> openNoteAction,
            Consumer<String> statusUpdate) {
        if (notesGridView == null || notesListView == null || i18n == null || openNoteAction == null
                || statusUpdate == null) {
            return;
        }

        List<Note> notes = new ArrayList<>(notesListView.getItems());
        StringBuilder signatureBuilder = new StringBuilder(notes.size() * 24);
        signatureBuilder.append(isDarkTheme ? "dark|" : "light|");
        for (Note note : notes) {
            signatureBuilder.append(note.getId()).append('|')
                    .append(note.getModifiedDate() != null ? note.getModifiedDate() : "")
                    .append('|').append(note.isPinned() ? '1' : '0')
                    .append(note.isFavorite() ? '1' : '0').append(';');
        }
        String signature = signatureBuilder.toString();
        if (signature.equals(lastGridSignature) && gridNotes.size() == notes.size()) {
            return;
        }

        // Cards are built on demand by the cell factory as rows scroll into view, so what
        // is stored here is how to build one — not thousands of already-built cards.
        gridCardFactory = note -> createNoteCard(note, notesListView, isDarkTheme, i18n, openNoteAction,
                statusUpdate);
        gridNotes = notes;
        rebuildGridRows();
        lastGridSignature = signature;
    }

    private VBox createNoteCard(
            Note note,
            ListView<Note> notesListView,
            boolean isDarkTheme,
            Function<String, String> i18n,
            Consumer<Note> openNoteAction,
            Consumer<String> statusUpdate) {
        VBox card = new VBox(8);
        card.setPrefWidth(180);
        card.setPrefHeight(140);
        card.setPadding(new javafx.geometry.Insets(12));
        card.getStyleClass().add("note-card");
        card.setCursor(javafx.scene.Cursor.HAND);

        HBox titleRow = new HBox(5);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        if (note.isPinned()) {
            FontIcon pinIcon = new FontIcon("bi-pin-angle");
            pinIcon.getStyleClass().add("feather-pin-active");
            pinIcon.setIconSize(12);
            titleRow.getChildren().add(pinIcon);
        }

        if (note.isFavorite()) {
            FontIcon favIcon = new FontIcon("fth-star");
            favIcon.getStyleClass().add("feather-favorite-active");
            favIcon.setIconSize(12);
            titleRow.getChildren().add(favIcon);
        }

        Label titleLabel = new Label(note.getTitle() != null ? note.getTitle() : i18n.apply("app.untitled"));
        titleLabel.getStyleClass().add("note-card-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxHeight(40);
        titleRow.getChildren().add(titleLabel);

        String preview = note.getContent() != null && !note.getContent().isEmpty()
                ? note.getContent().replaceAll("^#+\\s*", "").replaceAll("\\n", " ").trim()
                : "";
        if (preview.length() > 80) {
            preview = preview.substring(0, 77) + "...";
        }
        Label previewLabel = new Label(preview);
        previewLabel.getStyleClass().add("note-card-preview");
        previewLabel.setWrapText(true);
        previewLabel.setMaxHeight(60);
        VBox.setVgrow(previewLabel, Priority.ALWAYS);

        String dateText = note.getModifiedDate() != null ? note.getModifiedDate() : note.getCreatedDate();
        if (dateText != null && dateText.length() > 10) {
            dateText = dateText.substring(0, 10);
        }
        Label dateLabel = new Label(dateText != null ? dateText : "");
        dateLabel.getStyleClass().add("note-card-date");

        card.getChildren().addAll(titleRow, previewLabel, dateLabel);

        card.setOnMouseClicked(e -> {
            if (host.dragInProgress()) {
                return;
            }
            if (NewTabGesture.opensInNewTab(e)) {
                host.openInNewTab(note);
                return;
            }
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            notesListView.getSelectionModel().select(note);
            openNoteAction.accept(note);
        });
        card.setOnContextMenuRequested(event -> {
            host.selectWithoutOpening(note);
            ContextMenu contextMenu = host.menuFor(note, () -> {
                notesListView.getSelectionModel().select(note);
                openNoteAction.accept(note);
            });
            if (contextMenu != null) {
                contextMenu.show(card, event.getScreenX(), event.getScreenY());
            }
        });

        setupNoteCardDrag(card, note, i18n, statusUpdate);
        return card;
    }


    private void setupNoteCardDrag(
            VBox card,
            Note note,
            Function<String, String> i18n,
            Consumer<String> statusUpdate) {
        card.setOnDragDetected(event -> {
            host.setDragInProgress(true);
            javafx.scene.input.Dragboard db = card.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString("note:" + note.getId());
            db.setContent(content);

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            db.setDragView(card.snapshot(params, null));

            event.consume();
            statusUpdate.accept(java.text.MessageFormat.format(i18n.apply("status.dragging"), note.getTitle()));
        });

        card.setOnDragDone(event -> {
            host.setDragInProgress(false);
            if (event.getTransferMode() == javafx.scene.input.TransferMode.MOVE) {
                statusUpdate.accept(i18n.apply("status.note_moved"));
            }
            event.consume();
        });
    }

    /** Puts the grid in the notes panel in place of the list, or the list back. */
    void applyViewMode(boolean gridMode, Runnable gridRefreshAction, Consumer<String> warningLogger) {
        notesPanelContainer = applyNotesViewMode(gridMode, host.notesList(), notesGridView, notesPanelContainer,
                gridRefreshAction, warningLogger);
    }

    /** The list's notes changed: the next refresh draws the grid again, even if they look the same. */
    void forgetShownNotes() {
        lastGridSignature = "";
    }
}
