package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.URL;
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
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

/**
 * Preferences: "show note count next to each folder" — a real folder rendered with a
 * positive note count must show its "(N)" badge by default, and hide it once the
 * preference is switched off via {@link SidebarController#applyShowFolderNoteCountsPreference}.
 */
class SidebarShowFolderNoteCountsPreferenceTest {

    @Test
    void togglingThePreferenceShowsAndHidesTheCountBadgeOnARealRenderedFolder() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        boolean[] countVisibleByDefault = new boolean[1];
        boolean[] countVisibleAfterDisabling = new boolean[1];
        boolean[] countVisibleAfterReenabling = new boolean[1];
        Platform.runLater(() -> {
            try {
                URL fxml = SidebarShowFolderNoteCountsPreferenceTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/view/SidebarView.fxml");
                ResourceBundle bundle = ResourceBundle.getBundle(
                        "com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
                FXMLLoader loader = new FXMLLoader(fxml, bundle);
                Parent root = loader.load();
                SidebarController controller = loader.getController();

                Folder testFolder = new Folder("TestFolder", null, null);
                testFolder.setId("TestFolder");
                TreeItem<Folder> vaultRootItem = getVaultRootItem(controller);
                vaultRootItem.getChildren().setAll(new TreeItem<>(testFolder));
                vaultRootItem.setExpanded(true);

                getFolderNoteCountCache(controller).put("TestFolder", 5);
                getFolderRecursiveNoteCountCache(controller).put("TestFolder", 5);

                @SuppressWarnings("unchecked")
                TreeView<Folder> folderTreeView = (TreeView<Folder>) root.lookup("#folderTreeView");

                Stage stage = new Stage();
                stage.setScene(new javafx.scene.Scene(root, 260, 400));
                stage.show();
                root.applyCss();
                root.layout();
                countVisibleByDefault[0] = !folderTreeView.lookupAll(".folder-cell-count").isEmpty();

                controller.applyShowFolderNoteCountsPreference(false);
                root.applyCss();
                root.layout();
                countVisibleAfterDisabling[0] = !folderTreeView.lookupAll(".folder-cell-count").isEmpty();

                controller.applyShowFolderNoteCountsPreference(true);
                root.applyCss();
                root.layout();
                countVisibleAfterReenabling[0] = !folderTreeView.lookupAll(".folder-cell-count").isEmpty();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(countVisibleByDefault[0], "the count badge must show by default (preference on)");
        assertTrue(!countVisibleAfterDisabling[0],
                "the count badge must disappear once the preference is switched off");
        assertTrue(countVisibleAfterReenabling[0], "the count badge must come back once re-enabled");
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
}
