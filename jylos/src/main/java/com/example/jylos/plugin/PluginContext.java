package com.example.jylos.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.AppEvent;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.ui.components.CommandPalette;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ProgressBar;

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
    private final PluginNoteContextMenuRegistry noteContextMenuRegistry;
    private final SidePanelRegistry sidePanelRegistry;
    private final PreviewEnhancerRegistry previewEnhancerRegistry;
    private final EditorHookRegistry editorHookRegistry;
    private final ToolbarRegistry toolbarRegistry;
    private final EditorBlockRendererRegistry editorBlockRendererRegistry;
    private final Consumer<Note> noteOpenAction;
    private final BiConsumer<Integer, String> editorNavigateAction;
    private final List<String> registeredCommandIds = new ArrayList<>();
    // Copy-on-write, unlike registeredCommandIds. Plugins today subscribe from initialize()
    // on the FX thread, but nothing in the API says they must, and a plugin that owns
    // background threads (the MCP server plugin runs an HTTP server on its own) could
    // subscribe from one while teardown is walking this list.
    private final List<EventBus.Subscription> registeredSubscriptions = new CopyOnWriteArrayList<>();

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
     * @param editorBlockRendererRegistry The editor fenced-block renderer registry (may be null in tests)
     * @param noteOpenAction     Owner callback for opening a note from plugin code
     * @param editorNavigateAction Owner callback for plugin heading-navigation requests
     *                             (may be {@code null})
     * @param noteContextMenuRegistry The note context menu registry (may be null in tests)
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
            ToolbarRegistry toolbarRegistry,
            EditorBlockRendererRegistry editorBlockRendererRegistry,
            Consumer<Note> noteOpenAction,
            BiConsumer<Integer, String> editorNavigateAction,
            PluginNoteContextMenuRegistry noteContextMenuRegistry) {
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
        this.editorBlockRendererRegistry = editorBlockRendererRegistry;
        this.noteOpenAction = noteOpenAction;
        this.editorNavigateAction = editorNavigateAction;
        this.noteContextMenuRegistry = noteContextMenuRegistry;
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
     * Registers an item in every note's right-click context menu (the notes
     * list, both list-view and grid-view) — e.g. a plugin action that operates
     * on one specific note, rather than {@link #registerCommand} or {@link
     * #registerMenuItem}, which have no note to act on until the user picks one.
     *
     * @param label  The menu item's visible text
     * @param action Invoked with the right-clicked note when chosen
     */
    public void registerNoteContextMenuItem(String label, Consumer<Note> action) {
        if (noteContextMenuRegistry != null) {
            noteContextMenuRegistry.registerNoteContextMenuItem(pluginId, label, action);
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
     * <p>The subscription is tracked and cancelled for you when the plugin is disabled,
     * like every other contribution registered through this context. Cancelling the
     * returned handle yourself in {@code shutdown()} is still good practice and costs
     * nothing — {@code cancel()} is idempotent — but is no longer what stands between a
     * disabled plugin and a handler that keeps firing.</p>
     *
     * <p>Note the bus dispatches on the event's exact class: subscribing to a supertype
     * does not receive its subclasses.</p>
     *
     * @param <T>       The event type
     * @param eventType The event class
     * @param handler   The event handler
     * @return The subscription (can be used to unsubscribe early)
     */
    public <T extends AppEvent> EventBus.Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventBus != null) {
            EventBus.Subscription subscription = eventBus.subscribe(eventType, handler);
            registeredSubscriptions.add(subscription);
            return subscription;
        }
        return EventBus.Subscription.NO_OP;
    }

    /**
     * Cancels every event subscription this plugin registered (safe to call from
     * {@link Plugin#shutdown()}).
     *
     * <p>This is the safety net the other contribution types already had. A plugin whose
     * {@code shutdown()} throws before reaching its own cancellation code used to leave
     * live handlers behind for the rest of the session — still running code from a
     * disabled plugin, and pinning its classloader so it could never be collected.
     * Cancelling twice is harmless, so a plugin that does clean up after itself is
     * unaffected.</p>
     */
    public void unsubscribeAll() {
        for (EventBus.Subscription subscription : new ArrayList<>(registeredSubscriptions)) {
            try {
                subscription.cancel();
            } catch (RuntimeException e) {
                // One uncooperative subscription must not strand the others.
                logger.warning("Failed to cancel a subscription for plugin " + pluginId + ": " + e.getMessage());
            }
        }
        registeredSubscriptions.clear();
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
        if (noteOpenAction != null && note != null) {
            Platform.runLater(() -> {
                noteOpenAction.accept(note);
            });
        }
    }

    /**
     * Navigates to a heading in the currently open note — jumps the caret to
     * {@code offset} if the raw-source editor is showing, or scrolls to the matching
     * anchor id if the rendered Preview is showing. No-op if no note is open.
     *
     * @param offset      character offset of the heading in the note's raw Markdown
     * @param headingSlug the heading's anchor id in the rendered Preview, as produced
     *                    by {@code MarkdownProcessor.slugifyHeading}
     */
    public void navigateToHeading(int offset, String headingSlug) {
        if (editorNavigateAction != null) {
            Platform.runLater(() -> editorNavigateAction.accept(offset, headingSlug));
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
     * Applies the app's current theme to a plugin's own {@link DialogPane} — a plain
     * JavaFX dialog does not inherit the main window's stylesheets on its own, so
     * without this it renders with the platform default (light) look, illegible over a
     * dark theme. Call this on any {@link Alert}/{@link Dialog} a plugin builds itself
     * (a custom form, a confirmation with extra content, …) before showing it; {@link
     * #showInfo}, {@link #showCopyableInfo} and {@link #showError} already do this for
     * the dialogs they build.
     *
     * @param dialogPane the dialog pane to theme (usually {@code dialog.getDialogPane()})
     */
    public void applyTheme(DialogPane dialogPane) {
        UiDialogs.apply(dialogPane);
    }

    /**
     * Convenience overload of {@link #applyTheme(DialogPane)} taking the {@link Dialog}
     * itself.
     *
     * @param dialog the dialog to theme
     */
    public void applyTheme(Dialog<?> dialog) {
        UiDialogs.apply(dialog);
    }

    /**
     * Applies the app's current theme to a plugin's own custom {@link
     * javafx.stage.Stage}-based window (a popup that is not a {@link Dialog} at all —
     * for example, a borderless progress indicator) — same reasoning as {@link
     * #applyTheme(DialogPane)}, but for a plugin that builds its own {@link
     * javafx.scene.Scene} instead of going through {@code Dialog}/{@code Alert}.
     *
     * @param scene the scene to theme
     */
    public void applyTheme(javafx.scene.Scene scene) {
        UiDialogs.apply(scene);
    }

    /**
     * Themes a plugin's own {@link Dialog} and shows it modally, returning its result —
     * {@link #applyTheme(Dialog)} followed by {@code dialog.showAndWait()} in one call,
     * for the common case where nothing else needs to happen between the two.
     *
     * @param <T>    the dialog's result type
     * @param dialog the dialog to theme and show
     * @return the dialog's result, as {@link Dialog#showAndWait()} returns it
     */
    public <T> Optional<T> showThemed(Dialog<T> dialog) {
        return UiDialogs.show(dialog);
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
            UiDialogs.apply(alert.getDialogPane());
            alert.showAndWait();
        });
    }

    /**
     * Shows an information dialog whose content the user can actually select and copy —
     * for anything meant to be pasted elsewhere (a template snippet, a connection URL),
     * as opposed to {@link #showInfo}, which is for a message that is just read. {@code
     * setContentText} on a plain {@link Alert} renders the content as a {@code Label},
     * and JavaFX {@code Label}s are not selectable at all — no drag-select, no Ctrl+C,
     * nothing copies out of one no matter how the user tries. This swaps that content
     * area for a read-only, wrapped {@link javafx.scene.control.TextArea} instead (a
     * real text control, so normal text selection and copy shortcuts work exactly as
     * they would anywhere else in the app), plus an explicit "Copy" button as a second,
     * more discoverable way to get the same text onto the clipboard.
     *
     * @param title   The dialog title
     * @param header  The dialog header
     * @param content The copyable content
     */
    public void showCopyableInfo(String title, String header, String content) {
        Platform.runLater(() -> {
            Alert alert = buildCopyableInfoAlert(title, header, content);
            UiDialogs.apply(alert.getDialogPane());
            alert.showAndWait();
        });
    }

    /** Builds the {@link Alert} {@link #showCopyableInfo} shows, minus the app-theme styling and the
     *  blocking {@code showAndWait()} — split out so a test can inspect the real widgets (that the
     *  content is a real, read-only {@link javafx.scene.control.TextArea} carrying the exact text
     *  passed in, not a {@code Label}, and that the "Copy" button is wired to the clipboard) without
     *  ever popping an actual modal window. Package-private: {@link #showCopyableInfo} is the public
     *  entry point plugins use. */
    static Alert buildCopyableInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);

        javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea(content);
        textArea.setEditable(false);
        // Sized to the content, so a snippet that fits shows whole with no scrollbar at all.
        // wrapText stays OFF on purpose: this is pre-formatted text (a fenced code block, a
        // URL), wrapping it both misrepresents it and makes the visible line count larger
        // than the real one, which is what forced a scrollbar onto dialogs that fit fine.
        // The caps only kick in for genuinely huge content, where scrolling is the right
        // answer rather than a dialog taller than the screen.
        List<String> lines = content.lines().toList();
        int longestLine = lines.stream().mapToInt(String::length).max().orElse(20);
        textArea.setWrapText(false);
        textArea.setPrefRowCount(Math.min(30, Math.max(2, lines.size())));
        textArea.setPrefColumnCount(Math.min(100, Math.max(20, longestLine)));
        javafx.scene.layout.VBox.setVgrow(textArea, javafx.scene.layout.Priority.ALWAYS);
        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setExpandableContent(null);

        javafx.scene.control.ButtonType copyButtonType =
                new javafx.scene.control.ButtonType("Copy", javafx.scene.control.ButtonBar.ButtonData.LEFT);
        alert.getDialogPane().getButtonTypes().add(0, copyButtonType);
        // A plain ButtonType closes the dialog on click by default — consume the event
        // on this one so "Copy" copies and leaves the dialog open (the user may want
        // to re-read the content, or copy again after scrolling).
        javafx.scene.control.Button copyButton =
                (javafx.scene.control.Button) alert.getDialogPane().lookupButton(copyButtonType);
        copyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
            clipboardContent.putString(content);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboardContent);
            event.consume();
        });

        return alert;
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
            UiDialogs.apply(alert.getDialogPane());
            alert.showAndWait();
        });
    }

    /**
     * Runs a long operation on a background thread with a themed, indeterminate-turned-
     * determinate progress dialog — the ceremony a plugin doing real work (exporting a
     * vault, an I/O-bound backup, …) already needs today: build the dialog, bind a
     * progress bar to the task, start a daemon thread, close the dialog and hand off to a
     * follow-up callback on success or failure.
     *
     * <p>{@code onSuccess}/{@code onFailure} run <b>after</b> the progress dialog has
     * closed, each deferred one more {@link Platform#runLater} tick past that — opening
     * another modal {@link Alert} directly from a {@link Task}'s {@code setOnSucceeded}/
     * {@code setOnFailed} (which fire while the progress dialog's own {@code
     * showAndWait()} nested event loop is still unwinding) renders as a blank window on
     * JavaFX; deferring one tick is the established fix already used everywhere else in
     * this codebase that chains a modal after another. {@code task} should call {@link
     * Task#updateProgress} from its {@code call()} — {@link Task} marshals that back to
     * the FX thread itself, so it is safe to call from the background thread {@code
     * call()} runs on.</p>
     *
     * <p>Must be called on the FX thread (it builds and shows UI); safe to call it from
     * inside your own {@code Platform.runLater} if you are not already on it.</p>
     *
     * @param <T>       the task's result type
     * @param title     the progress dialog's title
     * @param header    the progress dialog's header text
     * @param task      the work to run — not yet started
     * @param onSuccess called with the task's result once it finishes successfully, or
     *                  {@code null} to ignore success
     * @param onFailure called with the task's exception if it fails, or {@code null} to
     *                  ignore failure (the exception is still logged nowhere by this
     *                  method itself — log it in the callback if you want it recorded)
     */
    public <T> void runWithProgress(String title, String header, Task<T> task,
            Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Alert progressDialog = new Alert(Alert.AlertType.INFORMATION);
        progressDialog.setTitle(title);
        progressDialog.setHeaderText(header);
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(320);
        progressDialog.getDialogPane().setContent(progressBar);
        progressDialog.getButtonTypes().setAll(ButtonType.CANCEL);
        UiDialogs.apply(progressDialog.getDialogPane());
        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            progressDialog.close();
            T result = task.getValue();
            if (onSuccess != null) {
                Platform.runLater(() -> onSuccess.accept(result));
            }
        });
        task.setOnFailed(e -> {
            progressDialog.close();
            Throwable ex = task.getException();
            if (onFailure != null) {
                Platform.runLater(() -> onFailure.accept(ex));
            }
        });

        Thread thread = new Thread(task, "jylos-plugin-" + pluginId + "-task");
        thread.setDaemon(true);
        thread.start();

        UiDialogs.show(progressDialog);
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
     * A {@link Preferences} node reserved for this plugin's own settings — a stable,
     * namespaced key/value store that survives restarts, instead of every plugin that
     * needs one calling {@code Preferences.userNodeForPackage(SomeOwnClass.class)} by
     * hand. Two problems that reaching for the JDK API directly invites: first, a plugin
     * choosing its own arbitrary node (or reusing a class name that collides with another
     * plugin's, or with a core class's) has no guarantee of not colliding with anything
     * else on the same machine; second, a test that happens to construct a real instance
     * of that DAO/service class off this exact node touches whatever the live app has
     * persisted there — this actually happened once in this project's own history, with a
     * plugin id colliding with a real plugin's disabled/enabled flag.
     *
     * <p>The node returned here is scoped under the same {@code Preferences} subtree the
     * host itself uses for plugin bookkeeping ({@link PluginManager}'s own node), keyed by
     * this plugin's id — guaranteed distinct per plugin, and never the same node the host
     * uses for enable/disable state (that lives directly on the parent, this is a child of
     * it), so a plugin cannot accidentally read or corrupt it.</p>
     *
     * @return this plugin's own {@link Preferences} node
     */
    public Preferences getPluginPreferences() {
        return Preferences.userNodeForPackage(PluginManager.class).node(pluginId);
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

    /**
     * Registers an {@link EditorBlockRenderer}: displays fenced blocks of the given
     * language as rendered HTML inside the editor's Live Preview, reverting to source
     * while the cursor is inside the block.
     *
     * @param language the fenced-block info string to match (e.g. {@code "dataview"})
     * @param renderer the renderer implementation
     */
    public void registerEditorBlockRenderer(String language, EditorBlockRenderer renderer) {
        if (editorBlockRendererRegistry != null) {
            editorBlockRendererRegistry.registerEditorBlockRenderer(pluginId, language, renderer);
        }
    }

    /** Removes every editor block renderer registered by this plugin (safe from {@code shutdown()}). */
    public void unregisterEditorBlockRenderers() {
        if (editorBlockRendererRegistry != null) {
            editorBlockRendererRegistry.unregisterEditorBlockRenderers(pluginId);
        }
    }
}
