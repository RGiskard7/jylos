package com.example.jylos.ui.components;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.util.KanbanModel;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A Kanban board in the spirit of Obsidian's Kanban plugin: a board is a note whose Markdown
 * body holds columns ({@code ## Heading}) of free-text cards, shown as a board in the note's
 * own tab. The user adds columns and cards, drags cards between columns, and a card may link
 * to a note ({@code [[Title]]}) or be converted into one.
 *
 * <p>It does not write the note: each change hands the board's new Markdown to its owner
 * (the editor), which saves it as it saves any edit. The format lives in
 * {@link KanbanModel}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class KanbanBoard extends VBox {

    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]|#]+)");

    private final NoteService noteService;
    /** Id of the board's note: what card links and images resolve from. */
    private final String boardId;
    private final BiConsumer<String, Boolean> onOpenNoteTitle;
    private final Consumer<String> onChanged;
    private final Consumer<Note> onNoteCreated;
    private final Function<String, String> i18n;

    private final HBox lanes = new HBox();

    private KanbanModel model;

    /**
     * @param noteService     where card notes are created and attachments found
     * @param boardId         the board's note id
     * @param markdown        the board note's text
     * @param onOpenNoteTitle a card's link clicked, and whether it opens in a new tab
     *                        (Cmd/Ctrl+click, middle click or "Open in new tab")
     * @param onChanged       the board changed: its new Markdown, for the owner to save
     * @param onNoteCreated   a card was turned into a note
     * @param i18n            translations by key
     */
    public KanbanBoard(NoteService noteService, String boardId, String markdown,
            BiConsumer<String, Boolean> onOpenNoteTitle, Consumer<String> onChanged,
            Consumer<Note> onNoteCreated, Function<String, String> i18n) {
        this.noteService = noteService;
        this.boardId = boardId;
        this.onOpenNoteTitle = onOpenNoteTitle;
        this.onChanged = onChanged;
        this.onNoteCreated = onNoteCreated;
        this.i18n = i18n;
        this.model = KanbanModel.parse(markdown);

        getStyleClass().add("kanban-board");
        setFocusTraversable(true);
        getChildren().add(buildScrollableLanes());
        render();
    }

    /** The board as Markdown, as it would be saved. */
    public String markdown() {
        return model.toMarkdown();
    }

    /** Adds a card at the end of a column, as the column's "Add card" does. */
    public void addCard(int column, String text) {
        if (column < 0 || column >= model.getColumns().size() || text == null || text.isBlank()) {
            return;
        }
        model.getColumns().get(column).getCards().add(text.trim());
        save();
        render();
    }

    // ── Boards ───────────────────────────────────────────────────────────────

    /** The vault's board notes, the most recently changed first. */
    public static List<Note> boards(NoteService noteService) {
        List<Note> boards = new ArrayList<>();
        if (noteService == null) {
            return boards;
        }
        for (Note n : noteService.getAllNotes()) {
            if (n != null && !n.isDeleted() && KanbanModel.isBoard(n.getContent())) {
                boards.add(n);
            }
        }
        boards.sort(Comparator.comparing((Note n) -> n.getModifiedDate() != null ? n.getModifiedDate() : "")
                .reversed());
        return boards;
    }

    private static Button iconButton(String iconLiteral, String iconStyle, String tip,
            EventHandler<ActionEvent> onAction) {
        FontIcon fi = new FontIcon(iconLiteral);
        fi.getStyleClass().add(iconStyle);
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

    private void save() {
        if (onChanged != null) {
            onChanged.accept(model.toMarkdown());
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────

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

        // Optional column color: a thin stripe across the top of the lane. The value is
        // validated to #rrggbb by KanbanModel, so it is safe to inline in a style.
        if (col.getColor() != null) {
            Region stripe = new Region();
            stripe.setMinHeight(3);
            stripe.setMaxHeight(3);
            stripe.setStyle("-fx-background-color: " + col.getColor()
                    + "; -fx-background-radius: 2;");
            lane.getChildren().add(stripe);
        }

        Label name = new Label(col.getTitle());
        name.getStyleClass().add("kanban-lane-header");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        // WIP limit is advisory: the count badge turns red when exceeded.
        int size = col.getCards().size();
        Label count = new Label(col.getWipLimit() > 0
                ? size + "/" + col.getWipLimit()
                : String.valueOf(size));
        count.getStyleClass().add("kanban-lane-count");
        if (col.getWipLimit() > 0 && size > col.getWipLimit()) {
            count.getStyleClass().add("kanban-wip-exceeded");
            count.setTooltip(new Tooltip(str("kanban.wip_exceeded", "WIP limit exceeded")));
        }
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
            addCard(model.getColumns().indexOf(col), text);
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

        // Image/PDF reference in the card text → embedded thumbnail.
        Node thumbnail = thumbnailFor(text);
        if (thumbnail != null) {
            card.getChildren().add(thumbnail);
        }

        // Drag source: carries column index + card text so the drop target can move it.
        card.setOnDragDetected(e -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString("kanbancard:" + model.getColumns().indexOf(col) + ":" + text);
            db.setContent(cc);
            e.consume();
        });
        // A plain click opens the card's link in the current tab, Cmd/Ctrl+click or a middle
        // click in a new one; a right click is for the menu.
        card.setOnMouseClicked(e -> {
            boolean newTab = NewTabGesture.opensInNewTab(e);
            if (e.getButton() != MouseButton.PRIMARY && !newTab) {
                return;
            }
            if (e.getClickCount() == 2 && !newTab) {
                editCard(col, text);
            } else if (linked != null && onOpenNoteTitle != null) {
                onOpenNoteTitle.accept(linked, newTab);
            }
        });
        card.setOnContextMenuRequested(e ->
                ContextMenus.show(cardContextMenu(col, text, linked), card, e.getScreenX(), e.getScreenY()));
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
            open.setOnAction(e -> { if (onOpenNoteTitle != null) onOpenNoteTitle.accept(linked, false); });
            MenuItem openInNewTab = new MenuItem(str("action.open_in_new_tab", "Open in new tab"));
            openInNewTab.setOnAction(e -> { if (onOpenNoteTitle != null) onOpenNoteTitle.accept(linked, true); });
            menu.getItems().addAll(open, openInNewTab);
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
        if (onNoteCreated != null) {
            onNoteCreated.accept(note);
        }
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

    private void showColumnMenu(KanbanModel.Column col, ActionEvent e) {
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem(str("kanban.rename_column", "Rename column"));
        rename.setOnAction(ev -> {
            String n = prompt(str("kanban.rename_column", "Rename column"),
                    str("kanban.column_name", "Column name:"), col.getTitle());
            if (n != null && !n.isBlank()) { col.setTitle(n.trim()); save(); render(); }
        });
        MenuItem wip = new MenuItem(str("kanban.wip_limit", "WIP limit…"));
        wip.setOnAction(ev -> {
            String n = prompt(str("kanban.wip_limit", "WIP limit…"),
                    str("kanban.wip_prompt", "Maximum cards (empty = no limit):"),
                    col.getWipLimit() > 0 ? String.valueOf(col.getWipLimit()) : "");
            if (n != null) {
                try {
                    col.setWipLimit(n.isBlank() ? 0 : Integer.parseInt(n.trim()));
                    save();
                    render();
                } catch (NumberFormatException ignored) {
                    // non-numeric input — leave the limit unchanged
                }
            }
        });
        MenuItem colorItem = new MenuItem(str("kanban.column_color", "Column color…"));
        colorItem.setOnAction(ev -> chooseColumnColor(col));
        MenuItem delete = new MenuItem(str("kanban.delete_column", "Delete column"));
        delete.setOnAction(ev -> { model.getColumns().remove(col); save(); render(); });
        menu.getItems().addAll(rename, wip, colorItem, delete);
        if (e.getSource() instanceof Node node) {
            menu.show(node, Side.BOTTOM, 0, 0);
        }
    }

    /** Color picker dialog with an explicit "clear" choice for the column color. */
    private void chooseColumnColor(KanbanModel.Column col) {
        Dialog<ButtonType> dialog =
                new Dialog<>();
        dialog.setTitle(str("kanban.column_color", "Column color…"));
        UiDialogs.apply(dialog);
        ButtonType clearType = new ButtonType(
                str("kanban.color_clear", "No color"), ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, clearType, ButtonType.CANCEL);
        ColorPicker picker = new ColorPicker();
        try {
            picker.setValue(Color.web(col.getColor() != null ? col.getColor() : "#7c3aed"));
        } catch (IllegalArgumentException ignored) {
            // fall back to the picker default
        }
        VBox box = new VBox(picker);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait().ifPresent(choice -> {
            if (choice == clearType) {
                col.setColor(null);
            } else if (choice == ButtonType.OK) {
                Color c = picker.getValue();
                col.setColor(String.format("#%02x%02x%02x",
                        (int) Math.round(c.getRed() * 255),
                        (int) Math.round(c.getGreen() * 255),
                        (int) Math.round(c.getBlue() * 255)));
            } else {
                return;
            }
            save();
            render();
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String prompt(String title, String header, String initial) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        ButtonType ok = new ButtonType(str("action.save", "Save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(str("action.cancel", "Cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancel);

        TextArea area = new TextArea(initial != null ? initial : "");
        area.setWrapText(true);
        area.setPrefRowCount(7);
        area.setPrefWidth(420);
        area.setPromptText(str("kanban.card_placeholder",
                "Card text… supports lists (- item), checkboxes (- [ ] task), wiki-links ([[Note]])"));
        // Tab inserts 2 spaces instead of moving focus, so indented lists work
        area.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB) {
                int pos = area.getCaretPosition();
                area.insertText(pos, "  ");
                e.consume();
            }
        });

        VBox content = new VBox(6,
                new Label(str("kanban.card_hint",
                        "Markdown supported: - list, - [ ] checkbox, **bold**, [[link]]")),
                area);
        content.setPadding(new Insets(12, 16, 4, 16));
        dialog.getDialogPane().setContent(content);

        // focus the textarea when the dialog opens
        dialog.setOnShown(e -> {
            area.requestFocus();
            area.positionCaret(area.getLength());
        });

        dialog.setResultConverter(bt -> bt == ok ? area.getText() : null);

        return UiDialogs.show(dialog).orElse(null);
    }

    /** Id of the board's note: the note card links resolve from. */
    public String boardId() {
        return boardId;
    }

    private static String firstLink(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = WIKILINK.matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }

    // ── Card thumbnails (image / PDF references) ─────────────────────────────

    /** Matches the first image/PDF reference: {@code ![alt](file.png)} or {@code [[file.pdf]]}. */
    private static final Pattern ATTACHMENT_REF = Pattern.compile(
            "!\\[[^\\]]*\\]\\(([^)\\s]+\\.(?:png|jpe?g|gif|pdf))\\)|\\[\\[([^\\]|]+\\.(?:png|jpe?g|gif|pdf))\\]\\]",
            Pattern.CASE_INSENSITIVE);

    private static final double THUMB_WIDTH = 200;
    private static final float PDF_THUMB_DPI = 36;

    /**
     * Builds a thumbnail node when the card references an image or PDF, or null.
     * Paths resolve absolutely or relative to the board note's directory (walking up
     * a few levels covers vault-root-relative attachment links). PDF thumbnails render
     * the first page at low DPI via PDFBox. Any failure simply yields no thumbnail.
     */
    private Node thumbnailFor(String cardText) {
        if (cardText == null) {
            return null;
        }
        Matcher m = ATTACHMENT_REF.matcher(cardText);
        if (!m.find()) {
            return null;
        }
        String ref = m.group(1) != null ? m.group(1) : m.group(2);
        Path file = resolveAttachment(ref.strip());
        if (file == null) {
            return null;
        }
        try {
            Image image = ref.toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? pdfThumbnail(file)
                    : new Image(file.toUri().toString(), THUMB_WIDTH, 0, true, true);
            if (image == null || image.isError()) {
                return null;
            }
            ImageView view = new ImageView(image);
            view.setFitWidth(THUMB_WIDTH);
            view.setPreserveRatio(true);
            view.getStyleClass().add("kanban-card-thumb");
            return view;
        } catch (Exception e) {
            return null;
        }
    }

    private Path resolveAttachment(String ref) {
        try {
            Path p = Path.of(ref);
            if (p.isAbsolute()) {
                return Files.exists(p) ? p : null;
            }
            if (noteService != null && boardId != null) {
                Optional<Path> boardFile = noteService.getNoteFilePath(boardId);
                if (boardFile.isPresent()) {
                    Path base = boardFile.get().getParent();
                    for (int depth = 0; base != null && depth < 4; depth++, base = base.getParent()) {
                        Path candidate = base.resolve(ref);
                        if (Files.exists(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        } catch (InvalidPathException ignored) {
            // malformed reference — no thumbnail
        }
        return null;
    }

    /** First PDF page as a small image (PDFBox render + PNG round-trip into JavaFX). */
    private static Image pdfThumbnail(Path pdf) {
        try (PDDocument doc =
                PDDocument.load(pdf.toFile())) {
            if (doc.getNumberOfPages() == 0) {
                return null;
            }
            BufferedImage page = new PDFRenderer(doc)
                    .renderImageWithDPI(0, PDF_THUMB_DPI, ImageType.RGB);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(page, "png", baos);
                return new Image(
                        new ByteArrayInputStream(baos.toByteArray()), THUMB_WIDTH, 0, true, true);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String str(String key, String fallback) {
        if (i18n == null) {
            return fallback;
        }
        String v = i18n.apply(key);
        return v != null && !v.equals(key) ? v : fallback;
    }
}
