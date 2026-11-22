package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.example.jylos.ui.UiNodes;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
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
 * and the chosen tab is brought into sight.
 *
 * <p>A tab is dragged along the row to move it; the views come in the order kept from last
 * time ({@link #setPreferredOrder}), a view not in it at the end. A tab's menu closes its view
 * and lists every view, to open a closed one again. A view asked for, or closed, before it
 * exists (the one chosen last time, a plugin's that registers later) is so when it comes.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
public final class SideDock extends VBox {

    private final HBox tabsBar = new HBox(6);
    private final ScrollPane tabsRow = new ScrollPane(tabsBar);
    private final StackPane views = new StackPane();
    private final Label empty = new Label();
    private final Map<String, View> byId = new HashMap<>();
    /** The views' ids in the order of their tabs. */
    private final List<String> order = new ArrayList<>();
    private List<String> preferredOrder = List.of();
    /** Closed from the tabs' menu (or last time), here or not yet. */
    private final Set<String> closed = new HashSet<>();
    /** Left out of the row by their owner for a while ({@link #setShown}). */
    private final Set<String> hiddenByOwner = new HashSet<>();
    private String selected;
    /** The view asked for that is not here (yet). */
    private String wanted;
    private String closeText = "Close";
    private Consumer<String> onSelected = id -> {
    };
    private Consumer<List<String>> onOrderChanged = ids -> {
    };
    private Consumer<List<String>> onClosedChanged = ids -> {
    };
    /** The tab being dragged, and whether it moved. */
    private String dragging;
    private boolean dragMoved;

    /** A view: its tab, and its body (its actions bar, if any, over its content, which scrolls). */
    private record View(String id, String title, Button tab, VBox body) {
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
        // On the row but not on a tab: the list of views, to open a closed one.
        tabsBar.setOnContextMenuRequested(e -> {
            menuFor(null).show(tabsBar, e.getScreenX(), e.getScreenY());
            e.consume();
        });

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

    /** The name of the tab menu's entry that closes the view. */
    public void setCloseText(String text) {
        closeText = text;
    }

    /** Told the id of each view chosen, by a click or not. */
    public void setOnSelected(Consumer<String> onSelected) {
        this.onSelected = onSelected != null ? onSelected : id -> {
        };
    }

    /** Told the views' ids in their new order after a tab is dragged elsewhere. */
    public void setOnOrderChanged(Consumer<List<String>> onOrderChanged) {
        this.onOrderChanged = onOrderChanged != null ? onOrderChanged : ids -> {
        };
    }

    /** Told the ids of the views closed (sorted) each time one is closed or opened again. */
    public void setOnClosedChanged(Consumer<List<String>> onClosedChanged) {
        this.onClosedChanged = onClosedChanged != null ? onClosedChanged : ids -> {
        };
    }

    /** The order kept from last time: the views that come are placed by it. */
    public void setPreferredOrder(List<String> ids) {
        preferredOrder = ids != null ? List.copyOf(ids) : List.of();
    }

    /**
     * Adds a view (or replaces the one with that id, in its place), where the order kept from
     * last time puts it, or at the end.
     *
     * @param icon    the tab's icon
     * @param content what the view shows; the panel scrolls it
     */
    public void addView(String id, String title, Node icon, Node content) {
        addView(id, title, icon, content, null);
    }

    /**
     * Adds a view with its own bar of actions ({@code actions}, nullable) over its content, as
     * Obsidian's views have: it does not scroll with the content.
     */
    public void addView(String id, String title, Node icon, Node content, Node actions) {
        Button tab = new Button();
        tab.getStyleClass().addAll("sidebar-nav-btn", "side-dock-tab");
        tab.setGraphic(icon);
        tab.setTooltip(new Tooltip(title));
        tab.setFocusTraversable(false);
        tab.setMinWidth(Region.USE_PREF_SIZE);
        tab.setOnAction(e -> select(id));
        tab.setOnContextMenuRequested(e -> {
            menuFor(id).show(tab, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        tab.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            dragging = id;
            dragMoved = false;
        });
        tab.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> dragTo(e.getSceneX()));
        tab.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> endDrag());

        VBox padded = new VBox(content);
        padded.getStyleClass().add("side-dock-view");
        padded.setPadding(new Insets(8, 12, 16, 12));
        ScrollPane scroll = new ScrollPane(padded);
        scroll.getStyleClass().addAll("panel-scroll", "side-dock-view-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox body = new VBox();
        if (actions != null) {
            body.getChildren().add(actions);
        }
        body.getChildren().add(scroll);

        View view = new View(id, title, tab, body);
        View old = byId.get(id);
        if (old != null) {
            tabsBar.getChildren().set(tabsBar.getChildren().indexOf(old.tab()), tab);
            views.getChildren().set(views.getChildren().indexOf(old.body()), body);
        } else {
            int at = placeFor(id);
            order.add(at, id);
            tabsBar.getChildren().add(at, tab);
            views.getChildren().add(body);
        }
        byId.put(id, view);
        if (inRow(id) && (id.equals(wanted) || selected == null || id.equals(selected))) {
            choose(id);
        } else {
            refresh();
        }
    }

    /** Where a view that comes goes: before the first one the order kept puts after it. */
    private int placeFor(String id) {
        int rank = preferredOrder.indexOf(id);
        if (rank < 0) {
            return order.size();
        }
        for (int i = 0; i < order.size(); i++) {
            int other = preferredOrder.indexOf(order.get(i));
            if (other < 0 || other > rank) {
                return i;
            }
        }
        return order.size();
    }

    public void removeView(String id) {
        View view = byId.remove(id);
        if (view == null) {
            return;
        }
        order.remove(id);
        tabsBar.getChildren().remove(view.tab());
        views.getChildren().remove(view.body());
        if (id.equals(selected)) {
            selected = null;
            firstInRow().ifPresent(this::choose);
        }
        refresh();
    }

    /** Chooses a view; one that is not here (or not in the row) yet is chosen when it comes. */
    public void select(String id) {
        if (!byId.containsKey(id) || !inRow(id)) {
            wanted = id;
            return;
        }
        choose(id);
    }

    public Optional<String> selected() {
        return Optional.ofNullable(selected);
    }

    /** The views' ids, in the order of their tabs (in the row or not). */
    public List<String> viewIds() {
        return new ArrayList<>(order);
    }

    /** Leaves a view out of the row, or brings it back (its owner hides it for a while). */
    public void setShown(String id, boolean shown) {
        if (shown) {
            hiddenByOwner.remove(id);
        } else {
            hiddenByOwner.add(id);
        }
        afterRowChanged(id);
    }

    public boolean isShown(String id) {
        return byId.containsKey(id) && !hiddenByOwner.contains(id);
    }

    /** Closes a view or opens it again (here or not yet); a view opened again is not chosen. */
    public void setClosed(String id, boolean close) {
        boolean changed = close ? closed.add(id) : closed.remove(id);
        if (changed) {
            afterRowChanged(id);
            onClosedChanged.accept(closed.stream().sorted().toList());
        }
    }

    public boolean isClosed(String id) {
        return closed.contains(id);
    }

    private void afterRowChanged(String id) {
        if (selected != null && selected.equals(id) && !inRow(id)) {
            selected = null;
            firstInRow().ifPresent(this::choose);
        } else if (selected == null && byId.containsKey(id) && inRow(id)) {
            choose(id);
        }
        refresh();
    }

    /**
     * The menu of the tab of {@code id} (or of the row, for {@code null}): close that view, then
     * every view the panel has, checked when open, to open or close it.
     */
    ContextMenu menuFor(String id) {
        ContextMenu menu = new ContextMenu();
        if (id != null) {
            MenuItem close = new MenuItem(closeText);
            close.setOnAction(e -> setClosed(id, true));
            menu.getItems().addAll(close, new SeparatorMenuItem());
        }
        for (String viewId : order) {
            if (hiddenByOwner.contains(viewId)) {
                continue;
            }
            CheckMenuItem item = new CheckMenuItem(byId.get(viewId).title());
            item.setSelected(!closed.contains(viewId));
            // Toggles what the panel has, not what the check shows: the same by click or not.
            item.setOnAction(e -> {
                boolean reopen = closed.contains(viewId);
                setClosed(viewId, !reopen);
                if (reopen) {
                    select(viewId);
                }
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    private boolean inRow(String id) {
        return !closed.contains(id) && !hiddenByOwner.contains(id);
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

    private Optional<String> firstInRow() {
        return order.stream().filter(this::inRow).findFirst();
    }

    private void refresh() {
        for (View view : byId.values()) {
            boolean chosen = view.id().equals(selected);
            view.tab().getStyleClass().remove("sidebar-nav-btn-selected");
            if (chosen) {
                view.tab().getStyleClass().add("sidebar-nav-btn-selected");
            }
            UiNodes.show(view.tab(), inRow(view.id()));
            view.body().setVisible(chosen);
        }
        empty.setVisible(selected == null);
    }

    /** While a tab is dragged: it takes the place of the tab under the pointer. */
    private void dragTo(double sceneX) {
        if (dragging == null) {
            return;
        }
        for (String id : order) {
            Button tab = byId.get(id).tab();
            if (!tab.isVisible() || id.equals(dragging)) {
                continue;
            }
            Bounds bounds = tab.localToScene(tab.getLayoutBounds());
            if (sceneX >= bounds.getMinX() && sceneX <= bounds.getMaxX()) {
                move(dragging, order.indexOf(id));
                dragMoved = true;
                return;
            }
        }
    }

    private void endDrag() {
        if (dragging != null && dragMoved) {
            onOrderChanged.accept(viewIds());
        }
        dragging = null;
        dragMoved = false;
    }

    private void move(String id, int to) {
        Button tab = byId.get(id).tab();
        order.remove(id);
        order.add(to, id);
        tabsBar.getChildren().remove(tab);
        tabsBar.getChildren().add(to, tab);
        applyCss();
        layout();
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
