package com.example.jylos.ui.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.service.CanvasReferences;
import com.example.jylos.service.FileFingerprint;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.RichLinkService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.CanvasView;
import com.example.jylos.ui.components.MarkdownEditorView;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.MarkdownPreview;
import com.example.jylos.util.RichLinks;
import com.example.jylos.util.SystemBrowser;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

/**
 * FXML controller for the note editor pane.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Loads/saves notes including YAML custom properties.</li>
 *   <li>Collapsible properties panel — header always visible; clicking expands/collapses.</li>
 *   <li>CodeMirror-backed Markdown editing and wiki-link autocomplete.</li>
 *   <li>Property values containing {@code [[wiki-links]]} show a clickable link button.</li>
 *   <li>Back/forward navigation arrow buttons for traversing note history.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.3.0
 */
public class EditorController {

    private static final Logger logger = LoggerConfig.getLogger(EditorController.class);

    // ── Service / state ─────────────────────────────────────────────────────
    private EventBus eventBus;
    private NoteService noteService;
    private TagService tagService;
    private ResourceBundle bundle;
    private Consumer<String> statusAction = message -> {
    };
    private Consumer<Note> noteModifiedAction = note -> {
    };
    private Consumer<Boolean> livePreviewPreferenceAction = enabled -> {
    };

    /** Obsidian-style centered content column, toggled from Preferences. */
    private boolean readableLineLength = false;
    /** Editor/preview font size, independent from the native JavaFX chrome's own font size. */
    private double contentFontSize = 14.0;

    private Note currentNote;
    private boolean isModified = false;
    /** The note's file as it was when the editor loaded or last saved it; null without a file. */
    private FileFingerprint persistedFingerprint;
    private final SaveConflictSupport saveConflictSupport = new SaveConflictSupport();
    private String persistedTitle = "";
    private String persistedContent = "";
    private Map<String, String> persistedCustomProperties = new LinkedHashMap<>();

    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    /** True when the editor is in "read" (preview-only) view: properties become read-only. */
    private boolean readOnlyView = false;
    /** The properties panel: its rows, folded or open, and the add-property dialog. */
    private final PropertiesPanelSupport properties = new PropertiesPanelSupport(new PropertiesHost());

    /** Titles supplier for wiki-link autocomplete. Defaults to the global title index. */
    private Supplier<List<String>> autocompleteTitlesSupplier = List::of;

    private final EditorBlockRenderSupport blockRenderSupport = new EditorBlockRenderSupport();
    private WikiLinkHandler wikiLinkHandler;
    private java.util.function.Consumer<String> tagLinkHandler;
    /** The reading view: renders, scroll, heading jumps, its page bridge and plugin enhancers. */
    private final EditorPreviewSupport preview = new EditorPreviewSupport(new PreviewHost());
    private final RichLinkService richLinkService = new RichLinkService();
    private CanvasView currentCanvasView;
    private Path currentCanvasPath;
    private DocumentFingerprint currentCanvasFingerprint;
    private AttachmentType currentAttachmentType = AttachmentType.MARKDOWN;

    private record DocumentFingerprint(long lastModifiedMillis, long size) {
    }

    /** Opens a note when the user clicks a wiki-link in the Markdown preview. */
    @FunctionalInterface
    public interface WikiLinkHandler {
        void openNoteByTitle(String title);
    }

    // ── FXML — header ───────────────────────────────────────────────────────
    @FXML private VBox editorContainer;
    @FXML private ScrollPane editorTabScroll;
    @FXML private HBox editorTabBar;
    @FXML private Label notePathLabel;
    @FXML private HBox editorHeaderBar;
    @FXML private StackPane editorContentStack;
    @FXML private VBox emptyState;
    @FXML private Button navBackBtn;
    @FXML private Button navForwardBtn;
    @FXML private TextField noteTitleField;
    @FXML private Label noteTitleLabel;
    @FXML private Label privateIndicator;
    @FXML private FontIcon privateIndicatorIcon;
    @FXML private Tooltip privateIndicatorTip;
    @FXML private HBox noteOnlyControls;
    @FXML private ToggleButton toggleTagsBtn;
    @FXML private Button readingModeButton;
    @FXML private FontIcon readingModeIcon;
    @FXML private Tooltip readingModeTooltip;
    @FXML private Button editorSaveBtn;
    @FXML private ToggleButton pinButton;
    @FXML private ToggleButton favoriteButton;
    @FXML private ToggleButton focusModeToggleBtn;

    // ── FXML — tags bar ─────────────────────────────────────────────────────
    @FXML private VBox tagsContainer;
    @FXML private FlowPane tagsFlowPane;
    @FXML private Label modifiedDateLabel;

    // ── FXML — properties panel ─────────────────────────────────────────────
    @FXML private VBox  propertiesSection;   // outer, shown when note has custom props
    @FXML private HBox  propertiesHeader;    // always-visible header row
    @FXML private Label propertiesCollapseIcon;
    @FXML private VBox  propertiesBody;      // rows + "add property" line, collapsible
    @FXML private VBox  propertiesContent;   // rows, toggleable via header click
    @FXML private Button addPropertyBtn;
    @FXML private Button addPropertyHeaderBtn;

    // ── FXML — editor / preview ─────────────────────────────────────────────
    @FXML private SplitPane editorPreviewSplitPane;
    @FXML private VBox      editorPane;
    @FXML private MarkdownEditorView noteContentArea;
    @FXML private HBox      formatToolbarContainer;
    @FXML private VBox      previewPane;
    @FXML private WebView previewWebView;

    // ============================================================
    // Setters
    // ============================================================

    private void setEventBus(EventBus eventBus) {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        this.eventBus = eventBus;
        subscribeToEvents();
    }

    private void setTagService(TagService tagService) {
        this.tagService = tagService;
    }

    private void setNoteService(NoteService noteService) {
        this.noteService = noteService;
    }

