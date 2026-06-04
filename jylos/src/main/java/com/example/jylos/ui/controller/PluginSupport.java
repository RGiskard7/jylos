package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginLoader;
import com.example.jylos.plugin.PluginManager;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Loads the core and external plugins and registers them with the {@link PluginManager}.
 */
class PluginLifecycle {

    private static final Logger logger = LoggerConfig.getLogger(PluginLifecycle.class);

    record LoadResult(int registeredCount, List<String> loadFailures) {
    }

    LoadResult registerCoreAndExternalPlugins(PluginManager pluginManager) {
        if (pluginManager == null) {
            return new LoadResult(0, List.of("PluginManager is null"));
        }

        pluginManager.registerPlugin(new com.example.jylos.plugin.mermaid.MermaidPlugin());

        PluginLoader.PluginLoadReport pluginLoadReport = PluginLoader.loadExternalPluginsWithReport();
        int registeredCount = 0;

        for (Plugin plugin : pluginLoadReport.getPlugins()) {
            if (pluginManager.registerPlugin(plugin)) {
                registeredCount++;
            } else {
                logger.warning("Failed to register external plugin: " + plugin.getName());
            }
        }

        return new LoadResult(registeredCount, pluginLoadReport.getFailures());
    }

    void subscribePluginUiEvents(EventBus eventBus, Runnable refreshListsAction) {
        if (eventBus == null) {
            return;
        }

        eventBus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, event -> {
            if (refreshListsAction != null) {
                refreshListsAction.run();
            }
            logger.info("Refreshed notes from plugin request");
        });
    }
}

/**
 * Dynamic plugin UI registration/removal for menus, side panels and status bar.
 *
 * <p>Holds its {@link MainController} for i18n/status and the active plugin manager;
 * the menu/panel/status-bar registries it maintains are passed in by the controller
 * that owns the corresponding FXML containers.</p>
 */
class PluginUi {

    private static final Logger logger = LoggerConfig.getLogger(PluginUi.class);

    private final MainController controller;

    PluginUi(MainController controller) {
        this.controller = controller;
    }

