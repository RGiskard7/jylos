package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.service.NoteService.SortOption;
import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

/**
 * The Explorer tree: one tree with folders and notes, loaded folder by folder as the user
 * expands them, so a large vault does not have to be read up front.
 */
class ExplorerTreeTest {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    @Test
    void onlyTheVaultRootIsReadUntilAFolderIsExpanded() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Trabajo", "t");
        vault.folder("ROOT", "Archivo", "a");
        vault.note("ROOT", "Inicio");
        vault.note("t", "Acta 10");
        vault.note("t", "Acta 2");
        vault.folder("t", "Reuniones", "t/r");

        onFx(() -> {
            new ExplorerTree(vault, Runnable::run, key -> key).setRoot(new Folder("ROOT", "Vault"));
            return null;
        });

        assertEquals(List.of("ROOT"), settle(() -> new ArrayList<>(vault.queries)));
    }

    @Test
    void expandingAFolderLoadsItsChildrenOnceFoldersFirst() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Trabajo", "t");
        vault.note("ROOT", "Inicio");
        vault.note("t", "Acta 10");
        vault.note("t", "Acta 2");
        vault.folder("t", "Reuniones", "t/r");
        Holder h = new Holder();

        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        List<String> top = settle(() -> titles(h.tree.getView().getRoot()));
        assertEquals(List.of("Trabajo", "Inicio"), top);

        onFx(() -> {
            h.tree.getView().getRoot().getChildren().get(0).setExpanded(true);
            return null;
        });
        List<String> inside = settle(() -> titles(h.tree.getView().getRoot().getChildren().get(0)));
        assertEquals(List.of("Reuniones", "Acta 2", "Acta 10"), inside);

        onFx(() -> {
            TreeItem<Component> trabajo = h.tree.getView().getRoot().getChildren().get(0);
            trabajo.setExpanded(false);
            trabajo.setExpanded(true);
            return null;
        });
        assertEquals(List.of("ROOT", "t"), settle(() -> vault.queries), "re-expanding must not read again");
    }

    @Test
    void anUnreadFolderShowsAnArrowAndAnEmptyOneDoesNotOnceRead() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Vacía", "v");
        Holder h = new Holder();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        boolean leafBefore = settle(() -> h.tree.getView().getRoot().getChildren().get(0).isLeaf());
        onFx(() -> {
            h.tree.getView().getRoot().getChildren().get(0).setExpanded(true);
            return null;
        });
        boolean leafAfter = settle(() -> h.tree.getView().getRoot().getChildren().get(0).isLeaf());

        assertFalse(leafBefore, "a folder not read yet must look expandable");
        assertTrue(leafAfter, "an empty folder, once read, has nothing to expand");
    }

    @Test
    void whileAFolderLoadsItShowsALoadingRowAndALateOlderResultIsIgnored() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Trabajo", "t");
        vault.note("t", "Primera");
        QueuedExecutor queue = new QueuedExecutor();
        Holder h = new Holder();

        onFx(() -> {
            h.tree = new ExplorerTree(vault, queue, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        List<String> loadingRoot = settle(() -> titles(h.tree.getView().getRoot()));
        assertEquals(List.of("explorer.loading"), loadingRoot);

        queue.runNext();
        settle(() -> null);
        onFx(() -> {
            h.tree.getView().getRoot().getChildren().get(0).setExpanded(true);
            return null;
        });
        // A first read of "Trabajo" is pending; the vault changes and a refresh starts a second one.
        vault.note("t", "Segunda");
        onFx(() -> {
            h.tree.refresh();
            return null;
        });
        // The refresh's reads finish first; the first, older read of "Trabajo" finishes last.
        Runnable older = queue.takeFirst();
        queue.runAll();
        vault.removeNote("t", "Segunda");
        older.run();
        List<String> inside = settle(() -> titles(h.tree.getView().getRoot().getChildren().get(0)));

        assertEquals(List.of("Primera", "Segunda"), inside);
    }

    @Test
    void refreshKeepsExpandedFoldersOpenAndShowsWhatChanged() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Trabajo", "t");
        vault.folder("ROOT", "Viejo", "o");
        vault.note("t", "Acta");
        Holder h = new Holder();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        settle(() -> null);
        onFx(() -> {
            h.tree.getView().getRoot().getChildren().get(0).setExpanded(true);
            return null;
        });
        settle(() -> null);
        TreeItem<Component> before = onFx(() -> h.tree.getView().getRoot().getChildren().get(0));

        vault.note("t", "Borrador");
        vault.removeFolder("ROOT", "Viejo");
        onFx(() -> {
            h.tree.refresh();
            return null;
        });
        TreeItem<Component> after = settle(() -> h.tree.getView().getRoot().getChildren().get(0));

        assertEquals(List.of("Trabajo"), settle(() -> titles(h.tree.getView().getRoot())));
        assertSame(before, after, "the folder row is kept, so its expansion and selection survive");
        assertTrue(after.isExpanded());
        assertEquals(List.of("Acta", "Borrador"), settle(() -> titles(after)));
    }

    @Test
    void changingTheNoteOrderReordersTheLoadedFolders() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.note("ROOT", "a");
        vault.note("ROOT", "c");
        vault.note("ROOT", "b");
        Holder h = new Holder();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        assertEquals(List.of("a", "b", "c"), settle(() -> titles(h.tree.getView().getRoot())));
        onFx(() -> {
            h.tree.setNoteSort(SortOption.TITLE_DESC);
            return null;
        });
        assertEquals(List.of("c", "b", "a"), settle(() -> titles(h.tree.getView().getRoot())));
    }

    @Test
    void clickingANoteOpensItAndTheNewTabGesturesOpenItInANewTab() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.note("ROOT", "Uno");
        vault.note("ROOT", "Dos");
        List<String> opened = new ArrayList<>();
        Holder h = shown(vault, opened);

        onFx(() -> {
            click(h.cell("Uno"), MouseButton.PRIMARY, false);
            click(h.cell("Dos"), MouseButton.PRIMARY, true);
            click(h.cell("Uno"), MouseButton.MIDDLE, false);
            return null;
        });
        settle(() -> null);

        assertEquals(List.of("Uno CURRENT_TAB", "Dos NEW_TAB", "Uno NEW_TAB"), opened);
    }

    @Test
    void clickingAFolderRowTogglesItAndEnterOpensTheSelectedNote() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Trabajo", "t");
        vault.note("ROOT", "Uno");
        List<String> opened = new ArrayList<>();
        Holder h = shown(vault, opened);

        boolean expanded = onFx(() -> {
            click(h.cell("Trabajo"), MouseButton.PRIMARY, false);
            return h.tree.getView().getRoot().getChildren().get(0).isExpanded();
        });
        boolean collapsed = onFx(() -> {
            click(h.cell("Trabajo"), MouseButton.PRIMARY, false);
            return !h.tree.getView().getRoot().getChildren().get(0).isExpanded();
        });
        onFx(() -> {
            h.tree.getView().getSelectionModel().select(h.tree.getView().getRoot().getChildren().get(1));
            Event.fireEvent(h.tree.getView(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
            return null;
        });

        assertTrue(expanded);
        assertTrue(collapsed);
        assertEquals(List.of("Uno CURRENT_TAB"), opened);
    }

    @Test
    void theActiveNoteRowIsMarkedAndFollowsTheActiveNote() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.note("ROOT", "Uno");
        vault.note("ROOT", "Dos");
        Holder h = shown(vault, new ArrayList<>());

        List<Boolean> marks = onFx(() -> {
            h.tree.setActiveNoteId("id-Dos");
            h.layout();
            boolean first = h.cell("Dos").getStyleClass().contains("explorer-active-note")
                    && !h.cell("Uno").getStyleClass().contains("explorer-active-note");
            h.tree.setActiveNoteId("id-Uno");
            h.layout();
            boolean second = h.cell("Uno").getStyleClass().contains("explorer-active-note")
                    && !h.cell("Dos").getStyleClass().contains("explorer-active-note");
            return List.of(first, second);
        });

        assertEquals(List.of(true, true), marks);
    }

    @Test
    void rowsUseTheMenuForTheirKindAndTheLoadingRowHasNone() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Trabajo", "t");
        vault.note("ROOT", "Uno");
        Holder h = shown(vault, new ArrayList<>());

        List<String> menus = onFx(() -> {
            h.tree.setNoteMenu(n -> new ContextMenu(new MenuItem("note " + n.getTitle())));
            h.tree.setFolderMenu(f -> new ContextMenu(new MenuItem("folder " + f.getTitle())));
            h.tree.getView().refresh();
            h.layout();
            return List.of(h.cell("Uno").getContextMenu().getItems().get(0).getText(),
                    h.cell("Trabajo").getContextMenu().getItems().get(0).getText());
        });

        assertEquals(List.of("note Uno", "folder Trabajo"), menus);

        QueuedExecutor queue = new QueuedExecutor();
        ContextMenu loadingMenu = onFx(() -> {
            ExplorerTree pending = new ExplorerTree(vault, queue, key -> key);
            pending.setNoteMenu(n -> new ContextMenu());
            pending.setFolderMenu(f -> new ContextMenu());
            pending.setRoot(new Folder("ROOT", "Vault"));
            Holder p = new Holder();
            p.tree = pending;
            p.show();
            return p.cell("explorer.loading").getContextMenu();
        });
        assertNull(loadingMenu);
    }

    @Test
    void folderAndNoteNamesStartAtTheSameColumn() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Trabajo", "t");
        vault.note("ROOT", "Uno");
        Holder h = shown(vault, new ArrayList<>());

        double[] x = onFx(() -> new double[] { nameX(h.cell("Trabajo")), nameX(h.cell("Uno")) });

        assertEquals(x[0], x[1], 0.5, "folder and note names must line up");
    }

    @Test
    void theFilterShowsMatchingNotesAndFoldersWithTheirFoldersOpen() throws Exception {
        assumeFx();
        FakeVault vault = nestedVault();
        Holder h = new Holder();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            h.tree.setFilter("acta");
            return null;
        });

        assertEquals(List.of("Trabajo", "  Reuniones", "    Acta semanal", "  Acta 2", "  Acta 10"),
                settle(() -> outline(h.tree.getView().getRoot(), "")));

        onFx(() -> {
            h.tree.setFilter("arbol");
            return null;
        });
        assertEquals(List.of("Árbol genealógico"), settle(() -> outline(h.tree.getView().getRoot(), "")),
                "accents and case do not matter; a folder matches by its name");
    }

    @Test
    void theFilterAlsoMatchesThePathOfEachNote() throws Exception {
        assumeFx();
        FakeVault vault = nestedVault();
        Holder h = new Holder();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            h.tree.setFilter("reuniones/acta");
            return null;
        });
        assertEquals(List.of("Trabajo", "  Reuniones", "    Acta semanal"),
                settle(() -> outline(h.tree.getView().getRoot(), "")));

        onFx(() -> {
            h.tree.setFilter("trabajo/presu");
            return null;
        });
        assertEquals(List.of("Trabajo", "  Presupuesto"), settle(() -> outline(h.tree.getView().getRoot(), "")),
                "a folder in the query does not bring in the rest of its notes");
    }

    @Test
    void theFoldersLeftOpenOpenAgainAndOpeningOrClosingOneIsReported() throws Exception {
        assumeFx();
        FakeVault vault = nestedVault();
        Holder h = new Holder();
        List<Set<String>> reported = new ArrayList<>();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setOpenFolders(Set.of("folder:t", "folder:t/r"));
            h.tree.setOnOpenFoldersChanged(reported::add);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        assertEquals(List.of("Árbol genealógico", "Trabajo", "  Reuniones", "    Acta semanal", "  Acta 2",
                "  Acta 10", "  Presupuesto", "Inicio"), settle(() -> outline(h.tree.getView().getRoot(), "")));

        onFx(() -> {
            h.tree.getView().getRoot().getChildren().get(0).setExpanded(true);
            h.tree.getView().getRoot().getChildren().get(1).setExpanded(false);
            return null;
        });
        settle(() -> null);
        assertEquals(Set.of("folder:a", "folder:t/r"), reported.get(reported.size() - 1),
                "a closed folder is forgotten; the folder inside it keeps its own state");
    }

    @Test
    void holdingADragOverAClosedFolderOpensItAfterAMoment() throws Exception {
        assumeFx();
        Holder h = shown(nestedVault(), new ArrayList<>());
        onFx(() -> {
            fireDrag(h.cell("Trabajo"), DragEvent.DRAG_ENTERED);
            fireDrag(h.cell("Trabajo"), DragEvent.DRAG_EXITED);
            fireDrag(h.cell("Árbol genealógico"), DragEvent.DRAG_ENTERED);
            return null;
        });
        Thread.sleep(400);
        boolean tooSoon = onFx(() -> h.tree.getView().getRoot().getChildren().get(0).isExpanded());
        Thread.sleep(600);
        List<Boolean> after = onFx(() -> List.of(h.tree.getView().getRoot().getChildren().get(0).isExpanded(),
                h.tree.getView().getRoot().getChildren().get(1).isExpanded()));
        onFx(() -> {
            h.stage.close();
            return null;
        });

        assertFalse(tooSoon, "not before 750 ms");
        assertEquals(List.of(true, false), after, "the folder held over opens; the one left does not");
    }

    private static void fireDrag(Node target, EventType<DragEvent> type) {
        Bounds b = target.localToScene(target.getLayoutBounds());
        Event.fireEvent(target, new DragEvent(type, null, b.getCenterX(), b.getCenterY(),
                b.getCenterX(), b.getCenterY(), TransferMode.MOVE, null, null, null));
    }

    @Test
    void f2RenamesTheSelectedRowInPlaceAndEnterAppliesTheNewName() throws Exception {
        assumeFx();
        Holder h = shown(nestedVault(), new ArrayList<>());
        List<String> renamed = new ArrayList<>();
        List<String> seen = onFx(() -> {
            h.tree.setOnRename((item, name) -> renamed.add(item.getTitle() + " -> " + name));
            TreeItem<Component> inicio = h.tree.getView().getRoot().getChildren().get(2);
            h.tree.getView().getSelectionModel().select(inicio);
            key(h.tree.getView(), KeyCode.F2);
            return null;
        });
        TextField field = settle(() -> renameField(h));
        String shownText = onFx(field::getText);
        onFx(() -> {
            field.setText("Portada");
            key(field, KeyCode.ENTER);
            return null;
        });
        boolean fieldGone = settle(() -> h.tree.getView().lookup(".explorer-rename-field") == null
                || !h.tree.getView().lookup(".explorer-rename-field").isVisible()
                || h.tree.getView().lookup(".explorer-rename-field").getScene() == null);
        onFx(() -> {
            h.stage.close();
            return null;
        });

        assertEquals("Inicio", shownText, "the field starts with the current name");
        assertEquals(List.of("Inicio -> Portada"), renamed);
        assertTrue(fieldGone);
    }

    @Test
    void escapeLeavesTheNameAsItWas() throws Exception {
        assumeFx();
        Holder h = shown(nestedVault(), new ArrayList<>());
        List<String> renamed = new ArrayList<>();
        onFx(() -> {
            h.tree.setOnRename((item, name) -> renamed.add(name));
            h.tree.startRename(h.tree.getView().getRoot().getChildren().get(1).getValue());
            return null;
        });
        TextField field = settle(() -> renameField(h));
        onFx(() -> {
            field.setText("Otra cosa");
            key(field, KeyCode.ESCAPE);
            return null;
        });
        String label = settle(() -> h.cell("Trabajo").getText() == null ? "Trabajo" : h.cell("Trabajo").getText());
        onFx(() -> {
            h.stage.close();
            return null;
        });
        assertEquals(List.of(), renamed);
        assertEquals("Trabajo", label);
    }

    @Test
    void anItemAskedToBeRenamedBeforeItIsShownIsRenamedOnceItAppears() throws Exception {
        assumeFx();
        FakeVault vault = nestedVault();
        Holder h = shown(vault, new ArrayList<>());
        Folder created = new Folder("Sin nombre", "Sin nombre");
        onFx(() -> {
            vault.folders.get("ROOT").add(created);
            h.tree.startRename(created);
            h.tree.refresh();
            return null;
        });
        TextField field = settle(() -> renameField(h));
        String text = onFx(field::getText);
        onFx(() -> {
            h.stage.close();
            return null;
        });
        assertEquals("Sin nombre", text);
    }

    private static TextField renameField(Holder h) {
        h.layout();
        Node node = h.tree.getView().lookup(".explorer-rename-field");
        assertTrue(node instanceof TextField && node.getScene() != null, "no rename field shown");
        return (TextField) node;
    }

    private static void key(Node target, KeyCode code) {
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED,
                "", "", code, false, false, false, false));
    }

    @Test
    void clearingTheFilterBringsBackTheTreeAsItWas() throws Exception {
        assumeFx();
        FakeVault vault = nestedVault();
        Holder h = new Holder();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        settle(() -> null);
        TreeItem<Component> rootBefore = onFx(() -> {
            h.tree.getView().getRoot().getChildren().get(0).setExpanded(true);
            return h.tree.getView().getRoot();
        });
        settle(() -> null);

        onFx(() -> {
            h.tree.setFilter("acta");
            return null;
        });
        settle(() -> null);
        onFx(() -> {
            h.tree.setFilter("  ");
            return null;
        });

        assertSame(rootBefore, settle(() -> h.tree.getView().getRoot()));
        assertTrue(settle(() -> h.tree.getView().getRoot().getChildren().get(0).isExpanded()));
    }

    @Test
    void revealOpensTheFoldersOfANoteNotReadYetAndSelectsIt() throws Exception {
        assumeFx();
        FakeVault vault = nestedVault();
        Holder h = new Holder();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        settle(() -> null);

        onFx(() -> {
            h.tree.reveal("id-Acta semanal");
            return null;
        });
        String selected = settle(() -> {
            TreeItem<Component> item = h.tree.getView().getSelectionModel().getSelectedItem();
            return item == null ? null : item.getValue().getTitle();
        });

        assertEquals("Acta semanal", selected);
        assertTrue(settle(() -> h.tree.getView().getRoot().getChildren().get(1).isExpanded()));
    }

    @Test
    void scrollingUnderAStillMouseDoesNotCarryTheHoverToAnotherRow() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        for (int i = 10; i < 70; i++) {
            vault.note("ROOT", "Nota " + i);
        }
        Holder h = shown(vault, new ArrayList<>());

        onFx(() -> {
            setHover(h.cell("Nota 10"), true);
            h.tree.getView().scrollTo(40);
            h.layout();
            return null;
        });
        List<String> hovered = settle(() -> {
            h.layout();
            List<String> out = new ArrayList<>();
            for (Node node : h.tree.getView().lookupAll(".tree-cell")) {
                if (node instanceof TreeCell<?> cell && cell.isVisible() && cell.isHover() && !cell.isEmpty()
                        && cell.getItem() instanceof Component c && !"Nota 10".equals(c.getTitle())) {
                    out.add(c.getTitle());
                }
            }
            return out;
        });

        assertEquals(List.of(), hovered, "a reused row must not keep the hover of the item it showed before");
    }

    @Test
    void markingTheActiveNoteDoesNotRebuildTheRows() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.note("ROOT", "Uno");
        vault.note("ROOT", "Dos");
        Holder h = shown(vault, new ArrayList<>());

        boolean sameCell = onFx(() -> {
            TreeCell<Component> before = h.cell("Uno");
            h.tree.setActiveNoteId("id-Uno");
            h.layout();
            TreeCell<Component> after = h.cell("Uno");
            return before == after && after.getStyleClass().contains("explorer-active-note");
        });

        assertTrue(sameCell, "the row is restyled in place, not replaced (a replaced row loses the mouse hover)");
    }

    @Test
    void foldersShowTheBadgeTheOwnerProvidesAndUpdateWhenAsked() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Trabajo", "t");
        vault.note("ROOT", "Uno");
        Holder h = shown(vault, new ArrayList<>());
        int[] count = { 3 };

        List<String> badges = onFx(() -> {
            h.tree.setFolderBadge(folder -> "t".equals(folder.getId()) ? String.valueOf(count[0]) : null);
            h.layout();
            String first = badge(h.cell("Trabajo"));
            String note = badge(h.cell("Uno"));
            count[0] = 4;
            h.tree.refreshDecorations();
            h.layout();
            String second = badge(h.cell("Trabajo"));
            h.tree.setFolderBadge(null);
            h.layout();
            return Arrays.asList(first, note, second, badge(h.cell("Trabajo")));
        });

        assertEquals(Arrays.asList("3", null, "4", null), badges);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * A pinned, favourite or private note shows it in the explorer, as in the notes list, and
     * the marks stay visible when its row is selected, in both themes: none takes the
     * selection's own colour.
     */
    @Test
    void aNotesMarksShowAndStayVisibleWhenSelected() throws Exception {
        assumeFx();
        FakeVault vault = new FakeVault();
        vault.note("ROOT", "Marcada");
        vault.note("ROOT", "Normal");
        Note marked = vault.notes.get("ROOT").get(0);
        marked.setPinned(true);
        marked.setFavorite(true);
        marked.setPrivate(true);
        Holder h = shown(vault, new ArrayList<>());

        for (String theme : List.of("modern-theme.css", "dark-theme.css")) {
            List<String> seen = onFx(() -> {
                h.tree.getView().getScene().getStylesheets().setAll(
                        ExplorerTreeTest.class.getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                h.tree.getView().getRoot().getChildren().stream().filter(item -> item.getValue() == marked)
                        .findFirst().ifPresent(item -> h.tree.getView().getSelectionModel().select(item));
                h.layout();
                List<String> out = new ArrayList<>();
                for (Node node : h.tree.getView().lookupAll(".explorer-cell")) {
                    @SuppressWarnings("unchecked")
                    TreeCell<Component> cell = (TreeCell<Component>) node;
                    if (cell.getItem() == null) {
                        continue;
                    }
                    List<String> marks = new ArrayList<>();
                    for (Node icon : cell.lookupAll(".explorer-mark")) {
                        FontIcon font = (FontIcon) icon;
                        marks.add(font.getIconLiteral());
                        Color background = selectionColour(cell);
                        if (cell.isSelected() && font.getIconColor().equals(background)) {
                            marks.add("invisible");
                        }
                    }
                    out.add(cell.getItem().getTitle() + " " + marks);
                }
                return out;
            });
            assertEquals(List.of("Marcada [bi-pin-angle, fth-star, fth-lock]", "Normal []"), seen, theme);
        }
    }

    /** The colour the selected row is painted with (its first background fill). */
    private static Color selectionColour(TreeCell<Component> cell) {
        if (cell.getBackground() == null || cell.getBackground().getFills().isEmpty()) {
            return null;
        }
        Paint fill = cell.getBackground().getFills().get(cell.getBackground().getFills().size() - 1)
                .getFill();
        return fill instanceof Color color ? color : null;
    }

    private static final class Holder {
        ExplorerTree tree;
        Stage stage;

        void show() {
            stage = new Stage();
            stage.setScene(new Scene(tree.getView(), 300, 400));
            stage.show();
            layout();
        }

        void layout() {
            tree.getView().applyCss();
            tree.getView().layout();
        }

        @SuppressWarnings("unchecked")
        TreeCell<Component> cell(String title) {
            for (Node node : tree.getView().lookupAll(".tree-cell")) {
                if (node instanceof TreeCell<?> c && !c.isEmpty() && c.getTreeItem() != null) {
                    Object value = c.getTreeItem().getValue();
                    String shown = value instanceof Component comp ? comp.getTitle() : "explorer.loading";
                    if (title.equals(shown)) {
                        return (TreeCell<Component>) c;
                    }
                }
            }
            throw new AssertionError("no rendered row " + title);
        }
    }

    private static Holder shown(FakeVault vault, List<String> opened) throws Exception {
        Holder h = new Holder();
        onFx(() -> {
            h.tree = new ExplorerTree(vault, Runnable::run, key -> key);
            h.tree.setOnOpenNote((note, target) -> opened.add(note.getTitle() + " " + target));
            h.tree.setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        settle(() -> null);
        onFx(() -> {
            h.show();
            return null;
        });
        return h;
    }

    /** An in-memory vault that records which folders were read. */
    private static final class FakeVault implements ExplorerTree.Source {
        final Map<String, List<Folder>> folders = new HashMap<>();
        final Map<String, List<Note>> notes = new HashMap<>();
        final List<String> queries = new ArrayList<>();

        void folder(String parent, String title, String id) {
            folders.computeIfAbsent(parent, k -> new ArrayList<>()).add(new Folder(id, title));
        }

        void note(String parent, String title) {
            Note note = new Note(title, "");
            note.setId("id-" + title);
            notes.computeIfAbsent(parent, k -> new ArrayList<>()).add(note);
        }

        void removeNote(String parent, String title) {
            notes.getOrDefault(parent, new ArrayList<>()).removeIf(n -> title.equals(n.getTitle()));
        }

        void removeFolder(String parent, String title) {
            folders.getOrDefault(parent, new ArrayList<>()).removeIf(f -> title.equals(f.getTitle()));
        }

        @Override
        public synchronized List<Folder> subfolders(Folder folder) {
            queries.add(folder.getId());
            return new ArrayList<>(folders.getOrDefault(folder.getId(), List.of()));
        }

        @Override
        public synchronized List<Note> notes(Folder folder) {
            return new ArrayList<>(notes.getOrDefault(folder.getId(), List.of()));
        }
    }

    /** Holds submitted loads until the test runs them, in the order it chooses. */
    private static final class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            tasks.add(command);
        }

        synchronized Runnable takeFirst() {
            return tasks.removeFirst();
        }

        void runNext() {
            takeFirst().run();
        }

        void runAll() {
            Runnable next;
            while ((next = poll()) != null) {
                next.run();
            }
        }

        private synchronized Runnable poll() {
            return tasks.pollFirst();
        }
    }

    private static FakeVault nestedVault() {
        FakeVault vault = new FakeVault();
        vault.folder("ROOT", "Árbol genealógico", "a");
        vault.folder("ROOT", "Trabajo", "t");
        vault.note("ROOT", "Inicio");
        vault.folder("t", "Reuniones", "t/r");
        vault.note("t", "Acta 10");
        vault.note("t", "Acta 2");
        vault.note("t", "Presupuesto");
        vault.note("t/r", "Acta semanal");
        vault.note("a", "Abuelos");
        return vault;
    }

    /** The tree as indented titles: only expanded folders show their children. */
    private static List<String> outline(TreeItem<Component> parent, String indent) {
        List<String> out = new ArrayList<>();
        for (TreeItem<Component> child : parent.getChildren()) {
            out.add(indent + (child.getValue() != null ? child.getValue().getTitle() : "explorer.loading"));
            if (child.isExpanded()) {
                out.addAll(outline(child, indent + "  "));
            }
        }
        return out;
    }

    private static String badge(TreeCell<Component> cell) {
        for (Node node : cell.lookupAll(".folder-cell-count")) {
            if (node.isVisible() && node instanceof Label label) {
                return label.getText().replace("(", "").replace(")", "");
            }
        }
        return null;
    }

    private static void setHover(Node node, boolean hover) throws Exception {
        Method m = Node.class.getDeclaredMethod("setHover", boolean.class);
        m.setAccessible(true);
        m.invoke(node, hover);
    }

    private static double nameX(TreeCell<Component> cell) {
        for (Node node : cell.lookupAll(".explorer-cell-name")) {
            return node.localToScene(node.getBoundsInLocal()).getMinX();
        }
        throw new AssertionError("row has no name label");
    }

    private static List<String> titles(TreeItem<Component> parent) {
        List<String> out = new ArrayList<>();
        for (TreeItem<Component> child : parent.getChildren()) {
            out.add(child.getValue() != null ? child.getValue().getTitle() : "explorer.loading");
        }
        return out;
    }

    private static void click(Node node, MouseButton button, boolean shortcut) {
        Point2D at = node.localToScene(node.getBoundsInLocal().getWidth() - 4,
                node.getBoundsInLocal().getHeight() / 2);
        for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            boolean down = type == MouseEvent.MOUSE_PRESSED;
            Event.fireEvent(node, new MouseEvent(type, at.getX(), at.getY(), at.getX(), at.getY(),
                    button, 1, false, shortcut && !MAC, false, shortcut && MAC,
                    down && button == MouseButton.PRIMARY, down && button == MouseButton.MIDDLE, false,
                    false, false, true, null));
        }
    }

    private static void assumeFx() {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
    }

    /** Lets pending FX work (load results posted with runLater) run, then reads on the FX thread. */
    private static <T> T settle(Callable<T> read) throws Exception {
        onFx(() -> null);
        onFx(() -> null);
        return onFx(read);
    }
}
