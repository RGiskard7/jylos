package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.AppContext;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.ui.components.CommandPalette;
import com.example.jylos.ui.components.QuickSwitcher;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Command routing table with stable IDs and backward-compatible aliases.
 */
class CommandRouting {

    record DispatchResult(boolean handled, String resolvedToken) {
    }

    private final Map<String, Runnable> routes = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    boolean isEmpty() {
        return routes.isEmpty();
    }

    void registerRoute(String id, String legacyName, Runnable action) {
        routes.put(id, action);
        aliases.put(id, id);
        if (legacyName != null && !legacyName.isEmpty()) {
            aliases.put(legacyName, id);
        }
    }

    void registerAlias(String alias, String commandId) {
        if (alias == null || alias.isEmpty() || commandId == null || commandId.isEmpty()) {
            return;
        }
        aliases.put(alias, commandId);
    }

    String resolveToken(String commandToken) {
        if (commandToken == null) {
            return "";
        }
        return aliases.getOrDefault(commandToken, commandToken);
    }

    DispatchResult dispatch(String commandToken, Predicate<String> fallbackExecutor) {
        String resolved = resolveToken(commandToken);
        Runnable route = routes.get(resolved);
        if (route != null) {
            route.run();
            return new DispatchResult(true, resolved);
        }

        boolean handledByFallback = false;
        if (fallbackExecutor != null) {
            handledByFallback = fallbackExecutor.test(commandToken)
                    || (!resolved.equals(commandToken) && fallbackExecutor.test(resolved));
        }
        return new DispatchResult(handledByFallback, resolved);
    }
}

/**
 * Catalog of stable command IDs and legacy aliases, registered in one place.
 */
class CommandRegistry {

    @FunctionalInterface
    interface RouteRegistrar {
        void register(String id, String legacyName, Runnable action);
    }

    @FunctionalInterface
    interface AliasRegistrar {
        void registerAlias(String alias, String commandId);
    }

    interface CommandActionProvider {
        Runnable actionFor(String commandId);
    }

    private record CommandDef(String id, String legacyName) {
    }

    private static final List<CommandDef> DEFAULT_COMMANDS = List.of(
            new CommandDef("cmd.new_note", "New Note"),
            new CommandDef("cmd.new_folder", "New Folder"),
            new CommandDef("cmd.save", "Save"),
            new CommandDef("cmd.save_all", "Save All"),
            new CommandDef("cmd.import", "Import"),
            new CommandDef("cmd.export", "Export"),
            new CommandDef("cmd.delete_note", "Delete Note"),
            new CommandDef("cmd.undo", "Undo"),
            new CommandDef("cmd.redo", "Redo"),
            new CommandDef("cmd.find", "Find"),
            new CommandDef("cmd.replace", "Find and Replace"),
            new CommandDef("cmd.cut", "Cut"),
            new CommandDef("cmd.copy", "Copy"),
            new CommandDef("cmd.paste", "Paste"),
            new CommandDef("cmd.bold", "Bold"),
            new CommandDef("cmd.italic", "Italic"),
            new CommandDef("cmd.underline", "Underline"),
            new CommandDef("cmd.insert_link", "Insert Link"),
            new CommandDef("cmd.insert_rich_link", "Insert Rich Link"),
            new CommandDef("cmd.insert_image", "Insert Image"),
            new CommandDef("cmd.insert_todo", "Insert Todo"),
            new CommandDef("cmd.insert_list", "Insert List"),
            new CommandDef("cmd.toggle_sidebar", "Toggle Sidebar"),
            new CommandDef("cmd.graph_view", "Graph View"),
            new CommandDef("cmd.knowledge_insights", "Knowledge Insights"),
            new CommandDef("cmd.workspace_save", "Workspace: Save Current"),
            new CommandDef("cmd.workspace_save_as", "Workspace: Save As…"),
            new CommandDef("cmd.workspace_open", "Workspace: Open…"),
            new CommandDef("cmd.workspace_manage", "Workspace: Manage…"),
            new CommandDef("cmd.git_panel", "Git: Sync Panel"),
            new CommandDef("cmd.git_sync", "Git: Synchronize"),
            new CommandDef("cmd.git_commit_push", "Git: Commit & Push"),
            new CommandDef("cmd.git_pull", "Git: Pull"),
            new CommandDef("cmd.git_init", "Git: Initialize"),
            new CommandDef("cmd.git_add_remote", "Git: Add Remote"),
            new CommandDef("cmd.toggle_info_panel", "Toggle Info Panel"),
            new CommandDef("cmd.editor_mode", "Editor Mode"),
            new CommandDef("cmd.preview_mode", "Preview Mode"),
            new CommandDef("cmd.split_mode", "Split Mode"),
            new CommandDef("cmd.zoom_in", "Zoom In"),
            new CommandDef("cmd.zoom_out", "Zoom Out"),
            new CommandDef("cmd.reset_zoom", "Reset Zoom"),
            new CommandDef("cmd.theme_light", "Light Theme"),
            new CommandDef("cmd.theme_dark", "Dark Theme"),
            new CommandDef("cmd.theme_system", "System Theme"),
            new CommandDef("cmd.quick_switcher", "Quick Switcher"),
            new CommandDef("cmd.global_search", "Global Search"),
            new CommandDef("cmd.daily_note", "Open Today's Daily Note"),
            new CommandDef("cmd.new_from_template", "New Note from Template…"),
            new CommandDef("cmd.export_vault", "Export All Notes (PDF/HTML)…"),
            new CommandDef("cmd.goto_all_notes", "Go to All Notes"),
            new CommandDef("cmd.goto_favorites", "Go to Favorites"),
            new CommandDef("cmd.goto_recent", "Go to Recent"),
            new CommandDef("cmd.tag_manager", "Tag Manager"),
            new CommandDef("cmd.preferences", "Preferences"),
            new CommandDef("cmd.toggle_favorite", "Toggle Favorite"),
            new CommandDef("cmd.refresh", "Refresh"),
            new CommandDef("cmd.plugins.manage", "Plugins: Manage Plugins"),
            new CommandDef("cmd.keyboard_shortcuts", "Keyboard Shortcuts"),
            new CommandDef("cmd.documentation", "Documentation"),
            new CommandDef("cmd.about", "About Jylos"));

