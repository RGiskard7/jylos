package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;

/**
 * Cleaning a whole vault of the fields older versions of Jylos wrote in every note: only notes
 * whose properties Jylos wrote (id, favorite, pinned and deleted together) are touched, each is
 * first copied to the backup folder at the same path, the fields leave as lines (the rest of
 * the properties stays as written, {@code created} included), a block left empty goes, and the
 * favorite and pinned marks move to Jylos' data unless it already has them.
 */
class VaultLegacyCleanupTest {

    private static final String LEGACY = "---\nid: X.md\ntitle: X\ncreated: '2025-03-01T10:00:00Z'\n"
            + "modified: '2025-03-02T10:00:00Z'\nfavorite: true\npinned: false\ndeleted: false\n";

    @TempDir
    Path vault;
    @TempDir
    Path backups;

    private void write(String path, String text) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private String read(Path root, String path) throws Exception {
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }

    @Test
    void onlyTheNotesWhosePropertiesJylosWroteAreFound() throws Exception {
        write("Vieja.md", LEGACY + "---\n\ntexto\n");
        write("Proyectos/Otra.md", LEGACY + "---\ntexto\n");
        write(".trash/Borrada.md", LEGACY + "---\ntexto\n");
        write("Propia.md", "---\nid: 202401011200\nfavorite: true\n---\ntexto\n");
        write("Obsidian.md", "---\ntags: [a]\n---\ntexto\n");
        write("Simple.md", "texto\n");
        write(".obsidian/plantilla.md", LEGACY + "---\ntexto\n");

        assertEquals(List.of(".trash/Borrada.md", "Proyectos/Otra.md", "Vieja.md"),
                new NoteDAOFileSystem(vault.toString()).notesWithJylosFields());
    }

    @Test
    void theFieldsLeaveAsLinesAfterABackupAndTheMarksMoveToJylosData() throws Exception {
        String old = "---\n# mis notas\nid: Vieja.md\ntitle: Vieja\ncreated: '2025-03-01T10:00:00Z'\n"
                + "modified: '2025-03-02T10:00:00Z'\nfavorite: true\npinned: true\ndeleted: false\n"
                + "fuente: \"con comillas\"\n---\n\ntexto\n";
        write("Proyectos/Vieja.md", old);
        write("Solo.md", LEGACY + "---\n\ntexto solo\n");

        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        int cleaned = dao.cleanJylosFields(dao.notesWithJylosFields(), backups);

        assertEquals(2, cleaned);
        assertEquals(old, read(backups, "Proyectos/Vieja.md"));
        assertEquals("---\n# mis notas\ncreated: '2025-03-01T10:00:00Z'\nfuente: \"con comillas\"\n---\n\ntexto\n",
                read(vault, "Proyectos/Vieja.md"));
        assertEquals("---\ncreated: '2025-03-01T10:00:00Z'\n---\n\ntexto solo\n", read(vault, "Solo.md"));
        Note reread = new NoteDAOFileSystem(vault.toString()).getNoteById("Proyectos/Vieja.md");
        assertEquals(List.of(true, true), List.of(reread.isFavorite(), reread.isPinned()));
        assertEquals(List.of(), new NoteDAOFileSystem(vault.toString()).notesWithJylosFields());
    }

    @Test
    void aBlockWithOnlyJylosFieldsGoesAndMarksJylosAlreadyHasWin() throws Exception {
        write("Nota.md", "---\nid: Nota.md\ntitle: Nota\nfavorite: true\npinned: false\ndeleted: false\n---\n\ntexto\n");
        // Unmarked in Jylos since: Jylos' data says so, and that is what stays.
        write(".jylos/document-metadata.json", "{\"Nota.md\": {\"favorite\": false, \"pinned\": false}}");

        new NoteDAOFileSystem(vault.toString()).cleanJylosFields(List.of("Nota.md"), backups);

        assertEquals("texto\n", read(vault, "Nota.md"));
        assertEquals(false, new NoteDAOFileSystem(vault.toString()).getNoteById("Nota.md").isFavorite());
    }

    @Test
    void aFieldOverSeveralLinesIsStillTakenOutAndANoteJylosDidNotWriteIsLeftAlone() throws Exception {
        write("Larga.md", "---\nid: Larga.md\ntitle: >\n  Un título\n  muy largo\nfavorite: false\npinned: false\n"
                + "deleted: false\nprioridad: 3\n---\ntexto\n");
        String own = "---\nid: 202401011200\nfavorite: true\n---\ntexto\n";
        write("Propia.md", own);

        int cleaned = new NoteDAOFileSystem(vault.toString()).cleanJylosFields(List.of("Larga.md", "Propia.md"), backups);

        assertEquals(1, cleaned);
        String larga = read(vault, "Larga.md");
        assertEquals(List.of(false, false, true), List.of(larga.contains("title"), larga.contains("muy largo"),
                larga.contains("prioridad: 3")), larga);
        assertEquals(own, read(vault, "Propia.md"));
    }
}
