package com.example.jylos.ui.controller;

import java.io.File;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.example.jylos.AppConfig;
import com.example.jylos.ui.AppIconLoader;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.preferences.UiPreferencesStore;
import com.example.jylos.ui.theme.CssSnippetCatalog;
import com.example.jylos.ui.theme.ThemeCatalog;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Lightweight application dialogs (preferences, about, docs, new tag).
 *
 * <p>Holds its {@link MainController} for i18n strings and status updates, so the
 * caller only passes data and post-action callbacks.</p>
 */
class DialogSupport {

    private static final Logger logger = LoggerConfig.getLogger(DialogSupport.class);

    private final MainController controller;
    private TagService tagService;

    DialogSupport(MainController controller) {
        this.controller = controller;
    }

    void wire(TagService tagService) {
        this.tagService = tagService;
    }

    /** Delegates i18n string resolution to the host {@link MainController}. */
    private String i18n(String key) {
        return controller.getString(key);
    }

    /**
     * The actual storage location to display in preferences: the vault folder in
     * filesystem mode, or the SQLite database file otherwise. Replaces a hardcoded path
     * that did not reflect the real (and configurable) location.
     */
    private String currentStorageLocation() {
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);
        String type = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));
        if ("filesystem".equalsIgnoreCase(type)) {
            String path = prefs.get("filesystem_path", "");
            return path.isBlank() ? i18n("storage.vault") : path;
        }
        return new File(com.example.jylos.AppDataDirectory.getDataDirectory(), "database.db").getAbsolutePath();
    }

    record PreferencesDialogResult(
            boolean autosaveEnabled,
            int autosaveIdleMs,
            String themeSource,
            String externalThemeId,
            int notesPreviewLines,
            int uiFontSize,
            String accentColor,
            Set<String> enabledSnippets) {
    }

    Optional<PreferencesDialogResult> showPreferences(
            UiPreferencesStore.UiPreferencesData current,
            List<ThemeCatalog.ThemeDescriptor> themes,
            CssSnippetCatalog snippetCatalog,
            Set<String> enabledSnippets) {
        Dialog<PreferencesDialogResult> dialog = new Dialog<>();
        dialog.setTitle(i18n("dialog.preferences.title"));
        dialog.setHeaderText(i18n("dialog.preferences.header"));

        ButtonType saveButton = new ButtonType(i18n("action.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButton = new ButtonType(i18n("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, closeButton);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label dbLabel = new Label(i18n("dialog.preferences.storage_location"));
        Label dbPathLabel = new Label(currentStorageLocation());
        dbPathLabel.setStyle("-fx-text-fill: gray;");

        CheckBox autosaveEnabledCheck = new CheckBox(i18n("dialog.preferences.autosave_enabled"));
        autosaveEnabledCheck.setSelected(current.autosaveEnabled());

        Label autosaveIntervalLabel = new Label(i18n("dialog.preferences.autosave_interval"));
        ComboBox<String> autosaveIntervalCombo = new ComboBox<>();
        autosaveIntervalCombo.getItems().addAll("1s", "2s", "5s");
        String autosaveText = current.autosaveIdleMs() <= 1200 ? "1s"
                : current.autosaveIdleMs() <= 3000 ? "2s" : "5s";
        autosaveIntervalCombo.getSelectionModel().select(autosaveText);
        autosaveIntervalCombo.setDisable(!autosaveEnabledCheck.isSelected());
        autosaveEnabledCheck.selectedProperty()
                .addListener((obs, oldVal, newVal) -> autosaveIntervalCombo.setDisable(!newVal));

        Label notesPreviewLinesLabel = new Label(i18n("dialog.preferences.notes_preview_lines"));
        ComboBox<Integer> notesPreviewLinesCombo = new ComboBox<>();
        for (int i = UiPreferencesStore.MIN_NOTES_PREVIEW_LINES; i <= UiPreferencesStore.MAX_NOTES_PREVIEW_LINES; i++) {
            notesPreviewLinesCombo.getItems().add(i);
        }
        notesPreviewLinesCombo.getSelectionModel()
                .select(Integer.valueOf(UiPreferencesStore.clampPreviewLines(current.notesPreviewLines())));

        Label fontSizeLabel = new Label(i18n("dialog.preferences.font_size"));
        ComboBox<Integer> fontSizeCombo = new ComboBox<>();
        for (int i = UiPreferencesStore.MIN_UI_FONT_SIZE; i <= UiPreferencesStore.MAX_UI_FONT_SIZE; i++) {
            fontSizeCombo.getItems().add(i);
        }
        fontSizeCombo.getSelectionModel()
                .select(Integer.valueOf(UiPreferencesStore.clampFontSize(current.uiFontSize())));

        // Accent color: opt-in override of the theme's -fx-accent (Obsidian-style).
        CheckBox customAccentCheck = new CheckBox(i18n("dialog.preferences.accent_custom"));
        javafx.scene.control.ColorPicker accentPicker = new javafx.scene.control.ColorPicker();
        boolean hasCustomAccent = current.accentColor() != null && !current.accentColor().isBlank();
        customAccentCheck.setSelected(hasCustomAccent);
        accentPicker.setDisable(!hasCustomAccent);
        try {
            accentPicker.setValue(javafx.scene.paint.Color.web(
                    hasCustomAccent ? current.accentColor() : "#7c3aed"));
        } catch (IllegalArgumentException ignored) {
            accentPicker.setValue(javafx.scene.paint.Color.web("#7c3aed"));
        }
        customAccentCheck.selectedProperty()
                .addListener((obs, oldVal, newVal) -> accentPicker.setDisable(!newVal));

        Label themeModeLabel = new Label(i18n("dialog.preferences.theme_mode"));
        ComboBox<String> themeModeCombo = new ComboBox<>();
        themeModeCombo.getItems().addAll(i18n("pref.theme.builtin"), i18n("pref.theme.external"));
        themeModeCombo.getSelectionModel().select(
                UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(current.themeSource()) ? 1 : 0);

        Label externalThemeLabel = new Label(i18n("dialog.preferences.external_theme"));
        ComboBox<String> externalThemeCombo = new ComboBox<>();
        externalThemeCombo.getItems().add(i18n("pref.theme.none"));
        int externalSelected = 0;
        if (themes != null) {
            int idx = 1;
            for (ThemeCatalog.ThemeDescriptor descriptor : themes) {
                if (!"external".equals(descriptor.source())) {
                    continue;
                }
                String display = descriptor.name() + " [" + descriptor.id() + "]";
                externalThemeCombo.getItems().add(display);
                if (descriptor.id().equals(current.externalThemeId())) {
                    externalSelected = idx;
                }
                idx++;
            }
        }
        externalThemeCombo.getSelectionModel().select(externalSelected);
        externalThemeCombo.setDisable(themeModeCombo.getSelectionModel().getSelectedIndex() != 1);
        themeModeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            externalThemeCombo.setDisable(themeModeCombo.getSelectionModel().getSelectedIndex() != 1);
        });

        // CSS snippets: passive .css files layered over the theme (Obsidian-style).
        // The selection is held in a live set so the "Reload" rescan keeps the user's
        // current choices even for snippets not yet on disk.
        Set<String> selectedSnippets = new LinkedHashSet<>(
                enabledSnippets != null ? enabledSnippets : Set.of());
        Label snippetsLabel = new Label(i18n("dialog.preferences.css_snippets"));
        Label snippetsHint = new Label(i18n("dialog.preferences.css_snippets_hint"));
        snippetsHint.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        snippetsHint.setWrapText(true);
        VBox snippetsBox = new VBox(6);
        Runnable populateSnippets = () -> {
            snippetsBox.getChildren().clear();
            List<CssSnippetCatalog.SnippetDescriptor> available =
                    snippetCatalog != null ? snippetCatalog.getAvailableSnippets() : List.of();
            if (available.isEmpty()) {
                Label none = new Label(i18n("dialog.preferences.css_snippets_empty"));
                none.setStyle("-fx-text-fill: gray;");
                snippetsBox.getChildren().add(none);
                return;
            }
            for (CssSnippetCatalog.SnippetDescriptor snippet : available) {
                CheckBox box = new CheckBox(snippet.name());
                box.setSelected(selectedSnippets.contains(snippet.name()));
                box.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (Boolean.TRUE.equals(newVal)) {
                        selectedSnippets.add(snippet.name());
                    } else {
                        selectedSnippets.remove(snippet.name());
                    }
                });
                snippetsBox.getChildren().add(box);
            }
        };
        populateSnippets.run();
        Button openSnippetsFolder = new Button(i18n("dialog.preferences.css_snippets_open"));
        openSnippetsFolder.setOnAction(e -> {
            if (snippetCatalog != null) {
                openFolder(snippetCatalog.primaryDirectory().toString());
            }
        });
        Button reloadSnippets = new Button(i18n("dialog.preferences.css_snippets_reload"));
        reloadSnippets.setOnAction(e -> populateSnippets.run());

        content.getChildren().addAll(
                new Label(i18n("dialog.preferences.general_settings")),
                dbLabel, dbPathLabel,
                new Separator(),
                autosaveEnabledCheck,
                new HBox(8, autosaveIntervalLabel, autosaveIntervalCombo),
                new Separator(),
                notesPreviewLinesLabel, notesPreviewLinesCombo,
                fontSizeLabel, fontSizeCombo,
                new Separator(),
                themeModeLabel, themeModeCombo,
                externalThemeLabel, externalThemeCombo,
                new Separator(),
                new HBox(8, customAccentCheck, accentPicker),
                new Separator(),
                snippetsLabel, snippetsHint, snippetsBox,
                new HBox(8, openSnippetsFolder, reloadSnippets));

        // The settings list has outgrown a fixed pane: scroll vertically when needed
        // so no row is ever clipped (no horizontal bar — content fits the width).
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("preferences-scroll");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefSize(500, 620);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButton) {
                return null;
            }
            int autosaveMs = switch (autosaveIntervalCombo.getSelectionModel().getSelectedItem()) {
                case "1s" -> 1000;
                case "5s" -> 5000;
                default -> 2000;
            };
            String themeSource = themeModeCombo.getSelectionModel().getSelectedIndex() == 1
                    ? UiPreferencesStore.THEME_SOURCE_EXTERNAL
                    : UiPreferencesStore.THEME_SOURCE_BUILTIN;
            String externalThemeId = "";
            String selectedExternal = externalThemeCombo.getSelectionModel().getSelectedItem();
            if (selectedExternal != null && selectedExternal.contains("[") && selectedExternal.endsWith("]")) {
                int start = selectedExternal.lastIndexOf('[');
                externalThemeId = selectedExternal.substring(start + 1, selectedExternal.length() - 1).trim();
            }
            if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(themeSource) && externalThemeId.isBlank()
                    && themes != null) {
                for (ThemeCatalog.ThemeDescriptor descriptor : themes) {
                    if ("external".equals(descriptor.source())) {
                        externalThemeId = descriptor.id();
                        break;
                    }
                }
            }
            Integer previewLines = notesPreviewLinesCombo.getSelectionModel().getSelectedItem();
            Integer fontSize = fontSizeCombo.getSelectionModel().getSelectedItem();
            String accent = "";
            if (customAccentCheck.isSelected() && accentPicker.getValue() != null) {
                javafx.scene.paint.Color c = accentPicker.getValue();
                accent = String.format("#%02x%02x%02x",
                        (int) Math.round(c.getRed() * 255),
                        (int) Math.round(c.getGreen() * 255),
                        (int) Math.round(c.getBlue() * 255));
            }
            return new PreferencesDialogResult(
                    autosaveEnabledCheck.isSelected(),
                    autosaveMs,
                    themeSource,
                    externalThemeId,
                    previewLines != null ? previewLines : UiPreferencesStore.DEFAULT_NOTES_PREVIEW_LINES,
                    fontSize != null ? fontSize : UiPreferencesStore.DEFAULT_UI_FONT_SIZE,
                    accent,
                    new LinkedHashSet<>(selectedSnippets));
        });
        return com.example.jylos.ui.UiDialogs.show(dialog);
    }

    /** Opens a folder in the OS file manager (best-effort, mirrors the notes-list reveal). */
    private void openFolder(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", absolutePath).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", absolutePath).start();
            } else {
                new ProcessBuilder("xdg-open", absolutePath).start();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not open snippets folder: " + absolutePath, e);
        }
    }

    void showDocumentation() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(i18n("dialog.documentation.title"));
        alert.setHeaderText(i18n("dialog.documentation.header"));
        alert.setContentText(i18n("dialog.documentation.content"));
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(500, 400);
        com.example.jylos.ui.UiDialogs.show(alert);
    }

    void showKeyboardShortcuts() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(i18n("dialog.shortcuts.title"));
        alert.setHeaderText(i18n("dialog.shortcuts.header"));
        alert.setContentText(i18n("dialog.shortcuts.content"));
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(450, 500);
        com.example.jylos.ui.UiDialogs.show(alert);
    }

    void showAbout() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(i18n("dialog.about.title"));

        ButtonType closeButton = new ButtonType(i18n("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeButton);

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);

        AppIconLoader.load(72).ifPresent(img -> {
            ImageView icon = new ImageView(img);
            icon.setFitWidth(72);
            icon.setFitHeight(72);
            icon.setPreserveRatio(true);
            content.getChildren().add(icon);
        });

        Label titleLabel = new Label(i18n("about.app_name"));
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label versionLabel = new Label(MessageFormat.format(i18n("about.version"), AppConfig.getAppVersion()));
        versionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");

        Label descLabel = new Label(AppConfig.getAppDescription());
        descLabel.setStyle("-fx-font-size: 13px;");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(350);
        descLabel.setAlignment(Pos.CENTER);

        Separator separator = new Separator();
        separator.setPrefWidth(300);

        Label techLabel = new Label(i18n("about.tech_stack"));
        techLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        Label copyrightLabel = new Label(AppConfig.getAppCopyright());
        copyrightLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        Label developerLabel = new Label(i18n("about.developer_credit"));
        developerLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        content.getChildren().addAll(
                titleLabel, versionLabel, descLabel,
                separator,
                techLabel, copyrightLabel, developerLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(400, 380);
        dialog.setOnShown(e -> {
            if (dialog.getDialogPane().getScene() != null
                    && dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                AppIconLoader.load().ifPresent(img -> stage.getIcons().add(img));
            }
        });
        com.example.jylos.ui.UiDialogs.show(dialog);
    }

    void handleNewTag(Runnable refreshTagsAction) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(i18n("dialog.new_tag.title"));
        dialog.setHeaderText(i18n("dialog.new_tag.header"));
        dialog.setContentText(i18n("dialog.new_tag.content"));

        Optional<String> result = com.example.jylos.ui.UiDialogs.show(dialog);
        if (result.isEmpty() || result.get().trim().isEmpty()) {
            return;
        }

        if (tagService == null) {
            controller.updateStatus(i18n("status.error"));
            return;
        }
        try {
            String tagName = result.get().trim();
            if (tagService.tagExists(tagName)) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle(i18n("dialog.tag_exists.title"));
                alert.setHeaderText(i18n("dialog.tag_exists.header"));
                alert.setContentText(i18n("dialog.tag_exists.content"));
                com.example.jylos.ui.UiDialogs.show(alert);
                return;
            }

            tagService.createTag(tagName);
            if (refreshTagsAction != null) {
                refreshTagsAction.run();
            }
            controller.updateStatus(MessageFormat.format(i18n("status.tag_created"), tagName));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create tag", e);
            controller.updateStatus(i18n("status.error") + ": " + e.getMessage());
        }
    }
}

