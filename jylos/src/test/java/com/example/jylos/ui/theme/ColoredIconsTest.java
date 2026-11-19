package com.example.jylos.ui.theme;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.ExplorerTree;
import com.example.jylos.ui.preferences.UiPreferencesStore;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * With coloured icons on, each kind of file is told apart by its icon's colour in the Explorer
 * (a PDF red, a folder amber, a plain note as before); off, they all look as they always have.
 */
class ColoredIconsTest {

    @Test
    void theSettingStartsOffAndIsRemembered() throws BackingStoreException {
        UiPreferencesStore store = new UiPreferencesStore();
        Preferences prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        try {
            assertFalse(store.loadColoredIcons(prefs));
            store.saveColoredIcons(prefs, true);
            assertEquals(true, store.loadColoredIcons(prefs));
        } finally {
            prefs.removeNode();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"dark-theme.css", "modern-theme.css"})
    void eachKindHasItsColourOnlyWithColouredIconsOn(String theme) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        Map<String, String> off = colours(theme, false, false);
        Map<String, String> on = colours(theme, true, false);
        Map<String, String> onSelected = colours(theme, true, true);

        assertEquals(1, off.values().stream().distinct().count(), "without coloured icons all look alike: " + off);
        assertEquals(off.get("Nota"), on.get("Nota"), "a plain note keeps its colour");
        assertNotEquals(on.get("Nota"), on.get("doc.pdf"), "a PDF stands out");
        assertNotEquals(on.get("Nota"), on.get("Carpeta"), "a folder stands out");
        assertNotEquals(on.get("doc.pdf"), on.get("Carpeta"), "a PDF and a folder are told apart");
        assertNotEquals(on.get("doc.pdf"), onSelected.get("doc.pdf"),
                "on its selected row a PDF's icon takes the selection's colour, as any icon does");
    }

    /** "title -> icon colour" for a folder, a note and a PDF in the Explorer, the PDF's row selected if asked. */
    private static Map<String, String> colours(String theme, boolean colored, boolean selectPdf) throws Exception {
        AtomicReference<ExplorerTree> holder = new AtomicReference<>();
        onFx(() -> {
            ExplorerTree tree = new ExplorerTree(new ExplorerTree.Source() {
                @Override
                public List<Folder> subfolders(Folder folder) {
                    return "ROOT".equals(folder.getId()) ? List.of(new Folder("c", "Carpeta")) : List.of();
                }

                @Override
                public List<Note> notes(Folder folder) {
                    if (!"ROOT".equals(folder.getId())) {
                        return List.of();
                    }
                    Note note = new Note("Nota", "texto");
                    note.setId("Nota.md");
                    Note pdf = new Note("doc.pdf", "");
                    pdf.setId("doc.pdf");
                    return List.of(note, pdf);
                }
            }, Runnable::run, k -> k);
            tree.setRoot(new Folder("ROOT", "Vault"));
            holder.set(tree);
            return null;
        });
        return onFx(() -> {
            VBox root = new VBox(holder.get().getView());
            root.getStyleClass().addAll("root-container", "root");
            UiNodes.setStyleClass(root, "colored-icons", colored);
            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().add(ColoredIconsTest.class.getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            if (selectPdf) {
                for (TreeItem<Component> child : holder.get().getView().getRoot().getChildren()) {
                    if (child.getValue() instanceof Note n && "doc.pdf".equals(n.getId())) {
                        holder.get().getView().getSelectionModel().select(holder.get().getView().getRow(child));
                    }
                }
            }
            root.applyCss();
            root.layout();
            Map<String, String> out = new TreeMap<>();
            for (Node node : holder.get().getView().lookupAll(".tree-cell")) {
                if (node instanceof TreeCell<?> cell && !cell.isEmpty() && cell.getTreeItem() instanceof TreeItem<?> item
                        && item.getValue() != null) {
                    String title = item.getValue() instanceof Folder f ? f.getTitle()
                            : item.getValue() instanceof Note n ? n.getTitle() : "?";
                    for (Node icon : cell.lookupAll(".ikonli-font-icon")) {
                        if (icon instanceof FontIcon fi && fi.isVisible() && fi.getStyleClass().stream().anyMatch(c -> c.startsWith("icon-type-"))) {
                            out.put(title, String.valueOf(fi.getIconColor()));
                        }
                    }
                }
            }
            stage.close();
            return out;
        });
    }
}
