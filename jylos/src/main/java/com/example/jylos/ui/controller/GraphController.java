package com.example.jylos.ui.controller;

import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.FolderEvents;
import com.example.jylos.event.events.NoteEvents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.graph.GraphCanvas;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Hosts the Obsidian-style knowledge graph, rendered natively on a JavaFX
 * {@link GraphCanvas}.
 *
 * <p>The model (nodes/edges) is built off the FX thread by {@link GraphBuilder}
 * and handed to the canvas, which owns the force simulation, rendering and
 * interaction. This controller only orchestrates building, theming and routing
 * node clicks back to the app.</p>
 *
 * @author Edu Díaz (RGiskard7)
 */
public class GraphController {

    private static final Logger logger = LoggerConfig.getLogger(GraphController.class);
    private static final int LOCAL_DEPTH = 2;

    @FXML private VBox graphRoot;
    @FXML private Label graphTitleLabel;
    @FXML private ToggleButton localGraphBtn;
    @FXML private ToggleButton showTagsBtn;
    @FXML private ToggleButton settingsBtn;
    @FXML private StackPane graphCanvasHolder;

    // Settings panel
    @FXML private VBox settingsPanel;
    @FXML private TextField graphFilterField;
    @FXML private ComboBox<String> tagFilterCombo;
    @FXML private ComboBox<String> folderFilterCombo;
    @FXML private CheckBox orphansCheck;
    @FXML private CheckBox ghostsCheck;
    @FXML private CheckBox arrowsCheck;
    @FXML private CheckBox colorFolderCheck;
    @FXML private Slider repulsionSlider;
    @FXML private Slider linkForceSlider;
    @FXML private Slider linkDistanceSlider;
    @FXML private Slider centerSlider;
    @FXML private Slider labelThresholdSlider;
    @FXML private Slider nodeScaleSlider;
    @FXML private Slider lineThicknessSlider;

    private final GraphCanvas canvas = new GraphCanvas();

    private GraphBuilder graphBuilder;
    private TagService tagService;

    /** Last model built off the FX thread; view filters re-render from this without rebuilding. */
    private GraphData lastBuilt = GraphData.empty();
    /** Sentinel "All" option shared by the tag/folder filter combos. */
    private String filterAllLabel = "All";

    private ResourceBundle bundle;
    private Consumer<String> onOpenNote;
    private Runnable onClose;
    private Supplier<String> currentNoteIdSupplier = () -> null;

    /** True while the graph overlay is on screen (drives live refresh on edits). */
    private boolean graphVisible = false;
    private boolean dataEventsWired = false;

    @FXML
    private void initialize() {
        if (graphCanvasHolder != null) {
            graphCanvasHolder.getChildren().add(0, canvas);
            VBox.setVgrow(graphCanvasHolder, Priority.ALWAYS);
        }
        canvas.setOnOpenNote(id -> {
            if (onOpenNote != null && id != null) {
                onOpenNote.accept(id);
            }
        });
        wireForceSliders();
        wireFilters();
    }

    /**
     * Wires the view filters (text / tag / folder). These re-render from the cached
     * {@link #lastBuilt} model via {@link #applyView()} — no model rebuild, so typing in
     * the filter box is cheap even on large vaults.
     */
    private void wireFilters() {
        if (graphFilterField != null) {
            graphFilterField.textProperty().addListener((o, a, b) -> applyView());
        }
        if (tagFilterCombo != null) {
            tagFilterCombo.valueProperty().addListener((o, a, b) -> applyView());
        }
        if (folderFilterCombo != null) {
            folderFilterCombo.valueProperty().addListener((o, a, b) -> applyView());
        }
    }

