package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.NoteContextMenuEntry;
import com.example.jylos.plugin.PluginManager;
import com.example.jylos.plugin.PluginMenuRegistry;
import com.example.jylos.plugin.PluginNoteContextMenuRegistry;
import com.example.jylos.plugin.SidePanelRegistry;
import com.example.jylos.plugin.StatusBarRegistry;
import com.example.jylos.plugin.ToolbarRegistry;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.SideDock;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * What plugins add to the window: items in the Plugins menu, side panels in the right panel,
 * toolbar buttons and entries in a note's context menu. Keeps each plugin's contributions so
 * they can be taken out again when the plugin is disabled.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
class PluginUi implements PluginMenuRegistry, SidePanelRegistry, ToolbarRegistry, PluginNoteContextMenuRegistry,
        StatusBarRegistry {

    private static final Logger logger = LoggerConfig.getLogger(PluginUi.class);

    private Function<String, String> i18nFn = key -> key;
    private Consumer<String> updateStatus = message -> { };
    private Supplier<PluginManager> pluginManagerSupplier = () -> null;
    private Supplier<ToolbarController> toolbar = () -> null;
    private SideDock rightDock;
    /** Whether the plugin panels' tabs are in the right panel (a plugin can hide them all). */
    private boolean pluginPanelsShown = true;
    private HBox statusBarContainer;

    private final Map<String, Menu> pluginCategoryMenus = new HashMap<>();
    private final Map<String, List<MenuItem>> pluginMenuItems = new HashMap<>();
    /** pluginId → its panels' view ids in the right panel. */
    private final Map<String, List<String>> pluginPanelIds = new HashMap<>();
    /** pluginId → toolbar buttons. */
    private final Map<String, List<Node>> pluginToolbarButtons = new HashMap<>();
    /** pluginId → note context menu entries. */
    private final Map<String, List<NoteContextMenuEntry>> pluginNoteContextMenuItems = new HashMap<>();

    /**
     * @param toolbar               the toolbar, whose Plugins menu and plugin button area receive items
     * @param rightDock             the right panel, where each plugin side panel is a view
     * @param statusBarContainer    the status bar's area for plugin items
     */
    void wire(Function<String, String> i18n, Consumer<String> updateStatus,
            Supplier<PluginManager> pluginManagerSupplier, Supplier<ToolbarController> toolbar,
            SideDock rightDock, HBox statusBarContainer) {
        this.i18nFn = i18n;
        this.updateStatus = updateStatus;
        this.pluginManagerSupplier = pluginManagerSupplier;
        this.toolbar = toolbar != null ? toolbar : () -> null;
        this.rightDock = rightDock;
        this.statusBarContainer = statusBarContainer;
    }

    @Override
    public void registerMenuItem(String pluginId, String category, String itemName, Runnable action) {
        registerMenuItem(pluginId, category, itemName, null, action);
    }

    @Override
    public boolean isPluginEnabled(String pluginId) {
        PluginManager pluginManager = pluginManagerSupplier.get();
        return pluginManager != null && pluginManager.isPluginEnabled(pluginId);
    }

    @Override
    public void registerMenuItem(String pluginId, String category, String itemName, String shortcut, Runnable action) {
        ToolbarController toolbarController = toolbar.get();
        Platform.runLater(() -> {
            if (toolbarController == null || toolbarController.getPluginsMenu() == null) {
                logger.warning("Plugins menu not available for registration: " + itemName);
                return;
            }

            Menu categoryMenu = pluginCategoryMenus.get(category);
            if (categoryMenu == null) {
                categoryMenu = new Menu(category);
                pluginCategoryMenus.put(category, categoryMenu);
                int insertIndex = Math.min(toolbarController.getPluginsMenu().getItems().size(), 2);
                toolbarController.getPluginsMenu().getItems().add(insertIndex, categoryMenu);
            }

            MenuItem menuItem = new MenuItem(itemName);
            menuItem.setOnAction(e -> {
                PluginManager pluginManager = pluginManagerSupplier.get();
                if (pluginManager != null && pluginManager.isPluginEnabled(pluginId)) {
                    action.run();
                } else {
                    updateStatus.accept(
                            MessageFormat.format(i18nFn.apply("status.plugin_not_enabled"), pluginId));
                }
            });

            if (shortcut != null && !shortcut.isEmpty()) {
                try {
                    menuItem.setAccelerator(KeyCombination.keyCombination(shortcut));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Invalid shortcut for plugin menu item", ex);
                }
            }

            categoryMenu.getItems().add(menuItem);
            pluginMenuItems.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(menuItem);

            logger.fine("Registered menu item: " + category + " > " + itemName + " for plugin " + pluginId);
        });
    }

    @Override
    public void addMenuSeparator(String pluginId, String category) {
        Platform.runLater(() -> {
            Menu categoryMenu = pluginCategoryMenus.get(category);
            if (categoryMenu != null) {
                SeparatorMenuItem separator = new SeparatorMenuItem();
                categoryMenu.getItems().add(separator);
                pluginMenuItems.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(separator);
            }
        });
    }

    @Override
    public void removePluginMenuItems(String pluginId) {
        ToolbarController toolbarController = toolbar.get();
        Platform.runLater(() -> {
            List<MenuItem> items = pluginMenuItems.remove(pluginId);
            if (items == null) {
                return;
            }

            for (MenuItem item : items) {
                for (Menu categoryMenu : pluginCategoryMenus.values()) {
                    categoryMenu.getItems().remove(item);
                }
            }

            pluginCategoryMenus.entrySet().removeIf(entry -> {
                Menu menu = entry.getValue();
                if (menu.getItems().isEmpty()) {
                    if (toolbarController != null && toolbarController.getPluginsMenu() != null) {
                        toolbarController.getPluginsMenu().getItems().remove(menu);
                    }
                    return true;
                }
                return false;
            });

            logger.info("Removed menu items for plugin: " + pluginId);
        });
    }

    @Override
    public void registerSidePanel(String pluginId, String panelId, String title, Node content, String icon) {
        Platform.runLater(() -> {
            if (rightDock == null) {
                logger.warning("The right panel is not available for registration: " + panelId);
                return;
            }
            // Each plugin panel is one view of the right panel, with its tab, as Obsidian's.
            String viewId = viewId(pluginId, panelId);
            rightDock.addView(viewId, title, panelIcon(icon), content);
            List<String> ids = pluginPanelIds.computeIfAbsent(pluginId, k -> new ArrayList<>());
            if (!ids.contains(viewId)) {
                ids.add(viewId);
            }
            rightDock.setShown(viewId, pluginPanelsShown);
            logger.fine("Registered side panel: " + title + " for plugin " + pluginId);
        });
    }

    /** The id of a plugin panel's view in the right panel. */
    static String viewId(String pluginId, String panelId) {
        return "plugin:" + pluginId + ":" + panelId;
    }

    /**
     * The tab's icon: an Ikonli literal (e.g. "fth-calendar") as an icon; any other text (an
     * emoji) as it is; none, a generic one.
     */
    private static Node panelIcon(String icon) {
        String literal = icon != null ? icon.trim() : "";
        if (literal.isEmpty() || literal.matches("[a-z]{2,5}-[a-z0-9-]+")) {
            try {
                FontIcon fontIcon = new FontIcon(literal.isEmpty() ? "fth-box" : literal);
                fontIcon.setIconSize(16); // as the panel's own tabs
                fontIcon.getStyleClass().add("feather-icon");
                return fontIcon;
            } catch (RuntimeException unknown) {
                // Not a known icon literal: shown as text below.
            }
        }
        Label text = new Label(literal.isEmpty() ? "?" : literal);
        text.getStyleClass().add("side-dock-text-icon");
        return text;
    }

    @Override
    public void removeSidePanel(String pluginId, String panelId) {
        Platform.runLater(() -> {
            String viewId = viewId(pluginId, panelId);
            List<String> ids = pluginPanelIds.get(pluginId);
            if (ids != null) {
                ids.remove(viewId);
            }
            if (rightDock != null) {
                rightDock.removeView(viewId);
            }
            logger.info("Removed side panel: " + panelId);
        });
    }

    @Override
    public void removeAllSidePanels(String pluginId) {
        Platform.runLater(() -> {
            List<String> ids = pluginPanelIds.remove(pluginId);
            if (ids == null || rightDock == null) {
                return;
            }
            ids.forEach(rightDock::removeView);
            logger.info("Removed all side panels for plugin: " + pluginId);
        });
    }

    /** Shows or hides the tabs of every plugin panel (a plugin's "show/hide panel" command). */
    @Override
    public void setPluginPanelsVisible(boolean visible) {
        Platform.runLater(() -> {
            pluginPanelsShown = visible;
            if (rightDock != null) {
                pluginPanelIds.values().forEach(ids -> ids.forEach(id -> rightDock.setShown(id, visible)));
            }
        });
    }

    @Override
    public boolean isPluginPanelsVisible() {
        return pluginPanelsShown;
    }

    // ── Toolbar buttons ──────────────────────────────────────────────────────

    @Override
    public void registerToolbarButton(String pluginId, String buttonId, String tooltip,
            String iconLiteral, Runnable action) {
        if (pluginId == null || buttonId == null || action == null) {
            return;
        }
        ToolbarController toolbarController = toolbar.get();
        HBox container = toolbarController != null ? toolbarController.getPluginToolbarContainer() : null;
        if (container == null) {
            return;
        }
        Platform.runLater(() -> {
            Button button = new Button();
            button.getStyleClass().add("toolbar-btn");
            // Stable per-plugin id so re-registration replaces instead of duplicating.
            button.setId("plugin-toolbar-" + pluginId + "-" + buttonId);
            if (tooltip != null && !tooltip.isBlank()) {
                button.setTooltip(new Tooltip(tooltip));
            }
            if (iconLiteral != null && !iconLiteral.isBlank()) {
                FontIcon icon = new FontIcon(iconLiteral);
                icon.getStyleClass().add("feather-icon");
                icon.setIconSize(16);
                button.setGraphic(icon);
            } else {
                button.setText(tooltip != null ? tooltip : buttonId);
            }
            button.setOnAction(e -> action.run());

            container.getChildren().removeIf(n -> button.getId().equals(n.getId()));
            container.getChildren().add(button);
            pluginToolbarButtons.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(button);
            UiNodes.show(container, true);
        });
    }

    @Override
    public void removeToolbarButtons(String pluginId) {
        ToolbarController toolbarController = toolbar.get();
        HBox container = toolbarController != null ? toolbarController.getPluginToolbarContainer() : null;
        List<Node> buttons = pluginToolbarButtons.remove(pluginId);
        if (container == null || buttons == null) {
            return;
        }
        Platform.runLater(() -> {
            container.getChildren().removeAll(buttons);
            UiNodes.show(container, !container.getChildren().isEmpty());
        });
    }

    // ── Note context menu entries ────────────────────────────────────────────

    @Override
    public void registerNoteContextMenuItem(String pluginId, String label, Consumer<Note> action) {
        pluginNoteContextMenuItems.computeIfAbsent(pluginId, k -> new ArrayList<>())
                .add(new NoteContextMenuEntry(pluginId, label, action));
    }

    @Override
    public void removePluginNoteContextMenuItems(String pluginId) {
        pluginNoteContextMenuItems.remove(pluginId);
    }

    @Override
    public List<NoteContextMenuEntry> getNoteContextMenuItems() {
        List<NoteContextMenuEntry> all = new ArrayList<>();
        for (List<NoteContextMenuEntry> entries : pluginNoteContextMenuItems.values()) {
            all.addAll(entries);
        }
        return all;
    }

    // ── Status bar items ─────────────────────────────────────────────────────

    /** pluginId:itemId → the item shown (its separator and content), in registration order. */
    private final Map<String, Node> statusBarItems = new LinkedHashMap<>();

    @Override
    public void registerStatusBarItem(String pluginId, String itemId, Node content) {
        if (pluginId == null || itemId == null || content == null) {
            return;
        }
        Platform.runLater(() -> {
            if (statusBarContainer == null) {
                logger.warning("Status bar area not available for: " + itemId);
                return;
            }
            // A separator before each item, as between the bar's own items.
            Separator separator = new Separator(Orientation.VERTICAL);
            separator.getStyleClass().add("status-separator");
            HBox item = new HBox(8, separator, content);
            item.setAlignment(Pos.CENTER_LEFT);
            String key = pluginId + ":" + itemId;
            Node previous = statusBarItems.put(key, item);
            int at = previous != null ? statusBarContainer.getChildren().indexOf(previous) : -1;
            if (at >= 0) {
                statusBarContainer.getChildren().set(at, item);
            } else {
                statusBarContainer.getChildren().add(item);
            }
        });
    }

    @Override
    public void removeStatusBarItem(String pluginId, String itemId) {
        Platform.runLater(() -> {
            Node item = statusBarItems.remove(pluginId + ":" + itemId);
            if (item != null && statusBarContainer != null) {
                statusBarContainer.getChildren().remove(item);
            }
        });
    }

    @Override
    public void removeStatusBarItems(String pluginId) {
        Platform.runLater(() -> {
            statusBarItems.entrySet().removeIf(entry -> {
                if (!entry.getKey().startsWith(pluginId + ":")) {
                    return false;
                }
                if (statusBarContainer != null) {
                    statusBarContainer.getChildren().remove(entry.getValue());
                }
                return true;
            });
        });
    }
}
