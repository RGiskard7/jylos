package com.example.jylos.ui.controller;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;

/**
 * Puts the open-note tabs in the top bar, as in Obsidian, starting right above the editor.
 *
 * <p>The top bar is {@code [buttons][tabs ...][layout buttons]}. The tabs' container gets a
 * left margin so the tabs begin at the editor's left edge, which depends on the layout (after
 * the sidebar, after the notes list) and moves with the dividers and when panels are hidden;
 * the margin follows the editor's real position in the window. When the editor starts left
 * of the end of the buttons (sidebar hidden), the margin is zero and the tabs start right
 * after the buttons rather than covering them.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class TopBarTabsSupport {

    private TopBarTabsSupport() {
    }

    /** Left margin so a container whose unshifted left is {@code baseLeft} starts at {@code editorLeft}. */
    static double marginFor(double baseLeft, double editorLeft) {
        return Math.max(0, editorLeft - baseLeft);
    }

    /** Moves {@code tabStrip} into {@code tabsHost} and keeps it starting above {@code editor}. */
    static void install(Pane tabsHost, Node tabStrip, Node editor) {
        tabsHost.getChildren().setAll(tabStrip);
        HBox.setHgrow(tabStrip, Priority.ALWAYS);
        HBox.setHgrow(tabsHost, Priority.ALWAYS);
        tabsHost.setMinWidth(0);
        // The tabs sit on the bar's bottom line (as in Obsidian), not centred with the buttons.
        if (tabsHost instanceof HBox box) {
            box.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        }

        // Change listeners, not invalidation listeners: the transforms are lazy properties that
        // would only report one invalidation until someone reads them again.
        ChangeListener<Object> align = (obs, was, now) -> align(tabsHost, editor);
        editor.localToSceneTransformProperty().addListener(align);
        editor.boundsInLocalProperty().addListener(align);
        tabsHost.localToSceneTransformProperty().addListener(align);
        editor.sceneProperty().addListener(align);
        align(tabsHost, editor);
    }

    private static void align(Pane tabsHost, Node editor) {
        if (editor.getScene() == null || tabsHost.getScene() == null) {
            return;
        }
        Insets margin = HBox.getMargin(tabsHost);
        double current = margin != null ? margin.getLeft() : 0;
        double wanted = Math.round(marginFor(unshiftedLeft(tabsHost), editor.localToScene(0, 0).getX()));
        // Reach down through the bar's bottom padding so the tabs touch its bottom line.
        double bottom = tabsHost.getParent() instanceof HBox bar && bar.getPadding() != null
                ? -bar.getPadding().getBottom() : 0;
        if (Math.abs(current - wanted) >= 0.5 || margin == null || margin.getBottom() != bottom) {
            HBox.setMargin(tabsHost, new Insets(0, 0, bottom, wanted));
        }
    }

    /**
     * Where the tabs would start with no margin, in scene coordinates: right after the
     * previous shown node of the top bar (plus the bar's spacing), or at the bar's left
     * padding. Measured from the siblings, never from the tabs' own position, which lags one
     * layout pass behind every margin change and would feed back into the margin.
     */
    private static double unshiftedLeft(Pane tabsHost) {
        if (!(tabsHost.getParent() instanceof HBox bar)) {
            return tabsHost.localToScene(0, 0).getX();
        }
        double x = bar.snappedLeftInset();
        int index = bar.getChildren().indexOf(tabsHost);
        for (int i = index - 1; i >= 0; i--) {
            Node previous = bar.getChildren().get(i);
            if (previous.isManaged()) {
                Insets m = HBox.getMargin(previous);
                x = previous.getBoundsInParent().getMaxX() + (m != null ? m.getRight() : 0) + bar.getSpacing();
                break;
            }
        }
        return bar.localToScene(x, 0).getX();
    }
}