    void registerDefaultRoutes(RouteRegistrar routeRegistrar, AliasRegistrar aliasRegistrar,
            CommandActionProvider actionProvider) {
        if (routeRegistrar == null || actionProvider == null) {
            return;
        }

        for (CommandDef def : DEFAULT_COMMANDS) {
            Runnable action = actionProvider.actionFor(def.id());
            if (action != null) {
                routeRegistrar.register(def.id(), def.legacyName(), action);
            }
        }

        if (aliasRegistrar != null) {
            aliasRegistrar.registerAlias("Toggle Right Panel", "cmd.toggle_info_panel");
        }
    }
}

/**
 * Command palette / quick switcher creation and global keyboard shortcuts.
 */
class CommandUI {

    private static final Logger logger = LoggerConfig.getLogger(CommandUI.class);

    record CommandUiComponents(CommandPalette commandPalette, QuickSwitcher quickSwitcher) {
    }

    CommandUiComponents ensureCommandUiComponents(
            Stage stage,
            CommandPalette existingPalette,
            QuickSwitcher existingSwitcher,
            Consumer<String> commandHandler,
            Consumer<Note> noteSelectionHandler) {
        if (stage == null) {
            return new CommandUiComponents(existingPalette, existingSwitcher);
        }

        CommandPalette palette = existingPalette;
        if (palette == null) {
            palette = new CommandPalette(stage);
            if (commandHandler != null) {
                palette.setCommandHandler(commandHandler);
            }
        }

        QuickSwitcher switcher = existingSwitcher;
        if (switcher == null) {
            switcher = new QuickSwitcher(stage);
            if (noteSelectionHandler != null) {
                switcher.setOnNoteSelected(noteSelectionHandler);
            }
        }

        return new CommandUiComponents(palette, switcher);
    }

    void initializeKeyboardShortcuts(Scene scene, Runnable openPaletteAction, Runnable openQuickSwitcherAction) {
        if (scene == null) {
            logger.warning("Scene not available for keyboard shortcuts");
            return;
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case P:
                        if (openPaletteAction != null) {
                            openPaletteAction.run();
                            event.consume();
                        }
                        break;
                    case O:
                        if (openQuickSwitcherAction != null) {
                            openQuickSwitcherAction.run();
                            event.consume();
                        }
                        break;
                    default:
                        break;
                }
            }
        });

        logger.info("Keyboard shortcuts initialized (Ctrl+P: Command Palette, Ctrl+O: Quick Switcher)");
    }
}

