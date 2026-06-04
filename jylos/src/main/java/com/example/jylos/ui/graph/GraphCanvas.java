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
 * <h3>Why native Canvas (not a WebView)</h3>
 * Rendering directly on a JavaFX {@code Canvas} avoids the WebView's
 * software-canvas/requestAnimationFrame overhead and the JS↔Java bridge latency,
 * giving fluid zoom/pan/drag and instant node hit-testing.
 *
 * <h3>Physics</h3>
 * A d3-force-style simulation: many-body repulsion accelerated with a
 * Barnes–Hut quadtree ({@code O(n log n)}), spring attraction along edges, mild
 * centering gravity, and velocity damping with <em>alpha cooling</em>. When the
 * layout settles (alpha below a floor and nothing being dragged) the
 * {@link AnimationTimer} stops, so an idle graph consumes no CPU. Interaction
 * re-heats the simulation only when needed.
 *
 * <h3>Interaction</h3>
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

    // ── Physics tuning (close to Obsidian's defaults) ───────────────────────
    private static final double ALPHA_INIT     = 1.0;
    private static final double ALPHA_MIN      = 0.0015;
    private static final double ALPHA_DECAY    = 0.0135;   // longer settle → fuller spread
    private static final double VELOCITY_DECAY = 0.60;     // fraction of velocity kept per tick
    private static final double CHARGE         = -260.0;   // many-body repulsion (negative = repel)
    private static final double THETA2         = 0.81;     // Barnes–Hut accuracy (theta=0.9)^2
    private static final double LINK_DISTANCE  = 36.0;
    private static final double CENTER_GRAVITY = 0.035;    // weak pull → orphans drift to the rim
    private static final double MAX_VELOCITY   = 160.0;    // clamp to avoid explosions

    // ── Interaction tuning ──────────────────────────────────────────────────
    private static final double MIN_SCALE = 0.02;
    private static final double MAX_SCALE = 8.0;
    private static final double CLICK_SLOP = 4.0;          // px movement still counts as a click
    private static final int    LABEL_NODE_LIMIT = 260;    // above this, labels only near hover
    private static final double MIN_NODE_PX = 2.0;         // floor on on-screen node radius (Obsidian-like dots)

    private final Canvas canvas = new Canvas();
    private final GraphicsContext g = canvas.getGraphicsContext2D();

    // ── Live-adjustable forces (defaults from the constants above) ──────────
    private double charge = CHARGE;
    private double linkDistance = LINK_DISTANCE;
    private double centerGravity = CENTER_GRAVITY;
    private double linkForce = 1.0;        // spring strength multiplier (Obsidian "link force")
    private boolean colorByFolder = false;

    // ── Live-adjustable display options ─────────────────────────────────────
    private double nodeScale = 1.0;        // node radius multiplier
    private double lineThickness = 1.0;    // edge width multiplier
    private boolean showArrows = false;    // draw directional arrowheads on edges
    private double labelThreshold = 0.4;   // min zoom at which labels appear (text fade)

    // ── Node arrays (struct-of-arrays for cache-friendly physics) ───────────
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
    private int[][] neighbors = new int[0][];

    private static final int KIND_NOTE = 0;
    private static final int KIND_TAG = 1;
    private static final int KIND_GHOST = 2;

    // ── Edges (index pairs) + per-node link counts (for spring strength) ────
    private int[] edgeA = new int[0];
    private int[] edgeB = new int[0];
    private int[] linkCount = new int[0];

    // ── View transform (world → screen): screen = world*scale + offset ──────
    private double scale = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;

    // ── Simulation / interaction state ──────────────────────────────────────
    private double alpha = 0;
    private boolean timerRunning = false;
    private int hoverIndex = -1;
    private int dragIndex = -1;
    private boolean panning = false;
    private boolean movedSincePress = false;
    private boolean needsInitialFit = false;
    private double pressX, pressY;        // screen coords at mouse-press
    private double lastDragX, lastDragY;  // screen coords for incremental pan

    private Consumer<String> onOpenNote = id -> { };
    private Palette palette = Palette.light();

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            tick();
            draw();
            if (alpha < ALPHA_MIN && dragIndex < 0) {
                stopTimer();
            }
        }
    };

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
        buildModel(data != null ? data : GraphData.empty());
        seedLayout();
        // If the canvas hasn't been laid out yet, defer the initial framing to
        // the first real resize so the graph isn't pushed off-screen.
        needsInitialFit = width() <= 0 || height() <= 0;
        resetView();
        reheat();
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
        double newScale = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
        double wx = (anchorX - offsetX) / scale;
        double wy = (anchorY - offsetY) / scale;
        scale = newScale;
        offsetX = anchorX - wx * scale;
        offsetY = anchorY - wy * scale;
        drawIfIdle();
    }

    /** Re-frames the viewport so the whole graph fits with a small margin. */
    public void resetView() {
        if (n == 0) {
            scale = 1.0;
            offsetX = width() / 2.0;
            offsetY = height() / 2.0;
            draw();
            return;
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
        scale = clamp(Math.min(vw / w, vh / h) * margin, MIN_SCALE, MAX_SCALE);
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        offsetX = vw / 2.0 - cx * scale;
        offsetY = vh / 2.0 - cy * scale;
        draw();
    }

    /** Stops the animation loop (call when the view is hidden). */
    public void pause() {
        stopTimer();
    }

    // ── Model construction ─────────────────────────────────────────────────────

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
            double base = node.type() == GraphNode.Type.GHOST ? 2.0 : 2.4;
            radius[i] = base + Math.sqrt(Math.max(0, node.degree())) * 1.5;
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

        hoverIndex = -1;
        dragIndex = -1;
    }

    /**
     * Seeds nodes at random positions inside a disk. A random (symmetry-breaking)
     * start is essential: an ordered seed (e.g. phyllotaxis) is itself the
     * uniform-repulsion equilibrium, so the simulation would never break it into
     * Obsidian-like organic clusters. The PRNG is seeded for reproducible layouts.
     */
    private void seedLayout() {
        java.util.Random rnd = new java.util.Random(0x5DEECE66DL ^ n);
        double spread = 60.0 + Math.sqrt(Math.max(1, n)) * 26.0;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = spread * Math.sqrt(rnd.nextDouble());
            x[i] = Math.cos(a) * r;
            y[i] = Math.sin(a) * r;
            vx[i] = 0;
            vy[i] = 0;
        }
    }

    private void reheat() {
        alpha = ALPHA_INIT;
        startTimer();
    }

    // ── Simulation step ────────────────────────────────────────────────────────

    private void tick() {
        if (n == 0) {
            alpha = 0;
            return;
        }
        alpha += (0 - alpha) * ALPHA_DECAY;

        applyManyBody();
        applyLinks();
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
            // Stronger springs for low-degree endpoints (d3's bias heuristic).
            double strength = 1.0 / Math.min(linkCount[s], linkCount[t]);
            double l = (d - linkDistance) / d * alpha * strength * linkForce;
            double fx = dx * l;
            double fy = dy * l;
            double bias = (double) linkCount[s] / (linkCount[s] + linkCount[t]);
            vx[t] -= fx * (1 - bias);
            vy[t] -= fy * (1 - bias);
            vx[s] += fx * bias;
            vy[s] += fy * bias;
        }
    }

    // ── Barnes–Hut many-body repulsion ──────────────────────────────────────────

    private void applyManyBody() {
        if (n < 2) {
            return;
        }
        QuadNode root = buildQuadtree();
        if (root == null) {
            return;
        }
        for (int i = 0; i < n; i++) {
            accumulateRepulsion(root, i);
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
        root.computeMass();
        return root;
    }

    /** A Barnes–Hut quadtree cell. */
    private static final class QuadNode {
        final double x0, y0, size;
        QuadNode[] children;   // NW, NE, SW, SE
        int index = -1;        // leaf node index, or -1
        int count = 0;         // number of bodies in subtree
        double cx, cy;         // center of mass
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

        void computeMass() {
            if (leaf) {
                return;
            }
            double sx = 0, sy = 0;
            int c = 0;
            for (QuadNode child : children) {
                if (child == null) {
                    continue;
                }
                child.computeMass();
                sx += child.cx * child.count;
                sy += child.cy * child.count;
                c += child.count;
            }
            if (c > 0) {
                cx = sx / c;
                cy = sy / c;
                count = c;
            }
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

        boolean hovering = hoverIndex >= 0;
        boolean[] active = null;
        if (hovering) {
            active = new boolean[n];
            active[hoverIndex] = true;
            for (int nb : neighbors[hoverIndex]) {
                active[nb] = true;
            }
        }

        // Edges — thin, constant-ish width so relationships always read clearly.
        g.setLineWidth(clamp(scale, 0.7, 1.6) * lineThickness);
        for (int e = 0; e < edgeA.length; e++) {
            int s = edgeA[e];
            int t = edgeB[e];
            if (!hovering) {
                g.setStroke(palette.link);
            } else if (active[s] && active[t]) {
                g.setStroke(palette.linkActive);
            } else {
                g.setStroke(palette.linkDim);
            }
            double x1 = sx(x[s]), y1 = sy(y[s]), x2 = sx(x[t]), y2 = sy(y[t]);
            g.strokeLine(x1, y1, x2, y2);
            if (showArrows) {
                drawArrowhead(x1, y1, x2, y2, screenRadius(t));
            }
        }

        // Nodes
        boolean drawAllLabels = n <= LABEL_NODE_LIMIT && scale >= labelThreshold;
        for (int i = 0; i < n; i++) {
            double r = screenRadius(i);
            double px = sx(x[i]);
            double py = sy(y[i]);
            boolean dim = hovering && !active[i];
            g.setGlobalAlpha(dim ? 0.28 : 1.0);

            if (kind[i] == KIND_GHOST) {
                // Unresolved link → hollow node (Obsidian style).
                g.setStroke(i == hoverIndex ? palette.accent : palette.ghost);
                g.setLineWidth(1.2);
                g.strokeOval(px - r, py - r, r * 2, r * 2);
            } else {
                Color fill;
                if (i == hoverIndex) {
                    fill = palette.accent;
                } else if (kind[i] == KIND_TAG) {
                    fill = palette.tag;
                } else if (colorByFolder) {
                    fill = groupColor(group[i]);
                } else {
                    fill = palette.node;
                }
                g.setFill(fill);
                g.fillOval(px - r, py - r, r * 2, r * 2);
                if (i == hoverIndex) {
                    g.setStroke(palette.nodeRing);
                    g.setLineWidth(1.5);
                    g.strokeOval(px - r, py - r, r * 2, r * 2);
                }
            }
        }
        g.setGlobalAlpha(1.0);

        // Labels (kept cheap: all when zoomed-in on small graphs; otherwise near hover)
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font(Math.max(9, Math.min(15, 11 * Math.max(0.8, scale)))));
        g.setFill(palette.text);
        for (int i = 0; i < n; i++) {
            boolean near = !hovering || active[i];
            boolean show = drawAllLabels ? (!hovering || active[i]) : (hovering && near);
            if (i == hoverIndex) {
                show = true;
            }
            if (!show || labels[i].isEmpty()) {
                continue;
            }
            double r = radius[i] * scale;
            g.setGlobalAlpha(hovering && !active[i] ? 0.0 : 1.0);
            g.fillText(labels[i], sx(x[i]), sy(y[i]) + r + 12);
        }
        g.setGlobalAlpha(1.0);
    }

    // ── Interaction ────────────────────────────────────────────────────────────

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
                canvas.setCursor(hit >= 0 ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
                drawIfIdle();
            }
        });

        canvas.setOnMouseExited(e -> {
            if (hoverIndex != -1) {
                hoverIndex = -1;
                drawIfIdle();
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

    // ── Timer / helpers ────────────────────────────────────────────────────────

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
        if (needsInitialFit && width() > 0 && height() > 0 && n > 0) {
            needsInitialFit = false;
            resetView();
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
