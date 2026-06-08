package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.KanbanModel;

/**
 * The Kanban board is stored as Markdown inside a note ({@code ## column} /
 * {@code - card}) with a hidden marker. Parsing and serialising must round-trip.
 */
class KanbanModelTest {

    @Test
    void detectsBoardsByMarker() {
        assertTrue(KanbanModel.isBoard(KanbanModel.MARKER + "\n## To do\n- x\n"));
        assertFalse(KanbanModel.isBoard("# Just a note\nsome text"));
        assertFalse(KanbanModel.isBoard(null));
    }

    @Test
    void parsesColumnsAndCards() {
        String md = KanbanModel.MARKER + "\n\n## To do\n- Write summary\n- [[Refs]]\n\n## Done\n- Ship\n";
        KanbanModel m = KanbanModel.parse(md);
        assertEquals(2, m.getColumns().size());
        assertEquals("To do", m.getColumns().get(0).getTitle());
        assertEquals(List.of("Write summary", "[[Refs]]"), m.getColumns().get(0).getCards());
        assertEquals("Done", m.getColumns().get(1).getTitle());
        assertEquals(List.of("Ship"), m.getColumns().get(1).getCards());
    }

    @Test
    void roundTripsThroughMarkdown() {
        KanbanModel m = KanbanModel.withDefaults("To do", "Doing", "Done");
        m.getColumns().get(0).getCards().add("Task A");
        m.getColumns().get(1).getCards().add("[[Note B]]");

        KanbanModel reparsed = KanbanModel.parse(m.toMarkdown());

        assertTrue(KanbanModel.isBoard(m.toMarkdown()), "serialised board keeps the marker");
        assertEquals(3, reparsed.getColumns().size());
        assertEquals(List.of("Task A"), reparsed.getColumns().get(0).getCards());
        assertEquals(List.of("[[Note B]]"), reparsed.getColumns().get(1).getCards());
        assertTrue(reparsed.getColumns().get(2).getCards().isEmpty());
    }
}
