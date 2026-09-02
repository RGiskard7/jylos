package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * Covers {@link NoteOperations#getNotesByFolderRecursive}, the notes panel's
 * held-down "peek" button: every note in a folder AND every subfolder beneath it,
 * however deep — not just what's sitting directly inside it.
 */
class NoteOperationsRecursiveFolderNotesTest {

    @Test
    void collectsNotesFromTheWholeSubtreeNotJustDirectChildren() {
        Folder root = folder("Root");
        Folder docs = folder("Docs");
        Folder deep = folder("Docs/Deep");

        Map<String, List<Folder>> subfoldersById = Map.of(
                "Root", List.of(docs),
                "Docs", List.of(deep),
                "Docs/Deep", List.of());
        Map<String, List<Note>> directNotesById = Map.of(
                "Root", List.of(note("root-note")),
                "Docs", List.of(note("docs-note-1"), note("docs-note-2")),
                "Docs/Deep", List.of(note("deep-note")));

        FolderService folderService = new FolderService(noOpFolderDao(), noOpNoteDao()) {
            @Override
            public List<Folder> getSubfolders(Folder parentFolder) {
                return subfoldersById.getOrDefault(parentFolder.getId(), List.of());
            }
        };
        NoteService noteService = new NoteService(noOpNoteDao(), noOpFolderDao()) {
            @Override
            public List<Note> getNotesByFolder(Folder folder) {
                return directNotesById.getOrDefault(folder.getId(), List.of());
            }
        };

        List<Note> result = new NoteOperations(noteService, folderService).getNotesByFolderRecursive(root);

        assertEquals(4, result.size(),
                "must include Root's own note plus every note in Docs and Docs/Deep");
        assertTrue(result.stream().anyMatch(n -> "root-note".equals(n.getId())));
        assertTrue(result.stream().anyMatch(n -> "docs-note-1".equals(n.getId())));
        assertTrue(result.stream().anyMatch(n -> "docs-note-2".equals(n.getId())));
        assertTrue(result.stream().anyMatch(n -> "deep-note".equals(n.getId())));
    }

    @Test
    void aLeafFolderWithNoSubfoldersJustReturnsItsOwnNotes() {
        Folder leaf = folder("Leaf");
        FolderService folderService = new FolderService(noOpFolderDao(), noOpNoteDao()) {
            @Override
            public List<Folder> getSubfolders(Folder parentFolder) {
                return List.of();
            }
        };
        NoteService noteService = new NoteService(noOpNoteDao(), noOpFolderDao()) {
            @Override
            public List<Note> getNotesByFolder(Folder folder) {
                return List.of(note("only-note"));
            }
        };

        List<Note> result = new NoteOperations(noteService, folderService).getNotesByFolderRecursive(leaf);

        assertEquals(1, result.size());
        assertEquals("only-note", result.get(0).getId());
    }

    private static Folder folder(String id) {
        Folder folder = new Folder(id, null, null);
        folder.setId(id);
        return folder;
    }

    private static Note note(String id) {
        Note note = new Note(id, "content");
        note.setId(id);
        return note;
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
