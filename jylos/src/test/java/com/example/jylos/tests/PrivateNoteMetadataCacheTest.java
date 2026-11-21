package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * What the vault caches of its notes is what their files hold: the text of a private note is
 * encrypted there, so nothing it says reaches the cache, though the graph and plugins still see
 * it (read and decrypted, in memory) while the session is unlocked.
 */
class PrivateNoteMetadataCacheTest {

    private static final String SECRET = "SECRETO-no-debe-cachearse";

    @TempDir
    Path root;

    @AfterEach
    void lockAndClear() {
        EncryptionService.getInstance().lock();
        EventBus.getInstance().clear();
    }

    @Test
    void nothingAPrivateNoteSaysIsCachedButTheUnlockedGraphStillHasItsLinks() throws Exception {
        Path vault = root.resolve("vault");
        Files.createDirectories(vault);
        Path cache = root.resolve("cache/vault.json");
        EncryptionService.getInstance().configure("clave-de-prueba".toCharArray());
        NoteService first = new NoteService(new NoteDAOFileSystem(vault.toString(), false, cache),
                new FolderDAOFileSystem(vault.toString()));
        first.createNote(new Note("EnlaceOculto", ""));
        first.createNote(new Note("Abierta", "Texto [[EnlaceOculto]]"));
        Note secret = new Note("Privada", "borrador");
        secret.setPrivate(true);
        secret = first.createNote(secret);
        secret.setContent(SECRET + " [[EnlaceOculto]] ![[oculta.png]] #oculta");
        first.updateNote(secret);
        EventBus.getInstance().clear();

        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), true, cache);
        assertTrue(dao.awaitContentLoaded(20_000));
        NoteService notes = new NoteService(dao, new FolderDAOFileSystem(vault.toString()));
        GraphData graph = new GraphBuilder(notes, new TagService(new TagDAOFileSystem(dao), dao))
                .buildGlobalGraph(new GraphBuilder.Options(false, true, true));
        Note listed = dao.fetchAllNotes().stream().filter(n -> n.getId().equals("Privada.md")).findFirst().orElseThrow();
        Optional<String> derived = notes.derived(listed, "prueba@1", text -> text);
        Note open = dao.fetchAllNotes().stream().filter(n -> n.getId().equals("Abierta.md")).findFirst().orElseThrow();
        assertEquals(Optional.of("Texto [[EnlaceOculto]]"), notes.derived(open, "prueba@1", text -> text));
        Thread.sleep(3000); // the derived values are written a moment later

        assertTrue(graph.edges().stream().anyMatch(e -> e.source().equals("Privada.md")
                && e.target().equals("EnlaceOculto.md")), "unlocked, the graph reads the private note's links");
        assertTrue(derived.orElse("").contains(SECRET), "unlocked, a plugin works from the decrypted text");
        String stored = Files.readString(cache, StandardCharsets.UTF_8);
        assertTrue(stored.contains("prueba@1"), "precondition: derived values are written");
        assertFalse(stored.contains(SECRET), "the decrypted text is never cached");
        assertFalse(stored.contains("oculta"), "nor anything it says");
    }
}
