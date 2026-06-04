package com.example.jylos.ui.controller;

/**
 * Theme selection/detection/application and theme catalog scanning.
 *
 * <p>Package-private shell-support types collaborating with {@link MainController}.
 * Extracted from the former single-file shell-services unit into cohesive files.</p>
 */
import com.example.jylos.AppDataDirectory;
import com.example.jylos.config.LoggerConfig;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.util.Duration;

/**
 * Theme selection, OS-theme detection and stylesheet application.
 */
class ThemeCommand {

    private static final Logger logger = LoggerConfig.getLogger(ThemeCommand.class);
    private static final String LIGHT_STYLESHEET = "/com/example/jylos/ui/css/modern-theme.css";
    private static final String DARK_STYLESHEET = "/com/example/jylos/ui/css/dark-theme.css";

    record SystemThemeResult(String currentTheme, String detectedTheme) {
    }

    record ThemeApplicationResult(String requestedTheme, String appliedVariant, boolean externalApplied) {
    }

    String setLightTheme(Preferences prefs) {
        if (prefs != null) {
            prefs.put("theme", "light");
        }
        return "light";
    }

    String setDarkTheme(Preferences prefs) {
        if (prefs != null) {
            prefs.put("theme", "dark");
        }
        return "dark";
    }

    SystemThemeResult setSystemTheme(Preferences prefs) {
        if (prefs != null) {
            prefs.put("theme", "system");
        }
        return new SystemThemeResult("system", detectSystemTheme());
    }

    boolean detectWindowsTheme() {
        try {
            Process process = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        String trimmed = line.trim();
                        if (trimmed.endsWith("0x0")) {
                            return true;
                        }
                        if (trimmed.endsWith("0x1")) {
                            return false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not detect Windows theme via registry", e);
        }
        return false;
    }

    String detectSystemTheme() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean isSystemDark = false;

        if (osName.contains("win")) {
            isSystemDark = detectWindowsTheme();
        } else if (osName.contains("mac")) {
            isSystemDark = detectMacOsDarkTheme();
        } else if (osName.contains("linux") || osName.contains("nix")) {
            isSystemDark = detectLinuxDarkTheme();
        }

        return isSystemDark ? "dark" : "light";
    }

    private boolean detectMacOsDarkTheme() {
        if (detectMacOsDarkThemeViaDefaults()) {
            return true;
        }
        return detectMacOsDarkThemeViaOsascript();
    }

