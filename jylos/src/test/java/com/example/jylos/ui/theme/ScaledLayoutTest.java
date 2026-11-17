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

import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * With a larger interface font the sidebar's view buttons, the empty tab's tiles and the gap
 * above them grow with it, so nothing ends up cramped or out of line.
 */
class ScaledLayoutTest {

    @ParameterizedTest
    @ValueSource(strings = {"dark-theme.css", "modern-theme.css"})
    void sidebarButtonsAndEmptyTabTilesGrowWithTheFont(String theme) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        assertEquals("nav 28, tiles 150x96 level, gap 16", measure(theme, 13));
        assertEquals("nav 47, tiles 254x162 level, gap 27", measure(theme, 22));
    }

    private static String measure(String theme, double fontPx) throws Exception {
        return onFx(() -> {
            VBox root = new VBox();
            root.getStyleClass().addAll("root-container");
            root.setStyle("-fx-font-size: " + fontPx + "px;");
            ResourceBundle es = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
            for (String view : new String[] {"SidebarView", "EditorView"}) {
                URL fxml = ScaledLayoutTest.class.getClassLoader().getResource("com/example/jylos/ui/view/" + view + ".fxml");
                root.getChildren().add((Parent) new FXMLLoader(fxml, es).load());
            }
            Scene scene = new Scene(root, 1600, 1400);
            scene.getStylesheets().add(ScaledLayoutTest.class
                    .getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
            UiSizeScale.apply(scene, fontPx, 1.0);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();

            Region nav = (Region) root.lookup(".sidebar-nav-btn");
            List<Region> tiles = new ArrayList<>();
            for (Node node : root.lookupAll(".empty-state-tile")) {
                tiles.add((Region) node);
            }
            Region first = tiles.get(0);
            boolean sameSize = tiles.stream().allMatch(t -> t.getWidth() == first.getWidth() && t.getHeight() == first.getHeight());
            boolean level = tiles.stream().allMatch(t -> bounds(t).getMinY() == bounds(first).getMinY());
            Bounds button = bounds(root.lookup(".empty-state-btn"));
            Bounds tilesBox = bounds(root.lookup(".empty-state-tiles"));
            stage.close();
            return "nav " + Math.round(nav.getWidth())
                    + ", tiles " + Math.round(first.getWidth()) + "x" + Math.round(first.getHeight())
                    + (sameSize && level ? " level" : " uneven")
                    + ", gap " + Math.round(tilesBox.getMinY() - button.getMaxY());
        });
    }

    private static Bounds bounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }
}
