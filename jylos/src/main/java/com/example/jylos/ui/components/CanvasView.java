package com.example.jylos.ui.components;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.CanvasModel;
import com.example.jylos.util.CanvasModel.CanvasEdge;
import com.example.jylos.util.CanvasModel.CanvasNode;
import com.example.jylos.util.SystemBrowser;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

/**
 * Viewer/editor for an Obsidian-compatible JSON Canvas ({@code .canvas}). Lays the
 * nodes out at their stored coordinates on an infinite, pannable/zoomable surface and
 * draws the edges between them; node content is rendered with native JavaFX controls
 * (lightweight Markdown for text/file nodes, the image for image nodes).
 *
 * <p>Phase 2 adds editing: drag a node to <b>move</b> it; <b>create</b>/<b>edit</b>/<b>delete</b>
 * text nodes; <b>connect</b> nodes with edges (connect mode) and delete a selected edge; plus a
 * toolbar to zoom/fit and <b>save</b>. Saving round-trips through {@link CanvasModel.Document}
 * so unknown fields are preserved.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
public final class CanvasView extends BorderPane {

    private static final java.util.logging.Logger logger = LoggerConfig.getLogger(CanvasView.class);

    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 4.0;
    private static final double ZOOM_STEP = 1.1;

    private final Pane world = new Pane();
    private final Group worldGroup = new Group(world);
    private final Scale scaleTransform = new Scale(1, 1, 0, 0);
    private final Translate panTransform = new Translate(0, 0);
    private final Pane viewport = new Pane(worldGroup);

    private final CanvasModel.Document document;
    private final Consumer<String> onOpenFile;
    private final Function<String, Path> resolveFile;
    private final Consumer<String> onSave;
    private final Function<String, String> i18n;

    /** Node id → its on-canvas box, used to drag nodes and re-anchor edges. */
    private final Map<String, Region> nodeBoxes = new HashMap<>();
    /** Drawn edges paired with their model, so they follow the nodes as they move. */
    private final List<EdgeLine> edgeLines = new ArrayList<>();

    private double dragAnchorX;
    private double dragAnchorY;
    private boolean fitted = false;
    private boolean dirty = false;
    private Button saveButton;
    private Button connectButton;
    /** Currently selected node id (for delete), or null. */
    private String selectedNodeId;
    /** Currently selected edge id (for delete), or null. */
    private String selectedEdgeId;
    /** True while a text node is being edited (suspends node dragging). */
    private boolean editing = false;
    /** True while in "connect" mode: click a source node then a target to draw an edge. */
    private boolean connecting = false;
    /** First node picked in connect mode, awaiting a target; null otherwise. */
    private String connectSourceId;

    private record EdgeLine(CanvasEdge edge, Line line) {
    }

    /**
     * @param resolveFile maps a node's {@code file} reference (vault-relative) to an
     *                    absolute path, or {@code null} when it cannot be resolved.
     * @param onSave      receives the serialized canvas JSON when the user saves; null
     *                    disables saving (pure viewer).
     */
    public CanvasView(CanvasModel.Document document, Consumer<String> onOpenFile,
            Function<String, Path> resolveFile, Consumer<String> onSave, Function<String, String> i18n) {
        this.document = document != null ? document : CanvasModel.Document.parse(null);
        this.onOpenFile = onOpenFile;
        this.resolveFile = resolveFile;
        this.onSave = onSave;
        this.i18n = i18n;

        getStyleClass().add("canvas-view");
        worldGroup.getTransforms().addAll(panTransform, scaleTransform);
        viewport.getStyleClass().add("canvas-viewport");
        viewport.setClip(buildClip());
        viewport.widthProperty().addListener((o, a, b) -> fitToContentOnce());
        viewport.heightProperty().addListener((o, a, b) -> fitToContentOnce());

        buildWorld();
        installPanZoom();
        setTop(buildToolbar());
        setCenter(viewport);

        // Delete removes the selected node (and its edges). Click the background to
        // deselect; the view must be focusable for key events to arrive.
        setFocusTraversable(true);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && connecting) {
                cancelConnect();
                e.consume();
            } else if ((e.getCode() == javafx.scene.input.KeyCode.DELETE
                    || e.getCode() == javafx.scene.input.KeyCode.BACK_SPACE)
                    && (selectedNodeId != null || selectedEdgeId != null)) {
                deleteSelected();
                e.consume();
            }
        });
    }

    // ── Toolbar ────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox bar = new HBox(6);
        bar.getStyleClass().add("graph-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 14));

        FontIcon icon = new FontIcon("fth-grid");
        icon.getStyleClass().add("feather-icon");
        Label title = new Label(tr("canvas.title", "Canvas"));
        title.getStyleClass().add("graph-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addText = iconButton("fth-plus", tr("canvas.add_text", "Add text node"), e -> addTextNode());
        connectButton = iconButton("fth-git-commit", tr("canvas.connect", "Connect nodes"), e -> toggleConnect());
        Button zoomOut = iconButton("fth-zoom-out", tr("canvas.zoom_out", "Zoom out"),
                e -> zoomAt(1 / ZOOM_STEP, viewport.getWidth() / 2, viewport.getHeight() / 2));
        Button zoomIn = iconButton("fth-zoom-in", tr("canvas.zoom_in", "Zoom in"),
                e -> zoomAt(ZOOM_STEP, viewport.getWidth() / 2, viewport.getHeight() / 2));
        Button fit = iconButton("fth-maximize", tr("canvas.fit", "Fit to content"), e -> { fitted = false; fit(); });

        bar.getChildren().addAll(icon, title, spacer, addText, connectButton, zoomOut, zoomIn, fit);

        if (onSave != null) {
            saveButton = iconButton("fth-save", tr("canvas.save", "Save"), e -> save());
            saveButton.setDisable(true);
            bar.getChildren().add(saveButton);
        }
        return bar;
    }

    private void save() {
        if (onSave == null || !dirty) {
            return;
        }
        onSave.accept(document.toJson());
        dirty = false;
        if (saveButton != null) {
            saveButton.setDisable(true);
        }
    }

    private void markDirty() {
        dirty = true;
        if (saveButton != null) {
            saveButton.setDisable(false);
        }
    }

    // ── World construction ───────────────────────────────────────────────────

    /** Re-renders the whole canvas from the (mutated) document, keeping the current pan/zoom. */
    private void rebuild() {
        selectedNodeId = null;
        selectedEdgeId = null;
        connectSourceId = null;
        buildWorld();
    }

    private void buildWorld() {
        world.getChildren().clear();
        nodeBoxes.clear();
        edgeLines.clear();
        for (CanvasNode n : document.nodes()) {
            if ("group".equals(n.type())) {
                Region box = groupNode(n);
                nodeBoxes.put(n.id(), box);
                world.getChildren().add(box);
                installNodeInteraction(box, n);
            }
        }
        for (CanvasEdge e : document.edges()) {
            Line line = new Line();
            line.getStyleClass().add("canvas-edge");
            String color = hexColor(e.color());
            if (color != null) {
                line.setStyle("-fx-stroke: " + color + ";");
            }
            final String edgeId = e.id();
            line.setOnMouseClicked(ev -> {
                requestFocus();
                selectEdge(edgeId);
                ev.consume();
            });
            edgeLines.add(new EdgeLine(e, line));
            world.getChildren().add(line);
            if (!e.label().isEmpty()) {
                world.getChildren().add(edgeLabel(e));
            }
        }
        for (CanvasNode n : document.nodes()) {
            if (!"group".equals(n.type())) {
                Region box = contentNode(n);
                nodeBoxes.put(n.id(), box);
                world.getChildren().add(box);
                installNodeInteraction(box, n);
            }
        }
        refreshEdges();
        if (document.isEmpty()) {
            Label empty = new Label(tr("canvas.empty", "This canvas is empty."));
            empty.getStyleClass().add("canvas-empty");
            empty.relocate(40, 40);
            world.getChildren().add(empty);
        }
    }

    private Region groupNode(CanvasNode n) {
        VBox box = new VBox();
        box.getStyleClass().add("canvas-group");
        applyColor(box, n.color());
        place(box, n);
        if (!n.label().isEmpty()) {
            Label label = new Label(n.label());
            label.getStyleClass().add("canvas-group-label");
            box.getChildren().add(label);
        }
        return box;
    }

    private Region contentNode(CanvasNode n) {
        VBox box = new VBox(6);
        box.getStyleClass().add("canvas-node");
        box.setPadding(new Insets(10));
        applyColor(box, n.color());
        place(box, n);

        switch (n.type()) {
            case "file" -> {
                box.getStyleClass().add("canvas-node-file");
                Path path = resolveFile != null && !n.file().isEmpty() ? resolveFile.apply(n.file()) : null;
                if (path != null && AttachmentType.fromName(path.toString()) == AttachmentType.IMAGE) {
                    box.getChildren().add(imageContent(path, n));
                } else {
                    Label name = new Label(baseName(n.file()));
                    name.getStyleClass().add("canvas-node-title");
                    name.setWrapText(true);
                    box.getChildren().add(name);
                    String body = path != null ? noteBodyPreview(path) : "";
                    if (!body.isEmpty()) {
                        box.getChildren().add(scrollable(com.example.jylos.util.MarkdownMini.render(body)));
                    }
                }
            }
            case "link" -> {
                box.getStyleClass().add("canvas-node-link");
                Label url = new Label(n.url());
                url.getStyleClass().add("canvas-node-link-url");
                url.setWrapText(true);
                box.getChildren().add(url);
            }
            default -> box.getChildren().add(scrollable(com.example.jylos.util.MarkdownMini.render(n.text())));
        }
        return box;
    }

    /** Wraps node content so a long note/text can be scrolled (wheel) without zooming the canvas. */
    private ScrollPane scrollable(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("canvas-node-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private Label edgeLabel(CanvasEdge e) {
        Label label = new Label(e.label());
        label.getStyleClass().add("canvas-edge-label");
        // positioned in refreshEdges()
        label.setUserData(e.id());
        return label;
    }

    private void refreshEdges() {
        for (EdgeLine el : edgeLines) {
            Region from = nodeBoxes.get(el.edge().fromNode());
            Region to = nodeBoxes.get(el.edge().toNode());
            if (from == null || to == null) {
                el.line().setVisible(false);
                continue;
            }
            double[] a = anchor(from, el.edge().fromSide());
            double[] b = anchor(to, el.edge().toSide());
            el.line().setVisible(true);
            el.line().setStartX(a[0]);
            el.line().setStartY(a[1]);
            el.line().setEndX(b[0]);
            el.line().setEndY(b[1]);
        }
        // Re-position any edge labels at the midpoint of their edge.
        for (Node node : world.getChildren()) {
            if (node instanceof Label label && label.getStyleClass().contains("canvas-edge-label")
                    && label.getUserData() instanceof String edgeId) {
                edgeLines.stream().filter(el -> edgeId.equals(el.edge().id())).findFirst().ifPresent(el -> {
                    Region from = nodeBoxes.get(el.edge().fromNode());
                    Region to = nodeBoxes.get(el.edge().toNode());
                    if (from != null && to != null) {
                        double[] a = anchor(from, el.edge().fromSide());
                        double[] b = anchor(to, el.edge().toSide());
                        label.relocate((a[0] + b[0]) / 2.0 - 20, (a[1] + b[1]) / 2.0 - 10);
                    }
                });
            }
        }
    }

    /** Anchor point on a node box's border for the given side (centre when unknown). */
    private static double[] anchor(Region box, String side) {
        double x = box.getLayoutX();
        double y = box.getLayoutY();
        double w = box.getPrefWidth();
        double h = box.getPrefHeight();
        return switch (side) {
            case "top" -> new double[] {x + w / 2, y};
            case "bottom" -> new double[] {x + w / 2, y + h};
            case "left" -> new double[] {x, y + h / 2};
            case "right" -> new double[] {x + w, y + h / 2};
            default -> new double[] {x + w / 2, y + h / 2};
        };
    }

    private void place(Region box, CanvasNode n) {
        double w = n.width() > 0 ? n.width() : 200;
        double h = n.height() > 0 ? n.height() : 100;
        box.setMinSize(w, h);
        box.setPrefSize(w, h);
        box.setMaxSize(w, h);
        box.relocate(n.x(), n.y());
        Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        box.setClip(clip);
    }

    // ── Node interaction (drag to move, click to select, double-click to act) ──

    private void installNodeInteraction(Region box, CanvasNode node) {
        final String id = node.id();
        final double[] last = new double[2];
        final boolean[] moved = {false};
        box.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();
            moved[0] = false;
        });
        box.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (editing || connecting) {
                return; // editing: allow text selection; connecting: clicks pick endpoints
            }
            double s = scaleTransform.getX();
            double dx = (e.getSceneX() - last[0]) / s;
            double dy = (e.getSceneY() - last[1]) / s;
            box.relocate(box.getLayoutX() + dx, box.getLayoutY() + dy);
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();
            moved[0] = true;
            refreshEdges();
            e.consume();
        });
        box.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (moved[0]) {
                document.moveNode(id, box.getLayoutX(), box.getLayoutY());
                markDirty();
                e.consume();
            }
        });
        box.setOnMouseClicked(e -> {
            requestFocus();
            if (connecting) {
                handleConnectClick(id);
                e.consume();
                return;
            }
            if (e.getClickCount() >= 2) {
                switch (node.type()) {
                    case "file" -> {
                        if (onOpenFile != null && !node.file().isEmpty()) {
                            onOpenFile.accept(node.file());
                        }
                    }
                    case "link" -> {
                        if (!node.url().isEmpty()) {
                            SystemBrowser.open(node.url());
                        }
                    }
                    case "group" -> { /* nothing to open */ }
                    default -> editTextNode(id);
                }
            } else {
                select(id);
            }
        });
    }

    // ── Selection / create / edit / delete ─────────────────────────────────

    /** Selects a node (highlight) or clears the selection when {@code id} is null. */
    private void select(String id) {
        clearEdgeSelection();
        if (selectedNodeId != null && nodeBoxes.containsKey(selectedNodeId)) {
            nodeBoxes.get(selectedNodeId).getStyleClass().remove("canvas-selected");
        }
        selectedNodeId = id;
        if (id != null && nodeBoxes.containsKey(id)) {
            Region box = nodeBoxes.get(id);
            if (!box.getStyleClass().contains("canvas-selected")) {
                box.getStyleClass().add("canvas-selected");
            }
        }
    }

    /** Selects an edge (highlight) for deletion, clearing any node selection. */
    private void selectEdge(String id) {
        if (selectedNodeId != null) {
            select(null);
        }
        clearEdgeSelection();
        selectedEdgeId = id;
        edgeLines.stream().filter(el -> id != null && id.equals(el.edge().id())).findFirst()
                .ifPresent(el -> el.line().getStyleClass().add("canvas-edge-selected"));
    }

    private void clearEdgeSelection() {
        if (selectedEdgeId != null) {
            edgeLines.stream().filter(el -> selectedEdgeId.equals(el.edge().id())).findFirst()
                    .ifPresent(el -> el.line().getStyleClass().remove("canvas-edge-selected"));
        }
        selectedEdgeId = null;
    }

    /** Deletes the current selection — an edge if one is selected, otherwise the selected node. */
    private void deleteSelected() {
        if (selectedEdgeId != null) {
            document.removeEdge(selectedEdgeId);
            markDirty();
            rebuild();
            return;
        }
        if (selectedNodeId != null) {
            document.removeNode(selectedNodeId);
            markDirty();
            rebuild();
        }
    }

    // ── Connect mode (draw edges) ──────────────────────────────────────────

    /** Toggles connect mode: click a source node, then a target, to draw an edge. */
    private void toggleConnect() {
        connecting = !connecting;
        connectSourceId = null;
        select(null);
        if (connectButton != null) {
            connectButton.getStyleClass().remove("toolbar-btn-active");
            if (connecting) {
                connectButton.getStyleClass().add("toolbar-btn-active");
            }
        }
        viewport.setCursor(connecting ? javafx.scene.Cursor.CROSSHAIR : javafx.scene.Cursor.DEFAULT);
        rebuildHighlights();
    }

    private void cancelConnect() {
        if (!connecting) {
            return;
        }
        connecting = false;
        connectSourceId = null;
        if (connectButton != null) {
            connectButton.getStyleClass().remove("toolbar-btn-active");
        }
        viewport.setCursor(javafx.scene.Cursor.DEFAULT);
        rebuildHighlights();
    }

    /** In connect mode, the first click picks the source node; the second draws the edge. */
    private void handleConnectClick(String id) {
        if (connectSourceId == null) {
            connectSourceId = id;
            rebuildHighlights();
            return;
        }
        if (!connectSourceId.equals(id)) {
            String[] sides = bestSides(connectSourceId, id);
            document.addEdge(connectSourceId, sides[0], id, sides[1]);
            markDirty();
        }
        connectSourceId = null;
        rebuild(); // redraw with the new edge (also clears the source highlight)
    }

    /** Clears node highlights and re-applies the connect-source one (without changing the selection). */
    private void rebuildHighlights() {
        for (Region box : nodeBoxes.values()) {
            box.getStyleClass().remove("canvas-selected");
        }
        if (connectSourceId != null && nodeBoxes.containsKey(connectSourceId)) {
            nodeBoxes.get(connectSourceId).getStyleClass().add("canvas-selected");
        } else if (selectedNodeId != null && nodeBoxes.containsKey(selectedNodeId)) {
            nodeBoxes.get(selectedNodeId).getStyleClass().add("canvas-selected");
        }
    }

    /** Picks the pair of sides (from, to) facing each other, based on node centres. */
    private String[] bestSides(String fromId, String toId) {
        Region from = nodeBoxes.get(fromId);
        Region to = nodeBoxes.get(toId);
        if (from == null || to == null) {
            return new String[] {"right", "left"};
        }
        double fcx = from.getLayoutX() + from.getPrefWidth() / 2;
        double fcy = from.getLayoutY() + from.getPrefHeight() / 2;
        double tcx = to.getLayoutX() + to.getPrefWidth() / 2;
        double tcy = to.getLayoutY() + to.getPrefHeight() / 2;
        double dx = tcx - fcx;
        double dy = tcy - fcy;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? new String[] {"right", "left"} : new String[] {"left", "right"};
        }
        return dy >= 0 ? new String[] {"bottom", "top"} : new String[] {"top", "bottom"};
    }

    /** Adds a text node at the centre of the current view and opens it for editing. */
    private void addTextNode() {
        double s = scaleTransform.getX();
        double cx = (viewport.getWidth() / 2 - panTransform.getX()) / s;
        double cy = (viewport.getHeight() / 2 - panTransform.getY()) / s;
        String id = document.addTextNode(cx - 125, cy - 60, 250, 120, "");
        markDirty();
        rebuild();
        editTextNode(id);
    }

    /** Replaces a text node's content with an editor; commits on focus loss / Cmd+Enter, cancels on Esc. */
    private void editTextNode(String id) {
        Region box = nodeBoxes.get(id);
        CanvasNode node = document.nodes().stream().filter(n -> id.equals(n.id())).findFirst().orElse(null);
        if (!(box instanceof VBox vbox) || node == null) {
            return;
        }
        editing = true;
        select(id);
        TextArea editor = new TextArea(node.text());
        editor.getStyleClass().add("canvas-node-editor");
        editor.setWrapText(true);
        VBox.setVgrow(editor, Priority.ALWAYS);
        vbox.getChildren().setAll(editor);
        editor.requestFocus();

        final boolean[] committed = {false};
        Runnable commit = () -> {
            if (committed[0]) {
                return;
            }
            committed[0] = true;
            editing = false;
            document.setNodeText(id, editor.getText());
            markDirty();
            rebuild();
        };
        editor.focusedProperty().addListener((o, was, is) -> {
            if (Boolean.FALSE.equals(is)) {
                commit.run();
            }
        });
        editor.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                committed[0] = true;
                editing = false;
                rebuild();
                ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ENTER && ev.isShortcutDown()) {
                commit.run();
                ev.consume();
            }
        });
    }

    // ── Pan / zoom ─────────────────────────────────────────────────────────

    private void installPanZoom() {
        viewport.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? ZOOM_STEP : 1 / ZOOM_STEP;
            zoomAt(factor, e.getX(), e.getY());
            e.consume();
        });
        viewport.setOnMousePressed(e -> {
            dragAnchorX = e.getX() - panTransform.getX();
            dragAnchorY = e.getY() - panTransform.getY();
            requestFocus();
            select(null); // pressing the background clears the selection
            if (connecting && connectSourceId != null) {
                connectSourceId = null; // abandon the in-progress connection
                rebuildHighlights();
            }
        });
        viewport.setOnMouseDragged(e -> {
            panTransform.setX(e.getX() - dragAnchorX);
            panTransform.setY(e.getY() - dragAnchorY);
        });
    }

    private void zoomAt(double factor, double screenX, double screenY) {
        double oldScale = scaleTransform.getX();
        double newScale = clamp(oldScale * factor, MIN_SCALE, MAX_SCALE);
        if (newScale == oldScale) {
            return;
        }
        double wx = (screenX - panTransform.getX()) / oldScale;
        double wy = (screenY - panTransform.getY()) / oldScale;
        scaleTransform.setX(newScale);
        scaleTransform.setY(newScale);
        panTransform.setX(screenX - wx * newScale);
        panTransform.setY(screenY - wy * newScale);
    }

    private void fitToContentOnce() {
        if (!fitted) {
            fit();
        }
    }

    /** Centres and scales the canvas to fit the viewport. */
    private void fit() {
        if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0) {
            return;
        }
        Bounds b = world.getLayoutBounds();
        if (b.getWidth() <= 0 || b.getHeight() <= 0) {
            return;
        }
        fitted = true;
        double margin = 40;
        double sx = (viewport.getWidth() - margin) / b.getWidth();
        double sy = (viewport.getHeight() - margin) / b.getHeight();
        double s = clamp(Math.min(sx, sy), MIN_SCALE, 1.0);
        scaleTransform.setX(s);
        scaleTransform.setY(s);
        panTransform.setX((viewport.getWidth() - b.getWidth() * s) / 2.0 - b.getMinX() * s);
        panTransform.setY((viewport.getHeight() - b.getHeight() * s) / 2.0 - b.getMinY() * s);
    }

    // ── Node content helpers ───────────────────────────────────────────────

    private ImageView imageContent(Path path, CanvasNode n) {
        ImageView view = new ImageView(new Image(path.toUri().toString(), true));
        view.setPreserveRatio(true);
        view.setSmooth(true);
        double w = (n.width() > 0 ? n.width() : 200) - 20;
        double h = (n.height() > 0 ? n.height() : 100) - 20;
        view.setFitWidth(Math.max(1, w));
        view.setFitHeight(Math.max(1, h));
        return view;
    }

    private String noteBodyPreview(Path path) {
        if (AttachmentType.fromName(path.toString()) != AttachmentType.MARKDOWN) {
            return "";
        }
        try {
            return stripFrontmatter(Files.readString(path)).strip();
        } catch (Exception e) {
            logger.fine("Could not read canvas file-node preview '" + path + "': " + e.getMessage());
            return "";
        }
    }

    private static String stripFrontmatter(String content) {
        if (content == null) {
            return "";
        }
        String c = content.stripLeading();
        if (c.startsWith("---")) {
            int end = c.indexOf("\n---", 3);
            if (end >= 0) {
                int nl = c.indexOf('\n', end + 1);
                return nl >= 0 ? c.substring(nl + 1) : "";
            }
        }
        return content;
    }

    // ── Misc helpers ────────────────────────────────────────────────────────

    private Rectangle buildClip() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        return clip;
    }

    private void applyColor(Region box, String color) {
        String hex = hexColor(color);
        if (hex != null) {
            box.setStyle("-fx-border-color: " + hex + "; -fx-border-width: 0 0 0 4;");
        }
    }

    private static String hexColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        return switch (color) {
            case "1" -> "#e93147";
            case "2" -> "#ec7500";
            case "3" -> "#e0ac00";
            case "4" -> "#08b94e";
            case "5" -> "#00bfbc";
            case "6" -> "#7852ee";
            default -> color.startsWith("#") ? color : null;
        };
    }

    private static String baseName(String file) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        String f = file.replace('\\', '/');
        int slash = f.lastIndexOf('/');
        String name = slash >= 0 ? f.substring(slash + 1) : f;
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private Button iconButton(String iconLiteral, String tip, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        FontIcon fi = new FontIcon(iconLiteral);
        fi.getStyleClass().add("feather-icon");
        fi.setIconSize(16);
        Button b = new Button();
        b.setGraphic(fi);
        b.getStyleClass().add("toolbar-btn");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(action);
        return b;
    }

    private String tr(String key, String fallback) {
        if (i18n == null) {
            return fallback;
        }
        String v = i18n.apply(key);
        return v == null || v.equals(key) ? fallback : v;
    }
}
