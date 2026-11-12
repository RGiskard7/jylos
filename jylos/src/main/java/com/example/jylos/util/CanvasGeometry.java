package com.example.jylos.util;

import java.util.List;

/**
 * The geometry of a canvas, in canvas coordinates and without any UI: the curve an edge
 * draws between two cards, where a card snaps to while it is moved, and which cards a
 * selection box takes.
 */
public final class CanvasGeometry {

    /** How far a card has to be from a line of another card to snap to it, in screen pixels. */
    private static final double OBJECT_SNAP_PX = 15;
    /** The bend of an edge: half the gap between its ends, within these bounds. */
    private static final double MIN_BEND = 70;
    private static final double MAX_BEND = 150;

    private CanvasGeometry() {
    }

    public record Point(double x, double y) {
    }

    public record Rect(double x, double y, double width, double height) {

        /** The rectangle spanned by two corners, in any order (a selection box being dragged). */
        public static Rect between(Point a, Point b) {
            return new Rect(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()),
                    Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
        }

        /** Whether the point is on this rectangle (edges included). */
        public boolean contains(Point p) {
            return p.x() >= x && p.x() <= x + width && p.y() >= y && p.y() <= y + height;
        }

        /** Whether the two overlap; only touching along an edge does not count. */
        public boolean intersects(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    /** A cubic Bézier from {@code start} to {@code end}. */
    public record Curve(Point start, Point control1, Point control2, Point end) {

        /** The point at {@code t} (0 = start, 1 = end). */
        public Point at(double t) {
            double u = 1 - t;
            double a = u * u * u;
            double b = 3 * u * u * t;
            double c = 3 * u * t * t;
            double d = t * t * t;
            return new Point(a * start.x() + b * control1.x() + c * control2.x() + d * end.x(),
                    a * start.y() + b * control1.y() + c * control2.y() + d * end.y());
        }

        /** The unit direction the curve arrives at its end with, for the arrowhead. */
        public Point endDirection() {
            double dx = end.x() - control2.x();
            double dy = end.y() - control2.y();
            if (Math.hypot(dx, dy) < 1e-9) {
                dx = end.x() - start.x();
                dy = end.y() - start.y();
            }
            double length = Math.hypot(dx, dy);
            return length < 1e-9 ? new Point(0, 0) : new Point(dx / length, dy / length);
        }
    }

    /**
     * The curve of an edge between two card sides: it leaves the first card and enters the
     * second perpendicular to their sides ({@code left}, {@code right}, {@code top},
     * {@code bottom}), bending by half the distance between its ends, between 70 and 150. An
     * end without a side does not bend.
     */
    public static Curve edgeCurve(Point start, String fromSide, Point end, String toSide) {
        double bend = Math.max(MIN_BEND, Math.min(MAX_BEND,
                Math.hypot(end.x() - start.x(), end.y() - start.y()) / 2));
        return new Curve(start, outward(start, fromSide, bend), outward(end, toSide, bend), end);
    }

    private static Point outward(Point point, String side, double distance) {
        if (side == null) {
            return point;
        }
        return switch (side) {
            case "left" -> new Point(point.x() - distance, point.y());
            case "right" -> new Point(point.x() + distance, point.y());
            case "top" -> new Point(point.x(), point.y() - distance);
            case "bottom" -> new Point(point.x(), point.y() + distance);
            default -> point;
        };
    }

    /** The grid cards snap to: coarser as the canvas zooms out. */
    public static double gridSpacing(double zoom) {
        return zoom < 0.4 ? 80 : zoom < 0.75 ? 40 : 20;
    }

    /**
     * Where a card being moved to {@code moving} ends up (its top-left corner). On each axis
     * it lines up with the nearest edge or centre of another card within 15 screen pixels
     * ({@code toObjects}); on an axis where nothing is that close, it snaps to the grid
     * ({@code toGrid}).
     */
    public static Point snap(Rect moving, List<Rect> others, double zoom, boolean toGrid, boolean toObjects) {
        double x = moving.x();
        double y = moving.y();
        double w = moving.width();
        double h = moving.height();
        Double shiftX = null;
        Double shiftY = null;
        if (toObjects) {
            double threshold = OBJECT_SNAP_PX / zoom;
            double bestX = Double.POSITIVE_INFINITY;
            double bestY = Double.POSITIVE_INFINITY;
            for (Rect other : others) {
                double[][] xPairs = {
                    {x, other.x()}, {x + w, other.x() + other.width()},
                    {x + w / 2, other.x() + other.width() / 2},
                    {x, other.x() + other.width()}, {x + w, other.x()},
                };
                for (double[] pair : xPairs) {
                    double gap = Math.abs(pair[0] - pair[1]);
                    if (gap < threshold && gap < bestX) {
                        bestX = gap;
                        shiftX = pair[1] - pair[0];
                    }
                }
                double[][] yPairs = {
                    {y, other.y()}, {y + h, other.y() + other.height()},
                    {y + h / 2, other.y() + other.height() / 2},
                    {y, other.y() + other.height()}, {y + h, other.y()},
                };
                for (double[] pair : yPairs) {
                    double gap = Math.abs(pair[0] - pair[1]);
                    if (gap < threshold && gap < bestY) {
                        bestY = gap;
                        shiftY = pair[1] - pair[0];
                    }
                }
            }
        }
        if (toGrid) {
            double spacing = gridSpacing(zoom);
            if (shiftX == null) {
                shiftX = Math.round(x / spacing) * spacing - x;
            }
            if (shiftY == null) {
                shiftY = Math.round(y / spacing) * spacing - y;
            }
        }
        return new Point(x + (shiftX != null ? shiftX : 0), y + (shiftY != null ? shiftY : 0));
    }

    /**
     * The side of {@code target} that faces {@code source}: where a new edge from the source
     * enters it.
     */
    public static String facingSide(Rect source, Rect target) {
        double dx = target.x() + target.width() / 2 - (source.x() + source.width() / 2);
        double dy = target.y() + target.height() / 2 - (source.y() + source.height() / 2);
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? "left" : "right";
        }
        return dy > 0 ? "top" : "bottom";
    }

    /**
     * A card resized by dragging one of its sides or corners ({@code top}, {@code right},
     * {@code bottom}, {@code left}, {@code top-left}, ...) by {@code (dx, dy)}. It does not get
     * smaller than {@code minWidth} x {@code minHeight}, and the opposite side stays where it
     * was; with a {@code grid} above 0 the dragged sides land on it.
     */
    public static Rect resize(Rect start, String handle, double dx, double dy,
            double minWidth, double minHeight, double grid) {
        double left = start.x();
        double top = start.y();
        double right = start.x() + start.width();
        double bottom = start.y() + start.height();
        if (handle.contains("left")) {
            left = Math.min(onGrid(left + dx, grid), right - minWidth);
        }
        if (handle.contains("right")) {
            right = Math.max(onGrid(right + dx, grid), left + minWidth);
        }
        if (handle.contains("top")) {
            top = Math.min(onGrid(top + dy, grid), bottom - minHeight);
        }
        if (handle.contains("bottom")) {
            bottom = Math.max(onGrid(bottom + dy, grid), top + minHeight);
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    private static double onGrid(double value, double grid) {
        return grid > 0 ? Math.round(value / grid) * grid : value;
    }
}
