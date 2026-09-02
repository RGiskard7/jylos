package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Locale;
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
 * The sidebar's "All Notes" virtual row is gone — the notes panel's "show notes from
 * subfolders" toggle now covers the same need by selecting the real vault root folder
 * with that toggle on. This pins that the tree's only top-level entry is the vault
 * root (id "ROOT"): no folder anywhere in the tree carries the old "ALL_NOTES_VIRTUAL" id.
 */
class SidebarNoAllNotesRowTest {

    @Test
    void theFolderTreesOnlyTopLevelEntryIsTheVaultRoot() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        int[] topLevelCount = new int[1];
        String[] onlyChildId = new String[1];
        Platform.runLater(() -> {
            try {
                URL fxml = SidebarNoAllNotesRowTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/view/SidebarView.fxml");
                ResourceBundle bundle = ResourceBundle.getBundle(
                        "com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
                FXMLLoader loader = new FXMLLoader(fxml, bundle);
                Parent root = loader.load();
                root.applyCss();
                root.layout();
                SidebarController controller = loader.getController();

                TreeItem<Folder> vaultRootItem = getVaultRootItem(controller);
                TreeItem<Folder> invisibleRoot = vaultRootItem.getParent();
                topLevelCount[0] = invisibleRoot.getChildren().size();
                onlyChildId[0] = invisibleRoot.getChildren().get(0).getValue().getId();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertEquals(1, topLevelCount[0],
                "the folder tree must have exactly one top-level entry now — the vault root — "
                        + "not the old vault-root-plus-All-Notes pair");
        assertEquals("ROOT", onlyChildId[0],
                "the single top-level entry must be the vault root, not a leftover "
                        + "ALL_NOTES_VIRTUAL row");
    }

    @SuppressWarnings("unchecked")
    private static TreeItem<Folder> getVaultRootItem(SidebarController controller) throws Exception {
        Field f = SidebarController.class.getDeclaredField("vaultRootItem");
        f.setAccessible(true);
        return (TreeItem<Folder>) f.get(controller);
    }
}
