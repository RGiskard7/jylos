package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.example.jylos.ui.UiNodes;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A side panel as Obsidian's: a row of icon tabs on top, one per view (the view's name is the
 * tab's tooltip), and under it the view of the chosen tab, alone and with its own scroll. When
 * the tabs do not fit, the row scrolls sideways with the mouse wheel, without a scroll bar,
 * and the chosen tab is brought into sight. The tabs keep the order the views came in.
 *
 * <p>A view asked for before it exists (the one chosen last time, a plugin's that registers
 * later) is chosen when it comes; until then the first one is.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
public final class SideDock extends VBox {

    private final HBox tabsBar = new HBox(6);
    private final ScrollPane tabsRow = new ScrollPane(tabsBar);
    private final StackPane views = new StackPane();
    private final Label empty = new Label();
    private final Map<String, View> byId = new LinkedHashMap<>();
    private String selected;
    /** The view asked for that is not here (yet). */
    private String wanted;
    private Consumer<String> onSelected = id -> {
    };

    private record View(String id, Button tab, ScrollPane body) {
    }

    public SideDock() {
        getStyleClass().add("side-dock");

        // Same bar as the side panel's on the left (and as tall as the editor's header).
        tabsBar.getStyleClass().addAll("sidebar-nav-bar", "side-dock-tabs-bar");
        tabsBar.setAlignment(Pos.CENTER_LEFT);
        tabsBar.setFillHeight(false);
        tabsBar.setPadding(new Insets(4, 16, 4, 16));
        tabsRow.getStyleClass().add("side-dock-tabs");
        tabsRow.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tabsRow.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tabsRow.setFitToHeight(true);
        tabsRow.setMinHeight(Region.USE_PREF_SIZE);
        // The bar fills the row when the tabs fit, so its bottom line runs across.
        tabsRow.viewportBoundsProperty().addListener((o, was, now) -> tabsBar.setMinWidth(now.getWidth()));
        tabsRow.addEventFilter(ScrollEvent.SCROLL, this::scrollTabs);

        empty.getStyleClass().addAll("side-dock-empty", "info-value-small");
        empty.setWrapText(true);
        StackPane.setAlignment(empty, Pos.TOP_CENTER);
        views.getStyleClass().add("side-dock-views");
        views.getChildren().add(empty);
        VBox.setVgrow(views, Priority.ALWAYS);

        getChildren().addAll(tabsRow, views);
        refresh();
    }

    /** What the panel says when it has no view. */
    public void setEmptyText(String text) {
        empty.setText(text);
    }

    /** Told the id of each view chosen, by a click or not. */
    public void setOnSelected(Consumer<String> onSelected) {
        this.onSelected = onSelected != null ? onSelected : id -> {
        };
    }

    /**
     * Adds a view at the end of the row (or replaces the one with that id, in its place).
     *
     * @param icon    the tab's icon
     * @param content what the view shows; the panel scrolls it
     */
    public void addView(String id, String title, Node icon, Node content) {
        Button tab = new Button();
        tab.getStyleClass().addAll("sidebar-nav-btn", "side-dock-tab");
        tab.setGraphic(icon);
        tab.setTooltip(new Tooltip(title));
        tab.setFocusTraversable(false);
        tab.setMinWidth(Region.USE_PREF_SIZE);
        tab.setOnAction(e -> select(id));

        VBox padded = new VBox(content);
        padded.getStyleClass().add("side-dock-view");
        padded.setPadding(new Insets(8, 12, 16, 12));
        ScrollPane body = new ScrollPane(padded);
        body.getStyleClass().addAll("panel-scroll", "side-dock-view-scroll");
        body.setFitToWidth(true);
        body.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        View view = new View(id, tab, body);
        View old = byId.get(id);
        if (old != null) {
            int at = tabsBar.getChildren().indexOf(old.tab());
            tabsBar.getChildren().set(at, tab);
            views.getChildren().set(views.getChildren().indexOf(old.body()), body);
        } else {
            tabsBar.getChildren().add(tab);
            views.getChildren().add(body);
        }
        byId.put(id, view);
        if (id.equals(wanted) || selected == null || id.equals(selected)) {
            choose(id);
        } else {
            refresh();
        }
    }

