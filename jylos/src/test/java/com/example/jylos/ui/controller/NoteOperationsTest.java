package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * Covers {@link NoteOperations#moveToFolder}, the business logic behind
 * {@code NotesListController.moveNote()}. The destination folder is picked
 * using {@link MoveTargetSupport#buildTargets}, the same target-list builder
 * the move dialog itself uses, so the fixture matches what a real move-target
 * picker would hand the controller.
 */
class NoteOperationsTest {

    @Test
    void moveToFolderDelegatesToFolderServiceWithTheChosenDestination() {
        Folder destination = new Folder("Projects");
        destination.setId("projects-id");

        List<MoveTargetSupport.MoveTarget> targets = MoveTargetSupport.buildTargets(
                List.of(destination), folder -> true, folder -> Optional.empty(), key -> key);
        Folder chosen = targets.stream()
                .filter(t -> "projects-id".equals(t.folder() != null ? t.folder().getId() : null))
                .findFirst().orElseThrow().folder();

        AtomicReference<Note> movedNote = new AtomicReference<>();
        AtomicReference<Folder> movedTo = new AtomicReference<>();
        FolderDAO folderDAO = noOpFolderDao();
        NoteDAO noteDAO = noOpNoteDao();
        FolderService folderService = new FolderService(folderDAO, noteDAO) {
            @Override
            public void moveNoteToFolder(Note note, Folder folder) {
                movedNote.set(note);
                movedTo.set(folder);
            }
        };

        Note note = new Note("Report", "content");
        note.setId("note-1");

        NoteOperations.NoteMoveResult result =
                new NoteOperations(new NoteService(noteDAO, folderDAO), folderService).moveToFolder(note, chosen);

        assertTrue(result.success());
        assertEquals("note-1", result.previousId());
        assertEquals(chosen, result.destination());
        assertEquals(note, movedNote.get());
        assertEquals("projects-id", movedTo.get().getId());
    }

    @Test
    void moveToFolderToRootPassesNullDestinationThrough() {
        FolderDAO folderDAO = noOpFolderDao();
        NoteDAO noteDAO = noOpNoteDao();
        AtomicReference<Folder> movedTo = new AtomicReference<>();
        FolderService folderService = new FolderService(folderDAO, noteDAO) {
            @Override
            public void moveNoteToFolder(Note note, Folder folder) {
                movedTo.set(folder);
            }
        };

        Note note = new Note("Report", "content");
        note.setId("note-2");

        NoteOperations.NoteMoveResult result =
                new NoteOperations(new NoteService(noteDAO, folderDAO), folderService).moveToFolder(note, null);

        assertTrue(result.success());
        assertNull(movedTo.get());
        assertNull(result.destination());
    }

    @Test
    void moveToFolderReportsFailureInsteadOfSwallowingIt() {
        FolderDAO folderDAO = noOpFolderDao();
        NoteDAO noteDAO = noOpNoteDao();
        FolderService folderService = new FolderService(folderDAO, noteDAO) {
            @Override
            public void moveNoteToFolder(Note note, Folder folder) {
                throw new RuntimeException("disk full");
            }
        };

        Note note = new Note("Report", "content");
        note.setId("note-3");

        NoteOperations.NoteMoveResult result =
                new NoteOperations(new NoteService(noteDAO, folderDAO), folderService).moveToFolder(note, null);

        assertFalse(result.success());
        assertEquals("note-3", result.previousId());
    }

    @Test
    void moveToFolderWithoutAFolderServiceReportsFailureInsteadOfThrowing() {
        NoteOperations operations = new NoteOperations(null, null);
        Note note = new Note("Report", "content");
        note.setId("note-4");

        NoteOperations.NoteMoveResult result = operations.moveToFolder(note, null);

        assertFalse(result.success());
    }

    private static NoteDAO noOpNoteDao() {
        return (NoteDAO) Proxy.newProxyInstance(
                NoteDAO.class.getClassLoader(),
                new Class<?>[] { NoteDAO.class },
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static FolderDAO noOpFolderDao() {
        return (FolderDAO) Proxy.newProxyInstance(
                FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class },
                (proxy, method, args) -> defaultValue(method.getReturnType()));
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