    /** Initializes the settings sliders from the canvas defaults, binds live updates,
     *  and gives each a tooltip showing its current numeric value on hover. */
    private void wireForceSliders() {
        bindSlider(repulsionSlider, canvas.getRepulsion(),
                canvas::setRepulsion, v -> String.valueOf((int) v));
        bindSlider(linkForceSlider, canvas.getLinkForce() * 100.0,
                v -> canvas.setLinkForce(v / 100.0), v -> String.format("%.2f", v / 100.0));
        bindSlider(linkDistanceSlider, canvas.getLinkDistance(),
                canvas::setLinkDistance, v -> String.valueOf((int) v));
        bindSlider(centerSlider, canvas.getCenterGravity() * 1000.0,
                v -> canvas.setCenterGravity(v / 1000.0), v -> String.format("%.3f", v / 1000.0));
        bindSlider(labelThresholdSlider, canvas.getLabelThreshold() * 100.0,
                v -> canvas.setLabelThreshold(v / 100.0), v -> String.format("%.2f", v / 100.0));
        bindSlider(nodeScaleSlider, canvas.getNodeScale() * 100.0,
                v -> canvas.setNodeScale(v / 100.0), v -> String.format("%.2f×", v / 100.0));
        bindSlider(lineThicknessSlider, canvas.getLineThickness() * 100.0,
                v -> canvas.setLineThickness(v / 100.0), v -> String.format("%.2f×", v / 100.0));
    }

    /**
     * Binds a slider: sets its initial value, applies changes live to the canvas,
     * and shows a hover tooltip with the current (mapped) numeric value.
     */
    private void bindSlider(Slider slider, double initial,
            java.util.function.DoubleConsumer setter,
            java.util.function.DoubleFunction<String> format) {
        if (slider == null) {
            return;
        }
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(format.apply(initial));
        tip.setShowDelay(javafx.util.Duration.millis(120));
        slider.setTooltip(tip);
        slider.setValue(initial);
        slider.valueProperty().addListener((o, a, b) -> {
            setter.accept(b.doubleValue());
            tip.setText(format.apply(b.doubleValue()));
        });
    }

    // ------------------------------------------------------------------
    // Wiring from MainController
    // ------------------------------------------------------------------

    public void setServices(NoteService noteService, TagService tagService) {
        this.tagService = tagService;
        this.graphBuilder = new GraphBuilder(noteService, tagService);
        wireDataEvents();
    }

