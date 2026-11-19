package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

/**
 * Jylos writes nothing of its own in the notes of a vault, as Obsidian: a note keeps only what
 * the user wrote. Its properties stay byte for byte while they do not change, a note without
 * them is written without them, and the favorite and pinned marks live in Jylos' own data
 * ({@code .jylos/}). A note written by an older Jylos loses its fields at the next save, but
 * not its marks; the same fields written by the user are theirs and stay.
 */
class VaultCleanFilesTest {

    @TempDir
    Path vault;

    private String file(String name) throws Exception {
        return Files.readString(vault.resolve(name), StandardCharsets.UTF_8);
    }

    private NoteService notes() {
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    private void editText(String id, String text) {
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note note = dao.getNoteById(id);
        note.setContent(text);
        dao.updateNote(note);
    }

    @Test
    void unchangedPropertiesStayByteForByte() throws Exception {
        String head = "---\n# un comentario\ntítulo-raro: 'con comillas'\nzeta: 1\nlista:\n    - a\n    - b\n---\n\n";
        Files.writeString(vault.resolve("Nota.md"), head + "texto\n", StandardCharsets.UTF_8);
        String crlfHead = "---\r\nzeta: 1\r\nalfa: \"dos\"\r\n---\r\n";
        Files.writeString(vault.resolve("Windows.md"), crlfHead + "texto\r\n", StandardCharsets.UTF_8);

        editText("Nota.md", "texto cambiado\n");
        editText("Windows.md", "texto cambiado\r\n");

        assertEquals(head + "texto cambiado\n", file("Nota.md"));
        assertEquals(crlfHead + "texto cambiado\r\n", file("Windows.md"));
    }

    /** The properties Jylos reads into its own fields stay as written too: a list stays a list. */
    @Test
    void propertiesReadIntoJylosFieldsStayByteForByte() throws Exception {
        String one = "---\nauthor:\n  - Edu\nstatus: En progreso / Completado\nsource_url: https://example.org/a\n---\n";
        Files.writeString(vault.resolve("Uno.md"), one + "texto\n", StandardCharsets.UTF_8);
        String two = "---\nauthor: [Ana, Luis]\n---\n";
        Files.writeString(vault.resolve("Dos.md"), two + "texto\n", StandardCharsets.UTF_8);

        editText("Uno.md", "texto cambiado\n");
        editText("Dos.md", "texto cambiado\n");

        assertEquals(one + "texto cambiado\n", file("Uno.md"));
        assertEquals(two + "texto cambiado\n", file("Dos.md"));
    }

    /** An empty property stays empty, as Obsidian writes it ({@code key:}), also when another one changes. */
    @Test
    void emptyPropertiesStayEmpty() throws Exception {
        String head = "---\ndescription: algo\ncategory:\nrelated:\ntags:\n---\n";
        Files.writeString(vault.resolve("Nota.md"), head + "texto\n", StandardCharsets.UTF_8);

        editText("Nota.md", "texto cambiado\n");
        assertEquals(head + "texto cambiado\n", file("Nota.md"));

        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note note = dao.getNoteById("Nota.md");
        note.getCustomProperties().put("description", "otra");
        dao.updateNote(note);
        assertEquals("---\ndescription: otra\ncategory:\nrelated:\ntags:\n---\ntexto cambiado\n", file("Nota.md"));
    }

    /**
     * Properties are read as Obsidian reads them, YAML 1.2: {@code no}, {@code yes}, {@code on},
     * {@code 10:30} and {@code 1_000} are text, not a boolean or a number, and stay as they are
     * when another property changes; {@code true}, {@code 12} and {@code 1.5} are a boolean and numbers.
     */
    @Test
    void propertiesAreReadAsObsidianReadsThem() throws Exception {
        String rest = "firmado: no\nlisto: yes\nluz: on\nhora: 10:30\nmil: 1_000\nbin: 0b101\nok: true\nn: 12\nx: 1.5\n";
        Files.writeString(vault.resolve("Nota.md"), "---\ndescription: algo\n" + rest + "---\ntexto\n", StandardCharsets.UTF_8);
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note note = dao.getNoteById("Nota.md");

        assertEquals("no", note.getCustomProperties().get("firmado"));
        assertEquals("10:30", note.getCustomProperties().get("hora"));
        note.getCustomProperties().put("description", "otra");
        dao.updateNote(note);

        assertEquals("---\ndescription: otra\n" + rest + "---\ntexto\n", file("Nota.md"));
    }

    @Test
    void aNoteWithoutPropertiesStaysWithoutThemAndANewOneHasNone() throws Exception {
        Files.writeString(vault.resolve("Simple.md"), "Solo texto.\n", StandardCharsets.UTF_8);
        editText("Simple.md", "Solo texto, editado.\n");
        String id = notes().createNote("Nueva", "Texto nuevo.\n").getId();

        assertEquals(List.of("Solo texto, editado.\n", "Texto nuevo.\n"), List.of(file("Simple.md"), file(id)));
    }

    @Test
    void markingAFavoriteDoesNotTouchTheNoteFile() throws Exception {
        Files.writeString(vault.resolve("Nota.md"), "---\nzeta: 1\n---\ntexto\n", StandardCharsets.UTF_8);
        FileTime old = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(vault.resolve("Nota.md"), old);

        NoteService notes = notes();
        Note note = notes.getNoteById("Nota.md").orElseThrow();
        notes.toggleFavorite(note);
        notes.togglePinned(note);

        assertEquals("---\nzeta: 1\n---\ntexto\n", file("Nota.md"));
        assertEquals(old, Files.getLastModifiedTime(vault.resolve("Nota.md")));
        Note reread = notes().getNoteById("Nota.md").orElseThrow();
        assertEquals(List.of(true, true), List.of(reread.isFavorite(), reread.isPinned()));
        assertTrue(file(".jylos/document-metadata.json").contains("Nota.md"));
    }

    @Test
    void aNoteWrittenByAnOlderJylosLosesItsFieldsButNotItsMarks() throws Exception {
        Files.writeString(vault.resolve("Vieja.md"), """
                ---
                id: Vieja.md
                title: Vieja
                created: '2025-03-01T10:00:00Z'
                modified: '2025-03-02T10:00:00Z'
                favorite: true
                pinned: true
                deleted: false
                prioridad: 3
                ---

                texto
                """, StandardCharsets.UTF_8);
        Note before = notes().getNoteById("Vieja.md").orElseThrow();
        editText("Vieja.md", "texto editado\n");

        String saved = file("Vieja.md");
        for (String field : List.of("id:", "title:", "modified:", "favorite:", "pinned:", "deleted:")) {
            assertFalse(saved.contains(field), field + " in the file: " + saved);
        }
        // Written again without Jylos' fields, the date is plain text, as Obsidian writes it.
        assertTrue(saved.contains("created: 2025-03-01T10:00:00Z\n") && saved.contains("prioridad: 3"), saved);
        Note after = notes().getNoteById("Vieja.md").orElseThrow();
        assertEquals(List.of(true, true, true, true),
                List.of(before.isFavorite(), before.isPinned(), after.isFavorite(), after.isPinned()));
    }

    @Test
    void theSameFieldsWrittenByTheUserAreTheirsAndStay() throws Exception {
        String head = "---\nid: 202401011200\nmodified: ayer\nfavorite: true\n---\n";
        Files.writeString(vault.resolve("Propia.md"), head + "texto\n", StandardCharsets.UTF_8);
        NoteService notes = notes();
        Note note = notes.getNoteById("Propia.md").orElseThrow();
        boolean favoriteAtFirst = note.isFavorite();
        notes.toggleFavorite(note);
        editText("Propia.md", "texto editado\n");

        assertEquals(head + "texto editado\n", file("Propia.md"));
        assertEquals(List.of(true, false), List.of(favoriteAtFirst, notes().getNoteById("Propia.md").orElseThrow().isFavorite()));
    }
}
