package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ToggleButton;

/**
 * The "show all descendants" button is a real persisted toggle (like the app's other
 * toggle buttons — show tags, local graph, …), not a momentary press-and-hold: once
 * pressed it stays on until pressed again, and {@link NotesListController#loadNotesForFolder}
 * itself has to read that state on every load — including one triggered by picking a
 * DIFFERENT folder from the sidebar — for the recursive view to keep applying to
 * whatever folder ends up selected, not just the one that was current the moment the
 * button was clicked.
 */
class NotesListPeekRecursiveToggleTest {

    @Test
    void loadingAFolderWithTheToggleOffOnlyShowsThatFoldersOwnNotes() throws Exception {
        assertLoadedNotes(false, "root-note");
    }

    @Test
    void loadingAFolderWithTheToggleOnAlsoShowsNotesFromEverySubfolder() throws Exception {
        assertLoadedNotes(true, "root-note", "sub-note");
    }

    /**
     * Each load is version-guarded (a later load in flight discards an earlier one that
     * hasn't finished yet — the mechanism that keeps a slow request from clobbering a
     * newer one), so the second load below only starts once the first one has genuinely
     * finished — exactly like a real user waiting to see the recursive view before
     * toggling it back off, not two clicks in the same instant.
     */
    @Test
    void reloadingAfterTogglingOffOnlyShowsThatFoldersOwnNotes() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch firstLoad = new CountDownLatch(1);
        CountDownLatch secondLoad = new CountDownLatch(1);
        List<Note>[] firstResult = new List[1];
        List<Note>[] secondResult = new List[1];
        Object[] controllerHolder = new Object[1];
        Object[] toggleHolder = new Object[1];
        Object[] rootHolder = new Object[1];

        Platform.runLater(() -> {
            try {
                Folder root = folder("Root", null);
                Folder sub = folder("Sub", "Root");
                NoteService noteService = fakeNoteService(root, List.of(note("root-note")), sub, List.of(note("sub-note")));
                FolderService folderService = fakeFolderService(root, List.of(sub), sub, List.of());

                NotesListController controller = loadController();
                controller.wire(null, noteService, null, folderService, null,
                        n -> { }, n -> { }, n -> { },
                        (notes, msg) -> {
                            if (firstResult[0] == null) {
                                firstResult[0] = notes;
                                firstLoad.countDown();
                            } else {
                                secondResult[0] = notes;
                                secondLoad.countDown();
                            }
                        },
                        msg -> { });

                ToggleButton toggle = getPeekToggle(controller);
                controllerHolder[0] = controller;
                toggleHolder[0] = toggle;
                rootHolder[0] = root;

                toggle.setSelected(true);
                controller.loadNotesForFolder(root); // recursive
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(firstLoad.await(30, TimeUnit.SECONDS), "first (recursive) load never completed");
        assertEquals(2, firstResult[0].size(), "sanity check: the recursive load must have run first");

        Platform.runLater(() -> {
            ((ToggleButton) toggleHolder[0]).setSelected(false);
            ((NotesListController) controllerHolder[0]).loadNotesForFolder((Folder) rootHolder[0]);
        });

        assertTrue(secondLoad.await(30, TimeUnit.SECONDS), "second (non-recursive) load never completed");
        assertEquals(1, secondResult[0].size(),
                "toggling back off must reload with just this folder's own notes");
        assertEquals("root-note", secondResult[0].get(0).getId());
    }

    @SuppressWarnings("unchecked")
    private void assertLoadedNotes(boolean toggleSelected, String... expectedIds) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch loaded = new CountDownLatch(1);
        List<Note>[] result = new List[1];
        Platform.runLater(() -> {
            try {
                Folder root = folder("Root", null);
                Folder sub = folder("Sub", "Root");
                NoteService noteService = fakeNoteService(root, List.of(note("root-note")), sub, List.of(note("sub-note")));
                FolderService folderService = fakeFolderService(root, List.of(sub), sub, List.of());

                NotesListController controller = loadController();
                controller.wire(null, noteService, null, folderService, null,
                        n -> { }, n -> { }, n -> { },
                        (notes, msg) -> {
                            result[0] = notes;
                            loaded.countDown();
                        },
                        msg -> { });

                getPeekToggle(controller).setSelected(toggleSelected);
                controller.loadNotesForFolder(root);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(loaded.await(30, TimeUnit.SECONDS), "notes load never completed");
        assertEquals(expectedIds.length, result[0].size(),
                "expected " + List.of(expectedIds) + " but got "
                        + result[0].stream().map(Note::getId).toList());
        for (String id : expectedIds) {
            assertTrue(result[0].stream().anyMatch(n -> id.equals(n.getId())), "missing note " + id);
        }
    }

    private static NoteService fakeNoteService(Folder root, List<Note> rootNotes, Folder sub, List<Note> subNotes) {
        return new NoteService(noOpNoteDao(), noOpFolderDao()) {
            @Override
            public List<Note> getNotesByFolder(Folder folder) {
                if (root.getId().equals(folder.getId())) {
                    return rootNotes;
                }
                if (sub.getId().equals(folder.getId())) {
                    return subNotes;
                }
                return List.of();
            }
        };
    }

    private static FolderService fakeFolderService(Folder root, List<Folder> rootSubfolders, Folder sub, List<Folder> subSubfolders) {
        return new FolderService(noOpFolderDao(), noOpNoteDao()) {
            @Override
            public List<Folder> getSubfolders(Folder parentFolder) {
                if (root.getId().equals(parentFolder.getId())) {
                    return rootSubfolders;
                }
                if (sub.getId().equals(parentFolder.getId())) {
                    return subSubfolders;
                }
                return List.of();
            }
        };
    }

    private static Folder folder(String id, String unused) {
        Folder folder = new Folder(id, null, null);
        folder.setId(id);
        return folder;
    }

    private static Note note(String id) {
        Note note = new Note(id, "content");
        note.setId(id);
        return note;
    }

    private static NotesListController loadController() throws Exception {
        URL fxml = NotesListPeekRecursiveToggleTest.class.getClassLoader()
                .getResource("com/example/jylos/ui/view/NotesListView.fxml");
        ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
        FXMLLoader loader = new FXMLLoader(fxml, bundle);
        Parent root = loader.load();
        root.applyCss();
        root.layout();
        return loader.getController();
    }

    private static ToggleButton getPeekToggle(NotesListController controller) throws Exception {
        Field f = NotesListController.class.getDeclaredField("peekRecursiveBtn");
        f.setAccessible(true);
        return (ToggleButton) f.get(controller);
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
