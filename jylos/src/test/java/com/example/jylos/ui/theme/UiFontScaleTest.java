package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * {@code MainController.applyUiZoom()} sets {@code -fx-font-size} as an inline style
 * on the main scene's {@code .root-container} node — that only reaches descendants
 * through CSS inheritance, the weakest cascade source. Every rule in
 * {@code modern-theme.css}/{@code dark-theme.css} that fixed its own {@code font-size}
 * in absolute px used to silently freeze that text regardless of the preference (the
 * notes list, the folder tree, most dialogs including Git). They were converted to
 * {@code em}, which JavaFX resolves relative to the inherited font-size, so this test
 * asserts real, measured scaling — not just that the CSS parses.
 */
class UiFontScaleTest {

    @Test
    void listCellAndGitDialogTextScaleWithTheRootFontSize() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] listCellHeightSmall = new double[1];
        double[] listCellHeightLarge = new double[1];
        double[] gitTitleHeightSmall = new double[1];
        double[] gitTitleHeightLarge = new double[1];

        Platform.runLater(() -> {
            try {
                String css = UiFontScaleTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/css/modern-theme.css").toExternalForm();

                // Same classes production uses: .list-cell is what the notes list and the
                // sidebar folder tree render through; .git-change-title is Git dialog content.
                Label listCellLabel = new Label("Sample note title");
                listCellLabel.getStyleClass().add("list-cell");
                Label gitTitleLabel = new Label("Sample git change title");
                gitTitleLabel.getStyleClass().add("git-change-title");

                VBox root = new VBox(10, listCellLabel, gitTitleLabel);
                root.getStyleClass().add("root-container");

                Scene scene = new Scene(root, 400, 120);
                scene.getStylesheets().add(css);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();

                root.setStyle("-fx-font-size: 13px;");
                root.applyCss();
                root.layout();
                listCellHeightSmall[0] = listCellLabel.getLayoutBounds().getHeight();
                gitTitleHeightSmall[0] = gitTitleLabel.getLayoutBounds().getHeight();

                root.setStyle("-fx-font-size: 22px;");
                root.applyCss();
                root.layout();
                listCellHeightLarge[0] = listCellLabel.getLayoutBounds().getHeight();
                gitTitleHeightLarge[0] = gitTitleLabel.getLayoutBounds().getHeight();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(listCellHeightLarge[0] > listCellHeightSmall[0] * 1.3,
                "notes-list/folder-tree text (.list-cell) must grow with the UI font size preference, "
                        + "not stay fixed — was " + listCellHeightSmall[0] + " then " + listCellHeightLarge[0]);
        assertTrue(gitTitleHeightLarge[0] > gitTitleHeightSmall[0] * 1.3,
                "Git dialog text (.git-change-title) must grow with the UI font size preference, "
                        + "not stay fixed — was " + gitTitleHeightSmall[0] + " then " + gitTitleHeightLarge[0]);
    }
}
