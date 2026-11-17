package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.text.Text;

/** The settings window's layout: sections on the left, one page of rows at a time. */
class SettingsPaneTest {

    private static final String LONG_NAME =
            "Always open notes in a new tab, so that a click never replaces the note in the current one";

    /**
     * A setting with a long name in a narrow window: the name wraps onto more lines and is
     * shown whole, never cut or ellipsised, and the control stays inside the window.
     */
    @Test
    void aLongSettingNameWrapsAndItsControlStaysInTheWindow() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SettingsPane pane = new SettingsPane();
            CheckBox control = new CheckBox();
            pane.addSection("Notes").row(LONG_NAME, "If not, a click replaces the note in the current tab.", control);
            pane.showSection(0);
            Scene scene = new Scene(pane, 420, 400);
            pane.applyCss();
            pane.layout();

            Label name = findLabel(pane, LONG_NAME);
            double oneLine = name.getFont().getSize() * 1.6;
            Bounds controlInScene = control.localToScene(control.getLayoutBounds());
            return List.of(
                    shownText(name),
                    name.getHeight() > oneLine ? "wrapped" : "one line, " + name.getHeight() + " high",
                    controlInScene.getMaxX() <= scene.getWidth() + 0.5 && controlInScene.getWidth() > 0
                            ? "control inside" : "control at " + controlInScene);
        });

        assertEquals(LONG_NAME, seen.get(0).replace('\n', ' ').replaceAll(" +", " ").trim(),
                "the whole name is shown");
        assertEquals("wrapped", seen.get(1));
        assertEquals("control inside", seen.get(2));
    }

    /**
     * A wide control (a drop-down with a long choice) does not crush the setting's name into a
     * letter per line: with the app's theme, the name keeps room for itself and the control
     * gives way.
     */
    @Test
    void aWideControlLeavesRoomForTheName() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        String seen = onFx(() -> {
            SettingsPane pane = new SettingsPane();
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().add("Explorer (folders and notes in a single tree, as in other note apps)");
            combo.getSelectionModel().select(0);
            pane.addSection("Notes").row("Layout", null, combo);
            pane.setStyle("-fx-font-size: 22px;");
            Scene scene = new Scene(pane, 760, 400);
            scene.getStylesheets().add(SettingsPane.class
                    .getResource("/com/example/jylos/ui/css/dark-theme.css").toExternalForm());
            pane.applyCss();
            pane.layout();
            Label name = findLabel(pane, "Layout");
            return name.getHeight() < name.getFont().getSize() * 1.6 ? "one line" : "crushed, " + name.getHeight() + " high";
        });

        assertEquals("one line", seen);
    }

    /** Choosing a section on the left shows its settings and only those. */
    @Test
    void choosingASectionShowsItsSettings() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SettingsPane pane = new SettingsPane();
            pane.addSection("General").row("Autosave", null, new CheckBox());
            pane.addSection("Appearance").row("Theme", null, new ComboBox<String>());
            new Scene(pane, 700, 400);
            pane.showSection(0);
            pane.applyCss();
            pane.layout();
            String first = names(pane);
            pane.showSection(1);
            pane.applyCss();
            pane.layout();
            return List.of(first, names(pane), String.join(",", pane.sectionTitles()));
        });

        assertEquals("Autosave", seen.get(0));
        assertEquals("Theme", seen.get(1));
        assertEquals("General,Appearance", seen.get(2));
    }

    private static String names(Parent root) {
        StringBuilder out = new StringBuilder();
        collectNames(root, out);
        return out.toString();
    }

    private static void collectNames(Node node, StringBuilder out) {
        if (node instanceof Label label && label.getStyleClass().contains("settings-row-name")
                && label.getScene() != null && label.isVisible()) {
            out.append(out.isEmpty() ? "" : ",").append(label.getText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectNames(child, out);
            }
        }
    }

    private static Label findLabel(Node node, String text) {
        if (node instanceof Label label && text.equals(label.getText())) {
            return label;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Label found = findLabel(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The text a label actually draws, ellipsis included. */
    private static String shownText(Node label) {
        return label.lookup(".text") instanceof Text text ? text.getText() : "<none>";
    }
}
