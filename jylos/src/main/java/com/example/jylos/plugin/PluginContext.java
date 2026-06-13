package com.example.jylos.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.AppEvent;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.components.CommandPalette;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;

/**
 * Context provided to plugins during initialization.
 * Provides access to application services, UI registration, and event system.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class PluginContext {

    private static final Logger logger = LoggerConfig.getLogger(PluginContext.class);

    private final String pluginId;
    private final NoteService noteService;
    private final FolderService folderService;
    private final TagService tagService;
    private final EventBus eventBus;
    private final CommandPalette commandPalette;
    private final PluginMenuRegistry menuRegistry;
    private final SidePanelRegistry sidePanelRegistry;
    private final PreviewEnhancerRegistry previewEnhancerRegistry;
    private final EditorHookRegistry editorHookRegistry;
    private final ToolbarRegistry toolbarRegistry;
    private final List<String> registeredCommandIds = new ArrayList<>();

    /**
     * Creates a new PluginContext.
     *
     * @param pluginId           The ID of the plugin using this context
     * @param noteService        The note service
     * @param folderService      The folder service
     * @param tagService         The tag service
     * @param eventBus           The event bus
     * @param commandPalette     The command palette
     * @param menuRegistry       The menu registry for registering menu items
     * @param sidePanelRegistry  The side panel registry for registering UI panels
     * @param previewEnhancerRegistry The preview enhancer registry
     * @param editorHookRegistry The editor hook registry (may be null in tests)
     * @param toolbarRegistry    The toolbar button registry (may be null in tests)
     */
    public PluginContext(
            String pluginId,
            NoteService noteService,
            FolderService folderService,
            TagService tagService,
            EventBus eventBus,
            CommandPalette commandPalette,
            PluginMenuRegistry menuRegistry,
            SidePanelRegistry sidePanelRegistry,
            PreviewEnhancerRegistry previewEnhancerRegistry,
            EditorHookRegistry editorHookRegistry,
            ToolbarRegistry toolbarRegistry) {
        this.pluginId = pluginId;
        this.noteService = noteService;
        this.folderService = folderService;
        this.tagService = tagService;
        this.eventBus = eventBus;
        this.commandPalette = commandPalette;
        this.menuRegistry = menuRegistry;
        this.sidePanelRegistry = sidePanelRegistry;
        this.previewEnhancerRegistry = previewEnhancerRegistry;
        this.editorHookRegistry = editorHookRegistry;
        this.toolbarRegistry = toolbarRegistry;
    }

    /**
     * Gets the note service.
     * 
     * @return The note service
     */
    public NoteService getNoteService() {
        return noteService;
    }

    /**
     * Gets the folder service.
     * 
     * @return The folder service
     */
    public FolderService getFolderService() {
        return folderService;
    }

    /**
     * Gets the tag service.
     * 
     * @return The tag service
     */
    public TagService getTagService() {
        return tagService;
    }

    /**
     * Gets the event bus.
     * 
     * @return The event bus
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Gets the command palette.
     * 
     * @return The command palette
     */
    public CommandPalette getCommandPalette() {
        return commandPalette;
    }

    /**
     * Registers a command in the Command Palette.
     * 
     * @param name        The command name
     * @param description The command description
     * @param action      The action to execute
     */
    public void registerCommand(String name, String description, Runnable action) {
        registerCommand(name, description, null, action);
    }

    /**
     * Registers a command in the Command Palette with a keyboard shortcut.
     * 
     * @param name        The command name
     * @param description The command description
     * @param shortcut    The keyboard shortcut (e.g., "Ctrl+Shift+W")
     * @param action      The action to execute
     */
    public void registerCommand(String name, String description, String shortcut, Runnable action) {
        if (commandPalette == null || name == null || name.isBlank()) {
            return;
        }
        String commandId = PluginIds.commandId(pluginId, name);
        commandPalette.addCommand(new CommandPalette.Command(
                commandId,
                name,
                description,
                shortcut != null ? shortcut : "",
                ">",
                "Plugins",
                action));
        registeredCommandIds.add(commandId);
        logger.fine("Plugin " + pluginId + " registered command: " + commandId);
    }

    /**
     * Unregisters a command from the Command Palette.
     *
     * @param commandName display name or stable command id
     */
    public void unregisterCommand(String commandName) {
        if (commandPalette == null || commandName == null) {
            return;
        }
        String commandId = commandName.startsWith(PluginIds.COMMAND_PREFIX)
                ? commandName
                : PluginIds.commandId(pluginId, commandName);
        commandPalette.removeCommandById(commandId);
        commandPalette.removeCommand(commandName);
        registeredCommandIds.remove(commandId);
        logger.fine("Plugin " + pluginId + " unregistered command: " + commandId);
    }

    /**
     * Removes all commands registered through this context (safe to call from {@link Plugin#shutdown()}).
     */
    public void unregisterAllCommands() {
        for (String commandId : new ArrayList<>(registeredCommandIds)) {
            if (commandPalette != null) {
                commandPalette.removeCommandById(commandId);
            }
        }
        registeredCommandIds.clear();
        unregisterPreviewEnhancer();
    }

    /**
     * Registers a menu item in a category.
     * 
     * @param category The menu category (e.g., "Core", "Productivity", "AI")
     * @param itemName The menu item name
     * @param action   The action to execute
     */
    public void registerMenuItem(String category, String itemName, Runnable action) {
        registerMenuItem(category, itemName, null, action);
    }

    /**
     * Registers a menu item in a category with a keyboard shortcut.
     * 
     * @param category The menu category
     * @param itemName The menu item name
     * @param shortcut The keyboard shortcut
     * @param action   The action to execute
     */
    public void registerMenuItem(String category, String itemName, String shortcut, Runnable action) {
        if (menuRegistry != null) {
            menuRegistry.registerMenuItem(pluginId, category, itemName, shortcut, action);
        }
    }

    /**
     * Adds a separator in a menu category.
     * 
     * @param category The menu category
     */
    public void addMenuSeparator(String category) {
        if (menuRegistry != null) {
            menuRegistry.addMenuSeparator(pluginId, category);
        }
    }

    /**
     * Registers a side panel in the right sidebar.
     * 
     * @param panelId The unique panel ID
     * @param title   The panel title
     * @param content The panel content (JavaFX Node)
     */
    public void registerSidePanel(String panelId, String title, Node content) {
        registerSidePanel(panelId, title, content, null);
    }

    /**
     * Registers a side panel with an icon.
     * 
     * @param panelId The unique panel ID
     * @param title   The panel title
     * @param content The panel content
     * @param icon    The icon (emoji or text)
     */
    public void registerSidePanel(String panelId, String title, Node content, String icon) {
        if (sidePanelRegistry != null) {
            sidePanelRegistry.registerSidePanel(pluginId, panelId, title, content, icon);
        }
    }

    /**
     * Removes a side panel.
     * 
     * @param panelId The panel ID to remove
     */
    public void removeSidePanel(String panelId) {
        if (sidePanelRegistry != null) {
            sidePanelRegistry.removeSidePanel(pluginId, panelId);
        }
    }

    /**
     * Shows or hides the plugin panels section.
     * 
     * @param visible true to show, false to hide
     */
    public void setPluginPanelsVisible(boolean visible) {
        if (sidePanelRegistry != null) {
            sidePanelRegistry.setPluginPanelsVisible(visible);
        }
    }

    /**
     * Checks if the plugin panels section is visible.
     * 
     * @return true if visible, false otherwise
     */
    public boolean isPluginPanelsVisible() {
        if (sidePanelRegistry != null) {
            return sidePanelRegistry.isPluginPanelsVisible();
        }
        return false;
    }

    /**
     * Subscribes to an event type.
     * 
     * @param <T>       The event type
     * @param eventType The event class
     * @param handler   The event handler
     * @return The subscription (can be used to unsubscribe)
     */
    public <T extends AppEvent> EventBus.Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventBus != null) {
            return eventBus.subscribe(eventType, handler);
        }
        return EventBus.Subscription.NO_OP;
    }

    /**
     * Publishes an event.
     * 
     * @param event The event to publish
     */
    public void publish(AppEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    /**
     * Requests to open a note in the editor.
     * 
     * @param note The note to open
     */
    public void requestOpenNote(Note note) {
        if (eventBus != null && note != null) {
            Platform.runLater(() -> {
                eventBus.publish(new NoteEvents.NoteOpenRequestEvent(note));
            });
        }
    }

    /**
     * Requests a refresh of the notes list.
     */
    public void requestRefreshNotes() {
        if (eventBus != null) {
            Platform.runLater(() -> {
                eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
            });
        }
    }

    /**
     * Shows an information dialog.
     * 
     * @param title   The dialog title
     * @param header  The dialog header
     * @param content The dialog content
     */
    public void showInfo(String title, String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            com.example.jylos.ui.UiDialogs.apply(alert.getDialogPane());
            alert.showAndWait();
        });
    }

    /**
     * Shows an error dialog.
     * 
     * @param title   The dialog title
     * @param message The error message
     */
    public void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            com.example.jylos.ui.UiDialogs.apply(alert.getDialogPane());
            alert.showAndWait();
        });
    }

    /**
     * Logs a message.
     * 
     * @param message The message to log
     */
    public void log(String message) {
        logger.info("[" + pluginId + "] " + message);
    }

    /**
     * Logs an error.
     * 
     * @param message   The error message
     * @param throwable The exception
     */
    public void logError(String message, Throwable throwable) {
        logger.severe("[" + pluginId + "] " + message);
        if (throwable != null) {
            logger.severe("[" + pluginId + "] Exception: " + throwable.getMessage());
        }
    }

    /**
     * Gets the plugin ID.
     * 
     * @return The plugin ID
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * Registers a preview enhancer.
     * This allows the plugin to inject CSS/JS into the note preview.
     * 
     * @param enhancer The preview enhancer
     */
    public void registerPreviewEnhancer(PreviewEnhancer enhancer) {
        if (previewEnhancerRegistry != null) {
            previewEnhancerRegistry.registerPreviewEnhancer(pluginId, enhancer);
        }
    }

    /**
     * Unregisters the preview enhancer.
     */
    public void unregisterPreviewEnhancer() {
        if (previewEnhancerRegistry != null) {
            previewEnhancerRegistry.unregisterPreviewEnhancer(pluginId);
        }
    }

    /**
     * Registers an {@link EditorHook}: lets the plugin transform snippet insertions
     * and note content before save, and observe successful saves. Hooks run in
     * registration order and are removed automatically when the plugin is disabled.
     *
     * @param hook the hook implementation
     */
    public void registerEditorHook(EditorHook hook) {
        if (editorHookRegistry != null) {
            editorHookRegistry.registerEditorHook(pluginId, hook);
        }
    }

    /** Removes every editor hook registered by this plugin (safe from {@code shutdown()}). */
    public void unregisterEditorHooks() {
        if (editorHookRegistry != null) {
            editorHookRegistry.unregisterEditorHooks(pluginId);
        }
    }

    /**
     * Adds a button to the main toolbar.
     *
     * @param buttonId    stable id, unique within this plugin
     * @param tooltip     tooltip text (also the button text when no icon is given)
     * @param iconLiteral Ikonli Feather literal (e.g. {@code "fth-clock"}) or null
     * @param action      invoked on the JavaFX Application Thread when clicked
     */
    public void registerToolbarButton(String buttonId, String tooltip, String iconLiteral, Runnable action) {
        if (toolbarRegistry != null) {
            toolbarRegistry.registerToolbarButton(pluginId, buttonId, tooltip, iconLiteral, action);
        }
    }

    /** Removes every toolbar button registered by this plugin (safe from {@code shutdown()}). */
    public void removeToolbarButtons() {
        if (toolbarRegistry != null) {
            toolbarRegistry.removeToolbarButtons(pluginId);
        }
    }
}
