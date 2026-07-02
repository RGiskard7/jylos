package com.example.jylos.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.components.CommandPalette;

/**
 * Manages the lifecycle and state of all plugins.
 * 
 * <p>
 * Responsibilities:
 * </p>
 * <ul>
 * <li>Register and unregister plugins</li>
 * <li>Initialize and shutdown plugins</li>
 * <li>Enable and disable plugins</li>
 * <li>Track plugin states</li>
 * <li>Resolve plugin dependencies</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class PluginManager {

    private static final Logger logger = LoggerConfig.getLogger(PluginManager.class);
    private static final String PREFS_DISABLED_PREFIX = "plugin.disabled.";

    /**
     * Plugin states.
     */
    public enum PluginState {
        REGISTERED, // Plugin is registered but not initialized
        INITIALIZED, // Plugin is initialized and ready
        ENABLED, // Plugin is enabled and active
        DISABLED, // Plugin is disabled
        ERROR // Plugin encountered an error
    }

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
    private final Consumer<Note> noteOpenAction;

    // Plugin storage
    private final Map<String, Plugin> plugins = new HashMap<>();
    private final Map<String, PluginState> pluginStates = new HashMap<>();
    private final Map<String, PluginContext> pluginContexts = new HashMap<>();
    private final Preferences pluginPreferences = Preferences.userNodeForPackage(PluginManager.class);

    /**
     * Creates a new PluginManager.
     *
     * @param noteService        The note service
     * @param folderService      The folder service
     * @param tagService         The tag service
     * @param eventBus           The event bus
     * @param commandPalette     The command palette
     * @param menuRegistry       The menu registry
     * @param sidePanelRegistry  The side panel registry
     * @param previewEnhancerRegistry The preview enhancer registry
     * @param editorHookRegistry The editor hook registry (nullable)
     * @param toolbarRegistry    The toolbar button registry (nullable)
     * @param noteOpenAction     Owner callback for plugin note-open requests
     */
    public PluginManager(
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
            Consumer<Note> noteOpenAction) {
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
        this.noteOpenAction = noteOpenAction;
    }

    /**
     * Registers a plugin.
     * 
     * @param plugin The plugin to register
     * @return true if registered successfully, false otherwise
     */
    public boolean registerPlugin(Plugin plugin) {
        if (plugin == null) {
            logger.warning("Attempted to register null plugin");
            return false;
        }

        String pluginId = plugin.getId();
        if (pluginId == null || pluginId.isEmpty()) {
            logger.warning("Plugin has invalid ID");
            return false;
        }

        if (plugins.containsKey(pluginId)) {
            logger.warning("Plugin already registered: " + pluginId);
            return false;
        }

        plugins.put(pluginId, plugin);
        pluginStates.put(pluginId, PluginState.REGISTERED);
        logger.info("Registered plugin: " + plugin.getName() + " (" + pluginId + ")");

        return true;
    }

    /**
     * Unregisters a plugin.
     * 
     * @param pluginId The plugin ID
     * @return true if unregistered successfully, false otherwise
     */
    public boolean unregisterPlugin(String pluginId) {
        if (pluginId == null || !plugins.containsKey(pluginId)) {
            return false;
        }

        // Shutdown plugin first
        shutdownPlugin(pluginId);

        // Remove plugin
        plugins.remove(pluginId);
        pluginStates.remove(pluginId);
        pluginContexts.remove(pluginId);

        // Remove UI components
        if (menuRegistry != null) {
            menuRegistry.removePluginMenuItems(pluginId);
        }
        if (sidePanelRegistry != null) {
            sidePanelRegistry.removeAllSidePanels(pluginId);
        }

        logger.info("Unregistered plugin: " + pluginId);
        return true;
    }

    /**
     * Initializes a plugin.
     * 
     * @param pluginId The plugin ID
     * @return true if initialized successfully, false otherwise
     */
    public boolean initializePlugin(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            logger.warning("Plugin not found: " + pluginId);
            return false;
        }

        PluginState currentState = pluginStates.get(pluginId);
        if (currentState == PluginState.INITIALIZED || currentState == PluginState.ENABLED) {
            logger.fine("Plugin already initialized: " + pluginId);
            return true;
        }

        // Check dependencies
        String[] dependencies = plugin.getDependencies();
        for (String depId : dependencies) {
            if (!plugins.containsKey(depId)) {
                logger.warning("Plugin " + pluginId + " depends on missing plugin: " + depId);
                pluginStates.put(pluginId, PluginState.ERROR);
                return false;
            }
            if (!isPluginEnabled(depId)) {
                logger.warning("Plugin " + pluginId + " depends on disabled plugin: " + depId);
                pluginStates.put(pluginId, PluginState.ERROR);
                return false;
            }
        }

        try {
            // Create context
            PluginContext context = new PluginContext(
                    pluginId,
                    noteService,
                    folderService,
                    tagService,
                    eventBus,
                    commandPalette,
                    menuRegistry,
                    sidePanelRegistry,
                    previewEnhancerRegistry,
                    editorHookRegistry,
                    toolbarRegistry,
                    noteOpenAction);
            pluginContexts.put(pluginId, context);

            // Initialize plugin
            plugin.initialize(context);

            pluginStates.put(pluginId, PluginState.INITIALIZED);
            logger.info("Initialized plugin: " + plugin.getName() + " (" + pluginId + ")");

            if (plugin.isEnabled() && !isDisabledInPreferences(pluginId)) {
                enablePlugin(pluginId);
            } else {
                pluginStates.put(pluginId, PluginState.DISABLED);
            }

            return true;
        } catch (Throwable t) {
            cleanupPluginRuntime(pluginId);
            pluginContexts.remove(pluginId);
            logger.log(Level.SEVERE, "Failed to initialize plugin " + pluginId, t);
            pluginStates.put(pluginId, PluginState.ERROR);
            return false;
        }
    }

    /**
     * Initializes all registered plugins in priority order.
     */
    public void initializeAll() {
        List<Plugin> sortedPlugins = sortPluginsForInitialization();
        for (Plugin plugin : sortedPlugins) {
            initializePlugin(plugin.getId());
        }
        logger.info("Initialized " + plugins.size() + " plugin(s)");
    }

    private List<Plugin> sortPluginsForInitialization() {
        Map<String, Plugin> byId = new LinkedHashMap<>();
        for (Plugin plugin : plugins.values()) {
            byId.put(plugin.getId(), plugin);
        }
        List<Plugin> sorted = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        List<Plugin> roots = new ArrayList<>(byId.values());
        roots.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        for (Plugin plugin : roots) {
            visitPluginForInit(plugin, byId, sorted, visiting, visited);
        }
        return sorted;
    }

    private void visitPluginForInit(
            Plugin plugin,
            Map<String, Plugin> byId,
            List<Plugin> sorted,
            Set<String> visiting,
            Set<String> visited) {
        if (plugin == null || visited.contains(plugin.getId())) {
            return;
        }
        if (!visiting.add(plugin.getId())) {
            logger.warning("Circular plugin dependency detected at: " + plugin.getId());
            return;
        }
        for (String dependencyId : plugin.getDependencies()) {
            Plugin dependency = byId.get(dependencyId);
            if (dependency != null) {
                visitPluginForInit(dependency, byId, sorted, visiting, visited);
            } else {
                logger.warning("Plugin '" + plugin.getId() + "' depends on missing plugin '" + dependencyId + "'");
            }
        }
        visiting.remove(plugin.getId());
        visited.add(plugin.getId());
        sorted.add(plugin);
    }

    /**
     * Shuts down a plugin.
     * 
     * @param pluginId The plugin ID
     */
    public void shutdownPlugin(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            return;
        }

        PluginState state = pluginStates.get(pluginId);
        PluginContext context = pluginContexts.get(pluginId);
        if (context == null
                && state != PluginState.INITIALIZED
                && state != PluginState.ENABLED
                && state != PluginState.DISABLED) {
            return;
        }

        try {
            plugin.shutdown();
            pluginStates.put(pluginId, PluginState.DISABLED);
            logger.info("Shut down plugin: " + pluginId);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error shutting down plugin " + pluginId, t);
        } finally {
            cleanupPluginRuntime(pluginId);
        }
    }

    /**
     * Shuts down all plugins.
     */
    public void shutdownAll() {
        for (String pluginId : new ArrayList<>(plugins.keySet())) {
            shutdownPlugin(pluginId);
        }
        logger.info("Shut down all plugins");
    }

    /**
     * Enables a plugin.
     * 
     * @param pluginId The plugin ID
     * @return true if enabled successfully, false otherwise
     */
    public boolean enablePlugin(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            return false;
        }

        PluginState currentState = pluginStates.get(pluginId);
        if (currentState == PluginState.ENABLED) {
            return true;
        }

        if (currentState == PluginState.REGISTERED || currentState == PluginState.DISABLED) {
            if (!initializePlugin(pluginId)) {
                return false;
            }
        }

        pluginStates.put(pluginId, PluginState.ENABLED);
        setDisabledInPreferences(pluginId, false);
        logger.info("Enabled plugin: " + pluginId);
        return true;
    }

    /**
     * Disables a plugin.
     * 
     * @param pluginId The plugin ID
     * @return true if disabled successfully, false otherwise
     */
    public boolean disablePlugin(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            return false;
        }

        try {
            plugin.shutdown();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error while disabling plugin " + pluginId, t);
        }

        pluginStates.put(pluginId, PluginState.DISABLED);
        cleanupPluginRuntime(pluginId);
        setDisabledInPreferences(pluginId, true);

        logger.info("Disabled plugin: " + pluginId);
        return true;
    }

    private void cleanupPluginRuntime(String pluginId) {
        if (menuRegistry != null) {
            menuRegistry.removePluginMenuItems(pluginId);
        }
        if (sidePanelRegistry != null) {
            sidePanelRegistry.removeAllSidePanels(pluginId);
        }
        if (previewEnhancerRegistry != null) {
            previewEnhancerRegistry.unregisterPreviewEnhancer(pluginId);
        }
        if (editorHookRegistry != null) {
            editorHookRegistry.unregisterEditorHooks(pluginId);
        }
        if (toolbarRegistry != null) {
            toolbarRegistry.removeToolbarButtons(pluginId);
        }
        PluginContext context = pluginContexts.get(pluginId);
        if (context != null) {
            context.unregisterAllCommands();
        }
    }

    private boolean isDisabledInPreferences(String pluginId) {
        return pluginPreferences.getBoolean(PREFS_DISABLED_PREFIX + pluginId, false);
    }

    private void setDisabledInPreferences(String pluginId, boolean disabled) {
        pluginPreferences.putBoolean(PREFS_DISABLED_PREFIX + pluginId, disabled);
    }

    /**
     * Checks if a plugin is enabled.
     * 
     * @param pluginId The plugin ID
     * @return true if enabled, false otherwise
     */
    public boolean isPluginEnabled(String pluginId) {
        PluginState state = pluginStates.get(pluginId);
        return state == PluginState.ENABLED || state == PluginState.INITIALIZED;
    }

    /**
     * Gets the state of a plugin.
     * 
     * @param pluginId The plugin ID
     * @return The plugin state, or null if plugin not found
     */
    public PluginState getPluginState(String pluginId) {
        return pluginStates.get(pluginId);
    }

    /**
     * Gets a plugin by ID.
     * 
     * @param pluginId The plugin ID
     * @return The plugin, or empty if not found
     */
    public Optional<Plugin> getPlugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    /**
     * Gets all registered plugins.
     * 
     * @return List of all plugins
     */
    public List<Plugin> getAllPlugins() {
        return new ArrayList<>(plugins.values());
    }

    /**
     * Gets all enabled plugins.
     * 
     * @return List of enabled plugins
     */
    public List<Plugin> getEnabledPlugins() {
        return plugins.values().stream()
                .filter(p -> isPluginEnabled(p.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Gets all disabled plugins.
     * 
     * @return List of disabled plugins
     */
    public List<Plugin> getDisabledPlugins() {
        return plugins.values().stream()
                .filter(p -> !isPluginEnabled(p.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Gets the number of registered plugins.
     * 
     * @return The plugin count
     */
    public int getPluginCount() {
        return plugins.size();
    }

    /**
     * Gets plugin information as a string.
     * 
     * @param pluginId The plugin ID
     * @return Plugin information string
     */
    public String getPluginInfo(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            return "Plugin not found: " + pluginId;
        }

        PluginState state = pluginStates.get(pluginId);
        StringBuilder info = new StringBuilder();
        info.append("Name: ").append(plugin.getName()).append("\n");
        info.append("ID: ").append(plugin.getId()).append("\n");
        info.append("Version: ").append(plugin.getVersion()).append("\n");
        info.append("State: ").append(state).append("\n");
        if (!plugin.getAuthor().isEmpty()) {
            info.append("Author: ").append(plugin.getAuthor()).append("\n");
        }
        if (!plugin.getDescription().isEmpty()) {
            info.append("Description: ").append(plugin.getDescription()).append("\n");
        }

        return info.toString();
    }
}
