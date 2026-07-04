package com.example.jylos.plugin.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Outline Plugin - Shows document structure (headers) in the side panel.
 * 
 * <p>This is a classic Obsidian-style plugin that displays:</p>
 * <ul>
 *   <li>All headers (H1-H6) from the current note</li>
 *   <li>Hierarchical indentation based on header level</li>
 *   <li>Click to navigate to that header</li>
 *   <li>Real-time updates as you type</li>
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
    private static final String AUTHOR = "Jylos Team";
    
    private static final String PANEL_ID = "outline-panel";
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    
    private PluginContext context;
    private VBox outlineContent;
    private VBox headersList;
    private Label emptyLabel;
    private Note currentNote;
    private String lastContent = "";
    private List<EventBus.Subscription> subscriptions = new ArrayList<>();
    
    @Override
    public String getId() { return ID; }
    
    @Override
    public String getName() { return NAME; }
    
    @Override
    public String getVersion() { return VERSION; }
    
    @Override
    public String getDescription() { return DESCRIPTION; }
    
    @Override
    public String getAuthor() { return AUTHOR; }
    
    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        
        // Register commands
        context.registerCommand(
            "Outline: Refresh",
            "Refresh the document outline",
            null,
            this::refreshOutline
        );
        
        // Register menu items
        context.registerMenuItem("Outline", "Refresh Outline", this::refreshOutline);
        
        // Subscribe to note events
        EventBus.Subscription noteSub = context.subscribe(NoteEvents.NoteSelectedEvent.class, event -> {
            this.currentNote = event.getNote();
            Platform.runLater(() -> updateOutline(currentNote != null ? currentNote.getContent() : ""));
        });
        subscriptions.add(noteSub);
        
        // Subscribe to note content changes (if available)
        EventBus.Subscription saveSub = context.subscribe(NoteEvents.NoteSavedEvent.class, event -> {
            if (currentNote != null && java.util.Objects.equals(event.getNote().getId(), currentNote.getId())) {
                this.currentNote = event.getNote();
                Platform.runLater(() -> updateOutline(currentNote.getContent()));
            }
        });
        subscriptions.add(saveSub);
        
        // Create and register the side panel
        Platform.runLater(() -> {
            createOutlinePanel();
            context.registerSidePanel(PANEL_ID, "Outline", outlineContent, "fth-layers");
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
        
        emptyLabel = new Label("No headers found");
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
        
        List<HeaderEntry> headers = parseHeaders(content);
        
        if (headers.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }
        
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        
        for (HeaderEntry header : headers) {
            Label headerLabel = createHeaderLabel(header);
            headersList.getChildren().add(headerLabel);
        }
    }
    
    /**
     * Parses headers from markdown content.
     */
    private List<HeaderEntry> parseHeaders(String content) {
        List<HeaderEntry> headers = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(content);
        
        while (matcher.find()) {
            int level = matcher.group(1).length();
            String text = matcher.group(2).trim();
            int position = matcher.start();
            headers.add(new HeaderEntry(level, text, position));
        }
        
        return headers;
    }
    
    /**
     * Creates a clickable label for a header.
     */
    private Label createHeaderLabel(HeaderEntry header) {
        Label label = new Label(header.text);
        
        // Indent based on level
        int indent = (header.level - 1) * 12;
        label.setPadding(new Insets(4, 8, 4, 8 + indent));
        
        // Use CSS classes for styling
        label.getStyleClass().add("outline-item");
        label.getStyleClass().add("outline-h" + header.level);
        label.setMaxWidth(Double.MAX_VALUE);
        
        // Click to show info (in future: scroll to position)
        label.setOnMouseClicked(e -> {
            context.showInfo("Navigate to Header", header.text, 
                String.format("Header Level: H%d\nPosition: %d", header.level, header.position));
        });
        
        return label;
    }
    
    /**
     * Refresh the outline manually.
     */
    private void refreshOutline() {
        if (currentNote != null) {
            // Force refresh by clearing lastContent
            lastContent = "";
            updateOutline(currentNote.getContent());
            context.showInfo("Outline", null, "Outline refreshed!");
        } else {
            context.showInfo("Outline", null, "No note selected");
        }
    }
    
    /**
     * Header entry data class.
     */
    private static class HeaderEntry {
        final int level;
        final String text;
        final int position;
        
        HeaderEntry(int level, String text, int position) {
            this.level = level;
            this.text = text;
            this.position = position;
        }
    }
}
