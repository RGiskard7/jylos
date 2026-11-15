package com.example.jylos.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;

/** Each kind of file has its icon wherever notes are listed; a board looks like a board. */
class NoteIconsTest {

    @Test
    void eachKindOfFileHasItsIcon() {
        List<String> icons = List.of(
                NoteIcons.literalFor(note("Nota.md", "texto")),
                NoteIcons.literalFor(note("Tablero.md", "%% jylos-kanban %%\n## Por hacer\n")),
                NoteIcons.literalFor(note("Mapa.canvas", "{}")),
                NoteIcons.literalFor(note("foto.png", "")),
                NoteIcons.literalFor(note("doc.pdf", "")),
                NoteIcons.literalFor(null));

        assertEquals(List.of("fth-file-text", "fth-columns", "fth-grid", "fth-image", "fth-file", "fth-file-text"),
                icons);
    }

    /** A tab shows the icon of what is not a plain note (a board, a canvas, an attachment); a note's tab has none. */
    @Test
    void aTabShowsTheIconOfWhatIsNotAPlainNote() {
        List<String> icons = new ArrayList<>();
        icons.add(NoteIcons.tabIconFor(note("Tablero.md", "%% jylos-kanban %%\n")));
        icons.add(NoteIcons.tabIconFor(note("Mapa.canvas", "{}")));
        icons.add(NoteIcons.tabIconFor(note("Nota.md", "texto")));

        assertEquals(Arrays.asList("fth-columns", "fth-grid", null), icons);
    }

    private static Note note(String id, String content) {
        Note note = new Note(id.replaceAll("\\.[a-z]+$", ""), content);
        note.setId(id);
        return note;
    }
}
