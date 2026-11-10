package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.NoteContextMenuEntry;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

/**
 * Characterization of the note context menu of the notes list and of the grid cards, pinned
 * before both menus moved to one shared builder ({@link NoteContextMenuSupport}) that the
 * Explorer tree also uses. The menu is
 * described as its visible entries, in order, with "(off)" on disabled ones and "---" for
 * separators. "Reveal in files" is only shown for a folder vault, which this test reads from
 * the machine's own preferences, as the app does.
 */
class NoteContextMenuCharacterizationTest {

    private static final boolean FILE_VAULT = !"sqlite".equals(
            Preferences.userNodeForPackage(NotesListController.class).get("storage_type", "sqlite"));

    @Test
    void listMenuOfAPlainNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> entries = onFxThread(() -> describe(new Fixture().cell(0).getContextMenu()));

        assertEquals(expected("Fijar Nota", "Añadir a Favoritos", "Hacer Privada", "Mover a la Papelera"), entries);
    }

    @Test
    void listMenuOfAPinnedFavoritePrivateNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> entries = onFxThread(() -> describe(new Fixture().cell(1).getContextMenu()));

        assertEquals(expected("Desfijar Nota", "Quitar de Favoritos", "Hacer Normal (Descifrar)", "Mover a la Papelera (off)"),
                entries);
    }

    @Test
    void gridMenuOfAPlainNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> entries = onFxThread(() -> {
            Fixture f = new Fixture();
            return describe(f.gridMenu(f.list.getItems().get(0), () -> { }));
        });

        assertEquals(expected("Fijar Nota", "Añadir a Favoritos", "Hacer Privada", "Mover a la Papelera"), entries);
    }

    @Test
    void gridMenuOfAPinnedFavoritePrivateNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> entries = onFxThread(() -> {
            Fixture f = new Fixture();
            return describe(f.gridMenu(f.list.getItems().get(1), () -> { }));
        });

        // Changed on purpose when both menus moved to NoteContextMenuSupport: the grid used to
        // leave "Move to trash" enabled for a private note (clicking it only showed "cannot
        // delete a private note"); it is now disabled, as in the list.
        assertEquals(expected("Desfijar Nota", "Quitar de Favoritos", "Hacer Normal (Descifrar)",
                "Mover a la Papelera (off)"), entries);
    }

    @Test
    void listMenuEntriesReachTheirActions() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> calls = onFxThread(() -> {
            Fixture f = new Fixture();
            ContextMenu menu = f.cell(0).getContextMenu();
            for (String label : List.of("Abrir", "Abrir en pestaña nueva", "Hacer Privada", "Exportar nota…",
                    "Plugin: contar")) {
                item(menu, label).fire();
            }
            return f.calls;
        });

        assertEquals(List.of("open a", "newTab a", "privacy a", "export a", "plugin a"), calls);
    }

    @Test
    void gridMenuEntriesReachTheirActions() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> calls = onFxThread(() -> {
            Fixture f = new Fixture();
            ContextMenu menu = f.gridMenu(f.list.getItems().get(0), () -> f.calls.add("gridOpen"));
            for (String label : List.of("Abrir", "Abrir en pestaña nueva", "Hacer Privada", "Exportar nota…",
                    "Plugin: contar")) {
                item(menu, label).fire();
            }
            return f.calls;
        });

        assertEquals(List.of("gridOpen", "newTab a", "privacy a", "export a", "plugin a"), calls);
    }

    private static List<String> expected(String pin, String favorite, String privacy, String delete) {
        List<String> out = new ArrayList<>(List.of("Abrir", "Abrir en pestaña nueva", pin, favorite, privacy,
                "Renombrar", "Mover a Carpeta..."));
        if (FILE_VAULT) {
            out.add("Mostrar en el explorador de archivos");
        }
        out.addAll(List.of("Exportar nota…", "---", delete, "---", "Plugin: contar"));
        return out;
    }

    private static List<String> describe(ContextMenu menu) {
        assertNotNull(menu, "no context menu");
        List<String> out = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (!item.isVisible()) {
                continue;
            }
            if (item instanceof SeparatorMenuItem) {
                out.add("---");
            } else {
                out.add(item.getText() + (item.isDisable() ? " (off)" : ""));
            }
        }
        return out;
    }

    private static MenuItem item(ContextMenu menu, String label) {
        return menu.getItems().stream().filter(i -> label.equals(i.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("no menu entry " + label));
    }

    private static final class Fixture {
        final ListView<Note> list;
        final NotesListController controller;
        final List<String> calls = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Fixture() throws Exception {
            URL fxml = NoteContextMenuCharacterizationTest.class.getClassLoader()
                    .getResource("com/example/jylos/ui/view/NotesListView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            Parent root = loader.load();
            controller = loader.getController();
            controller.wire(null, null, null, null, bundle,
                    n -> calls.add("open " + n.getId()), n -> calls.add("export " + n.getId()),
                    n -> calls.add("privacy " + n.getId()),
                    (notes, msg) -> { }, msg -> { },
                    () -> List.of(new NoteContextMenuEntry("demo", "Plugin: contar", n -> calls.add("plugin " + n.getId()))));
            controller.setOpenInNewTabAction(n -> calls.add("newTab " + n.getId()));

            Field field = NotesListController.class.getDeclaredField("notesListView");
            field.setAccessible(true);
            list = (ListView<Note>) field.get(controller);
            Note plain = note("a");
            Note flagged = note("b");
            flagged.setPinned(true);
            flagged.setFavorite(true);
            flagged.setPrivate(true);
            list.getItems().setAll(plain, flagged);

            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320, 480));
            stage.show();
            root.applyCss();
            root.layout();
        }

        @SuppressWarnings("unchecked")
        ListCell<Note> cell(int index) {
            for (Node node : list.lookupAll(".list-cell")) {
                if (node instanceof ListCell<?> c && c.getIndex() == index && !c.isEmpty()) {
                    return (ListCell<Note>) c;
                }
            }
            throw new AssertionError("no rendered cell for row " + index);
        }

        ContextMenu gridMenu(Note note, Runnable open) throws Exception {
            Method m = NotesListController.class.getDeclaredMethod("createGridNoteContextMenu", Note.class,
                    Runnable.class);
            m.setAccessible(true);
            return (ContextMenu) m.invoke(controller, note, open);
        }
    }

    private static Note note(String id) {
        Note note = new Note(id, "content");
        note.setId(id);
        return note;
    }

    private static <T> T onFxThread(Callable<T> work) throws Exception {
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
