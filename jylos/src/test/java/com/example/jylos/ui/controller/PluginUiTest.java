package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * What a plugin adds to the window — a Plugins menu item, a side panel, a toolbar button, a
 * note context menu entry — shows up, and goes away again when the plugin takes it out. A
 * menu item of a plugin that is not enabled does not run and says so.
 */
class PluginUiTest {

    @Test
    void contributionsAppearAndAreTakenOutAgain() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> status = new ArrayList<>();
        List<String> ran = new ArrayList<>();
        PluginUi ui = new PluginUi();
        ToolbarController toolbar = onFx(() -> {
            URL fxml = PluginUiTest.class.getResource("/com/example/jylos/ui/view/ToolbarView.fxml");
            FXMLLoader loader = new FXMLLoader(fxml,
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            loader.load();
            return loader.getController();
        });
        VBox panels = onFx(VBox::new);
        onFx(() -> {
            panels.setVisible(false);
            ui.wire(key -> key + " {0}", status::add, () -> null, () -> toolbar, panels, new HBox());
            ui.registerMenuItem("demo", "Demo", "Saludar", () -> ran.add("menu"));
            ui.registerSidePanel("demo", "panel", "Panel", new Label("contenido"), "fth-star");
            ui.registerToolbarButton("demo", "boton", "Botón", null, () -> ran.add("button"));
            ui.registerNoteContextMenuItem("demo", "Copiar", note -> ran.add("note " + note.getTitle()));
            return null;
        });
        settle();

        List<String> shown = onFx(() -> {
            List<String> out = new ArrayList<>();
            Menu demo = (Menu) toolbar.getPluginsMenu().getItems().stream()
                    .filter(i -> "Demo".equals(i.getText())).findFirst().orElseThrow();
            MenuItem saludar = demo.getItems().get(0);
            out.add("menu " + saludar.getText());
            saludar.fire();
            out.add("panels " + panels.getChildren().size() + ", shown " + panels.isVisible());
            HBox buttons = toolbar.getPluginToolbarContainer();
            Button button = (Button) buttons.getChildren().stream()
                    .filter(n -> "plugin-toolbar-demo-boton".equals(n.getId())).findFirst().orElseThrow();
            button.fire();
            out.add("context " + ui.getNoteContextMenuItems().get(0).label());
            ui.getNoteContextMenuItems().get(0).action().accept(new Note("Uno", ""));
            ui.removePluginMenuItems("demo");
            ui.removeAllSidePanels("demo");
            ui.removeToolbarButtons("demo");
            ui.removePluginNoteContextMenuItems("demo");
            return out;
        });
        settle();

        List<String> after = onFx(() -> List.of(
                "menu left " + toolbar.getPluginsMenu().getItems().stream().anyMatch(i -> "Demo".equals(i.getText())),
                "panels " + panels.getChildren().size() + ", shown " + panels.isVisible(),
                "buttons " + toolbar.getPluginToolbarContainer().getChildren().stream()
                        .anyMatch(n -> "plugin-toolbar-demo-boton".equals(n.getId())),
                "context " + ui.getNoteContextMenuItems().size()));

        assertEquals(List.of("menu Saludar", "panels 1, shown true", "context Copiar"), shown);
        // The menu item of a plugin that is not enabled does not run and says so.
        assertEquals(List.of("status.plugin_not_enabled demo"), status);
        assertEquals(List.of("button", "note Uno"), ran);
        assertEquals(List.of("menu left false", "panels 0, shown false", "buttons false", "context 0"), after);
    }

    /**
     * A plugin's status bar item shows in the status bar's plugin area, after a separator as
     * the bar's own items; registering the same id again replaces it, and it can be removed
     * alone or with the rest of the plugin's items.
     */
    @Test
    void statusBarItemsAppearAreReplacedAndAreTakenOut() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        PluginUi ui = new PluginUi();
        HBox bar = onFx(HBox::new);
        onFx(() -> {
            ui.wire(key -> key, message -> { }, () -> null, () -> null, null, bar);
            ui.registerStatusBarItem("demo", "palabras", new Label("10 palabras"));
            ui.registerStatusBarItem("demo", "reloj", new Label("12:00"));
            ui.registerStatusBarItem("otro", "sync", new Label("Sincronizado"));
            return null;
        });
        settle();
        List<String> seen = new ArrayList<>();
        seen.add(onFx(() -> texts(bar)));
        onFx(() -> {
            ui.registerStatusBarItem("demo", "palabras", new Label("11 palabras"));
            return null;
        });
        settle();
        seen.add(onFx(() -> texts(bar)));
        onFx(() -> {
            ui.removeStatusBarItem("demo", "reloj");
            return null;
        });
        settle();
        seen.add(onFx(() -> texts(bar)));
        onFx(() -> {
            ui.removeStatusBarItems("demo");
            return null;
        });
        settle();
        seen.add(onFx(() -> texts(bar)));

        assertEquals(List.of(
                "| 10 palabras | 12:00 | Sincronizado",
                "| 11 palabras | 12:00 | Sincronizado",
                "| 11 palabras | Sincronizado",
                "| Sincronizado"), seen);
    }

    /** The bar's items in order: "|" for a separator, the text of each label. */
    private static String texts(HBox bar) {
        StringBuilder out = new StringBuilder();
        for (javafx.scene.Node item : bar.getChildren()) {
            for (javafx.scene.Node node : ((HBox) item).getChildren()) {
                if (node.getStyleClass().contains("status-separator")) {
                    out.append(out.length() > 0 ? " | " : "| ");
                } else if (node instanceof Label label) {
                    out.append(label.getText());
                }
            }
        }
        return out.toString();
    }

    /** Lets the registrations queued with Platform.runLater run. */
    private static void settle() throws Exception {
        TimeUnit.MILLISECONDS.sleep(100);
        onFx(() -> null);
    }
}
