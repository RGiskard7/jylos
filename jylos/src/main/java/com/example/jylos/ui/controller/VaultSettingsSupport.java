package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.FolderTemplate;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;
import com.example.jylos.data.models.VaultSettings.Startup;
import com.example.jylos.data.models.VaultSettings.StartupFront;
import com.example.jylos.data.models.VaultSettings.StartupTabs;
import com.example.jylos.data.models.VaultSettings.Templater;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.service.LinkIndex;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.components.InputSuggest;
import com.example.jylos.ui.components.SettingsPane;
import com.example.jylos.util.MomentFormat;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Preferences > Vault: the open vault's own settings ({@link VaultSettings}): what opens with it
 * and its home page, its templates folder and their date and time formats, its daily note,
 * where new notes go, and Templater.
 * A folder or a note is typed or chosen from the vault's; a date format shows what it writes
 * today. Nothing is kept until the window is saved: {@link #edited} gives what it shows.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class VaultSettingsSupport {

    private final VaultSettings current;
    private final List<String> folders;
    private final List<String> notes;
    private final List<String> homes;
    private final Function<String, String> i18n;
    private final Locale language;
    private final Supplier<ZonedDateTime> now;

    private final TextField home;
    private final ComboBox<String> startupTabs = new ComboBox<>();
    private final ComboBox<String> startupFront = new ComboBox<>();
    private final TextField templatesFolder;
    private final TextField dateFormat = new TextField();
    private final TextField timeFormat = new TextField();
    private final TextField dailyFormat = new TextField();
    private final TextField dailyFolder;
    private final TextField dailyTemplate;
    private final ComboBox<String> newLocation = new ComboBox<>();
    private final TextField newFolder;
    private final CheckBox templaterEnabled = new CheckBox();
    private final CheckBox templaterTrigger = new CheckBox();
    private final VBox folderTemplates = new VBox();
    /** The folder and template fields of each row of {@link #folderTemplates}, in order. */
    private final List<FolderTemplateRow> folderTemplateRows = new ArrayList<>();

    /**
     * @param current  the vault's settings now
     * @param folders  the vault's folders, by path, to choose from
     * @param notes    the vault's notes, by path, to choose a template from
     * @param i18n     the interface's texts
     * @param language the interface's language, for the previews
     * @param now      the moment the previews write
     */
    VaultSettingsSupport(VaultSettings current, List<String> folders, List<String> notes,
            Function<String, String> i18n, Locale language, Supplier<ZonedDateTime> now) {
        this(current, folders, notes, notes, i18n, language, now);
    }

    /**
     * @param homes the vault's notes and canvases, by path, to choose the home page from
     */
    VaultSettingsSupport(VaultSettings current, List<String> folders, List<String> notes, List<String> homes,
            Function<String, String> i18n, Locale language, Supplier<ZonedDateTime> now) {
        this.current = current != null ? current : VaultSettings.defaults();
        this.folders = folders != null ? folders : List.of();
        this.notes = notes != null ? notes : List.of();
        this.homes = homes != null ? homes : List.of();
        this.i18n = i18n;
        this.language = language;
        this.now = now;
        home = choosing(this.homes);
        startupTabs.getItems().setAll(i18n.apply("pref.vault.startup_tabs.last_session"),
                i18n.apply("pref.vault.startup_tabs.pinned"));
        startupFront.getItems().setAll(i18n.apply("pref.vault.startup_front.last_active"),
                i18n.apply("pref.vault.startup_front.new_tab"), i18n.apply("pref.vault.startup_front.home"),
                i18n.apply("pref.vault.startup_front.today"));
        templatesFolder = choosing(this.folders);
        dailyFolder = choosing(this.folders);
        dailyTemplate = choosing(this.notes);
        newFolder = choosing(this.folders);
        newLocation.getItems().setAll(i18n.apply("pref.vault.new_location.current"),
                i18n.apply("pref.vault.new_location.root"), i18n.apply("pref.vault.new_location.folder"));
        newLocation.getSelectionModel().selectedIndexProperty()
                .addListener((o, was, index) -> newFolder.setDisable(index.intValue() != 2));
        show(this.current);
    }

    /**
     * Preferences > Vault for the open vault, with its folders and notes to choose from, or
     * {@code null} with no vault open.
     */
    static VaultSettingsSupport forVault(VaultSettingsStore store, NoteService notes, Function<String, String> i18n,
            ResourceBundle resources) {
        if (store == null || notes == null) {
            return null;
        }
        List<String> folderPaths = notes.folderPaths().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        LinkIndex links = notes.linkIndex();
        List<String> paths = notes.getAllNotes().stream().map(note -> links.pathOf(note.getId()))
                .filter(path -> path != null).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        List<String> notePaths = paths.stream().filter(path -> path.endsWith(".md")).toList();
        List<String> homePaths = paths.stream().filter(path -> path.endsWith(".md") || path.endsWith(".canvas")).toList();
        Locale language = resources != null ? resources.getLocale() : Locale.getDefault();
        return new VaultSettingsSupport(store.get(), folderPaths, notePaths, homePaths, i18n, language,
                ZonedDateTime::now);
    }

    /** Keeps {@code edited} as the vault's settings, when there is a vault and the section was shown. */
    static void save(VaultSettingsStore store, VaultSettings edited) {
        if (store != null && edited != null) {
            store.set(edited);
        }
    }

    /** Adds the section to {@code pane}, with its Reset labelled {@code resetLabel}. */
    void addTo(SettingsPane pane, String resetLabel) {
        Button add = new Button(i18n.apply("pref.vault.folder_templates_add"));
        add.setOnAction(e -> addFolderTemplate(new FolderTemplate("", "")));
        pane.addSection(i18n.apply("dialog.preferences.section.vault"))
                .resettable(resetLabel, () -> show(VaultSettings.defaults()))
                .subheading(i18n.apply("pref.vault.startup"))
                .row(i18n.apply("pref.vault.startup_home"), i18n.apply("pref.vault.startup_home_desc"), home)
                .row(i18n.apply("pref.vault.startup_tabs"), i18n.apply("pref.vault.startup_tabs_desc"), startupTabs)
                .row(i18n.apply("pref.vault.startup_front"), i18n.apply("pref.vault.startup_front_desc"), startupFront)
                .subheading(i18n.apply("pref.vault.templates"))
                .row(i18n.apply("pref.vault.templates_folder"), i18n.apply("pref.vault.templates_folder_desc"),
                        templatesFolder)
                .row(i18n.apply("pref.vault.date_format"), i18n.apply("pref.vault.date_format_desc"),
                        withPreview(dateFormat))
                .row(i18n.apply("pref.vault.time_format"), i18n.apply("pref.vault.time_format_desc"),
                        withPreview(timeFormat))
                .subheading(i18n.apply("pref.vault.daily"))
                .row(i18n.apply("pref.vault.daily_format"), i18n.apply("pref.vault.daily_format_desc"),
                        withPreview(dailyFormat))
                .row(i18n.apply("pref.vault.daily_folder"), i18n.apply("pref.vault.daily_folder_desc"), dailyFolder)
                .row(i18n.apply("pref.vault.daily_template"), i18n.apply("pref.vault.daily_template_desc"),
                        dailyTemplate)
                .subheading(i18n.apply("pref.vault.new_notes"))
                .row(i18n.apply("pref.vault.new_location"), null, newLocation)
                .row(i18n.apply("pref.vault.new_folder"), null, newFolder)
                .subheading(i18n.apply("pref.vault.templater"))
                .row(i18n.apply("pref.vault.templater_enabled"), i18n.apply("pref.vault.templater_enabled_desc"),
                        templaterEnabled)
                .row(i18n.apply("pref.vault.templater_trigger"), i18n.apply("pref.vault.templater_trigger_desc"),
                        templaterTrigger)
                .subheading(i18n.apply("pref.vault.folder_templates"), add)
                .block(folderTemplates);
    }

    /** The settings as the section shows them. */
    VaultSettings edited() {
        List<FolderTemplate> byFolder = new ArrayList<>();
        for (FolderTemplateRow row : folderTemplateRows) {
            String folder = value(row.folder());
            String template = value(row.template());
            if (!folder.isEmpty() || !template.isEmpty()) {
                byFolder.add(new FolderTemplate(folder, template));
            }
        }
        NewNoteLocation location = NewNoteLocation.values()[Math.max(0,
                newLocation.getSelectionModel().getSelectedIndex())];
        return current
                .withTemplates(new Templates(value(templatesFolder), dateFormat.getText().strip(),
                        timeFormat.getText().strip()))
                .withDaily(new DailyNotes(value(dailyFolder), dailyFormat.getText().strip(), value(dailyTemplate)))
                .withNewNotes(new NewNotes(location, value(newFolder)))
                .withTemplater(new Templater(templaterEnabled.isSelected(), templaterTrigger.isSelected(), byFolder))
                .withStartup(new Startup(value(home),
                        StartupTabs.values()[Math.max(0, startupTabs.getSelectionModel().getSelectedIndex())],
                        StartupFront.values()[Math.max(0, startupFront.getSelectionModel().getSelectedIndex())]));
    }

    private void show(VaultSettings settings) {
        home.setText(settings.startup().home());
        startupTabs.getSelectionModel().select(settings.startup().tabs().ordinal());
        startupFront.getSelectionModel().select(settings.startup().front().ordinal());
        templatesFolder.setText(settings.templates().folder());
        dateFormat.setText(settings.templates().dateFormat());
        timeFormat.setText(settings.templates().timeFormat());
        dailyFormat.setText(settings.daily().format());
        dailyFolder.setText(settings.daily().folder());
        dailyTemplate.setText(settings.daily().template());
        newLocation.getSelectionModel().select(settings.newNotes().location().ordinal());
        newFolder.setText(settings.newNotes().folder());
        newFolder.setDisable(settings.newNotes().location() != NewNoteLocation.FOLDER);
        templaterEnabled.setSelected(settings.templater().enabled());
        templaterTrigger.setSelected(settings.templater().triggerOnCreate());
        folderTemplates.getChildren().clear();
        folderTemplateRows.clear();
        settings.templater().folderTemplates().forEach(this::addFolderTemplate);
    }

    private void addFolderTemplate(FolderTemplate template) {
        TextField folder = choosing(folders);
        folder.setPromptText(i18n.apply("pref.vault.folder_templates_folder"));
        folder.setText(template.folder());
        TextField note = choosing(notes);
        note.setPromptText(i18n.apply("pref.vault.folder_templates_template"));
        note.setText(template.template());
        HBox.setHgrow(folder, Priority.ALWAYS);
        HBox.setHgrow(note, Priority.ALWAYS);
        folder.setMaxWidth(Double.MAX_VALUE);
        note.setMaxWidth(Double.MAX_VALUE);
        Button remove = new Button(i18n.apply("pref.vault.folder_templates_remove"));
        remove.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = SettingsPane.controls(folder, note, remove);
        row.getStyleClass().add("vault-folder-template");
        FolderTemplateRow fields = new FolderTemplateRow(folder, note);
        remove.setOnAction(e -> {
            folderTemplates.getChildren().remove(row);
            folderTemplateRows.remove(fields);
        });
        folderTemplates.getChildren().add(row);
        folderTemplateRows.add(fields);
    }

    /** One row of the folder templates: its folder and its template. */
    private record FolderTemplateRow(TextField folder, TextField template) {
    }

    /** {@code format} with what it writes now under it, kept up to date as it is typed. */
    private Node withPreview(TextField format) {
        Label preview = new Label();
        preview.getStyleClass().addAll("settings-row-description", "vault-format-preview");
        Runnable update = () -> preview.setText(MessageFormat.format(i18n.apply("pref.vault.preview"),
                MomentFormat.format(now.get(), format.getText(), language)));
        format.textProperty().addListener((o, was, text) -> update.run());
        update.run();
        VBox box = new VBox(format, preview);
        box.getStyleClass().add("vault-format");
        return box;
    }

    /**
     * A field to type a path in, with the paths of {@code choices} that match what is typed
     * suggested under it, as in Obsidian's settings ({@link InputSuggest}).
     */
    private static TextField choosing(List<String> choices) {
        TextField field = new TextField();
        field.getStyleClass().add("vault-path");
        new InputSuggest(field, InputSuggest.fuzzy(() -> choices));
        return field;
    }

    private static String value(TextField field) {
        String text = field.getText();
        return text != null ? text.strip() : "";
    }
}
