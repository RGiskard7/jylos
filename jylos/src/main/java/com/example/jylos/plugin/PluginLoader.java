package com.example.jylos.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.AppDataDirectory;
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
 * @since 4.4.0
 */
public class PluginLoader {

    private static final Logger logger = LoggerConfig.getLogger(PluginLoader.class);
    private static final String PLUGINS_DIR = "plugins";

    // Keep classloaders open so inner classes remain accessible
    private static final List<URLClassLoader> activeClassLoaders = new ArrayList<>();

    /**
     * Detailed report for plugin loading operations.
     * Provides loaded plugins and non-fatal load failures.
     */
    public static final class PluginLoadReport {
        private final List<Plugin> plugins;
        private final List<String> failures;

        public PluginLoadReport(List<Plugin> plugins, List<String> failures) {
            this.plugins = plugins;
            this.failures = failures;
        }

        public List<Plugin> getPlugins() {
            return plugins;
        }

        public List<String> getFailures() {
            return failures;
        }
    }

    /**
     * Scans the plugins directory and loads all available plugins.
     * Checks multiple locations: app bundle (jpackage), AppData, and relative path.
     * On first run of a packaged app, copies bundled plugins to AppData so they are
     * found.
     * 
     * @return List of loaded plugin instances
     */
    public static List<Plugin> loadExternalPlugins() {
        return loadExternalPluginsWithReport().getPlugins();
    }

    /**
     * Scans and loads plugins and returns a detailed report.
     *
     * This is additive and keeps {@link #loadExternalPlugins()} behavior intact.
     *
     * @return plugin loading report with loaded plugins and failures
     */
    public static PluginLoadReport loadExternalPluginsWithReport() {
        // Copy bundled plugins to AppData on first run (packaged apps: DMG, MSI, etc.)
        copyBundledPluginsToAppDataIfNeeded();

        List<Plugin> plugins = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        Set<Path> scannedDirs = new HashSet<>();

        for (Path pluginsPath : getPluginSearchPaths()) {
            if (pluginsPath == null || !Files.exists(pluginsPath) || !Files.isDirectory(pluginsPath)) {
                continue;
            }
            if (scannedDirs.contains(pluginsPath.normalize())) {
                continue;
            }
            scannedDirs.add(pluginsPath.normalize());

            try {
                Files.list(pluginsPath)
                        .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                        .filter(path -> !path.getFileName().toString().contains("jylos")
                                && !path.getFileName().toString().contains("uber"))
                        .forEach(jarPath -> {
                            try {
                                Plugin plugin = loadPluginFromJar(jarPath);
                                if (plugin != null) {
                                    plugins.add(plugin);
                                    logger.info(
                                            "Loaded external plugin: " + plugin.getName() + " v" + plugin.getVersion());
                                } else {
                                    failures.add("Could not load plugin from " + jarPath.getFileName());
                                }
                            } catch (Exception e) {
                                String message = "Failed to load plugin from " + jarPath.getFileName();
                                logger.log(Level.WARNING, message, e);
                                failures.add(message + ": " + e.getMessage());
                            }
                        });
            } catch (IOException e) {
                String message = "Failed to scan plugins directory " + pluginsPath;
                logger.log(Level.WARNING, message, e);
                failures.add(message + ": " + e.getMessage());
            }
        }

        logger.info("Loaded " + plugins.size() + " external plugin(s)");
        return new PluginLoadReport(plugins, failures);
    }

