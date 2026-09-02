package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;

/**
 * The right panel lives inside a {@code SplitPane}, not a plain box — a SplitPane
 * still reserves that item's divided region even once it's set invisible/unmanaged,
 * because collapsing happens via the item's own width, not the normal managed-layout
 * pass. Entering focus mode with the right panel open used to leave that region
 * visible and empty instead of actually reclaiming the space — this pins the fix:
 * focus mode must collapse the right panel exactly like {@code UiLayout.toggleRightPanel}
 * itself does (squeeze width to 0, not managed/visible false), and restore it the same way.
 */
class FocusModeSupportTest {

    @Test
    void enteringFocusModeWithTheRightPanelOpenCollapsesItsWidthNotJustItsVisibility() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] widthDuringFocusMode = new double[1];
        boolean[] managedDuringFocusMode = new boolean[1];
        boolean[] visibleDuringFocusMode = new boolean[1];
        double[] widthAfterExit = new double[1];
        Platform.runLater(() -> {
            try {
                VBox rightPanel = new VBox();
                expandRightPanel(rightPanel);

                FocusModeSupport support = wiredSupport(rightPanel);

                support.toggle(); // enter
                widthDuringFocusMode[0] = rightPanel.getPrefWidth();
                managedDuringFocusMode[0] = rightPanel.isManaged();
                visibleDuringFocusMode[0] = rightPanel.isVisible();

                support.toggle(); // exit
                widthAfterExit[0] = rightPanel.getPrefWidth();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertEquals(0, widthDuringFocusMode[0],
                "the right panel's own width must collapse to 0 during focus mode — a SplitPane "
                        + "keeps reserving an item's divided region even when it's just set "
                        + "invisible/unmanaged, which is what left an empty gap before this fix");
        assertTrue(managedDuringFocusMode[0] && visibleDuringFocusMode[0],
                "managed/visible must stay true throughout — exactly like the app's own normal "
                        + "collapse (UiLayout.toggleRightPanel), which never touches those flags "
                        + "for this panel, only its width");
        assertEquals(300, widthAfterExit[0],
                "exiting focus mode must restore the width the panel had before (this codebase's "
                        + "own 'expanded' width, 300), not leave it collapsed");
    }

    @Test
    void enteringFocusModeWithTheRightPanelAlreadyCollapsedLeavesItCollapsedOnExit() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] widthAfterExit = new double[1];
        Platform.runLater(() -> {
            try {
                VBox rightPanel = new VBox();
                collapseRightPanel(rightPanel); // already collapsed before focus mode

                FocusModeSupport support = wiredSupport(rightPanel);

                support.toggle(); // enter
                support.toggle(); // exit
                widthAfterExit[0] = rightPanel.getPrefWidth();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertEquals(0, widthAfterExit[0],
                "a right panel that was already collapsed before entering focus mode must not "
                        + "be force-expanded just because focus mode was toggled off");
    }

    private static FocusModeSupport wiredSupport(VBox rightPanel) {
        SplitPane mainSplitPane = new SplitPane();
        SplitPane contentSplitPane = new SplitPane();
        Label sidebarPlaceholder = new Label("sidebar");
        Label notesPlaceholder = new Label("notes");
        Label editorPlaceholder = new Label("editor");
        mainSplitPane.getItems().setAll(sidebarPlaceholder, contentSplitPane);
        contentSplitPane.getItems().setAll(notesPlaceholder, editorPlaceholder);

        FocusModeSupport support = new FocusModeSupport();
        support.wire(mainSplitPane, contentSplitPane, () -> new Label("toolbar"),
                new Label("status"), rightPanel, () -> editorPlaceholder,
                Preferences.userRoot().node("/com/example/jylos/test/focus-mode"),
                key -> key, message -> { });
        return support;
    }

    private static void expandRightPanel(VBox rightPanel) {
        rightPanel.setVisible(true);
        rightPanel.setManaged(true);
        rightPanel.setMinWidth(240);
        rightPanel.setMaxWidth(Double.MAX_VALUE);
        rightPanel.setPrefWidth(300);
    }

    private static void collapseRightPanel(VBox rightPanel) {
        rightPanel.setVisible(true);
        rightPanel.setManaged(true);
        rightPanel.setMinWidth(0);
        rightPanel.setMaxWidth(0);
        rightPanel.setPrefWidth(0);
    }
}
