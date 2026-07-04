package com.example.jylos.ui.theme;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.ui.preferences.UiPreferencesStore;

import javafx.scene.Scene;
import javafx.scene.web.WebView;

/**
 * Theme selection, OS-theme detection and stylesheet application.
 */
public final class ThemeCommand {

    private static final Logger logger = LoggerConfig.getLogger(ThemeCommand.class);
    private static final String LIGHT_STYLESHEET = "/com/example/jylos/ui/css/modern-theme.css";
    private static final String DARK_STYLESHEET = "/com/example/jylos/ui/css/dark-theme.css";

    public record SystemThemeResult(String currentTheme, String detectedTheme) {
    }

    public record ThemeApplicationResult(String requestedTheme, String appliedVariant, boolean externalApplied) {
    }

    public String setLightTheme(Preferences prefs) {
        if (prefs != null) {
            prefs.put("theme", "light");
        }
        return "light";
    }

    public String setDarkTheme(Preferences prefs) {
        if (prefs != null) {
            prefs.put("theme", "dark");
        }
        return "dark";
    }

    public SystemThemeResult setSystemTheme(Preferences prefs) {
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

    public String detectSystemTheme() {
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

    public String resolveThemeToApply(String currentTheme) {
        if ("system".equalsIgnoreCase(currentTheme)) {
            return detectSystemTheme();
        }
        return "dark".equalsIgnoreCase(currentTheme) ? "dark" : "light";
    }

    public boolean isDarkThemeActive(String currentTheme) {
        return "dark".equalsIgnoreCase(resolveThemeToApply(currentTheme));
    }

    private void clearApplicationStylesheets(Scene scene) {
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
                || stylesheetUrl.contains("/snippets/")
                || stylesheetUrl.contains("jylos/ui/css");
    }

    private void applyRootThemeClass(Scene scene, String variant) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        var classes = scene.getRoot().getStyleClass();
        classes.removeAll("theme-dark", "theme-light");
        classes.add("dark".equalsIgnoreCase(variant) ? "theme-dark" : "theme-light");
    }

    private void appendSnippets(Scene scene, List<String> snippetCssUris) {
        if (snippetCssUris == null) {
            return;
        }
        for (String uri : snippetCssUris) {
            if (uri != null && !uri.isBlank() && !scene.getStylesheets().contains(uri)) {
                scene.getStylesheets().add(uri);
            }
        }
    }

    public ThemeApplicationResult applyTheme(
            Scene scene,
            String currentTheme,
            String themeSource,
            String externalThemeId,
            ThemeCatalog themeCatalog,
            Class<?> resourceAnchor,
            WebView previewWebView,
            Runnable refreshPreview,
            List<String> snippetCssUris) {
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
                String variant = external.darkLike() ? "dark" : "light";
                applyRootThemeClass(scene, variant);
                appendSnippets(scene, snippetCssUris);
                ensurePreviewThemeClass(previewWebView);
                if (refreshPreview != null) {
                    refreshPreview.run();
                }
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
        applyRootThemeClass(scene, variant);
        appendSnippets(scene, snippetCssUris);
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

    public boolean resolveDarkUi(String currentTheme, String themeSource, ThemeCatalog.ThemeDescriptor external) {
        if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(effectiveThemeSource(currentTheme, themeSource))
                && external != null) {
            return external.darkLike();
        }
        return isDarkThemeActive(currentTheme);
    }

    public static String effectiveThemeSource(String currentTheme, String themeSource) {
        if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(themeSource)) {
            return UiPreferencesStore.THEME_SOURCE_EXTERNAL;
        }
        return UiPreferencesStore.THEME_SOURCE_BUILTIN;
    }

    public static boolean isSystemBuiltinMode(String currentTheme, String themeSource) {
        return "system".equalsIgnoreCase(currentTheme)
                && !UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(themeSource);
    }

    private void ensurePreviewThemeClass(WebView previewWebView) {
        if (previewWebView != null && !previewWebView.getStyleClass().contains("webview-theme")) {
            previewWebView.getStyleClass().add("webview-theme");
        }
    }
}
