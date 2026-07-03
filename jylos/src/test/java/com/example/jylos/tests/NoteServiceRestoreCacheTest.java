package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.service.NoteService;

class NoteServiceRestoreCacheTest {

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