/**
 * Navigation, layout-toggle and refresh commands.
 *
 * <p>Holds its {@link MainController} for i18n strings and status updates; node
 * references are passed in by the controller that owns them.</p>
 */
class NavigationCommand {

    private static final Logger logger = LoggerConfig.getLogger(NavigationCommand.class);

    private final MainController controller;

    NavigationCommand(MainController controller) {
        this.controller = controller;
    }

    private String i18n(String key) {
        return controller.getString(key);
    }

    boolean handleSearch(ToolbarController toolbarController) {
        if (toolbarController == null || toolbarController.getSearchField() == null) {
            return false;
        }
        toolbarController.getSearchField().requestFocus();
        toolbarController.getSearchField().selectAll();
        controller.updateStatus(i18n("status.search_focused"));
        return true;
    }

    boolean toggleSidebar(boolean isStackedLayout, SplitPane navSplitPane, VBox sidebarPane,
            SplitPane mainSplitPane, ToolbarController toolbarController) {
        if (isStackedLayout) {
            if (navSplitPane == null) {
                return false;
            }
            boolean isCollapsed = navSplitPane.getMaxWidth() < 10;
            if (isCollapsed) {
                navSplitPane.setMinWidth(200);
                navSplitPane.setMaxWidth(Double.MAX_VALUE);
                navSplitPane.setPrefWidth(300);
                if (mainSplitPane != null) {
                    mainSplitPane.setDividerPositions(0.25);
                }
                controller.updateStatus(i18n("status.nav_shown"));
                setSidebarToggle(toolbarController, true);
            } else {
                navSplitPane.setMinWidth(0);
                navSplitPane.setMaxWidth(0);
                navSplitPane.setPrefWidth(0);
                controller.updateStatus(i18n("status.nav_hidden"));
                setSidebarToggle(toolbarController, false);
            }
            return true;
        }

        if (sidebarPane == null) {
            return false;
        }
        boolean isCollapsed = sidebarPane.getMaxWidth() < 10;
        if (isCollapsed) {
            sidebarPane.setMinWidth(200);
            sidebarPane.setMaxWidth(Double.MAX_VALUE);
            sidebarPane.setPrefWidth(250);
            if (mainSplitPane != null) {
                mainSplitPane.setDividerPositions(0.22);
            }
            controller.updateStatus(i18n("status.sidebar_shown"));
            setSidebarToggle(toolbarController, true);
        } else {
            sidebarPane.setMinWidth(0);
            sidebarPane.setMaxWidth(0);
            sidebarPane.setPrefWidth(0);
            controller.updateStatus(i18n("status.sidebar_hidden"));
            setSidebarToggle(toolbarController, false);
        }
        return true;
    }

    boolean toggleNotesPanel(boolean isStackedLayout, VBox notesPanel, SplitPane contentSplitPane,
            ToolbarController toolbarController, Runnable sidebarFallback) {
        if (isStackedLayout) {
            if (sidebarFallback != null) {
                sidebarFallback.run();
                return true;
            }
            return false;
        }

        if (notesPanel == null) {
            return false;
        }

        boolean isCollapsed = notesPanel.getMaxWidth() < 10;
        if (isCollapsed) {
            notesPanel.setMinWidth(180);
            notesPanel.setMaxWidth(Double.MAX_VALUE);
            notesPanel.setPrefWidth(260);
            if (contentSplitPane != null) {
                contentSplitPane.setDividerPositions(0.25);
            }
            controller.updateStatus(i18n("status.notes_panel_shown"));
            setNotesToggle(toolbarController, true);
        } else {
            notesPanel.setMinWidth(0);
            notesPanel.setMaxWidth(0);
            notesPanel.setPrefWidth(0);
            controller.updateStatus(i18n("status.notes_panel_hidden"));
            setNotesToggle(toolbarController, false);
        }

        return true;
    }

    double zoomIn(double currentUiFontSize) {
        return currentUiFontSize + 1.0;
    }

    double zoomOut(double currentUiFontSize) {
        if (currentUiFontSize > 8.0) {
            return currentUiFontSize - 1.0;
        }
        return currentUiFontSize;
    }

    double resetUiZoom() {
        return 13.0;
    }

