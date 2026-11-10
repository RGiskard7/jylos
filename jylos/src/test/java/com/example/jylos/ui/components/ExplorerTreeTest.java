package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.service.NoteService.SortOption;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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

    // ------------------------------------------------------------------ fixtures

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

    private static List<String> titles(TreeItem<Component> parent) {
        List<String> out = new ArrayList<>();
        for (TreeItem<Component> child : parent.getChildren()) {
            out.add(child.getValue() != null ? child.getValue().getTitle() : "explorer.loading");
        }
        return out;
    }

    private static void click(Node node, MouseButton button, boolean shortcut) {
        javafx.geometry.Point2D at = node.localToScene(node.getBoundsInLocal().getWidth() - 4,
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

    private static <T> T onFx(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
