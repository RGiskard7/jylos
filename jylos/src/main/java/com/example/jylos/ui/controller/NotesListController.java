package com.example.jylos.ui.controller;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.config.AppContext;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.event.events.UIEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import java.util.prefs.Preferences;
import java.io.File;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.shape.Rectangle;

/**
 * Notes list column: custom cells (title, preview lines, dates), sorting, drag-and-drop,
 * and context actions. Listens to {@link com.example.jylos.event.EventBus} for refresh.
 */
public class NotesListController {
    private static final Logger logger = LoggerConfig.getLogger(NotesListController.class);

    private EventBus eventBus;
    private NoteService noteService;
    private TagService tagService;
    private FolderService folderService;
    private ResourceBundle bundle;

    private String currentFilterType = "all";
    private Folder currentFolder;
    private Tag currentTag;
    private final ExecutorService notesLoadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jylos-notes-loader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong notesLoadVersion = new AtomicLong(0);
    private volatile List<Note> allNotesSearchCache = List.of();
    private volatile boolean allNotesSearchCacheDirty = true;
    /** Lazy cache of full, lowercased note content for true full-text search. */
    private final java.util.Map<String, String> fullContentCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Number of body-preview lines shown under each note title (0 hides the preview). */
    private int previewLines = 2;

    @FXML
    private VBox notesPanel;
    @FXML
    private HBox stackedModeHeader;
    @FXML
    private Label notesPanelTitleLabel;
    @FXML
    private ComboBox<String> sortComboBox;
    @FXML
    private Button refreshBtn;
    @FXML
    private ListView<Note> notesListView;

