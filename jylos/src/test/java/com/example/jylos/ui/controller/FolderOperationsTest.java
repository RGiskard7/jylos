package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.service.FolderService;

class FolderOperationsTest {

    @Test
    void createFolderShouldDelegateRootCreationToFolderService() {
        AtomicInteger createFolderCalls = new AtomicInteger();
        AtomicInteger addSubFolderCalls = new AtomicInteger();

        FolderDAO folderDAO = (FolderDAO) Proxy.newProxyInstance(
                FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "createFolder" -> {
                            createFolderCalls.incrementAndGet();
                            Folder folder = (Folder) args[0];
                            yield "generated-" + folder.getTitle();
                        }
                        case "addSubFolder" -> {
                            addSubFolderCalls.incrementAndGet();
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    };
                });
        NoteDAO noteDAO = noOpNoteDao();

        FolderOperations folderOperations = new FolderOperations();
        folderOperations.wire(new FolderService(folderDAO, noteDAO));

        FolderOperations.FolderCreationResult result = folderOperations.createFolder("Inbox", null, true);

        assertTrue(result.success());
        assertNotNull(result.folder());
        assertEquals("generated-Inbox", result.folder().getId());
        assertEquals("Inbox", result.folder().getTitle());
        assertEquals(1, createFolderCalls.get());
        assertEquals(0, addSubFolderCalls.get());
    }

    @Test
    void createFolderShouldDelegateSubfolderCreationToFolderService() {
        AtomicInteger createFolderCalls = new AtomicInteger();
        AtomicInteger addSubFolderCalls = new AtomicInteger();
        AtomicReference<Folder> recordedParent = new AtomicReference<>();
        AtomicReference<Folder> recordedChild = new AtomicReference<>();

        FolderDAO folderDAO = (FolderDAO) Proxy.newProxyInstance(
                FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "createFolder" -> {
                            createFolderCalls.incrementAndGet();
                            Folder folder = (Folder) args[0];
                            yield "generated-" + folder.getTitle();
                        }
                        case "addSubFolder" -> {
                            addSubFolderCalls.incrementAndGet();
                            recordedParent.set((Folder) args[0]);
                            recordedChild.set((Folder) args[1]);
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    };
                });
        NoteDAO noteDAO = noOpNoteDao();

        FolderOperations folderOperations = new FolderOperations();
        folderOperations.wire(new FolderService(folderDAO, noteDAO));

        Folder parent = new Folder("Projects");
        parent.setId("parent-id");

        FolderOperations.FolderCreationResult result = folderOperations.createFolder("Docs", parent, false);

        assertTrue(result.success());
        assertNotNull(result.folder());
        assertEquals("generated-Docs", result.folder().getId());
        assertEquals("Docs", result.folder().getTitle());
        assertEquals(1, createFolderCalls.get());
        assertEquals(1, addSubFolderCalls.get());
        assertSame(parent, recordedParent.get());
        assertSame(result.folder(), recordedChild.get());
    }

    private static NoteDAO noOpNoteDao() {
        return (NoteDAO) Proxy.newProxyInstance(
                NoteDAO.class.getClassLoader(),
                new Class<?>[] { NoteDAO.class },
                (proxy, method, args) -> defaultValue(method.getReturnType()));
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
