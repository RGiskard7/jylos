package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.preferences.LayoutMode;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.stage.Stage;

/**
 * The toolbar's layout button looks like its neighbours (its icon centred and the same
 * size) and opens a menu with the three layouts, the current one ticked.
 */
class LayoutButtonTest {

    @ParameterizedTest
    @ValueSource(strings = { "modern-theme.css", "dark-theme.css" })
    void itsIconIsCentredLikeTheNeighbouringButtons(String theme) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        double[] measures = onFx(() -> {
            Fixture f = new Fixture(theme);
            ButtonBase sidebar = f.button("sidebarToggleBtn");
            ButtonBase layout = f.button("layoutSwitchBtn");
            return new double[] { iconOffset(sidebar), iconOffset(layout), sidebar.getWidth(), layout.getWidth() };
        });

        assertEquals(measures[0], measures[1], 0.75, "icon offset from the button centre");
        assertEquals(measures[2], measures[3], 0.75, "button width");
    }

    @Test
    void clickingItOffersTheThreeLayoutsWithTheCurrentOneTicked() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<LayoutMode> chosen = new ArrayList<>();

        List<String> items = onFx(() -> {
            Fixture f = new Fixture("modern-theme.css");
            f.controller.setLayoutModeAction(chosen::add);
            f.controller.showLayoutMode(LayoutMode.CLASSIC_STACKED);
            ContextMenu menu = f.controller.layoutMenu();
            List<String> out = new ArrayList<>();
            for (MenuItem item : menu.getItems()) {
                out.add(item.getText() + (item instanceof RadioMenuItem r && r.isSelected() ? " ✓" : ""));
            }
            menu.getItems().get(0).fire();
            return out;
        });

        assertEquals(List.of("Explorador (carpetas y notas en un solo árbol)", "Clásico: columnas",
                "Clásico: apilado ✓"), items);
        assertEquals(List.of(LayoutMode.EXPLORER), chosen);
    }

    @Test
    void inANarrowWindowTheOverflowMenuOffersTheSameThreeLayouts() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<LayoutMode> chosen = new ArrayList<>();

        List<String> items = onFx(() -> {
            Fixture f = new Fixture("modern-theme.css");
            f.controller.setLayoutModeAction(chosen::add);
            f.controller.showLayoutMode(LayoutMode.EXPLORER);
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            UiInitialization ui = new UiInitialization();
            Runnable none = () -> { };
            ui.wire(bundle::getString, new UiInitialization.OverflowActions(none, none, none, none, none, none,
                    none, none, none, none));
            ui.updateToolbarOverflow(f.controller, 350);
            Menu layouts = f.controller.getToolbarOverflowBtn().getItems().stream()
                    .filter(i -> i instanceof Menu).map(i -> (Menu) i)
                    .findFirst().orElseThrow(() -> new AssertionError("no layout submenu in the overflow menu"));
            List<String> out = new ArrayList<>();
            out.add(layouts.getText());
            for (MenuItem item : layouts.getItems()) {
                out.add(item.getText() + (item instanceof RadioMenuItem r && r.isSelected() ? " ✓" : ""));
            }
            layouts.getItems().get(2).fire();
            return out;
        });

        assertEquals(List.of("Diseño", "Explorador (carpetas y notas en un solo árbol) ✓", "Clásico: columnas",
                "Clásico: apilado"), items);
        assertEquals(List.of(LayoutMode.CLASSIC_STACKED), chosen);
    }

    private static double iconOffset(ButtonBase button) {
        Node icon = button.getGraphic();
        Bounds b = button.localToScene(button.getBoundsInLocal());
        Bounds i = icon.localToScene(icon.getBoundsInLocal());
        return (i.getMinX() + i.getWidth() / 2) - (b.getMinX() + b.getWidth() / 2);
    }

    private static final class Fixture {
        final ToolbarController controller;
        private final Parent root;

        Fixture(String theme) throws Exception {
            URL fxml = LayoutButtonTest.class.getClassLoader().getResource("com/example/jylos/ui/view/ToolbarView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            root = loader.load();
            controller = loader.getController();
            Scene scene = new Scene(root, 1200, 80);
            scene.getStylesheets().add(LayoutButtonTest.class.getResource("/com/example/jylos/ui/css/" + theme)
                    .toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
        }

        ButtonBase button(String name) throws Exception {
            Field f = ToolbarController.class.getDeclaredField(name);
            f.setAccessible(true);
            return (ButtonBase) f.get(controller);
        }
    }
}
