package com.example.jylos.ui.components;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
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
    private static final int MAX_EMBED_DEPTH = 3;
    private static final Pattern EMBED_TOKEN = Pattern.compile("!\\[\\[(.+?)]]");
    private static final Pattern LINK_TRIGGER = Pattern.compile("(!?)\\[\\[([^\\]\\[\\n]*)$");
    private static final String INTERACTIVE_CONTENT_KEY = "canvasInteractiveContent";

    private final Pane world = new Pane();
    private final Group worldGroup = new Group(world);
    private final Scale scaleTransform = new Scale(1, 1, 0, 0);
    private final Translate panTransform = new Translate(0, 0);
    private final Pane viewport = new Pane(worldGroup);

    private final CanvasModel.Document document;
    private final Consumer<String> onOpenFile;
    private final Function<String, Path> resolveFile;
    private final Function<String, String> canonicalizeFileRef;
    private final Function<String, String> noteIdToCanvasRef;
    private final Supplier<List<String>> noteTitleSuggestions;
    private final Supplier<List<String>> fileReferenceSuggestions;
    private final Consumer<String> onSave;
    private final Runnable onDirty;
    private final Function<String, String> i18n;

    /** Node id → its on-canvas box, used to drag nodes and re-anchor edges. */
    private final Map<String, Region> nodeBoxes = new HashMap<>();
    /** Drawn edges paired with their model, so they follow the nodes as they move. */
    private final List<EdgeLine> edgeLines = new ArrayList<>();
    /** A single bottom-right resize grip shown over the selected node. */
    private final Region resizeHandle = new Region();
    private static final double MIN_NODE_W = 80;
    private static final double MIN_NODE_H = 50;

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
    private Popup autocompletePopup;
    private ListView<String> autocompleteList;
    private TextInputControl autocompleteOwner;
    private Supplier<List<String>> autocompleteSuggestions = List::of;
    private String autocompletePrefix = "[[";

    private record EdgeLine(CanvasEdge edge, Line line, javafx.scene.shape.Polygon arrow) {
    }

    /**
     * @param resolveFile maps a node's {@code file} reference (vault-relative) to an
     *                    absolute path, or {@code null} when it cannot be resolved.
     * @param onSave      receives the serialized canvas JSON when the user saves; null
     *                    disables saving (pure viewer).
     */
    public CanvasView(CanvasModel.Document document, Consumer<String> onOpenFile,
            Function<String, Path> resolveFile, Function<String, String> canonicalizeFileRef,
            Function<String, String> noteIdToCanvasRef,
            Supplier<List<String>> noteTitleSuggestions,
            Supplier<List<String>> fileReferenceSuggestions,
            Consumer<String> onSave, Runnable onDirty,
            Function<String, String> i18n) {
        this.document = document != null ? document : CanvasModel.Document.parse(null);
        this.onOpenFile = onOpenFile;
        this.resolveFile = resolveFile;
        this.canonicalizeFileRef = canonicalizeFileRef;
        this.noteIdToCanvasRef = noteIdToCanvasRef;
        this.noteTitleSuggestions = noteTitleSuggestions != null ? noteTitleSuggestions : List::of;
        this.fileReferenceSuggestions = fileReferenceSuggestions != null ? fileReferenceSuggestions : List::of;
        this.onSave = onSave;
        this.onDirty = onDirty;
        this.i18n = i18n;

        getStyleClass().add("canvas-view");
        worldGroup.getTransforms().addAll(panTransform, scaleTransform);
        viewport.getStyleClass().add("canvas-viewport");
        viewport.setClip(buildClip());
        viewport.widthProperty().addListener((o, a, b) -> fitToContentOnce());
        viewport.heightProperty().addListener((o, a, b) -> fitToContentOnce());

        initResizeHandle();
        buildWorld();
        installPanZoom();
        setTop(buildToolbar());
        setCenter(viewport);

        // Delete removes the selected node (and its edges). Click the background to
        // deselect; the view must be focusable for key events to arrive.
        setFocusTraversable(true);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (editing || isTextInputTarget(e.getTarget()) || hasTextInputFocus()) {
                return;
            }
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
        Button addFile = iconButton("fth-file", tr("canvas.add_file", "Add file node"), e -> addFileNode());
        Button addLink = iconButton("fth-link", tr("canvas.add_link", "Add link node"), e -> addLinkNode());
        Button addGroup = iconButton("fth-square", tr("canvas.add_group", "Add group"), e -> addGroupNode());
        connectButton = iconButton("fth-git-commit", tr("canvas.connect", "Connect nodes"), e -> toggleConnect());
        Button zoomOut = iconButton("fth-zoom-out", tr("canvas.zoom_out", "Zoom out"),
                e -> zoomAt(1 / ZOOM_STEP, viewport.getWidth() / 2, viewport.getHeight() / 2));
        Button zoomIn = iconButton("fth-zoom-in", tr("canvas.zoom_in", "Zoom in"),
                e -> zoomAt(ZOOM_STEP, viewport.getWidth() / 2, viewport.getHeight() / 2));
        Button fit = iconButton("fth-maximize", tr("canvas.fit", "Fit to content"), e -> { fitted = false; fit(); });

        bar.getChildren().addAll(icon, title, spacer, addText, addFile, addLink, addGroup, connectButton, zoomOut, zoomIn, fit);

        if (onSave != null) {
            saveButton = iconButton("fth-save", tr("canvas.save", "Save"), e -> save());
            saveButton.setDisable(true);
            bar.getChildren().add(saveButton);
        }
        return bar;
    }

    public void save() {
        if (onSave == null) {
            return;
        }
        onSave.accept(document.toJson());
        markSaved();
    }

    private void markDirty() {
        boolean wasDirty = dirty;
        dirty = true;
        if (saveButton != null) {
            saveButton.setDisable(false);
        }
        if (!wasDirty && onDirty != null) {
            onDirty.run();
        }
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    public String serialize() {
        return document.toJson();
    }

    public void markSaved() {
        dirty = false;
        if (saveButton != null) {
            saveButton.setDisable(true);
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
            javafx.scene.shape.Polygon arrow = new javafx.scene.shape.Polygon();
            arrow.getStyleClass().add("canvas-edge-arrow");
            String color = hexColor(e.color());
            if (color != null) {
                line.setStyle("-fx-stroke: " + color + ";");
                arrow.setStyle("-fx-fill: " + color + ";");
            }
            final String edgeId = e.id();
            javafx.event.EventHandler<MouseEvent> pick = ev -> {
                requestFocus();
                selectEdge(edgeId);
                if (ev.getClickCount() >= 2) {
                    editEdgeLabel(edgeId);
                }
                ev.consume();
            };
            line.setOnMouseClicked(pick);
            arrow.setOnMouseClicked(pick);
            line.setOnContextMenuRequested(ev -> {
                selectEdge(edgeId);
                elementMenu(
                        c -> { document.setEdgeColor(edgeId, c); markDirty(); rebuild(); },
                        () -> { document.removeEdge(edgeId); markDirty(); rebuild(); },
                        edgeLabelMenuItem(edgeId))
                        .show(line, ev.getScreenX(), ev.getScreenY());
                ev.consume();
            });
            edgeLines.add(new EdgeLine(e, line, arrow));
            world.getChildren().addAll(line, arrow);
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
        // The translucent fill makes the whole area visible and grabbable (a transparent
        // region only picks on its painted border), so a group can be dragged/selected.
        box.setPickOnBounds(true);
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
                        box.getChildren().add(scrollable(renderMarkdownWithEmbeds(body), true));
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
            default -> box.getChildren().add(scrollable(renderMarkdownWithEmbeds(n.text()), false));
        }
        return box;
    }

    /** Wraps node content so a long note/text can be scrolled (wheel) without zooming the canvas. */
    private ScrollPane scrollable(Node content, boolean interactive) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("canvas-node-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        if (interactive) {
            markInteractive(scroll);
        }
        installEmbeddedScrollIsolation(scroll);
        return scroll;
    }

    private Region renderMarkdownWithEmbeds(String markdown) {
        return renderMarkdownWithEmbeds(markdown, 0, new HashSet<>());
    }

    private Region renderMarkdownWithEmbeds(String markdown, int depth, Set<String> visitedTargets) {
        VBox container = new VBox(8);
        container.getStyleClass().add("canvas-embed-stack");
        if (markdown == null || markdown.isBlank()) {
            return container;
        }

        StringBuilder markdownBlock = new StringBuilder();
        Matcher matcher = EMBED_TOKEN.matcher(markdown);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                markdownBlock.append(markdown, last, matcher.start());
            }
            appendMarkdownBlock(container, markdownBlock);
            markdownBlock.setLength(0);
            container.getChildren().add(renderEmbedReference(matcher.group(1), depth, visitedTargets));
            last = matcher.end();
        }
        if (last < markdown.length()) {
            markdownBlock.append(markdown.substring(last));
        }
        appendMarkdownBlock(container, markdownBlock);
        return container;
    }

    private void appendMarkdownBlock(VBox container, StringBuilder markdownBlock) {
        if (markdownBlock == null || markdownBlock.isEmpty() || markdownBlock.toString().isBlank()) {
            return;
        }
        container.getChildren().add(com.example.jylos.util.MarkdownMini.render(markdownBlock.toString()));
    }

    private Node renderEmbedReference(String rawTarget, int depth, Set<String> visitedTargets) {
        String reference = embedReference(rawTarget);
        if (reference.isBlank()) {
            return embedNotice(tr("canvas.embed_missing", "Embedded item not found"));
        }
        if (depth >= MAX_EMBED_DEPTH) {
            return embedNotice(tr("canvas.embed_too_deep", "Embedded content nested too deep"));
        }

        Path path = resolveCanvasReference(reference);
        if (path == null) {
            return embedNotice(tr("canvas.embed_missing", "Embedded item not found") + ": " + reference);
        }

        String normalizedTarget = path.toAbsolutePath().normalize().toString();
        if (!visitedTargets.add(normalizedTarget)) {
            return embedNotice(tr("canvas.embed_cycle", "Circular embed"));
        }

        AttachmentType type = AttachmentType.fromName(path.toString());
        Node content = switch (type) {
            case IMAGE -> embeddedImage(path);
            case MARKDOWN -> embeddedMarkdown(path, depth, visitedTargets);
            default -> embedNotice(baseName(reference));
        };
        visitedTargets.remove(normalizedTarget);

        VBox wrapper = new VBox(6);
        wrapper.getStyleClass().add("canvas-embed");
        Label title = new Label(baseName(reference));
        title.getStyleClass().add("canvas-embed-title");
        title.setWrapText(true);
        title.setOnMouseClicked(e -> {
            if (onOpenFile != null) {
                onOpenFile.accept(reference);
            }
            e.consume();
        });
        markInteractive(title);
        wrapper.getChildren().addAll(title, content);
        return wrapper;
    }

    private Node embeddedMarkdown(Path path, int depth, Set<String> visitedTargets) {
        try {
            String body = stripFrontmatter(Files.readString(path)).strip();
            return renderMarkdownWithEmbeds(body, depth + 1, visitedTargets);
        } catch (Exception e) {
            logger.fine("Could not read embedded canvas note '" + path + "': " + e.getMessage());
            return embedNotice(tr("canvas.embed_missing", "Embedded item not found"));
        }
    }

    private Node embeddedImage(Path path) {
        ImageView view = new ImageView(new Image(path.toUri().toString(), true));
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setFitWidth(260);
        VBox box = new VBox(view);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("canvas-embed-image");
        markInteractive(box);
        return box;
    }

    private Label embedNotice(String message) {
        Label notice = new Label(message);
        notice.getStyleClass().add("canvas-embed-notice");
        notice.setWrapText(true);
        return notice;
    }

    private Path resolveCanvasReference(String reference) {
        if (resolveFile == null || reference == null || reference.isBlank()) {
            return null;
        }
        Path resolved = resolveFile.apply(reference);
        if (resolved == null && !reference.endsWith(".md")) {
            resolved = resolveFile.apply(reference + ".md");
        }
        return resolved;
    }

    private static String embedReference(String rawTarget) {
        if (rawTarget == null) {
            return "";
        }
        String target = rawTarget.trim();
        if (target.startsWith("![[") && target.endsWith("]]")) {
            target = target.substring(3, target.length() - 2).trim();
        } else if (target.startsWith("[[") && target.endsWith("]]")) {
            target = target.substring(2, target.length() - 2).trim();
        }
        int alias = target.indexOf('|');
        if (alias >= 0) {
            target = target.substring(0, alias);
        }
        int heading = target.indexOf('#');
        if (heading >= 0) {
            target = target.substring(0, heading);
        }
        return target.trim();
    }

    private String canonicalizeFileReference(String input) {
        String normalized = embedReference(input);
        if (normalized.isBlank()) {
            return "";
        }
        if (canonicalizeFileRef != null) {
            String canonical = canonicalizeFileRef.apply(normalized);
            if (canonical != null && !canonical.isBlank()) {
                return canonical;
            }
        }
        return normalized;
    }

    private Label edgeLabel(CanvasEdge e) {
        Label label = new Label(e.label());
        label.getStyleClass().add("canvas-edge-label");
        // positioned in refreshEdges()
        label.setUserData(e.id());
        label.setOnMouseClicked(event -> {
            requestFocus();
            selectEdge(e.id());
            if (event.getClickCount() >= 2) {
                editEdgeLabel(e.id());
            }
            event.consume();
        });
        label.setOnContextMenuRequested(event -> {
            selectEdge(e.id());
            elementMenu(
                    c -> { document.setEdgeColor(e.id(), c); markDirty(); rebuild(); },
                    () -> { document.removeEdge(e.id()); markDirty(); rebuild(); },
                    edgeLabelMenuItem(e.id()))
                    .show(label, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return label;
    }

    private javafx.scene.control.MenuItem edgeLabelMenuItem(String edgeId) {
        javafx.scene.control.MenuItem item =
                new javafx.scene.control.MenuItem(tr("canvas.edge_label", "Edit label"));
        item.setOnAction(e -> editEdgeLabel(edgeId));
        return item;
    }

    private void editEdgeLabel(String edgeId) {
        CanvasEdge edge = document.edges().stream()
                .filter(candidate -> edgeId.equals(candidate.id()))
                .findFirst()
                .orElse(null);
        if (edge == null) {
            return;
        }
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(edge.label());
        dialog.setTitle(tr("canvas.edge_label", "Edit label"));
        dialog.setHeaderText(null);
        dialog.setContentText(tr("canvas.edge_label_value", "Label:"));
        com.example.jylos.ui.UiDialogs.apply(dialog);
        String value = com.example.jylos.ui.UiDialogs.show(dialog).orElse(null);
        if (value == null) {
            return;
        }
        document.setEdgeLabel(edgeId, value.trim());
        markDirty();
        rebuild();
    }

    private void refreshEdges() {
        for (EdgeLine el : edgeLines) {
            Region from = nodeBoxes.get(el.edge().fromNode());
            Region to = nodeBoxes.get(el.edge().toNode());
            if (from == null || to == null) {
                el.line().setVisible(false);
                el.arrow().setVisible(false);
                continue;
            }
            double[] a = anchor(from, el.edge().fromSide());
            double[] b = anchor(to, el.edge().toSide());
            el.line().setVisible(true);
            el.line().setStartX(a[0]);
            el.line().setStartY(a[1]);
            el.line().setEndX(b[0]);
            el.line().setEndY(b[1]);
            updateArrow(el.arrow(), a[0], a[1], b[0], b[1]);
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

    /** Lays out the arrowhead triangle pointing at the target end {@code (bx,by)}. */
    private static void updateArrow(javafx.scene.shape.Polygon arrow, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len = Math.hypot(dx, dy);
        if (len < 1) {
            arrow.setVisible(false);
            return;
        }
        arrow.setVisible(true);
        double ux = dx / len;
        double uy = dy / len;
        final double size = 11;
        final double half = 5;
        double baseX = bx - ux * size;
        double baseY = by - uy * size;
        double px = -uy;
        double py = ux;
        arrow.getPoints().setAll(
                bx, by,
                baseX + px * half, baseY + py * half,
                baseX - px * half, baseY - py * half);
    }

    /**
     * Context menu for a node or edge: the Obsidian colour presets (1–6) plus "no colour",
     * and a <b>Delete</b> action. {@code setColor} applies/clears the colour; {@code delete}
     * removes the element.
     */
    private javafx.scene.control.ContextMenu elementMenu(Consumer<String> setColor, Runnable delete,
            javafx.scene.control.MenuItem... extra) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        String[][] colors = {
            {"1", tr("canvas.color.red", "Red")},
            {"2", tr("canvas.color.orange", "Orange")},
            {"3", tr("canvas.color.yellow", "Yellow")},
            {"4", tr("canvas.color.green", "Green")},
            {"5", tr("canvas.color.cyan", "Cyan")},
            {"6", tr("canvas.color.purple", "Purple")},
        };
        for (String[] c : colors) {
            Rectangle swatch = new Rectangle(12, 12);
            swatch.setArcWidth(3);
            swatch.setArcHeight(3);
            swatch.setStyle("-fx-fill: " + hexColor(c[0]) + ";");
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(c[1], swatch);
            item.setOnAction(e -> setColor.accept(c[0]));
            menu.getItems().add(item);
        }
        javafx.scene.control.MenuItem none = new javafx.scene.control.MenuItem(tr("canvas.color.none", "No colour"));
        none.setOnAction(e -> setColor.accept(null));

        FontIcon trashIcon = new FontIcon("fth-trash-2");
        trashIcon.getStyleClass().add("feather-icon");
        javafx.scene.control.MenuItem deleteItem =
                new javafx.scene.control.MenuItem(tr("canvas.delete", "Delete"), trashIcon);
        deleteItem.setOnAction(e -> delete.run());

        menu.getItems().addAll(new javafx.scene.control.SeparatorMenuItem(), none);
        if (extra != null && extra.length > 0) {
            menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            menu.getItems().addAll(extra);
        }
        menu.getItems().addAll(new javafx.scene.control.SeparatorMenuItem(), deleteItem);
        return menu;
    }

    // ── Groups: membership, label editing, member alignment (Obsidian-style) ──

    /** Content nodes whose centre lies inside the group's rectangle (groups don't nest here). */
    private List<String> groupMemberIds(String groupId) {
        List<String> out = new ArrayList<>();
        Region g = nodeBoxes.get(groupId);
        if (g == null) {
            return out;
        }
        double gx = g.getLayoutX();
        double gy = g.getLayoutY();
        double gw = g.getPrefWidth();
        double gh = g.getPrefHeight();
        for (CanvasNode n : document.nodes()) {
            if (n.id().equals(groupId) || "group".equals(n.type())) {
                continue;
            }
            Region b = nodeBoxes.get(n.id());
            if (b == null) {
                continue;
            }
            double cx = b.getLayoutX() + b.getPrefWidth() / 2;
            double cy = b.getLayoutY() + b.getPrefHeight() / 2;
            if (cx >= gx && cx <= gx + gw && cy >= gy && cy <= gy + gh) {
                out.add(n.id());
            }
        }
        return out;
    }

    /** Inline-edits a group's label; commits on Enter / focus loss, cancels on Esc. */
    private void editGroupLabel(String id) {
        Region box = nodeBoxes.get(id);
        CanvasNode node = document.nodes().stream().filter(n -> id.equals(n.id())).findFirst().orElse(null);
        if (!(box instanceof VBox vbox) || node == null) {
            return;
        }
        editing = true;
        select(id);
        javafx.scene.control.TextField field = new javafx.scene.control.TextField(node.label());
        field.getStyleClass().add("canvas-node-editor");
        field.setPromptText(tr("canvas.group_default", "Group"));
        markInteractive(field);
        vbox.getChildren().setAll(field);
        field.requestFocus();
        field.selectAll();

        final boolean[] committed = {false};
        Runnable commit = () -> {
            if (committed[0]) {
                return;
            }
            committed[0] = true;
            editing = false;
            document.setNodeLabel(id, field.getText().trim());
            markDirty();
            rebuild();
        };
        field.focusedProperty().addListener((o, was, is) -> {
            if (Boolean.FALSE.equals(is)) {
                commit.run();
            }
        });
        field.setOnAction(ev -> commit.run());
        field.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                committed[0] = true;
                editing = false;
                rebuild();
                ev.consume();
            }
        });
    }

    private enum AlignMode { LEFT, CENTER_H, RIGHT, TOP, CENTER_V, BOTTOM }

    /** Builds the "Align" submenu that lines up a group's member nodes. */
    private javafx.scene.control.Menu alignSubmenu(String groupId) {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu(tr("canvas.align", "Align"));
        addAlignItem(menu, groupId, AlignMode.LEFT, tr("canvas.align.left", "Left"));
        addAlignItem(menu, groupId, AlignMode.CENTER_H, tr("canvas.align.center_h", "Center horizontally"));
        addAlignItem(menu, groupId, AlignMode.RIGHT, tr("canvas.align.right", "Right"));
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        addAlignItem(menu, groupId, AlignMode.TOP, tr("canvas.align.top", "Top"));
        addAlignItem(menu, groupId, AlignMode.CENTER_V, tr("canvas.align.center_v", "Center vertically"));
        addAlignItem(menu, groupId, AlignMode.BOTTOM, tr("canvas.align.bottom", "Bottom"));
        return menu;
    }

    private void addAlignItem(javafx.scene.control.Menu menu, String groupId, AlignMode mode, String label) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
        item.setOnAction(e -> alignMembers(groupId, mode));
        menu.getItems().add(item);
    }

    /** Aligns a group's member nodes to their shared bounding box along {@code mode}. */
    private void alignMembers(String groupId, AlignMode mode) {
        List<String> members = groupMemberIds(groupId);
        if (members.size() < 2) {
            return;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (String m : members) {
            Region b = nodeBoxes.get(m);
            minX = Math.min(minX, b.getLayoutX());
            minY = Math.min(minY, b.getLayoutY());
            maxX = Math.max(maxX, b.getLayoutX() + b.getPrefWidth());
            maxY = Math.max(maxY, b.getLayoutY() + b.getPrefHeight());
        }
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        for (String m : members) {
            Region b = nodeBoxes.get(m);
            double x = b.getLayoutX();
            double y = b.getLayoutY();
            double w = b.getPrefWidth();
            double h = b.getPrefHeight();
            switch (mode) {
                case LEFT -> x = minX;
                case RIGHT -> x = maxX - w;
                case CENTER_H -> x = cx - w / 2;
                case TOP -> y = minY;
                case BOTTOM -> y = maxY - h;
                case CENTER_V -> y = cy - h / 2;
            }
            document.moveNode(m, x, y);
        }
        markDirty();
        rebuild();
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
        final boolean isGroup = "group".equals(node.type());
        final double[] last = new double[2];
        final boolean[] moved = {false};
        // Nodes whose centre lies inside this group when the drag starts: they travel with it.
        final List<String> dragMembers = new ArrayList<>();
        box.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (isInteractiveTarget(e.getTarget())) {
                return;
            }
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();
            moved[0] = false;
            dragMembers.clear();
            if (isGroup) {
                dragMembers.addAll(groupMemberIds(id));
            }
        });
        box.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (editing || connecting || isInteractiveTarget(e.getTarget())) {
                return; // editing: allow text selection; connecting: clicks pick endpoints
            }
            double s = scaleTransform.getX();
            double dx = (e.getSceneX() - last[0]) / s;
            double dy = (e.getSceneY() - last[1]) / s;
            box.relocate(box.getLayoutX() + dx, box.getLayoutY() + dy);
            for (String m : dragMembers) {
                Region mb = nodeBoxes.get(m);
                if (mb != null) {
                    mb.relocate(mb.getLayoutX() + dx, mb.getLayoutY() + dy);
                }
            }
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();
            moved[0] = true;
            refreshEdges();
            if (id.equals(selectedNodeId)) {
                positionResizeHandle(box);
            }
            e.consume();
        });
        box.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (isInteractiveTarget(e.getTarget())) {
                return;
            }
            if (moved[0]) {
                document.moveNode(id, box.getLayoutX(), box.getLayoutY());
                for (String m : dragMembers) {
                    Region mb = nodeBoxes.get(m);
                    if (mb != null) {
                        document.moveNode(m, mb.getLayoutX(), mb.getLayoutY());
                    }
                }
                markDirty();
                e.consume();
            }
        });
        box.setOnMouseClicked(e -> {
            if (isInteractiveTarget(e.getTarget())) {
                return;
            }
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
                    case "group" -> editGroupLabel(id);
                    default -> editTextNode(id);
                }
            } else {
                select(id);
            }
        });
        box.setOnContextMenuRequested(e -> {
            select(id);
            javafx.scene.control.ContextMenu menu = elementMenu(
                    c -> { document.setNodeColor(id, c); markDirty(); rebuild(); },
                    () -> { document.removeNode(id); markDirty(); rebuild(); },
                    isGroup ? new javafx.scene.control.MenuItem[] {alignSubmenu(id)}
                            : new javafx.scene.control.MenuItem[0]);
            menu.show(box, e.getScreenX(), e.getScreenY());
            e.consume();
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
            showResizeHandleFor(id);
        } else {
            hideResizeHandle();
        }
    }

    /** Selects an edge (highlight) for deletion, clearing any node selection. */
    private void selectEdge(String id) {
        if (selectedNodeId != null) {
            select(null);
        }
        clearEdgeSelection();
        hideResizeHandle();
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

    // ── Resize (bottom-right grip on the selected node) ────────────────────

    private static final double HANDLE_SIZE = 16;

    private void initResizeHandle() {
        resizeHandle.getStyleClass().add("canvas-resize-handle");
        resizeHandle.setPrefSize(HANDLE_SIZE, HANDLE_SIZE);
        resizeHandle.setMinSize(HANDLE_SIZE, HANDLE_SIZE);
        resizeHandle.setMaxSize(HANDLE_SIZE, HANDLE_SIZE);
        resizeHandle.setVisible(false);
        // Managed so the Pane gives it its preferred size (an unmanaged child stays 0×0 and
        // never shows); it is still positioned by relocate() in positionResizeHandle().
        resizeHandle.resize(HANDLE_SIZE, HANDLE_SIZE);
        resizeHandle.setCursor(javafx.scene.Cursor.SE_RESIZE);

        final double[] start = new double[4]; // sceneX, sceneY, startW, startH
        resizeHandle.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Region box = selectedNodeId != null ? nodeBoxes.get(selectedNodeId) : null;
            if (box == null) {
                return;
            }
            start[0] = e.getSceneX();
            start[1] = e.getSceneY();
            start[2] = box.getPrefWidth();
            start[3] = box.getPrefHeight();
            e.consume();
        });
        resizeHandle.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            Region box = selectedNodeId != null ? nodeBoxes.get(selectedNodeId) : null;
            if (box == null) {
                return;
            }
            double s = scaleTransform.getX();
            double w = Math.max(MIN_NODE_W, start[2] + (e.getSceneX() - start[0]) / s);
            double h = Math.max(MIN_NODE_H, start[3] + (e.getSceneY() - start[1]) / s);
            applyNodeSize(box, w, h);
            positionResizeHandle(box);
            refreshEdges();
            e.consume();
        });
        resizeHandle.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            Region box = selectedNodeId != null ? nodeBoxes.get(selectedNodeId) : null;
            if (box != null) {
                document.resizeNode(selectedNodeId, box.getPrefWidth(), box.getPrefHeight());
                markDirty();
            }
            e.consume();
        });
    }

    /** Applies a new fixed size to a node box and keeps its rounded clip in sync. */
    private void applyNodeSize(Region box, double w, double h) {
        box.setMinSize(w, h);
        box.setPrefSize(w, h);
        box.setMaxSize(w, h);
        if (box.getClip() instanceof Rectangle clip) {
            clip.setWidth(w);
            clip.setHeight(h);
        }
    }

    private void showResizeHandleFor(String id) {
        Region box = nodeBoxes.get(id);
        if (box == null) {
            hideResizeHandle();
            return;
        }
        if (!world.getChildren().contains(resizeHandle)) {
            world.getChildren().add(resizeHandle);
        }
        resizeHandle.toFront();
        resizeHandle.setVisible(true);
        positionResizeHandle(box);
    }

    private void positionResizeHandle(Region box) {
        // Sit fully inside the bottom-right corner so the grip is clearly visible and grabbable.
        resizeHandle.relocate(
                box.getLayoutX() + box.getPrefWidth() - HANDLE_SIZE - 1,
                box.getLayoutY() + box.getPrefHeight() - HANDLE_SIZE - 1);
    }

    private void hideResizeHandle() {
        resizeHandle.setVisible(false);
        world.getChildren().remove(resizeHandle);
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

    /** World coordinates of the current viewport centre, where new nodes are placed. */
    private double[] viewCenterWorld() {
        double s = scaleTransform.getX();
        return new double[] {
            (viewport.getWidth() / 2 - panTransform.getX()) / s,
            (viewport.getHeight() / 2 - panTransform.getY()) / s,
        };
    }

    private double[] screenToWorld(double viewX, double viewY) {
        double s = scaleTransform.getX();
        return new double[] {
            (viewX - panTransform.getX()) / s,
            (viewY - panTransform.getY()) / s,
        };
    }

    /** Adds a text node at the centre of the current view and opens it for editing. */
    private void addTextNode() {
        double[] c = viewCenterWorld();
        addTextNode(c[0], c[1]);
    }

    private void addTextNode(double worldX, double worldY) {
        String id = document.addTextNode(worldX - 125, worldY - 60, 250, 120, "");
        markDirty();
        rebuild();
        editTextNode(id);
    }

    /** Prompts for a URL and adds a link node at the centre of the current view. */
    private void addLinkNode() {
        double[] c = viewCenterWorld();
        addLinkNode(c[0], c[1]);
    }

    private void addLinkNode(double worldX, double worldY) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("https://");
        dialog.setTitle(tr("canvas.add_link", "Add link node"));
        dialog.setHeaderText(null);
        dialog.setContentText(tr("canvas.link_url", "URL:"));
        com.example.jylos.ui.UiDialogs.apply(dialog);
        String url = com.example.jylos.ui.UiDialogs.show(dialog).orElse(null);
        if (url == null || url.isBlank()) {
            return;
        }
        String id = document.addLinkNode(worldX - 125, worldY - 40, 250, 80, url.trim());
        markDirty();
        rebuild();
        select(id);
    }

    /** Prompts for a note title or vault-relative file path and adds a file node. */
    private void addFileNode() {
        double[] c = viewCenterWorld();
        addFileNode(c[0], c[1]);
    }

    private void addFileNode(double worldX, double worldY) {
        String input = pickFileReference();
        if (input == null || input.isBlank()) {
            return;
        }

        String reference = canonicalizeFileReference(input);
        if (reference.isBlank()) {
            return;
        }

        String id = createFileNode(worldX, worldY, reference);
        if (id == null) {
            return;
        }
        select(id);
    }

    private String createFileNode(double worldX, double worldY, String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String id = document.addFileNode(worldX - 160, worldY - 120, 320, 240, reference);
        markDirty();
        rebuild();
        return id;
    }

    private String pickFileReference() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(tr("canvas.add_file", "Add file node"));
        dialog.setHeaderText(tr("canvas.add_file_help", "Type a note title, image name or vault-relative file path"));
        ButtonType insert = new ButtonType(tr("action.insert", "Insert"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(insert, ButtonType.CANCEL);

        TextField input = new TextField();
        input.setPromptText(tr("canvas.file_ref", "Reference:"));
        ListView<String> suggestions = new ListView<>();
        suggestions.setPrefHeight(220);
        suggestions.getStyleClass().add("autocomplete-list");

        Runnable refresh = () -> {
            List<String> matches = filterSuggestions(fileReferenceSuggestions, embedReference(input.getText()), 40);
            suggestions.getItems().setAll(matches);
            if (!matches.isEmpty()) {
                suggestions.getSelectionModel().selectFirst();
            }
        };
        refresh.run();

        input.textProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        input.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case DOWN -> {
                    suggestions.requestFocus();
                    suggestions.getSelectionModel().selectNext();
                    suggestions.scrollTo(suggestions.getSelectionModel().getSelectedIndex());
                    event.consume();
                }
                case ENTER -> {
                    if (suggestions.getSelectionModel().getSelectedItem() != null) {
                        input.setText(suggestions.getSelectionModel().getSelectedItem());
                    }
                }
                default -> {
                }
            }
        });
        suggestions.setOnMouseClicked(event -> {
            String selected = suggestions.getSelectionModel().getSelectedItem();
            if (selected != null) {
                input.setText(selected);
                if (event.getClickCount() >= 2) {
                    dialog.setResult(input.getText());
                    dialog.close();
                }
            }
        });

        VBox content = new VBox(8, new Label(tr("canvas.file_ref", "Reference:")), input, suggestions);
        content.setPadding(new Insets(4, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        com.example.jylos.ui.UiDialogs.apply(dialog);
        dialog.setResultConverter(button -> {
            if (button != insert) {
                return null;
            }
            String typed = input.getText() != null ? input.getText().trim() : "";
            String selected = suggestions.getSelectionModel().getSelectedItem();
            if (!typed.isBlank() && (selected == null || typed.equals(selected))) {
                return typed;
            }
            if (selected != null && !selected.isBlank()) {
                return selected;
            }
            return typed;
        });
        return com.example.jylos.ui.UiDialogs.show(dialog).orElse(null);
    }

    /** Adds a group (labelled rectangle) at the centre of the current view. */
    private void addGroupNode() {
        double[] c = viewCenterWorld();
        addGroupNode(c[0], c[1]);
    }

    private void addGroupNode(double worldX, double worldY) {
        String id = document.addGroupNode(worldX - 200, worldY - 150, 400, 300,
                tr("canvas.group_default", "Group"));
        markDirty();
        rebuild();
        select(id);
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
        markInteractive(editor);
        VBox.setVgrow(editor, Priority.ALWAYS);
        vbox.getChildren().setAll(editor);
        editor.requestFocus();
        installTextAutocomplete(editor);

        final boolean[] committed = {false};
        Runnable commit = () -> {
            if (committed[0]) {
                return;
            }
            committed[0] = true;
            hideAutocompletePopup();
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
                hideAutocompletePopup();
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
        installCanvasDrops();
        viewport.setOnContextMenuRequested(e -> {
            double[] worldPoint = screenToWorld(e.getX(), e.getY());
            backgroundMenu(worldPoint[0], worldPoint[1]).show(viewport, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        viewport.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            Object target = e.getTarget();
            if ((target != viewport && target != world && target != worldGroup) || e.getClickCount() < 2 || connecting) {
                return;
            }
            double[] worldPoint = screenToWorld(e.getX(), e.getY());
            addTextNode(worldPoint[0], worldPoint[1]);
            e.consume();
        });
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

    private void installCanvasDrops() {
        viewport.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if ((dragboard.hasString() && dragboard.getString().startsWith("note:")) || dragboard.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY, TransferMode.MOVE);
                event.consume();
            }
        });
        viewport.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            double[] worldPoint = screenToWorld(event.getX(), event.getY());
            boolean success = false;
            if (dragboard.hasString() && dragboard.getString().startsWith("note:")) {
                String noteId = dragboard.getString().substring("note:".length()).trim();
                String reference = noteIdToCanvasRef != null ? noteIdToCanvasRef.apply(noteId) : canonicalizeFileReference(noteId);
                success = createFileNode(worldPoint[0], worldPoint[1], reference) != null;
            } else if (dragboard.hasFiles()) {
                double offset = 0;
                for (java.io.File file : dragboard.getFiles()) {
                    String reference = canonicalizeFileReference(file.toPath().toString());
                    if (createFileNode(worldPoint[0] + offset, worldPoint[1] + offset, reference) != null) {
                        success = true;
                        offset += 28;
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private javafx.scene.control.ContextMenu backgroundMenu(double worldX, double worldY) {
        javafx.scene.control.MenuItem addText =
                new javafx.scene.control.MenuItem(tr("canvas.add_text", "Add text node"));
        addText.setOnAction(e -> addTextNode(worldX, worldY));

        javafx.scene.control.MenuItem addFile =
                new javafx.scene.control.MenuItem(tr("canvas.add_file", "Add file node"));
        addFile.setOnAction(e -> addFileNode(worldX, worldY));

        javafx.scene.control.MenuItem addLink =
                new javafx.scene.control.MenuItem(tr("canvas.add_link", "Add link node"));
        addLink.setOnAction(e -> addLinkNode(worldX, worldY));

        javafx.scene.control.MenuItem addGroup =
                new javafx.scene.control.MenuItem(tr("canvas.add_group", "Add group"));
        addGroup.setOnAction(e -> addGroupNode(worldX, worldY));

        return new javafx.scene.control.ContextMenu(addText, addFile, addLink, addGroup);
    }

    private void installTextAutocomplete(TextInputControl editor) {
        ensureAutocompletePopup();
        autocompleteOwner = editor;
        autocompleteSuggestions = noteTitleSuggestions;
        editor.textProperty().addListener((obs, oldValue, newValue) -> checkAndShowAutocomplete(editor));
        editor.caretPositionProperty().addListener((obs, oldValue, newValue) -> checkAndShowAutocomplete(editor));
        editor.focusedProperty().addListener((obs, oldValue, focused) -> {
            if (!Boolean.TRUE.equals(focused) && autocompleteOwner == editor) {
                hideAutocompletePopup();
            }
        });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!isAutocompleteVisible() || autocompleteOwner != editor) {
                return;
            }
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
                    String selected = autocompleteList.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        completeAutocomplete(selected);
                        event.consume();
                    }
                }
                case ESCAPE -> {
                    hideAutocompletePopup();
                    event.consume();
                }
                default -> {
                }
            }
        });
    }

    private void ensureAutocompletePopup() {
        if (autocompletePopup != null) {
            return;
        }
        autocompleteList = new ListView<>();
        autocompleteList.setPrefWidth(320);
        autocompleteList.setPrefHeight(180);
        autocompleteList.getStyleClass().add("autocomplete-list");
        autocompleteList.setOnMouseClicked(event -> {
            String selected = autocompleteList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                completeAutocomplete(selected);
                event.consume();
            }
        });

        autocompletePopup = new Popup();
        autocompletePopup.setAutoHide(true);
        autocompletePopup.setConsumeAutoHidingEvents(false);
        autocompletePopup.getContent().add(autocompleteList);
    }

    private void checkAndShowAutocomplete(TextInputControl editor) {
        if (editor == null) {
            hideAutocompletePopup();
            return;
        }
        String fullText = editor.getText();
        int caret = editor.getCaretPosition();
        if (fullText == null || caret <= 0) {
            hideAutocompletePopup();
            return;
        }
        Matcher matcher = LINK_TRIGGER.matcher(fullText.substring(0, Math.min(caret, fullText.length())));
        if (!matcher.find()) {
            hideAutocompletePopup();
            return;
        }
        autocompletePrefix = "!".equals(matcher.group(1)) ? "![[" : "[[";
        autocompleteSuggestions = "![[".equals(autocompletePrefix) ? fileReferenceSuggestions : noteTitleSuggestions;
        List<String> matches = filterSuggestions(autocompleteSuggestions, matcher.group(2), 20);
        if (matches.isEmpty()) {
            hideAutocompletePopup();
            return;
        }
        autocompleteOwner = editor;
        autocompleteList.getItems().setAll(matches);
        autocompleteList.getSelectionModel().selectFirst();
        showAutocompletePopup(editor);
    }

    private List<String> filterSuggestions(Supplier<List<String>> suggestionsSupplier, String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<String> source = suggestionsSupplier != null ? suggestionsSupplier.get() : List.of();
        List<String> matches = new ArrayList<>();
        for (String item : source) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (normalizedQuery.isBlank() || item.toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)) {
                matches.add(item);
                if (matches.size() >= limit) {
                    break;
                }
            }
        }
        return matches;
    }

    private void completeAutocomplete(String value) {
        if (autocompleteOwner == null || value == null) {
            return;
        }
        String text = autocompleteOwner.getText();
        int caret = autocompleteOwner.getCaretPosition();
        Matcher matcher = LINK_TRIGGER.matcher(text.substring(0, Math.min(caret, text.length())));
        if (!matcher.find()) {
            hideAutocompletePopup();
            return;
        }
        int linkStart = caret - matcher.group(0).length();
        String completed = autocompletePrefix + value + "]]";
        autocompleteOwner.replaceText(linkStart, caret, completed);
        autocompleteOwner.positionCaret(linkStart + completed.length());
        autocompleteOwner.requestFocus();
        hideAutocompletePopup();
    }

    private void showAutocompletePopup(TextInputControl editor) {
        if (autocompletePopup == null || editor.getScene() == null) {
            return;
        }
        Bounds bounds = editor.localToScreen(editor.getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        if (!autocompletePopup.isShowing()) {
            autocompletePopup.show(editor.getScene().getWindow(), bounds.getMinX() + 24, bounds.getMinY() + 40);
            return;
        }
        autocompletePopup.setX(bounds.getMinX() + 24);
        autocompletePopup.setY(bounds.getMinY() + 40);
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

    private void installEmbeddedScrollIsolation(ScrollPane scroll) {
        scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (consumeEmbeddedVerticalScroll(scroll, event)) {
                event.consume();
            }
        });
        scroll.addEventFilter(ZoomEvent.ANY, ZoomEvent::consume);
    }

    private boolean consumeEmbeddedVerticalScroll(ScrollPane scroll, ScrollEvent event) {
        Node content = scroll.getContent();
        if (content == null) {
            return false;
        }
        Bounds contentBounds = content.getBoundsInLocal();
        Bounds viewportBounds = scroll.getViewportBounds();
        double extraHeight = contentBounds.getHeight() - viewportBounds.getHeight();
        if (extraHeight <= 1) {
            return false;
        }
        double currentPixels = scroll.getVvalue() * extraHeight;
        double nextPixels = clamp(currentPixels - event.getDeltaY(), 0, extraHeight);
        scroll.setVvalue(extraHeight <= 0 ? 0 : nextPixels / extraHeight);
        return true;
    }

    private static void markInteractive(Node node) {
        if (node != null) {
            node.getProperties().put(INTERACTIVE_CONTENT_KEY, Boolean.TRUE);
        }
    }

    private static boolean isInteractiveTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof javafx.scene.control.ScrollBar) {
                return true;
            }
            if (Boolean.TRUE.equals(current.getProperties().get(INTERACTIVE_CONTENT_KEY))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTextInputTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof TextInputControl) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTextInputFocus() {
        return getScene() != null && getScene().getFocusOwner() instanceof TextInputControl;
    }

    private boolean isAutocompleteVisible() {
        return autocompletePopup != null && autocompletePopup.isShowing();
    }

    private void hideAutocompletePopup() {
        if (autocompletePopup != null) {
            autocompletePopup.hide();
        }
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
            // Colour the whole frame (all four sides), like Obsidian — not just a left bar.
            box.setStyle("-fx-border-color: " + hex + "; -fx-border-width: 2;");
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
