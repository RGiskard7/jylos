package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.config.AppContext;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.NoteProperty;
import com.example.jylos.data.models.Tag;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.service.NoteService;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.MarkdownPreview;
import com.example.jylos.util.WikiLinkResolver;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;

import java.util.ResourceBundle;

/**
 * FXML controller for the note editor pane.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Loads/saves notes including YAML custom properties.</li>
 *   <li>Collapsible properties panel — header always visible; clicking expands/collapses.</li>
 *   <li>Wiki-link autocomplete popup when the user types {@code [[}.</li>
 *   <li>Property values containing {@code [[wiki-links]]} show a clickable link button.</li>
 *   <li>Back/forward navigation arrow buttons for traversing note history.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.3.0
 */
public class EditorController {

    private static final Logger logger = LoggerConfig.getLogger(EditorController.class);

    /** Matches an open [[query before the text-area caret. */
    private static final Pattern WIKI_TRIGGER = Pattern.compile("\\[\\[([^\\]\\[\\n]*)$");

    /** Matches a single wiki-link as the entire property value (full or partial). */
    private static final Pattern WIKI_VALUE = Pattern.compile(
            "\\[\\[([^\\[\\]|#\\n]+?)(?:#[^\\[\\]|\\n]+?)?(?:\\|[^\\[\\]\\n]+?)?\\]\\]");

    // ── Service / state ─────────────────────────────────────────────────────
    private EventBus eventBus;
    private NoteService noteService;
    private NoteDAO noteDAO;
    private ResourceBundle bundle;

    private Note currentNote;
    private boolean isModified = false;

    /** True when the editor is in "read" (preview-only) view: properties become read-only. */
    private boolean readOnlyView = false;
    /** Whether the properties panel body is expanded. Collapsed by default. */
    private boolean propertiesExpanded = false;

    /** Cached note titles for wiki-link autocomplete — refreshed on note events. */
    private List<String> cachedNoteTitles = new ArrayList<>();

    private final Map<String, PreviewEnhancer> previewEnhancers = new HashMap<>();
    private boolean wikiLinkListenerInstalled;
    private WikiLinkHandler wikiLinkHandler;
    private final PreviewJavaBridge previewJavaBridge = new PreviewJavaBridge();

    /** Opens a note when the user clicks a wiki-link in the Markdown preview. */
    @FunctionalInterface
    public interface WikiLinkHandler {
        void openNoteByTitle(String title);
    }

    // ── FXML — header ───────────────────────────────────────────────────────
    @FXML private VBox editorContainer;
    @FXML private HBox editorTabBar;
    @FXML private HBox editorPathBar;
    @FXML private Label notePathLabel;
    @FXML private Button closeNoteBtn;
    @FXML private HBox editorHeaderBar;
    @FXML private StackPane editorContentStack;
    @FXML private VBox emptyState;
    @FXML private Button navBackBtn;
    @FXML private Button navForwardBtn;
    @FXML private TextField noteTitleField;
    @FXML private Label noteTitleLabel;
    @FXML private Label dirtySaveIndicator;
    @FXML private Tooltip dirtySaveIndicatorTip;
    @FXML private ToggleButton toggleTagsBtn;
    @FXML private ToggleButton editorOnlyButton;
    @FXML private ToggleButton splitViewButton;
    @FXML private ToggleButton previewOnlyButton;
    @FXML private ToggleButton pinButton;
    @FXML private ToggleButton favoriteButton;
    @FXML private ToggleButton infoButton;

    // ── FXML — tags bar ─────────────────────────────────────────────────────
    @FXML private VBox tagsContainer;
    @FXML private FlowPane tagsFlowPane;
    @FXML private Label modifiedDateLabel;

    // ── FXML — properties panel ─────────────────────────────────────────────
    @FXML private VBox  propertiesSection;   // outer, shown when note has custom props
    @FXML private HBox  propertiesHeader;    // always-visible header row
    @FXML private Label propertiesCollapseIcon;
    @FXML private VBox  propertiesContent;   // rows, toggleable via header click
    @FXML private Button addPropertyBtn;

    // ── FXML — editor / preview ─────────────────────────────────────────────
    @FXML private SplitPane editorPreviewSplitPane;
    @FXML private VBox      editorPane;
    @FXML private TextArea  noteContentArea;
    @FXML private Button    heading1Btn, heading2Btn, heading3Btn;
    @FXML private Button    boldBtn, italicBtn, strikeBtn, underlineBtn;
    @FXML private Button    highlightBtn, linkBtn, imageBtn;
    @FXML private Button    todoBtn, bulletBtn, numberBtn;
    @FXML private Button    quoteBtn, codeBtn;
    @FXML private Label     wordCountLabel;
    @FXML private VBox      previewPane;
    @FXML private javafx.scene.web.WebView previewWebView;

    // ── Wiki-link autocomplete ───────────────────────────────────────────────
    private Popup autocompletePopup;
    private ListView<String> autocompleteList;

