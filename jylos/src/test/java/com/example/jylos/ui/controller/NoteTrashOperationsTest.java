package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

/**
 * Covers the note trash/restore business rule most likely to cause silent data
 * loss: a private note must never be moved to trash, and a failed DAO call must
 * be reported rather than silently treated as success.
 */
class NoteTrashOperationsTest {

    @Test
    void moveToTrashDelegatesToNoteServiceForANormalNote() {
        AtomicReference<String> deletedId = new AtomicReference<>();
        NoteService noteService = noteServiceWithDao(
                (proxy, method, args) -> {
                    if ("deleteNote".equals(method.getName())) {
                        deletedId.set((String) args[0]);
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });

        Note note = new Note("Meeting notes", "content");
        note.setId("note-1");

        NoteTrashOperations.TrashResult result = new NoteTrashOperations(noteService).moveToTrash(note);

        assertTrue(result.success());
        assertEquals("note-1", deletedId.get());
    }

    @Test
    void moveToTrashRefusesAPrivateNoteWithoutCallingTheDao() {
        AtomicInteger deleteCalls = new AtomicInteger();
        NoteService noteService = noteServiceWithDao(
                (proxy, method, args) -> {
                    if ("deleteNote".equals(method.getName())) {
                        deleteCalls.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });

        Note note = new Note("Secret", "content");
        note.setId("note-2");
        note.setPrivate(true);

        NoteTrashOperations.TrashResult result = new NoteTrashOperations(noteService).moveToTrash(note);

        assertFalse(result.success());
        assertEquals(NoteTrashOperations.FailureReason.PRIVATE_NOTE, result.failureReason());
        assertEquals(0, deleteCalls.get(), "a private note must never reach the DAO delete call");
    }

    @Test
    void moveToTrashReportsFailureInsteadOfSwallowingIt() {
        NoteService noteService = noteServiceWithDao(
                (proxy, method, args) -> {
                    if ("deleteNote".equals(method.getName())) {
                        throw new com.example.jylos.exceptions.DataAccessException("db is gone", null);
                    }
                    return defaultValue(method.getReturnType());
                });

        Note note = new Note("Meeting notes", "content");
        note.setId("note-3");

        NoteTrashOperations.TrashResult result = new NoteTrashOperations(noteService).moveToTrash(note);

        assertFalse(result.success());
        assertEquals(NoteTrashOperations.FailureReason.DELETE_FAILED, result.failureReason());
    }

    @Test
    void moveToTrashWithoutANoteServiceReportsUnavailableInsteadOfThrowing() {
        NoteTrashOperations.TrashResult result = new NoteTrashOperations(null).moveToTrash(new Note("x", ""));

        assertFalse(result.success());
        assertEquals(NoteTrashOperations.FailureReason.NOTE_SERVICE_UNAVAILABLE, result.failureReason());
    }

    @Test
    void restoreDelegatesToNoteService() {
        AtomicReference<String> restoredId = new AtomicReference<>();
        NoteService noteService = noteServiceWithDao(
                (proxy, method, args) -> {
                    if ("restoreNote".equals(method.getName())) {
                        restoredId.set((String) args[0]);
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });

        Note note = new Note("Restored", "content");
        note.setId("note-4");

        NoteTrashOperations.TrashResult result = new NoteTrashOperations(noteService).restore(note);

        assertTrue(result.success());
        assertEquals("note-4", restoredId.get());
    }

    @Test
    void permanentlyDeleteDelegatesToNoteService() {
        AtomicReference<String> deletedId = new AtomicReference<>();
        NoteService noteService = noteServiceWithDao(
                (proxy, method, args) -> {
                    if ("permanentlyDeleteNote".equals(method.getName())) {
                        deletedId.set((String) args[0]);
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });

        Note note = new Note("Gone for good", "content");
        note.setId("note-5");

        NoteTrashOperations.TrashResult result = new NoteTrashOperations(noteService).permanentlyDelete(note);

        assertTrue(result.success());
        assertEquals("note-5", deletedId.get());
    }

    private static NoteService noteServiceWithDao(java.lang.reflect.InvocationHandler handler) {
        NoteDAO noteDAO = (NoteDAO) Proxy.newProxyInstance(
                NoteDAO.class.getClassLoader(), new Class<?>[] { NoteDAO.class }, handler);
        FolderDAO folderDAO = (FolderDAO) Proxy.newProxyInstance(
                FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class },
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new NoteService(noteDAO, folderDAO);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }
}
