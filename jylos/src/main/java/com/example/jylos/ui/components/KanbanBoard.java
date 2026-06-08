package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;
import com.example.jylos.util.KanbanModel;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A Kanban board overlay in the spirit of Obsidian/Trello: a board is a note whose
 * Markdown body holds columns ({@code ## Heading}) of free-text cards. The user
 * picks/creates boards, adds columns and cards, drags cards between columns, and a
 * card may link to a note ({@code [[Title]]}) or be converted into one.
 *
 * <p>Each change is serialised back to the board note via {@link NoteService}; the
 * board parsing/format lives in {@link KanbanModel}. The toolbar mirrors the graph
 * overlay (same style classes and padding).</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class KanbanBoard extends VBox {

    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]|#]+)");

    private final NoteService noteService;
    private final Consumer<String> onOpenNoteTitle;
    private final Runnable onClose;
    private final Function<String, String> i18n;

    private final ComboBox<Note> boardSelector = new ComboBox<>();
    private final HBox lanes = new HBox();

    private Note currentBoard;
    private KanbanModel model = new KanbanModel();
    /** Guards selector listener while we repopulate it programmatically. */
    private boolean populatingSelector = false;

    public KanbanBoard(NoteService noteService,
            Consumer<String> onOpenNoteTitle,
            Runnable onClose,
            Function<String, String> i18n) {
        this.noteService = noteService;
        this.onOpenNoteTitle = onOpenNoteTitle;
        this.onClose = onClose;
        this.i18n = i18n;

        getStyleClass().add("kanban-board");
        setFocusTraversable(true);
        getChildren().addAll(buildToolbar(), buildScrollableLanes());

        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && onClose != null) {
                onClose.run();
                e.consume();
            }
        });
    }

    // ------------------------------------------------------------------
    // Toolbar
    // ------------------------------------------------------------------

    private HBox buildToolbar() {
        HBox toolbar = new HBox();
        toolbar.getStyleClass().add("graph-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setSpacing(8);
        toolbar.setPadding(new Insets(8, 12, 8, 14)); // match GraphView.fxml toolbar

        FontIcon icon = new FontIcon("fth-columns");
        icon.getStyleClass().add("feather-icon");
        Label title = new Label(str("kanban.title", "Kanban"));
        title.getStyleClass().add("graph-title");

        boardSelector.getStyleClass().add("kanban-board-selector");
        boardSelector.setPromptText(str("kanban.select_board", "Select board…"));
        boardSelector.setButtonCell(boardCell());
        boardSelector.setCellFactory(lv -> boardCell());
        boardSelector.valueProperty().addListener((obs, old, sel) -> {
            if (!populatingSelector && sel != null) {
                loadBoard(sel);
            }
        });

        Button newBoard = iconButton("fth-plus", "feather-icon",
                str("kanban.new_board", "New board"), e -> createBoard());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refresh = iconButton("fth-refresh-cw", "feather-icon",
                str("kanban.refresh", "Refresh"), e -> reload());
        Button close = iconButton("fth-x", "feather-icon-danger",
                str("kanban.close", "Close board"), e -> { if (onClose != null) onClose.run(); });
        close.getStyleClass().add("toolbar-btn-danger");

        toolbar.getChildren().addAll(icon, title, boardSelector, newBoard, spacer, refresh, close);
        return toolbar;
    }

    private ListCell<Note> boardCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Note n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null
                        : (n.getTitle() == null || n.getTitle().isBlank()
                                ? str("kanban.untitled", "(untitled)") : n.getTitle()));
            }
        };
    }

    private static Button iconButton(String iconLiteral, String iconStyle, String tip,
            javafx.event.EventHandler<javafx.event.ActionEvent> onAction) {
        FontIcon fi = new FontIcon(iconLiteral);
        fi.getStyleClass().add(iconStyle);
        // .feather-icon-danger doesn't define a size; pin 16 so every toolbar icon
        // (including the close X) matches .feather-icon exactly, like the graph toolbar.
        fi.setIconSize(16);
        Button b = new Button();
        b.setGraphic(fi);
        b.getStyleClass().add("toolbar-btn");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(onAction);
        return b;
    }

    private ScrollPane buildScrollableLanes() {
        lanes.getStyleClass().add("kanban-lanes");
        ScrollPane scroll = new ScrollPane(lanes);
        scroll.getStyleClass().add("kanban-scroll");
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    public void setDarkTheme(boolean dark) {
        getStyleClass().remove("kanban-dark");
        if (dark) {
            getStyleClass().add("kanban-dark");
        }
    }

    // ------------------------------------------------------------------
    // Board loading / persistence
    // ------------------------------------------------------------------

    /** Reloads the list of board notes and re-renders the current (or first) board. */
    public void reload() {
        List<Note> boards = findBoards();
        populatingSelector = true;
        boardSelector.getItems().setAll(boards);
        populatingSelector = false;

        Note toShow = null;
        if (currentBoard != null) {
            for (Note b : boards) {
                if (b.getId() != null && b.getId().equals(currentBoard.getId())) {
                    toShow = b;
                    break;
                }
            }
        }
        if (toShow == null && !boards.isEmpty()) {
            toShow = boards.get(0);
        }
        if (toShow != null) {
            boardSelector.setValue(toShow);
            loadBoard(toShow);
        } else {
            currentBoard = null;
            model = new KanbanModel();
            renderEmpty();
        }
    }

    private List<Note> findBoards() {
        List<Note> boards = new ArrayList<>();
        if (noteService == null) {
            return boards;
        }
        for (Note n : noteService.getAllNotes()) {
            if (n != null && !n.isDeleted() && KanbanModel.isBoard(n.getContent())) {
                boards.add(n);
            }
        }
        boards.sort((a, b) -> safeTitle(a).compareToIgnoreCase(safeTitle(b)));
        return boards;
    }

    private void loadBoard(Note boardRef) {
        // Read full content (getAllNotes may return a truncated body in vault mode).
        Note full = noteService != null && boardRef.getId() != null
                ? noteService.getNoteById(boardRef.getId()).orElse(boardRef) : boardRef;
        currentBoard = full;
        model = KanbanModel.parse(full.getContent());
        render();
    }

    private void save() {
        if (currentBoard == null || noteService == null) {
            return;
        }
        currentBoard.setContent(model.toMarkdown());
        noteService.updateNote(currentBoard);
        com.example.jylos.event.EventBus.getInstance()
                .publish(new com.example.jylos.event.events.NoteEvents.NoteUpdatedEvent(currentBoard));
    }

    private void createBoard() {
        if (noteService == null) {
            return;
        }
        String name = prompt(str("kanban.new_board", "New board"),
                str("kanban.board_name", "Board name:"), str("kanban.board_default", "My board"));
        if (name == null || name.isBlank()) {
            return;
        }
        KanbanModel fresh = KanbanModel.withDefaults(
                str("kanban.col_todo", "To do"),
                str("kanban.col_doing", "Doing"),
                str("kanban.col_done", "Done"));
        Note board = noteService.createNote(new Note(name.trim(), fresh.toMarkdown()));
        com.example.jylos.event.EventBus.getInstance()
                .publish(new com.example.jylos.event.events.NoteEvents.NoteCreatedEvent(board));
        currentBoard = board;
        reload();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void renderEmpty() {
        lanes.getChildren().clear();
        VBox empty = new VBox(12);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(40));
        Label msg = new Label(str("kanban.no_boards", "No boards yet."));
        msg.getStyleClass().add("kanban-lane-header");
        Button create = new Button("+ " + str("kanban.new_board", "New board"));
        create.getStyleClass().add("kanban-add-card");
        create.setOnAction(e -> createBoard());
        empty.getChildren().addAll(msg, create);
        lanes.getChildren().add(empty);
    }

    private void render() {
        lanes.getChildren().clear();
        for (KanbanModel.Column col : model.getColumns()) {
            lanes.getChildren().add(buildLane(col));
        }
        lanes.getChildren().add(buildAddColumnLane());
    }

    private VBox buildLane(KanbanModel.Column col) {
        VBox lane = new VBox();
        lane.getStyleClass().add("kanban-lane");

        Label name = new Label(col.getTitle());
        name.getStyleClass().add("kanban-lane-header");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Label count = new Label(String.valueOf(col.getCards().size()));
        count.getStyleClass().add("kanban-lane-count");
        Button colMenu = iconButton("fth-more-horizontal", "feather-icon",
                str("kanban.column_menu", "Column options"), e -> showColumnMenu(col, e));
        colMenu.getStyleClass().clear();
        colMenu.getStyleClass().add("kanban-icon-btn");
        HBox header = new HBox(6, name, count, colMenu);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox cards = new VBox();
        cards.getStyleClass().add("kanban-cards");
        for (String card : col.getCards()) {
            cards.getChildren().add(buildCard(col, card));
        }

        ScrollPane scroll = new ScrollPane(cards);
        scroll.getStyleClass().add("kanban-lane-scroll");
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button addCard = new Button("+ " + str("kanban.add_card", "Add card"));
        addCard.getStyleClass().add("kanban-add-card");
        addCard.setMaxWidth(Double.MAX_VALUE);
        addCard.setOnAction(e -> {
            String text = prompt(str("kanban.add_card", "Add card"),
                    str("kanban.card_text", "Card text:"), "");
            if (text != null && !text.isBlank()) {
                col.getCards().add(text.trim());
                save();
                render();
            }
        });

        lane.getChildren().addAll(header, scroll, addCard);
        installColumnDropTarget(lane, col);
        return lane;
    }

    private VBox buildAddColumnLane() {
        VBox lane = new VBox();
        lane.getStyleClass().add("kanban-lane-add");
        Button addCol = new Button("+ " + str("kanban.add_column", "Add column"));
        addCol.getStyleClass().add("kanban-add-card");
        addCol.setMaxWidth(Double.MAX_VALUE);
        addCol.setOnAction(e -> {
            String name = prompt(str("kanban.add_column", "Add column"),
                    str("kanban.column_name", "Column name:"), "");
            if (name != null && !name.isBlank()) {
                model.addColumn(name.trim());
                save();
                render();
            }
        });
        lane.getChildren().add(addCol);
        return lane;
    }

    private VBox buildCard(KanbanModel.Column col, String text) {
        VBox card = new VBox();
        card.getStyleClass().add("kanban-card");

        String linked = firstLink(text);
        Label label = new Label(text);
        label.getStyleClass().add("kanban-card-title");
        label.setWrapText(true);
        if (linked != null) {
            label.getStyleClass().add("kanban-card-link");
        }
        card.getChildren().add(label);

        // Drag source: carries column index + card text so the drop target can move it.
        card.setOnDragDetected(e -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString("kanbancard:" + model.getColumns().indexOf(col) + ":" + text);
            db.setContent(cc);
            e.consume();
        });
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                editCard(col, text);
            } else if (linked != null && onOpenNoteTitle != null) {
                onOpenNoteTitle.accept(linked);
            }
        });
        card.setOnContextMenuRequested(e ->
                cardContextMenu(col, text, linked).show(card, e.getScreenX(), e.getScreenY()));
        return card;
    }

    private ContextMenu cardContextMenu(KanbanModel.Column col, String text, String linked) {
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem(str("action.edit", "Edit"));
        edit.setOnAction(e -> editCard(col, text));
        MenuItem delete = new MenuItem(str("action.delete", "Delete"));
        delete.setOnAction(e -> { col.getCards().remove(text); save(); render(); });
        menu.getItems().addAll(edit, delete);
        if (linked != null) {
            MenuItem open = new MenuItem(str("kanban.open_note", "Open linked note"));
            open.setOnAction(e -> { if (onOpenNoteTitle != null) onOpenNoteTitle.accept(linked); });
            menu.getItems().add(open);
        } else {
            MenuItem convert = new MenuItem(str("kanban.convert_note", "Convert to note"));
            convert.setOnAction(e -> convertToNote(col, text));
            menu.getItems().add(convert);
        }
        return menu;
    }

    private void editCard(KanbanModel.Column col, String text) {
        String edited = prompt(str("kanban.edit_card", "Edit card"),
                str("kanban.card_text", "Card text:"), text);
        if (edited != null && !edited.isBlank()) {
            int idx = col.getCards().indexOf(text);
            if (idx >= 0) {
                col.getCards().set(idx, edited.trim());
                save();
                render();
            }
        }
    }

    /** Creates a note titled after the card and replaces the card text with a link to it. */
    private void convertToNote(KanbanModel.Column col, String text) {
        if (noteService == null) {
            return;
        }
        Note note = noteService.createNote(new Note(text.trim(), ""));
        com.example.jylos.event.EventBus.getInstance()
                .publish(new com.example.jylos.event.events.NoteEvents.NoteCreatedEvent(note));
        int idx = col.getCards().indexOf(text);
        if (idx >= 0) {
            col.getCards().set(idx, "[[" + text.trim() + "]]");
            save();
            render();
        }
    }

    private void installColumnDropTarget(VBox lane, KanbanModel.Column target) {
        lane.setOnDragOver(e -> {
            if (e.getDragboard().hasString() && e.getDragboard().getString().startsWith("kanbancard:")) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        lane.setOnDragEntered(e -> { lane.getStyleClass().add("kanban-lane-drop"); e.consume(); });
        lane.setOnDragExited(e -> { lane.getStyleClass().remove("kanban-lane-drop"); e.consume(); });
        lane.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean ok = false;
            if (db.hasString() && db.getString().startsWith("kanbancard:")) {
                String payload = db.getString().substring("kanbancard:".length());
                int sep = payload.indexOf(':');
                if (sep > 0) {
                    int fromCol = parseInt(payload.substring(0, sep));
                    String text = payload.substring(sep + 1);
                    if (fromCol >= 0 && fromCol < model.getColumns().size()) {
                        KanbanModel.Column from = model.getColumns().get(fromCol);
                        if (from.getCards().remove(text)) {
                            target.getCards().add(text);
                            save();
                            render();
                            ok = true;
                        }
                    }
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    private void showColumnMenu(KanbanModel.Column col, javafx.event.ActionEvent e) {
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem(str("kanban.rename_column", "Rename column"));
        rename.setOnAction(ev -> {
            String n = prompt(str("kanban.rename_column", "Rename column"),
                    str("kanban.column_name", "Column name:"), col.getTitle());
            if (n != null && !n.isBlank()) { col.setTitle(n.trim()); save(); render(); }
        });
        MenuItem delete = new MenuItem(str("kanban.delete_column", "Delete column"));
        delete.setOnAction(ev -> { model.getColumns().remove(col); save(); render(); });
        menu.getItems().addAll(rename, delete);
        if (e.getSource() instanceof javafx.scene.Node node) {
            menu.show(node, javafx.geometry.Side.BOTTOM, 0, 0);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String prompt(String title, String header, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial != null ? initial : "");
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        return com.example.jylos.ui.UiDialogs.show(dialog).orElse(null);
    }

    private static String firstLink(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = WIKILINK.matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String safeTitle(Note n) {
        return n.getTitle() != null ? n.getTitle() : "";
    }

    private String str(String key, String fallback) {
        if (i18n == null) {
            return fallback;
        }
        String v = i18n.apply(key);
        return v != null ? v : fallback;
    }
}
