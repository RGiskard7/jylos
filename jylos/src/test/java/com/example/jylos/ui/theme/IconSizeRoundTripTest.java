package com.example.jylos.ui.theme;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.EditorTabs;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Raising the interface font and lowering it back leaves every icon as it was: none keeps the
 * larger size until the app is reopened.
 */
class IconSizeRoundTripTest {

    private static final String[] VIEWS = {"ToolbarView", "SidebarView", "EditorView", "NotesListView", "GraphView"};

    @ParameterizedTest
    @ValueSource(strings = {"dark-theme.css", "modern-theme.css"})
    void iconsGoBackToTheirSizeWhenTheFontGoesBack(String theme) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<List<String>> seen = onFx(() -> {
            VBox root = new VBox();
            root.getStyleClass().add("root-container");
            ResourceBundle es = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
            for (String view : VIEWS) {
                URL fxml = IconSizeRoundTripTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/view/" + view + ".fxml");
                root.getChildren().add((Parent) new FXMLLoader(fxml, es).load());
            }
            HBox tabStrip = new HBox();
            new EditorTabs(tabStrip, tabStrip, null, k -> k).setNewTabAction(() -> { });
            root.getChildren().add(tabStrip);
            Scene scene = new Scene(root, 1600, 1400);
            scene.getStylesheets().add(IconSizeRoundTripTest.class
                    .getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
            List<String> before = sizesAt(scene, 13);
            sizesAt(scene, 22);
            List<String> after = sizesAt(scene, 13);
            return List.of(before, after);
        });

        List<String> changed = new ArrayList<>();
        for (int i = 0; i < seen.get(0).size(); i++) {
            if (!seen.get(0).get(i).equals(seen.get(1).get(i))) {
                changed.add(seen.get(0).get(i) + " -> " + seen.get(1).get(i));
            }
        }
        assertEquals(List.of(), changed, "icons that kept the 22 px size");
    }

    private static List<String> sizesAt(Scene scene, double fontPx) {
        scene.getRoot().setStyle("-fx-font-size: " + fontPx + "px;");
        UiSizeScale.apply(scene, fontPx, 1.0);
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        scene.getRoot().applyCss();
        List<String> out = new ArrayList<>();
        collect(scene.getRoot(), out);
        return out;
    }

    private static void collect(Node node, List<String> out) {
        if (node instanceof FontIcon icon) {
            out.add(icon.getIconLiteral() + "=" + icon.getIconSize());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, out);
            }
        }
    }
}