/**
 * App settings flows (theme/language/storage menus and dialogs).
 *
 * <p>Holds its {@link MainController} for i18n strings used in the storage dialog.</p>
 */
class AppSettings {

    private final MainController controller;

    AppSettings(MainController controller) {
        this.controller = controller;
    }

    /** Delegates i18n string resolution to the host {@link MainController}. */
    private String i18n(String key) {
        return controller.getString(key);
    }

    void handleSwitchStorage(Window owner, Preferences prefs) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(i18n("pref.storage"));
        alert.setHeaderText(i18n("pref.storage.header"));
        alert.setContentText(i18n("pref.storage.content"));

        ButtonType sqliteBtn = new ButtonType(i18n("pref.storage.sqlite"));
        ButtonType filesystemBtn = new ButtonType(i18n("pref.storage.filesystem"));
        ButtonType cancelBtn = new ButtonType(i18n("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(sqliteBtn, filesystemBtn, cancelBtn);

        Optional<ButtonType> result = com.example.jylos.ui.UiDialogs.show(alert);
        if (result.isEmpty() || result.get() == cancelBtn) {
            return;
        }

        String newType = "sqlite";
        String customPath = "";
        boolean changed = false;

        String currentType = prefs.get("storage_type", "sqlite");
        String currentPath = prefs.get("filesystem_path", "");

        if (result.get() == sqliteBtn) {
            newType = "sqlite";
            changed = !newType.equals(currentType);
        } else if (result.get() == filesystemBtn) {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(i18n("pref.storage.browse"));

            File selectedDirectory = directoryChooser.showDialog(owner);
            if (selectedDirectory == null) {
                return;
            }
            newType = "filesystem";
            customPath = selectedDirectory.getAbsolutePath();
            changed = !newType.equals(currentType) || !customPath.equals(currentPath);
        }

        if (!changed) {
            return;
        }

        prefs.put("storage_type", newType);
        prefs.put("filesystem_path", customPath);

        Alert restartAlert = new Alert(Alert.AlertType.INFORMATION);
        restartAlert.setTitle(i18n("app.restart_required"));
        restartAlert.setHeaderText(null);
        restartAlert.setContentText(i18n("app.restart_storage_message"));
        com.example.jylos.ui.UiDialogs.show(restartAlert);
    }

    ToggleGroup initializeThemeMenu(ToolbarController toolbarController, String currentTheme,
            Supplier<String> detectSystemTheme,
            Consumer<String> setCurrentTheme,
            Runnable updateThemeMenuSelection,
            Runnable applyTheme) {
        ToggleGroup themeToggleGroup = new ToggleGroup();

        if (toolbarController != null) {
            if (toolbarController.getLightThemeMenuItem() != null) {
                toolbarController.getLightThemeMenuItem().setToggleGroup(themeToggleGroup);
            }
            if (toolbarController.getDarkThemeMenuItem() != null) {
                toolbarController.getDarkThemeMenuItem().setToggleGroup(themeToggleGroup);
            }
            if (toolbarController.getSystemThemeMenuItem() != null) {
                toolbarController.getSystemThemeMenuItem().setToggleGroup(themeToggleGroup);
            }
        }

        if (updateThemeMenuSelection != null) {
            updateThemeMenuSelection.run();
        }

        if (applyTheme != null) {
            Platform.runLater(applyTheme);
        }

        return themeToggleGroup;
    }

    ToggleGroup initializeLanguageMenu(ToolbarController toolbarController, Preferences prefs,
            java.util.function.Consumer<String> onLanguageSelected) {
        ToggleGroup languageToggleGroup = new ToggleGroup();
        if (toolbarController == null) {
            return languageToggleGroup;
        }

        if (toolbarController.getEnglishLangMenuItem() != null) {
            toolbarController.getEnglishLangMenuItem().setToggleGroup(languageToggleGroup);
            toolbarController.getEnglishLangMenuItem().setOnAction(e -> {
                if (onLanguageSelected != null && toolbarController.getEnglishLangMenuItem().isSelected()) {
                    onLanguageSelected.accept("en");
                }
            });
        }
        if (toolbarController.getSpanishLangMenuItem() != null) {
            toolbarController.getSpanishLangMenuItem().setToggleGroup(languageToggleGroup);
            toolbarController.getSpanishLangMenuItem().setOnAction(e -> {
                if (onLanguageSelected != null && toolbarController.getSpanishLangMenuItem().isSelected()) {
                    onLanguageSelected.accept("es");
                }
            });
        }

        String currentLang = prefs.get("language", Locale.getDefault().getLanguage());
        if ("es".equals(currentLang)) {
            if (toolbarController.getSpanishLangMenuItem() != null) {
                toolbarController.getSpanishLangMenuItem().setSelected(true);
            }
        } else {
            if (toolbarController.getEnglishLangMenuItem() != null) {
                toolbarController.getEnglishLangMenuItem().setSelected(true);
            }
        }

        return languageToggleGroup;
    }

    boolean changeLanguage(String lang, Preferences prefs) {
        String currentLang = prefs.get("language", Locale.getDefault().getLanguage());
        if (currentLang.equals(lang)) {
            return false;
        }
        prefs.put("language", lang);
        return true;
    }

    void updateThemeMenuSelection(ToolbarController toolbarController, ToggleGroup themeToggleGroup,
            String currentTheme) {
        if (themeToggleGroup == null || toolbarController == null) {
            return;
        }

        if ("dark".equals(currentTheme)) {
            if (toolbarController.getDarkThemeMenuItem() != null) {
                toolbarController.getDarkThemeMenuItem().setSelected(true);
            }
            return;
        }

        if ("system".equalsIgnoreCase(currentTheme)) {
            if (toolbarController.getSystemThemeMenuItem() != null) {
                toolbarController.getSystemThemeMenuItem().setSelected(true);
            }
            return;
        }

        if (toolbarController.getLightThemeMenuItem() != null) {
            toolbarController.getLightThemeMenuItem().setSelected(true);
        }
    }
}

/**
 * Tag dialogs (manager, add/remove tag on the current note).
 *
 * <p>Holds its {@link MainController} for i18n/status; tag and note persistence
 * are wired explicitly by the shell controller.</p>
 */
class TagManagement {

