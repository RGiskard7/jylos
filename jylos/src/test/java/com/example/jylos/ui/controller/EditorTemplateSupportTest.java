package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.models.Note;
import com.example.jylos.ui.components.TextEditing;

/**
 * "Insert template" puts the template where the cursor is, as Obsidian does: its text in place
 * of the selection, and its properties added to the note's; a template whose properties are
 * not YAML inserts nothing; and with no note open for editing, nothing happens.
 */
class EditorTemplateSupportTest {

    /** A text and its selection, as the editor keeps them. */
    private static final class FakeEditor implements TextEditing {
        String text;
        int from;
        int to;

        FakeEditor(String text, int from, int to) {
            this.text = text;
            this.from = from;
            this.to = to;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public int getSelectionStart() {
            return from;
        }

        @Override
        public int getSelectionEnd() {
            return to;
        }

        @Override
        public void replaceSelection(String value, int cursorOffset) {
            text = text.substring(0, from) + value + text.substring(to);
            from = to = from + (cursorOffset >= 0 ? cursorOffset : value.length());
        }
    }

    private int changes;

    private EditorTemplateSupport support(FakeEditor editor, Note note) {
        return new EditorTemplateSupport(() -> editor, () -> note, () -> changes++);
    }

    @Test
    void theTemplatesTextGoesInPlaceOfTheSelection() {
        Note note = FrontmatterHandler.parse("Uno dos tres\n");
        FakeEditor editor = new FakeEditor(note.getContent(), 4, 7);

        EditorTemplateSupport.Outcome outcome = support(editor, note).insertTemplate("**plantilla**");

        assertEquals(EditorTemplateSupport.Outcome.INSERTED, outcome);
        assertEquals("Uno **plantilla** tres\n", editor.text);
        assertEquals(1, changes, "the editor is told, so the note counts as changed");
    }

    @Test
    void itsPropertiesAreAddedToTheNotesAndItsTextGoesAtTheCursor() {
        String onDisk = "---\ntags:\n  - proyecto\n---\nNotas\n";
        Note note = FrontmatterHandler.parse(onDisk);
        FakeEditor editor = new FakeEditor(note.getContent(), 5, 5);

        support(editor, note).insertTemplate("---\ntags: [reunión]\nlugar: sala\n---\n\n## Acta\n");

        assertEquals("Notas\n## Acta\n\n", editor.text, "the text after the properties, blank line included");
        assertEquals(Map.of("tags", List.of("proyecto", "reunión"), "lugar", "sala"),
                FrontmatterHandler.propertiesOf(note));
    }

    @Test
    void inANoteWithoutPropertiesTheTemplateBringsItsOwn() {
        Note note = FrontmatterHandler.parse("");
        FakeEditor editor = new FakeEditor("", 0, 0);

        support(editor, note).insertTemplate("---\nestado: borrador\n---\n# Idea\n");

        assertEquals("# Idea\n", editor.text);
        assertEquals(Map.of("estado", "borrador"), FrontmatterHandler.propertiesOf(note));
    }

    @Test
    void propertiesThatAreNotYamlInsertNothing() {
        Note note = FrontmatterHandler.parse("Texto\n");
        FakeEditor editor = new FakeEditor(note.getContent(), 0, 0);

        EditorTemplateSupport.Outcome outcome = support(editor, note).insertTemplate("---\nmal: [\n---\nx");

        assertEquals(EditorTemplateSupport.Outcome.INVALID_PROPERTIES, outcome);
        assertEquals("Texto\n", editor.text);
        assertEquals(0, changes);
    }

    @Test
    void withNoNoteOpenForEditingNothingHappens() {
        FakeEditor editor = new FakeEditor("Texto", 0, 0);

        EditorTemplateSupport.Outcome outcome = support(editor, null).insertTemplate("x");

        assertEquals(EditorTemplateSupport.Outcome.NO_NOTE, outcome);
        assertEquals("Texto", editor.text);
    }

    @Test
    void theTemplateIsWrittenForTheSelectionAndLeavesTheCursorWhereItSays() {
        Note note = FrontmatterHandler.parse("Uno dos tres\n");
        FakeEditor editor = new FakeEditor(note.getContent(), 4, 7);

        support(editor, note).insertTemplate(selection ->
                new EditorTemplateSupport.Written("---\nx: 1\n---\n[" + selection + "] fin", 13 + selection.length() + 2));

        assertEquals("Uno [dos] fin tres\n", editor.text, "the selection, read before it is replaced");
        assertEquals(4 + "[dos]".length(), editor.from, "the cursor where the template put it, past its properties");
    }

    @Test
    void aDateIsWrittenAtTheCursorWithTheCursorAfterIt() {
        Note note = FrontmatterHandler.parse("Hoy: \n");
        FakeEditor editor = new FakeEditor(note.getContent(), 5, 5);

        support(editor, note).insertText("2026-11-19");

        assertEquals("Hoy: 2026-11-19\n", editor.text);
        assertEquals(15, editor.from);
    }
}