    private boolean detectMacOsDarkThemeViaDefaults() {
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line == null) {
                    return false;
                }
                String normalized = line.trim().replace("(", "").replace(")", "");
                return normalized.equalsIgnoreCase("Dark");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not detect macOS theme via defaults", e);
        }
        return false;
    }

    private boolean detectMacOsDarkThemeViaOsascript() {
        try {
            Process process = new ProcessBuilder("osascript", "-e",
                    "tell application \"System Events\" to tell appearance preferences to return dark mode")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line != null && line.trim().equalsIgnoreCase("true");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not detect macOS theme via osascript", e);
        }
        return false;
    }

    private boolean detectLinuxDarkTheme() {
        try {
            Process process = new ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
                    .redirectErrorStream(true)
                    .start();
            if (process.waitFor(2, TimeUnit.SECONDS)) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String value = reader.readLine();
                    if (value != null && value.toLowerCase().contains("dark")) {
                        return true;
                    }
                }
            } else {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
            // gsettings not available on this desktop
        }
        try {
            Process gtk = new ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
                    .redirectErrorStream(true)
                    .start();
            if (gtk.waitFor(2, TimeUnit.SECONDS)) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(gtk.getInputStream(), StandardCharsets.UTF_8))) {
                    String value = reader.readLine();
                    return value != null && value.toLowerCase().contains("dark");
                }
            } else {
                gtk.destroyForcibly();
            }
        } catch (Exception ignored) {
            // optional
        }
        return false;
    }

    String resolveThemeToApply(String currentTheme) {
        if ("system".equalsIgnoreCase(currentTheme)) {
            return detectSystemTheme();
        }
        return "dark".equalsIgnoreCase(currentTheme) ? "dark" : "light";
    }

    boolean isDarkThemeActive(String currentTheme) {
        return "dark".equalsIgnoreCase(resolveThemeToApply(currentTheme));
    }

    void clearApplicationStylesheets(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().removeIf(this::isManagedThemeStylesheet);
    }

    private boolean isManagedThemeStylesheet(String stylesheetUrl) {
        if (stylesheetUrl == null) {
            return false;
        }
        return stylesheetUrl.contains("modern-theme.css")
                || stylesheetUrl.contains("dark-theme.css")
                || stylesheetUrl.contains("/themes/")
                || stylesheetUrl.contains("jylos/ui/css");
    }

    ThemeApplicationResult applyTheme(
            Scene scene,
            String currentTheme,
            String themeSource,
            String externalThemeId,
            ThemeCatalog themeCatalog,
            Class<?> resourceAnchor,
            WebView previewWebView,
            Runnable refreshPreview) {
        if (scene == null || themeCatalog == null || resourceAnchor == null) {
            logger.warning("Cannot apply theme: scene or catalog not available");
            return new ThemeApplicationResult(currentTheme, "light", false);
        }

        clearApplicationStylesheets(scene);

        String effectiveSource = effectiveThemeSource(currentTheme, themeSource);

        if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(effectiveSource)) {
            ThemeCatalog.ThemeDescriptor external = themeCatalog.findById(
                    themeCatalog.getAvailableThemes(), externalThemeId);
            if (external != null && external.cssPath() != null && !external.cssPath().isBlank()) {
                String baseVariant = external.resolveBaseVariant(currentTheme, this::detectSystemTheme);
                URL baseStylesheet = resolveBuiltinStylesheet(baseVariant, resourceAnchor);
                if (baseStylesheet != null) {
                    scene.getStylesheets().add(baseStylesheet.toExternalForm());
                }
                scene.getStylesheets().add(external.cssPath());
                ensurePreviewThemeClass(previewWebView);
                if (refreshPreview != null) {
                    refreshPreview.run();
                }
                String variant = external.darkLike() ? "dark" : "light";
                logger.info("Applied external theme: " + external.id()
                        + " (base=" + baseVariant + ", variant=" + variant + ")");
                return new ThemeApplicationResult(currentTheme, variant, true);
            }
            logger.warning("External theme '" + externalThemeId + "' not available; falling back to built-in theme.");
        }

        String variant = resolveThemeToApply(currentTheme);
        URL themeResource = resolveBuiltinStylesheet(variant, resourceAnchor);
        if (themeResource == null) {
            logger.warning("Could not load theme stylesheet for: " + currentTheme);
            return new ThemeApplicationResult(currentTheme, variant, false);
        }

        scene.getStylesheets().add(themeResource.toExternalForm());
        ensurePreviewThemeClass(previewWebView);
        if (refreshPreview != null) {
            refreshPreview.run();
        }
        logger.info("Theme applied: requested=" + currentTheme + ", variant=" + variant);
        return new ThemeApplicationResult(currentTheme, variant, false);
    }

    URL resolveBuiltinStylesheet(String variant, Class<?> resourceAnchor) {
        if (resourceAnchor == null || variant == null) {
            return null;
        }
        String path = "dark".equalsIgnoreCase(variant) ? DARK_STYLESHEET : LIGHT_STYLESHEET;
        return resourceAnchor.getResource(path);
    }

    /**
     * Resolves whether the UI should use dark accents (preview HTML, overlays, grid cards).
     */
    boolean resolveDarkUi(String currentTheme, String themeSource, ThemeCatalog.ThemeDescriptor external) {
        if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(effectiveThemeSource(currentTheme, themeSource))
                && external != null) {
            return external.darkLike();
        }
        return isDarkThemeActive(currentTheme);
    }

    /**
     * Preferences {@code ui.theme.source} wins over the View menu light/dark/system choice.
     * External themes stay active even when the menu is on System (menu only affects
     * {@link ThemeCatalog.ThemeDescriptor#resolveBaseVariant} when {@code base=auto}).
     */
    static String effectiveThemeSource(String currentTheme, String themeSource) {
        if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(themeSource)) {
            return UiPreferencesStore.THEME_SOURCE_EXTERNAL;
        }
        return UiPreferencesStore.THEME_SOURCE_BUILTIN;
    }

    static boolean isSystemBuiltinMode(String currentTheme, String themeSource) {
        return "system".equalsIgnoreCase(currentTheme)
                && !UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(themeSource);
    }

    private void ensurePreviewThemeClass(WebView previewWebView) {
        if (previewWebView != null && !previewWebView.getStyleClass().contains("webview-theme")) {
            previewWebView.getStyleClass().add("webview-theme");
        }
    }
}


// ===== ThemeCatalog =====
/**
 * Scans built-in and external theme descriptors.
 */
class ThemeCatalog {

    private static final Logger logger = Logger.getLogger(ThemeCatalog.class.getName());

    public record ThemeDescriptor(String id, String name, String source, String cssPath, boolean darkLike, String base) {

        /**
         * Resolves the built-in base layer (light/dark) under an external theme overlay.
         *
         * @param menuTheme light, dark, system, or other value from preferences menu
         * @param systemDetector supplies OS light/dark when needed
         */
        public String resolveBaseVariant(String menuTheme, Supplier<String> systemDetector) {
            String configured = base != null ? base.trim().toLowerCase() : "";
            if ("system".equals(configured)) {
                return systemDetector.get();
            }
            if ("light".equals(configured) || "dark".equals(configured)) {
                return configured;
            }
            if ("system".equalsIgnoreCase(menuTheme)) {
                return systemDetector.get();
            }
            if ("dark".equalsIgnoreCase(menuTheme)) {
                return "dark";
            }
            if ("light".equalsIgnoreCase(menuTheme)) {
                return "light";
            }
            return darkLike ? "dark" : "light";
        }
    }

