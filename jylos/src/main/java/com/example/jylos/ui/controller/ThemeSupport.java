package com.example.jylos.ui.controller;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import com.example.jylos.ui.UiDialogs;
import com.example.jylos.ui.preferences.UiPreferencesStore;
import com.example.jylos.ui.theme.CssSnippetCatalog;
import com.example.jylos.ui.theme.SystemThemeMonitor;
import com.example.jylos.ui.theme.ThemeCatalog;
import com.example.jylos.ui.theme.ThemeCommand;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Window;

/**
 * The window's theme: light, dark or following the system, a built-in or an external theme,
 * and the enabled CSS snippets on top. Applies it to the window's scene and to dialogs, keeps
 * the View menu's theme items in step, and follows the system's appearance while "system" is
 * chosen. What else depends on the theme (editor, notes grid, graph) is redrawn through the
 * {@code refreshDependents} callback.
 */
final class ThemeSupport {

    private final ThemeCommand command = new ThemeCommand();
    private final ThemeCatalog catalog = new ThemeCatalog();
    private final CssSnippetCatalog snippetCatalog = new CssSnippetCatalog();
    private final SystemThemeMonitor systemMonitor = new SystemThemeMonitor(command::detectSystemTheme,
            this::applyAndRefresh);

    private Preferences prefs;
    private java.util.function.BooleanSupplier ready = () -> false;
    private Supplier<Scene> mainScene = () -> null;
    private Supplier<javafx.scene.Node> previewWebView = () -> null;
    private Supplier<Runnable> previewRefresh = () -> null;
    private Runnable refreshDependents = () -> { };
    private Supplier<ToolbarController> toolbar = () -> null;
    private AppSettings appSettings;
    private Function<String, String> i18n = key -> key;
    private Consumer<String> status = message -> { };

    private String currentTheme = "light";
    private String source = UiPreferencesStore.THEME_SOURCE_BUILTIN;
    private String externalId = "";
    /** Filenames of the CSS snippets the user has enabled; layered over the theme. */
    private Set<String> enabledSnippets = Set.of();
    /** The main window's scene, given before the window is shown (see {@link #applyBeforeShow}). */
    private Scene windowScene;
    private ToggleGroup menuGroup;

    /**
     * @param prefs             where the chosen theme is kept
     * @param mainScene         the scene the main split pane is in, or {@code null} while it is
     *                          not in one (the Explorer layout)
     * @param previewWebView    the reading view, styled with the theme
     * @param previewRefresh    redraws the reading view in the new theme; {@code null} when no
     *                          note is open
     * @param refreshDependents redraws what else depends on the theme
     * @param ready             whether the window's controls are in place to be themed
     */
    void wire(Preferences prefs, java.util.function.BooleanSupplier ready, Supplier<Scene> mainScene, Supplier<javafx.scene.Node> previewWebView,
            Supplier<Runnable> previewRefresh, Runnable refreshDependents, Supplier<ToolbarController> toolbar,
            AppSettings appSettings, Function<String, String> i18n, Consumer<String> status) {
        this.prefs = prefs;
        this.ready = ready;
        this.mainScene = mainScene;
        this.previewWebView = previewWebView;
        this.previewRefresh = previewRefresh;
        this.refreshDependents = refreshDependents;
        this.toolbar = toolbar;
        this.appSettings = appSettings;
        this.i18n = i18n;
        this.status = status;
        this.currentTheme = prefs.get("theme", "light");
    }

    /** The theme chosen in Preferences: built-in or external, and the enabled snippets. */
    void loadPreferences(String themeSource, String externalThemeId, Set<String> snippets) {
        this.source = themeSource;
        this.externalId = externalThemeId;
        this.enabledSnippets = snippets != null ? snippets : Set.of();
    }

    String source() {
        return source;
    }

    String externalId() {
        return externalId;
    }

    Set<String> enabledSnippets() {
        return enabledSnippets;
    }

    ThemeCatalog catalog() {
        return catalog;
    }

    CssSnippetCatalog snippetCatalog() {
        return snippetCatalog;
    }

    /** Builds the theme items (light / dark / system) of the View menu. */
    void initializeMenu() {
        menuGroup = appSettings.initializeThemeMenu(toolbar.get(), currentTheme, command::detectSystemTheme,
                theme -> currentTheme = theme, this::apply);
    }

    void updateMenuSelection() {
        appSettings.updateThemeMenuSelection(toolbar.get(), menuGroup, currentTheme);
    }

    void setLight() {
        currentTheme = command.setLightTheme(prefs);
        chosen(i18n.apply("status.theme_light"));
    }

    void setDark() {
        currentTheme = command.setDarkTheme(prefs);
        chosen(i18n.apply("status.theme_dark"));
    }

    void setSystem() {
        ThemeCommand.SystemThemeResult result = command.setSystemTheme(prefs);
        currentTheme = result.currentTheme();
        chosen(java.text.MessageFormat.format(i18n.apply("status.theme_system"), result.detectedTheme()));
    }

    private void chosen(String message) {
        updateMenuSelection();
        applyAndRefresh();
        status.accept(message);
    }

    /** Applies the theme's stylesheets to the window and makes dialogs follow them. */
    void apply() {
        if (!ready.getAsBoolean()) {
            return;
        }
        // In the Explorer layout the main split pane is not part of the scene: fall back to the
        // window's scene, or the theme would wait until after the first paint and the window
        // would flash in the light theme.
        Scene scene = mainScene.get() != null ? mainScene.get() : windowScene;
        if (scene == null) {
            Platform.runLater(this::apply);
            return;
        }
        command.applyTheme(scene, currentTheme, source, externalId, catalog, getClass(), previewWebView.get(),
                previewRefresh.get(), snippetCatalog.resolveEnabledUris(enabledSnippets));
        // JavaFX dialogs don't inherit the scene's stylesheets on their own.
        UiDialogs.syncFromScene(scene);
    }

    /**
     * Applies the theme once the scene is attached but before the window is first painted,
     * avoiding a flash of the default stylesheet.
     */
    void applyBeforeShow(Scene scene) {
        windowScene = scene;
        apply();
        syncSystemMonitoring();
    }

    /** Applies the theme, redraws what depends on it and follows the system's appearance if chosen. */
    void applyAndRefresh() {
        apply();
        refreshDependents.run();
        syncSystemMonitoring();
    }

    /** Follows the system's appearance only while the built-in "system" mode is chosen. */
    void syncSystemMonitoring() {
        systemMonitor.setActive(ThemeCommand.isSystemBuiltinMode(currentTheme, source));
    }

    /** Checks the system's appearance again whenever {@code window} gains focus in "system" mode. */
    void followSystemOnFocus(Window window) {
        if (window == null) {
            return;
        }
        window.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!Boolean.TRUE.equals(isFocused) || !ThemeCommand.isSystemBuiltinMode(currentTheme, source)) {
                return;
            }
            systemMonitor.refreshOnFocus();
            applyAndRefresh();
        });
    }

    /** Whether the active theme (built-in or external) is a dark one. */
    boolean isDark() {
        ThemeCatalog.ThemeDescriptor external = null;
        if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(ThemeCommand.effectiveThemeSource(currentTheme, source))) {
            external = catalog.findById(catalog.getAvailableThemes(), externalId);
        }
        return command.resolveDarkUi(currentTheme, source, external);
    }

    /** Detected OS appearance: "dark" or "light". */
    String detectSystemTheme() {
        return command.detectSystemTheme();
    }
}
