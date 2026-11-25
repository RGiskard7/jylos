package com.example.jylos.plugin.builtin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginI18n;
import com.example.jylos.util.InlineMarkdown;
import com.example.jylos.util.InlineMarkdown.Run;
import com.example.jylos.util.InlineMarkdown.Style;
import com.example.jylos.util.MarkdownProcessor;
import com.example.jylos.util.NoteAnchors;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Outline Plugin - Shows document structure (headers) in the side panel.
 * 
 * <p>This is a classic Obsidian-style plugin that displays:</p>
 * <ul>
 *   <li>All headers (H1-H6) from the current note, as the reading view shows them: bold,
 *       italic, code, struck through, highlighted, links by their text, without the marks</li>
 *   <li>Each nested under the heading above it</li>
 *   <li>Click to navigate to that header</li>
 *   <li>Updated when another note is opened and when the note is saved</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.3.0
 */
public class OutlinePlugin implements Plugin {
    
    private static final String ID = "outline";
    private static final String NAME = "Outline";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Shows document structure (headers) in sidebar";
    private static final String AUTHOR = "Edu Díaz";
    
    private static final String PANEL_ID = "outline-panel";
    
    private PluginContext context;
    private VBox outlineContent;
    private VBox headersList;
    private Label emptyLabel;
    private Note currentNote;
    private String lastContent = "";
    private List<EventBus.Subscription> subscriptions = new ArrayList<>();

    // Loaded once, lazily — getDescription() can be called by the Plugin Manager before
    // initialize() ever runs, so this cannot wait for that.
    private static ResourceBundle bundle;

    private static String tr(String key, String fallback) {
        if (bundle == null) {
            bundle = PluginI18n.bundle(OutlinePlugin.class);
        }
        return PluginI18n.tr(bundle, key, fallback);
    }

    @Override
    public String getId() { return ID; }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDescription() { return tr("outline.plugin.description", DESCRIPTION); }

    @Override
    public String getAuthor() { return AUTHOR; }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;

        // Register commands
        context.registerCommand(
            "Outline: Refresh",
            tr("outline.command.refresh.description", "Refresh the document outline"),
            null,
            this::refreshOutline
        );

        // Register menu items
        context.registerMenuItem(tr("outline.title", "Outline"), tr("outline.menu.refresh", "Refresh Outline"), this::refreshOutline);
        
        // Subscribe to note events
        EventBus.Subscription noteSub = context.subscribe(NoteEvents.NoteSelectedEvent.class, event -> {
            this.currentNote = event.getNote();
            Platform.runLater(() -> updateOutline(currentNote != null ? currentNote.getContent() : ""));
        });
        subscriptions.add(noteSub);
        
        // Subscribe to note content changes (if available)
        EventBus.Subscription saveSub = context.subscribe(NoteEvents.NoteSavedEvent.class, event -> {
            if (currentNote != null && Objects.equals(event.getNote().getId(), currentNote.getId())) {
                this.currentNote = event.getNote();
                Platform.runLater(() -> updateOutline(currentNote.getContent()));
            }
        });
        subscriptions.add(saveSub);
        
        // Create and register the side panel
        Platform.runLater(() -> {
            createOutlinePanel();
            context.registerSidePanel(PANEL_ID, tr("outline.title", "Outline"), outlineContent, "fth-layers");
        });
        
