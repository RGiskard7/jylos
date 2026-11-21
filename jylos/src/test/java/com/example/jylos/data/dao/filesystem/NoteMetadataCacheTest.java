package com.example.jylos.data.dao.filesystem;

import static com.example.jylos.data.dao.filesystem.ListedNoteCharacterizationTest.FILLER;
import static com.example.jylos.data.dao.filesystem.ListedNoteCharacterizationTest.expected;
import static com.example.jylos.data.dao.filesystem.ListedNoteCharacterizationTest.fixtures;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.event.EventBus;

/**
 * The second time a vault opens, a note whose file has the same modified time and size is
 * listed from the cache without reading the file (as Obsidian does): with iCloud, reading an
 * offloaded note downloads it. What is listed is exactly what reading the file gives.
 */
class NoteMetadataCacheTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void anUnchangedNoteIsListedFromTheCacheWithoutReadingItsFile(@TempDir Path root) throws Exception {
        Path vault = write(root.resolve("vault"), fixtures());
        Path cache = root.resolve("cache/vault.json");
        Map<String, Map<String, Object>> first = new LinkedHashMap<>();
        NoteDAOFileSystem cold = loaded(vault, cache);
        for (String id : fixtures().keySet()) {
            first.put(id, shape(listed(cold, id)));
        }
        EventBus.getInstance().clear();

        // Same size and modified time, other bytes: only a read of the file can tell.
        for (String id : fixtures().keySet()) {
            sameSizeOtherBytes(vault.resolve(id));
        }
        NoteDAOFileSystem warm = loaded(vault, cache);