    public void removeView(String id) {
        View view = byId.remove(id);
        if (view == null) {
            return;
        }
        tabsBar.getChildren().remove(view.tab());
        views.getChildren().remove(view.body());
        if (id.equals(selected)) {
            selected = null;
            firstShown().ifPresent(this::choose);
        }
        refresh();
    }

    /** Chooses a view; one that is not here yet is chosen when it comes. */
    public void select(String id) {
        View view = byId.get(id);
        if (view == null || !view.tab().isVisible()) {
            wanted = id;
            return;
        }
        choose(id);
    }

    public Optional<String> selected() {
        return Optional.ofNullable(selected);
    }

    /** The views' ids, in the order of their tabs (shown or not). */
    public List<String> viewIds() {
        return new ArrayList<>(byId.keySet());
    }

    /** Leaves a view out of the row, or brings it back; a view left out is not chosen. */
    public void setShown(String id, boolean shown) {
        View view = byId.get(id);
        if (view == null) {
            return;
        }
        UiNodes.show(view.tab(), shown);
        if (!shown && id.equals(selected)) {
            selected = null;
            firstShown().ifPresent(this::choose);
        } else if (selected == null && shown) {
            choose(id);
        }
        refresh();
    }

    public boolean isShown(String id) {
        View view = byId.get(id);
        return view != null && view.tab().isVisible();
    }

    private void choose(String id) {
        boolean changed = !id.equals(selected);
        selected = id;
        if (id.equals(wanted)) {
            wanted = null;
        }
        refresh();
        bringIntoSight(byId.get(id).tab());
        if (changed) {
            onSelected.accept(id);
        }
    }

    private Optional<String> firstShown() {
        return byId.values().stream().filter(v -> v.tab().isVisible()).map(View::id).findFirst();
    }

    private void refresh() {
        for (View view : byId.values()) {
            boolean chosen = view.id().equals(selected);
            view.tab().getStyleClass().remove("sidebar-nav-btn-selected");
            if (chosen) {
                view.tab().getStyleClass().add("sidebar-nav-btn-selected");
            }
            view.body().setVisible(chosen);
        }
        empty.setVisible(selected == null);
    }

    /** The wheel moves the row sideways: up or left goes back, down or right forward. */
    private void scrollTabs(ScrollEvent event) {
        double delta = event.getDeltaX() != 0 ? event.getDeltaX() : event.getDeltaY();
        double hidden = tabsBar.getWidth() - tabsRow.getViewportBounds().getWidth();
        if (hidden > 0 && delta != 0) {
            double value = tabsRow.getHvalue() - delta / hidden * (tabsRow.getHmax() - tabsRow.getHmin());
            tabsRow.setHvalue(Math.max(tabsRow.getHmin(), Math.min(tabsRow.getHmax(), value)));
        }
        event.consume();
    }

    private void bringIntoSight(Button tab) {
        applyCss();
        layout();
        double viewport = tabsRow.getViewportBounds().getWidth();
        double hidden = tabsBar.getWidth() - viewport;
        if (hidden <= 0) {
            return;
        }
        Bounds bounds = tab.getBoundsInParent();
        double left = (tabsRow.getHvalue() - tabsRow.getHmin()) / (tabsRow.getHmax() - tabsRow.getHmin()) * hidden;
        double target = left;
        if (bounds.getMinX() < left) {
            target = bounds.getMinX();
        } else if (bounds.getMaxX() > left + viewport) {
            target = bounds.getMaxX() - viewport;
        }
        double value = tabsRow.getHmin() + Math.min(1, target / hidden) * (tabsRow.getHmax() - tabsRow.getHmin());
        tabsRow.setHvalue(value);
    }
}
