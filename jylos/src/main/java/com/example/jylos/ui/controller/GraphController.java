package com.example.jylos.ui.controller;

import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.graph.GraphCanvas;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
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
    private ResourceBundle bundle;
    private Consumer<String> onOpenNote;
    private Runnable onClose;
    private Supplier<String> currentNoteIdSupplier = () -> null;

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
        this.graphBuilder = new GraphBuilder(noteService, tagService);
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
        canvas.setDarkTheme(dark);
        rebuild();
    }

    /** Re-applies dark/light colors without rebuilding the graph data. */
    public void applyTheme(boolean dark) {
        canvas.setDarkTheme(dark);
    }

    /** Stops the simulation loop while the graph is hidden (saves CPU). */
    public void pause() {
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
        task.setOnSucceeded(e -> canvas.setData(task.getValue()));
        task.setOnFailed(e -> logger.log(Level.WARNING, "Graph build failed", task.getException()));
        Thread thread = new Thread(task, "graph-build");
        thread.setDaemon(true);
        thread.start();
    }
}
