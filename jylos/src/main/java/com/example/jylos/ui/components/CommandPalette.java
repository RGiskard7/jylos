package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import java.util.ResourceBundle;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.util.FuzzySearchUtils;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Command Palette component - similar to Obsidian/VS Code.
 * Provides quick access to all application commands via keyboard.
 * Uses universal text symbols for cross-platform compatibility.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public class CommandPalette {
    
    private static final Logger logger = LoggerConfig.getLogger(CommandPalette.class);
    
    private final Stage parentStage;
    private Stage paletteStage;
    private TextField searchField;
    private ListView<Command> commandListView;
    private final List<Command> commands = new ArrayList<>();
    private Consumer<String> commandHandler;
    private ResourceBundle bundle;
    
    /**
     * Creates a new Command Palette.
     * 
     * @param parentStage The parent stage to center the palette on
     */
    public CommandPalette(Stage parentStage) {
        this.parentStage = parentStage;
        initializeDefaultCommands();
    }

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /** Resolves an i18n string, falling back to the key if the bundle is unavailable. */
    private String i18n(String key) {
        try {
            return bundle != null ? bundle.getString(key) : key;
        } catch (Exception e) {
            return key;
        }
    }
    
    /**
     * Represents a command in the palette.
     */
    public static class Command {
        private final String id;
        private final String name;
        private final String description;
        private final String shortcut;
        private final String icon;
        private final String category;
        private Runnable action;
        
        public Command(String name, String description, String shortcut, Runnable action) {
            this(name, name, description, shortcut, ">", "General", action);
        }
        
        public Command(String name, String description, String shortcut, String icon, String category, Runnable action) {
            this(name, name, description, shortcut, icon, category, action);
        }

        public Command(String id, String name, String description, String shortcut, String icon, String category,
                Runnable action) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.shortcut = shortcut;
            this.icon = icon;
            this.category = category;
            this.action = action;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getShortcut() { return shortcut; }
        public String getIcon() { return icon; }
        public String getCategory() { return category; }
        public String getDisplayName(ResourceBundle bundle) {
            return i18n(bundle, "palette.command." + id + ".name", name);
        }
        public String getDisplayDescription(ResourceBundle bundle) {
            return i18n(bundle, "palette.command." + id + ".desc", description);
        }
        public String getDisplayCategory(ResourceBundle bundle) {
            if (category == null || category.isBlank()) {
                return "";
            }
            return i18n(bundle, "palette.category." + category.toLowerCase(java.util.Locale.ROOT), category);
        }
        public void setAction(Runnable action) { this.action = action; }
        public void execute() { if (action != null) action.run(); }

        private static String i18n(ResourceBundle bundle, String key, String fallback) {
            try {
                return bundle != null && key != null ? bundle.getString(key) : fallback;
            } catch (Exception e) {
                return fallback;
            }
        }
    }
    
    /**
     * Adds a command to the palette.
     */
    public void addCommand(Command command) {
        commands.add(command);
    }
    
    /**
     * Removes a command from the palette.
     */
    public void removeCommand(String commandName) {
        commands.removeIf(c -> c.getName().equals(commandName));
    }

    /**
     * Removes a command by its stable id.
     */
    public void removeCommandById(String commandId) {
        if (commandId == null) {
            return;
        }
        commands.removeIf(c -> commandId.equals(c.getId()));
    }
    
    /**
     * Finds a command by name.
     * 
     * @param commandName The name of the command to find
     * @return The command, or null if not found
     */
    public Command findCommand(String commandName) {
        return commands.stream()
            .filter(c -> c.getName().equals(commandName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Finds a command by internal ID.
     *
     * @param commandId The internal command id
     * @return The command, or null if not found
     */
    public Command findCommandById(String commandId) {
        return commands.stream()
            .filter(c -> c.getId().equals(commandId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Clears all commands.
     */
    public void clearCommands() {
        commands.clear();
    }
    
    /**
     * Initialize default commands with universal icons.
     */
    private void initializeDefaultCommands() {
        // File commands - using simple ASCII/text symbols
        commands.add(new Command("cmd.new_note", "New Note", "Create a new note", "Ctrl+N", "+", "File", null));
        commands.add(new Command("cmd.new_canvas", "New Canvas", "Create a new canvas", "", "#", "File", null));
        commands.add(
                new Command("cmd.new_folder", "New Folder", "Create a new folder", "Ctrl+Shift+N", "+", "File", null));
        commands.add(new Command("cmd.save", "Save", "Save current note", "Ctrl+S", "*", "File", null));
        commands.add(new Command("cmd.save_all", "Save All", "Save all notes", "Ctrl+Shift+S", "*", "File", null));
        commands.add(new Command("cmd.import", "Import", "Import notes from files", "Ctrl+I", "<", "File", null));
        commands.add(new Command("cmd.export", "Export", "Export current note", "Ctrl+E", ">", "File", null));
        commands.add(new Command("cmd.delete_note", "Delete Note", "Delete current note", "Del", "x", "File", null));
        
        // Edit commands
        commands.add(new Command("cmd.undo", "Undo", "Undo last action", "Ctrl+Z", "<", "Edit", null));
        commands.add(new Command("cmd.redo", "Redo", "Redo last action", "Ctrl+Y", ">", "Edit", null));
        commands.add(new Command("cmd.find", "Find", "Find text in note", "Ctrl+F", "?", "Edit", null));
        commands.add(new Command("cmd.replace", "Find and Replace", "Find and replace text", "Ctrl+H", "~", "Edit",
                null));
        commands.add(new Command("cmd.cut", "Cut", "Cut selection", "Ctrl+X", "x", "Edit", null));
        commands.add(new Command("cmd.copy", "Copy", "Copy selection", "Ctrl+C", "=", "Edit", null));
        commands.add(new Command("cmd.paste", "Paste", "Paste from clipboard", "Ctrl+V", "v", "Edit", null));
        
        // Format commands
        commands.add(new Command("cmd.bold", "Bold", "Make text bold", "Ctrl+B", "B", "Format", null));
        commands.add(new Command("cmd.italic", "Italic", "Make text italic", "Ctrl+I", "I", "Format", null));
        commands.add(new Command("cmd.underline", "Underline", "Underline text", "Ctrl+U", "U", "Format", null));
        commands.add(new Command("cmd.insert_link", "Insert Link", "Insert a hyperlink", "Ctrl+K", "@", "Format",
                null));
        commands.add(new Command("cmd.insert_rich_link", "Insert Rich Link",
                "Insert a URL as a visual card", null, "@", "Format", null));
        commands.add(new Command("cmd.insert_image", "Insert Image", "Insert an image", null, "#", "Format", null));
        commands.add(new Command("cmd.insert_todo", "Insert Todo", "Insert a todo item", null, "[]", "Format", null));
        commands.add(
                new Command("cmd.insert_list", "Insert List", "Insert numbered list", null, "1.", "Format", null));
        
        // View commands
        commands.add(
                new Command("cmd.toggle_sidebar", "Toggle Sidebar", "Show/hide sidebar", "F9", "|", "View", null));
        commands.add(new Command("cmd.graph_view", "Graph View",
                "Open the knowledge graph", "Ctrl+G", "\u2b21", "View", null));
        commands.add(new Command("cmd.knowledge_insights", "Knowledge Insights",
                "Analyze orphans, broken links, connections, tags and graph health",
                "Ctrl+Shift+K", "\u2261", "View", null));
        commands.add(new Command("cmd.workspace_save", "Workspace: Save Current",
                "Save the current tabs and layout to the active workspace", null, "\u25a3", "Workspace", null));
        commands.add(new Command("cmd.workspace_save_as", "Workspace: Save As\u2026",
                "Save the current tabs and layout under a new name", null, "\u25a3", "Workspace", null));
        commands.add(new Command("cmd.workspace_open", "Workspace: Open\u2026",
                "Open a saved workspace", null, "\u25a4", "Workspace", null));
        commands.add(new Command("cmd.workspace_manage", "Workspace: Manage\u2026",
                "Open or delete saved workspaces", null, "\u25a4", "Workspace", null));
        commands.add(new Command("cmd.git_panel", "Git: Sync Panel",
                "Open the Git Sync panel (status, changes, commit, pull & push)", "Ctrl+Shift+G",
                "\u2387", "Git", null));
        commands.add(new Command("cmd.git_sync", "Git: Synchronize",
                "Commit, pull and push the vault", "Ctrl+Shift+S", "\u21bb", "Git", null));
        commands.add(new Command("cmd.git_commit_push", "Git: Commit & Push",
                "Commit local changes and push", null, "\u2191", "Git", null));
        commands.add(new Command("cmd.git_pull", "Git: Pull",
                "Pull changes from the remote", null, "\u2193", "Git", null));
        commands.add(new Command("cmd.git_init", "Git: Initialize",
                "Initialize Git in the current vault", null, "\u2387", "Git", null));
        commands.add(new Command("cmd.git_add_remote", "Git: Add Remote",
                "Set the vault's remote URL", null, "\u2197", "Git", null));
        commands.add(new Command("cmd.toggle_info_panel", "Toggle Info Panel", "Show/hide info panel",
                "Ctrl+Shift+I", "i", "View", null));
        commands.add(new Command("cmd.editor_mode", "Editor Mode", "Show only editor", null, "E", "View", null));
        commands.add(new Command("cmd.preview_mode", "Preview Mode", "Show only preview", null, "P", "View", null));
        commands.add(
                new Command("cmd.split_mode", "Split Mode", "Show editor and preview", null, "||", "View", null));
        commands.add(new Command("cmd.zoom_in", "Zoom In", "Increase text size", "Ctrl++", "+", "View", null));
        commands.add(new Command("cmd.zoom_out", "Zoom Out", "Decrease text size", "Ctrl+-", "-", "View", null));
        commands.add(
                new Command("cmd.reset_zoom", "Reset Zoom", "Reset text size to 100%", "Ctrl+0", "0", "View", null));
        
        // Theme commands
        commands.add(new Command("cmd.theme_light", "Light Theme", "Switch to light theme", null, "O", "Theme", null));
        commands.add(new Command("cmd.theme_dark", "Dark Theme", "Switch to dark theme", null, "*", "Theme", null));
        commands.add(
                new Command("cmd.theme_system", "System Theme", "Use system theme", null, "S", "Theme", null));
        
        // Navigation commands
        commands.add(new Command("cmd.quick_switcher", "Quick Switcher", "Switch between notes", "Ctrl+O", "/",
                "Navigation", null));
        commands.add(new Command("cmd.global_search", "Global Search", "Search all notes", "Ctrl+Shift+F", "?",
                "Navigation", null));
        commands.add(
                new Command("cmd.goto_all_notes", "Go to All Notes", "Show all notes", null, "*", "Navigation", null));
        commands.add(new Command("cmd.goto_favorites", "Go to Favorites", "Show favorite notes", null, "*",
                "Navigation", null));
        commands.add(
                new Command("cmd.goto_recent", "Go to Recent", "Show recent notes", null, "~", "Navigation", null));
        
        // Tools commands
        commands.add(new Command("cmd.tag_manager", "Tag Manager", "Manage tags", null, "#", "Tools", null));
        commands.add(
                new Command("cmd.preferences", "Preferences", "Open settings", "Ctrl+,", "=", "Tools", null));
        commands.add(new Command("cmd.toggle_favorite", "Toggle Favorite", "Mark/unmark as favorite", null, "*",
                "Tools", null));
        commands.add(new Command("cmd.refresh", "Refresh", "Refresh current view", "F5", "~", "Tools", null));
        
        // Help commands
        commands.add(new Command("cmd.keyboard_shortcuts", "Keyboard Shortcuts", "View all shortcuts", "F1", "?",
                "Help", null));
        commands.add(
                new Command("cmd.documentation", "Documentation", "View user guide", null, "?", "Help", null));
        commands.add(new Command("cmd.about", "About Jylos", "About this application", null, "i", "Help",
                null));
    }
    
    /**
     * Sets the action handler for commands.
     */
    public void setCommandHandler(Consumer<String> handler) {
        this.commandHandler = handler;
        // Update all commands to use this handler
        for (Command cmd : commands) {
            final String cmdToken = cmd.getId() != null ? cmd.getId() : cmd.getName();
            cmd.setAction(() -> {
                if (commandHandler != null) {
                    commandHandler.accept(cmdToken);
                }
            });
        }
    }
    
    /**
     * Shows the Command Palette.
     */
    public void show() {
        syncWithParentStageBounds();
        if (paletteStage != null && paletteStage.isShowing()) {
            paletteStage.toFront();
            searchField.requestFocus();
            return;
        }
        
        createPaletteStage();
        syncWithParentStageBounds();
        paletteStage.show();
        paletteStage.toFront();
        paletteStage.requestFocus();
        animateEntrance();
        
        Platform.runLater(() -> {
            searchField.requestFocus();
            searchField.selectAll();
            commandListView.getItems().setAll(commands);
            if (!commands.isEmpty()) {
                commandListView.getSelectionModel().selectFirst();
            }
        });
    }
    
    /**
     * Hides the Command Palette.
     */
    public void hide() {
        if (paletteStage != null && paletteStage.isShowing()) {
            animateExit(() -> paletteStage.hide());
        }
    }
    
    /**
     * Creates the palette stage and UI.
     */
    private void createPaletteStage() {
        paletteStage = new Stage();
        paletteStage.initOwner(parentStage);
        paletteStage.initModality(Modality.APPLICATION_MODAL);
        paletteStage.initStyle(StageStyle.TRANSPARENT);

        VBox mainContainer = new VBox(0);
        mainContainer.setMaxWidth(600);
        mainContainer.setMaxHeight(450);
        mainContainer.getStyleClass().addAll("overlay-panel", "command-palette-panel");
        
        HBox searchContainer = new HBox(12);
        searchContainer.setAlignment(Pos.CENTER_LEFT);
        searchContainer.setPadding(new Insets(16, 20, 16, 20));
        searchContainer.getStyleClass().add("overlay-search-row");
        
        Label searchIcon = new Label(">");
        searchIcon.getStyleClass().add("overlay-search-icon");
        
        searchField = new TextField();
        searchField.setPromptText(i18n("palette.search_placeholder"));
        searchField.getStyleClass().add("overlay-search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        Label shortcutHint = new Label(i18n("palette.hint.close"));
        shortcutHint.getStyleClass().add("overlay-hint");
        
        searchContainer.getChildren().addAll(searchIcon, searchField, shortcutHint);
        
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.getStyleClass().add("overlay-divider");
        
        commandListView = new ListView<>();
        commandListView.getStyleClass().add("overlay-list-view");
        commandListView.setCellFactory(lv -> createCommandCell());
        commandListView.setFixedCellSize(56);
        VBox.setVgrow(commandListView, Priority.ALWAYS);
        
        HBox footer = new HBox(20);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(10, 16, 10, 16));
        footer.getStyleClass().add("overlay-footer");
        
        Label navHint = new Label(i18n("palette.hint.navigate"));
        navHint.getStyleClass().add("overlay-hint");
        Label selectHint = new Label(i18n("palette.hint.select"));
        selectHint.getStyleClass().add("overlay-hint");
        footer.getChildren().addAll(navHint, selectHint);
        
        mainContainer.getChildren().addAll(searchContainer, separator, commandListView, footer);
        
        StackPane overlay = new StackPane(mainContainer);
        overlay.setAlignment(Pos.TOP_CENTER);
        overlay.setPadding(new Insets(80, 0, 0, 0));
        overlay.getStyleClass().addAll("overlay-backdrop", "command-palette-overlay");
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                hide();
            }
        });
        
        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        UiDialogs.apply(scene);
        paletteStage.setScene(scene);
        
        // Size to match parent
        paletteStage.setWidth(parentStage.getWidth());
        paletteStage.setHeight(parentStage.getHeight());
        paletteStage.setX(parentStage.getX());
        paletteStage.setY(parentStage.getY());
        
        setupEventHandlers();
    }

    /** Resizes and repositions the palette stage to overlay the parent stage, enforcing minimum 900×600 dimensions. */
    private void syncWithParentStageBounds() {
        if (parentStage == null) {
            return;
        }
        if (paletteStage != null) {
            double width = Math.max(parentStage.getWidth(), 900);
            double height = Math.max(parentStage.getHeight(), 600);
            paletteStage.setWidth(width);
            paletteStage.setHeight(height);
            paletteStage.setX(parentStage.getX());
            paletteStage.setY(parentStage.getY());
        }
    }
    
    /**
     * Creates a styled command cell.
     */
    private ListCell<Command> createCommandCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Command command, boolean empty) {
                super.updateItem(command, empty);
                if (!getStyleClass().contains("overlay-list-cell")) {
                    getStyleClass().addAll("overlay-list-cell", "command-palette-cell");
                }
                
                if (empty || command == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox container = new HBox(14);
                    container.setAlignment(Pos.CENTER_LEFT);
                    container.setPadding(new Insets(8, 12, 8, 12));
                    container.getStyleClass().add("overlay-item");
                    
                    Label iconLabel = new Label(command.getIcon() != null ? command.getIcon() : ">");
                    iconLabel.setMinWidth(28);
                    iconLabel.setAlignment(Pos.CENTER);
                    iconLabel.getStyleClass().add("overlay-item-icon");
                    
                    VBox textContainer = new VBox(2);
                    Label nameLabel = new Label(command.getDisplayName(bundle));
                    nameLabel.getStyleClass().add("overlay-item-title");
                    
                    Label descLabel = new Label(command.getDisplayDescription(bundle));
                    descLabel.getStyleClass().add("overlay-item-subtitle");
                    
                    textContainer.getChildren().addAll(nameLabel, descLabel);
                    HBox.setHgrow(textContainer, Priority.ALWAYS);
                    
                    container.getChildren().addAll(iconLabel, textContainer);
                    
                    if (command.getShortcut() != null && !command.getShortcut().isEmpty()) {
                        Label shortcutLabel = new Label(command.getShortcut());
                        shortcutLabel.getStyleClass().add("overlay-shortcut-badge");
                        container.getChildren().add(shortcutLabel);
                    }
                    
                    setGraphic(container);
                }
            }
        };
    }
    
    /**
     * Setup keyboard and search handlers.
     */
    private void setupEventHandlers() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterCommands(newVal));
        searchField.setOnKeyPressed(this::handleKeyPress);
        commandListView.setOnKeyPressed(this::handleKeyPress);
        commandListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                executeSelectedCommand();
            }
        });
    }
    
    /** Routes ESCAPE, ENTER, UP and DOWN key events to their respective palette actions; all other keys are left unhandled. */
    private void handleKeyPress(KeyEvent event) {
        switch (event.getCode()) {
            case ESCAPE:
                hide();
                event.consume();
                break;
            case ENTER:
                executeSelectedCommand();
                event.consume();
                break;
            case UP:
                navigateUp();
                event.consume();
                break;
            case DOWN:
                navigateDown();
                event.consume();
                break;
            default:
                break;
        }
    }
    
    /** Filters the command list against {@code query} using fuzzy scoring across name, description and category; resets to full list when blank. */
    private void filterCommands(String query) {
        if (query == null || query.trim().isEmpty()) {
            commandListView.getItems().setAll(commands);
        } else {
            String trimmed = query.trim();
            List<Command> filtered = commands.stream()
                .map(cmd -> {
                    int best = Math.max(
                        FuzzySearchUtils.fuzzyScore(trimmed, cmd.getDisplayName(bundle)),
                        Math.max(
                            FuzzySearchUtils.fuzzyScore(trimmed, cmd.getDisplayDescription(bundle)),
                            FuzzySearchUtils.fuzzyScore(trimmed, cmd.getDisplayCategory(bundle))
                        ));
                    return new Object[]{ cmd, best };
                })
                .filter(pair -> (int) pair[1] > 0)
                .sorted((a, b) -> Integer.compare((int) b[1], (int) a[1]))
                .map(pair -> (Command) pair[0])
                .collect(Collectors.toList());
            commandListView.getItems().setAll(filtered);
        }

        if (!commandListView.getItems().isEmpty()) {
            commandListView.getSelectionModel().selectFirst();
        }
    }
    
    /** Moves the list selection one row up, scrolling the list to keep the selected item visible. */
    private void navigateUp() {
        int idx = commandListView.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            commandListView.getSelectionModel().select(idx - 1);
            commandListView.scrollTo(idx - 1);
        }
    }
    
    /** Moves the list selection one row down, scrolling the list to keep the selected item visible. */
    private void navigateDown() {
        int idx = commandListView.getSelectionModel().getSelectedIndex();
        if (idx < commandListView.getItems().size() - 1) {
            commandListView.getSelectionModel().select(idx + 1);
            commandListView.scrollTo(idx + 1);
        }
    }
    
    /** Hides the palette, then executes the currently selected command on the next FX pulse via {@link Platform#runLater}. */
    private void executeSelectedCommand() {
        Command selected = commandListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            hide();
            Platform.runLater(() -> {
                try {
                    selected.execute();
                    logger.info("Executed command: " + selected.getName());
                } catch (Exception e) {
                    logger.severe("Error executing command: " + e.getMessage());
                }
            });
        }
    }
    
    /** Plays a 150 ms fade-in + scale-up entrance animation on the main container. */
    private void animateEntrance() {
        VBox container = (VBox) ((StackPane) paletteStage.getScene().getRoot()).getChildren().get(0);
        
        FadeTransition fade = new FadeTransition(Duration.millis(150), container);
        fade.setFromValue(0);
        fade.setToValue(1);
        
        ScaleTransition scale = new ScaleTransition(Duration.millis(150), container);
        scale.setFromX(0.95);
        scale.setFromY(0.95);
        scale.setToX(1);
        scale.setToY(1);
        
        fade.play();
        scale.play();
    }
    
    /** Plays a 100 ms fade-out animation, then invokes {@code onFinished} to hide or close the stage. */
    private void animateExit(Runnable onFinished) {
        VBox container = (VBox) ((StackPane) paletteStage.getScene().getRoot()).getChildren().get(0);
        
        FadeTransition fade = new FadeTransition(Duration.millis(100), container);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> onFinished.run());
        fade.play();
    }
}
