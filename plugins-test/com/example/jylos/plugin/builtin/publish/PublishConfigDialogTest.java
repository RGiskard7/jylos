package com.example.jylos.plugin.builtin.publish;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

import javafx.application.Platform;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeItem;

/**
 * Real behavioural checks for {@link PublishConfigDialog}'s tree/flat view split — this is
 * exactly the thing string-content or compile-only checks cannot catch: whether the actual
 * {@link CheckBoxTreeItem} scene graph the dialog builds has real folder structure in it, and
 * whether the two toggle buttons actually swap which widget is visible. Runs on the real
 * JavaFX toolkit (headless {@link JFXPanel} init trick — no visible window, but the same
 * Node/Control classes as the live app), not a description of what the code "should" do.
 */
public final class PublishConfigDialogTest {

    private static int passed;
    private static int failed;

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + "  -> " + detail);
        }
    }

    public static void main(String[] args) throws Exception {
        // Headless JavaFX toolkit init — no Stage/Scene ever shown, but Controls need the
        // toolkit alive before construction or they throw IllegalStateException. Platform.
        // startup (not Application.launch) is the no-window way to do this without pulling
        // in javafx-swing just for JFXPanel's init side effect.
        CountDownLatch toolkitReady = new CountDownLatch(1);
        Platform.startup(toolkitReady::countDown);
        toolkitReady.await(10, TimeUnit.SECONDS);

        Folder books = folder("books");
        Folder scifi = folder("scifi");
        // "zebra" sorts last but is returned FIRST by the fake service, so an ordering
        // assertion cannot pass by accident on insertion order alone.
        Folder zebra = folder("zebra");
        Note dune = note("dune", "Dune");
        Note foundation = note("foundation", "Foundation");
        Note orphan = note("orphan", "Untethered Note");
        Note alpha = note("alpha", "Alpha Note");

        NoteService noteService = new NoteService(noOpNoteDao(), noOpFolderDao()) {
            @Override
            public List<Note> getAllNotes() {
                return List.of(dune, foundation, orphan, alpha);
            }

            @Override
            public List<Note> getNotesByFolder(Folder folder) {
                return switch (folder.getId()) {
                    case "books" -> List.of(dune);
                    case "scifi" -> List.of(foundation);
                    default -> List.of();
                };
            }
        };

        FolderService folderService = new FolderService(noOpFolderDao(), noOpNoteDao()) {
            @Override
            public List<Folder> getRootFolders() {
                return List.of(zebra, books); // deliberately not alphabetical
            }

            @Override
            public List<Folder> getSubfolders(Folder parent) {
                return "books".equals(parent.getId()) ? List.of(scifi) : List.of();
            }
        };

        AtomicReference<PublishConfigDialog.Built> builtRef = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            try {
                builtRef.set(PublishConfigDialog.buildContent(noteService, folderService, null, true));
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("buildContent did not complete on the FX thread within 10s");
        }
        if (failure.get() != null) {
            throw new RuntimeException("buildContent threw", failure.get());
        }
        PublishConfigDialog.Built built = builtRef.get();

        System.out.println("\n-- tree mode actually has real folder structure, not a flat note list --");
        CheckBoxTreeItem<Object> booksItem = findChildByValue(built.root(), books);
        check("root has a child item for the 'books' folder itself (a Folder value, not a Note)",
                booksItem != null, "root children: " + describeChildren(built.root()));
        if (booksItem != null) {
            CheckBoxTreeItem<Object> duneItem = findChildByValue(booksItem, dune);
            check("'books' folder item has 'Dune' nested under it as a child (not sitting flat at the root)",
                    duneItem != null, "books children: " + describeChildren(booksItem));
            CheckBoxTreeItem<Object> scifiItem = findChildByValue(booksItem, scifi);
            check("'books' folder item also has the 'scifi' SUBFOLDER nested under it",
                    scifiItem != null, "books children: " + describeChildren(booksItem));
            if (scifiItem != null) {
                CheckBoxTreeItem<Object> foundationItem = findChildByValue(scifiItem, foundation);
                check("'scifi' subfolder has 'Foundation' nested two levels deep under root",
                        foundationItem != null, "scifi children: " + describeChildren(scifiItem));
            }
        }
        CheckBoxTreeItem<Object> orphanAtRoot = findChildByValue(built.root(), orphan);
        check("a root-level note (no folder) sits directly under root, alongside the folder item",
                orphanAtRoot != null, "root children: " + describeChildren(built.root()));
        // getAllNotes() returns EVERY note in the vault, folder-contained ones included — naively
        // treating all of them as "root-level" would ALSO add Dune/Foundation directly under root,
        // duplicated, on top of the real copies correctly nested inside their folders above. With a
        // big enough vault that duplicate flood buries the folder rows so far down it reads as "the
        // tree view shows the exact same thing as the flat view" — the bug actually reported.
        check("'Dune' does NOT ALSO sit duplicated directly under root — only nested inside 'books'",
                findChildByValue(built.root(), dune) == null, "root children: " + describeChildren(built.root()));
        check("'Foundation' does NOT ALSO sit duplicated directly under root — only nested inside 'scifi'",
                findChildByValue(built.root(), foundation) == null, "root children: " + describeChildren(built.root()));

        System.out.println("\n-- ordering: folders before notes, each group by name (same as Jylos's sidebar tree) --");
        check("root lists both folders first, alphabetically, then the loose notes alphabetically "
                        + "(the fake service hands back 'zebra' before 'books' on purpose)",
                "[Folder:books, Folder:zebra, Note:alpha, Note:orphan, ]".equals(describeChildren(built.root())),
                "root children: " + describeChildren(built.root()));
        if (booksItem != null) {
            check("inside a folder too: the 'scifi' subfolder comes before the folder's own note 'Dune'",
                    "[Folder:scifi, Note:dune, ]".equals(describeChildren(booksItem)),
                    "books children: " + describeChildren(booksItem));
        }

        System.out.println("\n-- default state matches the dialog's original flat-only behavior --");
        check("'All notes' toggle is selected by default", built.flatModeButton().isSelected(), "");
        check("'Folder tree' toggle is NOT selected by default", !built.treeModeButton().isSelected(), "");
        check("the flat list is visible by default", built.flatView().isVisible() && built.flatView().isManaged(),
                "visible=" + built.flatView().isVisible() + " managed=" + built.flatView().isManaged());
        check("the tree is hidden by default", !built.tree().isVisible() && !built.tree().isManaged(),
                "visible=" + built.tree().isVisible() + " managed=" + built.tree().isManaged());

        System.out.println("\n-- clicking 'Folder tree' actually swaps which widget is shown --");
        CountDownLatch toggled = new CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            built.treeModeButton().fire(); // fire(), not setSelected() directly — exercises the
                                            // same ToggleGroup/ActionEvent path a real click does
            toggled.countDown();
        });
        toggled.await(10, TimeUnit.SECONDS);
        check("after clicking 'Folder tree', the tree becomes visible",
                built.tree().isVisible() && built.tree().isManaged(),
                "visible=" + built.tree().isVisible() + " managed=" + built.tree().isManaged());
        check("...and the flat list is hidden",
                !built.flatView().isVisible() && !built.flatView().isManaged(),
                "visible=" + built.flatView().isVisible() + " managed=" + built.flatView().isManaged());
        check("'All notes' toggle is no longer selected (ToggleGroup mutual exclusion)",
                !built.flatModeButton().isSelected(), "");

        System.out.println("\n-- unchecking a whole folder cascades to everything nested under it --");
        CountDownLatch unchecked = new CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            findChildByValue(built.root(), books).setSelected(false);
            unchecked.countDown();
        });
        unchecked.await(10, TimeUnit.SECONDS);
        CheckBoxTreeItem<Object> booksAfter = findChildByValue(built.root(), books);
        CheckBoxTreeItem<Object> duneAfter = findChildByValue(booksAfter, dune);
        CheckBoxTreeItem<Object> scifiAfter = findChildByValue(booksAfter, scifi);
        CheckBoxTreeItem<Object> foundationAfter = findChildByValue(scifiAfter, foundation);
        check("unchecking 'books' unchecks 'Dune' directly inside it",
                !duneAfter.isSelected(), "dune.selected=" + duneAfter.isSelected());
        check("...and unchecks the 'scifi' subfolder too",
                !scifiAfter.isSelected(), "scifi.selected=" + scifiAfter.isSelected());
        check("...and unchecks 'Foundation', nested two levels down inside that subfolder",
                !foundationAfter.isSelected(), "foundation.selected=" + foundationAfter.isSelected());
        check("a note OUTSIDE the unchecked folder is unaffected",
                findChildByValue(built.root(), orphan).isSelected(), "");

        // ---------------------------------------------------------------------------
        // Everything above inspects the MODEL (TreeItem structure, toggle state). That
        // is not enough on its own, and this suite proved it: the model was correct all
        // along while the tree rendered blank in the real app, because TreeView's
        // VirtualFlow creates cells for the empty rows past the last item and calls
        // updateItem(null) on them — and the StringConverter dereferenced that null,
        // throwing inside layoutChildren on the FX thread and aborting the flow
        // mid-population. Nothing that skips an actual layout pass can see that. So:
        // put the tree in a real Stage, show it, force a pulse, and watch the FX
        // thread's own uncaught-exception handler.
        System.out.println("\n-- the tree actually RENDERS (real Stage, real layout pass, no FX-thread crash) --");
        AtomicReference<Throwable> fxCrash = new AtomicReference<>();
        AtomicReference<Integer> renderedCells = new AtomicReference<>(-1);
        CountDownLatch rendered = new CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> fxCrash.set(e));
            try {
                // setSelected, NOT fire(): the toggle section above already fired this button,
                // and firing an already-selected ToggleButton in a ToggleGroup DESELECTS it
                // (JavaFX allows an empty selection in a group, unlike RadioButton) — which
                // would hide the tree and make this section report 0 cells for a reason that
                // has nothing to do with the tree's own rendering.
                built.treeModeButton().setSelected(true);
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(built.content(), 660, 620));
                stage.show();
                built.content().applyCss();
                built.content().layout(); // forces the VirtualFlow to build and update its cells

                int cells = 0;
                for (javafx.scene.Node node : built.tree().lookupAll(".tree-cell")) {
                    if (node instanceof javafx.scene.control.TreeCell<?> cell && !cell.isEmpty()) {
                        cells++;
                    }
                }
                renderedCells.set(cells);
                stage.close();
            } catch (Throwable t) {
                fxCrash.set(t);
            } finally {
                rendered.countDown();
            }
        });
        rendered.await(15, TimeUnit.SECONDS);
        check("laying the tree out surfaced no escaping FX-thread exception",
                fxCrash.get() == null, String.valueOf(fxCrash.get()));
        check("the tree actually rendered non-empty cells (the folder row and the root-level note), "
                        + "rather than laying out to a blank box",
                renderedCells.get() >= 2, "non-empty rendered cells: " + renderedCells.get());
        // HONEST SCOPE, do not over-trust this section. It catches "the tree renders nothing
        // at all", and that is worth having. It does NOT cover either of the two fixes made
        // after the crash report against the running app — verified by sabotage, both times
        // it still passed:
        //   1. the StringConverter's null guard. Reproducing it needs a real VirtualFlow
        //      re-indexing live cells; a fixture this small never asks for an out-of-range
        //      index, and driving TreeCell.updateIndex by hand outside a skin does not route
        //      through to updateItem the way the production stack trace shows it doing.
        //   2. not wrapping the TreeView in a ScrollPane. With the wrapper back the tree is
        //      still visible in tree mode, so cells still render here; what the wrapper broke
        //      was hiding it in FLAT mode (the wrapper stayed laid out, dragging the hidden
        //      tree through a layout pass it should never have had).
        // Both fixes rest on the production stack trace, not on this test.

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        // Platform.startup's FX Application Thread is non-daemon — without telling it to
        // stop, the JVM hangs forever after main() returns, even with 0 failures.
        Platform.exit();
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static CheckBoxTreeItem<Object> findChildByValue(CheckBoxTreeItem<Object> parent, Object value) {
        for (TreeItem<Object> child : parent.getChildren()) {
            if (child.getValue() == value && child instanceof CheckBoxTreeItem<Object> checkChild) {
                return checkChild;
            }
        }
        return null;
    }

    private static String describeChildren(CheckBoxTreeItem<Object> item) {
        StringBuilder sb = new StringBuilder("[");
        for (TreeItem<Object> child : item.getChildren()) {
            Object v = child.getValue();
            sb.append(v instanceof Folder f ? "Folder:" + f.getId() : v instanceof Note n ? "Note:" + n.getId() : v)
                    .append(", ");
        }
        return sb.append(']').toString();
    }

    private static Folder folder(String id) {
        Folder f = new Folder(id, null, null);
        f.setId(id);
        return f;
    }

    private static Note note(String id, String title) {
        Note n = new Note(id, "");
        n.setId(id);
        n.setTitle(title);
        return n;
    }

    private static NoteDAO noOpNoteDao() {
        return (NoteDAO) Proxy.newProxyInstance(NoteDAO.class.getClassLoader(),
                new Class<?>[] { NoteDAO.class }, (proxy, method, methodArgs) -> defaultValue(method.getReturnType()));
    }

    private static FolderDAO noOpFolderDao() {
        return (FolderDAO) Proxy.newProxyInstance(FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class }, (proxy, method, methodArgs) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return type == List.class ? List.of() : null;
        }
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        return null;
    }
}
