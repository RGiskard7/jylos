package com.example.jylos.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

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

    /** Each kind has a style class the theme can colour it by (only when coloured icons are on). */
    @Test
    void eachKindOfFileHasItsColourClass() {
        assertEquals(List.of("icon-type-note", "icon-type-board", "icon-type-canvas", "icon-type-image", "icon-type-pdf"),
                List.of(NoteIcons.typeClassFor(note("Nota.md", "texto")),
                        NoteIcons.typeClassFor(note("Tablero.md", "%% jylos-kanban %%\n## Por hacer\n")),
                        NoteIcons.typeClassFor(note("Mapa.canvas", "{}")),
                        NoteIcons.typeClassFor(note("foto.png", "")),
                        NoteIcons.typeClassFor(note("doc.pdf", ""))));
        assertEquals("icon-type-folder", NoteIcons.FOLDER_CLASS);
    }

    /** A tab knows its icon by literal: the same colour class, and none for what is not a file. */
    @Test
    void aTabIconsLiteralGivesItsColourClass() {
        assertEquals(Arrays.asList("icon-type-pdf", "icon-type-board", null),
                Arrays.asList(NoteIcons.typeClassForLiteral("fth-file"),
                        NoteIcons.typeClassForLiteral("fth-columns"),
                        NoteIcons.typeClassForLiteral("fth-share-2")));
    }

    /** A row's icon is reused for other notes: it takes the new kind's literal and class, and drops the old class. */
    @Test
    void anIconReusedForAnotherNoteTakesItsKind() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        String seen = FxTestSupport.onFx(() -> {
            FontIcon icon = new FontIcon("fth-file-text");
            icon.getStyleClass().add("feather-icon");
            NoteIcons.apply(icon, note("doc.pdf", ""));
            NoteIcons.apply(icon, note("foto.png", ""));
            return icon.getIconLiteral() + " " + icon.getStyleClass();
        });
        assertEquals("fth-image ikonli-font-icon feather-icon icon-type-image", seen);
    }

    /** Icons made for a list that builds them anew (the trash): a file's kind, or a folder. */
    @Test
    void newIconsForAFileOrAFolderCarryTheirKind() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> seen = FxTestSupport.onFx(() -> {
            FontIcon pdf = NoteIcons.iconFor(note("doc.pdf", ""));
            FontIcon folder = NoteIcons.folderIcon();
            return List.of(pdf.getIconLiteral() + " " + pdf.getStyleClass(),
                    folder.getIconLiteral() + " " + folder.getStyleClass());
        });
        assertEquals(List.of("fth-file ikonli-font-icon icon-type-pdf", "fth-folder ikonli-font-icon icon-type-folder"), seen);
    }

    private static Note note(String id, String content) {
        Note note = new Note(id.replaceAll("\\.[a-z]+$", ""), content);
        note.setId(id);
        return note;
    }
}
