package com.example.jylos.data.dao.filesystem;

import static com.example.jylos.data.dao.filesystem.ListedNoteCharacterizationTest.expected;
import static com.example.jylos.data.dao.filesystem.ListedNoteCharacterizationTest.listed;
import static com.example.jylos.data.dao.filesystem.ListedNoteCharacterizationTest.loaded;
import static com.example.jylos.data.dao.filesystem.ListedNoteCharacterizationTest.shape;
import static com.example.jylos.data.dao.filesystem.ListedNoteCharacterizationTest.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.ExternalChanges;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;

/**
 * The files a watcher reports as changed are applied to the listed vault one by one, without
 * walking it: a note created, changed, renamed or deleted outside the app is listed as reading
 * its file gives, a file whose modified time and size did not change is not read, and the app's
 * own writes are not changes from outside.
 */
class ExternalChangesTest {

    @TempDir
    Path root;
    Path vault;
    NoteDAOFileSystem dao;

    @BeforeEach
    void openVault() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("A.md", "---\nestado: uno\n---\nTexto de A [[B]] #a\n");
        files.put("B.md", "Texto de B\n");
        files.put("Libros/Dune.md", "Dune #libro\n");
        files.put("Libros/Fundación.md", "Fundación #libro\n");
        vault = write(root.resolve("vault"), files);
        dao = loaded(vault, root.resolve("cache/vault.json"));
    }

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aNoteCreatedOutsideIsListedAsItsFileReads() throws Exception {
        Path nueva = vault.resolve("Nueva.md");
        Files.writeString(nueva, "---\ntags:\n  - fuera\n---\nEscrita fuera [[A]]\n", StandardCharsets.UTF_8);

        ExternalChanges changes = dao.applyExternalChanges(List.of(nueva));

        assertEquals(Set.of("Nueva.md"), changes.created());
        assertEquals(expected(vault, "Nueva.md"), shape(listed(dao, "Nueva.md")));
    }

    @Test
    void aNoteChangedOutsideIsListedAgain() throws Exception {
        Path a = vault.resolve("A.md");
        Files.writeString(a, "---\nestado: dos\n---\nOtro texto [[Nueva]] #b\n", StandardCharsets.UTF_8);

        ExternalChanges changes = dao.applyExternalChanges(List.of(a));

        assertEquals(Set.of("A.md"), changes.changed());
        assertEquals(expected(vault, "A.md"), shape(listed(dao, "A.md")));
    }

    @Test
    void aNoteDeletedOutsideLeavesTheList() throws Exception {
        Path b = vault.resolve("B.md");
        Files.delete(b);

        ExternalChanges changes = dao.applyExternalChanges(List.of(b));

        assertEquals(Set.of("B.md"), changes.deleted());
        assertFalse(dao.fetchAllNotes().stream().anyMatch(n -> n.getId().equals("B.md")));
    }

    @Test
    void aNoteRenamedOutsideIsTheOldOneDeletedAndTheNewOneCreated() throws Exception {
        Path b = vault.resolve("B.md");
        Path c = vault.resolve("C.md");
        Files.move(b, c);

        ExternalChanges changes = dao.applyExternalChanges(List.of(b, c));

        assertEquals(Set.of("B.md"), changes.deleted());
        assertEquals(Set.of("C.md"), changes.created());
        assertEquals(expected(vault, "C.md"), shape(listed(dao, "C.md")));
    }

    /**
     * Moving a file keeps its modified time and size: a note gone and one come with the same
     * ones, in the same batch, is the note moved. It is not read again (it may be only in
     * iCloud): what the vault knew of it moves with it.
     */
    @Test
    void aNoteMovedOutsideIsFollowedWithoutReadingIt() throws Exception {
        Path a = vault.resolve("A.md");
        Map<String, Object> before = shape(listed(dao, "A.md"));
        Path moved = vault.resolve("Libros/A movida.md");
        Files.move(a, moved);
        sameSizeOtherBytes(moved);

        ExternalChanges changes = dao.applyExternalChanges(List.of(moved, a));

        assertEquals(Map.of("A.md", "Libros/A movida.md"), changes.renamed());
        Map<String, Object> after = shape(listed(dao, "Libros/A movida.md"));
        after.put("title", before.get("title"));
        assertEquals(before, after, "what the vault knew of it, not the file's bytes");
    }

    /**
     * The list drops notes whose file is gone on its own (every few seconds, as it is read):
     * a note moved outside and dropped that way before the watcher's batch arrives is still
     * found moved.
     */
    @Test
    void aMoveIsFoundEvenIfTheListDroppedTheOldNoteFirst() throws Exception {
        Path b = vault.resolve("B.md");
        Path moved = vault.resolve("B movida.md");
        Files.move(b, moved);
        dao.fetchAllNotes(); // drops B.md, whose file is gone

        ExternalChanges changes = dao.applyExternalChanges(List.of(b, moved));

        assertEquals(Map.of("B.md", "B movida.md"), changes.renamed());
    }

    @Test
    void aFolderRenamedOutsideMovesItsNotes() throws Exception {
        Files.move(vault.resolve("Libros"), vault.resolve("Lecturas"));

        ExternalChanges changes = dao.applyExternalChanges(List.of(vault.resolve("Libros"), vault.resolve("Lecturas")));

        assertEquals(Map.of("Libros/Dune.md", "Lecturas/Dune.md", "Libros/Fundación.md", "Lecturas/Fundación.md"),
                changes.renamed());
    }

    /** Two notes with the same modified time and size: which went where cannot be told, so nothing is guessed. */
    @Test
    void anAmbiguousMoveIsNotGuessed() throws Exception {
        Path b = vault.resolve("B.md");
        Path b2 = vault.resolve("B2.md");
        Files.writeString(b2, Files.readString(b));
        Files.setLastModifiedTime(b2, Files.getLastModifiedTime(b));
        dao.applyExternalChanges(List.of(b2));
        Files.move(b, vault.resolve("X.md"));
        Files.move(b2, vault.resolve("Y.md"));

        ExternalChanges changes = dao.applyExternalChanges(
                List.of(b, b2, vault.resolve("X.md"), vault.resolve("Y.md")));

        assertEquals(Map.of(), changes.renamed());
        assertEquals(Set.of("B.md", "B2.md"), changes.deleted());
        assertEquals(Set.of("X.md", "Y.md"), changes.created());
    }

    private static void sameSizeOtherBytes(Path file) throws Exception {
        FileTime modified = Files.getLastModifiedTime(file);
        int size = (int) Files.size(file);
        Files.write(file, "#z ".repeat(size / 3 + 1).substring(0, size).getBytes(StandardCharsets.US_ASCII));
        Files.setLastModifiedTime(file, modified);
    }

    @Test
    void aFolderAddedOrDeletedOutsideBringsOrTakesItsNotes() throws Exception {
        Path nueva = vault.resolve("Nueva");
        write(vault, Map.of("Nueva/x.md", "x\n", "Nueva/Sub/y.md", "y\n"));

        ExternalChanges added = dao.applyExternalChanges(List.of(nueva));

        assertEquals(Set.of("Nueva/x.md", "Nueva/Sub/y.md"), added.created());
        assertTrue(added.foldersMayHaveChanged());

        Path libros = vault.resolve("Libros");
        for (String name : List.of("Dune.md", "Fundación.md")) {
            Files.delete(libros.resolve(name));
        }
        Files.delete(libros);
        ExternalChanges removed = dao.applyExternalChanges(List.of(libros));

        assertEquals(Set.of("Libros/Dune.md", "Libros/Fundación.md"), removed.deleted());
        assertTrue(removed.foldersMayHaveChanged());
    }

    /** A folder reported (or the vault itself) is brought up to date both ways. */
    @Test
    void aReportedFolderIsBroughtUpToDateBothWays() throws Exception {
        Files.delete(vault.resolve("B.md"));
        Files.writeString(vault.resolve("Libros/Nuevo.md"), "nuevo\n");
        Files.writeString(vault.resolve("A.md"), "A cambiada, más larga que antes\n");

        ExternalChanges changes = dao.applyExternalChanges(List.of(vault));

        assertEquals(Set.of("B.md"), changes.deleted());
        assertEquals(Set.of("Libros/Nuevo.md"), changes.created());
        assertEquals(Set.of("A.md"), changes.changed());
        assertEquals(expected(vault, "A.md"), shape(listed(dao, "A.md")));
    }

    /** iCloud reports a note whose content it took off the disk: its date and size are the same. */
    @Test
    void aFileWhoseDateAndSizeDidNotChangeIsNotRead() throws Exception {
        Path a = vault.resolve("A.md");
        Map<String, Object> before = shape(listed(dao, "A.md"));
        FileTime modified = Files.getLastModifiedTime(a);
        Files.write(a, "#z ".repeat((int) Files.size(a) / 3 + 1).substring(0, (int) Files.size(a))
                .getBytes(StandardCharsets.US_ASCII));
        Files.setLastModifiedTime(a, modified);

        ExternalChanges changes = dao.applyExternalChanges(List.of(a));

        assertTrue(changes.isEmpty(), changes.toString());
        assertEquals(before, shape(listed(dao, "A.md")));
    }

    @Test
    void hiddenTemporaryForeignAndOutsideFilesAreNotNotes() throws Exception {
        write(vault, Map.of(".obsidian/workspace.md", "x", "A.md123456.tmp", "x", "notas.txt", "x"));
        Path outside = Files.writeString(root.resolve("fuera.md"), "x");

        ExternalChanges changes = dao.applyExternalChanges(List.of(vault.resolve(".obsidian/workspace.md"),
                vault.resolve("A.md123456.tmp"), vault.resolve("notas.txt"), outside));

        assertTrue(changes.isEmpty(), changes.toString());
    }

    /** What the app writes itself is already listed: the watcher's report of it changes nothing. */
    @Test
    void theAppsOwnWritesAreNotChangesFromOutside() throws Exception {
        Note created = new Note("Propia", "Escrita en la app");
        String createdId = dao.createNote(created);
        Note a = dao.getNoteById("A.md");
        a.setContent("Editada en la app, más larga que antes [[B]]");
        dao.updateNote(a);
        Note b = dao.getNoteById("B.md");
        b.setTitle("B renombrada");
        dao.updateNote(b);

        ExternalChanges changes = dao.applyExternalChanges(List.of(vault.resolve(createdId), vault.resolve("A.md"),
                vault.resolve("B.md"), vault.resolve("B renombrada.md")));

        assertTrue(changes.created().isEmpty() && changes.changed().isEmpty() && changes.deleted().isEmpty(),
                changes.toString());
    }

    /** A note saved in the app is cached as it was written: the next start does not read it. */
    @Test
    void aNoteSavedInTheAppIsNotReadAgainOnTheNextStart() throws Exception {
        Note a = dao.getNoteById("A.md");
        a.setContent("Guardada en la app [[Destino]] #guardada");
        dao.updateNote(a);
        Map<String, Object> saved = shape(listed(dao, "A.md"));
        Thread.sleep(3000); // the cache is written a moment later
        Path file = vault.resolve("A.md");
        FileTime modified = Files.getLastModifiedTime(file);
        int size = (int) Files.size(file);
        Files.write(file, "#z ".repeat(size / 3 + 1).substring(0, size).getBytes(StandardCharsets.US_ASCII));
        Files.setLastModifiedTime(file, modified);
        EventBus.getInstance().clear();

        NoteDAOFileSystem next = loaded(vault, root.resolve("cache/vault.json"));

        Note listedAgain = listed(next, "A.md");
        assertEquals(List.of("Destino"), listedAgain.getLinkTargets(), "from the cache, not the file's bytes");
        assertTrue(listedAgain.getTags().stream().anyMatch(t -> "guardada".equals(t.getTitle())), saved.toString());
    }
}