    public List<ThemeDescriptor> getAvailableThemes() {
        List<ThemeDescriptor> out = new ArrayList<>();
        out.add(new ThemeDescriptor("light", "Light", "builtin",
                "/com/example/jylos/ui/css/modern-theme.css", false, "light"));
        out.add(new ThemeDescriptor("dark", "Dark", "builtin",
                "/com/example/jylos/ui/css/dark-theme.css", true, "dark"));
        out.add(new ThemeDescriptor("system", "System", "builtin",
                "/com/example/jylos/ui/css/modern-theme.css", false, "system"));

        List<Path> baseDirs = List.of(
                Paths.get(AppDataDirectory.getBaseDirectory(), "themes"),
                Paths.get(System.getProperty("user.dir", "."), "themes"),
                Paths.get(System.getProperty("user.dir", "."), "..", "themes").normalize());

        Map<String, ThemeDescriptor> unique = new LinkedHashMap<>();
        for (Path baseDir : baseDirs) {
            if (baseDir == null || !Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
                continue;
            }
            try (var dirs = Files.list(baseDir).filter(Files::isDirectory)) {
                dirs.forEach(dir -> {
                    // Skip "_"-prefixed folders (e.g. _template) and hidden dirs.
                    String dirName = dir.getFileName().toString();
                    if (dirName.startsWith("_") || dirName.startsWith(".")) {
                        return;
                    }
                    ThemeDescriptor descriptor = readThemeDescriptor(dir);
                    if (descriptor != null) {
                        unique.putIfAbsent(descriptor.id(), descriptor);
                    }
                });
            } catch (Exception e) {
                logger.warning("Could not read themes directory '" + baseDir + "': " + e.getMessage());
            }
        }

        List<ThemeDescriptor> external = new ArrayList<>(unique.values());
        external.sort(Comparator.comparing(ThemeDescriptor::name, String.CASE_INSENSITIVE_ORDER));
        out.addAll(external);
        return out;
    }

    public ThemeDescriptor findById(List<ThemeDescriptor> themes, String id) {
        if (themes == null || id == null || id.isBlank()) {
            return null;
        }
        for (ThemeDescriptor t : themes) {
            if (id.equals(t.id())) {
                return t;
            }
        }
        return null;
    }

    private ThemeDescriptor readThemeDescriptor(Path dir) {
        Path propsFile = dir.resolve("theme.properties");
        if (!Files.exists(propsFile)) {
            return null;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(propsFile.toFile())) {
            p.load(in);
        } catch (Exception e) {
            logger.warning("Invalid theme descriptor in " + dir + ": " + e.getMessage());
            return null;
        }

        String id = valueOrDefault(p.getProperty("id"), dir.getFileName().toString().trim());
        String name = valueOrDefault(p.getProperty("name"), id);
        String css = valueOrDefault(p.getProperty("css"), "theme.css");
        boolean darkLike = Boolean.parseBoolean(valueOrDefault(p.getProperty("darkLike"), "false"));
        String base = valueOrDefault(p.getProperty("base"), "auto");

        Path cssPath = dir.resolve(css).normalize();
        if (!Files.exists(cssPath) || !Files.isRegularFile(cssPath)) {
            logger.warning("Theme '" + id + "' ignored (css not found): " + cssPath);
            return null;
        }
        return new ThemeDescriptor(id, name, "external", cssPath.toUri().toString(), darkLike, base);
    }

    private String valueOrDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}


/**
 * Polls the OS appearance while the app is in built-in "system" theme mode.
 */
class SystemThemeMonitor {

    private static final Logger logger = LoggerConfig.getLogger(SystemThemeMonitor.class);
    private static final Duration POLL_INTERVAL = Duration.seconds(1.5);

    private final Supplier<String> detector;
    private final Runnable onOsThemeChange;
    private Timeline timeline;
    private String lastDetected = "";

    SystemThemeMonitor(Supplier<String> detector, Runnable onOsThemeChange) {
        this.detector = detector;
        this.onOsThemeChange = onOsThemeChange;
    }

    void setActive(boolean active) {
        stop();
        if (!active) {
            return;
        }
        lastDetected = detector.get();
        timeline = new Timeline(new KeyFrame(POLL_INTERVAL, event -> poll()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        logger.fine("System theme monitor started (detected=" + lastDetected + ")");
    }

    void refreshOnFocus() {
        if (timeline == null) {
            return;
        }
        String detected = detector.get();
        if (!detected.equals(lastDetected)) {
            lastDetected = detected;
            logger.info("OS theme changed on focus (now " + detected + ")");
            Platform.runLater(onOsThemeChange);
        }
    }

    private void poll() {
        String detected = detector.get();
        if (detected.equals(lastDetected)) {
            return;
        }
        lastDetected = detected;
        logger.info("OS theme changed (now " + detected + ")");
        Platform.runLater(onOsThemeChange);
    }

    void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }
}

