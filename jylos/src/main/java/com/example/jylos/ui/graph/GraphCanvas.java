package com.example.jylos.ui.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * High-performance, dependency-free Obsidian-style force-directed graph rendered
 * natively on a JavaFX {@link Canvas}.
 *
 * <h2>Why native Canvas (not a WebView)</h2>
 * Rendering directly on a JavaFX {@code Canvas} avoids the WebView's
 * software-canvas/requestAnimationFrame overhead and the JS↔Java bridge latency,
 * giving fluid zoom/pan/drag and instant node hit-testing.
 *
 * <h2>Physics</h2>
 * A d3-force-style simulation: many-body repulsion accelerated with a
 * Barnes–Hut quadtree ({@code O(n log n)}), spring attraction along edges, mild
 * centering gravity, and velocity damping with <em>alpha cooling</em>. When the
 * layout settles (alpha below a floor and nothing being dragged) the
 * {@link AnimationTimer} stops, so an idle graph consumes no CPU. Interaction
 * re-heats the simulation only when needed.
 *
 * <h2>Interaction</h2>
 * Scroll = zoom toward the cursor, drag background = pan, drag node = move it
 * (re-heats), hover = highlight a node and its neighbours, click a note node =
 * open it via {@link #setOnOpenNote(Consumer)}.
 *
 * <p>All mutable state is touched only on the JavaFX Application Thread.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.4.0
 */
public final class GraphCanvas extends Region {

    // ── Physics tuning (close to Obsidian's defaults) ────────────────────────
    private static final double ALPHA_INIT     = 1.0;
    // Obsidian's simulation (its graph worker): alpha 1 down to .001 in about 300 steps,
    // keeping 60 % of the velocity each step.
    private static final double ALPHA_MIN      = 0.001;
    private static final double ALPHA_DECAY    = 1 - Math.pow(0.001, 1.0 / 300);
    private static final double VELOCITY_DECAY = 0.60;     // fraction of velocity kept per tick
    private static final double CHARGE         = -1000.0;  // many-body repulsion (negative = repel)
    private static final double CHARGE_MIN_DISTANCE2 = 30.0 * 30.0;
    private static final double THETA2         = 0.81;     // Barnes–Hut accuracy (theta=0.9)^2
    private static final double LINK_DISTANCE  = 250.0;
    private static final double CENTER_GRAVITY = 0.1;      // pulls every node toward the centre
    private static final double MAX_VELOCITY   = 10_000.0; // only a guard against blow-ups
    private static final double COLLISION_RADIUS = 60.0;   // every node keeps this much room
    private static final double COLLISION_STRENGTH = 0.5;
                                                             // this is not scaled by alpha: collisions must
                                                             // keep resolving even once the simulation has
                                                             // cooled down, or an overlap in an already-settled
                                                             // layout would never get corrected

    // ── Interaction tuning ───────────────────────────────────────────────────
    private static final double MIN_SCALE = 0.02;
    private static final double MAX_SCALE = 8.0;
    private static final double GHOST_ALPHA = 0.5;         // opacity of a note that does not exist yet (Obsidian's)
    private static final double ZOOM_EASE = 0.85;          // share of the zoom gap left after a frame (Obsidian's)
    private static final double CLICK_SLOP = 4.0;          // px movement still counts as a click
    private static final double MIN_NODE_PX = 2.0;         // floor on on-screen node radius (Obsidian-like dots)
    private static final double HOVER_DIM_ALPHA = 0.28;    // opacity for nodes/labels not part of the hovered relation
    private static final double HOVER_EASE_RATE = 0.25;    // fraction of the remaining hover-fade gap closed
                                                             // per animation frame — a simple exponential ease,
                                                             // not a fixed-duration tween: no elapsed-time
                                                             // bookkeeping needed, and it self-adjusts to
                                                             // whatever frame rate is actually delivered
    private static final double BASE_NODE_RADIUS = 8.0;    // smallest note radius (Obsidian's minimum size)
                                                             // the reference point labelThreshold is calibrated
                                                             // against; a bigger node's label reveals at a
                                                             // proportionally lower zoom than that reference

    private final Canvas canvas = new Canvas();
    private final GraphicsContext g = canvas.getGraphicsContext2D();

    // ── Live-adjustable forces (defaults from the constants above) ───────────
    private double charge = CHARGE;
    private double linkDistance = LINK_DISTANCE;
    private double centerGravity = CENTER_GRAVITY;
    private double linkForce = 1.0;        // spring strength multiplier (Obsidian "link force")
    private boolean colorByFolder = false;

    // ── Live-adjustable display options ──────────────────────────────────────
    private double nodeScale = 1.0;        // node radius multiplier
    private double lineThickness = 1.0;    // edge width multiplier
    private boolean showArrows = false;    // draw directional arrowheads on edges
    private double labelThreshold = 0.4;   // min zoom at which labels appear (text fade)

    // ── Node arrays (struct-of-arrays for cache-friendly physics) ────────────
    private int n = 0;
    private double[] x = new double[0];
    private double[] y = new double[0];
    private double[] vx = new double[0];
    private double[] vy = new double[0];
    private double[] radius = new double[0];
    private int[] kind = new int[0];          // 0 = note, 1 = tag, 2 = ghost
    private String[] ids = new String[0];
    private String[] labels = new String[0];
    private String[] group = new String[0];   // folder/tag group, for color-by-folder
    /** Colour groups: the colour of each node a group matched, by node id. */
    private Map<String, Color> nodeColors = Map.of();
    private Color[] groupFill = new Color[0];
    private int[][] neighbors = new int[0][];
    /** Node indices sorted by descending radius, computed once per model build (radius
     *  never changes afterward) so the label pass doesn't re-sort on every frame. */
    private int[] labelOrder = new int[0];
    private final List<double[]> placedLabelBoxes = new ArrayList<>();

    private static final int KIND_NOTE = 0;
    private static final int KIND_TAG = 1;
    private static final int KIND_GHOST = 2;

    // ── Edges (index pairs) + per-node link counts (for spring strength) ─────
    private int[] edgeA = new int[0];
    private int[] edgeB = new int[0];
    private int[] linkCount = new int[0];

    // ── View transform (world → screen): screen = world*scale + offset ───────
    private double scale = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;
    /** The zoom the view eases toward, a little each frame, as Obsidian's does. */
    private double targetScale = 1.0;
    /** Until the user zooms or drags the view, it keeps the whole graph framed. */
    private boolean followFit = true;
    /** The next data starts a fresh view, as opening the graph: see {@link #openView}. */
    private boolean openPending = true;
    private Double openScale = null;
    private Consumer<Double> onZoomChosen = s -> { };
    private double lastWidth = 0;
    private double lastHeight = 0;

    // ── Simulation / interaction state ───────────────────────────────────────
    private double alpha = 0;
    private boolean timerRunning = false;
    private int hoverIndex = -1;
    /** The node whose highlight is currently being shown/animated — updates the
     *  instant a new node is hovered, but (unlike {@link #hoverIndex}) holds its
     *  last value through the fade-<em>out</em> after the cursor leaves, so the
     *  highlight has something to ease back down from instead of vanishing the
     *  instant the cursor moves off. */
    private int displayHoverIndex = -1;
    private double hoverStrength = 0.0;  // 0 = no highlight showing, 1 = fully shown
    private double hoverTarget = 0.0;
    private boolean hoverAnimating = false;
    private int dragIndex = -1;
    private boolean panning = false;
    private boolean movedSincePress = false;
    private double pressX, pressY;        // screen coords at mouse-press
    private double lastDragX, lastDragY;  // screen coords for incremental pan

    private Consumer<String> onOpenNote = id -> { };
    private Palette palette = Palette.light();

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            frame();
            if (alpha < ALPHA_MIN && dragIndex < 0 && !viewMoving()) {
                stopTimer();
            }
        }
    };

    /** Drives {@link #hoverStrength} toward {@link #hoverTarget}, independently of
     *  the physics timer above — hovering must animate smoothly even when the
     *  layout has long since settled and that timer isn't running. Starts on a
     *  hover change and stops itself once it converges, matching the same
     *  don't-run-when-nothing's-animating discipline the physics timer already
     *  follows. */
    private final AnimationTimer hoverTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            hoverStrength += (hoverTarget - hoverStrength) * HOVER_EASE_RATE;
            if (Math.abs(hoverTarget - hoverStrength) < 0.01) {
                hoverStrength = hoverTarget;
                if (hoverTarget <= 0.0) {
                    displayHoverIndex = -1;
                }
                hoverAnimating = false;
                stop();
            }
            draw();
        }
    };

    private void setHoverTarget(double target) {
        hoverTarget = target;
        if (!hoverAnimating) {
            hoverAnimating = true;
            hoverTimer.start();
        }
    }

    public GraphCanvas() {
        getChildren().add(canvas);
        setMinSize(0, 0);
        widthProperty().addListener((o, a, b) -> resize());
        heightProperty().addListener((o, a, b) -> resize());
        installInteraction();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void setOnOpenNote(Consumer<String> handler) {
        this.onOpenNote = handler != null ? handler : id -> { };
    }

    /**
     * Told the zoom the user chooses (wheel, buttons), to keep it for next time; told
     * {@code null} when they reset the view, which goes back to framing the whole graph.
     */
    public void setOnZoomChosen(Consumer<Double> handler) {
        this.onZoomChosen = handler != null ? handler : s -> { };
    }

    /**
     * Makes the next {@link #setData} start the view afresh, as Obsidian opens its graph: centred
     * on the middle of the graph, from close up, zooming out to {@code savedScale}; with no zoom
     * kept ({@code null}) it frames the whole graph while it spreads out, until the user zooms or
     * drags the view. The notes are all placed anew too.
     */
    public void openView(Double savedScale) {
        openPending = true;
        openScale = savedScale != null && savedScale > 0 ? savedScale : null;
    }

    /** Applies the dark/light palette and repaints. */
    public void setDarkTheme(boolean dark) {
        this.palette = dark ? Palette.dark() : Palette.light();
        draw();
    }

    /** Sets the many-body repulsion strength (magnitude; stored negative). */
    public void setRepulsion(double magnitude) {
        this.charge = -Math.abs(magnitude);
        reheat();
    }

    /** Sets the preferred edge (spring) rest length. */
    public void setLinkDistance(double distance) {
        this.linkDistance = Math.max(5, distance);
        reheat();
    }

    /** Sets the strength of the gravity pulling nodes toward the center. */
    public void setCenterGravity(double strength) {
        this.centerGravity = Math.max(0, strength);
        reheat();
    }

    /** Toggles coloring note nodes by their folder group. */
    public void setColorByFolder(boolean enabled) {
        this.colorByFolder = enabled;
        draw();
    }

    /** Sets the spring (link) force multiplier. */
    public void setLinkForce(double force) {
        this.linkForce = Math.max(0, force);
        reheat();
    }

    /** Sets the node radius multiplier. */
    public void setNodeScale(double scaleMul) {
        this.nodeScale = Math.max(0.2, scaleMul);
        draw();
    }

    /** Sets the edge width multiplier. */
    public void setLineThickness(double thickness) {
        this.lineThickness = Math.max(0.2, thickness);
        draw();
    }

    /** Toggles directional arrowheads on edges. */
    public void setShowArrows(boolean show) {
        this.showArrows = show;
        draw();
    }

    /** Sets the minimum zoom at which node labels appear (text fade threshold). */
    public void setLabelThreshold(double threshold) {
        this.labelThreshold = threshold;
        draw();
    }

    /** Current values, for initializing UI controls. */
    public double getRepulsion()     { return Math.abs(charge); }
    public double getLinkDistance()  { return linkDistance; }
    public double getCenterGravity() { return centerGravity; }
    public double getLinkForce()     { return linkForce; }
    public double getNodeScale()     { return nodeScale; }
    public double getLineThickness() { return lineThickness; }
    public double getLabelThreshold(){ return labelThreshold; }

    /** Draws a small arrowhead pointing toward the target node, just outside it. */
    private void drawArrowhead(double x1, double y1, double x2, double y2, double targetR) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-3) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        double tipX = x2 - ux * (targetR + 1);
        double tipY = y2 - uy * (targetR + 1);
        double size = Math.max(4, 5 * Math.min(1.4, scale)) * lineThickness;
        double ang = Math.toRadians(26);
        double cos = Math.cos(ang);
        double sin = Math.sin(ang);
        double lx = tipX - size * (ux * cos - uy * sin);
        double ly = tipY - size * (uy * cos + ux * sin);
        double rx = tipX - size * (ux * cos + uy * sin);
        double ry = tipY - size * (uy * cos - ux * sin);
        g.strokeLine(tipX, tipY, lx, ly);
        g.strokeLine(tipX, tipY, rx, ry);
    }

    /**
     * The colours the colour groups give, by node id; nodes not in the map keep their usual
     * colour. A group's colour wins over "colour by folder", as in Obsidian.
     */
    public void setNodeColors(Map<String, Color> colors) {
        nodeColors = colors != null ? Map.copyOf(colors) : Map.of();
        applyNodeColors();
        drawIfIdle();
    }

    private void applyNodeColors() {
        groupFill = new Color[n];
        for (int i = 0; i < n; i++) {
            groupFill[i] = nodeColors.get(ids[i]);
        }
    }

    /** The colour a node is drawn with when the pointer is not on it; null if it is not shown. */
    public Color fillOf(String nodeId) {
        for (int i = 0; i < n; i++) {
            if (ids[i].equals(nodeId)) {
                return kind[i] == KIND_GHOST ? null : restingFill(i);
            }
        }
        return null;
    }

    private Color restingFill(int i) {
        if (i < groupFill.length && groupFill[i] != null) {
            return groupFill[i];
        }
        if (kind[i] == KIND_TAG) {
            return palette.tag;
        }
        return colorByFolder ? groupColor(group[i]) : palette.node;
    }

    /** Stable pastel color derived from a group key (folder path). */
    private Color groupColor(String key) {
        if (key == null || key.isBlank()) {
            return palette.node;
        }
        int h = key.hashCode();
        double hue = (h & 0x7fffffff) % 360;
        return Color.hsb(hue, 0.45, palette.bg.getBrightness() < 0.5 ? 0.82 : 0.62);
    }

    /**
     * Loads a new graph model, lays out initial positions deterministically and
     * (re)heats the simulation.
     */
    public void setData(GraphData data) {
        boolean opening = openPending;
        // As Obsidian: the notes already on the graph stay where they are; only a fresh view
        // (or an empty graph before) places them all anew.
        Map<String, double[]> kept = opening || n == 0 ? Map.of() : currentPositions();
        buildModel(data != null ? data : GraphData.empty());
        applyNodeColors();
        seedLayout(kept);
        if (opening) {
            openPending = false;
            startView();
        }
        alpha = kept.isEmpty() ? ALPHA_INIT : Math.max(alpha, 0.3);
        draw();
        startTimer();
    }

    private Map<String, double[]> currentPositions() {
        Map<String, double[]> positions = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            positions.put(ids[i], new double[] {x[i], y[i]});
        }
        return positions;
    }

    /** Centred on the middle of the graph, close up (Obsidian starts at zoom 1), easing out. */
    private void startView() {
        scale = 1.0;
        offsetX = width() / 2.0;
        offsetY = height() / 2.0;
        followFit = openScale == null;
        targetScale = openScale != null ? clamp(openScale, MIN_SCALE, MAX_SCALE) : scale;
    }

    /** Zooms in one step about the viewport center. */
    public void zoomIn() {
        zoomBy(1.25, width() / 2.0, height() / 2.0);
    }

    /** Zooms out one step about the viewport center. */
    public void zoomOut() {
        zoomBy(1.0 / 1.25, width() / 2.0, height() / 2.0);
    }

    /** Multiplies the zoom by {@code factor}, keeping the given screen point fixed. */
    private void zoomBy(double factor, double anchorX, double anchorY) {
        zoomTo(clamp(scale * factor, MIN_SCALE, MAX_SCALE), anchorX, anchorY);
        takeView();
        onZoomChosen.accept(scale);
        drawIfIdle();
    }

    private void zoomTo(double newScale, double anchorX, double anchorY) {
        double wx = (anchorX - offsetX) / scale;
        double wy = (anchorY - offsetY) / scale;
        scale = newScale;
        offsetX = anchorX - wx * scale;
        offsetY = anchorY - wy * scale;
    }

    /** The user has taken the view: it stays where they leave it. */
    private void takeView() {
        followFit = false;
        targetScale = scale;
    }

    /**
     * Re-frames the viewport so the whole graph fits with a small margin, and keeps it framed
     * while the graph moves, until the user zooms or drags the view again.
     */
    public void resetView() {
        double[] fit = fitView();
        if (fit == null) {
            scale = 1.0;
            offsetX = width() / 2.0;
            offsetY = height() / 2.0;
        } else {
            scale = fit[0];
            offsetX = fit[1];
            offsetY = fit[2];
        }
        followFit = true;
        targetScale = scale;
        onZoomChosen.accept(null);
        draw();
    }

    /** The zoom and offsets that fit the whole graph in the view, or null with nothing to fit. */
    private double[] fitView() {
        if (n == 0 || width() <= 0 || height() <= 0) {
            return null;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, x[i] - radius[i]);
            minY = Math.min(minY, y[i] - radius[i]);
            maxX = Math.max(maxX, x[i] + radius[i]);
            maxY = Math.max(maxY, y[i] + radius[i]);
        }
        double w = Math.max(1, maxX - minX);
        double h = Math.max(1, maxY - minY);
        double vw = Math.max(1, width());
        double vh = Math.max(1, height());
        double margin = 0.86;
        double fitScale = clamp(Math.min(vw / w, vh / h) * margin, MIN_SCALE, MAX_SCALE);
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        return new double[] {fitScale, vw / 2.0 - cx * fitScale, vh / 2.0 - cy * fitScale};
    }

    /** One frame: a step of the simulation while it runs, the view eased, and a repaint. */
    private void frame() {
        if (alpha >= ALPHA_MIN || dragIndex >= 0) {
            tick();
        }
        easeView();
        draw();
    }

    /**
     * Moves the view a step toward where it should be: framing the whole graph while
     * {@link #followFit}, otherwise toward {@link #targetScale} about the middle of the view.
     * Each frame closes 15 % of the way, as Obsidian's zoom does.
     */
    private void easeView() {
        if (followFit) {
            double[] fit = fitView();
            if (fit == null) {
                return;
            }
            targetScale = fit[0];
            scale = eased(scale, targetScale);
            // Keep the middle of the graph where the fit puts it, at the eased zoom.
            double cx = (width() / 2.0 - fit[1]) / fit[0];
            double cy = (height() / 2.0 - fit[2]) / fit[0];
            offsetX = width() / 2.0 - cx * scale;
            offsetY = height() / 2.0 - cy * scale;
            return;
        }
        if (scale != targetScale) {
            zoomTo(eased(scale, targetScale), width() / 2.0, height() / 2.0);
        }
    }

    private static double eased(double from, double to) {
        double next = to + (from - to) * ZOOM_EASE;
        return Math.abs(next / to - 1) < 0.001 ? to : next;
    }

    /** Whether the view is still easing toward its zoom. */
    private boolean viewMoving() {
        return scale != targetScale;
    }

    /** Runs {@code frames} frames at once, as the view does one per screen refresh (tests). */
    void runFrames(int frames) {
        for (int f = 0; f < frames; f++) {
            frame();
        }
    }

    /** {@code {zoom, offsetX, offsetY}}: where graph point (0,0) is on screen, and the zoom (tests). */
    double[] view() {
        return new double[] {scale, offsetX, offsetY};
    }

    /** {@code {minX, minY, maxX, maxY}} of the nodes' circles on screen (tests). */
    double[] screenBounds() {
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < n; i++) {
            double r = radius[i] * scale;
            box[0] = Math.min(box[0], sx(x[i]) - r);
            box[1] = Math.min(box[1], sy(y[i]) - r);
            box[2] = Math.max(box[2], sx(x[i]) + r);
            box[3] = Math.max(box[3], sy(y[i]) + r);
        }
        return box;
    }

    /** Stops the animation loop (call when the view is hidden). */
    public void pause() {
        stopTimer();
    }

    // ── Model construction ───────────────────────────────────────────────────

    private void buildModel(GraphData data) {
        List<GraphNode> ns = data.nodes();
        List<GraphEdge> es = data.edges();
        n = ns.size();

        x = new double[n];
        y = new double[n];
        vx = new double[n];
        vy = new double[n];
        radius = new double[n];
        kind = new int[n];
        ids = new String[n];
        labels = new String[n];
        group = new String[n];
        linkCount = new int[n];

        Map<String, Integer> index = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            GraphNode node = ns.get(i);
            index.put(node.id(), i);
            ids[i] = node.id();
            labels[i] = node.label() != null ? node.label() : "";
            group[i] = node.group() != null ? node.group() : "";
            kind[i] = switch (node.type()) {
                case TAG -> KIND_TAG;
                case GHOST -> KIND_GHOST;
                default -> KIND_NOTE;
            };
            radius[i] = nodeSize(node.degree());
        }

        // Resolve edges to index pairs, dropping any dangling endpoints.
        List<int[]> pairs = new ArrayList<>(es.size());
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>(2));
        }
        for (GraphEdge e : es) {
            Integer a = index.get(e.source());
            Integer b = index.get(e.target());
            if (a == null || b == null || a.intValue() == b.intValue()) {
                continue;
            }
            pairs.add(new int[] { a, b });
            linkCount[a]++;
            linkCount[b]++;
            adj.get(a).add(b);
            adj.get(b).add(a);
        }

        int m = pairs.size();
        edgeA = new int[m];
        edgeB = new int[m];
        for (int i = 0; i < m; i++) {
            edgeA[i] = pairs.get(i)[0];
            edgeB[i] = pairs.get(i)[1];
        }

        neighbors = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> a = adj.get(i);
            int[] arr = new int[a.size()];
            for (int k = 0; k < arr.length; k++) {
                arr[k] = a.get(k);
            }
            neighbors[i] = arr;
        }

        // Sorted once here (radius is fixed for the model's lifetime) instead of on
        // every draw() — the label pass used to re-sort all n nodes on every frame,
        // including every intermediate event of a zoom/pan gesture.
        Integer[] boxed = new Integer[n];
        for (int i = 0; i < n; i++) {
            boxed[i] = i;
        }
        java.util.Arrays.sort(boxed, (a, b) -> Double.compare(radius[b], radius[a]));
        labelOrder = new int[n];
        for (int i = 0; i < n; i++) {
            labelOrder[i] = boxed[i];
        }

        hoverIndex = -1;
        dragIndex = -1;
    }

    /**
     * Places the notes as Obsidian's graph view does before its simulation starts. A note in
     * {@code kept} stays where it was. Each new one starts beside the notes it links to that are
     * already placed (their mean, plus up to half of {@code sqrt(60·60·new notes)} on each axis),
     * so a cluster starts together wherever its first note fell and stays spread over the graph,
     * instead of forming in the middle of notes scattered all over the disc. A new note with
     * nothing placed to link to starts at random in a ring of area {@code 60·60·new notes}
     * around the notes already there (a disc when there are none). The PRNG is seeded so a
     * layout can be reproduced.
     */
    private void seedLayout(Map<String, double[]> kept) {
        java.util.Random rnd = new java.util.Random(0x5DEECE66DL ^ n);
        boolean[] placed = new boolean[n];
        double keptRadius2 = 0;
        int fresh = 0;
        for (int i = 0; i < n; i++) {
            vx[i] = 0;
            vy[i] = 0;
            double[] p = kept.get(ids[i]);
            if (p != null) {
                x[i] = p[0];
                y[i] = p[1];
                placed[i] = true;
                keptRadius2 = Math.max(keptRadius2, x[i] * x[i] + y[i] * y[i]);
            } else {
                fresh++;
            }
        }
        if (fresh == 0) {
            return;
        }
        double area = 60.0 * 60.0 * fresh;
        double inner = Math.sqrt(keptRadius2);
        double ring = Math.sqrt(area / Math.PI + keptRadius2) - inner;
        double jitter = Math.sqrt(area);
        for (int i = 0; i < n; i++) {
            if (placed[i]) {
                continue;
            }
            double sumX = 0;
            double sumY = 0;
            int count = 0;
            for (int j : neighbors[i]) {
                if (placed[j]) {
                    sumX += x[j];
                    sumY += y[j];
                    count++;
                }
            }
            if (count > 0) {
                x[i] = sumX / count + (rnd.nextDouble() - 0.5) * jitter;
                y[i] = sumY / count + (rnd.nextDouble() - 0.5) * jitter;
            } else {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double r = inner + Math.sqrt(rnd.nextDouble()) * ring;
                x[i] = Math.cos(a) * r;
                y[i] = Math.sin(a) * r;
            }
            placed[i] = true;
        }
    }

    private void reheat() {
        alpha = ALPHA_INIT;
        startTimer();
    }

    // ── Simulation step ──────────────────────────────────────────────────────

    /** The radius of a note node with {@code degree} connections, before zoom and the size slider. */
    static double nodeSize(int degree) {
        return Math.max(BASE_NODE_RADIUS, Math.min(3 * Math.sqrt(Math.max(0, degree) + 1), 30));
    }

    /** Runs {@code ticks} steps of the simulation at once (tests; the view runs them per frame). */
    void runTicks(int ticks) {
        for (int t = 0; t < ticks; t++) {
            tick();
        }
    }

    /** Where a node is, in graph coordinates (the centre of the simulation is 0,0); null if absent. */
    double[] positionOf(String nodeId) {
        for (int i = 0; i < n; i++) {
            if (ids[i].equals(nodeId)) {
                return new double[] {x[i], y[i]};
            }
        }
        return null;
    }

    private void tick() {
        if (n == 0) {
            alpha = 0;
            return;
        }
        alpha += (0 - alpha) * ALPHA_DECAY;

        QuadNode root = buildQuadtree();
        applyManyBody(root);
        applyLinks();
        applyCollisions(root);
        applyCenterGravity();

        for (int i = 0; i < n; i++) {
            if (i == dragIndex) {
                vx[i] = 0;
                vy[i] = 0;
                continue;
            }
            vx[i] = clamp(vx[i] * VELOCITY_DECAY, -MAX_VELOCITY, MAX_VELOCITY);
            vy[i] = clamp(vy[i] * VELOCITY_DECAY, -MAX_VELOCITY, MAX_VELOCITY);
            x[i] += vx[i];
            y[i] += vy[i];
        }
    }

    private void applyCenterGravity() {
        double k = centerGravity * alpha;
        for (int i = 0; i < n; i++) {
            vx[i] -= x[i] * k;
            vy[i] -= y[i] * k;
        }
    }

    private void applyLinks() {
        for (int e = 0; e < edgeA.length; e++) {
            int s = edgeA[e];
            int t = edgeB[e];
            double dx = (x[t] + vx[t]) - (x[s] + vx[s]);
            double dy = (y[t] + vy[t]) - (y[s] + vy[s]);
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < 1e-6) {
                dx = jiggle();
                dy = jiggle();
                d = Math.sqrt(dx * dx + dy * dy);
            }
            // d3's default (stronger springs for low-degree ends), times the link force (1).
            double strength = 1.0 / Math.min(linkCount[s], linkCount[t]);
            double l = (d - linkDistance) / d * alpha * strength * linkForce;
            double fx = dx * l;
            double fy = dy * l;
            // As d3: the end with fewer links moves more (target by bias, source by 1 - bias).
            double bias = (double) linkCount[s] / (linkCount[s] + linkCount[t]);
            vx[t] -= fx * bias;
            vy[t] -= fy * bias;
            vx[s] += fx * (1 - bias);
            vy[s] += fy * (1 - bias);
        }
    }

    // ── Barnes–Hut many-body repulsion ───────────────────────────────────────

    private void applyManyBody(QuadNode root) {
        if (n < 2 || root == null) {
            return;
        }
        for (int i = 0; i < n; i++) {
            accumulateRepulsion(root, i);
        }
    }

    // ── Collision (node circles never overlap once the layout settles) ───────

    private void applyCollisions(QuadNode root) {
        if (n < 2 || root == null) {
            return;
        }
        for (int i = 0; i < n; i++) {
            resolveCollisionsFor(root, i);
        }
    }

    /**
     * Pushes node {@code i} apart from any other node whose circle it overlaps,
     * found via the same quadtree {@link #accumulateRepulsion} uses for repulsion —
     * pruned the same Barnes–Hut way, but on reach ({@code radius[i] + cell's max
     * radius}) instead of the charge/theta accuracy trade-off, since a collision is
     * exact-or-nothing rather than something an approximation is acceptable for.
     *
     * <p>Each pair is resolved only from the lower-index node's traversal ({@code
     * j <= i} is skipped) so overlap isn't corrected twice — once per node — in the
     * same tick, which would double the effective push.</p>
     */
    private void resolveCollisionsFor(QuadNode node, int i) {
        if (node == null) {
            return;
        }
        if (node.leaf) {
            int j = node.index;
            if (j <= i) {
                return;
            }
            double dx = (x[j] + vx[j]) - (x[i] + vx[i]);
            double dy = (y[j] + vy[j]) - (y[i] + vy[i]);
            double minDist = 2 * COLLISION_RADIUS;
            double d2 = dx * dx + dy * dy;
            if (d2 >= minDist * minDist) {
                return;
            }
            double d = Math.sqrt(d2);
            if (d < 1e-6) {
                dx = jiggle();
                dy = jiggle();
                d = Math.sqrt(dx * dx + dy * dy);
            }
            double push = (minDist - d) / d * COLLISION_STRENGTH;
            double fx = dx * push;
            double fy = dy * push;
            vx[j] += fx;
            vy[j] += fy;
            vx[i] -= fx;
            vy[i] -= fy;
            return;
        }
        // Prune whole subtrees no node inside could possibly reach: even the
        // biggest circle in this cell, added to i's own radius, can't bridge the
        // gap to the nearest point of the cell's bounding square.
        double closestX = clamp(x[i], node.x0, node.x0 + node.size);
        double closestY = clamp(y[i], node.y0, node.y0 + node.size);
        double dx = closestX - x[i];
        double dy = closestY - y[i];
        double maxReach = 2 * COLLISION_RADIUS;
        if (dx * dx + dy * dy > maxReach * maxReach) {
            return;
        }
        for (QuadNode child : node.children) {
            if (child != null) {
                resolveCollisionsFor(child, i);
            }
        }
    }

    private void accumulateRepulsion(QuadNode node, int i) {
        if (node == null || (node.leaf && node.index == i)) {
            return;
        }
        double dx = node.cx - x[i];
        double dy = node.cy - y[i];
        double d2 = dx * dx + dy * dy;
        if (d2 < 1e-6) {
            dx = jiggle();
            dy = jiggle();
            d2 = dx * dx + dy * dy;
        }
        // Far enough to treat the whole cell as one body? (size^2 / dist^2 < theta^2)
        if (node.leaf || (node.size * node.size) < THETA2 * d2) {
            if (d2 < CHARGE_MIN_DISTANCE2) {
                d2 = Math.sqrt(CHARGE_MIN_DISTANCE2 * d2); // d3's distanceMin: no huge push up close
            }
            double w = charge * alpha * node.count / d2;
            vx[i] += dx * w;
            vy[i] += dy * w;
            return;
        }
        for (QuadNode child : node.children) {
            if (child != null) {
                accumulateRepulsion(child, i);
            }
        }
    }

    private QuadNode buildQuadtree() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, x[i]);
            minY = Math.min(minY, y[i]);
            maxX = Math.max(maxX, x[i]);
            maxY = Math.max(maxY, y[i]);
        }
        double size = Math.max(maxX - minX, maxY - minY);
        if (size <= 0 || Double.isNaN(size)) {
            return null;
        }
        QuadNode root = new QuadNode(minX, minY, size);
        for (int i = 0; i < n; i++) {
            root.insert(i, x, y);
        }
        root.computeMass(radius);
        return root;
    }

    /** A Barnes–Hut quadtree cell. */
    private static final class QuadNode {
        final double x0, y0, size;
        QuadNode[] children;   // NW, NE, SW, SE
        int index = -1;        // leaf node index, or -1
        int count = 0;         // number of bodies in subtree
        double cx, cy;         // center of mass
        double maxRadius;      // largest node radius anywhere in this subtree (collision pruning)
        boolean leaf = true;

        QuadNode(double x0, double y0, double size) {
            this.x0 = x0;
            this.y0 = y0;
            this.size = size;
        }

        void insert(int i, double[] xs, double[] ys) {
            if (count == 0 && children == null) {
                index = i;
                count = 1;
                cx = xs[i];
                cy = ys[i];
                return;
            }
            if (children == null) {
                // Subdivide and push the existing point down.
                children = new QuadNode[4];
                leaf = false;
                int existing = index;
                index = -1;
                if (existing >= 0) {
                    childFor(xs[existing], ys[existing]).insert(existing, xs, ys);
                }
            }
            count++;
            childFor(xs[i], ys[i]).insert(i, xs, ys);
        }

        private QuadNode childFor(double px, double py) {
            double half = size / 2.0;
            int q = 0;
            double nx = x0;
            double ny = y0;
            if (px >= x0 + half) {
                q |= 1;
                nx = x0 + half;
            }
            if (py >= y0 + half) {
                q |= 2;
                ny = y0 + half;
            }
            if (children[q] == null) {
                children[q] = new QuadNode(nx, ny, half);
            }
            return children[q];
        }

        void computeMass(double[] radius) {
            if (leaf) {
                maxRadius = index >= 0 ? radius[index] : 0;
                return;
            }
            double sx = 0, sy = 0;
            int c = 0;
            double maxR = 0;
            for (QuadNode child : children) {
                if (child == null) {
                    continue;
                }
                child.computeMass(radius);
                sx += child.cx * child.count;
                sy += child.cy * child.count;
                c += child.count;
                maxR = Math.max(maxR, child.maxRadius);
            }
            if (c > 0) {
                cx = sx / c;
                cy = sy / c;
                count = c;
            }
            maxRadius = maxR;
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private void draw() {
        double w = width();
        double h = height();
        g.setFill(palette.bg);
        g.fillRect(0, 0, w, h);
        if (n == 0) {
            return;
        }

        boolean hoveringDisplay = displayHoverIndex >= 0;
        boolean[] active = null;
        if (hoveringDisplay) {
            active = new boolean[n];
            active[displayHoverIndex] = true;
            for (int nb : neighbors[displayHoverIndex]) {
                active[nb] = true;
            }
        }

        // Edges — one pixel on screen at any zoom (times the thickness slider), as Obsidian's.
        // Colors ease between resting and highlighted/dimmed via hoverStrength
        // instead of switching outright, so the whole highlight (edges, nodes,
        // labels below) reads as one smooth transition rather than some parts
        // snapping instantly while others fade.
        g.setLineWidth(lineThickness);
        for (int e = 0; e < edgeA.length; e++) {
            int s = edgeA[e];
            int t = edgeB[e];
            if (!hoveringDisplay) {
                g.setStroke(palette.link);
            } else {
                Color target = (active[s] && active[t]) ? palette.linkActive : palette.linkDim;
                g.setStroke(palette.link.interpolate(target, hoverStrength));
            }
            double x1 = sx(x[s]), y1 = sy(y[s]), x2 = sx(x[t]), y2 = sy(y[t]);
            g.strokeLine(x1, y1, x2, y2);
            if (showArrows) {
                drawArrowhead(x1, y1, x2, y2, screenRadius(t));
            }
        }

        // Nodes
        for (int i = 0; i < n; i++) {
            double r = screenRadius(i);
            double px = sx(x[i]);
            double py = sy(y[i]);
            boolean dim = hoveringDisplay && !active[i];
            g.setGlobalAlpha(dim ? 1.0 - (1.0 - HOVER_DIM_ALPHA) * hoverStrength : 1.0);

            if (kind[i] == KIND_GHOST) {
                // A note that does not exist yet: a faint dot at half opacity, as Obsidian's.
                g.setFill(i == displayHoverIndex ? palette.accent : palette.ghost);
                g.setGlobalAlpha(g.getGlobalAlpha() * GHOST_ALPHA);
                g.fillOval(px - r, py - r, r * 2, r * 2);
            } else {
                Color fill;
                if (i == displayHoverIndex) {
                    fill = palette.node.interpolate(palette.accent, hoverStrength);
                } else {
                    fill = restingFill(i);
                }
                g.setFill(fill);
                g.fillOval(px - r, py - r, r * 2, r * 2);
                if (i == displayHoverIndex) {
                    g.setStroke(palette.nodeRing);
                    g.setLineWidth(1.5);
                    g.setGlobalAlpha(hoverStrength);
                    g.strokeOval(px - r, py - r, r * 2, r * 2);
                }
            }
        }
        g.setGlobalAlpha(1.0);

        drawLabels(active, hoveringDisplay);
    }

    /**
     * Labels in two passes sharing one {@link #placedLabelBoxes} overlap-avoidance
     * list, so whichever pass runs first wins any conflict:
     *
     * <ol>
     * <li>When hovering, the hovered node and its direct neighbours — regardless
     * of zoom. This is the one place a label ignores {@link #labelThreshold}
     * entirely: hovering to see what a note connects to is most useful exactly
     * when zoomed out over the whole graph, which is precisely when the normal
     * zoom-based reveal below would otherwise show nothing. Fades in/out with
     * {@link #hoverStrength} together with the dimming below, and still goes
     * through the same overlap check as everything else — a hub's hover can't
     * flood the screen with a hundred overlapping names, it just places as many
     * as fit, prioritizing its larger (by radius) neighbours first.</li>
     * <li>Everyone else, using the normal per-node zoom-based reveal: each node's
     * label appears once <em>that node's own on-screen circle</em> — not a
     * one-size-fits-all zoom number — crosses a minimum legible size, so a big
     * hub's name shows at a lower zoom than a small note's, the same way a city
     * rotules before a village does on a map as you zoom out. {@link
     * #labelThreshold} calibrates that minimum size, relative to the smallest
     * normal node ({@link #BASE_NODE_RADIUS}); the existing settings slider still
     * drives it, unchanged in meaning for a node at that reference size.</li>
     * </ol>
     */
    private void drawLabels(boolean[] active, boolean hoveringDisplay) {
        Font font = Font.font(Math.max(9, Math.min(15, 11 * Math.max(0.8, scale))));
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(font);
        g.setFill(palette.text);
        double avgCharWidth = font.getSize() * 0.56; // typical proportional sans-serif average
        double labelHeight = font.getSize() + 2;

        placedLabelBoxes.clear();
        if (hoveringDisplay) {
            for (int idx : labelOrder) {
                if (active[idx]) {
                    // Never dimmer than its own zoom-based reveal would already make
                    // it — a neighbour that's already legible on zoom alone shouldn't
                    // visibly dip in brightness while the hover highlight fades out.
                    double opacity = Math.max(hoverStrength, labelFadeIn(idx));
                    placeLabel(idx, avgCharWidth, labelHeight, opacity, false);
                }
            }
        }
        for (int idx : labelOrder) {
            if (active != null && active[idx]) {
                continue; // already placed (or deliberately skipped) by the hover pass above
            }
            double fadeIn = labelFadeIn(idx);
            if (fadeIn <= 0.0) {
                continue;
            }
            placeLabel(idx, avgCharWidth, labelHeight, fadeIn, hoveringDisplay);
        }
        g.setGlobalAlpha(1.0);
    }

    /** The scale at which node {@code idx}'s own on-screen circle first reaches a
     *  legible label size, and how far {@code scale} currently is past that (0 =
     *  not there yet, 1 = fully faded in). A node twice the reference radius
     *  reveals its label at half the zoom a reference-sized node needs. */
    private double labelFadeIn(int idx) {
        double revealScale = labelThreshold * (BASE_NODE_RADIUS / radius[idx]);
        return clamp((scale - revealScale) / (revealScale * 0.75), 0.0, 1.0);
    }

    /**
     * Places one label if it isn't empty, isn't fully off-screen, and doesn't
     * overlap anything already placed this frame (appended to {@link
     * #placedLabelBoxes} on success). {@code O(k)} against the labels placed so
     * far, which self-bounds: a crowded viewport places few labels regardless of
     * how many nodes exist.
     *
     * <p>Width is a cheap chars-times-average-glyph-width estimate, not real font
     * metrics: measuring via a JavaFX Text node's getLayoutBounds() per node per
     * frame forces a real font/layout pass in the toolkit on every call — cheap
     * once, very expensive when it's the inner loop of a 60fps animation. The
     * estimate only needs to be close enough to avoid gross overlaps, not exact.</p>
     *
     * @param opacity base opacity for this label (the zoom fade-in, or {@link
     *                #hoverStrength} for the always-on hover set)
     * @param dim     whether to additionally darken it the way its node circle is
     *                dimmed while something else is hovered
     */
    private void placeLabel(int idx, double avgCharWidth, double labelHeight, double opacity, boolean dim) {
        if (labels[idx].isEmpty()) {
            return;
        }
        double w = width();
        double h = height();
        double r = radius[idx] * scale;
        double cx = sx(x[idx]);
        double cy = sy(y[idx]) + r + 12;
        double halfW = labels[idx].length() * avgCharWidth / 2.0 + 2;
        double minX = cx - halfW, maxX = cx + halfW;
        double minY = cy - labelHeight / 2.0, maxY = cy + labelHeight / 2.0;
        if (maxX < 0 || minX > w || maxY < 0 || minY > h) {
            return; // fully off-screen — cheap reject before any collision math
        }
        for (double[] box : placedLabelBoxes) {
            if (minX < box[2] && maxX > box[0] && minY < box[3] && maxY > box[1]) {
                return; // overlaps an already-placed label
            }
        }
        placedLabelBoxes.add(new double[] { minX, minY, maxX, maxY });
        g.setGlobalAlpha(opacity * (dim ? HOVER_DIM_ALPHA : 1.0));
        g.fillText(labels[idx], cx, cy);
    }

    // ── Interaction ──────────────────────────────────────────────────────────

    private void installInteraction() {
        canvas.setOnScroll(e -> {
            double factor = Math.exp(e.getDeltaY() * 0.0015);
            zoomBy(factor, e.getX(), e.getY());
        });

        canvas.setOnMousePressed(e -> {
            pressX = e.getX();
            pressY = e.getY();
            lastDragX = e.getX();
            lastDragY = e.getY();
            movedSincePress = false;
            takeView();
            int hit = pick(e.getX(), e.getY());
            if (hit >= 0) {
                dragIndex = hit;
                panning = false;
            } else {
                dragIndex = -1;
                panning = true;
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (Math.hypot(e.getX() - pressX, e.getY() - pressY) > CLICK_SLOP) {
                movedSincePress = true;
            }
            if (dragIndex >= 0) {
                x[dragIndex] = (e.getX() - offsetX) / scale;
                y[dragIndex] = (e.getY() - offsetY) / scale;
                vx[dragIndex] = 0;
                vy[dragIndex] = 0;
                alpha = Math.max(alpha, 0.35);
                startTimer();
            } else if (panning) {
                offsetX += e.getX() - lastDragX;
                offsetY += e.getY() - lastDragY;
                lastDragX = e.getX();
                lastDragY = e.getY();
                drawIfIdle();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (!movedSincePress) {
                int hit = pick(e.getX(), e.getY());
                if (hit >= 0 && kind[hit] == KIND_NOTE) {
                    onOpenNote.accept(ids[hit]);
                }
            }
            if (dragIndex >= 0) {
                // Release the node and let it settle naturally.
                alpha = Math.max(alpha, 0.1);
                startTimer();
            }
            dragIndex = -1;
            panning = false;
        });

        canvas.setOnMouseMoved(e -> {
            int hit = pick(e.getX(), e.getY());
            if (hit != hoverIndex) {
                hoverIndex = hit;
                if (hit >= 0) {
                    displayHoverIndex = hit; // switch immediately — only the fade *out* holds the old value
                }
                canvas.setCursor(hit >= 0 ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
                setHoverTarget(hit >= 0 ? 1.0 : 0.0);
            }
        });

        canvas.setOnMouseExited(e -> {
            if (hoverIndex != -1) {
                hoverIndex = -1;
                setHoverTarget(0.0);
            }
        });
    }

    /** Returns the node index under the given screen point, or -1. */
    private int pick(double px, double py) {
        int best = -1;
        double bestD2 = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double r = screenRadius(i) + 4;
            double dx = px - sx(x[i]);
            double dy = py - sy(y[i]);
            double d2 = dx * dx + dy * dy;
            if (d2 <= r * r && d2 < bestD2) {
                bestD2 = d2;
                best = i;
            }
        }
        return best;
    }

    // ── Timer / helpers ──────────────────────────────────────────────────────

    private void startTimer() {
        if (!timerRunning) {
            timerRunning = true;
            timer.start();
        }
    }

    private void stopTimer() {
        if (timerRunning) {
            timerRunning = false;
            timer.stop();
        }
    }

    /** Repaints immediately when the simulation is idle (interaction without physics). */
    private void drawIfIdle() {
        if (!timerRunning) {
            draw();
        }
    }

    private void resize() {
        canvas.setWidth(width());
        canvas.setHeight(height());
        // As Obsidian: what was in the middle of the view stays in the middle.
        offsetX += (width() - lastWidth) / 2.0;
        offsetY += (height() - lastHeight) / 2.0;
        lastWidth = width();
        lastHeight = height();
        if (followFit && n > 0) {
            startTimer(); // re-frame for the new size
        } else {
            draw();
        }
    }

    /** On-screen node radius, floored so nodes stay visible at any zoom level. */
    private double screenRadius(int i) {
        return Math.max(MIN_NODE_PX, radius[i] * scale * nodeScale);
    }

    private double width()  { return Math.max(0, getWidth()); }
    private double height() { return Math.max(0, getHeight()); }
    private double sx(double wx) { return wx * scale + offsetX; }
    private double sy(double wy) { return wy * scale + offsetY; }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double jiggle() {
        return (Math.random() - 0.5) * 1e-3;
    }

    @Override
    protected void layoutChildren() {
        canvas.relocate(0, 0);
        canvas.setWidth(width());
        canvas.setHeight(height());
    }

    // ── Color palette ────────────────────────────────────────────────────────

    /** Dark/light color set, mirroring the app themes. */
    private record Palette(
            Color bg, Color node, Color tag, Color ghost, Color nodeRing,
            Color link, Color linkActive, Color linkDim, Color text, Color accent) {

        static Palette light() {
            return new Palette(
                    Color.web("#f4f4f6"),                 // bg
                    Color.web("#5562c9"),                 // node (saturated indigo, clear on light bg)
                    Color.web("#7c3aed"),                 // tag
                    Color.web("#9aa0bf"),                 // ghost (hollow outline)
                    Color.web("#23263a", 0.55),           // node ring (dark, visible on light bg)
                    Color.web("#3a3f70", 0.30),           // link
                    Color.web("#5b21b6", 0.95),           // link active
                    Color.web("#3a3f70", 0.07),           // link dim
                    Color.web("#2a2e44"),                 // text
                    Color.web("#e0723a"));                // accent / hover (warm, pops on light)
        }

        static Palette dark() {
            return new Palette(
                    Color.web("#1a1a1a"),                 // bg
                    Color.web("#aab6da"),                 // node
                    Color.web("#c39bff"),                 // tag
                    Color.web("#6b7390"),                 // ghost (hollow outline)
                    Color.web("#0d0f16", 0.55),           // node ring (dark halo for separation)
                    Color.web("#ffffff", 0.18),           // link
                    Color.web("#eaf0ff", 0.95),           // link active
                    Color.web("#ffffff", 0.05),           // link dim
                    Color.web("#d4dcf0"),                 // text
                    Color.web("#ffb066"));                // accent / hover
        }
    }
}
