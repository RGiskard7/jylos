package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteHistoryService;
import com.example.jylos.service.NoteService;

class NoteServiceRestoreCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void restoreNoteShouldRefreshNoteAndFolderCachesAfterRestore() {
        AtomicInteger restoreNoteCalls = new AtomicInteger();
        AtomicInteger noteRefreshCalls = new AtomicInteger();
        AtomicInteger folderRefreshCalls = new AtomicInteger();

        NoteDAO noteDAO = (NoteDAO) Proxy.newProxyInstance(
                NoteDAO.class.getClassLoader(),
                new Class<?>[] { NoteDAO.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "restoreNote" -> {
                            restoreNoteCalls.incrementAndGet();
                            yield null;
                        }
                        case "refreshCache" -> {
                            noteRefreshCalls.incrementAndGet();
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    };
                });

        FolderDAO folderDAO = (FolderDAO) Proxy.newProxyInstance(
                FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class },
                (proxy, method, args) -> {
                    if ("refreshCache".equals(method.getName())) {
                        folderRefreshCalls.incrementAndGet();
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });

        NoteService noteService = new NoteService(noteDAO, folderDAO);

        noteService.restoreNote("note-1");

        assertEquals(1, restoreNoteCalls.get());
        assertEquals(1, noteRefreshCalls.get());
        assertEquals(1, folderRefreshCalls.get());
    }

    @Test
    void updateCanvasShouldNotReadStoredContentAgainForHistory() {
        AtomicInteger getNoteByIdCalls = new AtomicInteger();
        AtomicInteger updateNoteCalls = new AtomicInteger();

        NoteDAO noteDAO = (NoteDAO) Proxy.newProxyInstance(
                NoteDAO.class.getClassLoader(),
                new Class<?>[] { NoteDAO.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getNoteById" -> {
                            getNoteByIdCalls.incrementAndGet();
                            yield null;
                        }
                        case "updateNote" -> {
                            updateNoteCalls.incrementAndGet();
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    };
                });

        FolderDAO folderDAO = (FolderDAO) Proxy.newProxyInstance(
                FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class },
                (proxy, method, args) -> defaultValue(method.getReturnType()));

        NoteService noteService = new NoteService(noteDAO, folderDAO);
        noteService.setHistoryService(new NoteHistoryService(tempDir, 10, 0));

        Note canvas = new Note("Board.canvas", "{\"nodes\":[],\"edges\":[]}");
        canvas.setId("Board.canvas");

        noteService.updateNote(canvas);

        assertEquals(0, getNoteByIdCalls.get());
        assertEquals(1, updateNoteCalls.get());
    }

    @Test
    void toggleFavoriteShouldPersistWithoutReadingStoredContentForHistory() {
        AtomicInteger getNoteByIdCalls = new AtomicInteger();
        AtomicInteger updateNoteCalls = new AtomicInteger();

        NoteDAO noteDAO = (NoteDAO) Proxy.newProxyInstance(
                NoteDAO.class.getClassLoader(),
                new Class<?>[] { NoteDAO.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getNoteById" -> {
                            getNoteByIdCalls.incrementAndGet();
                            yield null;
                        }
                        case "updateNote" -> {
                            updateNoteCalls.incrementAndGet();
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    };
                });

        FolderDAO folderDAO = (FolderDAO) Proxy.newProxyInstance(
                FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class },
                (proxy, method, args) -> defaultValue(method.getReturnType()));

        NoteService noteService = new NoteService(noteDAO, folderDAO);
        noteService.setHistoryService(new NoteHistoryService(tempDir, 10, 0));

        Note note = new Note("A", "Body");
        note.setId("A.md");

        boolean favorite = noteService.toggleFavorite(note);

        assertTrue(favorite);
        assertEquals(0, getNoteByIdCalls.get());
        assertEquals(1, updateNoteCalls.get());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
