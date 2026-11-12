package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.NoteService;

/**
 * Notes longer than the 16 KB read for the list are indexed in full: a link past that point
 * counts as a backlink, the result is kept between sessions, and only files that changed are
 * read again.
 */
class LongNoteLinksTest {

    private static final String FILLER = "palabra ".repeat(4000); // ~32 KB, past the 16 KB head

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aLinkPastTheHeadIsIndexedInTheBackground(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), false, root.resolve("cache/links.json"));

        assertFalse(linksOf(dao, "Largo.md").contains("Tarde"), "the list only read the head");
        assertEquals(1, dao.pendingLongNoteCount());

        assertEquals(1, dao.indexLongNotes());

        assertTrue(linksOf(dao, "Largo.md").contains("Tarde"));
        assertEquals(0, dao.pendingLongNoteCount());
        assertEquals(0, dao.indexLongNotes(), "nothing left to read");
    }

    @Test
    void theLinkPastTheHeadIsABacklink(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), false, root.resolve("links.json"));
        NoteService notes = new NoteService(dao, new FolderDAOFileSystem(vault.toString()));
        BacklinkService backlinks = new BacklinkService(notes, EventBus.getInstance());
        try {
            Note tarde = notes.getNoteById("Tarde.md").orElseThrow();
            assertEquals(List.of(), backlinks.backlinksFor(tarde));

            dao.indexLongNotes();
            backlinks.invalidate();

            assertEquals(List.of("Largo"), backlinks.backlinksFor(tarde).stream().map(Note::getTitle).toList());
        } finally {
            backlinks.shutdown();
        }
    }

    @Test
    void aSecondSessionStartsWithTheLinksAndReadsNothing(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        Path cache = root.resolve("cache/links.json");
        new NoteDAOFileSystem(vault.toString(), false, cache).indexLongNotes();

        NoteDAOFileSystem second = new NoteDAOFileSystem(vault.toString(), false, cache);

        assertTrue(linksOf(second, "Largo.md").contains("Tarde"), "from the cache, with no indexing run");
        assertEquals(0, second.pendingLongNoteCount());
        assertEquals(0, second.indexLongNotes());
    }

    @Test
    void aFileChangedOutsideTheAppIsReadAgain(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        Path cache = root.resolve("links.json");
        new NoteDAOFileSystem(vault.toString(), false, cache).indexLongNotes();

        Path largo = vault.resolve("Largo.md");
        Files.writeString(largo, FILLER + "[[Tarde]] y [[Otra]]", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(largo, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        NoteDAOFileSystem second = new NoteDAOFileSystem(vault.toString(), false, cache);

        assertEquals(1, second.pendingLongNoteCount());
        assertEquals(1, second.indexLongNotes());
        assertTrue(linksOf(second, "Largo.md").containsAll(List.of("Tarde", "Otra")));
    }

    @Test
    void shortNotesAreNeverQueuedAndTheCallbackReportsTheIndexing(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), false, root.resolve("links.json"));
        AtomicInteger fired = new AtomicInteger();
        dao.setOnLinksIndexed(fired::incrementAndGet);

        assertEquals(1, dao.pendingLongNoteCount(), "only Largo.md; Tarde.md is short");
        dao.indexLongNotes();
        dao.indexLongNotes();

        assertEquals(1, fired.get(), "fired once, when something was indexed");
    }

    @Test
    void deferredStartupIndexesLongNotesOnItsOwn(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), true, root.resolve("links.json"));
        assertTrue(dao.awaitContentLoaded(10_000));

        long deadline = System.currentTimeMillis() + 10_000;
        while (!linksOf(dao, "Largo.md").contains("Tarde") && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(linksOf(dao, "Largo.md").contains("Tarde"));
    }

    private static List<String> linksOf(NoteDAOFileSystem dao, String id) {
        return dao.fetchAllNotes().stream().filter(n -> id.equals(n.getId())).findFirst().orElseThrow()
                .getLinkTargets();
    }

    private static Path vault(Path root, String longBody) throws Exception {
        Path vault = root.resolve("vault");
        Files.createDirectories(vault);
        Files.writeString(vault.resolve("Largo.md"), longBody, StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Tarde.md"), "# Tarde\n", StandardCharsets.UTF_8);
        return vault;
    }
}
