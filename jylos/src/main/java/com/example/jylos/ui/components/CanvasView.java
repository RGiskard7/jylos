package com.example.jylos.ui.components;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.CanvasGeometry;
import com.example.jylos.util.CanvasModel;
import com.example.jylos.util.CanvasModel.CanvasEdge;
import com.example.jylos.util.CanvasModel.CanvasNode;
import com.example.jylos.util.SystemBrowser;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Polygon;
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
 * <p>Editing supports moving, resizing, creating and deleting nodes, adding edges
 * in connect mode, zoom/fit and undo/redo. Saving round-trips through
 * {@link CanvasModel.Document} so unknown Obsidian fields are preserved.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
public final class CanvasView extends BorderPane {

    private static final Logger logger = LoggerConfig.getLogger(CanvasView.class);

    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 4.0;
    private static final double ZOOM_STEP = 1.1;
    private static final int MAX_EMBED_DEPTH = 3;
    private static final Pattern EMBED_TOKEN = Pattern.compile("!\\[\\[(.+?)]]");
    private static final Pattern LINK_TRIGGER = Pattern.compile("(!?)\\[\\[([^\\]\\[\\n]*)$");
    private static final String INTERACTIVE_CONTENT_KEY = "canvasInteractiveContent";
    /** Property of each card's box holding its node id. */
    static final String NODE_ID_KEY = "canvasNodeId";
    /** Property of each connection point: "card id:side". */
    static final String CONNECT_HANDLE_KEY = "canvasConnectHandle";
    /** Property of each resize grip: the side or corner it drags ("left", "top-right", ...). */
    static final String RESIZE_HANDLE_KEY = "canvasResizeHandle";
    static final String SNAP_TO_GRID_KEY = "canvas.snapToGrid";
    static final String SNAP_TO_OBJECTS_KEY = "canvas.snapToObjects";
    /** How far pasted cards land from the copied ones. */
    private static final double PASTE_OFFSET = 30;
    /** The clipboard format of copied cards, so they can be pasted into another canvas. */
    private static final DataFormat CANVAS_CLIPBOARD = Optional
            .ofNullable(DataFormat.lookupMimeType("application/x-jylos-canvas"))
            .orElseGet(() -> new DataFormat("application/x-jylos-canvas"));
    /** Where the canvas switches (snapping) are saved. */
    private static Preferences preferences = Preferences.userNodeForPackage(CanvasView.class);

    /** Saves the canvas switches somewhere else (tests); {@code null} goes back to the user's. */
    static void usePreferences(Preferences prefs) {
        preferences = prefs != null ? prefs : Preferences.userNodeForPackage(CanvasView.class);
    }

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
    /** The connection points on the sides of every card (shown on hover or selection). */
    private final List<ConnectHandle> connectHandles = new ArrayList<>();
    /** Drawn edges paired with their model, so they follow the nodes as they move. */
    private final List<EdgeLine> edgeLines = new ArrayList<>();
    /** The resize grips of the selected card: its four sides and four corners. */
    private final List<Region> resizeGrips = new ArrayList<>();
    private static final double MIN_NODE_W = 80;
    private static final double MIN_NODE_H = 50;

    private double dragAnchorX;
    /** Where the right button was pressed on the background; moving past {@link #CLICK_SLOP} makes it a pan. */
    private double pressX;
    private double pressY;
    private double dragAnchorY;
    private boolean fitted = false;
    private boolean dirty = false;
    private final List<String> historySnapshots = new ArrayList<>();
    private String lastSavedSnapshot;
    private int historyIndex = -1;
    private boolean replayingHistory = false;
    private Button undoButton;
    private Button redoButton;
    /** How far (px) a right-button press may move and still open the menu instead of panning. */
    private static final double CLICK_SLOP = 4;
    /** The selected cards, in the order they were picked. */
    private final LinkedHashSet<String> selectedNodeIds = new LinkedHashSet<>();
    /** Moving cards snap to the grid and to the other cards (saved switches, on by default). */
    private boolean snapToGrid = preferences.getBoolean(SNAP_TO_GRID_KEY, true);
    private boolean snapToObjects = preferences.getBoolean(SNAP_TO_OBJECTS_KEY, true);
    /** Space held down: dragging the background pans. */
    private boolean spaceDown;
    /** The background is being dragged to pan (middle or right button, or space + left button). */
    private boolean panning;
    /** The right button is down on the background, and whether it has moved (then it panned). */
    private boolean secondaryDown;
    private boolean secondaryMoved;
    /** Where the background menu was asked for while the right button was down (macOS asks on press). */
    private ContextMenuEvent pendingBackgroundMenu;
    /** The menu open now, if any: only one at a time, and pressing the canvas closes it. */
    private ContextMenu openMenu;
    /** Where a selection box started on the background (viewport coordinates), or null. */
    private CanvasGeometry.Point selectionBoxStart;
    private final Rectangle selectionBox = new Rectangle();
    /** Currently selected edge id (for delete), or null. */
    private String selectedEdgeId;
    /** True while a text node is being edited (suspends node dragging). */
    private boolean editing = false;
    /** The card under the pointer (with room to reach its connection points), or null. */
    private String hoveredNodeId;
    /** The edge being dragged out of a card's side, drawn until it is dropped. */
    private CubicCurve pendingEdge;
    private Popup autocompletePopup;
    private ListView<String> autocompleteList;
    private TextInputControl autocompleteOwner;
    private Supplier<List<String>> autocompleteSuggestions = List::of;
    private String autocompletePrefix = "[[";

    private record EdgeLine(CanvasEdge edge, CubicCurve line, Polygon arrow) {
    }

    private record ConnectHandle(String nodeId, String side, Region handle) {
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
        initResizeHandles();
        buildWorld();
        installPanZoom();

        VBox toolbar = buildToolbar();
        toolbar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        toolbar.setPickOnBounds(false);

        StackPane canvasStack = new StackPane(viewport, toolbar);
        StackPane.setAlignment(toolbar, Pos.CENTER_RIGHT);
        StackPane.setMargin(toolbar, new Insets(16));
        setCenter(canvasStack);

        lastSavedSnapshot = document.toJson();
        historySnapshots.add(lastSavedSnapshot);
        historyIndex = 0;
        updateHistoryButtons();