        for (String id : fixtures().keySet()) {
            assertEquals(first.get(id), shape(listed(warm, id)), id);
        }
    }

    @Test
    void aNoteChangedOutsideTheAppIsReadAgain(@TempDir Path root) throws Exception {
        Path vault = write(root.resolve("vault"), fixtures());
        Path cache = root.resolve("cache/vault.json");
        loaded(vault, cache);
        EventBus.getInstance().clear();

        Path otherSize = vault.resolve("Sin propiedades.md");
        Files.writeString(otherSize, "Nuevo #cambiado [[Nuevo]]\n", StandardCharsets.UTF_8);
        Path sameSize = vault.resolve("Tarea.md");
        FileTime before = Files.getLastModifiedTime(sameSize);
        String text = Files.readString(sameSize, StandardCharsets.UTF_8).replace("Hacer algo", "Hecho algo");
        Files.writeString(sameSize, text, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(sameSize, FileTime.fromMillis(before.toMillis() + 2000));
        NoteDAOFileSystem warm = loaded(vault, cache);

        assertEquals(expected(vault, "Sin propiedades.md"), shape(listed(warm, "Sin propiedades.md")));
        assertEquals(expected(vault, "Tarea.md"), shape(listed(warm, "Tarea.md")));
        assertTrue(listed(warm, "Tarea.md").getContent().startsWith("Hecho algo"));
    }

    @Test
    void aDeletedNoteLeavesTheCacheFile(@TempDir Path root) throws Exception {
        Path vault = write(root.resolve("vault"), fixtures());
        Path cache = root.resolve("cache/vault.json");
        loaded(vault, cache);
        EventBus.getInstance().clear();
        assertTrue(Files.readString(cache, StandardCharsets.UTF_8).contains("Tarea.md"), "cached at first");

        Files.delete(vault.resolve("Tarea.md"));
        loaded(vault, cache);

        String stored = Files.readString(cache, StandardCharsets.UTF_8);
        assertFalse(stored.contains("Tarea.md"), stored.length() > 300 ? stored.substring(0, 300) : stored);
        assertTrue(stored.contains("Propiedades.md"), "the others stay");
    }

    @Test
    void anUnreadableCacheIsBuiltAgainFromTheFiles(@TempDir Path root) throws Exception {
        Path vault = write(root.resolve("vault"), fixtures());
        Path cache = root.resolve("cache/vault.json");
        Files.createDirectories(cache.getParent());
        Files.writeString(cache, "{ esto no es json", StandardCharsets.UTF_8);

        NoteDAOFileSystem dao = loaded(vault, cache);

        for (String id : fixtures().keySet()) {
            assertEquals(expected(vault, id), shape(listed(dao, id)), id);
        }
        assertTrue(Files.readString(cache, StandardCharsets.UTF_8).contains("Propiedades.md"), "written again");
    }

    /**
     * A refresh (the toolbar button, on the JavaFX thread) lists the new notes at once and
     * writes the cache file, megabytes on a big vault, a moment later on another thread.
     */
    @Test
    void aRefreshWritesTheCacheLaterNotOnTheCallingThread(@TempDir Path root) throws Exception {
        Path vault = write(root.resolve("vault"), fixtures());
        Path cache = root.resolve("cache/vault.json");
        NoteDAOFileSystem dao = loaded(vault, cache);
        Files.writeString(vault.resolve("Nueva.md"), "Nueva [[Destino]]\n", StandardCharsets.UTF_8);

        dao.refreshCache();

        assertEquals(List.of("Destino"), listed(dao, "Nueva.md").getLinkTargets(), "listed at once");
        assertFalse(Files.readString(cache, StandardCharsets.UTF_8).contains("Nueva.md"), "not written yet");
        long deadline = System.currentTimeMillis() + 10_000;
        while (!Files.readString(cache, StandardCharsets.UTF_8).contains("Nueva.md")
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(Files.readString(cache, StandardCharsets.UTF_8).contains("Nueva.md"), "written a moment later");
    }

    /**
     * Closing the vault's session writes what the cache has pending at once, and nothing later:
     * a write scheduled by the closed session must not overwrite the file of the next one.
     */
    @Test
    void closingWritesWhatIsPendingAtOnceAndNothingLater(@TempDir Path root) throws Exception {
        Path vault = write(root.resolve("vault"), fixtures());
        Path cache = root.resolve("cache/vault.json");
        NoteDAOFileSystem first = loaded(vault, cache);
        first.createNote(new Note("Escrita", "en la primera sesión"));

        first.close();

        assertTrue(Files.readString(cache, StandardCharsets.UTF_8).contains("Escrita.md"), "written at once");
        EventBus.getInstance().clear();
        NoteDAOFileSystem second = loaded(vault, cache);
        second.derived("Tarea.md", "segunda@1", text -> "de la segunda");
        second.close();
        // Something of the closed session still touches its cache (a late query, say).
        first.derived("Tarea.md", "vieja@1", text -> "de la primera");
        Thread.sleep(2500); // past when a write it scheduled would be due

        String stored = Files.readString(cache, StandardCharsets.UTF_8);
        assertTrue(stored.contains("segunda@1") && !stored.contains("vieja@1"),
                "the first session wrote nothing after it closed");
    }

    /** A {@code #tag} far into a note is one of its tags, as in Obsidian, not only those of its start. */
    @Test
    void aTagFarIntoANoteIsListed(@TempDir Path root) throws Exception {
        Path vault = write(root.resolve("vault"), Map.of(
                "Corta.md", "palabra ".repeat(200) + "#lejana\n",
                "Larga.md", "---\ntags:\n  - arriba\n---\n" + FILLER + "\n#alfinal\n"));

        NoteDAOFileSystem dao = loaded(vault, root.resolve("cache/vault.json"));

        assertEquals(List.of("lejana"), tags(listed(dao, "Corta.md")));
        assertEquals(List.of("alfinal", "arriba"), tags(listed(dao, "Larga.md")));
    }

    private static List<String> tags(Note note) {
        return note.getTags().stream().map(Tag::getTitle).sorted().toList();
    }

    private static void sameSizeOtherBytes(Path file) throws Exception {
        FileTime modified = Files.getLastModifiedTime(file);
        byte[] bytes = new byte[(int) Files.size(file)];
        java.util.Arrays.fill(bytes, (byte) 'x');
        Files.write(file, bytes);
        Files.setLastModifiedTime(file, modified);
    }
}
