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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.prefs.Preferences;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphColorGroups;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.graph.GraphCanvas;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

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
 * @since 1.0.0
 */
public class GraphController {

    private static final Logger logger = LoggerConfig.getLogger(GraphController.class);
    /** Where the graph's settings are kept between sessions. */
    private static Preferences prefs =
            Preferences.userNodeForPackage(GraphController.class);

    /** Keeps the graph's settings somewhere else (tests); {@code null} goes back to the user's. */
    static void usePreferences(Preferences other) {
        prefs = other != null ? other : Preferences.userNodeForPackage(GraphController.class);
    }

    private static final String GROUPS_KEY = "graph.color_groups";

    @FXML private VBox graphRoot;
    @FXML private Label graphTitleLabel;
    @FXML private ToggleButton localGraphBtn;
    @FXML private ToggleButton showTagsBtn;
    @FXML private ToggleButton settingsBtn;
    @FXML private StackPane graphCanvasHolder;

    // Settings panel
    @FXML private ScrollPane settingsScrollPane;
    @FXML private VBox settingsPanel;
    @FXML private TextField graphFilterField;
    @FXML private ComboBox<String> tagFilterCombo;
    @FXML private ComboBox<String> folderFilterCombo;
    @FXML private CheckBox orphansCheck;
    @FXML private CheckBox ghostsCheck;
    @FXML private CheckBox attachmentsCheck;
    @FXML private CheckBox arrowsCheck;
    @FXML private CheckBox colorFolderCheck;
    @FXML private Slider repulsionSlider;
    @FXML private Slider linkForceSlider;
    @FXML private Slider linkDistanceSlider;
    @FXML private Slider centerSlider;
    @FXML private Slider labelThresholdSlider;
    @FXML private Slider nodeScaleSlider;
    @FXML private Slider lineThicknessSlider;
    @FXML private Slider localDepthSlider;
    @FXML private CheckBox localIncomingCheck;
    @FXML private CheckBox localOutgoingCheck;
    @FXML private CheckBox localNeighborCheck;
    @FXML private VBox colorGroupsBox;
    @FXML private Button addColorGroupBtn;
    /** Colour group edits are applied and saved a moment after the last change. */
    private final PauseTransition groupsDebounce =
            new PauseTransition(Duration.millis(250));
    /** The tags of each note, read only once a group asks for them ("tag:"); null until then. */
    private Map<String, Set<String>> noteTags;

    private final GraphCanvas canvas = new GraphCanvas();

    private EventBus eventBus;
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
    /** Notified with the freshly built model after every rebuild, so the right panel's
     *  baseline graph-info section can show live node/edge counts without polling. */
    private Consumer<GraphData> onDataBuilt = data -> { };