    private static final Logger logger = LoggerConfig.getLogger(TagManagement.class);

    private final MainController controller;
    private TagService tagService;
    private NoteDAO noteDAO;

    TagManagement(MainController controller) {
        this.controller = controller;
    }

    void wire(TagService tagService, NoteDAO noteDAO) {
        this.tagService = tagService;
        this.noteDAO = noteDAO;
    }

    /** Delegates i18n string resolution to the host {@link MainController}. */
    private String i18n(String key) {
        return controller.getString(key);
    }

    void handleAddTagToNote(Note currentNote, Runnable refreshSidebarTags, Consumer<Note> reloadCurrentNoteTags) {
        if (currentNote == null) {
            controller.updateStatus(i18n("status.no_note"));
            return;
        }

        if (tagService == null || noteDAO == null) {
            controller.updateStatus(i18n("status.error"));
            return;
        }
        try {
            List<Tag> existingTags = tagService.getAllTags();
            List<Tag> noteTags = noteDAO.fetchTags(currentNote.getId());

            List<String> availableTagNames = existingTags.stream()
                    .filter(tag -> noteTags.stream().noneMatch(nt -> nt.getId().equals(tag.getId())))
                    .map(Tag::getTitle)
                    .sorted()
                    .toList();

            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle(i18n("dialog.add_tag.title"));
            dialog.setHeaderText(availableTagNames.isEmpty()
                    ? i18n("dialog.add_tag.header_new")
                    : i18n("dialog.add_tag.header_select"));

            ButtonType addButtonType = new ButtonType(i18n("action.add"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

            VBox content = new VBox(10);
            ComboBox<String> tagComboBox = new ComboBox<>();
            tagComboBox.setEditable(true);
            tagComboBox.getItems().addAll(availableTagNames);
            tagComboBox.setPromptText(i18n("dialog.add_tag.prompt"));
            tagComboBox.setPrefWidth(300);
            content.getChildren().add(new Label(i18n("label.tag")));
            content.getChildren().add(tagComboBox);
            dialog.getDialogPane().setContent(content);

            dialog.setResultConverter(dialogButton -> dialogButton == addButtonType
                    ? tagComboBox.getEditor().getText()
                    : null);

            Optional<String> result = com.example.jylos.ui.UiDialogs.show(dialog);
            if (result.isEmpty() || result.get().trim().isEmpty()) {
                return;
            }

            String tagName = result.get().trim();
            Optional<Tag> existingTag = existingTags.stream()
                    .filter(t -> t.getTitle().equals(tagName))
                    .findFirst();

            Tag tag;
            if (existingTag.isPresent()) {
                tag = existingTag.get();
            } else {
                tag = new Tag(tagName);
                Tag createdTag = tagService.createTag(tag.getTitle());
                tag.setId(createdTag.getId());
            }

            boolean alreadyHasTag = noteDAO.fetchTags(currentNote.getId()).stream()
                    .anyMatch(t -> t.getId().equals(tag.getId()));

            if (alreadyHasTag) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(i18n("dialog.tag_already_assigned.title"));
                alert.setHeaderText(MessageFormat.format(i18n("dialog.tag_already_assigned.header"), tagName));
                com.example.jylos.ui.UiDialogs.show(alert);
                return;
            }

            noteDAO.addTag(currentNote, tag);
            reloadCurrentNoteTags.accept(currentNote);
            refreshSidebarTags.run();
            controller.updateStatus(MessageFormat.format(i18n("status.tag_added"), tagName));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to add tag to note", e);
            controller.updateStatus(i18n("status.tag_add_error"));
        }
    }

    void removeTagFromNote(Note currentNote, Tag tag, Consumer<Note> reloadCurrentNoteTags) {
        if (currentNote == null) {
            controller.updateStatus(i18n("status.no_note_selected"));
            return;
        }
        if (tag == null || tag.getId() == null) {
            controller.updateStatus(i18n("status.invalid_tag"));
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(i18n("dialog.remove_tag.title"));
        confirm.setHeaderText(MessageFormat.format(i18n("dialog.remove_tag.header"), tag.getTitle()));
        confirm.setContentText(i18n("dialog.remove_tag.content"));

        Optional<ButtonType> result = com.example.jylos.ui.UiDialogs.show(confirm);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                if (noteDAO == null) {
                    controller.updateStatus(i18n("status.tag_remove_error"));
                    return;
                }
                noteDAO.removeTag(currentNote, tag);
                reloadCurrentNoteTags.accept(currentNote);
                controller.updateStatus(MessageFormat.format(i18n("status.tag_removed"), tag.getTitle()));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to remove tag from note", e);
                controller.updateStatus(MessageFormat.format(i18n("status.tag_remove_error"), e.getMessage()));
            }
        }
    }