    void registerMenuItem(String pluginId, String category, String itemName, String shortcut, Runnable action,
            ToolbarController toolbarController,
            Map<String, Menu> pluginCategoryMenus,
            Map<String, List<MenuItem>> pluginMenuItems) {
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
                PluginManager pluginManager = controller.getPluginManager();
                if (pluginManager != null && pluginManager.isPluginEnabled(pluginId)) {
                    action.run();
                } else {
                    controller.updateStatus(
                            MessageFormat.format(controller.getString("status.plugin_not_enabled"), pluginId));
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

    void addMenuSeparator(String pluginId, String category,
            Map<String, Menu> pluginCategoryMenus,
            Map<String, List<MenuItem>> pluginMenuItems) {
        Platform.runLater(() -> {
            Menu categoryMenu = pluginCategoryMenus.get(category);
            if (categoryMenu != null) {
                SeparatorMenuItem separator = new SeparatorMenuItem();
                categoryMenu.getItems().add(separator);
                pluginMenuItems.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(separator);
            }
        });
    }

    void removePluginMenuItems(String pluginId,
            ToolbarController toolbarController,
            Map<String, Menu> pluginCategoryMenus,
            Map<String, List<MenuItem>> pluginMenuItems) {
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

    void registerSidePanel(String pluginId, String panelId, String title, Node content, String icon,
            VBox pluginPanelsContainer,
            Map<String, VBox> pluginPanels,
            Map<String, List<String>> pluginPanelIds) {
        Platform.runLater(() -> {
            if (pluginPanelsContainer == null) {
                logger.warning("Plugin panels container not available for registration: " + panelId);
                return;
            }

            String fullPanelId = pluginId + ":" + panelId;

            // Mirror the app's native collapsible section (e.g. "Note Info") so plugin
            // panels look identical to the rest of the right panel — no inline styles.
            VBox panelWrapper = new VBox();
            panelWrapper.getStyleClass().addAll("panel-section", "plugin-panel");

            HBox header = new HBox(6);
            header.getStyleClass().add("section-header");
            header.setAlignment(Pos.CENTER_LEFT);

            Label collapseIcon = new Label("▼");
            collapseIcon.getStyleClass().add("collapse-icon");

            header.getChildren().add(collapseIcon);

            // The icon may be an Ikonli literal (e.g. "fth-calendar") → render it as a
            // real icon; otherwise treat it as an emoji/text prefix on the title.
            String iconLiteral = icon != null ? icon.trim() : "";
            boolean rendered = false;
            if (iconLiteral.matches("[a-z]{2,5}-[a-z0-9-]+")) {
                try {
                    org.kordamp.ikonli.javafx.FontIcon fontIcon =
                            new org.kordamp.ikonli.javafx.FontIcon(iconLiteral);
                    fontIcon.getStyleClass().add("feather-icon");
                    header.getChildren().add(fontIcon);
                    rendered = true;
                } catch (Exception ignored) {
                    // Not a known icon literal — fall back to text below.
                }
            }

            String headerText = (!rendered && !iconLiteral.isEmpty() ? iconLiteral + " " : "") + title;
            Label titleLabel = new Label(headerText);
            titleLabel.getStyleClass().add("section-title");

            header.getChildren().add(titleLabel);

            VBox contentWrapper = new VBox();
            contentWrapper.getStyleClass().add("section-content");
            contentWrapper.getChildren().add(content);

            header.setOnMouseClicked(e -> {
                boolean expand = !contentWrapper.isVisible();
                contentWrapper.setVisible(expand);
                contentWrapper.setManaged(expand);
                collapseIcon.setText(expand ? "▼" : "▶");
            });

            panelWrapper.getChildren().addAll(header, contentWrapper);
            pluginPanelsContainer.getChildren().add(panelWrapper);
            pluginPanels.put(fullPanelId, panelWrapper);
            pluginPanelIds.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(fullPanelId);

            pluginPanelsContainer.setVisible(true);
            pluginPanelsContainer.setManaged(true);

            logger.fine("Registered side panel: " + title + " for plugin " + pluginId);
        });
    }

    void removeSidePanel(String pluginId, String panelId,
            VBox pluginPanelsContainer,
            Map<String, VBox> pluginPanels,
            Map<String, List<String>> pluginPanelIds) {
        Platform.runLater(() -> {
            String fullPanelId = pluginId + ":" + panelId;
            VBox panel = pluginPanels.remove(fullPanelId);
            if (panel == null || pluginPanelsContainer == null) {
                return;
            }

            pluginPanelsContainer.getChildren().remove(panel);
            List<String> ids = pluginPanelIds.get(pluginId);
            if (ids != null) {
                ids.remove(fullPanelId);
            }

            if (pluginPanelsContainer.getChildren().isEmpty()) {
                pluginPanelsContainer.setVisible(false);
                pluginPanelsContainer.setManaged(false);
            }

            logger.info("Removed side panel: " + panelId);
        });
    }

    void removeAllSidePanels(String pluginId,
            VBox pluginPanelsContainer,
            Map<String, VBox> pluginPanels,
            Map<String, List<String>> pluginPanelIds) {
        Platform.runLater(() -> {
            List<String> ids = pluginPanelIds.remove(pluginId);
            if (ids == null || pluginPanelsContainer == null) {
                return;
            }

            for (String fullPanelId : ids) {
                VBox panel = pluginPanels.remove(fullPanelId);
                if (panel != null) {
                    pluginPanelsContainer.getChildren().remove(panel);
                }
            }

            if (pluginPanelsContainer.getChildren().isEmpty()) {
                pluginPanelsContainer.setVisible(false);
                pluginPanelsContainer.setManaged(false);
            }

            logger.info("Removed all side panels for plugin: " + pluginId);
        });
    }

    void setPluginPanelsVisible(boolean visible, VBox pluginPanelsContainer) {
        Platform.runLater(() -> {
            if (pluginPanelsContainer != null) {
                pluginPanelsContainer.setVisible(visible);
                pluginPanelsContainer.setManaged(visible);
            }
        });
    }

    boolean isPluginPanelsVisible(VBox pluginPanelsContainer) {
        return pluginPanelsContainer != null && pluginPanelsContainer.isVisible();
    }

    void registerStatusBarItem(String pluginId, String itemId, Node content,
            HBox pluginStatusBarContainer,
            Map<String, Node> pluginStatusBarItems,
            Map<String, List<String>> pluginStatusBarItemIds) {
        Platform.runLater(() -> {
            if (pluginStatusBarContainer == null) {
                logger.warning("Status bar container not available for: " + itemId);
                return;
            }

            String fullItemId = pluginId + ":" + itemId;
            HBox wrapper = new HBox(8);
            wrapper.setAlignment(Pos.CENTER_LEFT);

            Separator sep = new Separator();
            sep.setOrientation(Orientation.VERTICAL);
            sep.getStyleClass().add("status-separator");

            wrapper.getChildren().addAll(sep, content);
            pluginStatusBarContainer.getChildren().add(wrapper);
            pluginStatusBarItems.put(fullItemId, wrapper);
            pluginStatusBarItemIds.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(fullItemId);

            logger.fine("Registered status bar item: " + itemId + " for plugin " + pluginId);
        });
    }

    void removeStatusBarItem(String pluginId, String itemId,
            HBox pluginStatusBarContainer,
            Map<String, Node> pluginStatusBarItems,
            Map<String, List<String>> pluginStatusBarItemIds) {
        Platform.runLater(() -> {
            String fullItemId = pluginId + ":" + itemId;
            Node item = pluginStatusBarItems.remove(fullItemId);
            if (item == null || pluginStatusBarContainer == null) {
                return;
            }

            pluginStatusBarContainer.getChildren().remove(item);
            List<String> ids = pluginStatusBarItemIds.get(pluginId);
            if (ids != null) {
                ids.remove(fullItemId);
            }
        });
    }

    void updateStatusBarItem(String pluginId, String itemId, Node content,
            Map<String, Node> pluginStatusBarItems) {
        Platform.runLater(() -> {
            String fullItemId = pluginId + ":" + itemId;
            Node wrapper = pluginStatusBarItems.get(fullItemId);
            if (wrapper instanceof HBox box && box.getChildren().size() > 1) {
                box.getChildren().set(1, content);
            }
        });
    }

    void removeAllStatusBarItems(String pluginId,
            HBox pluginStatusBarContainer,
            Map<String, Node> pluginStatusBarItems,
            Map<String, List<String>> pluginStatusBarItemIds) {
        Platform.runLater(() -> {
            List<String> ids = pluginStatusBarItemIds.remove(pluginId);
            if (ids == null || pluginStatusBarContainer == null) {
                return;
            }

            for (String fullItemId : ids) {
                Node item = pluginStatusBarItems.remove(fullItemId);
                if (item != null) {
                    pluginStatusBarContainer.getChildren().remove(item);
                }
            }
        });
    }
}