        context.log("Outline Plugin initialized");
    }
    
    @Override
    public void shutdown() {
        // Unsubscribe from events
        for (EventBus.Subscription sub : subscriptions) {
            sub.cancel();
        }
        subscriptions.clear();
        
        context.unregisterCommand("Outline: Refresh");
        context.removeSidePanel(PANEL_ID);
        context.log("Outline Plugin shutdown");
    }
    
    /**
     * Creates the outline panel UI.
     */
    private void createOutlinePanel() {
        outlineContent = new VBox();
        outlineContent.setSpacing(0);
        outlineContent.setPadding(new Insets(8));
        outlineContent.setStyle("-fx-background-color: transparent;");
        
        emptyLabel = new Label(tr("outline.panel.empty", "No headers found"));
        emptyLabel.getStyleClass().add("outline-empty-label");
        emptyLabel.setPadding(new Insets(8));
        
        // Headers list
        headersList = new VBox();
        headersList.setSpacing(2);
        
        outlineContent.getChildren().addAll(emptyLabel, headersList);
        
        // Initial state
        updateOutline("");
    }
    
    /**
     * Updates the outline based on content.
     */
    private void updateOutline(String content) {
        if (headersList == null) return;
        
        // Skip if content hasn't changed
        if (content != null && content.equals(lastContent)) {
            return;
        }
        lastContent = content != null ? content : "";
        
        headersList.getChildren().clear();
        
        if (content == null || content.trim().isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }
        
        List<Entry> headers = entries(content);
        
        if (headers.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }
        
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        
        for (Entry header : headers) {
            headersList.getChildren().add(createHeaderRow(header));
        }
    }

    /**
     * A heading in the outline.
     *
     * @param level  1 to 6
     * @param depth  how many headings above it hold it (a lower level, before it)
     * @param runs   its text as it shows, piece by piece
     * @param offset character offset of its line in the note
     * @param anchor the id the reading view gives it, numbered when repeated ({@code ""} if none)
     */
    record Entry(int level, int depth, List<Run> runs, int offset, String anchor) {
    }

    /**
     * The headings of a note, outside code, in order; each nested under the last one of a
     * lower level, and with the id the reading view gives it (the same text twice: the
     * second gets {@code -1}).
     */
    static List<Entry> entries(String content) {
        List<Entry> entries = new ArrayList<>();
        Deque<Integer> above = new ArrayDeque<>();
        Map<String, Integer> seen = new HashMap<>();
        for (NoteAnchors.Heading heading : NoteAnchors.headings(content)) {
            List<Run> runs = InlineMarkdown.runs(heading.title());
            if (runs.isEmpty()) {
                continue;
            }
            while (!above.isEmpty() && above.peek() >= heading.level()) {
                above.pop();
            }
            StringBuilder text = new StringBuilder();
            runs.forEach(run -> text.append(run.text()));
            String base = MarkdownProcessor.slugifyHeading(text.toString());
            String anchor = "";
            if (!base.isEmpty()) {
                int occurrence = seen.merge(base, 1, Integer::sum) - 1;
                anchor = occurrence == 0 ? base : base + "-" + occurrence;
            }
            entries.add(new Entry(heading.level(), above.size(), runs, heading.offset(), anchor));
            above.push(heading.level());
        }
        return entries;
    }
    
    /**
     * A clickable row for a header: its text as it shows, indented by how deep it is.
     */
    private HBox createHeaderRow(Entry header) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("outline-text");
        for (Run run : header.runs()) {
            flow.getChildren().add(piece(run));
        }
        HBox row = new HBox(flow);
        HBox.setHgrow(flow, Priority.ALWAYS);
        row.setPadding(new Insets(3, 8, 3, 8 + header.depth() * 14));
        row.getStyleClass().addAll("outline-item", "outline-h" + header.level());
        row.setMaxWidth(Double.MAX_VALUE);
        
        // Click to jump to this header — the raw-source offset if the editor is
        // showing, or its #anchor if the rendered Preview is showing.
        row.setOnMouseClicked(e -> context.navigateToHeading(header.offset(), header.anchor()));
        return row;
    }

    /** A piece of a heading: code and highlights on their own background, the rest as text. */
    private static Node piece(Run run) {
        List<String> classes = new ArrayList<>();
        for (Style style : run.styles()) {
            classes.add("outline-" + style.name().toLowerCase(Locale.ROOT));
        }
        if (run.styles().contains(Style.CODE) || run.styles().contains(Style.HIGHLIGHT)) {
            Label label = new Label(run.text());
            label.getStyleClass().add("outline-chip");
            label.getStyleClass().addAll(classes);
            label.setMinWidth(Label.USE_PREF_SIZE);
            return label;
        }
        Text text = new Text(run.text());
        text.getStyleClass().add("outline-run");
        text.getStyleClass().addAll(classes);
        return text;
    }
    
    /**
     * Refresh the outline manually.
     */
    private void refreshOutline() {
        if (currentNote != null) {
            // Force refresh by clearing lastContent
            lastContent = "";
            updateOutline(currentNote.getContent());
            context.showInfo(tr("outline.title", "Outline"), null, tr("outline.info.refreshed", "Outline refreshed!"));
        } else {
            context.showInfo(tr("outline.title", "Outline"), null, tr("outline.info.noNote", "No note selected"));
        }
    }
}
