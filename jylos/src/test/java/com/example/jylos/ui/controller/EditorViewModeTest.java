package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.event.EventBus;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Pane;

/**
 * The editor shows the note as source, as the reading view, or both side by side. The
 * reading view is read-only: the title becomes a heading and the button above the note
 * offers to go back to editing. The editor's button, the palette and the toolbar all switch
 * it.
 */
class EditorViewModeTest {

    private static final String EDITING = "editor true reading false title-field true icon fth-book-open";
    private static final String READING = "editor false reading true title-field false icon fth-edit-3";
    private static final String SPLIT = "editor true reading true title-field true icon fth-edit-3";

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void theReadingButtonSwitchesToTheReadingViewAndBack(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        NoteService notes = notes(vault);

        List<String> seen = onFx(() -> {
            FXMLLoader loader = openNote(notes);
            ButtonBase button = (ButtonBase) loader.getNamespace().get("readingModeButton");
            List<String> out = new ArrayList<>();
            out.add(state(loader));
            button.fire();
            out.add(state(loader));
            button.fire();
            out.add(state(loader));
            return out;
        });

        assertEquals(List.of(EDITING, READING, EDITING), seen);
    }

    @Test
    void thePaletteSwitchesTheReadingViewAndTheSplitView(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        NoteService notes = notes(vault);

        List<String> seen = onFx(() -> {
            FXMLLoader loader = openNote(notes);
            MainController main = mainWith(loader.getController());
            List<String> out = new ArrayList<>();
            command(main, "cmd.toggle_reading_view").run();
            out.add(state(loader));
            command(main, "cmd.toggle_reading_view").run();
            out.add(state(loader));
            command(main, "cmd.split_mode").run();
            out.add(state(loader));
            // From the split view the toggle goes back to editing, as its icon offers.
            command(main, "cmd.toggle_reading_view").run();
            out.add(state(loader));
            return out;
        });

        assertEquals(List.of(READING, EDITING, SPLIT, EDITING), seen);
    }

    /** The reading view is drawn in the theme the window applied, even before any render. */
    @Test
    void theReadingViewIsDrawnInTheAppliedTheme(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        NoteService notes = notes(vault);
        List<Boolean> renders = new CopyOnWriteArrayList<>();

        onFx(() -> {
            FXMLLoader loader = openNote(notes);
            EditorController editor = loader.getController();
            editor.registerPreviewEnhancer("recorder", new PreviewEnhancer() {
                @Override
                public String transformHtml(PreviewContext context, String html) {
                    renders.add(context.darkTheme());
                    return html;
                }
            });
            editor.applyEditorTheme(true, null);
            ((ButtonBase) loader.getNamespace().get("readingModeButton")).fire();
            return null;
        });

        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until && renders.isEmpty()) {
            Thread.sleep(25);
        }
        assertEquals(List.of(true), renders.stream().distinct().toList(), "themes drawn: " + renders);
    }

    private static NoteService notes(Path vault) throws Exception {
        Files.writeString(vault.resolve("Nota.md"), "texto\n", StandardCharsets.UTF_8);
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    private FXMLLoader openNote(NoteService notes) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
        new Scene(new Pane((Parent) loader.load()), 900, 600);
        EditorController editor = loader.getController();
        editor.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status -> { },
                List::of, false, enabled -> { }, null, null, null);
        editor.loadNote(notes.getNoteById("Nota.md").orElseThrow());
        return loader;
    }

    /** A window controller with only the editor and the status message it writes to. */
    private static MainController mainWith(EditorController editor) throws Exception {
        MainController main = new MainController();
        for (Map.Entry<String, Object> field : Map.<String, Object>of(
                "editorController", editor, "statusLabel", new Label()).entrySet()) {
            Field f = MainController.class.getDeclaredField(field.getKey());
            f.setAccessible(true);
            f.set(main, field.getValue());
        }
        return main;
    }

    private static Runnable command(MainController main, String id) throws Exception {
        Method resolve = MainController.class.getDeclaredMethod("resolveCommandAction", String.class);
        resolve.setAccessible(true);
        return (Runnable) resolve.invoke(main, id);
    }

    private static String state(FXMLLoader loader) {
        Map<String, Object> ids = loader.getNamespace();
        SplitPane split = (SplitPane) ids.get("editorPreviewSplitPane");
        return "editor " + shown(split, (Node) ids.get("editorPane"))
                + " reading " + shown(split, (Node) ids.get("previewPane"))
                + " title-field " + ((Node) ids.get("noteTitleField")).isVisible()
                + " icon " + ((FontIcon) ids.get("readingModeIcon")).getIconLiteral();
    }

    private static boolean shown(SplitPane split, Node pane) {
        return split.getItems().contains(pane) && pane.isVisible();
    }
}