    void refreshByContext(String currentFilterType, Folder currentFolder, Tag currentTag,
            ListView<Note> notesListView, Label noteCountLabel,
            Runnable refreshNotesListAction, Consumer<Folder> folderSelectionAction,
            Consumer<String> loadNotesForTagAction, Function<String, String> searchTextProvider,
            Consumer<String> performSearchAction, Consumer<String> onContextRefreshed) {
        try {
            switch (currentFilterType) {
                case "folder":
                    if (currentFolder != null && folderSelectionAction != null) {
                        folderSelectionAction.accept(currentFolder);
                    } else if (refreshNotesListAction != null) {
                        refreshNotesListAction.run();
                    }
                    break;
                case "tag":
                    if (currentTag != null && loadNotesForTagAction != null) {
                        loadNotesForTagAction.accept(currentTag.getTitle());
                    } else if (refreshNotesListAction != null) {
                        refreshNotesListAction.run();
                    }
                    break;
                case "favorites":
                    List<Note> favoriteNotes = AppContext.getNoteService().getAllNotes().stream()
                            .filter(Note::isFavorite).toList();
                    notesListView.getSelectionModel().clearSelection();
                    notesListView.getItems().setAll(favoriteNotes);
                    if (noteCountLabel != null) {
                        noteCountLabel.setText(
                                MessageFormat.format(i18n("info.favorite_notes_count"), favoriteNotes.size()));
                    }
                    if (onContextRefreshed != null) {
                        onContextRefreshed.accept(i18n("status.favs_refreshed"));
                    }
                    break;
                case "search":
                    String searchText = searchTextProvider != null ? searchTextProvider.apply(currentFilterType) : "";
                    if (searchText != null && !searchText.trim().isEmpty() && performSearchAction != null) {
                        performSearchAction.accept(searchText);
                    } else if (refreshNotesListAction != null) {
                        refreshNotesListAction.run();
                    }
                    break;
                default:
                    if (refreshNotesListAction != null) {
                        refreshNotesListAction.run();
                    }
                    break;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to refresh", e);
            controller.updateStatus(i18n("status.refresh_error"));
        }
    }

    boolean switchLayout(boolean currentStackedLayout, SplitPane mainSplitPane, SplitPane contentSplitPane,
            SplitPane navSplitPane, VBox sidebarPane, VBox notesPanel, VBox editorContainer,
            ToolbarController toolbarController) {
        if (mainSplitPane == null || contentSplitPane == null || navSplitPane == null || sidebarPane == null
                || notesPanel == null || editorContainer == null) {
            return currentStackedLayout;
        }

        boolean isStackedLayout = !currentStackedLayout;

        mainSplitPane.getItems().clear();
        contentSplitPane.getItems().clear();
        navSplitPane.getItems().clear();

        sidebarPane.setMinWidth(200);
        sidebarPane.setMaxWidth(Double.MAX_VALUE);
        notesPanel.setMinWidth(180);
        notesPanel.setMaxWidth(Double.MAX_VALUE);

        if (isStackedLayout) {
            navSplitPane.getItems().addAll(sidebarPane, notesPanel);
            navSplitPane.setDividerPositions(0.5);

            mainSplitPane.getItems().addAll(navSplitPane, editorContainer);
            mainSplitPane.setDividerPositions(0.25);
            controller.updateStatus(i18n("status.layout_stacked"));
        } else {
            contentSplitPane.getItems().addAll(notesPanel, editorContainer);
            contentSplitPane.setDividerPositions(0.3);

            mainSplitPane.getItems().addAll(sidebarPane, contentSplitPane);
            mainSplitPane.setDividerPositions(0.22);
            controller.updateStatus(i18n("status.layout_column"));
        }

        if (toolbarController != null && toolbarController.getSidebarToggleBtn() != null) {
            toolbarController.getSidebarToggleBtn().setSelected(true);
        }
        if (toolbarController != null && toolbarController.getNotesPanelToggleBtn() != null) {
            toolbarController.getNotesPanelToggleBtn().setSelected(true);
        }

        return isStackedLayout;
    }

    private void setSidebarToggle(ToolbarController toolbarController, boolean selected) {
        if (toolbarController != null && toolbarController.getSidebarToggleBtn() != null) {
            toolbarController.getSidebarToggleBtn().setSelected(selected);
        }
    }

    private void setNotesToggle(ToolbarController toolbarController, boolean selected) {
        if (toolbarController != null && toolbarController.getNotesPanelToggleBtn() != null) {
            toolbarController.getNotesPanelToggleBtn().setSelected(selected);
        }
    }
}
