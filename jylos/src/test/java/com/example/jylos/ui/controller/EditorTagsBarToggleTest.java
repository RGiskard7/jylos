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
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;

/**
 * The tags bar under the note title opens and closes from its button and from the palette's
 * "Toggle tags" command, and the button shows whether it is open either way.
 */
class EditorTagsBarToggleTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void theButtonOpensAndClosesTheTagsBar(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        NoteService notes = notes(vault);

        List<String> seen = onFx(() -> {
            FXMLLoader loader = openNote(notes);
            ToggleButton button = (ToggleButton) loader.getNamespace().get("toggleTagsBtn");
            List<String> out = new ArrayList<>();
            out.add(state(loader));
            button.fire();
            out.add(state(loader));
            button.fire();
            out.add(state(loader));
            return out;
        });

        assertEquals(List.of("bar false button false", "bar true button true", "bar false button false"), seen);
    }

    @Test
    void thePaletteCommandOpensAndClosesTheTagsBar(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        NoteService notes = notes(vault);

        List<String> seen = onFx(() -> {
            FXMLLoader loader = openNote(notes);
            MainController main = new MainController();
            Field editor = MainController.class.getDeclaredField("editorController");
            editor.setAccessible(true);
            editor.set(main, loader.getController());
            Method resolve = MainController.class.getDeclaredMethod("resolveCommandAction", String.class);
            resolve.setAccessible(true);
            Runnable toggleTags = (Runnable) resolve.invoke(main, "cmd.toggle_tags");
            List<String> out = new ArrayList<>();
            out.add(state(loader));
            toggleTags.run();
            out.add(state(loader));
            toggleTags.run();
            out.add(state(loader));
            return out;
        });

        assertEquals(List.of("bar false button false", "bar true button true", "bar false button false"), seen);
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

    private static String state(FXMLLoader loader) {
        Node bar = (Node) loader.getNamespace().get("tagsContainer");
        ToggleButton button = (ToggleButton) loader.getNamespace().get("toggleTagsBtn");
        return "bar " + (bar.isVisible() && bar.isManaged()) + " button " + button.isSelected();
    }
}