    // ============================================================
    // Setters
    // ============================================================

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus != null ? eventBus
                : (AppContext.isInitialized() ? AppContext.getEventBus() : null);
        subscribeToEvents();
    }

    public void setNoteDAO(NoteDAO noteDAO) {
        this.noteDAO = noteDAO;
    }

    public void setServices(NoteService noteService) {
        this.noteService = noteService != null ? noteService
                : (AppContext.isInitialized() ? AppContext.getNoteService() : null);
    }

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle != null ? bundle : AppContext.getBundle();
    }

    // ============================================================
    // View-mode button presentation
    // ============================================================

    /**
     * Renders the editor view-mode buttons (Edit / Split / Read) as icon-only
     * with their FXML tooltips. Called once the buttons are available.
     */
    public void applyViewModeButtonsPresentation() {
        applyIconOnly(editorOnlyButton);
        applyIconOnly(splitViewButton);
        applyIconOnly(previewOnlyButton);
    }

    private void applyIconOnly(ToggleButton btn) {
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
        isModified = false;
        updateSaveIndicator(false);
    }

    // FXML node getters (used by MainController for layout delegation)
    public VBox            getEditorContainer()        { return editorContainer; }
    public HBox            getEditorTabBar()           { return editorTabBar; }
    public TextField       getNoteTitleField()         { return noteTitleField; }
    public ToggleButton    getToggleTagsBtn()           { return toggleTagsBtn; }
    public ToggleButton    getEditorOnlyButton()        { return editorOnlyButton; }
    public ToggleButton    getSplitViewButton()         { return splitViewButton; }
    public ToggleButton    getPreviewOnlyButton()       { return previewOnlyButton; }
    public ToggleButton    getPinButton()               { return pinButton; }
    public ToggleButton    getFavoriteButton()          { return favoriteButton; }
    public ToggleButton    getInfoButton()              { return infoButton; }
    public VBox            getTagsContainer()           { return tagsContainer; }
    public FlowPane        getTagsFlowPane()            { return tagsFlowPane; }
    public Label           getModifiedDateLabel()       { return modifiedDateLabel; }
    public SplitPane       getEditorPreviewSplitPane()  { return editorPreviewSplitPane; }
    public VBox            getEditorPane()              { return editorPane; }
    public TextArea        getNoteContentArea()         { return noteContentArea; }
    public Label           getWordCountLabel()          { return wordCountLabel; }
    public VBox            getPreviewPane()             { return previewPane; }
    public javafx.scene.web.WebView getPreviewWebView() { return previewWebView; }

    // ============================================================
    // FXML action handlers
    // ============================================================

    @FXML private void handleToggleTags(ActionEvent e)        { publish(SystemActionEvent.ActionType.TOGGLE_TAGS); }
    @FXML private void handleEditorOnlyMode(ActionEvent e)    { publish(SystemActionEvent.ActionType.EDITOR_ONLY_MODE); }
    @FXML private void handleSplitViewMode(ActionEvent e)     { publish(SystemActionEvent.ActionType.SPLIT_VIEW_MODE); }
    @FXML private void handlePreviewOnlyMode(ActionEvent e)   { publish(SystemActionEvent.ActionType.PREVIEW_ONLY_MODE); }
    @FXML private void handleTogglePin(ActionEvent e)         { publish(SystemActionEvent.ActionType.TOGGLE_PIN); }
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
        propertiesExpanded = !propertiesExpanded;
        applyPropertiesExpandedState();
    }

    private void applyPropertiesExpandedState() {
        if (propertiesContent != null) {
            propertiesContent.setVisible(propertiesExpanded);
            propertiesContent.setManaged(propertiesExpanded);
        }
        if (propertiesCollapseIcon != null) {
            propertiesCollapseIcon.setText(propertiesExpanded ? "▼" : "▶");
        }
    }

    /** Opens a dialog to add a new YAML property (edit/split view only). */
    @FXML
    private void handleAddProperty(ActionEvent event) {
        if (currentNote == null || readOnlyView) return;

        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle(getString("dialog.add_property.title", "Add Property"));
        dialog.setHeaderText(getString("dialog.add_property.header", "Enter property name and value:"));
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType(getString("action.add", "Add"), ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField keyField   = new TextField();
        keyField.setPromptText(getString("dialog.add_property.key_prompt", "e.g. date, priority"));
        TextField valueField = new TextField();
        valueField.setPromptText(getString("dialog.add_property.value_prompt", "e.g. 2026-05-31, 1"));

        grid.add(new Label(getString("label.property_key",   "Key:")),   0, 0);
        grid.add(keyField,   1, 0);
        grid.add(new Label(getString("label.property_value", "Value:")), 0, 1);
        grid.add(valueField, 1, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn ->
                btn.getButtonData() == ButtonBar.ButtonData.OK_DONE
                        ? new String[]{keyField.getText().trim(), valueField.getText().trim()}
                        : null);

        Platform.runLater(keyField::requestFocus);
        com.example.jylos.ui.UiDialogs.show(dialog).ifPresent(pair -> {
            String key = pair[0];
            String val = pair[1];
            if (!key.isBlank()) {
                // Persist into the model immediately, then rebuild so the new row
                // is rendered with the correct (editable) presentation.
                currentNote.getCustomProperties().put(key, val);
                propertiesExpanded = true;
                rebuildPropertiesPanel();
                isModified = true;
                publishModified();
            }
        });
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
            currentNote.setCustomProperties(collectPropertiesFromPanel());
        }
        this.readOnlyView = readOnly;
        // In the read (preview-only) view, swap the editable title input for a plain
        // heading Label so the title reads as a title, not an input field.
        if (noteTitleField != null && noteTitleLabel != null) {
            setNodeVisible(noteTitleField, !readOnly);
            setNodeVisible(noteTitleLabel, readOnly);
        }
        rebuildPropertiesPanel();
    }

    // ============================================================
    // Note loading and saving
    // ============================================================

    /** FXML lifecycle hook: start in the empty state (no note open). */
    @FXML
    private void initialize() {
        setNoteOpen(false);
        // The read-view heading always mirrors the editable title field.
        if (noteTitleLabel != null && noteTitleField != null) {
            noteTitleLabel.textProperty().bind(noteTitleField.textProperty());
        }
    }

    /**
     * Toggles between the editing UI and the Obsidian-style empty state.
     * The empty state covers the editor when no note is open (and at startup).
     */
    private void setNoteOpen(boolean open) {
        hideAttachmentViewer();
        setNodeVisible(editorPathBar, open);
        setNodeVisible(editorHeaderBar, open);
        setNodeVisible(editorPreviewSplitPane, open);
        if (!open) {
            setNodeVisible(tagsContainer, false);
            setNodeVisible(propertiesSection, false);
        }
        setNodeVisible(emptyState, !open);
    }

    /** Builds the {@code folder/sub/note.md} breadcrumb for the path bar. */
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
        notePathLabel.setText(path);
    }

    @FXML
    private void handleCloseNote(ActionEvent e) {
        publish(SystemActionEvent.ActionType.CLOSE_NOTE);
    }

    @FXML
    private void handleEmptyCreate(ActionEvent e) {
        publish(SystemActionEvent.ActionType.NEW_NOTE);
    }

    @FXML
    private void handleEmptyGoToFile(ActionEvent e) {
        publish(SystemActionEvent.ActionType.QUICK_SWITCHER);
    }

    public void loadNote(Note note) {
        hideAutocompletePopup();

        if (note == null) {
            currentNote = null;
            if (noteTitleField  != null) noteTitleField.clear();
            if (noteContentArea != null) noteContentArea.clear();
            clearPropertiesPanel();
            setNoteOpen(false);
            isModified = false;
            updateSaveIndicator(false);
            return;
        }

        if (isModified && currentNote != null) handleSave();

        currentNote = (noteService != null)
                ? noteService.getNoteById(note.getId()).orElse(note)
                : note;

        // PDFs and images are not editable: show a native viewer instead of the editor.
        AttachmentType type = AttachmentType.fromName(currentNote.getId());
        if (type.isAttachment()) {
            showAttachment(currentNote, type);
            isModified = false;
            updateSaveIndicator(false);
            return;
        }
        hideAttachmentViewer();

        if (noteTitleField  != null) noteTitleField.setText(orEmpty(currentNote.getTitle()));
        if (noteContentArea != null) noteContentArea.setText(orEmpty(currentNote.getContent()));

        setNoteOpen(true);
        updateBreadcrumb(currentNote);
        rebuildPropertiesPanel();
        refreshNoteTitlesCache();
        isModified = false;
        updateSaveIndicator(false);
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
            setNodeVisible(attachmentViewer, false);
            if (editorContentStack != null) {
                editorContentStack.getChildren().add(attachmentViewer);
            }
        }
    }

    /** Shows {@code note} (a PDF/image) in a native viewer, hiding the editor chrome. */
    private void showAttachment(Note note, AttachmentType type) {
        viewingAttachment = true;
        ensureAttachmentViewer();
        attachmentViewer.getChildren().clear();

        java.nio.file.Path path = (noteService != null)
                ? noteService.getNoteFilePath(note.getId()).orElse(null)
                : null;
        if (path != null) {
            attachmentViewer.getChildren().add(
                    com.example.jylos.ui.components.FileViewer.forAttachment(path, type, bundle));
        } else {
            Label missing = new Label(bundle != null ? bundle.getString("viewer.file_not_found")
                    : "File not found");
            missing.getStyleClass().add("viewer-info");
            attachmentViewer.getChildren().add(missing);
        }

        setNodeVisible(editorPathBar, true);
        setNodeVisible(editorHeaderBar, false);
        setNodeVisible(editorPreviewSplitPane, false);
        setNodeVisible(tagsContainer, false);
        setNodeVisible(propertiesSection, false);
        setNodeVisible(emptyState, false);
        setNodeVisible(attachmentViewer, true);
        updateBreadcrumb(note);
    }

    private void hideAttachmentViewer() {
        viewingAttachment = false;
        if (attachmentViewer != null) {
            attachmentViewer.getChildren().clear(); // release rendered images
            setNodeVisible(attachmentViewer, false);
        }
    }

    public void handleSave() {
        if (currentNote == null || !isModified || noteService == null) return;
        if (noteTitleField  != null) currentNote.setTitle(noteTitleField.getText());
        if (noteContentArea != null) currentNote.setContent(noteContentArea.getText());
        // Property values are editable only in edit/split view; in read view the
        // model already holds the authoritative values, so we don't collect.
        if (!readOnlyView) {
            currentNote.setCustomProperties(collectPropertiesFromPanel());
        }
        noteService.updateNote(currentNote);
        isModified = false;
        updateSaveIndicator(false);
        if (eventBus != null) eventBus.publish(new NoteEvents.NoteSavedEvent(currentNote));
    }

    // ============================================================
    // Properties panel — internal
    // ============================================================

    /**
     * Rebuilds the whole properties panel for the current note and view mode.
     *
     * <ul>
     *   <li><b>Edit / split view</b>: the section is always shown (even with no
     *       properties) so the user can add some; values are editable; the
     *       "add" button is visible.</li>
     *   <li><b>Read view</b>: the section is shown only when the note actually
     *       has properties; values are read-only and any {@code [[wiki-links]]}
     *       render as clickable internal links; the "add" button is hidden.</li>
     * </ul>
     */
    private void rebuildPropertiesPanel() {
        clearPropertiesPanel();

        Map<String, String> props = currentNote != null ? currentNote.getCustomProperties() : null;
        boolean hasProps = props != null && !props.isEmpty();
        boolean sectionVisible = currentNote != null && (readOnlyView ? hasProps : true);

        setNodeVisible(propertiesSection, sectionVisible);
        if (addPropertyBtn != null) {
            addPropertyBtn.setVisible(!readOnlyView);
            addPropertyBtn.setManaged(!readOnlyView);
        }
        if (!sectionVisible) {
            return;
        }

        applyPropertiesExpandedState();

        if (hasProps) {
            props.forEach((key, value) -> {
                if (readOnlyView) {
                    addReadOnlyPropertyRow(key, value);
                } else {
                    addEditablePropertyRow(key, value);
                }
            });
        }
    }

    /** Editable row: {@code [key] [TextField | CheckBox] [× remove]} — no type icon. */
    private void addEditablePropertyRow(String key, String value) {
        if (propertiesContent == null) return;

        NoteProperty prop = NoteProperty.of(key, value != null ? value : "");

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("property-row");
        row.setUserData(key);

        Label keyLabel = new Label(key + ":");
        keyLabel.getStyleClass().add("property-key");
        keyLabel.setMinWidth(90);
        keyLabel.setMaxWidth(120);

        Control valueControl;
        if (prop.type() == NoteProperty.PropertyType.BOOLEAN) {
            CheckBox cb = new CheckBox();
            cb.setSelected("true".equalsIgnoreCase(value));
            cb.getStyleClass().add("property-value-check");
            cb.setOnAction(e -> { isModified = true; publishModified(); });
            valueControl = cb;
        } else {
            TextField tf = new TextField(value != null ? value : "");
            tf.getStyleClass().add("property-value");
            HBox.setHgrow(tf, Priority.ALWAYS);
            tf.textProperty().addListener((obs, o, n) -> { isModified = true; publishModified(); });
            valueControl = tf;
        }

        Button removeBtn = new Button("×");
        removeBtn.getStyleClass().add("property-remove-btn");
        removeBtn.setTooltip(new Tooltip(getString("tooltip.remove_property", "Remove property")));
        removeBtn.setOnAction(e -> {
            propertiesContent.getChildren().remove(row);
            if (currentNote != null) currentNote.getCustomProperties().remove(key);
            isModified = true;
            publishModified();
        });

        row.getChildren().addAll(keyLabel, valueControl, removeBtn);
        propertiesContent.getChildren().add(row);
    }

    /** Read-only row: {@code [key] [value]} where wiki-links are clickable. */
    private void addReadOnlyPropertyRow(String key, String value) {
        if (propertiesContent == null) return;

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("property-row");
        row.setUserData(key);

        Label keyLabel = new Label(key + ":");
        keyLabel.getStyleClass().add("property-key");
        keyLabel.setMinWidth(90);
        keyLabel.setMaxWidth(120);

        Node valueNode = buildReadOnlyValueNode(value != null ? value : "");
        HBox.setHgrow(valueNode, Priority.ALWAYS);

        row.getChildren().addAll(keyLabel, valueNode);
        propertiesContent.getChildren().add(row);
    }

    /**
     * Builds the read-only value node: a plain {@link Label} when the value has
     * no internal links, or a {@link TextFlow} mixing plain text and clickable
     * {@link Hyperlink}s for each {@code [[wiki-link]]}.
     */
    private Node buildReadOnlyValueNode(String value) {
        Matcher m = WIKI_VALUE.matcher(value);
        if (!m.find()) {
            Label label = new Label(value);
            label.getStyleClass().add("property-readonly-value");
            label.setWrapText(true);
            return label;
        }

        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("property-readonly-value");
        int last = 0;
        m.reset();
        while (m.find()) {
            if (m.start() > last) {
                flow.getChildren().add(new Text(value.substring(last, m.start())));
            }
            String title = WikiLinkResolver.extractTitle(m.group(1).trim());
            Hyperlink link = new Hyperlink(title);
            link.getStyleClass().add("property-wikilink");
            link.setOnAction(e -> openNoteByTitle(title));
            flow.getChildren().add(link);
            last = m.end();
        }
        if (last < value.length()) {
            flow.getChildren().add(new Text(value.substring(last)));
        }
        return flow;
    }

    private Map<String, String> collectPropertiesFromPanel() {
        Map<String, String> result = new LinkedHashMap<>();
        if (propertiesContent == null) return result;
        for (Node rowNode : propertiesContent.getChildren()) {
            if (!(rowNode instanceof HBox row)) continue;
            String key = (String) row.getUserData();
            if (key == null) continue;
            for (Node child : row.getChildren()) {
                if (child instanceof TextField tf) { result.put(key, tf.getText()); break; }
                if (child instanceof CheckBox cb)  { result.put(key, String.valueOf(cb.isSelected())); break; }
            }
        }
        return result;
    }

    private void clearPropertiesPanel() {
        if (propertiesContent != null) propertiesContent.getChildren().clear();
    }

    /** Opens a note by title via the EventBus (mirrors the wiki-link click flow). */
    private void openNoteByTitle(String title) {
        if (title == null || title.isBlank() || eventBus == null) return;
        Note lookup = new Note(title, "");
        eventBus.publish(new NoteEvents.NoteOpenRequestEvent(lookup));
    }

    // ============================================================
    // Wiki-link autocomplete
    // ============================================================

    private void ensureAutocompleteInitialized() {
        if (autocompletePopup != null || noteContentArea == null) return;

        autocompleteList = new ListView<>();
        autocompleteList.setPrefWidth(320);
        autocompleteList.setPrefHeight(180);
        autocompleteList.getStyleClass().add("autocomplete-list");

        autocompletePopup = new Popup();
        autocompletePopup.setAutoHide(true);
        autocompletePopup.setConsumeAutoHidingEvents(false);
        autocompletePopup.getContent().add(autocompleteList);

        autocompleteList.setOnMouseClicked(e -> {
            String sel = autocompleteList.getSelectionModel().getSelectedItem();
            if (sel != null) completeWikiLink(sel);
        });

        noteContentArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!isAutocompleteVisible()) return;
            switch (event.getCode()) {
                case DOWN -> {
                    autocompleteList.getSelectionModel().selectNext();
                    autocompleteList.scrollTo(autocompleteList.getSelectionModel().getSelectedIndex());
                    event.consume();
                }
                case UP -> {
                    autocompleteList.getSelectionModel().selectPrevious();
                    autocompleteList.scrollTo(autocompleteList.getSelectionModel().getSelectedIndex());
                    event.consume();
                }
                case ENTER, TAB -> {
                    String sel = autocompleteList.getSelectionModel().getSelectedItem();
                    if (sel != null) { completeWikiLink(sel); event.consume(); }
                }
                case ESCAPE -> { hideAutocompletePopup(); event.consume(); }
                default -> { /* pass through */ }
            }
        });

        noteContentArea.textProperty().addListener((obs, oldText, newText) ->
                checkAndShowAutocomplete(newText));
    }

    private void checkAndShowAutocomplete(String fullText) {
        if (noteContentArea == null || fullText == null) return;
        int caret = noteContentArea.getCaretPosition();
        if (caret <= 0) { hideAutocompletePopup(); return; }

        Matcher m = WIKI_TRIGGER.matcher(fullText.substring(0, Math.min(caret, fullText.length())));
        if (!m.find()) { hideAutocompletePopup(); return; }

        List<String> matches = filterTitles(m.group(1));
        if (matches.isEmpty()) { hideAutocompletePopup(); return; }

        autocompleteList.getItems().setAll(matches);
        autocompleteList.getSelectionModel().selectFirst();

        if (!isAutocompleteVisible()) showAutocompletePopupNearCaret();
    }

    private List<String> filterTitles(String query) {
        if (cachedNoteTitles.isEmpty()) refreshNoteTitlesCache();
        String q = query.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String t : cachedNoteTitles) {
            if (q.isBlank() || t.toLowerCase().contains(q)) result.add(t);
            if (result.size() >= 20) break;
        }
        return result;
    }

    private void completeWikiLink(String title) {
        if (noteContentArea == null || title == null) return;
        String text = noteContentArea.getText();
        int caret = noteContentArea.getCaretPosition();
        Matcher m = WIKI_TRIGGER.matcher(text.substring(0, Math.min(caret, text.length())));
        if (!m.find()) return;

        int linkStart = caret - m.group(0).length();
        String completed = "[[" + title + "]]";
        noteContentArea.setText(text.substring(0, linkStart) + completed + text.substring(caret));
        noteContentArea.positionCaret(linkStart + completed.length());
        noteContentArea.requestFocus();
        hideAutocompletePopup();
        isModified = true;
    }

    private void showAutocompletePopupNearCaret() {
        if (noteContentArea == null || noteContentArea.getScene() == null) return;
        Platform.runLater(() -> {
            if (autocompletePopup == null || noteContentArea.getScene() == null) return;
            Node caretNode = noteContentArea.lookup(".caret");
            if (caretNode != null) {
                javafx.geometry.Bounds b = caretNode.localToScreen(caretNode.getBoundsInLocal());
                if (b != null && b.getMinX() > 0) {
                    autocompletePopup.show(noteContentArea.getScene().getWindow(),
                            b.getMinX(), b.getMaxY() + 4);
                    return;
                }
            }
            javafx.geometry.Bounds ab = noteContentArea.localToScreen(noteContentArea.getBoundsInLocal());
            if (ab != null) autocompletePopup.show(noteContentArea.getScene().getWindow(),
                    ab.getMinX() + 30, ab.getMinY() + 30);
        });
    }

    private boolean isAutocompleteVisible() {
        return autocompletePopup != null && autocompletePopup.isShowing();
    }

    private void hideAutocompletePopup() {
        if (isAutocompleteVisible()) autocompletePopup.hide();
    }

    private void refreshNoteTitlesCache() {
        if (noteService == null) return;
        try {
            cachedNoteTitles = new ArrayList<>(
                    noteService.getAllNotes().stream()
                            .map(Note::getTitle)
                            .filter(t -> t != null && !t.isBlank())
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList());
        } catch (Exception e) {
            logger.warning("Failed to refresh note titles cache: " + e.getMessage());
        }
    }

    // ============================================================
    // EventBus subscriptions
    // ============================================================

    private void subscribeToEvents() {
        if (eventBus == null) return;

        eventBus.subscribe(SystemActionEvent.class, event -> Platform.runLater(() -> {
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
                case IMAGE     -> handleImageDialog();
                case SAVE      -> handleSave();
                default        -> { /* not handled here */ }
            }
        }));

        if (noteContentArea != null) {
            noteContentArea.textProperty().addListener((obs, oldVal, newVal) -> {
                if (currentNote != null && !newVal.equals(orEmpty(currentNote.getContent()))) {
                    isModified = true;
                    publishModified();
                }
                ensureAutocompleteInitialized();
            });
        }

        if (noteTitleField != null) {
            noteTitleField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (currentNote != null && !newVal.equals(orEmpty(currentNote.getTitle()))) {
                    isModified = true;
                    publishModified();
                }
            });
        }

        eventBus.subscribe(NoteEvents.NoteSavedEvent.class,   e -> refreshNoteTitlesCache());
        eventBus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> refreshNoteTitlesCache());
        eventBus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> refreshNoteTitlesCache());
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
            int pos = noteContentArea.getCaretPosition();
            String t = orEmpty(noteContentArea.getText());
            noteContentArea.setText(t.substring(0, pos) + prefix + suffix + t.substring(pos));
            noteContentArea.positionCaret(pos + prefix.length());
        }
        noteContentArea.requestFocus();
        isModified = true;
    }

    private void insertLinePrefix(String prefix) {
        if (noteContentArea == null) return;
        int pos = noteContentArea.getCaretPosition();
        String t = orEmpty(noteContentArea.getText());
        int lineStart = t.lastIndexOf('\n', pos - 1) + 1;
        if (t.substring(lineStart, pos).trim().isEmpty() && lineStart == pos) {
            noteContentArea.setText(t.substring(0, pos) + prefix + t.substring(pos));
            noteContentArea.positionCaret(pos + prefix.length());
        } else {
            noteContentArea.setText(t.substring(0, pos) + "\n" + prefix + t.substring(pos));
            noteContentArea.positionCaret(pos + prefix.length() + 1);
        }
        noteContentArea.requestFocus();
        isModified = true;
    }

    private void insertTodoList() {
        if (noteContentArea == null) return;
        int pos = noteContentArea.getCaretPosition();
        String t = orEmpty(noteContentArea.getText());
        String item = "- [ ] ";
        int lineStart = t.lastIndexOf('\n', pos - 1) + 1;
        if (t.substring(lineStart, pos).trim().isEmpty()) {
            noteContentArea.setText(t.substring(0, pos) + item + t.substring(pos));
            noteContentArea.positionCaret(pos + item.length());
        } else {
            noteContentArea.setText(t.substring(0, pos) + "\n" + item + t.substring(pos));
            noteContentArea.positionCaret(pos + item.length() + 1);
        }
        noteContentArea.requestFocus();
        isModified = true;
    }

    private void insertCodeBlock() {
        if (noteContentArea == null) return;
        String sel = noteContentArea.getSelectedText();
        if (sel != null && sel.contains("\n")) insertMarkdownFormat("```\n", "\n```");
        else insertMarkdownFormat("`", "`");
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
            String link = "[" + label + "](" + url.trim() + ")";
            if (sel != null && !sel.isEmpty()) noteContentArea.replaceSelection(link);
            else {
                int pos = noteContentArea.getCaretPosition();
                String t = orEmpty(noteContentArea.getText());
                noteContentArea.setText(t.substring(0, pos) + link + t.substring(pos));
                noteContentArea.positionCaret(pos + link.length());
            }
            noteContentArea.requestFocus();
            isModified = true;
        });
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
            String img = "![" + alt + "](" + path.trim() + ")";
            if (sel != null && !sel.isEmpty()) noteContentArea.replaceSelection(img);
            else {
                int pos = noteContentArea.getCaretPosition();
                String t = orEmpty(noteContentArea.getText());
                noteContentArea.setText(t.substring(0, pos) + img + t.substring(pos));
                noteContentArea.positionCaret(pos + img.length());
            }
            noteContentArea.requestFocus();
            isModified = true;
        });
    }

    // ============================================================
    // Editor commands (called from other controllers)
    // ============================================================

    public void handleUndo(TextArea ta) {
        if (ta != null) ta.undo();
    }

    public void handleRedo(Function<String, String> i18n, Consumer<String> status) {
        if (i18n != null && status != null) status.accept(i18n.apply("status.redo_not_available"));
    }

    public void handleCut(TextArea ta, TextField title) {
        if (ta    != null && ta.getSelectedText()    != null) ta.cut();
        else if (title != null && title.getSelectedText() != null) title.cut();
    }

    public void handleCopy(TextArea ta, TextField title) {
        if (ta    != null && ta.getSelectedText()    != null) ta.copy();
        else if (title != null && title.getSelectedText() != null) title.copy();
    }

    public void handlePaste(TextArea ta, TextField title) {
        if (ta    != null && ta.isFocused())    ta.paste();
        else if (title != null && title.isFocused()) title.paste();
    }

    public void handleFind(TextArea ta, Function<String, String> i18n, Consumer<String> status) {
        if (ta == null) return;
        TextInputDialog d = new TextInputDialog();
        d.setTitle(i18n.apply("dialog.find.title"));
        d.setHeaderText(i18n.apply("dialog.find.header"));
        d.setContentText(i18n.apply("dialog.find.content"));
        com.example.jylos.ui.UiDialogs.show(d).filter(s -> !s.trim().isEmpty()).ifPresent(query -> {
            int idx = ta.getText().indexOf(query);
            if (idx >= 0) {
                ta.selectRange(idx, idx + query.length());
                ta.requestFocus();
                status.accept(MessageFormat.format(i18n.apply("status.found_text"), query));
            } else {
                status.accept(MessageFormat.format(i18n.apply("status.text_not_found"), query));
            }
        });
    }

    public void handleReplace(TextArea ta, Function<String, String> i18n, Consumer<String> status) {
        if (ta == null) { if (status != null) status.accept(i18n.apply("status.no_note_open")); return; }
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(i18n.apply("dialog.replace.title"));
        dialog.setHeaderText(i18n.apply("dialog.replace.header"));
        ButtonType replBtn    = new ButtonType(i18n.apply("action.replace_one"),  ButtonBar.ButtonData.OK_DONE);
        ButtonType replAllBtn = new ButtonType(i18n.apply("action.replace_all"),  ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(replBtn, replAllBtn, ButtonType.CANCEL);
        VBox content = new VBox(10); content.setPadding(new Insets(20));
        TextField findF = new TextField(); findF.setPromptText(i18n.apply("dialog.replace.find_prompt"));
        TextField replF = new TextField(); replF.setPromptText(i18n.apply("dialog.replace.with_prompt"));
        content.getChildren().addAll(
                new Label(i18n.apply("dialog.replace.find_label")),  findF,
                new Label(i18n.apply("dialog.replace.with_label")),  replF);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(btn ->
                (btn == replBtn || btn == replAllBtn)
                        ? findF.getText() + "|" + replF.getText() + "|" + (btn == replAllBtn ? "all" : "one")
                        : null);
        com.example.jylos.ui.UiDialogs.show(dialog).ifPresent(res -> {
            String[] parts = res.split("\\|");
            if (parts.length != 3) return;
            String find = parts[0]; String repl = parts[1]; boolean all = "all".equals(parts[2]);
            String text = ta.getText();
            if (all) { ta.setText(text.replace(find, repl)); status.accept(i18n.apply("status.replaced_all")); return; }
            int idx = text.indexOf(find);
            if (idx >= 0) {
                ta.setText(text.substring(0, idx) + repl + text.substring(idx + find.length()));
                ta.selectRange(idx, idx + repl.length());
                status.accept(i18n.apply("status.replaced_first"));
            } else {
                status.accept(i18n.apply("status.text_not_found_general"));
            }
        });
    }

    // ============================================================
    // Tag management & metadata (called from MainController)
    // ============================================================

    public void loadNoteTags(Note note, FlowPane fp, Runnable onAdd, Consumer<Tag> onRemove) {
        if (fp == null) return;
        fp.getChildren().clear();
        if (note == null || note.getId() == null || note.getId().isEmpty()) return;
        try {
            List<Tag> tags = noteDAO.fetchTags(note.getId());
            for (Tag tag : tags) {
                HBox box = new HBox(4); box.setAlignment(Pos.CENTER_LEFT); box.getStyleClass().add("tag-container");
                Label lbl = new Label(tag.getTitle()); lbl.getStyleClass().add("tag-label");
                lbl.setTooltip(new Tooltip("Double-click to remove"));
                lbl.setOnMouseClicked(e -> { if (e.getClickCount() == 2) onRemove.accept(new Tag(tag.getId(), tag.getTitle(), null, null)); });
                Button rm = new Button("×"); rm.getStyleClass().add("tag-remove-btn");
                rm.setTooltip(new Tooltip("Remove tag from note"));
                rm.setOnAction(e -> onRemove.accept(new Tag(tag.getId(), tag.getTitle(), null, null)));
                box.getChildren().addAll(lbl, rm);
                fp.getChildren().add(box);
            }
            Button addBtn = new Button("+ Add Tag"); addBtn.getStyleClass().add("add-tag-button");
            addBtn.setOnAction(e -> onAdd.run());
            fp.getChildren().add(addBtn);
        } catch (Exception e) {
            logger.warning("Failed to load tags for note " + note.getId() + ": " + e.getMessage());
        }
    }

    public void updateNoteMetadata(Note note, Label modLabel,
            Label created, Label modified, Label words, Label chars,
            Label lat, Label lon, Label author, Label source,
            Function<String, String> i18n) {
        if (note == null) {
            setLabelSafe(modLabel, ""); setLabelSafe(created, "-"); setLabelSafe(modified, "-");
            setLabelSafe(words, "0"); setLabelSafe(chars, "0");
            if (lat    != null) lat.setText(MessageFormat.format(i18n.apply("info.lat"),    "-"));
            if (lon    != null) lon.setText(MessageFormat.format(i18n.apply("info.lon"),    "-"));
            if (author != null) author.setText(MessageFormat.format(i18n.apply("info.author"), "-"));
            if (source != null) source.setText(MessageFormat.format(i18n.apply("info.source"), "-"));
            return;
        }
        setLabelSafe(modLabel, note.getModifiedDate() != null ? "Modified " + note.getModifiedDate() : "");
        setLabelSafe(created,  orDash(note.getCreatedDate()));
        setLabelSafe(modified, orDash(note.getModifiedDate()));
        String body = liveEditorContent(note);
        int wordCount = countWords(body);
        setLabelSafe(words, String.valueOf(wordCount));
        setLabelSafe(chars, String.valueOf(body.length()));
        if (wordCountLabel != null && i18n != null) {
            wordCountLabel.setText(MessageFormat.format(i18n.apply("info.words_count"), wordCount));
        }
        if (lat    != null) lat.setText(MessageFormat.format(i18n.apply("info.lat"), note.getLatitude()  != 0 ? String.valueOf(note.getLatitude())  : "-"));
        if (lon    != null) lon.setText(MessageFormat.format(i18n.apply("info.lon"), note.getLongitude() != 0 ? String.valueOf(note.getLongitude()) : "-"));
        if (author != null) author.setText(MessageFormat.format(i18n.apply("info.author"), orDash(note.getAuthor())));
        if (source != null) source.setText(MessageFormat.format(i18n.apply("info.source"), orDash(note.getSourceUrl())));
    }

    // ============================================================
    // Preview, word count, editor chrome
    // ============================================================

    public void setWikiLinkHandler(WikiLinkHandler handler) {
        this.wikiLinkHandler = handler;
    }

    public void registerPreviewEnhancer(String pluginId, PreviewEnhancer enhancer) {
        if (pluginId != null && enhancer != null) {
            previewEnhancers.put(pluginId, enhancer);
            Platform.runLater(() -> refreshPreview(false));
        }
    }

    public void unregisterPreviewEnhancer(String pluginId) {
        if (pluginId != null) {
            previewEnhancers.remove(pluginId);
            Platform.runLater(() -> refreshPreview(false));
        }
    }

    public void refreshPreview(boolean darkTheme) {
        if (previewWebView == null || currentNote == null) {
            return;
        }
        String content = liveEditorContent(currentNote);
        String html = (content != null && !content.trim().isEmpty())
                ? MarkdownPreview.buildPreviewHtml(content, darkTheme, previewEnhancers.values(), previewBaseDir())
                : MarkdownPreview.buildEmptyHtml(darkTheme);
        previewWebView.getEngine().loadContent(html, "text/html");
        installWikiLinkListener();
    }

    /** Folder of the current note, used to resolve relative image paths in the preview. */
    private java.nio.file.Path previewBaseDir() {
        if (noteService == null || currentNote == null || currentNote.getId() == null) {
            return null;
        }
        return noteService.getNoteFilePath(currentNote.getId())
                .map(java.nio.file.Path::getParent)
                .orElse(null);
    }

    public void clearPreview() {
        if (previewWebView != null) {
            previewWebView.getEngine().loadContent("", "text/html");
        }
    }

    public void ensurePreviewWebViewThemeClass() {
        if (previewWebView != null && !previewWebView.getStyleClass().contains("webview-theme")) {
            previewWebView.getStyleClass().add("webview-theme");
        }
    }

    public void refreshWordCount(Function<String, String> i18n, Label externalWords, Label externalChars) {
        String content = liveEditorContent(currentNote);
        int wordCount = countWords(content);
        if (wordCountLabel != null && i18n != null) {
            wordCountLabel.setText(MessageFormat.format(i18n.apply("info.words_count"), wordCount));
        }
        setLabelSafe(externalWords, String.valueOf(wordCount));
        setLabelSafe(externalChars, String.valueOf(content.length()));
    }

    public void syncFavoritePinButtons(Function<String, String> i18n) {
        if (favoriteButton != null && currentNote != null) {
            boolean isFav = currentNote.isFavorite();
            favoriteButton.setSelected(isFav);
            if (favoriteButton.getTooltip() != null && i18n != null) {
                favoriteButton.getTooltip().setText(
                        isFav ? i18n.apply("action.remove_favorite") : i18n.apply("action.add_favorite"));
            }
        }
        if (pinButton != null) {
            if (currentNote != null && currentNote.isPinned()) {
                pinButton.setSelected(true);
                if (i18n != null) {
                    pinButton.setTooltip(new Tooltip(i18n.apply("action.unpin_note")));
                }
            } else {
                pinButton.setSelected(false);
                if (i18n != null) {
                    pinButton.setTooltip(new Tooltip(i18n.apply("tooltip.pin_note")));
                }
            }
        }
    }

    public void applyEditorZoom(double editorFontSize) {
        if (noteContentArea != null) {
            noteContentArea.setStyle("-fx-font-size: " + editorFontSize + "px;");
        }
        if (noteTitleField != null) {
            noteTitleField.setStyle("-fx-font-size: " + (editorFontSize + 2) + "px;");
        }
    }

    public void initializeTagsBarCollapsed() {
        if (toggleTagsBtn != null) {
            toggleTagsBtn.setSelected(false);
        }
        if (tagsContainer != null) {
            tagsContainer.setVisible(false);
            tagsContainer.setManaged(false);
        }
    }

    public void loadNoteTagsForNote(Note note, Runnable onAdd, Consumer<Tag> onRemove) {
        loadNoteTags(note, tagsFlowPane, onAdd, onRemove);
    }

    public void performUndo() {
        handleUndo(noteContentArea);
    }

    public void performCut() {
        handleCut(noteContentArea, noteTitleField);
    }

    public void performCopy() {
        handleCopy(noteContentArea, noteTitleField);
    }

    public void performPaste() {
        handlePaste(noteContentArea, noteTitleField);
    }

    public void performFind(Function<String, String> i18n, Consumer<String> status) {
        handleFind(noteContentArea, i18n, status);
    }

    public void performReplace(Function<String, String> i18n, Consumer<String> status) {
        handleReplace(noteContentArea, i18n, status);
    }

    private String liveEditorContent(Note note) {
        if (noteContentArea != null) {
            return orEmpty(noteContentArea.getText());
        }
        return note != null ? orEmpty(note.getContent()) : "";
    }

    private int countWords(String text) {
        if (noteService != null) {
            return noteService.countWords(text);
        }
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private void installWikiLinkListener() {
        if (wikiLinkListenerInstalled || previewWebView == null) {
            return;
        }
        wikiLinkListenerInstalled = true;
        previewWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    netscape.javascript.JSObject window = (netscape.javascript.JSObject)
                            previewWebView.getEngine().executeScript("window");
                    window.setMember("javaApp", previewJavaBridge);
                } catch (Exception e) {
                    logger.warning("Failed to inject preview Java bridge: " + e.getMessage());
                }
            }
        });
    }

    private final class PreviewJavaBridge {
        public void openNote(String title) {
            Platform.runLater(() -> {
                if (wikiLinkHandler != null) {
                    wikiLinkHandler.openNoteByTitle(title);
                }
            });
        }
    }

    // ============================================================
    // Favorite / pin toggles
    // ============================================================

    public record NoteToggleResult(boolean success, boolean newState, String successStatusKey, String errorMessage) {}

    public interface NoteMutationPort { void updateNote(Note note); }

    public NoteToggleResult toggleFavorite(Note note, NoteMutationPort port) {
        return toggleFlag(note, port, Note::isFavorite, Note::setFavorite,
                "status.note_marked_favorite", "status.note_unmarked_favorite");
    }

    public NoteToggleResult togglePin(Note note, NoteMutationPort port) {
        return toggleFlag(note, port, Note::isPinned, Note::setPinned,
                "status.note_pinned", "status.note_unpinned");
    }

    private NoteToggleResult toggleFlag(Note note, NoteMutationPort port,
            Function<Note, Boolean> getter, BiConsumer<Note, Boolean> setter, String onKey, String offKey) {
        if (note == null) return new NoteToggleResult(false, false, null, "Note is null");
        if (port == null) return new NoteToggleResult(false, false, null, "Port is null");
        try {
            boolean next = !Objects.equals(Boolean.TRUE, getter.apply(note));
            setter.accept(note, next);
            port.updateNote(note);
            return new NoteToggleResult(true, next, next ? onKey : offKey, null);
        } catch (Exception e) {
            logger.warning("Failed to toggle note flag: " + e.getMessage());
            return new NoteToggleResult(false, false, null, e.getMessage());
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
        if (eventBus != null && currentNote != null)
            eventBus.publish(new NoteEvents.NoteModifiedEvent(currentNote));
        updateSaveIndicator(true);
    }

    /**
     * Updates the inline save-indicator dot next to the title: amber while there are
     * unsaved changes, green once saved, hidden when no editable note is open.
     */
    private void updateSaveIndicator(boolean dirty) {
        if (dirtySaveIndicator == null) {
            return;
        }
        boolean show = currentNote != null && !viewingAttachment;
        setNodeVisible(dirtySaveIndicator, show);
        if (!show) {
            return;
        }
        dirtySaveIndicator.getStyleClass().removeAll("dirty", "saved");
        dirtySaveIndicator.getStyleClass().add(dirty ? "dirty" : "saved");
        if (dirtySaveIndicatorTip != null) {
            dirtySaveIndicatorTip.setText(dirty
                    ? getString("tooltip.unsaved_changes", "Unsaved changes")
                    : getString("tooltip.saved", "All changes saved"));
        }
    }

    private String getString(String key, String fallback) {
        if (bundle == null) return fallback;
        return bundle.containsKey(key) ? bundle.getString(key) : fallback;
    }

    private static void setNodeVisible(Node n, boolean visible) {
        if (n != null) { n.setVisible(visible); n.setManaged(visible); }
    }

    private static String orEmpty(String s)  { return s != null ? s : ""; }
    private static String orDash(String s)   { return (s != null && !s.isBlank()) ? s : "-"; }
    private static void setLabelSafe(Label l, String text) { if (l != null) l.setText(text); }
}
