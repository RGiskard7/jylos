package com.example.jylos.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.AppDirectories;
import com.example.jylos.config.LoggerConfig;

/**
 * Loads external plugins from the plugins/ directory.
 * 
 * <p>
 * This class scans the plugins/ directory for JAR files and dynamically loads
 * plugin classes that implement the Plugin interface. Plugins can be added or
 * removed by simply placing or deleting JAR files in the plugins/ directory.
 * </p>
 * 
 * <p>
 * Plugin JARs must contain:
 * </p>
 * <ul>
 * <li>A class that implements the Plugin interface</li>
 * <li>A manifest entry "Plugin-Class" specifying the fully qualified class
 * name</li>
 * <li>Or the JAR can contain a single class implementing Plugin
 * (auto-detected)</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public class PluginLoader {

    private static final Logger logger = LoggerConfig.getLogger(PluginLoader.class);
    private static final String PLUGINS_DIR = "plugins";

    /** Major version of the {@link PluginContext}/{@link Plugin} API this build exposes. */
    private static final String SUPPORTED_HOST_API_VERSION = "1";

    // Keep classloaders open so inner classes remain accessible
    private static final List<URLClassLoader> activeClassLoaders = new ArrayList<>();
    private static final Map<String, URLClassLoader> activeClassLoadersByPluginId = new ConcurrentHashMap<>();
    private static final Map<Path, URLClassLoader> activeClassLoadersByJarPath = new ConcurrentHashMap<>();
    private static final Map<String, Path> pluginJarPathsById = new ConcurrentHashMap<>();

    /**
     * Detailed report for plugin loading operations.
     * Provides loaded plugins and non-fatal load failures.
     */
    public static final class PluginLoadReport {
        private final List<Plugin> plugins;
        private final List<String> failures;
        private final Map<String, PluginOrigin> origins;

        public PluginLoadReport(List<Plugin> plugins, List<String> failures) {
            this(plugins, failures, Map.of());
        }

        PluginLoadReport(List<Plugin> plugins, List<String> failures, Map<String, PluginOrigin> origins) {
            this.plugins = plugins;
            this.failures = failures;
            this.origins = Map.copyOf(origins);
        }

        /** Whether {@code plugin} is Jylos's own or the community's; community if it was not loaded here. */
        public PluginOrigin originOf(Plugin plugin) {
            return plugin == null ? PluginOrigin.COMMUNITY
                    : origins.getOrDefault(plugin.getId(), PluginOrigin.COMMUNITY);
        }

        public List<Plugin> getPlugins() {
            return plugins;
        }

        public List<String> getFailures() {
            return failures;
        }
    }

    /**
     * Loads Jylos's own plugins from the installation and the community's from the user's
     * plugins folder.
     *
     * @return List of loaded plugin instances
     */
    public static List<Plugin> loadExternalPlugins() {
        return loadExternalPluginsWithReport().getPlugins();
    }

    /**
     * Loads Jylos's own plugins from the installation, then the community's from the user's
     * plugins folder, and says where each one comes from. Before that, removes what plugins
     * Jylos no longer has left behind ({@link RetiredPlugins}).
     *
     * @return plugin loading report with loaded plugins, their origin and failures
     */
    public static PluginLoadReport loadExternalPluginsWithReport() {
        RetiredPlugins.cleanUp(getPluginsDirectory(), Preferences.userNodeForPackage(PluginManager.class))
                .forEach(logger::info);
        return load(corePluginDirs(), getPluginsDirectory());
    }

    /**
     * Loads the plugins in {@code coreDirs} as Jylos's own and those in {@code userDir} as the
     * community's.
     * <ul>
     * <li>A plugin in the user's folder with the id of one already loaded from the installation
     * is not used and its JAR is released: Jylos's own plugin is updated with the app, a copy is
     * not.</li>
     * <li>The copies older versions of Jylos made in the user's folder (with
     * {@link RetiredPlugins#BUNDLED_COPY_MARKER}) of a JAR the installation has are deleted.</li>
     * <li>In a folder that is both (the Windows portable build), a plugin is core when its id is
     * one of {@link CorePlugins}.</li>
     * </ul>
     */
    static PluginLoadReport load(List<Path> coreDirs, Path userDir) {
        List<Plugin> plugins = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        Map<String, PluginOrigin> origins = new LinkedHashMap<>();
        Path user = userDir != null ? userDir.toAbsolutePath().normalize() : null;
        Set<Path> scanned = new HashSet<>();
        Set<String> coreJarNames = new HashSet<>();

        for (Path dir : coreDirs) {
            Path core = dir.toAbsolutePath().normalize();
            if (!scanned.add(core)) {
                continue;
            }
            boolean shared = core.equals(user);
            // The installation's own folder also holds the application's JAR: not a plugin.
            for (Path jar : jarsIn(core, failures, true)) {
                Plugin plugin = loadInto(jar, plugins, failures, origins);
                if (plugin == null) {
                    continue;
                }
                PluginOrigin origin = shared && !CorePlugins.isCore(plugin.getId())
                        ? PluginOrigin.COMMUNITY : PluginOrigin.CORE;
                origins.put(plugin.getId(), origin);
                if (origin == PluginOrigin.CORE) {
                    coreJarNames.add(jar.getFileName().toString());
                }
            }
        }

        if (user != null && scanned.add(user)) {
            removeOldBundledCopies(user, coreJarNames).forEach(logger::info);
            for (Path jar : jarsIn(user, failures, false)) {
                Plugin plugin = loadInto(jar, plugins, failures, origins);
                if (plugin != null) {
                    origins.put(plugin.getId(), PluginOrigin.COMMUNITY);
                }
            }
        }

        logger.info("Loaded " + plugins.size() + " external plugin(s)");
        return new PluginLoadReport(plugins, failures, origins);
    }

    /** Loads one JAR into {@code plugins}; {@code null} if it is not a plugin or its id is taken. */
    private static Plugin loadInto(Path jar, List<Plugin> plugins, List<String> failures,
            Map<String, PluginOrigin> loaded) {
        Plugin plugin;
        try {
            plugin = loadPluginFromJar(jar);
        } catch (Exception e) {
            String message = "Failed to load plugin from " + jar.getFileName();
            logger.log(Level.WARNING, message, e);
            failures.add(message + ": " + e.getMessage());
            return null;
        }
        if (plugin == null) {
            failures.add("Could not load plugin from " + jar.getFileName());
            return null;
        }
        if (loaded.containsKey(plugin.getId())) {
            logger.info("Skipping " + jar + ": plugin '" + plugin.getId() + "' is already loaded from "
                    + pluginJarPathsById.get(plugin.getId()));
            releaseDuplicate(jar);
            return null;
        }
        plugins.add(plugin);
        logger.info("Loaded external plugin: " + plugin.getName() + " v" + plugin.getVersion());
        return plugin;
    }

    /** The JARs in {@code dir}, sorted by name; without the application's own JAR when {@code skipAppJars}. */
    private static List<Path> jarsIn(Path dir, List<String> failures, boolean skipAppJars) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                    .filter(path -> !skipAppJars || !isApplicationJar(path))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            String message = "Failed to scan plugins directory " + dir;
            logger.log(Level.WARNING, message, e);
            failures.add(message + ": " + e.getMessage());
            return List.of();
        }
    }

    private static boolean isApplicationJar(Path jar) {
        String name = jar.getFileName().toString();
        return name.contains("jylos") || name.contains("uber");
    }

    /**
     * Deletes from the user's folder the JARs older versions of Jylos copied there (its
     * {@link RetiredPlugins#BUNDLED_COPY_MARKER} is present) whose name the installation has.
     */
    private static List<String> removeOldBundledCopies(Path userDir, Set<String> coreJarNames) {
        List<String> done = new ArrayList<>();
        if (coreJarNames.isEmpty() || !Files.exists(userDir.resolve(RetiredPlugins.BUNDLED_COPY_MARKER))) {
            return done;
        }
        for (String name : coreJarNames) {
            Path copy = userDir.resolve(name);
            try {
                if (Files.deleteIfExists(copy)) {
                    done.add("Removed an old copy of a plugin that comes with Jylos: " + copy);
                }
            } catch (IOException e) {
                logger.warning("Could not remove old plugin copy " + copy + ": " + e.getMessage());
            }
        }
        return done;
    }

    /** Closes the classloader of a JAR whose plugin id was already loaded from another JAR. */
    private static void releaseDuplicate(Path jar) {
        Path normalized = jar.toAbsolutePath().normalize();
        URLClassLoader classLoader = activeClassLoadersByJarPath.get(normalized);
        closePluginClassLoaderForJar(normalized);
        pluginJarPathsById.entrySet().removeIf(entry -> entry.getValue().equals(normalized));
        if (classLoader != null) {
            activeClassLoadersByPluginId.values().removeIf(loader -> loader == classLoader);
        }
    }

    /** The JAR a loaded plugin came from, or {@code null}. */
    static Path jarOf(String pluginId) {
        return pluginJarPathsById.get(pluginId);
    }

    /** How many plugin classloaders are open. */
    static int openClassLoaderCount() {
        return activeClassLoaders.size();
    }

    /**
     * The installation's plugin folders, where Jylos's own plugins are: next to the application
     * JAR, its {@code plugins/} folder and the installation's {@code plugins/} (where jpackage's
     * {@code --app-content} puts them); when developing, the repository's {@code plugins/}.
     */
    private static List<Path> corePluginDirs() {
        List<Path> paths = new ArrayList<>();
        Path appDir = getApplicationDirectory();
        if (appDir != null) {
            paths.add(appDir);
            paths.add(appDir.resolve(PLUGINS_DIR));
            Path installDir = appDir.getParent();
            if (installDir != null) {
                paths.add(installDir.resolve(PLUGINS_DIR));
            }
        }
        // Never for a packaged build, where the working directory is wherever it was launched.
        if (AppDataDirectory.layout().mode() == AppDirectories.Mode.DEVELOPMENT) {
            paths.add(Paths.get(PLUGINS_DIR));
        }
        return paths;
    }

    /**
     * Gets the directory containing the application JAR (for jpackage/bundled
     * apps).
     * Returns null if not running from a JAR.
     */
    private static Path getApplicationDirectory() {
        try {
            URL location = PluginLoader.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            Path path = Paths.get(location.toURI());
            if (Files.isRegularFile(path)) {
                return path.getParent();
            }
            if (Files.isDirectory(path)) {
                return path;
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            logger.fine("Could not determine application directory: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gets the primary plugins directory (for UI display, creating dirs, etc.).
     * Uses AppData so users can add plugins in a known location.
     */
    private static Path getPluginsDirectory() {
        return Paths.get(AppDataDirectory.getBaseDirectory(), PLUGINS_DIR);
    }

    /**
     * Checks whether a plugin's declared host API version matches what this
     * build supports.
     *
     * @param pluginApiVersion the value returned by {@link Plugin#getHostApiVersion()}
     * @return true if the plugin can be loaded against this build's plugin API
     */
    public static boolean isHostApiCompatible(String pluginApiVersion) {
        return SUPPORTED_HOST_API_VERSION.equals(pluginApiVersion);
    }

    /**
     * Loads a plugin from a JAR file.
     *
     * @param jarPath The path to the JAR file
     * @return The plugin instance, or null if loading failed
     */
    private static Plugin loadPluginFromJar(Path jarPath) {
        URLClassLoader classLoader = null;
        try {
            URL jarUrl = jarPath.toUri().toURL();
            classLoader = new PluginClassLoader(
                    new URL[] { jarUrl },
                    PluginLoader.class.getClassLoader());

            // Try to read plugin class from manifest
            String pluginClassName = getPluginClassFromManifest(jarPath);

            // If not in manifest, try to auto-detect by scanning JAR
            if (pluginClassName == null) {
                pluginClassName = autoDetectPluginClass(jarPath, classLoader);
            }

            if (pluginClassName == null) {
                logger.warning("Could not determine plugin class for " + jarPath.getFileName());
                try {
                    classLoader.close();
                } catch (IOException e) {
                    logger.fine("Could not close classloader after unresolved plugin class for "
                            + jarPath.getFileName() + ": " + e.getMessage());
                }
                return null;
            }

            // Load and instantiate the plugin class
            Class<?> pluginClass = classLoader.loadClass(pluginClassName);

            if (!Plugin.class.isAssignableFrom(pluginClass)) {
                logger.warning("Class " + pluginClassName + " does not implement Plugin interface");
                try {
                    classLoader.close();
                } catch (IOException e) {
                    logger.fine("Could not close classloader for invalid plugin class '" + pluginClassName
                            + "': " + e.getMessage());
                }
                return null;
            }

            Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();
            if (RetiredPlugins.isRetired(plugin.getId())) {
                logger.info("Skipping " + jarPath.getFileName() + ": plugin '" + plugin.getId()
                        + "' is no longer part of Jylos");
                closeQuietly(classLoader);
                return null;
            }

            String pluginApiVersion = plugin.getHostApiVersion();
            if (!isHostApiCompatible(pluginApiVersion)) {
                logger.warning(String.format(
                        "Plugin '%s' (%s) targets host API version %s, but this build supports version %s — skipping.",
                        plugin.getId(), jarPath.getFileName(), pluginApiVersion, SUPPORTED_HOST_API_VERSION));
                closeQuietly(classLoader);
                return null;
            }

            // Keep classloader open - don't close it, or inner classes won't be accessible
            // at runtime
            // The classloader will be closed when the application shuts down
            activeClassLoaders.add(classLoader);
            Path normalizedJar = jarPath.toAbsolutePath().normalize();
            activeClassLoadersByPluginId.putIfAbsent(plugin.getId(), classLoader);
            activeClassLoadersByJarPath.put(normalizedJar, classLoader);
            pluginJarPathsById.putIfAbsent(plugin.getId(), normalizedJar);
            return plugin;

        } catch (Throwable t) {
            // Catch Throwable, not just Exception: a plugin jar compiled for a newer
            // JVM throws UnsupportedClassVersionError, and missing transitive classes
            // throw NoClassDefFoundError — both are Errors. One bad jar must never
            // abort plugin loading or crash startup.
            if (t instanceof NoSuchMethodError || t instanceof AbstractMethodError || t instanceof NoClassDefFoundError) {
                logger.log(Level.WARNING, "Plugin " + jarPath.getFileName()
                        + " looks incompatible with this Jylos version (likely built against an older/newer "
                        + "PluginContext API) — skipping. Cause: " + t, t);
            } else {
                logger.log(Level.WARNING, "Error loading plugin from " + jarPath.getFileName(), t);
            }
            closeQuietly(classLoader);
            return null;
        }
    }

    /**
     * Reads the plugin class name from the JAR manifest.
     * 
     * @param jarPath The path to the JAR file
     * @return The plugin class name, or null if not found
     */
    private static String getPluginClassFromManifest(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            var manifest = jarFile.getManifest();
            if (manifest == null) {
                return null;
            }
            return manifest.getMainAttributes().getValue("Plugin-Class");
        } catch (Exception e) {
            // Manifest might not exist or not have Plugin-Class entry
            return null;
        }
    }

    /**
     * Auto-detects the plugin class by scanning the JAR for classes implementing
     * Plugin.
     * 
     * @param jarPath     The path to the JAR file
     * @param classLoader The class loader to use
     * @return The plugin class name, or null if not found
     */
    private static String autoDetectPluginClass(Path jarPath, URLClassLoader classLoader) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // Skip directories and non-class files
                if (name.endsWith("/") || !name.endsWith(".class")) {
                    continue;
                }

                // Convert path to class name
                String className = name.replace("/", ".").replace(".class", "");

                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    if (Plugin.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                        return className;
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // Skip classes that can't be loaded (dependencies might be missing)
                    continue;
                }
            }
        } catch (Exception e) {
            logger.warning("Error scanning JAR for plugin class: " + e.getMessage());
        }

        return null;
    }

    /**
     * Gets the plugins directory path as a File.
     * Useful for UI components that need to show the directory to users.
     * 
     * @return The plugins directory as a File
     */
    public static File getPluginsDirectoryFile() {
        return getPluginsDirectory().toFile();
    }

    /**
     * Copies a user-selected plugin JAR into the primary plugins directory.
     *
     * <p>The original file is left untouched. If another JAR with the same file name
     * already exists, a numeric suffix is appended instead of overwriting it.</p>
     *
     * @param sourceJar plugin JAR selected by the user
     * @return copied JAR path inside the primary plugins directory
     * @throws IOException if the file is not a readable JAR or cannot be copied
     */
    public static Path installPluginJar(Path sourceJar) throws IOException {
        if (sourceJar == null || !Files.isRegularFile(sourceJar)) {
            throw new IOException("Plugin JAR not found");
        }
        String fileName = sourceJar.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".jar")) {
            throw new IOException("Plugin file must be a .jar");
        }

        Path pluginsDir = getPluginsDirectory();
        Files.createDirectories(pluginsDir);

        Path source = sourceJar.toAbsolutePath().normalize();
        if (source.getParent() != null && source.getParent().equals(pluginsDir.toAbsolutePath().normalize())) {
            return source;
        }
        Path target = uniquePluginPath(pluginsDir, fileName);

        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        logger.info("Installed plugin JAR: " + target);
        return target;
    }

    /**
     * Loads a plugin from an explicit JAR path.
     *
     * @param jarPath JAR file to load
     * @return plugin instance, or {@code null} if the JAR is not a valid plugin
     */
    public static Plugin loadPluginJar(Path jarPath) {
        return loadPluginFromJar(jarPath);
    }

    /**
     * Removes the loaded plugin JAR from the primary user plugins directory.
     *
     * <p>The caller must shut the plugin down first. This method refuses to delete
     * JARs outside the user plugins directory so packaged application files are not
     * modified by the plugin manager.</p>
     *
     * @param pluginId plugin identifier
     * @return deleted JAR path
     * @throws IOException if the plugin file is unknown, outside the user plugins
     *                     directory, or cannot be deleted
     */
    public static Path deletePluginJar(String pluginId) throws IOException {
        Path jarPath = pluginJarPathsById.get(pluginId);
        if (jarPath == null) {
            throw new IOException("Plugin JAR location is not known: " + pluginId);
        }

        Path pluginsDir = getPluginsDirectory().toAbsolutePath().normalize();
        Path normalizedJar = jarPath.toAbsolutePath().normalize();
        if (!normalizedJar.startsWith(pluginsDir)) {
            throw new IOException("Plugin is not installed in the user plugins directory: " + pluginId);
        }

        closePluginClassLoaderForJar(normalizedJar);
        activeClassLoadersByPluginId.remove(pluginId);
        Files.deleteIfExists(normalizedJar);
        pluginJarPathsById.remove(pluginId);
        logger.info("Deleted plugin JAR: " + normalizedJar);
        return normalizedJar;
    }

    /**
     * Releases a plugin JAR that was loaded but then rejected by the manager.
     *
     * @param jarPath plugin JAR path
     */
    public static void releasePluginJar(Path jarPath) {
        if (jarPath == null) {
            return;
        }
        Path normalizedJar = jarPath.toAbsolutePath().normalize();
        URLClassLoader classLoader = activeClassLoadersByJarPath.get(normalizedJar);
        closePluginClassLoaderForJar(normalizedJar);
        pluginJarPathsById.entrySet().removeIf(entry -> entry.getValue().equals(normalizedJar));
        if (classLoader != null) {
            activeClassLoadersByPluginId.entrySet().removeIf(entry -> entry.getValue() == classLoader);
        }
    }

    /**
     * Returns whether the plugin is backed by a JAR in the primary plugins
     * directory and can therefore be removed by the plugin manager.
     *
     * @param pluginId plugin identifier
     * @return {@code true} when the manager may delete the plugin JAR
     */
    public static boolean isPluginRemovable(String pluginId) {
        Path jarPath = pluginJarPathsById.get(pluginId);
        if (jarPath == null) {
            return false;
        }
        Path pluginsDir = getPluginsDirectory().toAbsolutePath().normalize();
        return jarPath.toAbsolutePath().normalize().startsWith(pluginsDir);
    }

    private static Path uniquePluginPath(Path pluginsDir, String fileName) {
        Path candidate = pluginsDir.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int suffix = 1;
        do {
            candidate = pluginsDir.resolve(base + "-" + suffix + ext);
            suffix++;
        } while (Files.exists(candidate));
        return candidate;
    }

    /**
     * Closes all active classloaders.
     * Should be called when the application shuts down.
     */
    public static void closeAllClassLoaders() {
        for (URLClassLoader classLoader : activeClassLoaders) {
            try {
                classLoader.close();
            } catch (IOException e) {
                logger.warning("Error closing classloader: " + e.getMessage());
            }
        }
        activeClassLoaders.clear();
        activeClassLoadersByPluginId.clear();
        activeClassLoadersByJarPath.clear();
        pluginJarPathsById.clear();
    }

    private static void closePluginClassLoaderForJar(Path jarPath) {
        URLClassLoader classLoader = activeClassLoadersByJarPath.remove(jarPath);
        if (classLoader == null) {
            return;
        }
        closeQuietly(classLoader);
        activeClassLoaders.remove(classLoader);
    }

    private static void closeQuietly(URLClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (IOException e) {
            logger.fine("Could not close plugin classloader: " + e.getMessage());
        }
    }
}
