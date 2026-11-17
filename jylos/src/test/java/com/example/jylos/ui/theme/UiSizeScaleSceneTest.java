package com.example.jylos.ui.theme;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Scene;
import javafx.scene.layout.VBox;

/**
 * The rescaled sizes survive what the app does to the scene's stylesheets later: switching
 * theme takes the theme's stylesheet out and puts one back, and a snippet may be added.
 */
class UiSizeScaleSceneTest {

    private static final String DARK = "/com/example/jylos/ui/css/dark-theme.css";
    private static final String LIGHT = "/com/example/jylos/ui/css/modern-theme.css";

    @Test
    void iconsStayScaledWhenTheThemeIsSwitchedAfterwards() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        Fixture fixture = onFx(() -> {
            FontIcon icon = new FontIcon("fth-file");
            icon.getStyleClass().add("feather-icon");
            VBox root = new VBox(icon);
            root.setStyle("-fx-font-size: 22px;");
            Scene scene = new Scene(root);
            scene.getStylesheets().add(url(DARK));
            UiSizeScale.apply(scene, 22, 1.0);
            return new Fixture(scene, icon);
        });
        onFx(() -> {
            // What the theme switch does: out goes the old theme, in comes the new one.
            fixture.scene().getStylesheets().removeIf(u -> u.endsWith("dark-theme.css"));
            fixture.scene().getStylesheets().add(url(LIGHT));
            return null;
        });

        List<String> seen = onFx(() -> {
            fixture.scene().getRoot().applyCss();
            List<String> sheets = fixture.scene().getStylesheets();
            return List.of(String.valueOf(fixture.icon().getIconSize()),
                    sheets.get(sheets.size() - 1).startsWith("data:") ? "scaled sheet last" : "theme last");
        });

        assertEquals(List.of("27", "scaled sheet last"), seen, "16 px × 22/13 after the switch");
    }

    record Fixture(Scene scene, FontIcon icon) {
    }

    private static String url(String resource) {
        return UiSizeScaleSceneTest.class.getResource(resource).toExternalForm();
    }
}
