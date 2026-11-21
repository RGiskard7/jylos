package com.example.jylos.ui.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.event.AppEvent;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.plugin.EditorBlockRenderer;
import com.example.jylos.plugin.EditorBlockRendererRegistry;
import com.example.jylos.plugin.EditorHooks;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.plugin.PreviewEnhancerRegistry;
import com.example.jylos.service.CanvasReferences;
import com.example.jylos.service.FileFingerprint;
import com.example.jylos.service.LinkSuggestion;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.CanvasView;
import com.example.jylos.ui.components.FileViewer;
import com.example.jylos.ui.components.MarkdownEditorView;
import com.example.jylos.ui.web.HtmlView;
import com.example.jylos.ui.web.JavaFxHtmlView;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.CanvasModel;
import com.example.jylos.util.MarkdownPreview;
import com.example.jylos.util.SystemBrowser;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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
public class EditorController implements PreviewEnhancerRegistry,
        EditorBlockRendererRegistry {

    private static final Logger logger = LoggerConfig.getLogger(EditorController.class);

    // ── Service / state ──────────────────────────────────────────────────────
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

    /** How the note is shown: as source, side by side with the reading view, or only read. */
    public enum ViewMode {
        EDITOR_ONLY,
        SPLIT,
        PREVIEW_ONLY
    }

    private final ReadOnlyObjectWrapper<ViewMode> viewMode = new ReadOnlyObjectWrapper<>(ViewMode.EDITOR_ONLY);

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
    private Supplier<List<LinkSuggestion>> autocompleteTitlesSupplier = List::of;

    private final EditorBlockRenderSupport blockRenderSupport = new EditorBlockRenderSupport();
    private WikiLinkHandler wikiLinkHandler;
    private Consumer<String> tagLinkHandler;
    /** The reading view: renders, scroll, heading jumps, its page bridge and plugin enhancers. */
    private final EditorPreviewSupport preview = new EditorPreviewSupport(new PreviewHost());
    /** Bold, headings, lists, and the link and image dialogs. */
    /** A board note shown as its board (Kanban), or as Markdown on request. */
    private final EditorKanbanSupport kanban = new EditorKanbanSupport(new EditorKanbanSupport.Host(
            this::boardLayer, this::getCurrentContent, text -> {
                if (this.noteContentArea != null) {
                    this.noteContentArea.setText(text);
                }
            },
            () -> {
                reevaluateModifiedState();
                handleSave(true);
            },
            this::showBoard, this::openNoteByTitle, this::publishCreated, () -> preview.lastDarkTheme()),
            () -> this.noteService, key -> getString(key, key));
    /** The note's folders and its lock, in the title row. */
    private final EditorTitleRowSupport titleRow = new EditorTitleRowSupport();
    private final EditorFormattingSupport formatting = new EditorFormattingSupport(() -> this.noteContentArea,
            this::applyInsertHooks, this::reevaluateModifiedState, this::getString);
    /** Inserting templates and dates in the open note. */
    private final EditorTemplateSupport templates = new EditorTemplateSupport(() -> this.noteContentArea,
            this::noteOpenForEditing, () -> {
                propertiesInserted = true; // the panel shows only some of them: the note has changed
                properties.rebuild();
                reevaluateModifiedState();
            });
    private boolean propertiesInserted;
    private CanvasView currentCanvasView;
    private Path currentCanvasPath;
    private DocumentFingerprint currentCanvasFingerprint;
    private AttachmentType currentAttachmentType = AttachmentType.MARKDOWN;

    private record DocumentFingerprint(long lastModifiedMillis, long size) {
    }

    /** Opens a note when the user clicks a wiki-link in the Markdown preview. */
    @FunctionalInterface
    public interface WikiLinkHandler {
        /** Opens the note {@code title} names, in a new tab when {@code newTab} (Cmd/Ctrl+click). */
        void openNoteByTitle(String title, boolean newTab);
    }

    // ── FXML — header ────────────────────────────────────────────────────────
    @FXML private VBox editorContainer;
    @FXML private ScrollPane editorTabScroll;
    @FXML private HBox editorTabBar;
    @FXML private Label notePathLabel;
    @FXML private HBox editorHeaderBar;
    /** The header's controls for the open note; with none, only back and forward remain. */
    @FXML private HBox noteHeaderControls;
    @FXML private Button boardViewBtn;
    @FXML private Tooltip boardViewTooltip;
    @FXML private FontIcon boardViewIcon;
    @FXML private StackPane editorContentStack;
    @FXML private ScrollPane emptyState;
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

    // ── FXML — tags bar ──────────────────────────────────────────────────────
    @FXML private VBox tagsContainer;
    @FXML private FlowPane tagsFlowPane;
    @FXML private Label modifiedDateLabel;

    // ── FXML — properties panel ──────────────────────────────────────────────
    @FXML private ToggleButton togglePropertiesBtn; // opens and closes the panel
    @FXML private Node  propertiesDot;       // on the button: the note has properties
    @FXML private VBox  propertiesSection;   // the panel under the title row
    @FXML private VBox  propertiesContent;   // one row per property
    @FXML private Label propertiesEmptyLabel; // reading view, note without properties
    @FXML private HBox  addPropertyRow;      // "+ Add property", editing views only
    @FXML private Button addPropertyBtn;

    // ── FXML — editor / preview ──────────────────────────────────────────────
    @FXML private SplitPane editorPreviewSplitPane;
    @FXML private VBox      editorPane;
    @FXML private MarkdownEditorView noteContentArea;
    @FXML private HBox      formatToolbarContainer;
    @FXML private VBox      previewPane;
    /** The reading view's page. */
    @FXML private JavaFxHtmlView previewWebView;

    // ── Setters ──────────────────────────────────────────────────────────────

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
        titleRow.setBundle(bundle);
        EditorLabelsSupport.apply(noteContentArea, previewWebView, this::getString);
    }

    public void wire(EventBus eventBus, NoteService noteService, TagService tagService, ResourceBundle bundle,
            Consumer<Note> noteModifiedAction, Consumer<String> statusAction,
            Supplier<List<LinkSuggestion>> autocompleteTitlesSupplier, boolean livePreviewEnabled,
            Consumer<Boolean> livePreviewPreferenceAction, WikiLinkHandler wikiLinkHandler,
            Consumer<String> tagLinkHandler, EditorHooks editorHooks) {
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
    private EditorHooks editorHooks;

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

    // ── Header control presentation ──────────────────────────────────────────

    /**
     * Renders the compact editor-header controls as icon-only buttons.
     */
    public void applyHeaderControlsPresentation() {
        applyIconOnly(toggleTagsBtn);
        applyIconOnly(readingModeButton);
        applyIconOnly(editorSaveBtn);
        applyIconOnly(pinButton);
        applyIconOnly(favoriteButton);
    }

    private void applyIconOnly(ButtonBase btn) {
        if (btn == null) return;
        btn.setText("");
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        if (!btn.getStyleClass().contains("icon-only-btn")) {
            btn.getStyleClass().add("icon-only-btn");
        }
    }

    // ── Navigation state (called from MainController) ────────────────────────

    /**
     * Enables or disables the back/forward navigation arrow buttons.
     * Called by MainController after each history change.
     */
    public void updateNavigationState(boolean canGoBack, boolean canGoForward) {
        if (navBackBtn    != null) navBackBtn.setDisable(!canGoBack);
        if (navForwardBtn != null) navForwardBtn.setDisable(!canGoForward);
    }

    // ── State accessors ──────────────────────────────────────────────────────

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
        // Jylos itself just wrote the note (a rename, a pin, a favourite): the file on disk and
        // the title are ours, before the title field changes and is compared with them. Edits
        // still unsaved in the editor stay unsaved; they are not an outside change.
        persistedTitle = orEmpty(note.getTitle());
        persistedFingerprint = noteService != null && note.getId() != null
                ? noteService.fingerprint(note.getId()).orElse(null)
                : null;
        if (noteTitleField != null) {
            noteTitleField.setText(note.getTitle() != null ? note.getTitle() : "");
        }
        titleRow.showPath(currentNote);
        if (!isModified) {
            capturePersistedBaseline(currentNote);
        }
    }

    /** Live editor content (includes unsaved edits); empty while viewing an attachment. */
    public String getCurrentContent() {
        if (currentAttachmentType == AttachmentType.CANVAS && currentCanvasView != null) {
            // The canvas as it is on screen, unsaved changes included.
            return currentCanvasView.serialize();
        }
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
    public ScrollPane getEditorTabScroll() { return editorTabScroll; }

    /** Leaves the title of the open note selected in its field, ready to type a new name. */
    public void beginTitleRename() {
        if (noteTitleField != null && noteTitleField.isVisible() && !noteTitleField.isDisabled()) {
            noteTitleField.requestFocus();
            noteTitleField.selectAll();
        }
    }
    public Label           getModifiedDateLabel()       { return modifiedDateLabel; }
    public HtmlView getPreviewWebView() { return previewWebView; }

    // ── FXML action handlers ─────────────────────────────────────────────────

    @FXML private void handleToggleTags(ActionEvent e)        { showTagsBar(toggleTagsBtn.isSelected()); }
    @FXML private void handleToggleReadingMode(ActionEvent e) { toggleReadingMode(); }
    @FXML private void handleTogglePin(ActionEvent e)         { publish(SystemActionEvent.ActionType.TOGGLE_PIN); }
    @FXML private void handleSave(ActionEvent e)               { handleSave(); }
    @FXML private void handleDelete(ActionEvent e)             { publish(SystemActionEvent.ActionType.DELETE); }
    @FXML private void handleToggleFavorite(ActionEvent e)    { publish(SystemActionEvent.ActionType.TOGGLE_FAVORITE); }
    @FXML private void handleToggleRightPanel(ActionEvent e)  { publish(SystemActionEvent.ActionType.TOGGLE_RIGHT_PANEL); }
    @FXML private void handleHeading1(ActionEvent e)          { applyFormat(SystemActionEvent.ActionType.HEADING1); }
    @FXML private void handleHeading2(ActionEvent e)          { applyFormat(SystemActionEvent.ActionType.HEADING2); }
    @FXML private void handleHeading3(ActionEvent e)          { applyFormat(SystemActionEvent.ActionType.HEADING3); }
    @FXML private void handleBold(ActionEvent e)              { applyFormat(SystemActionEvent.ActionType.BOLD); }
    @FXML private void handleItalic(ActionEvent e)            { applyFormat(SystemActionEvent.ActionType.ITALIC); }
    @FXML private void handleStrike(ActionEvent e)            { applyFormat(SystemActionEvent.ActionType.STRIKE); }
    @FXML private void handleUnderline(ActionEvent e)         { applyFormat(SystemActionEvent.ActionType.UNDERLINE); }
    @FXML private void handleHighlight(ActionEvent e)         { applyFormat(SystemActionEvent.ActionType.HIGHLIGHT); }
    @FXML private void handleLink(ActionEvent e)              { applyFormat(SystemActionEvent.ActionType.LINK); }
    @FXML private void handleRichLink(ActionEvent e)          { applyFormat(SystemActionEvent.ActionType.RICH_LINK); }
    @FXML private void handleImage(ActionEvent e)             { applyFormat(SystemActionEvent.ActionType.IMAGE); }
    @FXML private void handleTodoList(ActionEvent e)          { applyFormat(SystemActionEvent.ActionType.TODO_LIST); }
    @FXML private void handleBulletList(ActionEvent e)        { applyFormat(SystemActionEvent.ActionType.BULLET_LIST); }
    @FXML private void handleNumberedList(ActionEvent e)      { applyFormat(SystemActionEvent.ActionType.NUMBERED_LIST); }
    @FXML private void handleQuote(ActionEvent e)             { applyFormat(SystemActionEvent.ActionType.QUOTE); }
    @FXML private void handleCode(ActionEvent e)              { applyFormat(SystemActionEvent.ActionType.CODE); }

    /** Back arrow — delegates history logic to MainController via EventBus. */
    @FXML private void handleNavigateBack(ActionEvent e)    { publish(SystemActionEvent.ActionType.NAVIGATE_BACK); }

    /** Forward arrow — delegates history logic to MainController via EventBus. */
    @FXML private void handleNavigateForward(ActionEvent e) { publish(SystemActionEvent.ActionType.NAVIGATE_FORWARD); }

    // ── Properties panel handlers ────────────────────────────────────────────

    /** Opens or closes the properties panel (the button in the title row). */
    @FXML
    private void handleToggleProperties(ActionEvent event) {
        properties.setOpen(togglePropertiesBtn.isSelected());
    }

    /** Adds an empty property row to fill in place (editing views only). */
    @FXML
    private void handleAddProperty(ActionEvent event) {
        properties.addProperty();
    }

    /** The view mode; the window listens to it to show the mode in the status bar. */
    public ReadOnlyObjectProperty<ViewMode> viewModeProperty() {
        return viewMode.getReadOnlyProperty();
    }

    public ViewMode getViewMode() {
        return viewMode.get();
    }

    /**
     * Shows the note as source, as the reading view, or both side by side. The reading view
     * is read-only.
     *
     * @param mode the view mode; {@code null} is ignored
     */
    public void setViewMode(ViewMode mode) {
        if (mode == null) {
            return;
        }
        layoutPanes(mode);
        if (mode != ViewMode.EDITOR_ONLY && isPreviewVisible()) {
            refreshPreview(preview.lastDarkTheme());
        }
        setReadOnlyView(mode == ViewMode.PREVIEW_ONLY);
        updateReadingModeControl(mode == ViewMode.EDITOR_ONLY);
        viewMode.set(mode);
    }

    /** Switches between editing and the reading view; from the split view, back to editing. */
    public void toggleReadingMode() {
        ViewMode next = getViewMode() == ViewMode.EDITOR_ONLY ? ViewMode.PREVIEW_ONLY : ViewMode.EDITOR_ONLY;
        setViewMode(next);
        statusAction.accept(next == ViewMode.EDITOR_ONLY
                ? getString("status.mode_editor", "Editing view")
                : getString("status.mode_preview", "Reading view"));
    }

    /** Puts the source pane, the reading pane or both in the editor's split pane. */
    private void layoutPanes(ViewMode mode) {
        if (editorPreviewSplitPane == null || editorPane == null || previewPane == null) {
            return;
        }
        boolean source = mode != ViewMode.PREVIEW_ONLY;
        boolean reading = mode != ViewMode.EDITOR_ONLY;
        UiNodes.show(editorPane, source);
        UiNodes.show(previewPane, reading);
        editorPreviewSplitPane.getItems().clear();
        if (source) {
            editorPreviewSplitPane.getItems().add(editorPane);
        }
        if (reading) {
            editorPreviewSplitPane.getItems().add(previewPane);
        }
        if (mode == ViewMode.SPLIT) {
            editorPreviewSplitPane.setDividerPositions(0.5);
        }
    }

    /**
     * Switches the properties panel between editable and read-only presentations.
     * Set with the view mode (preview-only = read-only).
     *
     * @param readOnly {@code true} for the "read" view
     */
    void setReadOnlyView(boolean readOnly) {
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

    // ── Note loading and saving ──────────────────────────────────────────────

    /** FXML lifecycle hook: start in the empty state (no note open). */
    @FXML
    private void initialize() {
        titleRow.wire(notePathLabel, editorHeaderBar, privateIndicator, privateIndicatorIcon, privateIndicatorTip);
        formatToolbar.wire(formatToolbarContainer, editorPane);
        setNoteOpen(false);
        setViewMode(viewMode.get());
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
            noteContentArea.setLinkHandlers((title, newTab) -> {
                if (wikiLinkHandler != null) {
                    wikiLinkHandler.openNoteByTitle(title, newTab);
                }
            }, SystemBrowser::open);
            noteContentArea.setImageSourceResolver(
                    source -> MarkdownPreview.resolveImageSource(source, preview.imageBaseDir(),
                            preview.imageContainmentRoot(), preview.imageLocator()));
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

    /** The format bar under the editor: shown or folded, with its arrow and notch. */
    private final FormatToolbarSupport formatToolbar = new FormatToolbarSupport();

    /**
     * What clicking the note's folder path in the title row does (the Explorer layout reveals
     * the note in its tree). {@code null} makes the path plain text, not a link.
     */
    public void setPathRevealAction(Runnable action) {
        titleRow.setPathRevealAction(action);
    }

    /** The format bar under the editor, shown or folded (Preferences, its arrow, the palette). */
    FormatToolbarSupport formatToolbar() {
        return formatToolbar;
    }

    /** Applies the persisted default editing mode without writing the preference again. */
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
    private void updateReadingModeControl(boolean editingOnly) {
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
        // A new tab keeps back and forward, as in Obsidian; the rest belongs to a note.
        UiNodes.show(editorHeaderBar, true);
        UiNodes.show(noteHeaderControls, open);
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



    @FXML private void handleBoardView(ActionEvent e) { kanban.toggle(currentNote); }
    @FXML private void handleEmptyCreate(ActionEvent e)       { publish(SystemActionEvent.ActionType.NEW_NOTE); }
    @FXML private void handleEmptyCreateCanvas(ActionEvent e) { publish(SystemActionEvent.ActionType.NEW_CANVAS); }
    @FXML private void handleEmptyGoToFile(ActionEvent e)     { publish(SystemActionEvent.ActionType.QUICK_SWITCHER); }
    @FXML private void handleEmptyOpenGraph(ActionEvent e)    { publish(SystemActionEvent.ActionType.GRAPH_VIEW); }
    @FXML private void handleEmptyOpenKanban(ActionEvent e)   { publish(SystemActionEvent.ActionType.KANBAN_VIEW); }

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
            titleRow.showLock(currentNote, viewingAttachment);
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
            titleRow.showLock(currentNote, viewingAttachment);
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
        UiNodes.show(boardViewBtn, kanban.show(currentNote));
        titleRow.showPath(currentNote);
        properties.rebuild();
        capturePersistedBaseline(currentNote);
        setModifiedState(false);
        titleRow.showLock(currentNote, viewingAttachment);
        syncFavoritePinButtons(key -> getString(key, key));
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

        Path path = (noteService != null)
                ? noteService.getNoteFilePath(note.getId()).orElse(null)
                : null;
        Node attachmentContent;
        if (path != null && type == AttachmentType.CANVAS) {
            if (canReuseCurrentCanvas(path)) {
                attachmentContent = currentCanvasView;
            } else {
                attachmentContent = buildCanvasView(path);
            }
        } else if (path != null) {
            attachmentContent = FileViewer.forAttachment(path, type, bundle);
        } else {
            Label missing = new Label(getString("viewer.file_not_found", "File not found"));
            missing.getStyleClass().add("viewer-info");
            attachmentContent = missing;
        }
        attachmentViewer.getChildren().setAll(attachmentContent);

        UiNodes.show(editorHeaderBar, true);
        UiNodes.show(noteHeaderControls, true);
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
        titleRow.showPath(note);
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
    private Node buildCanvasView(Path path) {
        try {
            String json = canvasContent(path);
            if (NoteService.LOCKED_PLACEHOLDER.equals(json)) {
                // A private canvas while locked: nothing to draw, and nothing to save over it.
                currentCanvasView = null;
                currentCanvasPath = null;
                Label locked = new Label(json);
                locked.getStyleClass().add("viewer-info");
                return locked;
            }
            CanvasModel.Document canvasDoc =
                    CanvasModel.Document.parse(json);
            CanvasReferences refs = new CanvasReferences(noteService,
                    CanvasReferences.vaultRootFor(path, currentNote != null ? currentNote.getId() : ""));
            final Supplier<List<String>> canvasReferenceSuggestions = memoizedCanvasReferenceSuggestions(refs);
            currentCanvasPath = path;
            currentCanvasView = new CanvasView(
                    canvasDoc,
                    file -> openNoteByTitle(refs.linkToOpen(file), false),
                    refs::resolve,
                    refs::canonical,
                    refs::refOfNote,
                    this::noteTitles,
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

    /**
     * The canvas's JSON. Read through the note service, which decrypts a private canvas (or
     * gives the lock placeholder while locked); straight from the file without one.
     */
    private String canvasContent(Path path) throws IOException {
        if (noteService != null && currentNote != null && currentNote.getId() != null) {
            Optional<String> content = noteService.getNoteById(currentNote.getId()).map(Note::getContent);
            if (content.isPresent()) {
                return content.get();
            }
        }
        return Files.readString(path);
    }

    private Supplier<List<String>> memoizedCanvasReferenceSuggestions(CanvasReferences refs) {
        AtomicReference<List<String>> cache = new AtomicReference<>();
        return () -> {
            List<String> cached = cache.get();
            if (cached != null) {
                return cached;
            }
            List<String> loaded = refs.suggestions(noteTitles());
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
        // Through setModifiedState, as a note's save: the window hears it and stops saying "Unsaved".
        setModifiedState(false);
        titleRow.showPath(currentNote);
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

    /** Where a board is drawn: the layer over the note's panes that attachments also use. */
    private StackPane boardLayer() {
        ensureAttachmentViewer();
        return attachmentViewer;
    }

    /** Shows the open board note as its board, or its panes (source and reading view). */
    private void showBoard(boolean board) {
        UiNodes.show(editorPreviewSplitPane, !board);
        UiNodes.show(noteOnlyControls, !board);
        UiNodes.show(boardLayer(), board);
        if (!board) {
            attachmentViewer.getChildren().clear();
        }
        boardViewIcon.setIconLiteral(board ? "fth-file-text" : "fth-columns");
        boardViewTooltip.setText(board ? getString("kanban.view_markdown", "View as Markdown")
                : getString("kanban.view_board", "View as board"));
    }

    private void hideAttachmentViewer() {
        kanban.clear();
        UiNodes.show(boardViewBtn, false);
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
            reportStatus(MessageFormat.format(safeI18n("status.save_conflict_copy"), created.getTitle()));
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
            titleRow.showPath(currentNote);
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
        if (noteContentArea != null && !Objects.equals(noteContentArea.getText(), currentNote.getContent())) {
            noteContentArea.replaceDocument(currentNote.getContent());
        }
        if (noteTitleField != null && !Objects.equals(noteTitleField.getText(), currentNote.getTitle())) {
            noteTitleField.setText(currentNote.getTitle());
        }
        properties.rebuild();
        capturePersistedBaseline(currentNote);
        setModifiedState(false);
        titleRow.showPath(currentNote);
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

    // ── Properties panel — internal ──────────────────────────────────────────

    /** Opens a note by title via the owning shell callback, in a new tab when {@code newTab}. */
    private void openNoteByTitle(String title, boolean newTab) {
        if (title == null || title.isBlank() || wikiLinkHandler == null) {
            return;
        }
        wikiLinkHandler.openNoteByTitle(title, newTab);
    }

    /** The names of the vault's notes, once each, for the canvas's note references. */
    private List<String> noteTitles() {
        List<LinkSuggestion> notes = autocompleteTitlesSupplier != null ? autocompleteTitlesSupplier.get() : null;
        return notes == null ? List.of() : notes.stream().map(LinkSuggestion::name)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))).stream().toList();
    }

    private void refreshAutocompleteTitles() {
        if (noteContentArea == null) return;
        List<LinkSuggestion> notes = autocompleteTitlesSupplier != null ? autocompleteTitlesSupplier.get() : List.of();
        noteContentArea.setLinkSuggestions(notes != null ? notes : List.of());
        noteContentArea.setLinkHeadingsProvider(link -> noteService != null
                ? noteService.headingsOf(link, currentNote != null ? currentNote.getId() : null) : List.of());
    }

    // ── EventBus subscriptions ───────────────────────────────────────────────

    private void subscribeToEvents() {
        if (eventBus == null) return;

        // The toolbar's Save and plugins reach the editor through the bus; the editor's own
        // buttons and the palette call it directly.
        subscriptions.add(eventBus.subscribe(SystemActionEvent.class, event -> Platform.runLater(() -> {
            if (event.getActionType() == SystemActionEvent.ActionType.SAVE) {
                handleSave();
            } else {
                applyFormat(event.getActionType());
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
        for (Class<? extends AppEvent> vaultEvent : List.of(
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

    // ── Editor commands (called from other controllers) ──────────────────────

    /**
     * Applies a formatting action (bold, a heading, a list, a link…) to the open note at the
     * cursor or selection. Other action types are ignored.
     *
     * @param type the formatting action
     */
    public void applyFormat(SystemActionEvent.ActionType type) {
        formatting.apply(type);
    }

    // ── Tag management & metadata (called from MainController) ───────────────

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
                Button rm = UiNodes.keepWhole(new Button("×")); rm.getStyleClass().add("tag-remove-btn");
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

    // ── Preview, word count, editor chrome ───────────────────────────────────

    @Override
    public void registerEditorBlockRenderer(String pluginId, String language,
            EditorBlockRenderer renderer) {
        blockRenderSupport.registerRenderer(pluginId, language, renderer);
        Platform.runLater(() -> refreshEditorBlockRenders(true));
    }

    @Override
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

    @Override
    public void registerPreviewEnhancer(String pluginId, PreviewEnhancer enhancer) {
        preview.registerEnhancer(pluginId, enhancer);
    }

    @Override
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
        boolean hasNote = currentNote != null;
        syncToggle(favoriteButton, hasNote, hasNote && currentNote.isFavorite(), i18n,
                "tooltip.add_favorite", "action.add_favorite", "action.remove_favorite");
        syncToggle(pinButton, hasNote, hasNote && currentNote.isPinned(), i18n,
                "tooltip.pin_note", "tooltip.pin_note", "action.unpin_note");
    }

    /**
     * A note's on/off button: off with no note, on as the note is, and its tooltip saying what
     * a click does: {@code noNote}, {@code turnOn} or {@code turnOff} (keys of {@code i18n}).
     */
    private static void syncToggle(ToggleButton button, boolean hasNote, boolean on, Function<String, String> i18n,
            String noNote, String turnOn, String turnOff) {
        if (button == null) {
            return;
        }
        button.setDisable(!hasNote);
        button.setSelected(on);
        if (button.getTooltip() != null && i18n != null) {
            button.getTooltip().setText(i18n.apply(!hasNote ? noNote : on ? turnOff : turnOn));
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
        preview.setDarkTheme(darkTheme);
        kanban.applyTheme(darkTheme);
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

    /** Opens the tags bar if it is closed and closes it if it is open (the palette's "Toggle tags"). */
    public void toggleTagsBar() {
        if (tagsContainer != null) {
            showTagsBar(!tagsContainer.isVisible());
        }
    }

    private void showTagsBar(boolean show) {
        if (toggleTagsBtn != null) {
            toggleTagsBtn.setSelected(show);
        }
        if (tagsContainer != null) {
            UiNodes.show(tagsContainer, show);
            statusAction.accept(show ? getString("status.tags_bar_shown", "Tags bar shown")
                    : getString("status.tags_bar_hidden", "Tags bar hidden"));
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
        formatting.cut(noteTitleField);
    }

    public void performCopy() {
        formatting.copy(noteTitleField);
    }

    public void performPaste() {
        formatting.paste(noteTitleField);
    }

    public void performFind(Function<String, String> i18n, Consumer<String> status) {
        formatting.openSearch(false);
    }

    public void performReplace(Function<String, String> i18n, Consumer<String> status) {
        formatting.openSearch(true);
    }

    /** Inserts the template {@code write} writes for the selection, its properties in the note's: "Insert template". */
    public EditorTemplateSupport.Outcome insertTemplate(Function<String, EditorTemplateSupport.Written> write) {
        return templates.insertTemplate(write);
    }

    /** Writes {@code text} at the cursor of the note open for editing; {@code false} with none. */
    public boolean insertText(String text) {
        return templates.insertText(text);
    }

    /** The note open for editing, with what its properties panel shows, or {@code null}. */
    private Note noteOpenForEditing() {
        if (currentNote == null || readOnlyView || viewingAttachment || currentAttachmentType == AttachmentType.CANVAS) {
            return null;
        }
        currentNote.setCustomProperties(properties.collect());
        return currentNote;
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
            return new PropertiesPanelSupport.Nodes(togglePropertiesBtn, propertiesDot, propertiesSection,
                    propertiesContent, propertiesEmptyLabel, addPropertyRow);
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
        public void openLink(String link, boolean newTab) {
            openNoteByTitle(link, newTab);
        }

        @Override
        public String text(String key, String fallback) {
            return getString(key, fallback);
        }
    }

    /** What the reading view reads from this editor. */
    private final class PreviewHost implements EditorPreviewSupport.Host {
        @Override
        public HtmlView previewWebView() {
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
        public void openLink(String link, boolean newTab) {
            if (wikiLinkHandler != null) {
                wikiLinkHandler.openNoteByTitle(link, newTab);
            }
        }

        @Override
        public void openTag(String tag) {
            if (tagLinkHandler != null) {
                tagLinkHandler.accept(tag);
            }
        }
    }

    // ── Private utilities ────────────────────────────────────────────────────

    private void publishCreated(Note note) {
        if (eventBus != null && note != null) eventBus.publish(new NoteEvents.NoteCreatedEvent(note));
    }

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
        propertiesInserted = false;
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
        boolean dirtyProperties = propertiesInserted || !currentPropertySnapshot().equals(persistedCustomProperties);
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
