package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The note editor header's back/forward nav buttons (.nav-btn) had their own
 * padding-driven size instead of the app-wide 28x28 icon-button square (.icon-btn),
 * making them visibly bigger/longer than every other icon button. Separately, the
 * note title field (.title-field) measured ~37px tall instead of its own declared 2px
 * vertical padding, because every JavaFX TextField also carries the implicit
 * ".text-field" style class, and that base rule's wider padding (declared later in the
 * stylesheet) won the cascade tie — the exact bug class already fixed once this
 * session for .search-field.
 */
class EditorHeaderButtonSizingTest {

    @Test
    void navBtnMatchesTheAppWideIconButtonSquare() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] navWidth = new double[1];
        double[] navHeight = new double[1];
        Platform.runLater(() -> {
            try {
                Button navBtn = new Button();
                navBtn.getStyleClass().add("nav-btn");
                navBtn.setGraphic(new FontIcon("fth-chevron-left"));

                VBox root = new VBox(navBtn);
                root.getStyleClass().add("root-container");
                Scene scene = new Scene(root, 200, 100);
                scene.getStylesheets().add(EditorHeaderButtonSizingTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/css/modern-theme.css").toExternalForm());
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                navWidth[0] = navBtn.getWidth();
                navHeight[0] = navBtn.getHeight();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertEquals(28, navWidth[0], 0.5,
                "the nav (back/forward) button must be exactly 28px wide, matching every "
                        + "other icon-only button in the app — was " + navWidth[0]);
        assertEquals(28, navHeight[0], 0.5,
                "the nav (back/forward) button must be exactly 28px tall, matching every "
                        + "other icon-only button in the app — was " + navHeight[0]);
    }

    @Test
    void titleFieldIsNoTallerThanTheHeaderIconButtons() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] titleFieldHeight = new double[1];
        Platform.runLater(() -> {
            try {
                TextField titleField = new TextField("Sample Note Title");
                titleField.getStyleClass().add("title-field");

                VBox root = new VBox(titleField);
                root.getStyleClass().add("root-container");
                Scene scene = new Scene(root, 400, 100);
                scene.getStylesheets().add(EditorHeaderButtonSizingTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/css/modern-theme.css").toExternalForm());
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                titleFieldHeight[0] = titleField.getHeight();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        // The header's icon buttons (.icon-btn, .nav-btn) are all a fixed 28px tall — the
        // title field sharing their row must not exceed that, per the exact bug this
        // pins: a plain .text-field base rule silently overriding .title-field's own
        // intended padding pushed it to ~37px.
        assertTrue(titleFieldHeight[0] <= 28.5,
                "the note title field must be no taller than the header's 28px icon "
                        + "buttons — was " + titleFieldHeight[0]);
    }
}