    void showTagsManager(Runnable refreshSidebarTags) {
        if (tagService == null) {
            controller.updateStatus(i18n("status.error"));
            return;
        }
        try {
            List<Tag> allTags = tagService.getAllTags();

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle(i18n("dialog.tags_manager.title"));
            dialog.setHeaderText(i18n("dialog.tags_manager.header"));

            ButtonType closeButton = new ButtonType(i18n("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().add(closeButton);

            VBox content = new VBox(10);
            content.setPadding(new Insets(20));

            ListView<Tag> tagListView = new ListView<>();
            tagListView.getItems().addAll(allTags);
            tagListView.setCellFactory(lv -> new ListCell<Tag>() {
                @Override
                protected void updateItem(Tag tag, boolean empty) {
                    super.updateItem(tag, empty);
                    if (empty || tag == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        HBox hbox = new HBox(10);
                        Label nameLabel = new Label(tag.getTitle());
                        nameLabel.setPrefWidth(200);
                        Label dateLabel = new Label(
                                tag.getCreatedDate() != null ? tag.getCreatedDate() : i18n("label.not_available"));
                        dateLabel.setStyle("-fx-text-fill: gray;");

                        ButtonType deleteType = new ButtonType(i18n("action.delete"), ButtonBar.ButtonData.OK_DONE);
                        javafx.scene.control.Button deleteButton = new javafx.scene.control.Button(i18n("action.delete"));
                        deleteButton.setOnAction(e -> {
                            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                            confirm.setTitle(i18n("dialog.delete_tag.title"));
                            confirm.setHeaderText(i18n("dialog.tags_manager.delete_header"));
                            confirm.setContentText(i18n("dialog.tags_manager.delete_content"));
                            confirm.getButtonTypes().setAll(deleteType, ButtonType.CANCEL);
                            Optional<ButtonType> confirmResult = com.example.jylos.ui.UiDialogs.show(confirm);
                            if (confirmResult.isPresent() && confirmResult.get() == deleteType) {
                                try {
                                    tagService.deleteTag(tag.getId());
                                    tagListView.getItems().remove(tag);
                                    refreshSidebarTags.run();
                                    controller.updateStatus(
                                            MessageFormat.format(i18n("status.tag_deleted"), tag.getTitle()));
                                } catch (Exception ex) {
                                    logger.log(Level.SEVERE, "Failed to delete tag from tags manager", ex);
                                    controller.updateStatus(i18n("status.error_deleting_tag"));
                                }
                            }
                        });

                        hbox.getChildren().addAll(nameLabel, dateLabel, deleteButton);
                        setGraphic(hbox);
                    }
                }
            });

            content.getChildren().add(
                    new Label(MessageFormat.format(i18n("dialog.tags_manager.all_tags_count"), allTags.size())));
            content.getChildren().add(tagListView);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setPrefSize(500, 400);

            com.example.jylos.ui.UiDialogs.show(dialog);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to open tags manager", e);
            controller.updateStatus(i18n("status.tags_manager_error"));
        }
    }
}