    @FXML
    public void initialize() {
        // Publish event when a note is selected
        notesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && eventBus != null) {
                eventBus.publish(new NoteEvents.NoteSelectedEvent(newVal));
            }
        });

        // Trigger sort on combo box change
        sortComboBox.valueProperty().addListener((obs, oldVal, newVal) -> sortNotes(newVal));

        notesListView.setMinWidth(0);
        if (notesPanel != null) {
            notesListView.maxWidthProperty().bind(notesPanel.widthProperty());
            notesListView.prefWidthProperty().bind(notesPanel.widthProperty());
        }
        if (notesPanelTitleLabel != null) {
            notesPanelTitleLabel.setEllipsisString("…");
            notesPanelTitleLabel.setMinWidth(0);
            notesPanelTitleLabel.setMaxWidth(Double.MAX_VALUE);
        }
        notesListView.setCellFactory(this::createNoteListCell);
        recomputeCellSize();
        disableHorizontalScrollOnListView(notesListView);
    }

    private static void disableHorizontalScrollOnListView(ListView<?> listView) {
        if (listView == null) {
            return;
        }
        Runnable disableHbar = () -> listView.lookupAll(".scroll-bar").forEach(node -> {
            if (node instanceof ScrollBar scrollBar
                    && scrollBar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) {
                scrollBar.setVisible(false);
                scrollBar.setManaged(false);
                scrollBar.setPrefWidth(0);
                scrollBar.setMaxWidth(0);
            }
        });
        Platform.runLater(disableHbar);
        listView.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(disableHbar));
    }

    private static double verticalScrollBarWidth(ListView<?> listView) {
        if (listView == null) {
            return 0;
        }
        double width = 0;
        for (javafx.scene.Node node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar scrollBar
                    && scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                    && scrollBar.isVisible()) {
                width = Math.max(width, scrollBar.getWidth());
            }
        }
        return width > 0 ? width : 14;
    }

    /**
     * Sets how many body-preview lines are shown per note in the list.
     * Clamped to [0, 5]; 0 hides the preview entirely.
     *
     * @param lines requested preview line count
     */
    public void setPreviewLines(int lines) {
        int clamped = Math.max(0, Math.min(5, lines));
        if (clamped == previewLines) {
            return;
        }
        previewLines = clamped;
        recomputeCellSize();
        if (notesListView != null) {
            notesListView.refresh();
        }
    }

    /** Recomputes the fixed cell height from the current preview-line count. */
    private void recomputeCellSize() {
        if (notesListView == null) {
            return;
        }
        int height = 26 + (previewLines > 0 ? previewLines * 17 + 6 : 0) + 20 + 12;
        notesListView.setFixedCellSize(height);
    }

    private ListCell<Note> createNoteListCell(ListView<Note> listView) {
        return new ListCell<>() {
            private static final double NOTE_ICON_RESERVED_WIDTH = 20;
            private static final double PREVIEW_LINE_HEIGHT = 17;
            private static final double CELL_HORIZONTAL_PADDING = 14;
            private static final double DATE_ROW_GAP = 6;
            private static final double NARROW_PANEL_WIDTH = 210;

            private final VBox container = new VBox(3);
            private final HBox titleRow = new HBox(6);
            private final FontIcon pinIcon = new FontIcon("fth-map-pin");
            private final FontIcon favIcon = new FontIcon("fth-star");
            private final Label titleLabel = new Label();
            private final FontIcon noteIcon = new FontIcon("fth-file-text");
            private final StackPane noteIconHolder = new StackPane();
            private final Label previewLabel = new Label();
            private final HBox dateRow = new HBox(DATE_ROW_GAP);
            private final Label modifiedLabel = new Label();
            private final Label createdLabel = new Label();
            private final Rectangle clip = new Rectangle();
            private final ContextMenu contextMenu = new ContextMenu();
            private final MenuItem openItem = new MenuItem(getString("action.open"));
            private final MenuItem favoriteItem = new MenuItem();
            private final MenuItem revealItem = new MenuItem(getString("action.reveal_in_files"));
            private final MenuItem exportItem = new MenuItem(getString("action.export_note"));
            private final MenuItem deleteItem = new MenuItem(getString("action.move_to_trash"));

            {
                container.getStyleClass().add("note-cell-container");
                container.setPadding(new javafx.geometry.Insets(4, 6, 4, 4));
                setAlignment(javafx.geometry.Pos.TOP_LEFT);
                setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                setMinWidth(0);

                titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                titleRow.setMinWidth(0);
                pinIcon.getStyleClass().add("feather-pin-active");
                pinIcon.setIconSize(12);
                favIcon.setIconColor(javafx.scene.paint.Color.GOLD);
                favIcon.setIconSize(12);

                titleLabel.getStyleClass().add("note-cell-title");
                titleLabel.setEllipsisString("…");
                titleLabel.setMinWidth(0);
                HBox.setHgrow(titleLabel, Priority.ALWAYS);
                noteIcon.getStyleClass().add("note-cell-icon");
                noteIcon.setIconSize(14);
                noteIcon.setMouseTransparent(true);
                noteIconHolder.getChildren().add(noteIcon);
                noteIconHolder.setMinWidth(NOTE_ICON_RESERVED_WIDTH);
                noteIconHolder.setPrefWidth(NOTE_ICON_RESERVED_WIDTH);
                noteIconHolder.setMaxWidth(NOTE_ICON_RESERVED_WIDTH);
                HBox.setHgrow(noteIconHolder, Priority.NEVER);
                HBox.setHgrow(pinIcon, Priority.NEVER);
                HBox.setHgrow(favIcon, Priority.NEVER);

                previewLabel.getStyleClass().add("note-cell-preview");
                previewLabel.setWrapText(true);
                previewLabel.setMinWidth(0);
                previewLabel.setMaxWidth(Double.MAX_VALUE);
                previewLabel.setMaxHeight(PREVIEW_LINE_HEIGHT * 5);
                previewLabel.setMinHeight(Region.USE_PREF_SIZE);

                modifiedLabel.getStyleClass().add("note-cell-date");
                modifiedLabel.setEllipsisString("…");
                modifiedLabel.setMinWidth(0);
                HBox.setHgrow(modifiedLabel, Priority.ALWAYS);
                createdLabel.getStyleClass().add("note-cell-date-created");
                createdLabel.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                createdLabel.setEllipsisString("…");
                createdLabel.setMinWidth(0);
                HBox.setHgrow(createdLabel, Priority.NEVER);

                dateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                dateRow.setMinWidth(0);
                dateRow.getChildren().addAll(modifiedLabel, createdLabel);

                container.getChildren().addAll(titleRow, previewLabel, dateRow);
                container.setFillWidth(true);
                container.setMinWidth(0);

                container.prefWidthProperty().bind(Bindings.createDoubleBinding(
                        () -> resolveContentWidth(listView),
                        listView.widthProperty()));
                container.maxWidthProperty().bind(container.prefWidthProperty());
                container.minWidthProperty().bind(container.prefWidthProperty());
                clip.widthProperty().bind(container.widthProperty());
                clip.heightProperty().bind(container.heightProperty());
                container.setClip(clip);

                container.widthProperty().addListener((obs, oldW, newW) -> layoutCellContent(newW.doubleValue()));

                openItem.setOnAction(e -> {
                    Note note = getItem();
                    if (note != null && eventBus != null) {
                        eventBus.publish(new NoteEvents.NoteSelectedEvent(note));
                    }
                });
                favoriteItem.setOnAction(e -> {
                    Note note = getItem();
                    if (note != null) {
                        toggleFavorite(note);
                    }
                });
                revealItem.setOnAction(e -> {
                    Note note = getItem();
                    if (note != null) {
                        revealInFileManager(note);
                    }
                });
                // "Reveal in file manager" only applies to file (vault) storage.
                revealItem.setVisible(isFileSystemStorage());
                exportItem.setOnAction(e -> {
                    Note note = getItem();
                    if (note != null && eventBus != null) {
                        eventBus.publish(new NoteEvents.NoteExportRequestEvent(note));
                    }
                });
                deleteItem.setOnAction(e -> {
                    Note note = getItem();
                    if (note != null) {
                        deleteNote(note);
                    }
                });
                contextMenu.getItems().addAll(openItem, favoriteItem, revealItem, exportItem,
                        new SeparatorMenuItem(), deleteItem);

                setOnDragDetected(event -> {
                    Note note = getItem();
                    if (note == null || note.getId() == null) {
                        return;
                    }
                    Dragboard db = startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString("note:" + note.getId());
                    db.setContent(content);
                    event.consume();
                });
            }

            private double resolveContentWidth(ListView<Note> lv) {
                double basis = lv.getWidth();
                if (basis <= 0) {
                    return 100;
                }
                return Math.max(64, basis - verticalScrollBarWidth(lv) - CELL_HORIZONTAL_PADDING);
            }

            private void layoutCellContent(double contentWidth) {
                if (contentWidth <= 0 || isEmpty()) {
                    return;
                }
                titleRow.setMaxWidth(contentWidth);
                double titleBudget = contentWidth - NOTE_ICON_RESERVED_WIDTH;
                if (titleRow.getChildren().contains(pinIcon)) {
                    titleBudget -= 16;
                }
                if (titleRow.getChildren().contains(favIcon)) {
                    titleBudget -= 16;
                }
                titleBudget -= titleRow.getSpacing() * Math.max(0, titleRow.getChildren().size() - 1);
                titleLabel.setMaxWidth(Math.max(24, titleBudget));

                previewLabel.setPrefWidth(contentWidth);
                previewLabel.setMaxWidth(contentWidth);
                int visiblePreviewLines = previewLines > 0 && previewLabel.isManaged() ? previewLines : 0;
                previewLabel.setMaxHeight(visiblePreviewLines * PREVIEW_LINE_HEIGHT);

                dateRow.setMaxWidth(contentWidth);
                double createdMax = Math.min(96, Math.max(52, contentWidth * 0.45));
                double modifiedMax = Math.max(32, contentWidth - createdMax - DATE_ROW_GAP);
                createdLabel.setMaxWidth(createdMax);
                modifiedLabel.setMaxWidth(modifiedMax);
            }

            @Override
            protected double computePrefWidth(double height) {
                ListView<Note> lv = getListView();
                if (lv != null && lv.getWidth() > 0) {
                    return lv.getWidth();
                }
                return super.computePrefWidth(height);
            }

            @Override
            protected double computeMaxWidth(double height) {
                return computePrefWidth(height);
            }

            @Override
            protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);
                if (empty || note == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                } else {
                    titleRow.getChildren().clear();
                    if (note.isPinned()) {
                        titleRow.getChildren().add(pinIcon);
                    }
                    if (note.isFavorite()) {
                        titleRow.getChildren().add(favIcon);
                    }
                    titleLabel.setText(note.getTitle() != null ? note.getTitle() : "");
                    // Distinguish attachments (PDF/image) from notes with a fitting icon.
                    switch (com.example.jylos.util.AttachmentType.fromName(note.getId())) {
                        case IMAGE -> noteIcon.setIconLiteral("fth-image");
                        case PDF -> noteIcon.setIconLiteral("fth-file");
                        default -> noteIcon.setIconLiteral("fth-file-text");
                    }
                    titleRow.getChildren().addAll(titleLabel, noteIconHolder);

                    boolean showPreview = previewLines > 0;
                    String preview = showPreview ? buildPreviewText(note.getContent(), previewLines) : "";
                    boolean hasPreview = showPreview && preview != null && !preview.isBlank();
                    previewLabel.setVisible(hasPreview);
                    previewLabel.setManaged(hasPreview);
                    previewLabel.setText(hasPreview ? preview : "");

                    modifiedLabel.setText(formatDate(
                            note.getModifiedDate() != null ? note.getModifiedDate() : note.getCreatedDate()));
                    String created = formatDate(note.getCreatedDate());
                    double contentWidth = resolveContentWidth(listView);
                    if (created.isEmpty()) {
                        createdLabel.setText("");
                    } else if (contentWidth < NARROW_PANEL_WIDTH) {
                        createdLabel.setText(created);
                    } else {
                        createdLabel.setText(java.text.MessageFormat.format(getString("panel.notes.created"), created));
                    }
                    layoutCellContent(contentWidth);

                    favoriteItem.setText(note.isFavorite() ? getString("action.remove_favorite")
                            : getString("action.add_favorite"));

                    setGraphic(container);
                    setText(null);
                    setContextMenu(contextMenu);
                }
            }
        };
    }

    /**
     * Builds the body preview shown under the title: strips a leading Markdown
     * heading and YAML frontmatter, then returns up to {@code maxLines} lines.
     *
     * @param content  raw note content (may be null)
     * @param maxLines maximum number of preview lines
     * @return preview text (possibly empty), with an ellipsis if truncated
     */
    /** Approximate characters that fit on one wrapped line in the notes panel. */
    private static final int PREVIEW_CHARS_PER_LINE = 44;

    private String buildPreviewText(String content, int maxLines) {
        if (content == null || content.isBlank() || maxLines <= 0) {
            return "";
        }
        String text = content;

        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end >= 0) {
                int nl = text.indexOf('\n', end + 1);
                text = nl >= 0 ? text.substring(nl + 1) : "";
            }
        }

        java.util.List<String> previewLines = new java.util.ArrayList<>();
        boolean truncated = false;
        for (String rawLine : text.split("\\R")) {
            String line = stripMarkdownForPreview(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            if (previewLines.size() >= maxLines) {
                truncated = true;
                break;
            }
            if (line.length() > PREVIEW_CHARS_PER_LINE) {
                line = line.substring(0, PREVIEW_CHARS_PER_LINE - 1).strip() + "…";
            }
            previewLines.add(line);
        }

        if (previewLines.isEmpty()) {
            String flow = stripMarkdownForPreview(text.replaceAll("\\s+", " ").strip());
            if (flow.isEmpty()) {
                return "";
            }
            int budget = maxLines * PREVIEW_CHARS_PER_LINE;
            if (flow.length() > budget) {
                truncated = true;
                flow = flow.substring(0, Math.max(0, budget - 1)).strip();
            }
            return wrapPreviewFlow(flow, maxLines, truncated);
        }

        String joined = String.join("\n", previewLines);
        if (truncated && !joined.endsWith("…")) {
            int last = previewLines.size() - 1;
            String lastLine = previewLines.get(last);
            if (!lastLine.endsWith("…")) {
                previewLines.set(last, lastLine + "…");
            }
            joined = String.join("\n", previewLines);
        }
        return joined;
    }

    private String stripMarkdownForPreview(String line) {
        if (line == null) {
            return "";
        }
        String stripped = line.replaceAll("^#+\\s*", "")
                .replaceAll("^>\\s*", "")
                .replaceAll("^[-*+]\\s+", "")
                .replaceAll("^\\d+\\.\\s+", "")
                .replaceAll("[*_`]", "")
                .strip();
        return stripped;
    }

    private String wrapPreviewFlow(String flow, int maxLines, boolean alreadyTruncated) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        int pos = 0;
        while (pos < flow.length() && lines.size() < maxLines) {
            int end = Math.min(flow.length(), pos + PREVIEW_CHARS_PER_LINE);
            String chunk = flow.substring(pos, end).strip();
            if (!chunk.isEmpty()) {
                lines.add(chunk);
            }
            pos = end;
        }
        boolean truncated = alreadyTruncated || pos < flow.length();
        String joined = String.join("\n", lines);
        if (truncated && !joined.isEmpty() && !joined.endsWith("…")) {
            joined = joined + "…";
        }
        return joined;
    }

    /**
     * Formats an ISO-like stored date ({@code yyyy-MM-dd[...]}) into a localized
     * medium date (e.g. "May 31, 2026"). Falls back to the raw date portion if
     * the value cannot be parsed.
     */
    private String formatDate(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        String datePart = stored.length() >= 10 ? stored.substring(0, 10) : stored;
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(datePart);
            Locale locale = bundle != null ? bundle.getLocale() : Locale.getDefault();
            return date.format(java.time.format.DateTimeFormatter
                    .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                    .withLocale(locale));
        } catch (Exception e) {
            return datePart;
        }
    }

    private void toggleFavorite(Note note) {
        try {
            note.setFavorite(!note.isFavorite());
            noteService.updateNote(note);
            notesListView.refresh();
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NoteModifiedEvent(note));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to toggle favorite", e);
        }
    }

    private String getString(String key) {
        return bundle != null && bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    private boolean isAllNotesVirtualFolder(Folder folder) {
        return folder != null && "ALL_NOTES_VIRTUAL".equals(folder.getId());
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus != null ? eventBus
                : (AppContext.isInitialized() ? AppContext.getEventBus() : null);
        subscribeToEvents();
    }

    private void subscribeToEvents() {
        if (eventBus == null)
            return;

        eventBus.subscribe(SystemActionEvent.class, event -> {
            javafx.application.Platform.runLater(() -> {
                if (event.getActionType() == SystemActionEvent.ActionType.NEW_NOTE) {
                    handleNewNote(null);
                } else if (event.getActionType() == SystemActionEvent.ActionType.DELETE) {
                    handleDelete(null);
                }
            });
        });
        eventBus.subscribe(NoteEvents.NoteCreatedEvent.class, event -> markAllNotesSearchCacheDirty());
        eventBus.subscribe(NoteEvents.NoteSavedEvent.class, event -> markAllNotesSearchCacheDirty());
        eventBus.subscribe(NoteEvents.NoteDeletedEvent.class, event -> markAllNotesSearchCacheDirty());
        eventBus.subscribe(NoteEvents.TrashItemDeletedEvent.class, event -> markAllNotesSearchCacheDirty());
    }

    public void setServices(NoteService noteService, TagService tagService, FolderService folderService) {
        this.noteService = noteService != null ? noteService
                : (AppContext.isInitialized() ? AppContext.getNoteService() : null);
        this.tagService = tagService != null ? tagService
                : (AppContext.isInitialized() ? AppContext.getTagService() : null);
        this.folderService = folderService != null ? folderService
                : (AppContext.isInitialized() ? AppContext.getFolderService() : null);
    }

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle != null ? bundle : AppContext.getBundle();
    }

    public VBox getNotesPanel() {
        return notesPanel;
    }

    public HBox getStackedModeHeader() {
        return stackedModeHeader;
    }

    public Label getNotesPanelTitleLabel() {
        return notesPanelTitleLabel;
    }

    public ComboBox<String> getSortComboBox() {
        return sortComboBox;
    }

    public Button getRefreshBtn() {
        return refreshBtn;
    }

    public ListView<Note> getNotesListView() {
        return notesListView;
    }

    // Phase 3: Migrated Loading Logic

    public void loadAllNotes() {
        if (noteService == null) {
            logger.warning("Cannot load notes: noteService is null");
            return;
        }
        currentFolder = null;
        currentTag = null;
        currentFilterType = "all";
        String sortOption = sortComboBox != null ? sortComboBox.getValue() : null;
        executeNotesLoad(
                this::loadAllNotesFromServiceAndRefreshCache,
                notes -> {
                    List<Note> sorted = sortNotesData(notes, sortOption);
                    notesListView.getSelectionModel().clearSelection();
                    notesListView.getItems().setAll(sorted);
                    String msg = bundle != null
                            ? java.text.MessageFormat.format(bundle.getString("info.notes_count"), sorted.size())
                            : sorted.size() + " notes";
                    if (notesPanelTitleLabel != null) {
                        notesPanelTitleLabel.setText(msg);
                    }
                    publishNotesLoadedEvent(sorted, getString("status.loaded_all"));
                },
                "Failed to load all notes");
    }

    public void loadNotesForFolder(Folder folder) {
        if (folder == null)
            return;
        if (noteService == null) {
            logger.warning("Cannot load folder notes: noteService is null");
            return;
        }
        currentFolder = folder;
        currentTag = null;
        currentFilterType = "folder";
        String sortOption = sortComboBox != null ? sortComboBox.getValue() : null;
        executeNotesLoad(
                () -> noteService.getNotesByFolder(folder),
                notes -> {
                    List<Note> sorted = sortNotesData(notes, sortOption);
                    notesListView.getSelectionModel().clearSelection();
                    notesListView.getItems().setAll(sorted);
                    if (notesPanelTitleLabel != null) {
                        notesPanelTitleLabel.setText(getString("panel.notes.title") + " - " + folder.getTitle());
                    }
                    publishNotesLoadedEvent(sorted,
                            bundle != null
                                    ? java.text.MessageFormat.format(bundle.getString("status.loaded_folder"),
                                            folder.getTitle())
                                    : "Loaded folder");
                },
                "Failed to load notes for folder");
    }

    public void loadNotesForTag(String tagName) {
        if (tagService == null) {
            logger.warning("Cannot filter by tag: tagService is null");
            return;
        }
        if (tagName == null || tagName.isEmpty()) {
            return;
        }
        try {
            Optional<Tag> tagOpt = tagService.getTagByTitle(tagName);
            if (tagOpt.isPresent()) {
                Tag tag = tagOpt.get();
                currentFolder = null;
                currentFilterType = "tag";
                currentTag = tag;
                String sortOption = sortComboBox != null ? sortComboBox.getValue() : null;
                executeNotesLoad(
                        () -> tagService.getNotesWithTag(tag),
                        notesWithTag -> {
                            List<Note> sorted = sortNotesData(notesWithTag, sortOption);
                            notesListView.getSelectionModel().clearSelection();
                            notesListView.getItems().setAll(sorted);
                            String msg = java.text.MessageFormat.format(getString("info.notes_count"), sorted.size());
                            if (notesPanelTitleLabel != null) {
                                notesPanelTitleLabel.setText(msg);
                            }
                            publishNotesLoadedEvent(sorted,
                                    bundle != null
                                            ? java.text.MessageFormat.format(bundle.getString("status.filtered_tag"),
                                                    tagName)
                                            : msg);
                        },
                        "Failed to filter notes by tag " + tagName);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to filter notes by tag " + tagName, e);
        }
    }

    public void performSearch(String searchText) {
        if (noteService == null) {
            logger.warning("Cannot perform search: noteService is null");
            return;
        }
        if (searchText == null || searchText.trim().isEmpty()) {
            if (currentFolder != null) {
                loadNotesForFolder(currentFolder);
            } else if (currentTag != null) {
                loadNotesForTag(currentTag.getTitle());
            } else {
                loadAllNotes();
            }
            return;
        }
        currentFilterType = "search";
        String sortOption = sortComboBox != null ? sortComboBox.getValue() : null;
        executeNotesLoad(
                () -> {
                    List<Note> allNotes = getSearchSourceNotes();
                    String searchLower = searchText.toLowerCase(Locale.ROOT);
                    List<Note> filteredNotes = allNotes.stream()
                            .filter(note -> {
                                String title = note.getTitle() != null ? note.getTitle().toLowerCase(Locale.ROOT) : "";
                                // Full-text: match the note's complete content, not the
                                // truncated lightweight body carried by list notes.
                                return title.contains(searchLower) || fullContentLower(note).contains(searchLower);
                            })
                            .toList();
                    return sortNotesData(filteredNotes, sortOption);
                },
                filteredNotes -> {
                    notesListView.getSelectionModel().clearSelection();
                    notesListView.getItems().setAll(filteredNotes);
                    String msg = bundle != null
                            ? java.text.MessageFormat.format(bundle.getString("info.notes_found"), filteredNotes.size())
                            : filteredNotes.size() + " notes found";
                    if (notesPanelTitleLabel != null) {
                        notesPanelTitleLabel.setText(msg);
                    }
                    publishNotesLoadedEvent(filteredNotes,
                            bundle != null
                                    ? java.text.MessageFormat.format(bundle.getString("status.search_active"),
                                            searchText)
                                    : "Search active");
                },
                "Failed to perform search");
    }

    private void markAllNotesSearchCacheDirty() {
        allNotesSearchCacheDirty = true;
        fullContentCache.clear();
    }

    /** Full, lowercased content of a note (read once via the service, then cached). */
    private String fullContentLower(Note note) {
        if (note == null || note.getId() == null) {
            return "";
        }
        return fullContentCache.computeIfAbsent(note.getId(), id -> {
            String content = note.getContent();
            try {
                Note full = noteService.getNoteById(id).orElse(null);
                if (full != null && full.getContent() != null) {
                    content = full.getContent();
                }
            } catch (Exception e) {
                logger.fine("Full-text: could not read content for " + id + ": " + e.getMessage());
            }
            return content == null ? "" : content.toLowerCase(Locale.ROOT);
        });
    }

    private List<Note> loadAllNotesFromServiceAndRefreshCache() {
        List<Note> allNotes = noteService.getAllNotes();
        allNotesSearchCache = List.copyOf(allNotes);
        allNotesSearchCacheDirty = false;
        return allNotes;
    }

    private List<Note> getSearchSourceNotes() {
        if (allNotesSearchCacheDirty || allNotesSearchCache.isEmpty()) {
            return loadAllNotesFromServiceAndRefreshCache();
        }
        return allNotesSearchCache;
    }

    public void sortNotes(String sortOption) {
        if (sortOption == null || notesListView == null)
            return;
        List<Note> notes = sortNotesData(new ArrayList<>(notesListView.getItems()), sortOption);
        notesListView.getSelectionModel().clearSelection();
        notesListView.getItems().setAll(notes);
    }

    private List<Note> sortNotesData(List<Note> notes, String sortOption) {
        if (notes == null || sortOption == null) {
            return notes != null ? notes : new ArrayList<>();
        }
        notes.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) {
                return a.isPinned() ? -1 : 1;
            }

            if (bundle != null && sortOption.equals(bundle.getString("sort.title_az"))) {
                String titleA = a.getTitle() != null ? a.getTitle() : "";
                String titleB = b.getTitle() != null ? b.getTitle() : "";
                return titleA.compareToIgnoreCase(titleB);
            } else if (bundle != null && sortOption.equals(bundle.getString("sort.title_za"))) {
                String titleZA = a.getTitle() != null ? a.getTitle() : "";
                String titleZB = b.getTitle() != null ? b.getTitle() : "";
                return titleZB.compareToIgnoreCase(titleZA);
            } else if (bundle != null && sortOption.equals(bundle.getString("sort.created_newest"))) {
                String cDateA = a.getCreatedDate() != null ? a.getCreatedDate() : "";
                String cDateB = b.getCreatedDate() != null ? b.getCreatedDate() : "";
                return cDateB.compareTo(cDateA);
            } else if (bundle != null && sortOption.equals(bundle.getString("sort.created_oldest"))) {
                String coDateA = a.getCreatedDate() != null ? a.getCreatedDate() : "";
                String coDateB = b.getCreatedDate() != null ? b.getCreatedDate() : "";
                return coDateA.compareTo(coDateB);
            } else if (bundle != null && sortOption.equals(bundle.getString("sort.modified_newest"))) {
                String mDateA = a.getModifiedDate() != null ? a.getModifiedDate()
                        : (a.getCreatedDate() != null ? a.getCreatedDate() : "");
                String mDateB = b.getModifiedDate() != null ? b.getModifiedDate()
                        : (b.getCreatedDate() != null ? b.getCreatedDate() : "");
                return mDateB.compareTo(mDateA);
            } else if (bundle != null && sortOption.equals(bundle.getString("sort.modified_oldest"))) {
                String moDateA = a.getModifiedDate() != null ? a.getModifiedDate()
                        : (a.getCreatedDate() != null ? a.getCreatedDate() : "");
                String moDateB = b.getModifiedDate() != null ? b.getModifiedDate()
                        : (b.getCreatedDate() != null ? b.getCreatedDate() : "");
                return moDateA.compareTo(moDateB);
            } else {
                return 0;
            }
        });
        return notes;
    }

    private void executeNotesLoad(Supplier<List<Note>> loader, Consumer<List<Note>> uiConsumer, String errorLog) {
        long requestVersion = notesLoadVersion.incrementAndGet();
        notesLoadExecutor.submit(() -> {
            try {
                List<Note> result = loader.get();
                if (requestVersion != notesLoadVersion.get()) {
                    return;
                }
                Platform.runLater(() -> {
                    if (requestVersion != notesLoadVersion.get()) {
                        return;
                    }
                    uiConsumer.accept(result != null ? result : List.of());
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, errorLog, e);
            }
        });
    }

    private void publishNotesLoadedEvent(List<Note> notes, String message) {
        if (eventBus != null) {
            eventBus.publish(new NoteEvents.NotesLoadedEvent(notes, message));
        }
    }

    @FXML
    private void handleToggleNotesPanel(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.TOGGLE_NOTES_LIST);
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.REFRESH_NOTES);
    }

    @FXML
    private void handleNewNote(ActionEvent event) {
        if (noteService == null) {
            logger.warning("Cannot create note: noteService is null");
            publishStatusUpdate(getString("status.error_creating_note"));
            return;
        }
        try {
            Note newNote = new Note(getString("app.untitled"), "");

            // Set parent folder
            if (currentFolder != null && currentFolder.getId() != null &&
                    !"ROOT".equals(currentFolder.getId()) &&
                    !isAllNotesVirtualFolder(currentFolder)) {
                newNote.setParent(currentFolder);
            }

            // Fix: If a folder is selected, prepare the ID with the folder path for
            // FileSystem storage
            Preferences prefs = Preferences.userNodeForPackage(NotesListController.class);
            boolean isFileSystem = !"sqlite".equals(prefs.get("storage_type", "sqlite"));

            if (isFileSystem && currentFolder != null && currentFolder.getId() != null &&
                    !"ROOT".equals(currentFolder.getId()) &&
                    !isAllNotesVirtualFolder(currentFolder)) {

                String pathSeparator = File.separator;
                String folderPath = currentFolder.getId();
                String safeTitle = newNote.getTitle().replaceAll("[^a-zA-Z0-9\\.\\-_ ]", "_");

                newNote.setId(folderPath + pathSeparator + safeTitle);
            }

            Note createdNote = noteService.createNote(newNote);
            String noteId = createdNote.getId();
            if (noteId == null) {
                publishStatusUpdate(getString("status.error_creating_note"));
                return;
            }
            newNote.setId(noteId);

            if (currentFolder != null && currentFolder.getId() != null &&
                    !"ROOT".equals(currentFolder.getId()) &&
                    !isAllNotesVirtualFolder(currentFolder)) {
                if (folderService != null) {
                    folderService.addNoteToFolder(currentFolder, newNote);
                } else {
                    logger.warning("Skipped addNoteToFolder: folderService is null");
                }
            }

            notesListView.getItems().add(0, newNote);
            notesListView.getSelectionModel().select(newNote);

            // Fire event for plugins and other controllers
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NoteCreatedEvent(newNote));
            }

            publishStatusUpdate(getString("status.note_created"));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create new note", e);
            publishStatusUpdate(getString("status.error_creating_note"));
        }
    }

    private void publishStatusUpdate(String message) {
        if (eventBus != null) {
            eventBus.publish(new UIEvents.StatusUpdateEvent(message));
        }
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        Note selectedNote = notesListView.getSelectionModel().getSelectedItem();
        if (selectedNote != null) {
            deleteNote(selectedNote);
        } else {
            // Note: SidebarController also listens to DELETE and handles its managed items.
            // If we are here and no note is selected, we could just do nothing or
            // publish a status if we want to be explicit.
            // However, MainController previously favored Sidebar over Note selection.
            // Here they both listen, so if Sidebar found something, it acts.
            // If NotesList finds something, it acts.
        }
    }

    /**
     * Reveals a note's file in the OS file manager (Finder / Explorer / Files).
     * Only available for file-based storage; SQLite notes have no file path.
     */
    /** True when the active storage backend is the file-based vault (not SQLite). */
    private boolean isFileSystemStorage() {
        Preferences prefs = Preferences.userNodeForPackage(NotesListController.class);
        return !"sqlite".equals(prefs.get("storage_type", "sqlite"));
    }

    private void revealInFileManager(Note note) {
        if (note == null || noteService == null) {
            return;
        }
        Optional<java.nio.file.Path> pathOpt = noteService.getNoteFilePath(note.getId());
        if (pathOpt.isEmpty()) {
            publishStatusUpdate(getString("status.reveal_unavailable"));
            return;
        }
        String absolute = pathOpt.get().toAbsolutePath().toString();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", absolute).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select," + absolute).start();
            } else {
                String dir = pathOpt.get().toAbsolutePath().getParent() != null
                        ? pathOpt.get().toAbsolutePath().getParent().toString()
                        : absolute;
                new ProcessBuilder("xdg-open", dir).start();
            }
            publishStatusUpdate(getString("status.revealed"));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to reveal note in file manager", ex);
            publishStatusUpdate(getString("status.reveal_error"));
        }
    }

    private void deleteNote(Note note) {
        if (note == null)
            return;
        if (noteService == null) {
            logger.warning("Cannot delete note: noteService is null");
            publishStatusUpdate(getString("status.note_delete_error"));
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(getString("dialog.delete_note.title"));
        alert.setHeaderText(getString("dialog.delete_note.header"));
        alert.setContentText(getString("dialog.delete_note.content"));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                noteService.moveToTrash(note.getId());

                // Selection clearing is important
                notesListView.getSelectionModel().clearSelection();

                // Publish event so Sidebar can refresh counts, Main can refresh etc.
                if (eventBus != null) {
                    eventBus.publish(new NoteEvents.NoteDeletedEvent(note.getId(), note.getTitle()));
                }

                publishStatusUpdate(getString("status.note_moved_trash"));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to delete note", e);
                publishStatusUpdate(getString("status.note_delete_error"));
            }
        }
    }

    private void publishEvent(SystemActionEvent.ActionType actionType) {
        if (eventBus != null) {
            eventBus.publish(new SystemActionEvent(actionType));
        }
    }

    // Grid view (from NotesGridWorkflow)

    public enum NotesViewMode {
        LIST, GRID
    }

    private NotesViewMode notesViewMode = NotesViewMode.LIST;
    private TilePane notesGridPane;
    private ScrollPane gridScrollPane;
    private VBox notesPanelContainer;
    private String lastGridSignature = "";

    public boolean isGridViewActive() {
        return notesViewMode == NotesViewMode.GRID;
    }

    public void initializeNotesGrid() {
        if (notesGridPane != null) {
            return;
        }
        notesGridPane = new TilePane();
        notesGridPane.setPrefColumns(3);
        notesGridPane.setHgap(12);
        notesGridPane.setVgap(12);
        notesGridPane.setPadding(new javafx.geometry.Insets(12));
        notesGridPane.setStyle("-fx-background-color: transparent;");

        gridScrollPane = new ScrollPane(notesGridPane);
        gridScrollPane.setFitToWidth(true);
        gridScrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        gridScrollPane.getStyleClass().add("notes-grid-scroll");

        if (notesListView != null && notesListView.getParent() instanceof VBox parent) {
            notesPanelContainer = parent;
        }
    }

    public boolean showListView(Runnable gridRefreshAction, Consumer<String> warningLogger) {
        if (notesViewMode == NotesViewMode.LIST) {
            return false;
        }
        notesViewMode = NotesViewMode.LIST;
        applyCurrentNotesViewMode(gridRefreshAction, warningLogger);
        return true;
    }

    public boolean showGridView(Runnable gridRefreshAction, Consumer<String> warningLogger) {
        if (notesViewMode == NotesViewMode.GRID) {
            return false;
        }
        notesViewMode = NotesViewMode.GRID;
        initializeNotesGrid();
        applyCurrentNotesViewMode(gridRefreshAction, warningLogger);
        return true;
    }

    private void applyCurrentNotesViewMode(Runnable gridRefreshAction, Consumer<String> warningLogger) {
        notesPanelContainer = applyNotesViewMode(
                isGridViewActive(),
                notesListView,
                gridScrollPane,
                notesPanelContainer,
                gridRefreshAction,
                warningLogger);
    }

    public void refreshGridViewIfActive(
            boolean isDarkTheme,
            Function<String, String> i18n,
            Consumer<Note> openNoteAction,
            Consumer<String> statusUpdate) {
        if (!isGridViewActive()) {
            return;
        }
        initializeNotesGrid();
        refreshGridView(notesGridPane, notesListView, isDarkTheme, i18n, openNoteAction, statusUpdate);
    }

    public VBox applyNotesViewMode(
            boolean isGridMode,
            ListView<Note> notesListView,
            ScrollPane gridScrollPane,
            VBox currentNotesPanelContainer,
            Runnable refreshGridViewAction,
            Consumer<String> warningLogger) {
        if (notesListView == null || gridScrollPane == null) {
            return currentNotesPanelContainer;
        }

        VBox notesPanelContainer = currentNotesPanelContainer;
        if (notesPanelContainer == null) {
            Parent parent = notesListView.getParent();
            if (parent instanceof VBox) {
                notesPanelContainer = (VBox) parent;
            } else {
                parent = gridScrollPane.getParent();
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
                if (!resolvedContainer.getChildren().contains(gridScrollPane)) {
                    resolvedContainer.getChildren().add(gridScrollPane);
                    VBox.setVgrow(gridScrollPane, Priority.ALWAYS);
                }
                if (refreshGridViewAction != null) {
                    refreshGridViewAction.run();
                }
            } else {
                if (resolvedContainer.getChildren().contains(gridScrollPane)) {
                    resolvedContainer.getChildren().remove(gridScrollPane);
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

    public void refreshGridView(
            TilePane notesGridPane,
            ListView<Note> notesListView,
            boolean isDarkTheme,
            Function<String, String> i18n,
            Consumer<Note> openNoteAction,
            Consumer<String> statusUpdate) {
        if (notesGridPane == null || notesListView == null || i18n == null || openNoteAction == null
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
        if (signature.equals(lastGridSignature) && notesGridPane.getChildren().size() == notes.size()) {
            return;
        }

        notesGridPane.getChildren().clear();
        for (Note note : notes) {
            VBox card = createNoteCard(note, notesListView, isDarkTheme, i18n, openNoteAction, statusUpdate);
            notesGridPane.getChildren().add(card);
        }
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
            FontIcon pinIcon = new FontIcon("fth-map-pin");
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
            notesListView.getSelectionModel().select(note);
            openNoteAction.accept(note);
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
            if (event.getTransferMode() == javafx.scene.input.TransferMode.MOVE) {
                statusUpdate.accept(i18n.apply("status.note_moved"));
            }
            event.consume();
        });
    }


    public void refreshNotesList(
            String currentFilterType,
            Folder currentFolder,
            Tag currentTag,
            String searchText,
            boolean gridMode,
            Runnable loadFavoritesAction,
            Runnable refreshGridView) {
        switch (currentFilterType) {
            case "folder":
                if (currentFolder != null) {
                    loadNotesForFolder(currentFolder);
                } else {
                    loadAllNotes();
                }
                break;
            case "tag":
                if (currentTag != null && currentTag.getTitle() != null) {
                    loadNotesForTag(currentTag.getTitle());
                } else {
                    loadAllNotes();
                }
                break;
            case "favorites":
                if (loadFavoritesAction != null) {
                    loadFavoritesAction.run();
                }
                break;
            case "search":
                performSearch(searchText != null ? searchText : "");
                break;
            case "all":
            default:
                loadAllNotes();
                break;
        }

        if (gridMode && refreshGridView != null) {
            refreshGridView.run();
        }
    }
}
