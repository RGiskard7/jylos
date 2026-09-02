package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TreeItem;

/**
 * The sidebar's folder note counts used to be direct children only — a folder with
 * subfolders full of notes showed a count that ignored all of them. Both the vault
 * root and every other folder must show the TOTAL of everything nested underneath,
 * recursively, the way a real file explorer would.
 */
class SidebarRecursiveNoteCountTest {

    @Test
    void folderCountsIncludeEveryNoteInEveryDescendantSubfolder() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        Map<String, Integer> recursiveCounts = new HashMap<>();
        Platform.runLater(() -> {
            try {
                SidebarController controller = loadController();

                // Root
                //  └─ Docs (2 notes directly)
                //      └─ Deep (3 notes directly)
                Folder root = folderWithId("ROOT");
                Folder docs = folderWithId("Docs");
                Folder deep = folderWithId("Docs/Deep");

                TreeItem<Folder> vaultRootItem = getVaultRootItem(controller);
                vaultRootItem.setValue(root);
                TreeItem<Folder> docsItem = new TreeItem<>(docs);
                TreeItem<Folder> deepItem = new TreeItem<>(deep);
                vaultRootItem.getChildren().setAll(docsItem);
                docsItem.getChildren().setAll(deepItem);

                Map<String, Integer> directCounts = getFolderNoteCountCache(controller);
                directCounts.put("ROOT", 0);
                directCounts.put("Docs", 2);
                directCounts.put("Docs/Deep", 3);

                invokeRecomputeRecursiveNoteCounts(controller);

                Map<String, Integer> recursive = getFolderRecursiveNoteCountCache(controller);
                recursiveCounts.putAll(recursive);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertEquals(3, recursiveCounts.get("Docs/Deep"),
                "a leaf folder's own count must still just be its direct notes");
        assertEquals(5, recursiveCounts.get("Docs"),
                "Docs must show its own 2 notes PLUS Deep's 3, not just its own 2");
        assertEquals(5, recursiveCounts.get("ROOT"),
                "the vault root must show the grand total of every note in every "
                        + "subfolder, not just the notes sitting directly at the root");
    }

    private static Folder folderWithId(String id) {
        Folder folder = new Folder(id, null, null);
        folder.setId(id);
        return folder;
    }

    private static SidebarController loadController() throws Exception {
        URL fxml = SidebarRecursiveNoteCountTest.class.getClassLoader()
                .getResource("com/example/jylos/ui/view/SidebarView.fxml");
        ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
        FXMLLoader loader = new FXMLLoader(fxml, bundle);
        Parent root = loader.load();
        root.applyCss();
        root.layout();
        return loader.getController();
    }

    @SuppressWarnings("unchecked")
    private static TreeItem<Folder> getVaultRootItem(SidebarController controller) throws Exception {
        Field f = SidebarController.class.getDeclaredField("vaultRootItem");
        f.setAccessible(true);
        return (TreeItem<Folder>) f.get(controller);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> getFolderNoteCountCache(SidebarController controller) throws Exception {
        Field f = SidebarController.class.getDeclaredField("folderNoteCountCache");
        f.setAccessible(true);
        return (Map<String, Integer>) f.get(controller);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> getFolderRecursiveNoteCountCache(SidebarController controller) throws Exception {
        Field f = SidebarController.class.getDeclaredField("folderRecursiveNoteCountCache");
        f.setAccessible(true);
        return (Map<String, Integer>) f.get(controller);
    }

    private static void invokeRecomputeRecursiveNoteCounts(SidebarController controller) throws Exception {
        Method m = SidebarController.class.getDeclaredMethod("recomputeRecursiveNoteCounts");
        m.setAccessible(true);
        m.invoke(controller);
    }
}
