package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

/**
 * The format bar under the editor folds away from its own arrow and comes back from the notch
 * it leaves at the bottom of the editor, or from the "toggle format bar" command; every change
 * is handed on to be remembered.
 */
class EditorFormatToolbarTest {

    @Test
    void theArrowFoldsTheBarAndTheNotchBringsItBack() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Parent root = loadEditor();
            EditorController controller = (EditorController) root.getProperties().get("controller");
            List<Boolean> remembered = new ArrayList<>();
            controller.formatToolbar().bind(true, remembered::add);
            List<String> out = new ArrayList<>();
            out.add(state(root));
            ((Button) root.lookup("#collapseFormatToolbarBtn")).fire();
            out.add(state(root));
            ((Button) root.lookup("#formatToolbarNotch")).fire();
            out.add(state(root));
            out.add("remembered " + remembered);
            return out;
        });

        assertEquals(List.of("bar true notch false", "bar false notch true", "bar true notch false",
                "remembered [false, true]"), seen);
    }

    @Test
    void thePaletteCommandTogglesTheBar() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Parent root = loadEditor();
            MainController main = new MainController();
            Field editor = MainController.class.getDeclaredField("editorController");
            editor.setAccessible(true);
            editor.set(main, root.getProperties().get("controller"));
            Method resolve = MainController.class.getDeclaredMethod("resolveCommandAction", String.class);
            resolve.setAccessible(true);
            Runnable toggle = (Runnable) resolve.invoke(main, "cmd.toggle_format_toolbar");
            List<String> out = new ArrayList<>();
            out.add(state(root));
            toggle.run();
            out.add(state(root));
            toggle.run();
            out.add(state(root));
            return out;
        });

        assertEquals(List.of("bar true notch false", "bar false notch true", "bar true notch false"), seen);
    }

    /** A bar hidden from Preferences leaves the notch too, so it can be brought back from there. */
    @Test
    void aBarHiddenFromPreferencesLeavesTheNotch() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        String seen = onFx(() -> {
            Parent root = loadEditor();
            EditorController controller = (EditorController) root.getProperties().get("controller");
            controller.formatToolbar().setVisible(false);
            return state(root);
        });

        assertEquals("bar false notch true", seen);
    }

    private static Parent loadEditor() throws Exception {
        FXMLLoader loader = new FXMLLoader(EditorFormatToolbarTest.class.getResource(
                "/com/example/jylos/ui/view/EditorView.fxml"),
                ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
        Parent root = loader.load();
        root.getProperties().put("controller", loader.getController());
        new Scene(new Pane(root), 1000, 700);
        ((EditorController) loader.getController()).loadNote(new Note("a.md", "A", "x"));
        // The split pane's items join the scene graph with its skin, after a CSS pass.
        root.applyCss();
        return root;
    }

    private static String state(Parent root) {
        Node bar = root.lookup("#formatToolbarContainer");
        Node notch = root.lookup("#formatToolbarNotch");
        return "bar " + (bar.isVisible() && bar.isManaged()) + " notch " + notch.isVisible();
    }
}