    /** True while the graph overlay is on screen (drives live refresh on edits). */
    private boolean graphVisible = false;
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

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
        // The zoom is kept for next time, as Obsidian does; the local graph keeps its own.
        canvas.setOnZoomChosen(scale -> {
            if (scale == null) {
                prefs.remove(scaleKey());
            } else {
                prefs.putDouble(scaleKey(), scale);
            }
        });
        wireForceSliders();
        wireFilters();
        rememberSettings();
        groupsDebounce.setOnFinished(e -> {
            saveColorGroups();
            applyColorGroups();
        });
        loadColorGroups();
    }

    /**
     * Restores every setting saved last time and saves each one when it changes, as Obsidian
     * keeps its graph settings.
     */
    private void rememberSettings() {
        remember(repulsionSlider, "graph.v2.repulsion"); // v2: Obsidian's scale (1000)
        remember(linkForceSlider, "graph.link_force");
        remember(linkDistanceSlider, "graph.v2.link_distance");
        remember(centerSlider, "graph.v2.center");
        remember(labelThresholdSlider, "graph.label_threshold");
        remember(nodeScaleSlider, "graph.node_scale");
        remember(lineThicknessSlider, "graph.line_thickness");
        remember(localDepthSlider, "graph.local_depth");
        if (arrowsCheck != null) {
            remember(arrowsCheck.selectedProperty(), "graph.arrows", this::handleArrows);
        }
        if (colorFolderCheck != null) {
            remember(colorFolderCheck.selectedProperty(), "graph.color_by_folder", this::handleColorMode);
        }
        for (Object[] toggle : new Object[][] {
                {orphansCheck, "graph.orphans"}, {ghostsCheck, "graph.unresolved"},
                {attachmentsCheck, "graph.attachments"},
                {localIncomingCheck, "graph.local_incoming"}, {localOutgoingCheck, "graph.local_outgoing"},
                {localNeighborCheck, "graph.local_neighbors"}}) {
            if (toggle[0] instanceof CheckBox box) {
                remember(box.selectedProperty(), (String) toggle[1], null);
            }
        }
        if (showTagsBtn != null) {
            remember(showTagsBtn.selectedProperty(), "graph.tags", null);
        }
        if (localDepthSlider != null) {
            // The depth applies on release (and at once when set by other means).
            localDepthSlider.valueProperty().addListener((o, was, now) -> {
                if (!localDepthSlider.isValueChanging()) {
                    rebuildIfLocal();
                }
            });
            localDepthSlider.valueChangingProperty().addListener((o, was, now) -> {
                if (!now) {
                    rebuildIfLocal();
                }
            });
        }
    }

    private void remember(Slider slider, String key) {
        if (slider == null) {
            return;
        }
        if (prefs.get(key, null) != null) {
            slider.setValue(prefs.getDouble(key, slider.getValue()));
        }
        slider.valueProperty().addListener((o, was, now) -> prefs.putDouble(key, now.doubleValue()));
    }

    private void remember(BooleanProperty selected, String key, Runnable apply) {
        if (prefs.get(key, null) != null) {
            selected.set(prefs.getBoolean(key, selected.get()));
            if (apply != null) {
                apply.run();
            }
        }
        selected.addListener((o, was, now) -> prefs.putBoolean(key, now));
    }

    private void rebuildIfLocal() {
        if (graphVisible && localGraphBtn != null && localGraphBtn.isSelected()) {
            rebuild();
        }
    }

    // ---- Colour groups: a query and a colour each; the first one a note matches colours it.

    @FXML
    private void handleAddColorGroup() {
        TextField query = addColorGroupRow("", Color.hsb(Math.random() * 360, 0.6, 0.85));
        groupsDebounce.playFromStart();
        query.requestFocus();
    }

    private TextField addColorGroupRow(String queryText, Color color) {
        TextField query = new TextField(queryText);
        query.getStyleClass().add("graph-color-group-query");
        query.setPromptText(text("graph.group_query", "Query: text, path:, file: or tag:"));
        ColorPicker picker = new ColorPicker(color);
        picker.getStyleClass().add("graph-color-group-color");
        picker.setMaxWidth(44);
        Button remove = new Button("\u2715");
        remove.getStyleClass().addAll("icon-btn", "graph-color-group-remove");
        remove.setTooltip(new Tooltip(text("graph.group_remove", "Remove group")));
        HBox row = new HBox(4, query, picker, remove);
        row.getStyleClass().add("graph-color-group");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(query, Priority.ALWAYS);
        query.textProperty().addListener((o, was, now) -> groupsDebounce.playFromStart());
        picker.valueProperty().addListener((o, was, now) -> groupsDebounce.playFromStart());
        remove.setOnAction(e -> {
            colorGroupsBox.getChildren().remove(row);
            groupsDebounce.playFromStart();
        });
        if (colorGroupsBox != null) {
            colorGroupsBox.getChildren().add(row);
        }
        return query;
    }

    /** The groups as shown, in order. */
    private List<GraphColorGroups.Group> colorGroups() {
        List<GraphColorGroups.Group> groups = new ArrayList<>();
        if (colorGroupsBox == null) {
            return groups;
        }
        for (Node node : colorGroupsBox.getChildren()) {
            if (node.lookup(".graph-color-group-query") instanceof TextField query
                    && node.lookup(".graph-color-group-color") instanceof ColorPicker picker) {
                groups.add(new GraphColorGroups.Group(query.getText(), hex(picker.getValue())));
            }
        }
        return groups;
    }

    private void saveColorGroups() {
        StringBuilder stored = new StringBuilder();
        for (GraphColorGroups.Group group : colorGroups()) {
            stored.append(group.color()).append('\t')
                    .append(group.query() == null ? "" : group.query().replace('\n', ' ')).append('\n');
        }
        prefs.put(GROUPS_KEY, stored.toString());
    }

    private void loadColorGroups() {
        for (String line : prefs.get(GROUPS_KEY, "").split("\n")) {
            int tab = line.indexOf('\t');
            if (tab > 0) {
                try {
                    addColorGroupRow(line.substring(tab + 1), Color.web(line.substring(0, tab)));
                } catch (IllegalArgumentException ignored) {
                    // a colour that no longer parses: skip the group
                }
            }
        }
    }

    /** Paints the nodes the groups match (reading the notes' tags first if a group needs them). */
    private void applyColorGroups() {
        List<GraphColorGroups.Group> groups = colorGroups();
        boolean needsTags = groups.stream().anyMatch(g -> g.query() != null
                && g.query().strip().toLowerCase(Locale.ROOT).startsWith("tag:"));
        if (needsTags && noteTags == null && tagService != null) {
            Task<Map<String, Set<String>>> read = new Task<>() {
                @Override
                protected Map<String, Set<String>> call() {
                    return readNoteTags();
                }
            };
            read.setOnSucceeded(e -> {
                noteTags = read.getValue();
                applyColorGroups();
            });
            Thread thread = new Thread(read, "graph-tags");
            thread.setDaemon(true);
            thread.start();
            return;
        }
        Map<String, Color> colors = new HashMap<>();
        GraphColorGroups.assign(lastBuilt.nodes(), groups,
                id -> noteTags != null ? noteTags.get(id) : null)
                .forEach((id, color) -> colors.put(id, Color.web(color)));
        canvas.setNodeColors(colors);
    }

    private Map<String, Set<String>> readNoteTags() {
        Map<String, Set<String>> tagsByNote = new HashMap<>();
        try {
            for (Tag tag : tagService.getAllTags()) {
                if (tag == null || tag.getTitle() == null) {
                    continue;
                }
                for (Note note : tagService.getNotesWithTag(tag)) {
                    if (note != null && note.getId() != null) {
                        tagsByNote.computeIfAbsent(note.getId(), k -> new HashSet<>()).add(tag.getTitle());
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Graph colour groups could not read tags: " + e.getMessage());
        }
        return tagsByNote;
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255));
    }

    private String text(String key, String fallback) {
        try {
            return bundle != null ? bundle.getString(key) : fallback;
        } catch (MissingResourceException e) {
            return fallback;
        }
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
            DoubleConsumer setter,
            DoubleFunction<String> format) {
        if (slider == null) {
            return;
        }
        Tooltip tip = new Tooltip(format.apply(initial));
        tip.setShowDelay(Duration.millis(120));
        slider.setTooltip(tip);
        slider.setValue(initial);
        slider.valueProperty().addListener((o, a, b) -> {
            setter.accept(b.doubleValue());
            tip.setText(format.apply(b.doubleValue()));
        });
    }

    // ── Wiring from MainController ───────────────────────────────────────────

    public void wire(EventBus eventBus, NoteService noteService, TagService tagService, ResourceBundle bundle,
            Consumer<String> onOpenNote, Runnable onClose, Supplier<String> currentNoteIdSupplier,
            Consumer<GraphData> onDataBuilt) {
        setServices(noteService, tagService);
        setBundle(bundle);
        setOnOpenNote(onOpenNote);
        setOnClose(onClose);
        setCurrentNoteIdSupplier(currentNoteIdSupplier);
        this.onDataBuilt = onDataBuilt != null ? onDataBuilt : data -> { };
        setEventBus(eventBus);
    }

    private void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
        rewireDataEvents();
    }

    private void setServices(NoteService noteService, TagService tagService) {
        this.tagService = tagService;
        this.graphBuilder = new GraphBuilder(noteService, tagService);
        rewireDataEvents();
    }

    /**
     * Subscribes to note/folder events so the cached link data stays correct and the
     * graph refreshes live while it is on screen (perf P3 — only the changed note is
     * re-read on the next build, not the whole vault).
     */
    private void rewireDataEvents() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (eventBus == null || graphBuilder == null) {
            return;
        }
        subscriptions.add(eventBus.subscribe(NoteEvents.NoteSavedEvent.class, e -> onNoteChanged(idOf(e.getNote()))));
        subscriptions.add(eventBus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> onNoteChanged(idOf(e.getNote()))));
        subscriptions.add(eventBus.subscribe(NoteEvents.NoteUpdatedEvent.class, e -> onNoteChanged(idOf(e.getNote()))));
        subscriptions.add(eventBus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> onNoteChanged(e.getNoteId())));
        subscriptions.add(eventBus.subscribe(FolderEvents.FolderDeletedEvent.class, e -> onVaultChanged()));
        subscriptions.add(eventBus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> onVaultChanged()));
    }

    public void teardown() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
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

    private static String idOf(Note note) {
        return note != null ? note.getId() : null;
    }

    private void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    private void setOnOpenNote(Consumer<String> onOpenNote) {
        this.onOpenNote = onOpenNote;
    }

    private void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    private void setCurrentNoteIdSupplier(Supplier<String> supplier) {
        if (supplier != null) {
            this.currentNoteIdSupplier = supplier;
        }
    }

    public VBox getRoot() {
        return graphRoot;
    }

    // ── Public actions (called by MainController) ────────────────────────────

    /** Shows the graph with the current theme and rebuilds the data. */
    public void show(boolean dark) {
        graphVisible = true;
        canvas.setDarkTheme(dark);
        openView();
        rebuild();
    }

    /** The graph opens afresh, at the zoom kept for this kind of graph (global or local). */
    private void openView() {
        double saved = prefs.getDouble(scaleKey(), -1);
        canvas.openView(saved > 0 ? saved : null);
    }

    private String scaleKey() {
        return localGraphBtn != null && localGraphBtn.isSelected() ? "graph.local_scale" : "graph.scale";
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

    // ── FXML handlers ────────────────────────────────────────────────────────

    @FXML
    private void handleToggleScope() {
        openView();
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
        if (settingsScrollPane == null) {
            return;
        }
        boolean show = settingsBtn != null && settingsBtn.isSelected();
        UiNodes.show(settingsScrollPane, show);
        if (show) {
            settingsScrollPane.setVvalue(0.0);
        }
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

    // ── Graph building (model off the FX thread, render on it) ───────────────

    private void rebuild() {
        if (graphBuilder == null) {
            return;
        }
        GraphBuilder.Options options = new GraphBuilder.Options(
                showTagsBtn == null || showTagsBtn.isSelected(),
                ghostsCheck == null || ghostsCheck.isSelected(),
                orphansCheck == null || orphansCheck.isSelected(),
                attachmentsCheck != null && attachmentsCheck.isSelected());
        final boolean local = localGraphBtn != null && localGraphBtn.isSelected();
        final String rootId = currentNoteIdSupplier.get();
        final GraphBuilder.LocalOptions localOptions = new GraphBuilder.LocalOptions(
                localDepthSlider != null ? (int) Math.round(localDepthSlider.getValue()) : 1,
                localOutgoingCheck == null || localOutgoingCheck.isSelected(),
                localIncomingCheck == null || localIncomingCheck.isSelected(),
                localNeighborCheck != null && localNeighborCheck.isSelected());
        noteTags = null; // tags may have changed: read again if a group needs them

        Task<GraphData> task = new Task<>() {
            @Override
            protected GraphData call() {
                return (local && rootId != null)
                        ? graphBuilder.buildLocalGraph(rootId, localOptions, options)
                        : graphBuilder.buildGlobalGraph(options);
            }
        };
        task.setOnSucceeded(e -> {
            lastBuilt = task.getValue();
            refreshFilterChoices(lastBuilt);
            applyView();
            applyColorGroups();
            onDataBuilt.accept(lastBuilt);
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