    /**
     * Gets all paths where plugins may be located.
     * Order: 1) explicit property, 2) app bundle (jpackage), 3) AppData, 4)
     * relative.
     * 
     * @return List of plugin directory paths to search
     */
    private static List<Path> getPluginSearchPaths() {
        List<Path> paths = new ArrayList<>();

        // 1. Explicit system property (highest priority)
        String dataDir = System.getProperty("jylos.data.dir");
        if (dataDir != null && !dataDir.isEmpty()) {
            paths.add(Paths.get(dataDir, PLUGINS_DIR));
        }

        // 2. App bundle directory (jpackage) - plugins alongside main JAR or in
        // plugins/ subdir
        Path appDir = getApplicationDirectory();
        if (appDir != null) {
            paths.add(appDir); // Same dir as JAR (--app-content puts plugins here)
            paths.add(appDir.resolve(PLUGINS_DIR)); // app/plugins/ subdirectory
            // Installation folder plugins/ (Windows: next to .exe; next to app/ folder)
            Path installDir = appDir.getParent();
            if (installDir != null) {
                paths.add(installDir.resolve(PLUGINS_DIR));
            }
        }

        // 3. AppData directory (user data, works for packaged apps)
        paths.add(Paths.get(AppDataDirectory.getBaseDirectory(), PLUGINS_DIR));

        // 4. Relative to CWD (development)
        paths.add(Paths.get(PLUGINS_DIR));

        // Normalize all paths and remove duplicates
        List<Path> uniquePaths = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Path p : paths) {
            try {
                Path absolute = p.toAbsolutePath().normalize();
                if (seen.add(absolute.toString())) {
                    uniquePaths.add(absolute);
                }
            } catch (Exception e) {
                logger.fine("Skipping invalid plugin search path '" + p + "': " + e.getMessage());
            }
        }

        return uniquePaths;
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
        String dataDir = System.getProperty("jylos.data.dir");
        if (dataDir != null && !dataDir.isEmpty()) {
            return Paths.get(dataDir, PLUGINS_DIR);
        }
        return Paths.get(AppDataDirectory.getBaseDirectory(), PLUGINS_DIR);
    }

    /**
     * Copies bundled plugins to AppData on first run.
     * When installing from DMG/MSI/DEB, plugins are packed via --app-content but
     * may not be in a location the classloader finds. Copying to AppData ensures
     * they are found on all platforms.
     */
    private static void copyBundledPluginsToAppDataIfNeeded() {
        Path appDataPlugins = Paths.get(AppDataDirectory.getBaseDirectory(), PLUGINS_DIR);
        try {
            if (!Files.exists(appDataPlugins)) {
                Files.createDirectories(appDataPlugins);
            }
            long existingCount;
            try (var stream = Files.list(appDataPlugins)) {
                existingCount = stream
                        .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                        .count();
            }
            if (existingCount > 0) {
                return;
            }
            Path appDir = getApplicationDirectory();
            if (appDir == null) {
                return;
            }
            Path bundleSource = null;
            if (Files.exists(appDir.resolve(PLUGINS_DIR))) {
                bundleSource = appDir.resolve(PLUGINS_DIR);
            } else {
                bundleSource = appDir;
            }
            List<Path> jars = new ArrayList<>();
            try (var stream = Files.list(bundleSource)) {
                stream
                        .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                        .filter(p -> !p.getFileName().toString().contains("jylos")
                                && !p.getFileName().toString().contains("uber"))
                        .forEach(jars::add);
            }
            if (jars.isEmpty()) {
                return;
            }
            for (Path jar : jars) {
                Path dest = appDataPlugins.resolve(jar.getFileName());
                if (!Files.exists(dest)) {
                    Files.copy(jar, dest);
                    logger.info("Copied bundled plugin to AppData: " + jar.getFileName());
                }
            }
        } catch (IOException e) {
            logger.fine("Could not copy bundled plugins: " + e.getMessage());
        }
    }

    /**
     * Loads a plugin from a JAR file.
     * 
     * @param jarPath The path to the JAR file
     * @return The plugin instance, or null if loading failed
     */
    private static Plugin loadPluginFromJar(Path jarPath) {
        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(
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
            // Keep classloader open - don't close it, or inner classes won't be accessible
            // at runtime
            // The classloader will be closed when the application shuts down
            activeClassLoaders.add(classLoader);
            return plugin;

        } catch (Throwable t) {
            // Catch Throwable, not just Exception: a plugin jar compiled for a newer
            // JVM throws UnsupportedClassVersionError, and missing transitive classes
            // throw NoClassDefFoundError — both are Errors. One bad jar must never
            // abort plugin loading or crash startup.
            logger.log(Level.WARNING, "Error loading plugin from " + jarPath.getFileName(), t);
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
    }
}
