package com.example.jylos.ui.theme;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.TextField;
import javafx.scene.layout.Border;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;

/**
 * With either built-in theme, a control's outline keeps its colour when the pointer is over
 * it and when it has the focus: those states change the background, never the border.
 */
class ThemeBordersTest {

    @ParameterizedTest
    @ValueSource(strings = {"dark-theme.css", "modern-theme.css"})
    void bordersKeepTheirColourOnHoverAndFocus(String theme) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> changed = onFx(() -> {
            List<String> out = new ArrayList<>();
            for (boolean inDialog : new boolean[] {false, true}) {
                List<Control> controls = List.of(new StateField(), new StateCombo(), new StateButton());
                VBox box = new VBox();
                box.getChildren().addAll(controls);
                VBox root = new VBox(box);
                root.getStyleClass().add("root-container");
                if (inDialog) {
                    box.getStyleClass().add("dialog-pane");
                }
                Scene scene = new Scene(root, 300, 200);
                scene.getStylesheets().add(ThemeBordersTest.class
                        .getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                for (Control control : controls) {
                    String where = control.getClass().getSimpleName() + (inDialog ? " in a dialog" : "");
                    String rest = border(root, control);
                    ((States) control).hover(true);
                    String hover = border(root, control);
                    ((States) control).hover(false);
                    ((States) control).focus(true);
                    String focus = border(root, control);
                    ((States) control).focus(false);
                    if (!rest.equals(hover)) {
                        out.add(where + " on hover: " + rest + " -> " + hover);
                    }
                    if (!rest.equals(focus)) {
                        out.add(where + " on focus: " + rest + " -> " + focus);
                    }
                }
            }
            return out;
        });

        assertEquals(List.of(), changed);
    }

    private static String border(VBox root, Control control) {
        root.applyCss();
        Border border = control.getBorder();
        if (border == null || border.getStrokes().isEmpty()) {
            return "none";
        }
        Paint top = border.getStrokes().get(0).getTopStroke();
        return String.valueOf(top);
    }

    private interface States {
        void hover(boolean on);

        void focus(boolean on);
    }

    private static final class StateField extends TextField implements States {
        public void hover(boolean on) { setHover(on); }
        public void focus(boolean on) { setFocused(on); }
    }

    private static final class StateCombo extends ComboBox<String> implements States {
        public void hover(boolean on) { setHover(on); }
        public void focus(boolean on) { setFocused(on); }
    }

    private static final class StateButton extends Button implements States {
        StateButton() { super("OK"); }
        public void hover(boolean on) { setHover(on); }
        public void focus(boolean on) { setFocused(on); }
    }
}