    private void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
        if (noteContentArea != null) {
            noteContentArea.setEditorLabels(Map.ofEntries(
                    Map.entry("undo", getString("action.undo", "Undo")),
                    Map.entry("redo", getString("action.redo", "Redo")),
                    Map.entry("cut", getString("action.cut", "Cut")),
                    Map.entry("copy", getString("action.copy", "Copy")),
                    Map.entry("paste", getString("action.paste", "Paste")),
                    Map.entry("selectAll", getString("action.select_all", "Select All")),
                    Map.entry("Find", getString("editor.search.find", "Find")),
                    Map.entry("Replace", getString("editor.search.replace", "Replace")),
                    Map.entry("next", getString("editor.search.next", "next")),
                    Map.entry("previous", getString("editor.search.previous", "previous")),
                    Map.entry("all", getString("editor.search.all", "all")),
                    Map.entry("match case", getString("editor.search.match_case", "match case")),
                    Map.entry("regexp", getString("editor.search.regexp", "regular expression")),
                    Map.entry("by word", getString("editor.search.by_word", "whole word")),
                    Map.entry("replace", getString("editor.search.replace_action", "replace")),
                    Map.entry("replace all", getString("editor.search.replace_all", "replace all")),
                    Map.entry("close", getString("action.close", "Close")),
                    Map.entry("Go to line", getString("editor.search.go_to_line", "Go to line")),
                    Map.entry("go", getString("editor.search.go", "go")),
                    Map.entry("current match", getString("editor.search.current_match", "current match")),
                    Map.entry("on line", getString("editor.search.on_line", "on line")),
                    Map.entry("replaced $ matches", getString("editor.search.replaced_matches", "replaced $ matches")),
                    Map.entry("replaced match on line $", getString("editor.search.replaced_match_on_line",
                            "replaced match on line $"))));
        }
    }

    public void wire(EventBus eventBus, NoteService noteService, TagService tagService, ResourceBundle bundle,
            Consumer<Note> noteModifiedAction, Consumer<String> statusAction,
            Supplier<List<String>> autocompleteTitlesSupplier, boolean livePreviewEnabled,
            Consumer<Boolean> livePreviewPreferenceAction, WikiLinkHandler wikiLinkHandler,
            Consumer<String> tagLinkHandler, com.example.jylos.plugin.EditorHooks editorHooks) {
        setNoteService(noteService);
        setTagService(tagService);
        setBundle(bundle);
        this.noteModifiedAction = noteModifiedAction != null ? noteModifiedAction : note -> {
        };
        this.statusAction = statusAction != null ? statusAction : message -> {
        };
        saveConflictSupport.wire(key -> getString(key, key));
        this.autocompleteTitlesSupplier = autocompleteTitlesSupplier != null ? autocompleteTitlesSupplier : List::of;
        // A link opens the note it names by path, resolved from the note it is written in.
        this.wikiLinkHandler = wikiLinkHandler;
        this.tagLinkHandler = tagLinkHandler;
        this.editorHooks = editorHooks;
        this.livePreviewPreferenceAction = livePreviewPreferenceAction != null
                ? livePreviewPreferenceAction : enabled -> {
                };
        setLivePreviewEnabled(livePreviewEnabled, false);
        refreshAutocompleteTitles();
        setEventBus(eventBus);
    }

    /** Plugin editor-hook dispatcher (nullable; wired by MainController). */
    private com.example.jylos.plugin.EditorHooks editorHooks;

    /**
     * Runs plugin {@code onBeforeTextInsert} hooks over a snippet about to be inserted
     * programmatically (dialogs, autocomplete, templates). Returns the snippet
     * unchanged when no hooks are registered.
     */
    private String applyInsertHooks(String snippet) {
        if (editorHooks == null || editorHooks.isEmpty() || snippet == null) {
            return snippet;
        }
        return editorHooks.applyBeforeTextInsert(currentNote, snippet);
    }

    // ============================================================
    // Header control presentation
    // ============================================================

    /**
     * Renders the compact editor-header controls as icon-only buttons.
     */
    public void applyHeaderControlsPresentation() {
        applyIconOnly(toggleTagsBtn);
        applyIconOnly(readingModeButton);
        applyIconOnly(editorSaveBtn);
        applyIconOnly(pinButton);
        applyIconOnly(favoriteButton);
        applyIconOnly(focusModeToggleBtn);
    }

    private void applyIconOnly(ButtonBase btn) {
        if (btn == null) return;
        btn.setText("");
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        if (!btn.getStyleClass().contains("icon-only-btn")) {
            btn.getStyleClass().add("icon-only-btn");
        }
    }

    // ============================================================
    // Navigation state (called from MainController)
    // ============================================================

    /**
     * Enables or disables the back/forward navigation arrow buttons.
     * Called by MainController after each history change.
     */
    public void updateNavigationState(boolean canGoBack, boolean canGoForward) {
        if (navBackBtn    != null) navBackBtn.setDisable(!canGoBack);
        if (navForwardBtn != null) navForwardBtn.setDisable(!canGoForward);
    }

    // ============================================================
    // State accessors
    // ============================================================

    public Note getCurrentNote() { return currentNote; }

    public void syncCurrentNoteIdentity(Note note) {
        if (currentNote == null || note == null) {
            return;
        }
        currentNote.setId(note.getId());
        currentNote.setTitle(note.getTitle());
        currentNote.setFavorite(note.isFavorite());
        currentNote.setPinned(note.isPinned());
        currentNote.setPrivate(note.isPrivate());
        currentNote.setDeleted(note.isDeleted());
        currentNote.setDeletedDate(note.getDeletedDate());
        currentNote.setStatus(note.getStatus());
        if (noteTitleField != null) {
            noteTitleField.setText(note.getTitle() != null ? note.getTitle() : "");
        }
        updateBreadcrumb(currentNote);
        if (!isModified) {
            capturePersistedBaseline(currentNote);
        }
    }

    /** Live editor content (includes unsaved edits); empty while viewing an attachment. */
    public String getCurrentContent() {
        if (viewingAttachment) {
            return "";
        }
        if (noteContentArea != null) {
            return noteContentArea.getText();
        }
        return currentNote != null ? currentNote.getContent() : "";
    }
    public boolean isModified()  { return isModified; }

    /** Drops the unsaved-changes flag without persisting (used when discarding on close). */
    public void markClean() {
        setModifiedState(false);
    }

    // FXML node getters (used by MainController for layout delegation)
    public VBox            getEditorContainer()        { return editorContainer; }
    public HBox            getEditorTabBar()           { return editorTabBar; }
    public javafx.scene.control.ScrollPane getEditorTabScroll() { return editorTabScroll; }

    /** Leaves the title of the open note selected in its field, ready to type a new name. */
    public void beginTitleRename() {
        if (noteTitleField != null && noteTitleField.isVisible() && !noteTitleField.isDisabled()) {
            noteTitleField.requestFocus();
            noteTitleField.selectAll();
        }
    }
    public ToggleButton    getToggleTagsBtn()           { return toggleTagsBtn; }
    public VBox            getTagsContainer()           { return tagsContainer; }
    public Label           getModifiedDateLabel()       { return modifiedDateLabel; }
    public SplitPane       getEditorPreviewSplitPane()  { return editorPreviewSplitPane; }
    public VBox            getEditorPane()              { return editorPane; }
    public VBox            getPreviewPane()             { return previewPane; }
    public javafx.scene.web.WebView getPreviewWebView() { return previewWebView; }

    // ============================================================
    // FXML action handlers
    // ============================================================

    @FXML private void handleToggleTags(ActionEvent e)        { publish(SystemActionEvent.ActionType.TOGGLE_TAGS); }
    @FXML private void handleToggleReadingMode(ActionEvent e) { publish(SystemActionEvent.ActionType.TOGGLE_READING_MODE); }
    @FXML private void handleTogglePin(ActionEvent e)         { publish(SystemActionEvent.ActionType.TOGGLE_PIN); }
    @FXML private void handleFocusMode(ActionEvent e)         { publish(SystemActionEvent.ActionType.FOCUS_MODE); }
    @FXML private void handleSave(ActionEvent e)               { publish(SystemActionEvent.ActionType.SAVE); }
    @FXML private void handleDelete(ActionEvent e)             { publish(SystemActionEvent.ActionType.DELETE); }
    @FXML private void handleToggleFavorite(ActionEvent e)    { publish(SystemActionEvent.ActionType.TOGGLE_FAVORITE); }
    @FXML private void handleToggleRightPanel(ActionEvent e)  { publish(SystemActionEvent.ActionType.TOGGLE_RIGHT_PANEL); }
    @FXML private void handleHeading1(ActionEvent e)          { publish(SystemActionEvent.ActionType.HEADING1); }
    @FXML private void handleHeading2(ActionEvent e)          { publish(SystemActionEvent.ActionType.HEADING2); }
    @FXML private void handleHeading3(ActionEvent e)          { publish(SystemActionEvent.ActionType.HEADING3); }
    @FXML private void handleBold(ActionEvent e)              { publish(SystemActionEvent.ActionType.BOLD); }
    @FXML private void handleItalic(ActionEvent e)            { publish(SystemActionEvent.ActionType.ITALIC); }
    @FXML private void handleStrike(ActionEvent e)            { publish(SystemActionEvent.ActionType.STRIKE); }
    @FXML private void handleUnderline(ActionEvent e)         { publish(SystemActionEvent.ActionType.UNDERLINE); }
    @FXML private void handleHighlight(ActionEvent e)         { publish(SystemActionEvent.ActionType.HIGHLIGHT); }
    @FXML private void handleLink(ActionEvent e)              { publish(SystemActionEvent.ActionType.LINK); }
    @FXML private void handleRichLink(ActionEvent e)          { publish(SystemActionEvent.ActionType.RICH_LINK); }
    @FXML private void handleImage(ActionEvent e)             { publish(SystemActionEvent.ActionType.IMAGE); }
    @FXML private void handleTodoList(ActionEvent e)          { publish(SystemActionEvent.ActionType.TODO_LIST); }
    @FXML private void handleBulletList(ActionEvent e)        { publish(SystemActionEvent.ActionType.BULLET_LIST); }
    @FXML private void handleNumberedList(ActionEvent e)      { publish(SystemActionEvent.ActionType.NUMBERED_LIST); }
    @FXML private void handleQuote(ActionEvent e)             { publish(SystemActionEvent.ActionType.QUOTE); }
    @FXML private void handleCode(ActionEvent e)              { publish(SystemActionEvent.ActionType.CODE); }

    /** Back arrow — delegates history logic to MainController via EventBus. */
    @FXML private void handleNavigateBack(ActionEvent e)    { publish(SystemActionEvent.ActionType.NAVIGATE_BACK); }

    /** Forward arrow — delegates history logic to MainController via EventBus. */
    @FXML private void handleNavigateForward(ActionEvent e) { publish(SystemActionEvent.ActionType.NAVIGATE_FORWARD); }

    // ============================================================
    // Properties panel handlers
    // ============================================================

    /** Toggles (collapses/expands) the properties content area. */
    @FXML
    private void handlePropertiesHeaderClick(MouseEvent event) {
        properties.toggleExpanded();
    }

    /** Opens a dialog to add a new YAML property (edit/split view only). */
    @FXML
    private void handleAddProperty(ActionEvent event) {
        properties.addProperty();
    }

    /**
     * Switches the properties panel between editable and read-only presentations.
     * Called by {@link MainController} when the editor view mode changes
     * (preview-only = read-only).
     *
     * @param readOnly {@code true} for the "read" view
     */
    public void setReadOnlyView(boolean readOnly) {
        if (this.readOnlyView == readOnly) {
            return;
        }
        // When leaving the editable view, capture any in-progress property edits
        // into the model so the read view (and a later save) reflect them.
        if (!this.readOnlyView && currentNote != null) {
            currentNote.setCustomProperties(properties.collect());
        }
        this.readOnlyView = readOnly;
        updateEditorEditableState();
        // In the read (preview-only) view, swap the editable title input for a plain
        // heading Label so the title reads as a title, not an input field.
        if (noteTitleField != null && noteTitleLabel != null) {
            UiNodes.show(noteTitleField, !readOnly);
            UiNodes.show(noteTitleLabel, readOnly);
        }
        properties.rebuild();
    }

    // ============================================================
    // Note loading and saving
    // ============================================================

    /** FXML lifecycle hook: start in the empty state (no note open). */
    @FXML
    private void initialize() {
        setNoteOpen(false);
        // Live Preview shows a note embedded on its own line rendered, as the reading view does.
        blockRenderSupport.setEmbedRenderer(preview::renderEditorEmbed);
        if (noteContentArea != null) {
            noteContentArea.setTagHandler(tag -> {
                if (tagLinkHandler != null) {
                    tagLinkHandler.accept(tag);
                }
            });
        }
        if (noteContentArea != null) {
            noteContentArea.setInsertionTransformer(this::applyInsertHooks);
            noteContentArea.setLinkHandlers(title -> {
                if (wikiLinkHandler != null) {
                    wikiLinkHandler.openNoteByTitle(title);
                }
            }, SystemBrowser::open);
            noteContentArea.setImageSourceResolver(
                    source -> MarkdownPreview.resolveImageSource(source, preview.imageBaseDir(),
                            preview.imageContainmentRoot()));
            noteContentArea.setTextChangeListener(content -> {
                if (currentNote != null) {
                    reevaluateModifiedState();
                }
                refreshEditorBlockRenders(false);
            });
        }
        // The read-view heading always mirrors the editable title field.
        if (noteTitleLabel != null && noteTitleField != null) {
            noteTitleLabel.textProperty().bind(noteTitleField.textProperty());
        }
    }

    /** Whether the editor renders Markdown inline (Live Preview) rather than showing raw source. */
    public boolean isLivePreviewEnabled() {
        return noteContentArea == null || noteContentArea.isLivePreviewEnabled();
    }

    /** Toggles between Markdown source and source-backed Live Preview. */
    public void toggleLivePreview() {
        boolean enabled = noteContentArea == null || !noteContentArea.isLivePreviewEnabled();
        setLivePreviewEnabled(enabled, true);
    }

    /** Applies the persisted default editing mode without writing the preference again. */
    /** Runs when the folder path in the title row is clicked (null: the path is plain text). */
    private Runnable pathRevealAction;
    /** Preference: whether the format bar under the editor is shown. */
    private boolean formatToolbarVisible = true;

    /**
     * What clicking the note's folder path in the title row does (the Explorer layout reveals
     * the note in its tree). {@code null} makes the path plain text, not a link.
     */
    public void setPathRevealAction(Runnable action) {
        this.pathRevealAction = action;
        if (notePathLabel != null) {
            notePathLabel.getStyleClass().remove("note-path-link");
            if (action != null) {
                notePathLabel.getStyleClass().add("note-path-link");
            }
            notePathLabel.setCursor(action != null ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        }
    }

    /** Shows or hides the format bar under the editor (Preferences). */
    public void setFormatToolbarVisible(boolean visible) {
        this.formatToolbarVisible = visible;
        UiNodes.show(formatToolbarContainer, visible);
    }

    public void applyLivePreviewPreference(boolean enabled) {
        setLivePreviewEnabled(enabled, false);
    }

    private void setLivePreviewEnabled(boolean enabled, boolean persist) {
        if (noteContentArea != null) {
            noteContentArea.setLivePreviewEnabled(enabled);
        }
        if (persist) {
            livePreviewPreferenceAction.accept(enabled);
            reportStatus(safeI18n(enabled ? "status.live_preview_enabled" : "status.source_mode_enabled"));
        }
    }

    /** Applies the persisted "readable line length" preference to both editor and preview. */
    public void applyReadableLineLengthPreference(boolean enabled) {
        this.readableLineLength = enabled;
        if (noteContentArea != null) {
            noteContentArea.setReadableLineLength(enabled);
        }
        if (isPreviewVisible()) {
            refreshPreview(preview.lastDarkTheme());
        }
    }

    /** Updates the single book/pencil action to reflect the visible editor layout. */
    public void updateReadingModeControl(boolean editingOnly) {
        if (readingModeIcon != null) {
            readingModeIcon.setIconLiteral(editingOnly ? "fth-book-open" : "fth-edit-3");
        }
        if (readingModeTooltip != null) {
            readingModeTooltip.setText(safeI18n(
                    editingOnly ? "tooltip.switch_to_reading" : "tooltip.switch_to_editing"));
        }
    }

    /**
     * Toggles between the editing UI and the Obsidian-style empty state.
     * The empty state covers the editor when no note is open (and at startup).
     */
    private void setNoteOpen(boolean open) {
        hideAttachmentViewer();
        UiNodes.show(editorHeaderBar, open);
        UiNodes.show(noteOnlyControls, open);
        UiNodes.show(editorPreviewSplitPane, open);
        if (!open) {
            UiNodes.show(tagsContainer, false);
            UiNodes.show(propertiesSection, false);
        }
        UiNodes.show(emptyState, !open);
        updateEditorEditableState();
    }

    private void updateEditorEditableState() {
        if (noteContentArea != null) {
            noteContentArea.setEditable(currentNote != null && !readOnlyView && !viewingAttachment);
        }
    }

    /**
     * Shows the note's folders ({@code folder / sub /}) in the title row, before the title;
     * hidden for a note at the root of the vault. The tooltip has the full file path.
     */
    private void updateBreadcrumb(Note note) {
        if (notePathLabel == null) return;
        String id = note.getId();
        String path;
        if (id != null && (id.contains("/") || id.contains("\\"))) {
            path = id.replace('\\', '/');
            // Keep the real extension for attachments; only assume .md when there is none.
            if (AttachmentType.extensionOf(path).isEmpty()) {
                path = path + ".md";
            }
        } else {
            String title = note.getTitle() != null ? note.getTitle() : "";
            path = title.isBlank() ? "" : (AttachmentType.extensionOf(title).isEmpty() ? title + ".md" : title);
        }
        int lastSlash = path.lastIndexOf('/');
        String folders = lastSlash > 0 ? path.substring(0, lastSlash).replace("/", " / ") + " /" : "";
        notePathLabel.setText(folders);
        notePathLabel.setTextOverrun(javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS);
        if (editorHeaderBar != null && !notePathLabel.maxWidthProperty().isBound()) {
            // A deep path never takes more than 40% of the row; the title keeps the rest.
            notePathLabel.maxWidthProperty().bind(editorHeaderBar.widthProperty().multiply(0.4));
        }
        notePathLabel.setTooltip(path.isBlank() ? null : new Tooltip(path));
        if (notePathLabel.getOnMouseClicked() == null) {
            notePathLabel.setOnMouseClicked(e -> {
                if (pathRevealAction != null) {
                    pathRevealAction.run();
                }
            });
        }
        UiNodes.show(notePathLabel, !folders.isEmpty());
    }


    @FXML
    private void handleEmptyCreate(ActionEvent e) {
        publish(SystemActionEvent.ActionType.NEW_NOTE);
    }

    @FXML
    private void handleEmptyCreateCanvas(ActionEvent e) {
        publish(SystemActionEvent.ActionType.NEW_CANVAS);
    }

    @FXML
    private void handleEmptyGoToFile(ActionEvent e) {
        publish(SystemActionEvent.ActionType.QUICK_SWITCHER);
    }

    public void loadNote(Note note) {
        // The tags bar is per-note UI state, not a sticky global preference: it must
        // start collapsed on every load. Without this, closing a note (setNoteOpen(false)
        // hides the container) or opening an attachment (showAttachment() does the same)
        // left the toggle button's own selected state untouched, so it could end up
        // visibly pressed while the bar it controls was already hidden.
        initializeTagsBarCollapsed();
        if (note == null) {
            clearReusableCanvasIfClean();
            currentNote = null;
            capturePersistedBaseline(null);
            if (noteTitleField  != null) noteTitleField.clear();
            if (noteContentArea != null) noteContentArea.clear();
            resetEditorUndoHistory();
            properties.clear();
            setNoteOpen(false);
            setModifiedState(false);
            updatePrivateIndicator();
            syncFavoritePinButtons(key -> getString(key, key));
            return;
        }

        currentNote = note;

        if (noteTitleField != null) {
            noteTitleField.setText(orEmpty(currentNote.getTitle()));
            noteTitleField.setEditable(true);
        }
        // PDFs and images are not editable: show a native viewer instead of the editor.
        AttachmentType type = AttachmentType.fromName(currentNote.getId());
        currentAttachmentType = type;
        if (type != AttachmentType.CANVAS) {
            clearReusableCanvasIfClean();
        }
        if (type.isAttachment()) {
            showAttachment(currentNote, type);
            capturePersistedBaseline(currentNote);
            setModifiedState(false);
            updatePrivateIndicator();
            syncFavoritePinButtons(key -> getString(key, key));
            return;
        }
        hideAttachmentViewer();

        if (noteContentArea != null) {
            noteContentArea.setText(orEmpty(currentNote.getContent()));
            refreshAutocompleteTitles();
            // Without the typing pause: a freshly opened note should not show its
            // rendered blocks as raw source for a moment first.
            refreshEditorBlockRenders(true);
        }

        setNoteOpen(true);
        updateBreadcrumb(currentNote);
        properties.rebuild();
        capturePersistedBaseline(currentNote);
        setModifiedState(false);
        updatePrivateIndicator();
        syncFavoritePinButtons(key -> getString(key, key));
    }

    /**
     * Shows a lock badge next to the title for private notes: a closed lock when the body
     * is still locked (🔒 placeholder), an open lock when it is readable this session.
     * Hidden for normal notes.
     */
    private void updatePrivateIndicator() {
        if (privateIndicator == null) {
            return;
        }
        boolean isPrivate = currentNote != null && currentNote.isPrivate() && !viewingAttachment;
        UiNodes.show(privateIndicator, isPrivate);
        if (!isPrivate) {
            return;
        }
        boolean readable = com.example.jylos.service.EncryptionService.getInstance().canRead(currentNote.getId());
        if (privateIndicatorIcon != null) {
            privateIndicatorIcon.setIconLiteral(readable ? "fth-unlock" : "fth-lock");
        }
        if (privateIndicatorTip != null && bundle != null) {
            String key = readable ? "tooltip.note_private_unlocked" : "tooltip.note_private_locked";
            if (bundle.containsKey(key)) {
                privateIndicatorTip.setText(bundle.getString(key));
            }
        }
    }

    /** True while a non-editable attachment (PDF/image) is being shown. */
    public boolean isViewingAttachment() {
        return viewingAttachment;
    }

    private boolean viewingAttachment = false;
    private StackPane attachmentViewer;

    private void ensureAttachmentViewer() {
        if (attachmentViewer == null) {
            attachmentViewer = new StackPane();
            attachmentViewer.getStyleClass().add("attachment-viewer-host");
            UiNodes.show(attachmentViewer, false);
            if (editorContentStack != null) {
                editorContentStack.getChildren().add(attachmentViewer);
            }
        }
    }

    /** Shows {@code note} (a PDF/image) in a native viewer, hiding the editor chrome. */
    private void showAttachment(Note note, AttachmentType type) {
        viewingAttachment = true;
        updateEditorEditableState();
        currentAttachmentType = type;
        ensureAttachmentViewer();

        java.nio.file.Path path = (noteService != null)
                ? noteService.getNoteFilePath(note.getId()).orElse(null)
                : null;
        javafx.scene.Node attachmentContent;
        if (path != null && type == AttachmentType.CANVAS) {
            if (canReuseCurrentCanvas(path)) {
                attachmentContent = currentCanvasView;
            } else {
                attachmentContent = buildCanvasView(path);
            }
        } else if (path != null) {
            attachmentContent = com.example.jylos.ui.components.FileViewer.forAttachment(path, type, bundle);
        } else {
            Label missing = new Label(getString("viewer.file_not_found", "File not found"));
            missing.getStyleClass().add("viewer-info");
            attachmentContent = missing;
        }
        attachmentViewer.getChildren().setAll(attachmentContent);

        UiNodes.show(editorHeaderBar, true);
        UiNodes.show(noteOnlyControls, false);
        if (noteTitleField != null) {
            UiNodes.show(noteTitleField, true);
            noteTitleField.setEditable(true);
        }
        if (noteTitleLabel != null) {
            UiNodes.show(noteTitleLabel, false);
        }
        UiNodes.show(editorPreviewSplitPane, false);
        UiNodes.show(tagsContainer, false);
        UiNodes.show(propertiesSection, false);
        UiNodes.show(emptyState, false);
        UiNodes.show(attachmentViewer, true);
        updateBreadcrumb(note);
    }

    /**
     * Reuses the current canvas only while it still maps to the same file and that file
     * has not changed externally. If the editor has local unsaved changes, the current
     * view wins to avoid discarding in-memory edits.
     */
    private boolean canReuseCurrentCanvas(Path path) {
        if (path == null || currentCanvasView == null || currentCanvasPath == null || !path.equals(currentCanvasPath)) {
            return false;
        }
        if (isModified || currentCanvasView.hasUnsavedChanges()) {
            return true;
        }
        DocumentFingerprint latest = fingerprint(path);
        if (latest == null || !latest.equals(currentCanvasFingerprint)) {
            currentCanvasView = null;
            currentCanvasPath = null;
            currentCanvasFingerprint = null;
            return false;
        }
        return true;
    }

    private void clearReusableCanvasIfClean() {
        if (currentCanvasView == null) {
            return;
        }
        if (isModified || currentCanvasView.hasUnsavedChanges()) {
            return;
        }
        currentCanvasView = null;
        currentCanvasPath = null;
        currentCanvasFingerprint = null;
    }

    /** Reads, parses and renders a {@code .canvas} file; file nodes open the referenced note. */
    private javafx.scene.Node buildCanvasView(java.nio.file.Path path) {
        try {
            String json = Files.readString(path);
            com.example.jylos.util.CanvasModel.Document canvasDoc =
                    com.example.jylos.util.CanvasModel.Document.parse(json);
            CanvasReferences refs = new CanvasReferences(noteService,
                    CanvasReferences.vaultRootFor(path, currentNote != null ? currentNote.getId() : ""));
            final Supplier<List<String>> canvasReferenceSuggestions = memoizedCanvasReferenceSuggestions(refs);
            currentCanvasPath = path;
            currentCanvasView = new com.example.jylos.ui.components.CanvasView(
                    canvasDoc,
                    file -> openNoteByTitle(refs.linkToOpen(file)),
                    refs::resolve,
                    refs::canonical,
                    refs::refOfNote,
                    autocompleteTitlesSupplier,
                    canvasReferenceSuggestions,
                    this::persistCurrentCanvas,
                    this::markCanvasModified,
                    this::safeI18n);
            currentCanvasFingerprint = fingerprint(path);
            return currentCanvasView;
        } catch (Exception e) {
            logger.warning("Could not open canvas '" + path + "': " + e.getMessage());
            currentCanvasPath = null;
            currentCanvasFingerprint = null;
            currentCanvasView = null;
            Label error = new Label(getString("viewer.canvas_error", "Could not open canvas"));
            error.getStyleClass().add("viewer-info");
            return error;
        }
    }

    private Supplier<List<String>> memoizedCanvasReferenceSuggestions(CanvasReferences refs) {
        AtomicReference<List<String>> cache = new AtomicReference<>();
        return () -> {
            List<String> cached = cache.get();
            if (cached != null) {
                return cached;
            }
            List<String> loaded = refs.suggestions(
                    autocompleteTitlesSupplier != null ? autocompleteTitlesSupplier.get() : List.of());
            cache.compareAndSet(null, loaded);
            return cache.get();
        };
    }

    /** Persists the current canvas through the same note update path used for title renames. */
    private void persistCurrentCanvas(String json) {
        if (currentNote == null || currentAttachmentType != AttachmentType.CANVAS) {
            return;
        }
        String previousNoteId = currentNote.getId();
        String updatedJson = json != null ? json : "";
        String previousStoredContent = currentCanvasView != null ? currentCanvasView.lastSavedSnapshot() : null;
        if (noteTitleField != null) {
            currentNote.setTitle(normalizeCanvasTitle(noteTitleField.getText()));
            noteTitleField.setText(currentNote.getTitle());
        }
        currentNote.setContent(updatedJson);
        if (noteService == null) {
            throw new IllegalStateException("Cannot save canvas without NoteService");
        }
        noteService.updateNote(currentNote, previousStoredContent);
        currentCanvasPath = noteService.getNoteFilePath(currentNote.getId()).orElse(currentCanvasPath);
        currentCanvasFingerprint = fingerprint(currentCanvasPath);
        isModified = false;
        updateBreadcrumb(currentNote);
        if (eventBus != null) {
            eventBus.publish(new NoteEvents.NoteSavedEvent(currentNote, previousNoteId));
        }
        if (editorHooks != null && !editorHooks.isEmpty()) {
            editorHooks.fireAfterSave(currentNote, currentNote.getContent());
        }
    }

    private void markCanvasModified() {
        if (currentNote == null) {
            return;
        }
        reevaluateModifiedState();
    }

    private DocumentFingerprint fingerprint(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return new DocumentFingerprint(Files.getLastModifiedTime(path).toMillis(), Files.size(path));
        } catch (IOException e) {
            logger.fine("Could not fingerprint canvas '" + path + "': " + e.getMessage());
            return null;
        }
    }

    private String normalizeCanvasTitle(String title) {
        String normalized = orEmpty(title).trim();
        if (normalized.isEmpty()) {
            normalized = getString("canvas.new_filename", "New Canvas");
        }
        String extension = AttachmentType.extensionOf(normalized);
        if (!extension.isEmpty()) {
            int dot = normalized.lastIndexOf('.');
            normalized = dot > 0 ? normalized.substring(0, dot) : normalized;
        }
        return normalized + ".canvas";
    }

    /** Resolves an i18n key, returning the key itself (never throwing) when absent. */
    private String safeI18n(String key) {
        if (bundle == null || key == null) {
            return key;
        }
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    private void hideAttachmentViewer() {
        viewingAttachment = false;
        currentAttachmentType = AttachmentType.MARKDOWN;
        if (noteTitleField != null) {
            noteTitleField.setEditable(true);
        }
        if (attachmentViewer != null) {
            attachmentViewer.getChildren().clear(); // release rendered images
            UiNodes.show(attachmentViewer, false);
        }
    }

    public void handleSave() {
        handleSave(false);
    }

    /**
     * Saves the current note unless its file changed on disk since it was opened or last
     * saved (another app, a sync client, {@code git pull}): then a manual save asks what to
     * do and an autosave does not write at all, so external edits are never overwritten
     * silently.
     *
     * @param autosave whether this save was triggered by the autosave timer
     * @return {@code true} when the editor has no unsaved changes left afterwards
     */
    public boolean handleSave(boolean autosave) {
        boolean markdownEdit = currentNote != null && isModified && noteService != null
                && !viewingAttachment && currentAttachmentType != AttachmentType.CANVAS;
        if (markdownEdit && !confirmNoExternalChange(autosave)) {
            return !isModified;
        }
        saveNow();
        return !isModified;
    }

    /**
     * Checks the note's file against the fingerprint taken when it was opened or saved.
     *
     * @return {@code true} when the save may go ahead
     */
    private boolean confirmNoExternalChange(boolean autosave) {
        SaveConflictSupport.Decision decision = SaveConflictSupport.decide(persistedFingerprint,
                noteService.fingerprint(currentNote.getId()), autosave);
        if (decision == SaveConflictSupport.Decision.PROCEED) {
            return true;
        }
        if (decision == SaveConflictSupport.Decision.CONFLICT_PAUSE) {
            reportStatus(safeI18n("status.save_conflict_paused"));
            return false;
        }
        switch (saveConflictSupport.askResolution(currentNote.getTitle())) {
            case OVERWRITE:
                return true;
            case RELOAD:
                reloadCurrentNoteFromDisk();
                return false;
            case SAVE_COPY:
                if (saveConflictCopy()) {
                    reloadCurrentNoteFromDisk();
                }
                return false;
            default:
                return false;
        }
    }

    /** Replaces the editor's version with the note as it is on disk now. */
    private void reloadCurrentNoteFromDisk() {
        noteService.getNoteById(currentNote.getId()).ifPresent(fresh -> {
            loadNote(fresh);
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NoteUpdatedEvent(fresh));
            }
        });
    }

    /**
     * Saves the editor's text as a new note next to the conflicting one, leaving the file on
     * disk untouched.
     *
     * @return {@code true} when the copy was written
     */
    private boolean saveConflictCopy() {
        String text = noteContentArea != null ? noteContentArea.getText() : currentNote.getContent();
        String baseTitle = noteTitleField != null ? noteTitleField.getText() : currentNote.getTitle();
        String title = orEmpty(baseTitle) + " " + safeI18n("note.conflict_copy_suffix");
        String id = currentNote.getId();
        int slash = id.lastIndexOf('/');
        String folderPrefix = slash >= 0 ? id.substring(0, slash + 1) : "";
        Note copy = new Note(folderPrefix + title, title, text);
        copy.setPrivate(currentNote.isPrivate());
        try {
            Note created = noteService.createNote(copy);
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NoteCreatedEvent(created));
            }
            reportStatus(java.text.MessageFormat.format(safeI18n("status.save_conflict_copy"), created.getTitle()));
            return true;
        } catch (RuntimeException e) {
            reportSaveFailure(e);
            return false;
        }
    }

    private void saveNow() {
        if (currentNote == null) return;
        if (currentAttachmentType == AttachmentType.CANVAS && currentCanvasView != null) {
            if (isModified || currentCanvasView.hasUnsavedChanges()) {
                try {
                    persistCurrentCanvas(currentCanvasView.serialize());
                    currentCanvasView.markSaved();
                } catch (RuntimeException e) {
                    reportSaveFailure(e);
                }
            }
            return;
        }
        if (!isModified || noteService == null) return;
        String previousNoteId = currentNote.getId();
        String editorTextBeforeSave = noteContentArea != null ? noteContentArea.getText() : currentNote.getContent();
        if (noteTitleField  != null) currentNote.setTitle(noteTitleField.getText());
        if (viewingAttachment) {
            try {
                noteService.updateNote(currentNote);
            } catch (RuntimeException e) {
                reportSaveFailure(e);
                return;
            }
            capturePersistedBaseline(currentNote);
            setModifiedState(false);
            updateBreadcrumb(currentNote);
            if (eventBus != null) eventBus.publish(new NoteEvents.NoteSavedEvent(currentNote, previousNoteId));
            return;
        }
        if (noteContentArea != null) currentNote.setContent(noteContentArea.getText());
        // Property values are editable only in edit/split view; in read view the
        // model already holds the authoritative values, so we don't collect.
        if (!readOnlyView) {
            currentNote.setCustomProperties(properties.collect());
        }
        String contentBeforeHooks = currentNote.getContent();
        // Plugin editor hooks may transform the content before it is persisted.
        if (editorHooks != null && !editorHooks.isEmpty()) {
            String transformed = editorHooks.applyBeforeSave(currentNote, currentNote.getContent());
            if (!transformed.equals(currentNote.getContent())) {
                currentNote.setContent(transformed);
            }
        }
        try {
            noteService.updateNote(currentNote);
        } catch (RuntimeException e) {
            currentNote.setContent(editorTextBeforeSave != null ? editorTextBeforeSave : contentBeforeHooks);
            reportSaveFailure(e);
            return;
        }
        if (noteContentArea != null && !java.util.Objects.equals(noteContentArea.getText(), currentNote.getContent())) {
            noteContentArea.replaceDocument(currentNote.getContent());
        }
        if (noteTitleField != null && !java.util.Objects.equals(noteTitleField.getText(), currentNote.getTitle())) {
            noteTitleField.setText(currentNote.getTitle());
        }
        properties.rebuild();
        capturePersistedBaseline(currentNote);
        setModifiedState(false);
        updateBreadcrumb(currentNote);
        if (eventBus != null) eventBus.publish(new NoteEvents.NoteSavedEvent(currentNote, previousNoteId));
        if (editorHooks != null && !editorHooks.isEmpty()) {
            editorHooks.fireAfterSave(currentNote, currentNote.getContent());
        }
    }

    private void reportSaveFailure(RuntimeException error) {
        logger.log(Level.WARNING, "Could not save current note", error);
        statusAction.accept(safeI18n("status.error_saving"));
    }

    private void reportStatus(String message) {
        if (statusAction != null) {
            statusAction.accept(message);
        }
    }

    // ============================================================
    // Properties panel — internal
    // ============================================================

    /** Opens a note by title via the owning shell callback. */
    private void openNoteByTitle(String title) {
        if (title == null || title.isBlank() || wikiLinkHandler == null) {
            return;
        }
        wikiLinkHandler.openNoteByTitle(title);
    }

    private void refreshAutocompleteTitles() {
        if (noteContentArea == null) return;
        List<String> titles = autocompleteTitlesSupplier != null
                ? autocompleteTitlesSupplier.get()
                : List.of();
        noteContentArea.setAutocompleteTitles(titles != null ? titles : List.of());
    }

    // ============================================================
    // EventBus subscriptions
    // ============================================================

    private void subscribeToEvents() {
        if (eventBus == null) return;

        subscriptions.add(eventBus.subscribe(SystemActionEvent.class, event -> Platform.runLater(() -> {
            switch (event.getActionType()) {
                case BOLD      -> insertMarkdownFormat("**", "**");
                case ITALIC    -> insertMarkdownFormat("*", "*");
                case UNDERLINE -> insertMarkdownFormat("<u>", "</u>");
                case STRIKE    -> insertMarkdownFormat("~~", "~~");
                case HIGHLIGHT -> insertMarkdownFormat("==", "==");
                case HEADING1  -> insertLinePrefix("# ");
                case HEADING2  -> insertLinePrefix("## ");
                case HEADING3  -> insertLinePrefix("### ");
                case BULLET_LIST   -> insertLinePrefix("- ");
                case NUMBERED_LIST -> insertLinePrefix("1. ");
                case TODO_LIST -> insertTodoList();
                case QUOTE     -> insertLinePrefix("> ");
                case CODE      -> insertCodeBlock();
                case LINK      -> handleLinkDialog();
                case RICH_LINK -> handleRichLinkDialog();
                case IMAGE     -> handleImageDialog();
                case SAVE      -> handleSave();
                default        -> { /* not handled here */ }
            }
        })));

        subscriptions.add(eventBus.subscribe(NoteEvents.NoteCreatedEvent.class,
                event -> Platform.runLater(this::refreshAutocompleteTitles)));
        subscriptions.add(eventBus.subscribe(NoteEvents.NoteSavedEvent.class,
                event -> Platform.runLater(this::refreshAutocompleteTitles)));
        subscriptions.add(eventBus.subscribe(NoteEvents.NoteUpdatedEvent.class,
                event -> Platform.runLater(this::refreshAutocompleteTitles)));
        subscriptions.add(eventBus.subscribe(NoteEvents.NoteDeletedEvent.class,
                event -> Platform.runLater(this::refreshAutocompleteTitles)));
        subscriptions.add(eventBus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class,
                event -> Platform.runLater(this::refreshAutocompleteTitles)));

        // A rendered block can summarise the whole vault, so its result goes stale when
        // any note changes — not only when the note holding the block is edited.
        for (Class<? extends com.example.jylos.event.AppEvent> vaultEvent : java.util.List.of(
                NoteEvents.NoteCreatedEvent.class, NoteEvents.NoteSavedEvent.class,
                NoteEvents.NoteUpdatedEvent.class, NoteEvents.NoteDeletedEvent.class,
                NoteEvents.NotesRefreshRequestedEvent.class)) {
            subscriptions.add(eventBus.subscribe(vaultEvent,
                    event -> Platform.runLater(() -> refreshEditorBlockRenders(false))));
        }

        if (noteTitleField != null) {
            noteTitleField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (currentNote != null) {
                    reevaluateModifiedState();
                }
            });
        }

    }

    public void teardown() {
        blockRenderSupport.shutdown();
        preview.shutdown();
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
    }

    // ============================================================
    // Markdown formatting
    // ============================================================

    private void insertMarkdownFormat(String prefix, String suffix) {
        if (noteContentArea == null) return;
        String sel = noteContentArea.getSelectedText();
        if (sel != null && !sel.isEmpty()) {
            noteContentArea.replaceSelection(prefix + sel + suffix);
        } else {
            noteContentArea.replaceSelection(prefix + suffix, prefix.length());
        }
        noteContentArea.requestFocus();
        reevaluateModifiedState();
    }

    private void insertLinePrefix(String prefix) {
        if (noteContentArea == null) return;
        int pos = noteContentArea.getCaretPosition();
        String t = orEmpty(noteContentArea.getText());
        int lineStart = t.lastIndexOf('\n', pos - 1) + 1;
        if (t.substring(lineStart, pos).trim().isEmpty() && lineStart == pos) {
            noteContentArea.replaceRange(pos, pos, prefix, pos + prefix.length());
        } else {
            String insertion = "\n" + prefix;
            noteContentArea.replaceRange(pos, pos, insertion, pos + insertion.length());
        }
        noteContentArea.requestFocus();
        reevaluateModifiedState();
    }

    private void insertTodoList() {
        if (noteContentArea == null) return;
        int pos = noteContentArea.getCaretPosition();
        String t = orEmpty(noteContentArea.getText());
        String item = applyInsertHooks("- [ ] ");
        int lineStart = t.lastIndexOf('\n', pos - 1) + 1;
        if (t.substring(lineStart, pos).trim().isEmpty()) {
            noteContentArea.replaceRange(pos, pos, item, pos + item.length());
        } else {
            String insertion = "\n" + item;
            noteContentArea.replaceRange(pos, pos, insertion, pos + insertion.length());
        }
        noteContentArea.requestFocus();
        reevaluateModifiedState();
    }

    // Always inserts a fenced code block, matching the toolbar tooltip
    // ("Code block") — inline code has its own affordance via backticks typed
    // directly, so this button no longer silently falls back to inline code
    // for a single-line or empty selection.
    private void insertCodeBlock() {
        if (noteContentArea == null) return;
        String sel = noteContentArea.getSelectedText();
        if (sel != null && !sel.isEmpty()) {
            noteContentArea.replaceSelection("```\n" + sel + "\n```");
        } else {
            String insertion = "```\n\n```";
            noteContentArea.replaceSelection(insertion, 4);
        }
        noteContentArea.requestFocus();
        reevaluateModifiedState();
    }

    private void handleLinkDialog() {
        if (noteContentArea == null) return;
        TextInputDialog d = new TextInputDialog(getString("dialog.link.default_url", "https://"));
        d.setTitle(getString("dialog.link.title", "Insert Link"));
        d.setHeaderText(getString("dialog.link.header", "Enter URL:"));
        d.setContentText(getString("dialog.link.content", "URL:"));
        com.example.jylos.ui.UiDialogs.show(d).filter(s -> !s.trim().isEmpty()).ifPresent(url -> {
            String sel = noteContentArea.getSelectedText();
            String label = (sel != null && !sel.isEmpty()) ? sel
                    : getString("dialog.link.default_text", "link text");
            String link = applyInsertHooks("[" + label + "](" + url.trim() + ")");
            if (sel != null && !sel.isEmpty()) noteContentArea.replaceSelection(link);
            else {
                int pos = noteContentArea.getCaretPosition();
                noteContentArea.replaceRange(pos, pos, link, pos + link.length());
            }
            noteContentArea.requestFocus();
            reevaluateModifiedState();
        });
    }

    /**
     * Prompts for a URL, fetches its metadata off the FX thread, and inserts a
     * {@code ::: rich-link} block at the caret. Fetching never blocks the UI; on any
     * failure the service returns a minimal card (URL + host) so a block is still
     * inserted.
     */
    private void handleRichLinkDialog() {
        if (noteContentArea == null) return;
        TextInputDialog d = new TextInputDialog("https://");
        d.setTitle(getString("dialog.rich_link.title", "Insert rich link"));
        d.setHeaderText(getString("dialog.rich_link.header", "Paste a URL:"));
        d.setContentText(getString("dialog.rich_link.content", "URL:"));
        com.example.jylos.ui.UiDialogs.show(d)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .ifPresent(this::fetchAndInsertRichLink);
    }

    private void fetchAndInsertRichLink(String url) {
        Task<RichLinks.RichLink> task = new Task<>() {
            @Override
            protected RichLinks.RichLink call() {
                return richLinkService.fetch(url);
            }
        };
        task.setOnSucceeded(e -> insertRichLinkBlock(task.getValue()));
        task.setOnFailed(e -> insertRichLinkBlock(
                new RichLinks.RichLink(url, "", "", "", RichLinks.hostOf(url))));
        Thread thread = new Thread(task, "rich-link-fetch");
        thread.setDaemon(true);
        thread.start();
    }

    /** Inserts the rich-link block on its own paragraph at the caret. */
    private void insertRichLinkBlock(RichLinks.RichLink link) {
        if (noteContentArea == null || link == null) return;
        String block = RichLinks.toMarkdown(link);
        int pos = noteContentArea.getCaretPosition();
        String text = orEmpty(noteContentArea.getText());
        String before = text.substring(0, pos);
        // Keep the block as its own paragraph (CommonMark needs a blank line around it).
        String lead = before.isEmpty() || before.endsWith("\n\n") ? "" : before.endsWith("\n") ? "\n" : "\n\n";
        String insertion = lead + block + "\n";
        noteContentArea.replaceRange(pos, pos, insertion, pos + insertion.length());
        noteContentArea.requestFocus();
        reevaluateModifiedState();
    }

    private void handleImageDialog() {
        if (noteContentArea == null) return;
        TextInputDialog d = new TextInputDialog("");
        d.setTitle(getString("dialog.image.title", "Insert Image"));
        d.setHeaderText(getString("dialog.image.header", "Enter image URL or path:"));
        d.setContentText(getString("dialog.image.content", "Image:"));
        com.example.jylos.ui.UiDialogs.show(d).filter(s -> !s.trim().isEmpty()).ifPresent(path -> {
            String sel = noteContentArea.getSelectedText();
            String alt = (sel != null && !sel.isEmpty()) ? sel
                    : getString("dialog.image.default_alt", "image");
            String img = applyInsertHooks("![" + alt + "](" + path.trim() + ")");
            if (sel != null && !sel.isEmpty()) noteContentArea.replaceSelection(img);
            else {
                int pos = noteContentArea.getCaretPosition();
                noteContentArea.replaceRange(pos, pos, img, pos + img.length());
            }
            noteContentArea.requestFocus();
            reevaluateModifiedState();
        });
    }

    // ============================================================
    // Editor commands (called from other controllers)
    // ============================================================

    // ============================================================
    // Tag management & metadata (called from MainController)
    // ============================================================

    public void loadNoteTags(Note note, FlowPane fp, Runnable onAdd, Consumer<Tag> onRemove) {
        if (fp == null) return;
        fp.getChildren().clear();
        if (note == null || note.getId() == null || note.getId().isEmpty()) return;
        try {
            List<Tag> tags = tagService != null ? tagService.getTagsForNote(note) : List.of();
            for (Tag tag : tags) {
                HBox box = new HBox(4); box.setAlignment(Pos.CENTER_LEFT); box.getStyleClass().add("tag-container");
                Label lbl = new Label(tag.getTitle()); lbl.getStyleClass().add("tag-label");
                lbl.setTooltip(new Tooltip(getString("tooltip.tag_double_click_remove", "Double-click to remove")));
                lbl.setOnMouseClicked(e -> { if (e.getClickCount() == 2) onRemove.accept(new Tag(tag.getId(), tag.getTitle(), null, null)); });
                Button rm = new Button("×"); rm.getStyleClass().add("tag-remove-btn");
                rm.setTooltip(new Tooltip(getString("tooltip.remove_tag_from_note", "Remove tag from note")));
                rm.setOnAction(e -> onRemove.accept(new Tag(tag.getId(), tag.getTitle(), null, null)));
                box.getChildren().addAll(lbl, rm);
                fp.getChildren().add(box);
            }
            Button addBtn = new Button(getString("action.add_tag_line", "+ Add tag")); addBtn.getStyleClass().add("add-tag-button");
            addBtn.setOnAction(e -> onAdd.run());
            fp.getChildren().add(addBtn);
        } catch (Exception e) {
            logger.warning("Failed to load tags for note " + note.getId() + ": " + e.getMessage());
        }
    }

    public void updateNoteMetadata(Note note, Label modLabel, Label created, Label modified) {
        if (note == null) {
            setLabelSafe(modLabel, ""); setLabelSafe(created, "-"); setLabelSafe(modified, "-");
            return;
        }
        setLabelSafe(modLabel, note.getModifiedDate() != null ? "Modified " + note.getModifiedDate() : "");
        setLabelSafe(created,  orDash(note.getCreatedDate()));
        setLabelSafe(modified, orDash(note.getModifiedDate()));
    }

    // ============================================================
    // Preview, word count, editor chrome
    // ============================================================

    public void registerEditorBlockRenderer(String pluginId, String language,
            com.example.jylos.plugin.EditorBlockRenderer renderer) {
        blockRenderSupport.registerRenderer(pluginId, language, renderer);
        Platform.runLater(() -> refreshEditorBlockRenders(true));
    }

    public void unregisterEditorBlockRenderers(String pluginId) {
        blockRenderSupport.unregisterRenderers(pluginId);
        Platform.runLater(() -> refreshEditorBlockRenders(true));
    }

    /**
     * Recomputes the HTML shown in place of plugin-claimed fenced blocks and hands it to
     * the editor.
     *
     * @param immediate skip the typing pause — used when the note, or the set of
     *                  registered renderers, changed rather than the text
     */
    private void refreshEditorBlockRenders(boolean immediate) {
        if (noteContentArea == null) {
            return;
        }
        blockRenderSupport.requestRender(currentNote, noteContentArea.getText(), immediate,
                rendered -> Platform.runLater(() -> noteContentArea.setBlockRenders(rendered)));
    }

    public void registerPreviewEnhancer(String pluginId, PreviewEnhancer enhancer) {
        preview.registerEnhancer(pluginId, enhancer);
    }

    public void unregisterPreviewEnhancer(String pluginId) {
        preview.unregisterEnhancer(pluginId);
    }

    public void refreshPreview(boolean darkTheme) {
        preview.refresh(darkTheme);
    }

    public boolean isPreviewVisible() {
        return !viewingAttachment
                && previewPane != null
                && previewPane.isVisible()
                && previewPane.isManaged();
    }

    public void clearPreview() {
        preview.clear();
    }

    public void ensurePreviewWebViewThemeClass() {
        preview.ensureThemeClass();
    }

    public void syncFavoritePinButtons(Function<String, String> i18n) {
        if (favoriteButton != null) {
            boolean hasNote = currentNote != null;
            boolean isFav = hasNote && currentNote.isFavorite();
            favoriteButton.setDisable(!hasNote);
            favoriteButton.setSelected(isFav);
            if (favoriteButton.getTooltip() != null && i18n != null) {
                favoriteButton.getTooltip().setText(
                        !hasNote ? i18n.apply("tooltip.add_favorite")
                                : isFav ? i18n.apply("action.remove_favorite") : i18n.apply("action.add_favorite"));
            }
        }
        if (pinButton != null) {
            boolean hasNote = currentNote != null;
            boolean isPinned = hasNote && currentNote.isPinned();
            pinButton.setDisable(!hasNote);
            pinButton.setSelected(isPinned);
            if (pinButton.getTooltip() != null && i18n != null) {
                pinButton.getTooltip().setText(
                        !hasNote ? i18n.apply("tooltip.pin_note")
                                : isPinned ? i18n.apply("action.unpin_note") : i18n.apply("tooltip.pin_note"));
            }
        }
    }

    /**
     * Navigates to a heading in whichever view is currently showing: the raw-source
     * character offset for the CodeMirror editor, or the rendered heading's anchor id
     * for the reading view (see {@code MarkdownProcessor.slugifyHeading}).
     */
    public void navigateToHeading(int offset, String headingSlug) {
        if (isPreviewVisible()) {
            preview.scrollToHeading(headingSlug);
        } else if (noteContentArea != null) {
            noteContentArea.scrollToPosition(offset);
        }
    }

    public void applyEditorZoom(double editorFontSize) {
        this.contentFontSize = editorFontSize;
        if (noteContentArea != null) {
            noteContentArea.setEditorFontSize(editorFontSize);
        }
        if (noteTitleField != null) {
            noteTitleField.setStyle("-fx-font-size: " + (editorFontSize + 2) + "px;");
        }
        if (isPreviewVisible()) {
            refreshPreview(preview.lastDarkTheme());
        }
    }

    /** Applies the active JavaFX theme values inside the embedded editor page. */
    public void applyEditorTheme(boolean darkTheme, String accentColor) {
        if (noteContentArea != null) {
            noteContentArea.setEditorTheme(darkTheme, accentColor);
        }
    }

    public void initializeTagsBarCollapsed() {
        if (toggleTagsBtn != null) {
            toggleTagsBtn.setSelected(false);
        }
        if (tagsContainer != null) {
            UiNodes.show(tagsContainer, false);
        }
    }

    public void loadNoteTagsForNote(Note note, Runnable onAdd, Consumer<Tag> onRemove) {
        loadNoteTags(note, tagsFlowPane, onAdd, onRemove);
    }

    public void performUndo() {
        if (noteContentArea != null && noteContentArea.undo()) {
            Platform.runLater(this::reevaluateModifiedState);
        }
    }

    public void performRedo() {
        if (noteContentArea != null && noteContentArea.redo()) {
            Platform.runLater(this::reevaluateModifiedState);
        } else {
            reportStatus(safeI18n("status.redo_not_available"));
        }
    }

    @FXML
    private void handleFormatToolbarUndo(ActionEvent event) {
        performUndo();
    }

    @FXML
    private void handleFormatToolbarRedo(ActionEvent event) {
        performRedo();
    }

    public void performCut() {
        if (noteTitleField != null && noteTitleField.isFocused()) {
            noteTitleField.cut();
        } else if (noteContentArea != null) {
            noteContentArea.cut();
        }
    }

    public void performCopy() {
        if (noteTitleField != null && noteTitleField.isFocused()) {
            noteTitleField.copy();
        } else if (noteContentArea != null) {
            noteContentArea.copy();
        }
    }

    public void performPaste() {
        if (noteTitleField != null && noteTitleField.isFocused()) {
            noteTitleField.paste();
        } else if (noteContentArea != null) {
            noteContentArea.paste();
        }
    }

    public void performFind(Function<String, String> i18n, Consumer<String> status) {
        if (noteContentArea != null) {
            noteContentArea.openSearch();
        }
    }

    public void performReplace(Function<String, String> i18n, Consumer<String> status) {
        if (noteContentArea != null) {
            noteContentArea.openReplace();
        }
    }

    private String liveEditorContent(Note note) {
        if (noteContentArea != null) {
            return orEmpty(noteContentArea.getText());
        }
        return note != null ? orEmpty(note.getContent()) : "";
    }

    /** What the properties panel reads from, and tells, this editor. */
    private final class PropertiesHost implements PropertiesPanelSupport.Host {
        @Override
        public PropertiesPanelSupport.Nodes nodes() {
            return new PropertiesPanelSupport.Nodes(propertiesSection, propertiesBody, propertiesContent,
                    propertiesCollapseIcon, addPropertyBtn, addPropertyHeaderBtn);
        }

        @Override
        public Note currentNote() {
            return currentNote;
        }

        @Override
        public boolean readOnlyView() {
            return readOnlyView;
        }

        @Override
        public boolean viewingAttachment() {
            return viewingAttachment;
        }

        @Override
        public void propertiesChanged() {
            reevaluateModifiedState();
        }

        @Override
        public void openLink(String link) {
            openNoteByTitle(link);
        }

        @Override
        public String text(String key, String fallback) {
            return getString(key, fallback);
        }
    }

    /** What the reading view reads from this editor. */
    private final class PreviewHost implements EditorPreviewSupport.Host {
        @Override
        public javafx.scene.web.WebView previewWebView() {
            return previewWebView;
        }

        @Override
        public boolean previewShown() {
            return isPreviewVisible();
        }

        @Override
        public Note currentNote() {
            return currentNote;
        }

        @Override
        public String liveContent() {
            return liveEditorContent(currentNote);
        }

        @Override
        public NoteService noteService() {
            return noteService;
        }

        @Override
        public boolean readableLineLength() {
            return readableLineLength;
        }

        @Override
        public double contentFontSize() {
            return contentFontSize;
        }

        @Override
        public void openLink(String link) {
            if (wikiLinkHandler != null) {
                wikiLinkHandler.openNoteByTitle(link);
            }
        }

        @Override
        public void openTag(String tag) {
            if (tagLinkHandler != null) {
                tagLinkHandler.accept(tag);
            }
        }
    }

    public void publishAction(EventBus bus, SystemActionEvent.ActionType type) {
        if (bus != null && type != null) bus.publish(new SystemActionEvent(type));
    }

    // ============================================================
    // Private utilities
    // ============================================================

    private void publish(SystemActionEvent.ActionType type) {
        if (eventBus != null) eventBus.publish(new SystemActionEvent(type));
    }

    private void publishModified() {
        if (currentNote != null) {
            noteModifiedAction.accept(currentNote);
        }
    }

    private void capturePersistedBaseline(Note note) {
        persistedFingerprint = note != null && note.getId() != null && noteService != null
                ? noteService.fingerprint(note.getId()).orElse(null)
                : null;
        persistedTitle = note != null ? orEmpty(note.getTitle()) : "";
        persistedContent = note != null ? orEmpty(note.getContent()) : "";
        persistedCustomProperties = snapshotCustomProperties(note != null ? note.getCustomProperties() : null);
    }

    private void resetEditorUndoHistory() {
        if (noteContentArea == null) {
            return;
        }
        noteContentArea.resetUndoHistory();
    }

    private Map<String, String> snapshotCustomProperties(Map<String, String> properties) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        if (properties == null) {
            return snapshot;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey() != null) {
                snapshot.put(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return snapshot;
    }

    private Map<String, String> currentPropertySnapshot() {
        if (readOnlyView || !properties.hasRows()) {
            return snapshotCustomProperties(currentNote != null ? currentNote.getCustomProperties() : null);
        }
        return snapshotCustomProperties(properties.collect());
    }

    private void reevaluateModifiedState() {
        if (currentNote == null) {
            setModifiedState(false);
            return;
        }
        boolean dirtyTitle = !orEmpty(noteTitleField != null ? noteTitleField.getText() : null).equals(persistedTitle);
        boolean dirtyContent;
        if (currentAttachmentType == AttachmentType.CANVAS && currentCanvasView != null) {
            dirtyContent = currentCanvasView.hasUnsavedChanges();
        } else if (viewingAttachment) {
            dirtyContent = false;
        } else {
            dirtyContent = !orEmpty(noteContentArea != null ? noteContentArea.getText() : null).equals(persistedContent);
        }
        boolean dirtyProperties = !currentPropertySnapshot().equals(persistedCustomProperties);
        setModifiedState(dirtyTitle || dirtyContent || dirtyProperties);
    }

    private void setModifiedState(boolean dirty) {
        boolean changed = isModified != dirty;
        isModified = dirty;
        if (changed || dirty) {
            publishModified();
        }
    }

    private String getString(String key, String fallback) {
        if (bundle == null) return fallback;
        return bundle.containsKey(key) ? bundle.getString(key) : fallback;
    }


    private static String orEmpty(String s)  { return s != null ? s : ""; }
    private static String orDash(String s)   { return (s != null && !s.isBlank()) ? s : "-"; }
    private static void setLabelSafe(Label l, String text) { if (l != null) l.setText(text); }
}
