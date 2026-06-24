package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.CanvasModel;
import com.example.jylos.util.CanvasModel.CanvasDoc;
import com.example.jylos.util.CanvasModel.CanvasNode;

class CanvasModelTest {

    @Test
    void parsesNodesAndEdges() {
        String json = """
                {
                  "nodes": [
                    {"id":"a","type":"text","x":0,"y":0,"width":200,"height":100,"text":"Hello"},
                    {"id":"b","type":"file","x":300,"y":50,"width":250,"height":120,"file":"Folder/Note.md","color":"4"},
                    {"id":"g","type":"group","x":-20,"y":-20,"width":600,"height":300,"label":"My group"}
                  ],
                  "edges": [
                    {"id":"e1","fromNode":"a","toNode":"b","fromSide":"right","toSide":"left","label":"links to"}
                  ]
                }""";
        CanvasDoc doc = CanvasModel.parse(json);

        assertEquals(3, doc.nodes().size());
        assertEquals(1, doc.edges().size());

        CanvasNode a = doc.nodes().get(0);
        assertEquals("a", a.id());
        assertEquals("text", a.type());
        assertEquals(200, a.width());
        assertEquals("Hello", a.text());

        CanvasNode b = doc.nodes().get(1);
        assertEquals("file", b.type());
        assertEquals("Folder/Note.md", b.file());
        assertEquals("4", b.color());

        var e = doc.edges().get(0);
        assertEquals("a", e.fromNode());
        assertEquals("b", e.toNode());
        assertEquals("right", e.fromSide());
        assertEquals("links to", e.label());
    }

    @Test
    void emptyOrInvalidInputYieldsEmptyDoc() {
        assertTrue(CanvasModel.parse(null).isEmpty());
        assertTrue(CanvasModel.parse("").isEmpty());
        assertTrue(CanvasModel.parse("not json").isEmpty());
        assertTrue(CanvasModel.parse("[]").isEmpty(), "a JSON array (not an object) is not a canvas");
    }

    @Test
    void skipsMalformedEntriesButKeepsValidOnes() {
        String json = """
                {
                  "nodes": [
                    {"type":"text","x":0,"y":0},
                    {"id":"ok","type":"text","x":1,"y":2,"width":10,"height":20,"text":"keep"},
                    "garbage"
                  ],
                  "edges": [
                    {"id":"bad"},
                    {"id":"e","fromNode":"ok","toNode":"ok"}
                  ]
                }""";
        CanvasDoc doc = CanvasModel.parse(json);
        assertEquals(1, doc.nodes().size(), "node without id is dropped, non-object is dropped");
        assertEquals("ok", doc.nodes().get(0).id());
        assertEquals(1, doc.edges().size(), "edge without from/to is dropped");
    }

    @Test
    void missingNumericFieldsDefaultToZero() {
        CanvasDoc doc = CanvasModel.parse("{\"nodes\":[{\"id\":\"a\",\"type\":\"text\"}]}");
        assertEquals(1, doc.nodes().size());
        assertEquals(0, doc.nodes().get(0).x());
        assertEquals(0, doc.nodes().get(0).width());
    }

    @Test
    void documentMoveNodeUpdatesPositionAndPreservesUnknownFields() {
        // Includes a field this app does not model ("styleAttributes") to verify round-trip.
        String json = """
                {"nodes":[
                  {"id":"a","type":"text","x":0,"y":0,"width":200,"height":100,"text":"Hi",
                   "styleAttributes":{"shape":"pill"}}
                ],"edges":[]}""";
        CanvasModel.Document doc = CanvasModel.Document.parse(json);
        doc.moveNode("a", 333.7, 90.2);

        // The in-memory view reflects the move (rounded to integers).
        assertEquals(334, doc.nodes().get(0).x());
        assertEquals(90, doc.nodes().get(0).y());

        // The serialized JSON keeps the unknown field and the new coordinates.
        String out = doc.toJson();
        assertTrue(out.contains("styleAttributes"), out);
        assertTrue(out.contains("pill"));
        assertTrue(out.contains("334"));
    }

    @Test
    void documentParseAddsEmptyArraysAndSerializesEmptyCanvas() {
        CanvasModel.Document doc = CanvasModel.Document.parse(null);
        assertTrue(doc.isEmpty());
        String out = doc.toJson();
        assertTrue(out.contains("\"nodes\""));
        assertTrue(out.contains("\"edges\""));
    }

    @Test
    void documentAddTextNodeCreatesNodeWithGeneratedId() {
        CanvasModel.Document doc = CanvasModel.Document.parse(null);
        String id = doc.addTextNode(10.4, 20.6, 250, 120, "Nueva nota");

        assertEquals(1, doc.nodes().size());
        CanvasNode n = doc.nodes().get(0);
        assertEquals(id, n.id());
        assertEquals("text", n.type());
        assertEquals(10, n.x());
        assertEquals(21, n.y());
        assertEquals(250, n.width());
        assertEquals("Nueva nota", n.text());

        // The new node survives serialization.
        String out = doc.toJson();
        assertTrue(out.contains(id), out);
        assertTrue(out.contains("Nueva nota"));
    }

    @Test
    void documentSetNodeTextUpdatesExistingNode() {
        CanvasModel.Document doc = CanvasModel.Document.parse(null);
        String id = doc.addTextNode(0, 0, 200, 100, "old");
        doc.setNodeText(id, "updated body");

        assertEquals("updated body", doc.nodes().get(0).text());
        assertTrue(doc.toJson().contains("updated body"));
    }

    @Test
    void documentRemoveNodeAlsoRemovesAttachedEdges() {
        String json = """
                {"nodes":[
                  {"id":"a","type":"text","x":0,"y":0,"width":100,"height":50,"text":"A"},
                  {"id":"b","type":"text","x":200,"y":0,"width":100,"height":50,"text":"B"}
                ],"edges":[
                  {"id":"e1","fromNode":"a","toNode":"b"},
                  {"id":"e2","fromNode":"b","toNode":"a"}
                ]}""";
        CanvasModel.Document doc = CanvasModel.Document.parse(json);
        doc.removeNode("a");

        assertEquals(1, doc.nodes().size());
        assertEquals("b", doc.nodes().get(0).id());
        assertEquals(0, doc.edges().size(), "edges touching the removed node are dropped");

        String out = doc.toJson();
        assertTrue(out.contains("\"b\""));
        assertTrue(!out.contains("e1") && !out.contains("e2"), out);
    }
}
