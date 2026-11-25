package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.NoteService;

/**
 * Notes longer than the start the list shows are read in full when the vault loads: a link
 * past that point counts as a backlink, what was read is kept between sessions, and only files
 * that changed are read again.
 */
class LongNoteLinksTest {

    private static final String FILLER = "palabra ".repeat(4000); // ~32 KB, far past the preview

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aLinkPastTheStartIsListed(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), false, root.resolve("cache/links.json"));

        assertTrue(linksOf(dao, "Largo.md").contains("Tarde"));
    }

    /**
     * The vault is opened on the JavaFX thread while the window is being built: the stored
     * cache (megabytes of JSON on a big vault) must be read by the background loader, not
     * there, and still be read once and used.
     */
    @Test
    void theCacheIsReadByTheBackgroundLoaderNotWhereTheVaultIsOpened(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        Path cacheFile = root.resolve("links.json");
        new NoteDAOFileSystem(vault.toString(), false, cacheFile);   // first start: reads in full, fills the cache
        sameSizeOtherBytes(vault.resolve("Largo.md"));

        List<Thread> readers = new CopyOnWriteArrayList<>();
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), true, () -> {
            readers.add(Thread.currentThread());
            return NoteMetadataCache.load(cacheFile);
        });
        assertTrue(dao.awaitContentLoaded(10_000));

        assertEquals(1, readers.size(), "the cache is read once");
        assertTrue(readers.get(0) != Thread.currentThread(), "not on the thread that opened the vault");
        assertTrue(linksOf(dao, "Largo.md").contains("Tarde"), "the stored links are used, not the file's bytes");
    }

    @Test
    void theLinkPastTheStartIsABacklink(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), false, root.resolve("links.json"));
        NoteService notes = new NoteService(dao, new FolderDAOFileSystem(vault.toString()));
        BacklinkService backlinks = new BacklinkService(notes, EventBus.getInstance());
        try {
            Note tarde = notes.getNoteById("Tarde.md").orElseThrow();

            assertEquals(List.of("Largo"), backlinks.backlinksFor(tarde).stream().map(Note::getTitle).toList());
        } finally {
            backlinks.shutdown();
        }
    }

    @Test
    void aFileChangedOutsideTheAppIsReadAgain(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        Path cache = root.resolve("links.json");
        new NoteDAOFileSystem(vault.toString(), false, cache);

        Path largo = vault.resolve("Largo.md");
        Files.writeString(largo, FILLER + "[[Tarde]] y [[Otra]]", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(largo, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        NoteDAOFileSystem second = new NoteDAOFileSystem(vault.toString(), false, cache);

        assertEquals(List.of("Tarde", "Otra"), linksOf(second, "Largo.md"));
    }

    @Test
    void deferredStartupListsTheLinksOfLongNotes(@TempDir Path root) throws Exception {
        Path vault = vault(root, FILLER + "[[Tarde]]");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), true, root.resolve("links.json"));

        assertTrue(dao.awaitContentLoaded(10_000));
        assertTrue(linksOf(dao, "Largo.md").contains("Tarde"));
    }

    private static void sameSizeOtherBytes(Path file) throws Exception {
        FileTime modified = Files.getLastModifiedTime(file);
        byte[] bytes = new byte[(int) Files.size(file)];
        Arrays.fill(bytes, (byte) 'x');
        Files.write(file, bytes);
        Files.setLastModifiedTime(file, modified);
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
