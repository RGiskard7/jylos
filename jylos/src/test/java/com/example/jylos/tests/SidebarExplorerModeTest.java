package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.controller.SidebarController;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/**
 * The sidebar in the Explorer layout: its folders tab shows the Explorer tree (folders and
 * notes together) instead of the classic folder tree, opening a note goes to the editor,
 * and a vault change reloads what the tree shows. Switching back restores the classic tree.
 */
class SidebarExplorerModeTest {

    @TempDir
    Path tempDir;

    private Connection connection;
    private FolderService folders;
    private NoteService notes;

    private Preferences prefs;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        SidebarController.usePreferences(prefs);
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("sidebar.db"));
        connection = SQLiteTestSupport.openConnection();
        FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
        NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
        folders = new FolderService(folderDAO, noteDAO);
        notes = new NoteService(noteDAO, folderDAO);
        Folder trabajo = folders.createFolder("Trabajo");
        folders.addNoteToFolder(trabajo, notes.createNote("Acta", "x"));
        notes.createNote("Inicio", "x");
    }

    @AfterEach
    void tearDown() throws Exception {
        SidebarController.usePreferences(null);
        prefs.removeNode();
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void theFoldersTabSwapsToTheExplorerTreeAndBack() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> opened = new ArrayList<>();
        Fixture f = onFx(() -> new Fixture(folders, notes, opened));

        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));

        assertTrue(onFx(() -> explorer.getParent() != null && f.classicTree().getParent() == null),
                "the Explorer tree takes the classic folder tree's place");
        assertEquals("Filtrar notas y carpetas…", onFx(() -> f.filterField().getPromptText()),
                "the header filter now filters the Explorer tree");

        onFx(() -> {
            explorer.getSelectionModel().select(explorer.getRoot().getChildren().get(1));
            Event.fireEvent(explorer, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
            return null;
        });
        assertEquals(List.of("Inicio CURRENT_TAB"), opened);

        onFx(() -> {
            f.sidebar.setExplorerMode(false);
            return null;
        });
        assertTrue(onFx(() -> explorer.getParent() == null && f.classicTree().getParent() != null));
        assertEquals("Filtrar carpetas...", onFx(() -> f.filterField().getPromptText()));
    }

    @Test
    void aVaultChangeReloadsTheExplorerTree() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));
        TreeItem<Component> trabajo = onFx(() -> explorer.getRoot().getChildren().get(0));

        notes.createNote("Borrador", "x");
        onFx(() -> {
            f.sidebar.loadFolders();
            return null;
        });
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Borrador", "Inicio")));

        assertSame(trabajo, onFx(() -> explorer.getRoot().getChildren().get(0)));
    }

    @Test
    void explorerRowsGetTheFolderAndNoteMenus() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.setExplorerNoteMenu(n -> new ContextMenu(
                    new MenuItem("nota " + n.getTitle())));
            f.sidebar.setExplorerMode(true);
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));

        // Rows exist only once the scene has been laid out with the tree in it.
        AtomicReference<List<String>> menus = new AtomicReference<>(List.of());
        try {
            waitUntil(() -> {
                explorer.getScene().getRoot().applyCss();
                explorer.getScene().getRoot().layout();
                List<String> out = new ArrayList<>();
                for (Node node : explorer.lookupAll(".tree-cell")) {
                    if (node instanceof TreeCell<?> cell && !cell.isEmpty()
                            && cell.getContextMenu() != null) {
                        out.add(cell.getContextMenu().getItems().get(0).getText());
                    }
                }
                menus.set(out.stream().sorted().toList());
                return out.size() >= 2;
            });
        } catch (AssertionError timeout) {
            // fall through to the assertion below, which shows what the rows had
        }

        // lookupAll returns rows in no particular order
        assertEquals(List.of("Nueva Nota", "nota Inicio"), menus.get());
    }

    @Test
    void theHeaderFiltersTheExplorerAndOffersRevealOnlyThere() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        boolean revealBeforeExplorer = onFx(() -> f.revealButton().isVisible());
        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));

        boolean shownInExplorer = onFx(() -> f.revealButton().isVisible() && f.filterField().isVisible());
        onFx(() -> {
            f.filterField().setText("act");
            return null;
        });
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo"))
                && titles(explorer.getRoot().getChildren().get(0)).equals(List.of("Acta")));

        onFx(() -> {
            f.sidebar.setExplorerMode(false);
            return null;
        });

        assertFalse(revealBeforeExplorer, "reveal only makes sense in the Explorer");
        assertTrue(shownInExplorer, "the Explorer header has the filter and the reveal button");
        assertEquals("", onFx(() -> f.filterField().getText()), "a filter does not carry over to the other tree");
        assertFalse(onFx(() -> f.revealButton().isVisible()));
    }

    @Test
    void theNoteCountPreferenceAlsoAppliesToTheExplorer() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.applyShowFolderNoteCountsPreference(true);
            f.sidebar.setExplorerMode(true);
            f.sidebar.loadFolders();
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());

        waitUntil(() -> "1".equals(badgeOf(explorer, "Trabajo")));
        Thread.sleep(600); // let any pending count rebuild finish, so it cannot mask the check below
        String afterTurningOff = onFx(() -> {
            f.sidebar.applyShowFolderNoteCountsPreference(false);
            return badgeOf(explorer, "Trabajo");
        });

        assertEquals(null, afterTurningOff, "turning the preference off hides the counts right away");
    }

    /** The count shown on a folder row of the Explorer, or null. */
    private static String badgeOf(TreeView<Component> explorer, String folder) {
        explorer.getScene().getRoot().applyCss();
        explorer.getScene().getRoot().layout();
        for (Node node : explorer.lookupAll(".tree-cell")) {
            if (node instanceof TreeCell<?> cell && !cell.isEmpty()
                    && cell.getItem() instanceof Component c && folder.equals(c.getTitle())) {
                for (Node label : cell.lookupAll(".folder-cell-count")) {
                    if (label.isVisible() && label instanceof Label l) {
                        return l.getText().replace("(", "").replace(")", "");
                    }
                }
                return null;
            }
        }
        return "no row";
    }

    /**
     * Whatever changes the vault reloads the Explorer tree, as in Obsidian: here, moving a
     * note to the trash (the notes list publishes NoteDeletedEvent) and a note changed
     * outside Jylos (NoteUpdatedEvent).
     */
    @Test
    void trashingANoteOrChangingItOutsideReloadsTheTree() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        EventBus bus = EventBus.getInstance();
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>(), bus));
        try {
            onFx(() -> {
                f.sidebar.setExplorerMode(true);
                return null;
            });
            TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
            waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));

            Note inicio = notes.getAllNotes().stream()
                    .filter(n -> "Inicio".equals(n.getTitle())).findFirst().orElseThrow();
            notes.moveToTrash(inicio.getId());
            onFx(() -> {
                bus.publish(new NoteEvents.NoteDeletedEvent(inicio.getId(), "Inicio"));
                return null;
            });
            waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo")));

            notes.createNote("Externa", "x");
            onFx(() -> {
                bus.publish(new NoteEvents.NoteUpdatedEvent(
                        new Note("Externa", "x")));
                return null;
            });
            waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Externa")));
        } finally {
            onFx(() -> {
                f.sidebar.teardown();
                return null;
            });
        }
    }

    @Test
    void theShowCurrentFileSwitchIsSavedAndRevealsTheOpenNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        String acta = notes.getAllNotes().stream().filter(n -> "Acta".equals(n.getTitle())).findFirst().orElseThrow().getId();
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        waitUntil(() -> titles(f.explorer().getRoot()).equals(List.of("Trabajo", "Inicio")));
        String tooltip = onFx(() -> f.revealButton().getTooltip().getText());

        onFx(() -> {
            f.sidebar.setExplorerActiveNote(acta);
            return null;
        });
        Thread.sleep(300);
        boolean openedWhileOff = onFx(() -> f.explorer().getRoot().getChildren().get(0).isExpanded());

        onFx(() -> {
            f.revealButton().fire();
            return null;
        });
        waitUntil(() -> selectedTitle(f.explorer()).equals("Acta"));
        // Switched on, opening a note shows it again (its folder had been collapsed meanwhile).
        onFx(() -> {
            f.explorer().getRoot().getChildren().get(0).setExpanded(false);
            f.explorer().getSelectionModel().clearSelection();
            f.sidebar.setExplorerActiveNote(null);
            f.sidebar.setExplorerActiveNote(acta);
            return null;
        });
        waitUntil(() -> selectedTitle(f.explorer()).equals("Acta")
                && f.explorer().getRoot().getChildren().get(0).isExpanded());
        boolean saved = prefs.getBoolean("ui.explorer.auto_reveal", false);
        Fixture again = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));

        assertEquals("Mostrar automáticamente el archivo actual", tooltip);
        assertFalse(openedWhileOff, "off: opening a note leaves the tree as it is");
        assertTrue(saved);
        assertTrue(onFx(() -> again.revealButton().isSelected()), "a new window reads the saved switch");
    }

    @Test
    void oneButtonExpandsEverythingAndThenCollapsesIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        waitUntil(() -> titles(f.explorer().getRoot()).equals(List.of("Trabajo", "Inicio")));
        List<String> steps = new ArrayList<>();
        steps.add(onFx(() -> f.expandCollapseButton().getTooltip().getText()));
        onFx(() -> {
            f.expandCollapseButton().fire();
            return null;
        });
        waitUntil(() -> f.explorer().getRoot().getChildren().get(0).isExpanded());
        steps.add(onFx(() -> f.expandCollapseButton().getTooltip().getText()));
        onFx(() -> {
            f.expandCollapseButton().fire();
            return null;
        });
        steps.add(onFx(() -> f.explorer().getRoot().getChildren().get(0).isExpanded()
                + " " + f.expandCollapseButton().getTooltip().getText()));
        assertEquals(List.of("Expandir todo", "Colapsar todo", "false Expandir todo"), steps);
    }

    @Test
    void theFoldersLeftOpenAreOpenInTheNextSession() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        waitUntil(() -> titles(f.explorer().getRoot()).equals(List.of("Trabajo", "Inicio")));
        onFx(() -> {
            f.explorer().getRoot().getChildren().get(0).setExpanded(true);
            return null;
        });
        Thread.sleep(600);

        Fixture next = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            next.sidebar.setExplorerMode(true);
            return null;
        });
        waitUntil(() -> titles(next.explorer().getRoot()).equals(List.of("Trabajo", "Inicio"))
                && next.explorer().getRoot().getChildren().get(0).isExpanded()
                && titles(next.explorer().getRoot().getChildren().get(0)).equals(List.of("Acta")));
    }

    @Test
    void renameInTheExplorerMenuRenamesTheFolderInItsRow() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        waitUntil(() -> titles(f.explorer().getRoot()).equals(List.of("Trabajo", "Inicio")));
        // The rows are drawn on the next layout pass: wait for the "Trabajo" one.
        waitUntil(() -> {
            f.explorer().layout();
            return f.explorer().lookupAll(".tree-cell").stream().anyMatch(node ->
                    node instanceof TreeCell<?> cell && cell.getItem() instanceof Component c
                            && "Trabajo".equals(c.getTitle()));
        });
        onFx(() -> {
            TreeCell<?> row = null;
            for (Node node : f.explorer().lookupAll(".tree-cell")) {
                if (node instanceof TreeCell<?> cell && cell.getItem() instanceof Component c
                        && "Trabajo".equals(c.getTitle())) {
                    row = cell;
                }
            }
            row.getContextMenu().getItems().stream().filter(i -> "Renombrar".equals(i.getText())).findFirst()
                    .orElseThrow().fire();
            return null;
        });
        waitUntil(() -> f.explorer().lookup(".explorer-rename-field") != null
                && f.explorer().lookup(".explorer-rename-field").getScene() != null);
        onFx(() -> {
            TextField field = (TextField) f.explorer().lookup(".explorer-rename-field");
            field.setText("Oficina");
            Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED,
                    "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });
        waitUntil(() -> titles(f.explorer().getRoot()).contains("Oficina"));
        assertTrue(folders.getAllFolders().stream().anyMatch(folder -> "Oficina".equals(folder.getTitle())),
                "renamed in the storage, not only on screen");
    }

    private static String selectedTitle(TreeView<Component> tree) {
        TreeItem<Component> item = tree.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() != null ? item.getValue().getTitle() : "";
    }

    private static final class Fixture {
        final SidebarController sidebar;

        Fixture(FolderService folders, NoteService notes, List<String> opened) throws Exception {
            this(folders, notes, opened, null);
        }

        Fixture(FolderService folders, NoteService notes, List<String> opened,
                EventBus bus) throws Exception {
            URL fxml = SidebarExplorerModeTest.class.getClassLoader()
                    .getResource("com/example/jylos/ui/view/SidebarView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            Parent root = loader.load();
            sidebar = loader.getController();
            sidebar.wire(bus, notes, null, folders, bundle, x -> { }, x -> { }, x -> { }, x -> { }, x -> { },
                    x -> { }, x -> { });
            sidebar.setExplorerOpenAction((note, target) -> opened.add(note.getTitle() + " " + target));
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320, 480));
            stage.show();
        }

        TreeView<?> classicTree() throws Exception {
            return (TreeView<?>) field("folderTreeView");
        }

        ToggleButton revealButton() throws Exception {
            return (ToggleButton) field("revealActiveNoteBtn");
        }

        Button expandCollapseButton() throws Exception {
            return (Button) field("expandCollapseBtn");
        }

        TreeView<Component> explorer() {
            return sidebar.getExplorerTree().getView();
        }

        TextField filterField() throws Exception {
            return (TextField) field("filterFoldersField");
        }

        private Object field(String name) throws Exception {
            Field field = SidebarController.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(sidebar);
        }
    }

    private static List<String> titles(TreeItem<Component> parent) {
        List<String> out = new ArrayList<>();
        if (parent != null) {
            for (TreeItem<Component> child : parent.getChildren()) {
                out.add(child.getValue() != null ? child.getValue().getTitle() : "(loading)");
            }
        }
        return out;
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(50);
        }
        assertNotNull(null, "condition not reached in time");
    }
}
