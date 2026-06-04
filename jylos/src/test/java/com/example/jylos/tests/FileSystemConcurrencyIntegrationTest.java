package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

class FileSystemConcurrencyIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentTrashAndRestoreShouldKeepDataConsistent() throws Exception {
        String root = tempDir.resolve("vault").toString();
        NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(root);
        FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(root);
        TagDAOFileSystem tagDAO = new TagDAOFileSystem(noteDAO);
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO, tagDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);

        int noteCount = 6;
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < noteCount; i++) {
            String title = "N" + i;
            titles.add(title);
            Note note = noteService.createNote(title, "content " + i);
            folderService.addNoteToFolder(docs, note);
        }
        assertEquals(noteCount, noteService.getNotesByFolder(docs).size());

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(noteCount);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (String title : titles) {
            pool.submit(() -> {
                try {
                    start.await(3, TimeUnit.SECONDS);

                    Note current = noteService.getNotesByFolder(docs).stream()
                            .filter(n -> title.equals(n.getTitle()))
                            .findFirst()
                            .orElse(null);
                    if (current == null) {
                        throw new IllegalStateException("Missing note before concurrent cycle: " + title);
                    }

                    noteService.moveToTrash(current.getId());

                    Note trashed = noteService.getTrashNotes().stream()
                            .filter(n -> title.equals(n.getTitle()))
                            .findFirst()
                            .orElse(null);
                    if (trashed == null) {
                        throw new IllegalStateException("Missing trashed note: " + title);
                    }

                    noteService.restoreNote(trashed.getId());
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Concurrent operations timed out");
        pool.shutdownNow();

        assertNull(error.get(), "Concurrent trash/restore should not throw exceptions");
        assertEquals(noteCount, noteService.getNotesByFolder(docs).size(),
                "After concurrent trash/restore cycles, all notes must be visible in original folder.");
        assertEquals(0, noteService.getTrashNotes().size(),
                "Trash should be empty after all notes were restored.");
    }

    @Test
    void concurrentTrashReadsAndMutationsShouldNotThrowOrCorruptState() throws Exception {
        String root = tempDir.resolve("vault-read-write").toString();
        NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(root);
        FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(root);
        TagDAOFileSystem tagDAO = new TagDAOFileSystem(noteDAO);
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO, tagDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);
        for (int i = 0; i < 8; i++) {
            Note note = noteService.createNote("N" + i, "content " + i);
            folderService.addNoteToFolder(docs, note);
        }

        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(5);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Runnable mutator = () -> {
            try {
                start.await(3, TimeUnit.SECONDS);
                for (int i = 0; i < 20; i++) {
                    List<Note> current = noteService.getNotesByFolder(docs);
                    if (current.isEmpty()) {
                        continue;
                    }
                    Note candidate = current.get(i % current.size());
                    noteService.moveToTrash(candidate.getId());
                    List<Note> trash = noteService.getTrashNotes();
                    if (!trash.isEmpty()) {
                        noteService.restoreNote(trash.get(0).getId());
                    }
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        };

        Runnable trashReader = () -> {
            try {
                start.await(3, TimeUnit.SECONDS);
                for (int i = 0; i < 80; i++) {
                    noteService.getTrashNotes();
                    folderService.getTrashFolders();
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        };

        pool.submit(mutator);
        pool.submit(mutator);
        pool.submit(mutator);
        pool.submit(trashReader);
        pool.submit(trashReader);

        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS), "Concurrent reader/writer operations timed out");
        pool.shutdownNow();

        assertNull(error.get(), "Concurrent trash reads/writes should not throw exceptions");
        int activeCount = noteService.getNotesByFolder(docs).size();
        int trashCount = noteService.getTrashNotes().size();
        assertEquals(8, activeCount + trashCount,
                "Concurrent trash reads/writes must preserve total note count (active + trash).");
    }
}