        // Delete removes the selected node (and its edges). Click the background to
        // deselect; the view must be focusable for key events to arrive.
        setFocusTraversable(true);
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (editing || isTextInputTarget(e.getTarget()) || hasTextInputFocus()) {
                return;
            }
            KeyCode code = e.getCode();
            if ((code == KeyCode.DELETE
                    || code == KeyCode.BACK_SPACE)
                    && (!selectedNodeIds.isEmpty() || selectedEdgeId != null)) {
                deleteSelected();
                e.consume();
            } else if (e.isShortcutDown() && code == KeyCode.Z) {
                if (e.isShiftDown()) {
                    redo();
                } else {
                    undo();
                }
                e.consume();
            } else if (e.isShortcutDown() && code == KeyCode.C) {
                copySelection();
                e.consume();
            } else if (e.isShortcutDown() && code == KeyCode.X) {
                if (copySelection()) {
                    deleteSelected();
                }
                e.consume();
            } else if (e.isShortcutDown() && code == KeyCode.V) {
                paste();
                e.consume();
            } else if (e.isShortcutDown() && code == KeyCode.A) {
                setSelection(nodeBoxes.keySet());
                e.consume();
            } else if (code == KeyCode.SPACE) {
                spaceDown = true;
                e.consume();
            }
        });
        addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() == KeyCode.SPACE) {
                spaceDown = false;
            }
        });
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────

    private VBox buildToolbar() {
        VBox bar = new VBox(8);
        bar.getStyleClass().addAll("graph-toolbar", "canvas-toolbar");
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10));
        bar.setFillWidth(false);

        Button addText = iconButton("fth-type", tr("canvas.add_text", "Add text node"), e -> addTextNode());
        Button addFile = iconButton("fth-file", tr("canvas.add_file", "Add file node"), e -> addFileNode());
        Button addLink = iconButton("fth-link", tr("canvas.add_link", "Add link node"), e -> addLinkNode());
        Button addGroup = iconButton("fth-square", tr("canvas.add_group", "Add group"), e -> addGroupNode());
        undoButton = iconButton("fth-rotate-ccw", tr("action.undo", "Undo"), e -> undo());
        redoButton = iconButton("fth-rotate-cw", tr("action.redo", "Redo"), e -> redo());
        Button zoomIn = iconButton("fth-zoom-in", tr("canvas.zoom_in", "Zoom in"),
                e -> zoomAt(ZOOM_STEP, viewport.getWidth() / 2, viewport.getHeight() / 2));
        Button zoomOut = iconButton("fth-zoom-out", tr("canvas.zoom_out", "Zoom out"),
                e -> zoomAt(1 / ZOOM_STEP, viewport.getWidth() / 2, viewport.getHeight() / 2));
        Button fit = iconButton("fth-maximize", tr("canvas.fit", "Fit to content"), e -> {
            fitted = false;
            fit();
        });

        bar.getChildren().addAll(
                addText,
                addFile,
                addLink,
                addGroup,
                toolbarSeparator(),
                undoButton,
                redoButton,
                toolbarSeparator(),
                zoomIn,
                zoomOut,
                fit,
                toolbarSeparator(),
                settingsButton());
        return bar;
    }

    /**
     * The canvas switches: snapping to the grid and to other cards, saved for every canvas. A
     * plain button like the others (a MenuButton keeps room for its arrow and its icon would
     * sit off-centre); its menu opens beside it.
     */
    private Button settingsButton() {
        ContextMenu menu = new ContextMenu(
                snapSwitch("canvas.snap_to_grid", "Snap to grid", snapToGrid, on -> {
                    snapToGrid = on;
                    preferences.putBoolean(SNAP_TO_GRID_KEY, on);
                }),
                snapSwitch("canvas.snap_to_objects", "Snap to other cards", snapToObjects, on -> {
                    snapToObjects = on;
                    preferences.putBoolean(SNAP_TO_OBJECTS_KEY, on);
                }));
        Button settings = iconButton("fth-settings", tr("canvas.settings", "Canvas settings"), null);
        settings.getStyleClass().add("canvas-settings");
        settings.setContextMenu(menu);
        settings.setOnAction(e -> menu.show(settings, Side.LEFT, 0, 0));
        return settings;
    }

    private CheckMenuItem snapSwitch(String key, String fallback, boolean on,
            Consumer<Boolean> apply) {
        CheckMenuItem item = new CheckMenuItem(tr(key, fallback));
        item.setUserData(key);
        item.setSelected(on);
        item.selectedProperty().addListener((obs, was, now) -> apply.accept(now));
        return item;
    }

    private Node toolbarSeparator() {
        Separator separator = new Separator();
        separator.setMaxWidth(24);
        return separator;
    }

    public void save() {
        if (onSave == null) {
            return;
        }
        String snapshot = document.toJson();
        onSave.accept(snapshot);
        markSaved(snapshot);
    }

    private void markDirty() {
        if (replayingHistory) {
            return;
        }
        pushHistorySnapshot(document.toJson());
    }

    private void pushHistorySnapshot(String snapshot) {
        if (snapshot == null) {
            return;
        }
        if (historyIndex >= 0 && historyIndex < historySnapshots.size()
                && snapshot.equals(historySnapshots.get(historyIndex))) {
            updateDirtyState(snapshot);
            updateHistoryButtons();
            return;
        }
        while (historySnapshots.size() > historyIndex + 1) {
            historySnapshots.remove(historySnapshots.size() - 1);
        }
        historySnapshots.add(snapshot);
        historyIndex = historySnapshots.size() - 1;
        updateDirtyState(snapshot);
        updateHistoryButtons();
    }

    private void undo() {
        applyHistorySnapshot(historyIndex - 1);
    }

    private void redo() {
        applyHistorySnapshot(historyIndex + 1);
    }

    private void applyHistorySnapshot(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= historySnapshots.size() || targetIndex == historyIndex) {
            updateHistoryButtons();
            return;
        }
        historyIndex = targetIndex;
        replayingHistory = true;
        try {
            document.replaceWith(historySnapshots.get(historyIndex));
            rebuild();
        } finally {
            replayingHistory = false;
        }
        updateDirtyState(historySnapshots.get(historyIndex));
        updateHistoryButtons();
    }

    private void updateDirtyState(String snapshot) {
        boolean wasDirty = dirty;
        dirty = lastSavedSnapshot == null || !lastSavedSnapshot.equals(snapshot);
        if (!wasDirty && dirty && onDirty != null) {
            onDirty.run();
        }
    }

    private void updateHistoryButtons() {
        if (undoButton != null) {
            undoButton.setDisable(historyIndex <= 0);
        }
        if (redoButton != null) {
            redoButton.setDisable(historyIndex < 0 || historyIndex >= historySnapshots.size() - 1);
        }
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    public String serialize() {
        return document.toJson();
    }

    public void markSaved() {
        markSaved(document.toJson());
    }

    public String lastSavedSnapshot() {
        return lastSavedSnapshot;
    }

    private void markSaved(String snapshot) {
        lastSavedSnapshot = snapshot;
        dirty = false;
        updateHistoryButtons();
    }

    /** Re-renders the whole canvas from the (mutated) document, keeping the current pan/zoom. */

    private void rebuild() {
        selectedNodeIds.clear();
        selectedEdgeId = null;
        buildWorld();
    }

    private void buildWorld() {
        world.getChildren().clear();
        nodeBoxes.clear();
        connectHandles.clear();
        edgeLines.clear();
        for (CanvasNode n : document.nodes()) {
            if ("group".equals(n.type())) {
                Region box = groupNode(n);
                nodeBoxes.put(n.id(), box);
                world.getChildren().add(box);
                installNodeInteraction(box, n);
                installConnectHandles(n.id(), box);
            }
        }
        for (CanvasEdge e : document.edges()) {
            CubicCurve line = new CubicCurve();
            line.setFill(null);
            line.getStyleClass().add("canvas-edge");
            Polygon arrow = new Polygon();
            arrow.getStyleClass().add("canvas-edge-arrow");
            String color = hexColor(e.color());
            if (color != null) {
                line.setStyle("-fx-stroke: " + color + ";");
                arrow.setStyle("-fx-fill: " + color + ";");
            }
            final String edgeId = e.id();
            EventHandler<MouseEvent> pick = ev -> {
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
                showMenu(elementMenu(
                        c -> { document.setEdgeColor(edgeId, c); markDirty(); rebuild(); },
                        () -> { document.removeEdge(edgeId); markDirty(); rebuild(); },
                        edgeLabelMenuItem(edgeId)),
                        line, ev.getScreenX(), ev.getScreenY());
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
                installConnectHandles(n.id(), box);
            }
        }
        refreshEdges();
        refreshConnectHandles();
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
        container.getChildren().add(MarkdownMini.render(markdownBlock.toString()));
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
            showMenu(elementMenu(
                    c -> { document.setEdgeColor(e.id(), c); markDirty(); rebuild(); },
                    () -> { document.removeEdge(e.id()); markDirty(); rebuild(); },
                    edgeLabelMenuItem(e.id())),
                    label, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return label;
    }

    private MenuItem edgeLabelMenuItem(String edgeId) {
        MenuItem item =
                new MenuItem(tr("canvas.edge_label", "Edit label"));
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
        String value = showSingleFieldDialog(
                tr("canvas.edge_label", "Edit label"),
                tr("canvas.edge_label_value", "Label:"),
                edge.label(),
                null,
                tr("action.accept", "Accept"));
        if (value == null) {
            return;
        }
        document.setEdgeLabel(edgeId, value.trim());
        markDirty();
        rebuild();
    }

    private void refreshEdges() {
        for (EdgeLine el : edgeLines) {
            CanvasGeometry.Curve curve = curveOf(el.edge());
            if (curve == null) {
                el.line().setVisible(false);
                el.arrow().setVisible(false);
                continue;
            }
            CubicCurve line = el.line();
            line.setVisible(true);
            line.setStartX(curve.start().x());
            line.setStartY(curve.start().y());
            line.setControlX1(curve.control1().x());
            line.setControlY1(curve.control1().y());
            line.setControlX2(curve.control2().x());
            line.setControlY2(curve.control2().y());
            line.setEndX(curve.end().x());
            line.setEndY(curve.end().y());
            updateArrow(el.arrow(), curve);
        }
        refreshConnectHandles();
        // Edge labels sit on the middle of their curve.
        for (Node node : world.getChildren()) {
            if (node instanceof Label label && label.getStyleClass().contains("canvas-edge-label")
                    && label.getUserData() instanceof String edgeId) {
                edgeLines.stream().filter(el -> edgeId.equals(el.edge().id())).findFirst().ifPresent(el -> {
                    CanvasGeometry.Curve curve = curveOf(el.edge());
                    if (curve != null) {
                        CanvasGeometry.Point middle = curve.at(0.5);
                        label.relocate(middle.x() - 20, middle.y() - 10);
                    }
                });
            }
        }
    }

    /** The curve of an edge between its cards as they are now, or null when one is missing. */
    private CanvasGeometry.Curve curveOf(CanvasEdge edge) {
        Region from = nodeBoxes.get(edge.fromNode());
        Region to = nodeBoxes.get(edge.toNode());
        if (from == null || to == null) {
            return null;
        }
        double[] a = anchor(from, edge.fromSide());
        double[] b = anchor(to, edge.toSide());
        return CanvasGeometry.edgeCurve(new CanvasGeometry.Point(a[0], a[1]), edge.fromSide(),
                new CanvasGeometry.Point(b[0], b[1]), edge.toSide());
    }

    /** Lays out the arrowhead triangle at the end of the curve, along the direction it arrives in. */
    private static void updateArrow(Polygon arrow, CanvasGeometry.Curve curve) {
        CanvasGeometry.Point direction = curve.endDirection();
        if (direction.x() == 0 && direction.y() == 0) {
            arrow.setVisible(false);
            return;
        }
        arrow.setVisible(true);
        double ux = direction.x();
        double uy = direction.y();
        final double size = 11;
        final double half = 5;
        double bx = curve.end().x();
        double by = curve.end().y();
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
    private ContextMenu elementMenu(Consumer<String> setColor, Runnable delete,
            MenuItem... extra) {
        ContextMenu menu = new ContextMenu();
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
            MenuItem item = new MenuItem(c[1], swatch);
            item.setOnAction(e -> setColor.accept(c[0]));
            menu.getItems().add(item);
        }
        MenuItem none = new MenuItem(tr("canvas.color.none", "No colour"));
        none.setOnAction(e -> setColor.accept(null));

        FontIcon trashIcon = new FontIcon("fth-trash-2");
        trashIcon.getStyleClass().add("feather-icon");
        MenuItem deleteItem =
                new MenuItem(tr("canvas.delete", "Delete"), trashIcon);
        deleteItem.setOnAction(e -> delete.run());

        menu.getItems().addAll(new SeparatorMenuItem(), none);
        if (extra != null && extra.length > 0) {
            menu.getItems().add(new SeparatorMenuItem());
            menu.getItems().addAll(extra);
        }
        menu.getItems().addAll(new SeparatorMenuItem(), deleteItem);
        return menu;
    }

    // ── Groups: membership, label editing, member alignment (Obsidian-style) ───

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
        TextField field = new TextField(node.label());
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
            if (ev.getCode() == KeyCode.ESCAPE) {
                committed[0] = true;
                editing = false;
                rebuild();
                ev.consume();
            }
        });
    }

    private enum AlignMode { LEFT, CENTER_H, RIGHT, TOP, CENTER_V, BOTTOM }

    /** Builds the "Align" submenu that lines up a group's member nodes. */
    private Menu alignSubmenu(String groupId) {
        Menu menu = new Menu(tr("canvas.align", "Align"));
        addAlignItem(menu, groupId, AlignMode.LEFT, tr("canvas.align.left", "Left"));
        addAlignItem(menu, groupId, AlignMode.CENTER_H, tr("canvas.align.center_h", "Center horizontally"));
        addAlignItem(menu, groupId, AlignMode.RIGHT, tr("canvas.align.right", "Right"));
        menu.getItems().add(new SeparatorMenuItem());
        addAlignItem(menu, groupId, AlignMode.TOP, tr("canvas.align.top", "Top"));
        addAlignItem(menu, groupId, AlignMode.CENTER_V, tr("canvas.align.center_v", "Center vertically"));
        addAlignItem(menu, groupId, AlignMode.BOTTOM, tr("canvas.align.bottom", "Bottom"));
        return menu;
    }

    private void addAlignItem(Menu menu, String groupId, AlignMode mode, String label) {
        MenuItem item = new MenuItem(label);
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

    // ── Node interaction (drag to move, click to select, double-click to act) ───

    private void installNodeInteraction(Region box, CanvasNode node) {
        final String id = node.id();
        box.getProperties().put(NODE_ID_KEY, id);
        final boolean isGroup = "group".equals(node.type());
        final double[] pressScene = new double[2];
        final boolean[] moved = {false};
        // The cards this drag moves and where each started: the selection when this card is
        // in it (else this card alone), and the cards inside any group among them.
        final Map<String, double[]> starts = new LinkedHashMap<>();
        box.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (isInteractiveTarget(e.getTarget())) {
                return;
            }
            pressScene[0] = e.getSceneX();
            pressScene[1] = e.getSceneY();
            moved[0] = false;
            starts.clear();
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (!editing && !selectedNodeIds.contains(id) && !e.isShiftDown() && !e.isShortcutDown()) {
                select(id);
            }
            LinkedHashSet<String> moving = new LinkedHashSet<>();
            moving.add(id);
            if (selectedNodeIds.contains(id)) {
                moving.addAll(selectedNodeIds);
            }
            for (String m : List.copyOf(moving)) {
                Region mb = nodeBoxes.get(m);
                if (mb != null && mb.getStyleClass().contains("canvas-group")) {
                    moving.addAll(groupMemberIds(m));
                }
            }
            for (String m : moving) {
                Region mb = nodeBoxes.get(m);
                if (mb != null) {
                    starts.put(m, new double[] {mb.getLayoutX(), mb.getLayoutY()});
                }
            }
        });
        box.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (editing || isInteractiveTarget(e.getTarget()) || starts.isEmpty()) {
                return; // editing: allow text selection
            }
            double s = scaleTransform.getX();
            double[] start = starts.get(id);
            CanvasGeometry.Point to = snapped(box, starts.keySet(),
                    start[0] + (e.getSceneX() - pressScene[0]) / s,
                    start[1] + (e.getSceneY() - pressScene[1]) / s);
            double dx = to.x() - start[0];
            double dy = to.y() - start[1];
            starts.forEach((m, from) -> {
                Region mb = nodeBoxes.get(m);
                if (mb != null) {
                    mb.relocate(from[0] + dx, from[1] + dy);
                }
            });
            moved[0] = true;
            refreshEdges();
            if (id.equals(singleSelection())) {
                positionResizeHandle(box);
            }
            e.consume();
        });
        box.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (isInteractiveTarget(e.getTarget())) {
                return;
            }
            if (moved[0]) {
                for (String m : starts.keySet()) {
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
            // A click that ends a drag leaves the selection as it is.
            if (isInteractiveTarget(e.getTarget()) || moved[0]) {
                return;
            }
            requestFocus();
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
            } else if (e.isShiftDown() || e.isShortcutDown()) {
                toggleSelection(id);
            } else {
                select(id);
            }
        });
        box.setOnContextMenuRequested(e -> {
            select(id);
            ContextMenu menu = elementMenu(
                    c -> { document.setNodeColor(id, c); markDirty(); rebuild(); },
                    () -> { document.removeNode(id); markDirty(); rebuild(); },
                    isGroup ? new MenuItem[] {alignSubmenu(id)}
                            : new MenuItem[0]);
            showMenu(menu, box, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    /**
     * The four connection points of a card, one in the middle of each side. Dragging one draws
     * an edge: dropped on another card it connects them; dropped on the empty canvas it offers
     * to create a card there, already connected.
     */
    private void installConnectHandles(String id, Region box) {
        for (String side : List.of("top", "right", "bottom", "left")) {
            Region handle = new Region();
            handle.getStyleClass().add("canvas-connect-handle");
            handle.getProperties().put(CONNECT_HANDLE_KEY, id + ":" + side);
            handle.setPickOnBounds(true);
            handle.setManaged(false);
            handle.setVisible(false);
            handle.setCursor(Cursor.CROSSHAIR);
            final double[] press = new double[2];
            handle.setOnMousePressed(event -> {
                event.consume();
                if (event.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                requestFocus();
                press[0] = event.getSceneX();
                press[1] = event.getSceneY();
                pendingEdge = new CubicCurve();
                pendingEdge.setFill(null);
                pendingEdge.getStyleClass().addAll("canvas-edge", "canvas-edge-pending");
                pendingEdge.setMouseTransparent(true);
                world.getChildren().add(pendingEdge);
                refreshConnectHandles();
                updatePendingEdge(id, side, event);
            });
            handle.setOnMouseDragged(event -> {
                event.consume();
                if (pendingEdge != null) {
                    updatePendingEdge(id, side, event);
                }
            });
            handle.setOnMouseReleased(event -> {
                event.consume();
                if (pendingEdge == null) {
                    return;
                }
                boolean moved = Math.hypot(event.getSceneX() - press[0], event.getSceneY() - press[1]) > 4;
                finishConnection(id, side, event, moved);
            });
            handle.setOnMouseClicked(Event::consume);
            ConnectHandle connectHandle = new ConnectHandle(id, side, handle);
            connectHandles.add(connectHandle);
            world.getChildren().add(handle);
            positionConnectHandle(box, connectHandle);
        }
    }

    /** Redraws the edge being dragged out of {@code side} of a card, up to the pointer. */
    private void updatePendingEdge(String sourceId, String side, MouseEvent event) {
        Region source = nodeBoxes.get(sourceId);
        if (source == null || pendingEdge == null) {
            return;
        }
        Point2D p = world.sceneToLocal(event.getSceneX(), event.getSceneY());
        String targetId = cardAt(new CanvasGeometry.Point(p.getX(), p.getY()), sourceId);
        double[] a = anchor(source, side);
        CanvasGeometry.Curve curve;
        if (targetId != null) {
            String toSide = CanvasGeometry.facingSide(rectOf(source), rectOf(nodeBoxes.get(targetId)));
            double[] b = anchor(nodeBoxes.get(targetId), toSide);
            curve = CanvasGeometry.edgeCurve(new CanvasGeometry.Point(a[0], a[1]), side,
                    new CanvasGeometry.Point(b[0], b[1]), toSide);
        } else {
            curve = CanvasGeometry.edgeCurve(new CanvasGeometry.Point(a[0], a[1]), side,
                    new CanvasGeometry.Point(p.getX(), p.getY()), null);
        }
        pendingEdge.setStartX(curve.start().x());
        pendingEdge.setStartY(curve.start().y());
        pendingEdge.setControlX1(curve.control1().x());
        pendingEdge.setControlY1(curve.control1().y());
        pendingEdge.setControlX2(curve.control2().x());
        pendingEdge.setControlY2(curve.control2().y());
        pendingEdge.setEndX(curve.end().x());
        pendingEdge.setEndY(curve.end().y());
        nodeBoxes.forEach((id, box) -> {
            box.getStyleClass().remove("canvas-connection-target");
            if (id.equals(targetId)) {
                box.getStyleClass().add("canvas-connection-target");
            }
        });
    }

    /** Ends dragging an edge out of a card: connect to the card under the pointer, or offer a new one. */
    private void finishConnection(String sourceId, String side, MouseEvent event, boolean moved) {
        world.getChildren().remove(pendingEdge);
        pendingEdge = null;
        nodeBoxes.values().forEach(box -> box.getStyleClass().remove("canvas-connection-target"));
        Point2D p = world.sceneToLocal(event.getSceneX(), event.getSceneY());
        CanvasGeometry.Point at = new CanvasGeometry.Point(p.getX(), p.getY());
        String targetId = cardAt(at, sourceId);
        if (targetId != null) {
            String toSide = CanvasGeometry.facingSide(rectOf(nodeBoxes.get(sourceId)), rectOf(nodeBoxes.get(targetId)));
            document.addEdge(sourceId, side, targetId, toSide);
            markDirty();
            rebuild();
            select(targetId);
            return;
        }
        refreshConnectHandles();
        if (moved && (cardAt(at, null) == null)) {
            showMenu(connectionMenu(sourceId, side, at), viewport, event.getScreenX(), event.getScreenY());
        }
    }

    /** What an edge dropped on the empty canvas can become: a new card there, connected. */
    private ContextMenu connectionMenu(String sourceId, String side, CanvasGeometry.Point at) {
        MenuItem text =
                new MenuItem(tr("canvas.add_text", "Add text node"));
        text.setUserData("canvas.add_text");
        text.setOnAction(e -> {
            String id = document.addTextNode(at.x() - 125, at.y() - 60, 250, 120, "");
            connectNew(sourceId, side, id);
            editTextNode(id);
        });
        MenuItem file =
                new MenuItem(tr("canvas.add_file", "Add file node"));
        file.setUserData("canvas.add_file");
        file.setOnAction(e -> {
            String input = pickFileReference();
            String reference = input == null ? "" : canonicalizeFileReference(input);
            if (reference.isBlank()) {
                return;
            }
            String id = document.addFileNode(at.x() - 160, at.y() - 120, 320, 240, reference);
            connectNew(sourceId, side, id);
            select(id);
        });
        return new ContextMenu(text, file);
    }

    /** Connects a card just added to the document to the card the edge was dragged from. */
    private void connectNew(String sourceId, String side, String newId) {
        CanvasNode created = document.nodes().stream().filter(n -> n.id().equals(newId)).findFirst().orElse(null);
        Region source = nodeBoxes.get(sourceId);
        if (created != null && source != null) {
            String toSide = CanvasGeometry.facingSide(rectOf(source),
                    new CanvasGeometry.Rect(created.x(), created.y(), created.width(), created.height()));
            document.addEdge(sourceId, side, newId, toSide);
        }
        markDirty();
        rebuild();
    }

    /** The card at a point of the canvas (cards above groups), other than {@code except}; null if none. */
    private String cardAt(CanvasGeometry.Point point, String except) {
        String group = null;
        for (CanvasNode n : document.nodes()) {
            Region box = nodeBoxes.get(n.id());
            if (box == null || n.id().equals(except) || !rectOf(box).contains(point)) {
                continue;
            }
            if (!"group".equals(n.type())) {
                return n.id();
            }
            group = group == null ? n.id() : group;
        }
        return group;
    }

    private static CanvasGeometry.Rect rectOf(Region box) {
        return new CanvasGeometry.Rect(box.getLayoutX(), box.getLayoutY(), box.getPrefWidth(), box.getPrefHeight());
    }

    // ── Selection / create / edit / delete ───────────────────────────────────

    /** Selects only this card, or nothing when {@code id} is null. */
    private void select(String id) {
        setSelection(id == null ? List.of() : List.of(id));
    }

    /** Selects exactly these cards. */
    private void setSelection(Collection<String> ids) {
        clearEdgeSelection();
        selectedNodeIds.clear();
        for (String id : ids) {
            if (nodeBoxes.containsKey(id)) {
                selectedNodeIds.add(id);
            }
        }
        applySelectionStyles();
    }

    /** Adds the card to the selection, or takes it out if it was in it (Shift/Cmd+click). */
    private void toggleSelection(String id) {
        clearEdgeSelection();
        if (!selectedNodeIds.remove(id)) {
            selectedNodeIds.add(id);
        }
        applySelectionStyles();
    }

    /** The selected card when exactly one is selected (it gets the resize grip), else null. */
    private String singleSelection() {
        return selectedNodeIds.size() == 1 ? selectedNodeIds.iterator().next() : null;
    }

    /** Highlights the selected cards; the one selected card also gets its resize grips. */
    private void applySelectionStyles() {
        nodeBoxes.forEach((id, box) -> {
            box.getStyleClass().remove("canvas-selected");
            if (selectedNodeIds.contains(id)) {
                box.getStyleClass().add("canvas-selected");
            }
        });
        String single = singleSelection();
        if (single != null) {
            showResizeHandleFor(single);
        } else {
            hideResizeHandle();
        }
        refreshConnectHandles();
    }

    /**
     * Where a card being dragged to {@code (x, y)} ends up, snapped to the grid and to the
     * cards not being moved, as the switches say.
     */
    private CanvasGeometry.Point snapped(Region box, Collection<String> moving, double x, double y) {
        List<CanvasGeometry.Rect> others = new ArrayList<>();
        nodeBoxes.forEach((id, other) -> {
            if (!moving.contains(id)) {
                others.add(new CanvasGeometry.Rect(other.getLayoutX(), other.getLayoutY(),
                        other.getPrefWidth(), other.getPrefHeight()));
            }
        });
        return CanvasGeometry.snap(new CanvasGeometry.Rect(x, y, box.getPrefWidth(), box.getPrefHeight()),
                others, scaleTransform.getX(), snapToGrid, snapToObjects);
    }

    /** Selects an edge (highlight) for deletion, clearing any node selection. */
    private void selectEdge(String id) {
        if (!selectedNodeIds.isEmpty()) {
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

    /** Deletes the current selection — an edge if one is selected, otherwise the selected cards. */
    private void deleteSelected() {
        if (selectedEdgeId != null) {
            document.removeEdge(selectedEdgeId);
            markDirty();
            rebuild();
            return;
        }
        if (!selectedNodeIds.isEmpty()) {
            selectedNodeIds.forEach(document::removeNode);
            markDirty();
            rebuild();
        }
    }

    /** Puts the selected cards and the edges between them on the clipboard; false when none. */
    private boolean copySelection() {
        if (selectedNodeIds.isEmpty()) {
            return false;
        }
        ClipboardContent content = new ClipboardContent();
        content.put(CANVAS_CLIPBOARD, document.copyFragment(selectedNodeIds));
        Clipboard.getSystemClipboard().setContent(content);
        return true;
    }

    /** Adds the copied cards a little away from the originals, and selects them. */
    private void paste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (!(clipboard.getContent(CANVAS_CLIPBOARD) instanceof String fragment)) {
            return;
        }
        List<String> pasted = document.pasteFragment(fragment, PASTE_OFFSET, PASTE_OFFSET);
        if (pasted.isEmpty()) {
            return;
        }
        markDirty();
        rebuild();
        setSelection(pasted);
    }

    // ── Resize (the sides and corners of the selected card) ──────────────────

    /** How thick the grip along each side is, and how big the one on each corner. */
    private static final double GRIP_THICKNESS = 8;
    private static final double GRIP_CORNER = 12;
    private static final List<String> GRIPS = List.of(
            "top", "right", "bottom", "left", "top-left", "top-right", "bottom-right", "bottom-left");

    private void initResizeHandles() {
        for (String grip : GRIPS) {
            Region handle = new Region();
            handle.getStyleClass().add("canvas-resize-handle");
            handle.getProperties().put(RESIZE_HANDLE_KEY, grip);
            handle.setManaged(false);
            handle.setPickOnBounds(true);
            handle.setVisible(false);
            handle.setCursor(switch (grip) {
                case "top" -> Cursor.N_RESIZE;
                case "bottom" -> Cursor.S_RESIZE;
                case "left" -> Cursor.W_RESIZE;
                case "right" -> Cursor.E_RESIZE;
                case "top-left" -> Cursor.NW_RESIZE;
                case "top-right" -> Cursor.NE_RESIZE;
                case "bottom-left" -> Cursor.SW_RESIZE;
                default -> Cursor.SE_RESIZE;
            });
            final double[] press = new double[2];
            final CanvasGeometry.Rect[] start = new CanvasGeometry.Rect[1];
            handle.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                e.consume();
                Region box = singleSelection() != null ? nodeBoxes.get(singleSelection()) : null;
                start[0] = box != null ? rectOf(box) : null;
                press[0] = e.getSceneX();
                press[1] = e.getSceneY();
            });
            handle.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
                e.consume();
                Region box = singleSelection() != null ? nodeBoxes.get(singleSelection()) : null;
                if (box == null || start[0] == null) {
                    return;
                }
                double s = scaleTransform.getX();
                CanvasGeometry.Rect r = CanvasGeometry.resize(start[0], grip,
                        (e.getSceneX() - press[0]) / s, (e.getSceneY() - press[1]) / s,
                        MIN_NODE_W, MIN_NODE_H, snapToGrid ? CanvasGeometry.gridSpacing(s) : 0);
                box.relocate(r.x(), r.y());
                applyNodeSize(box, r.width(), r.height());
                positionResizeHandle(box);
                refreshEdges();
            });
            handle.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
                e.consume();
                String id = singleSelection();
                Region box = id != null ? nodeBoxes.get(id) : null;
                if (box == null || start[0] == null || rectOf(box).equals(start[0])) {
                    return;
                }
                document.moveNode(id, box.getLayoutX(), box.getLayoutY());
                document.resizeNode(id, box.getPrefWidth(), box.getPrefHeight());
                markDirty();
            });
            handle.setOnMouseClicked(Event::consume);
            resizeGrips.add(handle);
        }
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
        for (Region grip : resizeGrips) {
            if (!world.getChildren().contains(grip)) {
                world.getChildren().add(grip);
            }
            grip.setVisible(true);
            grip.toFront();
        }
        positionResizeHandle(box);
    }

    /** Lays the grips along the card's sides and on its corners. */
    private void positionResizeHandle(Region box) {
        double x = box.getLayoutX();
        double y = box.getLayoutY();
        double w = box.getPrefWidth();
        double h = box.getPrefHeight();
        double t = GRIP_THICKNESS;
        double c = GRIP_CORNER;
        for (Region grip : resizeGrips) {
            switch ((String) grip.getProperties().get(RESIZE_HANDLE_KEY)) {
                case "top" -> grip.resizeRelocate(x + c / 2, y - t / 2, Math.max(0, w - c), t);
                case "bottom" -> grip.resizeRelocate(x + c / 2, y + h - t / 2, Math.max(0, w - c), t);
                case "left" -> grip.resizeRelocate(x - t / 2, y + c / 2, t, Math.max(0, h - c));
                case "right" -> grip.resizeRelocate(x + w - t / 2, y + c / 2, t, Math.max(0, h - c));
                case "top-left" -> grip.resizeRelocate(x - c / 2, y - c / 2, c, c);
                case "top-right" -> grip.resizeRelocate(x + w - c / 2, y - c / 2, c, c);
                case "bottom-left" -> grip.resizeRelocate(x - c / 2, y + h - c / 2, c, c);
                default -> grip.resizeRelocate(x + w - c / 2, y + h - c / 2, c, c);
            }
        }
    }

    private void hideResizeHandle() {
        for (Region grip : resizeGrips) {
            grip.setVisible(false);
            world.getChildren().remove(grip);
        }
    }

    /** Places the connection points just outside the middle of each side, and shows those of
     *  the card under the pointer or the one selected card. */
    private void refreshConnectHandles() {
        String single = singleSelection();
        for (ConnectHandle connectHandle : connectHandles) {
            Region box = nodeBoxes.get(connectHandle.nodeId());
            Region handle = connectHandle.handle();
            if (box == null || handle == null) {
                continue;
            }
            positionConnectHandle(box, connectHandle);
            String id = connectHandle.nodeId();
            handle.setVisible(pendingEdge == null && !editing
                    && (id.equals(single) || id.equals(hoveredNodeId)));
            handle.toFront();
        }
        resizeGrips.forEach(Node::toFront);
    }

    /** How far outside its side a connection point sits, so it does not cover the resize grip. */
    private static final double HANDLE_OFFSET = 14;

    private void positionConnectHandle(Region box, ConnectHandle connectHandle) {
        if (box == null || connectHandle == null || connectHandle.handle() == null) {
            return;
        }
        Region handle = connectHandle.handle();
        double size = handle.prefWidth(-1) > 0 ? handle.prefWidth(-1) : 12;
        double x = box.getLayoutX();
        double y = box.getLayoutY();
        double w = box.getPrefWidth();
        double h = box.getPrefHeight();
        double cx = switch (connectHandle.side()) {
            case "left" -> x - HANDLE_OFFSET;
            case "right" -> x + w + HANDLE_OFFSET;
            default -> x + w / 2.0;
        };
        double cy = switch (connectHandle.side()) {
            case "top" -> y - HANDLE_OFFSET;
            case "bottom" -> y + h + HANDLE_OFFSET;
            default -> y + h / 2.0;
        };
        handle.resizeRelocate(cx - size / 2.0, cy - size / 2.0, size, size);
    }

    /** Shows the connection points of the card under the pointer (with room to reach them). */
    private void trackHover(MouseEvent e) {
        Point2D p = world.sceneToLocal(e.getSceneX(), e.getSceneY());
        double reach = HANDLE_OFFSET + 10;
        String over = null;
        for (CanvasNode n : document.nodes()) {
            Region box = nodeBoxes.get(n.id());
            if (box != null && new CanvasGeometry.Rect(box.getLayoutX() - reach, box.getLayoutY() - reach,
                    box.getPrefWidth() + 2 * reach, box.getPrefHeight() + 2 * reach)
                    .contains(new CanvasGeometry.Point(p.getX(), p.getY()))) {
                over = n.id();
                if (!"group".equals(n.type())) {
                    break;
                }
            }
        }
        if (!Objects.equals(over, hoveredNodeId)) {
            hoveredNodeId = over;
            refreshConnectHandles();
        }
    }

    /** Clears node highlights and re-applies the connect-source one (without changing the selection). */
    private void rebuildHighlights() {
        applySelectionStyles();
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
        String url = showSingleFieldDialog(
                tr("canvas.add_link", "Add link node"),
                tr("canvas.link_url", "URL:"),
                "https://",
                null,
                tr("action.accept", "Accept"));
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

        Label help = new Label(tr("canvas.add_file_help", "Type a note title, image name or vault-relative file path"));
        help.setWrapText(true);
        Label referenceLabel = new Label(tr("canvas.file_ref", "Reference:"));
        VBox content = new VBox(10, help, referenceLabel, input, suggestions);
        content.setPadding(new Insets(12, 12, 0, 12));
        dialog.getDialogPane().setContent(content);
        UiDialogs.apply(dialog);
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
        return UiDialogs.show(dialog).orElse(null);
    }

    private String showSingleFieldDialog(String title, String labelText, String initialValue,
            String promptText, String acceptText) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        ButtonType accept = new ButtonType(acceptText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(accept, ButtonType.CANCEL);

        Label label = new Label(labelText);
        TextField field = new TextField(initialValue != null ? initialValue : "");
        if (promptText != null && !promptText.isBlank()) {
            field.setPromptText(promptText);
        }
        VBox content = new VBox(10, label, field);
        content.setPadding(new Insets(12, 12, 0, 12));
        dialog.getDialogPane().setContent(content);
        UiDialogs.apply(dialog);
        dialog.setResultConverter(button -> button == accept ? field.getText() : null);
        Platform.runLater(field::requestFocus);
        return UiDialogs.show(dialog).orElse(null);
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
            if (ev.getCode() == KeyCode.ESCAPE) {
                committed[0] = true;
                hideAutocompletePopup();
                editing = false;
                rebuild();
                ev.consume();
            } else if (ev.getCode() == KeyCode.ENTER && ev.isShortcutDown()) {
                commit.run();
                ev.consume();
            }
        });
    }

    // ── Pan / zoom ───────────────────────────────────────────────────────────

    private void installPanZoom() {
        installCanvasDrops();
        // The right button pans when dragged and opens the menu when it does not move, as in
        // Obsidian. JavaFX asks for the menu on press (macOS) or after release (Windows), so a
        // request during the press waits for the release, and one after a drag is dropped.
        viewport.setOnContextMenuRequested(e -> {
            if (secondaryDown && !e.isKeyboardTrigger()) {
                pendingBackgroundMenu = e;
            } else if (!secondaryMoved || e.isKeyboardTrigger()) {
                showBackgroundMenu(e);
            }
            e.consume();
        });
        viewport.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> hideOpenMenu());
        viewport.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            Object target = e.getTarget();
            if ((target != viewport && target != world && target != worldGroup) || e.getClickCount() < 2) {
                return;
            }
            double[] worldPoint = screenToWorld(e.getX(), e.getY());
            addTextNode(worldPoint[0], worldPoint[1]);
            e.consume();
        });
        // The wheel (two fingers on a trackpad) pans; with Cmd/Ctrl, or pinching, it zooms.
        viewport.setOnScroll(e -> {
            if (e.isShortcutDown()) {
                zoomAt(Math.pow(2, e.getDeltaY() / 300), e.getX(), e.getY());
            } else {
                double dx = e.getDeltaX();
                double dy = e.getDeltaY();
                if (e.isShiftDown() && dx == 0) {
                    dx = dy;
                    dy = 0;
                }
                panTransform.setX(panTransform.getX() + dx);
                panTransform.setY(panTransform.getY() + dy);
            }
            e.consume();
        });
        viewport.addEventFilter(MouseEvent.MOUSE_MOVED, this::trackHover);
        viewport.setOnMouseExited(e -> {
            hoveredNodeId = null;
            refreshConnectHandles();
        });
        viewport.setOnZoom(e -> {
            zoomAt(e.getZoomFactor(), e.getX(), e.getY());
            e.consume();
        });
        // On the background, the left button draws a selection box; the middle button, or
        // the left one with Space held, pans.
        selectionBox.getStyleClass().add("canvas-selection-box");
        selectionBox.setManaged(false);
        selectionBox.setMouseTransparent(true);
        viewport.setOnMousePressed(e -> {
            if (!isBackground(e.getTarget())) {
                return;
            }
            requestFocus();
            secondaryDown = e.getButton() == MouseButton.SECONDARY;
            secondaryMoved = false;
            panning = e.getButton() == MouseButton.MIDDLE || secondaryDown
                    || (e.getButton() == MouseButton.PRIMARY && spaceDown);
            if (panning) {
                pressX = e.getX();
                pressY = e.getY();
                dragAnchorX = e.getX() - panTransform.getX();
                dragAnchorY = e.getY() - panTransform.getY();
                return;
            }
            if (e.getButton() == MouseButton.PRIMARY) {
                select(null); // pressing the background clears the selection
                selectionBoxStart = new CanvasGeometry.Point(e.getX(), e.getY());
            }
        });
        viewport.setOnMouseDragged(e -> {
            if (panning) {
                if (secondaryDown && Math.hypot(e.getX() - pressX, e.getY() - pressY) > CLICK_SLOP) {
                    secondaryMoved = true;
                }
                panTransform.setX(e.getX() - dragAnchorX);
                panTransform.setY(e.getY() - dragAnchorY);
            } else if (selectionBoxStart != null) {
                CanvasGeometry.Rect box = CanvasGeometry.Rect.between(selectionBoxStart,
                        new CanvasGeometry.Point(e.getX(), e.getY()));
                selectionBox.setX(box.x());
                selectionBox.setY(box.y());
                selectionBox.setWidth(box.width());
                selectionBox.setHeight(box.height());
                if (!viewport.getChildren().contains(selectionBox)) {
                    viewport.getChildren().add(selectionBox);
                }
            }
        });
        viewport.setOnMouseReleased(e -> {
            if (panning) {
                panning = false;
                if (secondaryDown) {
                    secondaryDown = false;
                    ContextMenuEvent pending = pendingBackgroundMenu;
                    pendingBackgroundMenu = null;
                    if (pending != null && !secondaryMoved) {
                        showBackgroundMenu(pending);
                    }
                }
                return;
            }
            if (selectionBoxStart == null) {
                return;
            }
            double[] from = screenToWorld(selectionBoxStart.x(), selectionBoxStart.y());
            double[] to = screenToWorld(e.getX(), e.getY());
            selectionBoxStart = null;
            viewport.getChildren().remove(selectionBox);
            CanvasGeometry.Rect box = CanvasGeometry.Rect.between(new CanvasGeometry.Point(from[0], from[1]),
                    new CanvasGeometry.Point(to[0], to[1]));
            List<String> touched = new ArrayList<>();
            nodeBoxes.forEach((id, card) -> {
                if (box.intersects(new CanvasGeometry.Rect(card.getLayoutX(), card.getLayoutY(),
                        card.getPrefWidth(), card.getPrefHeight()))) {
                    touched.add(id);
                }
            });
            setSelection(document.nodes().stream().map(CanvasNode::id).filter(touched::contains).toList());
        });
    }

    /** Whether a mouse event landed on the empty canvas rather than on a card, edge or control. */
    private boolean isBackground(Object target) {
        return target == viewport || target == world || target == worldGroup
                || (target instanceof Label label && label.getStyleClass().contains("canvas-empty"));
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
                for (File file : dragboard.getFiles()) {
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

    private void showBackgroundMenu(ContextMenuEvent e) {
        double[] worldPoint = screenToWorld(e.getX(), e.getY());
        showMenu(backgroundMenu(worldPoint[0], worldPoint[1]), viewport, e.getScreenX(), e.getScreenY());
    }

    /** Shows {@code menu}, closing the one open before: menus never pile up. */
    private void showMenu(ContextMenu menu, Node anchor, double screenX, double screenY) {
        hideOpenMenu();
        openMenu = menu;
        menu.show(anchor, screenX, screenY);
    }

    private void hideOpenMenu() {
        if (openMenu != null) {
            openMenu.hide();
            openMenu = null;
        }
    }

    private ContextMenu backgroundMenu(double worldX, double worldY) {
        MenuItem addText =
                new MenuItem(tr("canvas.add_text", "Add text node"));
        addText.setOnAction(e -> addTextNode(worldX, worldY));

        MenuItem addFile =
                new MenuItem(tr("canvas.add_file", "Add file node"));
        addFile.setOnAction(e -> addFileNode(worldX, worldY));

        MenuItem addLink =
                new MenuItem(tr("canvas.add_link", "Add link node"));
        addLink.setOnAction(e -> addLinkNode(worldX, worldY));

        MenuItem addGroup =
                new MenuItem(tr("canvas.add_group", "Add group"));
        addGroup.setOnAction(e -> addGroupNode(worldX, worldY));

        return new ContextMenu(addText, addFile, addLink, addGroup);
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
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> source = suggestionsSupplier != null ? suggestionsSupplier.get() : List.of();
        List<String> matches = new ArrayList<>();
        for (String item : source) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (normalizedQuery.isBlank() || item.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
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

    // ── Node content helpers ─────────────────────────────────────────────────

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
            if (current instanceof ScrollBar) {
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

    // ── Misc helpers ─────────────────────────────────────────────────────────

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

    private Button iconButton(String iconLiteral, String tip, EventHandler<ActionEvent> action) {
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
