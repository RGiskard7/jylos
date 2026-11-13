package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The open-note tabs live in the top bar and start right above the editor, wherever the
 * editor starts: after the sidebar, after the notes list, and again after a divider is moved
 * or a panel hidden. When the editor starts further left than the end of the top bar's
 * buttons (sidebar hidden), the tabs start right after the buttons instead of covering them.
 */
class TopBarTabsSupportTest {

    private static final double GAP = 6;

    @Test
    void theMarginLinesTheTabsUpWithTheEditorAndNeverGoesNegative() {
        assertEquals(100, TopBarTabsSupport.marginFor(150, 250), 0.001);
        assertEquals(0, TopBarTabsSupport.marginFor(150, 100), 0.001);
        assertEquals(0, TopBarTabsSupport.marginFor(150, 150), 0.001);
    }

    @Test
    void theTabsFollowTheEditorsLeftEdge() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Double> offsets = onFx(() -> {
            Region buttons = fixed(150);
            HBox tabsHost = new HBox();
            HBox.setHgrow(tabsHost, Priority.ALWAYS);
            Region tabStrip = fixed(60);
            HBox topBar = new HBox(GAP, buttons, tabsHost);

            Region sidebar = fixed(250);
            Region editor = new Region();
            HBox.setHgrow(editor, Priority.ALWAYS);
            HBox content = new HBox(sidebar, editor);
            VBox window = new VBox(topBar, content);
            Stage stage = new Stage();
            stage.setScene(new Scene(window, 1000, 500));
            stage.show();

            TopBarTabsSupport.install(tabsHost, tabStrip, editor);
            settle(window);
            double withSidebar = left(tabStrip) - left(editor);

            sidebar.setMinWidth(420);
            sidebar.setPrefWidth(420);
            sidebar.setMaxWidth(420);
            settle(window);
            double wider = left(tabStrip) - left(editor);

            sidebar.setMinWidth(0);
            sidebar.setPrefWidth(0);
            sidebar.setMaxWidth(0);
            settle(window);
            double hiddenSidebar = left(tabStrip) - (left(buttons) + 150 + GAP);
            stage.close();
            return List.of(withSidebar, wider, hiddenSidebar);
        });

        assertEquals(0, offsets.get(0), 1, "tabs start at the editor's left edge");
        assertEquals(0, offsets.get(1), 1, "and follow it when the sidebar grows");
        assertEquals(0, offsets.get(2), 1, "with no sidebar, right after the buttons");
    }

    /**
     * The editor can move several times before the top bar is laid out again. Each move must
     * give the same margin as a single one: the margin is measured from the end of the
     * buttons, not from the tabs' own (stale) position, which fed back into itself and grew
     * the margin without end.
     */
    @Test
    void repeatedMovesBeforeALayoutPassDoNotMakeTheMarginGrow() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Double> margins = onFx(() -> {
            Region buttons = fixed(150);
            HBox tabsHost = new HBox();
            HBox.setHgrow(tabsHost, Priority.ALWAYS);
            Region tabStrip = fixed(60);
            HBox topBar = new HBox(GAP, buttons, tabsHost);
            Region sidebar = fixed(250);
            Region editor = new Region();
            HBox.setHgrow(editor, Priority.ALWAYS);
            VBox window = new VBox(topBar, new HBox(sidebar, editor));
            Stage stage = new Stage();
            stage.setScene(new Scene(window, 1000, 500));
            stage.show();
            TopBarTabsSupport.install(tabsHost, tabStrip, editor);
            settle(window);
            double settled = margin(tabsHost);

            // Three moves of the editor with no layout pass in between.
            editor.setTranslateX(10);
            editor.setTranslateX(20);
            editor.setTranslateX(30);
            double afterMoves = margin(tabsHost);
            stage.close();
            return List.of(settled, afterMoves);
        });

        assertEquals(250 - (150 + GAP), margins.get(0), 1);
        assertEquals(250 + 30 - (150 + GAP), margins.get(1), 1);
    }

    /** The tabs sit on the bar's bottom line, not floating above it by the bar's padding. */
    @Test
    void theTabsSitOnTheBottomOfTheBar() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        double gap = onFx(() -> {
            Region buttons = fixed(150);
            buttons.setMinHeight(28);
            buttons.setPrefHeight(28);
            HBox tabsHost = new HBox();
            Region tabStrip = fixed(60);
            tabStrip.setPrefHeight(28);
            tabStrip.setMaxHeight(28);
            HBox topBar = new HBox(GAP, buttons, tabsHost);
            topBar.setPadding(new javafx.geometry.Insets(4, 16, 4, 16));
            topBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Region editor = new Region();
            HBox.setHgrow(editor, Priority.ALWAYS);
            VBox window = new VBox(topBar, new HBox(fixed(250), editor));
            Stage stage = new Stage();
            stage.setScene(new Scene(window, 1000, 500));
            stage.show();
            TopBarTabsSupport.install(tabsHost, tabStrip, editor);
            settle(window);
            double barBottom = topBar.localToScene(topBar.getBoundsInLocal()).getMaxY();
            double stripBottom = tabStrip.localToScene(tabStrip.getBoundsInLocal()).getMaxY();
            stage.close();
            return barBottom - stripBottom;
        });

        assertEquals(0, gap, 0.5, "space between the tabs and the bar's bottom line");
    }

    private static double margin(HBox tabsHost) {
        javafx.geometry.Insets m = HBox.getMargin(tabsHost);
        return m != null ? m.getLeft() : 0;
    }

    private static Region fixed(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        r.setPrefWidth(width);
        r.setMaxWidth(width);
        r.setMinHeight(20);
        return r;
    }

    private static double left(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinX();
    }

    /** Two layout passes: the first moves the editor, the second applies the new spacer width. */
    private static void settle(VBox window) {
        for (int i = 0; i < 3; i++) {
            window.applyCss();
            window.layout();
        }
    }
}
