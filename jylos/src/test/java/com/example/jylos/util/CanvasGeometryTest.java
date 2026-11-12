package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.CanvasGeometry.Curve;
import com.example.jylos.util.CanvasGeometry.Point;
import com.example.jylos.util.CanvasGeometry.Rect;

class CanvasGeometryTest {

    private static final double EPS = 1e-9;

    // ── Edges ──────────────────────────────────────────────────────────────

    @Test
    void anEdgeLeavesAndEntersEachCardPerpendicularToItsSide() {
        // 400 apart: the control points sit half the distance out, capped at 150.
        Curve curve = CanvasGeometry.edgeCurve(new Point(0, 0), "right", new Point(400, 100), "left");
        assertPoint(0, 0, curve.start());
        assertPoint(150, 0, curve.control1());
        assertPoint(250, 100, curve.control2());
        assertPoint(400, 100, curve.end());

        Curve vertical = CanvasGeometry.edgeCurve(new Point(0, 0), "bottom", new Point(0, 200), "top");
        assertPoint(0, 100, vertical.control1());
        assertPoint(0, 100, vertical.control2());
    }

    @Test
    void theControlDistanceIsHalfTheGapBetween70And150() {
        assertEquals(70, CanvasGeometry.edgeCurve(new Point(0, 0), "right", new Point(40, 0), "left")
                .control1().x(), EPS);
        assertEquals(110, CanvasGeometry.edgeCurve(new Point(0, 0), "right", new Point(220, 0), "left")
                .control1().x(), EPS);
        assertEquals(150, CanvasGeometry.edgeCurve(new Point(0, 0), "right", new Point(1000, 0), "left")
                .control1().x(), EPS);
    }

    @Test
    void anEndWithoutASideGetsNoBend() {
        Curve curve = CanvasGeometry.edgeCurve(new Point(10, 20), "", new Point(300, 20), null);
        assertPoint(10, 20, curve.control1());
        assertPoint(300, 20, curve.control2());
    }

    @Test
    void theArrowPointsAlongTheCurveWhereItArrivesAndTheLabelSitsOnItsMiddle() {
        // Arriving from above into a top side: the arrow points down, whatever the start.
        Curve curve = CanvasGeometry.edgeCurve(new Point(0, 0), "right", new Point(300, 300), "top");
        Point direction = curve.endDirection();
        assertEquals(0, direction.x(), EPS);
        assertEquals(1, direction.y(), EPS);

        Curve straight = CanvasGeometry.edgeCurve(new Point(0, 0), "right", new Point(400, 0), "left");
        assertPoint(200, 0, straight.at(0.5));
        assertPoint(0, 0, straight.at(0));
        assertPoint(400, 0, straight.at(1));
    }

    // ── Snapping ───────────────────────────────────────────────────────────

    @Test
    void theGridGetsCoarserAsTheCanvasZoomsOut() {
        assertEquals(20, CanvasGeometry.gridSpacing(1.0), EPS);
        assertEquals(20, CanvasGeometry.gridSpacing(0.75), EPS);
        assertEquals(40, CanvasGeometry.gridSpacing(0.5), EPS);
        assertEquals(80, CanvasGeometry.gridSpacing(0.39), EPS);
    }

    @Test
    void aCardSnapsToTheGridOnEachAxis() {
        Rect card = new Rect(47, 112, 250, 60);
        assertPoint(40, 120, CanvasGeometry.snap(card, List.of(), 1.0, true, false));
        assertPoint(40, 120, CanvasGeometry.snap(card, List.of(), 0.5, true, false));
        assertPoint(47, 112, CanvasGeometry.snap(card, List.of(), 1.0, false, false));
    }

    @Test
    void aCardSnapsToTheNearestEdgeOrCentreOfAnotherCardWithin15ScreenPixels() {
        Rect other = new Rect(100, 100, 200, 100);
        // Left edges 10 apart: aligned. Vertically nothing is close: free (no grid).
        assertPoint(100, 400, CanvasGeometry.snap(new Rect(110, 400, 200, 50), List.of(other), 1.0, false, true));
        // Its top 12 below the other's bottom: sits right under it.
        assertPoint(500, 200, CanvasGeometry.snap(new Rect(500, 212, 80, 40), List.of(other), 1.0, false, true));
        // Centres: 200 vs 203.
        assertPoint(150, 600, CanvasGeometry.snap(new Rect(153, 600, 100, 40), List.of(other), 1.0, false, true));
        // 20 away is too far at 100 %, but within 15 screen pixels at 50 % zoom.
        assertPoint(120, 400, CanvasGeometry.snap(new Rect(120, 400, 200, 50), List.of(other), 1.0, false, true));
        assertPoint(100, 400, CanvasGeometry.snap(new Rect(120, 400, 200, 50), List.of(other), 0.5, false, true));
    }

    @Test
    void anotherCardWinsOverTheGridOnTheAxisItAligns() {
        Rect other = new Rect(103, 0, 200, 100);
        // x aligns with the card (103), y falls back to the grid (412 -> 420).
        assertPoint(103, 420, CanvasGeometry.snap(new Rect(108, 412, 200, 50), List.of(other), 1.0, true, true));
    }

    // ── Selection box ──────────────────────────────────────────────────────

    @Test
    void theSelectionBoxTakesEveryCardItTouches() {
        Rect box = Rect.between(new Point(300, 300), new Point(100, 100));
        assertTrue(box.intersects(new Rect(250, 250, 100, 100)), "overlapping a corner");
        assertTrue(box.intersects(new Rect(150, 150, 10, 10)), "inside");
        assertFalse(box.intersects(new Rect(300, 100, 50, 50)), "only touching the edge");
        assertFalse(box.intersects(new Rect(400, 400, 50, 50)), "outside");
    }

    private static void assertPoint(double x, double y, Point p) {
        assertEquals(x, p.x(), EPS, "x of " + p);
        assertEquals(y, p.y(), EPS, "y of " + p);
    }
}
