package com.example.jylos.ui.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javafx.scene.paint.Color;

/**
 * The graph is drawn as Obsidian draws it: a node's size on screen follows the square root of
 * the zoom, so a note with many links stays bigger than a lone one when zoomed out; hovering a
 * note lights up only its own links; and links are an opaque colour between the background and
 * the text, so hundreds of them crossing do not add up to a bright patch.
 */
class GraphCanvasLookTest {

    @Test
    void aNodeOnScreenFollowsTheSquareRootOfTheZoom() {
        assertEquals(8.0, GraphCanvas.screenRadiusFor(8, 1, 1), 1e-9, "at zoom 1, its own size");
        assertEquals(4.0, GraphCanvas.screenRadiusFor(8, 0.25, 1), 1e-9, "a quarter of the zoom, half the size");
        assertEquals(16.0, GraphCanvas.screenRadiusFor(8, 4, 1), 1e-9, "four times the zoom, twice the size");
        assertEquals(6.0, GraphCanvas.screenRadiusFor(8, 0.25, 1.5), 1e-9, "times the size slider");
    }

    @Test
    void zoomedOutANoteWithManyLinksIsStillBiggerThanALoneOne() {
        double zoom = 0.125; // the zoom of a whole vault of a few hundred notes
        double lone = GraphCanvas.screenRadiusFor(GraphCanvas.nodeSize(0), zoom, 1);
        double hub = GraphCanvas.screenRadiusFor(GraphCanvas.nodeSize(30), zoom, 1);

        assertEquals(2.83, lone, 0.01, "8·√0.125");
        assertEquals(5.91, hub, 0.01, "3·√31·√0.125");
        assertTrue(hub > lone * 1.8, "the hub reads as bigger: " + hub + " vs " + lone);
    }

    @Test
    void farOutANodeStaysADot() {
        assertEquals(2.0, GraphCanvas.screenRadiusFor(8, 0.02, 1), 1e-9);
    }

    @Test
    void hoveringANoteLightsUpOnlyItsOwnLinks() {
        int hovered = 1;
        assertTrue(GraphCanvas.highlightsEdge(1, 2, hovered), "from it");
        assertTrue(GraphCanvas.highlightsEdge(3, 1, hovered), "to it");
        assertFalse(GraphCanvas.highlightsEdge(2, 3, hovered), "between two of its neighbours");
        assertFalse(GraphCanvas.highlightsEdge(4, 5, hovered), "elsewhere");
    }

    @Test
    void aLinkIsTheOpaqueGreyObsidianUses() {
        Color dark = GraphCanvas.restingLinkColor(Color.web("#1e1e1e"), Color.web("#dadada"));
        Color light = GraphCanvas.restingLinkColor(Color.web("#ffffff"), Color.web("#222222"));

        assertEquals(1.0, dark.getOpacity(), 1e-9, "opaque: crossing links do not add up");
        assertEquals(1.0, light.getOpacity(), 1e-9);
        assertClose(Color.web("#3f3f3f"), dark, "Obsidian's dark --graph-line");
        assertClose(Color.web("#dadada"), light, "Obsidian's light --graph-line");
    }

    private static void assertClose(Color expected, Color actual, String what) {
        double tolerance = 3 / 255.0;
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= tolerance
                && Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance
                && Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance,
                what + ": expected " + expected + " but was " + actual);
    }
}