    /**
     * Subscribes to note/folder events so the cached link data stays correct and the
     * graph refreshes live while it is on screen (perf P3 — only the changed note is
     * re-read on the next build, not the whole vault).
     */
    private void wireDataEvents() {
        if (dataEventsWired) {
            return;
        }
        dataEventsWired = true;
        EventBus bus = EventBus.getInstance();
        bus.subscribe(NoteEvents.NoteSavedEvent.class, e -> onNoteChanged(idOf(e.getNote())));
        bus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> onNoteChanged(idOf(e.getNote())));
        bus.subscribe(NoteEvents.NoteUpdatedEvent.class, e -> onNoteChanged(idOf(e.getNote())));
        bus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> onNoteChanged(e.getNoteId()));
        bus.subscribe(FolderEvents.FolderDeletedEvent.class, e -> onVaultChanged());
        bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> onVaultChanged());
    }

    /** A single note changed: invalidate only its cache entry, refresh if visible. */
    private void onNoteChanged(String noteId) {
        if (graphBuilder != null) {
            graphBuilder.invalidateNote(noteId);
        }
        if (graphVisible) {
            rebuild();
        }
    }

    /** Wholesale change (folder delete / refresh): clear cache, refresh if visible. */
    private void onVaultChanged() {
        if (graphBuilder != null) {
            graphBuilder.invalidateAll();
        }
        if (graphVisible) {
            rebuild();
        }
    }

    private static String idOf(com.example.jylos.data.models.Note note) {
        return note != null ? note.getId() : null;
    }

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    public void setOnOpenNote(Consumer<String> onOpenNote) {
        this.onOpenNote = onOpenNote;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setCurrentNoteIdSupplier(Supplier<String> supplier) {
        if (supplier != null) {
            this.currentNoteIdSupplier = supplier;
        }
    }

    public VBox getRoot() {
        return graphRoot;
    }

    // ------------------------------------------------------------------
    // Public actions (called by MainController)
    // ------------------------------------------------------------------

    /** Shows the graph with the current theme and rebuilds the data. */
    public void show(boolean dark) {
        graphVisible = true;
        canvas.setDarkTheme(dark);
        rebuild();
    }

    /** Re-applies dark/light colors without rebuilding the graph data. */
    public void applyTheme(boolean dark) {
        canvas.setDarkTheme(dark);
    }

    /** Stops the simulation loop while the graph is hidden (saves CPU). */
    public void pause() {
        graphVisible = false;
        canvas.pause();
    }

    // ------------------------------------------------------------------
    // FXML handlers
    // ------------------------------------------------------------------

    @FXML
    private void handleToggleScope() {
        rebuild();
    }

    @FXML
    private void handleToggleTags() {
        rebuild();
    }

    @FXML
    private void handleZoomIn() {
        canvas.zoomIn();
    }

    @FXML
    private void handleZoomOut() {
        canvas.zoomOut();
    }

    @FXML
    private void handleResetView() {
        canvas.resetView();
    }

    @FXML
    private void handleRefresh() {
        rebuild();
    }

    @FXML
    private void handleClose() {
        if (onClose != null) {
            onClose.run();
        }
    }

    /** Shows/hides the settings panel (gear button). */
    @FXML
    private void handleToggleSettings() {
        if (settingsPanel == null) {
            return;
        }
        boolean show = settingsBtn != null && settingsBtn.isSelected();
        settingsPanel.setVisible(show);
        settingsPanel.setManaged(show);
    }

    /** A filter checkbox (orphans/ghosts) changed → rebuild the model. */
    @FXML
    private void handleFilters() {
        rebuild();
    }

    /** Color-by-folder toggle changed → repaint only (no rebuild needed). */
    @FXML
    private void handleColorMode() {
        canvas.setColorByFolder(colorFolderCheck != null && colorFolderCheck.isSelected());
    }

    /** Directional-arrows toggle changed → repaint only. */
    @FXML
    private void handleArrows() {
        canvas.setShowArrows(arrowsCheck != null && arrowsCheck.isSelected());
    }

    // ------------------------------------------------------------------
    // Graph building (model off the FX thread, render on it)
    // ------------------------------------------------------------------

    private void rebuild() {
        if (graphBuilder == null) {
            return;
        }
        GraphBuilder.Options options = new GraphBuilder.Options(
                showTagsBtn == null || showTagsBtn.isSelected(),
                ghostsCheck == null || ghostsCheck.isSelected(),
                orphansCheck == null || orphansCheck.isSelected());
        final boolean local = localGraphBtn != null && localGraphBtn.isSelected();
        final String rootId = currentNoteIdSupplier.get();

        Task<GraphData> task = new Task<>() {
            @Override
            protected GraphData call() {
                return (local && rootId != null)
                        ? graphBuilder.buildLocalGraph(rootId, LOCAL_DEPTH, options)
                        : graphBuilder.buildGlobalGraph(options);
            }
        };
        task.setOnSucceeded(e -> {
            lastBuilt = task.getValue();
            refreshFilterChoices(lastBuilt);
            applyView();
        });
        task.setOnFailed(e -> logger.log(Level.WARNING, "Graph build failed", task.getException()));
        Thread thread = new Thread(task, "graph-build");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Renders {@link #lastBuilt} through the active view filters (text / tag / folder).
     * Cheap: it only re-filters the in-memory model — no rebuild, no file reads.
     */
    private void applyView() {
        canvas.setData(filterView(lastBuilt));
    }

    /**
     * Filters a built graph by the current text/tag/folder selections. Keeps matching
     * nodes plus the edges between two kept nodes. Returns {@code data} unchanged when no
     * filter is active.
     */
    private GraphData filterView(GraphData data) {
        if (data == null) {
            return GraphData.empty();
        }
        String text = graphFilterField != null && graphFilterField.getText() != null
                ? graphFilterField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String tag = selectedFilter(tagFilterCombo);
        String folder = selectedFilter(folderFilterCombo);
        boolean noText = text.isEmpty();
        boolean noTag = tag == null;
        boolean noFolder = folder == null;
        if (noText && noTag && noFolder) {
            return data;
        }

        Set<String> tagNoteIds = noTag ? null : noteIdsForTag(tag);

        Set<String> keep = new HashSet<>();
        for (GraphNode n : data.nodes()) {
            boolean isNote = n.type() == GraphNode.Type.NOTE;
            if (!noText && (n.label() == null || !n.label().toLowerCase(Locale.ROOT).contains(text))) {
                continue;
            }
            if (!noFolder && !folder.equals(n.group())) {
                continue;
            }
            if (tagNoteIds != null && (!isNote || !tagNoteIds.contains(n.id()))) {
                // A tag filter restricts the graph to notes carrying that tag.
                continue;
            }
            keep.add(n.id());
        }

        List<GraphNode> nodes = new ArrayList<>();
        for (GraphNode n : data.nodes()) {
            if (keep.contains(n.id())) {
                nodes.add(n);
            }
        }
        List<GraphEdge> edges = new ArrayList<>();
        for (GraphEdge edge : data.edges()) {
            if (keep.contains(edge.source()) && keep.contains(edge.target())) {
                edges.add(edge);
            }
        }
        return new GraphData(nodes, edges);
    }

    /** Resolves the note ids carrying {@code tagTitle} (empty set if unknown). */
    private Set<String> noteIdsForTag(String tagTitle) {
        Set<String> ids = new HashSet<>();
        if (tagService == null) {
            return ids;
        }
        try {
            Tag tag = tagService.getTagByTitle(tagTitle).orElse(null);
            if (tag != null) {
                for (Note note : tagService.getNotesWithTag(tag)) {
                    if (note != null && note.getId() != null) {
                        ids.add(note.getId());
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Graph tag filter failed for '" + tagTitle + "': " + e.getMessage());
        }
        return ids;
    }

    /** Returns the combo's value, or null when it is empty / the "All" sentinel. */
    private String selectedFilter(ComboBox<String> combo) {
        if (combo == null) {
            return null;
        }
        String value = combo.getValue();
        return (value == null || value.equals(filterAllLabel)) ? null : value;
    }

    /**
     * Repopulates the tag and folder filter choices, preserving the current selection.
     * Tags come from {@link TagService}; folders are the distinct node groups present in
     * the built graph. The first entry is always the "All" sentinel.
     */
    private void refreshFilterChoices(GraphData data) {
        if (bundle != null) {
            try {
                filterAllLabel = bundle.getString("graph.filter_all");
            } catch (Exception ignored) {
                // keep default "All"
            }
        }
        if (tagFilterCombo != null && tagFilterCombo.getItems().size() <= 1 && tagService != null) {
            List<String> tags = new ArrayList<>();
            tags.add(filterAllLabel);
            try {
                for (Tag tag : tagService.getAllTagsSorted()) {
                    if (tag != null && tag.getTitle() != null) {
                        tags.add(tag.getTitle());
                    }
                }
            } catch (Exception ignored) {
                // leave just the "All" entry
            }
            String selected = tagFilterCombo.getValue();
            tagFilterCombo.getItems().setAll(tags);
            tagFilterCombo.setValue(selected != null && tags.contains(selected) ? selected : filterAllLabel);
        }
        if (folderFilterCombo != null) {
            Set<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (GraphNode n : data.nodes()) {
                if (n.type() == GraphNode.Type.NOTE && n.group() != null && !n.group().isBlank()) {
                    groups.add(n.group());
                }
            }
            List<String> folders = new ArrayList<>();
            folders.add(filterAllLabel);
            folders.addAll(groups);
            String selected = folderFilterCombo.getValue();
            folderFilterCombo.getItems().setAll(folders);
            folderFilterCombo.setValue(selected != null && folders.contains(selected) ? selected : filterAllLabel);
        }
    }
}
