package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

import com.example.jylos.ui.UiNodes;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * A side panel as Obsidian's: a row of icon tabs on top, one per view (the view's name is the
 * tab's tooltip), and under it the view of the chosen tab, alone and with its own scroll. When
 * the tabs do not fit, the row scrolls sideways with the mouse wheel, without a scroll bar,
 * and the chosen tab is brought into sight.
 *
 * <p>The panel can be split in groups, one over the other, each with its own row of tabs and
 * its own view shown, and a divider between them: "Split down" in a tab's menu puts its view
 * in a group of its own under its group, and a tab dropped on another group's row goes to that
 * group, as does a view moved up or down from its tab's menu. A tab dropped on the top or the
 * bottom of a group's view makes a group of its own above or below it, and on its middle joins
 * that group. While a tab is dragged nothing moves: a mark shows where it would go, and it goes
 * there when let go (let go away from the panel, it stays). A group left without views goes;
 * a view opened again comes back to its group.
 *
 * <p>The owner can add views of its own in a new group under another's
 * ({@link #addViewInNewGroupBelow}), mark a view as disposable (closing it takes it away for
 * good, {@link #setDisposable}) and add entries to a tab's menu ({@link #setMenuExtras}).
 *
 * <p>A tab is dragged along the row to move it; the views come in the order kept from last
 * time ({@link #setPreferredOrder}), a view not in it at the end, and in the group kept from
 * last time ({@link #setPreferredGroups}). A tab's menu closes its view and lists every view,
 * to open a closed one again. A view asked for, or closed, before it exists (the one chosen
 * last time, a plugin's that registers later) is so when it comes.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
public final class SideDock extends VBox {

    private final SplitPane split = new SplitPane();
    private final Map<String, View> byId = new HashMap<>();
    /** The views' ids in the order of their tabs (each group shows its own, in this order). */
    private final List<String> order = new ArrayList<>();
    private List<String> preferredOrder = List.of();
    /** The group of each view known, here or not yet; 0 is the top one, gaps allowed. */
    private final Map<String, Integer> groupOf = new HashMap<>();
    private final Map<Integer, Group> groups = new HashMap<>();
    /** The groups shown, top to bottom. */
    private List<Integer> shownGroups = List.of();
    private List<Double> preferredDividers = List.of();
    /** Closed from the tabs' menu (or last time), here or not yet. */
    private final Set<String> closed = new HashSet<>();
    /** Left out of the row by their owner for a while ({@link #setShown}). */
    private final Set<String> hiddenByOwner = new HashSet<>();
    /** The view chosen last, in whichever group. */
    private String selected;
    /** The views asked for that are not here (or not in a row) yet. */
    private final Set<String> wanted = new LinkedHashSet<>();
    private String emptyText = "";
    private String closeText = "Close";
    private String splitText = "Split down";
    private String moveUpText = "Move up";
    private String moveDownText = "Move down";
    private Consumer<String> onSelected = id -> {
    };
    private Consumer<List<String>> onOrderChanged = ids -> {
    };
    private Consumer<List<String>> onClosedChanged = ids -> {
    };
    private Consumer<Map<String, Integer>> onGroupsChanged = groups -> {
    };
    private Consumer<List<Double>> onDividersChanged = positions -> {
    };
    private Consumer<String> onDiscarded = id -> {
    };
    private Function<String, List<MenuItem>> menuExtras = id -> List.of();
    /** Views that go for good when closed, not listed to open again. */
    private final Set<String> disposable = new HashSet<>();
    /** The tab being dragged. */
    private String dragging;
    /** Over the groups, the marks of where a dragged tab would go. */
    private final Pane marks = new Pane();
    private final Region zoneMark = new Region();
    private final Region lineMark = new Region();

    /** The part of a group's view, at its top and at its bottom, where a tab makes a group of its own. */
    private static final double EDGE = 0.25;

    /** Where a dragged tab would go: into a group's row (before {@code before}, or at its end), a new group, or a group's end. */
    private enum DropKind {
        ROW, ABOVE, BELOW, JOIN
    }

    private record Drop(DropKind kind, int group, String before) {
    }

    /** A view: its tab, and its body (its actions bar, if any, over its content, which scrolls). */
    private record View(String id, String title, Button tab, VBox body) {
    }

    /** A group: a row of tabs over the view chosen in it. */
    private final class Group {
        final HBox tabsBar = new HBox(6);
        final ScrollPane tabsRow = new ScrollPane(tabsBar);
        final StackPane views = new StackPane();
        final Label empty = new Label();
        final VBox root = new VBox();
        String chosen;

        Group() {
            root.getStyleClass().add("side-dock-group");
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
            tabsRow.addEventFilter(ScrollEvent.SCROLL, e -> scrollTabs(this, e));
            // On the row but not on a tab: the list of views, to open a closed one.
            tabsBar.setOnContextMenuRequested(e -> {
                menuFor(null).show(tabsBar, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            empty.getStyleClass().addAll("side-dock-empty", "info-value-small");
            empty.setWrapText(true);
            empty.setText(emptyText);
            StackPane.setAlignment(empty, Pos.TOP_CENTER);
            views.getStyleClass().add("side-dock-views");
            VBox.setVgrow(views, Priority.ALWAYS);
            // A group shrinks down to its row of tabs, as Obsidian's: what does not fit is cut.
            views.setMinHeight(0);
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(views.widthProperty());
            clip.heightProperty().bind(views.heightProperty());
            views.setClip(clip);
            root.getChildren().addAll(tabsRow, views);
        }
    }

    public SideDock() {
        getStyleClass().add("side-dock");
        split.getStyleClass().add("side-dock-split");
        split.setOrientation(Orientation.VERTICAL);
        VBox.setVgrow(split, Priority.ALWAYS);
        // How tall the groups are is told when a divider is let go, not while the panel lays out.
        split.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> dividersLetGo());
        zoneMark.getStyleClass().add("side-dock-drop-zone");
        lineMark.getStyleClass().add("side-dock-drop-line");
        for (Region mark : List.of(zoneMark, lineMark)) {
            mark.setManaged(false);
            mark.setVisible(false);
        }
        marks.getChildren().addAll(zoneMark, lineMark);
        marks.setMouseTransparent(true);
        StackPane layers = new StackPane(split, marks);
        VBox.setVgrow(layers, Priority.ALWAYS);
        getChildren().add(layers);
        layoutGroups();
    }

    /** What the panel says when it has no view. */
    public void setEmptyText(String text) {
        emptyText = text != null ? text : "";
        groups.values().forEach(group -> group.empty.setText(emptyText));
    }

    /** The name of the tab menu's entry that closes the view. */
    public void setCloseText(String text) {
        closeText = text;
    }

    /** The name of the tab menu's entry that splits the view down. */
    public void setSplitText(String text) {
        splitText = text;
    }

    /** The names of the tab menu's entries that move the view to the group above and below. */
    public void setMoveTexts(String up, String down) {
        moveUpText = up;
        moveDownText = down;
    }

    /** Told the id of a disposable view closed, once it is gone. */
    public void setOnDiscarded(Consumer<String> onDiscarded) {
        this.onDiscarded = onDiscarded != null ? onDiscarded : id -> {
        };
    }

    /** Entries of the owner's for the menu of a view's tab, after the panel's own (none for {@code null}). */
    public void setMenuExtras(Function<String, List<MenuItem>> extras) {
        menuExtras = extras != null ? extras : id -> List.of();
    }

    /**
     * Closing the view {@code id} takes it away for good (it is not listed to open again). Its
     * tab is wider, for an icon that carries a mark of what it is (a pin, on a copy).
     */
    public void setDisposable(String id) {
        disposable.add(id);
        View view = byId.get(id);
        if (view != null && !view.tab().getStyleClass().contains("side-dock-tab-disposable")) {
            view.tab().getStyleClass().add("side-dock-tab-disposable");
        }
    }

    /** Names the view {@code id} anew: its tab's tooltip and its entry in the menus. */
    public void setTitle(String id, String title) {
        View view = byId.get(id);
        if (view != null) {
            byId.put(id, new View(id, title, view.tab(), view.body()));
            view.tab().getTooltip().setText(title);
        }
    }

    /**
     * Adds a view in a group of its own right under the group of the view {@code under} (the
     * groups under it go one down), chosen there. With no view {@code under}, as {@link #addView}.
     */
    public void addViewInNewGroupBelow(String under, String id, String title, Node icon, Node content, Node actions) {
        addView(id, title, icon, content, actions);
        Integer at = byId.containsKey(under) ? groupOf.get(under) : null;
        if (at != null && !under.equals(id)) {
            newGroup(id, at, true);
            onOrderChanged.accept(viewIds());
            onGroupsChanged.accept(new TreeMap<>(groupOf));
        }
    }

    /** Told the id of each view chosen in its group, by a click or not. */
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

    /** Told the group of each view known (sorted by id) when a view is split down or dragged to another group. */
    public void setOnGroupsChanged(Consumer<Map<String, Integer>> onGroupsChanged) {
        this.onGroupsChanged = onGroupsChanged != null ? onGroupsChanged : groups -> {
        };
    }

    /** Told where the dividers between the groups are (0 to 1, top to bottom) when one is let go. */
    public void setOnDividersChanged(Consumer<List<Double>> onDividersChanged) {
        this.onDividersChanged = onDividersChanged != null ? onDividersChanged : positions -> {
        };
    }

    /** The order kept from last time: the views that come are placed by it. */
    public void setPreferredOrder(List<String> ids) {
        preferredOrder = ids != null ? List.copyOf(ids) : List.of();
    }

    /** The groups kept from last time: a view that comes goes to its group (one not named, to the top). */
    public void setPreferredGroups(Map<String, Integer> groups) {
        if (groups != null) {
            groups.forEach((id, group) -> groupOf.put(id, Math.max(0, group)));
        }
        layoutGroups();
    }

    /** Where the dividers were last time, used when the panel has as many groups as then. */
    public void setPreferredDividers(List<Double> positions) {
        preferredDividers = positions != null ? List.copyOf(positions) : List.of();
        applyDividers();
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
        });
        tab.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> dragTo(e.getSceneX(), e.getSceneY()));
        tab.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> endDrag(e.getSceneX(), e.getSceneY()));

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

        if (!byId.containsKey(id)) {
            order.add(placeFor(id), id);
        }
        byId.put(id, new View(id, title, tab, body));
        groupOf.putIfAbsent(id, shownGroups.isEmpty() ? 0 : shownGroups.get(0));
        Group group = group(groupOf.get(id));
        if (inRow(id) && (wanted.contains(id) || group.chosen == null || id.equals(group.chosen))) {
            choose(id);
        } else {
            layoutGroups();
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
        if (byId.remove(id) == null) {
            return;
        }
        order.remove(id);
        layoutGroups();
    }

    /** Chooses a view in its group; one that is not here (or not in a row) yet is chosen when it comes. */
    public void select(String id) {
        if (!byId.containsKey(id) || !inRow(id)) {
            wanted.add(id);
            return;
        }
        choose(id);
    }

    /** The view chosen last, in whichever group, while it shows. */
    public Optional<String> selected() {
        return Optional.ofNullable(selected);
    }

    /** The view each group shows, top to bottom. */
    public List<String> shown() {
        List<String> shown = new ArrayList<>();
        for (int index : shownGroups) {
            String chosen = groups.get(index).chosen;
            if (chosen != null) {
                shown.add(chosen);
            }
        }
        return shown;
    }

    /** The views' ids, in the order of their tabs (in a row or not). */
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

    /** Closes a view or opens it again (here or not yet), in its group; a view opened again is not chosen. */
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
        if (wanted.contains(id) && byId.containsKey(id) && inRow(id)) {
            choose(id);
        } else {
            layoutGroups();
        }
    }

    /**
     * Puts the view {@code id} in a group of its own, right under its group (the groups under
     * it go one down). A view alone in its group stays.
     */
    public void splitDown(String id) {
        Integer at = groupOf.get(id);
        if (at == null || !byId.containsKey(id) || inRowOf(at).size() < 2) {
            return;
        }
        newGroup(id, at, true);
        onGroupsChanged.accept(new TreeMap<>(groupOf));
    }

    /**
     * Puts the view {@code id} in a new group of its own right above group {@code at} or right
     * under it (the groups from there go one down), and chooses it there.
     */
    private void newGroup(String id, int at, boolean below) {
        int target = below ? at + 1 : at;
        groupOf.replaceAll((view, group) -> group >= target ? group + 1 : group);
        Map<Integer, Group> moved = new HashMap<>();
        groups.forEach((index, group) -> moved.put(index >= target ? index + 1 : index, group));
        groups.clear();
        groups.putAll(moved);
        int from = groupOf.get(id);
        groupOf.put(id, target);
        Group left = groups.get(from);
        if (left != null && id.equals(left.chosen)) {
            left.chosen = null;
        }
        choose(id);
    }

    /**
     * Moves the view {@code id} to the group shown above it ({@code down} false) or below it, at
     * its end, and chooses it there. With no group that way it stays.
     */
    public void moveToNextGroup(String id, boolean down) {
        Integer target = nextGroup(id, down);
        if (target != null) {
            regroup(id, target, null);
            onOrderChanged.accept(viewIds());
            onGroupsChanged.accept(new TreeMap<>(groupOf));
        }
    }

    /** The group shown next to the one of {@code id}, below or above, or {@code null}. */
    private Integer nextGroup(String id, boolean down) {
        int at = groupOf.containsKey(id) ? shownGroups.indexOf(groupOf.get(id)) : -1;
        int next = at + (down ? 1 : -1);
        return at < 0 || next < 0 || next >= shownGroups.size() ? null : shownGroups.get(next);
    }

    /**
     * Puts the view {@code id} in group {@code target}, before the view {@code before} (or at the
     * end of the group, for {@code null}), and chooses it there.
     */
    private void regroup(String id, int target, String before) {
        int from = groupOf.get(id);
        List<String> rest = new ArrayList<>(order);
        rest.remove(id);
        groupOf.put(id, target);
        int at = before != null ? rest.indexOf(before) : afterLastOf(rest, target);
        order.clear();
        order.addAll(rest);
        order.add(at, id);
        Group left = groups.get(from);
        if (left != null && id.equals(left.chosen)) {
            left.chosen = null;
        }
        choose(id);
    }

    /**
     * The menu of the tab of {@code id} (or of a row, for {@code null}): close that view, split
     * it down, move it to the group above or below (when there is one), then every view the
     * panel has, checked when open, to open or close it.
     */
    public ContextMenu menuFor(String id) {
        ContextMenu menu = new ContextMenu();
        if (id != null) {
            MenuItem close = new MenuItem(closeText);
            close.setOnAction(e -> {
                if (disposable.contains(id)) {
                    discard(id);
                } else {
                    setClosed(id, true);
                }
            });
            MenuItem splitDown = new MenuItem(splitText);
            splitDown.setDisable(!groupOf.containsKey(id) || inRowOf(groupOf.get(id)).size() < 2);
            splitDown.setOnAction(e -> splitDown(id));
            menu.getItems().addAll(close, splitDown);
            if (nextGroup(id, false) != null) {
                MenuItem up = new MenuItem(moveUpText);
                up.setOnAction(e -> moveToNextGroup(id, false));
                menu.getItems().add(up);
            }
            if (nextGroup(id, true) != null) {
                MenuItem down = new MenuItem(moveDownText);
                down.setOnAction(e -> moveToNextGroup(id, true));
                menu.getItems().add(down);
            }
            menu.getItems().addAll(menuExtras.apply(id));
            menu.getItems().add(new SeparatorMenuItem());
        }
        for (String viewId : order) {
            if (hiddenByOwner.contains(viewId) || disposable.contains(viewId)) {
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

    /** Takes a disposable view away for good, and tells so. */
    private void discard(String id) {
        removeView(id);
        disposable.remove(id);
        groupOf.remove(id);
        wanted.remove(id);
        onOrderChanged.accept(viewIds());
        onGroupsChanged.accept(new TreeMap<>(groupOf));
        onDiscarded.accept(id);
    }

    private boolean inRow(String id) {
        return !closed.contains(id) && !hiddenByOwner.contains(id);
    }

    /** The views of group {@code index} in a row, in order. */
    private List<String> inRowOf(int index) {
        return order.stream().filter(id -> groupOf.get(id) == index && inRow(id)).toList();
    }

    private Group group(int index) {
        return groups.computeIfAbsent(index, i -> new Group());
    }

    private void choose(String id) {
        Group group = group(groupOf.get(id));
        boolean changed = !id.equals(group.chosen);
        group.chosen = id;
        selected = id;
        wanted.remove(id);
        layoutGroups();
        bringIntoSight(group, byId.get(id).tab());
        if (changed) {
            onSelected.accept(id);
        }
    }

    /**
     * Puts each view in its group, each group's chosen view in sight (the first of its row when
     * none is), and the groups that have a view in a row in the split, top to bottom; with none,
     * the top group shows, saying the panel is empty.
     */
    private void layoutGroups() {
        TreeSet<Integer> withViews = new TreeSet<>();
        for (String id : order) {
            if (inRow(id)) {
                withViews.add(groupOf.get(id));
            }
        }
        if (withViews.isEmpty()) {
            withViews.add(groups.isEmpty() ? 0 : groups.keySet().stream().min(Integer::compare).orElse(0));
        }
        withViews.forEach(this::group);
        List<String> nowChosen = new ArrayList<>();
        for (Map.Entry<Integer, Group> entry : groups.entrySet()) {
            int index = entry.getKey();
            Group group = entry.getValue();
            List<View> members = order.stream().filter(id -> groupOf.get(id) == index).map(byId::get).toList();
            List<Node> tabs = new ArrayList<>();
            List<Node> bodies = new ArrayList<>(List.of(group.empty));
            for (View view : members) {
                tabs.add(view.tab());
                bodies.add(view.body());
            }
            group.tabsBar.getChildren().setAll(tabs);
            group.views.getChildren().setAll(bodies);
            List<String> row = inRowOf(index);
            if (group.chosen != null && !row.contains(group.chosen)) {
                group.chosen = null;
            }
            if (group.chosen == null && !row.isEmpty()) {
                group.chosen = row.get(0);
                nowChosen.add(group.chosen);
            }
            for (View view : members) {
                boolean chosen = view.id().equals(group.chosen);
                view.tab().getStyleClass().remove("sidebar-nav-btn-selected");
                if (chosen) {
                    view.tab().getStyleClass().add("sidebar-nav-btn-selected");
                }
                UiNodes.show(view.tab(), inRow(view.id()));
                view.body().setVisible(chosen);
            }
            group.empty.setVisible(group.chosen == null);
        }
        List<Integer> nowShown = List.copyOf(withViews);
        if (!nowShown.equals(shownGroups)) {
            shownGroups = nowShown;
            split.getItems().setAll(nowShown.stream().map(index -> (Node) groups.get(index).root).toList());
            applyDividers();
        }
        if (selected == null || !shown().contains(selected)) {
            List<String> shown = shown();
            selected = shown.isEmpty() ? null : shown.get(0);
        }
        nowChosen.forEach(onSelected);
    }

    /** The dividers where they were last time when there are as many groups as then, else even. */
    private void applyDividers() {
        int count = split.getItems().size() - 1;
        if (count <= 0) {
            return;
        }
        double[] positions = new double[count];
        for (int i = 0; i < count; i++) {
            positions[i] = preferredDividers.size() == count ? preferredDividers.get(i) : (i + 1.0) / (count + 1);
        }
        split.setDividerPositions(positions);
    }

    private void dividersLetGo() {
        List<Double> now = new ArrayList<>();
        for (double position : split.getDividerPositions()) {
            now.add(position);
        }
        if (!now.isEmpty() && !now.equals(preferredDividers)) {
            preferredDividers = List.copyOf(now);
            onDividersChanged.accept(preferredDividers);
        }
    }

    /**
     * While a tab is dragged: the mark of where it would go. Over its own tab there is none, so a
     * click that slips a little shows nothing.
     */
    private void dragTo(double sceneX, double sceneY) {
        if (dragging == null) {
            return;
        }
        showMark(dropAt(sceneX, sceneY));
    }

    /**
     * Where the dragged tab would go with the pointer at a point of the scene, or {@code null}
     * where it would change nothing. On a row of tabs: in another group's, before the tab under
     * the pointer (or at its end); along its own, in the place of the tab under the pointer. On a
     * group's view: at its top or its bottom, a group of its own there; on its middle, that
     * group's end.
     */
    private Drop dropAt(double sceneX, double sceneY) {
        int from = groupOf.get(dragging);
        for (int index : shownGroups) {
            Group group = groups.get(index);
            if (sceneBounds(group.tabsRow).contains(sceneX, sceneY)) {
                String under = tabUnder(index, sceneX);
                if (index == from && (under == null || under.equals(dragging))) {
                    return null;
                }
                return new Drop(DropKind.ROW, index, under);
            }
            Bounds body = sceneBounds(group.views);
            if (body.contains(sceneX, sceneY)) {
                double at = (sceneY - body.getMinY()) / body.getHeight();
                boolean edge = at < EDGE || at > 1 - EDGE;
                if (index == from && (!edge || inRowOf(from).size() < 2)) {
                    return null;
                }
                return new Drop(!edge ? DropKind.JOIN : at < EDGE ? DropKind.ABOVE : DropKind.BELOW, index, null);
            }
        }
        return null;
    }

    /** Over the half (or the whole) of the view a drop makes a group in or joins, or as a line between tabs. */
    private void showMark(Drop drop) {
        zoneMark.setVisible(false);
        lineMark.setVisible(false);
        if (drop == null) {
            return;
        }
        Group group = groups.get(drop.group());
        if (drop.kind() == DropKind.ROW) {
            Bounds row = sceneBounds(group.tabsRow);
            double x = lineX(drop);
            place(lineMark, x - 1, row.getMinY() + 4, 2, row.getHeight() - 8);
            return;
        }
        Bounds body = sceneBounds(group.views);
        double half = body.getHeight() / 2;
        double top = drop.kind() == DropKind.BELOW ? body.getMinY() + half : body.getMinY();
        place(zoneMark, body.getMinX(), top, body.getWidth(), drop.kind() == DropKind.JOIN ? body.getHeight() : half);
    }

    /** Where, along the row, the line of a drop on a row goes: between two tabs, or after the last one. */
    private double lineX(Drop drop) {
        double gap = groups.get(drop.group()).tabsBar.getSpacing() / 2;
        if (drop.before() == null) {
            List<String> row = inRowOf(drop.group());
            if (row.isEmpty()) {
                return sceneBounds(groups.get(drop.group()).tabsBar).getMinX() + 16;
            }
            return sceneBounds(byId.get(row.get(row.size() - 1)).tab()).getMaxX() + gap;
        }
        Bounds under = sceneBounds(byId.get(drop.before()).tab());
        boolean after = drop.group() == groupOf.get(dragging)
                && order.indexOf(drop.before()) > order.indexOf(dragging);
        return after ? under.getMaxX() + gap : under.getMinX() - gap;
    }

    private void place(Region mark, double sceneX, double sceneY, double width, double height) {
        Bounds at = marks.sceneToLocal(new BoundingBox(sceneX, sceneY, width, height));
        mark.resizeRelocate(at.getMinX(), at.getMinY(), at.getWidth(), at.getHeight());
        mark.setVisible(true);
    }

    private static Bounds sceneBounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    /** The view whose tab, in group {@code index}, is under {@code sceneX}. */
    private String tabUnder(int index, double sceneX) {
        for (String id : inRowOf(index)) {
            Bounds bounds = sceneBounds(byId.get(id).tab());
            if (sceneX >= bounds.getMinX() && sceneX <= bounds.getMaxX()) {
                return id;
            }
        }
        return null;
    }

    /** Where in {@code ids} a view goes to be the last of group {@code index}. */
    private int afterLastOf(List<String> ids, int index) {
        int at = ids.size();
        for (int i = 0; i < ids.size(); i++) {
            if (groupOf.get(ids.get(i)) == index) {
                at = i + 1;
            }
        }
        return at;
    }

    /** Let go: the tab goes where its mark showed, and the new order (and groups) are told. */
    private void endDrag(double sceneX, double sceneY) {
        Drop drop = dragging != null ? dropAt(sceneX, sceneY) : null;
        String id = dragging;
        dragging = null;
        showMark(null);
        if (drop == null) {
            return;
        }
        boolean regrouped = drop.group() != groupOf.get(id) || drop.kind() != DropKind.ROW;
        switch (drop.kind()) {
            case ROW -> {
                if (drop.group() == groupOf.get(id)) {
                    move(id, order.indexOf(drop.before()));
                } else {
                    regroup(id, drop.group(), drop.before());
                }
            }
            case ABOVE -> newGroup(id, drop.group(), false);
            case BELOW -> newGroup(id, drop.group(), true);
            case JOIN -> regroup(id, drop.group(), null);
        }
        onOrderChanged.accept(viewIds());
        if (regrouped) {
            onGroupsChanged.accept(new TreeMap<>(groupOf));
        }
    }

    private void move(String id, int to) {
        order.remove(id);
        order.add(to, id);
        layoutGroups();
        applyCss();
        layout();
    }

    /** The wheel moves a row sideways: up or left goes back, down or right forward. */
    private void scrollTabs(Group group, ScrollEvent event) {
        ScrollPane row = group.tabsRow;
        double delta = event.getDeltaX() != 0 ? event.getDeltaX() : event.getDeltaY();
        double hidden = group.tabsBar.getWidth() - row.getViewportBounds().getWidth();
        if (hidden > 0 && delta != 0) {
            double value = row.getHvalue() - delta / hidden * (row.getHmax() - row.getHmin());
            row.setHvalue(Math.max(row.getHmin(), Math.min(row.getHmax(), value)));
        }
        event.consume();
    }

    private void bringIntoSight(Group group, Button tab) {
        applyCss();
        layout();
        ScrollPane row = group.tabsRow;
        double viewport = row.getViewportBounds().getWidth();
        double hidden = group.tabsBar.getWidth() - viewport;
        if (hidden <= 0) {
            return;
        }
        Bounds bounds = tab.getBoundsInParent();
        double left = (row.getHvalue() - row.getHmin()) / (row.getHmax() - row.getHmin()) * hidden;
        double target = left;
        if (bounds.getMinX() < left) {
            target = bounds.getMinX();
        } else if (bounds.getMaxX() > left + viewport) {
            target = bounds.getMaxX() - viewport;
        }
        double value = row.getHmin() + Math.min(1, target / hidden) * (row.getHmax() - row.getHmin());
        row.setHvalue(value);
    }
}
