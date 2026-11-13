package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Toggle;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderStroke;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

/**
 * Icon buttons in the top bar, the sidebar, the notes list header and the editor header and
 * format bar are flat, as in Obsidian: no border, and no background unless the mouse is over
 * them or they are toggled on. Checked on the real views with each theme's real stylesheet.
 */
class FlatButtonsTest {

    /** The icon-button style classes this rule covers. */
    private static final Set<String> ICON_BUTTON_CLASSES = Set.of("toolbar-btn", "modern-toggle-btn", "icon-btn",
            "sidebar-nav-btn", "nav-btn", "format-btn");

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
            "modern-theme.css, ToolbarView.fxml",
            "dark-theme.css, ToolbarView.fxml",
            "modern-theme.css, SidebarView.fxml",
            "dark-theme.css, SidebarView.fxml",
            "modern-theme.css, NotesListView.fxml",
            "dark-theme.css, NotesListView.fxml",
            "modern-theme.css, EditorView.fxml",
            "dark-theme.css, EditorView.fxml" })
    void iconButtonsHaveNoBorderAndNoBackgroundAtRest(String theme, String view) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> offenders = onFx(() -> {
            URL fxml = getClass().getResource("/com/example/jylos/ui/view/" + view);
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            Parent root = new FXMLLoader(fxml, bundle).load();
            Scene scene = new Scene(root, 1200, 700);
            scene.getStylesheets().add(getClass().getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();

            List<String> out = new ArrayList<>();
            int checked = 0;
            for (Node node : allButtons(root)) {
                if (!(node instanceof ButtonBase button) || !hasIconClass(button)) {
                    continue;
                }
                checked++;
                String name = describe(button);
                if (visibleBorder(button)) {
                    out.add(name + " has a border");
                }
                boolean on = (button instanceof Toggle t && t.isSelected())
                        || button.getStyleClass().contains("sidebar-nav-btn-selected");
                if (!on && visibleBackground(button)) {
                    out.add(name + " has a background");
                }
            }
            stage.close();
            out.add(0, "checked " + (checked > 0));
            return out;
        });

        assertEquals(List.of("checked true"), offenders);
    }

    private static List<Node> allButtons(Parent root) {
        List<Node> out = new ArrayList<>();
        walk(root, out);
        return out;
    }

    private static void walk(Node node, List<Node> out) {
        if (node instanceof ButtonBase) {
            out.add(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                walk(child, out);
            }
        }
    }

    private static boolean hasIconClass(ButtonBase button) {
        return button.getStyleClass().stream().anyMatch(ICON_BUTTON_CLASSES::contains);
    }

    private static String describe(ButtonBase button) {
        String id = button.getId() != null ? button.getId() : String.join(",", button.getStyleClass());
        return id;
    }

    private static boolean visibleBorder(ButtonBase button) {
        if (button.getBorder() == null) {
            return false;
        }
        for (BorderStroke stroke : button.getBorder().getStrokes()) {
            for (Paint paint : List.of(stroke.getTopStroke(), stroke.getRightStroke(), stroke.getBottomStroke(),
                    stroke.getLeftStroke())) {
                boolean width = stroke.getWidths().getTop() > 0 || stroke.getWidths().getLeft() > 0;
                if (width && !(paint instanceof Color c && c.getOpacity() == 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean visibleBackground(ButtonBase button) {
        if (button.getBackground() == null) {
            return false;
        }
        for (BackgroundFill fill : button.getBackground().getFills()) {
            if (!(fill.getFill() instanceof Color c && c.getOpacity() == 0)) {
                return true;
            }
        }
        return false;
    }
}
